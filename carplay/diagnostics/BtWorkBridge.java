package org.ev6.bt;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import java.io.*;
import java.lang.reflect.*;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Root-only exact-work cancel gate; all decisions run on the OEM SM Handler.
 * No flag/queue clearing, profile disconnect, or successful-session fabrication.
 */
public final class BtWorkBridge {
    static final int CANCEL=0x45564254;
    static volatile Object machine;
    static volatile long epoch=1;
    static volatile String receiver="";
    static volatile boolean broken;
    static Thread listener;
    static final class Ticket {
        final long epoch,expires; final String target;
        final CountDownLatch done=new CountDownLatch(1);
        volatile String result="UNVERIFIED";
        Ticket(long e,String t){epoch=e;target=t;expires=SystemClock.elapsedRealtime()+1200;}
    }
    static Method method(Class<?> c,String name,Class<?>...types) throws Exception {
        for(Class<?> k=c;k!=null;k=k.getSuperclass())try {Method m=k.getDeclaredMethod(name,types);m.setAccessible(true);return m;}catch(NoSuchMethodException missing){}
        throw new NoSuchMethodException(name);
    }
    static Object call(Object o,String name) throws Exception {return method(o.getClass(),name).invoke(o);}
    static Object field(Object o,String name) throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static String address(Object d) throws Exception {
        if(d==null)return "-";String a=(String)call(d,"getAddress");
        if(a==null || !a.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))throw new IOException("address");return a.toUpperCase(java.util.Locale.US);
    }
    static Object service() throws Exception {return field(machine,"mService");}
    static boolean empty(Object s) throws Exception {
        return ((Integer)call(s,"btcmGetCountConnectedDevices"))==0
            && call(s,"btcmGetHfpConnectedDevice")==null
            && call(s,"btcmGetA2dpConnectedDevice1")==null
            && call(s,"btcmGetA2dpConnectedDevice2")==null
            && call(s,"btcmGetConnectedCallDevice")==null;
    }
    static boolean projectionInactive(Object s) throws Exception {
        return !(Boolean)call(s,"btcmGetDipoConnectionStatus")
            && !(Boolean)call(s,"btcmGetGpmConnectionStatus")
            && !(Boolean)call(s,"btcmGetMirrorLinkConnectionStatus")
            && !(Boolean)call(s,"btcmGetCarlifeConnectionStatus");
    }
    public static void changed(Object m) {
        try {if(m==machine){if(epoch==Long.MAX_VALUE)broken=true;else epoch++;}}catch(Throwable ignored){broken=true;}
    }
    public static void attach(final Object m) {
        try {
            synchronized(BtWorkBridge.class) {
                if(machine!=null){if(machine!=m)broken=true;return;}
                machine=m;byte[] bytes=new byte[16];new SecureRandom().nextBytes(bytes);StringBuilder s=new StringBuilder();
                for(byte b:bytes)s.append(String.format(java.util.Locale.US,"%02x",b&255));receiver=s.toString();
                listener=new Thread(new Runnable(){public void run(){serve();}},"EV6-BT-work");listener.setDaemon(true);listener.start();
            }
        }catch(Throwable ignored){broken=true;}
    }
    /** Normal messages return immediately; original wrapper calls original body directly. */
    public static boolean consume(Object state,Message msg) {
        if(msg.what!=CANCEL) {
            // Invalidate a receipt for EVERY original SM message, including
            // a same-phone replacement which does not change processDevice.
            // Diagnostic Handler Runnables do not enter this wrapper.
            try {Object m=field(state,"this$0");synchronized(m){changed(m);}}
            catch(Throwable unavailable){broken=true;}
            return false;
        }
        if(!(msg.obj instanceof Ticket))return true;
        Ticket t=(Ticket)msg.obj;
        try {
            Object m=machine;
            if(m==null){t.result="UNAVAILABLE";return true;}
            synchronized(m) {
                // State machine processing and synchronized process/state setters
                // share this monitor. Compare AND original cancel dispatch stay
                // in the same OEM Handler invocation and monitor boundary.
                if(broken || t.epoch!=epoch || SystemClock.elapsedRealtime()>t.expires
                    || !state.getClass().getName().endsWith("$BTCMConnecting")
                    || field(state,"this$0")!=m
                    || ((Integer)call(m,"btcmSmGetExposedBTCMState"))!=1
                    || !t.target.equals(address(call(m,"btcmSmGetProcessDevice")))) {
                    t.result="STALE_WORK";return true;
                }
                Object svc=service();
                if(!empty(svc) || !projectionInactive(svc)){t.result="ACTIVE_OWNER";return true;}
                // Preserve original normal-cancel handling and the enclosing
                // StateMachine's normal transition processing after return.
                msg.what=0x1e;msg.arg1=0x1450;msg.obj=null;
                method(state.getClass(),"processMessage$ev6Original",Message.class).invoke(state,msg);
                t.result="ORIGINAL_CANCEL_DISPATCHED";
            }
        }catch(Throwable unavailable){t.result="UNVERIFIED";}
        finally {t.done.countDown();}
        return true;
    }
    static String snapshot() throws Exception {
        Object m=machine;
        synchronized(m) {
            if(broken)throw new IOException("unavailable");Object s=service();
            return "EV6BT1|"+receiver+"|"+epoch+"|"+call(m,"btcmSmGetExposedBTCMState")+"|"
                +call(s,"btcmIsBusy")+"|"+call(s,"btcmIsAutoConnection")+"|"
                +address(call(m,"btcmSmGetProcessDevice"))+"|"+empty(s)+"|"+projectionInactive(s);
        }
    }
    static String query(final Handler handler) throws Exception {
        final String[] answer={null};final CountDownLatch done=new CountDownLatch(1);
        final Runnable task=new Runnable(){public void run(){try{answer[0]=snapshot();}catch(Throwable unavailable){}finally{done.countDown();}}};
        if(!handler.post(task))return "EV6BT1|UNKNOWN";
        try {if(!done.await(1200,TimeUnit.MILLISECONDS) || answer[0]==null)return "EV6BT1|UNKNOWN";return answer[0];}
        finally {handler.removeCallbacks(task);}
    }
    static String read(InputStream input) throws Exception {
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        for(int i=0;i<160;i++){int c=input.read();if(c==10)return new String(b.toByteArray(),"US-ASCII");if(c<32 || c>126)throw new IOException("request");b.write(c);}
        throw new IOException("length");
    }
    static void closeAccepted(LocalSocket peer) throws Exception {
        Field f=LocalSocket.class.getDeclaredField("impl");f.setAccessible(true);Object impl=f.get(peer);
        Field owns=impl.getClass().getDeclaredField("mFdCreatedInternally");owns.setAccessible(true);
        if(!owns.getBoolean(impl)) {
            FileDescriptor fd=peer.getFileDescriptor();
            if(fd!=null && fd.valid())Class.forName("libcore.io.IoBridge").getDeclaredMethod("closeSocket",FileDescriptor.class).invoke(null,fd);
        }
        peer.close();
    }
    static void serve() {
        LocalServerSocket server=null;
        try {
            server=new LocalServerSocket("ev6.bluetooth.work."+android.os.Process.myPid());
            while(!broken) {
                LocalSocket peer=server.accept();
                try {
                    peer.setSoTimeout(1500);if(peer.getPeerCredentials().getUid()!=0)continue;
                    String request=read(peer.getInputStream()),response="EV6BT1|REJECTED";
                    Handler h=(Handler)call(machine,"getHandler");
                    if(request.equals("Q"))response=query(h);
                    else {
                        String[] f=request.split(" ");
                        if(f.length==4 && f[0].equals("C") && f[1].equals(receiver)
                            && f[2].matches("[1-9][0-9]{0,18}") && f[3].matches("([0-9A-F]{2}:){5}[0-9A-F]{2}")) {
                            Ticket t=new Ticket(Long.parseLong(f[2]),f[3]);Message msg=Message.obtain();msg.what=CANCEL;msg.obj=t;
                            method(machine.getClass(),"sendMessage",Message.class).invoke(machine,msg);
                            t.done.await(1400,TimeUnit.MILLISECONDS);response="EV6BT1|"+t.result;
                        }
                    }
                    peer.getOutputStream().write((response+"\n").getBytes("US-ASCII"));peer.getOutputStream().flush();
                }catch(Exception ignored){}
                finally {try{closeAccepted(peer);}catch(Exception ignored){}}
            }
        }catch(Exception unavailable){broken=true;}
        finally {if(server!=null)try{server.close();}catch(Exception ignored){}}
    }
}
