package org.ev6.research.aa;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.lang.reflect.InvocationTargetException;
import org.ev6.projection.RequestWorkLedger;
import org.ev6.projection.ReservationStore;

/** Exact local Intent/WiFi-init handoffs. No release, native ACK or USB adoption.
 * Radio producers before sendStartServiceWireless still need source generation.
 */
public final class AaRequestHandoff {
    private static final String EXTRA="org.ev6.projection.request.handoff";
    private static final ThreadLocal<Scope> ACTIVE=new ThreadLocal<Scope>();
    private static final HashMap<String,Envelope> INTENTS=new HashMap<String,Envelope>();
    private static final IdentityHashMap<Message,Delivery> MESSAGES=new IdentityHashMap<Message,Delivery>();
    private static final IdentityHashMap<Object,RequestWorkLedger> SERVICES=new IdentityHashMap<Object,RequestWorkLedger>();
    private static final ArrayList<Delivery> UNSCHEDULED=new ArrayList<Delivery>();
    private static final class Envelope {
        final RequestWorkLedger request;final RequestWorkLedger.Work work;
        Envelope(RequestWorkLedger r,RequestWorkLedger.Work w){request=r;work=w;}
    }
    /** Private Intent snapshot, one registered Work, no current-request adoption.
     * This callback runs only through the exact admitted source dispatch.
     */
    static final class Submission implements Runnable {
        final RequestWorkLedger request;final RequestWorkLedger.Work work;
        private final Object origin;private final Context context;private final Intent payload;
        private final String id;private boolean attempted;
        Submission(Object origin,Context context,Intent intent,RequestWorkLedger request,RequestWorkLedger.Work work){
            this.origin=origin;this.context=context;this.request=request;this.work=work;
            id=ReservationStore.nonce();payload=new Intent(intent);payload.putExtra(EXTRA,id);
        }
        public void run(){
            final Envelope transfer;final Intent snapshot;
            synchronized(AaRequestHandoff.class){
                Scope source=ACTIVE.get();
                if(attempted||!sourceLive(origin,request)||source.envelope.work!=work)return;
                attempted=true;snapshot=new Intent(payload);transfer=new Envelope(request,work);
                if(!deferSource(source))return;
                INTENTS.put(id,transfer); // Work remains registered before Binder submission.
            }
            // Never hold the helper/source monitor across Binder or re-entry.
            if(request.isSealed()||!AaProjectionReservation.sameWirelessRequest(request)){cancelIntent(id,transfer);return;}
            try{if(context.startService(snapshot)==null)cancelIntent(id,transfer);}
            catch(RuntimeException unavailable){
                // No IPC retry after an attempt with uncertain acceptance.
                // If the service consumed the envelope, only its owner finishes it.
                cancelIntent(id,transfer);
            }
        }
    }
    private static final class Delivery {
        final Object service;final Handler handler;final int what;final Envelope envelope;
        final Runnable continuation;
        int retries;
        Delivery(Object s,Handler h,int what,Envelope e){this(s,h,what,e,null);}
        Delivery(Object s,Handler h,int what,Envelope e,Runnable continuation){
            service=s;handler=h;this.what=what;envelope=e;this.continuation=continuation;
        }
    }
    private static final class WirelessContinuation implements Runnable {
        final Object service;
        WirelessContinuation(Object service){this.service=service;}
        public void run(){
            synchronized(AaRequestHandoff.class){
                Scope scope=ACTIVE.get();
                if(!liveContinuation(scope,service)||scope.delivery.continuation!=this)return;
            }
            try{service.getClass().getMethod("ev6AaContinueWirelessStart").invoke(service);}
            catch(InvocationTargetException failure){
                Throwable cause=failure.getCause();
                if(cause instanceof RuntimeException)throw (RuntimeException)cause;
                if(cause instanceof Error)throw (Error)cause;
                throw new IllegalStateException("wireless-continuation-body-failed",cause);
            }catch(Exception unavailable){throw new IllegalStateException("wireless-continuation-unavailable",unavailable);}
        }
    }
    public static final class Scope {
        final Scope previous;final Object service;Envelope envelope;
        final boolean guarded;boolean entered,ended;
        Delivery delivery;Message dispatchMessage;boolean bodyAdmitted,retryNeeded;
        boolean continuationInvoked;
        boolean serviceEntry;
        Scope(Object s,Envelope e,boolean guard,boolean entered){
            previous=ACTIVE.get();service=s;envelope=e;guarded=guard;this.entered=entered;ACTIVE.set(this);
        }
    }
    /** Capture at native VideoSink creation, never infer identity on a later callback. */
    static synchronized RequestWorkLedger videoRequest(){
        Scope scope=ACTIVE.get();
        return scope!=null&&scope.bodyAdmitted&&status(scope)==ReservationStore.VALID?scope.envelope.request:null;
    }
    private static boolean current(RequestWorkLedger r){return r!=null&&AaProjectionReservation.wirelessRequest()==r;}
    private static boolean mayStart(Scope scope){
        if(scope==null||scope.ended||!scope.entered||scope.envelope==null)return false;
        try{return current(scope.envelope.request)&&scope.envelope.request.mayStart(scope.envelope.work);}
        catch(Exception unavailable){return false;}
    }
    private static int status(Scope scope){
        if(scope==null||scope.ended||!scope.entered||scope.envelope==null
            ||!AaProjectionReservation.sameWirelessRequest(scope.envelope.request))return ReservationStore.INVALID;
        return scope.envelope.request.startStatus(scope.envelope.work);
    }
    static synchronized Scope unboundSource(Object origin){return new Scope(origin,null,true,false);}
    static synchronized boolean bindSource(Scope scope,RequestWorkLedger request){
        if(scope==null||ACTIVE.get()!=scope||scope.ended||scope.envelope!=null||!current(request))return false;
        RequestWorkLedger.Work work=request.register();if(work==null)return false;
        return bindQueuedSource(scope,request,work);
    }
    /** Root continuation already retains exact request identity; no current()
     * validity read which could collapse BUSY into a lost binding.
     */
    static synchronized int bindExactSource(Scope scope,RequestWorkLedger request){
        if(scope==null||ACTIVE.get()!=scope||scope.ended||scope.envelope!=null
            ||!AaProjectionReservation.sameWirelessRequest(request))return ReservationStore.INVALID;
        RequestWorkLedger.Work work=request.register();if(work==null)return ReservationStore.INVALID;
        if(!request.enter(work)){request.cancelBeforeEntry(work);return ReservationStore.INVALID;}
        scope.envelope=new Envelope(request,work);scope.entered=true;return status(scope);
    }
    /** Bind the Work captured BEFORE enqueue, including stale-work retirement. */
    static synchronized boolean bindQueuedSource(Scope scope,RequestWorkLedger request,RequestWorkLedger.Work work){
        if(scope==null||ACTIVE.get()!=scope||scope.ended||scope.envelope!=null||request==null||work==null)return false;
        if(!request.enter(work)){request.cancelBeforeEntry(work);return false;}
        scope.envelope=new Envelope(request,work);scope.entered=true;
        return mayStart(scope);
    }
    static synchronized boolean sourceReady(Object origin,RequestWorkLedger request){
        Scope scope=ACTIVE.get();
        return scope!=null&&scope.service==origin&&scope.envelope!=null&&scope.envelope.request==request&&mayStart(scope);
    }
    static synchronized int sourceStatus(Object origin,RequestWorkLedger request){
        Scope scope=ACTIVE.get();
        if(scope==null||scope.service!=origin||scope.envelope==null||scope.envelope.request!=request)return ReservationStore.INVALID;
        return status(scope);
    }
    static synchronized void admitSource(Scope scope){
        if(scope!=null&&ACTIVE.get()==scope&&!scope.ended&&scope.envelope!=null
            &&AaProjectionReservation.sameWirelessRequest(scope.envelope.request)
            &&scope.envelope.request.bodyLive(scope.envelope.work))scope.bodyAdmitted=true;
    }
    static synchronized boolean sourceLive(Object origin,RequestWorkLedger request){
        Scope scope=ACTIVE.get();
        return scope!=null&&!scope.ended&&scope.service==origin&&scope.bodyAdmitted&&scope.envelope!=null
            &&scope.envelope.request==request&&AaProjectionReservation.sameWirelessRequest(request)
            &&request.bodyLive(scope.envelope.work);
    }
    static synchronized Submission captureSubmission(Object origin,Context context,Intent intent,RequestWorkLedger request){
        if(context==null||intent==null||!sourceLive(origin,request))return null;
        RequestWorkLedger.Work work=request.register();if(work==null)return null;
        try{return new Submission(origin,context,intent,request,work);}
        catch(RuntimeException invalid){request.cancelBeforeEntry(work);throw invalid;}
    }
    static synchronized boolean deferSource(Scope scope){
        if(scope==null||ACTIVE.get()!=scope||scope.ended||!scope.entered||scope.envelope==null
            ||!scope.envelope.request.deferBeforeEffects(scope.envelope.work))return false;
        scope.entered=false;scope.envelope=null;return true;
    }
    /** Strict OEM ingress used by radio-bound candidates: never adopts current. */
    static ComponentName startServiceBound(Context context,Intent intent){
        synchronized(AaRequestHandoff.class){if(!mayStart(ACTIVE.get()))return null;}
        return startService(context,intent);
    }
    public static ComponentName startService(Context context,Intent intent){
        final Envelope envelope;final String id=ReservationStore.nonce();
        synchronized(AaRequestHandoff.class){
            Scope scope=ACTIVE.get();
            // An existing but unbound/stale scope must never fall back to current.
            RequestWorkLedger request=scope==null?AaProjectionReservation.wirelessRequest():
                mayStart(scope)?scope.envelope.request:null;
            if(!current(request))return null;
            RequestWorkLedger.Work work=request.register();if(work==null)return null;
            envelope=new Envelope(request,work);
            try{intent.putExtra(EXTRA,id);}catch(RuntimeException failure){request.cancelBeforeEntry(work);throw failure;}
            INTENTS.put(id,envelope);
        }
        try{
            ComponentName result=context.startService(intent);
            if(result==null)cancelIntent(id,envelope);
            return result;
        }catch(RuntimeException failure){cancelIntent(id,envelope);throw failure;}
    }
    private static synchronized void cancelIntent(String id,Envelope expected){
        if(INTENTS.get(id)==expected){INTENTS.remove(id);expected.request.cancelBeforeEntry(expected.work);}
    }
    public static synchronized Scope beginService(Object service,Intent intent){
        Envelope envelope=null;
        if(intent!=null){
            try{String id=intent.getStringExtra(EXTRA);if(id!=null)envelope=INTENTS.remove(id);}
            catch(RuntimeException malformed){/* Deny wireless branch, preserve unrelated OEM branches. */}
        }
        boolean entered=envelope!=null&&envelope.request.enter(envelope.work);
        Scope scope=new Scope(service,envelope,true,entered);scope.serviceEntry=true;return scope;
    }
    /** Status 1 after a completed stop is reusable only by a fresh, exact
     * service delivery which already owns AA. This only opens the OEM gate;
     * accept() and queued transport dispatch still revalidate ownership.
     * No state reset, socket restart, lease release or request adoption here.
     */
    public static synchronized boolean canRestartIdleWireless(Object service){
        Scope scope=ACTIVE.get();
        return scope!=null&&scope.serviceEntry&&scope.service==service&&!scope.bodyAdmitted
            &&SERVICES.get(service)==null&&status(scope)==ReservationStore.VALID;
    }
    /** Runs at original wireless branch, after original USB/mode classification. */
    public static synchronized boolean accept(Object service){
        return accept(service,null);
    }
    public static synchronized boolean accept(Object service,Handler ownerHandler){
        Scope scope=ACTIVE.get();
        if(scope==null||!scope.serviceEntry||scope.bodyAdmitted||scope.service!=service||scope.envelope==null)return false;
        int state=status(scope);if(state==ReservationStore.INVALID)return false;
        RequestWorkLedger previous=SERVICES.get(service);
        if(previous!=null&&previous!=scope.envelope.request)return false;
        SERVICES.put(service,scope.envelope.request); // Identity binding alone grants no effects.
        if(state==ReservationStore.VALID){scope.bodyAdmitted=true;return true;}
        if(state==ReservationStore.BUSY||state==ReservationStore.UNKNOWN){
            Delivery delivery=new Delivery(service,ownerHandler,10,scope.envelope,new WirelessContinuation(service));
            Message pending=ownerHandler==null?null:Message.obtain(ownerHandler,delivery.continuation);
            if(pending!=null)pending.what=10;
            if(deferSource(scope)){
                if(ownerHandler==null)UNSCHEDULED.add(delivery);
                else schedule(pending,delivery,25);
            }else if(pending!=null)pending.recycle();
        }
        return false;
    }
    private static boolean liveContinuation(Scope scope,Object service){
        return scope!=null&&!scope.ended&&scope.service==service&&scope.bodyAdmitted&&scope.delivery!=null
            &&scope.delivery.continuation!=null&&scope.envelope!=null
            &&SERVICES.get(service)==scope.envelope.request
            &&AaProjectionReservation.sameWirelessRequest(scope.envelope.request)
            &&scope.envelope.request.bodyLive(scope.envelope.work);
    }
    /** Called by the generated wireless-only bridge, at most once per delivery.
     * Fresh durable admission already occurred at ConnectionHandler dispatch.
     */
    public static synchronized boolean continuationReady(Object service){
        Scope scope=ACTIVE.get();
        if(!liveContinuation(scope,service)||scope.continuationInvoked)return false;
        scope.continuationInvoked=true;return true;
    }
    private static void schedule(Message message,Delivery delivery,long delay){
        MESSAGES.put(message,delivery);
        try{
            if(!delivery.handler.sendMessageDelayed(message,delay)){
                MESSAGES.remove(message);UNSCHEDULED.add(delivery);
            }
        }catch(RuntimeException unavailable){
            MESSAGES.remove(message);UNSCHEDULED.add(delivery);
        }
    }
    private static boolean queuePermission(Scope scope){
        int state=status(scope);
        return state==ReservationStore.VALID||(scope!=null&&scope.bodyAdmitted
            &&(state==ReservationStore.BUSY||state==ReservationStore.UNKNOWN));
    }
    public static synchronized boolean post(Handler handler,Message message,long delay,Object service){
        Scope scope=ACTIVE.get();
        if(message==null||message.what!=10||scope==null||scope.service!=service||!queuePermission(scope)
            ||SERVICES.get(service)!=scope.envelope.request||MESSAGES.containsKey(message))return false;
        RequestWorkLedger request=scope.envelope.request;
        RequestWorkLedger.Work work=request.register();if(work==null)return false;
        Delivery delivery=new Delivery(service,handler,message.what,new Envelope(request,work));
        MESSAGES.put(message,delivery);
        try{
            boolean sent=handler.sendMessageDelayed(message,delay);
            if(!sent){MESSAGES.remove(message);request.cancelBeforeEntry(work);}
            return sent;
        }catch(RuntimeException failure){MESSAGES.remove(message);request.cancelBeforeEntry(work);throw failure;}
    }
    public static synchronized void remove(Handler handler,int what,Object service){
        Scope scope=ACTIVE.get();
        if(what!=10||scope==null||scope.service!=service||!queuePermission(scope)
            ||SERVICES.get(service)!=scope.envelope.request)return;
        // While durable state is unreadable, cancel only registered tokens.
        // Leave physical messages to be denied on dispatch; do not touch
        // potentially unrelated, unregistered Handler messages under UNKNOWN.
        if(status(scope)==ReservationStore.VALID)handler.removeMessages(what);
        Iterator<java.util.Map.Entry<Message,Delivery>> iterator=MESSAGES.entrySet().iterator();
        while(iterator.hasNext()){
            Delivery delivery=iterator.next().getValue();
            if(delivery.handler==handler&&delivery.what==what&&delivery.envelope.request==scope.envelope.request){
                delivery.envelope.request.cancelBeforeEntry(delivery.envelope.work);iterator.remove();
            }
        }
        Iterator<Delivery> stranded=UNSCHEDULED.iterator();
        while(stranded.hasNext()){
            Delivery delivery=stranded.next();
            if(delivery.handler==handler&&delivery.what==what&&delivery.envelope.request==scope.envelope.request){
                delivery.envelope.request.cancelBeforeEntry(delivery.envelope.work);stranded.remove();
            }
        }
    }
    /** Must be used at dispatchMessage, including Callback/Runnable paths. */
    public static synchronized Scope beginMessage(Object service,Handler handler,Message message){
        boolean guarded=message!=null&&message.what==10;
        Delivery delivery=guarded?MESSAGES.remove(message):null;
        boolean entered=delivery!=null&&delivery.envelope.request.enter(delivery.envelope.work);
        boolean matches=entered&&delivery.service==service&&delivery.handler==handler;
        Scope scope=new Scope(service,delivery==null?null:delivery.envelope,guarded,matches);
        if(matches){scope.delivery=delivery;scope.dispatchMessage=message;}
        return scope;
    }
    public static synchronized boolean allowed(Scope scope){
        if(scope==null||scope.ended)return false;
        if(!scope.guarded)return true;
        if(scope.delivery==null)return mayStart(scope);
        int state=status(scope);
        if(state==ReservationStore.VALID){scope.bodyAdmitted=true;scope.retryNeeded=false;return true;}
        if(!scope.bodyAdmitted&&(state==ReservationStore.BUSY||state==ReservationStore.UNKNOWN))scope.retryNeeded=true;
        return false;
    }
    public static synchronized void end(Scope scope){
        if(scope==null||scope.ended)return;
        // An out-of-order exit cannot overwrite a newer nested scope.
        if(ACTIVE.get()!=scope)throw new IllegalStateException("request-scope-order");
        if(scope.delivery!=null&&scope.retryNeeded&&!scope.bodyAdmitted){
            Message retry=Message.obtain(scope.dispatchMessage);
            Delivery delivery=scope.delivery;
            if(SERVICES.get(scope.service)==delivery.envelope.request&&deferSource(scope)){
                long delay=25L<<delivery.retries;
                if(delivery.retries<5)delivery.retries++;
                schedule(retry,delivery,delay);
            }else retry.recycle();
        }
        if(scope.envelope!=null)scope.envelope.request.finish(scope.envelope.work);
        scope.ended=true;
        if(scope.previous==null)ACTIVE.remove();else ACTIVE.set(scope.previous);
    }
    public static synchronized void forget(Object service){
        RequestWorkLedger request=SERVICES.remove(service);
        if(request!=null){
            request.seal(); // Never releases durable lease/native resources.
            Iterator<Delivery> queued=MESSAGES.values().iterator();
            while(queued.hasNext()){
                Delivery delivery=queued.next();
                if(delivery.service==service&&delivery.envelope.request==request){
                    request.cancelBeforeEntry(delivery.envelope.work);queued.remove();
                }
            }
            Iterator<Delivery> stranded=UNSCHEDULED.iterator();
            while(stranded.hasNext()){
                Delivery delivery=stranded.next();
                if(delivery.service==service&&delivery.envelope.request==request){
                    request.cancelBeforeEntry(delivery.envelope.work);stranded.remove();
                }
            }
        }
    }

    /** Capture only the already-admitted exact thread scope, never SERVICES fallback. */
    public static RequestWorkLedger receiverRequest(Object service) {
        Scope scope=ACTIVE.get();
        if(scope==null||scope.ended||!scope.entered||!scope.bodyAdmitted
                ||scope.service!=service||scope.envelope==null)return null;
        // This is identity capture, not a fresh admission or a validity lease.
        // The thread-local admitted scope is exact even if its request retires.
        return scope.envelope.request;
    }
}
