package dev.airpodsnative.companion;

import android.annotation.SuppressLint;
import android.content.Context;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.media.AudioManager;
import android.media.AudioDeviceInfo;
import android.util.Log;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/** Minimal P0 implementation of CAPod GPLv3 AAP L2CAP and ANC state wire protocol.
 * Real state only; no optimistic UI value. In this first iteration, connection
 * is read-only. See CAPod's DefaultAapDeviceProfile / AapConnection.
 */
public final class AapController {
    public static final String TAG="AirPodsAAP";
    private static final int PSM=0x1001;
    private static final byte[] HANDSHAKE = {0,0,4,0,1,0,2,0,0,0,0,0,0,0,0,0};
    private static final byte[] NOTIFY1 = {4,0,4,0,15,0,(byte)255,(byte)255,(byte)239,(byte)255};
    private static final byte[] NOTIFY2 = {4,0,4,0,15,0,(byte)255,(byte)255,(byte)255,(byte)255};
    private static final byte[] INIT_EXT = {4,0,4,0,77,0,(byte)215,0,0,0,0,0,0,0};
    private static final int[] MODE_TO_WIRE={2,1,4,3}; // Cancellation, Off, Adaptive, Transparency
    public interface Listener {void onMode(int index);}
    public interface FeatureListener {void onFeature(int controlId, int value);}
    public interface SessionListener {void onConnectionChanged(boolean active);}
    public interface WearListener {void onWear(String address,long generation,int primary,int secondary);}
    private final CopyOnWriteArrayList<WearListener> wearListeners=new CopyOnWriteArrayList<>();
    private int wearPrimary=-1,wearSecondary=-1;
    public synchronized void addWearListener(WearListener l){
        if(l==null)return;
        wearListeners.addIfAbsent(l);
        // Seed only this live session; enabling while already worn must not
        // miss the first removal, nor synthesize an action when already off.
        if(connected && wearPrimary>=0 && wearSecondary>=0)
            l.onWear(policy.target(),policy.generation(),wearPrimary,wearSecondary);
    }
    public void removeWearListener(WearListener l){wearListeners.remove(l);}
    public synchronized boolean isCurrentSession(String address,long generation){
        return connected && policy.current(generation,address);
    }
    // CAPod GPLv3 AAP known safe controls. Only write device-reported settings.
    public static final int CONVERSATION_AWARENESS=0x28;
    public static final int PERSONALIZED_VOLUME=0x26;
    public static final int EAR_DETECTION=0x0a;
    public static final int ALLOW_OFF=0x34;
    public static final int ADAPTIVE_NOISE=0x2e;
    public static final int LISTENING_MODE_CYCLE=0x1a;
    public static final int MICROPHONE_MODE=0x01;
    public static final int PRESS_SPEED=0x17;
    public static final int HOLD_DURATION=0x18;
    public static final int CHIME_VOLUME=0x1f;
    private static final Set<Integer> SUPPORTED_CONTROLS=Set.of(
        CONVERSATION_AWARENESS,PERSONALIZED_VOLUME,EAR_DETECTION,ALLOW_OFF,
        ADAPTIVE_NOISE,LISTENING_MODE_CYCLE,MICROPHONE_MODE,PRESS_SPEED,HOLD_DURATION,CHIME_VOLUME);

