package ir.device.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class MBtService(context: Context, mHandler: Handler) {

    private var context: Context
    private var mHandler: Handler
    private var serviceStatus = Constants.STATE_NONE
    private val UUID_BT = UUID.fromString("0001101-0000-1000-8000-00805F9B34FB")

    private var mAdapter: BluetoothAdapter? = null
    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null

    init {
        this.context = context
        this.mHandler = mHandler
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mAdapter = bluetoothManager.adapter
        serviceStatus = Constants.STATE_NONE

        bluetoothStatusHandler("init MBtService is Done.")
    }

    fun start() {
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        setState(Constants.STATE_LISTEN)
    }

    fun stop() {
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        setState(Constants.STATE_NONE)
    }

    @Synchronized
    fun getState(): Int {
        return serviceStatus
    }

    @Synchronized
    fun setState(state: Int) {
        serviceStatus = state
    }

    @Synchronized
    fun connect(device: BluetoothDevice) {
        // ----> Cancel any thread attempting to make a connection:
        if (serviceStatus == Constants.STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread!!.cancel()
                mConnectThread = null
            }
        }

        // ----> Cancel any thread currently running a connection:
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // ----> Start the thread to connect with the given device:
        mConnectThread = ConnectThread(device)
        mConnectThread!!.start()
        setState(Constants.STATE_CONNECTING)
    }

    @Synchronized
    fun connected(socket: BluetoothSocket) {
        // ----> Cancel the thread that completed the connection:
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        // ----> Cancel any thread currently running a connection:
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // ----> Start the thread to manage the connection and perform transmissions:
        mConnectedThread = ConnectedThread(socket)
        mConnectedThread!!.start()

        setState(Constants.STATE_CONNECTED)
    }

    fun write(out: ByteArray) {
        // ----> Create temporary object:
        var cT: ConnectedThread
        // ----> Synchronize a copy of the ConnectedThread:
        synchronized(this) {
            if (serviceStatus != Constants.STATE_CONNECTED) return
            cT = mConnectedThread!!
        }
        // ----> Perform the write synchronized:
        cT.write(out)
    }

    private fun bluetoothStatusHandler(text: String, mode: String? = null, state: Int? = null) {
        val msg: Message = if (mode == null) {
            mHandler.obtainMessage(Constants.STATE_LOG)
        } else {
            mHandler.obtainMessage(state!!)
        }
        val bundle = Bundle()
        bundle.putString("TEXT", text)
        msg.data = bundle
        mHandler.sendMessage(msg)
    }

    private fun bluetoothConnectionFailed() {
        // ----> Start the service over to restart listening mode:
        setState(Constants.STATE_NONE)
        bluetoothStatusHandler(
            "Bluetooth connection Failed.",
            "STATE_CONNECT_FAILED",
            Constants.STATE_CONNECT_FAILED
        )
    }

    private fun bluetoothConnectionLost() {
        // ----> Start the service over to restart listening mode:
        setState(Constants.STATE_NONE)
        bluetoothStatusHandler(
            "Bluetooth connection Lost.",
            "STATE_CONNECT_LOST",
            Constants.STATE_CONNECT_LOST
        )
    }

    private inner class ConnectThread(mmDevice: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            mmDevice.createRfcommSocketToServiceRecord(UUID_BT)
        }

        override fun run() {
            bluetoothStatusHandler("ConnectThread START")
            // ----> Always cancel discovery because it will slow down a connection:
            if (mAdapter!!.isDiscovering) {
                mAdapter?.cancelDiscovery()
            }

            // ----> Make a connection to the BluetoothSocket:
            try {
                // ----> This is a blocking call and will only return on a successful connection or an exception:
                mmSocket!!.connect()
                bluetoothStatusHandler(
                    "ConnectThread Socket has been connected.",
                    "STATE_CONNECTED",
                    Constants.STATE_CONNECTED
                )
            } catch (e: IOException) {
                bluetoothStatusHandler("[ConnectThread] Socket connect() failed \n${e.message}")
                Log.e(
                    "LOG",
                    "Socket connect() failed", e
                )
                // ----> Close the socket:
                try {
                    mmSocket!!.close()
                } catch (e2: IOException) {
                    bluetoothStatusHandler(
                        "unable to close() socket during connection failure \n" +
                                "${e2.message}"
                    )
                    Log.e(
                        "LOG",
                        "unable to close() socket during connection failure", e2
                    )
                }
                bluetoothConnectionFailed()
                return
            }

            // ----> Reset the ConnectThread because we're done
            synchronized(this@MBtService) { mConnectThread = null }

            // ----> Start the connected thread
            connected(mmSocket!!)
        }

        fun cancel() {
            try {
                setState(Constants.STATE_NONE)
                mmSocket!!.close()
            } catch (e: IOException) {
                bluetoothStatusHandler("close() of connect socket failed")
                Log.e(
                    "LOG",
                    "close() of connect socket failed", e
                )
            }
        }
    }

    private inner class ConnectedThread(socket: BluetoothSocket) : Thread() {

        private var text: String = ""
        private var str: String = ""
        private val mmSocket: BluetoothSocket by lazy {
            socket
        }
        private val mmInStream: InputStream? by lazy {
            socket.inputStream
        }
        private val mmOutStream: OutputStream? by lazy {
            socket.outputStream
        }

        override fun run() {
            while (true) {
                try {
                    // ----> Do something with the bytes read in There are bytesRead bytes in tempBuffer:
                    val buffer = ByteArray(1)
                    mmInStream!!.read(buffer, 0, buffer.size)
                    str = String(buffer)
                    for (element in str) {
                        text += element
                        if (text.contains(">")) {
                            if (text.isNotEmpty()) {
                                bluetoothStatusHandler(text, "STATE_READ", Constants.STATE_READ)
                                text = ""
                            } else {
                                bluetoothStatusHandler("READ: text is Empty!")
                            }
                        }
                    }
                } catch (e: IOException) {
                    bluetoothConnectionLost()
                    break
                }
            }
        }

        fun write(buffer: ByteArray) {
            try {
                mmOutStream!!.write(buffer)
                mmOutStream!!.flush()

                val writeMessage = String(buffer)
                bluetoothStatusHandler("During write Request: $writeMessage")
            } catch (e: IOException) {
                bluetoothStatusHandler("Exception during write Error! \n${e.message}")
            }
        }

        fun cancel() {
            try {
                setState(Constants.STATE_NONE)
                mmSocket.close()
            } catch (e: IOException) {
                bluetoothStatusHandler("close() of connect socket failed")
                Log.e("LOG", "close() of connect socket failed", e)
            }
        }
    }

    enum class BluetoothDeviceType(val value: Int, name: String) {
        DEVICE_BT(50, "BluetoothDevice"),
        AUDIO_VIDEO_CAMCORDER(1076, "AvCamcorder"),
        AUDIO_VIDEO_CAR_AUDIO(1056, "CarAudio"),
        AUDIO_VIDEO_HANDSFREE(1032, "HandsFree"),
        AUDIO_VIDEO_HEADPHONES(1048, "HeadPhones"),
        AUDIO_VIDEO_HIFI_AUDIO(1064, "HiFiAudio"),
        AUDIO_VIDEO_LOUDSPEAKER(1044, "LoudSpeaker"),
        AUDIO_VIDEO_MICROPHONE(1040, "Microphone"),
        AUDIO_VIDEO_PORTABLE_AUDIO(1052, "PortableAudio"),
        AUDIO_VIDEO_SET_TOP_BOX(1060, "SetTopBox"),
        AUDIO_VIDEO_UNCATEGORIZED(1024, "Uncategorized_Av"),
        AUDIO_VIDEO_VCR(1068, "VCR"),
        AUDIO_VIDEO_VIDEO_CAMERA(1072, "VideoCamera"),
        AUDIO_VIDEO_VIDEO_CONFERENCING(1088, "VideoConferencing"),
        AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER(1084, "Display_LoudSpeaker"),
        AUDIO_VIDEO_VIDEO_GAMING_TOY(1096, "GamingToy"),
        AUDIO_VIDEO_VIDEO_MONITOR(1080, "VideoMonitor"),
        AUDIO_VIDEO_WEARABLE_HEADSET(1028, "WearableHeadset"),
        COMPUTER_DESKTOP(260, "Desktop"),
        COMPUTER_HANDHELD_PC_PDA(272, "HandheldPcPDA"),
        COMPUTER_LAPTOP(268, "Laptop"),
        COMPUTER_PALM_SIZE_PC_PDA(276, "PalmSizePcPDA"),
        COMPUTER_SERVER(264, "Server"),
        COMPUTER_UNCATEGORIZED(256, "Uncategorized_Pc"),
        COMPUTER_WEARABLE(280, "WearablePc"),
        HEALTH_BLOOD_PRESSURE(2308, "BloodPressure"),
        HEALTH_DATA_DISPLAY(2332, "DataDisplay"),
        HEALTH_GLUCOSE(2320, "Glucose"),
        HEALTH_PULSE_OXIMETER(2324, "PulseOxiMeter"),
        HEALTH_PULSE_RATE(2328, "PulseRate"),
        HEALTH_THERMOMETER(2312, "Thermometer"),
        HEALTH_UNCATEGORIZED(2304, "Uncategorized_Health"),
        HEALTH_WEIGHING(2316, "Weighing"),
        PERIPHERAL_KEYBOARD(1344, "Keyboard"),
        PERIPHERAL_KEYBOARD_POINTING(1472, "KeyboardPointing"),
        PERIPHERAL_NON_KEYBOARD_NON_POINTING(1280, "NonKeyboardPointing"),
        PERIPHERAL_POINTING(1408, "Pointing"),
        PHONE_CELLULAR(516, "PhoneCellular"),
        PHONE_CORDLESS(520, "PhoneCordless"),
        PHONE_ISDN(532, "PhoneISDN"),
        PHONE_MODEM_OR_GATEWAY(528, "PhoneModemOrGateway"),
        PHONE_SMART(524, "SmartPhone"),
        PHONE_UNCATEGORIZED(512, "Uncategorized_Phone"),
        TOY_CONTROLLER(2064, "Controller"),
        TOY_DOLL_ACTION_FIGURE(2060, "DollActionFigure"),
        TOY_GAME(2068, "Game"),
        TOY_ROBOT(2052, "Robot"),
        TOY_UNCATEGORIZED(2048, "Uncategorized_Toy"),
        TOY_VEHICLE(2056, "Vehicle"),
        WEARABLE_GLASSES(1812, "Glasses"),
        WEARABLE_HELMET(1808, "Helmet"),
        WEARABLE_JACKET(1804, "Jacket"),
        WEARABLE_PAGER(1800, "Pager"),
        WEARABLE_UNCATEGORIZED(1792, "Uncategorized_Wearable"),
        WEARABLE_WRIST_WATCH(1796, "WristWatch");

        companion object {
            fun findByValue(value: Int): BluetoothDeviceType {
                return values().find { it.value == value } ?: DEVICE_BT
            }
        }
    }
}