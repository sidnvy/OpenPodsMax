package dev.airpodsnative.companion;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import org.xmlpull.v1.XmlPullParser;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** A Settings config is a complete layout, not an append-only extension.
 * Preserve the installed OEM's top-level preferences, including whole categories.
 * Missing/unknown resources fail closed so Settings can keep its original layout. */
final class NativeSettingsLayout {
    static List<Parcelable> withPlatformItems(Context context,List<? extends Parcelable> additions) {
        if(context==null)throw new IllegalStateException("Settings resource context missing");
        try {
            Resources resources=context.getPackageManager().getResourcesForApplication("com.android.settings");
            int id=resources.getIdentifier("bluetooth_device_details_fragment","xml","com.android.settings");
            if(id==0)throw new IllegalStateException("Settings layout unavailable");
            LinkedHashSet<String> keys=new LinkedHashSet<>();
            try(XmlResourceParser parser=resources.getXml(id)) {
                for(int event=parser.next();event!=XmlPullParser.END_DOCUMENT;event=parser.next()) {
                    if(event!=XmlPullParser.START_TAG || parser.getDepth()!=2)continue;
                    String ns="http://schemas.android.com/apk/res/android";
                    int keyId=parser.getAttributeResourceValue(ns,"key",0);
                    String key=keyId!=0?resources.getString(keyId):parser.getAttributeValue(ns,"key");
                    if(key==null || key.isEmpty())throw new IllegalStateException("Unkeyed platform preference");
                    if(!keys.add(key))throw new IllegalStateException("Duplicate platform preference");
                }
            }
            if(keys.isEmpty())throw new IllegalStateException("Empty platform layout");
            List<Parcelable> items=new ArrayList<>();
            int index=2000;
            boolean inserted=false;
            for(String key:keys){items.add(new BuiltinItem(index++,key));
                if(key.equals("action_buttons")){items.addAll(additions);inserted=true;}}
            if(!inserted)items.addAll(additions);
            return items;
        } catch(Exception error) {throw new IllegalStateException("Cannot preserve platform Settings layout",error);}
    }
    // Outbound wire adapter; decoding belongs to the actual OEM Settings CREATOR.
    @android.annotation.SuppressLint("ParcelCreator")
    static final class BuiltinItem implements Parcelable {
        final int id;final String key;
        BuiltinItem(int id,String key){this.id=id;this.key=key;}
        public int describeContents(){return 0;}
        public void writeToParcel(Parcel p,int flags){
            p.writeInt(id);p.writeString(null);p.writeString(null);p.writeString(null);
            p.writeBoolean(false);p.writeString(key);p.writeBundle(Bundle.EMPTY);
        }
    }
}
