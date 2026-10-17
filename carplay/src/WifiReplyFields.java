package org.ev6.research;

import java.io.IOException;

/** Compare bounded observed Wi-Fi message fields without retaining their values. */
public final class WifiReplyFields {
    public static final class Result {
        public int fields;
        public boolean ssid, key, primaryKey;
    }
    static int u16(byte[] bytes,int offset){return ((bytes[offset]&255)<<8)|(bytes[offset+1]&255);}
    static boolean equalsField(byte[] bytes,int start,int end,String expected) throws IOException {
        if(expected==null || expected.length()==0)return false;
        byte[] value=expected.getBytes("UTF-8");
        // The native string parameter includes one terminating NUL.
        if(end>start && bytes[end-1]==0)end--;
        if(end-start!=value.length)return false;
        int difference=0;for(int n=0;n<value.length;n++)difference|=bytes[start+n]^value[n];
        return difference==0;
    }
    public static Result compare(byte[] bytes,int start,int end,String ssid,String key,String primary) throws IOException {
        if(bytes==null || start<0 || end<start || end>bytes.length || end-start<6 || end-start>1024)throw new IOException("wifi-message-bounds");
        if(u16(bytes,start)!=0x4040 || u16(bytes,start+2)!=end-start || u16(bytes,start+4)!=0x5703)throw new IOException("wifi-message-header");
        Result result=new Result();
        for(int offset=start+6;offset<end;) {
            if(end-offset<4 || result.fields>=16)throw new IOException("wifi-field-header");
            int size=u16(bytes,offset);if(size<4 || size>end-offset)throw new IOException("wifi-field-size");
            result.fields++;
            result.ssid|=equalsField(bytes,offset+4,offset+size,ssid);
            result.key|=equalsField(bytes,offset+4,offset+size,key);
            result.primaryKey|=equalsField(bytes,offset+4,offset+size,primary);
            offset+=size;
        }
        return result;
    }
}
