import dev.airpodsnative.companion.*;
import android.bluetooth.*;
import android.content.Context;
import java.lang.reflect.Field;
import java.util.concurrent.*;

/** Real controller against packet-queue Android doubles; never accesses Bluetooth. */
public final class AapControllerRaceTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void await(CountDownLatch latch)throws Exception{check(latch.await(3,TimeUnit.SECONDS),"deadline");}
 static final class PausingMap extends ConcurrentHashMap<Integer,Integer>{
  final CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
  @Override public Integer put(Integer k,Integer v){entered.countDown();try{await(release);}catch(Exception e){throw new RuntimeException(e);}return super.put(k,v);}
 }
 public static void main(String[] args)throws Exception{
  AapController c=new AapController();c.initialize(new Context());
  PausingMap observations=new PausingMap();Field f=AapController.class.getDeclaredField("observed");f.setAccessible(true);f.set(c,observations);
  BluetoothSocket socket=new BluetoothSocket();BluetoothAdapter.nextSocket=socket;
  c.connectReadOnlyAsync("01:23:45:67:89:AB");await(socket.wrote);
  socket.frames.add(new byte[]{4,0,4,0,9,0,46,50,0,0,0});await(observations.entered);
  CountDownLatch stopping=new CountDownLatch(1);
  Thread stop=new Thread(()->{stopping.countDown();c.stop();});stop.start();await(stopping);
  // Deterministic interleaving: the old implementation completes stop while
  // put() is paused, then publishes a stale observation after stop has cleared.
  stop.join(200);observations.release.countDown();stop.join(2000);
  check(!stop.isAlive(),"stop completed");
  Thread.sleep(100);
  check(!c.isConnected(),"stopped controller disconnected");
  check(c.snapshot().isEmpty(),"old reader repopulated state after stop");
  check(c.lastObservedMode()==-1 && c.batteryPercent()==-1,"all cached state invalidated");
  System.out.println("PASS deterministic stop versus in-flight setting publication race");
 }
}
