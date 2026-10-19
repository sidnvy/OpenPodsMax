package dev.airpodsnative.companion

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper

/**
 * Single short, explicitly requested BLE color probe (no persistent scanning).
 *
 * Critical identity rule: never use a color result for another nearby AirPods.
 * On Android, AirPods advertisements can use rotating private addresses. When
 * the advertised address is not resolved to the exact bonded address, this
 * scanner returns "not attributable" instead of inferring which device it is.
 *
 * Does not change Bluetooth state, pairings, Identity Keys or headphone settings.
 */
class AirPodsColorProbe(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var stopAction: (() -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun readColorByte(
        bondedClassicAddress: String,
        onResult: (Int?) -> Unit
    ) {
        stop()
        val required = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        if (required.any { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            onResult(null)
            return
        }
        val scanner = BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner
        if (scanner == null) {
            onResult(null)
            return
        }
        val prefix = byteArrayOf(0x07, 0x19, 0x01, 0x2d, 0x20)
        val filter = ScanFilter.Builder().setManufacturerData(
            0x004c, prefix, byteArrayOf(-1, -1, -1, -1, -1)
        ).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
        var finished = false
        lateinit var callback: ScanCallback
        val timeout = Runnable {
            if (!finished) {
                finished = true
                try { scanner.stopScan(callback) } catch (_: Exception) {}
                stopAction = null
                onResult(null)
            }
        }
        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (finished) return
                // AAP/BR/EDR target address is the user's chosen exact bond.
                if (!result.device.address.equals(bondedClassicAddress, ignoreCase = true)) return
                val code = AirPodsMax2ColorFrame.parseRecord(result.scanRecord) ?: return
                finished = true
                handler.removeCallbacks(timeout)
                try { scanner.stopScan(this) } catch (_: Exception) {}
                stopAction = null
                onResult(code)
            }
            override fun onScanFailed(errorCode: Int) {
                if (finished) return
                finished = true
                handler.removeCallbacks(timeout)
                stopAction = null
                onResult(null)
            }
        }
        stopAction = {
            finished = true
            handler.removeCallbacks(timeout)
            try { scanner.stopScan(callback) } catch (_: Exception) {}
        }
        try {
            scanner.startScan(listOf(filter), settings, callback)
            handler.postDelayed(timeout, 9000)
        } catch (_: Exception) {
            stop()
            onResult(null)
        }
    }

    fun stop() {
        stopAction?.invoke()
        stopAction = null
    }
}
