import java.net.*;
import java.util.*;

/** Read-only DNS identity comparison. Never sends a CarPlay control request. */
public final class MdnsIdentityProbe {
    public static void main(String[] args) throws Exception {
        android.os.Looper.prepareMainLooper();
        CarPlayController controller=new CarPlayController();String target=controller.hfp();
        if(target.length()==0){System.out.println("SELECTED_HFP_PRESENT=false");return;}
        NetworkInterface network=NetworkInterface.getByName("wlan0");
        InetAddress multicast=InetAddress.getByName("ff02::fb%wlan0");
        MulticastSocket socket=new MulticastSocket(null);
        int packets=0,records=0,matches=0;Set<String> keys=new TreeSet<String>();
        try {
            socket.setReuseAddress(true);socket.bind(new InetSocketAddress(5353));
            socket.setNetworkInterface(network);socket.setTimeToLive(255);
            socket.joinGroup(new InetSocketAddress(multicast,5353),network);
            byte[] query=MdnsControl.query();socket.send(new DatagramPacket(query,query.length,multicast,5353));
            long until=System.nanoTime()+4000000000L;
            while(packets<64) {
                long left=(until-System.nanoTime())/1000000;if(left<=0)break;
                socket.setSoTimeout((int)Math.max(1,left));byte[] raw=new byte[4096];DatagramPacket packet=new DatagramPacket(raw,raw.length);
                try{socket.receive(packet);}catch(SocketTimeoutException done){break;}
                packets++;int size=packet.getLength();
                if(packet.getPort()!=5353 || !(packet.getAddress() instanceof Inet6Address) || size<12)continue;
                try {
                    if((MdnsControl.u16(raw,2,size)&0xf80f)!=0x8000)continue;
                    int questions=MdnsControl.u16(raw,4,size),count=MdnsControl.u16(raw,6,size)+MdnsControl.u16(raw,8,size)+MdnsControl.u16(raw,10,size);
                    if(questions>32 || count>128)continue;
                    int pos=12;int[] next=new int[1];
                    for(int q=0;q<questions;q++){MdnsControl.name(raw,pos,size,next);pos=next[0]+4;if(pos>size)throw new java.io.IOException("bounds");}
                    for(int r=0;r<count;r++) {
                        String owner=MdnsControl.name(raw,pos,size,next);pos=next[0];
                        int type=MdnsControl.u16(raw,pos,size),length=MdnsControl.u16(raw,pos+8,size);pos+=10;
                        if(pos+length>size)throw new java.io.IOException("bounds");
                        int end=pos+length;
                        if(type==16 && owner.endsWith(MdnsControl.SERVICE)) {
                            records++;
                            while(pos<end) {
                                int len=raw[pos++]&255;if(pos+len>end)throw new java.io.IOException("txt");
                                String text=new String(raw,pos,len,"US-ASCII");pos+=len;int equal=text.indexOf('=');
                                if(equal<1)continue;String key=text.substring(0,equal),value=text.substring(equal+1);
                                if(key.matches("[A-Za-z0-9_-]{1,40}"))keys.add(key);
                                if((key.equals("id") || key.equals("deviceid")) && value.replace(":","").equalsIgnoreCase(target.replace(":","")))matches++;
                            }
                        }
                        pos=end;
                    }
                }catch(java.io.IOException malformed){}
            }
        }finally{socket.close();}
        System.out.println("MDNS_IDENTITY_PACKETS="+packets);
        System.out.println("MDNS_CONTROL_TXT_RECORDS="+records);
        System.out.println("MDNS_SELECTED_BT_ID_MATCHES="+matches);
        System.out.println("MDNS_CONTROL_TXT_KEYS="+keys.toString());
        System.out.println("CARPLAY_CONTROL_REQUEST_SENT=false");
    }
}
