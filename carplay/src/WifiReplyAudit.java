package org.ev6.research;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.lang.reflect.Method;

/** Opt-in audit: only equality results, never credentials or raw protocol data. */
public final class WifiReplyAudit {
    private static Context context;
    public static synchronized void init(Context value){context=value;}
    public static synchronized void release(Context value){if(context==value)context=null;}
    public static synchronized void ownerMessage(Context value,int code,byte[] bytes,int start,int end) {
        synchronized(BackendOwnership.class) {
        if(context!=value || !BackendOwnership.current(value))return;
        message("TRACE_TX_CSM=0x",code,bytes,start,end);
        }
    }
    public static synchronized void message(String direction,int code,byte[] bytes,int start,int end) {
        Context owner=context;
        if(owner==null || code!=0x5703 || !"TRACE_TX_CSM=0x".equals(direction)
                || !new File(owner.getFilesDir(),"wifi-audit-enabled").exists())return;
        String text="valid=false";
        try {
            Object manager=owner.getSystemService("wifi");
            Method get=manager.getClass().getMethod("getWifiApConfiguration");
            Object config=get.invoke(manager);Class<?> type=config.getClass();
            String ssid=(String)type.getField("ConnectivitySSID").get(config);
            String key=(String)type.getField("ConnectivityPSK").get(config);
            String primary=(String)type.getField("preSharedKey").get(config);
            WifiReplyFields.Result result=WifiReplyFields.compare(bytes,start,end,ssid,key,primary);
            int state=(Integer)manager.getClass().getMethod("getWifiApState").invoke(manager);
            text="valid=true fields="+result.fields+" ssid_matches_connectivity="+result.ssid
                +" key_matches_connectivity="+result.key+" key_matches_primary="+result.primaryKey
                +" connectivity_key_is_hex_psk="+(key!=null && key.matches("[0-9a-fA-F]{64}"))
                +" connectivity_key_is_passphrase="+(key!=null && key.getBytes("UTF-8").length>=8 && key.getBytes("UTF-8").length<=63)
                +" ap_state="+state;
        }catch(Exception failure){text="valid=false failure="+failure.getClass().getSimpleName();}
        AtomicFile output=new AtomicFile(new File(owner.getFilesDir(),"wifi-reply-audit"));FileOutputStream stream=null;
        try {stream=output.startWrite();stream.write(("elapsed="+android.os.SystemClock.elapsedRealtime()+" "+text+"\n").getBytes("US-ASCII"));output.finishWrite(stream);}
        catch(IOException failure){if(stream!=null)output.failWrite(stream);}
    }
}
