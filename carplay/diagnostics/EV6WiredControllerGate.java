import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.SystemClock;
import java.lang.reflect.*;
import java.util.*;

/** Orders ONLY the two OEM USB identification submissions behind fresh Wired Added/Updated.
 * Replays the original listener API. Never changes controllers, sessions or BT work.
 */
public final class EV6WiredControllerGate {
    static Owner owner;
    static final class Owner {
        final Object service;final Handler handler;
        long usbEpoch,revision,intentEpoch;boolean attached;Ticket pending;
        final Map<String,Object> wired=new HashMap<String,Object>();
        Owner(Object s,Handler h){service=s;handler=h;}
    }
    static final class Ticket {
        final Owner owner;final Object listener;final long epoch,intentEpoch,due,expires;
        final String target,originalAddress;final int what,arg1,arg2;
        Runnable release,expiry;
        Ticket(Owner o,Object l,long e,int w,int a,int b,String t,int delay,int timeout) {
            owner=o;listener=l;epoch=e;intentEpoch=o.intentEpoch;what=w;arg1=a;arg2=b;target=key(t);originalAddress=t;
            long now=SystemClock.uptimeMillis();due=now+Math.max(0,delay);expires=now+timeout;
        }
    }
    static Method method(Class<?> type,String name,Class<?>...args) throws Exception {
        for(Class<?> k=type;k!=null;k=k.getSuperclass())try{Method m=k.getDeclaredMethod(name,args);m.setAccessible(true);return m;}catch(NoSuchMethodException absent){}
        throw new NoSuchMethodException(name);
    }
    static Object call(Object value,String name) throws Exception {return method(value.getClass(),name).invoke(value);}
    static Object field(Object value,String name) throws Exception {
        for(Class<?> k=value.getClass();k!=null;k=k.getSuperclass())try{Field f=k.getDeclaredField(name);f.setAccessible(true);return f.get(value);}catch(NoSuchFieldException absent){}
        throw new NoSuchFieldException(name);
    }
    static String key(String target){return target==null?"":target.toUpperCase(java.util.Locale.US);}
    static void log(String event,Owner o) {android.util.Log.i("CarPlay_","EV6_WIRED_READY revision=16 event="+event+" usb_epoch="+o.usbEpoch+" attached="+o.attached+" pending="+(o.pending!=null));}
    static boolean onHandler(Owner o){return Thread.currentThread()==o.handler.getLooper().getThread();}
    static void retire(Owner o,String why) {
        Ticket p=o.pending;o.pending=null;o.revision++;
        if(p!=null){if(p.release!=null)o.handler.removeCallbacks(p.release);if(p.expiry!=null)o.handler.removeCallbacks(p.expiry);log(why,o);}
    }
    public static synchronized boolean pending(Object service){return owner!=null && owner.service==service && owner.pending!=null;}
    public static synchronized long revision(Object service){return owner!=null && owner.service==service?owner.revision:-1;}
    public static synchronized void install(Object service,Handler handler) {
        if(owner!=null && owner.service==service)return;
        if(owner!=null)retire(owner,"service-replaced");owner=new Owner(service,handler);log("installed",owner);
    }
    public static synchronized void usb(Object service,Intent intent) {
        Owner o=owner;if(o==null || o.service!=service || !onHandler(o) || intent==null)return;
        String a=intent.getAction();boolean add="android.hardware.usb.action.CARPLAY_DEVICE_ATTACHED".equals(a);
        if(!add && !"android.hardware.usb.action.CARPLAY_DEVICE_DETACHED".equals(a))return;
        retire(o,"usb-boundary-retired");o.wired.clear();o.intentEpoch++;
        if(o.usbEpoch==Long.MAX_VALUE){o.attached=false;log("epoch-exhausted",o);return;}
        o.usbEpoch++;o.attached=add;log(add?"usb-attached":"usb-detached",o);
    }
    public static synchronized void removal(Object service,int what) {
        Owner o=owner;if(o!=null && o.service==service && (what==0x2c || what==-1)){o.intentEpoch++;retire(o,"original-request-removed");}
    }
    static Object currentWired(Owner o,String target) throws Exception {
        Object list=field(o.service,"mControllerDevices");if(!(list instanceof List))return null;
        synchronized(list) {
            for(Object value:(List<?>)list)if(key((String)call(value,"getDeviceID")).equals(target)
                && "Wired".equals(String.valueOf(call(value,"getCarPlayTransportType"))))return value;
        }
        return null;
    }
    static boolean active(Owner o) throws Exception {return (Boolean)call(o.service,"isDipoActivated");}
    static boolean cableAndIdentity(Ticket t) throws Exception {
        Object utils=field(t.owner.service,"mUtils");
        boolean connected=(Boolean)method(utils.getClass(),"getUSBCableConnection",Context.class).invoke(utils,(Context)t.owner.service);
        return connected && key((String)call(utils,"getCurrentWiredAddress")).equals(t.target);
    }
    static boolean live(Ticket t) {return owner==t.owner && t.owner.attached && t.owner.usbEpoch==t.epoch && t.owner.intentEpoch==t.intentEpoch && t.owner.pending==t;}
    static void original(Ticket t,int delay) throws Exception {
        method(t.listener.getClass(),"sendHandleMessage",int.class,int.class,int.class,String.class,int.class)
            .invoke(t.listener,t.what,t.arg1,t.arg2,t.originalAddress,delay);
    }
    static void ready(final Ticket t) throws Exception {
        final Owner o=t.owner;
        if(!live(t))return;
        if(!cableAndIdentity(t)){retire(o,"cable-or-identity-changed");return;}
        if(active(o)){retire(o,"active-session-preserved");return;}
        Object expected=o.wired.get(t.target);
        if(expected==null || currentWired(o,t.target)!=expected){log("waiting-wired-added",o);return;}
        if(SystemClock.uptimeMillis()>t.expires){expire(t);return;}
        if(SystemClock.uptimeMillis()<t.due) {
            if(t.release==null)t.release=new Runnable(){public void run(){synchronized(EV6WiredControllerGate.class){try{ready(t);}catch(Exception e){retire(o,"release-unverified");}}}};
            o.handler.removeCallbacks(t.release);
            if(!o.handler.postAtTime(t.release,t.due))retire(o,"release-post-rejected");
            return;
        }
        // The OEM Handler serializes Added, removal, USB broadcasts and enqueue.
        retire(o,"fresh-wired-submit");original(t,0);
    }
    static void expire(Ticket t) throws Exception {
        if(!live(t))return;Owner o=t.owner;
        boolean valid=cableAndIdentity(t) && !active(o);
        // Same OEM method called by original Handler message 0x0d, on that Handler.
        // Retain pending evidence until the complete original method returns.
        if(valid) {
            log("original-timeout-expired",o);
            try{call(o.service,"displayConnectionError");}
            finally{retire(o,"original-timeout-returned");}
        }else retire(o,"expired-intent-no-longer-valid");
    }
    public static synchronized void added(Object service,String address,Object transport) {observed(service,address,transport,"added");}
    public static synchronized void updated(Object service,String address,Object transport) {observed(service,address,transport,"updated");}
    static void observed(Object service,String address,Object transport,String source) {
        Owner o=owner;if(o==null || o.service!=service || !onHandler(o))return;
        String target=key(address);
        try {
            o.wired.remove(target);
            if(o.attached && "Wired".equals(String.valueOf(transport))) {
                Object entry=currentWired(o,target);
                if(entry!=null){o.wired.put(target,entry);log("fresh-wired-"+source,o);}
            }
            if(o.pending!=null && o.pending.target.equals(target))ready(o.pending);
        }catch(Exception e){retire(o,"controller-event-unverified");}
    }
    public static synchronized void removed(Object service,String address) {
        Owner o=owner;if(o==null || o.service!=service || !onHandler(o))return;
        String target=key(address);Object prior=o.wired.remove(target);o.revision++;
        // A stale Wireless controller may be removed immediately before the fresh Wired Added/Updated.
        // Only retire if this epoch actually observed the removed Wired controller.
        if(prior!=null && o.pending!=null && o.pending.target.equals(target)){o.intentEpoch++;retire(o,"controller-removed");}
    }
    public static void request(final Object listener,final int what,final int arg1,final int arg2,final String address,final int delay) {
        try {
            final Owner o;final Ticket t;
            synchronized(EV6WiredControllerGate.class) {
                o=owner;
                if(o==null || field(listener,"this$0")!=o.service || !o.attached || what!=0x2c || arg1!=0 || arg2!=0
                    || !key(address).matches("([0-9A-F]{2}:){5}[0-9A-F]{2}")) {
                    if(o!=null)log("request-boundary-unverified",o);return;
                }
                int timeout=(Integer)field(o.service,"mConnectionErrorTime");
                if(timeout<=delay || timeout<=0){log("original-timeout-unverified",o);return;}
                retire(o,"request-replaced");o.intentEpoch++;
                t=new Ticket(o,listener,o.usbEpoch,what,arg1,arg2,address,delay,timeout);
                // Publish gate ownership before posting, so root cannot see a false idle gap.
                o.pending=t;o.revision++;
            }
            Runnable accept=new Runnable(){public void run(){synchronized(EV6WiredControllerGate.class){
                try {
                    if(!live(t)){log("stale-usb-request",o);return;}
                    // Preserve the OEM ALREADY_IN_USE path for an existing session.
                    if(active(o)){retire(o,"active-session-original-path");original(t,(int)Math.max(0,t.due-SystemClock.uptimeMillis()));return;}
                    t.expiry=new Runnable(){public void run(){synchronized(EV6WiredControllerGate.class){try{expire(t);}catch(Exception e){retire(o,"expiry-unverified");}}}};
                    if(!o.handler.postAtTime(t.expiry,t.expires)){retire(o,"expiry-post-rejected");return;}
                    log("wired-intent-pending",o);ready(t);
                }catch(Exception e){retire(o,"request-unverified");}
            }}};
            if(onHandler(o))accept.run();else if(!o.handler.post(accept)){synchronized(EV6WiredControllerGate.class){if(o.pending==t)retire(o,"request-post-rejected");}}
        }catch(Exception ignored){}
    }
}
