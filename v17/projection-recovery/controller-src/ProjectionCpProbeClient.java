import android.os.*;
/** Root client pins the actual DiPO binder; a replacement cannot complete old work. */
public final class ProjectionCpProbeClient {
 public static final class Snapshot {public final int state;public final String source,peer;public final long generation;Snapshot(int s,String o,long g,String p){state=s;source=o;generation=g;peer=p;}}
 private IBinder endpoint;private final String boot,request,peer;
 public ProjectionCpProbeClient(String b,String r,String p){boot=b;request=r;peer=p;}
 private Snapshot call(int code)throws Exception{
  IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("checkService",String.class).invoke(null,"ev6.projection.cp.bootstrap");
  if(b==null||!b.isBinderAlive()||(endpoint!=null&&endpoint!=b))throw new java.io.IOException("probe-source-replaced");endpoint=b;
  Parcel in=Parcel.obtain(),out=Parcel.obtain();try{
   in.writeInterfaceToken("org.ev6.CpBootstrap1");in.writeInt(1);in.writeString(boot);in.writeString(request);in.writeString(peer);
   if(!b.transact(code,in,out,0))throw new java.io.IOException("probe-source-unavailable");out.readException();if(out.readInt()!=1)throw new java.io.IOException("probe-reply-version");
   int state=out.readInt();String owner=null,address=null;long generation=0;
   if(state<0||state>5)throw new java.io.IOException("probe-state");
   if(code==3&&state!=0){int has=out.readInt();if(has==1){owner=out.readString();generation=out.readLong();address=out.readString();if(owner==null||!owner.matches("[0-9a-f]{32}")||generation<=0||!peer.equals(address))throw new java.io.IOException("probe-transport-identity");}else if(has!=0)throw new java.io.IOException("probe-transport-marker");}
   if(out.dataAvail()!=0||!b.isBinderAlive())throw new java.io.IOException("probe-reply-tail");return new Snapshot(state,owner,generation,address);
  }finally{in.recycle();out.recycle();}
 }
 public boolean start()throws Exception{int state=call(2).state;return state==1||state==2;}
 public Snapshot read()throws Exception{return call(3);}
 public boolean stop()throws Exception{int state=call(4).state;return state==4||state==0;}
}
