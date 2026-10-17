package org.ev6.research;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.bluetooth.BluetoothDevice;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Properties;
import java.util.Locale;

/** Wake signal only. It never claims iAP2 authorization or session success. */
public final class BackendRuntime extends BroadcastReceiver {
    private static BackendRuntime receiver;
    private final Context registrationOwner;
    private BackendRuntime(Context context){registrationOwner=context;}
    public static synchronized void init(Context context) {
        if (receiver!=null && receiver.registrationOwner==context) return;
        if (receiver!=null)release(receiver.registrationOwner);
        BackendRuntime candidate=new BackendRuntime(context);
        IntentFilter filter=new IntentFilter();
        filter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
        filter.addAction("android.bluetooth.device.action.ACL_CONNECTED");
        filter.addAction("android.bluetooth.device.action.ACL_DISCONNECTED");
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction("android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED");
        filter.addAction("com.broadcom.bt.hfdevice.profile.action.CONNECTION_STATE_CHANGED");
        filter.addAction("com.broadcom.bt.hfdevice.hfbtcm.action.BTCM_CONNECTION_STATE_CHANGED");
        context.registerReceiver(candidate,filter);
        receiver=candidate;
        receiver.onReceive(context,null);
    }
    public static synchronized void release(Context context) {
        if(receiver==null || receiver.registrationOwner!=context)return;
        try { context.unregisterReceiver(receiver); }
        catch(IllegalArgumentException alreadyGone) { /* Android already removed it. */ }
        receiver=null;
    }
    public void onReceive(Context context,Intent intent) {
        synchronized(BackendRuntime.class) {
        synchronized(BackendOwnership.class) {
        if(receiver!=this || !BackendOwnership.current(registrationOwner))return;
        context=registrationOwner;
        if(intent!=null && BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction()))recordBond(context,intent);
        AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"runtime.event"));
        FileOutputStream stream=null;
        try {
            stream=file.startWrite();
            stream.write(Long.toString(android.os.SystemClock.elapsedRealtime()).getBytes("US-ASCII"));
            file.finishWrite(stream);
        } catch (Exception failure) {
            if (stream!=null) file.failWrite(stream);
        }
        }
        }
    }
    private static void recordBond(Context context,Intent intent) {
        BluetoothDevice device=(BluetoothDevice)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if(device==null)return;
        String target=device.getAddress().toUpperCase(Locale.US);
        if(!target.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}"))return;
        int state=intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,-1);
        int previous=intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,-1);
        if(state<10 || state>12 || previous<10 || previous>12)return;
        AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"bond.state"));
        FileOutputStream stream=null;
        try {
            Properties values=new Properties();
            if(file.getBaseFile().exists()) {
                if(file.getBaseFile().length()>8192)return;
                FileInputStream input=file.openRead();try{values.load(input);}finally{input.close();}
            }
            BufferedReader bootFile=new BufferedReader(new FileReader("/proc/sys/kernel/random/boot_id"));
            String boot;try{boot=bootFile.readLine();}finally{bootFile.close();}
            if(boot==null || !boot.matches("[0-9a-f-]{36}"))return;
            if(!boot.equals(values.getProperty("boot")))values.clear();
            values.setProperty("boot",boot);
            if(!values.containsKey(target) && values.size()>=33)return;
            long now=android.os.SystemClock.elapsedRealtime(),start=now;
            String old=values.getProperty(target,"");String[] fields=old.split(" ");
            if(state==12 && previous==11 && fields.length==4 && fields[1].equals("11")) {
                try {long saved=Long.parseLong(fields[3]);if(saved>=0 && saved<=now)start=saved;}catch(NumberFormatException invalid){}
            }
            values.setProperty(target,now+" "+state+" "+previous+" "+start);
            stream=file.startWrite();values.store(stream,"Private bond lifecycle; no credentials");file.finishWrite(stream);
        }catch(Exception failure){if(stream!=null)file.failWrite(stream);}
    }
}
