package org.ev6.research;

import android.content.Context;
import android.content.Intent;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;

/** Serialize callback publication with Service ownership changes. */
public final class BackendCallbacks {
    private BackendCallbacks() {}
    public static void session(Context owner,boolean active) {
        synchronized(BackendOwnership.class) {
            if(!BackendOwnership.current(owner))return;
            write(owner,"session.state",active?"1":"0");
            android.util.Log.i("EV6BackendProbe",active?"GENUINE_WIRELESS_SESSION_CONNECTED":"GENUINE_WIRELESS_SESSION_DISCONNECTED");
        }
    }
    public static void wifi(Context owner,Intent intent) {
        synchronized(BackendOwnership.class) {
            if(!BackendOwnership.current(owner) || intent==null)return;
            write(owner,"event-state",intent.getAction()+":"+intent.getIntExtra("wifi_state",-1));
        }
    }
    public static void pairing(Context owner,String value) {
        synchronized(BackendOwnership.class) {
            if(!BackendOwnership.current(owner))return;
            AtomicFile file=new AtomicFile(new File(owner.getFilesDir(),"pairing.state"));
            FileOutputStream stream=null;
            try {
                stream=file.startWrite();
                stream.write(value.getBytes("US-ASCII"));file.finishWrite(stream);
                android.util.Log.i("EV6BackendProbe","PAIRING_STATE "+value);
            }catch(Exception failure){
                if(stream!=null)file.failWrite(stream);
                file.delete();
                android.util.Log.i("EV6BackendProbe","PAIRING_STATE_WRITE_FAILED");
            }
        }
    }
    static void write(Context context,String name,String value) {
        AtomicFile file=new AtomicFile(new File(context.getFilesDir(),name));
        FileOutputStream stream=null;
        try {
            stream=file.startWrite();stream.write(value.getBytes("US-ASCII"));file.finishWrite(stream);
        }catch(Exception failure){if(stream!=null)file.failWrite(stream);}
    }
}
