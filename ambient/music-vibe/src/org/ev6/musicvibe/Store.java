package org.ev6.musicvibe;
import java.io.*;
import android.content.*;
import android.os.*;
import android.util.AtomicFile;
import org.json.*;
final class Store {
 static File f(Context c,String n){return new File(c.getFilesDir(),n);}
 static void write(Context c,String n,String s)throws Exception {AtomicFile a=new AtomicFile(f(c,n));FileOutputStream out=a.startWrite();try{out.write(s.getBytes("UTF-8"));a.finishWrite(out);}catch(Exception e){a.failWrite(out);throw e;}}
 static String read(Context c,String n)throws Exception {FileInputStream in=new AtomicFile(f(c,n)).openRead();try{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] a=new byte[2048];int x;while((x=in.read(a))!=-1)b.write(a,0,x);return b.toString("UTF-8");}finally{in.close();}}
 static boolean armed(Context c){return f(c,"armed").exists();}
 static void disarm(Context c){f(c,"armed").delete();}
 static void beat(Context c)throws Exception {HeartbeatFile.write(f(c,"heartbeat"),SystemClock.elapsedRealtime());}
 static long heartbeat(Context c){try{return HeartbeatFile.read(f(c,"heartbeat"));}catch(Exception e){Diag.event("heartbeat_read_error",e.toString());return 0;}}
 static void status(Context c,String s){try{write(c,"status",s);}catch(Exception e){android.util.Log.e("MusicVibe",s,e);}}
 static Lights baseline(Context c)throws Exception {return Lights.from(new JSONObject(read(c,"restore.json")));}
 static void save(Context c,Lights s)throws Exception {if(f(c,"restore.json").exists())throw new Exception("Previous restore pending; refusing to overwrite backup");String j=s.json().put("saved_at_ms",System.currentTimeMillis()).toString(2);write(c,"restore.json",j);write(c,"last-backup.json",j);}
 static void restored(Context c)throws Exception {disarm(c);f(c,"restore.json").delete();write(c,"last-restored",Long.toString(System.currentTimeMillis()));}
 static String bootId(){try{BufferedReader r=new BufferedReader(new FileReader("/proc/sys/kernel/random/boot_id"));try{return r.readLine();}finally{r.close();}}catch(Exception e){return "boot-"+(System.currentTimeMillis()-SystemClock.elapsedRealtime())/10000;}}
 static boolean manualDisabled(Context c){try{return bootId().equals(read(c,"manual-stop-boot"));}catch(Exception e){return false;}}
 static void markManual(Context c){try{write(c,"manual-stop-boot",bootId());}catch(Exception e){android.util.Log.e("MusicVibe","manual marker failed",e);}disarm(c);}
 static void handedOff(Context c)throws Exception {disarm(c);f(c,"restore.json").delete();write(c,"last-manual-handoff",Long.toString(System.currentTimeMillis()));}
}
