package org.ev6.research;

import android.content.Context;
import android.content.BroadcastReceiver;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

/** Independent cleanup of owned resources; no synthetic session publication. */
public final class BackendCleanup {
    private BackendCleanup() {}
    static void failed(String operation,Exception failure) {
        android.util.Log.w("EV6BackendProbe",operation+":"+failure.getClass().getSimpleName());
    }
    static void unregister(Context owner,BroadcastReceiver receiver,String name) {
        if(receiver==null)return;
        try { owner.unregisterReceiver(receiver); }
        catch(Exception failure){failed(name,failure);}
    }
    public static void destroy(Context owner,Object delegate,BroadcastReceiver transport,BroadcastReceiver wifi) {
        // Preserve the previous normal transport-stop path, but isolate failure.
        try { NativeTransportGate.attachment(owner,false); }
        catch(Exception failure){failed("transport-stop",failure);}
        synchronized(BackendOwnership.class) {
            if(BackendOwnership.current(owner))BackendCallbacks.write(owner,"backend.ready","0");
            BackendOwnership.release(owner);
        }
        unregister(owner,transport,"transport-receiver-release");
        unregister(owner,wifi,"wifi-receiver-release");
        try { BackendRuntime.release(owner); }
        catch(Exception failure){failed("runtime-receiver-release",failure);}
        NativeTransportGate.release(owner);
        WifiReplyAudit.release(owner);
        // onCreate may have registered the OEM receiver before later setup
        // failed. Use this wrapper's Context, never the OEM static mContext
        // which may already belong to a successor instance.
        if(delegate!=null) {
            try {
                Class<?> type=delegate.getClass();Field receiver=null;
                while(type!=null) {
                    try{receiver=type.getDeclaredField("mIap2ServiceReceiver");break;}
                    catch(NoSuchFieldException absent){type=type.getSuperclass();}
                }
                if(receiver!=null) {
                    receiver.setAccessible(true);
                    unregister(owner,(BroadcastReceiver)receiver.get(delegate),"oem-receiver-release");
                }
            }catch(Exception failure){failed("oem-receiver-lookup",failure);}
        }
        // Stop only the delegate belonging to this Service instance. The
        // existing stopThread contract is retained; do not call a new native
        // disconnect or reset shared authorization/association metadata.
        if(delegate!=null) {
            try {
                Class<?> type=delegate.getClass();Method stop=null;
                while(type!=null) {
                    try { stop=type.getDeclaredMethod("stopThread");break; }
                    catch(NoSuchMethodException absent){type=type.getSuperclass();}
                }
                if(stop==null)throw new NoSuchMethodException("stopThread");
                stop.setAccessible(true);stop.invoke(delegate);
            }catch(Exception failure){failed("delegate-thread-release",failure);}
        }
    }
}
