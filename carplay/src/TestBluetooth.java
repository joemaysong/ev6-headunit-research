import java.io.*;
/** Explicit root-only validation action; never used by the runtime policy. */
public final class TestBluetooth {
 public static void main(String[] args) throws Exception {
  if(android.os.Process.myUid()!=0 || args.length!=1 || !(args[0].equals("connect") || args[0].equals("disconnect")))throw new IOException("explicit-test-action-required");
  android.os.Looper.prepareMainLooper();
  int process=NativeSession.findPid("com.daudio.app.dipo");
  if(process!=0){NativeSession session=new NativeSession(process);try{if(session.active())throw new IOException("active-carplay-preserved");}finally{session.close();}}
  String target=CarPlayController.validTarget(CarPlayController.read(new File(CarPlayController.STATE,"migration-target"),32));
  CarPlayController controller=new CarPlayController();
  if(!controller.bonded(target))throw new IOException("verified-test-target-required");
  Object manager=controller.bt();if(manager==null)throw new IOException("bluetooth-unavailable");
  if(args[0].equals("disconnect") && !target.equals(controller.hfp()))throw new IOException("selected-hfp-mismatch");
  String method=args[0].equals("connect")?"btcmConnect":"btcmDisconnect";
  boolean accepted=(Boolean)CarPlayController.call(manager,"com.broadcom.bt.hfdevice.hfbtcm.IHfDeviceBTCMService",method,new Class<?>[]{String.class},target);
  System.out.println("EXPLICIT_BLUETOOTH_TEST_SUBMITTED="+accepted);
 }
}
