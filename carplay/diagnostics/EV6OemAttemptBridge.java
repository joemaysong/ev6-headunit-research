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
 * It never submits, cancels, removes, reconnects, or changes native/session state.
 */
public final class EV6OemAttemptBridge {
    private static final Object LOCK=new Object();
    private static volatile Owner current;
    private static Thread server;
    private static volatile LocalServerSocket activeListener;
    private static long owners;
    private static final int[] CODES={0x2c,0x2d,0x30,0x31,0x39,0x3a,0x12,0x18,0x35,0x5,0xe};
    private static final String UNKNOWN="EV6ATTEMPT4|UNKNOWN\n";
    private static volatile int acceptedDescriptorRepairs;
    private static final class Owner {
        final Object service;
        final Handler handler;
        final AttemptObservation state;
        Probe pending;
        Owner(Object service,Handler handler,String generation) {
            this.service=service;this.handler=handler;
            state=new AttemptObservation(generation);
        }
    }
    private static final class Probe {
        final CountDownLatch done=new CountDownLatch(1);
        AttemptObservation.Snapshot answer;
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
    public static void release(Object service) {
        synchronized(LOCK) {
            Owner o=owner(service);
            if(o!=null){o.state.observationFailed();current=null;}
        }
    }
    public static void submitted(Object service,String address) {
        Owner o=owner(service);if(o==null)return;
        try {
            String target=address==null?"":address.toUpperCase(Locale.US);
            if(!target.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}"))target="";
            o.state.submitted(target);
        }catch(RuntimeException failure){o.state.observationFailed();}
    }
    public static void enteredDispatch(Object service) {
        Owner o=owner(service);if(o!=null)o.state.enteredDispatch();
    }
    private static boolean controlMessage(Message message) {
        if(message==null)return false;
        for(int what:CODES)if(message.what==what)return true;
        return false;
    }
    public static void enteredDispatch(Object service,Message message) {
        Owner o=owner(service);if(o!=null)o.state.enteredDispatch(controlMessage(message));
    }
    public static void exitedDispatch(Object service,Message message,boolean normal) {
        Owner o=owner(service);if(o==null)return;
        // The hook is after the ENTIRE original handleMessage has returned.
        // A log/broadcast inside onWCPConnectionTimeout cannot call this hook.
        if(normal && message!=null && message.what==0x31) {
            AttemptObservation.Snapshot s=o.state.snapshot();
            o.state.terminalReturned(s.receiver,s.sequence);
        }
        if(normal && message!=null && message.what==0x2c)o.state.submissionReturned();
        o.state.exitedDispatch(normal);
    }
    public static void enteredConnect(Object service,String address) {
        Owner o=owner(service);
        if(o!=null)o.state.enteredConnect(address==null?null:address.toUpperCase(Locale.US));
    }
    public static void returnedConnect(Object service,boolean normal) {
        Owner o=owner(service);if(o!=null)o.state.returnedConnect(normal);
    }
    public static void enteredDelegate(Object service,String address) {
        Owner o=owner(service);
        if(o!=null)o.state.enteredDelegate(address==null?null:address.toUpperCase(Locale.US));
    }
    public static void returnedConnect(Object service,Object result,boolean normal) {
        Owner o=owner(service);if(o==null)return;
        String name=result instanceof Enum?((Enum<?>)result).name():"UNOBSERVED";
        o.state.returnedConnect(normal,name);
    }
    /** Observe queue commit too: a delayed retry may be queued outside the
     * Handler thread. Root queries cannot mistake a not-yet-committed post
     * for an empty queue. The original Message and uptime are untouched.
     */
    public static long enteredQueue(Object service,Message message) {
        Owner o=owner(service);if(o==null || !controlMessage(message))return -1;
        long token=o.state.snapshot().sequence;o.state.enteredDispatch();return token;
    }
    public static void exitedQueue(Object service,long token,boolean accepted) {
        Owner o=owner(service);if(o!=null && token>=0)o.state.exitedDispatch(accepted);
    }
    public static long enteredEvent(Object service) {
        Owner o=owner(service);if(o==null)return -1;
        long sequence=o.state.snapshot().sequence;o.state.enteredDispatch();return sequence;
    }
    public static long enteredCancelEvent(Object service,String address) {
        Owner o=owner(service);if(o==null)return -1;
        AttemptObservation.Snapshot s=o.state.snapshot();
        if(s.sequence==0)return -1;
        if(address==null || !s.target.equalsIgnoreCase(address)) {
            o.state.observationFailed();return -1;
        }
        o.state.enteredDispatch();return s.sequence;
    }
    public static void exitedEvent(Object service,long sequence,boolean terminal,boolean normal) {
        Owner o=owner(service);if(o==null || sequence<0)return;
        if(terminal && normal) {
            AttemptObservation.Snapshot s=o.state.snapshot();
            o.state.terminalReturned(s.receiver,sequence);
        }
        o.state.exitedDispatch(normal);
    }
    private static String query(final Owner o) throws Exception {
        if(o==null)return UNKNOWN;
        // At most ONE observer Runnable may wait behind a blocked Handler.
        // A timed-out root query reuses that slot rather than growing its queue.
        if(o.pending==null) {
            final Probe probe=new Probe();o.pending=probe;
            boolean posted=o.handler.post(new Runnable(){public void run(){
            try {
                if(current!=o || Thread.currentThread()!=o.handler.getLooper().getThread())return;
                int mask=0;
                for(int index=0;index<CODES.length;index++)
                    if(o.handler.hasMessages(CODES[index]))mask|=1<<index;
                AttemptObservation.Snapshot before=o.state.snapshot();
                o.state.handlerBoundary(before.receiver,before.sequence,mask,reentryIntent(o.service));
                if(current==o)probe.answer=o.state.snapshot();
            }catch(RuntimeException failure){o.state.observationFailed();}
            finally{probe.done.countDown();}
            }});
            if(!posted){o.pending=null;return UNKNOWN;}
        }
        Probe probe=o.pending;
        // Wait only on this observer worker. Never wait on the original Handler.
        if(!probe.done.await(1500,TimeUnit.MILLISECONDS))return UNKNOWN;
        o.pending=null;
        if(current!=o || probe.answer==null)
            return UNKNOWN;
        AttemptObservation.Snapshot s=probe.answer;
        if(o.state.snapshot().revision!=s.revision)return UNKNOWN;
        return "EV6ATTEMPT4|"+s.receiver+"|"+s.sequence+"|"+s.revision+"|"+s.target+"|"
            +s.dispatches+"|"+s.connectCalls+"|"+s.queued+"|"+s.unknown+"|"+s.complete+"|"+s.reentryIntent
            +"|"+s.workSettled+"|"+s.connectEntries+"|"+s.preflightRejected+"\n";
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
