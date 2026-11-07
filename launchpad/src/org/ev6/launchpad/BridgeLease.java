package org.ev6.launchpad;
import java.io.*;
import java.nio.channels.*;

/** The kernel releases this lease when the owning bridge exits or is killed. */
final class BridgeLease implements Closeable {
 private final RandomAccessFile file;
 private final FileLock lock;
 private BridgeLease(RandomAccessFile f,FileLock l){file=f;lock=l;}
 static BridgeLease acquire(File path)throws IOException {
  RandomAccessFile f=new RandomAccessFile(path,"rw");
  try{
   FileLock l;
   try{l=f.getChannel().tryLock();}catch(OverlappingFileLockException busy){f.close();return null;}
   if(l==null){f.close();return null;}
   return new BridgeLease(f,l);
  }catch(IOException e){
   f.close();
   // This API19 libcore throws EAGAIN instead of returning null on contention.
   Throwable cause=e.getCause();
   if(cause!=null){try{if(cause.getClass().getField("errno").getInt(cause)==11)return null;}catch(Exception ignored){}}
   throw e;
  }
 }
 public void close()throws IOException {try{lock.release();}finally{file.close();}}
}
