import java.io.*;
import java.util.*;

/** Selected-device DNS and neighbor validation only; never sends control HTTP. */
public final class MdnsProbe {
 public static void main(String[] args) throws Exception {
  android.os.Looper.prepareMainLooper();CarPlayController controller=new CarPlayController();
  String target=controller.hfp();
  if(target.length()==0)target=CarPlayController.validTarget(CarPlayController.read(new File(CarPlayController.STATE,"current-target"),32));
  if(target.length()==0){System.out.println("MDNS_PROBE_SELECTED_TARGET=false");return;}
  Properties known=new Properties();String saved=CarPlayController.read(new File(CarPlayController.STATE,"known-peers"),8192);
  known.load(new ByteArrayInputStream(saved.getBytes("US-ASCII")));Set<String> others=new HashSet<String>();
  for(String other:known.stringPropertyNames())if(!other.equals(target))others.add(known.getProperty(other));
  CarPlayDiscovery browser=new CarPlayDiscovery(target,known.getProperty(target,""),others);
  try {
   boolean matched=false;long until=System.nanoTime()+4000000000L;
   while(System.nanoTime()<until && !matched)matched=browser.poll()!=null;
   System.out.println("MDNS_PROBE_SELECTED_IDENTITY_AND_STATION="+matched);
   System.out.println("MDNS_PROBE_STAGE="+browser.diagnosis);
   System.out.println("MDNS_PACKETS="+browser.received);
   System.out.println("MDNS_IDENTITY_MATCHES="+browser.identityMatches);
   System.out.println("MDNS_NEIGHBOR_MATCHES="+browser.neighborMatches);
   System.out.println("CARPLAY_CONTROL_REQUEST_SENT=false");
  }finally{browser.close();}
 }
}
