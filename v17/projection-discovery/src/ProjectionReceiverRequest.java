import java.util.Map;
/** Captures an immutable request from the exact receiver and transport lifetime. */
public final class ProjectionReceiverRequest {
 private static Object field(Object o,String n)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 private static Object call(Object o,String n)throws Exception{java.lang.reflect.Method m=o.getClass().getMethod(n);m.setAccessible(true);return m.invoke(o);}
 public static Object capture(Map<?,?> bindings,Object service,Object receiver,Object transport)throws Exception{
  if(service==null||receiver==null||transport==null||bindings==null||bindings.size()>4096)return null;
  Object found=null;for(Object binding:bindings.values()){
   if(field(binding,"receiver")!=receiver||!((Boolean)field(binding,"wifi")).booleanValue())continue;
   Object origin=field(binding,"origin");if(call(origin,"service")!=service||call(origin,"transport")!=transport)continue;
   Object request=call(origin,"request");if(request==null)continue;if(found!=null&&found!=request)return null;found=request;
  }return found;
 }
}
