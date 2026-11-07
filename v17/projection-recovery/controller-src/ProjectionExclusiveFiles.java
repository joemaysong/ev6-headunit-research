import java.io.*;
/** Root-owned durable journal/hold publication. Hold updates use rename, never a missing-file window. */
public final class ProjectionExclusiveFiles implements ProjectionExclusiveCoordinator.Journal {
 private final File directory,hold,journal;private final String boot;
 public ProjectionExclusiveFiles(File directory,String boot)throws Exception{
  if(android.os.Process.myUid()!=0||boot==null||!boot.matches("[0-9a-f]{32}"))throw new SecurityException("exclusive-files-root");this.directory=directory;this.boot=boot;hold=new File(directory,"bluetooth-hold");journal=new File(directory,"coordinator");
  if(!directory.exists()&&!directory.mkdir())throw new IOException("exclusive-directory");
  if(!directory.getCanonicalFile().equals(directory.getAbsoluteFile())||!directory.isDirectory())throw new IOException("exclusive-directory-identity");
  if(!directory.setWritable(false,false)||!directory.setWritable(true,true)||!directory.setReadable(true,true)||!directory.setExecutable(true,false))throw new IOException("exclusive-directory-mode");
  // The controller already owns its process lock. A .next file was never published:
  // preserve it as evidence, but never promote it or replay its intended effect.
  quarantine(new File(directory,"usb-handover.next")); clearUsbHandover();
  quarantine(new File(directory,"coordinator.next"));quarantine(new File(directory,"bluetooth-hold.next"));quarantine(new File(directory,"start-grant.next"));quarantine(new File(directory,"aa-stop.next"));quarantine(new File(directory,"aa-request.next"));
  if(new File(directory,"reconnect.next").exists())throw new IOException("reconnect-unresolved-write");
  if(hold.exists()&&!journal.exists())throw new IOException("exclusive-hold-without-journal");
 }
 private void quarantine(File next)throws Exception{
  if(!next.exists())return;if(!next.isFile()||!next.getCanonicalFile().equals(next.getAbsoluteFile()))throw new IOException("exclusive-next-identity");
  if(!next.setReadable(false,false)||!next.setReadable(true,true)||!next.setWritable(false,false)||!next.setWritable(true,true))throw new IOException("exclusive-next-mode");
  File archived=new File(directory,next.getName()+".abandoned-"+java.util.UUID.randomUUID().toString());if(archived.exists()||!next.renameTo(archived))throw new IOException("exclusive-next-quarantine");DirectorySync.sync(directory);
 }
 private String read(File file,int max)throws Exception{
  if(!file.exists())return "";if(!file.isFile()||!file.getCanonicalFile().equals(file.getAbsoluteFile()))throw new IOException("exclusive-file-identity");FileInputStream in=new FileInputStream(file);try{byte[] b=new byte[max+1];int n=0,k;while(n<b.length&&(k=in.read(b,n,b.length-n))!=-1)n+=k;if(n<1||n>max)throw new IOException("exclusive-file-size");return new String(b,0,n,"US-ASCII");}finally{in.close();}
 }
 private void atomic(File file,String data,boolean publicRead)throws Exception{
  File next=new File(directory,file.getName()+".next");if(next.exists())throw new IOException("exclusive-unresolved-write");if(!next.createNewFile())throw new IOException("exclusive-create");
  if(!next.setReadable(false,false)||!next.setWritable(false,false)||!next.setReadable(true,true)||!next.setWritable(true,true)||(publicRead&&!next.setReadable(true,false)))throw new IOException("exclusive-file-mode");
  FileOutputStream out=new FileOutputStream(next);try{out.write(data.getBytes("US-ASCII"));out.getFD().sync();}finally{out.close();}
  if(!next.renameTo(file))throw new IOException("exclusive-rename");DirectorySync.sync(directory);
 }
 File directory(){return directory;}
 public synchronized boolean startAbsent()throws Exception{return read(new File(directory,"start-grant"),1024).length()==0 && !new File(directory,"start-grant.next").exists();}
 public synchronized String readReconnect()throws Exception{return read(new File(directory,"reconnect"),300);}
 public synchronized void writeReconnect(String value)throws Exception{if(value.length()>300)throw new IOException("reconnect-size");File file=new File(directory,"reconnect");if(value.length()>0&&!value.equals(read(file,300)))atomic(file,value,false);}
 public synchronized String readAaRequest()throws Exception{return read(new File(directory,"aa-request"),500);}
 public synchronized void writeAaRequest(String request,String encoded)throws Exception{if(request==null||!request.matches("[0-9a-f]{32}")||encoded==null||encoded.length()>350)throw new IllegalArgumentException();atomic(new File(directory,"aa-request"),request+"|"+encoded,false);}
 public synchronized String readAaStop()throws Exception{return read(new File(directory,"aa-stop"),500);}
 public synchronized void writeAaStop(String request,String encoded)throws Exception{if(request==null||!request.matches("[0-9a-f]{32}")||encoded==null||encoded.length()>350)throw new IllegalArgumentException();atomic(new File(directory,"aa-stop"),request+"|"+encoded,false);}
 public synchronized String read()throws Exception{return read(journal,500);}
 private long usbDeadline;
 private String usbRequest="";
 synchronized void clearUsbHandover()throws Exception{
  File marker=new File(directory,"usb-handover");
  if(marker.exists() && !marker.delete())throw new IOException("usb-handover-clear");
  usbRequest="";usbDeadline=0;
 }
 synchronized void expireUsbHandover()throws Exception{
  if(usbDeadline!=0 && System.nanoTime()-usbDeadline>=0)clearUsbHandover();
 }
 synchronized void armUsbHandover(String request)throws Exception{
  if(!request.matches("[0-9a-f]{32}"))throw new IOException("usb-handover-request");
  usbRequest=request;usbDeadline=0;
  writeUsbPhase("DRAIN");
 }
 private void writeUsbPhase(String phase)throws Exception{
  atomic(new File(directory,"usb-handover"),boot+"|"+usbRequest+"|"+phase+"\n",true);
 }
 public synchronized void write(String record)throws Exception{
  String[] row=record.split("\\|",-1);
  if(!usbRequest.isEmpty() && (row.length!=8||!usbRequest.equals(row[2])||
     !(row[5].equals("DRAIN")||row[5].equals("RELEASE_INTENT")||row[5].equals("WAIT_RELEASE")||row[5].equals("DONE"))))clearUsbHandover();
  atomic(journal,record,false);
  if(!usbRequest.isEmpty() && row[5].equals("DONE")){
   writeUsbPhase("DONE");
   usbDeadline=System.nanoTime()+90000000000L;
  }
 }
 public synchronized String hold(String request,String target,int type,String predecessor)throws Exception{
  if(request==null||!request.matches("[0-9a-f]{32}")||target==null||!target.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")||(type!=1&&type!=2))throw new IllegalArgumentException();
  String record="EV6BTHOLD1|"+boot+"|"+request+"|1|"+(type==1?"CP":"AA")+"|"+target+"|END\n";
  String existing=read(hold,300);if(existing.length()>0&&!existing.equals(record)){String[] prior=existing.split("\\|",-1);if(prior.length!=7||!prior[0].equals("EV6BTHOLD1")||!prior[1].equals(boot)||!prior[2].equals(predecessor)||!prior[6].equals("END\n"))throw new IOException("exclusive-prior-hold");}
  if(!existing.equals(record))atomic(hold,record,true);return record;
 }
 public synchronized String heldRecord(String request)throws Exception{return heldRecord(request,boot);}
 public synchronized String heldRecord(String request,String expectedBoot)throws Exception{String value=read(hold,300);String[] p=value.split("\\|",-1);if(p.length!=7||!p[0].equals("EV6BTHOLD1")||!p[1].equals(expectedBoot)||!p[2].equals(request)||!p[6].equals("END\n"))throw new IOException("exclusive-hold-identity");return value;}
 private void noProbeAuthority()throws Exception{for(String name:new String[]{"probe-start","probe-start.next","probe-grant","probe-grant.next"})if(new File(directory,name).exists())throw new IOException("exclusive-probe-not-drained");}
 public synchronized void grant(String request,String predecessor)throws Exception{
  noProbeAuthority();
  String record=heldRecord(request);File grant=new File(directory,"start-grant");String old=read(grant,300);if(old.length()>0&&!old.equals(record)){String[] p=old.split("\\|",-1);if(p.length!=7||!p[0].equals("EV6BTHOLD1")||!p[1].equals(boot)||!p[2].equals(predecessor)||!p[6].equals("END\n"))throw new IOException("exclusive-prior-grant");}if(!old.equals(record))atomic(grant,record,true);
 }
 /** Revoke new START authority before cancellation; existing STOP/cleanup is unaffected. */
 public synchronized void revokeStart(String request,String predecessor,String expectedBoot)throws Exception{
  File grant=new File(directory,"start-grant");String record=read(grant,300);if(record.length()==0)return;String[] p=record.split("\\|",-1);
  if(p.length!=7||!p[0].equals("EV6BTHOLD1")||!p[1].equals(expectedBoot)||(!p[2].equals(request)&&(!p[2].equals(predecessor)||predecessor.equals("-")))||!p[6].equals("END\n"))throw new IOException("exclusive-revoke-owner");
  if(!grant.delete())throw new IOException("exclusive-grant-revoke");DirectorySync.sync(directory);
 }
 public synchronized String grantedRecord(String request)throws Exception{String record=heldRecord(request);if(!read(new File(directory,"start-grant"),300).equals(record))throw new IOException("exclusive-start-not-granted");return record;}
 /** HOLD_INTENT may have been persisted before atomic replacement of its predecessor. */
 public synchronized void releaseOwned(String request,String predecessor,String expectedBoot)throws Exception{
  noProbeAuthority();
  String value=read(hold,300);String[] p=value.split("\\|",-1);
  if(p.length!=7||!p[0].equals("EV6BTHOLD1")||!p[1].equals(expectedBoot)||!expectedBoot.matches("[0-9a-f]{32}")||!p[2].matches("[0-9a-f]{32}")||(!p[2].equals(request)&&(!p[2].equals(predecessor)||predecessor.equals("-")))||!p[3].matches("[1-9][0-9]{0,18}")||Long.parseLong(p[3])<=0||(!p[4].equals("CP")&&!p[4].equals("AA"))||!p[5].matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")||!p[6].equals("END\n"))throw new IOException("exclusive-release-owner");
  File grant=new File(directory,"start-grant");String granted=read(grant,300);if(granted.length()>0){String[] g=granted.split("\\|",-1);if(g.length!=7||!g[0].equals("EV6BTHOLD1")||!g[1].equals(expectedBoot)||(!g[2].equals(request)&&(!g[2].equals(predecessor)||predecessor.equals("-")))||!g[6].equals("END\n"))throw new IOException("exclusive-grant-owner");if(!grant.delete())throw new IOException("exclusive-grant-release");DirectorySync.sync(directory);}
  if(!hold.delete())throw new IOException("exclusive-release");DirectorySync.sync(directory);
 }
 public synchronized boolean absent()throws Exception{if(!directory.getCanonicalFile().equals(directory.getAbsoluteFile()))throw new IOException("exclusive-directory-replaced");return hold.getCanonicalFile().equals(hold.getAbsoluteFile())&&!hold.exists();}
 /** Caller proves full original/native/network cleanup before calling and pins identity again. */
 public synchronized void release(String request,String expectedBoot)throws Exception{noProbeAuthority();heldRecord(request,expectedBoot);if(!hold.delete())throw new IOException("exclusive-release");DirectorySync.sync(directory);}
}
