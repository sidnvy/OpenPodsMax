import dev.airpodsnative.companion.*;
import android.bluetooth.*;
import android.media.*;
import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
public final class AapControllerHandoffTest {
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
 static byte[] request(){byte[] key="audioRoutingSetOwnershipToFalse".getBytes(StandardCharsets.US_ASCII);byte[] b=new byte[7+key.length];b[0]=4;b[2]=4;b[4]=0x11;System.arraycopy(key,0,b,6,key.length);b[b.length-1]=1;return b;}
 static long releases(BluetoothSocket s){return s.writes.stream().filter(b->b.length==11&&b[4]==9&&b[6]==6&&b[7]==0).count();}
 public static void main(String[] args)throws Exception{
  byte[] b=request();check(HandoffRequest.release(b,b.length),"known request");
  check(!HandoffRequest.release(b,b.length-1),"truncated boolean");b[b.length-1]=0;check(!HandoffRequest.release(b,b.length),"false flag");b[b.length-1]=1;
  b[4]=0x10;check(!HandoffRequest.release(b,b.length),"outbound request not response");b[4]=0x11;
  check(!HandoffRequest.release(null,100),"null");check(!HandoffRequest.release(b,b.length+1),"bad declared length");
  AudioManager.devices=new AudioDeviceInfo[]{new AudioDeviceInfo("01:23:45:67:89:AB")};AudioManager.mode=AudioManager.MODE_NORMAL;
  AapController c=new AapController();c.initialize(new Context());BluetoothSocket socket=new BluetoothSocket();BluetoothAdapter.nextSocket=socket;
  c.connectReadOnlyAsync("01:23:45:67:89:AB");check(socket.wrote.await(3,TimeUnit.SECONDS),"live AAP");
  AudioManager.musicActive=true;AudioManager.pauseEvents=0;
  AudioManager.mode=AudioManager.MODE_IN_CALL;socket.frames.offer(request());Thread.sleep(300);check(releases(socket)==0,"never yield during call");check(AudioManager.pauseEvents==0,"never pause during call");
  AudioManager.mode=AudioManager.MODE_NORMAL;socket.frames.offer(request());long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(releases(socket)==0&&System.nanoTime()<until)Thread.sleep(10);
  check(releases(socket)==1,"reply owns false on current idle route");
  Thread.sleep(100);check(AudioManager.pauseEvents==2&&!AudioManager.musicActive,"pause current headset media on yield");
  c.stop();socket.frames.offer(request());Thread.sleep(200);check(releases(socket)==1,"no stale session reply");
  System.out.println("PASS handoff: bounded response parsing, call guard, live yield, stopped session");
 }
}
