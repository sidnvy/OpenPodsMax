package dev.airpodsnative.companion;

/** AAP Max 2 full-removal debounce. No media command on initial state, partial
 * placement, unknown reports, reconnect, or reinsertion. Android-independent. */
public final class WearPausePolicy {
    public static final long DEBOUNCE_MS=500;
    private boolean wornSinceReset;
    private long due=-1;
    public void reset(){wornSinceReset=false;due=-1;}
    public void observe(int primary,int secondary,long now){
        if(primary<0 || primary>1 || secondary<0 || secondary>1){reset();return;}
        if(primary==0 && secondary==0){wornSinceReset=true;due=-1;return;}
        if(primary!=1 || secondary!=1){due=-1;return;}
        if(wornSinceReset && due<0)due=now+DEBOUNCE_MS;
    }
    public long delay(long now){return due<0?-1:Math.max(0,due-now);}
    public boolean consume(long now){
        if(due<0 || now<due)return false;
        due=-1;wornSinceReset=false;return true;
    }
    public static boolean eligible(boolean enabled,boolean current,boolean trusted,
                                   boolean routeMatches,boolean normalAudioMode,boolean playing){
        return enabled && current && trusted && routeMatches && normalAudioMode && playing;
    }
}
