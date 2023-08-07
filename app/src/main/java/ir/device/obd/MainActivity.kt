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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat


class MainActivity : AppCompatActivity() {

    private lateinit var context: Context

    private var bluetoothAdapter: BluetoothAdapter? = null

    private val PERMISSION_REQUEST_BLU = 102

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --------> Set Activity Context:
        this.context = this

        // --------> Set up Bluetooth:
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter != null) {
            Log.i("LOG", "bluetooth is support in this Device!")
            Log.i("LOG", "bluetooth is not Enabled")
            var hasPermission = false
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.i("LOG", "bluetooth is support in this Device!")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_REQUEST_BLU
                    )
                } else {
                    hasPermission = true
                }
            } else {
                hasPermission = true
            }

            if (hasPermission) {
                if (!bluetoothAdapter!!.isEnabled) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    resultLauncher.launch(enableBtIntent)
                } else {
                    discoverBluetoothDevice()
                }
            }

        } else {
            Log.i("LOG", "bluetooth is not support in this Device!")
        }
    }

    private var resultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            discoverBluetoothDevice()
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(context, "بلوتوث دستگاه خاموش می باشد", Toast.LENGTH_SHORT).show()
        }
    }


    @SuppressLint("MissingPermission")
    private fun discoverBluetoothDevice() {
        pairBluetoothDevice()

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        this.registerReceiver(mReceiver, filter)
        bluetoothAdapter?.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun pairBluetoothDevice() {
        Log.i("LOG", "findBluetoothDevice")
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        pairedDevices?.forEach { device ->
            val deviceName = device.name
            val deviceHardwareAddress = device.address // MAC address

            Log.e("LOG", "deviceName: $deviceName")
            Log.e("LOG", "deviceHardwareAddress: $deviceHardwareAddress")
        }
    }

    private val mReceiver = object : BroadcastReceiver() {

        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            Log.i("LOG", "BroadcastReceiver")
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null){
                        val deviceName = device.name
                        val deviceHardwareAddress = device.address // MAC address

                        Log.d("LOG", "deviceName: $deviceName")
                        Log.d("LOG", "deviceHardwareAddress: $deviceHardwareAddress")
                    } else {
                        Log.e("LOG", "Error BroadcastReceiver")
                    }
                }
            }
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i("LOG", "onRequestPermissionsResult")
        if (requestCode == PERMISSION_REQUEST_BLU) {
            Log.i("LOG", "Done")
            if (!bluetoothAdapter!!.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                resultLauncher.launch(enableBtIntent)
            } else {
                discoverBluetoothDevice()
            }
            return
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mReceiver)
    }
}