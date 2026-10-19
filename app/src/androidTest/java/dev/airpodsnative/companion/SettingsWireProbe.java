package dev.airpodsnative.companion;

import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import dalvik.system.PathClassLoader;
import org.json.JSONObject;
import java.util.List;

/** Read-only interoperability test against this phone's installed Settings code.
 * Does not bind services, write metadata, connect AAP, or send media commands. */
final class SettingsWireProbe {
    static final String PREFIX="com.android.settingslib.bluetooth.devicesettings.";
    static void require(boolean value,String message) {if(!value)throw new IllegalStateException(message);}
    static Object get(Object object,String method)throws Exception {
        return object.getClass().getMethod(method).invoke(object);
    }
    static Object decode(ClassLoader loader,String name,Parcelable source)throws Exception {
        Parcel parcel=Parcel.obtain();
        try {
            source.writeToParcel(parcel,0);parcel.setDataPosition(0);
            Parcelable.Creator<?> creator=(Parcelable.Creator<?>)loader.loadClass(PREFIX+name).getField("CREATOR").get(null);
            Object result=creator.createFromParcel(parcel);
            require(parcel.dataAvail()==0,name+" did not consume exact payload");
            return result;
        } finally {parcel.recycle();}
    }
    static JSONObject run(Context context)throws Exception {
        android.content.pm.ApplicationInfo app=context.getPackageManager().getApplicationInfo("com.android.settings",0);
        String paths=app.sourceDir;
        if(app.splitSourceDirs!=null)for(String split:app.splitSourceDirs)paths+=java.io.File.pathSeparator+split;
        ClassLoader loader=new PathClassLoader(paths,context.getClassLoader());
        Object config=decode(loader,"DeviceSettingsConfig",new AirPodsSettingsService.Config(context));
        List<?> main=(List<?>)get(config,"getMainContentItems");
        require(main.size()>1,"platform preferences must be preserved");
        java.util.Set<String> keys=new java.util.HashSet<>();
        Object item=null;int controls=0;
        for(Object candidate:main){
            String key=(String)get(candidate,"getPreferenceKey");
            if(key!=null)require(keys.add(key),"unique built-in preference reference");
            else {controls++;if(get(candidate,"getSettingId").equals(1010))item=candidate;}
        }
        require(controls==NativeControlSetting.IDS.length+1,"native controls and advanced entry");
        require(keys.contains("action_buttons"),"native disconnect/forget buttons retained");
        require(keys.contains("general_bluetooth_device_header"),"native header retained");
        require(item!=null,"advanced entry retained");
        require(get(item,"getSettingId").equals(1010),"entry id");
        require(get(item,"getPackageName").equals(context.getPackageName()),"provider package");
        require(get(item,"getClassName").equals(context.getPackageName()+".AirPodsSettingsService"),"provider class");
        require(get(item,"getIntentAction").equals(context.getPackageName()+".BIND_STATE"),"state action");
        require(get(item,"getPreferenceKey")==null,"must be app-provided entry");
        require(((List<?>)get(config,"getMoreSettingsItems")).isEmpty(),"unexpected more settings");
        String[] labels={"Disconnected / not reported","Noise Cancellation","Off","Adaptive","Transparency"};
        for(int mode=-1;mode<4;mode++) {
            Object setting=decode(loader,"DeviceSetting",new AirPodsSettingsService.ProbeSetting(1010,mode));
            require(get(setting,"getSettingId").equals(1010),"state id");
            Object preference=get(setting,"getPreference");
            require(get(preference,"getTitle").equals("Open AirPods settings"),"entry title");
            String summary=(String)get(preference,"getSummary");
            require(summary.contains(mode<0?"unavailable":labels[mode+1]),"observed mode summary");
            require(get(preference,"hasSwitch").equals(false),"summary must not expose switch");
            Intent intent=(Intent)get(get(preference,"getAction"),"getIntent");
            require(intent.getComponent()!=null && intent.getComponent().getClassName().equals(context.getPackageName()+".MainActivity"),"explicit app destination");
        }
        for(int id:NativeControlSetting.IDS){
            boolean toggle=id==NativeControlSetting.PV || id==NativeControlSetting.CA;
            for(int value=-1;value<(id==NativeControlSetting.MODE?4:3);value++){
                if(toggle && value==0)continue;
                Object setting=decode(loader,"DeviceSetting",new NativeControlSetting(id,value,value>=0));
                Object pref=get(setting,"getPreference");
                require(get(pref,"getTitle").toString().startsWith(NativeControlSetting.title(id)),"native title");
                require(get(pref,"isAllowedChangingState").equals(value>=0),"unknown state disabled");
                if(toggle){require(get(pref,"hasSwitch").equals(true),"native switch");require(get(pref,"getChecked").equals(value==1),"observed checked");}
                else require(get(pref,"getState").equals(Math.max(0,value)),"observed selection");
            }
            // Serialize actual installed Settings state objects, then parse our inbound wire adapter.
            Class<?> stateClass=loader.loadClass(PREFIX+(toggle?"ActionSwitchPreferenceState":"MultiTogglePreferenceState"));
            java.lang.reflect.Constructor<?> ctor=stateClass.getDeclaredConstructor(toggle?boolean.class:int.class,android.os.Bundle.class);ctor.setAccessible(true);
            Object prefState=ctor.newInstance(toggle?Boolean.TRUE:Integer.valueOf(1),android.os.Bundle.EMPTY);
            Class<?> wrapper=loader.loadClass(PREFIX+"DeviceSettingState");
            java.lang.reflect.Constructor<?> wc=wrapper.getDeclaredConstructor(int.class,loader.loadClass(PREFIX+"DeviceSettingPreferenceState"),android.os.Bundle.class);wc.setAccessible(true);
            Parcelable state=(Parcelable)wc.newInstance(id,prefState,android.os.Bundle.EMPTY);
            Parcel incoming=Parcel.obtain();
            try {incoming.writeTypedObject(state,0);incoming.setDataPosition(0);NativeControlSetting.Update update=NativeControlSetting.readUpdate(incoming);
                require(update.id==id && update.value==1 && update.type==(toggle?1:2),"actual OEM inbound state");}
            finally {incoming.recycle();}
        }
        decode(loader,"DeviceSettingsProviderServiceStatus",new AirPodsSettingsService.ProviderStatus());
        Object status=decode(loader,"DeviceSettingsConfigServiceStatus",new AirPodsSettingsService.Status());
        require(get(status,"getSuccess").equals(true),"config status");
        return new JSONObject().put("config_and_entry",true).put("five_mode_summaries_and_launch_intents",true)
            .put("provider_status",true).put("preserved_platform_items",keys.size()).put("exact_payload_consumption",true)
            .put("native_control_outbound_and_inbound",true).put("metadata_written",false).put("native_rendering_verified",false);
    }
}
