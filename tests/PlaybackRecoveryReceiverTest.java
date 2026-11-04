import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import dev.airpodsnative.companion.PlaybackRecoveryReceiver;
import dev.airpodsnative.companion.WearPauseService;

/** Executes production receiver branches; framework cross-UID delivery is a device check. */
public final class PlaybackRecoveryReceiverTest {
    private static final String TARGET="AA:BB:CC:DD:EE:01";
    private static final class FakeContext extends Context implements SharedPreferences {
        boolean enabled=true, permitted=true, failStart;
        String selected=TARGET;
        int starts;
        public SharedPreferences getSharedPreferences(String name,int mode) {
            check("selected_airpods".equals(name), "unexpected preferences"); return this;
        }
        public String getString(String key,String fallback) {
            check("bond_address".equals(key), "unexpected address key"); return selected;
        }
        public boolean getBoolean(String key,boolean fallback) {
            check(WearPauseService.PREF.equals(key), "unexpected opt-in key"); return enabled;
        }
        public int checkSelfPermission(String permission) {
            check(Manifest.permission.BLUETOOTH_CONNECT.equals(permission), "unexpected permission");
            return permitted?PackageManager.PERMISSION_GRANTED:PackageManager.PERMISSION_DENIED;
        }
        public Object startForegroundService(Intent intent) {
            check(intent.component==WearPauseService.class, "wrong service"); starts++;
            if(failStart)throw new IllegalStateException("background start rejected");
            return null;
        }
    }
    private static Intent acl(String address) {
        return new Intent(BluetoothDevice.ACTION_ACL_CONNECTED)
            .putExtra(BluetoothDevice.EXTRA_DEVICE,new BluetoothDevice(address));
    }
    private static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }
    private static void expect(Intent intent,int starts,java.util.function.Consumer<FakeContext> setup) {
        FakeContext context=new FakeContext();setup.accept(context);
        new PlaybackRecoveryReceiver().onReceive(context,intent);
        check(context.starts==starts,"unexpected starts for "+(intent==null?null:intent.getAction()));
        check(context.enabled,"receiver changed opt-in");
    }
    public static void main(String[] args) {
        for(int api:new int[]{33,34,35,36,37}) {
            Build.VERSION.SDK_INT=api;
            expect(new Intent(Intent.ACTION_BOOT_COMPLETED),1,c->{});
            expect(new Intent(Intent.ACTION_MY_PACKAGE_REPLACED),1,c->{});
            expect(acl(TARGET.toLowerCase()),1,c->{});
            expect(acl("AA:BB:CC:DD:EE:02"),0,c->{});
            expect(acl(TARGET),0,c->c.selected=null);
            expect(new Intent(BluetoothDevice.ACTION_ACL_CONNECTED),0,c->{});
            expect(new Intent(BluetoothAdapter.ACTION_STATE_CHANGED).putExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.STATE_ON),1,c->{});
            expect(new Intent(BluetoothAdapter.ACTION_STATE_CHANGED).putExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.STATE_OFF),0,c->{});
            expect(new Intent(BluetoothAdapter.ACTION_STATE_CHANGED),0,c->{});
            expect(new Intent("unsupported"),0,c->{});
            expect(new Intent((String)null),0,c->{});
            expect(null,0,c->{});
            expect(acl(TARGET),0,c->c.permitted=false);
            expect(new Intent(Intent.ACTION_BOOT_COMPLETED),0,c->c.permitted=false);
            expect(acl(TARGET),1,c->c.failStart=true);
            FakeContext disabled=new FakeContext();disabled.enabled=false;
            new PlaybackRecoveryReceiver().onReceive(disabled,new Intent(Intent.ACTION_BOOT_COMPLETED));
            new PlaybackRecoveryReceiver().onReceive(disabled,acl(TARGET));
            check(disabled.starts==0&&!disabled.enabled,"opt-out must remain off");
        }
        Build.VERSION.SDK_INT=32;
        expect(new Intent(Intent.ACTION_BOOT_COMPLETED),0,c->{});
        expect(new Intent(Intent.ACTION_MY_PACKAGE_REPLACED),0,c->{});
        expect(acl(TARGET),0,c->{});
        expect(new Intent(BluetoothAdapter.ACTION_STATE_CHANGED).putExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.STATE_ON),0,c->{});
        System.out.println("PASS PlaybackRecoveryReceiver guarded recovery API32-37");
    }
}
