import java.io.*;
import java.util.*;
/** Single controller worker is the only writer. No alternate Bluetooth reconnection policy. */
public final class ProjectionExclusiveRuntime implements ProjectionExclusiveCoordinator.Port {
 private final CarPlayController c;private final ProjectionExclusiveFiles files;private final ProjectionExclusiveCoordinator coordinator;
 private final String boot;
 private final ProjectionBluetoothExclusiveClient bluetooth=new ProjectionBluetoothExclusiveClient();private final ProjectionAaDeferredClient aa=new ProjectionAaDeferredClient();
 private ProjectionAaDeferredClient.Request aaRequest;private ProjectionAaDeferredClient.Stop aaStop;private String held="";
 // CP selection keeps the durable aa-request journal as its predecessor proof.
 // Cache per coordinator request; no repeated filesystem work on the UI path.
 private ProjectionAaDeferredClient.Request predecessorAa;
 private String predecessorAaFor="";
 private ProjectionAaDeferredClient.Request predecessorAa()throws Exception{
  if(coordinator.type()!=1)return null;
  String current=coordinator.requestId();
  if(current.equals(predecessorAaFor))return predecessorAa;
  ProjectionAaDeferredClient.Request previous=null;
  String stored=files.readAaRequest(),prior=coordinator.predecessor();
  if(stored.length()>0){
   int separator=stored.indexOf('|');if(separator!=32)throw new IOException("aa-request-journal-schema");
   if(prior.equals(stored.substring(0,separator))){
    previous=ProjectionAaDeferredClient.restoreRequest(stored.substring(separator+1));
    if(!prior.equals(previous.id.replace("-","")))throw new IOException("aa-predecessor-identity");
   }
  }
  predecessorAa=previous;predecessorAaFor=current;return previous;
 }
 private ProjectionProbeFiles probeFiles;private ProjectionCpProbeClient probeClient;
 private String probeRequest="",probePeer="",queuedCp="";private long probeUntil,probeBond;
 private boolean probeIssued,probeGranted,probePositive,probeStopping;
 private boolean starting,maintaining,maintenanceDrained,cpManual,cpFirstPair;
 private String cpRecoveryRequest=""; // In-memory only; restart never restores retry authority.
 private final ProjectionReconnectPolicy reconnect=new ProjectionReconnectPolicy();
 private int diagnosticSequence;
 private String progressRequest="";private long progressUntil;
 private boolean deadlineWasConnected;private boolean deadlineDraining;
 private String rememberedAa="";private String timedRequest="";private long requestStarted;private boolean deadlineReported;
 private String diagnosticPhase="",lastDiagnostic="";
 private final String traceRun=UUID.randomUUID().toString().replace("-","");private String traceBoot;private boolean traceOriginKnown;
 private String traceRequest="",traceOrigin="recovered",traceLast="";private long traceStarted,tracePrevious,traceCommand;private int traceSequence;
 /** Bounded request-scoped observations; never a grant, and logging cannot abort an effect. */
 public void trace(String stage){trace(stage,false);}
 public void trace(String stage,boolean failure){
  try{
   String id=coordinator.requestId();if(id.length()==0)return;
   long now=System.nanoTime()/1000000L;
   if(!id.equals(traceRequest)){traceRequest=id;traceStarted=now;tracePrevious=now;traceSequence=0;traceLast="";traceCommand=coordinator.type()==1&&cpManual&&!cpFirstPair?c.lastCommand:0;traceOrigin=coordinator.type()==1?(traceOriginKnown?(cpFirstPair?"first-pair":cpManual?"manual":"automatic"):"recovered"):(aaRequest==null?"recovered":aaRequest.explicit?"manual":"automatic");c.state("connection-trace-first-failure","");}
   if(stage.equals(traceLast)&&!failure)return;
   String prefix="EV6CONNECT1|"+traceBoot+"|"+traceRun+"|"+coordinator.requestBoot()+"|"+id+"|";
   String value=prefix+(coordinator.type()==1?"CP":"AA")+"|"+traceOrigin+"|"+traceCommand+"|"+(now-traceStarted)+"|"+(now-tracePrevious)+"|"+stage.replace('|','/').replace('\n',' ').replace('\r',' ')+"|END";
   c.state("connection-trace-current",value);c.state("connection-trace-event-"+(traceSequence++%32),value);
   if(failure&&!CarPlayController.read(new File(CarPlayController.STATE,"connection-trace-first-failure"),2048).startsWith(prefix))c.state("connection-trace-first-failure",value);
   tracePrevious=now;traceLast=stage;
  }catch(Exception ignored){}
 }
 private void selectedManually(){traceOrigin="manual";traceCommand=c.lastCommand;trace("explicit-selection");}
 /** Diagnostics are observations only: never grant authority or interrupt cleanup. */
 private void diagnostic(String stage,String code,boolean failure){
  try{String identity="EV6HANDOFFDIAG1|"+coordinator.requestBoot()+"|"+coordinator.requestId()+"|";
   String value=identity+coordinator.phase().name()+"|"+stage+"|"+code+"|END";
   if(failure){String first=CarPlayController.read(new File(CarPlayController.STATE,"handoff-first-failure"),2048);if(!first.startsWith(identity))c.state("handoff-first-failure",value);}
   if(!value.equals(lastDiagnostic)){c.state("handoff-diagnostic",value);c.state("handoff-event-"+(diagnosticSequence++%16),value);lastDiagnostic=value;}
  }catch(Exception unavailable){/* A diagnostic write is never a transition or an effect acknowledgement. */}
 }
 public static boolean configured()throws Exception{return ProjectionBluetoothExclusiveClient.configured();}

