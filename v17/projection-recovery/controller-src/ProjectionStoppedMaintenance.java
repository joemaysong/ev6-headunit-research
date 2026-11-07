import java.io.File;
import java.io.IOException;
import org.ev6.projection.ReservationStore;
/** Hold admission after normal idle close, including a clean retained reboot. */
public final class ProjectionStoppedMaintenance {
 public interface Port {void verifyStopped()throws Exception;}
 public interface RecoveryPort extends Port {void certifyRecovery()throws Exception;}
 public static boolean hold(ReservationStore store,final File marker,String boot,Port port)throws Exception {
  final String requestBoot=ProjectionMaintenance.requestBoot(marker);
  final String owner=ProjectionMaintenance.request(marker,requestBoot);
  String expected=store.stoppedMaintenanceSnapshot();
  port.verifyStopped();
  ReservationStore.StoppedMaintenanceProof proof=new ReservationStore.StoppedMaintenanceProof(){public void verify()throws IOException {
   try{if(!requestBoot.equals(ProjectionMaintenance.requestBoot(marker))||!owner.equals(ProjectionMaintenance.request(marker,requestBoot)))throw new IOException("stopped-maintenance-marker-changed");}
   catch(IOException e){throw e;}catch(Exception e){throw new IOException(e);}
  }};
  ReservationStore.Ticket held=store.claimStoppedMaintenance(owner,expected,proof);
  if(held==null)return false;
  proof.verify();
  if(!held.boot.equals(boot)||store.status(held)!=ReservationStore.VALID)throw new IOException("stopped-maintenance-authority");
  port.verifyStopped();proof.verify();
  if(store.status(held)!=ReservationStore.VALID)throw new IOException("stopped-maintenance-changed");
  if(port instanceof RecoveryPort){((RecoveryPort)port).certifyRecovery();port.verifyStopped();proof.verify();if(store.status(held)!=ReservationStore.VALID)throw new IOException("recovery-maintenance-changed");}
  return true;
 }
}
