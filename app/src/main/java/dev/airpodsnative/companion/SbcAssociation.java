package dev.airpodsnative.companion;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.companion.*;
import android.content.*;
import android.os.*;

/** Official per-device association, not a privileged permission grant. */
public final class SbcAssociation {
    private SbcAssociation(){}
    private static String requestKey(String address){return "auto-sbc-request."+address.toUpperCase(java.util.Locale.ROOT);}
    public static void disable(Context c,String address){
        c.getSharedPreferences("selected_airpods",0).edit().putBoolean(SbcCodecController.key(address),false).remove(requestKey(address)).apply();
    }
    public static boolean associated(Context context,String address){
        if(Build.VERSION.SDK_INT<33)return true;
        try{
            for(AssociationInfo info:context.getSystemService(CompanionDeviceManager.class).getMyAssociations())
                if(info.getDeviceMacAddress()!=null && address.equalsIgnoreCase(info.getDeviceMacAddress().toString()))return true;
        }catch(RuntimeException ignored){}
        return false;
    }
    public static void enable(Activity activity,String address){
        if(!eligible(activity,address))return;
        if(Build.VERSION.SDK_INT<33 || associated(activity,address)){saveEnabled(activity,address);return;}
        final long requestId=SystemClock.elapsedRealtimeNanos();
        activity.getSharedPreferences("selected_airpods",0).edit().putLong(requestKey(address),requestId).apply();
        SbcCodecController.status(activity,address,"Allow Android to link this headset to OpenPodsMax.");
        AssociationRequest request=new AssociationRequest.Builder().setSingleDevice(true)
            .addDeviceFilter(new BluetoothDeviceFilter.Builder().setAddress(address).build()).build();
        try{
            activity.getSystemService(CompanionDeviceManager.class).associate(request,new CompanionDeviceManager.Callback(){
                @Override public void onAssociationPending(IntentSender sender){
                    if(!current())return;
                    try{activity.startIntentSenderForResult(sender,9137,null,0,0,0);}
                    catch(IntentSender.SendIntentException ex){failed();}
                }
                @Override public void onAssociationCreated(AssociationInfo info){
                    if(Build.VERSION.SDK_INT>=33 && info.getDeviceMacAddress()!=null && address.equalsIgnoreCase(info.getDeviceMacAddress().toString())
                        && current())saveEnabled(activity,address);
                }
                @Override public void onFailure(CharSequence error){failed();}
                private boolean current(){return eligible(activity,address) && activity.getSharedPreferences("selected_airpods",0).getLong(requestKey(address),-1)==requestId;}
                private void failed(){if(current())SbcCodecController.status(activity,address,"Android did not link this headset. Try enabling again.");}
            },new Handler(Looper.getMainLooper()));
        }catch(RuntimeException ex){
            android.util.Log.w(SbcCodecController.TAG,"ASSOCIATION_START_FAILED="+ex.getClass().getSimpleName());
            SbcCodecController.status(activity,address,"Android could not link this headset on this phone.");
        }
    }
    private static boolean eligible(Context c,String address){
        return address!=null && address.equalsIgnoreCase(c.getSharedPreferences("selected_airpods",0).getString("bond_address",""))
            && DeviceIdentityStore.trustedAndBonded(c,address);
    }
    private static void saveEnabled(Context c,String address){
        c.getSharedPreferences("selected_airpods",0).edit().putBoolean(SbcCodecController.key(address),true).remove(requestKey(address)).apply();
        SbcCodecController.status(c,address,"Waiting for this AirPods to connect.");
    }
}
