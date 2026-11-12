import dev.airpodsnative.companion.SbcCodecPolicy;
public final class SbcCodecPolicyTest {
    private static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        String first="00:11:22:33:44:55",second="00:11:22:33:44:66";
        SbcCodecPolicy p=new SbcCodecPolicy();
        p.configure(first,true);long token=p.connection(true);
        check(!p.claim(token,second));
        for(int i=0;i<4;i++)check(p.claim(token,first));
        check(!p.claim(token,first));
        // Duplicate connected notifications must not grant infinite retries.
        check(p.connection(true)==token);check(!p.claim(token,first));
        p.connection(false);check(!p.current(token,first));
        long reconnect=p.connection(true);check(reconnect!=token);check(p.claim(reconnect,first));
        // Device switch invalidates work queued for the previous bond.
        p.configure(second,true);long next=p.connection(true);
        check(!p.claim(reconnect,first));check(!p.claim(next,first));check(p.claim(next,second));
        // User opt-out also invalidates queued work, even on the same connection.
        p.configure(second,false);p.connection(true);check(!p.claim(next,second));
        check(!p.claim(p.token(),second));
        System.out.println("SBC policy: exact target, bounded retry, reconnect, stale work and opt-out passed");
    }
}
