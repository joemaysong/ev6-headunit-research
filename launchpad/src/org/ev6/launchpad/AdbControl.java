package org.ev6.launchpad;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.JSONObject;
/** Explicit local UI actions only. Never writes USB function/role or persistent TCP properties. */
final class AdbControl {
 static String prop(String name)throws Exception{return RootTool.cmd("getprop",name).trim();}
 static boolean hasAdb(String functions){return Arrays.asList(functions.split(",")).contains("adb");}
 static boolean listens(String table){for(String line:table.split("\n")){String[] f=line.trim().split("\\s+");if(f.length>3&&f[1].endsWith(":15B3")&&f[3].equals("0A"))return true;}return false;}
 static boolean listening()throws Exception{return listens(RootTool.cmd("cat","/proc/net/tcp"))||listens(RootTool.cmd("cat","/proc/net/tcp6"));}
 static boolean usb()throws Exception{return RootTool.cmd("settings","get","global","adb_enabled").trim().equals("1");}
 static void apply(String transport,boolean on)throws Exception {
  if(transport.equals("usb")){
   String mode=prop("sys.usb.config");if(mode.startsWith("carplay")||mode.startsWith("accessory")||mode.startsWith("digital_audio"))throw new IOException("\u8acb\u5148\u4e2d\u65b7\u6709\u7dda\u6295\u5f71\u518d\u5207\u63db USB ADB");
   boolean keepTcp=listening();RootTool.checked("settings","put","global","adb_enabled",on?"1":"0");
   long end=android.os.SystemClock.elapsedRealtime()+8000;
   while(android.os.SystemClock.elapsedRealtime()<end){if(usb()==on)break;Thread.sleep(100);}
   if(usb()!=on)throw new IOException("USB ADB \u8a2d\u5b9a\u672a\u5b8c\u6210");
   // Coordinate through the OEM USB manager so its default state cannot undo
   // the role change. Transaction 15 is setCurrentFunction(String, boolean)
   // in this vehicle's verified Android 4.4 IUsbManager interface.
   if(mode.equals("adb")||mode.equals("none")||mode.equals("host")||mode.equals("host,adb")){
    String target=on?"adb":"host";
    RootTool.checked("service","call","usb","15","s16",target,"i32","1");
    long until=android.os.SystemClock.elapsedRealtime()+8000;int stable=0;
    while(android.os.SystemClock.elapsedRealtime()<until){
     if(prop("sys.usb.state").equals(target)&&prop("persist.sys.usb.config").equals(target))stable++;else stable=0;
     if(stable>=3)break;Thread.sleep(200);
    }
    if(stable<3)throw new IOException("USB "+target+" \u6a21\u5f0f\u5c1a\u672a\u7a69\u5b9a，\u8acb\u91cd\u65b0\u8b80\u53d6");
   }
   if(keepTcp){RootTool.checked("setprop","ctl.start","adbd");waitTcp(true);}
  }else if(transport.equals("tcp")){
   RootTool.checked("setprop","service.adb.tcp.port",on?"5555":"-1");
   RootTool.checked("setprop","ctl.stop","adbd");
   long end=android.os.SystemClock.elapsedRealtime()+5000;
   while(!prop("init.svc.adbd").equals("stopped")&&android.os.SystemClock.elapsedRealtime()<end)Thread.sleep(100);
   if(!prop("init.svc.adbd").equals("stopped"))throw new IOException("ADB \u670d\u52d9\u5c1a\u672a\u505c\u6b62");
   if(on||usb())RootTool.checked("setprop","ctl.start","adbd");waitTcp(on);
  }else throw new IOException("Unknown ADB transport");
 }
 static void waitTcp(boolean expected)throws Exception{long end=android.os.SystemClock.elapsedRealtime()+7000;do{if(listening()==expected)return;Thread.sleep(150);}while(android.os.SystemClock.elapsedRealtime()<end);throw new IOException("TCP ADB \u5be6\u969b\u76e3\u807d\u72c0\u614b\u672a\u7b26\u5408\u8a2d\u5b9a，\u8acb\u91cd\u65b0\u8b80\u53d6");}
 static String status()throws Exception {
  JSONObject j=new JSONObject();j.put("usb",usb());j.put("usbMode",prop("sys.usb.state"));j.put("usbActive",hasAdb(prop("sys.usb.state"))&&prop("init.svc.adbd").equals("running"));j.put("tcp",listening());j.put("daemon",prop("init.svc.adbd"));
  StringBuilder addresses=new StringBuilder();Enumeration<NetworkInterface> nets=NetworkInterface.getNetworkInterfaces();while(nets!=null&&nets.hasMoreElements()){NetworkInterface n=nets.nextElement();Enumeration<InetAddress> ips=n.getInetAddresses();while(ips.hasMoreElements()){InetAddress ip=ips.nextElement();if(ip instanceof Inet4Address&&!ip.isLoopbackAddress()){if(addresses.length()>0)addresses.append("\n");addresses.append(n.getName()).append(": ").append(ip.getHostAddress()).append(":5555");}}}j.put("addresses",addresses.toString());return j.toString();
 }
}
