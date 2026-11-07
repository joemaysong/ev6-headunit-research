import java.lang.reflect.Field;
import java.lang.reflect.Method;
/** Retires only a stopped pre-receiver request. Never infers completion from a timeout. */
public final class ProjectionAaLeaseCompletion {
 private final Object service,request;
 private final boolean neverEnteredOnly;
 private boolean running,complete;
 public ProjectionAaLeaseCompletion(Object service,Object request){this(service,request,false);}
 public ProjectionAaLeaseCompletion(Object service,Object request,boolean neverEnteredOnly){this.service=service;this.request=request;this.neverEnteredOnly=neverEnteredOnly;}
 private static Field member(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
 private static Object field(Object o,String n)throws Exception{return member(o.getClass(),n).get(o);}
 private static Object call(Object o,String n)throws Exception{return o.getClass().getMethod(n).invoke(o);}
 public synchronized boolean completed(){
  if(complete)return true;
  if(!running){running=true;new Thread(new Runnable(){public void run(){boolean done=false;try{done=release();}catch(Exception unavailable){}finally{synchronized(ProjectionAaLeaseCompletion.this){complete=done;running=false;}}}},"EV6AaLeaseRelease").start();}
  return false;
 }
 // Separate from the native stop worker: transient storage failure retries release,
 // never replays cancelWifiConnection against a replacement transport.
 boolean release()throws Exception{
  synchronized(service){
   if(neverEnteredOnly&&!ProjectionAaServiceProof.stopNeverEntered(service,request))return false;
   if(field(service,"mWifiTransport")!=null||field(service,"mGalReceiver")!=null||field(service,"mUsbTransport")!=null
     ||((Integer)call(service,"getGpmConnectMode")).intValue()!=0)return false;
   int state=((Integer)call(service,"getGpmStatus")).intValue();if(state!=1&&state!=2)return false;
   if(!((Boolean)call(request,"drained")).booleanValue())return false;
   Class<?> producer=Class.forName("org.ev6.research.aa.AaProjectionReservation");
   Class<?> handoff=Class.forName("org.ev6.research.aa.AaRequestHandoff");
   synchronized(handoff){
   @SuppressWarnings("unchecked") java.util.Map<Object,Object> bindings=(java.util.Map<Object,Object>)member(handoff,"SERVICES").get(null);
   Object bound=bindings.get(service);
   if(bound!=null&&bound!=request&&!ProjectionAaServiceProof.denied(service,request))return false;
   synchronized(producer){
    if(member(producer,"wirelessRequest").get(null)!=request||!member(producer,"wirelessCommitted").getBoolean(null))return false;
    Object preparation=member(producer,"wirelessPreparation").get(null);
    if(preparation==null||call(field(preparation,"preparation"),"request")!=request)return false;
    Object ticket=call(request,"ticket");if(ticket==null||member(producer,"wireless").get(null)!=ticket)return false;
    Object store=field(request,"store");
    // Resolve all producer fields before changing durable ownership.
    Field q=member(producer,"wirelessRequest"),t=member(producer,"wireless"),a=member(producer,"wirelessAddress"),p=member(producer,"wirelessPreparation"),c=member(producer,"wirelessCommitted");
    Method release=store.getClass().getMethod("releaseCompletedAa",ticket.getClass());
    if(!((Boolean)release.invoke(store,ticket)).booleanValue())return false;
    if(bound==request)bindings.remove(service);
    ProjectionAaServiceProof.forget(request);
    ProjectionAaReceiverStop.forget(request);
    q.set(null,null);t.set(null,null);a.set(null,null);p.set(null,null);c.setBoolean(null,false);
    return true;
   }}
  }
 }
}
