import java.util.HashMap;
/** Display recovery only: root certifies a captured waiter set after validated maintenance. */
public final class ProjectionRecoveryBarrier {
 public interface Clock {long now();}
 private final Clock clock;private final HashMap<String,Long> deadlines=new HashMap<String,Long>();
 public ProjectionRecoveryBarrier(){this(new Clock(){public long now(){return System.nanoTime()/1000000L;}});}
 public ProjectionRecoveryBarrier(Clock clock){this.clock=clock;}
 private void expire(){java.util.Iterator<String> it=deadlines.keySet().iterator();while(it.hasNext()){String id=it.next();if(clock.now()>=deadlines.get(id)){batches.remove(id);it.remove();}}}
 private static final class Waiter {boolean complete;}
 private final HashMap<String,Waiter> waiting=new HashMap<String,Waiter>();
 private final HashMap<String,HashMap<String,Waiter>> batches=new HashMap<String,HashMap<String,Waiter>>();
 private static boolean uuid(String x){return x!=null&&x.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
 public synchronized boolean register(String current,String prior,String id,String address,boolean disconnect){if(!uuid(current)||!uuid(prior)||current.equals(prior)||!uuid(id)||address==null||!address.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))throw new IllegalArgumentException("recovery-identity");String k=prior+"|"+id+"|"+address.toUpperCase(java.util.Locale.US)+"|"+disconnect;Waiter w=waiting.get(k);if(w==null){if(waiting.size()>=64){java.util.Iterator<Waiter> it=waiting.values().iterator();while(it.hasNext())if(it.next().complete)it.remove();}if(waiting.size()>=64)throw new IllegalStateException("recovery-capacity");w=new Waiter();waiting.put(k,w);}return w.complete;}
 public synchronized String capture(){expire();if(batches.size()>=8)throw new IllegalStateException("recovery-batches");String id=java.util.UUID.randomUUID().toString();HashMap<String,Waiter> batch=new HashMap<String,Waiter>();for(String k:waiting.keySet())if(!waiting.get(k).complete)batch.put(k,waiting.get(k));batches.put(id,batch);deadlines.put(id,clock.now()+60000L);return id;}
 public synchronized void discard(String id){batches.remove(id);deadlines.remove(id);}
 public synchronized int size(String id){expire();HashMap<String,Waiter> batch=batches.get(id);return batch==null?-1:batch.size();}
 public synchronized boolean certify(String id,boolean idle){return idle&&certify(id);}
 public synchronized boolean certify(String id){expire();deadlines.remove(id);HashMap<String,Waiter> batch=batches.remove(id);if(batch==null)return false;for(String k:batch.keySet())if(waiting.get(k)==batch.get(k))batch.get(k).complete=true;return true;}
}
