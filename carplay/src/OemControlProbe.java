import java.io.*;
import java.net.*;
import java.util.*;

/** Firmware-specific read-only native control metadata; never prints identifiers. */
public final class OemControlProbe {
    static NativeSession state;
    static RandomAccessFile memory;
    static int u8(long p) throws IOException {memory.seek(p);return memory.readUnsignedByte();}
    static long u32(long p) throws IOException {return state.u32(p);}
    static String cstring(long p,int limit) throws IOException {
        if(p<4096 || p>0xffffffffL)throw new IOException("metadata-pointer");
        memory.seek(p);ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(int i=0;i<limit;i++){int v=memory.readUnsignedByte();if(v==0)return new String(out.toByteArray(),"UTF-8");out.write(v);}
        throw new IOException("metadata-string-size");
    }
    static String cfstring(long p) throws IOException {
        if(u8(p)!=0x56 || u8(p+1)!=7)return "";
        return cstring((u8(p+2)&1)!=0?p+8:u32(p+8),256);
    }
    static List<Long> dictionaryValues(long p) throws IOException {
        List<Long> out=new ArrayList<Long>();if(p==0)return out;
        if(u8(p)!=0x56 || u8(p+1)!=5)throw new IOException("metadata-dictionary-type");
        long count=u32(p+0x34),buckets=u32(p+0x38),table=u32(p+0x3c);
        if(count>64 || buckets>256)throw new IOException("metadata-dictionary-size");
        Set<Long> seen=new HashSet<Long>();
        for(int i=0;i<buckets;i++)for(long node=u32(table+4*i);node!=0;node=u32(node)) {
            if(!seen.add(node) || seen.size()>64)throw new IOException("metadata-dictionary-cycle");
            out.add(u32(node+8));
        }
        if(out.size()!=count)throw new IOException("metadata-dictionary-race");
        return out;
    }
    static List<Long> array(long p) throws IOException {
        List<Long> out=new ArrayList<Long>();if(p==0)return out;
        if(u8(p)!=0x56 || u8(p+1)!=1)throw new IOException("metadata-array-type");
        long count=u32(p+0x1c),data=u32(p+0x20);
        if(count>16)throw new IOException("metadata-array-size");
        for(int i=0;i<count;i++)out.add(u32(data+4*i));return out;
    }
    static void service(long p,String kind,int wlan) throws IOException {
        if(p==0)return;
        long endpoint=u32(p+0x14),port=u32(p+0x18)&65535,index=u32(p+0x1c);
        String name=endpoint==0?"":cstring(endpoint,512);
        String family=name.length()==0?"missing":name.matches("[0-9.]+")?"ipv4":name.indexOf(':')>=0?"ipv6":"dns";
        System.out.println("OEM_SERVICE kind="+kind+" endpoint_family="+family+" port="+port+" interface_index="+index
            +" interface_is_wlan="+(index==wlan)+" control_service_name="+name.contains("._carplay-ctrl._tcp.")+" local_name="+name.contains(".local"));
    }
    public static void main(String[] args) throws Exception {
        if(android.os.Process.myUid()!=0)throw new IOException("root-required");
        int pid=NativeSession.findPid("com.daudio.app.dipo");state=new NativeSession(pid);
        memory=new RandomAccessFile("/proc/"+pid+"/mem","r");
        try {
            int wlan=NetworkInterface.getByName("wlan0").getIndex();
            System.out.println("WLAN_INTERFACE_INDEX="+wlan);
            long context=state.context();System.out.println("NATIVE_ACTIVE="+state.active());
            System.out.println("NATIVE_CONTROL_OWNER_PRESENT="+state.hasControlOwner());
            List<Long> controllers=array(u32(context+0xd0));System.out.println("OEM_CONTROLLER_COUNT="+controllers.size());
            for(long controller:controllers) {
                List<Long> current=dictionaryValues(u32(controller+0x14)),retired=dictionaryValues(u32(controller+0x18));
                System.out.println("OEM_CONTROLLER_SERVICES current="+current.size()+" fallback="+retired.size());
                for(long item:current)service(item,"current",wlan);
                for(long item:retired)service(item,"fallback",wlan);
            }
        }catch(IOException transientState){System.out.println("OEM_METADATA_SAMPLE_INCOMPLETE=true");}
        finally{memory.close();state.close();}
    }
}
