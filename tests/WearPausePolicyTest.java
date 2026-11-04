import dev.airpodsnative.companion.WearPausePolicy;

public final class WearPausePolicyTest {
    private static int count;
    private static void check(boolean b,String message){count++;if(!b)throw new AssertionError(message);}
    public static void main(String[] args){
        WearPausePolicy p=new WearPausePolicy();
        for(int[] off:new int[][]{{1,0},{0,1},{1,1}}){
            p.reset();p.observe(off[0],off[1],0);
            check(!p.consume(1000) && p.delay(1000)<0,"initial off-head state must not pause");
        }
        for(int[] off:new int[][]{{1,0},{0,1},{1,1}}){
            p.reset();p.observe(0,0,1000);
            check(p.delay(1000)<0,"fresh fully worn state never pauses");
            p.observe(off[0],off[1],1100);
            check(!p.consume(1599),"either-cup removal must respect debounce");
            check(p.consume(1600),"each valid removed shape pauses after 500ms");
            check(!p.consume(1700),"removal is one shot");
            p.observe(off[0],off[1],1800);p.observe(1,1,1900);p.observe(0,1,2000);
            check(p.delay(3000)<0 && !p.consume(3000),"off-head transitions cannot rearm a consumed pause");
        }
        p.reset();p.observe(0,0,1000);p.observe(1,0,1100);
        p.observe(1,0,1200);check(p.delay(1200)==400,"duplicate removed state does not postpone");
        p.observe(1,1,1300);check(p.delay(1300)==300,"second cup removal retains first deadline");
        p.observe(0,1,1400);check(p.delay(1400)==200,"partial reinsertion retains deadline while one cup remains off");
        check(p.consume(1600),"asymmetric cup transitions consume at original deadline");

        p.observe(0,0,3100);check(!p.consume(4000),"reinsertion never starts playback");
        p.observe(0,1,4100);p.observe(0,0,4200);
        check(!p.consume(5000),"rapid full reinsertion cancels pause");
        p.observe(1,0,5100);p.reset();check(!p.consume(6000),"disconnect/reset cancels action");
        p.observe(0,1,6100);check(!p.consume(7000),"reconnect needs a fresh fully worn baseline");
        for(int[] unknown:new int[][]{{-1,0},{0,-1},{2,0},{0,2},{3,3}}){
            p.reset();p.observe(0,0,8100);p.observe(0,1,8200);p.observe(unknown[0],unknown[1],8250);
            check(!p.consume(9000),"unknown/case state clears pending intent");
            p.observe(1,1,9100);check(!p.consume(10000),"unknown state cannot bridge to a removal");
        }
        p.observe(0,0,10100);p.observe(1,0,10200);
        check(p.consume(10700),"a fresh full-wear/removal cycle re-arms pause");

        // Recorded Moto shape: one cup changes, no (1,1) report before reinsertion.
        p.reset();p.observe(0,0,0);p.observe(0,1,16485);
        check(!p.consume(16984),"recorded asymmetric case remains debounced");
        check(p.consume(16985),"recorded asymmetric removal no longer waits for both cups");
        p.observe(0,1,19541);check(!p.consume(19542),"late duplicate cannot send a second pause");
        p.observe(0,0,19543);check(!p.consume(20043),"recorded reinsertion never emits pause or play");

        for(int mask=0;mask<64;mask++)check(WearPausePolicy.eligible((mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0,(mask&16)!=0,(mask&32)!=0)==(mask==63),"all safety gates required "+mask);
        System.out.println("PASS "+count+" either-cup debounce, initial/reconnect/unknown-state, one-shot and media eligibility checks");
    }
}
