package dev.airpodsnative.companion;

import android.media.*;
import android.os.Handler;
import android.util.Log;

/** Temporary audio interruption owned by this service, never by another player's MediaSession. */
final class WearTransientFocus {
    static final String PREF="resume_when_worn_android";
    private final AudioManager audio;
    private final Handler handler;
    private final Runnable onFocusLost;
    private AudioFocusRequest request;
    private final AudioManager.OnAudioFocusChangeListener listener=this::onChange;

    WearTransientFocus(AudioManager audio,Handler handler,Runnable onFocusLost){
        this.audio=audio;this.handler=handler;this.onFocusLost=onFocusLost;
    }
    boolean acquire(){
        if(request!=null)return true;
        AudioFocusRequest next=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(listener,handler).build();
        try{
            int result=audio.requestAudioFocus(next);
            Log.i(AapController.TAG,"WEAR_FOCUS_REQUEST_RESULT="+result);
            if(result!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)return false;
            request=next;return true;
        }catch(RuntimeException ex){
            Log.w(AapController.TAG,"WEAR_FOCUS_REQUEST_FAILED="+ex.getClass().getSimpleName());
            return false;
        }
    }
    boolean owned(){return request!=null;}
    private void onChange(int change){
        Log.i(AapController.TAG,"WEAR_FOCUS_CHANGE="+change);
        if(change==AudioManager.AUDIOFOCUS_LOSS
            || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){
            abandon("other_audio");onFocusLost.run();
        }
    }
    void abandon(String reason){
        AudioFocusRequest current=request;
        request=null;
        if(current==null)return;
        try{
            int result=audio.abandonAudioFocusRequest(current);
            Log.i(AapController.TAG,"WEAR_FOCUS_RELEASE reason="+reason+" result="+result);
        }catch(RuntimeException ex){
            Log.w(AapController.TAG,"WEAR_FOCUS_RELEASE_FAILED="+ex.getClass().getSimpleName());
        }
    }
}
