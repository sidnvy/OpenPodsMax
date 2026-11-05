package dev.airpodsnative.companion

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.system.Os
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Opt-in identity override for the Moto firmware verified over external SDP. */
internal object MotoBluetoothIdentity {
    const val PREF = "moto_apple_bluetooth_identity"
    private const val DEVICE = "roadstr"
    private const val KERNEL = "6.6.118-android15-8-gbf8cd367de7a-ab15314822-4k"

    val supported: Boolean
        get() = Build.DEVICE == DEVICE && Os.uname().release == KERNEL

    @Synchronized
    fun apply(context: Context, address: String?): String {
        if (!supported || !DeviceIdentityStore.trustedAndBonded(context, address)) {
            return "Select and confirm your paired AirPods Max 2 first."
        }
        if (!File("/system/bin/su").canExecute()) {
            return "Run GhostLock and grant OpenPodsMax root access in KernelSU."
        }
        val audio = context.getSystemService(AudioManager::class.java)
        return try {
            repeat(2) { attempt ->
                if (audio == null || audio.mode != AudioManager.MODE_NORMAL || audio.isMusicActive) {
                    return "Pause playback and end any call, then reopen OpenPodsMax."
                }
                val process = ProcessBuilder("/system/bin/su", "-c", script)
                    .redirectOutput(File("/dev/null"))
                    .redirectError(File("/dev/null"))
                    .start()
                if (!process.waitFor(45, TimeUnit.SECONDS)) {
                    // Let the root shell finish restoring Bluetooth before another operation.
                    process.waitFor()
                    return "The operation took longer than expected. Check Bluetooth before retrying."
                }
                val code = process.exitValue()
                Log.i("MotoBluetoothIdentity", "Apply attempt ${attempt + 1} exited with $code")
                if (attempt == 0 && code in 15..17) {
                    Thread.sleep(2_000)
                    return@repeat
                }
                return when (code) {
                    0 -> "Apple Vendor ID set for this boot. Verify switching on both devices."
                    10, 1 -> "Grant OpenPodsMax root access in KernelSU."
                    13 -> "End the call, then reopen OpenPodsMax."
                    14 -> "Turn on Bluetooth, then reopen OpenPodsMax."
                    else -> "Couldn’t apply the Apple Bluetooth identity. Check Bluetooth before retrying."
                }
            }
            error("Identity retry limit exceeded")
        } catch (_: IOException) {
            "Temporary root is unavailable."
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            "The identity check was interrupted."
        }
    }

    private val script = """
        set -eu
        [ "$(id -u)" = 0 ] || exit 10
        [ "$(getprop ro.product.device)" = $DEVICE ] || exit 11
        [ "$(uname -r)" = $KERNEL ] || exit 11
        [ "$(getenforce)" = Enforcing ] || exit 12
        dumpsys audio | grep -q 'Actual mode = MODE_NORMAL' || exit 13
        state() { dumpsys bluetooth_manager | sed -n 's/^  State: *//p'; }
        if [ "$(state)" != ON ]; then
            [ "$(settings get global bluetooth_on)" = 1 ] || exit 14
            # KernelSU late-load can still be bringing Android Bluetooth back.
            for attempt in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16; do
                [ "$(state)" != ON ] || break
                sleep 1
            done
            [ "$(state)" = ON ] || exit 14
            dumpsys audio | grep -q 'Actual mode = MODE_NORMAL' || exit 13
        fi
        property=bluetooth.device_id.vendor_id
        previous="$(getprop "${'$'}property")"
        [ "${'$'}previous" != 0x004C ] || exit 0
        recover() {
            setprop "${'$'}property" "${'$'}previous"
            cmd bluetooth_manager enable >/dev/null
        }
        trap recover EXIT
        setprop "${'$'}property" 0x004C
        cmd bluetooth_manager disable >/dev/null || exit 15
        for attempt in 1 2 3 4 5 6 7 8 9 10; do
            case "$(state)" in OFF|BLE_ON) break;; esac
            sleep 1
        done
        case "$(state)" in OFF|BLE_ON) ;; *) exit 15;; esac
        sleep 2
        setprop "${'$'}property" 0x004C
        cmd bluetooth_manager enable >/dev/null || exit 16
        for attempt in 1 2 3 4 5 6 7 8 9 10 11 12; do
            [ "$(state)" != ON ] || break
            sleep 1
        done
        [ "$(state)" = ON ] || exit 16
        [ "$(getprop "${'$'}property")" = 0x004C ] || exit 17
        trap - EXIT
    """.trimIndent()
}
