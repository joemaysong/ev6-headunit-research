import android.content.*;import android.os.*;
/** GPM owns a real OEM service binding. Only its real Binder is exported to root. */
public final class ProjectionBluetoothBinding implements ServiceConnection {
 private volatile IBinder binder;
 public void start(Context context){Intent i=new Intent("com.broadcom.bt.hfdevice.hfbtcm.IHfDeviceBTCMService");i.setClassName("com.android.bluetooth","com.broadcom.bt.service.btcmservice.HfDeviceBTCMService");if(!context.bindService(i,this,Context.BIND_AUTO_CREATE))throw new IllegalStateException("bt-oem-bind-refused");}
 public void onServiceConnected(ComponentName name,IBinder service){if(!name.getPackageName().equals("com.android.bluetooth")||!name.getClassName().equals("com.broadcom.bt.service.btcmservice.HfDeviceBTCMService"))throw new SecurityException("bt-oem-component");binder=service;}
 public void onServiceDisconnected(ComponentName name){binder=null;}
 public boolean export(Parcel data,Parcel reply,int flags,String incarnation){
  if(Binder.getCallingUid()!=0||reply==null||(flags&~0x10)!=0)throw new SecurityException("bt-binding-root");
  data.enforceInterface("org.ev6.ProjectionDiscovery1");if(data.readInt()!=5||data.dataAvail()!=0)throw new SecurityException("bt-binding-schema");
  reply.writeNoException();reply.writeInt(5);reply.writeString(incarnation);reply.writeStrongBinder(current());return true;
 }
 public IBinder current(){IBinder b=binder;return b!=null&&b.isBinderAlive()?b:null;}
}
