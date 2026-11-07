import java.util.HashMap;
/** Actual OEM entry receipts. Returning from an entry is not projection completion. */
public final class ProjectionAaEntries {
 public interface Completion {boolean drained()throws Exception;}
 public interface Cancellation {boolean request()throws Exception;}
 public static final class Ticket {private final ProjectionAaEntries owner;private final String address;public final long sequence;private boolean returned;private Completion completion;private Cancellation cancellation;private Ticket(ProjectionAaEntries o,String a,long s){owner=o;address=a;sequence=s;}}
 private static final class Entry {Ticket ticket;long latest;int active;boolean latestReturned;}
 private final HashMap<String,Entry> entries=new HashMap<String,Entry>();private long serial;
 private String key(String a){if(a==null||!a.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))return null;return a.toUpperCase(java.util.Locale.US);}
 public synchronized Ticket enter(int type,String address,int connection){String k=key(address);if(type!=2||connection!=1||k==null)return null;Entry e=entries.get(k);if(e==null){if(entries.size()>=32)return null;e=new Entry();entries.put(k,e);}Ticket t=new Ticket(this,k,++serial);e.ticket=t;e.latest=t.sequence;e.latestReturned=false;e.active++;return t;}
 public synchronized void returned(Ticket t){if(t==null||t.owner!=this||t.returned)return;t.returned=true;Entry e=entries.get(t.address);if(e==null)return;e.active--;if(e.latest==t.sequence)e.latestReturned=true;}
 public synchronized void bind(Ticket t,Completion proof){if(t==null||t.owner!=this||t.returned||proof==null||t.completion!=null)return;t.completion=proof;}
 /** Bind only on the originating stack. A cancellation ACK is never completion. */
 public synchronized void bindCancellation(Ticket t,Cancellation action){if(t==null||t.owner!=this||t.returned||action==null||t.cancellation!=null)return;t.cancellation=action;}
 public boolean cancel(Ticket t)throws Exception{final Cancellation action;synchronized(this){if(t==null||t.owner!=this)return false;action=t.cancellation;}return action!=null&&action.request();}
 public synchronized long[] snapshot(String address){Entry e=entries.get(key(address));return e==null?new long[]{0,0,0,-1}:new long[]{e.latest,e.active,e.latestReturned?1:0,completion(e.ticket)};}
 public synchronized long[] snapshot(Ticket t){return t==null||t.owner!=this?new long[]{0,0,0,-1}:new long[]{t.sequence,t.returned?0:1,t.returned?1:0,completion(t)};}
 private int completion(Ticket t){if(t==null||!t.returned||t.completion==null)return -1;try{return t.completion.drained()?1:0;}catch(Exception unavailable){return -1;}}
}
