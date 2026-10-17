import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Credentials;
import java.io.*;

/** Root-only read adapter for the separately reviewed in-process observer.
 * No OEM commands, process injection, native writes or receiver restart.
 */
public final class OemAttemptClient {
    public static OemAttemptSnapshot read(NativeSession session) throws Exception {
        if(session==null || android.os.Process.myUid()!=0)throw new IOException("oem-attempt-root-reader-required");
        String generation=session.generation();
        LocalSocket socket=new LocalSocket();
        try {
            socket.connect(new LocalSocketAddress("ev6.carplay.attempt."+session.pid));
            socket.setSoTimeout(2500);
            Credentials peer=socket.getPeerCredentials();
            if(peer.getUid()!=1000 || peer.getPid()!=session.pid)throw new IOException("oem-attempt-peer-mismatch");
            socket.getOutputStream().write(0x51);socket.getOutputStream().flush();
            InputStream input=socket.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            for(int count=0;;count++) {
                int next=input.read();
                if(next==10)break;
                if(next<0 || next<32 || next>126 || count>=512)throw new IOException("oem-attempt-reply-unavailable");
                bytes.write(next);
            }
            if(!generation.equals(session.generation()))throw new IOException("oem-attempt-receiver-changed");
            return OemAttemptSnapshot.parse(new String(bytes.toByteArray(),"US-ASCII"),generation);
        }finally{socket.close();}
    }
}
