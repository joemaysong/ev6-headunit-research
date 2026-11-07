import android.content.Context;
import android.content.SharedPreferences;
/** Manual-end latch. Survives process and kernel restarts; only an explicit button or unpair clears it. */
public final class ProjectionAaPauseStore {
 private final SharedPreferences preferences;private final String boot;private final java.util.Set<String> faulted=new java.util.HashSet<String>();
 public ProjectionAaPauseStore(Context context)throws Exception{
  java.io.FileInputStream in=new java.io.FileInputStream("/proc/sys/kernel/random/boot_id");byte[] bytes=new byte[80];int n;try{n=in.read(bytes);}finally{in.close();}
  boot=n>0?new String(bytes,0,n,"US-ASCII").trim():"";if(!boot.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw new IllegalStateException("pause-boot");
  preferences=context.getSharedPreferences("ev6_aa_manual_pause_v1",0);
 }
 ProjectionAaPauseStore(SharedPreferences preferences,String boot){if(preferences==null||boot==null||!boot.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw new IllegalArgumentException();this.preferences=preferences;this.boot=boot;}
 private String key(String address){if(address==null||!address.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))throw new IllegalArgumentException("peer");return address.toUpperCase(java.util.Locale.US);}
 public synchronized boolean paused(String address){return faulted.contains(key(address))||preferences.getString(key(address),"").length()>0;}
 /** Called only for the broker's unique accepted explicit command before OEM effects. */
 public synchronized void manualIntent(String address,boolean disconnect)throws Exception{
  SharedPreferences.Editor edit=preferences.edit();if(disconnect)edit.putString(key(address),boot);else edit.remove(key(address));
  if(!edit.commit()){faulted.add(key(address));throw new java.io.IOException("pause-commit");}faulted.remove(key(address));
 }
 public synchronized void unpair(String address)throws Exception{if(!preferences.edit().remove(key(address)).commit()){faulted.add(key(address));throw new java.io.IOException("pause-remove");}faulted.remove(key(address));}
}
