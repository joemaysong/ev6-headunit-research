import java.io.*;
import java.util.Properties;
import org.ev6.projection.ReservationStore;

/** Retires an interrupted AP operation under its existing H, never a new lease.
 * The caller must revoke session START before invoking this transaction. A timeout
 * alone is not evidence of stopped effects. Snapshots survive cleanup/release loss.
 */
public final class ProjectionApAbort {
 public static boolean selectAbort(boolean idleExists,boolean abortExists,boolean cleanupAuthorized,boolean apPending)throws IOException {
  if(idleExists&&abortExists)throw new IOException("ap-close-conflicting-transactions");
  if(idleExists)return false;
  return cleanupAuthorized&&(abortExists||apPending);
 }
 private final File state,start;private final ReservationStore store;
 private final ProjectionApJournal.ParentSync sync;private final ProjectionIdleClose.Port port;
 public ProjectionApAbort(File state,File start,ReservationStore store,ProjectionApJournal.ParentSync sync,ProjectionIdleClose.Port port){this.state=state;this.start=start;this.store=store;this.sync=sync;this.port=port;}
 private void save(Properties p)throws Exception{
  File next=new File(state.getPath()+".next");if(!next.createNewFile())throw new IOException("ap-abort-pending-write");
  FileOutputStream out=new FileOutputStream(next);try{p.store(out,"AP interrupted operation cleanup");out.getFD().sync();}finally{out.close();}
  if(!next.renameTo(state))throw new IOException("ap-abort-rename");sync.sync(state.getParentFile());
 }
 private void require(ReservationStore.Ticket ticket)throws Exception{
  if(store.maintenanceRequested()||store.status(ticket)!=ReservationStore.VALID)throw new IOException("ap-abort-authority");
  port.requireIdle();
  if(store.status(ticket)!=ReservationStore.VALID)throw new IOException("ap-abort-authority-changed");
 }
 public boolean run()throws Exception{
  if(!state.getAbsoluteFile().equals(state.getCanonicalFile())||!start.getAbsoluteFile().equals(start.getCanonicalFile()))throw new IOException("ap-abort-path");
  // A .next file is unpublished intent, never permission to advance an effect.
  File next=new File(state.getPath()+".next");
  if(next.exists()){
   if(!next.isFile()||!next.getAbsoluteFile().equals(next.getCanonicalFile())||next.length()>2048)throw new IOException("ap-abort-next-identity");
   if(!next.renameTo(new File(state.getPath()+".abandoned-"+java.util.UUID.randomUUID().toString())))throw new IOException("ap-abort-next-archive");sync.sync(state.getParentFile());
  }
  Properties p=new Properties();
  if(state.exists()){
   if(!state.isFile()||state.length()>2048)throw new IOException("ap-abort-state-size");
   FileInputStream in=new FileInputStream(state);try{p.load(in);}finally{in.close();}
   if(!"EV6APABORT1".equals(p.getProperty("schema")))throw new IOException("ap-abort-schema");
  }else{
   if(!start.isFile()||start.length()>8192)throw new IOException("ap-abort-start-missing");
   RandomAccessFile in=new RandomAccessFile(start,"r");String first;try{first=in.readLine();}finally{in.close();}
   String[] h=first==null?new String[0]:first.split("\\|",-1);
   if(h.length!=5||!h[0].equals("EV6AP1"))throw new IOException("ap-abort-start-header");
   ReservationStore.Ticket t=store.recoverApIdentity(h[1]);if(t==null)throw new IOException("ap-abort-foreign-start");
   require(t);ProjectionApJournal.Snapshot snapshot=new ProjectionApJournal(start,t,sync).inspect();
   p.setProperty("schema","EV6APABORT1");p.setProperty("operation",h[1]);p.setProperty("digest",snapshot.digest);p.setProperty("phase","0");save(p);
  }
  ReservationStore.Ticket t=store.recoverApIdentity(p.getProperty("operation"));if(t==null)throw new IOException("ap-abort-successor");
  int phase=Integer.parseInt(p.getProperty("phase","-1"));if(phase<0||phase>4)throw new IOException("ap-abort-phase");
  File dir=new File(state.getParentFile(),"ap-abort-"+t.incarnation);
  if(!dir.getAbsoluteFile().equals(dir.getCanonicalFile()))throw new IOException("ap-abort-snapshot-path");
  if(phase==4&&store.apReleaseStatus(t)==ReservationStore.VALID){port.requireIdle();port.verifyStopped(dir);}
  else{
   require(t);
   if(phase<1){
    if(!p.getProperty("digest","").equals(new ProjectionApJournal(start,t,sync).inspect().digest))throw new IOException("ap-abort-start-changed");
    if(!dir.exists()&&!dir.mkdir())throw new IOException("ap-abort-snapshot-directory");
    port.snapshot(dir);p.setProperty("phase","1");save(p);phase=1;
   }
   if(phase<2){require(t);port.stop(dir);port.verifyStopped(dir);p.setProperty("phase","2");save(p);phase=2;}
   if(phase<3){require(t);port.verifyStopped(dir);port.cleanup(dir);p.setProperty("phase","3");save(p);phase=3;}
   require(t);port.verifyStopped(dir);p.setProperty("phase","4");save(p);
   if(!store.releaseCompleted(t)||store.apReleaseStatus(t)!=ReservationStore.VALID)throw new IOException("ap-abort-release-unconfirmed");
  }
  port.requireIdle();port.verifyStopped(dir);
  if(store.apReleaseStatus(t)!=ReservationStore.VALID)throw new IOException("ap-abort-release-changed");
  if(!state.delete())throw new IOException("ap-abort-retire");sync.sync(state.getParentFile());return true;
 }
}
