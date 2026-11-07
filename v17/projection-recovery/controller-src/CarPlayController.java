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
    final ProjectionCpCancelDrain nativeCancelDrain=new ProjectionCpCancelDrain();
    BluetoothWorkClient.Snapshot bluetoothPinned;
    boolean bluetoothCancelSent;
    long bluetoothCancelDeadline;
    String bluetoothCleanupReason="",bluetoothResult="none";
    OemAttemptSnapshot preparationBaseline;
    final List<FileObserver> observers=new ArrayList<FileObserver>();
    final Properties knownPeers=new Properties();
    final BondLifecycle bonds=new BondLifecycle();
    final ProjectionFirstPairPolicy firstPairs=new ProjectionFirstPairPolicy();
    final java.util.concurrent.ConcurrentHashMap<String,String> deviceEpochs=new java.util.concurrent.ConcurrentHashMap<String,String>();
    Object pp,bt,wifi;
    NativeSession nativeSession;
    CarPlayDiscovery discovery;
    String phase="idle",lastStatus="",lastHfp="",pendingTarget="",selectedPeer="";
    String lastAssessment="",pendingFailure="",publishedBusy;
    long lastDiscoveryDiagnosticAt;
    String lastDiscoveryDiagnosticScope="";
    boolean ap,btRequested,needUserStop,stationQueryUnavailable;
    boolean oemConnectSubmitted,btDeferred;
    boolean manualSelection;
    String failedBootstrapTarget="",failedBootstrapEpoch="";
    long failedBootstrapCommand;
    long deadline,lastCommand,knownPeerDeadline,oemEndDeadline;
    long pausedAt,activeCommand;
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
    // Inspect only the existing bounded cache; never relax identity or issue network traffic.
    static String discoveryCacheDiagnostic(CarPlayDiscovery d,String target) {
        int valid=0,expired=0,missingId=0,matchingId=0,otherId=0;
        long now=System.nanoTime();String expected=MdnsIdentity.normalize(target);
        for(MdnsIdentity.Service service:d.identity.services.values()) {
            if(service.port==0 || service.srvUntil<=now || (service.txtSeen && service.txtUntil<=now)){expired++;continue;}
            valid++;
            if(service.id==null)missingId++;
            else if(expected.equals(service.id))matchingId++;
            else otherId++;
        }
        return " records="+d.identity.services.size()+" valid="+valid+" expired_or_incomplete="+expired+" missing_id="+missingId+" matching_id="+matchingId+" other_or_invalid_id="+otherId;
    }
    void mark(String status) throws Exception {
        if(!status.matches("[a-z0-9_-]{1,100}"))throw new IOException("status-schema");
        if(status.equals(lastStatus))return;
        lastStatus=status;write(new File(STATE,"status"),status);
        if(exclusiveRuntime!=null)exclusiveRuntime.trace(status);
        File log=new File(STATE,"events.log");
        if(log.length()>65536) {File previous=new File(STATE,"events.previous.log");previous.delete();if(!log.renameTo(previous))throw new IOException("log-rotate");}
        FileOutputStream output=new FileOutputStream(log,true);
        try {output.write((Long.toString(SystemClock.elapsedRealtime())+" "+status+"\n").getBytes("US-ASCII"));}finally{output.close();}
        System.out.println("CARPLAY_STATE="+status);
    }
    private String publishedProjectionProgress;
    // Display-only, atomically published and short-lived; never an admission grant.
    boolean projectionFailurePending(){return pendingFailure.length()>0||phase.equals("error");}
    long progressClock(){return SystemClock.elapsedRealtime();}
    void projectionProgress(String target,int type,String request,String mode,long until)throws Exception {
        String value=request.length()==0?"":type+"|"+target+"|"+until+"|"+request+"|"+mode;
        if(value.equals(publishedProjectionProgress))return;
        prop("sys.ev6.pp.progress",value);publishedProjectionProgress=value;
    }
    void busy(boolean value) throws Exception {
        String selected=value?policy.target:"";
        if(selected.equals(publishedBusy))return;
        // The original Settings ODEX reads only these transient properties.
        prop("sys.ev6.cp.until",value?Long.toString(SystemClock.elapsedRealtime()/1000+90):"0");
        prop("sys.ev6.cp.busy",selected);
        exec(null,4000,"/system/bin/am","broadcast","-a","getCarPlayConnectedDeviceAddress");
        publishedBusy=selected;
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
        hashes.put("/system/lib/libCarPlayAudio.so","81402f9bb2c74840f953c05d27291b23");
        hashes.put("/system/lib/libCarPlayUtils.so","bed57ad5b2b02c6e4ff80595d6cb5064");
        hashes.put("/system/lib/libopus.so","32269345b7f9d526ed059c04b82790dc");
        hashes.put("/system/lib/libstagefright_soft_aacdec.so","a93483c55c32752b87139866663233fd");
        hashes.put("/system/lib/libev6aac.so","badc26bd4c40a1c3e543b733a8ec5028");
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
        IBinder binder=ProjectionBluetoothExclusiveClient.oemBinder();
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
        if(accepted) {
            pinBluetoothWork();
        }
        mark("selected-bluetooth-requested");return true;
    }
    void subnet(String operation) throws Exception {
        Map<String,String> env=new HashMap<String,String>();env.put("EV6_STATE",STATE.getPath());
        String result=exec(env,15000,"/system/bin/sh",new File(ASSET,"ap-subnet.sh").getPath(),operation);
        if(!result.contains(operation.equals("restore")?"AP_SUBNET_RESTORED":"AP_SUBNET_"))throw new IOException("subnet-not-verified");
    }
    void aaNetworkFirewall(AaApDemandClient.Demand demand)throws Exception {
        if(demand==null||!AaApDemandClient.live(demand))throw new IOException("aa-firewall-demand");
        String boot=read(new File("/proc/sys/kernel/random/boot_id"),100).trim().replace("-","");
        if(!boot.matches("[0-9a-f]{32}")||!demand.incarnation.matches("[0-9a-f]{32}")||!demand.requestId.matches("[0-9a-f]{32}"))throw new IOException("aa-firewall-identity");
        Map<String,String> env=new HashMap<String,String>();env.put("EV6_STATE",STATE.getPath());env.put("EV6_AA_TOKEN",boot+":"+demand.incarnation+":"+demand.requestId);
        String result=exec(env,5000,"/system/bin/sh",new File(ASSET,"ap-firewall.sh").getPath(),"aa-start");
        if(!result.contains("AP_FIREWALL_READY_AA_INBOUND"))throw new IOException("aa-firewall-not-ready");
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
    // Readback only: does not grant an AP lease or publish readiness.
    private void requireApNetworkBeforeEffects() throws Exception {
        if(wifiState("getWifiApEnabledState")!=13 || wifiState("getWifiApMode")!=2)throw new IOException("projection-network-not-ready");
        ProjectionApOwnership.requireRetained(STATE,new File("/proc/sys/kernel/random/boot_id"),wifiState("getWifiApEnabledState"),wifiState("getWifiApMode"));
        ProjectionApReadiness.requireReady(new ControllerApNetwork(this),false);
    }
    private void applyApNetworkEffects() throws Exception {
        networkFirewall("start");
    }
    // Shared AP readback only. Does not publish CP readiness or grant authority.
    private void requireApNetworkAfterEffects() throws Exception {
        ProjectionApReadiness.requireReady(new ControllerApNetwork(this),true);
        ProjectionApOwnership.requireRetained(STATE,new File("/proc/sys/kernel/random/boot_id"),wifiState("getWifiApEnabledState"),wifiState("getWifiApMode"));
    }
    // CP notification always revalidates immediately; no unchecked publish entry.
    private void verifyAndPublishApReady() throws Exception {
        requireApNetworkAfterEffects();
        if(policy.connected && !ControllerProjectionReservation.valid()) {
            // Adopt a proved existing session without authorizing another native START.
            prop("sys.ev6.cp.apready","1");return;
        }
        String grant=ControllerProjectionReservation.startGrant(policy.target);
        prop("sys.ev6.cp.apready","1");
        exec(null,10000,"/system/bin/am","startservice","-n","org.ev6.wirelesscarplay/org.ev6.research.ProbeService","-a","org.ev6.research.AP_READY","--es","projection-grant",grant);
    }
    void networkReady() throws Exception {
        prop("sys.ev6.cp.apready","0");
        requireApNetworkBeforeEffects();
        applyApNetworkEffects();
        verifyAndPublishApReady();
    }
    /** Explicit adapter only: init/begin do not create or execute this port yet. */
    private final class ColdStartPort implements ProjectionApColdStart.RecoveryPort {
        private final ProjectionApColdStartAuthority authority;
        private org.ev6.projection.ReservationStore.Ticket operation;
        private final File kernelBoot=new File("/proc/sys/kernel/random/boot_id");
        ColdStartPort(ProjectionApColdStartAuthority authority)throws Exception {
            if(authority==null)throw new IOException("ap-authority-required");
            this.authority=authority;
        }
        public void requireAuthority(org.ev6.projection.ReservationStore.Ticket ticket,int stage)throws Exception {
            if(ticket==null || !"AP".equals(ticket.kind) || (operation!=null && operation!=ticket)
                || !ProjectionApOwnerRecord.readBoot(kernelBoot).replace("-","").equals(ticket.boot))
                throw new IOException("ap-operation-identity-changed");
            authority.require(ticket,stage);operation=ticket;
        }
        public void requireFreshState()throws Exception {
            if(wifiState("getWifiApEnabledState")!=11
                || new File(STATE,"saved-client-state").exists() || new File(STATE,"ap-owner").exists())
                throw new IOException("ap-cold-start-recovery-required");
            int client=wifiState("getWifiEnabledState");
            if(client!=1 && client!=3)throw new IOException("ap-client-transition");
        }
        public void prepareClientAndOwner()throws Exception {
            if(operation==null)throw new IOException("ap-operation-required");
            String result=runTool("OemApSwitch","prepare",new File(STATE,"saved-client-state").getPath(),new File(BACKEND,"event-state").getPath());
            if(!result.contains("OEM_CLIENT_PREFERENCE_SAVED"))throw new IOException("ap-client-prepare-unverified");
            ProjectionApOwnerRecord.create(STATE,kernelBoot,operation.boot,ProjectionApJournal.ANDROID_SYNC);
        }
        public void switchPrepared()throws Exception {
            String result=runTool("OemApSwitch","start-prepared",new File(STATE,"saved-client-state").getPath(),new File(BACKEND,"event-state").getPath());
            if(!result.contains("OEM_SWITCH_COMPLETE"))throw new IOException("ap-start-unverified");
            ProjectionApOwnership.requireRetained(STATE,kernelBoot,wifiState("getWifiApEnabledState"),wifiState("getWifiApMode"));
            ap=true;
        }
        public void configureNetwork()throws Exception {
            subnet("start");requireApNetworkBeforeEffects();applyApNetworkEffects();
        }
        public void recoverPreparation()throws Exception {
            if(operation==null)throw new IOException("ap-recovery-operation-required");
            File saved=new File(STATE,"saved-client-state"),owner=new File(STATE,"ap-owner");
            if(!saved.exists() && !owner.exists()){requireFreshState();prepareClientAndOwner();return;}
            ProjectionApRecoveryFiles.prepared(STATE,kernelBoot,wifiState("getWifiApEnabledState"),wifiState("getWifiEnabledState"),ProjectionApJournal.ANDROID_SYNC);
        }
        public void recoverSwitch()throws Exception {
            ProjectionApRecoveryFiles.requireOwner(STATE,kernelBoot);
            int state=wifiState("getWifiApEnabledState"),client=wifiState("getWifiEnabledState");
            if(state==13){
                if(client!=1)throw new IOException("ap-recovery-client-active");
                ProjectionApOwnership.requireRetained(STATE,kernelBoot,state,wifiState("getWifiApMode"));ap=true;return;
            }
            SavedClientPreparation.requireRecoveryPrepared(new File(STATE,"saved-client-state"),state,client);
            String result=runTool("OemApSwitch","resume-prepared",new File(STATE,"saved-client-state").getPath(),new File(BACKEND,"event-state").getPath());
            if(!result.contains("OEM_SWITCH_COMPLETE"))throw new IOException("ap-recovered-switch-unverified");
            ProjectionApOwnership.requireRetained(STATE,kernelBoot,wifiState("getWifiApEnabledState"),wifiState("getWifiApMode"));ap=true;
        }
        public void recoverNetwork()throws Exception {
            ProjectionApRecoveryFiles.requireOwner(STATE,kernelBoot);
            ProjectionApOwnership.requireRetained(STATE,kernelBoot,wifiState("getWifiApEnabledState"),wifiState("getWifiApMode"));
            // Already configured subnet is preserved, even if its original snapshot exists.
            boolean topologyReady=false;
            try{requireApNetworkBeforeEffects();topologyReady=true;}catch(Exception unavailable){}
            if(!topologyReady){
                if(new File(STATE,"ap-subnet.original").exists()){
                    ProjectionApRecoveryFiles.reaffirm(new File(STATE,"ap-subnet.original"),ProjectionApJournal.ANDROID_SYNC);
                    Map<String,String> env=new HashMap<String,String>();env.put("EV6_STATE",STATE.getPath());
                    env.put("EV6_AP_RECORD","EV6RES1|"+operation.boot+"|"+operation.sequence+"|H|AP|"+operation.key+"|"+operation.incarnation+"|END");
                    String result=exec(env,15000,"/system/bin/sh",new File(ASSET,"ap-subnet-recover.sh").getPath());
                    if(!result.contains("AP_SUBNET_RECOVERED_ETHERNET_UNCHANGED"))throw new IOException("ap-subnet-repair-unverified");
                }else subnet("start");
                requireApNetworkBeforeEffects();
            }
            applyApNetworkEffects();requireApNetworkAfterEffects();ap=true;
        }
        public void verifyNetwork()throws Exception {requireApNetworkAfterEffects();}
    }
    private final ProjectionApJournal.ParentSync startupSync=new ProjectionApJournal.ParentSync(){
        public void sync(File parent)throws Exception {
            if(parent.getCanonicalPath().equals("/data/local/ev6-projection")){
                File reservation=new File(parent,"reservation");
                if(!reservation.isFile() || !reservation.getAbsoluteFile().equals(reservation.getCanonicalFile()))throw new IOException("startup-reservation-path");
                // Separate processes change metadata without closing a descriptor in our lock-owning VM.
                exec(null,5000,"/system/bin/chown","0.1000",reservation.getPath());
                exec(null,5000,"/system/bin/chmod","0664",reservation.getPath());
            }
            DirectorySync.sync(parent);
        }
    };
    private boolean startupBluetoothRetry;
    private long startupBluetoothRetryAt;
    private int startupRecoveryAttempts;
    private String startupFirstFailure;
    private String startupDemandIdentity;
    private String startupFailureChain(Throwable error){
        StringBuilder result=new StringBuilder();
        for(int i=0;error!=null && i<8;i++,error=error.getCause()){
            if(i>0)result.append(" <- ");
            result.append(error.getClass().getName()).append(":").append(error.getMessage());
        }
        return result.toString().replace('\n',' ').replace('\r',' ');
    }
    private void retrySharedApWhenIdle(boolean nativeActive)throws Exception {
        if(!startupBluetoothRetry || startupAttempted || ap || nativeActive
            || policy.connected || policy.disconnecting || attemptOwner.owned()
            || !originalCleanupReady() || SystemClock.elapsedRealtime()<startupBluetoothRetryAt)return;
        // Called only on the idle tick path. Re-run the full cohort, maintenance,
        // Bluetooth and reservation checks; never cancel unrelated OEM work.
        prepareSharedAp();
    }
    private boolean prepareSharedAp()throws Exception {
        startupBluetoothRetry=false;
        try{ensureProjectionAp();state("ap-startup-result","ready");return true;}
        catch(Exception blocked){
            String detail=startupFailureChain(blocked);
            if(exclusiveRuntime!=null)exclusiveRuntime.trace("ap-start-failure:"+detail,true);
            if(startupFirstFailure==null && (startupAttempted || startupRecoveryAttempts>0)){
                startupFirstFailure=detail;
                state("ap-startup-first-failure","elapsed="+SystemClock.elapsedRealtime()+" "+detail);
            }
            startupBluetoothRetry=(!startupAttempted
                && startupRecoveryAttempts<3 && "startup-bluetooth-work-present".equals(blocked.getMessage()))
                || (startupAttempted && startupRecoveryAttempts<3);
            startupBluetoothRetryAt=SystemClock.elapsedRealtime()+2000;
            state("ap-startup-result","recovery="+startupRecoveryAttempts+" blocked:"+detail);
            // AA AP setup has its own correlated demand and must not overwrite
            // the outcome of a previously completed CP button command.
            if(startingAaDemand==null && cpApDemand.current()!=null
                && cpApDemand.current().command==activeCommand)commandStage("rejected","shared-ap-startup-unconfirmed");
            mark("shared-ap-startup-blocked");return false;
        }
    }
    private boolean startupAttempted;
    private String startupBackendPath;
    private void requireStartupCohort()throws Exception {
        if(startupBackendPath==null){
            String result=exec(null,10000,"/system/bin/pm","path","org.ev6.wirelesscarplay").trim();
            if(!result.startsWith("package:") || result.indexOf('\n')>=0)throw new IOException("startup-backend-package-path");
            startupBackendPath=result.substring(8);
        }
        String boot=ProjectionApOwnerRecord.readBoot(new File("/proc/sys/kernel/random/boot_id")).replace("-","");
        ProjectionStartupCohort.require(new File(ASSET,"startup-cohort.properties"),boot,startupBackendPath);
        String guard=read(new File(BACKEND,"startup-auth.state"),256);
        if(!guard.equals("EV6_STARTUP_AUTH1|"+boot+"|"+backendPid))throw new IOException("startup-backend-guard-unconfirmed");
    }
    /** Projection demand uses exact selected-device authority; maintenance stays globally idle. */
    private boolean projectionBluetoothReady()throws Exception {return projectionBluetoothReady(null);}
    private boolean projectionBluetoothReady(String prepared)throws Exception {
        if(exclusiveRuntime!=null){
            if(startingAaDemand==null&&cpApDemand.current()==null)return false;
            try{if(startingAaDemand!=null)exclusiveRuntime.verifyAaApStart(startingAaDemand.peerKey,prepared);else exclusiveRuntime.verifyApStart(1,prepared);return true;}
            catch(Exception pending){state("ap-bluetooth-gate",startupFailureChain(pending));return false;}
        }
        BluetoothWorkClient.Snapshot work=readBluetoothWork();return work!=null&&work.settled();
    }
    private void ensureProjectionAp()throws Exception {
        final org.ev6.projection.ReservationStore store=org.ev6.projection.ProjectionReservation.store();
        if(store.maintenanceRequested())throw new IOException("projection-maintenance-in-progress");
        int pending=store.apPendingStatus();
        if(ap && pending==org.ev6.projection.ReservationStore.INVALID){
            requireApNetworkAfterEffects();return; // Retained AP; no AP lease or new START.
        }
        boolean recovering=startupAttempted;
        // A new request receives a fresh budget only after a proven no-effects
        // cancellation. Polling the same request must never refill its budget.
        CarPlayApDemand.Request request=cpApDemand.current();
        String demand=startingAaDemand!=null?"AA|"+startingAaDemand.incarnation+"|"+startingAaDemand.receipt+"|"+startingAaDemand.requestId
            :request==null?null:"CP|"+request.target+"|"+request.command+"|"+request.expires;
        if(demand!=null && !demand.equals(startupDemandIdentity) && !recovering && !ap){
            String currentBoot=ProjectionApOwnerRecord.readBoot(new File("/proc/sys/kernel/random/boot_id")).replace("-","");
            boolean evidence=new File("/data/local/ev6-projection/reservation.next").exists()
                || new File(STATE,"ap-startup-"+currentBoot+".journal").exists()
                || new File(STATE,"ap-owner").exists() || new File(STATE,"saved-client-state").exists()
                || new File(STATE,"ap-subnet.original").exists() || new File(STATE,"ap-firewall.owner").exists() || new File(STATE,"aa-firewall.owner").exists() || new File(STATE,"aa-firewall.owner.next").exists();
            if(ProjectionApPreparationProof.renewBudget(demand,startupDemandIdentity,recovering,ap,startupRecoveryAttempts,
                read(new File("/data/local/ev6-projection/reservation"),400),currentBoot,evidence)){
                startupRecoveryAttempts=0;startupFirstFailure=null;startupDemandIdentity=demand;
            }
        }
        if(startupRecoveryAttempts>=3)throw new IOException("startup-recovery-budget-exhausted");
        if(recovering){
            if(SystemClock.elapsedRealtime()<startupBluetoothRetryAt)throw new IOException("startup-recovery-backoff");
        }
        requireStartupCohort();requireAaDemandLive();
        if(attemptOwner.owned() || policy.connected || policy.disconnecting || nativeSession.active()
            || projectionReleasePending || !originalCleanupReady())throw new IOException("startup-original-work-present");
        if(!projectionBluetoothReady())throw new IOException("startup-bluetooth-work-present");
        if(recovering)startupRecoveryAttempts++;
        prop("sys.ev6.cp.apready","0");startupAttempted=true;
        final String[] preparedProof=new String[1];
        final int[] highestAuthorityStage=new int[]{-1};
        ProjectionApColdStartAuthority authority=new ProjectionApColdStartAuthority(){
            public void require(org.ev6.projection.ReservationStore.Ticket ticket,int stage)throws Exception {
                requireStartupCohort();requireAaDemandLive();
                int status=store.status(ticket);
                if(status!=org.ev6.projection.ReservationStore.VALID)
                    throw new IOException("startup-authority-stage-"+stage+"-reservation-"+status);
                if(attemptOwner.owned() || policy.connected || policy.disconnecting || nativeSession.active()
                    || projectionReleasePending || !originalCleanupReady())
                    throw new IOException("startup-authority-stage-"+stage+"-original-work");
                highestAuthorityStage[0]=Math.max(highestAuthorityStage[0],stage);
                String proof=ProjectionApPreparationProof.record(ticket.boot,ticket.sequence,ticket.kind,ticket.key,ticket.incarnation);
                if(stage==0)preparedProof[0]=proof;
                if(!projectionBluetoothReady(proof))
                    throw new IOException("startup-authority-stage-"+stage+"-bluetooth-work");
            }
        };
        String boot=ProjectionApOwnerRecord.readBoot(new File("/proc/sys/kernel/random/boot_id")).replace("-","");
        if(!recovering && new File("/data/local/ev6-projection/reservation").exists())
            ProjectionApCrossBoot.recover(store,STATE,boot,ProjectionApJournal.ANDROID_SYNC,new ProjectionApRetainedBoot.KernelSessionPort(){
                public void requireEndedKernelSession(String priorStamp)throws Exception {
                    requireStartupCohort();requireAaDemandLive();
                    String current=ProjectionApOwnerRecord.readBoot(new File("/proc/sys/kernel/random/boot_id")).replace("-","");
                    if(priorStamp.split(":",-1)[0].equals(current))throw new IOException("ap-same-kernel-session-preserved");
                    // Different kernel: old native processes/sockets cannot survive. Loaded cohort
                    // blocks new CP/AA/USB starts while the durable record still belongs to that boot.
                    BluetoothWorkClient.Snapshot work=readBluetoothWork();
                    if(attemptOwner.owned() || policy.connected || !originalCleanupReady() || work==null || !work.settled())throw new IOException("ap-new-kernel-work-not-settled");
                }
                public void requirePrior(org.ev6.projection.ReservationStore.Ticket prior,org.ev6.projection.ReservationStore.Ticket next,int stage)throws Exception {
                    requireStartupCohort();requireAaDemandLive();
                    BluetoothWorkClient.Snapshot work=readBluetoothWork();
                    if(attemptOwner.owned() || policy.connected || !originalCleanupReady() || work==null || !work.settled())throw new IOException("ap-cross-boot-work-present");
                    File ownerFile=new File(STATE,"ap-owner"),savedFile=new File(STATE,"saved-client-state");
                    int apState=wifiState("getWifiApEnabledState"),client=wifiState("getWifiEnabledState");
                    if(!savedFile.exists()){
                        if(ownerFile.exists() || stage>ProjectionApJournal.CLIENT_SAVE_PENDING || apState!=11 || (client!=1 && client!=3))throw new IOException("ap-cross-boot-preparation-unknown");
                        return;
                    }
                    int saved=SavedClientDurability.readSaved(savedFile);
                    if(ownerFile.exists()){
                        String owner=ProjectionApOwnerRecord.readBoot(ownerFile).replace("-","");
                        if(owner.equals(next.boot)){
                            if(store.status(next)!=org.ev6.projection.ReservationStore.VALID)throw new IOException("ap-cross-boot-owner-before-admission");
                        }else ProjectionApCrossBoot.requireAncestorOwner(STATE,prior,owner,ProjectionApJournal.ANDROID_SYNC);
                    }else if(stage>=ProjectionApJournal.CLIENT_SAVED)throw new IOException("ap-cross-boot-owner-missing");
                    if(apState==11){
                        if(client!=saved && !(stage>=ProjectionApJournal.SWITCH_PENDING && client==1))throw new IOException("ap-cross-boot-client-changed");
                    }else if(apState!=13 || wifiState("getWifiApMode")!=2 || client!=1 || stage<ProjectionApJournal.SWITCH_PENDING)
                        throw new IOException("ap-cross-boot-radio-unconfirmed");
                }
                public void adoptOwner(org.ev6.projection.ReservationStore.Ticket prior,org.ev6.projection.ReservationStore.Ticket next)throws Exception {
                    if(store.status(next)!=org.ev6.projection.ReservationStore.VALID)throw new IOException("ap-cross-boot-admission-changed");
                    File ownerFile=new File(STATE,"ap-owner");
                    if(!ownerFile.exists())return; // Recovery preparation completes a missing owner after admission.
                    String oldOwner=ProjectionApOwnerRecord.readBoot(ownerFile).replace("-","");
                    if(!oldOwner.equals(next.boot))ProjectionApCrossBoot.requireAncestorOwner(STATE,prior,oldOwner,ProjectionApJournal.ANDROID_SYNC);
                    ProjectionApRecoveryFiles.adoptOwner(STATE,new File("/proc/sys/kernel/random/boot_id"),oldOwner,next.boot,next.incarnation,ProjectionApJournal.ANDROID_SYNC);
                }
            });
        File startupJournal=new File(STATE,"ap-startup-"+boot+".journal");
        ColdStartPort startupPort=new ColdStartPort(authority);
        boolean released;
        try {
            released=recovering?ProjectionApInterruptedStart.recover(store,startupJournal,startupSync,startupPort)
                :new ProjectionApStartup(store,startupJournal,startupSync,startupPort).run();
        }catch(Exception failure){
            if(!recovering && highestAuthorityStage[0]==0 &&
                !new File(STATE,"ap-owner").exists() && !new File(STATE,"saved-client-state").exists() &&
                ProjectionApPreparationProof.canceled(preparedProof[0],read(new File("/data/local/ev6-projection/reservation"),400),
                    startupJournal.exists(),new File("/data/local/ev6-projection/reservation.next").exists(),failure.getMessage())){
                startupAttempted=false;startupRecoveryAttempts++;
                throw new IOException("startup-bluetooth-work-present",failure);
            }
            throw failure;
        }
        if(!released)throw new IOException("startup-handoff-unconfirmed");
        ap=true;mark("shared-ap-ready-for-session-admission");
    }
    void startAp() throws Exception {
        if(!ControllerProjectionReservation.valid())throw new IOException("projection-reservation-required-before-ap");
        if(!ap)throw new IOException("shared-ap-bootstrap-required");
        networkReady();
    }
    void retainProjectionNetwork() throws Exception {
        String retained=SharedProjectionAp.retainAfterCarPlay(
            new ControllerApRetentionPort(this),originalCleanupReady());
        state("projection-network-retention",retained);
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
    String backendState() throws Exception {
        String value=read(new File(BACKEND,"session.state"),8);
        return value.equals("0") || value.equals("1")?value:"UNKNOWN";
    }
    SessionEvidence runtimeEvidence() throws Exception {
        OemAttemptSnapshot before=originalObservation();
        if(before==null)return null;
        boolean active=nativeSession.active(),owner=nativeSession.hasControlOwner();
        String backend=backendState();
        String address=(String)pp("getCPConnectedBTAddress",new Class<?>[0]);
        OemAttemptSnapshot after=originalObservation();
        String confirmed=(String)pp("getCPConnectedBTAddress",new Class<?>[0]);
        if(address==null)address="";if(confirmed==null)confirmed="";
        if(after==null || !before.receiver.equals(after.receiver) || before.revision!=after.revision
            || active!=nativeSession.active() || owner!=nativeSession.hasControlOwner()
            || !backend.equals(backendState()) || !address.equalsIgnoreCase(confirmed)
            || (address.length()>0 && validTarget(address).length()==0))return null;
        return new SessionEvidence(after,active,owner,backend,address);
    }
    boolean originalCleanupReady() throws Exception {
        SessionEvidence evidence=runtimeEvidence();
        if(evidence==null || !evidence.inactive())return false;
        if(!nativeCancelDrain.ready())return false;
        OemAttemptSnapshot snapshot=evidence.observation;
        return attemptOwner.owned()?attemptOwner.canReleaseRecovered(snapshot,false,false,"")
            :OemAttemptOwner.unownedCleanupReady(snapshot,policy.paused);
    }
    boolean retireOriginalAttempt() throws Exception {
        SessionEvidence evidence=runtimeEvidence();
        if(evidence==null || !evidence.inactive())return false;
        if(!attemptOwner.owned())return OemAttemptOwner.unownedCleanupReady(evidence.observation,policy.paused);
        if(!attemptOwner.canReleaseRecovered(evidence.observation,false,false,""))return false;
        attemptOwner.releasedRecovered(evidence.observation,false,false,"");return true;
    }
    void state(String name,String value) throws Exception {write(new File(STATE,name),value);}
    void clearState(String name) throws Exception {
        File file=new File(STATE,name);
        if(file.exists() && !file.delete())throw new IOException("state-removal-failed");
    }
    void commandStage(String stage,String reason) throws Exception {
        if(activeCommand>0)state("command-outcome",activeCommand+" "+stage+" "+reason);
    }
    boolean projectionReleasePending,projectionReleaseNormal;
    String projectionReleaseReason,projectionReleaseTarget;
    void prepareProjectionRelease(boolean normal,String reason) {
        lastReleaseCheck="";projectionReleasePending=true;projectionReleaseNormal=normal;
        projectionReleaseReason=reason;projectionReleaseTarget=policy.target;
        policy.ended();needUserStop=false;phase="reservation-release-pending";
    }
    private String lastReleaseCheck="";
    private void releaseCheck(String condition) {
        if(!condition.equals(lastReleaseCheck)){
            android.util.Log.i("EV6ReleaseCheck",condition+" elapsedMs="+SystemClock.elapsedRealtime());
            lastReleaseCheck=condition;
        }
    }
    boolean retryProjectionRelease() throws Exception {
        if(!projectionReleasePending)return false;
        phase="reservation-release-pending";busy(false);
        String waiting="";
        try {
            BluetoothWorkClient.Snapshot bluetooth=readBluetoothWork();
            if(!read(new File(STATE,"bluetooth-cleanup-result"),100).equals("original-work-settled"))
                waiting="bluetooth-cleanup-receipt";
            else if(bluetooth==null||!bluetooth.settled())waiting="bluetooth-work";
            else if(!originalCleanupReady())waiting="original-projection-cleanup";
        }catch(Exception unavailable){waiting="cleanup-query-unavailable";}
        if(waiting.length()==0 && !ControllerProjectionReservation.releaseCompleted(true))
            waiting="reservation-release-receipt";
        if(waiting.length()>0){
            releaseCheck(waiting);
            commandStage("pending","projection-reservation-release-unverified");
            mark("projection-reservation-release-unverified");return false;
        }
        releaseCheck("released");projectionReleasePending=false;return true;
    }
    void resumeProjectionRelease() throws Exception {
        if(!projectionReleasePending)return;
        if(projectionReleaseNormal)ended();else fail(projectionReleaseReason);
    }
    private long idleClosePollAt;
    private byte[] idleBytes(File file)throws Exception {
        if(!file.isFile()||!file.getAbsoluteFile().equals(file.getCanonicalFile())||file.length()>8192)throw new IOException("idle-snapshot-path");
        byte[] bytes=new byte[(int)file.length()];DataInputStream in=new DataInputStream(new FileInputStream(file));
        try{in.readFully(bytes);if(in.read()!=-1)throw new IOException("idle-snapshot-changed");}finally{in.close();}return bytes;
    }
    private void idleCopy(File source,File target)throws Exception {
        byte[] bytes=idleBytes(source);
        if(target.exists()) {if(!Arrays.equals(bytes,idleBytes(target)))throw new IOException("idle-snapshot-conflict");return;}
        if(!target.createNewFile())throw new IOException("idle-snapshot-create");
        FileOutputStream out=new FileOutputStream(target);try{out.write(bytes);out.getFD().sync();}finally{out.close();}
        DirectorySync.sync(target.getParentFile());
    }
    private boolean closeIdleProjectionAp(boolean nativeActive)throws Exception {
        final boolean abort=ProjectionApAbort.selectAbort(
            new File(STATE,"ap-idle-close.properties").exists(),
            new File(STATE,"ap-startup-abort.properties").exists(),
            exclusiveRuntime!=null && exclusiveRuntime.cpCleanupAuthorized(),
            org.ev6.projection.ProjectionReservation.store().apPendingStatus()==org.ev6.projection.ReservationStore.VALID);
        final File journal=new File(STATE,abort?"ap-startup-abort.properties":"ap-idle-close.properties");
        boolean resuming=journal.exists();
        if(!resuming && !abort && (!ap || cpApDemand.current()!=null || pendingTarget.length()>0))return false;
        if(nativeActive || attemptOwner.owned() || policy.connected || policy.disconnecting || projectionReleasePending)return resuming;
        long now=SystemClock.elapsedRealtime();if(now<idleClosePollAt)return resuming;idleClosePollAt=now+2000;
        final String boot=ProjectionApOwnerRecord.readBoot(new File("/proc/sys/kernel/random/boot_id")).replace("-","");
        final org.ev6.projection.ReservationStore store=org.ev6.projection.ProjectionReservation.store();
        try {
            if(!resuming && AaApDemandClient.pendingRoots())return false;
            ProjectionIdleClose.RebootPort closePort=new ProjectionIdleClose.RebootPort(){
                final String[] names={"ap-owner","saved-client-state","ap-startup-"+boot+".journal","ap-subnet.original","ap-firewall.owner","aa-firewall.owner","aa-firewall.owner.next"};
                public void requireIdle()throws Exception {
                    if(abort && (exclusiveRuntime==null||!exclusiveRuntime.cpCleanupAuthorized()))throw new IOException("abort-start-grant-not-revoked");
                    requireStartupCohort();BluetoothWorkClient.Snapshot bt=readBluetoothWork();
                    // Preserve the exact first blocking condition. A generic work-present
                    // error hid whether native teardown or unrelated OEM reconnect work
                    // prevented AP closure after a wireless link failure.
                    boolean nativeActive=nativeSession.active(),attempt=attemptOwner.owned();
                    boolean projectionBusy=nativeActive||attempt||policy.connected||policy.disconnecting||projectionReleasePending;
                    boolean cleanupReady=projectionBusy||originalCleanupReady();
                    boolean bluetoothReady=bt!=null&&bt.settled();
                    if(!projectionBusy&&cleanupReady&&bt!=null&&!bluetoothReady&&exclusiveRuntime!=null){
                        bluetoothReady=ProjectionIdleBlocker.networkCompatible(false,
                            exclusiveRuntime.aaNetworkDrainAuthorized(),bt.state,bt.busy,bt.automatic,bt.inactive,bt.process);
                    }
                    String blocked=ProjectionIdleBlocker.reason(nativeActive,attempt,
                        policy.connected,policy.disconnecting,projectionReleasePending,
                        cleanupReady,bt!=null,bluetoothReady);
                    if(blocked.length()>0){state("ap-idle-wait-reason",blocked);throw new IOException("idle-"+blocked);}
                    clearState("ap-idle-wait-reason");
                }
                public void snapshotAfterReboot(File dir,String priorBoot)throws Exception {
                    if(priorBoot.equals(boot))throw new IOException("idle-reboot-required");
                    if(new File(STATE,"ap-startup-"+boot+".journal").exists())throw new IOException("idle-reboot-new-startup-present");
                    names[2]="ap-startup-"+priorBoot+".journal";
                    for(int i=0;i<names.length;i++){
                        File source=new File(STATE,names[i]),copy=new File(dir,names[i]);
                        if(source.exists())idleCopy(source,copy);else if(i<3&&!copy.isFile())throw new IOException("idle-reboot-evidence-missing");
                    }
                    if(!priorBoot.equals(ProjectionApOwnerRecord.readBoot(new File(dir,"ap-owner")).replace("-","")))throw new IOException("idle-reboot-owner");
                    SavedClientDurability.readSaved(new File(dir,"saved-client-state"));
                    File start=new File(dir,names[2]);String[] header=new String(idleBytes(start),"US-ASCII").split("\n",-1)[0].split("\\|",-1);
                    if(header.length!=5)throw new IOException("idle-reboot-header");
                    org.ev6.projection.ReservationStore.Ticket old=org.ev6.projection.ReservationStore.apJournalIdentity(header[1]);
                    if(!priorBoot.equals(old.boot)||new ProjectionApJournal(start,old,ProjectionApJournal.ANDROID_SYNC).inspect().stage!=ProjectionApJournal.READY)throw new IOException("idle-reboot-startup-incomplete");
                }
                public void requireRebootStopped(File dir)throws Exception {
                    requireIdle();
                    int client=wifiState("getWifiEnabledState");
                    if(wifiState("getWifiApEnabledState")!=11||(client!=1&&client!=3)
                        ||!exec(null,5000,"/system/bin/ndc","tether","status").trim().equals("210 0 Tethering services stopped"))throw new IOException("idle-reboot-network-active");
                    String fw=exec(null,5000,"/system/bin/iptables","-S");
                    String ready=exec(null,5000,"/system/bin/getprop","sys.ev6.cp.apready").trim();
                    if(!fw.contains("-P INPUT ")||fw.contains("ev6_cp_reply")||(!ready.equals("")&&!ready.equals("0")))throw new IOException("idle-reboot-network-unconfirmed");
                }
                public void restoreClientAfterReboot(File dir)throws Exception {
                    requireRebootStopped(dir);
                    int desired=SavedClientDurability.readSaved(new File(dir,"saved-client-state"));
                    if(wifiState("getWifiEnabledState")==desired)return;
                    File input=new File(dir,"client-input");idleCopy(new File(dir,"saved-client-state"),input);
                    String reply=runTool("OemApSwitch","stop",input.getPath(),new File(BACKEND,"event-state").getPath());
                    if(!reply.contains("OEM_SWITCH_COMPLETE"))throw new IOException("idle-reboot-client-unconfirmed");
                }
                public void snapshot(File dir)throws Exception {
                    File ownerFile=new File(STATE,"ap-owner"),savedFile=new File(STATE,"saved-client-state");
                    if(ownerFile.exists()&&!boot.equals(ProjectionApOwnerRecord.readBoot(ownerFile).replace("-","")))throw new IOException("idle-ap-owner");
                    File start=new File(STATE,names[2]);String[] header=new String(idleBytes(start),"US-ASCII").split("\n",-1)[0].split("\\|",-1);
                    if(header.length!=5)throw new IOException("idle-startup-header");
                    org.ev6.projection.ReservationStore.Ticket started=org.ev6.projection.ReservationStore.apJournalIdentity(header[1]);
                    int stage=new ProjectionApJournal(start,started,ProjectionApJournal.ANDROID_SYNC).inspect().stage;
                    if(!boot.equals(started.boot)||(!abort&&stage!=ProjectionApJournal.READY))throw new IOException("idle-startup-incomplete");
                    boolean early=abort&&stage<=ProjectionApJournal.CLIENT_SAVE_PENDING;
                    if(!ownerFile.exists()&&!early)throw new IOException("idle-ap-owner-missing");
                    if(savedFile.exists())SavedClientDurability.readSaved(savedFile);
                    else if(!early||wifiState("getWifiApEnabledState")!=11)throw new IOException("idle-saved-preference-missing");
                    int apState=wifiState("getWifiApEnabledState");
                    if((apState!=13||wifiState("getWifiApMode")!=2)&&!(abort&&apState==11))throw new IOException("idle-radio-not-owned");
                    String tether=exec(null,5000,"/system/bin/ndc","tether","interface","list");
                    boolean complete=false;for(String line:tether.trim().split("\n")){
                        String[] f=line.trim().split("\\s+");if(f.length>=3&&f[0].equals("200"))complete=true;
                        else if(f.length!=3||!f[0].equals("111")||!f[2].equals("wlan0"))throw new IOException("idle-foreign-tether");
                    }
                    if(!complete)throw new IOException("idle-tether-unknown");
                    for(int i=0;i<names.length;i++){
                        File source=new File(STATE,names[i]),copy=new File(dir,names[i]);
                        if(source.exists())idleCopy(source,copy);else if(i==1&&early){
                            int desired=wifiState("getWifiEnabledState");if(desired!=1&&desired!=3)throw new IOException("abort-client-transition");
                            FileOutputStream out=new FileOutputStream(copy);try{out.write(desired);out.getFD().sync();}finally{out.close();}DirectorySync.sync(dir);
                        }else if(i<3&&!(i==0&&early))throw new IOException("idle-evidence-missing");
                    }
                }
                public void stop(File dir)throws Exception {
                    if(!abort && (!Arrays.equals(idleBytes(new File(STATE,"ap-owner")),idleBytes(new File(dir,"ap-owner")))
                        || !Arrays.equals(idleBytes(new File(STATE,"saved-client-state")),idleBytes(new File(dir,"saved-client-state")))))throw new IOException("idle-owner-changed");
                    if(abort){
                        File owner=new File(dir,"ap-owner");
                        if(owner.exists()&&!new File(STATE,"ap-owner").exists())throw new IOException("abort-owner-disappeared");
                        if(!new File(STATE,"saved-client-state").exists()){
                            File evidence=new File(dir,names[2]);String[] h=new String(idleBytes(evidence),"US-ASCII").split("\n",-1)[0].split("\\|",-1);
                            if(h.length!=5)throw new IOException("abort-start-header");
                            int stage=new ProjectionApJournal(evidence,org.ev6.projection.ReservationStore.apJournalIdentity(h[1]),ProjectionApJournal.ANDROID_SYNC).inspect().stage;
                            if(stage>ProjectionApJournal.CLIENT_SAVE_PENDING||wifiState("getWifiApEnabledState")!=11)throw new IOException("abort-saved-preference-disappeared");
                        }
                    }
                    for(String name:names){File source=new File(STATE,name);
                        if(source.exists()&&!Arrays.equals(idleBytes(source),idleBytes(new File(dir,name))))throw new IOException("idle-effect-preimage-changed");
                    }
                    prop("sys.ev6.cp.apready","0");networkFirewall("restore");
                    if(new File(STATE,"ap-subnet.original").exists()){
                        if(wifiState("getWifiApEnabledState")!=13)throw new IOException("idle-subnet-recovery-required");
                        subnet("restore");
                    }
                    File input=new File(dir,"client-input");idleCopy(new File(dir,"saved-client-state"),input);
                    String reply=runTool("OemApSwitch","stop",input.getPath(),new File(BACKEND,"event-state").getPath());
                    if(!reply.contains("OEM_SWITCH_COMPLETE"))throw new IOException("idle-stop-unconfirmed");
                }
                public void verifyStopped(File dir)throws Exception {
                    int desired=SavedClientDurability.readSaved(new File(dir,"saved-client-state"));
                    if(wifiState("getWifiApEnabledState")!=11||wifiState("getWifiEnabledState")!=desired
                        || !exec(null,5000,"/system/bin/ndc","tether","status").trim().equals("210 0 Tethering services stopped"))throw new IOException("idle-radio-readback");
                    String fw=exec(null,5000,"/system/bin/iptables","-S");
                    if(!fw.contains("-P INPUT ")||fw.contains("ev6_cp_reply"))throw new IOException("idle-firewall-readback");
                }
                public void cleanup(File dir)throws Exception {
                    for(String name:names){File source=new File(STATE,name);if(!source.exists())continue;
                        if(!Arrays.equals(idleBytes(source),idleBytes(new File(dir,name))))throw new IOException("idle-cleanup-foreign-file");
                        if(!source.delete())throw new IOException("idle-cleanup-delete");DirectorySync.sync(STATE);
                    }
                }
            };
            boolean done=abort?new ProjectionApAbort(journal,new File(STATE,"ap-startup-"+boot+".journal"),store,ProjectionApJournal.ANDROID_SYNC,closePort).run()
                :new ProjectionIdleClose(journal,boot,store,ProjectionApJournal.ANDROID_SYNC,closePort).run();
            if(done){ap=false;startupAttempted=false;startupBluetoothRetry=false;startupRecoveryAttempts=0;startupFirstFailure=null;state("projection-idle-close","wifi-preference-restored");}
            if(done&&abort)mark("interrupted-ap-cleanup-complete");
            return abort||resuming||done;
        }catch(Exception blocked){state("projection-idle-close","blocked:"+blocked.getMessage());return abort||resuming;}
    }
    private AaApDemandClient.Demand startingAaDemand;
    private long aaDemandPollAt;
    private void requireAaDemandLive()throws Exception {
        if(startingAaDemand!=null && (cpApDemand.current()!=null || !AaApDemandClient.live(startingAaDemand)))
            throw new IOException("aa-ap-demand-withdrawn");
    }
    private boolean resumeAaApDemand(boolean nativeActive)throws Exception {
        if((!ap && startupAttempted && startupRecoveryAttempts>=3) || nativeActive || attemptOwner.owned() || policy.connected
            || policy.disconnecting || projectionReleasePending || cpApDemand.current()!=null)return false;
        long now=SystemClock.elapsedRealtime();if(now<aaDemandPollAt)return false;aaDemandPollAt=now+2000;
        try {
            AaApDemandClient.Demand[] demands=AaApDemandClient.pending();
            if(demands.length==0)return false;
            if(demands.length!=1){state("aa-ap-demand","multiple-requests-await-selection");return true;}
            if(!originalCleanupReady())return true;
            startingAaDemand=demands[0];requireAaDemandLive();
            // Arm before any AP effect; keep the exact root pending until durable
            // AP ownership release. Failed IPC is retried next tick, even if ap=true.
            if(!AaApDemandClient.handoff(startingAaDemand,false))throw new IOException("aa-ap-handoff-arm-rejected");
            // ap is physical readiness and may already be true in SWITCH_PENDING.
            // Always revalidate/recover the durable reservation before acknowledging.
            boolean ready=prepareSharedAp();
            if(ready){requireAaDemandLive();aaNetworkFirewall(startingAaDemand);requireAaDemandLive();}
            if(ready&&!AaApDemandClient.handoff(startingAaDemand,true))throw new IOException("aa-ap-handoff-release-rejected");
            state("aa-ap-demand",ready?"network-ready-awaiting-original-aa":"waiting-ap-bootstrap");
            return true;
        }catch(Exception unavailable){state("aa-ap-demand","unavailable-or-withdrawn:"+unavailable.getMessage());return false;}
        finally{startingAaDemand=null;}
    }
    private final CarPlayApDemand cpApDemand=new CarPlayApDemand();
    private long cpApDemandCheckAt;
    void beginFirstPair(String target,String request)throws Exception {
        exclusiveRuntime.verifyCpStart(target);
        if(!bonded(target)||projectionReleasePending||attemptOwner.owned()||policy.connected||policy.disconnecting||nativeSession.active()||!originalCleanupReady())throw new IOException("first-setup-no-longer-idle");
        cpApDemand.selectFirstSetup(target,request,lastCommand,SystemClock.elapsedRealtime());
        policy.paused=false;clearState("manual-pause");
        cpApDemandCheckAt=0;
        mark("first-pair-waiting-bluetooth-before-ap");
    }
    void promoteManualDemand(String target)throws Exception {
        CarPlayApDemand.Request prior=cpApDemand.current();
        if(prior==null || prior.manual || !prior.target.equals(target))return;
        // A new explicit command can renew its waiting window, but cannot resend
        // Bluetooth work already submitted by this same pending demand.
        CarPlayApDemand.Request selected=cpApDemand.select(target,true,lastCommand,SystemClock.elapsedRealtime());
        if(prior.bluetoothRequested())cpApDemand.claimBluetoothRequest(selected);
        cpApDemandCheckAt=0;
    }
    void begin(String target,boolean manual)throws Exception {
        if(exclusiveRuntime!=null&&!exclusiveRuntime.starting()) {exclusiveRuntime.cpRequest(target,manual);return;}
        if(!bonded(target) || (!manual && policy.paused))return;
        if(projectionReleasePending || attemptOwner.owned() || policy.connected || policy.disconnecting
            || nativeSession.active() || !OemAttemptOwner.submissionReady(originalObservation())) {
            if(manual)pendingTarget=target;
            mark("waiting-original-work-before-demand");return;
        }
        long recoveryRemaining=!manual&&exclusiveRuntime!=null?exclusiveRuntime.cpRecoveryRemaining(target):0;
        if(recoveryRemaining>0)cpApDemand.selectRecovery(target,exclusiveRuntime.demandRequestId(),lastCommand,SystemClock.elapsedRealtime(),recoveryRemaining);
        else cpApDemand.select(target,manual,lastCommand,SystemClock.elapsedRealtime());
        cpApDemandCheckAt=0;
        commandStage("pending","waiting-selected-bluetooth-before-ap");
        mark("waiting-selected-bluetooth-before-ap");
    }
    private boolean cancelPendingDemand(String action,String target)throws Exception {
        CarPlayApDemand.Request request=cpApDemand.current();
        if(request==null)return false;
        if(action.equals("connect")) {
            cpApDemand.clear(request);begin(target,true);return true;
        }
        if(!target.equals(request.target)) {
            commandStage("rejected","stop-target-not-selected");return true;
        }
        cpApDemand.clear(request);startupBluetoothRetry=false;
        policy.paused=true;pausedAt=SystemClock.elapsedRealtime();
        state("manual-pause",Long.toString(pausedAt));
        // Do not cancel unrelated/original BT work just to retire our demand.
        commandStage("completed","pending-demand-cancelled");mark("paused-demand-cancelled");return true;
    }
    private boolean resumePendingDemand(boolean nativeActive)throws Exception {
        CarPlayApDemand.Request request=cpApDemand.current();
        if(request==null)return false;
        long now=SystemClock.elapsedRealtime();
        if((exclusiveRuntime!=null&&!cpApDemand.belongsTo(request,exclusiveRuntime.demandRequestId()))||!cpApDemand.valid(request,now,bonded(request.target),policy.paused,lastCommand)) {
            cpApDemand.clear(request);startupBluetoothRetry=false;
            phase="error";commandStage("rejected","pending-demand-expired-or-invalidated");mark("pending-demand-expired-or-invalidated");return true;
        }
        if(request.recovery()&&(exclusiveRuntime==null||exclusiveRuntime.cpRecoveryRemaining(request.target)<=0)) {
            cpApDemand.clear(request);startupBluetoothRetry=false;phase="error";mark("pending-recovery-authority-expired");return true;
        }
        if(nativeActive || attemptOwner.owned() || policy.connected || policy.disconnecting) {
            cpApDemand.clear(request);startupBluetoothRetry=false;
            mark("pending-demand-yielded-to-original-session");return true;
        }
        if(now<cpApDemandCheckAt)return true;
        cpApDemandCheckAt=now+2000;
        if(projectionReleasePending || !originalCleanupReady())return true;
        if(org.ev6.projection.ProjectionReservation.store().maintenanceRequested()) {
            cpApDemand.clear(request);startupBluetoothRetry=false;mark("pending-demand-maintenance-cancelled");return true;
        }
        if(!projectionBluetoothReady()){
            state("ap-demand-result","elapsed="+now+" expires="+request.expires+" waiting-bluetooth recovery="+startupRecoveryAttempts);
            mark(startupAttempted?"waiting-bluetooth-before-ap-recovery":"waiting-bluetooth-before-ap-start");return true;
        }
        if(!request.target.equals(hfp())) {
            if(!request.manual&&!request.firstSetup()&&!request.recovery()){cpApDemand.clear(request);startupBluetoothRetry=false;phase="error";mark("pending-demand-selected-hfp-lost");return true;}
            if(request.recovery())exclusiveRuntime.verifyCpStart(request.target);
            Object manager=bt();if(manager==null)return true;
            boolean partial=listedByBluetooth(manager,request.target);
            // Record dispatch before Binder: an exception must not cause duplicate requests.
            if(!cpApDemand.claimBluetoothRequest(request))return true;
            boolean accepted=(Boolean)call(manager,"com.broadcom.bt.hfdevice.hfbtcm.IHfDeviceBTCMService",
                partial?"btcmOnlyHfpConnect":"btcmConnect",new Class<?>[]{String.class},request.target);
            // A false Binder return is not an observed disconnect. OEM pairing may
            // already be connecting this target. Keep this same bounded request,
            // never redispatch Bluetooth, and wait for the actual HFP observation.
            commandStage("pending",accepted?"bluetooth-request-submitted":"bluetooth-request-not-accepted-awaiting-observation");
            mark(accepted?"pending-demand-bluetooth-submitted":"pending-demand-bluetooth-awaiting-observation");
            return true;
        }
        // Recheck the user command before AP effects; the next tick consumes a newer command.
        command();
        if(!cpApDemand.valid(request,SystemClock.elapsedRealtime(),bonded(request.target),policy.paused,lastCommand))return true;
        int outcome=beginAdmitted(request.target,request.manual);
        if(outcome!=0){
            cpApDemand.clear(request);
            if(outcome==2){phase="error";mark("pending-demand-admission-rejected");}
            else mark("pending-demand-transferred-to-session");
        }
        return true;
    }
    int beginAdmitted(String target,boolean manual) throws Exception {
        if(projectionReleasePending) {
            if(manual)pendingTarget=target;
            commandStage("pending","projection-reservation-release-unverified");return 0;
        }
        if(nativeSession.active()) {mark("active-session-blocks-new-request");return 0;}
        OemAttemptSnapshot baseline=originalObservation();
        if(attemptOwner.owned() || !OemAttemptOwner.submissionReady(baseline)) {
            if(manual)pendingTarget=target;
            phase="settling-original";mark("waiting-original-work-before-new-selection");return 0;
        }
        if(!bonded(target))return 2;
        if(exclusiveRuntime!=null)exclusiveRuntime.verifyCpStart(target);
        if(!prepareSharedAp())return startupBluetoothRetry?0:2;
        if(exclusiveRuntime!=null)exclusiveRuntime.verifyCpStart(target);
        if(!ControllerProjectionReservation.acquire(target)) {
            String reason=ControllerProjectionReservation.acquisitionReason();
            state("cp-admission-result","elapsed="+SystemClock.elapsedRealtime()+" reason="+reason);
            if(reason.equals("store-lock-busy")){startupBluetoothRetry=true;commandStage("pending","projection-reservation-lock-busy");mark("waiting-projection-reservation-lock");return 0;}
            commandStage("rejected","projection-reservation-"+reason);
            mark("projection-reservation-"+reason);return 2;
        }
        if(!policy.begin(target,manual)){ControllerProjectionReservation.abandonPrepared();return 2;}
        if(!ControllerProjectionReservation.effects())throw new IOException("projection-reservation-commit-failed");
        preparationBaseline=baseline;
        clearState("bluetooth-cleanup-result");
        bluetoothPinned=null;bluetoothCancelSent=false;bluetoothResult="none";
        closeDiscovery();
        new File(STATE,"manual-pause").delete();
        manualSelection=manual;
        if(manual)pinBluetoothWork();
        phase="preparing";btRequested=false;selectedPeer="";
        oemConnectSubmitted=false;btDeferred=false;oemEndDeadline=0;pendingFailure="";
        deadline=SystemClock.elapsedRealtime()+90000;
        busy(true);mark(manual?"manual-connect-preparing":"bluetooth-auto-connect-preparing");
        startAp();phase="waiting-native";knownPeerDeadline=SystemClock.elapsedRealtime()+4000;
        Object manager=bt();
        boolean missingPhone=manager!=null && listedByBluetooth(manager,target) && !target.equals(hfp());
        if(!manual || knownPeers.getProperty(target)==null || missingPhone)requestBt();
        recoverManualIap2();
        return 1;
    }
    String observedPairingEpoch() throws Exception {
        java.util.regex.Matcher match=java.util.regex.Pattern.compile("^epoch=([1-9][0-9]*) ").matcher(read(new File(BACKEND,"pairing.state"),512));
        return match.find()?match.group(1):"";
    }
    /** Check actual iAP2 validity once for an explicit user selection. The
     * backend may preserve a valid link, recover a proved invalid link, or
     * recover a failed bootstrap only on a NEW user retry of that BT epoch.
     */
    void recoverManualIap2() throws Exception {
        if(!manualSelection || lastCommand<=0 || !policy.target.equals(hfp()))return;
        String epoch=observedPairingEpoch();if(epoch.length()==0)return;
        if(nativeSession.active() || backendSessionActive() || !originalCleanupReady()
            || wifiState("getWifiApEnabledState")!=13 || !new File(STATE,"ap-owner").exists())return;
        long prior=policy.target.equals(failedBootstrapTarget) && epoch.equals(failedBootstrapEpoch)
            && failedBootstrapCommand<lastCommand?failedBootstrapCommand:0;
        // Consume before dispatch. A dropped/rejected action cannot become a loop.
        failedBootstrapCommand=0;failedBootstrapTarget="";failedBootstrapEpoch="";
        String[] command={"/system/bin/am","startservice","-n","org.ev6.wirelesscarplay/org.ev6.research.ProbeService",
            "-a","org.ev6.research.RECOVER_MANUAL_IAP2","--es","target",policy.target,"--es","epoch",epoch,
            "--el","token",Long.toString(lastCommand),"--el","failed-token",Long.toString(prior),
            "--el","issued",Long.toString(SystemClock.elapsedRealtime())};
        exec(null,10000,command);mark("manual-iap2-state-check-requested");
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
            firstPairs.observed(target,event.newlyPaired(),baseline,!active&&!policy.connected,event.when,SystemClock.elapsedRealtime(),pausedAt);
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
    private final ProjectionCpHintClient cpHints=new ProjectionCpHintClient();
    private void publishCpSessionHint(){
        try{final String selected=policy.target;final SessionEvidence initial=runtimeEvidence();
            if(initial==null||!initial.activeFor(selected)||initial.observation.actualSessionTransport!=2)return;
            final String receiver=initial.observation.receiver;final long epoch=initial.observation.sessionEpoch;
            if(cpHints.publish(selected,receiver,epoch,new ProjectionCpHintClient.Port(){public void verify()throws Exception{
                SessionEvidence now=runtimeEvidence();if(!bonded(selected)||!selected.equals(policy.target)||now==null||!now.activeFor(selected)||now.observation.actualSessionTransport!=2||!receiver.equals(now.observation.receiver)||epoch!=now.observation.sessionEpoch)throw new IOException("cp-hint-session-changed");
            }}))state("cp-hint-result","verified-session-remembered");
        }catch(Exception waiting){stateQuietCpHint();}
    }
    private void stateQuietCpHint(){try{state("cp-hint-result","waiting-live-broker-or-session");}catch(Exception ignored){}}
    void started() throws Exception {
        SessionEvidence evidence=runtimeEvidence();
        if(evidence!=null && pendingTarget.length()>0 && evidence.activeFor(pendingTarget)
                && attemptOwner.canTransferTo(evidence.observation,pendingTarget)) {
            // An independent original USB/BT producer may already have started
            // the explicitly selected successor. Adopt that real session once
            // its predecessor's full finalized receipt is verified; do not
            // wait for all native activity to vanish or submit a duplicate.
            String next=pendingTarget;attemptOwner.transferTo(evidence.observation,next);
            policy.ended();policy.begin(next,true);pendingTarget="";needUserStop=false;
            clearState("manual-pause");
        }
        if(evidence==null || !evidence.activeFor(policy.target)) {
            mark("active-session-awaiting-consistent-evidence");return;
        }
        if(attemptOwner.owned() && !attemptOwner.observeSession(evidence.observation,true,
                evidence.backendState.equals("1"),true,evidence.association)) {
            mark("active-session-owner-needs-review");return;
        }
        preparationBaseline=null;
        boolean stopPending=policy.disconnecting;
        policy.started();failedBootstrapCommand=0;failedBootstrapTarget="";failedBootstrapEpoch="";
        phase="connected";lastHfp="";closeDiscovery();
        if(evidence.observation.actualSessionTransport==2){savePeer();publishCpSessionHint();}
        state("current-target",policy.target);busy(false);
        mark(evidence.observation.actualSessionTransport==1?"original-wired-session-connected":"genuine-carplay-connected");
        if(!stopPending && pendingTarget.length()==0)commandStage("completed","actual-session-verified");
        if(stopPending || pendingTarget.length()>0){if(pendingTarget.length()>0)pendingStopManual=false;needUserStop=true;}
    }
    boolean pendingStopManual=true;
    void disconnect() throws Exception {disconnect(true);}
    void disconnect(boolean manualStop) throws Exception {
        if(policy.target.length()==0 || policy.disconnecting)return;
        // Persist explicit stop intent even when a diagnostic read is briefly
        // unavailable; retry only the evidence read until original STOP is sent.
        pendingStopManual=manualStop;
        if(manualStop){firstPairs.clear();policy.paused=true;pausedAt=SystemClock.elapsedRealtime();bonds.clearSetups();
        state("manual-pause",Long.toString(pausedAt));}
        boolean submitted=policy.connected || oemConnectSubmitted || attemptOwner.owned();
        if(submitted) {
            SessionEvidence evidence=runtimeEvidence();
            if(evidence==null || !evidence.canSubmitStop(policy.target,true)) {
                needUserStop=true;commandStage("pending","stop-evidence-unavailable");
                mark("stop-awaiting-consistent-original-state");return;
            }
        }
        failedBootstrapCommand=0;failedBootstrapTarget="";failedBootstrapEpoch="";
        ProjectionStopPolicy.apply(policy,manualStop);
        phase="disconnecting";
        deadline=SystemClock.elapsedRealtime()+15000;busy(true);
        // Preserve the existing OEM targeted disconnectPP path and arguments.
        if(submitted)pp("disconnectPP",new Class<?>[]{int.class,String.class,int.class},1,policy.target,1);
        else {phase="settling-original";closeDiscovery();}
        commandStage("submitted","original-stop-requested");mark("manual-disconnect-awaiting-finalized");
    }
    void ended() throws Exception {
        if(projectionReleasePending && !projectionReleaseNormal){resumeProjectionRelease();return;}
        if(!projectionReleasePending) {
        if(!retireOriginalAttempt()){
            busy(false);commandStage("pending","original-completion-unverified");
            mark("waiting-original-work-before-cleanup");return;
        }
        if(!finishBluetooth("original-stop"))return;
        closeDiscovery();
        String address=(String)pp("getCPConnectedBTAddress",new Class<?>[0]);
        if(!ConnectionPolicy.originalAssociationCleared(address))throw new IOException("oem-association-not-cleared");
        prepareProjectionRelease(true,"original-stop");
        }
        if(!retryProjectionRelease())return;
        policy.ended();phase="idle";clearState("current-target");
        oemEndDeadline=0;oemConnectSubmitted=false;pendingFailure="";
        String next=pendingTarget;pendingTarget="";
        String btResult=read(new File(STATE,"bluetooth-cleanup-result"),100);
        if(!btResult.equals("original-work-settled")) {
            retainProjectionNetwork();preparationBaseline=null;busy(false);
            commandStage("pending","bluetooth-work-unresolved");mark("original-stop-bluetooth-work-unresolved");return;
        }
        // An explicit switch still waits for genuine finalization. Reuse only
        // our own verified projection AP; a plain user stop retains the shared projection network.
        if(next.length()>0 && bonded(next) && ap && new File(STATE,"ap-owner").exists()
            && wifiState("getWifiApEnabledState")==13 && wifiState("getWifiApMode")==2) {
            mark("switch-reusing-projection-ap");begin(next,true);
            if(policy.target.length()==0)fail("switch-target-unavailable-network-retained");
            return;
        }
        retainProjectionNetwork();preparationBaseline=null;busy(false);mark(policy.paused?"paused-network-retained":"disconnected-network-retained");
        if(next.length()>0)begin(next,true);
        else commandStage("completed","original-stop-verified");
    }
    /** Called ONLY for a newly accepted explicit native-UI selection command.
     * An original active session may have started over USB without this
     * wireless worker owning it. Adopt its observed identity for STOP only;
     * do not claim a published wireless session or start a second session.
     */
    void switchOriginalSessionByUser(String next) throws Exception {
        if(policy.target.length()!=0 || attemptOwner.owned()) {
            commandStage("pending","original-selection-changing");return;
        }
        SessionEvidence evidence=runtimeEvidence();
        if(evidence==null || !evidence.nativeActive || !evidence.controlOwner
                || !bonded(next) || validTarget(evidence.association).length()==0) {
            commandStage("rejected","original-session-unverified");
            mark("manual-switch-original-owner-needs-review");return;
        }
        if(evidence.association.equalsIgnoreCase(next)) {
            commandStage("completed","selected-device-already-active");return;
        }
        attemptOwner.adoptForStop(evidence.observation,evidence.association,true,true,evidence.association);
        policy.target=evidence.association;oemConnectSubmitted=true;pendingTarget=next;
        disconnect(false);
    }
    void command() throws Exception {
        String command=read(new File(BUTTON,"command"),128);
        if(!command.matches("[1-9][0-9]{0,18} (connect|disconnect) (?:[0-9A-F]{2}:){5}[0-9A-F]{2}"))return;
        String[] fields=command.split(" ");long token=Long.parseLong(fields[0]);
        if(token<=lastCommand)return;
        lastCommand=token;activeCommand=token;
        state("command-ack",fields[0]); // Consumption only; outcome has its own state.
        commandStage("consumed",fields[1]);handleSelection(fields[1],validTarget(fields[2]));
    }
    ProjectionExclusiveRuntime exclusiveRuntime;
    boolean exclusivePreparationActive(){return cpApDemand.current()!=null||policy.target.length()>0||attemptOwner.owned()||projectionReleasePending||policy.disconnecting;}
    void handleSelection(String action,String target) throws Exception {
        firstPairs.clear(); // Explicit stop or switch supersedes every pending first setup.
        if(exclusiveRuntime!=null){
            if(action.equals("connect")){if(!bonded(target)){commandStage("rejected","device-not-paired");return;}if(exclusiveRuntime.cpRequest(target,true))commandStage("pending","exclusive-handoff-requested");return;}
            exclusiveRuntime.userStop(target);
        }
        handleSelectionAdmitted(action,target);
    }
    void handleSelectionAdmitted(String action,String target) throws Exception {
        if(!bonded(target)){commandStage("rejected","device-not-paired");return;}
        if(cancelPendingDemand(action,target))return;
        if(projectionReleasePending) {
            if(action.equals("disconnect")) {
                if(!target.equals(projectionReleaseTarget) && !target.equals(pendingTarget)) {
                    commandStage("rejected","stop-target-not-selected");return;
                }
                pendingTarget="";policy.paused=true;pausedAt=SystemClock.elapsedRealtime();
                state("manual-pause",Long.toString(pausedAt));
            }else pendingTarget=target;
            commandStage("pending","projection-reservation-release-unverified");return;
        }
        if(phase.equals("settling-bluetooth") && action.equals("disconnect")) {
            pendingTarget="";policy.paused=true;pausedAt=SystemClock.elapsedRealtime();
            state("manual-pause",Long.toString(pausedAt));
            commandStage("pending","original-bluetooth-canceling");return;
        }
        if(action.equals("disconnect")) {
            if(target.equals(policy.target)){pendingTarget="";disconnect();}
            else if(policy.target.length()==0 && !attemptOwner.owned()) {
                SessionEvidence evidence=runtimeEvidence();
                if(evidence!=null && evidence.nativeActive && evidence.controlOwner
                        && target.equalsIgnoreCase(evidence.association)) {
                    attemptOwner.adoptForStop(evidence.observation,target,true,true,evidence.association);
                    policy.target=target;oemConnectSubmitted=true;pendingTarget="";disconnect();
                } else commandStage("rejected","stop-target-not-active");
            } else commandStage("rejected","stop-target-not-selected");
            return;
        }
        if(nativeSession.active() && !policy.connected && policy.target.length()==0 && !attemptOwner.owned()) {
            switchOriginalSessionByUser(target);return;
        }
        if(phase.equals("settling-bluetooth")) {
            pendingTarget=target;commandStage("pending","bluetooth-cleanup-in-progress");return;
        }
        if(policy.disconnecting) {
            pendingTarget=target;commandStage("pending","original-stop-in-progress");return;
        }
        if(policy.connected) {
            if(!target.equals(policy.target)){pendingTarget=target;disconnect(false);}
            else {
                // A later explicit connect may withdraw a stop which has not
                // yet passed evidence validation or been submitted to OEM.
                if(needUserStop){needUserStop=false;policy.paused=false;clearState("manual-pause");}
                commandStage("completed","selected-device-already-active");
            }
            return;
        }
        if(!phase.equals("idle") && !phase.equals("error")) {
            if(!target.equals(policy.target)) {
                if(oemConnectSubmitted || attemptOwner.owned() || nativeSession.active()) {pendingTarget=target;disconnect(false);}
                else {pendingTarget=target;fail("selection-superseded");}
            } else commandStage("pending","selected-device-request-in-progress");
            return;
        }
        begin(target,true);commandStage("pending","connection-preparation");
    }
    void fail(String code) throws Exception {
        if(projectionReleasePending && projectionReleaseNormal){resumeProjectionRelease();return;}
        if(!projectionReleasePending) {
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
        if(code.equals("connection-deadline-network-retained") && manualSelection
            && phase.equals("waiting-native") && !oemConnectSubmitted && policy.target.equals(hfp())) {
            String epoch=observedPairingEpoch();
            if(epoch.length()>0) {failedBootstrapTarget=policy.target;failedBootstrapEpoch=epoch;failedBootstrapCommand=lastCommand;}
        }
        if(!finishBluetooth(code))return;
        prepareProjectionRelease(false,code);
        }
        code=projectionReleaseReason;
        if(!retryProjectionRelease())return;
        policy.ended();phase="error";retainProjectionNetwork();preparationBaseline=null;busy(false);mark(code);pendingFailure="";oemConnectSubmitted=false;
        String btResult=read(new File(STATE,"bluetooth-cleanup-result"),100);
        commandStage("failed",btResult.equals("unowned-work-preserved") || btResult.equals("ownership-unverified")
            || btResult.equals("original-cancel-completion-unverified") || btResult.equals("selection-changed")
            || btResult.equals("original-cancel-rejected")?"bluetooth-work-unresolved":code);
        if(pendingTarget.length()>0) {
            if(!btResult.equals("original-work-settled")) {
                pendingTarget="";commandStage("rejected","bluetooth-work-unresolved");return;
            }
            String next=pendingTarget;pendingTarget="";begin(next,true);
        }
    }
    BluetoothWorkClient.Snapshot readBluetoothWork() {
        try {return BluetoothWorkClient.read();}catch(Exception unavailable){return null;}
    }
    void pinBluetoothWork() throws Exception {
        if(!manualSelection || bluetoothPinned!=null || bluetoothCancelSent)return;
        BluetoothWorkClient.Snapshot snapshot=readBluetoothWork();
        // An explicit selection can retire the exact earlier, unfinished work
        // after a failed preparation; it never adopts an active profile/session.
        if(snapshot!=null && snapshot.eligible()) {
            bluetoothPinned=snapshot;bluetoothResult="exact-work-observed";publishBluetoothWork();
        }
    }
    void publishBluetoothWork() throws Exception {
        state("bluetooth-work","revision=14 pinned="+(bluetoothPinned!=null)+" cancel_sent="+bluetoothCancelSent+" result="+bluetoothResult);
    }
    boolean finishBluetooth(String reason) throws Exception {
        BluetoothWorkClient.Snapshot current=readBluetoothWork();
        if(current!=null && current.settled()) {
            bluetoothResult="original-work-settled";state("bluetooth-cleanup-result",bluetoothResult);publishBluetoothWork();return true;
        }
        if(bluetoothCancelSent) {
            if(SystemClock.elapsedRealtime()<=bluetoothCancelDeadline) {
                commandStage("pending","original-bluetooth-canceling");mark("waiting-original-bluetooth-cancel");return false;
            }
            bluetoothResult="original-cancel-completion-unverified";
            state("bluetooth-cleanup-result",bluetoothResult);publishBluetoothWork();return true;
        }
        if(current==null || bluetoothPinned==null || !bluetoothPinned.sameWork(current) || !current.eligible()) {
            bluetoothResult="unowned-work-preserved";state("bluetooth-cleanup-result",bluetoothResult);publishBluetoothWork();return true;
        }
        SessionEvidence evidence=runtimeEvidence();
        if(evidence==null || !evidence.inactive() || !originalCleanupReady()) {
            bluetoothResult="ownership-unverified";state("bluetooth-cleanup-result",bluetoothResult);publishBluetoothWork();return true;
        }
        String latest=read(new File(BUTTON,"command"),128);String[] fields=latest.split(" ");
        if(fields.length!=3 || !fields[0].equals(Long.toString(lastCommand))
            || !(fields[2].equals(policy.target) || fields[2].equals(pendingTarget))) {
            bluetoothResult="selection-changed";state("bluetooth-cleanup-result",bluetoothResult);publishBluetoothWork();return true;
        }
        // Only the root-only original-process gate can issue the cancellation:
        // exact receiver+work epoch+target checked under the OEM SM monitor.
        bluetoothCancelSent=true;bluetoothCancelDeadline=SystemClock.elapsedRealtime()+15000;
        bluetoothCleanupReason=reason;phase="settling-bluetooth";busy(false);
        String reply;
        try {reply=BluetoothWorkClient.cancel(bluetoothPinned);}
        catch(Exception unavailable){reply="EV6BT1|UNVERIFIED";}
        bluetoothResult=reply.equals("EV6BT1|ORIGINAL_CANCEL_DISPATCHED")?"original-cancel-submitted":"original-cancel-result-unverified";
        state("bluetooth-cleanup-result",bluetoothResult);publishBluetoothWork();
        commandStage("pending","original-bluetooth-canceling");return false;
    }
    private boolean maintenanceWithoutNetwork(final String boot,File marker)throws Exception {
        if(new File(STATE,"ap-owner").exists()||new File(STATE,"saved-client-state").exists())return false;
        boolean held=ProjectionStoppedMaintenance.hold(org.ev6.projection.ProjectionReservation.store(),marker,boot,new ProjectionStoppedMaintenance.Port(){
            public void verifyStopped()throws Exception {
                requireStartupCohort();
                if(!boot.equals(ProjectionApOwnerRecord.readBoot(new File("/proc/sys/kernel/random/boot_id")).replace("-","")))throw new IOException("stopped-maintenance-boot-changed");
                BluetoothWorkClient.Snapshot work=readBluetoothWork();
                if(nativeSession.active()||attemptOwner.owned()||policy.connected||policy.disconnecting||projectionReleasePending
                    || !originalCleanupReady()||work==null||!work.settled()||AaApDemandClient.pendingRoots())throw new IOException("stopped-maintenance-work");
                for(String name:new String[]{"ap-owner","saved-client-state","ap-subnet.original","ap-firewall.owner","aa-firewall.owner","aa-firewall.owner.next","ap-idle-close.properties","ap-idle-close.properties.next","ap-startup-"+boot+".journal"})
                    if(new File(STATE,name).exists())throw new IOException("stopped-maintenance-evidence-present");
                int client=wifiState("getWifiEnabledState");
                if(wifiState("getWifiApEnabledState")!=11||(client!=1&&client!=3)
                    ||!exec(null,5000,"/system/bin/ndc","tether","status").trim().equals("210 0 Tethering services stopped"))throw new IOException("stopped-maintenance-radio");
                String ready=exec(null,5000,"/system/bin/getprop","sys.ev6.cp.apready").trim();
                String fw=exec(null,5000,"/system/bin/iptables","-S");
                if((!ready.equals("")&&!ready.equals("0"))||!fw.contains("-P INPUT ")||fw.contains("ev6_cp_reply"))throw new IOException("stopped-maintenance-network");
            }
        });
        if(held){ap=false;exclusiveMaintenanceDrained=true;state("maintenance-result",exclusiveRuntime==null?"drained-admission-held":"waiting-exclusive-cleanup");}
        else state("maintenance-result","waiting-reservation");
        return true;
    }
    private void certifyExclusiveRecovery()throws Exception {
        ProjectionUiRecoveryClient.certify(new ProjectionUiRecoveryClient.Port(){public void verify()throws Exception {
            final org.ev6.projection.ReservationStore store=org.ev6.projection.ProjectionReservation.store();
            File marker=new File("/data/local/ev6-projection/maintenance");String prior=ProjectionMaintenance.requestBoot(marker),owner=ProjectionMaintenance.request(marker,prior);
            String held=store.stoppedMaintenanceSnapshot(),current=ProjectionApOwnerRecord.readBoot(new File("/proc/sys/kernel/random/boot_id")).replace("-","");
            ProjectionMaintenanceEvidence.requireHeld(held,current,prior,owner,org.ev6.projection.ReservationStore.key("projection-maintenance-drain"));
            requireStartupCohort();BluetoothWorkClient.Snapshot work=readBluetoothWork();
            if(nativeSession.active()||backendSessionActive()||attemptOwner.owned()||policy.connected||policy.disconnecting||projectionReleasePending||!originalCleanupReady()||work==null||!work.settled()||AaApDemandClient.pendingRoots()||!new ProjectionAaDeferredClient().idle())throw new IOException("exclusive-recovery-work-present");
            for(String method:new String[]{"getCPConnectedBTAddress","getAAConnectedBTAddress"}){Object address=pp(method,new Class<?>[0]);if(address!=null&&address.toString().length()>0)throw new IOException("exclusive-recovery-projection-present");}
            int client=wifiState("getWifiEnabledState");String ready=exec(null,5000,"/system/bin/getprop","sys.ev6.cp.apready").trim(),firewall=exec(null,5000,"/system/bin/iptables","-S");
            if(wifiState("getWifiApEnabledState")!=11||(client!=1&&client!=3)||(!ready.equals("")&&!ready.equals("0"))||!exec(null,5000,"/system/bin/ndc","tether","status").trim().equals("210 0 Tethering services stopped")||!firewall.contains("-P INPUT ")||firewall.contains("ev6_cp_reply"))throw new IOException("exclusive-recovery-network-present");
            if(!prior.equals(ProjectionMaintenance.requestBoot(marker))||!owner.equals(ProjectionMaintenance.request(marker,prior))||!held.equals(store.stoppedMaintenanceSnapshot())||!current.equals(ProjectionApOwnerRecord.readBoot(new File("/proc/sys/kernel/random/boot_id")).replace("-","")))throw new IOException("exclusive-recovery-maintenance-changed");
        }});
    }
    private boolean exclusiveMaintenanceDrained;
    private boolean maintenanceTick()throws Exception {
        exclusiveMaintenanceDrained=false;
        final org.ev6.projection.ReservationStore store=org.ev6.projection.ProjectionReservation.store();
        if(!store.maintenanceRequested())return false;
        if(attemptOwner.owned() || policy.connected || projectionReleasePending || !originalCleanupReady()) {
            state("maintenance-result","waiting-original-completion");return false;
        }
        BluetoothWorkClient.Snapshot work=readBluetoothWork();
        if(work==null || !work.settled()){state("maintenance-result","waiting-bluetooth");return false;}
        final String boot=ProjectionApOwnerRecord.readBoot(new File("/proc/sys/kernel/random/boot_id")).replace("-","");
        try {
            final File marker=new File("/data/local/ev6-projection/maintenance");
            if(maintenanceWithoutNetwork(boot,marker))return true;
            if(!ProjectionMaintenance.requestBoot(marker).equals(boot)){
                boolean stopped=ProjectionMaintenance.rebootDrained(store,marker,boot,new ProjectionMaintenance.RebootPort(){
                    public void requireIdle()throws Exception {
                        requireStartupCohort();requireAaDemandLive();
                        BluetoothWorkClient.Snapshot current=readBluetoothWork();
                        if(attemptOwner.owned() || policy.connected || projectionReleasePending || !originalCleanupReady() || current==null || !current.settled())throw new IOException("maintenance-reboot-work-present");
                        if(!boot.equals(ProjectionApOwnerRecord.readBoot(new File("/proc/sys/kernel/random/boot_id")).replace("-","")))throw new IOException("maintenance-reboot-changed");
                    }
                    public void verifyStoppedNetwork(String priorBoot)throws Exception {
                        if(!priorBoot.equals(ProjectionApOwnerRecord.readBoot(new File(STATE,"ap-owner")).replace("-","")))throw new IOException("maintenance-reboot-owner");
                        int saved=SavedClientDurability.readSaved(new File(STATE,"saved-client-state"));
                        if(wifiState("getWifiApEnabledState")!=11 || wifiState("getWifiEnabledState")!=saved
                            || !exec(null,5000,"/system/bin/getprop","sys.ev6.cp.apready").trim().equals("0")
                            || !exec(null,5000,"/system/bin/ndc","tether","status").trim().equals("210 0 Tethering services stopped"))throw new IOException("maintenance-reboot-network-active");
                        String firewall=exec(null,5000,"/system/bin/iptables","-S");
                        if(!firewall.contains("-P INPUT ") || firewall.contains("ev6_cp_reply"))throw new IOException("maintenance-reboot-firewall-unconfirmed");
                    }
                });
                if(stopped){ap=false;exclusiveMaintenanceDrained=true;state("maintenance-result",exclusiveRuntime==null?"drained-admission-held":"waiting-exclusive-cleanup");}
                else state("maintenance-result","waiting-prior-maintenance");
                return true;
            }
            boolean drained=ProjectionMaintenance.drain(store,new File("/data/local/ev6-projection/maintenance"),boot,new ProjectionMaintenance.PreparationPort(){
                public void verifyPreparationOnly(int stage)throws Exception {
                    ProjectionPreparationDrain.require(STATE,new File("/proc/sys/kernel/random/boot_id"),stage,
                        wifiState("getWifiApEnabledState"),wifiState("getWifiEnabledState"),
                        exec(null,5000,"/system/bin/ndc","tether","status").trim(),
                        exec(null,5000,"/system/bin/getprop","sys.ev6.cp.apready").trim());
                }
                public void requireIdle()throws Exception {
                    requireStartupCohort();requireAaDemandLive();
                    BluetoothWorkClient.Snapshot w=readBluetoothWork();
                    if(attemptOwner.owned() || policy.connected || projectionReleasePending || !originalCleanupReady() || w==null || !w.settled())throw new IOException("maintenance-work-present");
                }
                public void requireOwnedNetwork()throws Exception {
                    File owner=new File(STATE,"ap-owner"),saved=new File(STATE,"saved-client-state");
                    if(!boot.equals(ProjectionApOwnerRecord.readBoot(owner).replace("-","")))throw new IOException("maintenance-unowned-ap");
                    SavedClientDurability.readSaved(saved);
                    int radio=wifiState("getWifiApEnabledState");
                    if(radio!=11 && (radio!=13 || wifiState("getWifiApMode")!=2))throw new IOException("maintenance-radio-transition");
                    String interfaces=exec(null,5000,"/system/bin/ndc","tether","interface","list");
                    boolean completed=false;
                    for(String line:interfaces.trim().split("\\n")) {
                        String[] fields=line.trim().split("\\s+");
                        if(fields.length>=3 && fields[0].equals("200"))completed=true;
                        else if(fields.length!=3 || !fields[0].equals("111") || !fields[2].equals("wlan0"))throw new IOException("maintenance-foreign-tether");
                    }
                    if(!completed)throw new IOException("maintenance-tether-unknown");
                }
                public void clearReady()throws Exception {prop("sys.ev6.cp.apready","0");}
                public void restoreFirewall()throws Exception {networkFirewall("restore");}
                public void stopApRestoreClient()throws Exception {
                    // Provider deletes its input on success. Keep the original snapshot for rollback/retry.
                    File original=new File(STATE,"saved-client-state"),copy=new File(STATE,"maintenance-client-state");
                    int desired=SavedClientDurability.readSaved(original);
                    if(copy.exists()) {
                        if(SavedClientDurability.readSaved(copy)!=desired)throw new IOException("maintenance-saved-client-conflict");
                    }else{
                        if(!copy.createNewFile())throw new IOException("maintenance-copy-conflict");
                        FileOutputStream out=new FileOutputStream(copy);
                        try{out.write(desired);SavedClientDurability.syncSaved(out,copy,desired);}finally{out.close();}
                    }
                    String reply=runTool("OemApSwitch","stop",copy.getPath(),new File(BACKEND,"event-state").getPath());
                    if(!reply.contains("OEM_SWITCH_COMPLETE"))throw new IOException("maintenance-stop-unconfirmed");
                }
                public void verifyDrained()throws Exception {
                    int desired=SavedClientDurability.readSaved(new File(STATE,"saved-client-state"));
                    if(wifiState("getWifiApEnabledState")!=11 || wifiState("getWifiEnabledState")!=desired
                        || !exec(null,5000,"/system/bin/ndc","tether","status").trim().equals("210 0 Tethering services stopped"))throw new IOException("maintenance-drain-unconfirmed");
                }
            },new File(STATE,"ap-startup-"+boot+".journal").exists()
                && store.apPendingStatus()!=org.ev6.projection.ReservationStore.INVALID
                ?ProjectionMaintenanceJournal.select(store,marker,boot,new File(STATE,"ap-startup-"+boot+".journal")):null);
            if(drained){ap=false;exclusiveMaintenanceDrained=true;state("maintenance-result",exclusiveRuntime==null?"drained-admission-held":"waiting-exclusive-cleanup");return true;}
            state("maintenance-result","waiting-reservation");return false;
        }catch(Exception error){state("maintenance-result","blocked:"+error.getMessage());return true;}
    }
    void tick() throws Exception {
        if(new File(STATE,"stop").exists() || !new File(BASE,"enabled").exists())throw new IOException("controller-stop");
        boolean maintenance=org.ev6.projection.ProjectionReservation.store().maintenanceRequested();
        if(exclusiveRuntime!=null)try{exclusiveRuntime.maintenance(maintenance);}catch(Exception blocked){state("maintenance-result","waiting-exclusive-cancellation");return;}
        if(maintenance){cpApDemand.clear();startupBluetoothRetry=false;}
        if(maintenanceTick()){
            if(exclusiveRuntime!=null)try{if(exclusiveMaintenanceDrained)certifyExclusiveRecovery();state("maintenance-result",exclusiveRuntime.maintenanceCompleted(exclusiveMaintenanceDrained)?"drained-admission-held":"waiting-exclusive-cleanup");}catch(Exception blocked){state("maintenance-result","waiting-exclusive-cleanup");}
            return;
        }
        command();
        if(exclusiveRuntime!=null)try{exclusiveRuntime.tick();}catch(Exception blocked){
            // Keep ordinary OEM/native cleanup running. A missing Binder or an uncertain
            // journal never authorizes a new START and must not restart the whole worker.
            state("handoff-result","blocked:"+blocked.getClass().getSimpleName());
        }
        if(needUserStop) {needUserStop=false;disconnect(pendingStopManual);}
        if(new File(STATE,"stop").exists() || !new File(BASE,"enabled").exists())throw new IOException("controller-stop");
        if(backendPid==0 || !new File("/proc/"+backendPid).exists())throw new IOException("backend-process-exited");
        if(!((android.os.IInterface)pp).asBinder().isBinderAlive())throw new IOException("projection-service-exited");
        if(!new File("/proc/"+nativeSession.pid).exists())throw new IOException("native-process-exited");
        if(projectionReleasePending){resumeProjectionRelease();return;}
        bondEvents(false);
        if(firstPairs.pending()&&nativeSession.active())firstPairs.clear();
        if(manualSelection && phase.equals("waiting-native"))pinBluetoothWork();
        boolean active=nativeSession.active();
        if(phase.equals("settling-bluetooth")) {
            String reason=bluetoothCleanupReason;
            if(active) {
                bluetoothResult="original-session-preserved";publishBluetoothWork();
                phase="settling-original";mark("bluetooth-cancel-interrupted-by-original-session");return;
            }
            if(!finishBluetooth(reason))return;
            bluetoothCleanupReason="";
            if(reason.equals("original-stop")){phase="settling-original";ended();}
            else {phase="waiting-native";fail(reason);}
            return;
        }
        if(phase.equals("settling-original") || phase.equals("recovering-original")) {
            if(!attemptOwner.owned() && policy.target.length()==0) {
                OemAttemptSnapshot recovered=originalObservation();
                if(recovered!=null && recovered.verifiedSession(recovered.sessionTarget)) {
                    attemptOwner.adopt(recovered,recovered.sessionTarget);policy.target=recovered.sessionTarget;oemConnectSubmitted=true;
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
            if(!policy.disconnecting)publishCpSessionHint();
            if(policy.disconnecting && active && pendingTarget.length()>0) {
                SessionEvidence successor=runtimeEvidence();
                if(successor!=null && successor.activeFor(pendingTarget)
                        && attemptOwner.canTransferTo(successor.observation,pendingTarget)) {
                    started();if(!policy.disconnecting)return;
                }
            }
            if(policy.disconnecting && SystemClock.elapsedRealtime()>deadline) {
                busy(false);commandStage("pending","original-finalization-overdue");
                mark("disconnect-awaiting-original-finalization");return;
            }
            mark(policy.disconnecting?"disconnecting":"connected");return;
        }
        if(policy.disconnecting){
            if(SystemClock.elapsedRealtime()>deadline)busy(false);
            commandStage("pending","original-finalization-unverified");
            mark("waiting-original-pending-stop-finalization");return;
        }
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
            if(!bonded(policy.target)){fail("selected-device-unpaired-network-retained");return;}
            if(SystemClock.elapsedRealtime()>deadline){fail("connection-deadline-network-retained");return;}
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
                // Diagnostic only. Bounded rate, no extra DNS queries, peer selection or state files.
                long diagnosticNow=SystemClock.elapsedRealtime();
                String diagnosticScope=selectedAtDiscovery+"|"+token;
                if(!diagnosticScope.equals(lastDiscoveryDiagnosticScope) || diagnosticNow-lastDiscoveryDiagnosticAt>=5000L) {
                    lastDiscoveryDiagnosticScope=diagnosticScope;lastDiscoveryDiagnosticAt=diagnosticNow;
                    System.out.println("CARPLAY_DISCOVERY elapsed="+diagnosticNow+" target="+selectedAtDiscovery+" epoch="+epoch+" deadline="+deadline+" fresh="+fresh+" known="+known+" stations="+stations.size()+" received="+discovery.received+" identity="+discovery.identityMatches+" neighbor="+discovery.neighborMatches+" diagnosis="+discovery.diagnosis+" resolved="+(location!=null)+discoveryCacheDiagnostic(discovery,selectedAtDiscovery));
                }
            }catch(IOException unavailable){fail("selected-discovery-unavailable");return;}
            if(location==null){mark("waiting-selected-control-service");return;}
            // Discovery is an identity/readiness guard only. The original
            // service and control client resolve and send their own request.
            command();
            if(!selectedAtDiscovery.equals(policy.target) || !phase.equals("waiting-native"))return;
            if(new File(STATE,"stop").exists() || !new File(BASE,"enabled").exists())throw new IOException("controller-stop");
            if(SystemClock.elapsedRealtime()>deadline){fail("connection-deadline-network-retained");return;}
            if(nativeSession.active()){fail("active-session-before-selected-control");return;}
            OemAttemptSnapshot baseline=originalObservation();
            if(!OemAttemptOwner.submissionReady(baseline)){mark("waiting-original-work-before-submission");return;}
            if(!ControllerProjectionReservation.valid()){mark("projection-reservation-required-before-submission");return;}
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
        if(closeIdleProjectionAp(active))return;
        if(resumePendingDemand(active))return;
        if(resumeAaApDemand(active))return;
        String hfp=hfp();boolean freshHfp=!hfp.equals(lastHfp);lastHfp=hfp;
        if(firstPairs.pending()&&exclusiveRuntime!=null&&exclusiveRuntime.firstPairIdle()) {
            ProjectionCpBootstrapClient.Ticket proof;
            try{proof=ProjectionCpBootstrapClient.readOriginalHandshake();}catch(Exception unavailable){mark("first-pair-oem-transport-unavailable");return;}
            if(proof!=null&&firstPairs.eligible(proof.address,SystemClock.elapsedRealtime(),bonded(proof.address),true,false)
                &&exclusiveRuntime.firstCpRequest(proof.address,proof)) {
                firstPairs.consumed(proof.address);bonds.consumeSetup(proof.address);mark("first-pair-oem-handshake-admitted");return;
            }
            mark("first-pair-awaiting-oem-cp-transport");return;
        }
        if(hfp.length()>0)bonds.consumeSetup(hfp);
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
        long bridgeDeadline=SystemClock.elapsedRealtime()+15000;
        while(readBluetoothWork()==null) {
            if(SystemClock.elapsedRealtime()>bridgeDeadline)throw new IOException("bluetooth-work-bridge-unavailable");
            Thread.sleep(250);
        }
        if(!new File(ASSET,"wl-stations").canExecute())throw new IOException("station-reader-not-executable");
        String boot=read(new File("/proc/sys/kernel/random/boot_id"),128);
        boolean newBoot=!boot.equals(read(new File(STATE,"boot-id"),128));
        boolean priorOwnedAp=newBoot && new File(STATE,"ap-owner").exists() && new File(STATE,"saved-client-state").exists();
        if(newBoot) {
            // A reboot is not a new user selection. Preserve the manual-end latch.
            if(new File(STATE,"manual-pause").exists())state("manual-pause",Long.toString(SystemClock.elapsedRealtime()));
            new File(STATE,"current-target").delete();
            new File(STATE,"bond-ack").delete();
            // Preserve original client/subnet recovery evidence across boots.
            // Kernel addresses may be stale; markers do not prove ownership.
            // A future journal-based recovery must reconcile rather than delete.
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
            policy.target=adopted;policy.started();failedBootstrapCommand=0;failedBootstrapTarget="";failedBootstrapEpoch="";phase="connected";ap=true;
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
            ap=ProjectionApOwnership.retained(STATE,new File("/proc/sys/kernel/random/boot_id"),
                wifiState("getWifiApEnabledState"),wifiState("getWifiApMode"));
            if(snapshot!=null && snapshot.verifiedSession(snapshot.sessionTarget)) {
                attemptOwner.adopt(snapshot,snapshot.sessionTarget);policy.target=snapshot.sessionTarget;oemConnectSubmitted=true;
            }
            if(OemAttemptOwner.unownedCleanupReady(snapshot,policy.paused) && !nativeSession.active()
                && ConnectionPolicy.originalAssociationCleared((String)pp("getCPConnectedBTAddress",new Class<?>[0])))
                reconcileInactiveReceiver();
            if(originalCleanupReady()) {
                if(!retireOriginalAttempt())throw new IOException("oem-startup-boundary-changed");
                policy.ended();reconcileInactiveReceiver();retainProjectionNetwork();
                if(priorOwnedAp)mark("prior-boot-projection-ap-recovery-required");
            }else {phase="recovering-original";mark("preserving-original-work-on-worker-start");}
        }
        exclusiveRuntime=new ProjectionExclusiveRuntime(this);
        bondEvents(true); // Old events invalidate stale caches but cannot override a pause on startup.
        observe(BACKEND,new HashSet<String>(Arrays.asList("pairing.state","session.state","runtime.event","bond.state")));
        observe(BUTTON,new HashSet<String>(Arrays.asList("command")));
        observe(STATE,new HashSet<String>(Arrays.asList("stop","manual-pause")));
        write(new File(STATE,"controller.pid"),Integer.toString(android.os.Process.myPid()));
        write(new File(BUTTON,"controller-ready"),"1");
        busy(attemptOwner.owned() || phase.equals("recovering-original"));
        mark(phase.equals("recovering-original")?"preserving-original-work-on-worker-start":
            (policy.connected?"connected":(policy.paused?"paused-network-retained":"waiting-bluetooth-network-retained")));
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
    long nextTickDelayMillis(){
        if(phase.equals("waiting-native"))return 1000;
        CarPlayApDemand.Request demand=cpApDemand.current();
        boolean pending=demand!=null && SystemClock.elapsedRealtime()<demand.expires;
        return pending || (exclusiveRuntime!=null&&exclusiveRuntime.startupWakePending())?1000:5000;
    }
    public static void main(String[] arguments) {
        Looper.prepareMainLooper();final CarPlayController controller=new CarPlayController();instance=controller;
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable(){public void run(){controller.close();}},"CarPlay-shutdown"));
        try {
            controller.init();
            for(;;) {
                controller.tick();
                synchronized(SIGNAL) {if(!signaled)SIGNAL.wait(controller.nextTickDelayMillis());signaled=false;}
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
