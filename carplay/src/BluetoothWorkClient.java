import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Credentials;
import java.io.*;

/** Root-only exact-work protocol. Epoch is assigned by the original BT Handler. */
public final class BluetoothWorkClient {
    static final class Snapshot {
        final int pid,state;final long epoch;final String process,receiver,generation;
        final boolean busy,automatic,empty,inactive;
        Snapshot(int p,String g,String line) throws Exception {
            String[] f=line.split("\\|",-1);
            if(f.length!=9 || !f[0].equals("EV6BT1") || !f[1].matches("[0-9a-f]{32}") || !f[2].matches("[1-9][0-9]{0,18}")
                || !f[3].matches("[0-4]") || !(f[6].equals("-") || f[6].matches("([0-9A-F]{2}:){5}[0-9A-F]{2}")))throw new IOException("bluetooth-work-schema");
            for(int i:new int[]{4,5,7,8})if(!f[i].equals("true") && !f[i].equals("false"))throw new IOException("bluetooth-work-schema");
            pid=p;generation=g;receiver=f[1];epoch=Long.parseLong(f[2]);state=Integer.parseInt(f[3]);process=f[6];
            busy=f[4].equals("true");automatic=f[5].equals("true");empty=f[7].equals("true");inactive=f[8].equals("true");
        }
        boolean eligible(){return state==1 && busy && empty && inactive && !process.equals("-");}
        boolean settled(){return (state==0 || state==2) && !busy;}
        boolean sameWork(Snapshot s){return s!=null && pid==s.pid && generation.equals(s.generation) && receiver.equals(s.receiver) && epoch==s.epoch && process.equals(s.process);}
    }
    static String generation(int pid) throws Exception {
        String stat=CarPlayController.read(new File("/proc/"+pid+"/stat"),4096);int end=stat.lastIndexOf(") ");
        if(end<0)throw new IOException("bluetooth-process");String[] f=stat.substring(end+2).split("\\s+");
        if(f.length<20 || !f[19].matches("[0-9]+"))throw new IOException("bluetooth-process");return pid+":"+f[19];
    }
    static String exchange(int pid,String expected,String request) throws Exception {
        if(android.os.Process.myUid()!=0 || !expected.equals(generation(pid)))throw new IOException("bluetooth-owner");
        LocalSocket socket=new LocalSocket();
        try {
            socket.connect(new LocalSocketAddress("ev6.bluetooth.work."+pid));socket.setSoTimeout(2200);
            Credentials peer=socket.getPeerCredentials();
            if(peer.getPid()!=pid || peer.getUid()!=1002)throw new IOException("bluetooth-peer");
            socket.getOutputStream().write((request+"\n").getBytes("US-ASCII"));socket.getOutputStream().flush();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();InputStream in=socket.getInputStream();
            for(int i=0;;i++){int c=in.read();if(c==10)break;if(c<32 || c>126 || i>=256)throw new IOException("bluetooth-reply");bytes.write(c);}
            if(!expected.equals(generation(pid)))throw new IOException("bluetooth-process-changed");return new String(bytes.toByteArray(),"US-ASCII");
        }finally{socket.close();}
    }
    static Snapshot read() throws Exception {
        int pid=NativeSession.findPid("com.android.bluetooth");if(pid==0)return null;
        String gen=generation(pid);String value=exchange(pid,gen,"Q");
        if(value.equals("EV6BT1|UNKNOWN"))return null;return new Snapshot(pid,gen,value);
    }
    static String cancel(Snapshot expected) throws Exception {
        return exchange(expected.pid,expected.generation,"C "+expected.receiver+" "+expected.epoch+" "+expected.process);
    }
}
