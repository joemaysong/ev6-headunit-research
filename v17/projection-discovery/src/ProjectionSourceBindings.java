import java.util.ArrayList;
import java.lang.ref.WeakReference;
/** Immutable provenance captured at transport creation, never looked up by current peer on reply. */
public final class ProjectionSourceBindings {
 public static final class Binding {
  private final WeakReference<Object> source;private final String address;private final long generation;
  private final int protocol;private final Object evidenceOwner;private final ProjectionDiscovery.Probe probe;private boolean closed,aaConfirmed;
  private Binding(Object s,String a,long g,int type,Object owner,ProjectionDiscovery.Probe p){protocol=type;evidenceOwner=owner;source=new WeakReference<Object>(s);address=a;generation=g;probe=p;}
 }
 private final ProjectionDiscovery model;private final ArrayList<Binding> seen=new ArrayList<Binding>();
 public ProjectionSourceBindings(ProjectionDiscovery d){if(d==null)throw new IllegalArgumentException();model=d;}
 private Binding lookup(Object source){if(source==null)return null;for(Binding b:seen)if(b.source.get()==source)return b;return null;}
 private void prune(){for(int i=seen.size()-1;i>=0;i--)if(seen.get(i).source.get()==null)seen.remove(i);}
 public synchronized Binding attach(String address,long generation,int protocol,Object source){
  prune();if(source==null || lookup(source)!=null||seen.size()>=64)return null;
  Object owner=new Object();ProjectionDiscovery.Probe probe=model.probe(address,generation,protocol,owner);if(probe==null)return null;
  Binding b=new Binding(source,address,generation,protocol,owner,probe);seen.add(b);return b;
 }
 public synchronized boolean seen(Object source){return lookup(source)!=null;}
 public synchronized Binding find(Object source){Binding b=lookup(source);return live(b)?b:null;}
 private boolean live(Binding b){return b!=null&&!b.closed&&b.source.get()!=null&&lookup(b.source.get())==b;}
 public synchronized void aaVersion(Binding b,String address,boolean success){if(live(b)){model.aaVersion(b.probe,b.evidenceOwner,address,success);if(b.protocol==ProjectionDiscovery.AA&&b.address.equalsIgnoreCase(address))b.aaConfirmed=success;}}
 public synchronized boolean aaStartEligible(Binding b,String address){ProjectionDiscovery.Snapshot state=address==null?null:model.snapshot(address);return live(b)&&b.protocol==ProjectionDiscovery.AA&&b.aaConfirmed&&b.address.equalsIgnoreCase(address)&&state!=null&&state.generation==b.generation&&state.readyType==ProjectionDiscovery.AA;}
 /** Resolve a still-live version exchange when the bond broadcast arrives later. */
 public synchronized Binding aaCandidate(String address){Binding found=null;for(Binding b:seen)if(aaStartEligible(b,address)){if(found!=null)return null;found=b;}return found;}
 public synchronized void cpIdentified(Binding b){if(live(b))model.cpIdentified(b.probe,b.evidenceOwner);}
 public synchronized void cpAuthenticated(Binding b){if(live(b))model.cpAuthenticated(b.probe,b.evidenceOwner);}
 public synchronized boolean cpEvent(Binding b,int event,Object msg){return live(b)&&new ProjectionIapEvidence(model).accept(b.probe,b.evidenceOwner,event,msg);}
 /** OEM Iap2HandleNoti notification IDs, not MsgConsumer command IDs. */
 public synchronized void cpNotification(Binding b,int event,Object message){
  if(!live(b)||b.protocol!=ProjectionDiscovery.CP)return;
  if(event==0)cpIdentified(b);
  else if(event==5)cpAuthenticated(b);
  else if(event==24||event==29)cpEvent(b,event,message);
  else if(event==1||event==2||event==3||event==4||event==7)close(b.source.get());
 }
 public synchronized void close(Object source){Binding b=lookup(source);if(!live(b))return;b.closed=true;model.cancel(b.probe,b.evidenceOwner);}
 public synchronized void retirePeer(String address,long generation){for(Binding b:seen)if(b.address.equalsIgnoreCase(address)&&b.generation==generation)close(b.source.get());}
 public synchronized void retireAll(){for(Binding b:seen)close(b.source.get());}
}
