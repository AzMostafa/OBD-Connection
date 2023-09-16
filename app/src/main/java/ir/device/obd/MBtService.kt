package ir.device.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
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
    private val NAME_BT = "ObdElm327"
    private val UUID_BT = UUID.fromString("0001101-0000-1000-8000-00805F9B34FB")

    private var mAdapter: BluetoothAdapter? = null
    private var mAcceptThread: AcceptThread? = null
    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null

    init {
        this.context = context
        this.mHandler = mHandler
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mAdapter = bluetoothManager.adapter
        serviceStatus = Constants.STATE_NONE

        setHandler("init MBtService is Done.")
    }

    fun setHandler(text: String, mode: String? = null, state: Int? = null) {
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

        if (mAcceptThread != null) {
            mAcceptThread!!.cancel()
            mAcceptThread = null
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
    fun connected(socket: BluetoothSocket, socketType: String) {
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

        // ----> Cancel the accept thread because we only want to connect to one device:
        if (mAcceptThread != null) {
            mAcceptThread!!.cancel()
            mAcceptThread = null
        }
        // ----> Start the thread to manage the connection and perform transmissions:
        mConnectedThread = ConnectedThread(socket, socketType)
        mConnectedThread!!.start()

        setState(Constants.STATE_CONNECTED)
    }

    fun write(out: ByteArray) {
        // ----> Create temporary object:
        var CT: ConnectedThread
        // ----> Synchronize a copy of the ConnectedThread:
        synchronized(this) {
            if (serviceStatus != Constants.STATE_CONNECTED) return
            CT = mConnectedThread!!
        }
        // ----> Perform the write unsynchronized:
        CT.write(out)
    }

    private fun connectionFailed() {
        // ----> Start the service over to restart listening mode:
        setState(Constants.STATE_NONE)
        setHandler("Bluetooth connection Failed")
        this@MBtService.start()
    }

    private fun connectionLost() {
        // ----> Start the service over to restart listening mode:
        setHandler("Bluetooth connection Lost")
        this@MBtService.start()
    }

    private inner class AcceptThread : Thread() {

        private val mSocketType: String? = null
        private val mmServerSocket: BluetoothServerSocket?

        init {
            setHandler("init AcceptThread")
            var tmp: BluetoothServerSocket? = null
            // ----> Create a new listening server socket
            try {
                tmp = mAdapter!!.listenUsingRfcommWithServiceRecord(NAME_BT, UUID_BT)
            } catch (e: IOException) {
                setHandler("[AcceptThread] Socket Type: " + mSocketType + "listen() failed")
                Log.e(
                    "LOG",
                    "Socket Type: " + mSocketType + "listen() failed", e
                )
            }
            mmServerSocket = tmp
        }

        override fun run() {
            name = "AcceptThread$mSocketType"
            var socket: BluetoothSocket? = null

            // ----> Listen to the server socket if we're not connected:
            while (serviceStatus != Constants.STATE_CONNECTED) {
                setHandler("AcceptThread run() and serviceStatus=$serviceStatus")
                socket = try {
                    // ----> This is a blocking call and will only return on a
                    // ----> successful connection or an exception:
                    mmServerSocket!!.accept()
                } catch (e: IOException) {
                    setHandler("[AcceptThread] Socket Type: " + mSocketType + "accept() failed")
                    Log.e(
                        "LOG",
                        "Socket Type: " + mSocketType + "accept() failed", e
                    )
                    break
                }

                // ----> If a connection was accepted:
                if (socket != null) {
                    synchronized(this@MBtService) {
                        when (serviceStatus) {
                            Constants.STATE_LISTEN, Constants.STATE_CONNECTING -> {
                                // ----> Situation normal Start the connected thread:
                                connected(socket, mSocketType!!)
                            }

                            else -> {
                                // ----> Either not ready or already connected. Terminate new socket:
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                    setHandler("Could not close unwanted socket")
                                    Log.e(
                                        "LOG",
                                        "Could not close unwanted socket", e
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        fun cancel() {
            try {
                mmServerSocket!!.close()
            } catch (e: IOException) {
                setHandler("Socket Type" + mSocketType + "close() of server failed")
                Log.e(
                    "LOG",
                    "Socket Type" + mSocketType + "close() of server failed", e
                )
            }
        }
    }

    private inner class ConnectThread(mmDevice: BluetoothDevice) : Thread() {

        private val mSocketType: String? = null
        private var mmDevice: BluetoothDevice? = null
        private val mmSocket: BluetoothSocket?

        init {
            setHandler("init ConnectThread")
            this.mmDevice = mmDevice
            var tmp: BluetoothSocket? = null

            // ----> Get a BluetoothSocket for a connection with the
            // ----> given BluetoothDevice:
            try {
                tmp = mmDevice.createRfcommSocketToServiceRecord(UUID_BT)
            } catch (e: IOException) {
                setHandler("Socket Type: " + mSocketType + "create() failed")
                Log.e(
                    "LOG",
                    "Socket Type: " + mSocketType + "create() failed", e
                )
            }
            mmSocket = tmp
        }

        override fun run() {
            setHandler("ConnectThread run() SocketType:$mSocketType")
            name = "ConnectThread$mSocketType"

            // ----> Always cancel discovery because it will slow down a connection:
            mAdapter?.cancelDiscovery()

            // ----> Make a connection to the BluetoothSocket:
            try {
                // ----> This is a blocking call and will only return on a
                // ----> successful connection or an exception:
                mmSocket!!.connect()
            } catch (e: IOException) {
                // ----> Close the socket:
                try {
                    mmSocket!!.close()
                } catch (e2: IOException) {
                    setHandler("unable to close() $mSocketType socket during connection failure")
                    Log.e(
                        "LOG",
                        "unable to close() $mSocketType socket during connection failure", e2
                    )
                }
                connectionFailed()
                return
            }

            // ----> Reset the ConnectThread because we're done
            synchronized(this@MBtService) { mConnectThread = null }

            // ----> Start the connected thread
            connected(mmSocket, mSocketType!!)
        }

        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) {
                setHandler("close() of connect $mSocketType socket failed")
                Log.e(
                    "LOG",
                    "close() of connect $mSocketType socket failed", e
                )
            }
        }
    }

    private inner class ConnectedThread(socket: BluetoothSocket, socketType: String) : Thread() {

        private val mmSocket: BluetoothSocket
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?
        private var s: String = ""
        private var msg: String = ""

        init {
            setHandler("create ConnectedThread: $socketType")
            mmSocket = socket
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            // ----> Get the BluetoothSocket input and output streams:
            try {
                tmpIn = socket.inputStream
                tmpOut = socket.outputStream
            } catch (e: IOException) {
                setHandler("temp sockets not created")
                Log.e("LOG", "temp sockets not created", e)
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
        }

        override fun run() {
            while (true) {
                try {
                    val buffer = ByteArray(1)
                    val s = String(buffer)
                    for (element in s) {
                        msg += element
                        setHandler("Message is $msg")
                    }
                    // ----> Do something with the bytes read in There are bytesRead bytes in tempBuffer:
                } catch (e: IOException) {
                    connectionLost()
                    // ----> Start the service over to restart listening mode:
                    this@MBtService.start()
                    break
                }
            }
        }

        fun write(buffer: ByteArray) {
            try {
                mmOutStream!!.write(buffer)
                mmOutStream.flush()
            } catch (e: IOException) {
                setHandler("Exception during write")
                Log.e("LOG", "Exception during write", e)
            }
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                setHandler("close() of connect socket failed")
                Log.e("LOG", "close() of connect socket failed", e)
            }
        }
    }
}