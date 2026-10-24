package dev.airpodsnative.companion;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;
import android.util.Log;
import android.view.KeyEvent;
import java.util.List;

/** Opt-in Android playback convenience, separate from headset control 0x0A.
 * Optional session-targeted resume requires user-enabled notification access.
 * Commands are gated on exact approved bond + current AAP generation + route. */
public final class WearPauseService extends Service {
    public static final String PREF="pause_when_removed_android";
    public static final String STOP="dev.airpodsnative.companion.WEAR_PAUSE_STOP";
    private static final String CHANNEL="wear_pause";
    private static final int NOTIFICATION=5193;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final WearPausePolicy policy=new WearPausePolicy();
    private WearResumeController resume;
    private AapController controller;
    private AudioManager audio;
    private String target;
    private long generation=-1;
    private boolean active;
    private final Runnable check=this::pauseIfDue;
    private final AapController.WearListener wear=(address,token,primary,secondary)->handler.post(()->{
        if(!active || target==null || !target.equalsIgnoreCase(address) || !controller.isCurrentSession(address,token))return;
        if(generation!=token){reset();generation=token;}
        policy.observe(primary,secondary,SystemClock.elapsedRealtime());
        resume.wear(primary,secondary);
        handler.removeCallbacks(check);
        long delay=policy.delay(SystemClock.elapsedRealtime());
        if(delay>=0)handler.postDelayed(check,delay);
    });
    private final AapController.SessionListener session=connected->handler.post(()->{
        Log.i(AapController.TAG,connected?"WEAR_AAP_SESSION_CONNECTED":"WEAR_AAP_SESSION_DISCONNECTED");
        reset();
    });
    private SharedPreferences prefs(){return getSharedPreferences("selected_airpods",MODE_PRIVATE);}
    private void reset(){handler.removeCallbacks(check);handler.removeCallbacks(resumeCheck);policy.reset();if(resume!=null)resume.clear();generation=-1;}
    private final Runnable resumeCheck=new Runnable(){public void run(){
        try {
            if(resume.tick(resumePendingSafe(),audio.isMusicActive()))Log.i(AapController.TAG,"WEAR_RESUME_REQUESTED");
            if(resume.pending())handler.postDelayed(this,250);
        }catch(RuntimeException ex){resume.clear();}
    }};
    private boolean resumePendingSafe(){return active && prefs().getBoolean(PREF,false) && prefs().getBoolean(WearResumeController.PREF,false)
        && controller.isCurrentSession(target,generation) && DeviceIdentityStore.trustedAndBonded(this,target)
        && mediaRouteMatches(audio,target) && audio.getMode()==AudioManager.MODE_NORMAL;}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onCreate(){
        super.onCreate();controller=AapController.instance();controller.initialize(this);
        audio=getSystemService(AudioManager.class);resume=new WearResumeController(this);
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel(CHANNEL,"Pause when removed",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && STOP.equals(intent.getAction())){prefs().edit().putBoolean(PREF,false).apply();stopSelf();return START_NOT_STICKY;}
        String selected=prefs().getString("bond_address",null);
        if(!prefs().getBoolean(PREF,false) || !DeviceIdentityStore.trustedAndBonded(this,selected)){
            stopSelf();return START_NOT_STICKY;
        }
        if(active && selected.equalsIgnoreCase(target))return START_STICKY;
        reset();target=selected;
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,WearPauseService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_pause).setContentTitle("Pause when AirPods are removed")
            .setContentText("Android playback control · tap to open settings").setContentIntent(open)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Stop",stop).build())
            .setOngoing(true).build();
        try {
            if(Build.VERSION.SDK_INT>=34)startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else startForeground(NOTIFICATION,notification);
            active=true;controller.addWearListener(wear);controller.addSessionListener(session);
            controller.connectReadOnlyAsync(target);
            Log.i(AapController.TAG,"WEAR_PAUSE_SERVICE_ACTIVE");
        }catch(RuntimeException ex){
            // Transient Android service-start failures are not user opt-out.
            Log.w(AapController.TAG,"WEAR_PAUSE_START_FAILED="+ex.getClass().getSimpleName());stopSelf();
            return START_NOT_STICKY;
        }
        // Allow a system-reclaimed service to rebuild its AAP subscriptions.
        // Explicit STOP and invalid configuration remain non-sticky.
        return START_STICKY;
    }
    public static boolean mediaRouteMatches(AudioManager audio,String address){
        if(Build.VERSION.SDK_INT<33 || audio==null || address==null)return false;
        List<AudioDeviceInfo> routes=audio.getAudioDevicesForAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        if(routes.size()!=1)return false;
        AudioDeviceInfo d=routes.get(0);
        return (d.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || d.getType()==AudioDeviceInfo.TYPE_BLE_HEADSET)
            && address.equalsIgnoreCase(d.getAddress());
    }
    private void pauseIfDue(){
        if(!active || !policy.consume(SystemClock.elapsedRealtime()))return;
        try {
            boolean enabled=prefs().getBoolean(PREF,false);
            boolean sessionCurrent=controller.isCurrentSession(target,generation);
            boolean trusted=DeviceIdentityStore.trustedAndBonded(this,target);
            boolean route=mediaRouteMatches(audio,target);
            boolean normal=audio.getMode()==AudioManager.MODE_NORMAL;
            boolean playing=audio.isMusicActive();
            if(!WearPausePolicy.eligible(enabled,sessionCurrent,trusted,route,normal,playing)){
                Log.i(AapController.TAG,"WEAR_PAUSE_SKIPPED_PRECONDITION enabled="+enabled+
                    " session="+sessionCurrent+" trusted="+trusted+" route="+route+
                    " normal="+normal+" playing="+playing);
                return;
            }
            resume.clear();
            boolean targeted=false;
            try {targeted=prefs().getBoolean(WearResumeController.PREF,false) && resume.pause();}
            catch(RuntimeException ex){resume.clear();Log.w(AapController.TAG,"WEAR_RESUME_UNAVAILABLE="+ex.getClass().getSimpleName());}
            if(targeted){
                Log.i(AapController.TAG,"WEAR_PAUSE_SESSION_REQUESTED");
                handler.removeCallbacks(resumeCheck);handler.postDelayed(resumeCheck,250);return;
            }
            long now=SystemClock.uptimeMillis();
            audio.dispatchMediaKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PAUSE,0));
            audio.dispatchMediaKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_MEDIA_PAUSE,0));
            Log.i(AapController.TAG,"WEAR_PAUSE_REQUESTED");
            handler.postDelayed(()->Log.i(AapController.TAG,audio.isMusicActive()?"WEAR_PAUSE_UNCONFIRMED":"WEAR_PAUSE_AUDIO_INACTIVE"),600);
        }catch(RuntimeException ex){Log.w(AapController.TAG,"WEAR_PAUSE_FAILED="+ex.getClass().getSimpleName());}
    }
    @Override public void onDestroy(){
        active=false;handler.removeCallbacksAndMessages(null);policy.reset();resume.clear();
        controller.removeWearListener(wear);controller.removeSessionListener(session);
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
}
