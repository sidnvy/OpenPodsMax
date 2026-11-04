package android.content; public class Context {
 public static final String AUDIO_SERVICE="audio";
 public Context getApplicationContext(){return this;}
 public Object getSystemService(String name){return new android.media.AudioManager();}
 public SharedPreferences getSharedPreferences(String name,int mode){throw new UnsupportedOperationException();}
 public int checkSelfPermission(String permission){return android.content.pm.PackageManager.PERMISSION_DENIED;}
 public Object startForegroundService(Intent intent){throw new UnsupportedOperationException();}
}
