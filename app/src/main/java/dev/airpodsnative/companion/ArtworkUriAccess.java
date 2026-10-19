package dev.airpodsnative.companion;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Binder;
import android.util.Log;
/** Settings insists on a persistable grant even for publicly readable artwork. */
final class ArtworkUriAccess {
 static void grantToSettings(Context context) {
  long identity=Binder.clearCallingIdentity();
  try {
   ApplicationInfo info=context.getPackageManager().getApplicationInfo("com.android.settings",0);
   if((info.flags & (ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0)return;
   context.grantUriPermission("com.android.settings",Uri.parse("content://"+context.getPackageName()+".artwork/airpods-max.png"),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
   Log.i("ArtworkUriAccess","Settings read/persistable artwork grant requested");
  } catch(Exception e){Log.w("ArtworkUriAccess","Settings artwork grant unavailable: "+e.getClass().getSimpleName());}
  finally {Binder.restoreCallingIdentity(identity);}
 }
}
