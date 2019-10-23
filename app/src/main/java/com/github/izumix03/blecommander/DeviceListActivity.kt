package com.github.izumix03.blecommander

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DeviceListActivity : AppCompatActivity(), AdapterView.OnItemClickListener {
    companion object {
        private const val REQUEST_ENABLE_BLUETOOTH = 1
        private const val REQUEST_ENABLE_LOCATION = 2
        private const val SCAN_MILL_SEC = 10_000L
        private const val TAG = "DeviceListActivity"

        const val EXTRAS_DEVICE_NAME = "DEVICE_NAME: "
        const val EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS: "
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val deviceListAdapter: DeviceListAdapter by lazy { DeviceListAdapter(this) }
    private val handler: Handler by lazy { Handler() }
    private var scanning: Boolean = false

    private val leScanCallback by lazy {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                runOnUiThread { deviceListAdapter.addDevice(result.device) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.d(TAG, "failed to scan")
                super.onScanFailed(errorCode)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        setResult(Activity.RESULT_CANCELED)

        findViewById<ListView>(R.id.device_list).apply {
            adapter = deviceListAdapter
            onItemClickListener = this@DeviceListActivity
        }

        bluetoothAdapter ?: run {
            Toast.makeText(this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val device = deviceListAdapter.getItem(position) ?: return

        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(EXTRAS_DEVICE_NAME, device.name)
            putExtra(EXTRAS_DEVICE_ADDRESS, device.address)
        })
        finish()
    }

    inner class DeviceListAdapter(activity: Activity) : BaseAdapter() {
        private val deviceList by lazy { mutableListOf<BluetoothDevice>() }
        private val inflater = activity.layoutInflater

        override fun getItem(position: Int): BluetoothDevice? {
            return deviceList[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return deviceList.size
        }

        fun clear() {
            deviceList.clear()
            notifyDataSetChanged()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {

            val view = convertView ?: inflater.inflate(R.layout.list_item_device, parent, false)

            val viewHolder: ViewHolder = convertView?.tag as ViewHolder? ?: ViewHolder(
                view.findViewById(R.id.text_view_device_name),
                view.findViewById(R.id.text_view_device_address)
            )

            view.tag = viewHolder

            val device = deviceList[position]
            viewHolder.deviceName.text =
                if (TextUtils.isEmpty(device.name)) resources.getString(R.string.unknown_device)
                else device.name

            viewHolder.deviceAddress.text = device.address.toString()

            return view
        }

        fun addDevice(device: BluetoothDevice?) {
            if (!deviceList.contains(device)) device?.let {
                deviceList.add(it)
                notifyDataSetChanged()
            }
        }

        inner class ViewHolder(var deviceName: TextView, var deviceAddress: TextView)
    }

    override fun onResume() {
        super.onResume()
        requestFeatures()
        startScan()
    }

    override fun onPause() {
        super.onPause()
        stopScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_ENABLE_BLUETOOTH -> {
                if (Activity.RESULT_CANCELED != resultCode) return
                Toast.makeText(this, R.string.bluetooth_is_not_working, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            REQUEST_ENABLE_LOCATION -> {
                if (Activity.RESULT_CANCELED != resultCode) return
                Toast.makeText(this, R.string.location_is_not_working, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val mn = menu ?: return super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.activity_device_list, mn)

        mn.findItem(R.id.menu_item_stop).isVisible = scanning
        mn.findItem(R.id.menu_item_scan).isVisible = !scanning

        if (scanning) mn.findItem(R.id.menu_item_progress)
            .setActionView(R.layout.action_bar_indeterminate_progress)
        else mn.findItem(R.id.menu_item_progress).actionView = null

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_item_scan -> startScan()
            R.id.menu_item_stop -> stopScan()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun requestFeatures() {
        requestBluetoothFeature()
        requestCoarseLocationFeature()
    }

    private fun requestCoarseLocationFeature() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_ENABLE_LOCATION
            )
        }
    }

    private fun requestBluetoothFeature() {
        if (bluetoothAdapter?.isEnabled == true) return
        startActivityForResult(
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
            REQUEST_ENABLE_BLUETOOTH
        )
    }

    private fun startScan() {
        deviceListAdapter.clear()

        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            Log.e(TAG, "failed to scan because bluetooth not working")
            requestFeatures()
            return
        }

        handler.postDelayed({
            scanning = false
            scanner.stopScan(leScanCallback)
        }, SCAN_MILL_SEC)

        scanning = true
        scanner.startScan(leScanCallback)
        invalidateOptionsMenu()
    }

    private fun stopScan() {
        handler.removeCallbacksAndMessages(null)
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            Log.e(TAG, "failed to scan because bluetooth not working")
            requestFeatures()
            return
        }
        scanning = false
        scanner.stopScan(leScanCallback)
        invalidateOptionsMenu()
    }
}
