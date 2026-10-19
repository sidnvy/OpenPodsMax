package dev.airpodsnative.companion;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.Map;

/** Explicit, one-setting hardware acceptance runner. No trust, bond, or metadata changes.
 * Run only on the owner-selected phone with its approved Max 2 already connected.
 * Separate test APK; no exported production control interface.
 */
public final class FunctionalAcceptanceRunner extends Instrumentation {
    private Bundle args;
    private AapController controller;
    private String address;
    private final JSONObject receipt = new JSONObject();
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); args=arguments; start(); }
    private void event(String phase, Object value) throws Exception {
        JSONObject e=new JSONObject(); e.put("utc",java.time.Instant.now().toString());e.put("value",value);
        receipt.put(phase,e);
        Bundle progress=new Bundle();progress.putString("stream",phase+" "+e+"\n");sendStatus(0,progress);
    }
    private void fresh() throws Exception {
        controller.stop();
        if(controller.isConnected() || !controller.snapshot().isEmpty() || controller.lastObservedMode()!=-1)
            throw new IllegalStateException("disconnect cache not invalidated");
        controller.connectReadOnlyAsync(address);
        // Observed Max 2 state delivery 20.6 s after handshake; retain a bounded wait.
        long end=SystemClock.elapsedRealtime()+45000;
        while(SystemClock.elapsedRealtime()<end && (!controller.isConnected() || controller.lastObservedMode()<0))
            SystemClock.sleep(100);
        if(!controller.isConnected() || controller.lastObservedMode()<0) throw new IllegalStateException("fresh session unavailable");
        SystemClock.sleep(1800);
    }
    private JSONObject snapshot() throws Exception {
        JSONObject s=new JSONObject();s.put("connected",controller.isConnected());
        s.put("anc",controller.lastObservedMode());s.put("battery",controller.batteryPercent());
        JSONObject settings=new JSONObject();for(Map.Entry<Integer,Integer> e:controller.snapshot().entrySet())settings.put(String.valueOf(e.getKey()),e.getValue());
        s.put("settings",settings);return s;
    }
    private void write(int id,int value) throws Exception {
        boolean sent=id==AapController.ADAPTIVE_NOISE ? controller.sendAdaptiveNoiseStrength(100-value) : controller.sendSetting(id,value);
        if(!sent)throw new IllegalStateException("controller refused setting write");
        // Allow the ordinary unsolicited acknowledgement window. A separate
        // fresh() below remains the required persistence proof.
        SystemClock.sleep(6500);
    }
    @Override public void onStart() {
        int id=-1,original=-1,originalMode=-1;boolean attempted=false,restored=false,modeAttempted=false,modeRestored=false;String status="BLOCKED";
        try {
            if(args.getString("operation", "snapshot").equals("settings_wire_probe")) {
                event("settings_wire",SettingsWireProbe.run(getTargetContext()));
                status="PASS";return;
            }
            if(args.getString("operation", "snapshot").equals("tone_probe")) {
                event("tone",MultiDeviceRouteProbe.tone(getTargetContext(),Integer.parseInt(args.getString("frequency","440"))));
                status="PASS";return;
            }
            if(args.getString("operation", "snapshot").equals("route_probe")) {
                event("route",MultiDeviceRouteProbe.run(getTargetContext()));
                status="PASS";return;
            }
            controller=AapController.instance();controller.initialize(getTargetContext());
            address=getTargetContext().getSharedPreferences("selected_airpods",0).getString("bond_address",null);
            if(!DeviceIdentityStore.trustedAndBonded(getTargetContext(),address))throw new IllegalStateException("approved bonded selection missing");
            event("identity","approved exact bond; identifier omitted");
            fresh();event("before",snapshot());
            String operation=args.getString("operation","snapshot");
            if(operation.equals("snapshot")){status="PASS";}
            else if(operation.equals("alias_roundtrip")) {
                android.bluetooth.BluetoothDevice target=android.bluetooth.BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
                String alias=target.getAlias(),name=target.getName();
                // getAlias may fall back to the hardware name. Without a distinct
                // override we cannot prove restoration of a previously absent alias.
                if(alias==null || alias.isEmpty() || alias.equals(name))
                    throw new IllegalStateException("No distinct alias override; cannot prove exact alias-presence restoration");
                String changed=alias+" · acceptance";
                boolean aliasRestored=false;
                try {
                    receipt.put("alias_write_attempted",true);
                    if(target.setAlias(changed)!=0)throw new IllegalStateException("Temporary alias refused");
                    SystemClock.sleep(500);
                    if(!changed.equals(target.getAlias()))throw new IllegalStateException("Alias readback mismatch");
                    if(!DeviceIdentityStore.trustedAndBonded(getTargetContext(),address))
                        throw new IllegalStateException("Approved identity lost after rename");
                    fresh();event("renamed_identity_snapshot",snapshot());
                    receipt.put("renamed_bond_still_trusted",true);status="PASS";
                } finally {
                    try {
                        target.setAlias(alias);SystemClock.sleep(500);
                        aliasRestored=alias.equals(target.getAlias());
                    } finally {
                        receipt.put("alias_restoration_verified",aliasRestored);
                        if(!aliasRestored)status="FAIL";
                    }
                }
            }
            else if(operation.equals("media_probe")){
                android.media.AudioManager audio=getTargetContext().getSystemService(android.media.AudioManager.class);
                JSONObject media=new JSONObject();
                media.put("route_matches_approved_headset",WearPauseService.mediaRouteMatches(audio,address));
                media.put("music_active",audio.isMusicActive());
                media.put("normal_audio_mode",audio.getMode()==android.media.AudioManager.MODE_NORMAL);
                media.put("pause_option_enabled",getTargetContext().getSharedPreferences("selected_airpods",0).getBoolean(WearPauseService.PREF,false));
                event("media",media);status="PASS";
            }
            else if(operation.equals("roundtrip") || operation.equals("adaptive_roundtrip")){
                id=operation.equals("adaptive_roundtrip")?46:Integer.parseInt(args.getString("control"));
                // Scope is deliberately narrower than the production control API.
                if(id!=46 && id!=26 && id!=23 && id!=24 && id!=31 && id!=10 && id!=38 && id!=40)
                    throw new IllegalArgumentException("control outside acceptance allowlist");
                if(!controller.hasSetting(id))throw new IllegalStateException("device did not report control "+id);
                original=controller.getSetting(id);
                if(operation.equals("adaptive_roundtrip")) {
                    originalMode=controller.lastObservedMode();
                    if(originalMode<0 || originalMode>3)throw new IllegalStateException("ANC baseline missing");
                    receipt.put("original_mode",originalMode);
                    modeAttempted=true;event("adaptive_mode_requested",2);
                    if(originalMode!=2 && !controller.sendMode(2))throw new IllegalStateException("adaptive mode write refused");
                    SystemClock.sleep(2000);fresh();event("adaptive_mode_confirmed",snapshot());
                    if(controller.lastObservedMode()!=2)throw new IllegalStateException("adaptive mode unconfirmed; no strength write");
                }
                int changed;
                if(id==46){if(original<0||original>100)throw new IllegalStateException("invalid baseline");changed=original==100?99:original+1;}
                else if(id==31){if(original<15||original>100)throw new IllegalStateException("invalid baseline");changed=original==15?16:original-1;}
                else if(id==23||id==24){if(original<0||original>2)throw new IllegalStateException("invalid baseline");changed=original==0?1:0;}
                else if(id==26){if(original<0||original>15||Integer.bitCount(original)<2)throw new IllegalStateException("unsafe cycle baseline");changed=original^8;if(Integer.bitCount(changed)<2)throw new IllegalStateException("unsafe alternate cycle");}
                else {if(original!=1&&original!=2)throw new IllegalStateException("invalid boolean baseline");changed=original==1?2:1;}
                receipt.put("control",id);receipt.put("original",original);receipt.put("requested",changed);
                event("write_requested",changed);attempted=true;write(id,changed);
                fresh();event("after",snapshot());
                boolean persisted=controller.getSetting(id)==changed;
                event("restore_requested",original);write(id,original);
                fresh();event("restored",snapshot());restored=controller.getSetting(id)==original;
                status=persisted&&restored?"PASS":"FAIL";
            }else throw new IllegalArgumentException("unknown operation");
        } catch(Exception e){
            status=(attempted || modeAttempted || receipt.optBoolean("alias_write_attempted"))?"FAIL":"BLOCKED";
            try {receipt.put("error",e.getClass().getSimpleName()+": "+e.getMessage());}catch(Exception ignored){}
        } finally {
            if(attempted && !restored){
                try {fresh();if(controller.getSetting(id)!=original)write(id,original);fresh();restored=controller.getSetting(id)==original;event("recovery_restore",snapshot());}
                catch(Exception e){try{receipt.put("restore_error",e.getClass().getSimpleName());}catch(Exception ignored){}}
            }
            if(modeAttempted) {
                try {
                    event("original_mode_restore_requested",originalMode);
                    if(controller.lastObservedMode()!=originalMode && !controller.sendMode(originalMode))
                        throw new IllegalStateException("mode restore refused");
                    SystemClock.sleep(2000);fresh();event("original_mode_restored",snapshot());
                    modeRestored=controller.lastObservedMode()==originalMode;
                }catch(Exception e){try{receipt.put("mode_restore_error",e.getClass().getSimpleName());}catch(Exception ignored){}}
                if(!modeRestored)status="FAIL";
                try{receipt.put("mode_restoration_verified",modeRestored);}catch(Exception ignored){}
            }
            if(controller!=null)controller.stop();
            try{receipt.put("status",status);receipt.put("write_attempted",attempted);receipt.put("restoration_verified",attempted?restored:JSONObject.NULL);}catch(Exception ignored){}
            Bundle result=new Bundle();result.putString("receipt",receipt.toString());finish(status.equals("PASS")?-1:0,result);
        }
    }
}
