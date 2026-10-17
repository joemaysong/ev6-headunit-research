import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;

/** Synthetic isolated VM only. No OEM class, Service, vehicle or native calls. */
public final class BridgePlatformCheck {
    private static final class FixtureUtils {private String mConnectDeviceID="",mReconnectAddress="";}
    private static final class FixtureOwner {private final FixtureUtils mUtils=new FixtureUtils();}
    private enum Result {OK,NOT_SUPPORTED}
    private static final String TARGET="00:11:22:33:44:55";
    private static void uid(int value) throws Exception {
        Method method=android.os.Process.class.getDeclaredMethod("setUid",Integer.TYPE);
        method.invoke(null,value);
        if(android.os.Process.myUid()!=value)throw new AssertionError("Isolated UID did not change");
    }
    public static void main(String[] args) throws Exception {
        if(args[0].equals("query")) {
            int pid=Integer.parseInt(args[1]);
            if(args.length>2 && args[2].equals("untrusted"))uid(2000);
            LocalSocket socket=new LocalSocket();
            boolean trustedServer=false;
            try {
                socket.connect(new LocalSocketAddress("ev6.carplay.attempt."+pid));
                socket.setSoTimeout(2500);
                if(socket.getPeerCredentials().getUid()!=1000 || socket.getPeerCredentials().getPid()!=pid)
                    throw new AssertionError("Isolated peer credentials differ");
                trustedServer=true;
                socket.getOutputStream().write(0x51);socket.getOutputStream().flush();
                BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),"US-ASCII"));
                String reply=input.readLine();
                // Never print a target or receiver identity. Emit closed flags.
                if(reply==null){System.out.println("QUERY_REJECTED=true");return;}
                if(reply.equals("EV6ATTEMPT4|UNKNOWN")){System.out.println("QUERY_UNKNOWN=true");return;}
                String[] fields=reply.split("\\|",-1);
                if(fields.length!=14 || !fields[0].equals("EV6ATTEMPT4"))
                    throw new AssertionError("Observer schema differs");
                System.out.println("QUERY_SEQUENCE="+Long.parseLong(fields[2]));
                System.out.println("QUERY_OWNER_INSTANCE="+Long.parseLong(fields[1].split(":")[2]));
                System.out.println("QUERY_QUEUED="+Integer.parseInt(fields[7]));
                System.out.println("QUERY_DISPATCHES="+Integer.parseInt(fields[5]));
                System.out.println("QUERY_CONNECT_CALLS="+Integer.parseInt(fields[6]));
                System.out.println("QUERY_UNKNOWN="+Boolean.parseBoolean(fields[8]));
                System.out.println("QUERY_COMPLETE="+Boolean.parseBoolean(fields[9]));
                System.out.println("QUERY_REENTRY_INTENT="+Integer.parseInt(fields[10]));
                System.out.println("QUERY_WORK_SETTLED="+Boolean.parseBoolean(fields[11]));
                System.out.println("QUERY_CONNECT_ENTRIES="+Integer.parseInt(fields[12]));
                System.out.println("QUERY_PREFLIGHT_REJECTED="+Boolean.parseBoolean(fields[13]));
            }catch(IOException rejected){
                if(args.length>2 && args[2].equals("untrusted")) {
                    String reason=rejected.getMessage();
                    boolean permission=reason!=null && (reason.contains("Permission denied") || reason.contains("Operation not permitted"));
                    System.out.println("QUERY_REJECTED="+(trustedServer || permission));
                    System.out.println("QUERY_REJECTION_PERMISSION="+permission);
                    System.out.println("QUERY_REJECTION_UNAVAILABLE="+(!trustedServer && !permission));
                }else throw rejected;
            }finally{socket.close();}
            return;
        }
        uid(1000);
        final Object[] owners={new FixtureOwner()};
        HandlerThread thread=new HandlerThread("Isolated-observer-fixture");thread.start();
        Handler handler=new Handler(thread.getLooper()) {
            public boolean sendMessageAtTime(Message message,long uptime) {
                Object owner=owners[0];long token=EV6OemAttemptBridge.enteredQueue(owner,message);
                boolean accepted=false;
                try {accepted=super.sendMessageAtTime(message,uptime);return accepted;}
                finally {EV6OemAttemptBridge.exitedQueue(owner,token,accepted);}
            }
            public void handleMessage(Message message) {
                Object owner=owners[0];
                EV6OemAttemptBridge.enteredDispatch(owner,message);
                boolean normal=false;
                try {
                    if(message.what==0x2c && message.arg1!=1) {
                        EV6OemAttemptBridge.enteredConnect(owner,TARGET);
                        EV6OemAttemptBridge.enteredDelegate(owner,TARGET);
                        EV6OemAttemptBridge.returnedConnect(owner,message.arg1==2?Result.NOT_SUPPORTED:Result.OK,true);
                        if(message.arg1==0)sendEmptyMessageDelayed(0x31,120000);
                    }
                    normal=true;
                }finally{EV6OemAttemptBridge.exitedDispatch(owner,message,normal);}
            }
        };
        EV6OemAttemptBridge.install(owners[0],handler);
        final CountDownLatch blocked=new CountDownLatch(1);
        BufferedReader commands=new BufferedReader(new InputStreamReader(System.in,"US-ASCII"));
        long eventToken=-1;
        System.out.println("ISOLATED_OBSERVER_READY_PID="+android.os.Process.myPid());System.out.flush();
        try {
            String command;
            while((command=commands.readLine())!=null && !command.equals("quit")) {
                if(command.equals("submit")) {
                    EV6OemAttemptBridge.submitted(owners[0],TARGET);handler.sendEmptyMessage(0x2c);
                }else if(command.equals("preflight-rejected") || command.equals("unsupported") || command.equals("ok-no-timeout")) {
                    EV6OemAttemptBridge.submitted(owners[0],TARGET);
                    Message message=handler.obtainMessage(0x2c);
                    message.arg1=command.equals("preflight-rejected")?1:command.equals("unsupported")?2:3;
                    handler.sendMessage(message);
                }else if(command.equals("arm-reentry") || command.equals("clear-reentry")) {
                    long token=EV6OemAttemptBridge.enteredEvent(owners[0]);
                    ((FixtureOwner)owners[0]).mUtils.mReconnectAddress=command.equals("arm-reentry")?TARGET:"";
                    EV6OemAttemptBridge.exitedEvent(owners[0],token,false,true);
                }else if(command.equals("terminal")) {
                    handler.removeMessages(0x31);handler.sendEmptyMessage(0x31);
                }else if(command.equals("drop-private-listener")) {
                    java.lang.reflect.Field field=EV6OemAttemptBridge.class.getDeclaredField("activeListener");
                    field.setAccessible(true);
                    android.net.LocalServerSocket listener=(android.net.LocalServerSocket)field.get(null);
                    if(listener==null)throw new IllegalStateException("Fixture listener not available");
                    listener.close();
                }else if(command.equals("delay-cleanup"))handler.sendEmptyMessageDelayed(0xe,120000);
                else if(command.equals("flush-cleanup")) {
                    handler.removeMessages(0xe);handler.sendEmptyMessage(0xe);
                }else if(command.equals("block")) {
                    handler.post(new Runnable(){public void run(){try{blocked.await();}catch(InterruptedException ignored){}}});
                }else if(command.equals("unblock"))blocked.countDown();
                else if(command.equals("release"))EV6OemAttemptBridge.release(owners[0]);
                else if(command.equals("recreate")) {
                    Object old=owners[0];owners[0]=new FixtureOwner();
                    EV6OemAttemptBridge.install(owners[0],handler);
                    EV6OemAttemptBridge.submitted(old,TARGET);EV6OemAttemptBridge.release(old);
                }else if(command.equals("threads")) {
                    int count=0;
                    for(Thread t:Thread.getAllStackTraces().keySet())
                        if(t.isAlive() && t.getName().equals("EV6-attempt-observer"))count++;
                    System.out.println("ISOLATED_THREAD_COUNT="+count);
                    java.lang.reflect.Field repairs=EV6OemAttemptBridge.class.getDeclaredField("acceptedDescriptorRepairs");
                    repairs.setAccessible(true);
                    System.out.println("ISOLATED_ACCEPTED_DESCRIPTOR_REPAIRS="+repairs.getInt(null));
                }else if(command.equals("event-enter"))eventToken=EV6OemAttemptBridge.enteredEvent(owners[0]);
                else if(command.equals("event-exit"))EV6OemAttemptBridge.exitedEvent(owners[0],eventToken,false,true);
                else if(command.equals("cancel-before-submit") || command.equals("stale-cancel")) {
                    long token=EV6OemAttemptBridge.enteredCancelEvent(owners[0],"AA:BB:CC:DD:EE:FF");
                    EV6OemAttemptBridge.exitedEvent(owners[0],token,true,true);
                }
                else throw new IllegalArgumentException("Unknown isolated fixture input");
                System.out.println("ISOLATED_FIXTURE_ACK="+command);System.out.flush();
            }
        }finally{
            blocked.countDown();EV6OemAttemptBridge.release(owners[0]);thread.quit();
        }
        System.out.println("NO_OEM_SERVICE_OR_NATIVE_EXECUTION=true");
    }
}
