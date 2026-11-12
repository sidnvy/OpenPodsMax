package dev.airpodsnative.companion

import android.content.Context
import android.media.AudioManager
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/** Opt-in runtime property backend. A local readback is not an on-wire DID or handoff PASS. */
internal object AppleBluetoothIdentity {
    private val JOURNAL = AppleIdentityConfig.JOURNAL
    private const val APPLE = "0x004C"
    private const val SOURCE = "1"
    private val roots = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su")
    val rootAvailable: Boolean get() = roots.any { File(it).canExecute() }
    data class Result(val enabled: Boolean, val status: String, val restorePending: Boolean)
    private data class Probe(val boot: String, val vendor: String, val source: String, val backend: Int, val state: String, val busy: Boolean, val configActive: Boolean, val configPending: Boolean)
    private data class Reply(val code: Int, val values: Map<String, String>)
    private fun number(value: String): Int? = when {
        value.isEmpty() -> null
        value.matches(Regex("0[xX][0-9a-fA-F]{1,4}")) -> value.substring(2).toIntOrNull(16)
        value.matches(Regex("[0-9]{1,5}")) -> value.toIntOrNull()?.takeIf { it <= 65535 }
        else -> null
    }
    private fun valid(value: String) = value.isEmpty() || number(value) != null
    private fun apple(p: Probe) = number(p.vendor) == 76 && number(p.source) == 1
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    private fun run(context: Context, vararg args: String): Reply {
        val root = roots.firstOrNull { File(it).canExecute() } ?: return Reply(10, emptyMap())
        val script = context.assets.open("apple_identity_config.sh").bufferedReader().use { it.readText() } + "\n" +
            context.assets.open("apple_identity.sh").bufferedReader().use { it.readText() }
        val output = File.createTempFile("identity-", ".result", context.cacheDir)
        try {
            val transaction = "set -- " + args.joinToString(" ", transform = ::quote) + "\n" + script
            val process = ProcessBuilder(root, "-c", "exec timeout -s TERM -k 5 45 sh -c " + quote(transaction))
                .redirectOutput(output).redirectError(File("/dev/null")).start()
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroy()
                if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
                return Reply(30, emptyMap()) // Durable preimage remains available for explicit recovery.
            }
            val allowed = setOf("BOOT", "VENDOR", "SOURCE", "BACKEND", "STATE", "BUSY", "CONFIG_ACTIVE", "CONFIG_PENDING", "RESULT", "ROLLBACK", "CONFIG_ROLLBACK")
            val values = output.useLines { lines -> lines.take(32).mapNotNull {
                val split = it.indexOf('=')
                if (split < 0 || it.length > 160) null else it.substring(0, split).takeIf(allowed::contains)?.let { key -> key to it.substring(split + 1) }
            }.toMap() }
            return Reply(process.exitValue(), values)
        } finally { output.delete() }
    }
    private fun probe(context: Context): Probe? {
        val r = run(context, "probe", File(context.filesDir, "apple-identity.lock").absolutePath)
        if (r.code != 0) return null
        val boot = r.values["BOOT"]?.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}")) } ?: return null
        val vendor = r.values["VENDOR"]?.takeIf(::valid) ?: return null
        val source = r.values["SOURCE"]?.takeIf(::valid) ?: return null
        return Probe(boot, vendor, source, r.values["BACKEND"]?.toIntOrNull() ?: 0, r.values["STATE"].orEmpty(), r.values["BUSY"] != "0", r.values["CONFIG_ACTIVE"] == "1", r.values["CONFIG_PENDING"] == "1")
    }
    fun restorePending(context: Context): Boolean = context.getSharedPreferences("selected_airpods", 0).contains(JOURNAL + "boot")

    @Synchronized
    fun change(context: Context, address: String?, enable: Boolean): Result {
        val prefs = context.getSharedPreferences("selected_airpods", 0)
        fun finish(message: String): Result {
            prefs.edit().putString(AppleIdentityConfig.STATUS, message).apply()
            return Result(AppleIdentityConfig.enabled(context), message, restorePending(context))
        }
        fun clearJournal(): Boolean = prefs.edit().remove(JOURNAL + "boot").remove(JOURNAL + "vendor")
            .remove(JOURNAL + "source").commit()
        // Opting out always cancels future reapplication, even if root is currently unavailable.
        if (!enable && !prefs.edit().putBoolean(AppleIdentityConfig.PREF, false).commit())
            return finish("Couldn’t save your choice. Try again.")
        if (enable && !DeviceIdentityStore.trustedAndBonded(context, address))
            return finish("Select and confirm your paired AirPods first.")
        if (!enable && !restorePending(context)) return finish("Off. No identity change made by this switch needs restoring.")
        try {
            val current = probe(context) ?: return finish("Root access unavailable. Grant OpenPodsMax access in your root manager." +
                if (restorePending(context)) " Original identity restoration is pending." else "")
            if (current.busy) return finish("Another identity change is still finishing. Wait before retrying.")
            if (restorePending(context) && prefs.getString(JOURNAL + "boot", "") != current.boot) {
                if (!clearJournal()) return finish("Couldn’t update the saved recovery state.")
            }
            if (!enable && !restorePending(context)) return finish("Off. The previous runtime override expired after restart.")
            val saved = restorePending(context)
            val originalVendor = if (saved) prefs.getString(JOURNAL + "vendor", null) else current.vendor
            val originalSource = if (saved) prefs.getString(JOURNAL + "source", null) else current.source
            if (originalVendor == null || originalSource == null || !valid(originalVendor) || !valid(originalSource))
                return finish("Saved recovery information is invalid. No Bluetooth setting was changed.")
            val original = current.vendor == originalVendor && current.source == originalSource
            if (saved && !apple(current) && !original)
                return finish("Bluetooth identity changed in another tool. It was not overwritten.")
            if (enable && apple(current) && (current.backend != 2 || current.configActive)) {
                if (!prefs.edit().putBoolean(AppleIdentityConfig.PREF, true).commit()) return finish("Couldn’t save your choice.")
                return finish(if (saved) "Apple identity applied for this boot. Device switching still needs a compatibility test."
                    else "Apple identity is already set by another tool. Turning this switch off will leave that tool’s setting unchanged.")
            }
            if (!enable && original && !current.configPending) {
                if (!clearJournal()) return finish("Identity already restored; couldn’t clear the saved recovery state.")
                return finish("Original phone identity restored.")
            }
            if (current.state != "ON") return finish("Turn on Bluetooth, then try again." +
                if (restorePending(context)) " Original identity restoration is pending." else "")
            if (current.backend == 0 && !current.configPending) return finish("This Bluetooth stack does not expose the supported identity method." +
                if (restorePending(context)) " Saved recovery information was kept." else "")
            val audio = context.getSystemService(AudioManager::class.java)
            if (audio == null || audio.mode != AudioManager.MODE_NORMAL || audio.isMusicActive)
                return finish("Pause playback and end any call, then try again." + if (!enable) " Original identity restoration is pending." else "")
            if (enable && !saved && !prefs.edit().putString(JOURNAL + "boot", current.boot)
                    .putString(JOURNAL + "vendor", current.vendor).putString(JOURNAL + "source", current.source).commit())
                return finish("Couldn’t save the original identity. Nothing was changed.")
            val action = if (enable && current.backend == 2) "enable-config"
                else if (!enable && current.configPending) "disable-config" else "change"
            val result = run(context, action, current.vendor, current.source,
                if (enable) APPLE else originalVendor, if (enable) SOURCE else originalSource, current.boot,
                File(context.filesDir, "apple-identity.lock").absolutePath)
            Log.i("AppleBluetoothIdentity", "TRANSACTION_RESULT=" + result.code)
            if (result.code == 30 || result.code == 124 || result.code == 137)
                return finish("Identity operation timed out. Check Bluetooth before retrying. Saved recovery information was kept.")
            val after = probe(context)
            if (after?.busy == true) return finish("Another identity change is still finishing. Saved recovery information was kept.")
            if (result.code == 0 && after?.boot == current.boot &&
                (if (enable) apple(after) && (current.backend != 2 || after.configActive) else !after.configPending && after.vendor == originalVendor && after.source == originalSource)) {
                if (enable) {
                    if (!prefs.edit().putBoolean(AppleIdentityConfig.PREF, true).commit())
                        return finish("Identity changed; saving the switch failed. Recovery information was retained.")
                } else if (!clearJournal()) return finish("Identity restored; couldn’t clear the saved recovery state.")
                return finish(if (enable) "Apple identity applied for this boot. Test switching on both devices to confirm support."
                    else "Original phone identity restored. Existing AirPods bonds may retain cached identity information.")
            }
            if (after?.boot == current.boot && !after.configPending && after.vendor == originalVendor && after.source == originalSource) clearJournal()
            val message = when (result.code) {
                13 -> "End the call before changing Bluetooth identity."
                14 -> "Turn on Bluetooth, then try again."
                18 -> "The phone restarted during the operation. Try again."
                19 -> "This Bluetooth stack does not support this method."
                20 -> "Bluetooth identity changed during the operation. Check it before retrying."
                22 -> "The temporary Bluetooth config could not be verified. Recovery information was kept."
                21 -> "Another identity change is still finishing. Wait before retrying."
                else -> "Couldn’t confirm the identity change. Check Bluetooth before retrying."
            }
            return finish(message + if (restorePending(context)) " Original identity restoration is pending." else "")
        } catch (_: Exception) {
            return finish("Root operation did not complete. Check Bluetooth before retrying." +
                if (restorePending(context)) " Original identity restoration is pending." else "")
        }
    }
}
