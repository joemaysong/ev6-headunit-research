package org.ev6.launchpad;
import android.content.Context;import android.net.*;import java.io.*;
/** Authenticated local socket; no unrestricted shell or arbitrary package operations. */
final class Root {
 static String call(Context c,String action)throws Exception {
  LocalSocket s=new LocalSocket();try{s.connect(new LocalSocketAddress("ev6.launchpad.control.v2"));s.setSoTimeout(65000);if(s.getPeerCredentials().getUid()!=0)throw new IOException("Unexpected bridge identity");DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeUTF(action);out.writeLong(android.os.SystemClock.elapsedRealtime()+65000);out.flush();DataInputStream in=new DataInputStream(s.getInputStream());boolean ok=in.readBoolean();int len=in.readInt();if(len<0||len>120000)throw new IOException("Invalid response");byte[] b=new byte[len];in.readFully(b);String value=new String(b,"UTF-8");if(!ok)throw new IOException(value);return value;}finally{s.close();}
 }
}
