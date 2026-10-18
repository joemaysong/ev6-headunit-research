/** Read-only correlation of OEM endpoints, individual requests and callbacks.
 * Tokens are LOCAL context generations, never native session identifiers.
 * No timer, cancellation, transport selection or OEM action exists here.
 */
public final class SessionLedger {
    public static final int IDLE=0,PENDING=1,VERIFIED=2,CONFLICTING=3;
    private static final int CALL=1,DELEGATE=2,OVERLAP=4,RETURN=8,
        LIFECYCLE=16,FINALIZE=32,IDENTITY=64;
    private static final class Request {
        final long id;
        final int requestedMode;
        final String endpoint;
        boolean delegated,returned,accepted;
        String result="UNOBSERVED";
        Request(long id,int mode,String endpoint) {
            this.id=id;requestedMode=mode;this.endpoint=endpoint;
        }
    }
    private final java.util.ArrayList<Request> requests=new java.util.ArrayList<Request>();
    // Requests received while a session exists remain independent successor
    // candidates. A current session's finalize never completes those requests.
    private final java.util.ArrayList<Request> successors=new java.util.ArrayList<Request>();
    private Request invocation;
    private long requestSerial,contextSerial,context,epochSerial,epoch;
    private String endpoint="",lastFinalizedTarget="";
    private long lastFinalizedEpoch;
    private int modes,anchor=-1,actual=-1,history,blockers;
    private int requestCount,acceptedCount,rejectedCount,repeats,mixed,unknownBindings,mismatches,retirements;
    private boolean initialized,initializationReturned,identity,startReturned,finalReturned;
    private int finalMode=-1;
    private boolean failedSingleWired;

