package dev.airpodsnative.bootstrap;
import android.bluetooth.BluetoothDevice;
import java.lang.reflect.Method;
import java.security.MessageDigest;
/** Read-only exact-bond key 5 inspection; deliberately has no metadata write operation. */
public final class IconMetadataInspect {
    public static void main(String[] args) throws Exception {
        if(args.length != 1) throw new IllegalArgumentException("Exact approved bond required");
        Method target = MetadataProbe.class.getDeclaredMethod("targetByAddress", String.class);
        target.setAccessible(true);
        BluetoothDevice device = (BluetoothDevice) target.invoke(null, args[0]);
        byte[] value = (byte[]) BluetoothDevice.class.getMethod("getMetadata", int.class).invoke(device, 5);
        System.out.println("ICON_PRESENT=" + (value != null));
        if(value != null) {
            System.out.println("ICON_LENGTH=" + value.length);
            StringBuilder hash = new StringBuilder();
            for(byte b : MessageDigest.getInstance("SHA-256").digest(value)) hash.append(String.format("%02x", b & 255));
            System.out.println("ICON_SHA256=" + hash);
        }
    }
}
