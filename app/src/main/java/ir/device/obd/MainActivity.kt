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
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var context: Context

    private lateinit var btnSwitchPower: Button
    private lateinit var btnFindDevice: Button
    private lateinit var txtBluetoothPower: TextView
    private lateinit var txtPairedDeviceEmpty: TextView
    private lateinit var lPairedDevice: ListView
    private lateinit var lDiscoveredDevice: ListView

    private var nextAction: String? = null
    private var isGrant = false
    private var hasPermission = false
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var listPairedDeviceName = mutableListOf<String>()
    private var listPairedDeviceMac = mutableListOf<String>()
    private var listDiscoveredDeviceName = mutableListOf<String>()
    private var listDiscoveredDeviceMac = mutableListOf<String>()
    private val PERMISSION_REQUEST_BLU = 102
    private val PERMISSION_REQUEST_ACCESS_LOCATION = 101
    private lateinit var bluetoothManager: BluetoothManager

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --------> Set Activity Context:
        this.context = this

        // --------> Set IDs:
        btnSwitchPower = findViewById(R.id.btn_Switch)
        btnFindDevice = findViewById(R.id.btn_find)
        txtBluetoothPower = findViewById(R.id.txt_BluetoothStatus)
        txtPairedDeviceEmpty = findViewById(R.id.txt_pairedDeviceEmpty)
        lPairedDevice = findViewById(R.id.lPairedBluetooth)
        lDiscoveredDevice = findViewById(R.id.lDiscoveredBluetooth)

        btnSwitchPower.setOnClickListener {
            if (hasPermission) {
                if (!bluetoothAdapter!!.isEnabled) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    bluetoothSwitcher.launch(enableBtIntent)
                } else {
                    Log.i("LOG", "bluetoothPower isEnable")
                    Toast.makeText(context, "Bluetooth is Turn ON", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("LOG", "bluetoothPermission isNowDone")
                nextAction = "SwitchPower"
                getBluetoothPermission()
            }
        }

        btnFindDevice.setOnClickListener {
            if (hasPermission) {
                clearLists()
                setBluetoothDevicePaired()
                setBluetoothDeviceDiscovered()
            } else {
                nextAction = "FindDevice"
                Log.e("LOG", "bluetoothPermission isNowDone")
                getBluetoothPermission()
            }
        }

        // --------> Set up Bluetooth:
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter != null) {
            Log.i("LOG", "bluetooth is support in this Device!")
            if (bluetoothAdapter!!.isEnabled) {
                txtBluetoothPower.text = "Bluetooth is Turn On."
                txtBluetoothPower.setTextColor(ContextCompat.getColor(context, R.color.A1))
                Log.i("LOG", "bluetoothPower isEnabled")
            }

            val filter = IntentFilter()
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            filter.addAction(BluetoothDevice.ACTION_FOUND)
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            registerReceiver(bluetoothReceiver, filter)
        } else {
            Log.e("LOG", "bluetooth is not support in this Device!")
        }
    }

    private fun clearLists() {
        listPairedDeviceName = mutableListOf()
        listPairedDeviceMac = mutableListOf()
        listDiscoveredDeviceMac = mutableListOf()
        listDiscoveredDeviceName = mutableListOf()

        txtPairedDeviceEmpty.visibility = View.INVISIBLE
    }

    private fun setNextAction() {
        if (isGrant && nextAction != null) {
            when (nextAction) {
                "SwitchPower" -> {
                    if (!bluetoothAdapter!!.isEnabled) {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        bluetoothSwitcher.launch(enableBtIntent)
                    } else {
                        Log.i("LOG", "bluetoothPower isEnable")
                        Toast.makeText(context, "Bluetooth is Turn ON", Toast.LENGTH_SHORT).show()
                    }
                }

                "FindDevice" -> {
                    setBluetoothDevicePaired()
                    setBluetoothDeviceDiscovered()
                }
            }
            nextAction = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun setBluetoothDeviceDiscovered() {
        Log.i("LOG", "onDiscoverBluetoothDevice")
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
            bluetoothAdapter?.startDiscovery()
        } else {
            bluetoothAdapter?.startDiscovery()
        }
        Log.i("LOG", "bluetoothAdapter: ${bluetoothAdapter?.startDiscovery()}")
        Log.i("LOG", "bluetoothAdapter state: ${bluetoothAdapter?.state}")
    }

    @SuppressLint("MissingPermission")
    private fun setBluetoothDevicePaired() {
        Log.i("LOG", "onPairBluetoothDevice")
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        pairedDevices?.forEach { device ->
            val deviceName = device.name
            val deviceUUID = device.uuids
            val deviceHardwareAddress = device.address // MAC address

            Log.d(
                "LOG",
                "----pair---- bluetoothPairedDevice: [deviceName=$deviceName / deviceHardwareAddress: $deviceHardwareAddress / deviceUUID:$deviceUUID]"
            )
            listPairedDeviceName.add(deviceName)
            listPairedDeviceMac.add(deviceHardwareAddress)
        }

        if (listPairedDeviceName.size == 0) {
            txtPairedDeviceEmpty.visibility = View.VISIBLE
        } else {
            val adapter = AdapterPairedDeviceBluetooth(listPairedDeviceName)
            lPairedDevice.adapter = adapter
            lPairedDevice.itemsCanFocus = true
        }
    }

    private fun getBluetoothPermission() {
        Log.i("LOG", "onBluetoothPermission")
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ), PERMISSION_REQUEST_ACCESS_LOCATION
            )
        } else if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ),
                    PERMISSION_REQUEST_BLU
                )
            } else {
                Log.d("LOG", "bluetoothPermission isDone")
                hasPermission = true
                isGrant = true
            }
        } else {
            Log.d("LOG", "bluetoothPermission isDone")
            hasPermission = true
            isGrant = true
            setNextAction()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {

        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            Log.i("LOG", "onBroadcastReceiver")
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
                        val deviceUUID = device.uuids
                        val deviceHardwareAddress = device.address // MAC address

                        if (!device.name.isNullOrEmpty()) {
                            if (device.name == "YSW10") {
//                                AcceptThread().run()
//                                ConnectThread(device).run()
                            }
                        }
                        when (device.bondState) {
                            BluetoothDevice.BOND_NONE -> {
                                Log.d("LOG", "${device.name} bond none")
                            }

                            BluetoothDevice.BOND_BONDING -> {
                                Log.d("LOG", "${device.name} bond BONDING")
                            }

                            BluetoothDevice.BOND_BONDED -> {
                                Log.d("LOG", "${device.name} bond BONDED")
                            }
                        }
                        Log.d(
                            "LOG",
                            "--discover-- bluetoothDiscoveredDevice: [deviceName=$deviceName / deviceHardwareAddress: $deviceHardwareAddress / deviceUUID:$deviceUUID]"
                        )

                        listDiscoveredDeviceName.add(deviceName)
                        listDiscoveredDeviceMac.add(deviceHardwareAddress)

                        val adapter = AdapterDiscoveredDeviceBluetooth(listDiscoveredDeviceName)
                        lDiscoveredDevice.adapter = adapter
                        lDiscoveredDevice.itemsCanFocus = true

                    } else {
                        Log.e("LOG", "bluetoothDiscovering Error")
                    }
                }
            }
        }
    }

    private var bluetoothSwitcher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {

            txtBluetoothPower.text = "Bluetooth is Turn On."
            txtBluetoothPower.setTextColor(ContextCompat.getColor(context, R.color.A1))
            Log.d("LOG", "bluetoothPower isEnabled")
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            Log.e("LOG", "bluetoothPower isCanceled")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i("LOG", "onRequestPermissionsResult")
        when (requestCode) {
            PERMISSION_REQUEST_ACCESS_LOCATION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_SCAN,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ),
                                PERMISSION_REQUEST_BLU
                            )
                        } else {
                            Log.d("LOG", "bluetoothPermission isDone")
                            hasPermission = true
                            isGrant = true
                        }
                    } else {
                        Log.d("LOG", "bluetoothPermission isDone")
                        hasPermission = true
                        isGrant = true
                    }
                } else {
                    Log.e("LOG", "bluetoothPermission isCanceled")
                }
            }

            PERMISSION_REQUEST_BLU -> {
                Log.d("LOG", "bluetoothPermission isDone")
                hasPermission = true
                isGrant = true
            }
        }
        setNextAction()
        return
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(bluetoothReceiver)
            Log.i("LOG", "bluetoothReceiver isDestroyed")
        } catch (_: Exception) {
            Log.e("LOG", "bluetoothReceiver isNotDestroyed")
        }
    }

    inner class AdapterPairedDeviceBluetooth(dataInput: List<String>) :
        ArrayAdapter<String?>(context, R.layout.listview_items, dataInput) {

        @SuppressLint("ViewHolder", "InflateParams")
        override fun getView(position: Int, view: View?, parent: ViewGroup): View {

            val activity = context as Activity
            val inflater = activity.layoutInflater
            val rowView: View = inflater.inflate(R.layout.listview_items, null, true)

            val txtName = rowView.findViewById<TextView>(R.id.txt_Name)
            val txtMac = rowView.findViewById<TextView>(R.id.txt_MacAddress)

            try {
                txtName.text = listPairedDeviceName[position]
                txtMac.text = listPairedDeviceMac[position]
            } catch (e: Exception) {
                Log.e("LOG", "bluetoothShowItem listPairedDeviceMac Error: ${e.message}")
            }

            return rowView
        }
    }

    inner class AdapterDiscoveredDeviceBluetooth(dataInput: List<String>) :
        ArrayAdapter<String?>(context, R.layout.listview_items, dataInput) {

        @SuppressLint("ViewHolder", "InflateParams")
        override fun getView(position: Int, view: View?, parent: ViewGroup): View {

            val activity = context as Activity
            val inflater = activity.layoutInflater
            val rowView: View = inflater.inflate(R.layout.listview_items, null, true)

            val txtName = rowView.findViewById<TextView>(R.id.txt_Name)
            val txtMac = rowView.findViewById<TextView>(R.id.txt_MacAddress)

            try {
                txtName.text = listDiscoveredDeviceName[position]
                txtMac.text = listDiscoveredDeviceMac[position]
            } catch (e: Exception) {
                Log.e("LOG", "bluetoothShowItem listDiscoveredDeviceMac Error: ${e.message}")
            }

            return rowView
        }
    }
}