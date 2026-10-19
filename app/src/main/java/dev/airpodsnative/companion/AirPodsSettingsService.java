package dev.airpodsnative.companion;

import android.app.Service;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;

/** P0: an inert, accurately marshalled Android 17 Settings config-provider binder. */
public final class AirPodsSettingsService extends Service {
    static final String TAG = "AirPodsNativeService";
    static final String CONFIG_INTERFACE =
        "com.android.settingslib.bluetooth.devicesettings.IDeviceSettingsConfigProviderService";
    static final String CALLBACK_INTERFACE =
        "com.android.settingslib.bluetooth.devicesettings.IGetDeviceSettingsConfigCallback";
    static final String PROVIDER_PACKAGE = "dev.airpodsnative.companion";
    private static volatile IBinder stateListener;
    private static volatile String lastPublished;
    private static volatile android.content.Context contextRef;
    private static final android.os.Handler STATE_HANDLER=new android.os.Handler(android.os.Looper.getMainLooper());
    private static final Runnable REFRESH=new Runnable(){public void run(){
        if(stateListener!=null){sendModeState(AapController.instance().lastObservedMode());STATE_HANDLER.postDelayed(this,500);}
    }};
    static android.content.Context thisContext(){return contextRef;}
    // Rebinds occur when Settings refreshes its screen; use one stable callback.
    /** Native controls plus an optional entry to additional companion features. */
    private static final int OPEN_SETTINGS_ID = 1010;
    private static final AapController.SessionListener SESSION_RELAY = active -> {
        sendModeState(active ? AapController.instance().lastObservedMode() : -1);
    };
    private static void sendReadbackState(ProbeSetting item){
        IBinder listener=stateListener;
        if(listener==null)return;
        Parcel parcel=Parcel.obtain();
        try {
            parcel.writeInterfaceToken(StateBinder.LISTENER_INTERFACE);
            java.util.List<Parcelable> states=NativeControlSetting.observed();
            StringBuilder signature=new StringBuilder().append(item.value);
            for(Parcelable state:states){NativeControlSetting n=(NativeControlSetting)state;signature.append(':').append(n.id).append('/').append(n.value).append('/').append(n.enabled);}
            String current=signature.toString();if(current.equals(lastPublished))return;
            lastPublished=current;
            states.add(item);parcel.writeTypedList(states);
            boolean sent=listener.transact(1,parcel,null,IBinder.FLAG_ONEWAY);
            Log.i(TAG,"SETTING_READBACK_ID="+item.settingId+" SENT="+sent);
        }catch(Exception e){Log.e(TAG,"STATE_READBACK_ERROR="+e.getClass().getSimpleName());}
        finally{parcel.recycle();}
    }
    static void sendModeState(int mode) {
        // Always display the latest read-only summary, including disconnected state.
        sendReadbackState(new ProbeSetting(OPEN_SETTINGS_ID,(mode>=0 && mode<4)?mode:-1));
    }
    @Override public void onCreate() {
        super.onCreate();
        contextRef=getApplicationContext();
        Log.i(TAG, "PROVIDER_CREATED");
    }
    @Override public IBinder onBind(Intent intent) {
        if (intent != null && (PROVIDER_PACKAGE + ".BIND_STATE").equals(intent.getAction())) {
            Log.i(TAG, "STATE_SERVICE_BOUND");
            return new StateBinder();
        }
        Log.i(TAG, "CONFIG_SERVICE_BOUND");
        return new ConfigBinder();
    }
    @Override public void onDestroy() {
        stateListener=null;STATE_HANDLER.removeCallbacks(REFRESH);
        AapController.instance().removeSessionListener(SESSION_RELAY);
        Log.i(TAG, "PROVIDER_DESTROYED");
        super.onDestroy();
    }

