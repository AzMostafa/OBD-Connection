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
                                bluetoothStatusHandler("READ: $text")
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
}