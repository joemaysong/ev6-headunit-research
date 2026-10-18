import java.io.IOException;

/** Ownership of this worker's selection, independent of OEM request/session IDs.
 * Whole-receiver lifecycle evidence, not a PP ordinal, gates resource release.
 */
public final class OemAttemptOwner {
    private String receiver="",target="";
    private long sequence,revision,observedEpoch;
    private boolean armed,invalid;
    public boolean owned(){return armed;}
    public boolean needsReview(){return invalid;}
    public static boolean submissionReady(OemAttemptSnapshot s) {
        return s!=null && s.transportsSettled && s.transportBlocker==0
            && s.dispatches==0 && s.connectCalls==0 && s.queued==0;
    }
    public static boolean unownedCleanupReady(OemAttemptSnapshot s,boolean explicitStop) {
        return submissionReady(s);
    }
    public static boolean preparationCleanupReady(OemAttemptSnapshot baseline,OemAttemptSnapshot s) {
        return submissionReady(baseline) && submissionReady(s)
            && baseline.receiver.equals(s.receiver) && baseline.sequence==s.sequence
            && baseline.connectEntries==s.connectEntries && s.revision>=baseline.revision;
    }
    private static boolean validTarget(String value) {
        return value!=null && value.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}");
    }
    public void arm(OemAttemptSnapshot baseline,String selected) throws IOException {
        if(armed || !validTarget(selected) || !submissionReady(baseline)
            || baseline.sequence==Long.MAX_VALUE)throw new IOException("oem-attempt-not-ready-for-submission");
        receiver=baseline.receiver;target=selected;sequence=baseline.sequence+1;
        revision=baseline.revision;armed=true;invalid=false;
    }
    public void adopt(OemAttemptSnapshot s,String selected) throws IOException {
        // USB can own the actual session independently of the latest PP target.
        if(armed || s==null || !validTarget(selected) || s.sequence<1
            || !(s.verifiedSession(selected) || (submissionReady(s) && selected.equals(s.target))))
            throw new IOException("oem-attempt-not-adoptable");
        receiver=s.receiver;target=selected;sequence=s.sequence;revision=s.revision;observedEpoch=s.verifiedSession(selected)?s.sessionEpoch:0;armed=true;invalid=false;
    }
    /** STOP-only adoption. Caller supplies bracketed real native evidence;
     * this does not certify correlation or authorize a new connection. */
    public void adoptForStop(OemAttemptSnapshot s,String selected,boolean nativeActive,
            boolean controlOwner,String association) throws IOException {
        if(armed || s==null || !validTarget(selected) || !nativeActive || !controlOwner
            || !selected.equalsIgnoreCase(association))throw new IOException("oem-stop-owner-unverified");
        receiver=s.receiver;target=selected;sequence=s.sequence;revision=s.revision;observedEpoch=s.verifiedSession(selected)?s.sessionEpoch:0;armed=true;invalid=false;
    }
    private boolean sameReceiver(OemAttemptSnapshot s) {
        if(!armed || s==null)return false;
        if(!receiver.equals(s.receiver) || s.revision<revision){invalid=true;return false;}
        revision=s.revision;return true;
    }
    public boolean accept(OemAttemptSnapshot s) {
        if(!sameReceiver(s))return false;
        // Missing/ambiguous evidence is reported by THIS snapshot, not latched
        // into a second process-lifetime owner fault. History remains upstream.
        return !invalid && s.transportsSettled && s.sequence>=sequence;
    }
    public boolean observeSession(OemAttemptSnapshot s,boolean nativeActive,
            boolean backendActive,boolean controlOwner,String association) {
        if(!sameReceiver(s) || invalid || !nativeActive || !controlOwner
            || !target.equalsIgnoreCase(association) || !s.verifiedSession(target))return false;
        boolean ready=s.actualSessionTransport==1 || (s.actualSessionTransport==2 && backendActive);
        if(ready)observedEpoch=s.sessionEpoch;return ready;
    }
    public boolean canTransferTo(OemAttemptSnapshot s,String selected) {
        return sameReceiver(s) && !invalid && observedEpoch>0
            && s.lastFinalizedEpoch>=observedEpoch
            && s.sessionEpoch>observedEpoch && s.verifiedSession(selected);
    }
    public void transferTo(OemAttemptSnapshot s,String selected) throws IOException {
        if(!canTransferTo(s,selected))throw new IOException("oem-successor-not-verified");
        target=selected;sequence=s.sequence;revision=s.revision;observedEpoch=s.sessionEpoch;
    }
    public boolean canReleaseRecovered(OemAttemptSnapshot s,boolean nativeActive,
            boolean backendActive,String association) {
        return sameReceiver(s) && !invalid && s.sequence>=sequence && submissionReady(s)
            && !nativeActive && !backendActive && ConnectionPolicy.originalAssociationCleared(association);
    }
    public void releasedRecovered(OemAttemptSnapshot s,boolean nativeActive,
            boolean backendActive,String association) throws IOException {
        if(!canReleaseRecovered(s,nativeActive,backendActive,association))
            throw new IOException("oem-recovery-receipt-mismatch");
        clear();
    }
    public boolean canRetireReceiver(OemAttemptSnapshot replacement,boolean oldProcessEnded,
            boolean nativeActive,boolean backendActive,String association) {
        if(!armed || !oldProcessEnded || !submissionReady(replacement))return false;
        String oldProcess=receiver.substring(0,receiver.lastIndexOf(':'));
        String nextProcess=replacement.receiver.substring(0,replacement.receiver.lastIndexOf(':'));
        return !oldProcess.equals(nextProcess) && !nativeActive && !backendActive
            && ConnectionPolicy.originalAssociationCleared(association);
    }
    public void retiredReceiver(OemAttemptSnapshot replacement,boolean oldProcessEnded,
            boolean nativeActive,boolean backendActive,String association) throws IOException {
        if(!canRetireReceiver(replacement,oldProcessEnded,nativeActive,backendActive,association))
            throw new IOException("oem-receiver-lifetime-not-ended");
        clear();
    }
    private void clear(){armed=false;invalid=false;receiver="";target="";sequence=0;revision=0;observedEpoch=0;}
}
