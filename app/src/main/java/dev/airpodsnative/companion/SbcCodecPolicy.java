package dev.airpodsnative.companion;

/** Per-connection retry budget. Stale work must never follow a headset switch. */
public final class SbcCodecPolicy {
    private String target;
    private long generation;
    private boolean connected;
    private int attempts;
    public long configure(String address, boolean allowed) {
        target=allowed && address!=null ? address.toUpperCase(java.util.Locale.ROOT) : null;
        connected=false;attempts=0;return ++generation;
    }
    public long connection(boolean value) {
        if(connected!=value){connected=value;attempts=0;generation++;}
        return generation;
    }
    public long token(){return generation;}
    public boolean current(long token,String address){
        return connected && target!=null && address!=null && token==generation && target.equalsIgnoreCase(address);
    }
    public boolean claim(long token,String address){
        if(!current(token,address) || attempts>=4)return false;
        attempts++;return true;
    }
}
