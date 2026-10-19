package dev.airpodsnative.companion;

import android.bluetooth.*;
import android.content.Context;
import android.media.AudioManager;
import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Read-only per-phone profile/route snapshot. Does not open an AAP socket. */
final class MultiDeviceRouteProbe {
    static JSONObject tone(Context context,int frequency)throws Exception {
        if(frequency!=440 && frequency!=660)throw new IllegalArgumentException("test tones only");
        JSONObject result=new JSONObject().put("before",run(context));
        String address=context.getSharedPreferences("selected_airpods",0).getString("bond_address",null);
        int sampleRate=24000,count=sampleRate*3;
        short[] samples=new short[count];
        for(int i=0;i<count;i++) {
            double envelope=Math.min(1.0,Math.min(i/(sampleRate*0.05),(count-1-i)/(sampleRate*0.05)));
            samples[i]=(short)(32767*0.025*envelope*Math.sin(2*Math.PI*frequency*i/sampleRate));
        }
        android.media.AudioTrack track=new android.media.AudioTrack.Builder()
            .setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(new android.media.AudioFormat.Builder().setSampleRate(sampleRate)
                .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(android.media.AudioTrack.MODE_STATIC).setBufferSizeInBytes(count*2).build();
        try {
            if(track.write(samples,0,count)!=count)throw new IllegalStateException("test audio write incomplete");
            track.play();android.os.SystemClock.sleep(1000);
            android.media.AudioDeviceInfo route=track.getRoutedDevice();
            result.put("actual_track_route_type",route==null?-1:route.getType());
            result.put("actual_track_route_matches_headset",route!=null && address.equalsIgnoreCase(route.getAddress()));
            result.put("during",run(context));
            android.os.SystemClock.sleep(2000);
        } finally {track.stop();track.release();}
        result.put("frequency_hz",frequency).put("duration_seconds",3).put("global_volume_changed",false);
        return result;
    }
    static JSONObject run(Context context)throws Exception {
        String address=context.getSharedPreferences("selected_airpods",0).getString("bond_address",null);
        if(!DeviceIdentityStore.trustedAndBonded(context,address))throw new IllegalStateException("approved exact bond missing");
        BluetoothAdapter adapter=context.getSystemService(BluetoothManager.class).getAdapter();
        BluetoothDevice target=adapter.getRemoteDevice(address);
        JSONObject result=new JSONObject();
        for(int profile:new int[]{BluetoothProfile.A2DP,BluetoothProfile.HEADSET}) {
            CountDownLatch ready=new CountDownLatch(1);
            String key=profile==BluetoothProfile.A2DP?"a2dp":"headset";
            boolean accepted=adapter.getProfileProxy(context,new BluetoothProfile.ServiceListener(){
                public void onServiceConnected(int id,BluetoothProfile proxy) {
                    try {
                        synchronized(result) {
                            result.put(key+"_state",proxy.getConnectionState(target));
                            if(proxy instanceof BluetoothA2dp)
                                result.put("a2dp_playing",((BluetoothA2dp)proxy).isA2dpPlaying(target));
                        }
                    } catch(Exception error) {
                        try{synchronized(result){result.put(key+"_error",error.getClass().getSimpleName());}}catch(Exception ignored){}
                    } finally {adapter.closeProfileProxy(id,proxy);ready.countDown();}
                }
                public void onServiceDisconnected(int id){}
            },profile);
            if(!accepted || !ready.await(5,TimeUnit.SECONDS))throw new IllegalStateException(key+" profile query unavailable");
        }
        AudioManager audio=context.getSystemService(AudioManager.class);
        result.put("media_route_matches_headset",WearPauseService.mediaRouteMatches(audio,address));
        result.put("music_active",audio.isMusicActive());
        result.put("audio_mode",audio.getMode());
        result.put("bluetooth_privileged_granted",context.checkSelfPermission("android.permission.BLUETOOTH_PRIVILEGED")==0);
        result.put("aap_opened_by_probe",false);
        return result;
    }
}
