import android.bluetooth.BluetoothDevice;
import java.io.*;

/** Inspect actual profile ownership without exporting device identifiers. */
public final class BtSelectionProbe {
    static void out(String key,Object value){System.out.println(key+"="+value);}
    static String address(Object value){return value instanceof BluetoothDevice?CarPlayController.validTarget(((BluetoothDevice)value).getAddress()):"";}
    public static void main(String[] args) throws Exception {
        android.os.Looper.prepareMainLooper();CarPlayController controller=new CarPlayController();
        String command=CarPlayController.read(new File(CarPlayController.BUTTON,"command"),128);
        String[] fields=command.split(" ");String target=fields.length==3?CarPlayController.validTarget(fields[2]):"";
        out("LAST_COMMAND_TARGET_PRESENT",target.length()>0);
        Object manager=controller.bt();out("OEM_MANAGER_PRESENT",manager!=null);if(manager==null)return;
        String owner="com.broadcom.bt.hfdevice.hfbtcm.IHfDeviceBTCMService";
        String hfp=controller.hfp();out("ANY_HFP_CONNECTED",hfp.length()>0);out("TARGET_HFP_CONNECTED",target.length()>0 && target.equals(hfp));
        boolean audio=false,otherAudio=false,listed=false;
        for(String name:new String[]{"btcmGetA2dpConnectedDevice1","btcmGetA2dpConnectedDevice2"}) {
            String peer=address(CarPlayController.call(manager,owner,name,new Class<?>[0]));
            audio|=target.length()>0 && target.equals(peer);otherAudio|=peer.length()>0 && !target.equals(peer);
        }
        for(String name:new String[]{"btcmGetConnectedDevice1","btcmGetConnectedDevice2"})listed|=target.length()>0 && target.equals(address(CarPlayController.call(manager,owner,name,new Class<?>[0])));
        out("TARGET_AUDIO_CONNECTED",audio);out("OTHER_AUDIO_CONNECTED",otherAudio);out("TARGET_ALREADY_ON_OEM_CONNECTED_LIST",listed);
        out("CONNECTED_DEVICE_COUNT",CarPlayController.call(manager,owner,"btcmGetCountConnectedDevices",new Class<?>[0]));
        out("OEM_STATE",CarPlayController.call(manager,owner,"btcmGetCurrentState",new Class<?>[0]));
        for(String name:new String[]{"btcmGetConnectingDevice","btcmGetDisconnectingDevice"}) {
            String peer=address(CarPlayController.call(manager,owner,name,new Class<?>[0]));
            out(name.toUpperCase(java.util.Locale.US)+"_PRESENT",peer.length()>0);
            out(name.toUpperCase(java.util.Locale.US)+"_TARGET",target.length()>0 && target.equals(peer));
        }
    }
}
