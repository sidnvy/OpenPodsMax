package android.media;
public class AudioManager {
 public static final int GET_DEVICES_OUTPUTS=2;
 public static volatile AudioDeviceInfo[] devices={new AudioDeviceInfo("01:23:45:67:89:AB")};
 public static volatile java.util.concurrent.CountDownLatch queryEntered,queryRelease;
 public AudioDeviceInfo[] getDevices(int flags){
  if(queryEntered!=null){queryEntered.countDown();try{queryRelease.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}
  return devices;
 }
}
