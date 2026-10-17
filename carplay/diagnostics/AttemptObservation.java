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
    private long removedUnstartedSequence=-1;
    private boolean submissionStarted,canceledBeforeDispatch;
    // Bounded cause bits for read-only diagnosis; no identities or event log.
    private int uncertaintyReasons;
    private int transport=-1,preflightContinuations;
    // Last submission predecessor facts, bounded and without peer identifiers.
    private boolean previousSameTarget,previousReturned,previousRejected,
        previousHandled,previousTerminal,previousSettled,previousRemoved;
    private int previousTransport=-1,previousEntries,previousDelegates,
        previousDispatches,previousCalls,previousQueued;

    private void uncertain(int reason) {unknown=true;uncertaintyReasons|=reason;}
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
    public synchronized long submitted(String address) {return submitted(address,-1);}
    public synchronized long submitted(String address,int originalTransport) {
        previousSameTarget=target.equals(address);
        previousReturned=returned;previousRejected=rejected;previousHandled=submissionHandled;
        previousTerminal=terminal;previousSettled=settledSequence==sequence;
        previousRemoved=removedUnstartedSequence==sequence;
        previousTransport=transport;previousEntries=connectEntries;previousDelegates=delegateEntries;
        previousDispatches=dispatches;previousCalls=connectCalls;previousQueued=queued;
        // Exact original wired preflight continuation: the old whole 0x2c
        // returned/rejected before ANY player delegation. Pending original
        // identity/retry messages are still tracked, not deleted or ignored.
        // This receipt permits a new submission only, never AP cleanup.
        boolean wiredPreflightContinuation=sequence>0 && !unknown
            && transport==0 && originalTransport==0 && previousSameTarget
            && returned && submissionHandled && rejected && delegateEntries==0 && connectCalls==0;
        if(wiredPreflightContinuation)preflightContinuations++;
        boolean priorUnfinished=sequence>0 && settledSequence!=sequence
            && removedUnstartedSequence!=sequence && !wiredPreflightContinuation;
        changed();sequence++;target=address==null?"":address;transport=originalTransport;
        if(priorUnfinished)uncertain(1);
        if(target.length()==0)uncertain(2);
        returned=false;terminal=false;queued=ALL;reentryIntent=3;
        settledSequence=-1;removedUnstartedSequence=-1;submissionStarted=false;canceledBeforeDispatch=false;connectEntries=0;delegateEntries=0;submissionHandled=false;rejected=false;
        connectResult="UNOBSERVED";
        return sequence;
    }
    /** An actual 0x2c dispatch has begun. A queue removal can no longer
     * prove that this request never ran, even before connectDSCarPlay enters. */
    public synchronized void submissionStarted() {
        changed();submissionStarted=true;canceledBeforeDispatch=false;removedUnstartedSequence=-1;settledSequence=-1;
    }
    /** Receipt only after the OEM removed a pending 0x2c. It permits a
     * replacement submission, never AP release or completion by itself. */
    public synchronized void submissionRemoved(long attempt) {
        if(attempt!=sequence || attempt<=0)return;
        changed();
        if(!submissionStarted && connectEntries==0 && delegateEntries==0 && connectCalls==0) {
            removedUnstartedSequence=sequence;canceledBeforeDispatch=true;returned=true;
        }
    }
    /** Bracket the complete original Handler dispatch, including exceptions. */
    public synchronized void enteredDispatch() {enteredDispatch(true);}
    public synchronized void enteredDispatch(boolean controlWork) {
        changed();dispatches++;
        if(controlWork)settledSequence=-1;
    }
    public synchronized void exitedDispatch(boolean normal) {
        changed();
        if(dispatches==0)uncertain(4);else dispatches--;
        if(!normal)uncertain(8);
    }
    /** Bracket connectDSCarPlay at both original Java call sites. Its preflight
     * and synchronous native work stay conservatively in flight together.
     * A normal return does not assert that the native call actually ran.
     */
    public synchronized void enteredConnect(String address) {
        changed();canceledBeforeDispatch=false;connectCalls++;connectEntries++;settledSequence=-1;removedUnstartedSequence=-1;
        terminal=false;rejected=false;
        if(sequence==0 || !target.equals(address) || dispatches==0 || connectCalls!=1)uncertain(16);
    }
    /** Exact original connectDS -> DipoPlayer call site, before invocation.
     * This is not a native-call count or a successful-control acknowledgement.
     */
    public synchronized void enteredDelegate(String address) {
        changed();canceledBeforeDispatch=false;delegateEntries++;settledSequence=-1;removedUnstartedSequence=-1;
        if(sequence==0 || !target.equals(address) || connectCalls!=1 || dispatches==0)uncertain(32);
    }
    public synchronized void returnedConnect(boolean normal) {
        returnedConnect(normal,"UNOBSERVED");
    }
    public synchronized void returnedConnect(boolean normal,String result) {
        changed();
        if(connectCalls==0)uncertain(4);else connectCalls--;
        if(normal){
            returned=true;connectResult=result==null?"UNOBSERVED":result;
            if(submissionHandled)rejected=!connectResult.equals("OK") && !connectResult.equals("UNOBSERVED");
        }
        else uncertain(8);
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
        if(queuedMask<0 || (queuedMask & ~ALL)!=0 || intentMask<0 || intentMask>3) {uncertain(64);queued=ALL;return;}
        queued=queuedMask;reentryIntent=intentMask;
        if(queued!=0)settledSequence=-1;
        if(dispatches==0 && connectCalls==0)boundaryRevision=revision;
        if(workSettled())settledSequence=sequence;
    }
    public synchronized void observationFailed() {changed();uncertain(128);}
    private boolean workSettled() {
        return sequence>0 && !unknown && returned && (terminal || canceledBeforeDispatch || (submissionHandled && rejected)) && dispatches==0
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
