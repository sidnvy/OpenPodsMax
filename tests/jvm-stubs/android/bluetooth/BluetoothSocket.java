package android.bluetooth;
import java.io.*;import java.util.concurrent.*;
public final class BluetoothSocket {
 public final BlockingQueue<byte[]> frames=new LinkedBlockingQueue<>();
 public final java.util.concurrent.atomic.AtomicInteger reads=new java.util.concurrent.atomic.AtomicInteger();
 public final CountDownLatch wrote=new CountDownLatch(1);
 private final InputStream input=new InputStream(){public int read(){throw new UnsupportedOperationException();} public int read(byte[] dst)throws IOException { try{reads.incrementAndGet();byte[] frame=frames.take();if(frame.length==0)return -1;System.arraycopy(frame,0,dst,0,frame.length);return frame.length;}catch(InterruptedException e){throw new IOException(e);} }};
 private final OutputStream output=new ByteArrayOutputStream(){public synchronized void write(byte[] b,int off,int len){super.write(b,off,len);wrote.countDown();}};
 public void connect(){} public InputStream getInputStream(){return input;} public OutputStream getOutputStream(){return output;} public void close()throws IOException{frames.offer(new byte[0]);}
}
