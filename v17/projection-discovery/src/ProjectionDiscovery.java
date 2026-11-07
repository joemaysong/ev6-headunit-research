import java.util.HashMap;
import java.util.Locale;
/** Live evidence is volatile. Optional persisted type hints only affect display, never readiness. */
public final class ProjectionDiscovery {
 public static final int UNKNOWN=0,CP=1,AA=2;
 public interface HintStore {int read(String address)throws Exception;void write(String address,int type)throws Exception;void remove(String address)throws Exception;}
 public interface Clock {long now();}
 private final HintStore hints;private final Clock clock;
 public ProjectionDiscovery(){this(null);}
 public ProjectionDiscovery(HintStore store){this(store,new Clock(){public long now(){return System.nanoTime()/1000000L;}});}
 public ProjectionDiscovery(HintStore store,Clock time){if(time==null)throw new IllegalArgumentException("clock");hints=store;clock=time;}
 private int readHint(String address){try{int t=hints==null?0:hints.read(address);return t==CP||t==AA?t:0;}catch(Exception unavailable){return 0;}}
 private void remember(Peer p,int type){if(hints==null||p.hint==type)return;p.hint=type;try{if(hints!=null)hints.write(p.address,type);}catch(Exception unavailable){}}
 private void forget(Peer p){p.hint=0;try{if(hints!=null)hints.remove(p.address);}catch(Exception unavailable){}}

