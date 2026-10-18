import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Handler;
import android.os.Message;
import java.io.FileInputStream;
import java.io.FileDescriptor;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Prototype private observation transport, not installed or an OEM API.
 * It never submits/cancels/removes OEM work or changes native/session state.
 * It posts and retires only its own diagnostic Runnable by exact object identity.
 */
public final class EV6OemAttemptBridge {
    private static final Object LOCK=new Object();
    private static volatile Owner current;
    private static Thread server;
    private static volatile LocalServerSocket activeListener;
    private static long owners;
    private static final int[] CODES={0x2c,0x2d,0x30,0x31,0x39,0x3a,0x12,0x18,0x35,0x5,0xe};
    private static final String UNKNOWN="EV6ATTEMPT13|UNKNOWN\n";
    private static volatile int acceptedDescriptorRepairs;
    private static final class Owner {
        final Object service;
        final Handler handler;
        final AttemptObservation state;
        Probe pending;
        boolean boundaryPending;
        Owner(Object service,Handler handler,String generation) {
            this.service=service;this.handler=handler;
            state=new AttemptObservation(generation);
        }
    }
    private static final class Probe {
        final CountDownLatch done=new CountDownLatch(1);
        AttemptObservation.Snapshot answer;
        long wiredGateRevision;
        Runnable task;
    }
    private static Owner owner(Object service) {
        Owner o=current;return o!=null && o.service==service?o:null;
    }
    private static String processGeneration() throws Exception {
        FileInputStream input=new FileInputStream("/proc/self/stat");
        try {
            byte[] data=new byte[4096];int size=input.read(data);
            if(size<1 || size==data.length)throw new IllegalStateException("Process metadata unavailable");
            String text=new String(data,0,size,"US-ASCII");int end=text.lastIndexOf(") ");
            if(end<0)throw new IllegalStateException("Process metadata malformed");
            String[] fields=text.substring(end+2).trim().split("\\s+");
            if(fields.length<=19 || !fields[19].matches("[0-9]+"))
                throw new IllegalStateException("Process generation unavailable");
            return android.os.Process.myPid()+":"+fields[19];
        }finally{input.close();}
    }
    /** Call only after the real Service has initialized its original Handler. */
    public static void install(Object service,Handler handler) {
        Owner created=null;
        try {
            if(service==null || handler==null || android.os.Process.myUid()!=1000)return;
            synchronized(LOCK) {
                if(owner(service)!=null)return;
                created=new Owner(service,handler,processGeneration()+":"+(++owners));
                current=created;
                if(server==null || !server.isAlive()) {
                    server=new Thread(new Runnable(){public void run(){serve();}},"EV6-attempt-observer");
                    server.setDaemon(true);server.start();
                }
            }
        }catch(Exception failure){
            synchronized(LOCK){if(created!=null && current==created)current=null;}
        }
    }
    private static void observed_release(Object service) {
        synchronized(LOCK) {
            Owner o=owner(service);
            if(o!=null){o.state.observationFailed();current=null;}
        }
    }
    /** Completion bookkeeping follows actual OEM work, independent of root polling.
     * The observation Runnable never submits an OEM message or invokes a control.
     * All queue/field reads run on the original Handler thread.
     */
    private static void boundaryNow(Owner o) {
        if(current!=o || Thread.currentThread()!=o.handler.getLooper().getThread())return;
        try {
            int mask=0;
            for(int index=0;index<CODES.length;index++)
                if(o.handler.hasMessages(CODES[index]))mask|=1<<index;
            AttemptObservation.Snapshot before=o.state.snapshot();
            o.state.handlerBoundary(before.receiver,before.sequence,mask,reentryIntent(o.service));
        }catch(RuntimeException failure){o.state.observationFailed();}
    }
    private static void boundaryAfterWork(final Owner o) {
        if(o==null || current!=o)return;
        if(Thread.currentThread()==o.handler.getLooper().getThread()) {
            boundaryNow(o);return;
        }
        synchronized(LOCK) {
            if(current!=o || o.boundaryPending)return;
            o.boundaryPending=true;
            boolean posted=o.handler.post(new Runnable(){public void run(){
                synchronized(LOCK){o.boundaryPending=false;}
                boundaryNow(o);
            }});
            if(!posted){o.boundaryPending=false;o.state.observationFailed();}
        }
    }
    private static void observed_submitted(Object service,String address) {submitted(service,address,-1);}
    private static void submitted(Object service,String address,int transport) {
        Owner o=owner(service);if(o==null)return;
        try {
            String target=address==null?"":address.toUpperCase(Locale.US);
            if(!target.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}"))target="";
            o.state.submitted(target,transport);
        }catch(RuntimeException failure){o.state.observationFailed();}
    }
    private static void observed_enteredDispatch(Object service) {
        Owner o=owner(service);if(o!=null)o.state.enteredDispatch();
    }
    private static boolean controlMessage(Message message) {
        if(message==null)return false;
        for(int what:CODES)if(message.what==what)return true;
        return false;
    }
    private static void observed_enteredDispatch(Object service,Message message) {
        Owner o=owner(service);if(o!=null) {
            if(message!=null && message.what==0x2c)o.state.submissionStarted();
            o.state.enteredDispatch(controlMessage(message));
        }
    }
    private static void observed_exitedDispatch(Object service,Message message,boolean normal) {
        Owner o=owner(service);if(o==null)return;
        // The hook is after the ENTIRE original handleMessage has returned.
        // A log/broadcast inside onWCPConnectionTimeout cannot call this hook.
        if(normal && message!=null && message.what==0x31) {
            AttemptObservation.Snapshot s=o.state.snapshot();
            o.state.terminalReturned(s.receiver,s.sequence);
        }
        if(normal && message!=null && message.what==0x2c)o.state.submissionReturned();
        o.state.exitedDispatch(normal);
        boundaryAfterWork(o);
    }
    private static void observed_enteredTransportConnect(Object service,String address,int transport) {
        Owner o=owner(service);
        if(o!=null){o.state.enteringTransportCall(transport,address==null?null:address.toUpperCase(Locale.US));o.state.enteredConnect(address==null?null:address.toUpperCase(Locale.US));}
    }
    private static void observed_returnedConnect(Object service,boolean normal) {
        Owner o=owner(service);if(o!=null)o.state.returnedConnect(normal);
    }
    private static void observed_enteredDelegate(Object service,String address) {
        Owner o=owner(service);
        if(o!=null)o.state.enteredDelegate(address==null?null:address.toUpperCase(Locale.US));
    }
    private static void observed_returnedConnect(Object service,Object result,boolean normal) {
        Owner o=owner(service);if(o==null)return;
        String name=result instanceof Enum?((Enum<?>)result).name():"UNOBSERVED";
        o.state.returnedConnect(normal,name);
    }
    /** Observe queue commit too: a delayed retry may be queued outside the
     * Handler thread. Root queries cannot mistake a not-yet-committed post
     * for an empty queue. The original Message and uptime are untouched.
     */
    private static long observed_enteredQueue(Object service,Message message) {
        Owner o=owner(service);if(o==null || !controlMessage(message))return -1;
        // Observe the shared original queue boundary, including original
        // producers that do not pass through PPActionListener. Never change
        // the message, uptime, original queue result or original retry logic.
        // Refresh a genuine Handler boundary BEFORE a new queue submission;
        // this closes the gap when two original events occur between polls.
        if(message.what==0x2c) {
            boundaryNow(o);
            submitted(service,message.obj instanceof String?(String)message.obj:null,message.arg1);
        }
        long token=o.state.snapshot().sequence;o.state.enteredDispatch();return token;
    }
    private static void observed_exitedQueue(Object service,long token,boolean accepted) {
        Owner o=owner(service);
        if(o!=null && token>=0){o.state.exitedDispatch(accepted);boundaryAfterWork(o);}
    }
    /** onControllerAdded may produce a NEW request. Keep a historical settled
     * receipt until actual queue/control work invalidates it. In-flight event
     * bracketing still blocks every live completion/release bit. */
    private static long observed_enteredControllerAdded(Object service) {
        Owner o=owner(service);if(o==null)return -1;
        boundaryNow(o);
        long sequence=o.state.snapshot().sequence;
        o.state.enteredDispatch(false);return sequence;
    }
    /** Observe only genuine original endpoint-group removal. No cancellation
     * is issued here. A running dispatch cannot qualify as never-started. */
    private static long observed_enteredRemoval(Object service,int what) {
        Owner o=owner(service);if(o==null)return -1;
        try {
            long token=(what==0x2c || what==-1) && o.handler.hasMessages(0x2c)
                ?o.state.snapshot().sequence:0;
            o.state.enteredDispatch(false);return token;
        }catch(RuntimeException failure){o.state.observationFailed();return -1;}
    }
    private static void observed_exitedRemoval(Object service,long token,boolean normal) {
        Owner o=owner(service);if(o==null || token<0)return;
        if(normal && token>0)o.state.submissionRemoved(token);
        o.state.exitedDispatch(normal);boundaryAfterWork(o);
    }
    private static long observed_enteredEvent(Object service) {
        Owner o=owner(service);if(o==null)return -1;
        long sequence=o.state.snapshot().sequence;o.state.enteredDispatch();return sequence;
    }
    private static long observed_enteredCancelEvent(Object service,String address) {
        Owner o=owner(service);if(o==null)return -1;
        AttemptObservation.Snapshot s=o.state.snapshot();
        if(s.sequence==0 || address==null || !s.target.equalsIgnoreCase(address)) {
            // Other OEM producers may cancel another PP target. Bracket its
            // real work but do not give this callback our terminal receipt.
            if(s.sequence>0)o.state.correlationUncertain();
            o.state.enteredDispatch();return -2;
        }
        o.state.enteredDispatch();return s.sequence;
    }
    private static void observed_exitedEvent(Object service,long sequence,boolean terminal,boolean normal) {
        Owner o=owner(service);if(o==null || sequence==-1)return;
        if(terminal && normal && sequence>0) {
            AttemptObservation.Snapshot s=o.state.snapshot();
            o.state.terminalReturned(s.receiver,sequence);
        }
        o.state.exitedDispatch(normal);
        boundaryAfterWork(o);
    }
    private static long observed_enteredControllerRemoval(Object service,String address) {
        Owner o=owner(service);if(o==null)return -1;
        long token=o.state.enteringFailedWiredRemoval(address);
        o.state.enteredDispatch();return token;
    }
    private static void observed_exitedControllerRemoval(Object service,long token,boolean terminal,boolean normal) {
        Owner o=owner(service);if(o==null)return;
        o.state.returnedFailedWiredRemoval(token,normal);
        o.state.exitedDispatch(normal);boundaryAfterWork(o);
    }
    private static int actualTransport(Object transport) {
        if(transport instanceof Enum) {
            String name=((Enum<?>)transport).name();
            if(name.equals("Wired"))return 0;if(name.equals("Wireless"))return 1;
            if(name.equals("Unknown"))return 2;
        }
        return -1;
    }
    private static long observed_enteredSessionInitialized(Object service) {
        Owner o=owner(service);if(o==null)return -1;
        o.state.enteredDispatch(false);return o.state.sessionInitializing();
    }
    private static void observed_exitedSessionInitialized(Object service,long epoch,boolean terminal,boolean normal) {
        Owner o=owner(service);if(o==null)return;
        o.state.sessionInitializedReturned(epoch,normal);
        o.state.exitedDispatch(normal);boundaryAfterWork(o);
    }
    private static long observed_enteredSessionDetails(Object service) {
        Owner o=owner(service);if(o==null)return -1;
        o.state.enteredDispatch(false);return o.state.enteringSessionDetails();
    }
    private static void observed_exitedSessionDetails(Object service,long epoch,String deviceID,boolean normal) {
        Owner o=owner(service);if(o==null)return;
        o.state.returnedSessionDetails(epoch,deviceID,normal);
        o.state.exitedDispatch(normal);boundaryAfterWork(o);
    }
    private static long observed_enteredSessionStarted(Object service) {
        Owner o=owner(service);if(o==null)return -1;
        o.state.enteredDispatch(false);return o.state.enteringSessionStart();
    }
    private static void observed_exitedSessionStarted(Object service,long token,Object transport,boolean normal) {
        Owner o=owner(service);if(o==null)return;
        o.state.sessionStarted(token,actualTransport(transport),normal);
        o.state.exitedDispatch(normal);boundaryAfterWork(o);
    }
    private static long observed_enteredTransportFinalized(Object service,Object transport) {
        Owner o=owner(service);if(o==null)return -1;
        int type=-1;
        if(transport instanceof Enum) {
            String name=((Enum<?>)transport).name();
            if(name.equals("Wired"))type=0;else if(name.equals("Wireless"))type=1;else if(name.equals("Unknown"))type=2;
        }
        long token=o.state.enteringTransportFinalized(type);
        o.state.enteredDispatch(false);return token;
    }
    private static void observed_exitedFinalizedEvent(Object service,long token,boolean terminal,boolean normal) {
        Owner o=owner(service);if(o==null)return;
        o.state.returnedTransportFinalized(token,normal);
        o.state.exitedDispatch(normal);boundaryAfterWork(o);
    }
    private static String query(final Owner o) throws Exception {
        if(o==null)return UNKNOWN;
        // At most ONE observer Runnable may wait behind a blocked Handler.
        // A timed-out root query reuses that slot rather than growing its queue.
        if(o.pending==null) {
            final Probe probe=new Probe();o.pending=probe;
            probe.task=new Runnable(){public void run(){
            try {
                if(current!=o || Thread.currentThread()!=o.handler.getLooper().getThread())return;
                if(EV6WiredControllerGate.pending(o.service))return;
                probe.wiredGateRevision=EV6WiredControllerGate.revision(o.service);
                int mask=0;
                for(int index=0;index<CODES.length;index++)
                    if(o.handler.hasMessages(CODES[index]))mask|=1<<index;
                AttemptObservation.Snapshot before=o.state.snapshot();
                o.state.handlerBoundary(before.receiver,before.sequence,mask,reentryIntent(o.service));
                if(current==o)probe.answer=o.state.snapshot();
            }catch(RuntimeException failure){o.state.observationFailed();}
            finally{probe.done.countDown();}
            }};
            boolean posted=o.handler.post(probe.task);
            if(!posted){if(o.pending==probe)o.pending=null;return UNKNOWN;}
        }
        Probe probe=o.pending;
        // Wait only on this observer worker. Never wait on the original Handler.
        if(!probe.done.await(1500,TimeUnit.MILLISECONDS)) {
            // OEM removeCallbacksAndMessages(null) may have discarded OUR
            // Runnable. Retire only that exact diagnostic task. Timeout is
            // NEVER a completion receipt or permission to release OEM work.
            try{o.handler.removeCallbacks(probe.task);}
            finally{if(o.pending==probe)o.pending=null;}
            return UNKNOWN;
        }
        if(o.pending==probe)o.pending=null;
        if(current!=o || probe.answer==null)
            return UNKNOWN;
        AttemptObservation.Snapshot s=probe.answer;
        if(o.state.snapshot().revision!=s.revision)return UNKNOWN;
        if(EV6WiredControllerGate.pending(o.service) || EV6WiredControllerGate.revision(o.service)!=probe.wiredGateRevision)return UNKNOWN;
        return serialize(s);
    }
    static String serialize(AttemptObservation.Snapshot s) {
        return "EV6ATTEMPT13|"+s.receiver+"|"+s.sequence+"|"+s.revision+"|"+s.target+"|"
            +s.dispatches+"|"+s.connectCalls+"|"+s.queued+"|"+s.unknown+"|"+s.complete+"|"+s.reentryIntent
            +"|"+s.workSettled+"|"+s.connectEntries+"|"+s.preflightRejected+"|"+s.uncertaintyReasons+"|"+s.recoveryReady+"|"+s.transportOpenMask+"|"+s.transportFault+"|"+s.transportsSettled+"|"+s.failedWiredRetirements+"|"+s.actualSessionTransport+"|"+s.sessionEpoch+"|"+s.requestActualMismatches+"|"+s.pendingRequestMask+"|"+s.repeatedPendingRequests+"|"+s.unknownRequestBindings+"|"+s.groupRequestedModes+"|"+s.mixedPendingRequests+"|"+s.sessionIdentityVerified+"|"+s.transportBlocker+"|"+s.lifecycleConfidence+"|"+s.sessionTarget
            +"|"+s.requestCount+"|"+s.acceptedRequests+"|"+s.rejectedRequests+"|"+s.lastFinalizedEpoch+"|"+s.lastFinalizedTarget+"\n";
    }
    /** Read exact original fields only; null has the original getters' empty
     * meaning. No original method invocation, field write or address output.
     */
    private static int reentryIntent(Object service) {
        try {
            Field field=service.getClass().getDeclaredField("mUtils");field.setAccessible(true);
            Object utils=field.get(service);if(utils==null)return -1;
            int flags=0,index=0;
            for(String name:new String[]{"mConnectDeviceID","mReconnectAddress"}) {
                Field value=utils.getClass().getDeclaredField(name);value.setAccessible(true);
                if(value.getType()!=String.class)return -1;
                String address=(String)value.get(utils);
                if(address!=null && address.length()>0)flags|=1<<index;
                index++;
            }
            return flags;
        }catch(Exception unavailable){return -1;}
    }
    private static void recordHookFailure(Object service) {
        try {Owner o=owner(service);if(o!=null)o.state.observationFailed();}
        catch(RuntimeException ignored) {/* Observation must not replace OEM execution. */}
    }
    public static void release(Object service) {
        try {observed_release(service);}
        catch(RuntimeException failure) {recordHookFailure(service);}
    }
    public static void submitted(Object service,String address) {
        try {observed_submitted(service,address);}
        catch(RuntimeException failure) {recordHookFailure(service);}
    }
    public static void enteredDispatch(Object service) {
        try {observed_enteredDispatch(service);}
        catch(RuntimeException failure) {recordHookFailure(service);}
    }
    public static void enteredDispatch(Object service,Message message) {
        try {observed_enteredDispatch(service,message);}
        catch(RuntimeException failure) {recordHookFailure(service);}
    }
    public static void exitedDispatch(Object service,Message message,boolean normal) {
        try {observed_exitedDispatch(service,message,normal);}
        catch(RuntimeException failure) {recordHookFailure(service);}
    }
    public static void enteredTransportConnect(Object service,String address,int transport) {
        try {observed_enteredTransportConnect(service,address,transport);}
        catch(RuntimeException failure) {recordHookFailure(service);}
    }
    public static void returnedConnect(Object service,boolean normal) {
        try {observed_returnedConnect(service,normal);}
        catch(RuntimeException failure) {recordHookFailure(service);}
    }
    public static void enteredDelegate(Object service,String address) {
        try {observed_enteredDelegate(service,address);}
        catch(RuntimeException failure) {recordHookFailure(service);}
    }
    public static void returnedConnect(Object service,Object result,boolean normal) {
        try {observed_returnedConnect(service,result,normal);}
        catch(RuntimeException failure) {recordHookFailure(service);}
    }
    public static long enteredQueue(Object service,Message message) {
        try {return observed_enteredQueue(service,message);}
        catch(RuntimeException failure) {recordHookFailure(service);return -1;}
    }
    public static void exitedQueue(Object service,long token,boolean accepted) {
        try {observed_exitedQueue(service,token,accepted);}
        catch(RuntimeException failure) {recordHookFailure(service);}
    }
    public static long enteredControllerAdded(Object service) {
        try {return observed_enteredControllerAdded(service);}
        catch(RuntimeException failure) {recordHookFailure(service);return -1;}
    }
    public static long enteredRemoval(Object service,int what) {
        try {return observed_enteredRemoval(service,what);}
        catch(RuntimeException failure) {recordHookFailure(service);return -1;}
    }
    public static void exitedRemoval(Object service,long token,boolean normal) {
        try {observed_exitedRemoval(service,token,normal);}
        catch(RuntimeException failure) {recordHookFailure(service);}
    }
    public static long enteredEvent(Object service) {
        try {return observed_enteredEvent(service);}
        catch(RuntimeException failure) {recordHookFailure(service);return -1;}
    }
    public static long enteredCancelEvent(Object service,String address) {
        try {return observed_enteredCancelEvent(service,address);}
        catch(RuntimeException failure) {recordHookFailure(service);return -1;}
    }
    public static void exitedEvent(Object service,long sequence,boolean terminal,boolean normal) {
        try {observed_exitedEvent(service,sequence,terminal,normal);}
        catch(RuntimeException failure) {recordHookFailure(service);}
    }
    public static long enteredControllerRemoval(Object service,String address) {
        try {return observed_enteredControllerRemoval(service,address);}
        catch(RuntimeException failure) {recordHookFailure(service);return -1;}
    }
    public static void exitedControllerRemoval(Object service,long token,boolean terminal,boolean normal) {
        try {observed_exitedControllerRemoval(service,token,terminal,normal);}
        catch(RuntimeException failure) {recordHookFailure(service);}
    }
    public static long enteredSessionInitialized(Object service) {
        try {return observed_enteredSessionInitialized(service);}
        catch(RuntimeException failure) {recordHookFailure(service);return -1;}
    }
    public static void exitedSessionInitialized(Object service,long epoch,boolean terminal,boolean normal) {
        try {observed_exitedSessionInitialized(service,epoch,terminal,normal);}
        catch(RuntimeException failure) {recordHookFailure(service);}
    }
    public static long enteredSessionDetails(Object service) {
        try {return observed_enteredSessionDetails(service);}
        catch(RuntimeException failure) {recordHookFailure(service);return -1;}
    }
    public static void exitedSessionDetails(Object service,long epoch,String deviceID,boolean normal) {
        try {observed_exitedSessionDetails(service,epoch,deviceID,normal);}
        catch(RuntimeException failure) {recordHookFailure(service);}
    }
    public static long enteredSessionStarted(Object service) {
        try {return observed_enteredSessionStarted(service);}
        catch(RuntimeException failure) {recordHookFailure(service);return -1;}
    }
    public static void exitedSessionStarted(Object service,long token,Object transport,boolean normal) {
        try {observed_exitedSessionStarted(service,token,transport,normal);}
        catch(RuntimeException failure) {recordHookFailure(service);}
    }
    public static long enteredTransportFinalized(Object service,Object transport) {
        try {return observed_enteredTransportFinalized(service,transport);}
        catch(RuntimeException failure) {recordHookFailure(service);return -1;}
    }
    public static void exitedFinalizedEvent(Object service,long token,boolean terminal,boolean normal) {
        try {observed_exitedFinalizedEvent(service,token,terminal,normal);}
        catch(RuntimeException failure) {recordHookFailure(service);}
    }
    private static void serve() {
        for(;;) {
            LocalServerSocket listener=null;
            try {
                listener=new LocalServerSocket("ev6.carplay.attempt."+android.os.Process.myPid());
                activeListener=listener;
                for(;;) {
                    LocalSocket peer=listener.accept();
                    try {
                        peer.setSoTimeout(1000);
                        if(peer.getPeerCredentials().getUid()!=0)continue;
                        InputStream input=peer.getInputStream();
                        if(input.read()!=0x51)continue;
                        String reply=query(current);
                        peer.getOutputStream().write(reply.getBytes("US-ASCII"));
                        peer.getOutputStream().flush();
                    }catch(Exception unavailable){/* No success is published. */}
                    finally{closeAcceptedPeer(peer);}
                }
            }catch(Exception unavailable){
                // Losing OUR transport does not lose the original entry/return
                // observations. Keep that same owner/sequence; require a fresh
                // original Handler boundary before replying after rebind.
            }finally{
                if(activeListener==listener)activeListener=null;
                if(listener!=null)try{listener.close();}catch(Exception ignored){}
            }
            // Private IPC recovery backoff only. Never an OEM retry, timeout
            // completion, cancellation, receiver restart or resource action.
            try{Thread.sleep(250);}catch(InterruptedException stopped){return;}
        }
    }
    /** Exact legacy-framework workaround for OUR accepted diagnostic sockets.
     * This firmware's accept() leaves mFdCreatedInternally=false, so close()
     * drops the Java reference without closing the descriptor. Read that flag
     * before using the platform's descriptor close; never close a guessed fd.
     * No original CarPlay/network/session socket is accessed by this adapter.
     */
    private static void closeAcceptedPeer(LocalSocket peer) throws Exception {
        Field implementation=LocalSocket.class.getDeclaredField("impl");
        implementation.setAccessible(true);Object impl=implementation.get(peer);
        Field owns=impl.getClass().getDeclaredField("mFdCreatedInternally");
        owns.setAccessible(true);
        if(!owns.getBoolean(impl)) {
            FileDescriptor descriptor=peer.getFileDescriptor();
            if(descriptor!=null && descriptor.valid()) {
                Class.forName("libcore.io.IoBridge").getDeclaredMethod(
                    "closeSocket",FileDescriptor.class).invoke(null,descriptor);
                acceptedDescriptorRepairs++;
            }
        }
        peer.close();
    }
}
