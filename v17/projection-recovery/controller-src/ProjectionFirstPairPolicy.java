import java.util.HashMap;
/** Volatile first-setup eligibility, distinct from remembered type and reconnect permission. */
public final class ProjectionFirstPairPolicy {
 private final HashMap<String,Long> pending=new HashMap<String,Long>();
 // Local setup deadline, not an Apple/Google protocol timeout. Never survives process restart.
 static final long WINDOW=120000L;
 public void observed(String peer,boolean newlyPaired,boolean baseline,boolean idle,long when,long now,long stoppedAt){
  pending.remove(peer);
  if(peer==null||!peer.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}"))return;
  if(newlyPaired&&!baseline&&idle&&when>stoppedAt&&when<=now&&now-when<WINDOW&&pending.size()<32)pending.put(peer,Long.valueOf(when+WINDOW));
 }
 public long deadline(String peer){Long value=pending.get(peer);return value==null?0:value.longValue();}
 public boolean current(String peer,long until,long now){return until>now&&deadline(peer)==until;}
 public boolean pending(){return !pending.isEmpty();}
 /** A reserved probe owns its eligibility until its explicit terminal path.
  * This read is for NEW admission; it must not cancel the in-flight owner. */
 public boolean eligible(String peer,long now,boolean bonded,boolean idle,boolean probing){
  if(probing)return false;
  return eligible(peer,now,bonded,idle);
 }
 public boolean eligible(String peer,long now,boolean bonded,boolean idle){
  java.util.Iterator<Long> values=pending.values().iterator();while(values.hasNext())if(now>=values.next().longValue())values.remove();
  Long until=pending.get(peer);
  if(!idle)return false;
  if(until==null)return false;
  if(!bonded||now>=until.longValue()){pending.remove(peer);return false;}
  return true;
 }
 public void consumed(String peer){pending.remove(peer);}
 public void clear(){pending.clear();}
}
