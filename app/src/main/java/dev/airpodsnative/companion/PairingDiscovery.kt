package dev.airpodsnative.companion

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Foreground classic Bluetooth discovery for initial AirPods Max 2 pairing.
 *
 * CAPod's case-open overlay is a separate post-connection/case event, not
 * Google's certified Fast Pair setup. This uses Android public startDiscovery()
 * and createBond(); the platform owns pairing confirmation and bond state.
 *
 * No persistent background scanning, automatic pairing, root or ADB privilege.
 */
class PairingDiscovery(private val context: Context) {
    private val tag = "AirPodsPairing"
    private val handler = Handler(Looper.getMainLooper())
    private val btAdapter: BluetoothAdapter? get() = BluetoothAdapter.getDefaultAdapter()
    private var receiver: BroadcastReceiver? = null
    private var timeout: Runnable? = null
    private var events: ((Event) -> Unit)? = null
    private var candidateAddresses = mutableSetOf<String>()
    private var chosenAddress: String? = null

    sealed interface Event {
        data object Searching : Event
        data class Found(val name: String, val address: String) : Event
        data object NothingFound : Event
        data object PairingRequested : Event
        data class Paired(val address: String) : Event
        data class Failed(val reason: String) : Event
    }

    private fun hasPermission(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(onEvent: (Event) -> Unit) {
        stop()
        events = onEvent
        candidateAddresses.clear()
        chosenAddress = null
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN) ||
            !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            onEvent(Event.Failed("Nearby devices access is required"))
            return
        }
        val adapter = btAdapter
        if (adapter == null || !adapter.isEnabled) {
            onEvent(Event.Failed("Turn on Bluetooth to discover AirPods"))
            return
        }
        val broadcast = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= 33)
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (device == null) return
                        val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                            ?: runCatching { device.name }.getOrNull()
                            ?: return
                        if (!name.contains("AirPods Max", ignoreCase = true)) return
                        val address = device.address
                        if (device.bondState != BluetoothDevice.BOND_NONE) return
                        if (!candidateAddresses.add(address)) return
                        onEvent(Event.Found(name, address))
                        Log.i(tag, "DISCOVERED_MAX_NAMED_CLASSIC_DEVICE (model not yet verified)")
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        if (chosenAddress == null && candidateAddresses.isEmpty()) {
                            onEvent(Event.NothingFound)
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device: BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= 33)
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (device?.address != chosenAddress) return
                        when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)) {
                            BluetoothDevice.BOND_BONDED -> {
                                val address = chosenAddress!!
                                onEvent(Event.Paired(address))
                                Log.i(tag, "PAIRING_COMPLETED_FOR_EXPLICITLY_CHOSEN_DEVICE")
                                stop()
                            }
                            BluetoothDevice.BOND_NONE -> {
                                onEvent(Event.Failed("Pairing was cancelled or unsuccessful"))
                                chosenAddress = null
                            }
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                // Bluetooth broadcasts can originate from a privileged non-system UID.
                // Android requires EXPORTED for those sender identities.
                context.registerReceiver(broadcast, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(broadcast, filter)
            }
            receiver = broadcast
            // A bounded one-shot scan; do not keep draining battery in background.
            timeout = Runnable {
                try { adapter.cancelDiscovery() } catch (_: Exception) {}
                if (chosenAddress == null && candidateAddresses.isEmpty()) onEvent(Event.NothingFound)
            }.also { handler.postDelayed(it, 18000) }
            if (!adapter.startDiscovery()) {
                onEvent(Event.Failed("Bluetooth discovery could not start"))
                stop()
            } else {
                onEvent(Event.Searching)
            }
        } catch (ex: Exception) {
            Log.w(tag, "Discovery unavailable", ex)
            stop()
            onEvent(Event.Failed("Bluetooth discovery unavailable"))
        }
    }

    /** Can only pair a visible, explicitly selected candidate; never auto-bond. */
    @SuppressLint("MissingPermission")
    fun pair(address: String) {
        val listener = events ?: return
        val adapter = btAdapter ?: return
        if (address !in candidateAddresses || !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            listener(Event.Failed("Select a nearby discoverable headset"))
            return
        }
        val device = try { adapter.getRemoteDevice(address) } catch (_: Exception) {
            listener(Event.Failed("Invalid Bluetooth device"))
            return
        }
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            listener(Event.Paired(address))
            stop()
            return
        }
        chosenAddress = address
        try {
            adapter.cancelDiscovery()
            timeout?.let { handler.removeCallbacks(it) }
            listener(Event.PairingRequested)
            if (!device.createBond()) {
                chosenAddress = null
                listener(Event.Failed("Android couldn't start the Bluetooth pairing"))
            }
        } catch (ex: Exception) {
            Log.w(tag, "Pairing request rejected", ex)
            chosenAddress = null
            listener(Event.Failed("Android rejected the pairing request"))
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        timeout?.let { handler.removeCallbacks(it) }
        timeout = null
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        if (hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            try { btAdapter?.cancelDiscovery() } catch (_: Exception) {}
        }
        events = null
        chosenAddress = null
    }
}
