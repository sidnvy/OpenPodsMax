package dev.airpodsnative.companion;

import android.content.Context;

/** Global phone identity opt-in, independent of the selected headset's SBC preference. */
public final class AppleIdentityConfig {
    public static final String PREF="apple_bluetooth_identity";
    public static final String JOURNAL="apple_identity_previous.";
    public static final String STATUS="apple_bluetooth_identity_status";
    private AppleIdentityConfig(){}
    public static boolean enabled(Context context){
        return context.getSharedPreferences("selected_airpods",0).getBoolean(PREF,false);
    }
}
