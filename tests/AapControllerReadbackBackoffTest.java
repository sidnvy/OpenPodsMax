import dev.airpodsnative.companion.*;
import android.bluetooth.*;
import android.content.Context;
import java.lang.reflect.*;
import java.util.Map;
import java.util.concurrent.*;

/** Repeated deliberate readbacks must not be counted as failing connections. */
public final class AapControllerReadbackBackoffTest {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static void until(java.util.function.BooleanSupplier f,String why)throws Exception{
  long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
  while(!f.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(5);
  check(f.getAsBoolean(),why);
 }
 public static void main(String[] args)throws Exception{
  String address="01:23:45:67:89:AB";
  AapController c=new AapController();c.initialize(new Context());
  Field policyField=AapController.class.getDeclaredField("policy");policyField.setAccessible(true);
  ConnectionPolicy policy=(ConnectionPolicy)policyField.get(c);
  Field awaiting=AapController.class.getDeclaredField("awaitingReadback");awaiting.setAccessible(true);
  BluetoothSocket current=new BluetoothSocket();BluetoothAdapter.nextSocket=current;c.connectReadOnlyAsync(address);
  check(current.wrote.await(4,TimeUnit.SECONDS),"initial handshake");
  try{
   int value=1;current.frames.offer(new byte[]{4,0,4,0,9,0,40,1,0,0,0});
   until(()->c.getSetting(40)==1,"initial observed value");
   for(int cycle=0;cycle<3;cycle++){
    int desired=value==1?2:1;
    check(c.sendSetting(40,desired),"setting write");
    Object receipt=((Map<?,?>)awaiting.get(c)).get(40);
    Method request=AapController.class.getDeclaredMethod("requestSettingReadback",receipt.getClass());request.setAccessible(true);
    BluetoothSocket next=new BluetoothSocket();BluetoothAdapter.nextSocket=next;
    request.invoke(c,receipt);
    check(next.wrote.await(3,TimeUnit.SECONDS),"planned reconnect must stay at one second");
    check(policy.failures()==0,"planned readback accumulated failure backoff");
    next.frames.offer(new byte[]{4,0,4,0,9,0,40,(byte)desired,0,0,0});
    until(()->c.getSetting(40)==desired && !c.isVerifyingSetting(40),"independent readback");
    current=next;value=desired;
   }
   current.close();until(()->policy.failures()==1,"genuine disconnect still increments failure history");
   System.out.println("PASS repeated planned readbacks keep bounded delay; genuine failures retain backoff");
  }finally{c.stop();}
 }
}
