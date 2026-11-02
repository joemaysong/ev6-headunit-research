package org.ev6.musicvibe;
import java.io.*;
/** One writer, read-only readers. A reader must never rename the writer's files. */
final class HeartbeatFile {
 static void write(File target,long value)throws IOException {
  File next=new File(target.getPath()+".next");
  FileOutputStream out=new FileOutputStream(next);
  try{out.write(Long.toString(value).getBytes("UTF-8"));}finally{out.close();}
  if(!next.renameTo(target))throw new IOException("Heartbeat rename failed");
 }
 static long read(File target)throws IOException {
  FileInputStream in=new FileInputStream(target);
  try{byte[] b=new byte[64];int n=in.read(b);if(n<=0)throw new IOException("Empty heartbeat");return Long.parseLong(new String(b,0,n,"UTF-8"));}finally{in.close();}
 }
}
