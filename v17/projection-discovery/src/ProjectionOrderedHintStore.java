import java.util.LinkedHashMap;
import java.util.Map;
/** One ordered writer. Pending removals supersede older writes, including retry failures. */
public final class ProjectionOrderedHintStore implements ProjectionDiscovery.HintStore {
 public interface Scheduler {void later(Runnable work,long delayMillis);}
 private static final class Value {final int type;Value(int t){type=t;}}
 private final ProjectionDiscovery.HintStore disk;private final Scheduler scheduler;
 private final LinkedHashMap<String,Value> pending=new LinkedHashMap<String,Value>();
 private boolean scheduled;
 public ProjectionOrderedHintStore(ProjectionDiscovery.HintStore d,Scheduler s){if(d==null||s==null)throw new IllegalArgumentException();disk=d;scheduler=s;}
 private String key(String a){if(a==null||!a.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))throw new IllegalArgumentException("address");return a.toUpperCase(java.util.Locale.US);}
 public int read(String a)throws Exception{String k=key(a);synchronized(this){Value v=pending.get(k);if(v!=null)return v.type;}return disk.read(k);}
 public void write(String a,int type){if(type!=1&&type!=2)throw new IllegalArgumentException("type");enqueue(key(a),type);}
 public void remove(String a){enqueue(key(a),0);}
 private synchronized void enqueue(String k,int type){pending.put(k,new Value(type));if(!scheduled){scheduled=true;scheduler.later(writer,0);}}
 private final Runnable writer=new Runnable(){public void run(){
  String key;Value value;synchronized(ProjectionOrderedHintStore.this){if(pending.isEmpty()){scheduled=false;return;}Map.Entry<String,Value> e=pending.entrySet().iterator().next();key=e.getKey();value=e.getValue();}
  boolean success=false;try{if(value.type==0)disk.remove(key);else disk.write(key,value.type);success=true;}catch(Exception retry){}
  synchronized(ProjectionOrderedHintStore.this){if(success&&pending.get(key)==value)pending.remove(key);if(pending.isEmpty())scheduled=false;else scheduler.later(this,success?0:1000);}
 }};
}
