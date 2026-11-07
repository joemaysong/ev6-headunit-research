import java.io.*;
import org.ev6.projection.ReservationStore;

/** Resume only an existing exact AP journal; never prepare a replacement lease. */
public final class ProjectionApInterruptedStart {
    private ProjectionApInterruptedStart() {}
    public static boolean recover(ReservationStore store,File file,
            ProjectionApJournal.ParentSync sync,ProjectionApColdStart.RecoveryPort port)throws Exception {
        if(!file.isFile() || !file.getAbsoluteFile().equals(file.getCanonicalFile()))
            throw new IOException("ap-interrupted-journal-missing");
        String operation;
        RandomAccessFile input=new RandomAccessFile(file,"r");
        try {
            if(input.length()<1 || input.length()>8192)throw new IOException("ap-interrupted-journal-size");
            String first=input.readLine();String[] fields=first==null?new String[0]:first.split("\\|",-1);
            if(fields.length!=5 || !fields[0].equals("EV6AP1"))throw new IOException("ap-interrupted-journal-header");
            operation=fields[1];
        }finally{input.close();}
        ReservationStore.Ticket ticket=store.recoverApIdentity(operation);
        if(ticket==null)throw new IOException("ap-interrupted-foreign-or-successor");
        // The operation validates the complete hash chain and actual state through
        // RecoveryPort before each effect. Damaged/changed evidence stays held.
        ProjectionApColdStart recovery=new ProjectionApColdStart(store,ticket,file,sync,port);
        recovery.recoverHandoff();return recovery.admissionReleased();
    }
}
