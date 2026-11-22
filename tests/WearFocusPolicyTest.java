import dev.airpodsnative.companion.WearFocusPolicy;
public final class WearFocusPolicyTest {
    static int n;
    static void ok(boolean cond,String why){n++;if(!cond)throw new AssertionError(why);}
    public static void main(String[] args){
        WearFocusPolicy p=new WearFocusPolicy();
        p.observe(0,0,0);ok(!p.owned(),"no session");ok(p.delay(1000)<0,"no initial auto play");
        p.acquired();ok(p.owned(),"owned after successful focus grant");
        p.observe(1,1,100);ok(p.delay(100)<0,"removal never releases");
        p.observe(0,1,200);ok(p.delay(1000)<0,"one sensor cannot resume");
        p.observe(0,0,300);ok(p.delay(300)==500,"worn stable debounce");
        p.observe(0,0,400);ok(p.delay(400)==400,"duplicate does not postpone");
        ok(!p.consume(799),"not yet stable");ok(p.consume(800),"release after 500ms");
        ok(!p.owned() && !p.consume(1000),"one shot");
        p.acquired();p.observe(0,0,1000);p.observe(1,0,1200);
        ok(p.delay(1500)<0,"partial removal cancels");
        p.observe(0,0,1500);ok(!p.consume(1999),"new full 500ms");
        ok(p.consume(2000),"second stable wears");
        p.acquired();p.observe(0,0,2500);p.observe(-1,0,2550);
        ok(!p.consume(3100),"unknown must not authorize resume");
        p.acquired();p.observe(0,0,3200);p.clear();
        ok(!p.consume(4200),"disconnect/stop invalidate");
        p.acquired();p.observe(0,0,5000);p.acquired();
        ok(p.delay(6000)<0,"new owned focus invalidates stale wear");
        p.observe(0,0,6100);ok(p.consume(6600),"fresh wear cycle allowed");
        System.out.println("WearFocusPolicyTest PASS "+n+" checks");
    }
}
