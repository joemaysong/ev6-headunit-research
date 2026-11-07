import java.io.IOException;
/** Durable orchestration. Effects are preceded by intent records and never blindly replayed. */
public final class ProjectionExclusiveCoordinator {
 /** Raised only before any START grant or effect has been issued. */
 public static final class StartNotIssued extends Exception {public StartNotIssued(Exception cause){super("exclusive-start-preflight-refused",cause);}}
 public interface Journal {String read()throws Exception;void write(String state)throws Exception;}
 public interface Port {
  void hold(String request,String target,int type)throws Exception;
  boolean holdMatches(String request,String target,int type)throws Exception;
  boolean startGrantMatches(String request)throws Exception;
  boolean oldProjectionDrained(String request)throws Exception;
  void stopOldProjection(String request)throws Exception;
  boolean otherBluetoothDrained(String request,String target)throws Exception;
  void disconnectOthers(String request,String target)throws Exception;
  boolean transportReady(String request,String target,int type)throws Exception;
  void start(String request,String target,int type)throws Exception;
  boolean connected(String request,String target,int type)throws Exception;
  boolean allEffectsDrained(String request)throws Exception;
  void release(String request)throws Exception;
  boolean released(String request)throws Exception;
  boolean priorBootDrained(String request,String priorBoot)throws Exception;
 }
 public enum Phase { IDLE,PROBE_HOLD,PROBE_WAIT,HOLD_INTENT,HOLD_CONFIRMED,STOP_INTENT,WAIT_OLD,BT_INTENT,WAIT_BT,START_INTENT,WAIT_START,CONNECTED,DRAIN,RELEASE_INTENT,WAIT_RELEASE,DONE }
 private final Journal journal;private final Port port;private final String boot;
 private String requestBoot,request="",target="",predecessor="-";private int type;private Phase phase=Phase.IDLE;private boolean recovering,faulted;
 public ProjectionExclusiveCoordinator(String boot,Journal journal,Port port)throws Exception{
  if(boot==null||!boot.matches("[0-9a-f]{32}")||journal==null||port==null)throw new IllegalArgumentException();this.boot=boot;requestBoot=boot;this.journal=journal;this.port=port;
  String record=journal.read();if(record!=null&&record.length()>0){String[] p=record.split("\\|",-1);if(p.length!=8||!p[0].equals("EV6EXCLUSIVE1")||!p[1].matches("[0-9a-f]{32}")||!p[7].equals("END\n"))throw new IOException("exclusive-journal-identity");requestBoot=p[1];request=p[2];target=p[3];type=Integer.parseInt(p[4]);phase=Phase.valueOf(p[5]);predecessor=p[6];validate();recovering=phase!=Phase.DONE&&phase!=Phase.IDLE;}
 }
 private void validate()throws Exception{if(!request.matches("[0-9a-f]{32}")||!target.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")||(type!=1&&type!=2)||(!predecessor.equals("-")&&!predecessor.matches("[0-9a-f]{32}")))throw new IOException("exclusive-request-schema");}
 private void save(Phase next)throws Exception{try{journal.write("EV6EXCLUSIVE1|"+requestBoot+"|"+request+"|"+target+"|"+type+"|"+next.name()+"|"+predecessor+"|END\n");phase=next;}catch(Exception failed){faulted=true;throw failed;}}
 public synchronized boolean request(String id,String peer,int protocol,boolean explicit)throws Exception{
  if(faulted)throw new IOException("exclusive-journal-uncertain");
  if(recovering||!explicit||!(phase==Phase.IDLE||phase==Phase.DONE||phase==Phase.CONNECTED))return false;
  if(id==null||!id.matches("[0-9a-f]{32}")||peer==null||!peer.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")||(protocol!=1&&protocol!=2))throw new IllegalArgumentException();
  if(id.equals(request))return false;predecessor=phase==Phase.CONNECTED?request:"-";requestBoot=boot;request=id;target=peer.toUpperCase(java.util.Locale.US);type=protocol;save(Phase.HOLD_INTENT);port.hold(request,target,type);return true;
 }
 /** Automatic admission cannot switch or supersede an owner, even if caller checks go stale. */
 public synchronized boolean requestAutomatic(String id,String peer,int protocol)throws Exception{
  if(faulted)throw new IOException("exclusive-journal-uncertain");
  if(recovering||(phase!=Phase.IDLE&&phase!=Phase.DONE))return false;
  return request(id,peer,protocol,true);
 }
 /** Reserve only the launch slot. No projection stop, Bluetooth disconnect,
  * AP grant or native start is performed by this phase. */
 public synchronized boolean requestProbe(String id,String peer)throws Exception{
  if(faulted)throw new IOException("exclusive-journal-uncertain");
  if(recovering||(phase!=Phase.IDLE&&phase!=Phase.DONE))return false;
  if(id==null||!id.matches("[0-9a-f]{32}")||peer==null||!peer.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}"))throw new IllegalArgumentException();
  if(id.equals(request))return false;
  requestBoot=boot;request=id;target=peer;type=1;predecessor="-";
  save(Phase.PROBE_HOLD);port.hold(request,target,type);return true;
 }
 /** Caller must first validate positive evidence and revoke the probe authority.
  * Promotion waits for actual old native/transport drain, never just STOP queued. */
 public synchronized boolean promoteProbe(String id,String peer)throws Exception{
  if(faulted)throw new IOException("exclusive-journal-uncertain");
  if(recovering||phase!=Phase.PROBE_WAIT||!request.equals(id)||!target.equals(peer))return false;
  if(!port.holdMatches(request,target,type)||port.startGrantMatches(request)||!port.oldProjectionDrained(request))return false;
  save(Phase.HOLD_CONFIRMED);return true;
 }
 public synchronized void tick()throws Exception{
  if(faulted)throw new IOException("exclusive-journal-uncertain");
  // Interrupted work goes through recovery/drain; a new process never replays old START/STOP.
  if(recovering){if(!requestBoot.equals(boot)&&!port.priorBootDrained(request,requestBoot))return;if(requestBoot.equals(boot)&&(phase==Phase.START_INTENT||phase==Phase.WAIT_START||phase==Phase.CONNECTED)&&port.connected(request,target,type)&&port.holdMatches(request,target,type)&&port.startGrantMatches(request)){save(Phase.CONNECTED);recovering=false;return;}save(Phase.DRAIN);recovering=false;}
  switch(phase){
   case PROBE_HOLD:if(port.holdMatches(request,target,type))save(Phase.PROBE_WAIT);break;
   case PROBE_WAIT:if(!port.holdMatches(request,target,type))save(Phase.DRAIN);break;
   case HOLD_INTENT:if(port.holdMatches(request,target,type))save(Phase.HOLD_CONFIRMED);break;
   case HOLD_CONFIRMED:
    if(!port.holdMatches(request,target,type))throw new IOException("exclusive-hold-lost");
    if(port.oldProjectionDrained(request)){save(Phase.BT_INTENT);port.disconnectOthers(request,target);save(Phase.WAIT_BT);}
    else {save(Phase.STOP_INTENT);port.stopOldProjection(request);save(Phase.WAIT_OLD);}break;
   case STOP_INTENT:case WAIT_OLD:if(port.oldProjectionDrained(request)){save(Phase.BT_INTENT);port.disconnectOthers(request,target);save(Phase.WAIT_BT);}break;
   case BT_INTENT:case WAIT_BT:
    if(!port.holdMatches(request,target,type))throw new IOException("exclusive-hold-lost");
    if(port.otherBluetoothDrained(request,target)&&port.oldProjectionDrained(request)){if(!port.transportReady(request,target,type))break;save(Phase.START_INTENT);try{port.start(request,target,type);}catch(StartNotIssued refused){save(Phase.DRAIN);return;}save(Phase.WAIT_START);}else {save(Phase.BT_INTENT);port.disconnectOthers(request,target);save(Phase.WAIT_BT);}break;
   case START_INTENT:case WAIT_START:if(port.connected(request,target,type))save(Phase.CONNECTED);break;
   case CONNECTED:if(port.allEffectsDrained(request))save(Phase.DRAIN);break;
   case DRAIN:if(port.allEffectsDrained(request)){save(Phase.RELEASE_INTENT);port.release(request);save(Phase.WAIT_RELEASE);}break;
   case RELEASE_INTENT:case WAIT_RELEASE:if(port.released(request))save(Phase.DONE);break;
   default:break;
  }
 }
 /** Cancellation is a drain request, never permission to forget an issued effect. */
 public synchronized boolean cancel(String id)throws Exception{if(faulted)throw new IOException("exclusive-journal-uncertain");if(!request.equals(id)||phase==Phase.IDLE||phase==Phase.DONE)return false;save(Phase.DRAIN);return true;}
 public synchronized boolean recoveryPending(){return recovering;}
 public synchronized String requestBoot(){return requestBoot;}
 public synchronized String predecessor(){return predecessor;}
 public synchronized String requestId(){return request;}
 public synchronized String target(){return target;}
 public synchronized int type(){return type;}
 public synchronized Phase phase(){return phase;}
}
