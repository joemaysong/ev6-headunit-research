import java.util.IdentityHashMap;
/** Private SDK instance boundary. Discovery cannot send Wi-Fi configuration by default.
 * Only a future adapter of the existing projection permit may provide Authorizer.
 */
public final class ProjectionCredentialBoundary {
 public interface Authorizer {boolean allowed(Object exactDelegate)throws Exception;}
 private static final IdentityHashMap<Object,Authorizer> live=new IdentityHashMap<Object,Authorizer>();
 private static final IdentityHashMap<Object,Boolean> seen=new IdentityHashMap<Object,Boolean>();
 private ProjectionCredentialBoundary(){}
 public static synchronized void bind(Object delegate,Authorizer authority){
  if(delegate==null||authority==null||seen.containsKey(delegate)||seen.size()>=32)throw new IllegalStateException("fresh-sdk-owner-required");
  seen.put(delegate,Boolean.TRUE);live.put(delegate,authority);
 }
 public static synchronized void close(Object delegate,Authorizer authority){if(live.get(delegate)==authority)live.remove(delegate);}
 public static synchronized boolean allow(Object delegate){
  Authorizer authority=live.get(delegate);if(authority==null)return false;
  try{return authority.allowed(delegate) && live.get(delegate)==authority;}catch(Exception unavailable){return false;}
 }
}
