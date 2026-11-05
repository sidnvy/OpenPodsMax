package android.media;
public class AudioManager {
 public static final int GET_DEVICES_OUTPUTS=2,MODE_NORMAL=0,MODE_IN_CALL=2;
 public static volatile int mode=MODE_NORMAL;
 public int getMode(){return mode;}
 public static volatile boolean musicActive;
 public static volatile int pauseEvents;
 public boolean isMusicActive(){return musicActive;}
 public void dispatchMediaKeyEvent(android.view.KeyEvent key){pauseEvents++;if(key.action==android.view.KeyEvent.ACTION_UP)musicActive=false;}
 public static volatile AudioDeviceInfo[] devices={new AudioDeviceInfo("01:23:45:67:89:AB")};
 public static volatile java.util.concurrent.CountDownLatch queryEntered,queryRelease;
 public AudioDeviceInfo[] getDevices(int flags){
  if(queryEntered!=null){queryEntered.countDown();try{queryRelease.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}
  return devices;
 }
}
