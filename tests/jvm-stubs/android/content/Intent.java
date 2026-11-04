package android.content;
import java.util.HashMap;
import java.util.Map;
public class Intent {
 public static final String ACTION_BOOT_COMPLETED="android.intent.action.BOOT_COMPLETED";
 public static final String ACTION_MY_PACKAGE_REPLACED="android.intent.action.MY_PACKAGE_REPLACED";
 private final String action; public final Class<?> component;
 private final Map<String,Object> extras=new HashMap<>();
 public Intent(String action){this.action=action;component=null;}
 public Intent(Context context,Class<?> component){this.action=null;this.component=component;}
 public String getAction(){return action;}
 public Intent putExtra(String key,Object value){extras.put(key,value);return this;}
 public int getIntExtra(String key,int fallback){Object value=extras.get(key);return value instanceof Integer?(Integer)value:fallback;}
 public <T> T getParcelableExtra(String key,Class<T> type){return type.cast(extras.get(key));}
}
