import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
/** Exact pre-receiver OEM cancellation. Success denotes OEM lifecycle completion,
 * not an independent proof of every native thread's lifetime. */
public final class ProjectionAaPendingStop implements ProjectionAaStopWorker.Target {
 private final Object service,request,transport,origin,receipt,work;
 private boolean fenced,completed;
 private static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 private static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 private static Object call(Object o,String n)throws Exception{return o.getClass().getMethod(n).invoke(o);}
 private static boolean flag(Object o,String n)throws Exception{return ((Boolean)field(o,n)).booleanValue();}
 public static ProjectionAaPendingStop capture(Object service,Object expected)throws Exception{
  if(service==null||expected==null)return null;
  synchronized(service){
   Object transport=field(service,"mWifiTransport");if(transport==null)return null;
   Object origin=field(transport,"ev6Origin");
   if(call(origin,"service")!=service||call(origin,"transport")!=transport||call(origin,"request")!=expected)return null;
   Class<?> c=Class.forName("org.ev6.research.aa.AaConnectedReceipts");
   Field lock=c.getDeclaredField("MAP"),map=c.getDeclaredField("ORIGINS");lock.setAccessible(true);map.setAccessible(true);
   Object receipt;synchronized(lock.get(null)){receipt=((Map<?,?>)map.get(null)).get(origin);}
   if(receipt==null)return null;
   return new ProjectionAaPendingStop(service,expected,transport,origin,receipt,field(receipt,"work"));
  }
 }
 private ProjectionAaPendingStop(Object s,Object q,Object t,Object o,Object r,Object w){service=s;request=q;transport=t;origin=o;receipt=r;work=w;}
 public boolean stopIfCurrent()throws Exception{
  synchronized(service){
   if(field(service,"mWifiTransport")!=transport||field(service,"mGalReceiver")!=null||field(service,"mUsbTransport")!=null
     ||call(origin,"request")!=request||call(origin,"service")!=service||call(origin,"transport")!=transport)return false;
   int mode=((Integer)call(service,"getGpmConnectMode")).intValue(),status=((Integer)call(service,"getGpmStatus")).intValue();
   if(mode!=2||status<3||status>5)return false;
   synchronized(receipt){
    if(work==null||!flag(receipt,"wifi")||!flag(receipt,"ready")||flag(receipt,"terminal")||flag(receipt,"dispatching")
      ||field(receipt,"receiver")!=null||field(receipt,"sending")!=null||field(receipt,"origin")!=origin)return false;
    // Existing begin/post paths reject terminal receipts, including messages
    // already queued or whose enqueue acknowledgement was lost.
    set(receipt,"terminal",Boolean.TRUE);fenced=true;
   }
   call(request,"seal");
   service.getClass().getMethod("cancelWifiConnection",Integer.TYPE).invoke(service,Integer.valueOf(1));
   if(field(service,"mWifiTransport")!=null||field(service,"mGalReceiver")!=null||field(service,"mUsbTransport")!=null
      ||((Integer)call(service,"getGpmConnectMode")).intValue()!=0)return false;
   status=((Integer)call(service,"getGpmStatus")).intValue();if(status!=1&&status!=2)return false;
   Method cancel=null;for(Method m:request.getClass().getMethods())if(m.getName().equals("cancelBeforeEntry")&&m.getParameterTypes().length==1&&m.getParameterTypes()[0].isInstance(work))cancel=m;
   if(cancel==null||!((Boolean)cancel.invoke(request,work)).booleanValue())return false;
   synchronized(this){completed=true;}return true;
  }
 }
 public synchronized boolean completed(){return completed;}
}
