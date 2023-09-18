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
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Timer
import java.util.TimerTask


@SuppressLint("InlinedApi", "MissingPermission")
class BluetoothActivity : AppCompatActivity() {

    private lateinit var context: Context

    private lateinit var lLog: ListView
    private lateinit var lPaired: ListView
    private lateinit var lDiscovered: ListView

    private val requiredPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    private var listLog: MutableList<String> = mutableListOf()
    private val listPairedBluetooth: HashMap<String, String> = hashMapOf()
    private var listDiscoveredBluetooth: HashMap<String, String> = hashMapOf()

    private val bluetoothManager by lazy {
        applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter by lazy {
        bluetoothManager.adapter
    }
    private val locationManager by lazy {
        applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager
    }
    private val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true
    private val isLocationEnabled: Boolean
        get() = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth)

        // --------> Set Activity Context:
        this.context = this

        // --------> Set IDs:
        val btnBTDevice = findViewById<Button>(R.id.btn_BluetoothDevice)
        val btnLogcat = findViewById<Button>(R.id.btn_LogStatus)
        val btnBluetooth = findViewById<Button>(R.id.btn_Bluetooth)
        val lBTDevice = findViewById<LinearLayout>(R.id.lBluetoothDevice)
        val lLogcat = findViewById<LinearLayout>(R.id.lLogcat)
        lLog = findViewById(R.id.lListLogcat)
        lPaired = findViewById(R.id.lPairedBluetooth)
        lDiscovered = findViewById(R.id.lDiscoveredBluetooth)

        if (bluetoothAdapter == null) {
            logcat("Bluetooth of Device is not Available!", "e")
        } else {
            val filter = IntentFilter()
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            filter.addAction(BluetoothDevice.ACTION_FOUND)
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            registerReceiver(bluetoothFindReceiver, filter)
        }

        // --------> Set Initialize:
        btnBTDevice.setOnClickListener {
            lLogcat.visibility = View.GONE
            lBTDevice.visibility = View.VISIBLE
            btnBluetooth.text = "FIND BLUETOOTH DEVICE"
        }

        btnLogcat.setOnClickListener {
            lLogcat.visibility = View.VISIBLE
            lBTDevice.visibility = View.GONE
            btnBluetooth.text = "CLEAR LOG"
        }

        btnBluetooth.setOnClickListener {
            if (lLogcat.visibility == View.VISIBLE) {
                listLog = mutableListOf()
                val adapter: ArrayAdapter<String> = ArrayAdapter<String>(
                    this,
                    R.layout.item_log,
                    listLog
                )
                lLog.adapter = adapter
            } else {
                bluetoothSystemLauncher()
            }
        }

        lPaired.onItemClickListener = AdapterView.OnItemClickListener { _, view, _, _ ->

        }

