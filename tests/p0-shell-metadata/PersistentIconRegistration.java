package dev.airpodsnative.bootstrap;
import android.bluetooth.BluetoothDevice;
import android.content.AttributionSource;
import android.os.Process;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;
import java.util.Arrays;
/** Explicitly approved persistent key-5 registration; never claims null restoration. */
public final class PersistentIconRegistration {
 public static void main(String[] args) throws Exception {
  if(args.length!=2 || !args[1].equals("accept-persistent-no-null-rollback"))throw new SecurityException("Explicit persistent authorization required");
  String model=android.os.Build.MODEL;
  if((!model.equals("Pixel 10 Pro XL") && !model.equals("motorola edge 70"))
      || android.os.Build.VERSION.SDK_INT!=37)throw new SecurityException("Unapproved model");
  Method target=MetadataProbe.class.getDeclaredMethod("targetByAddress",String.class);target.setAccessible(true);
  BluetoothDevice d=(BluetoothDevice)target.invoke(null,args[0]);
  BluetoothDevice.class.getMethod("setAttributionSource",AttributionSource.class).invoke(d,new AttributionSource.Builder(Process.myUid()).setPackageName("com.android.shell").build());
  Method get=BluetoothDevice.class.getMethod("getMetadata",int.class);
  byte[] old=(byte[])get.invoke(d,5),other=(byte[])get.invoke(d,25);
  byte[] desired="content://dev.airpodsnative.companion.artwork/airpods-max.png".getBytes(StandardCharsets.UTF_8);
  if(old!=null && !Arrays.equals(old,desired))throw new SecurityException("Preimage changed; refused overwrite");
  File backup=new File("/data/local/tmp/airpods-native-p0/persistent-icon-before-null.txt");
  if(old==null){
   backup.getParentFile().mkdirs();
   Files.write(backup.toPath(),"key=5\noriginal=null\nuser_accepted_persistent_registration=true\nexact_null_restore_unavailable=true\n".getBytes(StandardCharsets.UTF_8));
   backup.setReadable(false,false);backup.setReadable(true,true);
   if(get.invoke(d,5)!=null)throw new SecurityException("Concurrent preimage change");
   if(!(Boolean)BluetoothDevice.class.getMethod("setMetadata",int.class,byte[].class).invoke(d,5,desired))throw new IllegalStateException("Registration rejected");
  }
  if(!Arrays.equals(desired,(byte[])get.invoke(d,5)))throw new IllegalStateException("Readback mismatch");
  if(!Arrays.equals(other,(byte[])get.invoke(d,25)))throw new IllegalStateException("Unrelated metadata changed");
  System.out.println("ICON_PERSISTENT_REGISTERED=true");System.out.println("KEY25_UNCHANGED=true");
 }
}
