import java.io.*;
import java.util.*;

/** Root-only local recovery; no Bluetooth pairing, credentials, or flashing. */
public final class RecoveryTool {
    static final File ORIGINAL=new File(CarPlayController.BASE,"original");
    static final String OWNER="android.phoneprojection.IPPManager";
    static Object pp;
    static Object projection(String method,Class<?>[] types,Object...args) throws Exception {
        return CarPlayController.call(pp,OWNER,method,types,args);
    }
    static void inactive() throws Exception {
        int pid=NativeSession.findPid("com.daudio.app.dipo");
        if(pid==0)return;
        NativeSession session=new NativeSession(pid);
        try {if(session.active())throw new IOException("active-session-preserved");}finally{session.close();}
    }
    static void disconnect() throws Exception {
        int pid=NativeSession.findPid("com.daudio.app.dipo");
        if(pid==0){System.out.println("GENUINE_SESSION_INACTIVE");return;}
        NativeSession session=new NativeSession(pid);
        try {
            if(session.active()) {
                String address=CarPlayController.validTarget((String)projection("getCPConnectedBTAddress",new Class<?>[0]));
                if(address.length()==0 || !session.hasControlOwner())throw new IOException("active-target-unavailable");
                projection("disconnectPP",new Class<?>[]{int.class,String.class,int.class},1,address,1);
                long deadline=android.os.SystemClock.elapsedRealtime()+15000;
                while(session.active() || CarPlayController.validTarget((String)projection("getCPConnectedBTAddress",new Class<?>[0])).length()>0) {
                    if(android.os.SystemClock.elapsedRealtime()>deadline)throw new IOException("finalize-deadline");
                    Thread.sleep(100);
                }
            }
            String remaining=(String)projection("getCPConnectedBTAddress",new Class<?>[0]);
            if(remaining!=null && remaining.length()>0)throw new IOException("inactive-oem-association-not-cleared");
            System.out.println("GENUINE_SESSION_INACTIVE");
        }finally{session.close();}
    }
    static void apConfiguration() throws Exception {
        inactive();
        Object wifi=CarPlayController.service("wifi","android.net.wifi.IWifiManager$Stub");
        Class<?> owner=Class.forName("android.net.wifi.WifiConfiguration");Object config=owner.newInstance();
        DataInputStream input=new DataInputStream(new FileInputStream(new File(ORIGINAL,"softap.conf")));
        try {
            if(input.readInt()!=3)throw new IOException("ap-format");
            owner.getField("SSID").set(config,input.readUTF());
            owner.getField("ConnectivitySSID").set(config,input.readUTF());
            int key=input.readInt();if(key<0 || key>31)throw new IOException("ap-key-format");
            ((BitSet)owner.getField("allowedKeyManagement").get(config)).set(key);
            if(key!=0){owner.getField("preSharedKey").set(config,input.readUTF());owner.getField("ConnectivityPSK").set(config,input.readUTF());}
            owner.getField("channel").setInt(config,input.readInt());
            owner.getField("connectivityChannel").setInt(config,input.readInt());
            owner.getField("hiddenSSID").setBoolean(config,input.readBoolean());
            owner.getField("carplayIE").set(config,input.readUTF());
            owner.getField("apMode").setInt(config,input.readInt());
            if(!(Boolean)owner.getMethod("isValid").invoke(config))throw new IOException("ap-invalid");
        }finally{input.close();}
        CarPlayController.call(wifi,"android.net.wifi.IWifiManager","setWifiApConfiguration",new Class<?>[]{owner},config);
        Object verified=CarPlayController.call(wifi,"android.net.wifi.IWifiManager","getWifiApConfiguration",new Class<?>[0]);
        android.os.Parcel expected=android.os.Parcel.obtain(),actual=android.os.Parcel.obtain();
        try {
            owner.getMethod("writeToParcel",android.os.Parcel.class,int.class).invoke(config,expected,0);
            owner.getMethod("writeToParcel",android.os.Parcel.class,int.class).invoke(verified,actual,0);
            if(!Arrays.equals(expected.marshall(),actual.marshall()))throw new IOException("ap-readback");
        }finally{expected.recycle();actual.recycle();}
        System.out.println("ORIGINAL_AP_CONFIGURATION_RESTORED");
    }
    public static void main(String[] args) {
        try {
            if(android.os.Process.myUid()!=0 || args.length!=1)throw new IOException("root-and-operation-required");
            android.os.Looper.prepareMainLooper();
            pp=CarPlayController.service("phoneprojection","android.phoneprojection.IPPManager$Stub");
            if(args[0].equals("disconnect"))disconnect();
            else if(args[0].equals("inactive")){inactive();System.out.println("GENUINE_SESSION_INACTIVE");}
            else if(args[0].equals("ap-original"))apConfiguration();
            else throw new IOException("unknown-operation");
        }catch(Exception failure){System.out.println("RECOVERY_STOPPED="+failure.getClass().getSimpleName());System.exit(1);}
    }
}