        lDiscovered.onItemClickListener = AdapterView.OnItemClickListener { _, view, _, _ ->

        }
    }

    private fun bluetoothSystemLauncher() {
        if (requiredPermissions()) {
            requestPermissions()
        } else {
            bluetoothDiscoveringLauncher()
        }
    }

    private fun bluetoothDiscoveringLauncher() {
        if (!isBluetoothEnabled) {
            bluetoothPowerLauncher.launch(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            )
        } else if (!isLocationEnabled) {
            locationPowerLauncher.launch(
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            )
        } else {
            logcat("DISCOVER LAUNCH")
            // ------> Part 1: Paired Bluetooth Device
            if (listPairedBluetooth.size == 0) {
                val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
                pairedDevices?.forEach { device ->
                    val deviceName = device.name.toString()
                    val deviceMac = device.address.toString()

                    listPairedBluetooth[deviceName] = deviceMac
                }
                if (listPairedBluetooth.size != 0) {
                    val adapter = AdapterListViewPaired(listPairedBluetooth)
                    lPaired.adapter = adapter
                    lPaired.itemsCanFocus = true
                }
            }

            // ------> Part 2: Discovering Bluetooth Device
            listDiscoveredBluetooth = hashMapOf()
            val adapter = AdapterListViewDiscovered(listDiscoveredBluetooth)
            lDiscovered.adapter = adapter

            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
                bluetoothAdapter?.startDiscovery()
            } else {
                bluetoothAdapter?.startDiscovery()
            }
        }
    }

    private fun requiredPermissions(permission: String? = null): Boolean {
        var isPermissionOK = false
        if (permission == null) {
            for (per in requiredPermissions) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        per
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    isPermissionOK = true
                    break
                }
            }
        } else {
            isPermissionOK = ActivityCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
        return isPermissionOK
    }

    private fun requestPermissions() {
        logcat("Request Permissions")
        var result: Int
        val listPermissionsNeeded: MutableList<String> = ArrayList()
        for (per in requiredPermissions) {
            result = ContextCompat.checkSelfPermission(applicationContext, per)
            if (result != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(per)
            }
        }
        if (listPermissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                listPermissionsNeeded.toTypedArray(),
                Constants.REQUEST_BT_PERMISSION
            )
        }
    }

    private val bluetoothFindReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    logcat("Bluetooth Discovering States Changed!")
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    logcat("Bluetooth Discovering START")
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    logcat("Bluetooth Discovering END")
                }

                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    try {
                        if (device != null) {
                            val deviceName = device.name.toString()
                            val deviceMac = device.address.toString()
                            logcat("Bluetooth Discovering Device $deviceName | $deviceMac", "d")
                            listDiscoveredBluetooth[deviceName] = deviceMac

                            val adapter = AdapterListViewDiscovered(listDiscoveredBluetooth)
                            lDiscovered.adapter = adapter
                            lDiscovered.itemsCanFocus = true
                        } else {
                            logcat("Bluetooth Discovering device is null!", "e")
                        }
                    } catch (e: Exception) {
                        logcat("Bluetooth Discovering has Error! \n ${e.message}", "e")
                    }
                }
            }
        }
    }

    private val bluetoothPowerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            logcat("Bluetooth of Device is Enabled.", "d")
            bluetoothDiscoveringLauncher()
        } else if (it.resultCode == Activity.RESULT_CANCELED) {
            logcat("Bluetooth of Device is Disabled.", "e")
        }
    }

    private val locationPowerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (isLocationEnabled) {
            logcat("Location of Device is Enabled.", "d")
            bluetoothDiscoveringLauncher()
        } else {
            logcat("Location of Device is Disabled.", "e")
        }
    }

    inner class AdapterListViewPaired(dataInput: HashMap<String, String>) :
        ArrayAdapter<String>(context, R.layout.item_bluetooth, dataInput.keys.toTypedArray()) {

        private var dataInput: HashMap<String, String>

        init {
            this.dataInput = dataInput
        }

        @SuppressLint("ViewHolder", "InflateParams", "SetTextI18n")
        override fun getView(position: Int, view: View?, parent: ViewGroup): View {

            val activity = context as Activity
            val inflater = activity.layoutInflater
            val rowView: View = inflater.inflate(R.layout.item_bluetooth, null, true)

            val txtName = rowView.findViewById<TextView>(R.id.txt_Name)
            val txtMac = rowView.findViewById<TextView>(R.id.txt_MacAddress)

            try {
                val deviceName = dataInput.keys.elementAt(position)
                val deviceMac = dataInput.values.elementAt(position)
                if (deviceMac.isNotEmpty()) {
                    txtName.text = deviceName
                    txtMac.text = deviceMac
                } else {
                    txtName.text = "unKnown"
                    txtMac.text = "--:--:--:--:--:--"
                }
            } catch (e: Exception) {
                logcat("Bluetooth ShowListView Error: \n ${e.message}", "e")
            }

            return rowView
        }
    }

    inner class AdapterListViewDiscovered(dataInput: HashMap<String, String>) :
        ArrayAdapter<String>(context, R.layout.item_bluetooth, dataInput.keys.toTypedArray()) {

        private var dataInput: HashMap<String, String>

        init {
            this.dataInput = dataInput
        }

        @SuppressLint("ViewHolder", "InflateParams", "SetTextI18n")
        override fun getView(position: Int, view: View?, parent: ViewGroup): View {

            val activity = context as Activity
            val inflater = activity.layoutInflater
            val rowView: View = inflater.inflate(R.layout.item_bluetooth, null, true)

            val txtName = rowView.findViewById<TextView>(R.id.txt_Name)
            val txtMac = rowView.findViewById<TextView>(R.id.txt_MacAddress)

            try {
                val deviceName = dataInput.keys.elementAt(position)
                val deviceMac = dataInput.values.elementAt(position)
                if (deviceMac.isNotEmpty()) {
                    txtName.text = deviceName
                    txtMac.text = deviceMac
                } else {
                    txtName.text = "unKnown"
                    txtMac.text = "--:--:--:--:--:--"
                }
            } catch (e: Exception) {
                logcat("Bluetooth ShowListView Error: \n ${e.message}", "e")
            }

            return rowView
        }
    }

    private fun logcat(text: String, mode: String = "i") {
        listLog.add("$mode : $text")
        when (mode) {
            "d" -> {
                Log.d("LOG", text)
            }

            "e" -> {
                Log.e("LOG", text)
            }

            else -> {
                Log.i("LOG", text)
            }
        }
        val adapter: ArrayAdapter<String> = ArrayAdapter<String>(
            this,
            R.layout.item_log,
            listLog
        )
        lLog.adapter = adapter
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(bluetoothFindReceiver)
        } catch (_:Exception){}
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            Constants.REQUEST_BT_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    logcat("Required Permission is Enabled.", "d")
                    bluetoothSystemLauncher()
                } else {
                    logcat("Required Permission is Disabled.", "e")
                }
                return
            }
        }
    }
}