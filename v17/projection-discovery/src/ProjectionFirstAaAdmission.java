import java.util.*;
/** A fresh pairing permits one ordinary OEM setup attempt, never capability memory. */
public final class ProjectionFirstAaAdmission {
 private final Set<String> seen=new HashSet<String>();
 private final Map<String,Long> pending=new HashMap<String,Long>();
 public synchronized void bond(String peer,int prior,int state,long now){
  if(peer==null||!peer.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")||now<0)return;
  peer=peer.toUpperCase(Locale.US);
  if(state==10){pending.remove(peer);seen.remove(peer);return;}
  if(state==12&&prior==11&&!seen.contains(peer)){
   Iterator<Long> it=pending.values().iterator();while(it.hasNext())if(it.next().longValue()<now)it.remove();
   if(seen.size()<32){seen.add(peer);pending.put(peer,Long.valueOf(now+120000L));}
  }
 }
 public synchronized boolean claim(String peer,long now,boolean idle,boolean paused){
  if(peer==null)return false;Long until=pending.remove(peer.toUpperCase(Locale.US));
  return until!=null&&now>=until.longValue()-120000L&&now<until.longValue()&&idle&&!paused;
 }
 public synchronized void clear(){pending.clear();seen.clear();}
}
