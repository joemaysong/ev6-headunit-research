public final class BluetoothBoundaryProbe {
 public static void main(String[] args) throws Exception {
  android.os.Looper.prepareMainLooper();CarPlayController c=new CarPlayController();Object m=c.bt();
  if(m==null)throw new IllegalStateException("bluetooth-service-unavailable");
  String owner="com.broadcom.bt.hfdevice.hfbtcm.IHfDeviceBTCMService";
  for(String name:new String[]{"btcmGetCurrentState","btcmIsBusy","btcmGetCountConnectedDevices"})
   System.out.println(name.toUpperCase(java.util.Locale.US)+"="+CarPlayController.call(m,owner,name,new Class<?>[0]));
  for(String name:new String[]{"btcmGetConnectingDevice","btcmGetDisconnectingDevice","btcmGetConnectionCancelingDevice"})
   System.out.println(name.toUpperCase(java.util.Locale.US)+"_PRESENT="+(CarPlayController.call(m,owner,name,new Class<?>[0])!=null));
 }
}
