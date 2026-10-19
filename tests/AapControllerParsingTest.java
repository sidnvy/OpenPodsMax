import dev.airpodsnative.companion.*;
import android.bluetooth.*;
import android.content.Context;
import java.util.concurrent.TimeUnit;
public final class AapControllerParsingTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void processed(BluetoothSocket s,int count)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(s.reads.get()<count&&System.nanoTime()<end)Thread.sleep(5);check(s.reads.get()>=count,"reader deadline");}
 public static void main(String[] args)throws Exception{
  AapController c=new AapController();c.initialize(new Context());BluetoothSocket s=new BluetoothSocket();BluetoothAdapter.nextSocket=s;
  c.connectReadOnlyAsync("01:23:45:67:89:AB");check(s.wrote.await(2,TimeUnit.SECONDS),"handshake");
  try{
   s.frames.add(new byte[]{4,0,99,0,9,0,46,50,0,0,0});processed(s,2);
   check(!c.hasSetting(46),"wrong service accepted as a device capability");
   s.frames.add(new byte[]{4,0,4,0,9,0,23,(byte)255,0,0,0});processed(s,3);
   check(!c.hasSetting(23),"invalid enum accepted as device capability");
   s.frames.add(new byte[]{4,0,4,0});s.frames.add(new byte[]{9,0,46,50});processed(s,5);
   check(!c.hasSetting(46),"truncated frames manufactured a receipt");
   s.frames.add(new byte[]{4,0,4,0,9,0,46,50,0,0,0});processed(s,6);
   check(c.getSetting(46)==50,"valid hardware value dropped");
   s.frames.add(new byte[]{4,0,99,0,4,0,1,1,0,77,2,0});processed(s,7);
   check(c.batteryPercent()==-1,"battery from wrong service accepted");
   s.frames.add(new byte[]{4,0,4,0,6,0,0,0});processed(s,8);
   java.util.concurrent.CountDownLatch seeded=new java.util.concurrent.CountDownLatch(1);
   c.addWearListener((address,token,primary,secondary)->{
    check(c.isCurrentSession(address,token),"wear seed must belong to live generation");
    check(primary==0&&secondary==0,"wear baseline seed");seeded.countDown();
   });
   check(seeded.await(1,TimeUnit.SECONDS),"listener added after handshake receives current wear baseline");
   c.stop();
   c.addWearListener((address,token,primary,secondary)->{throw new AssertionError("stale wear seed after stop");});
   System.out.println("PASS actual controller packet validation and generation-bound wear baseline seeding");
  }finally{c.stop();}
 }
}
