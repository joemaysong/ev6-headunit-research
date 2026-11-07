import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;
/** Evidence from the actual guarded service exit, never reconstructed from idle. */
public final class ProjectionAaServiceProof {
 private static final Map<Object,Proof> ENDED=new WeakHashMap<Object,Proof>();
 private static final class Proof {Object service;boolean admitted;Proof(Object s){service=s;}}
 private static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 private static Object call(Object o,String n)throws Exception{return o.getClass().getMethod(n).invoke(o);}
 private static boolean flag(Object o,String n)throws Exception{return ((Boolean)field(o,n)).booleanValue();}
 /** Injected after ended=true in AaRequestHandoff.end, under its original lock. */
 public static synchronized void ended(Object scope){try{
  if(scope==null||!flag(scope,"ended")||!flag(scope,"serviceEntry")||!flag(scope,"entered"))return;
  Object envelope=field(scope,"envelope");if(envelope==null)return;
  Object request=field(envelope,"request"),service=field(scope,"service");if(request==null||service==null)return;
  Proof proof=ENDED.get(request);if(proof==null){proof=new Proof(service);ENDED.put(request,proof);}
  if(proof.service!=service||flag(scope,"bodyAdmitted"))proof.admitted=true;
 }catch(Exception unavailable){} }
 public static synchronized boolean denied(Object service,Object request){Proof p=ENDED.get(request);return p!=null&&p.service==service&&!p.admitted;}
 public static synchronized void forget(Object request){ENDED.remove(request);}
 public static boolean stopNeverEntered(Object service,Object request)throws Exception{
  synchronized(service){synchronized(Class.forName("org.ev6.research.aa.AaRequestHandoff")){
   if(!denied(service,request)||field(service,"mWifiTransport")!=null||field(service,"mGalReceiver")!=null||field(service,"mUsbTransport")!=null
    ||((Integer)call(service,"getGpmConnectMode")).intValue()!=0)return false;
   int state=((Integer)call(service,"getGpmStatus")).intValue();if(state!=1&&state!=2)return false;
   // Every original submitted work must already be retired by its own exit.
   if(((Integer)call(request,"pending")).intValue()!=0)return false;
   call(request,"seal");return ((Boolean)call(request,"drained")).booleanValue();
  }}
 }
}
