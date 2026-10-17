import java.io.IOException;

public final class OemAttemptOwnerTest {
    private static int checks;
    private static final String TARGET="AA:BB:CC:DD:EE:FF",GEN="123:456",OWNER=GEN+":1";
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Attempt owner check "+checks);}
    private static String line(long sequence,long revision,String target,int dispatch,int calls,int queued,boolean unknown,boolean complete) {
        return "EV6ATTEMPT4|"+OWNER+"|"+sequence+"|"+revision+"|"+target+"|"+dispatch+"|"+calls+"|"+queued+"|"+unknown+"|"+complete+"|0|"+complete+"|"+(sequence>0?1:0)+"|false";
    }
    private static OemAttemptSnapshot snapshot(long sequence,long revision,boolean complete) throws Exception {
        return OemAttemptSnapshot.parse(line(sequence,revision,sequence==0?"":TARGET,0,0,0,false,complete),GEN);
    }
    private static OemAttemptSnapshot retained(long revision,int intent,boolean settled,int entries) throws Exception {
        return OemAttemptSnapshot.parse("EV6ATTEMPT4|"+OWNER+"|1|"+revision+"|"+TARGET+
            "|0|0|0|false|false|"+intent+"|"+settled+"|"+entries+"|false",GEN);
    }
    private static void rejects(String text) {
        boolean rejected=false;try{OemAttemptSnapshot.parse(text,GEN);}catch(IOException expected){rejected=true;}
        check(rejected);
    }
    public static void main(String[] args) throws Exception {
        OemAttemptSnapshot idle=snapshot(0,1,false),done=snapshot(1,10,true);
        check(idle.sequence==0 && !idle.complete);check(done.owns(OWNER,1,TARGET));
        check(!done.owns(OWNER,2,TARGET));check(!done.owns(OWNER,1,"00:11:22:33:44:55"));
        for(String bad:new String[]{null,"","EV6ATTEMPT4|UNKNOWN",line(0,1,TARGET,0,0,0,false,false),
                line(1,1,"",0,0,0,false,false),line(1,1,TARGET,1,0,0,false,true),
                line(1,1,TARGET,0,1,0,false,true),line(1,1,TARGET,0,0,1,false,true),
                line(1,1,TARGET,0,0,0,true,true),line(1,1,TARGET,0,0,2048,false,false),
                line(1,1,TARGET,0,0,-1,false,false),line(1,1,TARGET,0,0,0,false,true)+"\n",
                line(1,1,TARGET,0,0,0,false,true)+"|extra",
                line(1,1,TARGET,0,0,0,false,true).replace("|1|1|","|01|1|"),
                line(1,1,TARGET,0,0,0,false,true).replace("|1|1|","|"+Long.toString(Long.MAX_VALUE)+"0|1|"),
                line(1,1,TARGET,0,0,0,false,true).replace("false","FALSE")})rejects(bad);
        boolean mismatch=false;
        try{OemAttemptSnapshot.parse(line(1,1,TARGET,0,0,0,false,true),"123:45");}
        catch(IOException expected){mismatch=true;}check(mismatch);
        OemAttemptOwner owner=new OemAttemptOwner();owner.arm(idle,TARGET);check(owner.owned());
        check(!owner.canRelease(idle,false,false,"")); // Earlier idle snapshot is not new submission completion.
        check(!owner.canRelease(snapshot(1,3,false),false,false,""));
        check(!owner.canRelease(done,true,false,"")); // Preserve genuine late native session.
        check(!owner.canRelease(done,false,true,""));
        check(!owner.canRelease(done,false,false,TARGET));
        check(owner.canRelease(done,false,false,""));
        owner.released(done,false,false,null);check(!owner.owned());
        owner.arm(done,TARGET);check(!owner.canRelease(done,false,false,""));
        check(owner.canRelease(snapshot(2,20,true),false,false,""));
        owner=new OemAttemptOwner();owner.arm(idle,TARGET);
        check(!owner.canRelease(snapshot(2,20,true),false,false,""));check(owner.needsReview() && owner.owned());
        owner=new OemAttemptOwner();owner.arm(idle,TARGET);owner.accept(snapshot(1,5,false));
        check(!owner.accept(snapshot(1,4,true)) && owner.needsReview());
        owner=new OemAttemptOwner();owner.arm(idle,TARGET);
        check(!owner.accept(OemAttemptSnapshot.parse(line(1,5,TARGET,0,0,0,true,false),GEN)) && owner.needsReview());
        owner=new OemAttemptOwner();owner.arm(idle,TARGET);
        check(!owner.accept(OemAttemptSnapshot.parse(line(1,5,TARGET,0,0,0,false,true).replace(OWNER,GEN+":2"),GEN)) && owner.needsReview());
        owner=new OemAttemptOwner();owner.arm(idle,TARGET);
        check(!owner.accept(null) && owner.owned() && !owner.needsReview()); // Transport outage is not proof.
        check(owner.canRelease(done,false,false,"")); // Fresh same-owner reply can recover an outage.
        boolean pendingRejected=false;
        try{new OemAttemptOwner().arm(snapshot(1,4,false),TARGET);}catch(IOException expected){pendingRejected=true;}
        check(pendingRejected);
        rejects(line(1,1,TARGET,0,0,0,false,true).replace("|true|0","|true|1"));
        rejects(line(1,1,TARGET,0,0,0,false,false).replace("|false|0","|false|4"));
        boolean armedIntentRejected=false;
        try {new OemAttemptOwner().arm(OemAttemptSnapshot.parse(line(0,1,"",0,0,0,false,false)
            .replace("|false|0","|false|2"),GEN),TARGET);}catch(IOException expected){armedIntentRejected=true;}
        check(armedIntentRejected);
        boolean unprovedRelease=false;
        try{owner.released(idle,false,false,"");}catch(IOException expected){unprovedRelease=true;}
        check(unprovedRelease && owner.owned());
        // V4 adds observed-work settlement and the real connect invocation
        // ordinal. Installed V2 replies cannot silently acquire that meaning.
        rejects(line(1,1,TARGET,0,0,0,false,true).replace("EV6ATTEMPT4","EV6ATTEMPT3"));
        rejects(line(1,1,TARGET,0,0,0,false,true).replace("|true|0|true|1","|true|0|false|1"));
        rejects("EV6ATTEMPT4|"+OWNER+"|1|1|"+TARGET+"|0|0|1|false|false|2|true|1");
        rejects("EV6ATTEMPT4|"+OWNER+"|1|1|"+TARGET+"|0|2|0|false|false|2|false|1");
        OemAttemptSnapshot active=retained(5,3,false,1),retired=retained(10,2,true,1);
        check(retired.workSettled && !retired.complete && retired.connectEntries==1);
        owner=new OemAttemptOwner();owner.arm(idle,TARGET);
        check(!owner.observeSession(active,true,true,false,TARGET));
        check(!owner.observeSession(active,true,false,true,TARGET));
        check(!owner.observeSession(active,false,true,true,TARGET));
        check(!owner.observeSession(active,true,true,true,"00:11:22:33:44:55"));
        check(owner.observeSession(active,true,true,true,TARGET.toLowerCase()));
        check(owner.canRelease(retired,false,false,""));
        check(!owner.canReleaseAfterSession(retired,false,false,"",false));
        check(!owner.canReleaseAfterSession(retired,true,false,"",true));
        check(!owner.canReleaseAfterSession(retired,false,true,"",true));
        check(!owner.canReleaseAfterSession(retired,false,false,TARGET,true));
        check(owner.canReleaseAfterSession(retired,false,false,"",true));
        owner.releasedAfterSession(retired,false,false,"",true);check(!owner.owned());
        owner.arm(retired,TARGET);check(owner.owned());
        check(!owner.canReleaseAfterSession(retired,false,false,"",true));
        owner=new OemAttemptOwner();owner.arm(idle,TARGET);
        check(!owner.canReleaseAfterSession(retired,false,false,"",true));
        owner=new OemAttemptOwner();owner.arm(idle,TARGET);owner.observeSession(active,true,true,true,TARGET);
        check(!owner.canReleaseAfterSession(retained(10,2,false,1),false,false,"",true));
        check(!owner.canReleaseAfterSession(retained(11,1,true,1),false,false,"",true));
        // An original callback can run another actual connect without a new PP
        // sequence. The old successful-session witness must not retire that call.
        check(!owner.canReleaseAfterSession(retained(12,2,true,2),false,false,"",true));
        check(owner.observeSession(retained(13,3,false,2),true,true,true,TARGET));
        check(owner.canReleaseAfterSession(retained(14,2,true,2),false,false,"",true));
        check(!owner.accept(retained(15,2,true,1)) && owner.needsReview());
        OemAttemptSnapshot replacement=OemAttemptSnapshot.parse(line(0,1,"",0,0,0,false,false)
            .replace(OWNER,"124:900:1"),"124:900");
        owner=new OemAttemptOwner();owner.arm(idle,TARGET);
        check(!owner.canRetireReceiver(replacement,false,false,false,""));
        check(!owner.canRetireReceiver(replacement,true,true,false,""));
        check(!owner.canRetireReceiver(replacement,true,false,true,""));
        check(!owner.canRetireReceiver(replacement,true,false,false,TARGET));
        OemAttemptSnapshot sameProcess=OemAttemptSnapshot.parse(line(0,1,"",0,0,0,false,false)
            .replace(OWNER,GEN+":2"),GEN);
        check(!owner.canRetireReceiver(sameProcess,true,false,false,""));
        check(owner.canRetireReceiver(replacement,true,false,false,""));
        owner.accept(OemAttemptSnapshot.parse(line(1,5,TARGET,0,0,0,true,false),GEN));
        check(owner.needsReview());
        owner.retiredReceiver(replacement,true,false,false,"");
        check(!owner.owned() && !owner.needsReview());
        owner=new OemAttemptOwner();owner.arm(idle,TARGET);
        check(!owner.canReleaseStopped(retired,false,false,"",false));
        check(!owner.canReleaseStopped(retired,true,false,"",true));
        check(!owner.canReleaseStopped(retired,false,true,"",true));
        check(!owner.canReleaseStopped(retired,false,false,TARGET,true));
        check(!owner.canReleaseStopped(retained(11,2,false,1),false,false,"",true));
        check(!owner.canReleaseStopped(retained(12,1,true,1),false,false,"",true));
        check(owner.canReleaseStopped(retained(13,2,true,1),false,false,"",true));
        owner.releasedStopped(retained(14,2,true,1),false,false,"",true);check(!owner.owned());
        OemAttemptSnapshot preflight=OemAttemptSnapshot.parse("EV6ATTEMPT4|"+OWNER+"|1|10|"+TARGET+
            "|0|0|0|false|false|2|true|1|true",GEN);
        owner=new OemAttemptOwner();owner.arm(idle,TARGET);
        check(!owner.canReleasePreflight(retired,false,false,""));
        check(!owner.canReleasePreflight(preflight,true,false,""));
        check(!owner.canReleasePreflight(preflight,false,true,""));
        check(!owner.canReleasePreflight(preflight,false,false,TARGET));
        check(owner.canReleasePreflight(preflight,false,false,""));
        rejects("EV6ATTEMPT4|"+OWNER+"|1|10|"+TARGET+"|0|0|0|false|false|2|false|1|true");
        check(OemAttemptOwner.submissionReady(idle));
        check(OemAttemptOwner.submissionReady(retired));
        check(!OemAttemptOwner.submissionReady(active));
        check(OemAttemptOwner.unownedCleanupReady(idle,false));
        check(OemAttemptOwner.unownedCleanupReady(done,false));
        check(OemAttemptOwner.unownedCleanupReady(retired,false));
        check(OemAttemptOwner.unownedCleanupReady(retired,true));
        check(OemAttemptOwner.unownedCleanupReady(preflight,false));
        check(!OemAttemptOwner.unownedCleanupReady(null,true));
        check(OemAttemptOwner.preparationCleanupReady(retired,retained(11,2,true,1)));
        check(!OemAttemptOwner.preparationCleanupReady(retired,retained(11,2,true,2)));
        check(!OemAttemptOwner.preparationCleanupReady(retired,snapshot(2,20,true)));
        check(!OemAttemptOwner.preparationCleanupReady(retired,retained(9,2,true,1)));
        check(!OemAttemptOwner.preparationCleanupReady(null,retired));
        owner=new OemAttemptOwner();owner.adopt(active,TARGET);check(owner.owned());
        check(owner.canReleaseStopped(retired,false,false,"",true));
        owner.releasedStopped(retired,false,false,"",true);check(!owner.owned());
        boolean rejectedAdoption=false;
        try {owner.adopt(active,"00:11:22:33:44:55");}catch(IOException expected){rejectedAdoption=true;}
        check(rejectedAdoption && !owner.owned());
        System.out.println("OEM_ATTEMPT_OWNER_CHECKS="+checks);
        System.out.println("INSTALLED_CONTROLLER_AND_LIVE_ACCEPTANCE=false");
    }
}