    private void fault(int bit) {history|=bit;blockers|=bit;}
    private void stale(int bit) {history|=bit;} // Does not poison a successor.
    private boolean current(long token) {return token>0 && token==context;}
    private void rejectedToken(long token,int bit) {
        if(token<0)fault(bit);else stale(bit);
    }
    public void enteringCall(int mode,String device) {
        if(invocation!=null || mode<0 || mode>2 || device==null || device.length()==0) {
            fault(CALL);return;
        }
        invocation=new Request(++requestSerial,mode,device);
    }
    public void delegated(String device) {
        if(invocation==null || invocation.delegated || device==null
                || !invocation.endpoint.equalsIgnoreCase(device)) {fault(DELEGATE);return;}
        invocation.delegated=true;requestCount++;
        if(context==0) {
            if(blockers!=0){fault(OVERLAP);return;}
            context=++contextSerial;endpoint=device;anchor=invocation.requestedMode;
            modes=0;actual=-1;epoch=0;initialized=false;initializationReturned=false;
            identity=false;startReturned=false;finalReturned=false;finalMode=-1;
        } else if((initialized || startReturned || finalReturned) && blockers==0) {
            if(successors.size()==64){fault(OVERLAP);return;}
            successors.add(invocation);return;
        } else if(!endpoint.equalsIgnoreCase(device) || blockers!=0) {
            // OEM keeps executing. Attribution remains unresolved; never
            // cancel another request or overwrite its context to make room.
            fault(OVERLAP);return;
        } else {
            // Pending requests retain their own result; grouping does not
            // assert that a repeat cancelled or completed its predecessor.
            repeats++;
            if((modes & (1<<invocation.requestedMode))==0)mixed++;
        }
        modes|=1<<invocation.requestedMode;
        failedSingleWired=false;
        // Bound diagnostic history; only returned request records can retire.
        if(requests.size()==64) {
            if(!requests.get(0).returned){fault(RETURN);return;}
            requests.remove(0);
        }
        requests.add(invocation);
    }
    public void returnedCall(boolean normal,String result) {
        if(invocation==null){fault(RETURN);return;}
        Request request=invocation;
        if(!normal)fault(RETURN);
        request.returned=true;request.result=result==null?"UNOBSERVED":result;
        request.accepted=normal && "OK".equals(request.result);
        if(request.delegated) {
            if(request.accepted)acceptedCount++;else rejectedCount++;
            failedSingleWired=normal && context>0 && !initialized
                && request.requestedMode==0 && requestsForContext()==1 && requests.contains(request)
                && "INVALID_ARGUMENT".equals(request.result)
                && endpoint.equalsIgnoreCase(request.endpoint);
        }
        invocation=null;
        completeFinalization();
    }
    private int requestsForContext() {
        int count=0;
        for(Request request:requests)if(request.endpoint.equalsIgnoreCase(endpoint))count++;
        return count;
    }
    public long enteringDetails() {return context==0?-1:context;}
    public void returnedDetails(long token,String device,boolean normal) {
        if(!current(token)){rejectedToken(token,IDENTITY);return;}
        if(!normal || device==null || !endpoint.equalsIgnoreCase(device)) {fault(IDENTITY);return;}
        // May legitimately precede initialized OR follow started. Keep the
        // evidence under this context instead of requiring a callback order.
        identity=true;completeFinalization();
    }
    public long enteringInitialization() {
        if(context==0 || initialized || finalReturned) {fault(LIFECYCLE);return -1;}
        initialized=true;epoch=++epochSerial;return context;
    }
    public void returnedInitialization(long token,boolean normal) {
        if(!current(token)){rejectedToken(token,LIFECYCLE);return;}
        if(!normal){fault(LIFECYCLE);return;}
        initializationReturned=true;completeFinalization();
    }
    public long enteringStart() {return context==0?-1:context;}
    public void returnedStart(long token,int mode,boolean normal) {
        if(!current(token)){rejectedToken(token,LIFECYCLE);return;}
        if(normal && mode==2)return; // OEM Unknown is pending evidence, not an actual transport.
        if(!normal || mode<0 || mode>1 || finalReturned || (actual>=0 && actual!=mode)) {
            fault(LIFECYCLE);return;
        }
        if(!startReturned) {
            if((modes&4)!=0)unknownBindings++;
            else if((modes&(1<<mode))==0)mismatches++;
        }
        actual=mode;startReturned=true;
    }
    public long enteringFinalization(int mode) {
        if(mode==2)return -3; // Does not identify which transport actually finalized.
        if(mode<0 || mode>1){fault(FINALIZE);return -1;}
        if(context==0)return -2; // Genuine idempotent OEM cleanup, no delegate.
        if(actual>=0 && actual!=mode){fault(FINALIZE);return -1;}
        return (context<<2)|mode;
    }
    public void returnedFinalization(long token,boolean normal) {
        if(token==-3)return; // Missing type never supplies a completion receipt.
        if(token==-2) {if(!normal)stale(FINALIZE);return;}
        long generation=token>>>2;int mode=(int)(token&3);
        if(!current(generation)){rejectedToken(token,FINALIZE);return;}
        if(!normal || mode>1 || (actual>=0 && actual!=mode)){fault(FINALIZE);return;}
        finalMode=mode;finalReturned=true;completeFinalization();
    }
    private void completeFinalization() {
        // Full returned OEM lifecycle, identity and request-call return are
        // independent evidence. Missing evidence remains pending, not a fault.
        if(!finalReturned || !initialized || !initializationReturned || !identity
                || invocation!=null || blockers!=0 || finalMode<0
                || (actual>=0 && actual!=finalMode))return;
        lastFinalizedEpoch=epoch;lastFinalizedTarget=endpoint;
        closeContext();promoteSuccessors();
    }
    private void promoteSuccessors() {
        if(successors.isEmpty())return;
        String selected=successors.get(0).endpoint;
        for(Request request:successors)if(!request.returned || !selected.equalsIgnoreCase(request.endpoint)) {
            // Keep unresolved requests; there is no endpoint in native init
            // with which to choose among different candidates.
            fault(OVERLAP);return;
        }
        context=++contextSerial;endpoint=selected;anchor=successors.get(0).requestedMode;
        for(Request request:successors){modes|=1<<request.requestedMode;requests.add(request);}
        successors.clear();
        failedSingleWired=requests.size()==1 && anchor==0
            && "INVALID_ARGUMENT".equals(requests.get(0).result);
    }
    private void closeContext() {
        context=0;epoch=0;endpoint="";modes=0;anchor=-1;actual=-1;
        initialized=false;initializationReturned=false;identity=false;startReturned=false;
        finalReturned=false;finalMode=-1;failedSingleWired=false;requests.clear();
    }
    public long enteringFailedWiredRemoval(String device) {
        return context>0 && blockers==0 && invocation==null && !initialized
            && failedSingleWired && device!=null && endpoint.equalsIgnoreCase(device)?context:-1;
    }
    public void returnedFailedWiredRemoval(long token,boolean normal) {
        if(token<0)return;
        if(!current(token)){stale(FINALIZE);return;}
        if(!normal){fault(FINALIZE);return;}
        if(failedSingleWired && invocation==null && !initialized && blockers==0) {
            retirements++;closeContext();promoteSuccessors();
        }
    }
    private int successorModes(){int value=0;for(Request request:successors)value|=1<<request.requestedMode;return value;}
    public int openMask(){return (context==0?0:1<<(actual>=0?actual:anchor))|successorModes();}
    public int pendingMask(){return (context>0 && !initialized?1<<(actual>=0?actual:anchor):0)|successorModes();}
    public int actualTransport(){return epoch>0 && startReturned?actual+1:0;}
    public long epoch(){return epochSerial;}
    public String sessionTarget(){return identity && initialized?endpoint:"";}
    public boolean identityVerified(){return identity && initialized;}
    public int confidence() {
        if(blockers!=0)return CONFLICTING;
        if(context==0)return IDLE;
        return initialized && initializationReturned && identity && startReturned && !finalReturned
            ?VERIFIED:PENDING;
    }
    public boolean settled(){return context==0 && successors.isEmpty() && invocation==null && blockers==0;}
    public int history(){return history;}
    public int blockers(){return blockers;}
    public int modes(){return modes|successorModes();}
    public int requestCount(){return requestCount;}
    public int acceptedCount(){return acceptedCount;}
    public int rejectedCount(){return rejectedCount;}
    public int repeats(){return repeats;}
    public int mixed(){return mixed;}
    public int unknownBindings(){return unknownBindings;}
    public int mismatches(){return mismatches;}
    public int retirements(){return retirements;}
    public long lastFinalizedEpoch(){return lastFinalizedEpoch;}
    public String lastFinalizedTarget(){return lastFinalizedTarget;}
}
