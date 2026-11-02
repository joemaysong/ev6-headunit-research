package org.ev6.musicvibe;
import android.content.*;import android.os.*;import android.util.Log;import java.io.*;import org.json.*;
/** Bounded per-process journal; no audio capture or new permissions. */
final class Diag {
 static Context context;static String role="unknown";static volatile String operation="idle";static volatile long operationAt;
 static synchronized void init(Context c,String r){context=c.getApplicationContext();role=r;event("process_create","boot="+Store.bootId());final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){public void uncaughtException(Thread t,Throwable e){event("uncaught_exception",t.getName()+" "+Log.getStackTraceString(e));if(previous!=null)previous.uncaughtException(t,e);else android.os.Process.killProcess(android.os.Process.myPid());}});}
 static synchronized void event(String type,String detail){
  if(context==null)return;try{
   JSONObject j=new JSONObject().put("wall_ms",System.currentTimeMillis()).put("elapsed_ms",SystemClock.elapsedRealtime()).put("uptime_ms",SystemClock.uptimeMillis()).put("pid",android.os.Process.myPid()).put("role",role).put("thread",Thread.currentThread().getName()).put("event",type).put("detail",detail);
   File f=Store.f(context,"diag-"+role+".jsonl");if(f.length()>1048576){File old=new File(f.getPath()+".1");old.delete();if(!f.renameTo(old))throw new IOException("Log rotate failed");}
   FileOutputStream out=new FileOutputStream(f,true);try{out.write((j.toString()+"\n").getBytes("UTF-8"));}finally{out.close();}
  }catch(Exception e){Log.e("MusicVibe","Diagnostic write failed",e);}
 }
 static void start(String op){operation=op;operationAt=SystemClock.elapsedRealtime();event("operation_start",op);}
 static void end(String op,long start){event("operation_end",op+" duration_ms="+(SystemClock.elapsedRealtime()-start));operation="idle";}
 static String stack(Thread t){if(t==null)return "none";StringBuilder b=new StringBuilder(t.getName()+" "+t.getState());for(StackTraceElement x:t.getStackTrace())b.append("\n").append(x);return b.toString();}
 static String health(Context c){String owner="unknown";try{owner=Store.read(c,"owner-pid");}catch(Exception ignored){}long hb=Store.heartbeat(c);File f=Store.f(c,"heartbeat");Runtime rt=Runtime.getRuntime();return "owner_pid="+owner+" owner_exists="+new File("/proc/"+owner).exists()+" heartbeat="+hb+" age_ms="+(SystemClock.elapsedRealtime()-hb)+" file_exists="+f.exists()+" size="+f.length()+" armed="+Store.armed(c)+" restore="+Store.f(c,"restore.json").exists()+" top="+ColorMenuWatcher.top(c)+" op="+operation+" op_age_ms="+(SystemClock.elapsedRealtime()-operationAt)+" heap_used="+(rt.totalMemory()-rt.freeMemory());}
}
