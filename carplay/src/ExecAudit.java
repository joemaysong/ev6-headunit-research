import java.io.*;
import java.util.*;
import java.util.regex.*;
public final class ExecAudit {
 static void summary(String prefix,String text) {
  Matcher matches=Pattern.compile("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}").matcher(text);
  Set<String> stations=new HashSet<String>();while(matches.find())stations.add(matches.group().toUpperCase(Locale.US));
  System.out.println(prefix+"_BYTES="+text.length());System.out.println(prefix+"_STATIONS="+stations.size());
 }
 public static void main(String[] args) throws Exception {
  String[] command=args.length>0 && args[0].equals("--pty")?new String[]{"/data/local/tmp/ev6-carplay-final-install/wl-stations"}:new String[]{"/system/bin/wl","-i","wlan0","assoclist"};
  Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int count;
  while((count=process.getInputStream().read(buffer))>0)bytes.write(buffer,0,count);
  System.out.println("DIRECT_RESULT="+process.waitFor());summary("DIRECT",new String(bytes.toByteArray(),"US-ASCII"));
  java.lang.reflect.Method exec=Class.forName("CarPlayController").getDeclaredMethod("exec",Map.class,long.class,String[].class);exec.setAccessible(true);
  String output=(String)exec.invoke(null,new Object[]{null,Long.valueOf(4000),command});summary("CONTROLLER",output);
  Object binder=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"phoneprojection");
  Object pp=Class.forName("android.phoneprojection.IPPManager$Stub").getMethod("asInterface",android.os.IBinder.class).invoke(null,binder);
  BufferedReader targetFile=new BufferedReader(new FileReader("/data/local/ev6-carplay/state/migration-target"));
  String target;try{target=targetFile.readLine();}finally{targetFile.close();}
  Object record=Class.forName("android.phoneprojection.IPPManager").getMethod("getWLDeviceInfo",String.class).invoke(pp,target);
  System.out.println("RECORD_PRESENT="+(record!=null));
  if(record!=null) {
   Class<?> type=Class.forName("android.phoneprojection.WLDeviceInfo");
   String bt=(String)type.getMethod("getWLDevBtAddr").invoke(record),peer=(String)type.getMethod("getWLDevMacAddr").invoke(record);
   System.out.println("RECORD_BT_MATCH_TARGET="+target.equalsIgnoreCase(bt));
   System.out.println("RECORD_PEER_VALID_MAC="+(peer!=null && peer.matches("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}")));
   System.out.println("RECORD_PEER_LENGTH="+(peer==null?0:peer.length()));
   System.out.println("RECORD_PEER_MATCH_TARGET="+target.equalsIgnoreCase(peer));
   if(args.length==1)System.out.println("RECORD_PEER_MATCH_STATION="+args[0].equalsIgnoreCase(peer));
  }
 }
}
