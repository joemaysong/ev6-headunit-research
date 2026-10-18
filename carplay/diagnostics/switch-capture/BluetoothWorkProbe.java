import java.io.File;
public final class BluetoothWorkProbe {
 public static void main(String[] args) throws Exception {
  BluetoothWorkClient.Snapshot s;
  try{s=BluetoothWorkClient.read();}catch(Exception e){s=null;}
  System.out.println("BT_WORK_BRIDGE_READY="+(s!=null));if(s==null)return;
  String command=CarPlayController.read(new File(CarPlayController.BUTTON,"command"),128);String[] f=command.split(" ");
  String target=f.length==3?CarPlayController.validTarget(f[2]):"";
  System.out.println("BT_WORK_EPOCH="+s.epoch);System.out.println("BT_WORK_STATE="+s.state);
  System.out.println("BT_WORK_BUSY="+s.busy);System.out.println("BT_WORK_AUTOMATIC="+s.automatic);
  System.out.println("BT_PROFILES_EMPTY="+s.empty);System.out.println("BT_PROJECTION_INACTIVE="+s.inactive);
  System.out.println("BT_PROCESS_MATCH_LAST_SELECTION="+(target.length()>0 && target.equals(s.process)));
 }
}