    private static volatile AapController singleton;
    public static synchronized AapController instance() {
        if (singleton==null) singleton=new AapController();
        return singleton;
    }
    private BluetoothSocket socket;
    private Thread reader;
    private final ConnectionPolicy policy = new ConnectionPolicy();
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "max2-reconnect");thread.setDaemon(true);return thread;
    });
    private ScheduledFuture<?> retry;
    private volatile Context applicationContext;
    private boolean routeMonitorStarted;
    private volatile AudioManager connectionAudio;
    private volatile Set<String> audioConnections=java.util.Collections.emptySet();
    public void initialize(Context context){
        // System-service initialization can wait on the main thread. Never do it
        // while holding our controller lock (the UI also needs that lock).
        Context app=context.getApplicationContext();
        AudioManager audio=(AudioManager)app.getSystemService(Context.AUDIO_SERVICE);
        synchronized(this){
            applicationContext=app;
            connectionAudio=audio;
            if(!routeMonitorStarted){
                routeMonitorStarted=true;
                reconnectExecutor.scheduleWithFixedDelay(this::checkAudioConnection,0,1000,TimeUnit.MILLISECONDS);
            }
        }
    }
    // A merely bonded target must never cause a new Bluetooth link. Read existing
    // audio endpoints only, outside controller locks; unknown state fails closed.
    private boolean hasAudioConnection(String address){
        return address!=null && audioConnections.contains(address.toUpperCase(java.util.Locale.ROOT));
    }
    private void checkAudioConnection(){
        Set<String> available=new java.util.HashSet<>();
        try {
            AudioManager audio=connectionAudio;
            if(audio!=null)for(AudioDeviceInfo device:audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
                if(device.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    || device.getType()==AudioDeviceInfo.TYPE_BLE_HEADSET){
                    String address=device.getAddress();
                    if(address!=null)available.add(address.toUpperCase(java.util.Locale.ROOT));
                }
        }catch(RuntimeException unavailable){ /* Unknown ownership fails closed. */ }
        synchronized(this){
            audioConnections=java.util.Collections.unmodifiableSet(available);
            String address=policy.target();
            if(address==null || hasAudioConnection(address))return;
            BluetoothSocket old=socket;
            if(old!=null){
                Log.i(TAG,"AAP_RELEASE_AUDIO_DISCONNECTED");
                clearDeviceState();
                awaitingReadback.clear();
                socket=null;
                try{old.close();}catch(Exception ignored){}
            }
        }
    }
    private volatile boolean running;
    private volatile boolean connected;
    private volatile int lastObservedMode=-1;
    private volatile int batteryPercent=-1;
    private volatile int batteryCharging=-1; // -1 unknown, 0 not charging, 1 charging
    private volatile Listener listener;
    private final Map<Integer,Integer> observed=new ConcurrentHashMap<>();
    private final Map<Integer,Integer> pending=new ConcurrentHashMap<>();
    /** Real readback pending across one controlled L2CAP control-channel reconnect.
     * Apple may persist 0x26/0x28 writes without an immediate unsolicited control ACK.
     * An AAP message observed after our next session handshake is the actual receipt.
     */
    private static final class PendingSetting {
        final int settingId, expected, original;
        final long generation;
        volatile boolean reconnectRequested;
        volatile long reconnectStartedAtMs;
        PendingSetting(int id,int expected,int original,long generation){
            this.settingId=id;this.expected=expected;this.original=original;this.generation=generation;
        }
    }
    private final Map<Integer,PendingSetting> awaitingReadback=new ConcurrentHashMap<>();
    public boolean isVerifyingSetting(int controlId){return awaitingReadback.containsKey(controlId);}
    /**
     * Presentation-only continuity for a planned, same-bond AAP readback reconnect.
     * This never claims that the control channel is connected, and never grants
     * permission to send commands. An actual audio-route loss fails closed.
     */
    public boolean isSettingsReadbackRefreshing(){
        String address=policy.target();
        if(address==null || !hasAudioConnection(address))return false;
        long now=TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        for(PendingSetting receipt:awaitingReadback.values()){
            long elapsed=now-receipt.reconnectStartedAtMs;
            if(receipt.reconnectRequested && receipt.reconnectStartedAtMs>0
                && elapsed>=0 && elapsed<=3000
                && policy.current(receipt.generation,address))return true;
        }
        return false;
    }

    private final CopyOnWriteArrayList<FeatureListener> featureListeners=new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<SessionListener> sessionListeners=new CopyOnWriteArrayList<>();
    public void addSessionListener(SessionListener l){if(l!=null)sessionListeners.addIfAbsent(l);}
    public void removeSessionListener(SessionListener l){sessionListeners.remove(l);}
    private void notifySession(boolean active){
        for(SessionListener l:sessionListeners)try{l.onConnectionChanged(active);}catch(Exception e){Log.w(TAG,"session listener",e);}
    }
    private volatile int pendingMode=-1;
    public void setListener(Listener next) {listener=next; }
    public void addFeatureListener(FeatureListener next) { if(next!=null)featureListeners.addIfAbsent(next); }
    public void removeFeatureListener(FeatureListener next) { featureListeners.remove(next); }
    public int getSetting(int controlId) {return observed.getOrDefault(controlId,-1);}
    public boolean hasSetting(int controlId) {return connected && observed.containsKey(controlId);}
    public Map<Integer,Integer> snapshot() {return new java.util.HashMap<>(observed);}
    public static boolean isBooleanControl(int id) {return id==CONVERSATION_AWARENESS || id==PERSONALIZED_VOLUME || id==EAR_DETECTION || id==ALLOW_OFF;}
    public static boolean isSupportedControl(int id) {return SUPPORTED_CONTROLS.contains(id);}
    private void notifyFeature(int id,int value) {
        for(FeatureListener l:featureListeners)try {l.onFeature(id,value);}catch(Exception ex){Log.w(TAG,"Listener error",ex);}
    }
    public boolean isVerifyingMode(){return pendingMode>=0;}
    public boolean isConnected(){return connected;}
    public int lastObservedMode(){return lastObservedMode;}
    public int batteryPercent(){return batteryPercent;}
    public int batteryCharging(){return batteryCharging;}

    @SuppressLint("MissingPermission")
    private BluetoothDevice target(String address) {
        if (!ConnectionPolicy.DeviceAddress.valid(address))
            throw new IllegalArgumentException("Missing or invalid target Bluetooth address");
        BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();
        if (a==null)throw new IllegalStateException("no Bluetooth adapter");
        BluetoothDevice candidate=a.getRemoteDevice(address);
        if(candidate.getBondState()!=BluetoothDevice.BOND_BONDED)
            throw new IllegalStateException("Target no longer bonded");
        Context app=applicationContext;
        if(app==null || !DeviceIdentityStore.trustedAndBonded(app,address))
            throw new SecurityException("Target bond has not been explicitly approved as Max 2");
        Log.i(TAG,"TARGET_APPROVED_MAX2_BOND=true");
        return candidate;
    }

    @SuppressLint("MissingPermission")
    private BluetoothSocket publicSocket(BluetoothDevice device) throws Exception {
        Class<?> settingsClass=Class.forName("android.bluetooth.BluetoothSocketSettings");
        Class<?> builderClass=Class.forName("android.bluetooth.BluetoothSocketSettings$Builder");
        Object builder=builderClass.getDeclaredConstructor().newInstance();
        builderClass.getMethod("setSocketType",int.class).invoke(builder,3);
        builderClass.getMethod("setL2capPsm",int.class).invoke(builder,PSM);
        builderClass.getMethod("setAuthenticationRequired",boolean.class).invoke(builder,false);
        builderClass.getMethod("setEncryptionRequired",boolean.class).invoke(builder,false);
        Object settings=builderClass.getMethod("build").invoke(builder);
        return (BluetoothSocket) BluetoothDevice.class.getMethod("createUsingSocketSettings",settingsClass).invoke(device,settings);
    }

    /** A single desired Max 2 bond. Reconnects are bounded and serialized per generation. */
    public synchronized void connectReadOnlyAsync(String address) {
        if (!ConnectionPolicy.DeviceAddress.valid(address)) return;
        Context app=applicationContext;
        if(app==null || !DeviceIdentityStore.trustedAndBonded(app,address)) {
            Log.w(TAG,"REFUSED_UNAPPROVED_BOND");return;
        }
        boolean same=policy.target()!=null && policy.target().equalsIgnoreCase(address);
        if (same && (running || retry != null)) return;
        long token=policy.select(address);
        if(!same){
            awaitingReadback.clear();
            if(retry!=null){retry.cancel(false);retry=null;}
            BluetoothSocket previous=socket;socket=null;
            if(previous!=null)try{previous.close();}catch(Exception ignored){}
            clearDeviceState();
        }
        startAttempt(address,token);
    }
    private synchronized void startAttempt(String address,long token) {
        if(!policy.current(token,address))return;
        retry=null;
        if(!hasAudioConnection(address)){
            // Poll local state without contacting the headset. Repeated UI calls
            // cannot bypass this gate, including on the inactive second phone.
            retry=reconnectExecutor.schedule(()->startAttempt(address,token),2000,TimeUnit.MILLISECONDS);
            return;
        }
        running=true;
        reader=new Thread(()->runReadOnly(address,token),"aap-max2-reader");
        reader.setDaemon(true);
        reader.start();
    }
    private synchronized void clearDeviceState(){
        boolean wasConnected=connected;
        connected=false;lastObservedMode=-1;pendingMode=-1;
        wearPrimary=-1;wearSecondary=-1;
        // Session observations become invalid on disconnect. Pending setting
        // readback survives only the controlled reconnect for the *same* bond.
        observed.clear();pending.clear();batteryPercent=-1;batteryCharging=-1;
        if(wasConnected)notifySession(false);
    }
    private synchronized void retryIfCurrent(String address,long token) {
        if (!policy.current(token,address))return;
        long delay=policy.nextDelay(token,address);
        if(delay<0)return;
        Log.i(TAG,"AAP_RECONNECT_IN_MS="+delay);
        retry=reconnectExecutor.schedule(()->startAttempt(address,token), delay, TimeUnit.MILLISECONDS);
    }
    // Public factory first; version-dependent reflection fallback is exception-guarded.
    // API 37 hardware acceptance covers the current path, not future platform support.
    @SuppressLint("SoonBlockedPrivateApi")
    private void runReadOnly(String address,long token) {
        BluetoothSocket local=null;
        try {
            Log.i(TAG,"CONNECT_BEGIN");
            BluetoothDevice d=target(address);
            try {
                local=publicSocket(d);
                Log.i(TAG,"SOCKET_FACTORY=PUBLIC_API");
            } catch (Exception publicFailure) {
                Throwable why=(publicFailure instanceof java.lang.reflect.InvocationTargetException
                    && publicFailure.getCause()!=null) ? publicFailure.getCause() : publicFailure;
                Log.i(TAG,"PUBLIC_API_FAILED="+why.getClass().getSimpleName());
                try {
                    Method hidden=BluetoothDevice.class.getDeclaredMethod("createInsecureL2capSocket",int.class);
                    local=(BluetoothSocket)hidden.invoke(d,PSM);
                    Log.i(TAG,"SOCKET_FACTORY=HIDDEN_DIRECT");
                } catch (Exception directFailure) {
                    Log.i(TAG,"HIDDEN_DIRECT_FAILED="+directFailure.getClass().getSimpleName());
                    ArtBypass.setExemptions("Landroid/bluetooth/");
                    Method hidden=BluetoothDevice.class.getDeclaredMethod("createInsecureL2capSocket",int.class);
                    local=(BluetoothSocket)hidden.invoke(d,PSM);
                    Log.i(TAG,"SOCKET_FACTORY=HIDDEN_BYPASS");
                }
            }
            synchronized(this) {
                if(!policy.current(token,address))return;
                if(!hasAudioConnection(address))return;
                socket=local;
            }
            local.connect();
            synchronized(this){
                if(!policy.current(token,address) || socket!=local || !hasAudioConnection(address))return;
                connected=true;
                final BluetoothSocket established=local;
                // A fleeting connection is not recovery. Preserve backoff across
                // flaps; reset only after a full minute on this exact socket.
                reconnectExecutor.schedule(()->{
                    synchronized(AapController.this){
                        if(policy.current(token,address) && connected && socket==established
                            && hasAudioConnection(address))policy.succeeded(token,address);
                    }
                },60,TimeUnit.SECONDS);
                notifySession(true);
            }
            Log.i(TAG,"SOCKET_CONNECTED");
            local.getOutputStream().write(HANDSHAKE);
            local.getOutputStream().write(NOTIFY1);
            local.getOutputStream().write(NOTIFY2);
            local.getOutputStream().write(INIT_EXT);
            local.getOutputStream().flush();
            Log.i(TAG,"AAP_HANDSHAKE_SENT");
            byte[] buf=new byte[2048];
            int packets=0;
            while(policy.current(token,address)){
                int n=local.getInputStream().read(buf);
                if (n<0) break;
                // Serialize the generation check and publication with stop/selection.
                // Otherwise a switch between the check and assignment can publish
                // the old headset's values into the new session's empty cache.
                synchronized(this) {
                if(!policy.current(token,address))break;
                packets++;
                // CAPod GPLv3: AAP message type 0x0004 BatteryInfo:
                // count at payload[0], each entry is type,reserved,percent,charging,reserved.
                if(n>=12 && AapFrameValidator.message(buf,n,4)) {
                    int count=u8(buf,6);
                    for(int i=0;i<count && 7+i*5+5<=n;i++) {
                        int start=7+i*5;
                        int kind=u8(buf,start);
                        int pct=u8(buf,start+2);
                        if((kind==1 || kind==2 || kind==4) && pct<=100) {
                            // AirPods Max should report a single battery (kind 0x01).
                            // Never sum two per-ear battery entries for a Max device.
                            if(kind==1 || batteryPercent<0) {
                                batteryPercent=pct;
                                int c=u8(buf,start+3);
                                batteryCharging=(c==1 || c==5)?1:(c==2?0:-1);
                                Log.i(TAG,"BATTERY_OBSERVED_PERCENT="+pct);
                            }
                        }
                    }
                }
                // Read-only diagnostics for physical acceptance. No audio or
                // playback side effects and no raw payload/device identifiers.
                if(AapFrameValidator.message(buf,n,0x4b)) {
                    int status=AapFrameValidator.conversationStatus(buf,n);
                    if(status>=0)Log.i(TAG,"CONVERSATION_STATUS_OBSERVED="+status);
                    else Log.w(TAG,"CONVERSATION_FRAME_UNRECOGNIZED_LENGTH="+n);
                }
                if(AapFrameValidator.message(buf,n,6) && n>=8) {
                    int primary=u8(buf,6),secondary=u8(buf,7);
                    wearPrimary=primary;wearSecondary=secondary;
                    Log.i(TAG,"WEAR_STATUS_OBSERVED_PRIMARY="+primary+" SECONDARY="+secondary);
                    for(WearListener l:wearListeners)try{l.onWear(address,token,primary,secondary);}
                    catch(Exception ex){Log.w(TAG,"wear listener",ex);}
                }
                if (AapFrameValidator.setting(buf,n)) {
                    int controlId=u8(buf,6);
                    int wireValue=u8(buf,7);
                    if (controlId==13) {
                        int mode=modeFromWire(wireValue);
                        if(mode>=0) {
                            lastObservedMode=mode;
                            Log.i(TAG,"ANC_OBSERVED_MODE_INDEX="+mode);
                            if(pendingMode==mode) {
                                Log.i(TAG,"ANC_COMMAND_CONFIRMED_INDEX="+mode);
                                pendingMode=-1;
                            }
                            Listener l=listener;
                            if(l!=null)l.onMode(mode);
                        }
                    } else if (SUPPORTED_CONTROLS.contains(controlId)) {
                        if(isBooleanControl(controlId) && wireValue!=1 && wireValue!=2)continue;
                        observed.put(controlId,wireValue);
                        Log.i(TAG,"SETTING_OBSERVED_ID="+controlId+" VALUE="+wireValue);
                        Integer expected=pending.get(controlId);
                        if(expected!=null && expected==wireValue && pending.remove(controlId,expected))
                            Log.i(TAG,"SETTING_COMMAND_CONFIRMED_ID="+controlId+" VALUE="+wireValue);
                        PendingSetting verifying=awaitingReadback.get(controlId);
                        if(verifying!=null && verifying.generation==token){
                            if(verifying.expected==wireValue){
                                if(awaitingReadback.remove(controlId,verifying))
                                    Log.i(TAG,"SETTING_READBACK_VERIFIED_ID="+controlId+" VALUE="+wireValue);
                            }else if(verifying.reconnectRequested){
                                if(awaitingReadback.remove(controlId,verifying))
                                    Log.w(TAG,"SETTING_READBACK_MISMATCH_ID="+controlId+" EXPECTED="+verifying.expected+" ACTUAL="+wireValue);
                            }
                        }
                        notifyFeature(controlId,wireValue);
                    }
                }
                if (n>=2 && packets<=4) Log.i(TAG,"AAP_FRAME type="+(u8(buf,0)|u8(buf,1)<<8)+" len="+n);
                }
            }
        } catch (Exception ex) {
            Throwable cause=(ex instanceof java.lang.reflect.InvocationTargetException && ex.getCause()!=null)
                ? ex.getCause() : ex;
            Log.e(TAG,"CONNECTION_ERROR="+cause.getClass().getSimpleName()+": "+cause.getMessage());
        } finally {
            try {if(local!=null)local.close();}catch(Exception ignored){}
            synchronized(this){
                if(policy.current(token,address)){
                    clearDeviceState();
                    running=false;
                    socket=null;
                    retryIfCurrent(address,token);
                }
            }
            Log.i(TAG,"DISCONNECTED");
        }
    }
    private static int u8(byte[] v,int i){return v[i]&255;}
    private static int modeFromWire(int v){
        for(int i=0;i<MODE_TO_WIRE.length;i++)if(MODE_TO_WIRE[i]==v)return i;
        return -1;
    }
    public synchronized boolean sendMode(int index) {
        if(index<0 || index>=MODE_TO_WIRE.length){
            Log.e(TAG,"INVALID_ANC_MODE_INDEX="+index);
            return false;
        }
        if(!connected || socket==null || lastObservedMode<0){
            Log.e(TAG,"ANC_WRITE_REJECTED_NOT_READY");
            return false;
        }
        if(index==lastObservedMode){
            Log.i(TAG,"ANC_ALREADY_IN_MODE_INDEX="+index);
            Listener l=listener;
            if(l!=null)l.onMode(index);
            return true;
        }
        pendingMode=index;
        final int targetMode=index;
        final long generation=policy.generation();
        reconnectExecutor.schedule(()->{
            synchronized(AapController.this){
                if(policy.generation()==generation && pendingMode==targetMode){
                    pendingMode=-1;
                    Log.w(TAG,"ANC_ACK_TIMEOUT_INDEX="+targetMode);
                }
            }
        }, 5000, TimeUnit.MILLISECONDS);
        try {
            byte[] command={(byte)0x04,0,0x04,0,0x09,0,0x0d,(byte)MODE_TO_WIRE[index],0,0,0};
            socket.getOutputStream().write(command);
            socket.getOutputStream().flush();
            Log.i(TAG,"ANC_WRITE_SENT_INDEX="+index);
            return true;
        } catch (Exception e){
            pendingMode=-1;
            Log.e(TAG,"ANC_WRITE_FAILED="+e.getClass().getSimpleName());
            return false;
        }
    }
    /** Writes only settings previously observed from the headphone itself.
     * Confirmation requires a matching device readback: not just socket write().
     */
    public synchronized boolean sendSetting(int controlId,int wireValue) {
        Integer original=observed.get(controlId);
        if(!SUPPORTED_CONTROLS.contains(controlId) || original==null || !connected || socket==null){
            Log.w(TAG,"SETTING_WRITE_REJECTED_NOT_SUPPORTED_ID="+controlId);
            return false;
        }
        if(!awaitingReadback.isEmpty()){
            // One controlled auxiliary-channel reconnect at a time.
            Log.w(TAG,"SETTING_WRITE_REJECTED_VERIFY_PENDING_ID="+controlId);
            return false;
        }
        if(isBooleanControl(controlId) && wireValue!=1 && wireValue!=2)return false;
        if(controlId==ADAPTIVE_NOISE && (wireValue<0 || wireValue>100))return false;
        if(controlId==LISTENING_MODE_CYCLE && (wireValue<0 || wireValue>15))return false;
        if(controlId==MICROPHONE_MODE && (wireValue<0 || wireValue>2))return false;
        if((controlId==PRESS_SPEED || controlId==HOLD_DURATION) && (wireValue<0 || wireValue>2))return false;
        if(controlId==CHIME_VOLUME && (wireValue<15 || wireValue>100))return false;
        if(wireValue==original){
            Log.i(TAG,"SETTING_ALREADY_OBSERVED_ID="+controlId+" VALUE="+wireValue);
            return true;
        }
        final long token=policy.generation();
        final PendingSetting receipt=new PendingSetting(controlId,wireValue,original,token);
        awaitingReadback.put(controlId,receipt);
        try {
            byte[] cmd={4,0,4,0,9,0,(byte)controlId,(byte)wireValue,0,0,0};
            pending.put(controlId,wireValue);
            socket.getOutputStream().write(cmd);
            socket.getOutputStream().flush();
            Log.i(TAG,"SETTING_WRITE_SENT_ID="+controlId+" VALUE="+wireValue+" BASELINE="+original);
            // Some Max2 settings emit no unsolicited ACK. Reconnect only the
            // auxiliary AAP socket once to elicit an independent settings snapshot.
            reconnectExecutor.schedule(()->requestSettingReadback(receipt),1200,TimeUnit.MILLISECONDS);
            reconnectExecutor.schedule(()->expireSettingReadback(receipt),30000,TimeUnit.MILLISECONDS);
            return true;
        } catch(Exception ex) {
            pending.remove(controlId);
            awaitingReadback.remove(controlId,receipt);
            Log.e(TAG,"SETTING_WRITE_FAILED_ID="+controlId+" ERROR="+ex.getClass().getSimpleName());
            return false;
        }
    }
    private synchronized void requestSettingReadback(PendingSetting receipt){
        if(awaitingReadback.get(receipt.settingId)!=receipt ||
            receipt.generation!=policy.generation())return;
        receipt.reconnectStartedAtMs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        receipt.reconnectRequested=true;
        BluetoothSocket toClose=socket;
        if(toClose==null){
            Log.i(TAG,"SETTING_READBACK_AWAITING_CONNECTION_ID="+receipt.settingId);
            return;
        }
        Log.i(TAG,"SETTING_READBACK_RECONNECT_REQUESTED_ID="+receipt.settingId);
        try{toClose.close();}catch(IOException ex){
            Log.w(TAG,"SETTING_READBACK_CLOSE_ERROR_ID="+receipt.settingId);
        }
        // The reader's finally block owns state invalidation and reconnection.
    }
    private void expireSettingReadback(PendingSetting receipt){
        if(receipt.generation!=policy.generation())return;
        if(awaitingReadback.remove(receipt.settingId,receipt)){
            pending.remove(receipt.settingId);
            Log.w(TAG,"SETTING_READBACK_UNRESOLVED_ID="+receipt.settingId+" EXPECTED="+receipt.expected);
        }
    }
    public boolean sendBoolean(int controlId,boolean enabled) {
        return isBooleanControl(controlId) && sendSetting(controlId,enabled?1:2);
    }
    public synchronized boolean sendAdaptiveNoiseStrength(int uiLevel) {
        if(lastObservedMode!=2 || uiLevel<0||uiLevel>100)return false;
        return sendSetting(ADAPTIVE_NOISE,100-uiLevel);
    }
    public synchronized void stop() {
        policy.stop();
        awaitingReadback.clear();
        if(retry!=null){retry.cancel(false);retry=null;}
        running=false;
        BluetoothSocket old=socket;socket=null;
        clearDeviceState();
        try{if(old!=null)old.close();}catch(Exception ignored){}
    }
}
