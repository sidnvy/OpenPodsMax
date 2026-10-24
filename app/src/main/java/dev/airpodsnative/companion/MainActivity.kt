package dev.airpodsnative.companion

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.pm.ApplicationInfo
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.net.Uri
import androidx.core.view.WindowCompat
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.airpodsnative.companion.ui.*
import kotlinx.coroutines.delay

/** Figma v3 personal-use UI. Production state is always read from the AAP device.
 * Debug-only design_preview renders static sample data without opening Bluetooth.
 */
class MainActivity : ComponentActivity() {
    private val pairFromNotification = mutableStateOf(false)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == NearbyPairingWatchService.ACTION_OPEN_PAIRING) {
            pairFromNotification.value = true
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val debug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val pairingPreview = debug && intent.getBooleanExtra("pairing_preview", false)
        val overlayPreview = debug && intent.getBooleanExtra("overlay_preview", false)
        val preview = debug && (pairingPreview || overlayPreview || intent.getBooleanExtra("design_preview", false))
        if (intent.action == NearbyPairingWatchService.ACTION_OPEN_PAIRING) pairFromNotification.value = true
        setContent { AirPodsApp(preview, pairingPreview, pairFromNotification, overlayPreview) }
    }
}
private enum class Screen { OVERVIEW, CONTROLS, ADAPTIVE, ACCESSIBILITY, ABOUT }
private data class BondOption(val address: String, val label: String)
private data class DeviceState(
    val connected: Boolean, val battery: Int, val mode: Int,
    val settings: Map<Int, Int>, val name: String,
    val verifying: Set<Int> = emptySet()
)
@Suppress("DEPRECATION", "MissingPermission")
private fun bondedAirPods(context: android.content.Context, includeAll: Boolean = false): List<BondOption> =
    BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.mapNotNull { d ->
        val label = d.alias ?: d.name ?: "Paired Bluetooth device"
        val approved = DeviceIdentityStore.trusted(context, d.address)
        // Names are a discovery hint only; after explicit Max 2 confirmation,
        // user-renaming doesn't make the trusted bond disappear.
        if (approved || includeAll || label.contains("AirPods Max", ignoreCase = true))
            BondOption(d.address, if (approved && !label.contains("AirPods Max", ignoreCase = true))
                "$label · approved Max 2" else label)
        else null
    }?.sortedBy { it.label } ?: emptyList()

