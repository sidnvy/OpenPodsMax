import dev.airpodsnative.companion.WearPausePolicy;

public final class WearPausePolicyTest {
    private static int count;
    private static void check(boolean b,String message){count++;if(!b)throw new AssertionError(message);}
    public static void main(String[] args){
        WearPausePolicy p=new WearPausePolicy();
        p.observe(1,1,0);check(!p.consume(1000),"initial removed report must not pause");
        p.observe(0,0,1000);check(p.delay(1000)<0,"initial wear does nothing");
        p.observe(1,0,1100);check(!p.consume(2000),"one side alone does not pause");
        p.observe(1,1,2100);check(!p.consume(2599),"debounce");
        p.observe(1,1,2300);check(p.delay(2300)==300,"duplicate must not postpone");
        check(p.consume(2600),"stable full removal pauses");
        check(!p.consume(2700),"no duplicate action");
        p.observe(1,1,2800);check(p.delay(3000)<0,"duplicate removed state cannot rearm");
        p.observe(0,0,3100);check(!p.consume(4000),"reinsertion never starts playback");
        p.observe(1,1,4100);p.observe(0,0,4200);check(!p.consume(5000),"quick adjustment cancels pause");
        p.observe(1,1,5100);p.observe(0,1,5200);check(!p.consume(6000),"partial reinsertion cancels pause");
        p.observe(1,1,6100);p.reset();check(!p.consume(7000),"disconnect cancels action");
        p.observe(1,1,7100);check(!p.consume(8000),"reconnect must see fresh worn baseline");
        p.observe(0,0,8100);p.observe(1,1,8200);p.observe(2,2,8250);check(!p.consume(9000),"unknown or case state clears intent");
        p.observe(1,1,9100);check(!p.consume(10000),"unknown cannot bridge transition");
        p.observe(0,0,10100);p.observe(1,1,10200);check(p.consume(10700),"new full cycle allowed");
        for(int mask=0;mask<64;mask++)check(WearPausePolicy.eligible((mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0,(mask&16)!=0,(mask&32)!=0)==(mask==63),"all safety gates required "+mask);
        System.out.println("PASS "+count+" wear debounce, initial/reconnect/unknown-state, no-auto-play and media eligibility checks");
    }
}
