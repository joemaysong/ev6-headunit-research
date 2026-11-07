import java.lang.reflect.Field;
import java.lang.reflect.Method;
/** Immutable listener identity. Never falls back to whichever receiver is current later. */
public final class ProjectionAaByeBye {
 private final Object service,receiver,transport,origin;private final boolean wifi;
 private ProjectionAaByeBye(Object s,Object r,Object t,Object o,boolean w){service=s;receiver=r;transport=t;origin=o;wifi=w;}
 private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 private static Object call(Object o,String name)throws Exception{return o.getClass().getMethod(name).invoke(o);}
 private static Object invoke(Class<?> c,String name,Object value)throws Exception{
  for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==1&&m.getParameterTypes()[0].isInstance(value)){m.setAccessible(true);return m.invoke(null,value);}
  throw new NoSuchMethodException(name);
 }
 public static Object capture(Object s){try{
  Object r=field(s,"mGalReceiver"),w=field(s,"mWifiTransport"),u=field(s,"mUsbTransport");
  if(r==null||(w==null)==(u==null))return null;Object t=w!=null?w:u,o=field(t,"ev6Origin");
  if(o==null||call(o,"service")!=s||call(o,"transport")!=t)return null;
  ProjectionAaByeBye captured=new ProjectionAaByeBye(s,r,t,o,w!=null);
  return captured.current()?captured:null;
 }catch(Exception unavailable){return null;}}
 private boolean current()throws Exception{
  return field(service,"mGalReceiver")==receiver&&field(service,wifi?"mWifiTransport":"mUsbTransport")==transport
   &&field(service,wifi?"mUsbTransport":"mWifiTransport")==null&&field(transport,"ev6Origin")==origin;
 }
 /** A late listener cannot send a response through a replacement native receiver. */
 public static void request(Object captured,int reason){
  if(!(captured instanceof ProjectionAaByeBye))return;ProjectionAaByeBye c=(ProjectionAaByeBye)captured;
  try{
   if(!c.current())return;
   Object producer=call(c.receiver,"ev6GetProducerScope");Class<?> producers=Class.forName("org.ev6.research.aa.AaReceiverScope");
   Object token=invoke(producers,"enter",producer);
   if(token!=null){try{if(c.current())call(c.receiver,"sendByeByeResponse");}catch(Exception responseFailed){/* Failed response must still enter exact-origin teardown. */}finally{invoke(producers,"exit",token);}}
   // A sealed producer may no longer emit packets. Cleanup still uses the original
   // origin and existing disconnect guard; this grants no resource-release receipt.
   c.disconnect(true);
  }catch(Exception unavailable){/* Unknown is not completion. Existing deadline/maintenance retains ownership. */}
 }
 public static void response(Object captured){
  if(!(captured instanceof ProjectionAaByeBye))return;
  try{((ProjectionAaByeBye)captured).disconnect(false);}catch(Exception unavailable){}
 }
 private void disconnect(boolean request)throws Exception{
  if(!current())return;
  Class<?> origins=Class.forName("org.ev6.research.aa.AaTransportOrigins");Object scope=invoke(origins,"callback",origin);
  try{
   if(!current())return;
   if(request&&wifi)service.getClass().getMethod("onDisconnected",Integer.TYPE,Boolean.TYPE).invoke(service,Integer.valueOf(999),Boolean.FALSE);
   else{
    if(request&&((Integer)call(service,"getGpmStatus")).intValue()!=6){Field f=service.getClass().getDeclaredField("mShouldShowNoRespondOsd");f.setAccessible(true);f.setBoolean(service,true);}
    service.getClass().getMethod("onDisconnected").invoke(service);
   }
  }finally{invoke(origins,"end",scope);}
 }
}
