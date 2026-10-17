public final class AttemptObservationTest {
    static int checks;
    static void check(boolean value) {
        checks++;if(!value)throw new AssertionError("Observation model check "+checks);
    }
    static AttemptObservation started() {
        AttemptObservation a=new AttemptObservation("receiver-1");
        a.submitted("selected");a.enteredDispatch();a.enteredConnect("selected");
        return a;
    }
    static void returned(AttemptObservation a) {
        a.returnedConnect(true);a.exitedDispatch(true);
    }
    static void finish(AttemptObservation a) {
        a.terminalReturned("receiver-1",1);a.handlerBoundary("receiver-1",1,0);
    }
    public static void main(String[] args) {
        AttemptObservation a=new AttemptObservation("receiver-1");
        check(!a.snapshot().complete);
        a.handlerBoundary("receiver-1",0,0);
        check(a.snapshot().queued==0 && !a.snapshot().complete);
        a.submitted("selected");finish(a);
        check(!a.snapshot().complete); // No actual original call returned.
        a=started();finish(a);check(!a.snapshot().complete);
        returned(a);check(!a.snapshot().complete); // Earlier boundary invalidated.
        finish(a);check(a.snapshot().complete);
        check(a.snapshot().matches("receiver-1",1,"selected"));
        check(!a.snapshot().matches("receiver-2",1,"selected"));
        check(!a.snapshot().matches("receiver-1",2,"selected"));
        check(!a.snapshot().matches("receiver-1",1,"other"));
        for(int work=1;work<=AttemptObservation.ALL;work<<=1) {
            a=started();returned(a);a.terminalReturned("receiver-1",1);
            a.handlerBoundary("receiver-1",1,work);check(!a.snapshot().complete);
        }
        a=started();returned(a);a.handlerBoundary("receiver-1",1,0);
        check(!a.snapshot().complete); // Empty queue is not terminal proof.
        a.terminalReturned("receiver-1",1);check(!a.snapshot().complete);
        a.handlerBoundary("receiver-1",1,0);check(a.snapshot().complete);
        a.enteredDispatch();check(!a.snapshot().complete);
        a.exitedDispatch(true);check(!a.snapshot().complete);
        a.handlerBoundary("receiver-1",1,0);check(a.snapshot().complete);
        a.observationFailed();a.handlerBoundary("receiver-1",1,0);check(!a.snapshot().complete);
        a=started();a.returnedConnect(false);a.exitedDispatch(true);finish(a);
        check(!a.snapshot().complete);
        a=started();returned(a);finish(a);a.submitted("other");
        a.terminalReturned("receiver-1",1);a.handlerBoundary("receiver-1",1,0);
        check(!a.snapshot().complete); // Old terminal cannot end the successor.
        check(a.snapshot().sequence==2);
        a.enteredDispatch();a.enteredConnect("other");returned(a);
        a.terminalReturned("receiver-1",2);a.handlerBoundary("receiver-1",2,0);
        check(a.snapshot().complete);
        a=started();a.submitted("other");returned(a);
        a.terminalReturned("receiver-1",2);a.handlerBoundary("receiver-1",2,0);
        check(!a.snapshot().complete); // Overlap cannot be hidden by a reset.
        a=started();returned(a);a.terminalReturned("receiver-1",1);
        a.handlerBoundary("receiver-1",1,AttemptObservation.ALL+1);
        check(!a.snapshot().complete && a.snapshot().unknown);
        a=started();returned(a);a.terminalReturned("receiver-2",1);
        a.handlerBoundary("receiver-2",1,0);check(!a.snapshot().complete);
        a=started();returned(a);finish(a);AttemptObservation.Snapshot old=a.snapshot();
        a.submitted("other");check(old.complete && old.sequence==1);
        check(!a.snapshot().complete && a.snapshot().sequence==2);
        a=new AttemptObservation("receiver-1");a.submitted("selected");
        a.enteredDispatch();a.submissionReturned();a.exitedDispatch(true);
        check(!a.snapshot().complete);
        a.handlerBoundary("receiver-1",1,0);check(a.snapshot().complete);
        a.enteredDispatch(false);a.exitedDispatch(true);
        a.submitted("other");check(!a.snapshot().unknown);
        a=started();a.returnedConnect(true,"NOT_SUPPORTED");
        a.submissionReturned();a.exitedDispatch(true);
        a.handlerBoundary("receiver-1",1,0);check(a.snapshot().complete);
        a=started();a.returnedConnect(true,"OK");
        a.submissionReturned();a.exitedDispatch(true);
        a.handlerBoundary("receiver-1",1,0);check(!a.snapshot().complete);
        finish(a);check(a.snapshot().complete);
        a.enteredDispatch(true);a.exitedDispatch(true);
        a.submitted("other");check(a.snapshot().unknown);
        a=started();returned(a);finish(a);check(a.snapshot().complete);
        for(int intent=1;intent<=3;intent++) {
            a.handlerBoundary("receiver-1",1,0,intent);check(!a.snapshot().complete);
        }
        a.handlerBoundary("receiver-1",1,0,0);check(a.snapshot().complete);
        a.enteredDispatch();a.enteredConnect("selected");a.returnedConnect(true,"OK");a.exitedDispatch(true);
        a.handlerBoundary("receiver-1",1,0,0);check(!a.snapshot().complete);
        a.terminalReturned("receiver-1",1);a.handlerBoundary("receiver-1",1,0,0);check(a.snapshot().complete);
        a.handlerBoundary("receiver-1",1,0,4);check(a.snapshot().unknown && !a.snapshot().complete);
        // Actual OEM10 X trace: returned successful call, retained fields3,
        // then full finalization and an empty queue with reconnect field2.
        a=started();a.returnedConnect(true,"OK");a.submissionReturned();a.exitedDispatch(true);
        a.handlerBoundary("receiver-1",1,0,3);
        check(!a.snapshot().workSettled && !a.snapshot().complete);
        a.enteredDispatch();a.terminalReturned("receiver-1",1);
        a.handlerBoundary("receiver-1",1,0,2);check(!a.snapshot().workSettled);
        a.exitedDispatch(true);a.handlerBoundary("receiver-1",1,0,2);
        check(a.snapshot().workSettled && !a.snapshot().complete);
        check(a.snapshot().connectEntries==1);
        a.submitted("other");check(!a.snapshot().unknown && a.snapshot().sequence==2);
        check(!a.snapshot().workSettled && a.snapshot().connectEntries==0);
        // A new original callback/queue producer invalidates the old receipt.
        a=started();returned(a);a.terminalReturned("receiver-1",1);a.handlerBoundary("receiver-1",1,0,2);
        a.enteredDispatch();a.exitedDispatch(true);a.submitted("other");check(a.snapshot().unknown);
        a=started();returned(a);a.terminalReturned("receiver-1",1);a.handlerBoundary("receiver-1",1,0,2);
        a.handlerBoundary("receiver-1",1,AttemptObservation.RETRY,2);
        check(!a.snapshot().workSettled);a.submitted("other");check(a.snapshot().unknown);
        // A later actual connect has a new invocation ordinal and cannot reuse
        // prior finalization, even if it returned between root observations.
        a=started();returned(a);finish(a);
        a.enteredDispatch();a.enteredConnect("selected");a.returnedConnect(true,"OK");a.exitedDispatch(true);
        a.handlerBoundary("receiver-1",1,0,2);
        check(a.snapshot().connectEntries==2 && !a.snapshot().workSettled);
        // No player delegation, not an enum guess: a declined preflight settles.
        a=started();a.returnedConnect(true,"NOT_IN_USE");a.submissionReturned();a.exitedDispatch(true);
        a.handlerBoundary("receiver-1",1,0,2);check(a.snapshot().preflightRejected);
        // Same enum AFTER delegation does not prove absence of control work.
        a=started();a.enteredDelegate("selected");a.returnedConnect(true,"NOT_IN_USE");
        a.submissionReturned();a.exitedDispatch(true);a.handlerBoundary("receiver-1",1,0,2);
        check(a.snapshot().workSettled && !a.snapshot().preflightRejected);
        // A later preflight cannot erase an earlier delegated call in this PP.
        a.enteredDispatch();a.enteredConnect("selected");a.returnedConnect(true,"NOT_IN_USE");
        a.exitedDispatch(true);a.handlerBoundary("receiver-1",1,0,2);
        check(!a.snapshot().preflightRejected);
        a=started();a.enteredConnect("selected");check(a.snapshot().unknown);
        a=started();a.enteredDelegate("other");check(a.snapshot().unknown);
        a=new AttemptObservation("receiver-1");a.submitted("selected");a.enteredDispatch();
        a.submissionReturned();a.exitedDispatch(true);a.handlerBoundary("receiver-1",1,0,0);
        check(a.snapshot().preflightRejected && a.snapshot().connectEntries==0);
        System.out.println("OBSERVATION_MODEL_CHECKS="+checks);
        System.out.println("NO_OEM_HOOK_OR_TRANSPORT_OR_CONTROLLER_IMPLEMENTATION=true");
    }
}
