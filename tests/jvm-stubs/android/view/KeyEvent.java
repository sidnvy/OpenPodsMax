package android.view;
public final class KeyEvent {
 public static final int ACTION_DOWN=0,ACTION_UP=1,KEYCODE_MEDIA_PAUSE=127;
 public final int action;
 public KeyEvent(long down,long now,int action,int code,int repeat){this.action=action;}
}
