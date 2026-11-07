/** Correlates an unchanged synchronous PPManager call with its reentrant GPM callback. */
public final class ProjectionAaCallBridge {
 public interface Action {void run()throws Exception;}
 public static final class Call {
  private final String address;private final boolean disconnect;private Object ticket;private boolean ambiguous,closed;
  private Call(String a,boolean d){address=a;disconnect=d;}
  public Object ticket(){return ambiguous?null:ticket;}
 }
 private final ThreadLocal<Call> active=new ThreadLocal<Call>();
 public Call invoke(String address,boolean disconnect,Action action)throws Exception{
  if(address==null||!address.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")||action==null)throw new IllegalArgumentException();
  if(active.get()!=null)throw new IllegalStateException("nested-dispatch");
  Call call=new Call(address,disconnect);active.set(call);
  try{action.run();return call;}finally{call.closed=true;active.remove();}
 }
 public void capture(String address,boolean disconnect,Object ticket){Call c=active.get();if(c==null||c.closed||ticket==null||c.disconnect!=disconnect||!c.address.equalsIgnoreCase(address))return;if(c.ticket!=null)c.ambiguous=true;else c.ticket=ticket;}
}
