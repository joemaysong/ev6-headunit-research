import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import java.io.*;

/** Explicit root maintenance: use the same app command path as the native row. */
public final class ControllerAdmin {
    public static void main(String[] args) throws Exception {
        if(android.os.Process.myUid()!=0 || args.length!=1 || !args[0].equals("pause-current"))throw new IOException("root-and-pause-current-required");
        android.os.Looper.prepareMainLooper();
        if(!new File(CarPlayController.BUTTON,"controller-ready").exists())throw new IOException("controller-not-ready");
        Object pp=CarPlayController.service("phoneprojection","android.phoneprojection.IPPManager$Stub");
        String active=CarPlayController.validTarget((String)CarPlayController.call(pp,"android.phoneprojection.IPPManager","getCPConnectedBTAddress",new Class<?>[0]));
        String owned=CarPlayController.validTarget(CarPlayController.read(new File(CarPlayController.STATE,"current-target"),32));
        if(active.length()==0 || !active.equals(owned))throw new IOException("current-device-not-owned");
        Intent intent=new Intent();intent.setClassName("org.ev6.carplaybutton","org.ev6.research.CpCommand");
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothAdapter.getDefaultAdapter().getRemoteDevice(active));
        Object manager=CarPlayController.call(null,"android.app.ActivityManagerNative","getDefault",new Class<?>[0]);
        // Same Binder entry and null caller as KitKat's `am startservice`.
        Object started=CarPlayController.call(manager,"android.app.IActivityManager","startService",new Class<?>[]{Class.forName("android.app.IApplicationThread"),Intent.class,String.class,int.class},null,intent,null,0);
        if(!(started instanceof android.content.ComponentName) || !((android.content.ComponentName)started).getPackageName().equals("org.ev6.carplaybutton"))throw new IOException("button-service-unavailable");
        System.out.println("CURRENT_DEVICE_DISCONNECT_REQUESTED");
    }
}
