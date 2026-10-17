import java.util.*;

/** Explicit new pairing is distinct from an ordinary Bluetooth reconnect. */
public final class BondLifecycle {
    public final Properties seen=new Properties();
    private final Set<String> pending=new HashSet<String>();
    public static final class Event {
        public final long when,start;
        public final int state,previous;
        Event(long w,int s,int p,long b){when=w;state=s;previous=p;start=b;}
        public boolean newlyPaired(){return state==12 && previous==11;}
    }
    public static Event parse(String value) {
        if(value==null || !value.matches("[0-9]{1,18} (10|11|12) (10|11|12) [0-9]{1,18}"))return null;
        String[] parts=value.split(" ");
        try {
            long when=Long.parseLong(parts[0]),start=Long.parseLong(parts[3]);
            if(start>when)return null;
            return new Event(when,Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),start);
        }catch(NumberFormatException invalid){return null;}
    }
    public Event observe(String target,String value,boolean baseline,boolean active,long pausedAt) {
        if(target==null || !target.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}"))return null;
        Event event=parse(value);if(event==null || value.equals(seen.getProperty(target)))return null;
        Event prior=parse(seen.getProperty(target));
        if(prior!=null && event.when<prior.when)return null;
        if(!seen.containsKey(target) && seen.size()>=32)return null;
        seen.setProperty(target,value);pending.remove(target);
        if(!baseline && !active && event.newlyPaired() && event.when>pausedAt)pending.add(target);
        return event;
    }
    public boolean consumeSetup(String target){return pending.remove(target);}
    public void clearSetups(){pending.clear();}
}
