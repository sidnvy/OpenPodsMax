package dev.airpodsnative.companion;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import android.util.Log;
import java.lang.reflect.*;
import java.util.Locale;

/** Opt-in, selected-bond-only codec workaround. Never creates a Bluetooth link. */
@SuppressLint("MissingPermission")
public final class SbcCodecController implements AutoCloseable {
    public static final String TAG="AirPodsCodec";
    private final Context context;
    private final String target;
    private final BluetoothAdapter adapter;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final SbcCodecPolicy policy=new SbcCodecPolicy();
    private BluetoothA2dp a2dp;
    private boolean closed,registered;
    public static String key(String address){return "auto-sbc."+(address==null?"":address.toUpperCase(Locale.ROOT));}
    public static String statusKey(String address){return "auto-sbc-status."+(address==null?"":address.toUpperCase(Locale.ROOT));}
    public static boolean enabled(Context c,String address){
        return address!=null && c.getSharedPreferences("selected_airpods",0).getBoolean(key(address),false);
    }
    public static void status(Context c,String address,String message){
        c.getSharedPreferences("selected_airpods",0).edit().putString(statusKey(address),message).apply();
    }
    private boolean eligible(){
        return !closed && enabled(context,target)
            && target.equalsIgnoreCase(context.getSharedPreferences("selected_airpods",0).getString("bond_address",""))
            && DeviceIdentityStore.trustedAndBonded(context,target);
    }
    private final BluetoothProfile.ServiceListener listener=new BluetoothProfile.ServiceListener(){
        public void onServiceConnected(int profile,BluetoothProfile proxy){handler.post(()->{
            if(closed){adapter.closeProfileProxy(BluetoothProfile.A2DP,proxy);return;}
            a2dp=(BluetoothA2dp)proxy;refresh();
        });}
        public void onServiceDisconnected(int profile){handler.post(()->{
            a2dp=null;policy.connection(false);handler.removeCallbacksAndMessages(null);
        });}
    };
    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        public void onReceive(Context c,Intent intent){
            BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if(device==null || !target.equalsIgnoreCase(device.getAddress()))return;
            if(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction()))refresh();
            else {long token=policy.token();handler.post(()->check(token));}
        }
    };
    public SbcCodecController(Context c,String address){
        context=c.getApplicationContext();target=address;
        adapter=context.getSystemService(BluetoothManager.class).getAdapter();
        policy.configure(target,true);
    }
    public void start(){
        if(!eligible())return;
        IntentFilter filter=new IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction("android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED");
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
        else context.registerReceiver(receiver,filter);
        registered=true;
        if(adapter==null || !adapter.getProfileProxy(context,listener,BluetoothProfile.A2DP))
            status(context,target,"Bluetooth audio is unavailable. Reopen OpenPodsMax to retry.");
    }
    private void refresh(){
        if(!eligible() || a2dp==null)return;
        try{
            BluetoothDevice device=adapter.getRemoteDevice(target);
            boolean connected=a2dp.getConnectionState(device)==BluetoothProfile.STATE_CONNECTED;
            long token=policy.connection(connected);
            handler.removeCallbacksAndMessages(null);
            if(!connected){status(context,target,"Waiting for this AirPods to connect.");return;}
            // Let Samsung finish negotiation, then verify. At most four writes.
            for(long delay:new long[]{0,600,1800,4000,7500})handler.postDelayed(()->check(token),delay);
        }catch(RuntimeException ex){failed(ex);}
    }
    private void check(long token){
        if(!eligible() || a2dp==null || !policy.current(token,target))return;
        try{
            BluetoothDevice device=adapter.getRemoteDevice(target);
            if(a2dp.getConnectionState(device)!=BluetoothProfile.STATE_CONNECTED){policy.connection(false);return;}
            ArtBypass.setExemptions("Landroid/bluetooth/");
            Object codecStatus=BluetoothA2dp.class.getMethod("getCodecStatus",BluetoothDevice.class).invoke(a2dp,device);
            if(codecStatus==null)return;
            Object config=codecStatus.getClass().getMethod("getCodecConfig").invoke(codecStatus);
            if(config==null)return;
            int codec=(Integer)config.getClass().getMethod("getCodecType").invoke(config);
            if(codec==0){status(context,target,"SBC confirmed for this AirPods.");Log.i(TAG,"SBC_READBACK_CONFIRMED");return;}
            if(!policy.claim(token,target)){status(context,target,"SBC was not confirmed. Toggle off and on to retry.");return;}
            Class<?> type=Class.forName("android.bluetooth.BluetoothCodecConfig");
            Object sbc=type.getConstructor(int.class,int.class,int.class,int.class,int.class,long.class,long.class,long.class,long.class)
                .newInstance(0,1000000,0,0,0,0L,0L,0L,0L);
            BluetoothA2dp.class.getMethod("setCodecConfigPreference",BluetoothDevice.class,type).invoke(a2dp,device,sbc);
            status(context,target,"Applying SBC to this AirPods…");Log.i(TAG,"SBC_CHANGE_REQUESTED");
        }catch(Exception ex){failed(ex);handler.removeCallbacksAndMessages(null);policy.configure(null,false);}
    }
    private void failed(Throwable error){
        while(error.getCause()!=null)error=error.getCause();
        status(context,target,error instanceof SecurityException
            ? "Android denied codec control for this headset. Check its device link in Android Settings."
            : "This phone could not apply SBC automatically.");
        Log.w(TAG,"SBC_CONTROL_FAILED="+error.getClass().getSimpleName());
    }
    public void close(){
        closed=true;policy.configure(null,false);handler.removeCallbacksAndMessages(null);
        if(registered){context.unregisterReceiver(receiver);registered=false;}
        if(a2dp!=null && adapter!=null){adapter.closeProfileProxy(BluetoothProfile.A2DP,a2dp);a2dp=null;}
    }
}
