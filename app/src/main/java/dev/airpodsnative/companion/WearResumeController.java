package dev.airpodsnative.companion;

import android.app.NotificationManager;
import android.content.*;
import android.media.MediaMetadata;
import android.media.session.*;
import android.os.SystemClock;
import android.util.Log;
import java.util.List;

/** Main-thread only; never sends a global PLAY command. */
final class WearResumeController {
    static final String PREF="resume_when_worn_android";
    private final Context context;
    private final WearResumePolicy policy=new WearResumePolicy();
    private MediaController owned;
    private String media;
    WearResumeController(Context context){this.context=context;}
    static boolean hasAccess(Context c){return c.getSystemService(NotificationManager.class)
        .isNotificationListenerAccessGranted(new ComponentName(c,PlaybackAccessService.class));}
    private List<MediaController> sessions(){return context.getSystemService(MediaSessionManager.class)
        .getActiveSessions(new ComponentName(context,PlaybackAccessService.class));}
    private static String media(MediaController c){
        MediaMetadata m=c.getMetadata();
        return m==null?null:m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)+"\n"+m.getString(MediaMetadata.METADATA_KEY_TITLE);
    }
    void clear(){policy.clear();owned=null;media=null;}
    private void cancel(String reason){Log.i(AapController.TAG,"WEAR_RESUME_CANCELLED="+reason);clear();}
    boolean pending(){return policy.pending();}
    void wear(int a,int b){policy.wear(a,b,SystemClock.elapsedRealtime());}
    boolean pause(){
        clear();
        if(!hasAccess(context))return false;
        MediaController chosen=null;
        for(MediaController c:sessions()){
            PlaybackState s=c.getPlaybackState();
            if(s!=null && s.getState()==PlaybackState.STATE_PLAYING){if(chosen!=null)return false;chosen=c;}
        }
        if(chosen==null || chosen.getPlaybackInfo()==null || chosen.getPlaybackInfo().getPlaybackType()!=MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL)return false;
        PlaybackState s=chosen.getPlaybackState();
        if(s==null || (s.getActions() & PlaybackState.ACTION_PAUSE)==0 || (s.getActions() & PlaybackState.ACTION_PLAY)==0)return false;
        owned=chosen;media=media(chosen);policy.arm(SystemClock.elapsedRealtime());
        chosen.getTransportControls().pause();return true;
    }
    boolean tick(boolean safe, boolean audioActive){
        if(!pending())return false;
        try {
            if(!safe || !hasAccess(context)){cancel("PRECONDITION_OR_ACCESS");return false;}
            boolean found=false;
            for(MediaController c:sessions()){
                PlaybackState s=c.getPlaybackState();
                if(c.getSessionToken().equals(owned.getSessionToken()))found=true;
                else if(s!=null && s.getState()!=PlaybackState.STATE_NONE && s.getState()!=PlaybackState.STATE_PAUSED && s.getState()!=PlaybackState.STATE_STOPPED){cancel("OTHER_ACTIVE_SESSION");return false;}
            }
            PlaybackState s=owned.getPlaybackState();
            boolean local=owned.getPlaybackInfo()!=null && owned.getPlaybackInfo().getPlaybackType()==MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL;
            if(!found || !local || s==null){cancel("SESSION_UNAVAILABLE_OR_REMOTE");return false;}
            if(!java.util.Objects.equals(media,media(owned))){cancel("TRACK_CHANGED");return false;}
            if(!policy.update(SystemClock.elapsedRealtime(),true,
                s.getState()==PlaybackState.STATE_PAUSED,s.getState()==PlaybackState.STATE_PLAYING,s.getLastPositionUpdateTime())){
                cancel(policy.reason());return false;
            }
            if(!policy.audioSettled(SystemClock.elapsedRealtime(),audioActive)){
                if(!policy.pending())cancel("AUDIO_STILL_ACTIVE_AFTER_GRACE");
                return false;
            }
            if(policy.consume(SystemClock.elapsedRealtime())){
                MediaController player=owned;clear();player.getTransportControls().play();return true;
            }
        }catch(RuntimeException ex){cancel("EXCEPTION_"+ex.getClass().getSimpleName());}
        return false;
    }
}
