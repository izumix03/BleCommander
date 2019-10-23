package com.github.izumix03.blecommander

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.izumix03.blecommander.DeviceListActivity.Companion.EXTRAS_DEVICE_ADDRESS
import com.github.izumix03.blecommander.DeviceListActivity.Companion.EXTRAS_DEVICE_NAME
import java.util.UUID

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private var serviceUUID: UUID? = null

    // button click
    override fun onClick(v: View?) {
        when (v?.id) {
            connectButton.id -> {
                connectButton.isEnabled = false
                connect()
                return
            }
            disconnectButton.id -> {
                disconnectButton.isEnabled = false
                disconnect()
                return
            }
            writeButton.id -> {
                disconnectButton.isEnabled = false
                connectButton.isEnabled = false
                writeCharacteristic()
                return
            }
        }
    }

    private fun writeCharacteristic() {
        val gatt = bluetoothGatt ?: return

        Log.d(TAG, "gatt: ${gatt.getService(serviceUUID).characteristics.map { it.uuid }}")

        val blechar = gatt.getService(serviceUUID)
            .getCharacteristic(gatt.getService(serviceUUID).characteristics.first().uuid)
        blechar.setValue("hello")
        gatt.writeCharacteristic(blechar)
    }

    private fun connect() {
        Log.d(TAG, "Connecting....")
        if (deviceAddress.isEmpty()) return

        bluetoothGatt ?: let {
            it.bluetoothGatt = bluetoothAdapter
                ?.getRemoteDevice(deviceAddress)
                ?.connectGatt(this, false, gattCallBack)
        }
    }

    private val gattCallBack by lazy {
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                Log.d(TAG, "status: $status, newState: $newState")
                if (status == 133) connect()
                if (status != BluetoothGatt.GATT_SUCCESS) return

                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> {
                        gatt?.discoverServices()
                        runOnUiThread {
                            disconnectButton.isEnabled = true
                        }
                    }
                    BluetoothGatt.STATE_DISCONNECTED -> {
                        bluetoothGatt?.connect()
                    }
                }
                super.onConnectionStateChange(gatt, status, newState)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "failed to service discovery status: $status")
                    return
                }
                serviceUUID = gatt?.services?.firstOrNull()?.uuid
                runOnUiThread {
                    writeButton.isEnabled = true
                }

                super.onServicesDiscovered(gatt, status)
            }
        }
    }

    private fun disconnect() {
        val gatt = bluetoothGatt ?: return

        gatt.close()
        bluetoothGatt = null

        connectButton.isEnabled = true
        disconnectButton.isEnabled = false
        writeButton.isEnabled = false
    }

    companion object {
        private const val TAG = "Main Activity"
        private const val REQUEST_ENABLE_BLUETOOTH = 1
        private const val REQUEST_CONNECT_DEVICE = 2
    }

    private var deviceAddress: String = ""
    private var bluetoothGatt: BluetoothGatt? = null

    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var writeButton: Button

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectButton = findViewById(R.id.button_connect)
        connectButton.setOnClickListener(this)

        disconnectButton = findViewById(R.id.button_disconnect)
        disconnectButton.setOnClickListener(this)

        writeButton = findViewById(R.id.button_write)
        writeButton.setOnClickListener(this)

        if (!hasBleFeature()) return

        if (!supportBluetoothAdapter()) return

        Log.d(TAG, "onCreate!!")
    }

    override fun onResume() {
        super.onResume()
        requestBluetoothFeature()

        connectButton.isEnabled = false
        disconnectButton.isEnabled = false
        writeButton.isEnabled = false

        if (deviceAddress.isNotEmpty()) connectButton.isEnabled = true

        connectButton.callOnClick()
    }

    override fun onPause() {
        super.onPause()
        disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.let {
            it.close()
            bluetoothGatt = null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "requestCode: $requestCode")
        when (requestCode) {
            REQUEST_ENABLE_BLUETOOTH -> {
                if (resultCode == Activity.RESULT_CANCELED) {
                    Toast.makeText(this, R.string.bluetooth_is_not_working, Toast.LENGTH_SHORT)
                        .show()
                    finish()
                    return
                }
            }
            REQUEST_CONNECT_DEVICE -> {
                Log.d(
                    TAG,
                    "data?.getStringExtra(EXTRAS_DEVICE_ADDRESS): ${data?.getStringExtra(
                        EXTRAS_DEVICE_ADDRESS
                    )}"
                )
                if (resultCode == Activity.RESULT_OK) {
                    deviceAddress = data?.getStringExtra(EXTRAS_DEVICE_ADDRESS) ?: ""
                    findViewById<TextView>(R.id.text_view_device_name).text =
                        data?.getStringExtra(EXTRAS_DEVICE_NAME)
                    findViewById<TextView>(R.id.text_view_device_address).text = deviceAddress
                } else {
                    findViewById<TextView>(R.id.text_view_device_name).text = ""
                    findViewById<TextView>(R.id.text_view_device_address).text = ""
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.activity_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "${item.itemId}")
        when (item.itemId) {
            R.id.menu_item_search ->
                startActivityForResult(
                    Intent(this, DeviceListActivity::class.java),
                    REQUEST_CONNECT_DEVICE
                ).let {
                    Log.d(TAG, "item searching")
                }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun requestBluetoothFeature() {
        if (bluetoothAdapter?.isEnabled == true) return
        startActivityForResult(
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
            REQUEST_ENABLE_BLUETOOTH
        )
    }

    /**
     * if cannot use bluetooth adapter, finish app
     */
    private fun supportBluetoothAdapter(): Boolean {
        bluetoothAdapter ?: run {
            Toast.makeText(this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT).show()
            finish()
            return false
        }
        return true
    }

    /**
     * if cannot use bluetooth, finish app
     */
    private fun hasBleFeature(): Boolean {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_is_not_supported, Toast.LENGTH_SHORT).show()
            finish()
            return false
        }
        return true
    }
}
