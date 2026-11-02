package org.ev6.musicvibe;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;

final class Vehicle {
 static final String SYS="com.hkmc.micom.ISystemService";
 static final String SML_ACTION="com.mobis.intent.action.SML_STATUS_CHANGED", SML_EXTRA="com.mobis.intent.extra.SML_STATUS";
 static IBinder service(String name) throws Exception {IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);if(b==null)throw new Exception("Service unavailable: "+name);return b;}
 interface Fill {void put(Parcel p);}
 static Parcel call(String name,String token,int code,Fill fill)throws Exception {
  String op=name+"/"+code;long began=SystemClock.elapsedRealtime();Diag.start(op);Parcel p=Parcel.obtain(),q=Parcel.obtain();boolean ok=false;
  try{p.writeInterfaceToken(token);if(fill!=null)fill.put(p);if(!service(name).transact(code,p,q,0))throw new Exception("Unsupported Binder transaction: "+code);q.readException();ok=true;return q;}finally{p.recycle();if(!ok)q.recycle();Diag.end(op,began);}
 }
 static void sys(int code,Fill f)throws Exception {Parcel p=call("com.hkmc.micom.system",SYS,code,f);p.recycle();}
 static void request()throws Exception {sys(74,null);}
 static void brightness(final int value)throws Exception {if(value<1||value>11)throw new Exception("Brightness out of range");sys(75,new Fill(){public void put(Parcel p){p.writeInt(5);p.writeInt(value);}});}
 static void rgb(final int r,final int g,final int b)throws Exception {sys(76,new Fill(){public void put(Parcel p){for(int x:new int[]{r,g,b})p.writeByteArray(new byte[]{(byte)(x>>8),(byte)x});}});}
 static int fade()throws Exception {Parcel q=call("com.hkmc.micom.usm","com.hkmc.usm.IUSMService",418,null);try{return q.readInt();}finally{q.recycle();}}
 static void restore(final Lights s)throws Exception {
  Parcel q=call("com.hkmc.micom.usm","com.hkmc.usm.IUSMService",280,new Fill(){public void put(Parcel p){for(int x:new int[]{s.r,s.g,s.b,s.bright,s.dim,s.drive,s.fade})p.writeInt(x);}});q.recycle();rgb(s.r,s.g,s.b);brightness(s.bright);
 }
}
