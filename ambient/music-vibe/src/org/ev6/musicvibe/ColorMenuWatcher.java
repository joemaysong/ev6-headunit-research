package org.ev6.musicvibe;
import android.content.*;import android.app.*;
/** Read-only foreground-task check. No accessibility service or secure-setting changes. */
final class ColorMenuWatcher {
 private static long readAt;private static String cached="";
 static synchronized String top(Context c){long now=android.os.SystemClock.elapsedRealtime();if(now-readAt<100)return cached;readAt=now;try{java.util.List<ActivityManager.RunningTaskInfo> a=((ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).getRunningTasks(1);if(!a.isEmpty()&&a.get(0).topActivity!=null)return cached=a.get(0).topActivity.flattenToShortString();}catch(Exception e){android.util.Log.e("MusicVibe","Foreground task unavailable",e);}return "";}
 static boolean colorSettingsOpen(Context c){String n=top(c);return n.startsWith("com.android.vehiclesettings/")&&(n.contains("AmbientLightClass")||n.contains("CustomColorActivity")||n.contains("CustomDual"));}
 static boolean paletteOpen(Context c){return colorSettingsOpen(c);}
}
