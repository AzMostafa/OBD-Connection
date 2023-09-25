package ir.device.obd

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.lang.Math.pow
import kotlin.math.pow

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
    private val listPairedBluetooth: HashMap<String, Array<String>> = hashMapOf()
    private var listDiscoveredBluetooth: HashMap<String, Array<String>> = hashMapOf()

    private val bluetoothManager by lazy {
        applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter by lazy {
        bluetoothManager.adapter
    }
    private val bluetoothService by lazy {
        MBtService(context, bluetoothHandler)
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
        val btnSendECUReuest = findViewById<Button>(R.id.btn_SendECURequest)
        val lBTDevice = findViewById<LinearLayout>(R.id.lBluetoothDevice)
        val lLogcat = findViewById<LinearLayout>(R.id.lLogcat)
        val edtECURequest = findViewById<EditText>(R.id.edt_SendECURequest)
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

        btnSendECUReuest.setOnClickListener {
            if (bluetoothService.getState() == Constants.STATE_CONNECTED) {
                val request = edtECURequest.text.toString()
                setECURequest(request)
            } else {
                Toast.makeText(context, "Connect to OBD.", Toast.LENGTH_SHORT).show()
            }
        }

        lPaired.onItemClickListener = AdapterView.OnItemClickListener { _, view, _, _ ->
            val txtMacDevice = view.findViewById<TextView>(R.id.txt_MacAddress)
            val deviceMac = txtMacDevice.text.toString()
            if (!(deviceMac == "" || deviceMac == "null")) {
                bluetoothConnectingLauncher(deviceMac)
            } else {
                logcat("Selected MacAddress is empty or null!", "e")
            }
        }

        lDiscovered.onItemClickListener = AdapterView.OnItemClickListener { _, view, _, _ ->
            val txtMacDevice = view.findViewById<TextView>(R.id.txt_MacAddress)
            val deviceMac = txtMacDevice.text.toString()
            if (!(deviceMac == "" || deviceMac == "null")) {
                bluetoothConnectingLauncher(deviceMac)
            } else {
                logcat("Selected MacAddress is empty or null!", "e")
            }
        }
    }

    // ------------------------------------------- OBD -----------------------------------------//
    // --> ATZ       : reset all
    // --> ATDP      : Describe the current Protocol
    // --> ATAT0-1-2 : Adaptive Timing Off - Adaptive Timing Auto1 - Adaptive Timing Auto2
    // --> ATE0-1    : Echo Off - Echo On
    // --> ATSP0     : Set Protocol to Auto and save it
    // --> ATMA      : Monitor All
    // --> ATL1-0    : LineFeeds On - LineFeeds Off
    // --> ATH1-0    : Headers On - Headers Off
    // --> ATS1-0    : printing of Spaces On - printing of Spaces Off
    // --> ATAL      : Allow Long (>7 byte) messages
    // --> ATI       : Read ID
    // --> ATRD      : Read the stored data
    // --> ATSTFF    : Set time out to maximum
    // --> ATSTHH    : Set timeout to 4ms
    private val pids = arrayOf("A6", "5E")
    private val initCommands =
        arrayOf("ATZ", "ATL0", "ATE1", "ATH1", "ATAT1", "ATSTFF", "ATI", "ATDP", "ATSP0", "0100")
    private var initCommandIndex = 0
    private var initializeCommands = false
    private fun initializeCommands() {
        if (!initializeCommands) {
            setECURequest(initCommands[initCommandIndex])
            if (initCommandIndex == (initCommands.size - 1)) {
                initializeCommands = true
            }
            initCommandIndex++
        }
    }

    private fun setECURequest(request: String) {
        if (bluetoothService.getState() == Constants.STATE_CONNECTED) {
            try {
                if (request.isNotEmpty()) {
                    val textByte = request + "\r"
                    bluetoothService.write(textByte.toByteArray())
                    logcat("SetECURequest: $request")
                }
            } catch (e: Exception) {
                logcat("unable to send request to ECU! \n${e.message}", "e")
            }
        }
    }

    private fun cleanResponse(txt: String): String {
        var text = txt

        text = text.replace("null", "")
        text = text.replace("\\s", "")  // ---> removes all [ \t\n\x0B\f\r]
        text = text.replace(">", "")
        text = text.replace("SEARCHING...", "")
        text = text.replace("ATZ", "")
        text = text.replace("ATI", "")
        text = text.replace("atz", "")
        text = text.replace("ati", "")
        text = text.replace("ATDP", "")
        text = text.replace("atdp", "")
        text = text.replace("ATRV", "")
        text = text.replace("atrv", "")

        return text
    }

    private fun checkResponse(response: String) {
        var dataReceived: String? = response
        var pid: Int? = 0
        var a: Double? = 0.0
        var b: Double? = 0.0
        var c: Double? = 0.0
        var d: Double? = 0.0
        try {
            logcat("Check Response")
            if (dataReceived != null && dataReceived.matches("^[0-9A-F]+$".toRegex())) {
                dataReceived = dataReceived.trim { it <= ' ' }
                val index = dataReceived.indexOf("41")
                if (index != -1) {
                    val res: String = dataReceived.substring(index, dataReceived.length)
                    if (res.substring(0, 2) == "41") {
                        pid = if (res.length <= 4) {
                            res.substring(2, 4).toInt(16)
                        } else {
                            null
                        }

                        a = if (res.length <= 6) {
                            res.substring(4, 6).toInt(16).toDouble()
                        } else {
                            null
                        }

                        b = if (res.length <= 8) {
                            res.substring(6, 8).toInt(16).toDouble()
                        } else {
                            null
                        }

                        c = if (res.length <= 10) {
                            res.substring(8, 10).toInt(16).toDouble()
                        } else {
                            null
                        }

                        d = if (res.length <= 12) {
                            res.substring(10, 12).toInt(16).toDouble()
                        } else {
                            null
                        }

                        logcat("Response of ECU is PID=$pid, A=$a, B=$b, C=$c, D=$d")

                        calculateResponse(pid, a, b, c, d)
                    }
                } else {
                    logcat("Response of ECU is not Valid [41]!", "e")
                }
            }
        } catch (e: Exception) {
            logcat("unable to check response of ECU! \n${e.message}", "e")
        }
    }

    private fun calculateResponse(pid: Int?, a: Double?, b: Double?, c: Double?, d: Double?) {
        logcat("Calculate Response")
        var errorParam = false
        var value: Double = 0.0
        when (pid) {
            12 -> { // ---------> PID(0C): RPM [value = ((A*256)+B)/4] [rpm]
                if (a != null && b != null) {
                    value = (((a * 256) + b) / 4)
                } else {
                    errorParam = true
                }
            }

            94 -> { // ---------> PID(5E): Engine fuel rate [value = ((A*256)+B)/20] [L/h]
                if (a != null && b != null) {
                    value = (((a * 256) + b) / 20)
                } else {
                    errorParam = true
                }
            }

            166 -> { // --------> PID(A6): Odometer [value = ((A*(2^24))+(B*(2^16))+(C*(2^8))+D)/4] [Km]
                if (a != null && b != null && c != null && d != null) {
                    value = ((a * 2.0.pow(24.0)) + (b * 2.0.pow(16.0)) + (c * 2.0.pow(8.0)) + d) / 4
                } else {
                    errorParam = true
                }
            }

            else -> {
                logcat("unable to calculation response of ECU [PID=$pid]!", "e")
            }
        }

        if (errorParam) {
            logcat(
                "unable to calculation response of ECU with this params [PID=$pid, A=$a, B=$b, C=$c, D=$d]!",
                "e"
            )
        }
    }

    // ------------------------------------- Bluetooth System -----------------------------------//
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

                    val btC: BluetoothClass = device.bluetoothClass
                    val deviceClass =
                        MBtService.BluetoothDeviceType.findByValue(btC.deviceClass).toString()
                    val arrayDetail = arrayOf(deviceMac, deviceClass)
                    listPairedBluetooth[deviceName] = arrayDetail
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

    private fun bluetoothConnectingLauncher(deviceMac: String) {
        if (bluetoothService.getState() == Constants.STATE_NONE || bluetoothService.getState() == Constants.STATE_LISTEN) {
            bluetoothService.start()

            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceMac)
            bluetoothService.connect(device)
        } else {
            Toast.makeText(context, "Connection Process is Running.", Toast.LENGTH_SHORT).show()
        }
    }

    private var bluetoothHandler = Handler(Looper.getMainLooper()) {
        when (it.what) {
            Constants.STATE_LOG -> {
                logcat(it.data.getString("TEXT").toString())
            }

            Constants.STATE_READ -> {
                val response = it.data.getString("TEXT").toString()
                val cleanRes = cleanResponse(response)
                logcat("Response: $cleanRes")

                if (!initializeCommands) {
                    initializeCommands()
                } else {
                    if (!(cleanRes.contains("NODATA") || cleanRes.contains("ERROR"))) {
                        checkResponse(cleanRes)
                    }
                }
            }

            Constants.STATE_CONNECT_LOST -> {
                Toast.makeText(
                    context,
                    "Your Connection is lost! Connect again.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            Constants.STATE_CONNECT_FAILED -> {
                Toast.makeText(
                    context,
                    "Connecting has Failed! Try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            Constants.STATE_CONNECTED -> {
                logcat("Bluetooth Device is Connected :)", "d")
                initializeCommands()
            }
        }
        true
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

                            val btC: BluetoothClass = device.bluetoothClass
                            val deviceClass =
                                MBtService.BluetoothDeviceType.findByValue(btC.deviceClass)
                                    .toString()
                            val arrayDetail = arrayOf(deviceMac, deviceClass)
                            logcat(
                                "Bluetooth Discovering Device $deviceName | $deviceMac | $deviceClass",
                                "d"
                            )
                            listDiscoveredBluetooth[deviceName] = arrayDetail

                            val adapter = AdapterListViewDiscovered(listDiscoveredBluetooth)
                            lDiscovered.adapter = adapter
                            lDiscovered.itemsCanFocus = true
                        } else {
                            logcat("Bluetooth Discovering device is null!", "e")
                        }
                    } catch (e: Exception) {

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

    inner class AdapterListViewPaired(dataInput: HashMap<String, Array<String>>) :
        ArrayAdapter<String>(context, R.layout.item_bluetooth, dataInput.keys.toTypedArray()) {

        private var dataInput: HashMap<String, Array<String>>

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
            val txtClass = rowView.findViewById<TextView>(R.id.txt_class)

            try {
                val deviceName = dataInput.keys.elementAt(position)
                val deviceDetail = dataInput.values.elementAt(position)
                if (deviceDetail[0].isNotEmpty()) {
                    txtName.text = deviceName
                    txtMac.text = deviceDetail[0]
                    txtClass.text = deviceDetail[1]
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

    inner class AdapterListViewDiscovered(dataInput: HashMap<String, Array<String>>) :
        ArrayAdapter<String>(context, R.layout.item_bluetooth, dataInput.keys.toTypedArray()) {

        private var dataInput: HashMap<String, Array<String>>

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
            val txtClass = rowView.findViewById<TextView>(R.id.txt_class)

            try {
                val deviceName = dataInput.keys.elementAt(position)
                val deviceDetail = dataInput.values.elementAt(position)
                if (deviceDetail[0].isNotEmpty()) {
                    txtName.text = deviceName
                    txtMac.text = deviceDetail[0]
                    txtClass.text = deviceDetail[1]
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

    // ------------------------------------------ Others ----------------------------------------//

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
            bluetoothService.stop()
            unregisterReceiver(bluetoothFindReceiver)
        } catch (_: Exception) {
        }
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