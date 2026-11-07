/** Diagnostic classification only: retain every existing AP-close prerequisite. */
public final class ProjectionIdleBlocker {
 /** Only an observed OEM automatic connect may coexist with a proven AA drain.
  * Profile occupancy is deliberately irrelevant: this never disconnects profiles. */
 public static boolean networkCompatible(boolean settled,boolean exactAaDrain,int state,
     boolean busy,boolean automatic,boolean inactive,String peer){
  return settled||(exactAaDrain&&state==1&&busy&&automatic&&inactive
      &&peer!=null&&peer.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}"));
 }
 public static String reason(boolean nativeActive,boolean attemptOwned,boolean connected,
     boolean disconnecting,boolean releasing,boolean originalReady,boolean bluetoothKnown,boolean bluetoothSettled){
  if(nativeActive)return "native-session-active";
  if(attemptOwned)return "attempt-owned";
  if(connected)return "policy-connected";
  if(disconnecting)return "policy-disconnecting";
  if(releasing)return "projection-release-pending";
  if(!originalReady)return "original-cleanup-pending";
  if(!bluetoothKnown)return "bluetooth-observation-unavailable";
  if(!bluetoothSettled)return "bluetooth-work-pending";
  return "";
 }
}
