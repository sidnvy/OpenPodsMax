package dev.airpodsnative.companion;

import android.content.*;

/** Restore only an explicitly enabled, still-paired SBC workaround. No connection request. */
public final class CodecRecoveryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))return;
        String target=context.getSharedPreferences("selected_airpods",0).getString("bond_address",null);
        if(SbcCodecController.enabled(context,target) && DeviceIdentityStore.trustedAndBonded(context,target)){
            try{context.startForegroundService(new Intent(context,WearPauseService.class));}
            catch(RuntimeException ignored){SbcCodecController.status(context,target,"Open OpenPodsMax to resume automatic SBC.");}
        }
    }
}