@Composable
private fun AirPodsApp(preview: Boolean, pairingPreview: Boolean,
                       pairFromNotification: MutableState<Boolean>, overlayPreview: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val controller = remember { AapController.instance().also { it.initialize(context) } }
    val prefs = remember { context.getSharedPreferences("selected_airpods", 0) }
    var permission by remember { mutableStateOf(preview || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    var selected by remember { mutableStateOf(prefs.getString("bond_address", null)) }
    var pendingIdentity by remember { mutableStateOf<String?>(null) }
    var identityVersion by remember { mutableIntStateOf(0) }
    var bonds by remember { mutableStateOf<List<BondOption>>(emptyList()) }
    var showAllBonds by remember { mutableStateOf(false) }
    val pairingDiscovery = remember { PairingDiscovery(context) }
    var pauseWhenRemoved by remember { mutableStateOf(prefs.getBoolean(WearPauseService.PREF, false)) }
    var resumeWhenWorn by remember { mutableStateOf(prefs.getBoolean(WearResumeController.PREF, false)) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == WearResumeController.PREF) resumeWhenWorn = prefs.getBoolean(key, false)
            if (key == WearPauseService.PREF) pauseWhenRemoved = prefs.getBoolean(key, false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(pauseWhenRemoved, selected, permission, preview) {
        if (!preview && pauseWhenRemoved && permission && DeviceIdentityStore.trustedAndBonded(context, selected)) {
            try { context.startForegroundService(Intent(context, WearPauseService::class.java)) }
            catch (e: RuntimeException) {
                // A transient foreground-service restriction must not erase user intent.
                android.util.Log.w(AapController.TAG, "WEAR_PAUSE_LAUNCH_FAILED=" + e.javaClass.simpleName)
            }
        } else if (!preview) context.stopService(Intent(context, WearPauseService::class.java))
    }
    var nearbyWatchRequested by remember { mutableStateOf(prefs.getBoolean("nearby_watcher_requested", false)) }
    var showPairingSheet by remember { mutableStateOf(pairingPreview) }
    var pairingStatus by remember {
        mutableStateOf<PairingDiscovery.Event>(
            if (pairingPreview) PairingDiscovery.Event.Found("AirPods Max 2 (demo)", "")
            else PairingDiscovery.Event.Searching
        )
    }
    fun initiatePairing() {
        if (preview) {
            showPairingSheet = true
            return
        }
        showPairingSheet = true
        pairingStatus = PairingDiscovery.Event.Searching
        pairingDiscovery.start { state ->
            pairingStatus = state
            if (state is PairingDiscovery.Event.Paired) {
                controller.stop()
                bonds = bondedAirPods(context, showAllBonds)
                pendingIdentity = state.address
                prefs.edit().putBoolean("nearby_watcher_requested", false).apply()
                context.stopService(Intent(context, NearbyPairingWatchService::class.java))
                nearbyWatchRequested = false
                showPairingSheet = false
            }
        }
    }
    val pairingPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val ready = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            .all { granted[it] == true || context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (ready) initiatePairing()
        else {showPairingSheet = false}
    }
    fun requestPairing() {
        val required = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        if (required.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) initiatePairing()
        else pairingPermission.launch(required.toTypedArray())
    }
    LaunchedEffect(pairFromNotification.value) {
        if (pairFromNotification.value) {
            pairFromNotification.value = false
            requestPairing()
        }
    }
    var nearbyWatchError by remember { mutableStateOf<String?>(null) }
    val nearbyPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val required = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN) +
            if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
        if (required.all { permissions[it] == true || context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            try {
                context.startForegroundService(
                    Intent(context, NearbyPairingWatchService::class.java).setAction(NearbyPairingWatchService.ACTION_START)
                )
                nearbyWatchRequested = true
                prefs.edit().putBoolean("nearby_watcher_requested", true).apply()
                nearbyWatchError = null
            } catch (e: Exception) {
                nearbyWatchError = "Android couldn't start nearby detection"
            }
        } else nearbyWatchError = "Bluetooth and notifications permissions are needed for background alerts"
    }
    fun setNearbyWatch(enabled: Boolean) {
        if (!enabled) {
            context.stopService(Intent(context, NearbyPairingWatchService::class.java))
            nearbyWatchRequested = false
            prefs.edit().putBoolean("nearby_watcher_requested", false).apply()
        } else {
            val permissions = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN) +
                if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
            nearbyPermissions.launch(permissions.toTypedArray())
        }
    }
    fun requestOverlaySettings() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)))
    }
    LaunchedEffect(overlayPreview) {
        if (overlayPreview) {
            context.startService(Intent(context, NearbyPairingWatchService::class.java)
                .setAction(NearbyPairingWatchService.ACTION_DEBUG_PREVIEW_OVERLAY))
        }
    }
    DisposableEffect(Unit) { onDispose { pairingDiscovery.stop() } }
    // Owner-confirmed Midnight is the personal build fallback. Saved per *bond*
    // so a different AirPods Max cannot inherit a nearby device's color.
    var finish by remember(selected) {
        mutableStateOf(AirPodsFinish.fromSaved(prefs.getString("color.$selected", "MIDNIGHT")))
    }
    var observedColorCode by remember(selected) {
        mutableIntStateOf(prefs.getInt("color-code.$selected", -1))
    }
    // Once the owner has allowed Nearby Devices scanning, re-detect on app open.
    var colorScanAttempts by remember { mutableIntStateOf(
        if (!preview && selected != null &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) 1 else 0
    ) }
    var colorScanAllowed by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
    }
    var colorScanMessage by remember { mutableStateOf("Not scanned") }
    val colorScanner = remember { AirPodsColorProbe(context) }
    val scanPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        colorScanAllowed = it
        if (!it) colorScanMessage = "Nearby devices permission is required"
    }
    DisposableEffect(Unit) { onDispose { colorScanner.stop() } }
    LaunchedEffect(selected, colorScanAllowed, colorScanAttempts) {
        if (!preview && colorScanAllowed && colorScanAttempts > 0 && selected != null) {
            colorScanMessage = "Scanning nearby Apple device broadcasts…"
            colorScanner.readColorByte(selected!!) { raw ->
                if (raw == null) colorScanMessage = "Couldn't securely match a broadcast to this paired headset"
                else {
                    observedColorCode = raw
                    val calibrated = prefs.getString("finish-for-raw.$raw", null)
                    val editor = prefs.edit().putInt("color-code.$selected", raw)
                    when {
                        calibrated != null -> {
                            finish = AirPodsFinish.fromSaved(calibrated)
                            editor.putString("color.$selected", finish.name)
                            colorScanMessage = "Auto-detected " + finish.label + " from calibrated device color 0x%02X".format(raw)
                        }
                        prefs.contains("color.$selected") -> {
                            // One-time explicit pairing+color calibration, never a guessed mapping.
                            editor.putString("finish-for-raw.$raw", finish.name)
                            colorScanMessage = "Calibrated " + finish.label + " to device color 0x%02X".format(raw)
                        }
                        else -> {
                            colorScanMessage = "Found device color 0x%02X. Select its finish once to calibrate.".format(raw)
                        }
                    }
                    editor.apply()
                }
            }
        }
    }
    var page by remember { mutableStateOf(Screen.OVERVIEW) }
    var observed by remember { mutableStateOf(DeviceState(false, -1, -1, emptyMap(), "AirPods Max 2")) }
    val sample = DeviceState(true, 86, 0, mapOf(
        AapController.PERSONALIZED_VOLUME to 1, AapController.CONVERSATION_AWARENESS to 1,
        AapController.EAR_DETECTION to 1, AapController.LISTENING_MODE_CYCLE to 14,
        AapController.PRESS_SPEED to 0, AapController.HOLD_DURATION to 0,
        AapController.ADAPTIVE_NOISE to 50
    ), "AirPods Max 2")
    val device = if (preview) sample else observed
    LaunchedEffect(permission, preview, showAllBonds) { bonds = if (permission && !preview) bondedAirPods(context, showAllBonds) else emptyList() }
    LaunchedEffect(permission, selected, bonds, preview, identityVersion) {
        if (!preview && permission && selected != null &&
            DeviceIdentityStore.trustedAndBonded(context, selected) &&
            bonds.any { it.address.equals(selected, ignoreCase = true) }) {
            controller.connectReadOnlyAsync(selected)
        }
    }
    LaunchedEffect(preview, selected, bonds) {
        if (preview) return@LaunchedEffect
        while (true) {
            observed = DeviceState(
                controller.isConnected, controller.batteryPercent(), controller.lastObservedMode(),
                controller.snapshot(), bonds.firstOrNull { it.address == selected }?.label ?: "AirPods Max 2",
                setOf(AapController.CONVERSATION_AWARENESS,AapController.PERSONALIZED_VOLUME,
                    AapController.EAR_DETECTION,AapController.ALLOW_OFF,
                    AapController.ADAPTIVE_NOISE,AapController.LISTENING_MODE_CYCLE,
                    AapController.PRESS_SPEED,AapController.HOLD_DURATION,AapController.CHIME_VOLUME)
                    .filter { controller.isVerifyingSetting(it) }.toSet()
            )
            delay(450)
        }
    }
    BackHandler(page != Screen.OVERVIEW) { page = Screen.OVERVIEW }
    val snackbar = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(device.mode, pending) { if (pending != null && device.mode == pending) pending = null }
    LaunchedEffect(pending) {
        val target = pending ?: return@LaunchedEffect
        delay(4500)
        if (pending == target && device.mode != target) {
            pending = null
            snackbar.showSnackbar("Couldn't confirm the listening mode change")
        }
    }
    fun requestMode(target: Int) {
        // Compose can retain a callback created before the first AAP state report.
        // The command gate must consult the live session, never a captured UI DTO.
        val active = controller.isConnected
        val current = controller.lastObservedMode()
        android.util.Log.i("AirPodsUI", "ANC_CLICK target="+target+" connected="+active+" current="+current+" pending="+pending+" preview="+preview)
        if (!preview && active && current >= 0 && target != current && pending == null) {
            if (controller.sendMode(target)) pending = target
        }
    }
    val darkMode = androidx.compose.foundation.isSystemInDarkTheme()
    SideEffect {
        (context as? Activity)?.window?.let { w ->
            val controller = WindowCompat.getInsetsController(w, w.decorView)
            controller.isAppearanceLightStatusBars = !darkMode
            controller.isAppearanceLightNavigationBars = !darkMode
            w.navigationBarColor = if (darkMode) android.graphics.Color.BLACK else android.graphics.Color.rgb(242, 242, 247)
            if (android.os.Build.VERSION.SDK_INT >= 29) w.isNavigationBarContrastEnforced = false
        }
    }
    AppleLook(darkMode) {
        val palette = LocalApplePalette.current
        Scaffold(
            containerColor = palette.background,
            snackbarHost = { SnackbarHost(snackbar) }
        ) { inner ->
            Column(Modifier.fillMaxSize().padding(inner)) {
                PageHeader(page, onBack = { page = Screen.OVERVIEW })
                val pageScroll = rememberScrollState()
                LaunchedEffect(page) { pageScroll.scrollTo(0) }
                Column(Modifier.fillMaxSize().verticalScroll(pageScroll).padding(horizontal = 20.dp, vertical = 10.dp)) {
                    if (!permission) {
                        Text("Bluetooth access is required for this headset.", color = palette.primary)
                        Button(onClick = { request.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text("Allow Bluetooth") }
                    } else if (!preview && (pendingIdentity != null ||
                        (selected != null && !DeviceIdentityStore.trustedAndBonded(context, selected)))) {
                        SectionCaption("VERIFY AIRPODS MAX 2")
                        Text("Only AirPods Max 2 is supported. Bluetooth names can be changed or imitated and do not prove the generation. Only confirm if the selected paired device really is your AirPods Max 2; use the separate physical headset as your reference.",
                            color = palette.secondary, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            val address = pendingIdentity ?: selected
                            if (address != null && bonds.any { it.address.equals(address, ignoreCase = true) }) {
                                try {
                                    DeviceIdentityStore.approveBond(context, address)
                                    selected = address
                                    pendingIdentity = null
                                    identityVersion++
                                } catch (_: Exception) {
                                    pendingIdentity = null
                                    selected = null
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Confirm: these are my AirPods Max 2") }
                        TextButton(onClick = {
                            pendingIdentity = null
                            selected = null
                            prefs.edit().remove("bond_address").apply()
                        }) { Text("Choose a different headset") }
                    } else if (!preview && (selected == null || bonds.none { it.address.equals(selected, ignoreCase = true) })) {
                        SectionCaption("CHOOSE YOUR AIRPODS")
                        Group {
                            bonds.forEachIndexed { i, bond ->
                                NavRow(bond.label, "Paired headset " + (i + 1)) {
                                    controller.stop()
                                    pendingIdentity = bond.address
                                }
                                if (i < bonds.lastIndex) Separator()
                            }
                            if (bonds.isEmpty()) NavRow("No paired AirPods Max", "Use the nearby pairing button below", enabled = false) {}
                        }
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = ::requestPairing, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                            Text("Find nearby AirPods Max 2")
                        }
                        Text("Hold the listening mode button until the light flashes white to make AirPods discoverable.",
                            fontSize = 12.sp, color = palette.secondary,
                            modifier = Modifier.padding(top = 9.dp))
                        TextButton(onClick = { bonds = bondedAirPods(context, showAllBonds) }) { Text("Refresh paired devices") }
                        TextButton(onClick = {
                            showAllBonds = !showAllBonds
                            bonds = bondedAirPods(context, showAllBonds)
                        }) { Text(if (showAllBonds) "Show only AirPods candidates" else "Can't find my renamed AirPods? Show all paired devices") }
                        SectionCaption("AUTOMATIC NEARBY ALERTS · OPTIONAL")
                        Group {
                            IosSwitchRow(
                                "Detect nearby AirPods Max 2", "Continuous BLE watcher with visible notification",
                                nearbyWatchRequested, enabled = !preview,
                                onCheckedChange = ::setNearbyWatch
                            )
                            if (nearbyWatchRequested) {
                                Separator()
                                NavRow("Allow floating pairing card", "Optional · show above other apps") {
                                    requestOverlaySettings()
                                }
                            }
                        }
                        if (nearbyWatchError != null) Text(nearbyWatchError!!,
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    } else when (page) {
                        Screen.OVERVIEW -> Overview(device, finish, pending, preview, ::requestMode,
                            onScreen = { page = it },
                            resumeWhenWorn = resumeWhenWorn,
                            onResumeWhenWorn = { enabled ->
                                if (!enabled || WearResumeController.hasAccess(context)) prefs.edit().putBoolean(WearResumeController.PREF, enabled).apply()
                                else context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            pauseWhenRemoved = pauseWhenRemoved,
                            onPauseWhenRemoved = { prefs.edit().putBoolean(WearPauseService.PREF, it).apply() },
                            onBoolean = { id, value -> if (!preview && controller.isConnected) controller.sendBoolean(id, value) })
                        Screen.CONTROLS -> Controls(device, preview,
                            onCycle = { mask -> if (!preview && controller.isConnected) controller.sendSetting(AapController.LISTENING_MODE_CYCLE, mask) })
                        Screen.ADAPTIVE -> AdaptivePage(device, preview,
                            onAdjust = { level -> !preview && controller.isConnected && controller.sendAdaptiveNoiseStrength(level) })
                        Screen.ACCESSIBILITY -> AccessibilityPage(device, preview,
                            onChoice = { id, index -> if (!preview && controller.isConnected) controller.sendSetting(id, index) },
                            onHeadDetection = { enabled -> if (!preview && controller.isConnected) controller.sendBoolean(AapController.EAR_DETECTION, enabled) },
                            onTone = { level -> if (!preview && controller.isConnected) controller.sendSetting(AapController.CHIME_VOLUME, level) })
                        Screen.ABOUT -> AboutPage(
                            device, finish, observedColorCode, colorScanMessage,
                            onChangeHeadset = {
                                controller.stop()
                                selected = null
                                pendingIdentity = null
                                prefs.edit().remove("bond_address").apply()
                                page = Screen.OVERVIEW
                            },
                            onSelectFinish = { newFinish ->
                                finish = newFinish
                                if (!preview) prefs.edit().putString("color.$selected", newFinish.name).apply()
                            },
                            onDetect = {
                                if (!colorScanAllowed) scanPermissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
                                colorScanAttempts++
                            }
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
        if (showPairingSheet) {
            NearbyPairingSheet(
                status = pairingStatus, isDemo = pairingPreview,
                onClose = {
                    showPairingSheet = false
                    if (!pairingPreview) pairingDiscovery.stop()
                },
                onRetry = ::requestPairing,
                onConnect = { address -> pairingDiscovery.pair(address) }
            )
        }
    }
}

@Composable private fun PageHeader(page: Screen, onBack: () -> Unit) {
    val p = LocalApplePalette.current
    val name = when (page) {
        Screen.OVERVIEW -> "AirPods Max 2"
        Screen.CONTROLS -> "Controls & Gestures"
        Screen.ADAPTIVE -> "Customize Adaptive Audio"
        Screen.ACCESSIBILITY -> "Accessibility"
        Screen.ABOUT -> "About your AirPods"
    }
    Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        if (page != Screen.OVERVIEW) {
            Text("‹", fontSize = 30.sp, color = p.tint, modifier = Modifier.width(32.dp).clickable(onClick = onBack))
        }
        Text(name, color = p.primary, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
    }
}

private fun asBool(d: DeviceState, id: Int): Boolean? = when (d.settings[id]) { 1 -> true; 2 -> false; else -> null }

private data class CycleOption(val name: String, val mask: Int)
private val cycles = listOf(
    CycleOption("Off", 1), CycleOption("Transparency", 4),
    CycleOption("Adaptive", 8), CycleOption("Noise Cancellation", 2)
)

@Composable
private fun Overview(
    d: DeviceState, finish: AirPodsFinish, pending: Int?, preview: Boolean,
    onMode: (Int) -> Unit, onScreen: (Screen) -> Unit,
    pauseWhenRemoved: Boolean, onPauseWhenRemoved: (Boolean) -> Unit,
    resumeWhenWorn: Boolean, onResumeWhenWorn: (Boolean) -> Unit,
    onBoolean: (Int, Boolean) -> Unit
) {
    val p = LocalApplePalette.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painterResource(finish.drawable), contentDescription = "AirPods Max 2 in " + finish.label,
            modifier = Modifier.fillMaxWidth().height(230.dp), contentScale = ContentScale.Fit
        )
        Text(if (d.battery in 0..100) "●  " + d.battery + "%" else "Battery not reported",
            color = if (d.battery in 0..100) p.green else p.secondary,
            fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(17.dp))
    Group {
        NavRow(d.name, if (d.connected) {
            if (d.battery in 0..100) "Connected · " + d.battery + "% Battery" else "Connected · Battery unavailable"
        } else "Disconnected") { onScreen(Screen.ABOUT) }
    }
    SectionCaption("LISTENING MODE")
    AppleListeningStrip(d.mode, enabled = d.connected, pendingMode = pending, onClickMode = onMode)
    SectionCaption("BUTTON CYCLES BETWEEN")
    CycleSummary(d)
    SectionCaption("CONTROLS")
    Group {
        NavRow("Digital Crown", "Increase Volume: awaiting protocol verification") { onScreen(Screen.CONTROLS) }
    }
    SectionCaption("AUDIO")
    Group {
        IosSwitchRow("Personalized Volume", "Automatically adjusts volume",
            asBool(d, AapController.PERSONALIZED_VOLUME),
            d.connected && AapController.PERSONALIZED_VOLUME !in d.verifying) {
            onBoolean(AapController.PERSONALIZED_VOLUME, it)
        }
        Separator()
        IosSwitchRow("Conversation Awareness", "Lowers media volume when you speak",
            asBool(d, AapController.CONVERSATION_AWARENESS),
            d.connected && AapController.CONVERSATION_AWARENESS !in d.verifying) {
            onBoolean(AapController.CONVERSATION_AWARENESS, it)
        }
    }
    Spacer(Modifier.height(7.dp))
    Group { NavRow("Adaptive Audio", "Customize noise allowance") { onScreen(Screen.ADAPTIVE) } }
    SectionCaption("ANDROID PLAYBACK")
    Group {
        IosSwitchRow("Pause when removed", "Pauses music on this phone.",
            pauseWhenRemoved, !preview && d.connected && Build.VERSION.SDK_INT >= 33,
            onPauseWhenRemoved)
        Separator()
        IosSwitchRow("Resume when worn", "Resumes the same player within 60 seconds. Requires notification access; enable this switch after granting access.",
            resumeWhenWorn, !preview && pauseWhenRemoved, onResumeWhenWorn)
    }
    SectionCaption("ENVIRONMENT")
    Group {
        IosSwitchRow("Loud Sound Reduction", "Not verified on Android", null, false) {}
        Separator()
        IosSwitchRow("Automatic Head Detection", "Pause when removed",
            asBool(d, AapController.EAR_DETECTION),
            d.connected && AapController.EAR_DETECTION !in d.verifying) {
            onBoolean(AapController.EAR_DETECTION, it)
        }
    }
    SectionCaption("OTHER")
    Group {
        NavRow("Accessibility", "Press speed and hold duration") { onScreen(Screen.ACCESSIBILITY) }
        Separator()
        NavRow("About your AirPods", "Model and firmware information") { onScreen(Screen.ABOUT) }
    }
}

@Composable private fun CycleSummary(d: DeviceState) {
    val p = LocalApplePalette.current
    val mask = d.settings[AapController.LISTENING_MODE_CYCLE]
    Group {
        cycles.forEachIndexed { idx, item ->
            ChoiceRow(item.name, mask != null && mask and item.mask != 0, enabled = false) {}
            if (idx < cycles.lastIndex) Separator()
        }
    }
    Text(if (mask == null) "Button cycle choices have not been reported by the headset."
        else "Press the listening mode button to cycle through selected modes.",
        fontSize = 11.sp, color = p.secondary, modifier = Modifier.padding(top = 6.dp))
}

@Composable private fun Controls(d: DeviceState, preview: Boolean, onCycle: (Int) -> Unit) {
    val p = LocalApplePalette.current
    Group {
        val crownArt = if (androidx.compose.foundation.isSystemInDarkTheme())
            R.drawable.airpods_crown_detail_dark else R.drawable.airpods_crown_detail
        Image(painterResource(crownArt), contentDescription = "Digital Crown close-up",
            modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(15.dp)),
            contentScale = ContentScale.Fit)
        Text("Digital Crown · Listening Mode button", color = p.secondary, fontSize = 11.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp))
    }
    SectionCaption("DIGITAL CROWN")
    Text("Increase Volume by Rotating Digital Crown", color = p.secondary, fontSize = 12.sp)
    Group {
        ChoiceRow("Back to Front", selected = false, enabled = false) {}
        Separator()
        ChoiceRow("Front to Back", selected = false, enabled = false) {}
    }
    Text("Rotation-direction control not yet verified by the AAP backend.",
        color = p.secondary, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
    SectionCaption("BUTTON CYCLES BETWEEN")
    val mask = d.settings[AapController.LISTENING_MODE_CYCLE]
    Group {
        cycles.forEachIndexed { idx, option ->
            ChoiceRow(option.name, mask != null && mask and option.mask != 0,
                enabled = !preview && d.connected && mask != null &&
                    AapController.LISTENING_MODE_CYCLE !in d.verifying) {
                if (mask != null) {
                    val updated = mask xor option.mask
                    if (Integer.bitCount(updated) >= 2) onCycle(updated)
                }
            }
            if (idx < cycles.lastIndex) Separator()
        }
    }
    if (mask == null) Text("The headset has not reported a listening-button configuration.",
        color = p.secondary, fontSize = 11.sp)
    SectionCaption("CALL CONTROLS")
    Group {
        NavRow("Mute & Unmute", "Requires protocol verification", enabled = false) {}
        Separator()
        NavRow("End Call", "Requires protocol verification", enabled = false) {}
    }
    SectionCaption("CAMERA REMOTE")
    Group { NavRow("Camera Control", "Apple ecosystem feature · unavailable", enabled = false) {} }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AdaptivePage(d: DeviceState, preview: Boolean, onAdjust: (Int) -> Boolean) {
    val p = LocalApplePalette.current
    val raw = d.settings[AapController.ADAPTIVE_NOISE]
    val verifying = AapController.ADAPTIVE_NOISE in d.verifying
    var draft by remember(raw, verifying, d.connected) {
        mutableFloatStateOf(if (raw != null) (100 - raw).toFloat() else 50f)
    }
    SectionCaption("CUSTOMIZE ADAPTIVE AUDIO")
    Row(Modifier.fillMaxWidth().height(80.dp).background(p.group, RoundedCornerShape(16.dp))
        .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(painterResource(R.drawable.speaker_quiet), contentDescription = null, modifier = Modifier.size(25.dp))
        Slider(
            value = draft, onValueChange = { draft = it }, valueRange = 0f..100f,
            enabled = d.connected && d.mode == 2 && raw != null && !preview && !verifying,
            onValueChangeFinished = {
                if (!onAdjust(draft.toInt())) draft = if (raw != null) (100 - raw).toFloat() else 50f
            },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                activeTrackColor = p.tint, inactiveTrackColor = p.tint, thumbColor = Color.White
            ),
            thumb = {
                Box(Modifier.size(38.dp, 18.dp).shadow(2.dp, RoundedCornerShape(11.dp))
                    .background(Color.White, RoundedCornerShape(11.dp)))
            },
            track = {
                Box(Modifier.fillMaxWidth().height(5.dp).background(p.tint, RoundedCornerShape(4.dp)))
            }
        )
        Image(painterResource(R.drawable.speaker_loud), contentDescription = null, modifier = Modifier.size(25.dp))
    }
    Spacer(Modifier.height(16.dp))
    Text("Adaptive Audio automatically adjusts noise cancellation and how much outside sound you hear based on your environment.",
        fontSize = 12.sp, lineHeight = 16.sp, color = p.secondary)
    Spacer(Modifier.height(8.dp))
    Text("Move the slider towards Noise Cancellation for less outside noise, or towards Transparency for more.",
        fontSize = 12.sp, lineHeight = 16.sp, color = p.secondary)
    Text(if (verifying) "Checking the headset…" else
        "Adjustment is not yet verified on AirPods Max 2. The last test did not retain the change.",
        fontSize = 11.sp, color = p.secondary, modifier = Modifier.padding(top = 14.dp))
    if (d.mode != 2) Text("Switch to Adaptive listening mode to adjust.",
        fontSize = 11.sp, color = p.secondary, modifier = Modifier.padding(top = 8.dp))
    if (raw == null) Text("The headset has not reported this adjustment.", fontSize = 11.sp,
        color = p.secondary, modifier = Modifier.padding(top = 14.dp))
}

@Composable private fun AccessibilityPage(
    d: DeviceState, preview: Boolean,
    onChoice: (Int, Int) -> Unit, onHeadDetection: (Boolean) -> Unit,
    onTone: (Int) -> Unit
) {
    @Composable fun choices(title: String, id: Int, labels: List<String>) {
        SectionCaption(title)
        val observed = d.settings[id]
        Group {
            labels.forEachIndexed { index, name ->
                ChoiceRow(name, observed == index, d.connected && !preview && observed != null &&
                    id !in d.verifying) {
                    onChoice(id, index)
                }
                if (index < labels.lastIndex) Separator()
            }
        }
    }
    choices("PRESS SPEED", AapController.PRESS_SPEED, listOf("Default", "Slower", "Slowest"))
    choices("PRESS AND HOLD DURATION", AapController.HOLD_DURATION, listOf("Default", "Shorter", "Shortest"))
    SectionCaption("TONE VOLUME")
    val tone = d.settings[AapController.CHIME_VOLUME]
    var toneDraft by remember(tone) { mutableFloatStateOf((tone ?: 80).toFloat()) }
    Group {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Tone Volume", color = LocalApplePalette.current.primary, fontSize = 14.sp)
                Text(if (tone == null) "Not reported" else "$tone%",
                    color = LocalApplePalette.current.secondary, fontSize = 12.sp)
            }
            Slider(value = toneDraft, onValueChange = { toneDraft = it },
                valueRange = 15f..100f,
                enabled = d.connected && !preview && tone != null &&
                    AapController.CHIME_VOLUME !in d.verifying,
                onValueChangeFinished = { onTone(toneDraft.toInt()) },
                colors = SliderDefaults.colors(activeTrackColor = LocalApplePalette.current.tint))
        }
    }
    SectionCaption("HEADPHONE BEHAVIOR")
    Group {
        IosSwitchRow("Automatic Head Detection", "Automatically pause when removed",
            asBool(d, AapController.EAR_DETECTION), d.connected && !preview, onHeadDetection)
    }
}

@Composable private fun AboutPage(
    d: DeviceState, finish: AirPodsFinish, colorCode: Int, colorScanMessage: String,
    onChangeHeadset: () -> Unit,
    onSelectFinish: (AirPodsFinish) -> Unit, onDetect: () -> Unit
) {
    val p = LocalApplePalette.current
    @Composable fun detail(label: String, value: String) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = p.primary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(value, color = p.secondary, fontSize = 11.sp, textAlign = TextAlign.End)
        }
    }
    SectionCaption("DEVICE INFORMATION")
    Group {
        detail("Model", "AirPods Max 2")
        Separator()
        detail("Model Number", "A3454")
        Separator()
        detail("Firmware Version", "Not yet read")
        Separator()
        detail("Battery", if (d.battery in 0..100) d.battery.toString() + "%" else "Not reported")
        Separator()
        detail("Serial Number", "Not displayed")
    }
    SectionCaption("COLOR / FINISH")
    Group {
        AirPodsFinish.entries.forEachIndexed { i, option ->
            ChoiceRow(option.label, selected = finish == option, enabled = true) { onSelectFinish(option) }
            if (i < AirPodsFinish.entries.lastIndex) Separator()
        }
    }
    Spacer(Modifier.height(8.dp))
    Group {
        NavRow("Detect from BLE broadcast", if (colorCode >= 0)
            "Observed device color code 0x%02X".format(colorCode)
            else "Optional · requires exact bonded identity match", onClick = onDetect)
    }
    Text(colorScanMessage, color = p.secondary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    SectionCaption("DEVICE SELECTION")
    Group {
        NavRow("Change AirPods Max 2", "Select another paired headset", onClick = onChangeHeadset)
    }
    SectionCaption("PRIVACY")
    Group {
        detail("Account required", "No")
        Separator()
        detail("Control processing", "On device")
    }
}
