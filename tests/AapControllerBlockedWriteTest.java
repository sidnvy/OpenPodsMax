import dev.airpodsnative.companion.*;
import android.bluetooth.*;
import android.content.Context;
import java.io.*;
import java.lang.reflect.Field;
import java.util.concurrent.*;

/** A blocked vendor write must not block wear/session guards or stop(). */
public final class AapControllerBlockedWriteTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        AapController c=new AapController();c.initialize(new Context());
        BluetoothSocket socket=new BluetoothSocket();BluetoothAdapter.nextSocket=socket;
        c.connectReadOnlyAsync("01:23:45:67:89:AB");
        check(socket.wrote.await(4,TimeUnit.SECONDS),"handshake");
        long initDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(socket.writes.size()<4 && System.nanoTime()<initDeadline)Thread.sleep(5);
        check(socket.writes.size()==4,"initialization must use four L2CAP packets");
        check(socket.writes.get(0).length==16 && socket.writes.get(1).length==10
            && socket.writes.get(2).length==10 && socket.writes.get(3).length==14,
            "initialization packet boundaries preserved");
        socket.frames.add(new byte[]{4,0,4,0,9,0,13,2,0,0,0});
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(c.lastObservedMode()!=0 && System.nanoTime()<deadline)Thread.sleep(5);
        check(c.lastObservedMode()==0,"mode observed");
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        Field output=BluetoothSocket.class.getDeclaredField("output");output.setAccessible(true);
        output.set(socket,new OutputStream(){
            public void write(int ignored)throws IOException {
                entered.countDown();
                try{if(!release.await(6,TimeUnit.SECONDS))throw new IOException("test deadline");}
                catch(InterruptedException e){throw new IOException(e);}
            }
        });
        boolean[] sent={true};Thread writer=new Thread(()->sent[0]=c.sendMode(2));writer.start();
        check(entered.await(2,TimeUnit.SECONDS),"command blocked in output");
        Thread stop=new Thread(c::stop);stop.start();stop.join(500);
        boolean blocked=stop.isAlive();release.countDown();writer.join(2000);stop.join(2000);
        check(!blocked,"blocked output held controller lock");
        check(!writer.isAlive(),"writer completed");
        check(!sent[0],"retired socket must not report a successful command");
        check(!c.isConnected() && c.lastObservedMode()==-1,"retired command cannot restore stale state");
        AapController stalled=new AapController();stalled.initialize(new Context());
        BluetoothSocket handshake=new BluetoothSocket();BluetoothAdapter.nextSocket=handshake;
        CountDownLatch handshakeEntered=new CountDownLatch(1);
        output.set(handshake,new OutputStream(){
            public void write(int ignored)throws IOException {
                handshakeEntered.countDown();
                try{if(!handshake.closed.await(5,TimeUnit.SECONDS))throw new IOException("test deadline");}
                catch(InterruptedException e){throw new IOException(e);}
                throw new IOException("watchdog closed socket");
            }
        });
        try {
            stalled.connectReadOnlyAsync("01:23:45:67:89:AB");
            check(handshakeEntered.await(4,TimeUnit.SECONDS),"initialization blocked in output");
            check(handshake.closed.await(4,TimeUnit.SECONDS),"watchdog closes stalled initialization");
            check(!stalled.isConnected(),"timed-out initialization invalidates session");
        } finally {stalled.stop();}
        System.out.println("PASS blocked command permits stop, rejects retired completion, and initialization times out");
    }
}
