package ir.device.obd

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@SuppressLint("InlinedApi")
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
    private val listLog: MutableList<String> = mutableListOf()

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

        // --------> Set Initialize:
        btnBTDevice.setOnClickListener {
            lLogcat.visibility = View.GONE
            lBTDevice.visibility = View.VISIBLE
        }

        btnLogcat.setOnClickListener {
            lLogcat.visibility = View.VISIBLE
            lBTDevice.visibility = View.GONE
        }

        btnBluetooth.setOnClickListener {
            bluetoothSystemLauncher()
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
        logcat("request Permissions")
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

    private val bluetoothPowerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            logcat("bluetooth of Device is Enabled.", "d")
            bluetoothDiscoveringLauncher()
        } else if (it.resultCode == Activity.RESULT_CANCELED) {
            logcat("bluetooth of Device is Disabled.", "e")
        }
    }

    private val locationPowerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (isLocationEnabled) {
            logcat("location of Device is Enabled.", "d")
            bluetoothDiscoveringLauncher()
        } else {
            logcat("location of Device is Disabled.", "e")
        }
    }

    private fun logcat(text: String, mode: String = "i") {
        listLog.add("$mode : $text")
        when (mode) {
            "d" -> {
                Log.d("LOG", mode)
            }

            "e" -> {
                Log.e("LOG", mode)
            }

            else -> {
                Log.i("LOG", mode)
            }
        }
        val adapter: ArrayAdapter<String> = ArrayAdapter<String>(
            this,
            R.layout.item_log,
            listLog
        )
        lLog.adapter = adapter
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
                    logcat("required Permission is Enabled.", "d")
                    bluetoothSystemLauncher()
                } else {
                    logcat("required Permission is Disabled.", "e")
                }
                return
            }
        }
    }
}