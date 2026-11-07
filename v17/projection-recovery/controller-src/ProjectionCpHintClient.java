import android.os.*;
/** Publish only after the caller's live SessionEvidence verification. Never grants transport/AP authority. */
public final class ProjectionCpHintClient {
 public interface Port {void verify()throws Exception;}
 private IBinder published;private String key="";
 private static IBinder endpoint()throws Exception{return (IBinder)Class.forName("android.os.ServiceManager").getMethod("checkService",String.class).invoke(null,"ev6.projection.discovery");}
 public boolean publish(String address,String receiver,long epoch,Port proof)throws Exception{
  if(android.os.Process.myUid()!=0)throw new SecurityException("cp-hint-root");IBinder service=endpoint();if(service==null||!service.isBinderAlive())return false;String identity=address+"|"+receiver+"|"+epoch;if(published==service&&key.equals(identity))return true;
  Parcel data=Parcel.obtain(),reply=Parcel.obtain();String incarnation;
  try{data.writeInterfaceToken("org.ev6.ProjectionDiscovery1");data.writeInt(5);if(!service.transact(12,data,reply,0))return false;reply.readException();if(reply.readInt()!=5)return false;incarnation=reply.readString();if(reply.dataAvail()!=0)return false;}finally{data.recycle();reply.recycle();}
  proof.verify();if(endpoint()!=service||!service.isBinderAlive())return false;
  data=Parcel.obtain();reply=Parcel.obtain();try{data.writeInterfaceToken("org.ev6.ProjectionDiscovery1");data.writeInt(5);data.writeString(incarnation);data.writeString(address);data.writeString(receiver);data.writeLong(epoch);if(!service.transact(11,data,reply,0))return false;reply.readException();if(reply.readInt()!=5||!incarnation.equals(reply.readString())||reply.readInt()!=1||reply.dataAvail()!=0)return false;published=service;key=identity;return true;}finally{data.recycle();reply.recycle();}
 }
}
