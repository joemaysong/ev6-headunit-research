/** Observation model only; no receiver hook, transport, cancellation or policy.
 * All boundaries must come from the original receiver/Handler, never a timer.
 */
public final class AttemptObservation {
    public static final int SUBMISSION=1, DISCONNECT=2, IDENTITY=4,
        TIMEOUT=8, RETRY=16, SPP=32, DELETE=64, STOP=128, UNSTABLE_DISCONNECT=256,
        BT_STATUS_CLEANUP=512, IAP2_FINALIZATION=1024, ALL=2047;
    private final String receiver;
    private long sequence, revision;
    private String target="";
    private int dispatches, connectCalls, queued=ALL, reentryIntent=3;
    private boolean returned, terminal, unknown;
    private long boundaryRevision=-1;
    // A returned terminal boundary can settle the observed work while original
    // reconnect metadata remains. This receipt is not an AP-release permission.
    private long settledSequence=-1;
    private int connectEntries,delegateEntries;
    private boolean submissionHandled,rejected;
    private String connectResult="UNOBSERVED";

    public AttemptObservation(String receiverGeneration) {
        if(receiverGeneration==null || receiverGeneration.length()==0)
            throw new IllegalArgumentException("Receiver generation required");
        receiver=receiverGeneration;
    }
    private void changed() { revision++;boundaryRevision=-1; }
    private boolean current(String generation,long attempt) {
        return receiver.equals(generation) && sequence==attempt && attempt>0;
    }
    /** Called before the genuine original submission is queued. No rejection. */
    public synchronized long submitted(String address) {
        boolean priorUnfinished=sequence>0 && settledSequence!=sequence;
        changed();sequence++;target=address==null?"":address;
        unknown=unknown || priorUnfinished || target.length()==0;
        returned=false;terminal=false;queued=ALL;reentryIntent=3;
        settledSequence=-1;connectEntries=0;delegateEntries=0;submissionHandled=false;rejected=false;
        connectResult="UNOBSERVED";
        return sequence;
    }
    /** Bracket the complete original Handler dispatch, including exceptions. */
    public synchronized void enteredDispatch() {enteredDispatch(true);}
    public synchronized void enteredDispatch(boolean controlWork) {
        changed();dispatches++;
        if(controlWork)settledSequence=-1;
    }
    public synchronized void exitedDispatch(boolean normal) {
        changed();
        if(dispatches==0)unknown=true;else dispatches--;
        if(!normal)unknown=true;
    }
    /** Bracket connectDSCarPlay at both original Java call sites. Its preflight
     * and synchronous native work stay conservatively in flight together.
     * A normal return does not assert that the native call actually ran.
     */
    public synchronized void enteredConnect(String address) {
        changed();connectCalls++;connectEntries++;settledSequence=-1;
        terminal=false;rejected=false;
        if(sequence==0 || !target.equals(address) || dispatches==0 || connectCalls!=1)unknown=true;
    }
    /** Exact original connectDS -> DipoPlayer call site, before invocation.
     * This is not a native-call count or a successful-control acknowledgement.
     */
    public synchronized void enteredDelegate(String address) {
        changed();delegateEntries++;settledSequence=-1;
        if(sequence==0 || !target.equals(address) || connectCalls!=1 || dispatches==0)unknown=true;
    }
    public synchronized void returnedConnect(boolean normal) {
        returnedConnect(normal,"UNOBSERVED");
    }
    public synchronized void returnedConnect(boolean normal,String result) {
        changed();
        if(connectCalls==0)unknown=true;else connectCalls--;
        if(normal){
            returned=true;connectResult=result==null?"UNOBSERVED":result;
            if(submissionHandled)rejected=!connectResult.equals("OK") && !connectResult.equals("UNOBSERVED");
        }
        else unknown=true;
    }
    /** Whole original 0x2c dispatch returned. A real preflight rejection is
     * distinct from a native call; it can finish only with no pending work.
     */
    public synchronized void submissionReturned() {
        changed();submissionHandled=true;
        if(connectEntries==0){returned=true;rejected=true;}
        else rejected=!connectResult.equals("OK") && !connectResult.equals("UNOBSERVED");
    }
    /** A real terminal callback has fully returned, not merely been received. */
    public synchronized void terminalReturned(String generation,long attempt) {
        if(!current(generation,attempt))return;
        changed();terminal=true;
    }
    /** On the original Handler after dispatch; the bridge supplies hasMessages.
     * This cannot be inferred from an empty out-of-process queue sample.
     */
    public synchronized void handlerBoundary(String generation,long attempt,int queuedMask) {
        handlerBoundary(generation,attempt,queuedMask,0);
    }
    public synchronized void handlerBoundary(String generation,long attempt,int queuedMask,int intentMask) {
        if(!receiver.equals(generation) || sequence!=attempt || attempt<0)return;
        changed();
        if(queuedMask<0 || (queuedMask & ~ALL)!=0 || intentMask<0 || intentMask>3) {unknown=true;queued=ALL;return;}
        queued=queuedMask;reentryIntent=intentMask;
        if(queued!=0)settledSequence=-1;
        if(dispatches==0 && connectCalls==0)boundaryRevision=revision;
        if(workSettled())settledSequence=sequence;
    }
    public synchronized void observationFailed() {changed();unknown=true;}
    private boolean workSettled() {
        return sequence>0 && !unknown && returned && (terminal || (submissionHandled && rejected)) && dispatches==0
            && connectCalls==0 && queued==0 && boundaryRevision==revision;
    }
    private boolean complete() {
        // Keep the installed EV6ATTEMPT2 wire meaning conservative. The new
        // source-only settled fact must not silently weaken its release bit.
        return workSettled() && reentryIntent==0;
    }
    public synchronized Snapshot snapshot() {
        return new Snapshot(receiver,sequence,revision,target,dispatches,connectCalls,
            queued,unknown,complete(),reentryIntent,workSettled(),connectEntries,
            workSettled() && submissionHandled && rejected && delegateEntries==0);
    }
    public static final class Snapshot {
        public final String receiver,target;
        public final long sequence,revision;
        public final int dispatches,connectCalls,queued,reentryIntent,connectEntries;
        public final boolean unknown,complete,workSettled,preflightRejected;
        Snapshot(String g,long s,long r,String t,int d,int n,int q,boolean u,boolean c,int i,boolean w,int e,boolean p) {
            receiver=g;sequence=s;revision=r;target=t;dispatches=d;connectCalls=n;
            queued=q;unknown=u;complete=c;reentryIntent=i;workSettled=w;connectEntries=e;preflightRejected=p;
        }
        public boolean matches(String generation,long attempt,String address) {
            return receiver.equals(generation) && sequence==attempt && target.equals(address);
        }
    }
}
