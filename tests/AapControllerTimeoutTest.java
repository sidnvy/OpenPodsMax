import dev.airpodsnative.companion.*;
import android.bluetooth.*;import android.content.Context;
import java.lang.reflect.Field;import java.util.concurrent.TimeUnit;
public final class AapControllerTimeoutTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static Object pendingMap(AapController c)throws Exception{Field f=AapController.class.getDeclaredField("pending");f.setAccessible(true);return f.get(c);}
 public static void main(String[] args)throws Exception{
  AapController c=new AapController();c.initialize(new Context());BluetoothSocket s=new BluetoothSocket();BluetoothAdapter.nextSocket=s;
  c.connectReadOnlyAsync("01:23:45:67:89:AB");check(s.wrote.await(2,TimeUnit.SECONDS),"handshake");
  try{
   s.frames.add(new byte[]{4,0,4,0,9,0,13,2,0,0,0});
   long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(c.lastObservedMode()<0&&System.nanoTime()<end)Thread.sleep(10);
   check(c.lastObservedMode()==0,"initial actual mode");check(c.sendMode(2),"outbound accepted");
   check(c.lastObservedMode()==0,"outbound command must never become an observed value");
   Field pending=AapController.class.getDeclaredField("pendingMode");pending.setAccessible(true);
   end=System.nanoTime()+TimeUnit.SECONDS.toNanos(7);while(pending.getInt(c)!=-1&&System.nanoTime()<end)Thread.sleep(25);
   check(pending.getInt(c)==-1,"missing ACK expires pending command");
   check(c.lastObservedMode()==0,"timeout must preserve device's observed value");
   s.frames.add(new byte[]{4,0,4,0,9,0,46,50,0,0,0});
   end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(!c.hasSetting(46)&&System.nanoTime()<end)Thread.sleep(5);
   check(!c.sendAdaptiveNoiseStrength(49),"adaptive strength rejected outside Adaptive mode");
   s.frames.add(new byte[]{4,0,4,0,9,0,13,4,0,0,0});
   end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(c.lastObservedMode()!=2&&System.nanoTime()<end)Thread.sleep(5);
   check(c.sendAdaptiveNoiseStrength(49),"adaptive strength accepted in Adaptive mode");
   byte[] sent=((java.io.ByteArrayOutputStream)s.getOutputStream()).toByteArray();
   check(sent[sent.length-4]==51,"adaptive UI value inverted on wire");
   check(c.getSetting(46)==50 && c.isVerifyingSetting(46),"write remains unconfirmed");
   Field awaiting=AapController.class.getDeclaredField("awaitingReadback");awaiting.setAccessible(true);
   Object receipt=((java.util.Map<?,?>)awaiting.get(c)).get(46);
   java.lang.reflect.Method expire=AapController.class.getDeclaredMethod("expireSettingReadback",receipt.getClass());expire.setAccessible(true);
   expire.invoke(c,receipt);
   check(!c.isVerifyingSetting(46),"readback expiry clears verification");
   check(c.getSetting(46)==50,"readback expiry never invents requested value");
   check(((java.util.Map<?,?>)pendingMap(c)).isEmpty(),"readback expiry clears pending flags");
   c.stop();check(!c.sendMode(2),"disconnected mode write refused");check(!c.sendSetting(23,1),"unreported/disconnected setting refused");
   System.out.println("PASS real controller ANC timeout, setting readback expiry, Adaptive mode guard/inversion, no optimistic state and disconnected-write rejection (offline)");
  }finally{c.stop();}
 }
}
