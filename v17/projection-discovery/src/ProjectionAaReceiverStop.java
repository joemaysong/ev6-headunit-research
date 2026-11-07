import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

/** Correlates OEM message-2 teardown with the exact wireless receiver lifetime.
 * Dispatch is not completion. Only the normal stop/destroy/transport-return path
 * can finish the lifetime; timeout, null fields and dispatch failure cannot. */
public final class ProjectionAaReceiverStop implements ProjectionAaStopWorker.Target {
 private final Object service,request,transport,origin,receiver,binding;
 private final boolean cancellation;
 private static final Map<Object,Object> DESTROYED=new IdentityHashMap<Object,Object>();
 private static final Map<Object,Object> COMPLETE=new IdentityHashMap<Object,Object>();
 private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 private static Object stat(String type,String name)throws Exception{Field f=Class.forName(type).getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 private static Object call(Object o,String name)throws Exception{return o.getClass().getMethod(name).invoke(o);}
 private static Object invoke(Class<?> c,Object target,String name,Object arg)throws Exception{
  for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==1&&m.getParameterTypes()[0].isInstance(arg)){m.setAccessible(true);return m.invoke(target,arg);}
  throw new NoSuchMethodException(name);
 }
 private static Object binding(Object origin)throws Exception{
  String type="org.ev6.research.aa.AaDisconnectGuard";Object lock=stat(type,"MAP");
  synchronized(lock){return ((Map<?,?>)stat(type,"BINDINGS")).get(origin);}
 }
 private static boolean current(Object request)throws Exception{return ((Boolean)invoke(Class.forName("org.ev6.research.aa.AaProjectionReservation"),null,"sameWirelessRequest",request)).booleanValue();}
 public static ProjectionAaReceiverStop capture(Object s,Object q)throws Exception{return capture(s,q,false);}
 /** Only the exact entry cancellation callback uses this factory. It may stop a
  * receiver that reached Running or Disconnecting since the request was admitted. */
 public static ProjectionAaReceiverStop captureForCancellation(Object s,Object q)throws Exception{return capture(s,q,true);}
 private static ProjectionAaReceiverStop capture(Object s,Object q,boolean cancellation)throws Exception{
  if(s==null||q==null)return null;
  synchronized(s){Object t=field(s,"mWifiTransport"),r=field(s,"mGalReceiver");if(t==null||r==null||field(s,"mUsbTransport")!=null)return null;
   Object o=field(t,"ev6Origin"),b=binding(o);
   if(call(o,"service")!=s||call(o,"transport")!=t||call(o,"request")!=q||b==null||field(b,"receiver")!=r||!Boolean.TRUE.equals(field(b,"wifi"))||field(b,"lifetime")==null)return null;
   return new ProjectionAaReceiverStop(s,q,t,o,r,b,cancellation);
  }
 }
 private ProjectionAaReceiverStop(Object s,Object q,Object t,Object o,Object r,Object b,boolean cancel){service=s;request=q;transport=t;origin=o;receiver=r;binding=b;cancellation=cancel;}
 public boolean stopIfCurrent()throws Exception{
  // The public OEM callback only dispatches message 2. The handler owns native
  // shutdown; this worker never waits for it while holding service's monitor.
  synchronized(service){
   if(field(service,"mWifiTransport")!=transport||field(service,"mGalReceiver")!=receiver||field(service,"mUsbTransport")!=null||binding(origin)!=binding||!current(request))return false;
   int mode=((Integer)call(service,"getGpmConnectMode")).intValue(),status=((Integer)call(service,"getGpmStatus")).intValue();
   if(mode!=2||!((status>=3&&status<=5)||(cancellation&&(status==1||status==6))))return false;
   // Cancellation authority belongs to this immutable request, never a successor.
   // Merely observing status1/null fields still cannot prove completion.
   call(request,"seal");
   Class<?> origins=Class.forName("org.ev6.research.aa.AaTransportOrigins");Object scope=invoke(origins,null,"callback",origin);
   try{service.getClass().getMethod("onDisconnected",Integer.TYPE,Boolean.TYPE).invoke(service,Integer.valueOf(0),Boolean.FALSE);}
   finally{invoke(origins,null,"end",scope);}
   return true;
  }
 }
 /** Inserted only after OEM destroy() returns on the admitted stop handler. */
 public static void destroyed(Object receiver){try{
  if(receiver!=null&&Boolean.TRUE.equals(call(receiver,"ev6NativeStopAccepted"))){synchronized(DESTROYED){DESTROYED.put(receiver,Boolean.TRUE);}}
 }catch(Exception unavailable){}}
 /** Inserted only after normal dispatch return, never on its exception path. */
 public static void returned(Object scope){try{
  if(scope==null||!Boolean.TRUE.equals(field(scope,"allowed"))||Boolean.TRUE.equals(field(scope,"ended")))return;
  Object receipt=field(scope,"receipt");if(receipt==null||!Boolean.TRUE.equals(field(receipt,"wifi"))||((Integer)field(receipt,"arg1")).intValue()!=0)return;
  Object o=field(receipt,"origin"),q=call(o,"request"),s=call(o,"service"),r=field(receipt,"receiver"),b=binding(o);
  if(q==null||b==null||field(b,"receiver")!=r||field(b,"origin")!=o||!current(q))return;
  synchronized(DESTROYED){if(!DESTROYED.containsKey(r))return;}
  synchronized(s){
   if(field(s,"mGalReceiver")!=null||field(s,"mWifiTransport")!=null||field(s,"mUsbTransport")!=null||((Integer)call(s,"getGpmConnectMode")).intValue()!=0)return;
   if(!Boolean.TRUE.equals(call(r,"ev6NativeStopAccepted")))return;
   call(q,"seal");Object lifetime=field(b,"lifetime");
   if(lifetime==null||!Boolean.TRUE.equals(invoke(q.getClass(),q,"finish",lifetime)))return;
   synchronized(COMPLETE){COMPLETE.put(q,s);}
   synchronized(DESTROYED){DESTROYED.remove(r);}
  }
 }catch(Exception unavailable){}}
 public static boolean completed(Object service,Object request){synchronized(COMPLETE){return COMPLETE.get(request)==service;}}
 public static void forget(Object request){synchronized(COMPLETE){COMPLETE.remove(request);}}
}
