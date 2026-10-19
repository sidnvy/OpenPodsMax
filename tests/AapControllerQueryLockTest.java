import dev.airpodsnative.companion.*;
import android.media.AudioManager;
import android.content.Context;
import java.util.concurrent.*;
public final class AapControllerQueryLockTest {
 public static void main(String[] args)throws Exception{
  AudioManager.queryEntered=new CountDownLatch(1);AudioManager.queryRelease=new CountDownLatch(1);
  AapController c=new AapController();c.initialize(new Context());
  if(!AudioManager.queryEntered.await(2,TimeUnit.SECONDS))throw new AssertionError("query not started");
  Thread stop=new Thread(c::stop);stop.start();stop.join(500);
  boolean blocked=stop.isAlive();AudioManager.queryRelease.countDown();stop.join(2000);
  if(blocked)throw new AssertionError("system audio query blocked controller stop/UI lock");
  System.out.println("PASS blocked audio query does not hold controller lock");
 }
}
