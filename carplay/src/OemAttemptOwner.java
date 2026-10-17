import java.io.IOException;

/** Selected-attempt ownership, used by the staged controller.
 * A deadline has deliberately no method that releases this owner.
 */
public final class OemAttemptOwner {
    private String receiver="",target="";
    private long sequence,revision;
    private boolean armed,observed,invalid;
    private int entries,sessionEntry=-1;
    public boolean owned(){return armed;}
    public boolean needsReview(){return invalid;}
    public static boolean submissionReady(OemAttemptSnapshot snapshot) {
        return snapshot!=null && !snapshot.unknown && snapshot.dispatches==0 && snapshot.connectCalls==0
            && snapshot.queued==0 && (snapshot.reentryIntent==0 || snapshot.reentryIntent==2)
            && (snapshot.sequence==0?snapshot.reentryIntent==0:snapshot.workSettled);
    }
    public static boolean unownedCleanupReady(OemAttemptSnapshot snapshot,boolean explicitStop) {
        return submissionReady(snapshot);
    }
    /** Our AP preparation never submitted PP. Retained prior metadata is not
     * new work, but a changed PP sequence or Java invocation invalidates this.
     */
    public static boolean preparationCleanupReady(OemAttemptSnapshot baseline,OemAttemptSnapshot snapshot) {
        return submissionReady(baseline) && submissionReady(snapshot)
            && baseline.receiver.equals(snapshot.receiver) && baseline.sequence==snapshot.sequence
            && baseline.target.equals(snapshot.target) && baseline.connectEntries==snapshot.connectEntries
            && snapshot.revision>=baseline.revision;
    }
    /** Adopt the original observed PP sequence after worker restart. The caller
     * obtains selected from the authenticated receiver, never a guessed MAC.
     */
    public void adopt(OemAttemptSnapshot snapshot,String selected) throws IOException {
        if(armed || snapshot==null || snapshot.unknown || snapshot.sequence<1
            || selected==null || !selected.equals(snapshot.target))throw new IOException("oem-attempt-not-adoptable");
        receiver=snapshot.receiver;target=selected;sequence=snapshot.sequence;revision=snapshot.revision;
        entries=snapshot.connectEntries;sessionEntry=-1;armed=true;observed=true;invalid=false;
    }
    public void arm(OemAttemptSnapshot baseline,String selected) throws IOException {
        if(armed || baseline==null || baseline.unknown || selected==null
            || !selected.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")
            || baseline.dispatches!=0 || baseline.connectCalls!=0 || baseline.queued!=0
            || (baseline.reentryIntent!=0 && baseline.reentryIntent!=2)
            || (baseline.sequence==0 && baseline.reentryIntent!=0)
            || (baseline.sequence>0 && !baseline.workSettled) || baseline.sequence==Long.MAX_VALUE)
            throw new IOException("oem-attempt-not-ready-for-submission");
        receiver=baseline.receiver;target=selected;sequence=baseline.sequence+1;
        revision=baseline.revision;armed=true;observed=false;invalid=false;entries=0;sessionEntry=-1;
    }
    public boolean accept(OemAttemptSnapshot snapshot) {
        if(!armed || invalid || snapshot==null)return false;
        if(!receiver.equals(snapshot.receiver) || snapshot.revision<revision || snapshot.unknown) {
            invalid=true;return false;
        }
        // A genuine asynchronous PP submission may not have reached the
        // receiver yet. The preceding observation cannot release its successor.
        if(!observed && snapshot.sequence==sequence-1) {
            revision=snapshot.revision;return false;
        }
        if(!snapshot.owns(receiver,sequence,target)){invalid=true;return false;}
        if(observed && snapshot.connectEntries<entries){invalid=true;return false;}
        if(snapshot.connectEntries!=entries)sessionEntry=-1;
        entries=snapshot.connectEntries;
        observed=true;revision=snapshot.revision;return snapshot.complete;
    }
    /** Transfer is evidenced by the genuine selected native session, never by
     * a successful Java return alone. A later actual connect invalidates it.
     */
    public boolean observeSession(OemAttemptSnapshot snapshot,boolean nativeActive,
            boolean backendActive,boolean nativeControlOwner,String originalAssociation) {
        accept(snapshot);
        if(!armed || invalid || !observed || snapshot==null || snapshot.connectEntries<1
            || snapshot.dispatches!=0 || snapshot.connectCalls!=0 || snapshot.queued!=0
            || !nativeActive || !backendActive || !nativeControlOwner
            || originalAssociation==null || !target.equalsIgnoreCase(originalAssociation))return false;
        sessionEntry=snapshot.connectEntries;return true;
    }
    public boolean canRelease(OemAttemptSnapshot snapshot,boolean nativeActive,
            boolean backendActive,String originalAssociation) {
        accept(snapshot);
        // Cleanup waits for this original work to return, not for the OEM to
        // erase its retained reconnect address. That address survived normal
        // stop in the actual trace and does not itself mean pending work.
        return armed && !invalid && observed && snapshot!=null && snapshot.workSettled
            && (snapshot.reentryIntent==0 || snapshot.reentryIntent==2) && !nativeActive && !backendActive
            && ConnectionPolicy.originalAssociationCleared(originalAssociation);
    }
    public void released(OemAttemptSnapshot snapshot,boolean nativeActive,
            boolean backendActive,String originalAssociation) throws IOException {
        if(!canRelease(snapshot,nativeActive,backendActive,originalAssociation))
            throw new IOException("oem-attempt-still-owned");
        clear();
    }
    /** Original preflight rejected before ANY player delegation in this PP
     * attempt. Return enum alone never supplies this fact.
     */
    public boolean canReleasePreflight(OemAttemptSnapshot snapshot,boolean nativeActive,
            boolean backendActive,String originalAssociation) {
        accept(snapshot);
        return armed && !invalid && observed && snapshot!=null && snapshot.preflightRejected
            && (snapshot.reentryIntent==0 || snapshot.reentryIntent==2)
            && !nativeActive && !backendActive && ConnectionPolicy.originalAssociationCleared(originalAssociation);
    }
    public void releasedPreflight(OemAttemptSnapshot snapshot,boolean nativeActive,
            boolean backendActive,String originalAssociation) throws IOException {
        if(!canReleasePreflight(snapshot,nativeActive,backendActive,originalAssociation))
            throw new IOException("oem-preflight-still-owned");
        clear();
    }
    /** An explicit stop withdraws the selected attempt even if it never had a
     * session. Original returned work plus a fresh Handler boundary are required.
     */
    public boolean canReleaseStopped(OemAttemptSnapshot snapshot,boolean nativeActive,
            boolean backendActive,String originalAssociation,boolean explicitStop) {
        accept(snapshot);
        return armed && !invalid && observed && snapshot!=null && explicitStop && snapshot.workSettled
            && (snapshot.reentryIntent==0 || snapshot.reentryIntent==2)
            && !nativeActive && !backendActive && ConnectionPolicy.originalAssociationCleared(originalAssociation);
    }
    public void releasedStopped(OemAttemptSnapshot snapshot,boolean nativeActive,
            boolean backendActive,String originalAssociation,boolean explicitStop) throws IOException {
        if(!canReleaseStopped(snapshot,nativeActive,backendActive,originalAssociation,explicitStop))
            throw new IOException("oem-stop-still-owned");
        clear();
    }
    /** Retained reconnect metadata2 is permitted only for an explicit stop or
     * switch AFTER this exact connect invocation had a genuine selected session.
     * Unexpected loss keeps original ownership for cause-aware recovery.
     */
    public boolean canReleaseAfterSession(OemAttemptSnapshot snapshot,boolean nativeActive,
            boolean backendActive,String originalAssociation,boolean explicitStopOrSwitch) {
        accept(snapshot);
        return armed && !invalid && observed && snapshot!=null && explicitStopOrSwitch
            && sessionEntry>0 && snapshot.connectEntries==sessionEntry && snapshot.workSettled
            && (snapshot.reentryIntent==0 || snapshot.reentryIntent==2)
            && !nativeActive && !backendActive && ConnectionPolicy.originalAssociationCleared(originalAssociation);
    }
    public void releasedAfterSession(OemAttemptSnapshot snapshot,boolean nativeActive,
            boolean backendActive,String originalAssociation,boolean explicitStopOrSwitch) throws IOException {
        if(!canReleaseAfterSession(snapshot,nativeActive,backendActive,originalAssociation,explicitStopOrSwitch))
            throw new IOException("oem-session-still-owned");
        clear();
    }
    /** Process death ends that process's native work. Service recreation in the
     * same PID/start-time does not supply this stronger lifetime boundary.
     * The caller must independently prove the old process generation ended.
     */
    public boolean canRetireReceiver(OemAttemptSnapshot replacement,boolean oldProcessEnded,
            boolean nativeActive,boolean backendActive,String originalAssociation) {
        if(!armed || !oldProcessEnded || replacement==null || replacement.unknown)return false;
        String oldProcess=receiver.substring(0,receiver.lastIndexOf(':'));
        String nextProcess=replacement.receiver.substring(0,replacement.receiver.lastIndexOf(':'));
        return !oldProcess.equals(nextProcess) && replacement.dispatches==0 && replacement.connectCalls==0
            && replacement.queued==0 && replacement.reentryIntent==0
            && (replacement.sequence==0 || replacement.complete)
            && !nativeActive && !backendActive && ConnectionPolicy.originalAssociationCleared(originalAssociation);
    }
    public void retiredReceiver(OemAttemptSnapshot replacement,boolean oldProcessEnded,
            boolean nativeActive,boolean backendActive,String originalAssociation) throws IOException {
        if(!canRetireReceiver(replacement,oldProcessEnded,nativeActive,backendActive,originalAssociation))
            throw new IOException("oem-receiver-lifetime-not-ended");
        clear();
    }
    private void clear() {
        armed=false;receiver="";target="";sequence=0;revision=0;observed=false;invalid=false;entries=0;sessionEntry=-1;
    }
}
