package dev.airpodsnative.companion;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import android.os.Build;

/** Restore the existing playback opt-in after boot/update; never opt a user in. */
public final class PlaybackRecoveryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || Build.VERSION.SDK_INT < 33) return;
        String action=intent.getAction();
        boolean boot=Intent.ACTION_BOOT_COMPLETED.equals(action)
            || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        boolean bluetoothOn=BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)
            && intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)==BluetoothAdapter.STATE_ON;
        boolean acl=BluetoothDevice.ACTION_ACL_CONNECTED.equals(action);
        if (!boot && !bluetoothOn && !acl) return;
        if (acl) {
            BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
            String selected=context.getSharedPreferences("selected_airpods",0).getString("bond_address",null);
            if(device==null || selected==null || !selected.equalsIgnoreCase(device.getAddress()))return;
        }
        if (!(context.getSharedPreferences("selected_airpods", 0).getBoolean(WearPauseService.PREF, false)
            || AppleIdentityConfig.enabled(context))
            || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
        try {
            context.startForegroundService(new Intent(context, WearPauseService.class));
            Log.i(AapController.TAG, "WEAR_RECOVERY_REQUESTED");
        } catch (RuntimeException error) {
            Log.w(AapController.TAG, "WEAR_RECOVERY_FAILED=" + error.getClass().getSimpleName());
        }
    }
}
