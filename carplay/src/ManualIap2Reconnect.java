package org.ev6.research;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import dalvik.system.PathClassLoader;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** One original lifecycle invocation for a new explicit retry of a failed
 * bootstrap. Never a radio toggle, fake attachment, or credential sender. */
public final class ManualIap2Reconnect {
    private static long consumed;
    private ManualIap2Reconnect() {}
    static Object field(Object object,String name) throws Exception {
        Field field=object.getClass().getDeclaredField(name);
        field.setAccessible(true);return field.get(object);
    }
    static Object call(Object object,String name,Class<?>[] types,Object...args) throws Exception {
        Method method=object.getClass().getDeclaredMethod(name,types);
        method.setAccessible(true);return method.invoke(object,args);
    }
    static Object binder(String name,String stub) throws Exception {
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager")
            .getMethod("getService",String.class).invoke(null,name);
        if(binder==null || !binder.isBinderAlive())throw new IllegalStateException("binder-unavailable");
        return Class.forName(stub).getMethod("asInterface",IBinder.class).invoke(null,binder);
    }
    static String hfp(Context context) throws Exception {
        Object activity=Class.forName("android.app.ActivityManagerNative").getMethod("getDefault").invoke(null);
        Intent service=new Intent();service.setClassName("com.android.bluetooth",
            "com.broadcom.bt.service.btcmservice.HfDeviceBTCMService");
        IBinder binder=(IBinder)Class.forName("android.app.IActivityManager")
            .getMethod("peekService",Intent.class,String.class).invoke(activity,service,null);
        if(binder==null || !binder.isBinderAlive())return "";
        ClassLoader loader=new PathClassLoader("/system/framework/com.broadcom.bt.jar",context.getClassLoader());
        Object manager=loader.loadClass("com.broadcom.bt.hfdevice.hfbtcm.IHfDeviceBTCMService$Stub")
            .getMethod("asInterface",IBinder.class).invoke(null,binder);
        Class<?> api=loader.loadClass("com.broadcom.bt.hfdevice.hfbtcm.IHfDeviceBTCMService");
        // The legacy iAP2 owner has one shared Bluetooth component. Never
        // restart it when another connected phone could own that component.
        if(((Integer)api.getMethod("btcmGetCountConnectedDevices").invoke(manager)).intValue()!=1)return "";
        BluetoothDevice device=(BluetoothDevice)api.getMethod("btcmGetHfpConnectedDevice").invoke(manager);
        return device==null?"":device.getAddress();
    }
    static void publish(Context owner,long token,String result) {
        try {SessionMetadata.write(new File(owner.getFilesDir(),"manual-reconnect.state"),
            "token="+token+" result="+result+"\n");}
        catch(Exception ignored) { /* No success receipt is synthesized. */ }
    }
    public static synchronized void request(Context owner,Object delegate,Intent intent) {
        long token=0;
        try {
            if(!BackendOwnership.current(owner) || delegate==null || intent==null)return;
            token=intent.getLongExtra("token",0);
            long failed=intent.getLongExtra("failed-token",0),issued=intent.getLongExtra("issued",0);
            long now=SystemClock.elapsedRealtime();
            String target=intent.getStringExtra("target"),epoch=intent.getStringExtra("epoch");
            if(token<=0 || token<=consumed || failed<0 || (failed>0 && token<=failed) || issued<=0 || now<issued || now-issued>5000
                || target==null || !target.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")
                || epoch==null || !epoch.matches("[1-9][0-9]*"))return;
            consumed=token;
            if(!target.equals(SessionMetadata.property("sys.ev6.cp.busy"))
                || !"1".equals(SessionMetadata.property("sys.ev6.cp.apready"))
                || !target.equalsIgnoreCase(hfp(owner))) {
                publish(owner,token,"selection-changed");return;
            }
            if(!SessionMetadata.read(new File(owner.getFilesDir(),"session.state").getPath(),8).trim().equals("0")) {
                publish(owner,token,"active-session-preserved");return;
            }
            Object pp=binder("phoneprojection","android.phoneprojection.IPPManager$Stub");
            String active=(String)Class.forName("android.phoneprojection.IPPManager")
                .getMethod("getCPConnectedBTAddress").invoke(pp);
            if(active!=null && active.length()>0){publish(owner,token,"active-association-preserved");return;}
            Class<?> events=Class.forName("org.ev6.research.PairingEvents");
            Field generation=events.getDeclaredField("epoch");generation.setAccessible(true);
            if(!epoch.equals(Integer.toString(generation.getInt(null)))) {
                publish(owner,token,"bluetooth-generation-changed");return;
            }
            Object manager=field(delegate,"mConnectionManager"),connection=field(manager,"mActiveConnection");
            if(connection==null || field(manager,"mNextConnection")!=null
                || ((Integer)call(connection,"getWithComponent",new Class<?>[0])).intValue()!=4
                || ((Integer)call(connection,"getState",new Class<?>[0])).intValue()!=2) {
                publish(owner,token,"original-work-preserved");return;
            }
            // Query real original iAP2 validity rather than infer it from HFP.
            boolean valid=(Boolean)call(delegate,"IsValidSvcState",new Class<?>[0]);
            Field authenticated=events.getDeclaredField("authenticated");authenticated.setAccessible(true);
            Field identified=events.getDeclaredField("identified");identified.setAccessible(true);
            boolean completedHandshake=authenticated.getBoolean(null) && identified.getBoolean(null);
            // Invalid service after an observed completed handshake is a concrete
            // stale-state mismatch. A normal handshake still in progress is kept.
            // A valid but unproductive link requires a failed-bootstrap receipt
            // from this same epoch AND this new explicit user retry.
            if(failed==0 && (valid || !completedHandshake)) {
                publish(owner,token,valid?"valid-link-preserved":"original-handshake-preserved");return;
            }
            publish(owner,token,valid?"recovering-failed-valid-link":"recovering-invalid-link");
            Method start=delegate.getClass().getMethod("onStartMain",Integer.TYPE);
            // Clear old metadata BEFORE invocation, so any synchronous original
            // notifications are retained. Never publish authorization ourselves.
            events.getMethod("resetForOwner",Context.class).invoke(null,owner);
            start.invoke(delegate,4);
            publish(owner,token,"original-recovery-invoked");
        }catch(Exception failure){publish(owner,token,"recovery-rejected");}
    }
}
