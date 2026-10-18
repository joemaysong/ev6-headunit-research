import android.os.FileObserver;
import android.os.SystemClock;
import java.io.*;
import java.util.*;
import java.util.regex.*;
/** Temporary standalone passive recorder. Never invokes OEM services or writes their files. */
public final class TimelineRecorder {
    static final String STAGE="/data/local/tmp/ev6-bt-lifecycle-capture-20261018";
    static FileOutputStream output; static long sequence;static volatile boolean stopping;
    static final Map<String,String> previous=new HashMap<String,String>();
    static final List<FileObserver> watchers=new ArrayList<FileObserver>();
    static final Pattern relevant=Pattern.compile("EV6CarPlayButton|CarPlay_|CarPlayService|PPService|UsbDeviceManager|UsbHostManager|UsbSettingsManager|EV6BackendProbe|CachedBluetoothDevice|LocalBluetoothManager|BluetoothUtils|BTCMStateMachine|BTCMService");
    static synchronized void record(String kind,String value) {
        if(stopping)return;
        try {
            File current=new File(STAGE,"timeline.tsv");
            if(current.length()>1048576) {
                output.close();new File(STAGE,"timeline.tsv.4").delete();
                for(int i=3;i>=1;i--)new File(STAGE,"timeline.tsv."+i).renameTo(new File(STAGE,"timeline.tsv."+(i+1)));
                current.renameTo(new File(STAGE,"timeline.tsv.1"));output=new FileOutputStream(current,true);
            }
            value=value.replace("\r","\\r").replace("\n","\\n").replace("\t","\\t");
            if(value.length()>4096)value=value.substring(0,4096)+"[truncated]";
            String row=(++sequence)+"\t"+SystemClock.elapsedRealtime()+"\t"+System.currentTimeMillis()+"\t"+kind+"\t"+value+"\n";
            output.write(row.getBytes("UTF-8"));output.flush();
        }catch(IOException failure){stopping=true;}
    }
    static String read(String path) {
        try {
            FileInputStream in=new FileInputStream(path);try {
                byte[] b=new byte[4096];int n=in.read(b);return n<0?"":new String(b,0,n,"UTF-8").trim();
            }finally{in.close();}
        }catch(IOException absent){return "[unavailable]";}
    }
    static synchronized void changed(String kind,String path) {
        String value=read(path);String old=previous.put(path,value);
        if(!value.equals(old))record(kind,path+"="+value);
    }
    static void watch(final String folder,final String[] names,final String kind) {
        FileObserver watcher=new FileObserver(folder,FileObserver.CLOSE_WRITE|FileObserver.MOVED_TO|FileObserver.DELETE|FileObserver.CREATE) {
            public void onEvent(int event,String path) {
                if(path==null)return;
                for(String name:names)if(name.equals(path)) {
                    record("FILE_EVENT",kind+" "+name+" mask="+event);
                    changed(kind,folder+"/"+name);return;
                }
            }
        };
        watchers.add(watcher);watcher.startWatching();
        for(String name:names)changed("BASELINE_"+kind,folder+"/"+name);
    }
    static void usb() {
        changed("USB_SYSFS","/sys/class/android_usb/android0/state");
        changed("USB_SYSFS","/sys/class/switch/usb_connected/state");
        for(String key:new String[]{"sys.usb.state","sys.usb.config"})try {
            String value=(String)Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,key,"");
            synchronized(TimelineRecorder.class){if(!value.equals(previous.put(key,value)))record("USB_PROPERTY",key+"="+value);}
        }catch(Exception unavailable){}
        File[] devices=new File("/sys/bus/usb/devices").listFiles();TreeSet<String> inventory=new TreeSet<String>();
        if(devices!=null)for(File f:devices)if(new File(f,"idVendor").isFile())inventory.add(f.getName()+":"+read(f+"/idVendor")+":"+read(f+"/idProduct"));
        String value=inventory.toString();synchronized(TimelineRecorder.class){if(!value.equals(previous.put("usb-enumeration",value)))record("USB_ENUMERATION",value);}
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1 || !args[0].equals(STAGE) || !new File(STAGE).isDirectory())throw new IOException("stage-required");
        output=new FileOutputStream(new File(STAGE,"timeline.tsv"),true);
        record("TIMELINE_START","columns=sequence,elapsed_ms,wall_ms,event,payload; logcat historical tail may precede start; private only");
        watch("/data/data/org.ev6.carplaybutton/files",new String[]{"command"},"BUTTON_COMMAND_WRITTEN");
        watch("/data/local/ev6-carplay/state",new String[]{"command-ack","command-outcome","status","manual-pause","current-target","bluetooth-work","bluetooth-cleanup-result"},"CONTROLLER_FILE");
        watch("/data/data/org.ev6.wirelesscarplay/files",new String[]{"session.state","runtime.event","transport-gate-state"},"BACKEND_FILE");
        final Process logger=new ProcessBuilder("/system/bin/logcat","-v","threadtime").redirectErrorStream(true).start();
        Thread logReader=new Thread(new Runnable(){public void run(){
            try {BufferedReader input=new BufferedReader(new InputStreamReader(logger.getInputStream(),"UTF-8"));String line;
                while(!stopping && (line=input.readLine())!=null)if(relevant.matcher(line).find())record("OEM_LOG",line);
            }catch(IOException failure){record("LOG_READER_ENDED",failure.getClass().getSimpleName());}
        }},"EV6-timeline-log");logReader.setDaemon(true);logReader.start();
        long deadline=SystemClock.elapsedRealtime()+1200000;
        try {while(!stopping && SystemClock.elapsedRealtime()<deadline && !new File(STAGE,"stop").exists()) {
            usb();
            // Fast fallback complements file notifications; it is not proof
            // of every physical tap, especially when the UI emits no command.
            changed("BUTTON_COMMAND_WRITTEN","/data/data/org.ev6.carplaybutton/files/command");
            changed("COMMAND_ACK","/data/local/ev6-carplay/state/command-ack");
            changed("COMMAND_OUTCOME","/data/local/ev6-carplay/state/command-outcome");
            changed("BLUETOOTH_WORK","/data/local/ev6-carplay/state/bluetooth-work");
            changed("BLUETOOTH_CLEANUP","/data/local/ev6-carplay/state/bluetooth-cleanup-result");
            Thread.sleep(250);
        }}finally {
            record("TIMELINE_END","stopped/limit");stopping=true;logger.destroy();
            for(FileObserver watcher:watchers)watcher.stopWatching();output.close();
            new File(STAGE,"timeline.done").createNewFile();
        }
        // FileObserver's platform observer thread can keep this standalone VM
        // alive. Exit ONLY this temporary recorder after all files are closed.
        System.exit(0);
    }
}
