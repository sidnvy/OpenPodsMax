package dev.airpodsnative.companion;
public final class WearPauseService {
 public static final String PREF="pause_when_removed_android";
 public static boolean mediaRouteMatches(android.media.AudioManager audio,String target){
  for(android.media.AudioDeviceInfo d:android.media.AudioManager.devices)if(target.equalsIgnoreCase(d.getAddress()))return true;
  return false;
 }
}
