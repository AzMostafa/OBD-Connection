package ir.device.obd

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    private lateinit var context: Context

    private lateinit var lPaired: ListView
    private lateinit var lDiscovered: ListView
    private lateinit var txtBTStatus: TextView
    private lateinit var txtPairedDeviceEmpty: TextView

    private val mBluetoothService: MBtService by lazy {
        MBtService(context, mBtHandler)
    }
    private var mBluetoothDevice: BluetoothDevice? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var listPairedDevice = hashMapOf<String, BluetoothDevice?>()
    private var listDiscoveredDevice = hashMapOf<String, BluetoothDevice?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --------> Set Activity Context:
        this.context = this

        // --------> Set IDs:
        val btnSwitchBT = findViewById<Button>(R.id.btn_BTSwitch)
        val btnFindBT = findViewById<Button>(R.id.btn_BTFind)
        txtBTStatus = findViewById(R.id.txt_BTStatus)
        txtPairedDeviceEmpty = findViewById(R.id.txt_pairedDeviceEmpty)
        lPaired = findViewById(R.id.lPairedBluetooth)
        lDiscovered = findViewById(R.id.lDiscoveredBluetooth)

        btnSwitchBT.setOnClickListener {
            setSwitchBluetooth()
        }

        btnFindBT.setOnClickListener {
            setDisplayBluetooth()
        }

        lDiscovered.onItemClickListener = AdapterView.OnItemClickListener { _, view, _, _ ->
            val txtMacDevice = view.findViewById<TextView>(R.id.txt_MacAddress)
            val deviceMac = txtMacDevice.text.toString()
            val device = listDiscoveredDevice[deviceMac]
            if (device != null && !(deviceMac == "" || deviceMac == "null")) {
                Log.i("LOG", "Selected Device Name: ${device.name} and Mac: $deviceMac")
                val deviceBT: BluetoothDevice = mBluetoothAdapter!!.getRemoteDevice(deviceMac)
                try {
                    mBluetoothService.connect(deviceBT)
                    mBluetoothDevice = deviceBT
                } catch (e: Exception) {
                    Log.e("LOG", "Unable to Connect BT!", e)
                }
            } else {
                Log.e("LOG", "Selected MacAddress is empty or listDiscoveredDevice is null!")
            }
        }

        lPaired.onItemClickListener = AdapterView.OnItemClickListener { _, view, _, _ ->
            val txtMacDevice = view.findViewById<TextView>(R.id.txt_MacAddress)
            val deviceMac = txtMacDevice.text.toString()
            val device = listPairedDevice[deviceMac]
            if (device != null && !(deviceMac == "" || deviceMac == "null")) {
                Log.i("LOG", "Selected Device Name: ${device.name} and Mac: $deviceMac")
                val deviceBT: BluetoothDevice = mBluetoothAdapter!!.getRemoteDevice(deviceMac)
                try {
                    mBluetoothService.connect(deviceBT)
                    mBluetoothDevice = deviceBT
                } catch (e: Exception) {
                    Log.e("LOG", "Unable to Connect BT!", e)
                }
            } else {
                Log.e("LOG", "Selected MacAddress is empty or listPairedDevice is null!")
            }
        }

        // --------> Set Initialize:
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter
        if (mBluetoothAdapter == null) {
            txtBTStatus.text = "bluetooth of Device is not Available!"
            txtBTStatus.setTextColor(ContextCompat.getColor(context, R.color.E1))
            Log.e("LOG", "bluetooth of Device is not Available!")
        } else {
            if (mBluetoothAdapter!!.isEnabled) {
                txtBTStatus.text = "Bluetooth is Turn On."
                txtBTStatus.setTextColor(ContextCompat.getColor(context, R.color.A1))
                Log.d("LOG", "bluetooth of Device is Enabled.")

                if (mBluetoothService.getState() == Constants.STATE_NONE) {
                    mBluetoothService.start()
                }
            }

            val filter = IntentFilter()
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            filter.addAction(BluetoothDevice.ACTION_FOUND)
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            registerReceiver(bluetoothFindReceiver, filter)
        }
    }

    private fun resetInfo() {
        listPairedDevice = hashMapOf()
        listDiscoveredDevice = hashMapOf()

        txtPairedDeviceEmpty.visibility = View.INVISIBLE
    }

    private fun setSwitchBluetooth() {
        if (checkPermission()) {
            if (!mBluetoothAdapter!!.isEnabled) {
                val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (checkPermission()) {
                    startActivityForResult(enableIntent, Constants.REQUEST_BT_ENABLE)
                } else {
                    setPermission()
                }
            } else {
                Toast.makeText(this, "Bluetooth is Enable.", Toast.LENGTH_SHORT).show()
            }
        } else {
            setPermission()
        }
    }

    private fun setDisplayBluetooth() {
        if (checkPermission()) {
            resetInfo()
            setBluetoothDevicePaired()
            setBluetoothDeviceDiscovered()
        } else {
            setPermission()
        }
    }

    private fun setECURequest(request: String) {
        // ----> Check that we're actually connected before trying anything
        if (mBluetoothService.getState() == Constants.STATE_CONNECTED) {
            try {
                if (request.isNotEmpty()) {
                    // ----> Get the message bytes and tell the BluetoothChatService to write
                    val textByte = request + "\r"
                    mBluetoothService.write(textByte.toByteArray())
                }
            } catch (e: Exception) {
                Log.e("LOG", "unable to send request to ECU!", e)
            }
        }
    }

    private fun setBluetoothDeviceDiscovered() {
        if (mBluetoothAdapter?.isDiscovering == true) {
            mBluetoothAdapter?.cancelDiscovery()
            mBluetoothAdapter?.startDiscovery()
        } else {
            mBluetoothAdapter?.startDiscovery()
        }
    }

    private fun setBluetoothDevicePaired() {
        val pairedDevices: Set<BluetoothDevice>? = mBluetoothAdapter?.bondedDevices
        pairedDevices?.forEach { device ->
            val deviceName = device.name
            val deviceMac = device.address
            Log.d(
                "LOG",
                "bluetoothPairedDevice: [deviceName=$deviceName / deviceHardwareAddress: $deviceMac]"
            )
            listPairedDevice[deviceMac] = device
        }

        if (listPairedDevice.size == 0) {
            txtPairedDeviceEmpty.visibility = View.VISIBLE
        } else {
            val adapter = AdapterPairedBT(ArrayList(listPairedDevice.keys))
            lPaired.adapter = adapter
            lPaired.itemsCanFocus = true
        }
    }

    private val bluetoothFindReceiver = object : BroadcastReceiver() {

        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    Log.i("LOG", "BluetoothDiscovery States Changed!")
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.i("LOG", "BluetoothDiscovery START")
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.i("LOG", "BluetoothDiscovery END")
                }

                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) {
                        val deviceName = device.name
                        val deviceMac = device.address
                        Log.d(
                            "LOG",
                            "bluetoothDiscoveredDevice: [deviceName=$deviceName / deviceHardwareAddress: $deviceMac]"
                        )
                        listDiscoveredDevice[deviceMac] = device

                        val adapter = AdapterDiscoveredBT(ArrayList(listDiscoveredDevice.keys))
                        lDiscovered.adapter = adapter
                        lDiscovered.itemsCanFocus = true

                    } else {
                        Log.e("LOG", "bluetoothDiscovering has Error!")
                    }
                }
            }
        }
    }

    private var mBtHandler = Handler(Looper.getMainLooper()) {
        when (it.what) {
            Constants.STATE_LOG -> {
                val text = it.data.getString("TEXT")
                if (text != null){
                    Log.i("LOG", text)
                } else {
                    Log.i("LOG", "HERERERERERERERH")
                }
            }

            Constants.STATE_NONE -> {

            }

            Constants.STATE_LISTEN -> {

            }

            Constants.STATE_CONNECTING -> {

            }

            Constants.STATE_CONNECTED -> {
                setECURequest(Constants.ECU_RESET)
            }
        }
        true
    }

    /// ---------------------------- Permission and Enable Bluetooth --------------------------- ///
    private fun setPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                Constants.REQUEST_BT_PERMISSION
            )
        } else {
            Log.d("LOG", "bluetooth Permission is Granted [SDK < 31].")
        }
    }

    private fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.i("LOG", "bluetooth Permission need to Granted [SDK > 31].")
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            Log.d("LOG", "bluetooth Permission is Granted [SDK < 31].")
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            Constants.REQUEST_BT_ENABLE -> {
                if (resultCode == Activity.RESULT_OK) {
                    txtBTStatus.text = "Bluetooth is Turn On."
                    txtBTStatus.setTextColor(ContextCompat.getColor(context, R.color.A1))

                    if (mBluetoothService.getState() == Constants.STATE_NONE) {
                        mBluetoothService.start()
                    }

                    Log.d("LOG", "bluetooth of Device is Enabled.")
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    Log.e("LOG", "bluetooth of Device is Disabled!")
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            Constants.REQUEST_BT_PERMISSION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    if (mBluetoothService.getState() == Constants.STATE_NONE) {
                        mBluetoothService.start()
                    }
                    if (!mBluetoothAdapter!!.isEnabled) {
                        setSwitchBluetooth()
                    } else {
                        setDisplayBluetooth()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mBluetoothService.stop()
            unregisterReceiver(bluetoothFindReceiver)
        } catch (_: Exception) {
            Log.e("LOG", "bluetoothReceiver isNotDestroyed")
        }
    }

    /// --------------------------------------- List Adapter ----------------------------------- ///
    inner class AdapterPairedBT(dataInput: List<String>) :
        ArrayAdapter<String?>(context, R.layout.listview_items, dataInput) {

        @SuppressLint("MissingPermission", "ViewHolder", "InflateParams")
        override fun getView(position: Int, view: View?, parent: ViewGroup): View {

            val activity = context as Activity
            val inflater = activity.layoutInflater
            val rowView: View = inflater.inflate(R.layout.listview_items, null, true)

            val txtName = rowView.findViewById<TextView>(R.id.txt_Name)
            val txtMac = rowView.findViewById<TextView>(R.id.txt_MacAddress)

            try {
                val device: BluetoothDevice? = listPairedDevice.values.elementAt(position)
                if (device != null) {
                    txtName.text = device.name
                    txtMac.text = device.address
                } else {
                    txtName.text = "unKnown"
                    txtMac.text = "--:--:--:--:--:--"
                }
            } catch (e: Exception) {
                Log.e("LOG", "bluetoothShowItem listPairedDevice Error: ${e.message}")
            }

            return rowView
        }
    }

    inner class AdapterDiscoveredBT(dataInput: List<String>) :
        ArrayAdapter<String?>(context, R.layout.listview_items, dataInput) {

        @SuppressLint("MissingPermission", "ViewHolder", "InflateParams")
        override fun getView(position: Int, view: View?, parent: ViewGroup): View {

            val activity = context as Activity
            val inflater = activity.layoutInflater
            val rowView: View = inflater.inflate(R.layout.listview_items, null, true)

            val txtName = rowView.findViewById<TextView>(R.id.txt_Name)
            val txtMac = rowView.findViewById<TextView>(R.id.txt_MacAddress)

            try {
                val device: BluetoothDevice? = listDiscoveredDevice.values.elementAt(position)
                if (device != null) {
                    txtName.text = device.name
                    txtMac.text = device.address
                } else {
                    txtName.text = "unKnown"
                    txtMac.text = "--:--:--:--:--:--"
                }
            } catch (e: Exception) {
                Log.e("LOG", "bluetoothShowItem listDiscoveredDevice Error: ${e.message}")
            }

            return rowView
        }
    }
}