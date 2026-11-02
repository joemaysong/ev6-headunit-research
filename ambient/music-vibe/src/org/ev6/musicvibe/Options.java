package org.ev6.musicvibe;
import android.content.*;import org.json.*;
final class Options {
 static int period=8000;static String mode="clockwise";static boolean boot=true;
 static void load(Context c){try{JSONObject j=new JSONObject(Store.read(c,"options.json"));period=j.optInt("period_ms",8000);if(period<2000||period>20000)period=8000;mode=j.optString("mode","clockwise");boot=j.optBoolean("autoboot",true);}catch(Exception ignored){} }
 static void set(Context c,Intent i)throws Exception {int p=i.getIntExtra("period_ms",8000);String m=i.getStringExtra("mode");if(p<2000||p>20000||!("clockwise".equals(m)||"counterclockwise".equals(m)||"random".equals(m)))throw new Exception("Invalid options");period=p;mode=m;boot=i.getBooleanExtra("autoboot",true);Store.write(c,"options.json",new JSONObject().put("period_ms",period).put("mode",mode).put("autoboot",boot).toString());Diag.event("options",period+"ms "+mode+" autoboot="+boot);}
}
