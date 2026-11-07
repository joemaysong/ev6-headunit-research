/** Exact proof passed only by AP authority after ReservationStore validates its ticket. */
public final class ProjectionApPreparationProof {
 private ProjectionApPreparationProof(){}
 public static String record(String boot,long sequence,String kind,String key,String owner){
  if(boot==null||!boot.matches("[0-9a-f]{32}")||sequence<1||!"AP".equals(kind)||key==null||!key.matches("[0-9a-f]{64}")||owner==null||!owner.matches("[0-9a-f]{32}"))throw new IllegalArgumentException("ap-preparation-proof");
  return "EV6RES1|"+boot+"|"+sequence+"|P|AP|"+key+"|"+owner+"|END";
 }
 public static boolean matches(String actual,String expected){
  return actual!=null&&expected!=null&&expected.matches("EV6RES1\\|[0-9a-f]{32}\\|[1-9][0-9]{0,18}\\|P\\|AP\\|[0-9a-f]{64}\\|[0-9a-f]{32}\\|END")&&expected.equals(actual.trim());
 }
 public static boolean renewBudget(String demand,String prior,boolean attempted,boolean ap,int failures,String reserve,String boot,boolean evidence){
  return demand!=null&&!demand.equals(prior)&&!attempted&&!ap&&(failures==0||(!evidence&&free(reserve,boot)));
 }
 public static boolean free(String actual,String boot){
  return actual!=null&&boot!=null&&boot.matches("[0-9a-f]{32}")&&actual.trim().matches("EV6RES1\\|"+boot+"\\|[1-9][0-9]{0,18}\\|F\\|-\\|-\\|-\\|END");
 }
 public static boolean canceled(String prepared,String actual,boolean journalPresent,boolean pendingPresent,String error){
  if(actual==null||journalPresent||pendingPresent||!"ap-startup-interrupted-cancel-1".equals(error)||!matches(prepared==null?"":prepared,prepared))return false;
  String[] p=prepared.split("\\|",-1);
  return ("EV6RES1|"+p[1]+"|"+p[2]+"|F|-|-|-|END").equals(actual.trim());
 }
}
