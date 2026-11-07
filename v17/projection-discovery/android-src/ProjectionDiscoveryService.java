import android.bluetooth.*;
import android.content.*;
import android.os.*;
/** Fresh observations plus a correlated adapter to the unchanged OEM AA command path. */
public final class ProjectionDiscoveryService extends Binder {
 public static final String NAME="ev6.projection.discovery",DESCRIPTOR="org.ev6.ProjectionDiscovery1";
 private static volatile ProjectionDiscoveryService instance;
 private final ProjectionAaPauseStore aaPause;private final ProjectionCapabilityHintStore hintDisk;private final ProjectionDiscovery model;private final ProjectionSourceBindings sources;
 private final ThreadLocal<CommandScope> command=new ThreadLocal<CommandScope>();
 private static final class CommandScope {final CommandScope previous;final ProjectionAaEntries.Ticket ticket;CommandScope(CommandScope p,ProjectionAaEntries.Ticket t){previous=p;ticket=t;}}
 private final ProjectionRecoveryBarrier recovery=new ProjectionRecoveryBarrier();
 private final java.util.concurrent.ExecutorService firstAaWorker=new java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<Runnable>(16),new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
 private final ProjectionFirstAaAdmission firstAa=new ProjectionFirstAaAdmission();
 private final ProjectionFirstCpEntry firstCpEntry=new ProjectionFirstCpEntry();
 private final ProjectionAaRequests requests=new ProjectionAaRequests();
 private final java.util.concurrent.ConcurrentHashMap<String,ProjectionSourceBindings.Binding> remoteOrigins=new java.util.concurrent.ConcurrentHashMap<String,ProjectionSourceBindings.Binding>();
 private final ThreadLocal<ProjectionAaRequests.Request> dispatching=new ThreadLocal<ProjectionAaRequests.Request>();
 private final ProjectionAaCallBridge calls=new ProjectionAaCallBridge();
 private final ProjectionAaEntries entries=new ProjectionAaEntries();
 private final String incarnation=java.util.UUID.randomUUID().toString();
 private final ThreadLocal<Scope> current=new ThreadLocal<Scope>();
 private final Context owner;private final Object gpm;
 private static final class Scope {final Scope previous;final ProjectionSourceBindings.Binding source;Scope(Scope p,ProjectionSourceBindings.Binding b){previous=p;source=b;}}
 private ProjectionDiscoveryService(Context context)throws Exception{gpm=context;owner=context.getApplicationContext();aaPause=new ProjectionAaPauseStore(owner);hintDisk=new ProjectionCapabilityHintStore(owner);model=new ProjectionDiscovery(new ProjectionOrderedHintStore(hintDisk,new ProjectionOrderedHintStore.Scheduler(){
 private android.os.Handler handler;
 public synchronized void later(Runnable task,long delay){if(handler==null){android.os.HandlerThread writer=new android.os.HandlerThread("EV6ProjectionHints");writer.start();handler=new android.os.Handler(writer.getLooper());}handler.postDelayed(task,delay);}
 }));sources=new ProjectionSourceBindings(model);}
 private void refreshBonded(){try{BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();if(adapter!=null&&adapter.getState()==BluetoothAdapter.STATE_ON){java.util.Set<BluetoothDevice> devices=adapter.getBondedDevices();if(devices==null)return;java.util.HashSet<String> bonded=new java.util.HashSet<String>();for(BluetoothDevice d:devices)bonded.add(d.getAddress());synchronized(this){model.reconcileBonded(bonded,hintDisk.addresses());for(String address:bonded)model.paired(address);}}}catch(Exception unavailable){}}
 private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){
  synchronized(ProjectionDiscoveryService.this){try{
   if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())){
    int state=intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1);
    if(state==BluetoothAdapter.STATE_OFF){sources.retireAll();model.adapterOff();firstCpEntry.clear();firstAa.clear();}else if(state==BluetoothAdapter.STATE_ON)refreshBonded();return;
   }
   BluetoothDevice d=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);if(d==null)return;String address=d.getAddress();
   if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())){firstAa.bond(address,intent.getIntExtra("android.bluetooth.device.extra.PREVIOUS_BOND_STATE",-1),d.getBondState(),android.os.SystemClock.elapsedRealtime());if(d.getBondState()==BluetoothDevice.BOND_NONE){ProjectionDiscovery.Snapshot old=model.snapshot(address);if(old!=null)sources.retirePeer(address,old.generation);model.unpair(address);firstCpEntry.unpair(address);aaPause.unpair(address);}else if(d.getBondState()==BluetoothDevice.BOND_BONDED){model.paired(address);admitFirstAa(address,sources.aaCandidate(address));}return;}
   if(d.getBondState()!=BluetoothDevice.BOND_BONDED)return;
   if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())){
    ProjectionDiscovery.Snapshot old=model.snapshot(address);long next=model.connected(address);if(old!=null&&old.generation!=next)sources.retirePeer(address,old.generation);
   }else if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())){
    ProjectionDiscovery.Snapshot old=model.snapshot(address);if(old!=null){sources.retirePeer(address,old.generation);model.disconnected(address,old.generation);}
   }
  }catch(Exception unavailable){}}
 }};
 private final ProjectionBluetoothBinding bluetoothBinding=new ProjectionBluetoothBinding();
 public static synchronized void install(Context context){
  if(instance!=null||android.os.Process.myUid()!=1000)return;
  try{
   Class<?> sm=Class.forName("android.os.ServiceManager");IBinder prior=(IBinder)sm.getMethod("checkService",String.class).invoke(null,NAME);if(prior!=null&&prior.isBinderAlive())return;
   ProjectionDiscoveryService s=new ProjectionDiscoveryService(context);IntentFilter f=new IntentFilter();
   f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
   context.registerReceiver(s.receiver,f);
   try{sm.getMethod("addService",String.class,IBinder.class).invoke(null,NAME,s);instance=s;s.refreshBonded();s.bluetoothBinding.start(context);}catch(Exception denied){context.unregisterReceiver(s.receiver);}
  }catch(Exception unavailable){}
 }
 /** Called at actual ConnectedThread construction, before any frame can be delivered. */
 public static void socketOpened(BluetoothSocket socket){ProjectionDiscoveryService s=instance;if(s==null||socket==null)return;
  synchronized(s){try{if(!socket.isConnected()||s.sources.seen(socket))return;BluetoothDevice d=socket.getRemoteDevice();if(d==null||d.getBondState()!=BluetoothDevice.BOND_BONDED)return;
   long g=s.model.connected(d.getAddress());s.sources.attach(d.getAddress(),g,ProjectionDiscovery.AA,socket);
  }catch(Exception unavailable){}}
 }
 public static void socketClosed(BluetoothSocket socket){ProjectionDiscoveryService s=instance;if(s!=null&&socket!=null)synchronized(s){s.sources.close(socket);}}
 public static Object enter(BluetoothSocket socket){ProjectionDiscoveryService s=instance;if(s==null)return null;
  Scope previous=s.current.get();Scope scope=new Scope(previous,previous==null?s.sources.find(socket):null);s.current.set(scope);return scope;
 }
 public static void exit(Object captured){ProjectionDiscoveryService s=instance;if(s==null)return;Scope now=s.current.get();if(now!=captured){s.current.remove();return;}if(now==null||now.previous==null)s.current.remove();else s.current.set(now.previous);}
 public static void confirmedAa(BluetoothDevice device){ProjectionDiscoveryService s=instance;if(s==null||device==null)return;Scope scope=s.current.get();if(scope!=null&&scope.source!=null)synchronized(s){s.sources.aaVersion(scope.source,device.getAddress(),true);
   s.admitFirstAa(device.getAddress(),scope.source);
  }}
 private void admitFirstAa(final String peer,final ProjectionSourceBindings.Binding source){
  if(source==null)return;
  try{firstAaWorker.execute(new Runnable(){public void run(){admitFirstAaWorker(peer,source);}});}catch(java.util.concurrent.RejectedExecutionException busy){/* Bounded queue: no callback/main-thread Binder wait. */}
 }
 private void admitFirstAaWorker(String peer,ProjectionSourceBindings.Binding source){
  try{boolean idle=remoteAdmissionIdle(),paused=aaPause.paused(peer);
   synchronized(this){if(!sources.aaStartEligible(source,peer)||!firstAa.claim(peer,android.os.SystemClock.elapsedRealtime(),idle,paused))return;
   synchronized(requests){for(java.util.Map.Entry<String,ProjectionSourceBindings.Binding> old:remoteOrigins.entrySet())if(old.getValue()==source&&requests.findManaged(old.getKey(),peer)!=null)return;
    ProjectionAaRequests.Request request=requests.prepare(peer,false);if(!requests.defer(request))throw new IllegalStateException("first-aa-defer");remoteOrigins.put(request.id,source);
   }}
  }catch(Exception unavailable){/* No direct start, AP operation or replacement of a live owner. */}
 }
 /** Applies only to native START admission; never cancels a BT profile or an existing STOP. */
 static IBinder aaBluetoothOwner(){ProjectionDiscoveryService s=instance;return s==null?null:s.bluetoothBinding.current();}
 public static boolean aaStartAllowed(String address){ProjectionDiscoveryService s=instance;if(s==null)return false;try{if(s.aaPause.paused(address))return false;if(ProjectionAaExclusiveProof.allowed(address))return true;
  if(!s.aaIdleSnapshot()||s.otherProjectionActive())return false;
  Scope scope=s.current.get();if(scope==null||!s.sources.aaStartEligible(scope.source,address))return false;
  synchronized(s.requests){for(java.util.Map.Entry<String,ProjectionSourceBindings.Binding> old:s.remoteOrigins.entrySet())if(old.getValue()==scope.source){ProjectionAaRequests.Request prior=s.requests.findManaged(old.getKey(),address);if(prior!=null)return false;}
   ProjectionAaRequests.Request request=s.requests.prepare(address,false);if(!s.requests.defer(request))throw new IllegalStateException("remote-aa-defer");s.remoteOrigins.put(request.id,scope.source);
  }return false;
 }catch(Exception unavailable){return false;}}
 /** This hook runs before connectWireless enters any original radio/AP operation. */
 public static boolean aaConnectAllowed(String address){boolean allowed=aaStartAllowed(address);if(!allowed){ProjectionDiscoveryService s=instance;if(s!=null){CommandScope scope=s.command.get();ProjectionAaRequests.Request request=s.dispatching.get();if(scope!=null&&scope.previous==null&&scope.ticket!=null&&request!=null&&!request.disconnect&&request.address.equalsIgnoreCase(address)&&request.ticket()==scope.ticket)s.entries.bind(scope.ticket,new ProjectionAaEntries.Completion(){public boolean drained(){return true;}});}}return allowed;}
 public static Object aaEntered(int type,String address,int connection,boolean disconnect){ProjectionDiscoveryService s=instance;if(s==null)return null;CommandScope scope=new CommandScope(s.command.get(),s.entries.enter(type,address,connection));s.command.set(scope);if(scope.ticket!=null)s.calls.capture(address,disconnect,scope.ticket);s.requests.capture(s.dispatching.get(),address,disconnect,scope.ticket);if(disconnect&&scope.ticket!=null)s.aaDisconnect(scope.ticket,address);return scope;}
 public static void aaReturned(Object token){ProjectionDiscoveryService s=instance;if(s==null||!(token instanceof CommandScope))return;CommandScope scope=(CommandScope)token;if(s.command.get()!=scope){s.command.remove();return;}s.entries.returned(scope.ticket);if(scope.previous==null)s.command.remove();else s.command.set(scope.previous);}
 /** Capture the exact preparation while still inside its originating command stack. */
 public static void aaRoot(Object radioScope){ProjectionDiscoveryService s=instance;if(s==null||radioScope==null)return;CommandScope scope=s.command.get();if(scope==null||scope.ticket==null)return;
  try{final Class<?> radio=Class.forName("org.ev6.research.aa.AaRadioOrigins");
   if(Boolean.TRUE.equals(radio.getMethod("rootDeniedWithoutWork",Object.class).invoke(null,radioScope))){
    ProjectionAaRequests.Request selected=s.dispatching.get();
    if(scope.previous==null&&selected!=null&&!selected.disconnect&&selected.ticket()==scope.ticket)
     s.entries.bind(scope.ticket,new ProjectionAaEntries.Completion(){public boolean drained(){return true;}});
    return;
   }
   final Object attempt,preparation;synchronized(radio){attempt=field(radioScope,"rootAttempt");Object operation=field(attempt,"operation");preparation=field(operation,"preparation");}
   final Object service=s.gpm;
   final java.util.concurrent.atomic.AtomicReference<ProjectionAaLeaseCompletion> releasing=new java.util.concurrent.atomic.AtomicReference<ProjectionAaLeaseCompletion>();
   final java.util.concurrent.atomic.AtomicReference<ProjectionAaStopWorker> stopping=new java.util.concurrent.atomic.AtomicReference<ProjectionAaStopWorker>();
   s.entries.bindCancellation(scope.ticket,new ProjectionAaEntries.Cancellation(){

    public boolean request()throws Exception{
     if(((Boolean)radio.getMethod("cancelCapturedPendingRoot",Object.class).invoke(null,attempt)).booleanValue())return true;
     final ProjectionAaStopWorker selected;
     synchronized(this){
      if(stopping.get()==null){final Object exact=call(preparation,"request");if(exact==null)return false;
       stopping.set(new ProjectionAaStopWorker(new ProjectionAaStopWorker.Target(){public boolean stopIfCurrent()throws Exception{if(ProjectionAaReceiverStop.completed(service,exact))return true;ProjectionAaReceiverStop receiver=ProjectionAaReceiverStop.captureForCancellation(service,exact);if(receiver!=null)return receiver.stopIfCurrent();ProjectionAaPendingStop stop=ProjectionAaPendingStop.capture(service,exact);return stop!=null?stop.stopIfCurrent():ProjectionAaServiceProof.stopNeverEntered(service,exact);}},new ProjectionAaStopWorker.Executor(){public void execute(Runnable work){Thread thread=new Thread(work,"EV6AaPendingStop");thread.start();}}));}
      selected=stopping.get();
     }
     return selected.request();
    }
   });
   s.entries.bind(scope.ticket,new ProjectionAaEntries.Completion(){public boolean drained()throws Exception{synchronized(radio){Object request=call(preparation,"request");if(request!=null){ProjectionAaStopWorker stop=stopping.get();
     if(stop==null&&!ProjectionAaReceiverStop.completed(service,request))return ((Boolean)call(request,"drained")).booleanValue();
     if(stop!=null&&!stop.returned()&&!stop.failed())return false;
     // Retry only positive never-entered proof, never an uncertain native stop.
     ProjectionAaLeaseCompletion release=releasing.get();if(release==null){release=new ProjectionAaLeaseCompletion(service,request,stop!=null&&!stop.returned()&&!ProjectionAaReceiverStop.completed(service,request));releasing.set(release);}return release.completed();}
    // Before acquisition, completion requires the exact attempt's proven cleanup.
    return ((Boolean)field(attempt,"done")).booleanValue()&&!((Boolean)field(attempt,"accepted")).booleanValue()&&((Integer)field(attempt,"cleanupState")).intValue()==1;}}});
  }catch(Exception unavailable){}
 }
 /** Bind before issuing ByeBye, using the exact current receiver's registered transport origin. */
 private void aaDisconnect(ProjectionAaEntries.Ticket ticket,String address){
  try{Object pp=owner.getSystemService("phoneprojection");if(!address.equalsIgnoreCase((String)call(pp,"getAAConnectedBTAddress")))return;
   Object receiver=field(gpm,"mGalReceiver"),transport=field(gpm,"mWifiTransport");Class<?> guard=Class.forName("org.ev6.research.aa.AaDisconnectGuard");
   java.lang.reflect.Field lockField=guard.getDeclaredField("MAP"),mapField=guard.getDeclaredField("BINDINGS");lockField.setAccessible(true);mapField.setAccessible(true);
   final Object request;Object lock=lockField.get(null);synchronized(lock){request=ProjectionReceiverRequest.capture((java.util.Map<?,?>)mapField.get(null),gpm,receiver,transport);}
   if(request==null||field(gpm,"mGalReceiver")!=receiver||field(gpm,"mWifiTransport")!=transport||!address.equalsIgnoreCase((String)call(pp,"getAAConnectedBTAddress")))return;
   entries.bind(ticket,new ProjectionAaEntries.Completion(){public boolean drained()throws Exception{return ((Boolean)call(request,"drained")).booleanValue();}});
  }catch(Exception unavailable){}
 }
 private static Object field(Object o,String name)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 private static Object call(Object o,String name)throws Exception{java.lang.reflect.Method m=o.getClass().getMethod(name);m.setAccessible(true);return m.invoke(o);}
 /** Read actual OEM lifecycle only; never derives capability from lifecycle or missing data. */
 private int aaState(String address){
  try{
   Object explorer=field(gpm,"mWirelessExplorer");Object thread=field(explorer,"mConnectedThread");
   BluetoothDevice device=(BluetoothDevice)call(explorer,"getDevice");
   if(device==null){Object registry=call(explorer,"getWirelessDeviceInfo");device=(BluetoothDevice)call(registry,"getBluetoothDevice");}

   int mode=((Integer)call(gpm,"getGpmConnectMode")).intValue(),status=((Integer)call(gpm,"getGpmStatus")).intValue();
   Object transport=field(gpm,"mWifiTransport");Object pp=owner.getSystemService("phoneprojection");String active=(String)call(pp,"getAAConnectedBTAddress");
   if(field(gpm,"mWirelessExplorer")!=explorer||field(explorer,"mConnectedThread")!=thread||field(gpm,"mWifiTransport")!=transport
     ||((Integer)call(gpm,"getGpmConnectMode")).intValue()!=mode||((Integer)call(gpm,"getGpmStatus")).intValue()!=status)return -1;
   if(mode==0&&(status==1||status==2)&&transport==null&&(active==null||active.length()==0))return 0;
   if(device==null||!address.equalsIgnoreCase(device.getAddress()))return -1;
   if(mode==2&&status==6&&address.equalsIgnoreCase(active))return 3;
   if(mode==2&&status>=3&&status<=5)return 2;
   if(mode==0&&(status==1||status==2)&&transport==null&&(active==null||active.length()==0))return 0;
  }catch(Exception unavailable){}return -1;
 }
 private boolean recoveryTransaction(int code,Parcel data,Parcel reply,int flags){
  int allowed=code==7?1000:0;if(Binder.getCallingUid()!=allowed||(flags&~0x10)!=0||reply==null)throw new SecurityException("recovery-caller");
  data.enforceInterface(DESCRIPTOR);if(data.readInt()!=5)throw new SecurityException("recovery-version");
  if(code==12){if(data.dataAvail()!=0)throw new SecurityException("incarnation-schema");reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);return true;}
  if(code==7||code==26){String prior=data.readString(),id=data.readString(),address=data.readString();int action=data.readInt();if(data.dataAvail()!=0||(action!=0&&action!=1))throw new SecurityException("recovery-schema");boolean complete=recovery.register(incarnation,prior,id,address,action==1);reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeInt(complete?1:0);return true;}
  if(code==8){if(data.dataAvail()!=0)throw new SecurityException("recovery-schema");String batch=recovery.capture();reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeString(batch);reply.writeInt(recovery.size(batch));return true;}
  String expected=data.readString(),batch=data.readString();if(data.dataAvail()!=0||!incarnation.equals(expected))throw new SecurityException("recovery-incarnation");boolean complete;if(code==10){recovery.discard(batch);complete=true;}else complete=recovery.certify(batch,aaIdleKnown());reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeInt(complete?1:0);return true;
 }
 private boolean cpSessionHint(Parcel data,Parcel reply,int flags){
  if(Binder.getCallingUid()!=0||(flags&~0x10)!=0||reply==null)throw new SecurityException("cp-session-root-only");
  data.enforceInterface(DESCRIPTOR);if(data.readInt()!=5)throw new SecurityException("cp-session-version");String expected=data.readString(),address=data.readString(),receiver=data.readString();long epoch=data.readLong();
  if(!incarnation.equals(expected)||receiver==null||!receiver.matches("[1-9][0-9]{0,7}:[1-9][0-9]{0,18}:[1-9][0-9]{0,18}")||epoch<=0||data.dataAvail()!=0)throw new SecurityException("cp-session-proof");
  try{BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();Object pp=owner.getSystemService("phoneprojection");
   if(adapter==null||adapter.getRemoteDevice(address).getBondState()!=BluetoothDevice.BOND_BONDED||!address.equalsIgnoreCase((String)call(pp,"getCPConnectedBTAddress")))throw new SecurityException("cp-session-association");
   boolean remembered=model.verifiedSessionHint(address,ProjectionDiscovery.CP);reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeInt(remembered?1:0);return true;
  }catch(SecurityException denied){throw denied;}catch(Exception unavailable){throw new IllegalStateException("cp-session-unavailable");}
 }
 private boolean remoteAdmissionIdle(){try{return ProjectionIncomingGuard.projectionIdle(owner)&&aaIdleSnapshot();}catch(Exception unknown){return false;}}
 private boolean otherProjectionActive()throws Exception{
  Object pp=owner.getSystemService("phoneprojection");Object cp=call(pp,"getCPConnectedBTAddress");
  return !ProjectionIncomingGuard.projectionIdle(owner)||cp!=null&&cp.toString().length()>0;
 }
 private boolean aaIdleSnapshot()throws Exception{
  int mode=((Integer)call(gpm,"getGpmConnectMode")).intValue(),status=((Integer)call(gpm,"getGpmStatus")).intValue(),pre=((Integer)field(gpm,"mPreConnectMode")).intValue();
  Object wifi=field(gpm,"mWifiTransport"),usb=field(gpm,"mUsbTransport"),transport=field(gpm,"mTransport"),device=field(gpm,"mUsbDevice");
  Object pp=owner.getSystemService("phoneprojection");String active=(String)call(pp,"getAAConnectedBTAddress");
  return ProjectionAaIdlePolicy.idle(mode,status,pre,wifi!=null,usb!=null,transport!=null,device!=null,active)
   &&((Integer)call(gpm,"getGpmConnectMode")).intValue()==mode&&((Integer)call(gpm,"getGpmStatus")).intValue()==status&&((Integer)field(gpm,"mPreConnectMode")).intValue()==pre
   &&field(gpm,"mWifiTransport")==wifi&&field(gpm,"mUsbTransport")==usb&&field(gpm,"mTransport")==transport&&field(gpm,"mUsbDevice")==device
   &&(active==null?call(pp,"getAAConnectedBTAddress")==null:active.equals(call(pp,"getAAConnectedBTAddress")));

 }
 private boolean aaIdleKnown(){try{return aaIdleSnapshot();}catch(Exception unknown){return false;}}
 private boolean handoffStopTransaction(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  if(Binder.getCallingUid()!=0||reply==null||(flags&~0x10)!=0)throw new SecurityException("aa-handoff-root");data.enforceInterface(DESCRIPTOR);if(data.readInt()!=5||!incarnation.equals(data.readString()))throw new SecurityException("aa-handoff-incarnation");
  try{
   if(code==20){String peer=data.readString();if(data.dataAvail()!=0)throw new SecurityException("aa-active-schema");reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeInt(aaState(peer)==3?1:0);return true;}
   // Read-only readiness: remembered capability is not a live AA version exchange.
   if(code==31){String peer=data.readString();if(peer==null||!peer.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")||data.dataAvail()!=0)throw new SecurityException("aa-ready-schema");reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeInt(sources.aaCandidate(peer)!=null?1:0);return true;}
   // Prepare only. The normal deferred grant still owns exclusivity, AP and OEM connectPP.
   if(code==32){String id=data.readString(),peer=data.readString();
    if(id==null||!id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")||peer==null||!peer.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")||data.dataAvail()!=0)throw new SecurityException("aa-retry-schema");
    boolean prepared=false;
    synchronized(requests){
     ProjectionAaRequests.Request old=requests.findManaged(id,peer);
     if(old!=null)prepared=requests.isDeferred(old)&&remoteOrigins.containsKey(id)&&sources.aaStartEligible(remoteOrigins.get(id),peer)&&!aaPause.paused(peer);
     else if(requests.deferredRequests().length==0&&!aaPause.paused(peer)&&remoteAdmissionIdle()
       &&!new java.io.File("/data/local/ev6-projection/handoff/bluetooth-hold").exists()
       &&!new java.io.File("/data/local/ev6-projection/handoff/start-grant").exists()){
      BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();ProjectionSourceBindings.Binding source=sources.aaCandidate(peer);
      if(adapter!=null&&adapter.getRemoteDevice(peer).getBondState()==BluetoothDevice.BOND_BONDED&&source!=null&&sources.aaStartEligible(source,peer)){
       ProjectionAaRequests.Request retry=requests.prepareRetry(id,peer);remoteOrigins.put(retry.id,source);prepared=requests.isDeferred(retry);
      }
     }
    }
    reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeInt(prepared?1:0);return true;
   }
   if(code==16){if(data.dataAvail()!=0)throw new SecurityException("aa-idle-schema");reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeInt(aaIdleSnapshot()?1:0);return true;}
   final String id=data.readString(),peer=data.readString();if(data.dataAvail()!=0)throw new SecurityException("aa-stop-schema");
   ProjectionAaRequests.Request request;
   if(code==24){if(!aaIdleSnapshot()||new java.io.File("/data/local/ev6-projection/handoff/bluetooth-hold").exists())throw new IllegalStateException("aa-stop-retire-not-drained");ProjectionAaRequests.Request old=requests.find(id,peer,true);if(old!=null){long[] exact=entries.snapshot((ProjectionAaEntries.Ticket)old.ticket());if(!(exact[0]>0&&exact[1]==0&&exact[2]==1&&exact[3]==1))throw new IllegalStateException("aa-stop-retire-unproven");requests.retire(old);}for(ProjectionAaRequests.Request archived:requests.retiredManagedFor(peer)){long[] exact=entries.snapshot((ProjectionAaEntries.Ticket)archived.ticket());if(requests.cancelledBeforeEffects(archived)||(exact[0]>0&&exact[1]==0&&exact[2]==1&&exact[3]==1)){requests.forgetManaged(archived);remoteOrigins.remove(archived.id);}}reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeInt(1);return true;}
   if(code==17){Object pp=owner.getSystemService("phoneprojection");ProjectionAaRequests.Request old=requests.find(id,peer,true);if(old==null&&!peer.equalsIgnoreCase((String)call(pp,"getAAConnectedBTAddress")))throw new SecurityException("aa-stop-target");request=requests.prepareRootStop(id,peer);
    if(requests.start(request)){final ProjectionAaRequests.Request selected=request;final Object manager=pp;dispatching.set(selected);try{calls.invoke(peer,true,new ProjectionAaCallBridge.Action(){public void run()throws Exception{manager.getClass().getMethod("disconnectPP",Integer.TYPE,String.class,Integer.TYPE).invoke(manager,Integer.valueOf(2),peer,Integer.valueOf(1));}});}finally{dispatching.remove();}}
   }else{request=requests.find(id,peer,true);if(request==null)throw new SecurityException("aa-stop-unknown");}
   long[] receipt=entries.snapshot((ProjectionAaEntries.Ticket)request.ticket());boolean done=receipt[0]>0&&receipt[1]==0&&receipt[2]==1&&receipt[3]==1&&aaIdleSnapshot();reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeInt(done?1:0);return true;
  }catch(SecurityException denied){throw denied;}catch(Exception unknown){throw new RemoteException("aa-handoff-outcome-unknown");}
 }
 /** Root query/cancel is exact-id and incarnation pinned; native cancellation is queued off Binder. */
 private boolean deferredStatusTransaction(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  if(Binder.getCallingUid()!=0||reply==null||(flags&~0x10)!=0)throw new SecurityException("aa-status-root");data.enforceInterface(DESCRIPTOR);if(data.readInt()!=5||!incarnation.equals(data.readString()))throw new SecurityException("aa-status-incarnation");
  String id=data.readString(),peer=data.readString();if(data.dataAvail()!=0)throw new SecurityException("aa-status-schema");
  try{
   if(code==19){ProjectionAaEntries.Ticket captured=null;synchronized(requests){requests.expireDeferred();ProjectionAaRequests.Request selected=requests.findManaged(id,peer);if(selected!=null&&!requests.cancelDeferred(selected))captured=(ProjectionAaEntries.Ticket)selected.ticket();}entries.cancel(captured);}
   synchronized(requests){requests.expireDeferred();ProjectionAaRequests.Request r=requests.findManaged(id,peer);int status=-1;
   if(r!=null){if(requests.cancelledBeforeEffects(r))status=2;else if(requests.isDeferred(r))status=0;else {long[] exact=entries.snapshot((ProjectionAaEntries.Ticket)r.ticket());status=exact[0]>0&&exact[1]==0&&exact[2]==1&&exact[3]==1&&aaIdleSnapshot()?3:1;}}
   if(code==23){if(!aaIdleSnapshot())throw new IllegalStateException("aa-retire-not-idle");java.io.File hold=new java.io.File("/data/local/ev6-projection/handoff/bluetooth-hold");if(hold.exists())throw new IllegalStateException("aa-retire-hold-present");if(r!=null){if(status!=2&&status!=3)throw new IllegalStateException("aa-retire-unproven");requests.forgetManaged(r);remoteOrigins.remove(r.id);}status=4;}
   reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeInt(status);return true;
  }}catch(Exception unknown){throw new RemoteException("aa-status-unavailable");}
 }
 private boolean deferredTransaction(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  if(Binder.getCallingUid()!=0||reply==null||(flags&~0x10)!=0)throw new SecurityException("aa-deferred-root");
  data.enforceInterface(DESCRIPTOR);if(data.readInt()!=5)throw new SecurityException("aa-deferred-version");String expected=data.readString();if(!incarnation.equals(expected))throw new SecurityException("aa-deferred-incarnation");
  if(code==14||code==28){synchronized(requests){for(java.util.Map.Entry<String,ProjectionSourceBindings.Binding> origin:remoteOrigins.entrySet()){ProjectionAaRequests.Request pending=null;for(ProjectionAaRequests.Request candidate:requests.deferredRequests())if(candidate.id.equals(origin.getKey()))pending=candidate;if(pending!=null&&(!sources.aaStartEligible(origin.getValue(),pending.address)||aaPause.paused(pending.address)||!remoteAdmissionIdle()))requests.cancelDeferred(pending);}}if(data.dataAvail()!=0)throw new SecurityException("aa-list-schema");ProjectionAaRequests.Request[] waiting=requests.deferredRequests();reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeInt(waiting.length);for(ProjectionAaRequests.Request r:waiting){reply.writeString(r.id);reply.writeString(r.address);if(code==28)reply.writeInt(remoteOrigins.containsKey(r.id)?0:1);}return true;}
  String id=data.readString(),address=data.readString();final String lease=data.readString();if(data.dataAvail()!=0)throw new SecurityException("aa-grant-schema");final ProjectionAaRequests.Request r=requests.find(id,address,false);if(r==null)throw new SecurityException("aa-grant-unknown");
  try{
   ProjectionAaDeferredDispatch.execute(requests,r,new ProjectionAaDeferredDispatch.Port(){
    boolean pauseBefore,cleared;
    public void verifyExclusive(String requestId,String peer)throws Exception{
     if(sources.aaCandidate(peer)==null)throw new IllegalStateException("aa-transport-no-longer-ready");
     ProjectionSourceBindings.Binding origin=remoteOrigins.get(requestId);if(origin!=null&&(!sources.aaStartEligible(origin,peer)||aaPause.paused(peer)))throw new IllegalStateException("aa-request-source-retired");
     ProjectionAaExclusiveProof.verify(requestId,peer,lease);BluetoothAdapter b=BluetoothAdapter.getDefaultAdapter();if(b==null||b.getRemoteDevice(peer).getBondState()!=BluetoothDevice.BOND_BONDED)throw new IllegalStateException("aa-grant-unpaired");
     Object pp=owner.getSystemService("phoneprojection");String cp=(String)call(pp,"getCPConnectedBTAddress");if(cp!=null&&cp.length()>0)throw new IllegalStateException("aa-grant-cp-active");if(!aaIdleSnapshot())throw new IllegalStateException("aa-grant-not-idle");
    }
    public void clearManualPause(String peer)throws Exception{pauseBefore=aaPause.paused(peer);if(remoteOrigins.containsKey(r.id)){if(pauseBefore)throw new IllegalStateException("remote-aa-manually-ended");return;}aaPause.manualIntent(peer,false);cleared=true;}
    public void restoreManualPause(String peer)throws Exception{if(cleared)aaPause.manualIntent(peer,pauseBefore);}
    public Object invoke(final String peer)throws Exception{
     final Object pp=owner.getSystemService("phoneprojection");dispatching.set(r);
     try{return calls.invoke(peer,false,new ProjectionAaCallBridge.Action(){public void run()throws Exception{pp.getClass().getMethod("connectPP",Integer.TYPE,String.class,Integer.TYPE).invoke(pp,Integer.valueOf(2),peer,Integer.valueOf(1));}}).ticket();}
     finally{dispatching.remove();}
    }
   });
   ProjectionAaEntries.Ticket ticket=(ProjectionAaEntries.Ticket)r.ticket();reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeLong(ticket==null?0:ticket.sequence);return true;
  }catch(Exception unknown){throw new RemoteException("aa-deferred-outcome-unknown");}
 }
 private boolean aaSessionHint(Parcel data,Parcel reply,int flags){
  if(Binder.getCallingUid()!=0||reply==null||(flags&~0x10)!=0)throw new SecurityException("aa-session-root");
  data.enforceInterface(DESCRIPTOR);if(data.readInt()!=5||!incarnation.equals(data.readString()))throw new SecurityException("aa-session-generation");
  String id=data.readString(),peer=data.readString();if(id==null||peer==null||data.dataAvail()!=0)throw new SecurityException("aa-session-schema");
  ProjectionAaRequests.Request r=requests.findManaged(id,peer);
  if(r==null||r.disconnect||r.ticket()==null||requests.isDeferred(r)||requests.cancelledBeforeEffects(r))throw new SecurityException("aa-session-request");
  long[] exact=entries.snapshot((ProjectionAaEntries.Ticket)r.ticket());
  BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
  if(exact[0]<=0||adapter==null||adapter.getRemoteDevice(peer).getBondState()!=BluetoothDevice.BOND_BONDED||aaState(peer)!=3||!ProjectionAaExclusiveProof.allowed(peer))throw new SecurityException("aa-session-unconfirmed");
  boolean saved=model.verifiedSessionHint(peer,ProjectionDiscovery.AA);
  reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeInt(saved?1:0);return true;
 }
 protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  if(code==30)return aaSessionHint(data,reply,flags);
  if(code==29){if(Binder.getCallingUid()!=0||reply==null||(flags&~0x10)!=0)throw new SecurityException("aa-pause-root");data.enforceInterface(DESCRIPTOR);if(data.readInt()!=5||!incarnation.equals(data.readString()))throw new SecurityException("aa-pause-generation");String peer=data.readString();if(peer==null||!peer.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")||data.dataAvail()!=0)throw new SecurityException("aa-pause-peer");reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeInt(aaPause.paused(peer)?1:0);return true;}
  if(code==27)return bluetoothBinding.export(data,reply,flags,incarnation);

  if(code==19||code==21||code==23)return deferredStatusTransaction(code,data,reply,flags);
  if(code==16||code==17||code==18||code==20||code==24||code==31||code==32)return handoffStopTransaction(code,data,reply,flags);
  if(code==13||code==14||code==28)return deferredTransaction(code,data,reply,flags);
  if(code==11)return cpSessionHint(data,reply,flags);
  if(code==7||code==8||code==9||code==10||code==12||code==26)return recoveryTransaction(code,data,reply,flags);
  if(code!=1&&code!=2&&code!=3&&code!=4&&code!=5&&code!=6)return super.onTransact(code,data,reply,flags);
  if(Binder.getCallingUid()!=1000||(flags&~0x10)!=0||reply==null)throw new SecurityException("discovery-reader");
  data.enforceInterface(DESCRIPTOR);if(data.readInt()!=5)throw new SecurityException("discovery-version");final String address=data.readString();boolean commandApi=code==2||code==4||code==5||code==6;int action=commandApi?data.readInt():0;String expected=commandApi?data.readString():null;String id=(code==2||code==4||code==6)?data.readString():null;final boolean disconnect=action==1;if((commandApi&&(action<0||action>1||!incarnation.equals(expected)))||data.dataAvail()!=0)throw new SecurityException("discovery-request");
  if(code==5){ProjectionDiscovery.Snapshot selected=model.snapshot(address);BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();if(selected==null||selected.displayType!=ProjectionDiscovery.AA||adapter==null||adapter.getRemoteDevice(address).getBondState()!=BluetoothDevice.BOND_BONDED)throw new SecurityException("aa-selection");ProjectionAaRequests.Request prepared=requests.prepare(address,disconnect);reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeString(prepared.id);return true;}
  requests.expireDeferred();
  final ProjectionAaRequests.Request owned=commandApi&&code!=5?requests.find(id,address,disconnect):null;
  if(code==6&&owned==null){reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);for(long part:new long[]{0,0,1,1})reply.writeLong(part);return true;}
  if((code==2||code==4)&&owned==null)throw new SecurityException("unknown-aa-request");
  if((code==4||code==6)&&requests.isDeferred(owned)){reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);for(long part:new long[]{0,1,0,0})reply.writeLong(part);return true;}
  if(code==4||code==6){long[] exact=requests.sealUnstarted(owned)?new long[]{0,0,1,1}:entries.snapshot((ProjectionAaEntries.Ticket)owned.ticket());
   if(code==6){if(exact[3]==1&&aaIdleKnown())requests.retire(owned);else if(!disconnect&&aaState(address)==3&&exact[0]>0)requests.retire(owned);exact=new long[]{0,0,1,requests.find(id,address,disconnect)==null?1:0};}
   reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);for(long part:exact)reply.writeLong(part);return true;
  }
  if(code==2){
   ProjectionDiscovery.Snapshot selected=model.snapshot(address);BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
   if(selected==null||selected.displayType!=ProjectionDiscovery.AA||adapter==null||adapter.getRemoteDevice(address).getBondState()!=BluetoothDevice.BOND_BONDED)throw new SecurityException("aa-selection");
   try{final Object pp=owner.getSystemService("phoneprojection");
    if(disconnect&&!address.equalsIgnoreCase((String)call(pp,"getAAConnectedBTAddress")))throw new SecurityException("aa-disconnect-target");
    // Explicit START is retained before pause mutation or OEM effects. A coordinator must
    // obtain the exact target exclusive lease before dispatching this retained request.
    if(!disconnect){requests.defer(owned);reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeLong(0);return true;}
    if(!requests.start(owned)){ProjectionAaEntries.Ticket prior=(ProjectionAaEntries.Ticket)owned.ticket();reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeLong(prior==null?0:prior.sequence);return true;}
    try{aaPause.manualIntent(address,disconnect);}catch(Exception commitFailed){if(!requests.cancelBeforeEffects(owned))throw new IllegalStateException("pause-noeffect-unverified");throw commitFailed;}
    dispatching.set(owned);
    ProjectionAaCallBridge.Call dispatched=calls.invoke(address,disconnect,new ProjectionAaCallBridge.Action(){public void run()throws Exception{
     pp.getClass().getMethod(disconnect?"disconnectPP":"connectPP",Integer.TYPE,String.class,Integer.TYPE).invoke(pp,Integer.valueOf(2),address,Integer.valueOf(1));
    }});
    ProjectionAaEntries.Ticket ticket=(ProjectionAaEntries.Ticket)dispatched.ticket();
    reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeLong(ticket==null?0:ticket.sequence);return true;
   }catch(Exception failed){throw new RemoteException("aa-dispatch-outcome-unknown");}finally{dispatching.remove();}
  }
  if(code==3){long[] receipt=entries.snapshot(address);reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);for(long part:receipt)reply.writeLong(part);return true;}
  ProjectionDiscovery.Snapshot value=model.snapshot(address);int display=value==null?0:value.buttonType;
  reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);
  // Preserve the UI owner's WL namespace: AA1/CP2, distinct from launch command types.
  reply.writeInt(display==ProjectionDiscovery.CP?2:display==ProjectionDiscovery.AA?1:0);reply.writeInt(display==ProjectionDiscovery.AA?aaState(address):-1);reply.writeInt(value!=null&&value.linkUp?1:0);return true;
 }
}
