package com.example.truespotrtlsandroid

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.example.truespotrtlsandroid.beacon.BeaconRegion
import com.example.truespotrtlsandroid.models.Beacon
import com.example.truespotrtlsandroid.models.BeaconType
import com.example.truespotrtlsandroid.models.IBeacon
import com.google.gson.Gson
import android.bluetooth.le.ScanFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler

import android.os.ParcelUuid
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.example.truespotrtlsandroid.beacon.TSBeaconSighting
import com.example.truespotrtlsandroid.data.CarsNearMeRequestBody
import com.example.truespotrtlsandroid.data.GetBeacon
import java.util.*
import kotlin.collections.ArrayList


class BeaconManagers(context: Context, activity: Activity) : ScanCallback() {

    var btManager : BluetoothManager? = null
    var btAdapter : BluetoothAdapter? = null
    var btScanner : BluetoothLeScanner? = null
    var location : Location? = null
    var scanning = false
    var mActivity : Activity? = null
    var mContext : Context? = null
    var beaconUpdatedList : MutableList<TSBeaconSighting>? = ArrayList()
    init {
        mContext = context
        mActivity = activity
        btManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = btManager!!.adapter
        btScanner = btAdapter?.bluetoothLeScanner
        if (btAdapter != null && !btAdapter!!.isEnabled)
        {

        }
        scanning = false

    }

    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        super.onScanResult(callbackType, result)
        if (!result!!.device.name.isNullOrEmpty()){
            val beaconType: BeaconType = result.scanRecord?.bytes?.let { getBeaconType(it) } ?: return
            val beacon: Beacon? = buildBeacon(beaconType, result.device, result.rssi, result.scanRecord?.bytes)
            val beaconList : MutableList<TSBeaconSighting> = ArrayList()
            if(beacon != null)
            {
                for(results in arrayOf(beacon))
                {
                    beaconList.add(TSBeaconSighting(results.device.name,results.rssi,results.device.address,IBeacon(results).uuid,IBeacon(results).major,IBeacon(results).minor))
                }
            }
            if(!beaconList.isNullOrEmpty())
            {
                for(s in beaconList)
                {
                    if(!beaconUpdatedList!!.contains(s))
                    {
                        beaconUpdatedList!!.add(s)
                        TSBeaconManagers.initializeBeaconObserver(getBeaconSightings(),getLastKnownLocation()!!)
                        //break
                    }
                }
            }
           Log.i("Log","beaconList-->${Gson().toJson(beaconUpdatedList)}")
           Log.i("Log", "onScanResult--->${IBeacon(beacon).major},${IBeacon(beacon).minor}")
        }
    }

    override fun onBatchScanResults(results: MutableList<ScanResult>?) {
        super.onBatchScanResults(results)
        Log.i("Log", "onBatchScanResults")
    }

    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
        Log.i("Log", "onScanFailed")
    }

    fun startMonitoring() {
           //val filters: ArrayList<ScanFilter> = ArrayList<ScanFilter>()
        //val uuid = UUID.randomUUID()
        /* val uuid = "U5C38DBDE567C4CCAB1DA40A8AD465656"
         val filter: ScanFilter = ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(uuid))).build()
         filters.add(filter)*/
        /*val uuid = "U5C38DBDE567C4CCAB1DA40A8AD465656"
        val yanService = ParcelUuid(UUID.fromString(uuid))
        val filters: ArrayList<ScanFilter> = ArrayList<ScanFilter>()
        filters.add(ScanFilter.Builder().setServiceUuid(yanService).build())*/
        val filters: ArrayList<ScanFilter> = ArrayList<ScanFilter>()
      //  filters.add(ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString("5C38DBDE-567C-4CCA-B1DA-40A8AD465656"))).build())
        val settings: ScanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()


        btScanner = btAdapter?.getBluetoothLeScanner()
        btScanner?.startScan(filters, settings, this)
        scanning = true
       /* val handle = Handler()
        handle.postDelayed(Runnable { stopMonitoring() }, 10000)
*/


    }

    fun stopMonitoring() {
        btScanner!!.stopScan(this)
        scanning = false
        Log.i("Log", "stopScan")
    }

    private fun buildBeacon(
        beaconType: BeaconType?,
        device: BluetoothDevice?,
        rssi: Int,
        scanRecord: ByteArray?
    ): Beacon? {
        val beacon = Beacon()
        beacon.rssi = rssi
        beacon.scanRecord = scanRecord
        beacon.device = device
        if (beaconType != null) {
            beacon.beaconType = beaconType
            when (beaconType) {
                BeaconType.I_BEACON -> {
                    return IBeacon(beacon)
                }

            }
        }
        return null
    }
   private fun getBeaconType(scanRecord: ByteArray): BeaconType? {
        val hexScanRecord = Beacon.bytesToHex(scanRecord)
        return if (this.containsString(hexScanRecord, BeaconType.I_BEACON.stringType)) {
            BeaconType.I_BEACON
        } else if (this.containsString(hexScanRecord, BeaconType.EDDYSTONE_TLM.stringType)) {
            BeaconType.EDDYSTONE_TLM
        } else {
            if (this.containsString(
                    hexScanRecord,
                    BeaconType.EDDYSTONE_UID.stringType
                )
            ) BeaconType.EDDYSTONE_UID else null
        }
    }

   private fun containsString(str1: String, str2: String): Boolean {
        var str1 = str1
        var str2 = str2
        str1 = str1.replace(" ", "").toLowerCase()
        str2 = str2.replace(" ", "").toLowerCase()
        str1 = str1.replace("-", "")
        str2 = str2.replace("-", "")
        return str1.contains(str2)
    }

    fun getBeaconSightings() : MutableList<TSBeaconSighting> {

        val result: MutableList<TSBeaconSighting> = ArrayList()
        val values: Collection<TSBeaconSighting> = beaconUpdatedList!!
        if (values.isNotEmpty()) {
            for (s in values) {
                if (s.getBeaconId() != null && !TextUtils.isEmpty(s.getBeaconId())) {
                    result.add(TSBeaconSighting(s))
                }
            }
        }
        return result
    }


    private fun getLastKnownLocation(): Location? {

        if (checkRequiredPermission(Manifest.permission.ACCESS_FINE_LOCATION,mContext!!)!!) {
                val lm = mContext!!.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val providers = lm.getProviders(true)
                for (provider in providers) {
                    location = lm.getLastKnownLocation(provider!!)
                    if (location != null) {
                        break
                    }
                }
            }
        return location
    }

    fun checkRequiredPermission(permission: String, context: Context): Boolean? {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }


}