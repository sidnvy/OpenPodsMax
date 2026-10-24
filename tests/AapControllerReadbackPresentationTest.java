import dev.airpodsnative.companion.*;
import android.bluetooth.*;
import android.content.Context;
import android.media.*;
import java.lang.reflect.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Presentation-grace state must never survive audio-route or generation loss. */
public final class AapControllerReadbackPresentationTest {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static void until(java.util.function.BooleanSupplier fn,String why)throws Exception{
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
  while(!fn.getAsBoolean() && System.nanoTime()<deadline)Thread.sleep(5);
  check(fn.getAsBoolean(),why);
 }
 public static void main(String[] args)throws Exception {
  AudioManager.devices=new AudioDeviceInfo[]{new AudioDeviceInfo("01:23:45:67:89:AB")};
  AapController c=new AapController();
  c.initialize(new Context());
  BluetoothSocket sock=new BluetoothSocket();
  BluetoothAdapter.nextSocket=sock;
  c.connectReadOnlyAsync("01:23:45:67:89:AB");
  check(sock.wrote.await(2,TimeUnit.SECONDS),"handshake");
  try {
   sock.frames.offer(new byte[]{4,0,4,0,9,0,40,1,0,0,0});
   until(()->c.getSetting(40)==1,"baseline device setting was not received");
   check(!c.isSettingsReadbackRefreshing(),"never refreshing before a requested reconnect");
   check(c.sendBoolean(40,false),"setting should be writable");
   check(!c.isSettingsReadbackRefreshing(),"pending write alone is not a reconnect");
   Field f=AapController.class.getDeclaredField("awaitingReadback");f.setAccessible(true);
   Object receipt=((Map<?,?>)f.get(c)).get(40);
   Method request=AapController.class.getDeclaredMethod("requestSettingReadback",receipt.getClass());
   request.setAccessible(true);request.invoke(c,receipt);
   until(()->!c.isConnected(),"forced socket close must disconnect");
   check(c.isSettingsReadbackRefreshing(),"bounded auxiliary reconnect should retain presentation");
   Field time=receipt.getClass().getDeclaredField("reconnectStartedAtMs");time.setAccessible(true);
   long original=time.getLong(receipt);
   time.setLong(receipt,original-4000);
   check(!c.isSettingsReadbackRefreshing(),"old readback must not retain stale UI");
   time.setLong(receipt,original);
   AudioManager.devices=new AudioDeviceInfo[0];
   Method checkAudio=AapController.class.getDeclaredMethod("checkAudioConnection");
   checkAudio.setAccessible(true);checkAudio.invoke(c);
   check(!c.isSettingsReadbackRefreshing(),"genuine audio-route loss must invalidate grace");
   c.stop();
   check(!c.isSettingsReadbackRefreshing(),"explicit stop must invalidate grace");
   System.out.println("PASS planned-readback UI grace, bounded expiration, audio-route loss and explicit stop");
  }finally {c.stop();AudioManager.devices=new AudioDeviceInfo[]{new AudioDeviceInfo("01:23:45:67:89:AB")};}
 }
}
