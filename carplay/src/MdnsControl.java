import java.io.*;
import java.net.*;
import java.util.*;

/** Bounded DNS names, scoped discovery query, and physical-neighbor checks. */
public final class MdnsControl {
    static final String SERVICE="._carplay-ctrl._tcp.local.";
    static int u16(byte[] data,int offset,int size) throws IOException {
        if (offset<0 || offset+2>size) throw new IOException("dns-bounds");
        return ((data[offset]&255)<<8)|(data[offset+1]&255);
    }
    static String name(byte[] data,int position,int size,int[] next) throws IOException {
        StringBuilder text=new StringBuilder();
        Set<Integer> seen=new HashSet<Integer>();
        int end=-1,labels=0;
        for (;;) {
            if (position<0 || position>=size || !seen.add(position) || ++labels>128) throw new IOException("dns-name");
            int length=data[position++]&255;
            if (length==0) { if (end<0) end=position; break; }
            if ((length&192)==192) {
                if (position>=size) throw new IOException("dns-pointer");
                if (end<0) end=position+1;
                position=((length&63)<<8)|(data[position]&255);
                continue;
            }
            if (length>63 || position+length>size || text.length()+length+1>255) throw new IOException("dns-label");
            for (int i=0;i<length;i++) {
                int ch=data[position+i]&255;
                if (ch<32 || ch==127) throw new IOException("dns-text");
            }
            text.append(new String(data,position,length,"UTF-8"));
            text.append('.'); position+=length;
        }
        next[0]=end;
        return text.toString().toLowerCase(Locale.US);
    }
    static final class Endpoint { final int port; final String host; Endpoint(int p,String h){port=p;host=h;} }
    static byte[] query() throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(new byte[]{0,0,0,0,0,1,0,0,0,0,0,0});
        for(String label: new String[]{"_carplay-ctrl","_tcp","local"}) { byte[] b=label.getBytes("US-ASCII");out.write(b.length);out.write(b); }
        out.write(new byte[]{0,0,12,0,1});return out.toByteArray();
    }
    static boolean neighborMatches(String neighbors,InetAddress source,String station) throws Exception {
        if(!station.matches("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}"))return false;
        for(String line:neighbors.split("\n")) {
            String[] words=line.trim().split("\\s+");
            if(words.length<4 || !words[0].matches("[0-9a-fA-F:]+(?:%wlan0)?"))continue;
            if(line.contains("FAILED") || line.contains("INCOMPLETE"))continue;
            boolean peer=false;
            for(int i=1;i+1<words.length;i++)if(words[i].equals("lladdr") && station.equalsIgnoreCase(words[i+1]))peer=true;
            if(peer && Arrays.equals(InetAddress.getByName(words[0]).getAddress(),source.getAddress()))return true;
        }
        return false;
    }
}
