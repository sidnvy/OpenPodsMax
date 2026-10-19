package dev.airpodsnative.companion;

/** Bounds and domain validation for one complete L2CAP AAP packet.
 * Bytes 2..3 are the service, not a stream payload length. Do not concatenate
 * incomplete/unknown packets and accidentally manufacture a setting receipt.
 * Protocol domains follow CAPod (GPLv3); see NOTICE.md.
 */
public final class AapFrameValidator {
    private AapFrameValidator() {}
    public static boolean message(byte[] data,int length,int command) {
        return data!=null && length>=6 && length<=data.length &&
            data[0]==4 && data[1]==0 && data[2]==4 && data[3]==0 &&
            (data[4]&255)==(command&255) && (data[5]&255)==(command>>>8);
    }
    /** CAPod legacy and framed speaking notifications; raw status only.
     * Decoding is not proof that Max 2 supports a host audio reaction.
     */
    public static int conversationStatus(byte[] data,int length) {
        if(!message(data,length,0x4b))return -1;
        if(length==7)return data[6]&255;
        if(length==10 && data[6]==2 && data[7]==0 && data[8]==1)return data[9]&255;
        return -1;
    }
    public static boolean setting(byte[] data,int length) {
        return message(data,length,9) && length>=8 && value(data[6]&255,data[7]&255);
    }
    public static boolean value(int id,int value) {
        switch(id) {
            case 0x0d:return value>=1 && value<=4;
            case 0x28:case 0x26:case 0x0a:case 0x34:return value==1 || value==2;
            case 0x01:case 0x17:case 0x18:return value>=0 && value<=2;
            case 0x2e:return value>=0 && value<=100;
            case 0x1a:return value>=0 && value<=15;
            case 0x1f:return value>=15 && value<=100;
            default:return false;
        }
    }
}
