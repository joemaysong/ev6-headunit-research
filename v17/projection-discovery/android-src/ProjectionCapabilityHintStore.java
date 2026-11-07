import android.content.Context;
import android.content.SharedPreferences;
/** Own versioned UI hints only. Never reads OEM records or legacy capability preferences. */
public final class ProjectionCapabilityHintStore implements ProjectionDiscovery.HintStore {
 private final SharedPreferences preferences;
 public ProjectionCapabilityHintStore(Context owner){preferences=owner.getSharedPreferences("ev6_projection_hints_v1",Context.MODE_PRIVATE);}
 private String key(String address){if(address==null||!address.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))throw new IllegalArgumentException("address");return address.toUpperCase(java.util.Locale.US);}
 public synchronized java.util.Set<String> addresses(){java.util.HashSet<String> result=new java.util.HashSet<String>();for(String a:preferences.getAll().keySet())if(a.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))result.add(key(a));return result;}
 public synchronized int read(String address){
  try{String v=preferences.getString(key(address),"");return "PROTOCOL_CONFIRMED1|CP".equals(v)?1:"PROTOCOL_CONFIRMED1|AA".equals(v)?2:0;}catch(RuntimeException badRecord){return 0;}
 }
 public synchronized void write(String address,int type)throws Exception {
  if(type!=1&&type!=2)throw new IllegalArgumentException("type");
  if(!preferences.edit().putString(key(address),type==1?"PROTOCOL_CONFIRMED1|CP":"PROTOCOL_CONFIRMED1|AA").commit())throw new java.io.IOException("hint-write-failed");
 }
 public synchronized void remove(String address)throws Exception{if(!preferences.edit().remove(key(address)).commit())throw new java.io.IOException("hint-remove-failed");}
}
