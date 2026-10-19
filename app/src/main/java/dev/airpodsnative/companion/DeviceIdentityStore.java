package dev.airpodsnative.companion;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * AirPods Max 2 only: selected Bluetooth bond + explicit owner approval.
 *
 * A Bluetooth alias is user-editable and cannot prove the device generation.
 * BLE model 0x2D20 is supporting evidence only when attributable to this bond.
 * On the first use of a bond the owner must confirm that it is Max 2.
 * No automatic trust from an arbitrary nearby device name.
 */
public final class DeviceIdentityStore {
    private static final String PREF = "selected_airpods";
    private static final String TRUST_PREFIX = "trusted-max2.";
    private static final String SELECTED = "bond_address";
    private DeviceIdentityStore() { }

    public static boolean validAddress(String address) {
        return address != null && address.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}");
    }
    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
    public static boolean trusted(Context c, String address) {
        return validAddress(address) && prefs(c).getBoolean(TRUST_PREFIX + address.toUpperCase(java.util.Locale.ROOT), false);
    }
    public static void approveBond(Context c, String address) {
        if (!validAddress(address)) throw new IllegalArgumentException("Invalid Bluetooth address");
        if (c.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                != android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw new IllegalStateException("Bluetooth permission required before approval");
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice target = adapter == null ? null : adapter.getRemoteDevice(address);
        try {
            if (target == null || target.getBondState() != BluetoothDevice.BOND_BONDED)
                throw new IllegalStateException("Only a currently bonded headset can be approved");
        } catch (SecurityException revoked) {
            throw new IllegalStateException("Bluetooth permission revoked before approval", revoked);
        }
        prefs(c).edit()
            .putBoolean(TRUST_PREFIX + address.toUpperCase(java.util.Locale.ROOT), true)
            .putString(SELECTED, address)
            .apply();
    }
    public static void invalidateBond(Context c, String address) {
        if (validAddress(address))
            prefs(c).edit().remove(TRUST_PREFIX + address.toUpperCase(java.util.Locale.ROOT)).apply();
    }
    public static boolean trustedAndBonded(Context c, String address) {
        if (!trusted(c, address)) return false;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return false;
        try {
            return adapter.getRemoteDevice(address).getBondState() == BluetoothDevice.BOND_BONDED;
        } catch (IllegalArgumentException | SecurityException e) {
            return false;
        }
    }
}
