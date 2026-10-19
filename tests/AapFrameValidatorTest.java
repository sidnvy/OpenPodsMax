import dev.airpodsnative.companion.AapFrameValidator;
import java.util.Arrays;

public final class AapFrameValidatorTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static byte[] frame(int id,int value){return new byte[]{4,0,4,0,9,0,(byte)id,(byte)value,0,0,0};}
    public static void main(String[] args){
        byte[] good=frame(46,50);
        check(AapFrameValidator.setting(good,good.length),"real Adaptive baseline");
        for(int length=0;length<8;length++)check(!AapFrameValidator.setting(good,length),"truncated setting "+length);
        check(!AapFrameValidator.setting(good,12),"length beyond buffer");
        check(!AapFrameValidator.setting(null,8),"null buffer");
        for(int offset:new int[]{0,1,2,3,4,5}){
            byte[] wrong=good.clone();wrong[offset]=99;
            check(!AapFrameValidator.setting(wrong,wrong.length),"wrong packet/service/command "+offset);
        }
        for(int id:new int[]{23,24,1}){
            for(int value=0;value<3;value++)check(AapFrameValidator.setting(frame(id,value),11),"enum valid");
            check(!AapFrameValidator.setting(frame(id,3),11),"unknown enum must not enable UI");
        }
        for(int id:new int[]{10,38,40,52}){
            for(int value:new int[]{1,2})check(AapFrameValidator.setting(frame(id,value),11),"bool valid");
            for(int value:new int[]{0,3,255})check(!AapFrameValidator.setting(frame(id,value),11),"bool invalid");
        }
        for(int value:new int[]{0,50,100})check(AapFrameValidator.setting(frame(46,value),11),"adaptive valid");
        for(int value:new int[]{101,255})check(!AapFrameValidator.setting(frame(46,value),11),"adaptive invalid");
        for(int value:new int[]{15,80,100})check(AapFrameValidator.setting(frame(31,value),11),"tone valid");
        for(int value:new int[]{0,14,101,255})check(!AapFrameValidator.setting(frame(31,value),11),"tone invalid");
        check(!AapFrameValidator.setting(frame(26,16),11),"unknown mask bits");
        check(!AapFrameValidator.setting(frame(99,1),11),"unknown control");
        for(int value=1;value<=4;value++)check(AapFrameValidator.setting(frame(13,value),11),"ANC valid");
        check(!AapFrameValidator.setting(frame(13,0),11),"ANC invalid");
        byte[] speech={4,0,4,0,0x4b,0,2,0,1,5};
        check(AapFrameValidator.conversationStatus(speech,10)==5,"framed speech status");
        check(AapFrameValidator.conversationStatus(new byte[]{4,0,4,0,0x4b,0,8},7)==8,"legacy speech status");
        for(int n:new int[]{0,6,8,9,11})check(AapFrameValidator.conversationStatus(speech,n)==-1,"malformed speech length");
        check(AapFrameValidator.conversationStatus(null,10)==-1,"null speech");
        for(int offset:new int[]{0,1,2,3,4,5,6,7,8}) {
            byte[] bad=speech.clone();bad[offset]=99;
            check(AapFrameValidator.conversationStatus(bad,10)==-1,"foreign or malformed speech shape");
        }
        System.out.println("PASS "+checks+" packet bounds, service identity and value-domain checks");
    }
}
