import java.io.IOException;
/** Read-only identity check for an already-held maintenance reservation; never claims or rebases it. */
public final class ProjectionMaintenanceEvidence {
 public static void requireHeld(String record,String currentBoot,String requestBoot,String owner,String key)throws IOException{
  if(record==null||currentBoot==null||requestBoot==null||owner==null||key==null||!currentBoot.matches("[0-9a-f]{32}")||!requestBoot.matches("[0-9a-f]{32}")||!owner.matches("[0-9a-f]{32}")||!key.matches("[0-9a-f]{64}"))throw new IOException("maintenance-evidence-input");
  String[] p=record.split("\\|",-1);if(p.length!=8||!p[0].equals("EV6RES1")||(!p[1].equals(currentBoot)&&!p[1].equals(requestBoot))||!p[2].matches("[1-9][0-9]{0,18}")||!p[3].equals("H")||!p[4].equals("AP")||!p[5].equals(key)||!p[6].equals(owner)||!p[7].equals("END\n"))throw new IOException("maintenance-evidence-not-held");
  try{if(Long.parseLong(p[2])<=0)throw new NumberFormatException();}catch(NumberFormatException invalid){throw new IOException("maintenance-evidence-sequence");}
 }
}
