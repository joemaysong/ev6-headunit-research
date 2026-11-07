import java.lang.reflect.Method;
/** Decode actual Iap2NotiMsg-shaped events. No identifiers are logged or persisted.
 * A parsed event is not a positive capability result; the model joins independent evidence.
 */
public final class ProjectionIapEvidence {
 private final ProjectionDiscovery discovery;
 public ProjectionIapEvidence(ProjectionDiscovery value){if(value==null)throw new IllegalArgumentException();discovery=value;}
 private static Object call(Object target,String name,Class<?>[] types,Object...args)throws Exception{
  if(target==null)throw new IllegalArgumentException("missing-item");Method m=target.getClass().getMethod(name,types);m.setAccessible(true);return m.invoke(target,args);
 }
 private static Object call(Object target,String name)throws Exception{return call(target,name,new Class<?>[0]);}
 public boolean accept(ProjectionDiscovery.Probe token,Object source,int event,Object message){
  if(event!=24 && event!=29)return false;
  try{
   Object group=call(message,"getItemGroup");int count=((Integer)call(group,"getItemCnt")).intValue();
   if(count<1 || count>32)throw new IllegalArgumentException("item-count");
   String address=null,serial=null;Integer wireless=null;boolean seenAddress=false,seenSerial=false,seenStatus=false;
   for(int i=0;i<count;i++){
    Object item=call(group,"getObjItem",new Class<?>[]{Integer.TYPE},i);
    int id=((Integer)call(item,"getItemId")).intValue(),type=((Integer)call(item,"getItemParamType")).intValue();
    if(event==24 && (id==0 || id==1)){
     if(type!=10 || (id==0?seenAddress:seenSerial))throw new IllegalArgumentException("transport-field");
     String[] values=(String[])call(item,"getItemStringArray");
     if(values==null || values.length!=1 || values[0]==null)throw new IllegalArgumentException("transport-array");
     if(id==0){address=values[0];seenAddress=true;}else{serial=values[0];seenSerial=true;}
    }else if(event==29 && id==0){
     if(type!=8 || seenStatus)throw new IllegalArgumentException("support-field");
     int[] values=(int[])call(item,"getItemIntArray");if(values==null || values.length!=1)throw new IllegalArgumentException("support-array");
     wireless=Integer.valueOf(values[0]);seenStatus=true;
    }
   }
   if(event==24){if(!seenAddress || !seenSerial)throw new IllegalArgumentException("missing-transport");discovery.cpTransport(token,source,address,serial);}
   else{if(!seenStatus)throw new IllegalArgumentException("missing-status");discovery.cpWirelessSupport(token,source,wireless.intValue());}
   return true;
  }catch(Exception malformed){discovery.cancel(token,source);return false;}
 }
}
