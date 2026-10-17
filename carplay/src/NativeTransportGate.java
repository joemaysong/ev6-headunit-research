package org.ev6.research;

import android.content.Context;
import android.os.IBinder;
import android.util.AtomicFile;
import java.io.*;

/** Event-driven AP readiness gate. No synthetic attachment, timer or consent. */
public final class NativeTransportGate {
    private static Context owner;
    private static TransportReadiness state;
    private static String published="";
    public static synchronized void init(Context context){owner=context;state=new TransportReadiness();published="";publish(false);}
    public static synchronized void release(Context context){
        if(owner!=context)return;
        owner=null;state=null;published="";
    }
    static Object call(Object receiver,Class<?> type,String name,Class<?>[] types,Object...args) throws Exception {
        return type.getMethod(name,types).invoke(receiver,args);
    }
    static boolean apReady() {
        try {
            String ready=(String)call(null,Class.forName("android.os.SystemProperties"),"get",new Class<?>[]{String.class,String.class},"sys.ev6.cp.apready","");
            if(!ready.equals("1"))return false;
            IBinder binder=(IBinder)call(null,Class.forName("android.os.ServiceManager"),"getService",new Class<?>[]{String.class},"wifi");
            Object wifi=call(null,Class.forName("android.net.wifi.IWifiManager$Stub"),"asInterface",new Class<?>[]{IBinder.class},binder);
            Class<?> type=Class.forName("android.net.wifi.IWifiManager");
            return (Integer)call(wifi,type,"getWifiApEnabledState",new Class<?>[0])==13
                && (Integer)call(wifi,type,"getWifiApMode",new Class<?>[0])==2;
        }catch(Exception unavailable){return false;}
    }
    public static synchronized void attachment(Context context,boolean present) {
        if(owner!=context || state==null || !BackendOwnership.current(context))return;
        boolean ready=apReady();dispatch(state.attachment(present,ready));publish(ready);
    }
    public static synchronized void ready(Context context) {
        if(owner!=context || state==null || !BackendOwnership.current(context))return;
        boolean ready=apReady();dispatch(state.ready(ready));publish(ready);
    }
    static void dispatch(int action) {
        if(action==TransportReadiness.NONE)return;
        // A swallowed delegate failure must not leave the gate started.
        // The acknowledgment covers invocation, not a completed native session.
        TransportDispatch.dispatch(state,owner,action);
    }
    static void publish(boolean ready) {
        if(owner==null || state==null)return;
        String values="attached="+state.attached+" started="+state.started+" ap_ready="+ready;
        if(values.equals(published))return;
        AtomicFile file=new AtomicFile(new File(owner.getFilesDir(),"transport-gate-state"));FileOutputStream output=null;
        try {
            output=file.startWrite();output.write(("elapsed="+android.os.SystemClock.elapsedRealtime()+" "+values+"\n").getBytes("US-ASCII"));
            file.finishWrite(output);published=values;
        }catch(IOException failure){if(output!=null)file.failWrite(output);}
    }
}
