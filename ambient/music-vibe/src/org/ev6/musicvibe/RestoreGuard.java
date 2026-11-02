package org.ev6.musicvibe;
import android.app.*;
import android.content.*;
import android.os.*;
public final class RestoreGuard extends Service {
 volatile boolean done;Thread thread;final Object lock=new Object();byte[] data;long generation;
 final BroadcastReceiver recv=new BroadcastReceiver(){public void onReceive(Context c,Intent i){byte[] b=i.getByteArrayExtra(Vehicle.SML_EXTRA);if(b!=null&&b.length==9)synchronized(lock){data=b.clone();generation++;lock.notifyAll();}}};
 public void onCreate(){super.onCreate();Diag.init(this,"guard");registerReceiver(recv,new IntentFilter(Vehicle.SML_ACTION));}
 public IBinder onBind(Intent i){return null;}
 public int onStartCommand(Intent i,int f,int id){Diag.event("guard_command","flags="+f+" id="+id);if(thread==null){thread=new Thread(new Runnable(){public void run(){watch();}},"AmbientRestore");thread.start();}return START_NOT_STICKY;}
 void watch(){long lastReport=0;try{Diag.event("guard_watch",Diag.health(this));
  while(!done&&Store.f(this,"restore.json").exists()){
   if(Store.manualDisabled(this)||ColorMenuWatcher.paletteOpen(this)){Diag.event("guard_manual_handoff",Diag.health(this));Store.markManual(this);Store.handedOff(this);break;}
   long hb=Store.heartbeat(this),now=SystemClock.elapsedRealtime();
   if(now-lastReport>=30000){Diag.event("guard_health",Diag.health(this));lastReport=now;}
   if(hb<=now&&now-hb<6000){Thread.sleep(1000);continue;}
   Diag.event("guard_trigger",Diag.health(this));Store.disarm(this);Lights base=Store.baseline(this);boolean restored=false;
   for(int n=0;n<3&&!restored;n++){
    try{if(Store.manualDisabled(this)||ColorMenuWatcher.paletteOpen(this)){Diag.event("guard_manual_handoff",Diag.health(this));Store.markManual(this);Store.handedOff(this);break;}Vehicle.restore(base);
     restored=RestoreCheck.await(this,base,new RestoreCheck.Reader(){public byte[] read()throws Exception{long gen;synchronized(lock){gen=generation;}Vehicle.request();long until=SystemClock.elapsedRealtime()+2500;synchronized(lock){while(generation==gen&&SystemClock.elapsedRealtime()<until)lock.wait(100);if(generation==gen)throw new Exception("No fresh guard light response");return data.clone();}}},4000);
    }catch(Exception e){Diag.event("guard_restore_error",android.util.Log.getStackTraceString(e));android.util.Log.e("MusicVibe","Protected restore retry",e);}if(!restored)Thread.sleep(500);
   }
   Diag.event("guard_restore_result","verified="+restored+" "+Diag.health(this));
   if(restored){Store.restored(this);Store.status(this,"Original lights restored and verified; breathing lights stopped");}
   else Store.status(this,"Restore unverified; use stop/restore action or recovery script; original backup retained");
   break;
  }
 }catch(Exception e){Diag.event("guard_error",android.util.Log.getStackTraceString(e));Store.status(this,"Restore guard error: "+e.getMessage()+"; backup retained");}finally{Diag.event("guard_exit",Diag.health(this));stopSelf();}}
 public void onDestroy(){Diag.event("guard_destroy",Diag.health(this));done=true;try{unregisterReceiver(recv);}catch(Exception e){}super.onDestroy();}
}
