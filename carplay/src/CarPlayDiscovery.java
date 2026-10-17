import java.io.*;
import java.net.*;
import java.util.*;

/** One continuous discovery subscription; no HTTP polling or first-peer choice. */
public final class CarPlayDiscovery implements Closeable {
    public static final class Location {
        final InetAddress address;
        final MdnsControl.Endpoint endpoint;
        final String station;
        final boolean identityFromTxt;
        final int associationCount;
        Location(InetAddress a,MdnsControl.Endpoint e,String s,boolean t,int count){address=a;endpoint=e;station=s;identityFromTxt=t;associationCount=count;}
    }
    final String target,known;
    final Set<String> excluded;
    final NetworkInterface network;
    final MulticastSocket socket;
    final MdnsIdentity identity=new MdnsIdentity();
    final Set<String> nudged=new HashSet<String>();
    final byte[] query;
    InetAddress pendingSource;
    public String diagnosis="not-started";
    public int received,identityMatches,neighborMatches;
    public CarPlayDiscovery(String selected,String knownStation,Set<String> otherStations) throws Exception {
        if(MdnsIdentity.normalize(selected).length()==0)throw new IOException("selected-device");
        target=selected;known=knownStation;excluded=new HashSet<String>(otherStations);
        network=NetworkInterface.getByName("wlan0");if(network==null)throw new IOException("no-wlan");
        socket=new MulticastSocket(null);query=MdnsControl.query();
        try {
            socket.setReuseAddress(true);socket.bind(new InetSocketAddress(5353));
            socket.setNetworkInterface(network);socket.setTimeToLive(255);
            InetAddress group=InetAddress.getByName("ff02::fb%wlan0");
            socket.joinGroup(new InetSocketAddress(group,5353),network);
            socket.send(new DatagramPacket(query,query.length,group,5353));diagnosis="selected-device-discovery";
        }catch(Exception failure){socket.close();throw failure;}
    }
    public Location poll() throws Exception {
        // NDP may complete after the DNS answer. Revalidate the cached answer
        // without requiring another DNS packet or retransmitting a query.
        if(pendingSource!=null){Location cached=resolve(pendingSource);if(cached!=null)return cached;}
        long until=System.nanoTime()+1000000000L;
        for(int packetIndex=0;packetIndex<64;packetIndex++) {
            long left=(until-System.nanoTime())/1000000;if(left<=0)return null;
            socket.setSoTimeout((int)Math.max(1,left));byte[] raw=new byte[4096];DatagramPacket packet=new DatagramPacket(raw,raw.length);
            try{socket.receive(packet);}catch(SocketTimeoutException pending){return null;}
            received++;InetAddress source=packet.getAddress();
            if(packet.getPort()!=5353 || !(source instanceof Inet6Address) || !source.isLinkLocalAddress())continue;
            String sourceKey=source.getHostAddress();
            try{identity.accept(raw,packet.getLength(),sourceKey,System.nanoTime());}catch(IOException malformed){continue;}
            Location location=resolve(source);if(location!=null)return location;
        }
        return null;
    }
    private Location resolve(InetAddress source) throws Exception {
        String sourceKey=source.getHostAddress();
        MdnsControl.Endpoint endpoint=identity.select(sourceKey,target,System.nanoTime(),false);
        if(endpoint==null && known.length()>0)endpoint=identity.select(sourceKey,target,System.nanoTime(),true);
        if(endpoint==null)return null;
        pendingSource=source;
        boolean advertisedIdentity=identity.select(sourceKey,target,System.nanoTime(),false)!=null;
        if(advertisedIdentity)identityMatches++;
        String neighbors=CarPlayController.exec(null,2000,"/system/bin/ip","-6","neigh","show","dev","wlan0");
        Set<String> stations=CarPlayController.associatedStations();String station=stationFor(neighbors,source,stations);
        InetAddress scoped=Inet6Address.getByAddress(null,source.getAddress(),network);
        if(station.length()==0) {
            if(nudged.size()<32 && nudged.add(sourceKey))socket.send(new DatagramPacket(query,query.length,scoped,5353));
            diagnosis="selected-device-neighbor-pending";return null;
        }
        if(excluded.contains(station) || (!advertisedIdentity && !station.equalsIgnoreCase(known)))return null;
        neighborMatches++;diagnosis="selected-device-identity-verified";
        return new Location(scoped,endpoint,station,advertisedIdentity,stations.size());
    }
    static String stationFor(String text,InetAddress source,Set<String> stations) throws Exception {
        String found="";
        for(String station:stations)if(MdnsControl.neighborMatches(text,source,station)) {
            if(found.length()>0 && !found.equals(station))return "";
            found=station;
        }
        return found;
    }
    public void close(){socket.close();}
}