    // Outbound wire adapter: the installed OEM Settings CREATOR decodes this payload.
    @android.annotation.SuppressLint("ParcelCreator")
    static final class Status implements Parcelable {
        final boolean success;
        Status() { this(true); }
        Status(boolean value) { success=value; }
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(Parcel p, int flags) {
            p.writeBoolean(success);
            p.writeBundle(Bundle.EMPTY);
        }
    }
    // Outbound wire adapter: the installed OEM Settings CREATOR decodes this payload.
    @android.annotation.SuppressLint("ParcelCreator")
    static final class Item implements Parcelable {
        final int settingId;
        Item(int id) { settingId = id; }
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(Parcel p, int flags) {
            p.writeInt(settingId);
            p.writeString(PROVIDER_PACKAGE);
            p.writeString(PROVIDER_PACKAGE + ".AirPodsSettingsService");
            p.writeString(PROVIDER_PACKAGE + ".BIND_STATE");
            p.writeBoolean(false); // highlighted
            p.writeString(null); // IMPORTANT: null means AppProvidedItem, nonnull means pre-existing built-in preference!
            p.writeBundle(Bundle.EMPTY);
        }
    }
    // Outbound wire adapter: the installed OEM Settings CREATOR decodes this payload.
    @android.annotation.SuppressLint("ParcelCreator")
    static final class Config implements Parcelable {
        final List<Parcelable> items;
        Config() { this(thisContext()); }
        Config(android.content.Context context) {
            java.util.List<Parcelable> controls=new java.util.ArrayList<>();
            for(int id:NativeControlSetting.IDS)controls.add(new Item(id));
            controls.add(new Item(OPEN_SETTINGS_ID));
            items=NativeSettingsLayout.withPlatformItems(context,controls);
        }
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(Parcel p, int flags) {
            // One native entry rather than a second ANC controller in Settings.
            p.writeTypedList(items);
            p.writeTypedList(Collections.emptyList());
            p.writeParcelable(null, flags);  // optional more-settings help item
            p.writeBundle(Bundle.EMPTY);
        }
    }
    // Outbound wire adapter: the installed OEM Settings CREATOR decodes this payload.
    @android.annotation.SuppressLint("ParcelCreator")
    static final class ProviderStatus implements Parcelable {
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(Parcel p, int flags) {
            p.writeBoolean(true);
            p.writeBundle(Bundle.EMPTY);
        }
    }
    // Outbound wire adapter: the installed OEM Settings CREATOR decodes this payload.
    @android.annotation.SuppressLint("ParcelCreator")
    static final class Toggle implements Parcelable {
        private final String label;
        private final int color;
        Toggle(String label, int color) { this.label = label; this.color = color; }
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(Parcel p, int flags) {
            Bitmap bitmap = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(android.graphics.Color.TRANSPARENT);
            android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.scale(3,3);
            android.graphics.Paint ink=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            ink.setColor(android.graphics.Color.WHITE);ink.setStrokeWidth(2);ink.setStyle(android.graphics.Paint.Style.STROKE);
            if(label.equals("Off")){canvas.drawCircle(12,12,8,ink);canvas.drawLine(6,18,18,6,ink);}
            else if(label.equals("Noise Cancellation")){canvas.drawCircle(12,12,7,ink);canvas.drawCircle(12,12,3,ink);}
            else if(label.equals("Transparency")){canvas.drawLine(6,5,6,19,ink);canvas.drawLine(12,2,12,22,ink);canvas.drawLine(18,5,18,19,ink);}
            else if(label.equals("Adaptive")){canvas.drawLine(4,12,8,6,ink);canvas.drawLine(8,6,16,18,ink);canvas.drawLine(16,18,20,12,ink);}
            else {canvas.drawCircle(12,12,8,ink);canvas.drawLine(12,12,12,6,ink);canvas.drawLine(12,12,17,12,ink);}
            p.writeString(label);
            p.writeParcelable(bitmap, flags);
            p.writeBundle(Bundle.EMPTY);
        }
    }
    // Outbound wire adapter: the installed OEM Settings CREATOR decodes this payload.
    @android.annotation.SuppressLint("ParcelCreator")
    static final class ProbeSetting implements Parcelable {
        final int settingId;
        final int value;
        ProbeSetting(int id,int val){settingId=id;value=val;}
        private static String modeName(int id){
            switch(id){
                case 0:return "Noise Cancellation";
                case 1:return "Off";
                case 2:return "Adaptive";
                case 3:return "Transparency";
                default:return "Disconnected / not reported";
            }
        }
        @Override public int describeContents(){return 0;}
        @Override public void writeToParcel(Parcel p,int flags){
            p.writeInt(settingId);
            p.writeBundle(Bundle.EMPTY);
            p.writeInt(1); // ActionSwitchPreference, no duplicated MultiTogglePreference.
            if(settingId == OPEN_SETTINGS_ID){
                p.writeString("Open AirPods settings");
                p.writeString(value>=0
                    ? "Listening mode: " + modeName(value) + " · Advanced controls"
                    : "Listening mode unavailable · Advanced AirPods settings");
                p.writeParcelable(null,flags); // optional icon bitmap
                p.writeInt(1); // DeviceSettingIntentAction (verified OEM SettingsLib)
                Intent launch = new Intent(Intent.ACTION_MAIN);
                launch.setClassName(PROVIDER_PACKAGE,PROVIDER_PACKAGE + ".MainActivity");
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                p.writeParcelable(launch,flags);
                p.writeBundle(Bundle.EMPTY); // action extras
                p.writeBoolean(false); // hasSwitch
                p.writeBoolean(false); // checked (irrelevant)
                p.writeBoolean(true); // isAllowedChangingState (row can open app)
            }
            p.writeBundle(Bundle.EMPTY);
        }
    }
    static final class StateBinder extends Binder {
        private String registeredAddress;
        static final String SERVICE_INTERFACE =
            "com.android.settingslib.bluetooth.devicesettings.IDeviceSettingsProviderService";
        static final String LISTENER_INTERFACE =
            "com.android.settingslib.bluetooth.devicesettings.IDeviceSettingsListener";
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(SERVICE_INTERFACE);
                return true;
            }
            Log.i(TAG, "STATE_TRANSACTION_" + code);
            data.enforceInterface(SERVICE_INTERFACE);
            if (code == 1) {
                if (reply != null) {
                    reply.writeNoException();
                    reply.writeTypedObject(new ProviderStatus(), 0);
                }
                Log.i(TAG, "STATE_STATUS_READY");
                return true;
            }
            if (code == 2) { // registerDeviceSettingsListener
                if (data.readInt() != 0) {
                    registeredAddress=data.readString();  // no Bluetooth address logging
                    data.readBundle(AirPodsSettingsService.class.getClassLoader());
                }
                IBinder listener = data.readStrongBinder();
                if(data.dataAvail()!=0)throw new android.os.BadParcelableException("Unexpected trailing Binder data");
                if (listener == null) throw new RemoteException("listener missing");
                long identity=Binder.clearCallingIdentity();
                try {if(!DeviceIdentityStore.trustedAndBonded(thisContext(),registeredAddress))throw new SecurityException("Unapproved target");}
                finally {Binder.restoreCallingIdentity(identity);}
                lastPublished=null;stateListener=listener;
                STATE_HANDLER.removeCallbacks(REFRESH);STATE_HANDLER.post(REFRESH);
                AapController.instance().addSessionListener(SESSION_RELAY);
                int observed=AapController.instance().lastObservedMode();
                sendModeState(observed);
                Log.i(TAG,"STATE_LISTENER_REGISTERED current_mode="+observed);
                return true;
            }
            if(code==3) {
                stateListener=null;registeredAddress=null;STATE_HANDLER.removeCallbacks(REFRESH);
                AapController.instance().removeSessionListener(SESSION_RELAY);
                return true;
            }
            if(code==4){
                if(data.readInt()!=1)throw new android.os.BadParcelableException("Missing device");
                String address=data.readString();data.readBundle(getClass().getClassLoader());
                NativeControlSetting.Update update=NativeControlSetting.readUpdate(data);
                long identity=Binder.clearCallingIdentity();
                try {
                    String selected=thisContext().getSharedPreferences("selected_airpods",0).getString("bond_address",null);
                    if(address==null || registeredAddress==null || !address.equalsIgnoreCase(registeredAddress)
                        || !address.equalsIgnoreCase(selected) || !DeviceIdentityStore.trustedAndBonded(thisContext(),address))
                        throw new SecurityException("Unapproved setting target");
                    AapController c=AapController.instance();c.connectReadOnlyAsync(address);boolean accepted=false;
                    if(update.id==NativeControlSetting.MODE){if(!c.isVerifyingMode())accepted=c.sendMode(update.value);}
                    else {int ctl=NativeControlSetting.control(update.id);
                        if(!c.isVerifyingSetting(ctl))accepted=c.sendSetting(ctl,update.type==1?(update.value==1?1:2):update.value);}
                    Log.i(TAG,"NATIVE_CONTROL_ID="+update.id+" ACCEPTED="+accepted);
                    lastPublished=null;sendModeState(c.lastObservedMode());
                } finally {Binder.restoreCallingIdentity(identity);}
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }
    static final class ConfigBinder extends Binder {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(CONFIG_INTERFACE);
                return true;
            }
            Log.i(TAG, "CONFIG_TRANSACTION_" + code);
            if (code == 2) { // Android 17: getDeviceSettingsConfigWithOptions
                data.enforceInterface(CONFIG_INTERFACE);
                if (data.readInt() != 0) {
                    String targetAddress=data.readString(); // never log address
                    data.readBundle(AirPodsSettingsService.class.getClassLoader());
                    if (DeviceIdentityStore.trustedAndBonded(AirPodsSettingsService.thisContext(), targetAddress)) {
                        AapController.instance().initialize(AirPodsSettingsService.thisContext());
                        AapController.instance().connectReadOnlyAsync(targetAddress);
                    } else {
                        Log.w(TAG,"Unapproved or unbonded Max 2 device; no AAP connection");
                    }
                }
                IBinder callback = data.readStrongBinder();
                if (data.readInt() != 0) data.readBundle(AirPodsSettingsService.class.getClassLoader());
                if(data.dataAvail()!=0)throw new android.os.BadParcelableException("Unexpected trailing Binder data");
                if (callback == null) throw new RemoteException("config callback missing");
                Parcel callbackParcel = Parcel.obtain();
                try {
                    callbackParcel.writeInterfaceToken(CALLBACK_INTERFACE);
                    Config config=null;
                    try { config=new Config(); }
                    catch (RuntimeException ex) { Log.w(TAG,"CONFIG_PLATFORM_LAYOUT_UNAVAILABLE"); }
                    callbackParcel.writeTypedObject(new Status(config!=null), 0);
                    callbackParcel.writeTypedObject(config, 0);
                    boolean sent = callback.transact(1, callbackParcel, null, IBinder.FLAG_ONEWAY);
                    Log.i(TAG, "CONFIG_CALLBACK_SENT=" + sent);
                } finally {
                    callbackParcel.recycle();
                }
                if (reply != null) reply.writeNoException();
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }
}
