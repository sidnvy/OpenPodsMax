package dev.airpodsnative.companion;

import java.nio.charset.StandardCharsets;

/** Known AAP smart-routing release request; no optimistic ownership inference. */
public final class HandoffRequest {
    private static final byte[] KEY="audioRoutingSetOwnershipToFalse".getBytes(StandardCharsets.US_ASCII);
    private HandoffRequest(){}
    public static boolean release(byte[] data,int length){
        if(data==null || length>data.length || length<7+KEY.length
            || !AapFrameValidator.message(data,length,0x11))return false;
        for(int start=6;start+KEY.length<length;start++){
            int i=0;while(i<KEY.length && data[start+i]==KEY[i])i++;
            if(i==KEY.length && data[start+i]==1)return true;
        }
        return false;
    }
}
