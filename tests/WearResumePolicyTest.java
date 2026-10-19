import dev.airpodsnative.companion.WearResumePolicy;
public final class WearResumePolicyTest {
    static int checks;
    static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    public static void main(String[] args){
        WearResumePolicy p=new WearResumePolicy();
        p.wear(0,0,0);check(!p.consume(1000),"never play without owned pause");
        p.arm(1000);p.wear(0,0,1100);check(!p.consume(1600),"must observe pause acknowledgement");
        check(p.update(1200,true,true,false,1100),"fresh pause accepted");
        check(!p.consume(1599),"wear debounce");check(p.consume(1600),"confirmed owned pause resumes");
        check(!p.consume(1700),"resume is one shot");
        p.arm(2000);p.update(2200,true,true,false,1900);p.wear(0,0,2300);
        check(!p.consume(2800),"old paused state cannot authorize play");
        check(!p.update(4001,true,false,true,1900),"unacknowledged pause expires");
        p.arm(5000);p.update(5100,true,true,false,5100);p.wear(0,0,5200);p.wear(1,0,5300);
        check(!p.consume(6000),"partial wear cancels debounce");
        p.wear(0,0,6000);p.wear(0,0,6200);check(p.consume(6500),"duplicates do not postpone");
        for(int reason=0;reason<5;reason++){
            p.arm(10000);p.update(10100,true,true,false,10100);p.wear(0,0,10200);
            if(reason==0)p.update(10300,false,true,false,10100);
            if(reason==1)p.update(10300,true,false,true,10300);
            if(reason==2)p.update(10300,true,true,false,10300);
            if(reason==3)p.wear(-1,0,10300);
            if(reason==4)p.clear();
            check(!p.consume(11000),"unsafe state cancels ownership: "+reason);
        }
        p.arm(20000);p.update(20100,true,true,false,20100);p.wear(0,0,80000);
        check(!p.consume(80500),"expired ownership never resumes");
        p.arm(90000);p.update(90250,true,true,false,90100);p.wear(0,0,90300);
        check(!p.audioSettled(90500,true),"audio tail must not authorize resume");
        check(p.pending(),"short audio tail must retain pause ownership");
        check(p.audioSettled(90800,false) && p.consume(90800),"resume after tail settles and wear debounce");
        p.arm(100000);p.update(100250,true,true,false,100100);
        check(!p.audioSettled(102001,true) && !p.pending(),"sustained other audio cancels ownership");
        check(!p.audioSettled(102100,false),"audio silence cannot revive cancelled ownership");
        System.out.println("WearResumePolicyTest PASS "+checks+" checks");
    }
}