 private static String kernelBoot()throws Exception{return CarPlayController.read(new File("/proc/sys/kernel/random/boot_id"),128).trim().replace("-","");}
 public ProjectionExclusiveRuntime(CarPlayController controller)throws Exception{this(controller,kernelBoot());}
 private ProjectionExclusiveRuntime(CarPlayController controller,String boot)throws Exception{this(controller,boot,new ProjectionExclusiveFiles(new File("/data/local/ev6-projection/handoff"),boot));}
 /** Same runtime and durable file adapter; host integration tests supply only external endpoints. */
 ProjectionExclusiveRuntime(CarPlayController controller,String boot,ProjectionExclusiveFiles storage)throws Exception{
  this.boot=boot;c=controller;traceBoot=boot;files=storage;reconnect.restore(files.readReconnect());coordinator=new ProjectionExclusiveCoordinator(boot,files,this);String storedRequest=files.readAaRequest();if(storedRequest.length()>0){int separator=storedRequest.indexOf('|');if(separator!=32)throw new IOException("aa-request-journal-schema");if(storedRequest.substring(0,separator).equals(coordinator.requestId()))aaRequest=ProjectionAaDeferredClient.restoreRequest(storedRequest.substring(separator+1));}
  String stored=files.readAaStop();if(stored.length()>0){int separator=stored.indexOf('|');if(separator!=32)throw new IOException("aa-stop-journal-schema");if(stored.substring(0,separator).equals(coordinator.requestId()))aaStop=ProjectionAaDeferredClient.restoreStop(stored.substring(separator+1));}
 }
 private void syncProgress(){
  try{ProjectionExclusiveCoordinator.Phase p=coordinator.phase();String id=coordinator.requestId();
   if(!id.equals(progressRequest)){progressRequest=id;progressUntil=c.progressClock()+120000L;deadlineReported=false;}
   boolean terminal=p==ProjectionExclusiveCoordinator.Phase.IDLE||p==ProjectionExclusiveCoordinator.Phase.DONE;
   boolean cleanup=coordinator.recoveryPending()||deadlineReported||(coordinator.type()==1&&c.projectionFailurePending()&&(p==ProjectionExclusiveCoordinator.Phase.WAIT_START||p==ProjectionExclusiveCoordinator.Phase.START_INTENT))||p==ProjectionExclusiveCoordinator.Phase.DRAIN||p==ProjectionExclusiveCoordinator.Phase.RELEASE_INTENT||p==ProjectionExclusiveCoordinator.Phase.WAIT_RELEASE;
   String mode=terminal?"I":cleanup?"C":p==ProjectionExclusiveCoordinator.Phase.CONNECTED?"A":"W";
   c.projectionProgress(coordinator.target(),coordinator.type(),id,mode,progressUntil/1000L);
  }catch(Exception ignored){/* UI feedback cannot change connection ownership. */}
 }
 /** Scheduling hint only: no IPC, authority change, or deadline extension. */
 public boolean startupWakePending(){
  if(maintaining||coordinator.recoveryPending()||c.progressClock()>=progressUntil)return false;
  ProjectionExclusiveCoordinator.Phase p=coordinator.phase();
  return (deadlineDraining&&!deadlineReported&&(p==ProjectionExclusiveCoordinator.Phase.DRAIN||p==ProjectionExclusiveCoordinator.Phase.RELEASE_INTENT||p==ProjectionExclusiveCoordinator.Phase.WAIT_RELEASE))
    ||p==ProjectionExclusiveCoordinator.Phase.HOLD_INTENT||p==ProjectionExclusiveCoordinator.Phase.HOLD_CONFIRMED
   ||p==ProjectionExclusiveCoordinator.Phase.STOP_INTENT||p==ProjectionExclusiveCoordinator.Phase.WAIT_OLD
   ||p==ProjectionExclusiveCoordinator.Phase.BT_INTENT||p==ProjectionExclusiveCoordinator.Phase.WAIT_BT
   ||p==ProjectionExclusiveCoordinator.Phase.START_INTENT||p==ProjectionExclusiveCoordinator.Phase.WAIT_START;
 }
 public String demandRequestId(){return coordinator.requestId();}
 public long cpRecoveryRemaining(String target){
  if(maintaining||c.policy.paused||!owns(target,1)||!coordinator.requestId().equals(cpRecoveryRequest))return 0;
  ProjectionExclusiveCoordinator.Phase p=coordinator.phase();
  if(p!=ProjectionExclusiveCoordinator.Phase.START_INTENT&&p!=ProjectionExclusiveCoordinator.Phase.WAIT_START)return 0;
  return reconnect.claimedRemaining(target,1,System.nanoTime()/1000000L);
 }
 public boolean starting(){return starting;}
 public boolean owns(String target,int type){return coordinator.phase()!=ProjectionExclusiveCoordinator.Phase.IDLE&&coordinator.phase()!=ProjectionExclusiveCoordinator.Phase.DONE&&coordinator.target().equalsIgnoreCase(target)&&coordinator.type()==type;}
 /** A fresh bond may initiate setup only with live CP evidence and no existing work. */
 public boolean firstPairIdle()throws Exception{return !starting&&!maintaining&&!probing()&&idle()&&!coordinator.recoveryPending()&&!c.exclusivePreparationActive()&&!c.policy.connected&&!c.nativeSession.active()&&active("getCPConnectedBTAddress").length()==0&&active("getAAConnectedBTAddress").length()==0;}
 public boolean firstCpRequest(String target,ProjectionCpBootstrapClient.Ticket proof)throws Exception{
  if(!firstPairIdle()||aa.pending().length!=0||!c.bonded(target)||proof==null||!target.equals(proof.address)||!ProjectionCpBootstrapClient.matches(proof))return false;
  // Automatic coordinator admission cannot replace an existing projection. The first-setup
  // permission clears an older pause only when the normal start grant is actually reached.
  String next=UUID.randomUUID().toString().replace("-","");
  if(!coordinator.requestAutomatic(next,target,1))return false;
  reconnect.cancel();files.writeReconnect(reconnect.checkpoint());
  traceOriginKnown=true;cpManual=true;cpFirstPair=true;aaRequest=null;aaStop=null;
  trace("first-pair-positive-cp-admitted");syncProgress();return true;
 }
 private boolean storedProbe(){for(String name:new String[]{"probe-start","probe-start.next","probe-grant","probe-grant.next"})if(new File(files.directory(),name).exists())return true;return false;}
 public boolean probing(){ProjectionExclusiveCoordinator.Phase p=coordinator.phase();return storedProbe()||probeRequest.length()>0||p==ProjectionExclusiveCoordinator.Phase.PROBE_HOLD||p==ProjectionExclusiveCoordinator.Phase.PROBE_WAIT;}
 private ProjectionProbeFiles probeFiles()throws Exception{if(probeFiles==null)probeFiles=new ProjectionProbeFiles(files.directory(),traceBoot);return probeFiles;}
 public boolean firstCpProbe(String target,long until)throws Exception{
  long now=android.os.SystemClock.elapsedRealtime();
  if(!firstPairIdle()||!c.originalCleanupReady()||aa.pending().length!=0||!c.bonded(target)||until<=now||until-now>120000L||!c.firstPairs.current(target,until,now))return false;
  String id=UUID.randomUUID().toString().replace("-","");if(!coordinator.requestProbe(id,target))return false;
  probeRequest=id;probePeer=target;probeUntil=until;probeBond=until-ProjectionFirstPairPolicy.WINDOW;
  if(probeBond<=0)probeBond=1;
  probeClient=new ProjectionCpProbeClient(traceBoot,id,target);probeIssued=probeGranted=probePositive=probeStopping=false;
  reconnect.cancel();files.writeReconnect(reconnect.checkpoint());traceOriginKnown=true;cpFirstPair=true;cpManual=false;trace("first-pair-probe-reserved");return true;
 }
 private void stopProbe(){probeStopping=true;probePositive=false;}
 /** Runs before normal handoff: a probe never advances through generic START. */
 private void tickProbe(boolean explicitAa)throws Exception{
  ProjectionExclusiveCoordinator.Phase phase=coordinator.phase();
  boolean storedProbe=new File(files.directory(),"probe-start").exists()||new File(files.directory(),"probe-start.next").exists()||new File(files.directory(),"probe-grant").exists()||new File(files.directory(),"probe-grant.next").exists();
  if(probeRequest.length()==0&&(storedProbe||phase==ProjectionExclusiveCoordinator.Phase.PROBE_HOLD||phase==ProjectionExclusiveCoordinator.Phase.PROBE_WAIT)){
   if(!probeFiles().recoverPublication())return;
   probeRequest=coordinator.requestId();probePeer=coordinator.target();probeStopping=true;probeIssued=true;
   probeClient=new ProjectionCpProbeClient(coordinator.requestBoot(),probeRequest,probePeer);
  }
  if(probeRequest.length()==0)return;
  long now=android.os.SystemClock.elapsedRealtime();
  if(maintaining||explicitAa||queuedCp.length()>0||!c.bonded(probePeer)||!c.firstPairs.current(probePeer,probeUntil,now)||phase==ProjectionExclusiveCoordinator.Phase.DRAIN)stopProbe();
  if(!probeStopping&&phase==ProjectionExclusiveCoordinator.Phase.PROBE_WAIT){
   if(!probeIssued){
    if(!probeFiles().publishStart(probeRequest,probePeer,probeBond,probeUntil,now))return;
    probeIssued=true; // An ambiguous IPC response must not replay SPP START.
    try{if(!probeClient.start())stopProbe();}catch(Exception uncertain){stopProbe();throw uncertain;}
   }
   if(!probeStopping){
    ProjectionCpProbeClient.Snapshot transport=probeClient.read();
    if(transport.state==0||transport.state==4||transport.state==5)stopProbe();
    else if(transport.state==2&&transport.source!=null){
     if(!probeGranted){
      if(!probeFiles().publish(probeRequest,probePeer,probeBond,probeUntil,transport.source,transport.generation,now))return;
      probeGranted=true;
      c.exec(null,10000,"/system/bin/am","startservice","-n","org.ev6.wirelesscarplay/org.ev6.research.ProbeService","-a","org.ev6.research.AP_READY");
     }
     ProjectionCpBootstrapClient.Ticket proof=ProjectionCpBootstrapClient.read();
     if(proof!=null&&probePeer.equals(proof.address)&&ProjectionCpBootstrapClient.matches(proof)){
      ProjectionCpProbeClient.Snapshot again=probeClient.read();
      if(again.state==2&&transport.source.equals(again.source)&&transport.generation==again.generation){probePositive=true;probeStopping=true;trace("first-pair-probe-positive");}
     }
    }
   }
  }
  if(!probeStopping)return;
  if(!probeFiles().revoke(probeRequest,coordinator.requestBoot()))return;
  if(!c.originalCleanupReady())return;
  if(probeIssued&&!probeClient.stop())return;
  if(!probeFiles().retireStart(probeRequest,coordinator.requestBoot()))return;
  boolean promote=probePositive&&!maintaining&&!explicitAa&&queuedCp.length()==0&&c.bonded(probePeer)&&c.firstPairs.current(probePeer,probeUntil,now);
  if(promote){if(!coordinator.promoteProbe(probeRequest,probePeer))return;cpManual=true;cpFirstPair=true;c.firstPairs.consumed(probePeer);trace("first-pair-probe-drained-admitted");}
  else coordinator.cancel(probeRequest);
  c.firstPairs.consumed(probePeer); // One bounded discovery attempt per fresh pairing.
  probeRequest="";probeClient=null;probeIssued=probeGranted=probePositive=probeStopping=false;
 }
 public boolean cpRequest(String target,boolean manual)throws Exception{
  if(manual){reconnect.cancel();files.writeReconnect(reconnect.checkpoint());}
  else if(reconnect.blocksAutomatic(target,1,System.nanoTime()/1000000L))return true;
  if(manual&&probing()){queuedCp=target;stopProbe();return true;}
  if(starting)return false;
  if(maintaining){if(manual)c.commandStage("rejected","projection-maintenance-in-progress");return false;}
  if(owns(target,1)){ProjectionExclusiveCoordinator.Phase phase=coordinator.phase();if(phase==ProjectionExclusiveCoordinator.Phase.DRAIN||phase==ProjectionExclusiveCoordinator.Phase.RELEASE_INTENT||phase==ProjectionExclusiveCoordinator.Phase.WAIT_RELEASE){if(manual)c.commandStage("rejected","projection-cleanup-in-progress");return false;}
   if(manual){traceOriginKnown=true;cpManual=true;cpFirstPair=false;c.promoteManualDemand(target);selectedManually();}
   return true;}
  if(!manual&&(!idle()||c.policy.connected||active("getCPConnectedBTAddress").length()>0||c.nativeSession.active()||active("getAAConnectedBTAddress").length()>0||c.policy.paused||c.knownPeers.getProperty(target)==null))return true;
  if(!accepting()){if(!manual)return true;c.commandStage("rejected","projection-handoff-busy");return false;}
  String next=UUID.randomUUID().toString().replace("-","");boolean accepted=manual?coordinator.request(next,target,1,true):coordinator.requestAutomatic(next,target,1);if(accepted){traceOriginKnown=true;cpManual=manual;cpFirstPair=false;aaRequest=null;aaStop=null;trace("request-accepted");syncProgress();}return accepted;
 }
 public void userStop(String target)throws Exception{queuedCp="";if(probing())stopProbe();trace("user-stop-command-"+c.lastCommand);reconnect.cancel();files.writeReconnect(reconnect.checkpoint());if(owns(target,1)){
   boolean connected=coordinator.phase()==ProjectionExclusiveCoordinator.Phase.CONNECTED;
   cancelCurrent();
   if(connected&&!maintaining)files.armUsbHandover(coordinator.requestId());
  }}
 private void cancelCurrent()throws Exception{files.revokeStart(coordinator.requestId(),coordinator.predecessor(),coordinator.requestBoot());coordinator.cancel(coordinator.requestId());syncProgress();}
 /** Maintenance may cancel preparation, but never converts an internal stop to USER pause. */
 public void maintenance(boolean requested)throws Exception{
  if(requested)files.clearUsbHandover();
  maintaining=requested;if(requested){reconnect.cancel();files.writeReconnect(reconnect.checkpoint());}if(!requested){maintenanceDrained=false;return;}
  if(idle())return;files.revokeStart(coordinator.requestId(),coordinator.predecessor(),coordinator.requestBoot());
  if(aaRequest!=null)aa.requestStatus(aaRequest,19);
  if(aaStop!=null)aa.stopped(aaStop); // Register exact old-incarnation waiter before maintenance capture.
  ProjectionExclusiveCoordinator.Phase phase=coordinator.phase();if(phase!=ProjectionExclusiveCoordinator.Phase.DRAIN&&phase!=ProjectionExclusiveCoordinator.Phase.RELEASE_INTENT&&phase!=ProjectionExclusiveCoordinator.Phase.WAIT_RELEASE)coordinator.cancel(coordinator.requestId());
 }
 /** Called only after the original maintenance transaction has verified radio/network cleanup. */
 public boolean maintenanceCompleted(boolean originalDrained)throws Exception{if(!maintaining)throw new IOException("exclusive-maintenance-not-requested");maintenanceDrained=originalDrained;tick();return idle()&&files.absent();}
 private boolean accepting(){return !coordinator.recoveryPending()&&(idle()||coordinator.phase()==ProjectionExclusiveCoordinator.Phase.CONNECTED);}
 private boolean idle(){return coordinator.phase()==ProjectionExclusiveCoordinator.Phase.IDLE||coordinator.phase()==ProjectionExclusiveCoordinator.Phase.DONE;}
 private String active(String name)throws Exception{Object value=c.pp(name,new Class<?>[0]);return value==null?"":value.toString();}
 public void tick()throws Exception{tick(System.nanoTime()/1000000L);}
 /** Monotonic request deadline; uncertain effects retain ownership until real drain. */
 void tick(long now)throws Exception{syncProgress();try{tickInner(now);}finally{syncProgress();}}
 private void tickInner(long now)throws Exception{
  files.expireUsbHandover();
  String id=coordinator.requestId();if(!id.equals(timedRequest)){timedRequest=id;requestStarted=now;deadlineReported=false;deadlineWasConnected=false;deadlineDraining=false;}
  ProjectionExclusiveCoordinator.Phase currentPhase=coordinator.phase();
  if(currentPhase==ProjectionExclusiveCoordinator.Phase.CONNECTED)deadlineWasConnected=true;
  boolean draining=currentPhase==ProjectionExclusiveCoordinator.Phase.DRAIN||currentPhase==ProjectionExclusiveCoordinator.Phase.RELEASE_INTENT||currentPhase==ProjectionExclusiveCoordinator.Phase.WAIT_RELEASE;
  if(draining&&!deadlineDraining){deadlineDraining=true;if(deadlineWasConnected){requestStarted=now;deadlineReported=false;progressUntil=c.progressClock()+120000L;}}

  if(!deadlineReported&&id.length()>0&&currentPhase!=ProjectionExclusiveCoordinator.Phase.IDLE&&currentPhase!=ProjectionExclusiveCoordinator.Phase.DONE&&currentPhase!=ProjectionExclusiveCoordinator.Phase.CONNECTED&&now-requestStarted>=120000L&&!connected(id,coordinator.target(),coordinator.type())){
   deadlineReported=true;diagnostic("request-deadline","handoff-progress-timeout",true);
   if(currentPhase==ProjectionExclusiveCoordinator.Phase.HOLD_INTENT||currentPhase==ProjectionExclusiveCoordinator.Phase.HOLD_CONFIRMED||currentPhase==ProjectionExclusiveCoordinator.Phase.STOP_INTENT||currentPhase==ProjectionExclusiveCoordinator.Phase.WAIT_OLD||currentPhase==ProjectionExclusiveCoordinator.Phase.BT_INTENT||currentPhase==ProjectionExclusiveCoordinator.Phase.WAIT_BT){cancelCurrent();c.commandStage("rejected","projection-handoff-preparation-timeout");}
   else {cancelCurrent();c.commandStage("rejected","projection-handoff-cleanup-required");}
  }
  if(!configured()){c.state("handoff-result","waiting-bluetooth-exclusive-owner");return;}
  ProjectionAaDeferredClient.Request[] waiting=maintaining?new ProjectionAaDeferredClient.Request[0]:aa.pending();
  boolean explicitWaiting=false;for(ProjectionAaDeferredClient.Request request:waiting)if(request.explicit)explicitWaiting=true;
  boolean samePeerAa=false;for(ProjectionAaDeferredClient.Request request:waiting)if(probing()&&request.address.equals(probePeer))samePeerAa=true;
  tickProbe(explicitWaiting||samePeerAa);
  if(probing()){coordinator.tick();return;}
  if(queuedCp.length()>0&&idle()&&!maintaining){String target=queuedCp;queuedCp="";cpRequest(target,true);}
  boolean manuallyEnded=(coordinator.type()==1&&c.policy.paused)||(coordinator.type()==2&&!idle()&&aa.manuallyEnded(coordinator.target()))||(idle()&&reconnect.type()==2&&reconnect.peer().length()>0&&aa.manuallyEnded(reconnect.peer()));
  if(explicitWaiting||manuallyEnded)reconnect.cancel();
  // A manual AA stop is intent to drain, not evidence that native teardown finished.
  // Enter DRAIN now so the exact request's formal cancellation can produce that evidence.
  // An old peer pause must not cancel a new explicit request before grant clears it.
  if(manuallyEnded&&coordinator.type()==2&&aaRequest!=null
    &&coordinator.phase()==ProjectionExclusiveCoordinator.Phase.CONNECTED){
   cancelCurrent();
   if(!maintaining)files.armUsbHandover(coordinator.requestId());
  }
  if(!maintaining&&!explicitWaiting&&!manuallyEnded&&coordinator.phase()==ProjectionExclusiveCoordinator.Phase.CONNECTED){
   if(connected(coordinator.requestId(),coordinator.target(),coordinator.type()))reconnect.connected(coordinator.requestId(),coordinator.target(),coordinator.type(),now);
   else {
    SessionEvidence ended=c.runtimeEvidence();
    if(coordinator.type()==1&&ended!=null&&ended.inactive())files.revokeStart(coordinator.requestId(),coordinator.predecessor(),coordinator.requestBoot());
    if(oldProjectionDrained(coordinator.requestId()))reconnect.ended(coordinator.requestId(),now);
   }
  }
  if(idle())reconnect.failed(now); // Previous failed attempt drained; never replay its START.
  files.writeReconnect(reconnect.checkpoint()); // Persist denial before any new START effect.
  // Recreate the invitation only after the old native session and AP fully drained.
  // A fresh request goes through the same exclusive coordinator as the first connection.
  if(!maintaining&&!explicitWaiting&&!manuallyEnded&&waiting.length==0&&idle()
    &&reconnect.type()==2&&reconnect.due(now)&&aaRequest!=null
    &&allEffectsDrained(coordinator.requestId())&&files.absent()){
   String retryId=UUID.nameUUIDFromBytes((boot+"|AA-RETRY|"+coordinator.requestId()+"|"+reconnect.peer()+"|"+reconnect.attempts()).getBytes("US-ASCII")).toString();
   if(aa.prepareRetry(aaRequest,retryId,reconnect.peer()))waiting=aa.pending();
  }
  if(!maintaining&&accepting()){
   if(waiting.length==1){
    ProjectionAaDeferredClient.Request selected=waiting[0];
    boolean retry=!selected.explicit&&reconnect.matches(selected.address,2);
    if(!selected.explicit&&(!idle()||!oldProjectionDrained(coordinator.requestId())||(!retry&&reconnect.blocksAutomatic(selected.address,2,now)))){aa.requestStatus(selected,19);c.state("handoff-result","remote-request-rejected-active-owner");}
    else if(!retry||(reconnect.due(now)&&allEffectsDrained(coordinator.requestId())&&files.absent())){
     files.writeAaRequest(selected.id.replace("-",""),ProjectionAaDeferredClient.encodeRequest(selected));
     if(retry&&!reconnect.claim(selected.address,2,now))throw new IOException("aa-recovery-claim-lost");
     if(selected.explicit?coordinator.request(selected.id.replace("-",""),selected.address,2,true):coordinator.requestAutomatic(selected.id.replace("-",""),selected.address,2)){aaRequest=selected;aaStop=null;}
    }else if(!reconnect.window(now))aa.requestStatus(selected,19);
   }else if(waiting.length>1)c.state("handoff-result","multiple-explicit-requests-await-selection");
   if(waiting.length==0&&idle()&&reconnect.type()==1&&reconnect.due(now)&&!c.policy.paused&&allEffectsDrained(coordinator.requestId())&&files.absent()){
    String target=reconnect.peer();if(reconnect.claim(target,1,now)&&coordinator.requestAutomatic(UUID.randomUUID().toString().replace("-",""),target,1)){traceOriginKnown=true;cpManual=false;cpFirstPair=false;aaRequest=null;aaStop=null;cpRecoveryRequest=coordinator.requestId();c.state("reconnect-result","CP attempt="+reconnect.attempts());}
   }
  }
  if(coordinator.phase()==ProjectionExclusiveCoordinator.Phase.WAIT_START&&coordinator.type()==1&&!c.exclusivePreparationActive()&&oldProjectionDrained(coordinator.requestId()))cancelCurrent();
  if(coordinator.type()==2&&aaRequest!=null&&coordinator.phase()!=ProjectionExclusiveCoordinator.Phase.DRAIN&&coordinator.phase()!=ProjectionExclusiveCoordinator.Phase.RELEASE_INTENT&&coordinator.phase()!=ProjectionExclusiveCoordinator.Phase.WAIT_RELEASE&&coordinator.phase()!=ProjectionExclusiveCoordinator.Phase.DONE&&aa.ended(aaRequest))cancelCurrent();
  if(coordinator.type()==2&&aaRequest!=null&&coordinator.phase()==ProjectionExclusiveCoordinator.Phase.DRAIN)aa.requestStatus(aaRequest,19);
  if(coordinator.phase()==ProjectionExclusiveCoordinator.Phase.DRAIN&&(coordinator.type()==1||(aaRequest!=null&&aa.ended(aaRequest))))files.revokeStart(coordinator.requestId(),coordinator.predecessor(),coordinator.requestBoot());
  // Native stop may finish several polls after CONNECTED has already entered
  // DRAIN. Capture eligibility then; START still waits for complete AP cleanup.
  if(!maintaining&&!explicitWaiting&&!manuallyEnded&&coordinator.type()==2&&aaRequest!=null
    &&coordinator.phase()==ProjectionExclusiveCoordinator.Phase.DRAIN&&aa.ended(aaRequest)
    &&oldProjectionDrained(coordinator.requestId())){
   reconnect.ended(coordinator.requestId(),now);files.writeReconnect(reconnect.checkpoint());
  }
  try{
   boolean checkReleaseNow = !maintaining && !coordinator.recoveryPending()
       && coordinator.phase()==ProjectionExclusiveCoordinator.Phase.DRAIN;
   coordinator.tick();
   // Only the release receipt is eligible for this extra check. An unavailable
   // receipt leaves WAIT_RELEASE intact for the next scheduled tick.
   if(checkReleaseNow && coordinator.phase()==ProjectionExclusiveCoordinator.Phase.WAIT_RELEASE)
    coordinator.tick();
  }catch(Exception failed){diagnostic("tick",failureCode(failed),true);throw failed;}
  if(coordinator.type()==2&&coordinator.phase()==ProjectionExclusiveCoordinator.Phase.CONNECTED&&aaRequest!=null&&!rememberedAa.equals(aaRequest.id)){
   try{if(connected(coordinator.requestId(),coordinator.target(),2)&&aa.rememberConnected(aaRequest)){rememberedAa=aaRequest.id;diagnostic("aa-session-memory","confirmed",false);}}catch(Exception unavailable){diagnostic("aa-session-memory","awaiting-success-receipt",false);}
  }
  // Capture the first verified CONNECTED transition in this tick, not one poll later.
  // A short real session may disconnect before the next poll.
  if(!maintaining&&!explicitWaiting&&!manuallyEnded&&coordinator.phase()==ProjectionExclusiveCoordinator.Phase.CONNECTED
    &&connected(coordinator.requestId(),coordinator.target(),coordinator.type()))
   reconnect.connected(coordinator.requestId(),coordinator.target(),coordinator.type(),now);
  files.writeReconnect(reconnect.checkpoint());
  String observed=coordinator.phase().name();if(!observed.equals(diagnosticPhase)){diagnostic("phase","observed",false);diagnosticPhase=observed;trace("handoff-"+observed.toLowerCase(java.util.Locale.US));}c.state("handoff-result",observed);
 }
 public void hold(String request,String target,int type)throws Exception{held=files.hold(request,target,type,coordinator.predecessor());}
 public boolean holdMatches(String request,String target,int type)throws Exception{try{held=files.heldRecord(request);String[] p=held.split("\\|",-1);return p[4].equals(type==1?"CP":"AA")&&p[5].equals(target);}catch(IOException missing){return false;}}
 public boolean startGrantMatches(String request)throws Exception{try{files.grantedRecord(request);return true;}catch(IOException unavailable){return false;}}
 private boolean noAaLease()throws Exception{return noAaLease(null);}
 private boolean noAaLease(String prepared)throws Exception{
  File reservation=new File("/data/local/ev6-projection/reservation"),pending=new File("/data/local/ev6-projection/reservation.next");
  if(!reservation.getCanonicalFile().equals(reservation.getAbsoluteFile())||!pending.getCanonicalFile().equals(pending.getAbsoluteFile())||pending.exists())return false;
  // Before the first AP demand the controller has not created a shared reservation yet.
  // The caller separately verifies actual CP/AA/native work is idle; absence grants no lease.
  if(!reservation.exists())return true;
  String value=CarPlayController.read(reservation,400);
  String[] p=value.trim().split("\\|",-1);if(p.length!=8||!p[0].equals("EV6RES1")||!p[7].equals("END"))return false;
  return ProjectionApPreparationProof.matches(value,prepared)||p[3].equals("F")||(p[3].equals("H")&&p[4].equals("AP"));
 }
 private static String failureCode(Exception failure){String message=failure.getMessage();return failure.getClass().getSimpleName()+(message!=null&&message.matches("[a-z][a-z0-9-]{0,100}")?":"+message:"");}
 private boolean waiting(String code){diagnostic("old-projection-drain",code,false);return false;}
 public boolean oldProjectionDrained(String request)throws Exception{return oldProjectionDrained(request,null);}
 private boolean oldProjectionDrained(String request,String prepared)throws Exception{
  if(active("getCPConnectedBTAddress").length()>0)return waiting("cp-address-active");
  if(active("getAAConnectedBTAddress").length()>0)return waiting("aa-address-active");
  if(c.nativeSession.active())return waiting("native-session-active");
  if(c.backendSessionActive())return waiting("backend-session-active");
  if(c.policy.connected)return waiting("policy-connected");
  if(c.policy.disconnecting)return waiting("policy-disconnecting");
  if(c.attemptOwner.owned())return waiting("attempt-owned");
  if(c.projectionReleasePending)return waiting("release-pending");
  if(!c.originalCleanupReady())return waiting("original-cleanup-pending");
  if(aaStop!=null&&!aa.stopped(aaStop))return waiting("aa-stop-pending");
  if(!aa.idle())return waiting("aa-idle-unproven");
  ProjectionAaDeferredClient.Request previous=predecessorAa();
  if(previous!=null&&!aa.ended(previous))return waiting("aa-predecessor-completion-pending");
  if(!noAaLease(prepared))return waiting("aa-lease-unreleased");return true;
 }
 public void stopOldProjection(String request)throws Exception{
  files.revokeStart(request,coordinator.predecessor(),coordinator.requestBoot());
  String cp=active("getCPConnectedBTAddress"),android=active("getAAConnectedBTAddress");if(cp.length()>0&&android.length()>0)throw new IOException("projection-dual-owner");
  if(cp.length()>0){
   SessionEvidence evidence=c.runtimeEvidence();if(evidence==null||!evidence.canSubmitStop(cp,true))throw new IOException("handoff-old-cp-unverified");
   if(!c.attemptOwner.owned()){c.attemptOwner.adoptForStop(evidence.observation,cp,true,true,evidence.association);c.policy.target=cp;c.oemConnectSubmitted=true;}
   c.pendingTarget="";c.disconnect(false);
  }else if(android.length()>0){aaStop=aa.prepareStop(request,android);files.writeAaStop(request,ProjectionAaDeferredClient.encodeStop(aaStop));aa.stop(aaStop);}
  else if(!oldProjectionDrained(request))throw new IOException("handoff-unidentified-old-work");
 }
 private Set<String> otherPeers(String target)throws Exception{
  Object manager=c.bt();if(manager==null)throw new IOException("handoff-bluetooth-unavailable");Set<String> out=new HashSet<String>();
  for(String method:new String[]{"btcmGetConnectedDevice1","btcmGetConnectedDevice2","btcmGetHfpConnectedDevice","btcmGetA2dpConnectedDevice1","btcmGetA2dpConnectedDevice2"}){
   Object value=CarPlayController.call(manager,"com.broadcom.bt.hfdevice.hfbtcm.IHfDeviceBTCMService",method,new Class<?>[0]);
   if(value!=null){if(!(value instanceof android.bluetooth.BluetoothDevice))throw new IOException("handoff-peer-schema");String peer=((android.bluetooth.BluetoothDevice)value).getAddress();if(!peer.equalsIgnoreCase(target))out.add(peer);}
  }return out;
 }
 public void disconnectOthers(String request,String target)throws Exception{
  if(!oldProjectionDrained(request))throw new IOException("handoff-disconnect-before-finalization");held=files.heldRecord(request);
  bluetooth.cancelOtherWork(held,target);
  for(String peer:otherPeers(target))bluetooth.disconnectOther(held,target,peer);
 }
 public boolean otherBluetoothDrained(String request,String target)throws Exception{
  try{bluetooth.requireExclusive(files.heldRecord(request),target);
   return true;}catch(IllegalStateException pending){try{diagnostic("bluetooth-drain",bluetooth.diagnostic(files.heldRecord(request),target),false);}catch(Exception unknown){diagnostic("bluetooth-drain","unknown-"+unknown.getClass().getSimpleName(),false);}return false;}
 }
 public boolean transportReady(String request,String target,int type)throws Exception{
   if(type==2){
    if(aaRequest==null||!aaRequest.id.replace("-","").equals(request)||!aaRequest.address.equals(target))throw new IOException("aa-ready-request-mismatch");
    if(!aa.transportReady(aaRequest)){trace("waiting-aa-version-exchange");return false;}
   }
  return true;
 }
 public void start(String request,String target,int type)throws Exception{
  try{if(!oldProjectionDrained(request))throw new IOException("handoff-start-old-work");held=files.heldRecord(request);bluetooth.requireExclusive(held,target);}
  catch(Exception refused){diagnostic("start-preflight",failureCode(refused),true);throw new ProjectionExclusiveCoordinator.StartNotIssued(refused);}
  // Grant persistence or later Binder errors remain uncertain effects, never classify as unissued.
  files.grant(request,coordinator.predecessor());
  if(type==1){starting=true;try{if(cpFirstPair)c.beginFirstPair(target,request);else if(cpManual)c.handleSelectionAdmitted("connect",target);else c.begin(target,false);}finally{starting=false;}}
  else{
   if(aaRequest==null||!aaRequest.id.replace("-","").equals(request)||!aaRequest.address.equals(target))throw new IOException("handoff-aa-request-lost");
   // AP is bootstrapped by the existing AA demand path after the normal OEM root request.
   aa.resume(aaRequest,held);
  }
 }
 /** Recheck the installed owner at the actual delayed CP effect, not just admission. */
 public void verifyCpStart(String target)throws Exception{
  ProjectionExclusiveCoordinator.Phase phase=coordinator.phase();
  if(!owns(target,1)||(phase!=ProjectionExclusiveCoordinator.Phase.START_INTENT&&phase!=ProjectionExclusiveCoordinator.Phase.WAIT_START))throw new IOException("handoff-cp-start-without-current-grant");
  if(!oldProjectionDrained(coordinator.requestId()))throw new IOException("handoff-cp-old-work-reappeared");
  bluetooth.requireExclusive(files.grantedRecord(coordinator.requestId()),target);
 }
 /** One selected-device gate shared by CP and AA AP startup. No grant is created here. */
 public void verifyApStart(int type)throws Exception{verifyApStart(type,null);}
 void verifyApStart(int type,String prepared)throws Exception{
  ProjectionExclusiveCoordinator.Phase phase=coordinator.phase();
  if(maintaining||coordinator.recoveryPending()||coordinator.type()!=type||
    (phase!=ProjectionExclusiveCoordinator.Phase.START_INTENT&&phase!=ProjectionExclusiveCoordinator.Phase.WAIT_START))throw new IOException("handoff-ap-without-current-request");
  if(type==2&&(aaRequest==null||!aaRequest.id.replace("-","").equals(coordinator.requestId())||aa.ended(aaRequest)))throw new IOException("handoff-aa-request-withdrawn");
  // AA has already entered its own deferred native preparation. Requiring AA idle
  // here would wait on the very request whose AP is being prepared.
  if(type==1){if(!oldProjectionDrained(coordinator.requestId(),prepared))throw new IOException("handoff-ap-old-work-present");}
  else if(active("getCPConnectedBTAddress").length()>0||active("getAAConnectedBTAddress").length()>0||c.nativeSession.active()||c.backendSessionActive()||c.policy.connected||c.policy.disconnecting||c.attemptOwner.owned()||c.projectionReleasePending||!c.originalCleanupReady()||!noAaLease(prepared))throw new IOException("handoff-ap-old-work-present");
  bluetooth.requireExclusive(files.grantedRecord(coordinator.requestId()),coordinator.target());
 }
 public void verifyAaApStart(String peerKey)throws Exception{verifyAaApStart(peerKey,null);}
 void verifyAaApStart(String peerKey,String prepared)throws Exception{
  verifyApStart(2,prepared);
  StringBuilder key=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(coordinator.target().getBytes("UTF-8")))key.append(String.format(java.util.Locale.US,"%02x",b&255));
  if(!key.toString().equals(peerKey))throw new IOException("handoff-aa-ap-wrong-peer");
 }
 public boolean connected(String request,String target,int type)throws Exception{
  if(type==1){SessionEvidence evidence=c.runtimeEvidence();return evidence!=null&&evidence.activeFor(target);}
  return target.equalsIgnoreCase(active("getAAConnectedBTAddress"))&&aa.connected(target);
 }
 public boolean cpCleanupAuthorized()throws Exception{return (coordinator.type()==1||(coordinator.type()==2&&aaRequest!=null&&aa.ended(aaRequest)))&&coordinator.phase()==ProjectionExclusiveCoordinator.Phase.DRAIN&&files.startAbsent()&&!c.exclusivePreparationActive()&&oldProjectionDrained(coordinator.requestId());}
 /** Network-only cleanup under the exact AA drain hold. Never cancels Bluetooth
  * work, releases admission, or treats a lost link as native stop completion. */
 public boolean aaNetworkDrainAuthorized()throws Exception{
  if(maintaining||coordinator.recoveryPending()||coordinator.type()!=2||aaRequest==null
    ||coordinator.phase()!=ProjectionExclusiveCoordinator.Phase.DRAIN
    ||!coordinator.requestBoot().equals(boot)||!coordinator.requestId().equals(aaRequest.id.replace("-",""))
    ||!coordinator.target().equals(aaRequest.address))return false;
  if(!holdMatches(coordinator.requestId(),coordinator.target(),2)||!files.startAbsent()
    ||c.exclusivePreparationActive()||!aa.ended(aaRequest)||!oldProjectionDrained(coordinator.requestId()))return false;
  return holdMatches(coordinator.requestId(),coordinator.target(),2)&&files.startAbsent();
 }
 public boolean allEffectsDrained(String request)throws Exception{
  if(probeRequest.length()>0||storedProbe())return false;
  if(!idle()&&coordinator.type()==2&&aaRequest!=null&&!aa.ended(aaRequest))return false;
  if(new File(CarPlayController.STATE,"ap-startup-abort.properties").exists()||new File(CarPlayController.STATE,"ap-startup-abort.properties.next").exists())return false;
  if(!oldProjectionDrained(request))return false;
  if(c.exclusivePreparationActive())return false;
  int client=c.wifiState("getWifiEnabledState");
  return !c.ap&&c.wifiState("getWifiApEnabledState")==11&&(client==1||client==3)&&((!new File(CarPlayController.STATE,"ap-owner").exists()&&!new File(CarPlayController.STATE,"saved-client-state").exists())||(maintaining&&maintenanceDrained))&&CarPlayController.exec(null,5000,"/system/bin/ndc","tether","status").trim().equals("210 0 Tethering services stopped");
 }
 public void release(String request)throws Exception{
  if(!allEffectsDrained(request))throw new IOException("handoff-release-before-cleanup");if(!files.absent()){files.releaseOwned(request,coordinator.predecessor(),coordinator.requestBoot());}held="";
 }
 public boolean priorBootDrained(String request,String priorBoot)throws Exception{return !CarPlayController.read(new File("/proc/sys/kernel/random/boot_id"),128).trim().replace("-","").equals(priorBoot)&&allEffectsDrained(request);}
 public boolean released(String request)throws Exception{if(!files.absent())return false;if(!bluetooth.retireLedger(coordinator.requestBoot(),request,coordinator.predecessor()))return false;if(aaStop!=null&&!aa.retireStop(aaStop))return false;if(aaRequest!=null&&aa.requestStatus(aaRequest,23)!=4)return false;return true;}
}
