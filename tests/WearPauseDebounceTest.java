import dev.airpodsnative.companion.WearPauseDebounce;
import dev.airpodsnative.companion.WearPausePolicy;
import java.util.ArrayList;
import java.util.List;

/** Drives the production coordinator with deterministic scheduler/clock observations. */
public final class WearPauseDebounceTest {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static final class Harness {
        final WearPausePolicy policy=new WearPausePolicy();
        final List<Long> scheduled=new ArrayList<>();
        boolean active=true;
        int pauses;
        void remove(){policy.observe(0,0,0);policy.observe(1,1,1000);}
        void callback(long now){
            if(WearPauseDebounce.consumeOrReschedule(policy,active,now,scheduled::add))pauses++;
        }
    }
    public static void main(String[] args){
        Harness h=new Harness();h.remove();
        h.callback(1499);
        check(h.pauses==0,"an early scheduler callback must not pause early");
        check(h.scheduled.equals(List.of(1L)),"retain the last millisecond with a new callback");
        h.callback(1500);
        check(h.pauses==1,"the rescheduled callback consumes the removal at its deadline");
        check(h.scheduled.size()==1,"successful consumption must not reschedule");
        h.callback(1501);
        check(h.pauses==1 && h.scheduled.size()==1,"duplicate callback cannot pause or reschedule again");

        for(int cancellation=0;cancellation<3;cancellation++){
            Harness cancelled=new Harness();cancelled.remove();cancelled.callback(1499);
            if(cancellation==0)cancelled.policy.observe(0,0,1499);
            if(cancellation==1)cancelled.policy.observe(3,3,1499);
            if(cancellation==2)cancelled.policy.reset();
            cancelled.callback(1500);cancelled.callback(1600);
            check(cancelled.pauses==0,"full reinsertion/unknown/reset removal must not pause: "+cancellation);
            check(cancelled.scheduled.equals(List.of(1L)),"cancelled deadline must not keep rescheduling: "+cancellation);
        }
        Harness partial=new Harness();partial.remove();partial.callback(1499);
        partial.policy.observe(0,1,1499);partial.callback(1500);
        check(partial.pauses==1 && partial.scheduled.equals(List.of(1L)),
            "one remaining off cup must retain the original retry deadline");
        Harness recorded=new Harness();recorded.policy.observe(0,0,0);recorded.policy.observe(0,1,16485);
        recorded.callback(16984);recorded.callback(16985);
        check(recorded.pauses==1 && recorded.scheduled.equals(List.of(1L)),
            "recorded asymmetric removal must pause after its early callback is retried");
        Harness stopped=new Harness();stopped.remove();stopped.active=false;stopped.callback(1499);stopped.callback(1500);
        check(stopped.pauses==0 && stopped.scheduled.isEmpty(),"inactive service must neither consume nor reschedule");
        Harness initial=new Harness();initial.policy.observe(1,1,1000);initial.callback(1500);
        check(initial.pauses==0 && initial.scheduled.isEmpty(),"initial removed report cannot synthesize intent");
        Harness late=new Harness();late.remove();late.callback(2500);
        check(late.pauses==1 && late.scheduled.isEmpty(),"a late due callback consumes only once without another delay");
        Harness repeated=new Harness();repeated.remove();repeated.callback(1498);repeated.callback(1499);repeated.callback(1500);
        check(repeated.pauses==1 && repeated.scheduled.equals(List.of(2L,1L)),"early callbacks preserve the original deadline without extending it");
        System.out.println("PASS "+count+" early-debounce retry, exact deadline, cancellation, inactive and duplicate checks");
    }
}
