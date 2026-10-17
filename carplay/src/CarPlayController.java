import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.FileObserver;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import java.io.*;
import java.lang.reflect.*;
import java.nio.channels.*;
import java.security.MessageDigest;
import java.util.*;

/** On-unit controller. One event-coalescing worker, no Mac or timed retries. */
public final class CarPlayController {
    static final File BASE=new File("/data/local/ev6-carplay");
    static final File STATE=new File(BASE,"state");
    static final File ASSET=new File("/system/etc/ev6-carplay");
    static final File BACKEND=new File("/data/data/org.ev6.wirelesscarplay/files");
    static final File BUTTON=new File("/data/data/org.ev6.carplaybutton/files");
    static final String CLASSPATH="/system/framework/com.broadcom.bt.jar:/system/framework/com.tcc.iap2.jar:/system/etc/ev6-carplay/controller.jar";
    static final Object SIGNAL=new Object();
    static volatile boolean signaled;
    static volatile boolean registryStop;
    static CarPlayController instance;
    final ConnectionPolicy policy=new ConnectionPolicy();
    final OemAttemptOwner attemptOwner=new OemAttemptOwner();
    OemAttemptSnapshot preparationBaseline;
    final List<FileObserver> observers=new ArrayList<FileObserver>();
    final Properties knownPeers=new Properties();
    final BondLifecycle bonds=new BondLifecycle();
    final java.util.concurrent.ConcurrentHashMap<String,String> deviceEpochs=new java.util.concurrent.ConcurrentHashMap<String,String>();
    Object pp,bt,wifi;
    NativeSession nativeSession;
    CarPlayDiscovery discovery;
    String phase="idle",lastStatus="",lastHfp="",pendingTarget="",selectedPeer="";
    String lastAssessment="",pendingFailure="";
    boolean ap,btRequested,needUserStop,stationQueryUnavailable;
    boolean oemConnectSubmitted,btDeferred;
    long deadline,lastCommand,knownPeerDeadline,oemEndDeadline;
    long pausedAt;
    int backendPid;
    int stationCount=-1,lastStationCount=-2;
    FileChannel lockChannel;
    FileLock lock;
    FileOutputStream lockStream;

