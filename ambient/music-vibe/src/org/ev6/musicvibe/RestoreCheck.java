package org.ev6.musicvibe;
import android.content.Context;import android.os.SystemClock;
/** Confirm asynchronous MICOM writes with two fresh, consecutive matching samples. */
final class RestoreCheck {
 interface Reader {byte[] read()throws Exception;}
 static boolean await(Context c,Lights expected,Reader reader,long timeout)throws Exception {
  long deadline=SystemClock.elapsedRealtime()+timeout;int consecutive=0;
  Diag.event("restore_expected",expected.json().toString());
  while(SystemClock.elapsedRealtime()<deadline){
   if(Store.manualDisabled(c)||ColorMenuWatcher.colorSettingsOpen(c))return false;
   try{byte[] raw=reader.read();int fade=Vehicle.fade();Lights actual=Lights.decode(raw,fade);boolean match=expected.matches(raw)&&fade==expected.fade;
    Diag.event("restore_sample","match="+match+" actual="+actual.json().toString());
    consecutive=match?consecutive+1:0;if(consecutive>=2)return true;
   }catch(Exception e){consecutive=0;Diag.event("restore_sample_error",e.toString());}
   Thread.sleep(250);
  }
  return false;
 }
}
