import dev.airpodsnative.companion.*;
import android.bluetooth.*;
import android.media.*;
import android.content.Context;
import java.lang.reflect.*;
import java.util.concurrent.TimeUnit;
public final class AapControllerOwnershipTest {
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
 public static void main(String[] args)throws Exception{
  String target="01:23:45:67:89:AB";
  AudioManager.devices=new AudioDeviceInfo[]{new AudioDeviceInfo("11:22:33:44:55:66")};
  AapController c=new AapController();c.initialize(new Context());
  BluetoothSocket socket=new BluetoothSocket();BluetoothAdapter.nextSocket=socket;
  c.connectReadOnlyAsync(target);
  check(!socket.wrote.await(250,TimeUnit.MILLISECONDS),"wrong audio device must not open AAP");
  c.connectReadOnlyAsync(target);
  check(!socket.wrote.await(250,TimeUnit.MILLISECONDS),"UI retry must not bypass ownership");
  Field p=AapController.class.getDeclaredField("policy");p.setAccessible(true);
  ConnectionPolicy policy=(ConnectionPolicy)p.get(c);
  policy.nextDelay(policy.generation(),target);
  AudioManager.devices=new AudioDeviceInfo[]{new AudioDeviceInfo(target)};
  check(socket.wrote.await(3,TimeUnit.SECONDS),"existing target audio link permits AAP");
  check(policy.failures()==1,"short success must not reset retry history");
  AudioManager.devices=new AudioDeviceInfo[0];
  Method monitor=AapController.class.getDeclaredMethod("checkAudioConnection");monitor.setAccessible(true);monitor.invoke(c);
  check(!c.isConnected(),"audio disconnect releases control state");
  BluetoothSocket other=new BluetoothSocket();BluetoothAdapter.nextSocket=other;
  check(!other.wrote.await(2500,TimeUnit.MILLISECONDS),"no background reclaim after audio loss");
  c.stop();
  System.out.println("PASS audio ownership: wrong target, repeated UI, activation, loss and no reclaim");
 }
}