    public static void wake() { synchronized(SIGNAL) {signaled=true;SIGNAL.notifyAll();} }
    public static void userDisconnected(String address) {
        CarPlayController controller=instance;
        if(controller!=null && ConnectionPolicy.userStopApplies(controller.policy.target,validTarget(address),
            controller.policy.target.length()>0))controller.needUserStop=true;
        wake();
    }
    public static void deviceUpdated(String address) {
        CarPlayController controller=instance;if(controller==null)return;
        String target=validTarget(address);if(target.length()==0)return;
        try {
            String state=read(new File(BACKEND,"pairing.state"),512);
            java.util.regex.Matcher epoch=java.util.regex.Pattern.compile("^epoch=([1-9][0-9]*) ").matcher(state);
            if(epoch.find())controller.deviceEpochs.put(target,epoch.group(1));
        }catch(IOException ignored){}
        wake();
    }
    static Object call(Object object,String owner,String name,Class<?>[] types,Object...args) throws Exception {
        try {return Class.forName(owner).getMethod(name,types).invoke(object,args);}
        catch(InvocationTargetException failed) {
            Throwable cause=failed.getCause();if(cause instanceof Exception)throw (Exception)cause;throw failed;
        }
    }
    static String prop(String name) throws Exception {
        return (String)call(null,"android.os.SystemProperties","get",new Class<?>[]{String.class,String.class},name,"");
    }
    static void prop(String name,String value) throws Exception {
        call(null,"android.os.SystemProperties","set",new Class<?>[]{String.class,String.class},name,value);
    }
    static Object service(String name,String stub) throws Exception {
        IBinder binder=(IBinder)call(null,"android.os.ServiceManager","getService",new Class<?>[]{String.class},name);
        if(binder==null || !binder.isBinderAlive())throw new IOException("binder-not-ready");
        return call(null,stub,"asInterface",new Class<?>[]{IBinder.class},binder);
    }
    static String read(File file,int maximum) throws IOException {
        if(!file.exists())return "";
        if(file.length()>maximum)throw new IOException("state-size");
        FileInputStream input=new FileInputStream(file);
        try {ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[256];int size;
            while((size=input.read(buffer))>0) {if(bytes.size()+size>maximum)throw new IOException("state-size");bytes.write(buffer,0,size);}
            return new String(bytes.toByteArray(),"US-ASCII").trim();
        } finally {input.close();}
    }
    static void write(File file,String text) throws Exception {
        android.util.AtomicFile atomic=new android.util.AtomicFile(file);FileOutputStream output=null;
        try {output=atomic.startWrite();output.write(text.getBytes("US-ASCII"));atomic.finishWrite(output);}
        catch(Exception failure) {if(output!=null)atomic.failWrite(output);throw failure;}
    }
    static String exec(Map<String,String> env,long timeout,String...command) throws Exception {
        ProcessBuilder builder=new ProcessBuilder(command).redirectErrorStream(true);
        if(env!=null)builder.environment().putAll(env);
        final Process process=builder.start();final ByteArrayOutputStream output=new ByteArrayOutputStream();
        final boolean[] overflow={false};
        Thread reader=new Thread(new Runnable(){public void run(){try {
            InputStream input=process.getInputStream();byte[] bytes=new byte[1024];int size;
            while((size=input.read(bytes))>0) {synchronized(output) {if(output.size()+size<=32768)output.write(bytes,0,size);else overflow[0]=true;}}
            input.close();
        }catch(IOException ignored){}}},"CarPlay-command-output");reader.start();
        final int[] result={-1};
        Thread waiter=new Thread(new Runnable(){public void run(){try{result[0]=process.waitFor();}catch(InterruptedException ignored){}}},"CarPlay-command-wait");waiter.start();
        waiter.join(timeout);
        if(waiter.isAlive()){process.destroy();waiter.join(1000);throw new IOException("command-deadline");}
        reader.join(1000);
        if(result[0]!=0 || overflow[0] || reader.isAlive())throw new IOException("command-failed");
        synchronized(output) {return new String(output.toByteArray(),"US-ASCII");}
    }
    static String runTool(String name,String...args) throws Exception {
        String[] command=new String[3+args.length];command[0]="/system/bin/app_process";command[1]="/system/bin";command[2]=name;
        System.arraycopy(args,0,command,3,args.length);
        Map<String,String> env=new HashMap<String,String>();env.put("CLASSPATH",CLASSPATH);
        return exec(env,35000,command);
    }
    void mark(String status) throws Exception {
        if(!status.matches("[a-z0-9_-]{1,100}"))throw new IOException("status-schema");
        if(status.equals(lastStatus))return;
        lastStatus=status;write(new File(STATE,"status"),status);
        File log=new File(STATE,"events.log");
        if(log.length()>65536) {File previous=new File(STATE,"events.previous.log");previous.delete();if(!log.renameTo(previous))throw new IOException("log-rotate");}
        FileOutputStream output=new FileOutputStream(log,true);
        try {output.write((Long.toString(SystemClock.elapsedRealtime())+" "+status+"\n").getBytes("US-ASCII"));}finally{output.close();}
        System.out.println("CARPLAY_STATE="+status);
    }
    void busy(boolean value) throws Exception {
        // The original Settings ODEX reads only these transient properties.
        prop("sys.ev6.cp.until",value?Long.toString(SystemClock.elapsedRealtime()/1000+90):"0");
        prop("sys.ev6.cp.busy",value?policy.target:"");
        exec(null,4000,"/system/bin/am","broadcast","-a","getCarPlayConnectedDeviceAddress");
    }
    void observe(File directory,final Set<String> names) {
        FileObserver observer=new FileObserver(directory.getPath(),FileObserver.CLOSE_WRITE|FileObserver.MOVED_TO|FileObserver.CREATE|FileObserver.DELETE) {
            public void onEvent(int event,String path) {if(path!=null && names.contains(path))wake();}
        };
        observer.startWatching();observers.add(observer);
    }
    void checkFirmware() throws Exception {
        Map<String,String> hashes=new LinkedHashMap<String,String>();
        hashes.put("/system/lib/libCarPlayAndroid.so","ea0ca708efa1379d48e8bbffe3183f52");
        hashes.put("/system/lib/libCarPlayAudio.so","f7a37c4594e23c40c5fa72f811f884e8");
        hashes.put("/system/lib/libCarPlay.so","3104b375dc8d2d9c02aca7bec9f4db72");
        hashes.put("/system/lib/libCarPlayCore.so","acc35d6178084d8c4524ff14fb6eecbc");
        for(Map.Entry<String,String> item:hashes.entrySet()) {
            MessageDigest digest=MessageDigest.getInstance("MD5");FileInputStream file=new FileInputStream(item.getKey());
            try{byte[] bytes=new byte[16384];int size;while((size=file.read(bytes))>0)digest.update(bytes,0,size);}finally{file.close();}
            StringBuilder result=new StringBuilder();for(byte b:digest.digest())result.append(String.format(Locale.US,"%02x",b&255));
            if(!item.getValue().equals(result.toString()))throw new IOException("firmware-mismatch");
        }
    }
    Object pp(String method,Class<?>[] types,Object...args) throws Exception {return call(pp,"android.phoneprojection.IPPManager",method,types,args);}
    static String validTarget(String address) {
        if(address==null)return "";String result=address.toUpperCase(Locale.US);
        return BluetoothAdapter.checkBluetoothAddress(result)?result:"";
    }
    boolean bonded(String target) {
        return target.length()>0 && BluetoothAdapter.getDefaultAdapter().getRemoteDevice(target).getBondState()==BluetoothDevice.BOND_BONDED;
    }
    Object bt() throws Exception {
        if(bt!=null)return bt;
        Object manager=call(null,"android.app.ActivityManagerNative","getDefault",new Class<?>[0]);
        Intent intent=new Intent();intent.setClassName("com.android.bluetooth","com.broadcom.bt.service.btcmservice.HfDeviceBTCMService");
        IBinder binder=(IBinder)call(manager,"android.app.IActivityManager","peekService",new Class<?>[]{Intent.class,String.class},intent,null);
        if(binder==null)return null;
        bt=call(null,"com.broadcom.bt.hfdevice.hfbtcm.IHfDeviceBTCMService$Stub","asInterface",new Class<?>[]{IBinder.class},binder);return bt;
    }
    String hfp() throws Exception {
        Object manager=bt();if(manager==null)return "";
        BluetoothDevice device=(BluetoothDevice)call(manager,"com.broadcom.bt.hfdevice.hfbtcm.IHfDeviceBTCMService","btcmGetHfpConnectedDevice",new Class<?>[0]);
        return device==null?"":validTarget(device.getAddress());
    }
    boolean listedByBluetooth(Object manager,String target) throws Exception {
        for(String name:new String[]{"btcmGetConnectedDevice1","btcmGetConnectedDevice2"}) {
            BluetoothDevice connected=(BluetoothDevice)call(manager,"com.broadcom.bt.hfdevice.hfbtcm.IHfDeviceBTCMService",name,new Class<?>[0]);
            if(connected!=null && target.equalsIgnoreCase(connected.getAddress()))return true;
        }
        return false;
    }
    boolean requestBt() throws Exception {
        if(btRequested)return true;
        Object manager=bt();if(manager==null){fail("bluetooth-unavailable-user-action-required");return false;}
        if(policy.target.equals(hfp())){btRequested=true;btDeferred=false;mark("waiting-fresh-native-availability");return true;}
        String owner="com.broadcom.bt.hfdevice.hfbtcm.IHfDeviceBTCMService";
        boolean busy=(Boolean)call(manager,owner,"btcmIsBusy",new Class<?>[0]);
        if(busy){btDeferred=true;mark("waiting-oem-bluetooth-ready");return true;}
        // OEM btcmConnect rejects any device already present on its connected
        // list, including an A2DP-only device. The native UI uses the HFP-only
        // API to add the missing phone profile in that state.
        boolean partial=listedByBluetooth(manager,policy.target);
        btRequested=true;btDeferred=false;
        boolean accepted=(Boolean)call(manager,"com.broadcom.bt.hfdevice.hfbtcm.IHfDeviceBTCMService",partial?"btcmOnlyHfpConnect":"btcmConnect",new Class<?>[]{String.class},policy.target);
        write(new File(STATE,"last-bluetooth-request"),"elapsed="+SystemClock.elapsedRealtime()+" target_already_listed="+partial+" hfp_only="+partial+" accepted="+accepted);
        if(!accepted && !policy.target.equals(hfp())){fail("bluetooth-request-rejected-user-action-required");return false;}
        mark("selected-bluetooth-requested");return true;
    }
    void subnet(String operation) throws Exception {
        Map<String,String> env=new HashMap<String,String>();env.put("EV6_STATE",STATE.getPath());
        String result=exec(env,15000,"/system/bin/sh",new File(ASSET,"ap-subnet.sh").getPath(),operation);
        if(!result.contains(operation.equals("restore")?"AP_SUBNET_RESTORED":"AP_SUBNET_"))throw new IOException("subnet-not-verified");
    }
    void networkFirewall(String operation) throws Exception {
        Map<String,String> env=new HashMap<String,String>();env.put("EV6_STATE",STATE.getPath());
        String result=exec(env,5000,"/system/bin/sh",new File(ASSET,"ap-firewall.sh").getPath(),operation);
        if(operation.equals("start")) {
            if(!result.contains("AP_FIREWALL_READY_REPLY_ONLY"))throw new IOException("ap-reply-rule-not-ready");
        } else if(!result.contains("AP_FIREWALL_RESTORED") && !result.contains("AP_FIREWALL_ABSENT")) {
            throw new IOException("ap-reply-rule-not-restored");
        }
    }
    int wifiState(String name) throws Exception {return (Integer)call(wifi,"android.net.wifi.IWifiManager",name,new Class<?>[0]);}
    void networkReady() throws Exception {
        if(wifiState("getWifiApEnabledState")!=13 || wifiState("getWifiApMode")!=2)throw new IOException("projection-network-not-ready");
        networkFirewall("start");
        prop("sys.ev6.cp.apready","1");
        exec(null,10000,"/system/bin/am","startservice","-n","org.ev6.wirelesscarplay/org.ev6.research.ProbeService","-a","org.ev6.research.AP_READY");
    }
    void startAp() throws Exception {
        if(ap){networkReady();return;}
        // The OEM persists projection AP across restart. Keep ownership and
        // the saved client preference until our normal stop has completed.
        if(wifiState("getWifiApEnabledState")!=11 || new File(STATE,"saved-client-state").exists())throw new IOException("ap-other-owner-or-recovery-required");
        write(new File(STATE,"ap-owner"),read(new File(STATE,"boot-id"),128));
        String result=runTool("OemApSwitch","start",new File(STATE,"saved-client-state").getPath(),new File(BACKEND,"event-state").getPath());
        if(!result.contains("OEM_SWITCH_COMPLETE"))throw new IOException("ap-start-unverified");
        ap=true;subnet("start");networkReady();
    }
    void restoreClient() throws Exception {
        if(!originalCleanupReady())throw new IOException("oem-work-blocks-client-restoration");
        if(wifiState("getWifiApEnabledState")!=11 && !new File(STATE,"ap-owner").exists())
            throw new IOException("unowned-ap-preserved");
        prop("sys.ev6.cp.apready","0");
        networkFirewall("restore");
        if(new File(STATE,"ap-subnet.original").exists())subnet("restore");
        if(new File(STATE,"saved-client-state").exists()) {
            String result=runTool("OemApSwitch","stop",new File(STATE,"saved-client-state").getPath(),new File(BACKEND,"event-state").getPath());
            if(!result.contains("OEM_SWITCH_COMPLETE"))throw new IOException("ap-stop-unverified");
        }
        ap=false;
        if(wifiState("getWifiApEnabledState")!=11)throw new IOException("ap-other-owner");
        if(wifiState("getWifiEnabledState")!=3 || !(Boolean)call(wifi,"android.net.wifi.IWifiManager","getPersistedWifiEnabled",new Class<?>[0])) {
            if(!runTool("WifiClientPolicy").contains("WIFI_CLIENT_ENABLED_AND_PERSISTED"))throw new IOException("client-policy-unverified");
        }
        new File(STATE,"ap-owner").delete();
    }
    static Set<String> associatedStations() throws Exception {
        String output=exec(null,4000,new File(ASSET,"wl-stations").getPath());
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}").matcher(output);
        Set<String> result=new HashSet<String>();while(matcher.find())result.add(matcher.group().toUpperCase(Locale.US));
        if(result.size()>32)throw new IOException("station-list-size");
        return result;
    }
    Set<String> stations() throws Exception {
        try {Set<String> result=associatedStations();stationQueryUnavailable=false;stationCount=result.size();return result;}
        catch(IOException unavailable){stationQueryUnavailable=true;stationCount=-1;return Collections.emptySet();}
    }
    String singleStation() throws Exception {
        Set<String> result=stations();
        return result.size()==1?result.iterator().next():"";
    }
    void closeDiscovery(){if(discovery!=null){discovery.close();discovery=null;}}
    OemAttemptSnapshot originalObservation() {
        try{return OemAttemptClient.read(nativeSession);}
        catch(Exception unavailable){return null;} // IPC outage never becomes a completion acknowledgement.
    }
    boolean backendSessionActive() throws Exception {
        return !read(new File(BACKEND,"session.state"),8).equals("0");
    }
    boolean originalCleanupReady() throws Exception {
        OemAttemptSnapshot snapshot=originalObservation();
        boolean nativeActive=nativeSession.active();
        boolean backendActive=backendSessionActive();
        String address=(String)pp("getCPConnectedBTAddress",new Class<?>[0]);
        if(nativeActive || backendActive || !ConnectionPolicy.originalAssociationCleared(address))return false;
        if(!attemptOwner.owned())return OemAttemptOwner.unownedCleanupReady(snapshot,policy.paused)
            || (!oemConnectSubmitted && OemAttemptOwner.preparationCleanupReady(preparationBaseline,snapshot));
        return attemptOwner.canRelease(snapshot,false,false,address)
            || attemptOwner.canReleasePreflight(snapshot,false,false,address)
            || attemptOwner.canReleaseStopped(snapshot,false,false,address,policy.paused);
    }
    boolean retireOriginalAttempt() throws Exception {
        OemAttemptSnapshot snapshot=originalObservation();
        boolean active=nativeSession.active(),published=backendSessionActive();
        String address=(String)pp("getCPConnectedBTAddress",new Class<?>[0]);
        if(active || published || !ConnectionPolicy.originalAssociationCleared(address))return false;
        if(!attemptOwner.owned())return OemAttemptOwner.unownedCleanupReady(snapshot,policy.paused)
            || (!oemConnectSubmitted && OemAttemptOwner.preparationCleanupReady(preparationBaseline,snapshot));
        if(attemptOwner.canRelease(snapshot,active,published,address))attemptOwner.released(snapshot,active,published,address);
        else if(attemptOwner.canReleasePreflight(snapshot,active,published,address))attemptOwner.releasedPreflight(snapshot,active,published,address);
        else if(attemptOwner.canReleaseStopped(snapshot,active,published,address,policy.paused))attemptOwner.releasedStopped(snapshot,active,published,address,policy.paused);
        else return false;
        return true;
    }
    void begin(String target,boolean manual) throws Exception {
        if(nativeSession.active()) {mark("active-session-blocks-new-request");return;}
        OemAttemptSnapshot baseline=originalObservation();
        if(attemptOwner.owned() || !OemAttemptOwner.submissionReady(baseline)) {
            if(manual)pendingTarget=target;
            phase="settling-original";mark("waiting-original-work-before-new-selection");return;
        }
        if(!bonded(target) || !policy.begin(target,manual))return;
        preparationBaseline=baseline;
        closeDiscovery();
        new File(STATE,"manual-pause").delete();
        phase="preparing";btRequested=false;selectedPeer="";
        oemConnectSubmitted=false;btDeferred=false;oemEndDeadline=0;pendingFailure="";
        deadline=SystemClock.elapsedRealtime()+90000;
        busy(true);mark(manual?"manual-connect-preparing":"bluetooth-auto-connect-preparing");
        startAp();phase="waiting-native";knownPeerDeadline=SystemClock.elapsedRealtime()+4000;
        Object manager=bt();
        boolean missingPhone=manager!=null && listedByBluetooth(manager,target) && !target.equals(hfp());
        if(!manual || knownPeers.getProperty(target)==null || missingPhone)requestBt();
    }
    String pairingEpoch() throws Exception {
        String state=read(new File(BACKEND,"pairing.state"),512);
        java.util.regex.Matcher match=java.util.regex.Pattern.compile("epoch=([1-9][0-9]*) auth=true identified=true transport=true status=1 request=(true|false) reply=(true|false)").matcher(state);
        if(!match.matches())return "";
        return match.group(1);
    }
    boolean nativeTargetReady(String epoch) throws Exception {
        Object record=pp("getWLDeviceInfo",new Class<?>[]{String.class},policy.target);
        if(record==null)return false;
        String target=(String)call(record,"android.phoneprojection.WLDeviceInfo","getWLDevBtAddr",new Class<?>[0]);
        boolean recordMatch=policy.target.equalsIgnoreCase(target);
        boolean selectedHfp=epoch.length()>0 && policy.target.equals(hfp());
        boolean ready=ConnectionPolicy.nativeIdentityReady(epoch,deviceEpochs.get(policy.target),recordMatch,selectedHfp);
        // The native record callback can precede the backend file publish.
        // A fully authenticated native epoch plus the selected live HFP and
        // matching native record binds that epoch without requiring callback
        // ordering. Retain it when Bluetooth later hands over to Wi-Fi.
        if(ready && selectedHfp)deviceEpochs.put(policy.target,epoch);
        return ready;
    }
    void savePeer() throws Exception {
        if(selectedPeer.length()==0)return;
        // A peer is learned only after the genuine native session has begun.
        knownPeers.setProperty(policy.target,selectedPeer);
        storeProperties(new File(STATE,"known-peers"),knownPeers);
    }
    static void storeProperties(File file,Properties values) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();values.store(output,"Private device lifecycle; no credentials");
        write(file,new String(output.toByteArray(),"US-ASCII"));
    }
    void bondEvents(boolean baseline) throws Exception {
        String text=read(new File(BACKEND,"bond.state"),8192);if(text.length()==0)return;
        Properties values=new Properties();values.load(new ByteArrayInputStream(text.getBytes("US-ASCII")));
        if(!read(new File(STATE,"boot-id"),128).equals(values.getProperty("boot")))return;
        if(values.size()>33)throw new IOException("bond-state-size");
        boolean changed=false,peersChanged=false,active=nativeSession.active();
        for(String target:values.stringPropertyNames()) {
            BondLifecycle.Event event=bonds.observe(target,values.getProperty(target),baseline,active,pausedAt);
            if(event==null)continue;changed=true;
            // A completed new bond must not reuse a Wi-Fi peer learned under
            // the removed pairing. Preserve the validated native-record/HFP
            // reconciliation: the OEM may reuse a record without a new callback.
            if(event.state!=12 || event.newlyPaired()) {
                peersChanged|=knownPeers.remove(target)!=null;
                deviceEpochs.remove(target);
                if(target.equals(pendingTarget) && event.state==10)pendingTarget="";
                if(event.state==10 && ConnectionPolicy.userStopApplies(policy.target,target,policy.connected))needUserStop=true;
            }
        }
        if(active)bonds.clearSetups(); // Pairing another phone never switches an active session.
        if(peersChanged)storeProperties(new File(STATE,"known-peers"),knownPeers);
        if(changed)storeProperties(new File(STATE,"bond-ack"),bonds.seen);
    }
    void started() throws Exception {
        String address=(String)pp("getCPConnectedBTAddress",new Class<?>[0]);
        if(!ConnectionPolicy.oemSessionReady(nativeSession.active(),read(new File(BACKEND,"session.state"),8).equals("1"),
                nativeSession.hasControlOwner(),policy.target,address))return;
        if(attemptOwner.owned())attemptOwner.observeSession(originalObservation(),true,true,true,address);
        preparationBaseline=null;
        // The original service owns both the native control identifier and
        // the PP association. Never manufacture either to make the UI light.
        boolean stopPending=policy.disconnecting;
        policy.started();phase="connected";lastHfp="";savePeer();
        write(new File(STATE,"current-target"),policy.target);busy(false);mark("genuine-carplay-connected");
        if(stopPending || pendingTarget.length()>0)needUserStop=true;
    }
    void disconnect() throws Exception {
        if(policy.target.length()==0 || policy.disconnecting)return;
        policy.stopByUser();pausedAt=SystemClock.elapsedRealtime();bonds.clearSetups();
        write(new File(STATE,"manual-pause"),Long.toString(pausedAt));
        phase="disconnecting";deadline=SystemClock.elapsedRealtime()+15000;busy(true);
        String activeAddress=(String)pp("getCPConnectedBTAddress",new Class<?>[0]);
        if(!ConnectionPolicy.originalAssociationCleared(activeAddress) && !policy.target.equalsIgnoreCase(activeAddress))
            throw new IOException("disconnect-target-mismatch");
        // The original disconnect handler is used for a submitted pending
        // attempt too. cancelConnectionPP only removes timeout31 and cannot
        // establish cancellation of its in-flight call or original retry.
        if(policy.connected || oemConnectSubmitted || attemptOwner.owned())
            pp("disconnectPP",new Class<?>[]{int.class,String.class,int.class},1,policy.target,1);
        else {phase="settling-original";closeDiscovery();}
        mark("manual-disconnect-awaiting-finalized");
    }
    void ended() throws Exception {
        if(!retireOriginalAttempt()){mark("waiting-original-work-before-cleanup");return;}
        closeDiscovery();
        String address=(String)pp("getCPConnectedBTAddress",new Class<?>[0]);
        if(!ConnectionPolicy.originalAssociationCleared(address))throw new IOException("oem-association-not-cleared");
        policy.ended();phase="idle";new File(STATE,"current-target").delete();
        oemEndDeadline=0;oemConnectSubmitted=false;pendingFailure="";
        String next=pendingTarget;pendingTarget="";
        // An explicit switch still waits for genuine finalization. Reuse only
        // our own verified projection AP; a plain user stop restores client.
        if(next.length()>0 && bonded(next) && ap && new File(STATE,"ap-owner").exists()
            && wifiState("getWifiApEnabledState")==13 && wifiState("getWifiApMode")==2) {
            mark("switch-reusing-projection-ap");begin(next,true);
            if(policy.target.length()==0)fail("switch-target-unavailable-client-restored");
            return;
        }
        restoreClient();preparationBaseline=null;busy(false);mark(policy.paused?"paused-client-enabled":"disconnected-client-enabled");
        if(next.length()>0)begin(next,true);
    }
    void command() throws Exception {
        String command=read(new File(BUTTON,"command"),128);
        if(!command.matches("[1-9][0-9]{0,18} (connect|disconnect) (?:[0-9A-F]{2}:){5}[0-9A-F]{2}"))return;
        String[] fields=command.split(" ");long token=Long.parseLong(fields[0]);
        if(token<=lastCommand)return;
        lastCommand=token;write(new File(STATE,"command-ack"),fields[0]);
        String target=validTarget(fields[2]);if(!bonded(target))return;
        if(fields[1].equals("disconnect")) {
            if(target.equals(policy.target))disconnect();return;
        }
        if(policy.connected) {
            if(!target.equals(policy.target)){pendingTarget=target;disconnect();}
            return;
        }
        if(!phase.equals("idle") && !phase.equals("error")) {
            if(!target.equals(policy.target)) {
                if(oemConnectSubmitted || attemptOwner.owned() || nativeSession.active()) {pendingTarget=target;disconnect();}
                else {closeDiscovery();policy.ended();phase="idle";begin(target,true);}
            }
            return;
        }
        begin(target,true);
    }
    void fail(String code) throws Exception {
        closeDiscovery();
        if(nativeSession!=null && nativeSession.active()) {
            // Keep the native session if a state publisher is late. Never
            // restore Wi-Fi or fake successful completion on this path.
            busy(false);phase="settling-original";mark("active-session-needs-state-review");return;
        }
        pendingFailure=code;
        if(!retireOriginalAttempt()) {
            phase="settling-original";mark("waiting-original-work-after-failure");return;
        }
        policy.ended();phase="error";restoreClient();preparationBaseline=null;busy(false);mark(code);pendingFailure="";oemConnectSubmitted=false;
        if(pendingTarget.length()>0){String next=pendingTarget;pendingTarget="";begin(next,true);}
    }
    void tick() throws Exception {
        command();
        if(needUserStop) {needUserStop=false;disconnect();}
        if(new File(STATE,"stop").exists() || !new File(BASE,"enabled").exists())throw new IOException("controller-stop");
        if(backendPid==0 || !new File("/proc/"+backendPid).exists())throw new IOException("backend-process-exited");
        if(!((android.os.IInterface)pp).asBinder().isBinderAlive())throw new IOException("projection-service-exited");
        if(!new File("/proc/"+nativeSession.pid).exists())throw new IOException("native-process-exited");
        bondEvents(false);
        boolean active=nativeSession.active();
        if(phase.equals("settling-original") || phase.equals("recovering-original")) {
            if(!attemptOwner.owned() && policy.target.length()==0) {
                OemAttemptSnapshot recovered=originalObservation();
                if(recovered!=null && !recovered.unknown && recovered.sequence>0) {
                    attemptOwner.adopt(recovered,recovered.target);policy.target=recovered.target;oemConnectSubmitted=true;
                }
            }
            if(active) {
                started();if(!policy.connected)mark("preserving-original-active-session");return;
            }
            if(!originalCleanupReady()){mark("waiting-original-work-or-observer-recovery");return;}
            if(pendingFailure.length()>0){fail(pendingFailure);return;}
            ended();return;
        }
        if(policy.disconnecting && !policy.connected && active) {
            started();if(!policy.connected)mark("waiting-original-stop-session-association");return;
        }
        if((policy.connected || policy.disconnecting) && !active && read(new File(BACKEND,"session.state"),8).equals("0")) {
            String address=(String)pp("getCPConnectedBTAddress",new Class<?>[0]);
            if(address==null || address.length()==0){ended();return;}
            if(!address.equalsIgnoreCase(policy.target))throw new IOException("oem-association-changed");
            if(oemEndDeadline==0)oemEndDeadline=SystemClock.elapsedRealtime()+15000;
            if(SystemClock.elapsedRealtime()>oemEndDeadline) {
                busy(false);mark("oem-finalized-association-needs-review");
            } else mark("waiting-oem-disconnected-association");
            return;
        }
        if(policy.connected) {
            if(policy.disconnecting && SystemClock.elapsedRealtime()>deadline)mark("disconnect-awaiting-original-finalization");
            mark(policy.disconnecting?"disconnecting":"connected");return;
        }
        if(policy.disconnecting){mark("waiting-original-pending-stop-finalization");return;}
        if(phase.equals("connecting") || phase.equals("waiting-native")) {
            if(active){
                started();
                if(!policy.connected) {
                    // Original native/session/address callbacks are asynchronous.
                    // A genuine active receiver must not become a failed attempt
                    // merely because its publishers cross our discovery deadline.
                    // Keep the selection and continue verifying original ownership.
                    mark("waiting-oem-active-session-association");
                }
                return;
            }
            if(!bonded(policy.target)){fail("selected-device-unpaired-client-restored");return;}
            if(SystemClock.elapsedRealtime()>deadline){fail("connection-deadline-client-restored");return;}
            if(phase.equals("connecting"))return;
            Set<String> stations=stations();
            if(stationCount!=lastStationCount) {
                write(new File(STATE,"last-station-count"),"elapsed="+SystemClock.elapsedRealtime()+" count="+stationCount);
                lastStationCount=stationCount;
            }
            if(stations.isEmpty()) {
                mark(stationQueryUnavailable?"station-query-unavailable":"waiting-ap-station");
                if(!btRequested && (btDeferred || SystemClock.elapsedRealtime()>knownPeerDeadline))requestBt();
                return;
            }
            String epoch=pairingEpoch();
            // Bind native readiness to the actual BT record update in this
            // backend epoch. This firmware leaves WLDevMacAddr empty; it is
            // not an authoritative Wi-Fi address and must not be a gate.
            boolean fresh=epoch.length()>0 && nativeTargetReady(epoch);
            String knownStation=knownPeers.getProperty(policy.target,"");
            boolean known=knownStation.length()>0 && stations.contains(knownStation);
            // Another phone's association must not block the selected phone's
            // Bluetooth bootstrap. Remembered identity permits requesting BT;
            // HTTP eligibility still requires fresh native or the selected
            // verified associated peer below.
            if((!fresh && btDeferred) || ConnectionPolicy.needsBluetoothFallback(fresh,knownStation.length()>0,btRequested,SystemClock.elapsedRealtime()>knownPeerDeadline)) {
                if(!requestBt())return;
            }
            String assessment="native_ready="+fresh+" known_peer="+known+" bt_requested="+btRequested;
            if(!assessment.equals(lastAssessment)) {
                write(new File(STATE,"last-selection-assessment"),"elapsed="+SystemClock.elapsedRealtime()+" "+assessment);
                lastAssessment=assessment;
            }
            String token=fresh?("native:"+epoch):("known:"+lastCommand);
            if(!policy.canRequest(token,fresh,known)){mark("waiting-selected-native-epoch");return;}
            CarPlayDiscovery.Location location;String selectedAtDiscovery=policy.target;
            try {
                if(discovery==null) {
                    Set<String> others=new HashSet<String>();
                    for(String other:knownPeers.stringPropertyNames())if(!other.equals(policy.target))others.add(knownPeers.getProperty(other));
                    discovery=new CarPlayDiscovery(policy.target,knownStation,others);
                }
                location=discovery.poll();
            }catch(IOException unavailable){fail("selected-discovery-unavailable");return;}
            if(location==null){mark("waiting-selected-control-service");return;}
            // Discovery is an identity/readiness guard only. The original
            // service and control client resolve and send their own request.
            command();
            if(!selectedAtDiscovery.equals(policy.target) || !phase.equals("waiting-native"))return;
            if(new File(STATE,"stop").exists() || !new File(BASE,"enabled").exists())throw new IOException("controller-stop");
            if(SystemClock.elapsedRealtime()>deadline){fail("connection-deadline-client-restored");return;}
            if(nativeSession.active()){fail("active-session-before-selected-control");return;}
            OemAttemptSnapshot baseline=originalObservation();
            if(!OemAttemptOwner.submissionReady(baseline)){mark("waiting-original-work-before-submission");return;}
            attemptOwner.arm(baseline,policy.target);
            selectedPeer=location.station;policy.requested(token);
            // Record ownership BEFORE the asynchronous Binder call. An exception
            // does not establish that the original submission was never queued.
            oemConnectSubmitted=true;phase="connecting";
            pp("connectPP",new Class<?>[]{int.class,String.class,int.class},1,policy.target,1);
            write(new File(STATE,"last-control-identity"),"elapsed="+SystemClock.elapsedRealtime()+" txt_identity="+location.identityFromTxt+" known_station="+selectedPeer.equals(knownStation)+" associations="+location.associationCount+" native_ready="+fresh+" oem_connect_submitted=true");
            closeDiscovery();
            // connectPP is asynchronous and returns no acceptance status.
            // Keep OEM ownership and wait for real session + OEM association.
            deadline=Math.min(deadline,SystemClock.elapsedRealtime()+30000);
            mark("oem-connect-submitted-awaiting-session");
            return;
        }
        String hfp=hfp();boolean freshHfp=!hfp.equals(lastHfp);lastHfp=hfp;
        if(hfp.length()>0 && bonds.consumeSetup(hfp) && bonded(hfp)) {
            begin(hfp,true);mark("new-pairing-awaiting-native-availability");return;
        }
        if(!policy.paused && hfp.length()>0 && freshHfp)begin(hfp,false);
    }
    void reconcileInactiveReceiver() throws Exception {
        if(nativeSession.active())throw new IOException("reconcile-active-session");
        if(!OemAttemptOwner.unownedCleanupReady(originalObservation(),policy.paused))
            throw new IOException("reconcile-original-work-not-settled");
        String generation=nativeSession.generation();
        prop("sys.ev6.cp.idle",generation);
        try {
            if(nativeSession.active())throw new IOException("reconcile-session-became-active");
            exec(null,10000,"/system/bin/am","startservice","-n","org.ev6.wirelesscarplay/org.ev6.research.ProbeService","-a","org.ev6.research.RECONCILE_INACTIVE_RECEIVER");
            long limit=SystemClock.elapsedRealtime()+5000;
            while(!generation.equals(read(new File(BACKEND,"session.reconciled"),64)) || !read(new File(BACKEND,"session.state"),8).equals("0")) {
                if(SystemClock.elapsedRealtime()>limit)throw new IOException("inactive-reconcile-not-acknowledged");
                Thread.sleep(100);
            }
            if(nativeSession.active() || !generation.equals(nativeSession.generation()))throw new IOException("reconcile-generation-changed");
            String address=(String)pp("getCPConnectedBTAddress",new Class<?>[0]);
            // Only the original activation/finalization path owns this mapping.
            // An inactive pointer or a receiver restart does not authorize us
            // to manufacture completion by clearing even our own old address.
            if(!ConnectionPolicy.originalAssociationCleared(address))
                throw new IOException("oem-inactive-association-needs-finalization");
            new File(STATE,"current-target").delete();
            mark("inactive-receiver-state-reconciled");
        }finally{prop("sys.ev6.cp.idle","");}
    }
    void init() throws Exception {
        if(!STATE.isDirectory() && !STATE.mkdirs())throw new IOException("state-directory");
        lockStream=new FileOutputStream(new File(STATE,"controller.lock"),true);
        lockChannel=lockStream.getChannel();
        lock=lockChannel.tryLock();if(lock==null)throw new IOException("controller-already-running");
        new File(BUTTON,"controller-ready").delete();
        checkFirmware();
        if(!new File(ASSET,"wl-stations").canExecute())throw new IOException("station-reader-not-executable");
        String boot=read(new File("/proc/sys/kernel/random/boot_id"),128);
        boolean newBoot=!boot.equals(read(new File(STATE,"boot-id"),128));
        boolean priorOwnedAp=newBoot && new File(STATE,"ap-owner").exists() && new File(STATE,"saved-client-state").exists();
        if(newBoot) {
            new File(STATE,"manual-pause").delete();new File(STATE,"current-target").delete();
            new File(STATE,"bond-ack").delete();
            // netd addresses belong to the prior kernel. The saved client
            // preference remains valid for our own persisted projection AP.
            new File(STATE,"ap-subnet.original").delete();
            if(!new File(STATE,"ap-owner").exists())new File(STATE,"saved-client-state").delete();
            // Button nonces use System.nanoTime(), which restarts at kernel
            // boot. Retire both the old command and its acknowledgement:
            // keeping the old high-water mark drops valid new UI clicks.
            for(File stale:Arrays.asList(new File(BUTTON,"command"),new File(BUTTON,"command.bak"),new File(STATE,"command-ack"))) {
                if(stale.exists() && !stale.delete())throw new IOException("prior-boot-command-not-retired");
            }
        }
        write(new File(STATE,"boot-id"),boot);
        policy.paused=new File(STATE,"manual-pause").exists();
        String pause=read(new File(STATE,"manual-pause"),32);
        pausedAt=policy.paused?(pause.matches("[1-9][0-9]{1,17}")?Long.parseLong(pause):SystemClock.elapsedRealtime()):0;
        String ack=read(new File(STATE,"command-ack"),32);lastCommand=ack.matches("[1-9][0-9]{0,18}")?Long.parseLong(ack):0;
        File known=new File(STATE,"known-peers");if(known.exists()) {FileInputStream input=new FileInputStream(known);try{knownPeers.load(input);}finally{input.close();}}
        File bondAck=new File(STATE,"bond-ack");if(bondAck.exists()) {
            String saved=read(bondAck,8192);bonds.seen.load(new ByteArrayInputStream(saved.getBytes("US-ASCII")));
            if(bonds.seen.size()>32)throw new IOException("bond-ack-size");
        }
        pp=service("phoneprojection","android.phoneprojection.IPPManager$Stub");
        wifi=service("wifi","android.net.wifi.IWifiManager$Stub");
        prop("sys.ev6.cp.apready","0");
        Class.forName("FinalRegistry").getMethod("start").invoke(null);
        exec(null,10000,"/system/bin/am","startservice","-n","org.ev6.wirelesscarplay/org.ev6.research.ProbeService");
        long limit=SystemClock.elapsedRealtime()+30000;
        while(!new File(BACKEND,"pairing.state").exists() || !new File(BACKEND,"event-state").exists()
                || !read(new File(BACKEND,"backend.ready"),8).equals("1")) {
            if(SystemClock.elapsedRealtime()>limit)throw new IOException("backend-not-ready");Thread.sleep(100);
        }
        backendPid=NativeSession.findPid("org.ev6.wirelesscarplay");
        if(backendPid==0)throw new IOException("backend-process-missing");
        int pid=NativeSession.findPid("com.daudio.app.dipo");
        if(pid!=0) {try{nativeSession=new NativeSession(pid);}catch(IOException notReady){}}
        String adopted=validTarget(read(new File(STATE,"current-target"),32));
        if(nativeSession!=null && nativeSession.active()) {
            if(!newBoot && adopted.length()==0) {
                // Recover an original session that completed at the old worker's
                // discovery deadline. The real selected UI intent is only a
                // candidate; the original owner and association below must agree.
                String[] command=read(new File(BUTTON,"command"),128).split(" ");
                if(command.length==3 && command[1].equals("connect"))adopted=validTarget(command[2]);
            }
            if(newBoot || !bonded(adopted))throw new IOException("active-session-without-owner");
            String address=(String)pp("getCPConnectedBTAddress",new Class<?>[0]);
            if(!ConnectionPolicy.oemSessionReady(nativeSession.active(),read(new File(BACKEND,"session.state"),8).equals("1"),
                    nativeSession.hasControlOwner(),adopted,address))throw new IOException("active-session-without-oem-owner");
            policy.target=adopted;policy.started();phase="connected";ap=true;
            write(new File(STATE,"current-target"),adopted);
            networkReady();
            mark("adopted-genuine-owned-session");
        } else {
            // Worker startup is not an OEM attempt completion boundary. Reuse
            // the live receiver, including its original pending work. Only an
            // actually absent receiver needs its normal original Service start.
            if(pid==0)exec(null,10000,"/system/bin/am","startservice","-n","com.daudio.app.dipo/.service.DipoService");
            limit=SystemClock.elapsedRealtime()+30000;
            while(nativeSession==null) {
                int candidate=NativeSession.findPid("com.daudio.app.dipo");
                if(candidate!=0)try {NativeSession session=new NativeSession(candidate);if(session.ready())nativeSession=session;else session.close();}catch(IOException notReady){}
                if(SystemClock.elapsedRealtime()>limit)throw new IOException("player-ready-deadline");
                if(nativeSession==null)Thread.sleep(200);
            }
            OemAttemptSnapshot snapshot=originalObservation();
            ap=new File(STATE,"ap-owner").exists() && new File(STATE,"saved-client-state").exists()
                && wifiState("getWifiApEnabledState")==13;
            if(snapshot!=null && !snapshot.unknown && snapshot.sequence>0) {
                attemptOwner.adopt(snapshot,snapshot.target);policy.target=snapshot.target;oemConnectSubmitted=true;
            }
            if(OemAttemptOwner.unownedCleanupReady(snapshot,policy.paused) && !nativeSession.active()
                && ConnectionPolicy.originalAssociationCleared((String)pp("getCPConnectedBTAddress",new Class<?>[0])))
                reconcileInactiveReceiver();
            if(originalCleanupReady()) {
                if(!retireOriginalAttempt())throw new IOException("oem-startup-boundary-changed");
                policy.ended();reconcileInactiveReceiver();restoreClient();
                if(priorOwnedAp)mark("prior-boot-projection-ap-recovered-client");
            }else {phase="recovering-original";mark("preserving-original-work-on-worker-start");}
        }
        bondEvents(true); // Old events invalidate stale caches but cannot override a pause on startup.
        observe(BACKEND,new HashSet<String>(Arrays.asList("pairing.state","session.state","runtime.event","bond.state")));
        observe(BUTTON,new HashSet<String>(Arrays.asList("command")));
        observe(STATE,new HashSet<String>(Arrays.asList("stop","manual-pause")));
        write(new File(STATE,"controller.pid"),Integer.toString(android.os.Process.myPid()));
        write(new File(BUTTON,"controller-ready"),"1");
        busy(attemptOwner.owned() || phase.equals("recovering-original"));
        mark(phase.equals("recovering-original")?"preserving-original-work-on-worker-start":
            (policy.connected?"connected":(policy.paused?"paused-client-enabled":"waiting-bluetooth-client-enabled")));
    }
    void close() {
        if(lock==null)return; // A second process must not disarm the owner.
        try{prop("sys.ev6.cp.busy","");prop("sys.ev6.cp.until","0");prop("sys.ev6.cp.apready","0");}catch(Exception ignored){}
        new File(BUTTON,"controller-ready").delete();
        for(FileObserver observer:observers)observer.stopWatching();
        closeDiscovery();
        try{Class.forName("FinalRegistry").getMethod("close").invoke(null);}catch(Exception ignored){}
        try{if(nativeSession!=null)nativeSession.close();}catch(Exception ignored){}
        try{if(lock!=null)lock.release();if(lockChannel!=null)lockChannel.close();if(lockStream!=null)lockStream.close();}catch(Exception ignored){}
    }
    public static void main(String[] arguments) {
        Looper.prepareMainLooper();final CarPlayController controller=new CarPlayController();instance=controller;
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable(){public void run(){controller.close();}},"CarPlay-shutdown"));
        try {
            controller.init();
            for(;;) {
                controller.tick();
                synchronized(SIGNAL) {if(!signaled)SIGNAL.wait(controller.phase.equals("waiting-native")?1000:5000);signaled=false;}
            }
        } catch(Throwable failure) {
            String code=failure.getMessage();
            if(code==null || !code.matches("[a-z0-9-]{1,65}"))code=failure.getClass().getSimpleName().toLowerCase(Locale.US);
            if(controller.lock!=null)try{controller.mark("controller-stopped-"+code);}catch(Exception ignored){}
            System.out.println("CARPLAY_CONTROLLER_STOPPED="+failure.getClass().getSimpleName());
            controller.close();System.exit(1);
        }
    }
}
