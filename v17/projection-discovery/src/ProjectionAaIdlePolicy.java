/** OEM normal wireless teardown retains preMode=2 as history; it is not an active mode.
 * Current mode, all transport objects and peer must still be absent. Exact request
 * completion is independently required by callers before releasing ownership. */
public final class ProjectionAaIdlePolicy {
 public static boolean idle(int mode,int status,int preMode,boolean wifi,boolean usb,boolean transport,boolean device,String active){
  return mode==0&&(preMode==0||preMode==2)&&status>=0&&status<=2&&!wifi&&!usb&&!transport&&!device&&(active==null||active.length()==0);
 }
}
