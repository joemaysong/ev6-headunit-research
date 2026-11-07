import android.os.*;
/** Checks exact root lease readback from installed Bluetooth owner; never creates a lease. */
public final class ProjectionAaExclusiveProof {
 public static String readGrant()throws Exception{
  return readGrant(new java.io.File("/data/local/ev6-projection/handoff/start-grant"));
 }
 static String readGrant(java.io.File file)throws Exception{if(!file.isFile()||!file.getCanonicalFile().equals(file.getAbsoluteFile()))throw new java.io.IOException("aa-grant-path");java.io.FileInputStream in=new java.io.FileInputStream(file);try{byte[] bytes=new byte[301];int n=0,k;while(n<bytes.length&&(k=in.read(bytes,n,bytes.length-n))!=-1)n+=k;if(n<1||n>300)throw new java.io.IOException("aa-grant-size");return new String(bytes,0,n,"US-ASCII");}finally{in.close();}
 }
 public static boolean allowed(String address){try{String record=readGrant();String[] p=record.split("\\|",-1);if(p.length!=7)return false;verify(p[2],address,record);return true;}catch(Exception unavailable){return false;}}
 public static void verify(String id,String address,String record)throws Exception{verify(id,address,record,new java.io.File("/data/local/ev6-projection/handoff/start-grant"));}
 static void verify(String id,String address,String record,java.io.File grant)throws Exception{
  String[] p=record==null?new String[0]:record.split("\\|",-1);
  if(p.length!=7||!p[0].equals("EV6BTHOLD1")||!p[2].equals(id.replace("-",""))||!p[4].equals("AA")||!p[5].equals(address)||!p[6].equals("END\n"))throw new SecurityException("aa-lease-identity");
  if(!record.equals(readGrant(grant)))throw new SecurityException("aa-start-not-granted");
  IBinder endpoint=ProjectionDiscoveryService.aaBluetoothOwner();if(endpoint==null||!endpoint.isBinderAlive())throw new IllegalStateException("bt-gate-unavailable");
  long identity=Binder.clearCallingIdentity();
  Parcel in=Parcel.obtain(),out=Parcel.obtain();try{
   in.writeInterfaceToken("org.ev6.BtExclusive1");in.writeInt(1);in.writeString(record);in.writeString(address);if(!endpoint.transact(0x45563602,in,out,0))throw new IllegalStateException("bt-gate-no-reply");out.readException();if(out.readInt()!=1)throw new IllegalStateException("bt-gate-version");String incarnation=out.readString();int revision=out.readInt(),ready=out.readInt();String reason=out.readString();if(incarnation==null||!incarnation.matches("[0-9a-f-]{36}")||revision!=9||ready!=1||!"ready".equals(reason)||out.dataAvail()!=0||!endpoint.isBinderAlive()||ProjectionDiscoveryService.aaBluetoothOwner()!=endpoint||!record.equals(readGrant(grant)))throw new IllegalStateException("bt-gate-proof");
  }finally{in.recycle();out.recycle();Binder.restoreCallingIdentity(identity);}
 }
}
