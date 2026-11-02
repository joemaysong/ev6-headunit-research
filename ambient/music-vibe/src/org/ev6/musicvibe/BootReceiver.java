package org.ev6.musicvibe;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())){Diag.init(c,"boot");Diag.event("boot_received",i.getAction());Options.load(c);if(!Options.boot){Diag.event("boot_skipped","autoboot=false");return;}c.startService(new Intent(c,BreathService.class).setAction("BOOT"));}}
}
