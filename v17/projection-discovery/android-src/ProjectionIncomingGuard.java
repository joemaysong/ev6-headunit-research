import android.content.Context;
import android.content.Intent;
/** Incoming cable events are not user selections. Only an explicit wireless stop can arm a bounded wired handover. */
public final class ProjectionIncomingGuard {
 private static String boot()throws Exception{java.io.BufferedReader r=new java.io.BufferedReader(new java.io.FileReader("/proc/sys/kernel/random/boot_id"));try{return r.readLine();}finally{r.close();}}
 private static String hash(String s)throws Exception{byte[] b=java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));StringBuilder out=new StringBuilder();for(byte v:b)out.append(String.format(java.util.Locale.US,"%02x",v&255));return out.toString();}
 private static String device(Intent i)throws Exception{
  Object d=i.getParcelableExtra("device");if(d!=null){String name=String.valueOf(call(d,"getDeviceName"));return "USB:"+name+":"+call(d,"getDeviceId")+":"+call(d,"getVendorId")+":"+call(d,"getProductId");}
  String serial=i.getStringExtra("serial");return serial==null||serial.length()==0?null:"SERIAL:"+serial;
 }
 private static String read(java.io.File f)throws Exception{if(!f.exists())return "";if(!f.getCanonicalFile().equals(f.getAbsoluteFile())||f.length()>256)throw new java.io.IOException("usb-ledger-identity");java.io.BufferedReader r=new java.io.BufferedReader(new java.io.FileReader(f));try{String line=r.readLine();if(r.readLine()!=null)throw new java.io.IOException("usb-ledger-trailing-data");return line;}finally{r.close();}}
 private static void write(java.io.File f,String value)throws Exception{java.io.File n=new java.io.File(f.getPath()+".next");if(n.exists()||!n.createNewFile())throw new java.io.IOException("usb-ledger-pending");java.io.FileOutputStream o=new java.io.FileOutputStream(n);try{o.write(value.getBytes("US-ASCII"));o.getFD().sync();}finally{o.close();}if(!n.renameTo(f))throw new java.io.IOException("usb-ledger-publish");}

 /** Same narrow exclusion as the existing wired-AA filter: every interface must be storage/hub.
  * Zero-interface and composite/unknown devices remain possible phones, never guessed away. */
 private static boolean nonPhone(Object d)throws Exception{
  int n=((Integer)call(d,"getInterfaceCount")).intValue();if(n<0||n>128)throw new java.io.IOException("usb-interface-count");if(n==0)return false;
  for(int i=0;i<n;i++){Object part=d.getClass().getMethod("getInterface",Integer.TYPE).invoke(d,Integer.valueOf(i));int type=((Integer)call(part,"getInterfaceClass")).intValue();if(type!=8&&type!=9)return false;}return true;
 }
 private static String topology(Context c)throws Exception{
  Object usb=c.getSystemService("usb");if(usb==null)throw new java.io.IOException("usb-inventory-unavailable");
  Object value=call(usb,"getDeviceList");if(!(value instanceof java.util.Map))throw new java.io.IOException("usb-inventory-invalid");
  Object selected=null;for(Object d:((java.util.Map<?,?>)value).values()){
   if(nonPhone(d))continue;if(selected!=null)return "AMBIGUOUS";selected=d;
  }
  if(selected==null)return "EMPTY";
  return hash("USB:"+call(selected,"getDeviceName")+":"+call(selected,"getDeviceId")+":"+call(selected,"getVendorId")+":"+call(selected,"getProductId"));
 }
 private static Object call(Object o,String method)throws Exception{return o.getClass().getMethod(method).invoke(o);}
 public static boolean projectionIdle(Context c){try{
  Object p=c.getSystemService("phoneprojection");if(p==null)return false;
  if(!Integer.valueOf(0).equals(call(p,"getPhoneProjectionStatus")))return false;
  for(String n:new String[]{"getCPConnectedBTAddress","getAAConnectedBTAddress"}){Object v=call(p,n);if(v!=null&&v.toString().length()>0)return false;}
  return Integer.valueOf(0).equals(call(p,"getPhoneProjectionStatus"));
 }catch(Exception unknown){return false;}}
 private static boolean noWirelessOwner(Context c){try{if(new java.io.File("/data/local/ev6-projection/handoff/bluetooth-hold").exists())return false;Object p=c.getSystemService("phoneprojection");if(p==null)return false;Object status=call(p,"getPhoneProjectionStatus");if(!Integer.valueOf(0).equals(status)&&!Integer.valueOf(1).equals(status))return false;for(String n:new String[]{"getCPConnectedBTAddress","getAAConnectedBTAddress"}){Object v=call(p,n);if(v!=null&&v.toString().length()>0)return false;}return status.equals(call(p,"getPhoneProjectionStatus"));}catch(Exception unknown){return false;}}
 /** CP's BT-address field is also populated for WIRED sessions. Do not mistake
  * its delayed disconnect update for a wireless owner. Require OEM wired mode,
  * exact OEM wired peer, physical unplug and the admitted serial proof together. */
 private static boolean endedOwnerSafe(Context c,boolean wiredRoleProof){
  if(noWirelessOwner(c))return true;if(!wiredRoleProof)return false;
  try{
   if(new java.io.File("/data/local/ev6-projection/handoff/bluetooth-hold").exists())return false;
   Object p=c.getSystemService("phoneprojection"),d=c.getSystemService("dipo");if(p==null||d==null)return false;
   Object aa=call(p,"getAAConnectedBTAddress");if(aa!=null&&aa.toString().length()>0)return false;
   if(!Integer.valueOf(1).equals(call(d,"getDiPOConnectedMode")))return false;
   Object cp=call(p,"getCPConnectedBTAddress"),wired=call(d,"getLastWiredCPAddress");
   if(cp==null||wired==null||!cp.toString().matches("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}")||!cp.toString().equalsIgnoreCase(wired.toString()))return false;
   return Integer.valueOf(1).equals(call(d,"getDiPOConnectedMode"))&&cp.equals(call(p,"getCPConnectedBTAddress"))&&(call(p,"getAAConnectedBTAddress")==null||"".equals(call(p,"getAAConnectedBTAddress")));
  }catch(Exception unknown){return false;}
 }
 private static boolean cpRole(String mode){return "carplay".equals(mode)||"carplay_connect".equals(mode);}
 public static boolean usbIdle(Context c){return projectionIdle(c)&&!new java.io.File("/data/local/ev6-projection/handoff/bluetooth-hold").exists();}
 public static synchronized boolean allow(Object receiver,Context c,Intent intent){
  if(intent==null||intent.getAction()==null)return true;
  if(family(intent.getAction())==null)return true;
  try{
   String currentBoot=boot();
   boolean idle=usbIdle(c);
   String action=intent.getAction();
   if(pending!=null && (action.endsWith("DETACHED") || (action.endsWith("ATTACHED") && replaying==null)))pending.cancel();
   if(supportedAttach(receiver,action)&&replaying==null){
    String[] handover=coordinator();
    if(handover.length==3 && receipt(handover,currentBoot,handover[1],"DRAIN"))idle=false;
    // A fresh attach invalidates any earlier generation, including another phone.
    pending=null;

   }
   boolean allowed=allowForBoot(receiver,c,intent,currentBoot,idle);
   decision(receiver,intent,allowed?"admitted":idle?"ledger-or-identity-rejected":"owner-not-idle");return allowed;
  }catch(Exception unknown){decision(receiver,intent,"unverified-"+unknown.getClass().getSimpleName());return false;}
 }
 private static final String CP_ATTACH="android.hardware.usb.action.CARPLAY_DEVICE_ATTACHED";
 private static final String HAND="/data/local/ev6-projection/handoff/";
 private static boolean supportedAttach(Object receiver,String action){
  String name=receiver.getClass().getName();
  if(CP_ATTACH.equals(action))return name.equals("com.tcc.iap2server.Iap2Service$1")
      || name.equals("com.daudio.app.dipo.service.DipoService$34");
  return "android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(action)
      && name.equals("com.google.android.projection.service.StartReceiver");
 }
 private static boolean present(Context c,String action,String identity,String[] role)throws Exception{
  if(identity==null)return false;
  if(CP_ATTACH.equals(action))return configured(identity,role);
  return identity.startsWith("USB:") && hash(identity).equals(topology(c));
 }
 private static Pending pending,replaying;
 private static boolean receipt(String[] row,String boot,String request,String phase){
  return row.length==3 && boot.replace("-","").equals(row[0]) && request.matches("[0-9a-f]{32}")
      && request.equals(row[1]) && phase.equals(row[2]);
 }
 private static boolean configured(String identity,String[] role){
  return cpRole(role[0]) && "CONFIGURED".equals(role[1]) && role[2]!=null && role[2].length()>0
      && ("SERIAL:"+role[2]).equals(identity);
 }
 private static String[] coordinator()throws Exception{
  return read(new java.io.File(HAND+"usb-handover")).split("\\|",-1);
 }
 private static void arm(Object receiver,Context c,Intent intent,String currentBoot)throws Exception{
  if(!supportedAttach(receiver,intent.getAction()))return;
  String[] row=coordinator();String identity=device(intent);
  if(!present(c,intent.getAction(),identity,roleSnapshot()))return;
  String id=row.length==3 && receipt(row,currentBoot,row[1],"DRAIN") ? row[1] : heldRequest(currentBoot);
  if(id==null)return;
  pending=new Pending(receiver,c,intent,currentBoot,id,identity);
  pending.start();
  pending.check();
 }
 private static String heldRequest(String bootId)throws Exception{
  String[] h=read(new java.io.File(HAND+"bluetooth-hold")).split("\\|",-1);
  return h.length==7 && "EV6BTHOLD1".equals(h[0]) && bootId.replace("-","").equals(h[1])
      && h[2].matches("[0-9a-f]{32}") && "END".equals(h[6]) ? h[2] : null;
 }
 private static final class Pending extends Thread{
  final Object receiver;final Context context;final Intent intent;final String bootId,request,identity;
  boolean posted;
  Pending(Object receiver,Context c,Intent intent,String boot,String request,String identity){
   super("ev6-wired-handover");
   this.receiver=receiver;context=c;this.intent=intent;bootId=boot;this.request=request;this.identity=identity;
  }
  public void run(){
   try{
    for(;;){
     synchronized(ProjectionIncomingGuard.class){if(pending!=this||posted)return;}
     Thread.sleep(1000);
     check();
    }
   }catch(InterruptedException cancelled){/* A newer USB event retired this generation. */}
  }
  void cancel(){if(pending==this)pending=null;interrupt();}
  void check(){
   synchronized(ProjectionIncomingGuard.class){
    if(pending!=this||posted)return;
    try{
     String[] row=coordinator();
     if(!receipt(row,bootId,request,"DONE")){
      if(!receipt(row,bootId,request,"DRAIN") && !request.equals(heldRequest(bootId)))cancel();
      return;
     }
     if(!eligible(row)||!usbIdle(context)){cancel();return;}
     posted=new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable(){public void run(){deliver();}});
     if(!posted)cancel();
    }catch(Exception unavailable){cancel();}
   }
  }
  boolean eligible(String[] row)throws Exception{
   return (pending==this||replaying==this) && bootId.equals(boot()) && present(context,intent.getAction(),identity,roleSnapshot())
       && !new java.io.File("/data/local/ev6-projection/maintenance").exists()
       && (receipt(row,bootId,request,"DRAIN")||receipt(row,bootId,request,"WAIT_RELEASE")
           ||receipt(row,bootId,request,"RELEASE_INTENT")||receipt(row,bootId,request,"DONE"));
  }
  void deliver(){
   synchronized(ProjectionIncomingGuard.class){
    try{
     String[] row=coordinator();
     if(!eligible(row)||!receipt(row,bootId,request,"DONE")||!usbIdle(context))return;
     pending=null;replaying=this;
     ((android.content.BroadcastReceiver)receiver).onReceive(context,intent);
    }catch(Exception ignored){/* Consume this attempt without retrying OEM effects. */}
    finally{replaying=null;cancel();}
   }
  }
 }
 /** One bounded record per USB event, no serial/address and no polling on the UI path. */
 private static void decision(Object receiver,Intent intent,String reason){
  try{android.util.Log.i("EV6UsbAdmission","receiver="+receiver.getClass().getName()+" action="+intent.getAction()+" result="+reason);}catch(Exception ignored){}
 }
 private static String family(String a){for(String stem:new String[]{"android.hardware.usb.action.USB_DEVICE_","android.hardware.usb.action.CARPLAY_DEVICE_","android.hardware.usb.action.DIGITAL_AUDIO_DEVICE_"})if(a.equals(stem+"ATTACHED")||a.equals(stem+"DETACHED"))return stem;return null;}
 /** Device-role CP disappears from the HOST inventory after OEM role switching.
  * Read a bounded kernel snapshot; missing/unstable evidence never grants cleanup. */
 private static String[] roleSnapshot(){try{
  Class<?> props=Class.forName("android.os.SystemProperties");java.lang.reflect.Method get=props.getMethod("get",String.class);
  String mode=(String)get.invoke(null,"sys.usb.state"),serial=(String)get.invoke(null,"sys.usb.serial");
  java.io.BufferedReader r=new java.io.BufferedReader(new java.io.FileReader("/sys/class/android_usb/android0/state"));String state;try{state=r.readLine();}finally{r.close();}
  if(!mode.equals(get.invoke(null,"sys.usb.state"))||!serial.equals(get.invoke(null,"sys.usb.serial")))return new String[]{"","",""};
  return new String[]{mode,state,serial};
 }catch(Exception unavailable){return new String[]{"","",""};}}
 /** Same implementation used by host tests with an explicit kernel identity, never a START grant. */
 static synchronized boolean allowForBoot(Object receiver,Context c,Intent intent,String boot,boolean idle)throws Exception{
  return allowForBootRole(receiver,c,intent,boot,idle,roleSnapshot());
 }
 static synchronized boolean allowForBootRole(Object receiver,Context c,Intent intent,String boot,boolean idle,String[] role)throws Exception{
  String a=intent.getAction(),family=family(a);if(family==null)return true;
  if(role==null||role.length!=3)return false;
  if(boot==null||!boot.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"))return false;
  boolean attach=a.endsWith("ATTACHED");String identity=device(intent);if(identity==null&&attach)return false;
  java.io.File dir=new java.io.File(c.getFilesDir().getCanonicalFile(),"ev6-usb-admission");if(!dir.exists()&&!dir.mkdir())return false;
  if(!dir.getCanonicalFile().equals(dir.getAbsoluteFile()))return false;
  String familyKey=hash(receiver.getClass().getName()+"|"+family);
  java.io.File ownerFile=new java.io.File(dir,familyKey+".owner"),lockFile=new java.io.File(dir,familyKey+".lock");
  if(!lockFile.getCanonicalFile().equals(lockFile.getAbsoluteFile()))return false;
  java.io.RandomAccessFile lockHandle=new java.io.RandomAccessFile(lockFile,"rw");java.nio.channels.FileLock lock=null;
  try{lock=lockHandle.getChannel().tryLock();if(lock==null)return false;
  if(new java.io.File(ownerFile.getPath()+".next").exists())return false;
  java.io.File topologyFile=new java.io.File(dir,familyKey+".topology");if(new java.io.File(topologyFile.getPath()+".next").exists())return false;
  String topologyRecord=read(topologyFile);boolean emptySeen=(boot+"|EMPTY").equals(topologyRecord);
  String liveTopology="UNKNOWN";try{liveTopology=topology(c);}catch(Exception unavailable){}
  if(!attach&&liveTopology.equals("EMPTY"))write(topologyFile,boot+"|EMPTY");
  if(attach)write(topologyFile,boot+"|ATTACHED");
  String owner=read(ownerFile);if(owner==null)return false;
  if(!owner.equals("")&&!owner.matches("EV6USBF2\\|[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\|[0-9a-f]{64}\\|(R[0-9a-f]{64}|[0-9a-f]{64}|-)"))return false;
  String[] fields=owner.split("\\|",-1);boolean ownerValid=fields.length==4&&fields[0].equals("EV6USBF2")&&fields[1].equals(boot)&&fields[2].matches("[0-9a-f]{64}")&&fields[3].matches("R[0-9a-f]{64}|[0-9a-f]{64}|-");
  String key=identity==null?null:hash(receiver.getClass().getName()+"|"+family+"|"+identity);
  boolean wiredRoleProof=ownerValid&&fields[3].startsWith("R")&&family.equals("android.hardware.usb.action.CARPLAY_DEVICE_")&&"DISCONNECTED".equals(role[1])&&role[2]!=null&&role[2].length()>0&&fields[3].equals("R"+hash("SERIAL:"+role[2]))&&liveTopology.equals("EMPTY");
  if(identity==null){
   // Anonymous detach is attributable only to the sole admitted possible phone,
   // now absent from the live USB inventory. Another possible phone still present rejects it.
   if(!ownerValid||fields[3].equals("-")||!topology(c).equals("EMPTY")||!endedOwnerSafe(c,wiredRoleProof))return false;
   if(fields[3].startsWith("R")){
    if(!family.equals("android.hardware.usb.action.CARPLAY_DEVICE_")||!"DISCONNECTED".equals(role[1])||role[2]==null||role[2].length()==0||!fields[3].equals("R"+hash("SERIAL:"+role[2])))return false;
   }
   key=fields[2];
  }
  java.io.File f=new java.io.File(dir,key);
  if(new java.io.File(f.getPath()+".next").exists())return false;
  String old=read(f),admitted="EV6USB1|"+boot+"|ADMITTED",ended="EV6USB1|"+boot+"|ENDED";
  if(old==null||(!old.equals("")&&!old.matches("EV6USB1\\|[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\|(ADMITTED|ENDED|REJECTED)")))return false;
  if(!attach){boolean owned=admitted.equals(old);
   // A foreign/late detach must not consume the live receipt before validation.
   if(owned){if(!ownerValid||!fields[2].equals(key)||!endedOwnerSafe(c,wiredRoleProof))return false;
    if(fields[3].startsWith("R")&&!wiredRoleProof)return false;
    write(f,ended);return true;}
   if(("EV6USB1|"+boot+"|REJECTED").equals(old))write(f,ended);return false;
  }
  // Duplicate events may never convert an old rejection or consumed detach into new authority.
  boolean retry=replaying!=null && receiver==replaying.receiver && intent==replaying.intent
      && boot.equals(replaying.bootId) && identity.equals(replaying.identity)
      && receipt(coordinator(),boot,replaying.request,"DONE") && present(c,a,identity,role)
      && ("EV6USB1|"+boot+"|REJECTED").equals(old);
  if(replaying!=null&&!retry)return false;
  if(!retry&&old!=null&&old.startsWith("EV6USB1|"+boot+"|")&&!ended.equals(old)&&!(old.endsWith("|REJECTED")&&emptySeen&&liveTopology.matches("[0-9a-f]{64}")))return false;
  if(!idle){
   write(f,"EV6USB1|"+boot+"|REJECTED");
   if(replaying==null && supportedAttach(receiver,a) && present(c,a,identity,role))arm(receiver,c,intent,boot);
   return false;
  }
  String physical="-";try{String t=topology(c);if(t.matches("[0-9a-f]{64}"))physical=t;}catch(Exception unavailable){/* Identified detach remains possible; anonymous detach must fail closed. */}
  if(physical.equals("-")&&liveTopology.equals("EMPTY")&&family.equals("android.hardware.usb.action.CARPLAY_DEVICE_")&&cpRole(role[0])&&"CONFIGURED".equals(role[1])&&identity.equals("SERIAL:"+role[2]))physical="R"+hash(identity);
  write(ownerFile,"EV6USBF2|"+boot+"|"+key+"|"+physical);
  if(!usbIdle(c)||(retry&&!replaying.eligible(coordinator()))){write(f,"EV6USB1|"+boot+"|REJECTED");return false;}
  write(f,admitted);
  // Recheck after disk publication too: slow I/O is not permission to preempt a new owner.
  if(!usbIdle(c)||(retry&&!replaying.eligible(coordinator()))){write(f,"EV6USB1|"+boot+"|REJECTED");return false;}
  return true;
  }finally{if(lock!=null)lock.release();lockHandle.close();}
 }
 private ProjectionIncomingGuard(){}
}
