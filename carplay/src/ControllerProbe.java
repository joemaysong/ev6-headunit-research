import java.io.*;

/** Read-only output consists of booleans, counts, and internal status codes. */
public final class ControllerProbe {
    static void show(String key,Object value){System.out.println(key+"="+value);}
    public static void main(String[] args) throws Exception {
        android.os.Looper.prepareMainLooper();
        CarPlayController controller=new CarPlayController();
        controller.pp=CarPlayController.service("phoneprojection","android.phoneprojection.IPPManager$Stub");
        controller.wifi=CarPlayController.service("wifi","android.net.wifi.IWifiManager$Stub");
        String target=CarPlayController.validTarget(CarPlayController.read(new File(CarPlayController.STATE,"current-target"),32));
        if(target.length()==0)target=CarPlayController.validTarget(CarPlayController.prop("sys.ev6.cp.busy"));
        if(target.length()==0)target=CarPlayController.validTarget(CarPlayController.read(new File(CarPlayController.STATE,"migration-target"),32));
        controller.policy.target=target;
        show("CONTROLLER_REVISION",14);
        BluetoothWorkClient.Snapshot bluetooth=controller.readBluetoothWork();
        show("BLUETOOTH_WORK_BRIDGE_READY",bluetooth!=null);
        if(bluetooth!=null) {show("BLUETOOTH_WORK_STATE",bluetooth.state);show("BLUETOOTH_WORK_BUSY",bluetooth.busy);show("BLUETOOTH_PROFILES_EMPTY",bluetooth.empty);}
        String status=CarPlayController.read(new File(CarPlayController.STATE,"status"),128);
        if(status.matches("[a-z0-9_-]{1,100}"))show("CONTROLLER_STATUS",status);
        show("MANUAL_PAUSED",new File(CarPlayController.STATE,"manual-pause").exists());
        String pid=CarPlayController.read(new File(CarPlayController.STATE,"controller.pid"),16);
        show("CONTROLLER_PROCESS_PRESENT",pid.matches("[1-9][0-9]*") && new File("/proc/"+pid).exists());
        show("BUTTON_READY",new File(CarPlayController.BUTTON,"controller-ready").exists());
        show("PROJECTION_AP_OWNER",new File(CarPlayController.STATE,"ap-owner").exists());
        show("SAVED_CLIENT_PREFERENCE",new File(CarPlayController.STATE,"saved-client-state").exists());
        String connectedHfp=controller.hfp();
        show("ANY_HFP_CONNECTED",connectedHfp.length()>0);
        show("HFP_SELECTED_TARGET_MATCH",target.length()>0 && target.equals(connectedHfp));
        Object record=controller.pp("getWLDeviceInfo",new Class<?>[]{String.class},target);
        show("NATIVE_SELECTED_RECORD_PRESENT",record!=null);
        String station=controller.wifiState("getWifiApEnabledState")==13?controller.singleStation():"";
        show("SINGLE_AP_STATION",station.length()>0);
        show("AP_STATION_COUNT",controller.stationCount);
        show("STATION_QUERY_OK",!controller.stationQueryUnavailable);
        String epoch=controller.pairingEpoch();
        show("NATIVE_RECORD_PEER_FIELD_POPULATED",record!=null && CarPlayController.call(record,"android.phoneprojection.WLDeviceInfo","getWLDevMacAddr",new Class<?>[0])!=null && ((String)CarPlayController.call(record,"android.phoneprojection.WLDeviceInfo","getWLDevMacAddr",new Class<?>[0])).length()>0);
        show("NATIVE_ELIGIBILITY_READY",epoch.length()>0);
        show("WIFI_CLIENT_STATE",controller.wifiState("getWifiEnabledState"));
        show("WIFI_AP_STATE",controller.wifiState("getWifiApEnabledState"));
        show("WIFI_CLIENT_PERSISTED",CarPlayController.call(controller.wifi,"android.net.wifi.IWifiManager","getPersistedWifiEnabled",new Class<?>[0]));
        int process=NativeSession.findPid("com.daudio.app.dipo");
        if(process!=0) {
            NativeSession session=new NativeSession(process);
            try{show("NATIVE_READY",session.ready());show("NATIVE_ACTIVE",session.active());show("NATIVE_CONTROL_OWNER_PRESENT",session.hasControlOwner());}finally{session.close();}
        }
        String published=CarPlayController.read(new File(CarPlayController.BACKEND,"session.state"),8);
        show("BACKEND_SESSION_PUBLISHED",published.equals("1"));
        String active=(String)controller.pp("getCPConnectedBTAddress",new Class<?>[0]);
        show("OEM_CONNECTED_ADDRESS_PRESENT",active!=null && active.length()>0);
        show("ACTIVE_TARGET_MATCH",target.length()>0 && target.equalsIgnoreCase(active));
        String busy=CarPlayController.prop("sys.ev6.cp.busy");
        show("UI_PROGRESS_ACTIVE",busy.length()>0);
    }
}
