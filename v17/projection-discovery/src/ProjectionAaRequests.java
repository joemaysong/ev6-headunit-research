import java.util.HashMap;
/** IDs are issued before effects. Unknown/retired IDs can never create an operation. */
public final class ProjectionAaRequests {
 public static final class Request {
  public final String id,address;public final boolean disconnect;
  private final long created=System.nanoTime();private boolean started,ambiguous,cancelled,deferred,managed;private Object ticket;
  private Request(String i,String a,boolean d){id=i;address=a;disconnect=d;}
  public synchronized Object ticket(){return ambiguous?null:ticket;}
 }
 private final HashMap<String,Request> records=new HashMap<String,Request>();
 private final HashMap<String,Request> retiredManaged=new HashMap<String,Request>();
 /** Root-issued retry identity is idempotent, including after cancellation. Never grants START. */
 public synchronized Request prepareRetry(String id,String address){
  if(id==null||!id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")||address==null||!address.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}"))throw new IllegalArgumentException();
  Request old=records.get(id);if(old==null)old=retiredManaged.get(id);
  if(old!=null){if(old.disconnect||!old.managed||!old.address.equals(address))throw new IllegalStateException("retry-identity-collision");return old;}
  if(records.size()+retiredManaged.size()>=64)throw new IllegalStateException("request-capacity");
  Request r=new Request(id,address,false);r.deferred=true;r.managed=true;records.put(id,r);return r;
 }
 public synchronized Request prepare(String address,boolean disconnect){if(address==null||!address.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))throw new IllegalArgumentException();java.util.Iterator<Request> old=records.values().iterator();long now=System.nanoTime();while(old.hasNext()){Request prior=old.next();synchronized(prior){if(!prior.started&&!prior.deferred&&now-prior.created>300000000000L)old.remove();}}if(records.size()+retiredManaged.size()>=64)throw new IllegalStateException("request-capacity");Request r=new Request(java.util.UUID.randomUUID().toString(),address.toUpperCase(java.util.Locale.US),disconnect);records.put(r.id,r);return r;}
 public synchronized Request prepareRootStop(String id,String address){if(id==null||!id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")||address==null||!address.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))throw new IllegalArgumentException();Request prior=records.get(id);if(prior!=null){if(!prior.disconnect||!prior.address.equalsIgnoreCase(address))throw new IllegalStateException("root-stop-collision");return prior;}if(records.size()+retiredManaged.size()>=64)throw new IllegalStateException("request-capacity");Request r=new Request(id,address.toUpperCase(java.util.Locale.US),true);records.put(id,r);return r;}
 public synchronized Request find(String id,String address,boolean disconnect){Request r=records.get(id);return r!=null&&r.address.equalsIgnoreCase(address)&&r.disconnect==disconnect?r:null;}
 /** Only pre-effect deferred requests expire. Issued/ambiguous effects never become success. */
 public synchronized int expireDeferred(){return expireDeferred(System.nanoTime(),90000000000L);}
 synchronized int expireDeferred(long now,long limit){if(limit<0)throw new IllegalArgumentException();int count=0;for(Request r:records.values())synchronized(r){if(r.deferred&&!r.started&&!r.cancelled&&now-r.created>=limit){r.deferred=false;r.cancelled=true;r.started=true;count++;}}return count;}
 public synchronized boolean cancelledBeforeEffects(Request r){if(r==null||(records.get(r.id)!=r&&retiredManaged.get(r.id)!=r))return false;synchronized(r){return r.cancelled&&!r.ambiguous&&r.ticket==null;}}
 public synchronized Request[] deferredRequests(){expireDeferred();java.util.ArrayList<Request> out=new java.util.ArrayList<Request>();for(Request r:records.values())if(isDeferred(r))out.add(r);return out.toArray(new Request[out.size()]);}
 public synchronized boolean defer(Request r){if(r==null||records.get(r.id)!=r||r.disconnect)return false;synchronized(r){if(r.started||r.cancelled)return false;r.deferred=true;r.managed=true;return true;}}
 public synchronized boolean isDeferred(Request r){if(r==null||records.get(r.id)!=r)return false;synchronized(r){return r.deferred&&!r.started&&!r.cancelled;}}
 /** Root adapter must prove the exact request's exclusive lease before granting. Not a pause-clear action. */
 public synchronized boolean grantDeferred(Request r){if(!isDeferred(r))return false;synchronized(r){r.deferred=false;return true;}}
 public synchronized boolean cancelDeferred(Request r){if(r==null||records.get(r.id)!=r)return false;synchronized(r){if(!r.deferred||r.started)return false;r.deferred=false;r.cancelled=true;r.started=true;return true;}}
 public synchronized boolean start(Request r){if(r==null||records.get(r.id)!=r)return false;synchronized(r){if(r.started||r.deferred)return false;r.started=true;return true;}}
 public synchronized boolean cancelBeforeEffects(Request r){if(r==null||records.get(r.id)!=r)return false;synchronized(r){if(!r.started||r.ticket!=null||r.ambiguous)return false;r.cancelled=true;return true;}}
 public synchronized void capture(Request r,String address,boolean disconnect,Object ticket){if(r==null||records.get(r.id)!=r||ticket==null||r.disconnect!=disconnect||!r.address.equalsIgnoreCase(address))return;synchronized(r){if(!r.started)return;if(r.ticket!=null)r.ambiguous=true;else r.ticket=ticket;}}
 public synchronized boolean sealUnstarted(Request r){if(r==null||records.get(r.id)!=r)return false;synchronized(r){if(r.deferred)return false;if(!r.started){r.started=true;r.cancelled=true;}return r.cancelled;}}
 public synchronized Request findManaged(String id,String address){Request r=find(id,address,false);if(r==null)r=retiredManaged.get(id);return r!=null&&r.managed&&r.address.equalsIgnoreCase(address)?r:null;}
 /** UI retirement cannot destroy a root coordinator's exact terminal evidence. */
 public synchronized void retire(Request r){if(r!=null&&records.get(r.id)==r){records.remove(r.id);if(r.managed&&!r.disconnect)retiredManaged.put(r.id,r);}}
 public synchronized Request[] retiredManagedFor(String peer){java.util.ArrayList<Request> out=new java.util.ArrayList<Request>();for(Request r:retiredManaged.values())if(r.address.equalsIgnoreCase(peer))out.add(r);return out.toArray(new Request[out.size()]);}
 /** Root calls only after actual cleanup and hold removal. No OEM operation or request creation. */
 public synchronized void forgetManaged(Request r){if(r!=null&&r.managed){if(records.get(r.id)==r)records.remove(r.id);if(retiredManaged.get(r.id)==r)retiredManaged.remove(r.id);}}

}
