package dev.airpodsnative.companion;

/** Pure timing/ownership policy. The AudioManager holds the actual focus. */
public final class WearFocusPolicy {
    public static final long REWEAR_DEBOUNCE_MS=500;
    private boolean owned;
    private long wornDue=-1;
    public void acquired(){owned=true;wornDue=-1;}
    public void clear(){owned=false;wornDue=-1;}
    public boolean owned(){return owned;}
    public void observe(int primary,int secondary,long now){
        if(!owned)return;
        if(primary==0 && secondary==0){
            if(wornDue<0)wornDue=now+REWEAR_DEBOUNCE_MS;
        }else wornDue=-1;
    }
    public long delay(long now){return !owned || wornDue<0 ? -1 : Math.max(0,wornDue-now);}
    public boolean consume(long now){
        if(delay(now)!=0)return false;
        clear();return true;
    }
}
