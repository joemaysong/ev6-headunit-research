import android.os.*;
/** Root-only display recovery certificate, issued while existing maintenance holds admission. */
public final class ProjectionUiRecoveryClient {
 public interface Port {void verify()throws Exception;}
 private static IBinder endpoint()throws Exception{return (IBinder)Class.forName("android.os.ServiceManager").getMethod("checkService",String.class).invoke(null,"ev6.projection.discovery");}
 public static void certify(Port port)throws Exception{
  if(android.os.Process.myUid()!=0)throw new SecurityException("recovery-root-only");final IBinder service=endpoint();if(service==null)return;
  String incarnation,batch;Parcel data=Parcel.obtain(),reply=Parcel.obtain();
  try{data.writeInterfaceToken("org.ev6.ProjectionDiscovery1");data.writeInt(5);if(!service.transact(8,data,reply,0))throw new IllegalStateException("recovery-snapshot");reply.readException();if(reply.readInt()!=5)throw new IllegalStateException("recovery-version");incarnation=reply.readString();batch=reply.readString();int count=reply.readInt();if(incarnation==null||batch==null||!incarnation.matches("[0-9a-f-]{36}")||!batch.matches("[0-9a-f-]{36}")||count<0||count>64||reply.dataAvail()!=0)throw new IllegalStateException("recovery-schema");}finally{data.recycle();reply.recycle();}
  boolean certified=false;try{port.verify();if(endpoint()!=service||!service.isBinderAlive())throw new IllegalStateException("recovery-endpoint-changed");
  data=Parcel.obtain();reply=Parcel.obtain();try{data.writeInterfaceToken("org.ev6.ProjectionDiscovery1");data.writeInt(5);data.writeString(incarnation);data.writeString(batch);if(!service.transact(9,data,reply,0))throw new IllegalStateException("recovery-certificate");reply.readException();if(reply.readInt()!=5||!incarnation.equals(reply.readString())||reply.readInt()!=1||reply.dataAvail()!=0)throw new IllegalStateException("recovery-certificate-rejected");certified=true;}finally{data.recycle();reply.recycle();}
  }finally{if(!certified){Parcel cancel=Parcel.obtain(),answer=Parcel.obtain();try{cancel.writeInterfaceToken("org.ev6.ProjectionDiscovery1");cancel.writeInt(5);cancel.writeString(incarnation);cancel.writeString(batch);service.transact(10,cancel,answer,0);}catch(Exception ignored){}finally{cancel.recycle();answer.recycle();}}}
 }
}
