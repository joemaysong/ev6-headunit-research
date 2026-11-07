import java.io.*;
import java.nio.channels.*;
/** Root-only publisher. Backend reads the public probe grant, never the private
 * coordinator journal. Caller owns the controller process lock. */
public final class ProjectionProbeFiles {
 private final File directory,lockFile,grant;private final String boot;
 public ProjectionProbeFiles(File directory,String boot)throws Exception{
  if(android.os.Process.myUid()!=0||boot==null||!boot.matches("[0-9a-f]{32}"))throw new SecurityException("probe-root");
  this.directory=directory;this.boot=boot;lockFile=new File(directory,"probe.lock");grant=new File(directory,"probe-grant");
  if(!directory.isDirectory()||!directory.getCanonicalFile().equals(directory.getAbsoluteFile()))throw new IOException("probe-directory");
  if(!lockFile.exists()&&!lockFile.createNewFile())throw new IOException("probe-lock-create");
  canonical(lockFile);
  if(!lockFile.setWritable(false,false)||!lockFile.setWritable(true,true)||!lockFile.setReadable(true,false))throw new IOException("probe-lock-mode");
  DirectorySync.sync(directory);
 }
 private static void canonical(File f)throws Exception{if(!f.isFile()||!f.getCanonicalFile().equals(f.getAbsoluteFile()))throw new IOException("probe-path");}
 private static String read(File f)throws Exception{canonical(f);FileInputStream in=new FileInputStream(f);try{byte[] b=new byte[513];int n=0,k;while(n<b.length&&(k=in.read(b,n,b.length-n))!=-1)n+=k;if(n==0||n>512)throw new IOException("probe-size");return new String(b,0,n,"US-ASCII");}finally{in.close();}}
 private interface Mutation{void run()throws Exception;}
 private boolean locked(Mutation mutation)throws Exception{
  canonical(lockFile);RandomAccessFile file=new RandomAccessFile(lockFile,"rw");FileLock lock=null;
  try{try{lock=file.getChannel().tryLock();}catch(OverlappingFileLockException busy){return false;}if(lock==null)return false;mutation.run();return true;}finally{if(lock!=null)lock.release();file.close();}
 }
 /** SPP-only intent. Kept until exact native/transport drain so delayed Wi-Fi
  * replies remain blocked after native START authority has been revoked. */
 public boolean recoverPublication()throws Exception{
  return locked(new Mutation(){public void run()throws Exception{
   for(String name:new String[]{"probe-start.next","probe-grant.next"}){
    File next=new File(directory,name);if(!next.exists())continue;canonical(next);
    File archived=new File(directory,name+".abandoned-"+java.util.UUID.randomUUID().toString());
    if(!next.setReadable(false,false)||!next.setReadable(true,true)||!next.renameTo(archived))throw new IOException("probe-recovery-quarantine");DirectorySync.sync(directory);
   }
  }});
 }
 public boolean publishStart(final String request,final String peer,final long bond,final long until,final long now)throws Exception{
  if(request==null||!request.matches("[0-9a-f]{32}")||peer==null||!peer.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")||bond<=0||bond>999999999999999999L||until>999999999999999999L||now<0||until<=now||until-now>120000L)throw new IllegalArgumentException();
  return locked(new Mutation(){public void run()throws Exception{
   for(String name:new String[]{"probe-start","probe-start.next","probe-grant","probe-grant.next","start-grant","start-grant.next","bluetooth-hold.next","coordinator.next"})if(new File(directory,name).exists())throw new IOException("probe-start-authority-present");
   if(!read(new File(directory,"bluetooth-hold")).equals("EV6BTHOLD1|"+boot+"|"+request+"|1|CP|"+peer+"|END\n")||!read(new File(directory,"coordinator")).equals("EV6EXCLUSIVE1|"+boot+"|"+request+"|"+peer+"|1|PROBE_WAIT|-|END\n"))throw new IOException("probe-start-reservation");
   File next=new File(directory,"probe-start.next");if(!next.createNewFile())throw new IOException("probe-start-create");
   if(!next.setWritable(false,false)||!next.setWritable(true,true)||!next.setReadable(true,false))throw new IOException("probe-start-mode");
   FileOutputStream out=new FileOutputStream(next);try{out.write(("EV6PROBESTART1|"+boot+"|"+request+"|"+peer+"|"+bond+"|"+until+"|END\n").getBytes("US-ASCII"));out.getFD().sync();}finally{out.close();}
   if(!next.renameTo(new File(directory,"probe-start")))throw new IOException("probe-start-publish");DirectorySync.sync(directory);
  }});
 }
 /** Only after exact socket/native completion; not a cancellation command. */
 public boolean retireStart(final String request,final String expectedBoot)throws Exception{
  if(request==null||!request.matches("[0-9a-f]{32}")||expectedBoot==null||!expectedBoot.matches("[0-9a-f]{32}"))throw new IllegalArgumentException();
  return locked(new Mutation(){public void run()throws Exception{
   for(String name:new String[]{"probe-start.next","probe-grant","probe-grant.next"})if(new File(directory,name).exists())throw new IOException("probe-start-not-revoked");
   File start=new File(directory,"probe-start");if(!start.exists())return;String[] p=read(start).split("\\|",-1);
   if(p.length!=7||!p[0].equals("EV6PROBESTART1")||!p[1].equals(expectedBoot)||!p[2].equals(request)||!p[6].equals("END\n"))throw new IOException("probe-start-retire-owner");
   if(!start.delete())throw new IOException("probe-start-retire");DirectorySync.sync(directory);
  }});
 }
 public boolean publish(final String request,final String peer,final long bond,final long until,final String source,final long generation,final long now)throws Exception{
  if(request==null||!request.matches("[0-9a-f]{32}")||peer==null||!peer.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")||source==null||!source.matches("[0-9a-f]{32}")||bond<=0||generation<=0||bond>999999999999999999L||generation>999999999999999999L||until>999999999999999999L||now<0||until<=now||until-now>120000L)throw new IllegalArgumentException();
  return locked(new Mutation(){public void run()throws Exception{
   for(String name:new String[]{"probe-grant","probe-grant.next","start-grant","start-grant.next","bluetooth-hold.next","coordinator.next"})if(new File(directory,name).exists())throw new IOException("probe-authority-present");
   String hold="EV6BTHOLD1|"+boot+"|"+request+"|1|CP|"+peer+"|END\n";
   String journal="EV6EXCLUSIVE1|"+boot+"|"+request+"|"+peer+"|1|PROBE_WAIT|-|END\n";
   if(!read(new File(directory,"bluetooth-hold")).equals(hold)||!read(new File(directory,"coordinator")).equals(journal))throw new IOException("probe-reservation-mismatch");
   File next=new File(directory,"probe-grant.next");if(!next.createNewFile())throw new IOException("probe-create");
   if(!next.setWritable(false,false)||!next.setWritable(true,true)||!next.setReadable(true,false))throw new IOException("probe-mode");
   FileOutputStream out=new FileOutputStream(next);try{out.write(("EV6PROBE1|"+boot+"|"+request+"|"+peer+"|"+bond+"|"+until+"|"+source+"|"+generation+"|END\n").getBytes("US-ASCII"));out.getFD().sync();}finally{out.close();}
   if(!next.renameTo(grant))throw new IOException("probe-publish");DirectorySync.sync(directory);
  }});
 }
 /** Identity-bound revocation. False means a native invocation still holds the
  * lock; caller must wait and must not release/promote the coordinator yet. */
 public boolean revoke(final String request,final String expectedBoot)throws Exception{
  if(request==null||!request.matches("[0-9a-f]{32}")||expectedBoot==null||!expectedBoot.matches("[0-9a-f]{32}"))throw new IllegalArgumentException();
  return locked(new Mutation(){public void run()throws Exception{
   if(new File(directory,"probe-grant.next").exists())throw new IOException("probe-unresolved-publication");
   if(!grant.exists())return;String[] p=read(grant).split("\\|",-1);
   if(p.length!=9||!p[0].equals("EV6PROBE1")||!p[1].equals(expectedBoot)||!p[2].equals(request)||!p[8].equals("END\n"))throw new IOException("probe-revoke-owner");
   if(!grant.delete())throw new IOException("probe-revoke");DirectorySync.sync(directory);
  }});
 }
}
