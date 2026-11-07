/** Exact deferred request execution. An exception after claiming is unknown, never replayed. */
public final class ProjectionAaDeferredDispatch {
 public interface Port {void verifyExclusive(String requestId,String address)throws Exception;void clearManualPause(String address)throws Exception;void restoreManualPause(String address)throws Exception;Object invoke(String address)throws Exception;}
 public static Object execute(ProjectionAaRequests registry,ProjectionAaRequests.Request request,Port port)throws Exception {
  if(request==null||request.disconnect||port==null)throw new IllegalArgumentException();
  synchronized(registry){
   if(!registry.isDeferred(request))return request.ticket();
   port.verifyExclusive(request.id,request.address);
   if(!registry.grantDeferred(request)||!registry.start(request))throw new IllegalStateException("deferred-claim");
   try{port.clearManualPause(request.address);}catch(Exception commitFailed){if(!registry.cancelBeforeEffects(request))throw new IllegalStateException("noeffect-unverified");throw commitFailed;}
   // Verification is repeated after the durable pause mutation and immediately before effect.
   // If it fails the request remains claimed and ambiguous; the coordinator must drain it.
   try{port.verifyExclusive(request.id,request.address);}catch(Exception changed){port.restoreManualPause(request.address);if(!registry.cancelBeforeEffects(request))throw new IllegalStateException("restore-noeffect-unverified");throw changed;}
   return port.invoke(request.address);
  }
 }
 private ProjectionAaDeferredDispatch(){}
}
