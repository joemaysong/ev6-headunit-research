import java.io.*;
import java.util.*;

/** Bounded DNS-SD cache keyed by packet source and service owner. */
public final class MdnsIdentity {
    static final class Service {
        String source,owner,host,id;
        int port;
        long srvUntil,txtUntil;
        boolean txtSeen;
    }
    static final class Update {
        String owner,host,id;
        int type,port;
        long ttl;
    }
    final Map<String,Service> services=new LinkedHashMap<String,Service>();
    static String normalize(String value) {
        if(value==null)return "";
        if(value.matches("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}") || value.matches("(?i)[0-9a-f]{12}"))return value.replace(":","").toUpperCase(Locale.US);
        return "";
    }
    public void accept(byte[] raw,int size,String source,long now) throws IOException {
        if(size<12 || size>raw.length)return;
        int flags=MdnsControl.u16(raw,2,size);
        if((flags&0xf80f)!=0x8000 || (flags&0x0200)!=0)return;
        int questions=MdnsControl.u16(raw,4,size),count=MdnsControl.u16(raw,6,size)+MdnsControl.u16(raw,8,size)+MdnsControl.u16(raw,10,size);
        if(questions>32 || count>128)throw new IOException("dns-count");
        List<Update> changes=new ArrayList<Update>();int pos=12;int[] next=new int[1];
        for(int q=0;q<questions;q++){MdnsControl.name(raw,pos,size,next);pos=next[0]+4;if(pos>size)throw new IOException("dns-question");}
        for(int r=0;r<count;r++) {
            String owner=MdnsControl.name(raw,pos,size,next);pos=next[0];
            int type=MdnsControl.u16(raw,pos,size),klass=MdnsControl.u16(raw,pos+2,size)&0x7fff;
            if(pos+10>size)throw new IOException("dns-record");
            long ttl=((long)(raw[pos+4]&255)<<24)|((long)(raw[pos+5]&255)<<16)|((long)(raw[pos+6]&255)<<8)|(raw[pos+7]&255);
            int length=MdnsControl.u16(raw,pos+8,size);pos+=10;int end=pos+length;
            if(end>size)throw new IOException("dns-rdata");
            if(klass==1 && owner.endsWith(MdnsControl.SERVICE) && (type==33 || type==16)) {
                Update update=new Update();update.owner=owner;update.type=type;update.ttl=ttl;
                if(type==33) {
                    if(length<7)throw new IOException("dns-srv");
                    update.port=MdnsControl.u16(raw,pos+4,size);update.host=MdnsControl.name(raw,pos+6,size,next);
                    if(next[0]>end || update.port==0 || !update.host.matches("[a-z0-9._-]+\\.local\\."))throw new IOException("dns-srv");
                } else {
                    int textPos=pos;boolean found=false;
                    while(textPos<end) {
                        int len=raw[textPos++]&255;if(textPos+len>end)throw new IOException("dns-txt");
                        String text=new String(raw,textPos,len,"US-ASCII");textPos+=len;
                        int equal=text.indexOf('=');
                        if(equal==2 && text.substring(0,equal).equalsIgnoreCase("id")) {
                            String id=normalize(text.substring(equal+1));if(id.length()==0)id="!invalid";
                            if(found && !id.equals(update.id))id="!ambiguous";
                            update.id=id;found=true;
                        }
                    }
                }
                changes.add(update);
            }
            pos=end;
        }
        // Malformed packets never partially update an otherwise valid cache.
        for(Update update:changes) {
            String key=source+" "+update.owner;Service service=services.get(key);
            if(service==null) {
                if(services.size()>=32)throw new IOException("dns-cache-size");
                service=new Service();service.source=source;service.owner=update.owner;services.put(key,service);
            }
            long until=now+Math.min(update.ttl,3600L)*1000000000L;
            if(update.type==33){service.port=update.port;service.host=update.host;service.srvUntil=until;}
            else {service.id=update.id;service.txtSeen=true;service.txtUntil=until;}
        }
    }
    public MdnsControl.Endpoint select(String source,String target,long now,boolean verifiedKnownSource) {
        String expected=normalize(target);if(expected.length()==0)return null;
        MdnsControl.Endpoint result=null;
        for(Service service:services.values()) {
            if(!service.source.equals(source) || service.port==0 || service.srvUntil<=now)continue;
            if(service.txtSeen && service.txtUntil<=now)continue;
            if(service.id!=null) {if(!expected.equals(service.id))continue;}
            else if(!verifiedKnownSource)continue;
            // Do not choose arbitrarily between conflicting advertisements.
            if(result!=null && (result.port!=service.port || !result.host.equals(service.host)))return null;
            result=new MdnsControl.Endpoint(service.port,service.host);
        }
        return result;
    }
}
