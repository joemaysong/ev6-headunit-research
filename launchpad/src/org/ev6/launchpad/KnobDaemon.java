package org.ev6.launchpad;
import java.io.*;import java.nio.channels.*;import java.util.*;
/** Passive evdev reader: no input grabbing, injection or changes to OEM dispatch. */
public final class KnobDaemon {
 static File dir=new File("/data/local/ev6-launchpad");static volatile boolean down;static volatile long pressed,last;static FileLock lock;static RandomAccessFile lockFile;
 public static void main(String[] a){try{dir.mkdirs();lockFile=new RandomAccessFile(new File(dir,"knob.lock"),"rw");lock=lockFile.getChannel().tryLock();if(lock==null)return;log("start hold_ms=800 device=daudio_wp_daudioplus_cvev_ir key=17");Timer t=new Timer();t.schedule(new TimerTask(){public void run(){if(new File(dir,"disabled").exists()){down=false;return;}long now=System.nanoTime()/1000000L;if(down&&now-pressed>=800&&now-last>=1500){down=false;last=now;try{String s=RootTool.cmd("am","start","-n","org.ev6.launchpad/.MainActivity","-f","0x10000000","--ez","from_knob","true");log("hold launch "+s.trim());}catch(Exception e){log("launch error "+e);}}}},50,50);
 while(true){String path=locate();if(path==null){log("device missing");Thread.sleep(3000);continue;}FileInputStream in=new FileInputStream(path);try{byte[] b=new byte[16];DataInputStream d=new DataInputStream(in);log("reading "+path);while(true){d.readFully(b);int type=u16(b,8),code=u16(b,10),v=u16(b,12);if(type==1&&code==17){if(v==1){pressed=System.nanoTime()/1000000L;down=true;}else if(v==0)down=false;}}}catch(IOException e){down=false;log("read error "+e);Thread.sleep(3000);}finally{in.close();}}
 }catch(Exception e){log("fatal "+e);}}
 static int u16(byte[] b,int i){return (b[i]&255)|((b[i+1]&255)<<8);}
 static String locate()throws Exception{BufferedReader r=new BufferedReader(new FileReader("/proc/bus/input/devices"));try{boolean match=false;String s;while((s=r.readLine())!=null){if(s.startsWith("N:"))match=s.equals("N: Name=\"daudio_wp_daudioplus_cvev_ir\"");if(match&&s.startsWith("H:"))for(String part:s.split(" "))if(part.startsWith("event"))return "/dev/input/"+part;}return null;}finally{r.close();}}
 static synchronized void log(String s){try{File f=new File(dir,"knob.log");if(f.length()>262144){File old=new File(dir,"knob.log.1");old.delete();f.renameTo(old);}FileOutputStream out=new FileOutputStream(f,true);out.write((new Date()+" "+s+"\n").getBytes("UTF-8"));out.close();}catch(Exception ignored){}}
}
