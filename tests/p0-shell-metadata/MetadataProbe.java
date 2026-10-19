package dev.airpodsnative.bootstrap;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Process;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Small, deliberately read-only Shell UID probe. Does not mutate BT data. */
public final class MetadataProbe {
    private static final int CUSTOM_FIELDS = 25;
    private static String digest(byte[] data) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b & 255));
        return sb.toString();
    }
    private static BluetoothAdapter adapter() {
        try {
            if (Looper.getMainLooper() == null) {
                Looper.class.getMethod("prepareMainLooper").invoke(null);
            }
        } catch (Exception ex) {
            System.err.println("MAIN_LOOPER_INIT_FAILED=" + ex);
        }
        try {
            Class<?> managerType = Class.forName("android.os.BluetoothServiceManager");
            Class<?> initializer = Class.forName("android.bluetooth.BluetoothFrameworkInitializer");
            Object manager = managerType.getConstructor().newInstance();
            initializer.getMethod("setBluetoothServiceManager", managerType).invoke(null, manager);
            System.out.println("LOCAL_BLUETOOTH_SERVICE_MANAGER_INITIALIZED=true");
        } catch (IllegalStateException ex) {
            System.out.println("LOCAL_BLUETOOTH_SERVICE_MANAGER_ALREADY_INITIALIZED=true");
        } catch (Exception ex) {
            System.err.println("LOCAL_BLUETOOTH_SERVICE_MANAGER_ERROR=" + ex);
        }
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a != null) System.out.println("DEFAULT_ADAPTER_PRESENT=true");
        try {
            if (Looper.myLooper() == null) Looper.prepare();
            Class<?> threadType = Class.forName("android.app.ActivityThread");
            Object thread = threadType.getMethod("systemMain").invoke(null);
            Context context = (Context) threadType.getMethod("getSystemContext").invoke(thread);
            BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            System.out.println("HAS_SYSTEM_CONTEXT=" + (context != null));
            System.out.println("MANAGER=" + (manager == null ? "null" : manager.getClass().getName()));
            Context shellContext = context.createPackageContext("com.android.shell", 0);
            Object source = shellContext.getAttributionSource();
            System.out.println("ATTRIBUTION_UID=" + source.getClass().getMethod("getUid").invoke(source));
            System.out.println("ATTRIBUTION_PACKAGE=" + source.getClass().getMethod("getPackageName").invoke(source));
            for (Method m : BluetoothDevice.class.getDeclaredMethods()) {
                if (m.getName().contains("Attribution") || m.getName().contains("Metadata"))
                    System.out.println("BLUETOOTH_DEVICE_METHOD=" + m.toString());
            }
            for (Method m : BluetoothAdapter.class.getDeclaredMethods()) {
                if (m.getName().contains("createAdapter") || m.getName().contains("Attribution"))
                    System.out.println("BLUETOOTH_ADAPTER_METHOD=" + m.toString());
            }
            try {
                BluetoothAdapter improved = (BluetoothAdapter) BluetoothAdapter.class
                    .getMethod("createAdapter", Class.forName("android.content.AttributionSource"))
                    .invoke(null, source);
                System.out.println("ATTRIBUTED_ADAPTER_PRESENT=" + (improved != null));
                if (improved != null) return improved;
            } catch (Exception ex) { System.err.println("ATTRIBUTED_ADAPTER_ERROR=" + ex); }
            BluetoothAdapter fallback = manager == null ? null : manager.getAdapter();
            return fallback;
        } catch (Throwable err) {
            System.err.println("CONTEXT_BOOTSTRAP_FAILED=" + err.toString());
            err.printStackTrace(System.err);
            return null;
        }
    }
    private static List<BluetoothDevice> bonds() {
        BluetoothAdapter a = adapter();
        if (a == null) throw new IllegalStateException("Bluetooth adapter unavailable");
        Set<BluetoothDevice> bonded = a.getBondedDevices();
        List<BluetoothDevice> list = new ArrayList<>(bonded);
        list.sort(Comparator.comparing(d -> d.getName() == null ? "" : d.getName()));
        return list;
    }
    /** Exact user-selected bonded address; neither name nor bond ordering is stable. */
    private static BluetoothDevice targetByAddress(String address) throws Exception {
        if (address == null || !address.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))
            throw new IllegalArgumentException("Invalid target address");
        BluetoothAdapter a=adapter();
        if(a==null)throw new IllegalStateException("Bluetooth adapter unavailable");
        BluetoothDevice d=a.getRemoteDevice(address);
        if(d.getBondState()!=BluetoothDevice.BOND_BONDED)
            throw new SecurityException("Target not bonded");
        return d;
    }
    private static void privateBackup(File file, byte[] data) throws Exception {
        if (!file.getCanonicalPath().startsWith("/data/local/tmp/airpods-native-p0/"))
            throw new SecurityException("Invalid backup destination");
        File dir=file.getParentFile();
        if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Can't create private directory");
        try(FileOutputStream out=new FileOutputStream(file)){out.write(data);}
        if(!file.setReadable(false,false)||!file.setReadable(true,true)||!file.setWritable(true,true))
            throw new SecurityException("Couldn't restrict backup file");
    }
    private static boolean writeKey25(BluetoothDevice d,byte[] value) throws Exception {
        android.content.AttributionSource source=new android.content.AttributionSource.Builder(Process.myUid())
            .setPackageName("com.android.shell").build();
        BluetoothDevice.class.getMethod("setAttributionSource",android.content.AttributionSource.class)
            .invoke(d,source);
        Method method=BluetoothDevice.class.getMethod("setMetadata",int.class,byte[].class);
        return (Boolean)method.invoke(d,CUSTOM_FIELDS,value);
    }
    private static byte[] metadata(BluetoothDevice d) throws Exception {
        Method get = BluetoothDevice.class.getMethod("getMetadata", int.class);
        return (byte[]) get.invoke(d, CUSTOM_FIELDS);
    }
    public static void main(String[] args) throws Exception {
        try {
            System.out.println("SHELL_UID=" + Process.myUid());
            if (args.length == 0 || args[0].equals("list")) {
                List<BluetoothDevice> list = bonds();
                System.out.println("BONDED_COUNT=" + list.size());
                for (int i = 0; i < list.size(); ++i) {
                    String name = list.get(i).getName();
                    System.out.println("INDEX=" + i + " AIRPODS_NAME_MATCH=" + (name != null && name.toLowerCase().contains("airpods"))
                            + " MAX_MATCH=" + (name != null && name.toLowerCase().contains("max"))
                            + " FIND_MY_MATCH=" + (name != null && name.toLowerCase().contains("find my")));
                }
            } else if (args[0].equals("inspect")) {
                if (args.length != 2) throw new IllegalArgumentException("inspect <bond-index>");
                List<BluetoothDevice> list = bonds();
                int idx = Integer.parseInt(args[1]);
                if (idx < 0 || idx >= list.size()) throw new IllegalArgumentException("invalid index");
                byte[] data = metadata(list.get(idx));
                System.out.println("METADATA_KEY=25");
                try { System.out.println("MAX_METADATA_KEY="+BluetoothDevice.class.getMethod("getMaxMetadataKey").invoke(null)); }
                catch (Exception e) { System.out.println("MAX_METADATA_KEY_UNAVAILABLE=" + e.getClass().getSimpleName()); }
                System.out.println("PRESENT=" + (data != null));
                if (data != null) {
                    System.out.println("LENGTH=" + data.length);
                    System.out.println("SHA256=" + digest(data));
                    // no plaintext metadata, private identifiers, or keys printed
                }
            } else if (args[0].equals("backup")) {
                if (args.length != 3) throw new IllegalArgumentException("backup <bond-index> <device-path>");
                List<BluetoothDevice> list = bonds();
                int idx = Integer.parseInt(args[1]);
                if (idx < 0 || idx >= list.size()) throw new IllegalArgumentException("invalid index");
                byte[] data = metadata(list.get(idx));
                if (data == null) {
                    System.out.println("PRESENT=false; no file written");
                } else {
                    File file = new File(args[2]);
                    if (!file.getCanonicalPath().startsWith("/data/local/tmp/airpods-native-p0/")) {
                        throw new SecurityException("backup path must be inside /data/local/tmp/airpods-native-p0/");
                    }
                    File dir = file.getParentFile();
                    if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdir failed");
                    try(FileOutputStream out = new FileOutputStream(file)) {out.write(data);}
                    if (!file.setReadable(false, false) || !file.setReadable(true, true) || !file.setWritable(true,true)) {
                        throw new SecurityException("could not restrict backup file permissions");
                    }
                    System.out.println("BACKED_UP length=" + data.length + " sha256=" + digest(data));
                }
            } else if(args[0].equals("backup-target")) {
                if(args.length!=3)throw new IllegalArgumentException("backup-target <exact-bond-address> <private-path>");
                BluetoothDevice d=targetByAddress(args[1]);
                byte[] data=metadata(d);
                if(data==null||data.length==0||data.length>2048)
                    throw new IllegalStateException("Unsupported missing/oversized key25; do not invent metadata");
                privateBackup(new File(args[2]),data);
                System.out.println("BACKUP_OK=true");
                System.out.println("LENGTH="+data.length);
                System.out.println("SHA256="+digest(data));
            } else if(args[0].equals("inspect-target")) {
                if(args.length!=2)throw new IllegalArgumentException("inspect-target <exact-bond-address>");
                byte[] data=metadata(targetByAddress(args[1]));
                System.out.println("PRESENT="+(data!=null));
                if(data!=null){System.out.println("LENGTH="+data.length);System.out.println("SHA256="+digest(data));}
            } else if(args[0].equals("swap-target")) {
                if(args.length!=5)throw new IllegalArgumentException("swap-target <exact-bond-address> <expected-file> <replacement-file> <expected-sha256>");
                BluetoothDevice d=targetByAddress(args[1]);
                byte[] expected=Files.readAllBytes(new File(args[2]).toPath());
                byte[] replacement=Files.readAllBytes(new File(args[3]).toPath());
                byte[] current=metadata(d);
                if(expected.length==0||expected.length>2048||replacement.length==0||replacement.length>2048)
                    throw new SecurityException("Bad metadata length");
                if(!Arrays.equals(current,expected)||!digest(expected).equals(args[4]))
                    throw new SecurityException("Metadata changed; refuse overwrite");
                if(!Arrays.equals(expected,replacement)){
                    if(!writeKey25(d,replacement))throw new IllegalStateException("Metadata write returned false");
                }
                byte[] after=metadata(d);
                if(!Arrays.equals(replacement,after))throw new IllegalStateException("Metadata readback mismatch");
                System.out.println("WRITE_RESULT=true");
                System.out.println("READBACK_MATCH=true");
                System.out.println("AFTER_SHA256="+digest(after));
            } else if (args[0].equals("rewrite-identical")) {
                if (args.length != 3) throw new IllegalArgumentException("rewrite-identical <bond-index> <backup-path>");
                List<BluetoothDevice> list = bonds();
                int idx = Integer.parseInt(args[1]);
                if (idx < 0 || idx >= list.size()) throw new IllegalArgumentException("invalid index");
                byte[] before = metadata(list.get(idx));
                byte[] backup = Files.readAllBytes(new File(args[2]).toPath());
                if (before == null || !Arrays.equals(before,backup)) throw new SecurityException("backup/current mismatch");
                android.content.AttributionSource shellSource = new android.content.AttributionSource.Builder(Process.myUid())
                        .setPackageName("com.android.shell").build();
                BluetoothDevice.class.getMethod("setAttributionSource", android.content.AttributionSource.class).invoke(list.get(idx),shellSource);
                System.out.println("WRITE_ATTRIBUTION_UID=" + shellSource.getUid());
                System.out.println("WRITE_ATTRIBUTION_PACKAGE=" + shellSource.getPackageName());
                Method set = BluetoothDevice.class.getMethod("setMetadata", int.class, byte[].class);
                boolean result = (Boolean) set.invoke(list.get(idx), CUSTOM_FIELDS, backup);
                byte[] after = metadata(list.get(idx));
                if (!Arrays.equals(backup,after)) throw new IllegalStateException("readback mismatch");
                System.out.println("WRITE_SAME_RESULT=" + result);
                System.out.println("UNCHANGED_SHA256=" + digest(after));
            } else if (args[0].equals("swap-guarded")) {
                if (args.length != 5) throw new IllegalArgumentException("swap-guarded <bond-index> <expected-file> <replacement-file> <expected-sha256>");
                List<BluetoothDevice> list = bonds();
                byte[] expected = Files.readAllBytes(new File(args[2]).toPath());
                byte[] replacement = Files.readAllBytes(new File(args[3]).toPath());
                BluetoothDevice device=null;
                if ("auto".equals(args[1])) {
                    for (BluetoothDevice d : list) {
                        String name=d.getName();
                        if(name!=null && name.contains("AirPods Max") && Arrays.equals(metadata(d), expected)) {
                            if(device!=null)throw new SecurityException("Ambiguous bonds with identical metadata");
                            device=d;
                        }
                    }
                    if(device==null) throw new SecurityException("No AirPods Max matching exact original metadata");
                } else {
                    int idx=Integer.parseInt(args[1]);
                    if(idx<0 || idx>=list.size())throw new IllegalArgumentException("invalid index");
                    device=list.get(idx);
                }
                String name=device.getName();
                if (name==null || !name.contains("AirPods Max"))
                    throw new SecurityException("Target identity is not AirPods Max");
                byte[] current=metadata(device);
                if (current == null || !Arrays.equals(current, expected) || !digest(current).equals(args[4]))
                    throw new SecurityException("Unexpected current metadata; refusing any write");
                if (replacement.length > 2048 || replacement.length == 0)
                    throw new SecurityException("Invalid replacement length");
                android.content.AttributionSource source = new android.content.AttributionSource.Builder(Process.myUid())
                        .setPackageName("com.android.shell").build();
                BluetoothDevice.class.getMethod("setAttributionSource", android.content.AttributionSource.class)
                        .invoke(device, source);
                Method set = BluetoothDevice.class.getMethod("setMetadata", int.class, byte[].class);
                boolean ok = (Boolean) set.invoke(device, CUSTOM_FIELDS, replacement);
                byte[] after = metadata(device);
                boolean verified = Arrays.equals(replacement, after);
                System.out.println("WRITE_RESULT=" + ok);
                System.out.println("READBACK_MATCH=" + verified);
                System.out.println("AFTER_SHA256=" + (after == null ? "<NULL>" : digest(after)));
                if (!verified) throw new IllegalStateException("Bluetooth returned incorrect metadata after write");
            } else {
                throw new IllegalArgumentException("Commands: list | inspect <index> | backup <index> <path>");
            }
        } catch (Exception ex) {
            System.err.println("PROBE_FAILED=" + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
