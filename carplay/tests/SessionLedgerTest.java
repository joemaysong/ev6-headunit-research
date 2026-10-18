/** Regression cases for real ordering failures from V10-V13, no OEM actions. */
public final class SessionLedgerTest {
    static int checks;
    static final String A="AA:BB:CC:DD:EE:FF",B="00:11:22:33:44:55";
    static void check(boolean x){checks++;if(!x)throw new AssertionError("ledger check "+checks);}
    static void request(SessionLedger s,int mode,String target,String result){s.enteringCall(mode,target);s.delegated(target);s.returnedCall(true,result);}
    static void start(SessionLedger s,String target,int actual){long d=s.enteringDetails();s.returnedDetails(d,target,true);long i=s.enteringInitialization();s.returnedInitialization(i,true);s.returnedStart(s.enteringStart(),actual,true);}
    static void end(SessionLedger s,int mode){s.returnedFinalization(s.enteringFinalization(mode),true);}
    public static void main(String[] args){
        SessionLedger s=new SessionLedger();check(s.settled());request(s,2,A,"OK");start(s,A,0);
        check(s.actualTransport()==1 && s.identityVerified() && s.unknownBindings()==1 && s.blockers()==0);end(s,0);check(s.settled() && s.lastFinalizedEpoch()==1);
        s=new SessionLedger();request(s,1,A,"OK");request(s,0,A,"OK");start(s,A,1);
        check(s.repeats()==1 && s.mixed()==1 && s.actualTransport()==2 && s.blockers()==0);end(s,1);check(s.settled());
        s=new SessionLedger();request(s,1,A,"OK");start(s,A,1);long stale=s.enteringDetails();request(s,0,B,"OK");end(s,1);
        check(!s.settled() && s.pendingMask()!=0);s.returnedDetails(stale,A,true);check(s.blockers()==0 && !s.identityVerified());start(s,B,0);check(s.sessionTarget().equals(B) && s.actualTransport()==1 && s.blockers()==0);end(s,0);check(s.settled());
        s=new SessionLedger();request(s,0,A,"INVALID_ARGUMENT");long removal=s.enteringFailedWiredRemoval(B);s.returnedFailedWiredRemoval(removal,true);check(!s.settled());
        removal=s.enteringFailedWiredRemoval(A);s.returnedFailedWiredRemoval(removal,true);check(s.settled() && s.retirements()==1);
        s=new SessionLedger();request(s,1,A,"OK");start(s,A,1);end(s,2);check(!s.settled());end(s,1);check(s.settled());
        System.out.println("SESSION_LEDGER_CHECKS="+checks);
    }
}
