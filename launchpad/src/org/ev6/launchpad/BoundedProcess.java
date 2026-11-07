package org.ev6.launchpad;
import java.io.*;
/** Bounded subprocess output and execution, compatible with Android API 19. */
final class BoundedProcess {
 static String run(ProcessBuilder builder,long timeoutMs,final int limit)throws Exception {
  final Process process=builder.redirectErrorStream(true).start();
  final ByteArrayOutputStream output=new ByteArrayOutputStream();
  final IOException[] failure=new IOException[1];
  Thread drain=new Thread(new Runnable(){public void run(){try{InputStream in=process.getInputStream();byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1){synchronized(output){int keep=Math.min(n,Math.max(0,limit-output.size()));output.write(b,0,keep);}}}catch(IOException e){failure[0]=e;}}},"LaunchpadOutput");
  drain.setDaemon(true);drain.start();long deadline=System.nanoTime()+timeoutMs*1000000L;
  try {int code;while(true){try{code=process.exitValue();break;}catch(IllegalThreadStateException running){if(System.nanoTime()>=deadline)throw new IOException("Command timed out; state must be checked before retry");Thread.sleep(25);}}
   long remaining=(deadline-System.nanoTime())/1000000L;if(remaining>0)drain.join(remaining);
   if(drain.isAlive())throw new IOException("Command output timed out");
   if(failure[0]!=null)throw failure[0];String text; synchronized(output){text=output.toString("UTF-8");}
   if(code!=0)throw new IOException("Command failed ("+code+"): "+text);return text;
  }finally{process.destroy();try{process.getInputStream().close();}catch(IOException ignored){}try{process.getOutputStream().close();}catch(IOException ignored){}try{process.getErrorStream().close();}catch(IOException ignored){}}
 }
}
