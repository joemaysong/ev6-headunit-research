package org.ev6.research;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;

/** Only invalidates local metadata for a root-verified inactive generation. */
public final class SessionMetadata {
    static String property(String name) throws Exception {
        return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,name,"");
    }
    static String read(String path,int limit) throws IOException {
        FileInputStream input=new FileInputStream(path);
        try {
            ByteArrayOutputStream output=new ByteArrayOutputStream();int value;
            while((value=input.read())!=-1) {if(output.size()>=limit)throw new IOException("metadata-size");output.write(value);}
            return new String(output.toByteArray(),"US-ASCII");
        }finally{input.close();}
    }
    static void write(File file,String value) throws IOException {
        AtomicFile atomic=new AtomicFile(file);FileOutputStream output=null;
        try{output=atomic.startWrite();output.write(value.getBytes("US-ASCII"));atomic.finishWrite(output);}
        catch(IOException failure){if(output!=null)atomic.failWrite(output);throw failure;}
    }
    public static void reconcile(Context owner) {
        try {
            String proof=property("sys.ev6.cp.idle");
            if(!proof.matches("[1-9][0-9]{0,7}:[1-9][0-9]{0,18}"))return;
            String prefix="/proc/"+proof.split(":")[0]+"/";
            String command=read(prefix+"cmdline",256).split("\u0000")[0];
            if(!SessionGeneration.matches(proof,command,read(prefix+"stat",4096),property("sys.ev6.cp.apready")))return;
            // No OEM broadcast, native authorization, radio or transport change.
            write(new File(owner.getFilesDir(),"session.state"),"0");
            write(new File(owner.getFilesDir(),"session.reconciled"),proof);
            android.util.Log.i("EV6BackendProbe","INACTIVE_RECEIVER_METADATA_RECONCILED");
        }catch(Exception failure){android.util.Log.w("EV6BackendProbe","INACTIVE_RECEIVER_METADATA_RECONCILE_REJECTED");}
    }
}