 private static final class Peer {
  final String address;long generation,round;boolean up;Probe cp,aa;int confirmed,retained,hint;boolean conflict;Session session;Intent intent;
  Peer(String a){address=a;}
 }
 public static final class Probe {
  private final Peer peer;private final long generation,round;private final Object transport;private final int type;
  private boolean live=true,identified,authenticated,transportSeen,supportSeen,supported,success;
  private Probe(Peer p,int t,Object socket){peer=p;generation=p.generation;round=++p.round;transport=socket;type=t;}
 }
 public static final class Session {
  private final Peer peer;private final long generation;private final int type;private boolean expectedDrop,started;
  private Session(Peer p){peer=p;generation=p.generation;type=p.confirmed;}
 }
 public static final class Intent {
  private final Peer peer;private final long requiredGeneration,created;private final int type;
  private Intent(Peer p,int t,long now){peer=p;type=t;created=now;requiredGeneration=p.up?p.generation:p.generation+1;}
 }
 /** One-shot user protocol selection, not proof of a session or an AP/native permit. */
 public static final class Launch {
  public final String address;public final int type;public final long observedGeneration;
  private Launch(Intent i){address=i.peer.address;type=i.type;observedGeneration=i.peer.generation;}
 }
 public static final class Snapshot {
  public final long generation;public final boolean linkUp,conflict,projecting;public final int displayType,readyType,buttonType;
  private Snapshot(Peer p){generation=p.generation;linkUp=p.up;conflict=p.conflict;projecting=p.session!=null;
   displayType=p.session!=null?p.session.type:p.conflict?UNKNOWN:p.confirmed!=0?p.confirmed:p.retained!=0?p.retained:p.hint;
   readyType=p.up&&!p.conflict?p.confirmed:UNKNOWN;buttonType=p.conflict?UNKNOWN:p.hint;}
 }
 private final HashMap<String,Peer> peers=new HashMap<String,Peer>();
 private static String address(String a){if(a==null||!a.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))throw new IllegalArgumentException("address");return a.toUpperCase(Locale.US);}
 private Peer peer(String a){return peers.get(address(a));}
 private void revokeProbes(Peer p){if(p.cp!=null)p.cp.live=false;if(p.aa!=null)p.aa.live=false;p.cp=p.aa=null;}
 /** Caller must confirm the device is still bonded. Hints alone never establish a link. */
 public synchronized void paired(String a){String key=address(a);if(peers.containsKey(key))return;
  if(peers.size()>=32)throw new IllegalStateException("peer-capacity");Peer p=new Peer(key);p.hint=readHint(key);peers.put(key,p);}
 public synchronized void reconcileBonded(java.util.Set<String> bonded,java.util.Set<String> stored){
  java.util.HashSet<String> keep=new java.util.HashSet<String>();for(String a:bonded)keep.add(address(a));
  java.util.HashSet<String> known=new java.util.HashSet<String>(peers.keySet());known.addAll(stored);
  for(String a:known)if(!keep.contains(address(a)))unpair(a);
 }
 /** Adapter calls this on observed ACL up; duplicate up does not open another generation. */
 public synchronized long connected(String a){
  paired(a);Peer p=peer(a);
  if(p.up)return p.generation;
  revokeProbes(p);p.up=true;p.generation++;p.confirmed=p.retained=0;p.conflict=false;
  if(p.intent!=null && p.intent.requiredGeneration!=p.generation)p.intent=null;
  return p.generation;
 }
 public synchronized void disconnected(String a,long generation){
  Peer p=peer(a);if(p==null||p.generation!=generation||!p.up)return;
  p.up=false;revokeProbes(p);p.confirmed=0;p.conflict=false;p.retained=0;
  // Actual session is separate from the ACL; only its scoped expected handoff keeps a reconnect entry.
  if(p.session!=null && p.session.generation==generation && p.session.expectedDrop)p.retained=p.session.type;
 }
 public synchronized Probe probe(String a,long generation,int type,Object transport){
  Peer p=peer(a);if(p==null||!p.up||p.generation!=generation||transport==null||(type!=CP&&type!=AA))return null;
  Probe previous=type==CP?p.cp:p.aa;if(previous!=null)previous.live=false;
  p.retained=0;Probe t=new Probe(p,type,transport);if(type==CP)p.cp=t;else p.aa=t;
  // A new probe supersedes this protocol's old result, never silently adopts it.
  recompute(p);return t;
 }
 private boolean valid(Probe t,Object transport,int type){return t!=null && peers.get(t.peer.address)==t.peer && t.live && t.transport==transport && t.type==type
  && t.peer.up && t.peer.generation==t.generation && (type==CP?t.peer.cp:t.peer.aa)==t;}
 private void recompute(Peer p){boolean cp=p.cp!=null&&p.cp.live&&p.cp.success,aa=p.aa!=null&&p.aa.live&&p.aa.success;
  p.conflict=cp&&aa;p.confirmed=p.conflict?UNKNOWN:cp?CP:aa?AA:UNKNOWN;if(p.conflict){p.intent=null;forget(p);}else if(p.confirmed!=0){if(p.intent!=null&&p.intent.type!=p.confirmed)p.intent=null;}}
 private void cpReady(Probe t){t.success=t.identified&&t.authenticated&&t.transportSeen&&t.supportSeen&&t.supported;
  if(t.identified&&t.authenticated&&t.transportSeen&&t.supportSeen&&!t.supported&&t.peer.hint==CP)forget(t.peer);recompute(t.peer);}
 public synchronized void cpIdentified(Probe t,Object transport){if(!valid(t,transport,CP))return;t.identified=true;cpReady(t);}
 public synchronized void cpAuthenticated(Probe t,Object transport){if(!valid(t,transport,CP))return;t.authenticated=true;cpReady(t);}
 public synchronized void cpTransport(Probe t,Object transport,String reported,String serial){
  if(!valid(t,transport,CP))return;
  if(reported==null||!t.peer.address.equalsIgnoreCase(reported)||serial==null||serial.length()==0||serial.length()>256){cancel(t,transport);return;}
  t.transportSeen=true;cpReady(t);
 }
 public synchronized void cpWirelessSupport(Probe t,Object transport,int status){if(!valid(t,transport,CP))return;if(status!=0&&status!=1){cancel(t,transport);return;}t.supportSeen=true;t.supported=status==1;cpReady(t);}
 public synchronized void aaVersion(Probe t,Object transport,String reported,boolean success){
  if(!valid(t,transport,AA))return;
  if(reported==null||!t.peer.address.equalsIgnoreCase(reported)){cancel(t,transport);return;}
  t.success=success;recompute(t.peer);
 }
 public synchronized void cancel(Probe t,Object transport){if(t==null||!valid(t,transport,t.type))return;t.live=false;recompute(t.peer);}
 /** Called only after the real, separately authorized projection start is associated with this evidence. */
 public synchronized Session projection(String a,long generation){Peer p=peer(a);if(p==null||!p.up||p.generation!=generation||p.confirmed==0||p.conflict||p.session!=null)return null;
  p.session=new Session(p);return p.session;}
 private boolean session(Session s){return s!=null&&peers.get(s.peer.address)==s.peer&&s.peer.session==s;}
 public synchronized void started(Session s){if(session(s)){s.started=true;remember(s.peer,s.type);if(hints==null)s.peer.hint=s.type;}}
 public synchronized void expectedDrop(Session s){if(session(s))s.expectedDrop=true;}
 /** Observe actual OEM finalization; issuing a stop request is not this event. */
 public synchronized void stopped(Session s){if(!session(s))return;Peer p=s.peer;
  if(p.generation==s.generation && (p.up||s.expectedDrop))p.retained=s.type;
  p.session=null;
 }
 public synchronized Intent reconnect(String a){Peer p=peer(a);if(p==null||p.session!=null)return null;if(p.intent!=null&&!intentLive(p.intent))p.intent=null;if(p.intent!=null)return null;int type=new Snapshot(p).displayType;
  if(type==0)return null;p.intent=new Intent(p,type,clock.now());return p.intent;}
 private boolean intentLive(Intent i){long now=clock.now();return now>=i.created&&now-i.created<=60000;}
 public synchronized boolean reconnectReady(Intent i){if(i==null||peers.get(i.peer.address)!=i.peer||i.peer.intent!=i||!intentLive(i))return false;Peer p=i.peer;
  return p.session==null&&p.up&&p.generation==i.requiredGeneration&&!p.conflict&&p.confirmed==i.type;}
 /** A remembered button can route a normal CP/AA launch immediately.
  * The existing controller must still issue/check its own start permit and native handshake.
  */
 public synchronized Launch consumeLaunch(Intent i){
  if(i==null||peers.get(i.peer.address)!=i.peer||i.peer.intent!=i||!intentLive(i))return null;
  Peer p=i.peer;if(p.session!=null||p.conflict||new Snapshot(p).displayType!=i.type)return null;
  p.intent=null;return new Launch(i);
 }
 public synchronized void cancelReconnect(Intent i){if(i!=null&&peers.get(i.peer.address)==i.peer&&i.peer.intent==i)i.peer.intent=null;}
 /** Caller has verified a real completed protocol handshake. Stores a UI route only. */
 public synchronized boolean verifiedSessionHint(String a,int type){if(type!=CP&&type!=AA)throw new IllegalArgumentException("type");paired(a);Peer p=peer(a);if(p.session!=null&&p.session.type!=type)return false;
  if(p.conflict||p.confirmed!=type){revokeProbes(p);p.confirmed=p.retained=0;p.conflict=false;}if(p.intent!=null&&p.intent.type!=type)p.intent=null;remember(p,type);if(hints==null)p.hint=type;return true;
 }
 public synchronized Snapshot snapshot(String a){Peer p=peer(a);return p==null?null:new Snapshot(p);}
 public synchronized void unpair(String a){String key=address(a);Peer p=peers.remove(key);if(p!=null){revokeProbes(p);p.session=null;p.intent=null;forget(p);}else try{if(hints!=null)hints.remove(key);}catch(Exception unavailable){}}
 public synchronized void adapterOff(){for(Peer p:peers.values()){revokeProbes(p);p.session=null;p.intent=null;}peers.clear();}
}
