package org.ev6.launchpad;
import android.app.*;import android.content.*;import android.os.*;import android.util.Log;
/** Boot receiver health check; root bridge owns passive key listening. */
public final class KnobService extends Service {
 public IBinder onBind(Intent i){return null;}public int onStartCommand(Intent i,int f,int id){new Thread(new Runnable(){public void run(){try{Root.call(KnobService.this,"pad-ping");}catch(Exception e){Log.e("EV6Launchpad","Bridge unavailable",e);}stopSelf();}}).start();return START_NOT_STICKY;}
}
