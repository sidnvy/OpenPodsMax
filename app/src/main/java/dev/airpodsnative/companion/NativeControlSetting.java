package dev.airpodsnative.companion;

import android.os.*;
import java.util.*;

/** Outbound SettingsLib controls, with values taken only from observed state. */
@android.annotation.SuppressLint("ParcelCreator")
final class NativeControlSetting implements Parcelable {
    static final int MODE=1001,PV=1038,CA=1040,PRESS=1023,HOLD=1024;
    static final int[] IDS={MODE,PV,CA,PRESS,HOLD};
    final int id,value; final boolean enabled;
    NativeControlSetting(int id,int value,boolean enabled){this.id=id;this.value=value;this.enabled=enabled;}
    static int control(int id){switch(id){case PV:return 38;case CA:return 40;case PRESS:return 23;case HOLD:return 24;default:return -1;}}
    static List<Parcelable> observed(){
        AapController c=AapController.instance();List<Parcelable> out=new ArrayList<>();
        out.add(new NativeControlSetting(MODE,c.lastObservedMode(),c.isConnected() && c.lastObservedMode()>=0 && !c.isVerifyingMode()));
        for(int id:new int[]{PV,CA,PRESS,HOLD}){int ctl=control(id),v=c.getSetting(ctl);
            out.add(new NativeControlSetting(id,v,c.hasSetting(ctl) && !c.isVerifyingSetting(ctl)));}
        return out;
    }
    static String title(int id){switch(id){case MODE:return "Listening mode";case PV:return "Personalized Volume";case CA:return "Conversation Awareness";case PRESS:return "Press speed";case HOLD:return "Press and hold duration";default:throw new IllegalArgumentException();}}
    public int describeContents(){return 0;}
    public void writeToParcel(Parcel p,int flags){
        p.writeInt(id);p.writeBundle(Bundle.EMPTY);
        if(id==PV || id==CA){
            p.writeInt(1);p.writeString(title(id));
            p.writeString(value<0?"Not reported by headphones":!enabled?"Verifying headphone setting…":id==PV?"Automatically adjusts media volume":"Lowers media volume when you speak");
            p.writeParcelable(null,flags);p.writeInt(0); // EMPTY_ACTION, no extra payload
            p.writeBoolean(true);p.writeBoolean(value==1);p.writeBoolean(enabled);p.writeBundle(Bundle.EMPTY);
        }else{
            p.writeInt(2);p.writeString(title(id)+(value<0?" · Not reported":""));
            String[] labels=id==MODE?new String[]{"Noise Cancellation","Off","Adaptive","Transparency"}:id==PRESS?new String[]{"Default","Slower","Slowest"}:new String[]{"Default","Shorter","Shortest"};
            List<AirPodsSettingsService.Toggle> toggles=new ArrayList<>();
            for(String label:labels)toggles.add(new AirPodsSettingsService.Toggle(id==PRESS?"Press: "+label:id==HOLD?"Hold: "+label:label,android.graphics.Color.TRANSPARENT));
            p.writeTypedList(toggles);p.writeInt(Math.max(0,value));p.writeBoolean(value>=0);p.writeBoolean(enabled);p.writeBundle(Bundle.EMPTY);
        }
    }
    static final class Update {
        final int id,type,value;
        Update(int id,int type,int value){this.id=id;this.type=type;this.value=value;}
    }
    static Update readUpdate(Parcel p){
        if(p.readInt()!=1)throw new BadParcelableException("Missing setting state");
        int id=p.readInt();p.readBundle(NativeControlSetting.class.getClassLoader());
        int type=p.readInt();int value=p.readInt();p.readBundle(NativeControlSetting.class.getClassLoader());
        if(p.dataAvail()!=0)throw new BadParcelableException("Trailing setting state");
        boolean valid=(id==MODE && type==2 && value>=0 && value<4)
            || ((id==PRESS || id==HOLD) && type==2 && value>=0 && value<3)
            || ((id==PV || id==CA) && type==1 && (value==0 || value==1));
        if(!valid)throw new BadParcelableException("Unsupported setting state");
        return new Update(id,type,value);
    }
}
