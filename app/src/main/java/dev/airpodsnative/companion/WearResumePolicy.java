package dev.airpodsnative.companion;

/** One-shot, short-lived ownership of a pause requested by this app. */
public final class WearResumePolicy {
    private String reason="NONE";
    public String reason(){return reason;}
    private boolean fail(String why){clear();reason=why;return false;}
    private long requested=-1, worn=-1;
    private boolean confirmed;
    private long pausedUpdate=-1;
    public void clear(){requested=-1;worn=-1;confirmed=false;pausedUpdate=-1;}
    public void arm(long now){clear();reason="NONE";requested=now;}
    public boolean pending(){return requested>=0;}
    public boolean update(long now, boolean safe, boolean paused, boolean playing, long stateUpdated){
        if(!pending())return false;
        if(!safe)return fail("UNSAFE");
        if(now-requested>60000)return fail("EXPIRED");
        if(!confirmed && now-requested>2000)return fail("PAUSE_NOT_ACKNOWLEDGED");
        if(!confirmed && paused && stateUpdated>=requested){confirmed=true;pausedUpdate=stateUpdated;}
        else if(confirmed && stateUpdated!=pausedUpdate)return fail("PAUSED_TIMESTAMP_CHANGED");
        else if(confirmed && !paused)return fail("NO_LONGER_PAUSED");
        else if(!confirmed && !paused && !playing)return fail("UNEXPECTED_PLAYBACK_STATE");
        return pending();
    }
    /** PAUSED may precede AudioManager becoming inactive. Never play during that tail. */
    public boolean audioSettled(long now, boolean active){
        if(!pending())return false;
        if(!active)return true;
        if(now-requested>2000)clear();
        return false;
    }
    public void wear(int a,int b,long now){
        if(a<0 || a>1 || b<0 || b>1){clear();return;}
        if(a==0 && b==0){if(worn<0)worn=now;}else worn=-1;
    }
    public boolean consume(long now){
        if(!confirmed || worn<0 || now-worn<500 || now-requested>60000)return false;
        clear();return true;
    }
}
