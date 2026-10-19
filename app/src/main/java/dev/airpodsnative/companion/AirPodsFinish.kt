package dev.airpodsnative.companion

import android.bluetooth.le.ScanRecord
import androidx.annotation.DrawableRes

/** Distinct render assets. Bluetooth model 0x2D20 identifies Max 2 but does not identify its color. */
enum class AirPodsFinish(val label: String, @param:DrawableRes val drawable: Int) {
    MIDNIGHT("Midnight", R.drawable.airpods_max_midnight),
    STARLIGHT("Starlight", R.drawable.airpods_max_starlight),
    BLUE("Blue", R.drawable.airpods_max_blue),
    PURPLE("Purple", R.drawable.airpods_max_purple),
    ORANGE("Orange", R.drawable.airpods_max_orange);

    companion object {
        fun fromSaved(s: String?): AirPodsFinish =
            entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: MIDNIGHT
    }
}

/** Public Apple Proximity Pairing broadcast. Adapted from CAPod GPLv3:
 * manufacturerData[0] = 0x07 (message type), [1] = 0x19 (25-byte payload),
 * [2] = 0x01 (paired status), [3,4] = 0x2D,0x20 (Max 2 model),
 * [9] = public device color byte (payload.public.data[7]).
 *
 * A raw color byte by itself DOES NOT establish a verified finish name:
 * CAPod's common DeviceColor enum predates the new Midnight / Purple / Orange
 * finish catalog, so we do not guess at its mapping.
 */
object AirPodsMax2ColorFrame {
    private const val APPLE_MANUFACTURER = 0x004C
    private val MAX2_PREFIX = byteArrayOf(0x07, 0x19, 0x01, 0x2D, 0x20)
    const val COLOR_OFFSET = 9

    fun parseManufacturer(data: ByteArray?): Int? {
        if (data == null || data.size < 11) return null
        if (MAX2_PREFIX.indices.any { data[it] != MAX2_PREFIX[it] }) return null
        val raw = data[COLOR_OFFSET].toInt() and 0xff
        return if (raw == 0xff) null else raw
    }

    fun parseRecord(record: ScanRecord?): Int? =
        parseManufacturer(record?.getManufacturerSpecificData(APPLE_MANUFACTURER))

    fun matchesMax2Manufacturer(data: ByteArray?): Boolean =
        data != null && data.size >= 11 &&
            MAX2_PREFIX.indices.all { data[it] == MAX2_PREFIX[it] }
}
