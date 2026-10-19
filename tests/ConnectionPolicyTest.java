import dev.airpodsnative.companion.ConnectionPolicy;

/** Runs on workstation JVM without Android hardware. */
public final class ConnectionPolicyTest {
    private static void yes(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        ConnectionPolicy c=new ConnectionPolicy();
        final String a="01:23:45:67:89:AB";
        final String b="11:22:33:44:55:66";
        yes(!ConnectionPolicy.DeviceAddress.valid(null),"null must fail");
        yes(!ConnectionPolicy.DeviceAddress.valid("My AirPods Max"),"alias must not stand in for address");
        yes(ConnectionPolicy.DeviceAddress.valid(a),"valid address");
        long first=c.select(a);
        yes(c.current(first,a),"first connection current");
        yes(c.select(a.toLowerCase())==first,"reselect idempotent");
        long[] expected={1000,2000,4000,8000,16000,30000,30000};
        for(long delay:expected)yes(c.nextDelay(first,a)==delay,"backoff differs "+delay);
        c.succeeded(first,a);
        yes(c.nextDelay(first,a)==1000,"success must reset failure count");
        long second=c.select(b);
        yes(second>first,"switch should increment generation");
        yes(!c.current(first,a),"stale reader must be invalidated");
        yes(c.nextDelay(first,a)==-1,"stale reader must never retry");
        yes(c.current(second,b),"new target current");
        yes(c.nextDelay(second,b)==1000,"new target backoff resets");
        long stopped=c.stop();
        yes(stopped>second,"stop increments generation");
        yes(!c.current(second,b),"stop cancels in-flight readers");
        yes(c.target()==null,"no target after stop");
        System.out.println("PASS 14 JVM connection identity, switch, stale-reader, backoff and stop checks");
    }
}
