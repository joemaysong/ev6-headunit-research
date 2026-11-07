import java.io.File;
import org.ev6.projection.ReservationStore;

/** An already-owned maintenance hold resumes independently of an older AP journal.
 * Selection grants nothing: drain still rechecks the marker, store and radio evidence. */
public final class ProjectionMaintenanceJournal {
 public static File select(ReservationStore store,File marker,String boot,File journal)throws Exception {
  String owner=ProjectionMaintenance.request(marker,boot);
  String snapshot=store.stoppedMaintenanceSnapshot();
  String[] p=snapshot==null?new String[0]:snapshot.trim().split("\\|",-1);
  if(p.length==8 && p[0].equals("EV6RES1") && p[1].equals(boot)
      && p[3].equals("H") && p[4].equals("AP")
      && p[5].equals(ReservationStore.key("projection-maintenance-drain"))
      && p[6].equals(owner) && p[7].equals("END"))return null;
  return journal;
 }
}
