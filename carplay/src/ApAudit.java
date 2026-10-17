import java.io.*;
import java.util.*;
/** Compare credentials on car only; never output names, keys or raw config. */
public final class ApAudit {
 static void out(String key,Object value){System.out.println(key+"="+value);}
 public static void main(String[] args) throws Exception {
  Object binder=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"wifi");
  Object wifi=Class.forName("android.net.wifi.IWifiManager$Stub").getMethod("asInterface",android.os.IBinder.class).invoke(null,binder);
  Class<?> service=Class.forName("android.net.wifi.IWifiManager");
  Object config=service.getMethod("getWifiApConfiguration").invoke(wifi);
  Class<?> type=config.getClass();
  Properties host=new Properties();FileInputStream stream=new FileInputStream("/data/misc/wifi/hostapd.conf");
  try{host.load(stream);}finally{stream.close();}
  String ssid=(String)type.getField("SSID").get(config),projection=(String)type.getField("ConnectivitySSID").get(config);
  String key=(String)type.getField("preSharedKey").get(config),projectionKey=(String)type.getField("ConnectivityPSK").get(config);
  out("HOSTAPD_SSID_EQUALS_PRIMARY",Objects.equals(host.getProperty("ssid"),ssid));
  out("HOSTAPD_SSID_EQUALS_CONNECTIVITY",Objects.equals(host.getProperty("ssid"),projection));
  out("HOSTAPD_KEY_EQUALS_PRIMARY",Objects.equals(host.getProperty("wpa_passphrase"),key));
  out("HOSTAPD_KEY_EQUALS_CONNECTIVITY",Objects.equals(host.getProperty("wpa_passphrase"),projectionKey));
  out("PRIMARY_EQUALS_CONNECTIVITY_SSID",Objects.equals(ssid,projection));
  out("PRIMARY_EQUALS_CONNECTIVITY_KEY",Objects.equals(key,projectionKey));
  out("HOSTAPD_PASSPHRASE_PRESENT",host.containsKey("wpa_passphrase"));
  out("HOSTAPD_PSK_PRESENT",host.containsKey("wpa_psk"));
  out("CONNECTIVITY_KEY_VALID_LENGTH",projectionKey!=null && projectionKey.length()>=8 && projectionKey.length()<=64);
  String expectedPsk="";
  if(projectionKey!=null && projectionKey.matches("[0-9a-fA-F]{64}"))expectedPsk=projectionKey;
  else if(projectionKey!=null && projectionKey.length()>=8) {
   javax.crypto.spec.PBEKeySpec spec=new javax.crypto.spec.PBEKeySpec(projectionKey.toCharArray(),projection.getBytes("UTF-8"),4096,256);
   byte[] psk=javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
   StringBuilder hex=new StringBuilder();for(byte value:psk)hex.append(String.format(java.util.Locale.US,"%02x",value&255));
   expectedPsk=hex.toString();spec.clearPassword();
  }
  out("HOSTAPD_DERIVED_PSK_EQUALS_CONNECTIVITY",expectedPsk.length()>0 && expectedPsk.equalsIgnoreCase(host.getProperty("wpa_psk")));
  out("CONFIG_VALID",type.getMethod("isValid").invoke(config));
  out("CONFIG_HIDDEN",type.getField("hiddenSSID").get(config));
  out("CONFIG_CHANNEL",type.getField("channel").get(config));
  out("CONNECTIVITY_CHANNEL",type.getField("connectivityChannel").get(config));
  String ie=(String)type.getField("carplayIE").get(config);
  out("CONFIG_CARPLAY_IE_PRESENT",ie!=null && ie.length()>0);
  out("CONFIG_AP_MODE",type.getField("apMode").get(config));
  out("WIFI_AP_MODE",service.getMethod("getWifiApMode").invoke(wifi));
  out("WIFI_AP_STATE",service.getMethod("getWifiApEnabledState").invoke(wifi));
 }
}
