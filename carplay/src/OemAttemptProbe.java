import java.io.File;

/** Private observation tool. Prints counts/flags only, never phone identifiers.
 * Not a controller release gate or an OEM command.
 */
public final class OemAttemptProbe {
    public static void main(String[] args) {
        NativeSession session=null;
        try {
            int pid=NativeSession.findPid("com.daudio.app.dipo");
            if(pid==0)throw new java.io.IOException("receiver-unavailable");
            session=new NativeSession(pid);
            OemAttemptSnapshot snapshot=OemAttemptClient.read(session);
            String selected=CarPlayController.validTarget(CarPlayController.read(new File(CarPlayController.STATE,"current-target"),32));
            if(selected.length()==0)selected=CarPlayController.validTarget(CarPlayController.prop("sys.ev6.cp.busy"));
            System.out.println("OBSERVER_PROTOCOL=EV6ATTEMPT4");
            System.out.println("ATTEMPT_SEQUENCE="+snapshot.sequence);
            System.out.println("ATTEMPT_REVISION="+snapshot.revision);
            System.out.println("ORIGINAL_DISPATCHES="+snapshot.dispatches);
            System.out.println("ORIGINAL_CONNECT_CALLS="+snapshot.connectCalls);
            System.out.println("ORIGINAL_QUEUED_MASK="+snapshot.queued);
            System.out.println("ORIGINAL_REENTRY_FIELD_MASK="+snapshot.reentryIntent);
            System.out.println("OBSERVER_UNKNOWN="+snapshot.unknown);
            System.out.println("OBSERVED_COMPLETE="+snapshot.complete);
            System.out.println("OBSERVED_WORK_SETTLED="+snapshot.workSettled);
            System.out.println("ORIGINAL_CONNECT_ENTRIES="+snapshot.connectEntries);
            System.out.println("ORIGINAL_PREFLIGHT_REJECTED="+snapshot.preflightRejected);
            System.out.println("SELECTED_TARGET_KNOWN="+(selected.length()>0));
            System.out.println("ATTEMPT_TARGET_MATCH="+(selected.length()>0 && selected.equals(snapshot.target)));
        }catch(Exception failure){
            System.out.println("OBSERVER_READ_FAILED="+failure.getClass().getSimpleName());
            System.exit(1);
        }finally{if(session!=null)try{session.close();}catch(java.io.IOException ignored){}}
    }
}
