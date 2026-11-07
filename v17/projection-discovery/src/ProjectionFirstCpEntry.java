import java.util.*;
/** Volatile display bridge over the expected BT-to-WiFi handoff. Never grants start or writes a type hint. */
public final class ProjectionFirstCpEntry {
 private static final class Seen {long positiveUntil,attemptUntil;}
 private final HashMap<String,Seen> entries=new HashMap<String,Seen>();
 public synchronized int display(String peer,int original,boolean conflict,boolean livePositive,String busyPeer,long busyUntil,long now){
  if(peer==null||!peer.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")||now<0)return original;
  if(conflict)return original;Seen s=entries.get(peer);
  if(livePositive){if(s==null){if(entries.size()>=32)prune(now);if(entries.size()>=32)return original;s=new Seen();entries.put(peer,s);}s.positiveUntil=now+10000;}
  if(s!=null&&peer.equals(busyPeer)&&busyUntil>now&&busyUntil<=now+180000&&(now<=s.positiveUntil||s.attemptUntil>now))s.attemptUntil=busyUntil;
  if(original!=0)return original;
  return livePositive||(s!=null&&peer.equals(busyPeer)&&busyUntil>now&&s.attemptUntil>now)?1:0;
 }
 private void prune(long now){java.util.Iterator<Seen> values=entries.values().iterator();while(values.hasNext()){Seen s=values.next();if(s.positiveUntil<now&&s.attemptUntil<now)values.remove();}}
 public synchronized void unpair(String peer){entries.remove(peer);}public synchronized void clear(){entries.clear();}
}
