package dev.airpodsnative.companion

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import kotlin.math.roundToInt

/**
 * Opt-in BLE proximity signal watcher. Not Google Fast Pair.
 *
 * Notifies only for the AirPods Max 2 Apple manufacturer model 0x2D20
 * detected in public BLE frames. That indicates nearby model, NOT
 * necessarily discoverable pairing mode and NOT authenticated identity.
 * The human must initiate pairing from the app and confirm with Android.
 *
 * Starts only from foreground, with a persistent notification. No root,
 * ADB, background activity launching or auto-bond. Overlay is optional
 * and requires explicit SYSTEM_ALERT_WINDOW consent.
 */
class NearbyPairingWatchService : Service() {
    companion object {
        const val ACTION_START = "dev.airpodsnative.companion.action.NEARBY_START"
        const val ACTION_STOP = "dev.airpodsnative.companion.action.NEARBY_STOP"
        const val ACTION_OPEN_PAIRING = "dev.airpodsnative.companion.action.OPEN_PAIRING"
        const val ACTION_DEBUG_PREVIEW_OVERLAY = "dev.airpodsnative.companion.action.DEBUG_PREVIEW_OVERLAY"
        private const val CHANNEL_RUNNING = "airpods_nearby_running"
        private const val CHANNEL_DETECTED = "airpods_nearby_detected"
        private const val NOTIFICATION_RUNNING = 5191
        private const val NOTIFICATION_DETECTED = 5192
        private const val MIN_ALERT_INTERVAL_MS = 120_000L
        private val MAX2_PREFIX = byteArrayOf(0x07,0x19,0x01,0x2d,0x20)
    }
    private var callback: ScanCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastAlertTime = -MIN_ALERT_INTERVAL_MS
    private var overlay: View? = null
    private var overlayArtwork: ImageView? = null
    private val notificationManager get() = getSystemService(NotificationManager::class.java)
    private val windowManager get() = getSystemService(WindowManager::class.java)
    private val bluetooth get() = BluetoothAdapter.getDefaultAdapter()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING,"AirPods nearby detection",NotificationManager.IMPORTANCE_LOW))
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_DETECTED,"Nearby AirPods Max 2",NotificationManager.IMPORTANCE_HIGH))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DEBUG_PREVIEW_OVERLAY -> {
                // Non-exported, debug-build-only visual test. No Bluetooth scan,
                // pairing, notification or persistent foreground watcher.
                val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                if (debuggable && Settings.canDrawOverlays(this)) {
                    showOptionalOverlay(debugPreview = true)
                    handler.postDelayed({ stopSelf() }, 20000)
                } else stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                getSharedPreferences("selected_airpods", 0).edit()
                    .putBoolean("nearby_watcher_requested", false).apply()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val launch = PendingIntent.getActivity(
                    this, 1, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val stop = PendingIntent.getService(
                    this, 2, Intent(this, NearbyPairingWatchService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val n = Notification.Builder(this, CHANNEL_RUNNING)
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setContentTitle("AirPods Max 2 nearby detection")
                    .setContentText("On · Tap to open OpenPodsMax")
                    .setContentIntent(launch)
                    .addAction(Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,"Stop",stop).build())
                    .setOngoing(true).build()
                try {
                    if (Build.VERSION.SDK_INT >= 34) {
                        startForeground(
                            NOTIFICATION_RUNNING, n,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                        )
                    } else startForeground(NOTIFICATION_RUNNING,n)
                } catch (_: Exception) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startWatching()
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startWatching() {
        stopScan()
        val scanner = bluetooth?.bluetoothLeScanner ?: run { stopSelf(); return }
        val filter = ScanFilter.Builder().setManufacturerData(
            0x004c, MAX2_PREFIX, ByteArray(MAX2_PREFIX.size) { -1 }).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val raw = result.scanRecord?.getManufacturerSpecificData(0x004c)
                if (!AirPodsMax2ColorFrame.matchesMax2Manufacturer(raw)) return
                // This is a first-time setup prompt: do not repeatedly alert
                // after the user already has any AirPods Max bonded here.
                val alreadyPaired = runCatching {
                    bluetooth?.bondedDevices?.any { d ->
                        (d.alias ?: d.name)?.contains("AirPods Max", ignoreCase = true) == true
                    } == true
                }.getOrDefault(false)
                if (alreadyPaired) return
                // A raw Apple advertisement can be spoofed and is not an authenticated
                // pairing-ready signal. Never bond or disclose private identifiers here.
                val now = SystemClock.elapsedRealtime()
                if (now - lastAlertTime < MIN_ALERT_INTERVAL_MS) return
                lastAlertTime = now
                announceNearby()
            }
            override fun onScanFailed(errorCode: Int) {
                // Scanner might be denied/throttled by OEM; leave the user with
                // manual pairing in the app rather than silently trying hidden APIs.
                stopScan()
                stopSelf()
            }
        }
        try {scanner.startScan(listOf(filter),settings,callback)}
        catch (_: Exception) {callback=null; stopSelf()}
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        val current=callback
        callback=null
        if (current != null) {
            try { bluetooth?.bluetoothLeScanner?.stopScan(current) } catch (_: Exception) {}
        }
    }

    private fun pairIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(ACTION_OPEN_PAIRING)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun announceNearby() {
        val n=Notification.Builder(this, CHANNEL_DETECTED)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("AirPods Max 2 nearby")
            .setContentText("If your headphones are in pairing mode, tap to connect")
            .setContentIntent(pairIntent())
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_DETECTED,n)
        if (Settings.canDrawOverlays(this)) showOptionalOverlay()
    }

    private fun overlayBackground(): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.rgb(28,28,30))
            cornerRadius = (28 * resources.displayMetrics.density)
        }

    private fun showOptionalOverlay(debugPreview: Boolean = false) {
        if (overlay != null) return
        val density=resources.displayMetrics.density
        val container=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER_HORIZONTAL
            setPadding((20*density).roundToInt(),(18*density).roundToInt(),
                (20*density).roundToInt(),(17*density).roundToInt())
            background=overlayBackground()
            elevation=16*density
        }
        container.addView(TextView(this).apply {
            text="AirPods Max 2 nearby"
            setTextColor(Color.WHITE)
            textSize=19f
            gravity=Gravity.CENTER
        })
        val artBox=FrameLayout(this)
        val artHeight=(169*density).roundToInt()
        val poster=ImageView(this).apply {
            setImageResource(R.drawable.airpods_max_midnight)
            contentDescription="Midnight AirPods Max"
            overlayArtwork=this
            scaleType=ImageView.ScaleType.FIT_CENTER
            setPadding(0,(5*density).roundToInt(),0,(5*density).roundToInt())
        }
        artBox.addView(poster,FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,artHeight))
        // One Android-native transparent WebP is shared with the in-app pairing
        // sheet. Unlike theme-precomposited MP4, no mismatched film background
        // or loading white border is possible on either light or dark surfaces.
        // Install only AFTER the overlay is attached, see below.
        container.addView(artBox,LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,artHeight))
        container.addView(TextView(this).apply {
            text="Ready to connect? Put your headphones in pairing mode."
            setTextColor(Color.rgb(188,189,197))
            textSize=12f
            gravity=Gravity.CENTER
        })
        val actions=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER
        }
        val dismiss=Button(this).apply {
            text="Later"
            setOnClickListener { hideOverlay() }
        }
        val connect=Button(this).apply {
            text="Connect"
            setOnClickListener {
                hideOverlay()
                try { pairIntent().send() } catch (_: PendingIntent.CanceledException) {}
            }
        }
        actions.addView(dismiss,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        actions.addView(connect,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        container.addView(actions)
        val params=WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels - 32*density).roundToInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity=Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y=(40*density).roundToInt()
        }
        try {
            windowManager.addView(container,params)
            overlay=container
            PairingMotion.install(poster)
            container.alpha=0f
            container.translationY=50*density
            container.animate().alpha(1f).translationY(0f).setDuration(300).start()
            handler.postDelayed({ hideOverlay() },if (debugPreview) 19000 else 12000)
        } catch (_: Exception) {
            overlay=null
        }
    }

    private fun hideOverlay() {
        overlayArtwork?.let { PairingMotion.stop(it) }
        overlayArtwork=null
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay=null
    }

    override fun onDestroy() {
        getSharedPreferences("selected_airpods", 0).edit()
            .putBoolean("nearby_watcher_requested", false).apply()
        stopScan()
        hideOverlay()
        super.onDestroy()
    }
}
