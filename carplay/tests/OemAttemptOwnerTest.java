import java.io.IOException;
/** Current EV6ATTEMPT13 parser/owner regressions; synthetic identities only. */
public final class OemAttemptOwnerTest {
    static int checks;
    static final String TARGET="AA:BB:CC:DD:EE:FF", GENERATION="123:456";
    static void check(boolean value) { checks++; if(!value)throw new AssertionError("check "+checks); }
    static String[] idle() {
        String[] f=new String[37];java.util.Arrays.fill(f,"0");
        f[0]="EV6ATTEMPT13";f[1]=GENERATION+":1";f[3]="1";f[4]="";f[31]="";f[36]="";
        for(int i:new int[]{8,9,11,13,15,18,28})f[i]="false";
        f[18]="true";return f;
    }
    static String wire(String[] f) {StringBuilder s=new StringBuilder();for(String v:f){if(s.length()>0)s.append('|');s.append(v);}return s.toString();}
    static OemAttemptSnapshot parse(String[] f)throws Exception{return OemAttemptSnapshot.parse(wire(f),GENERATION);}
    static void rejects(String line)throws Exception{try{OemAttemptSnapshot.parse(line,GENERATION);throw new AssertionError("accepted invalid reply");}catch(IOException expected){checks++;}}
    public static void main(String[] args)throws Exception {
        OemAttemptSnapshot clean=parse(idle());check(OemAttemptOwner.submissionReady(clean));
        OemAttemptOwner owner=new OemAttemptOwner();owner.arm(clean,TARGET);check(owner.owned());
        String[] live=idle();live[2]="1";live[3]="2";live[4]=TARGET;live[12]="1";live[16]="2";live[18]="false";
        live[20]="2";live[21]="1";live[26]="2";live[28]="true";live[30]="2";live[31]=TARGET;live[32]="1";live[33]="1";
        OemAttemptSnapshot active=parse(live);check(active.verifiedSession(TARGET));check(!active.verifiedSession("00:11:22:33:44:55"));
        check(!owner.observeSession(active,true,false,true,TARGET));check(owner.observeSession(active,true,true,true,TARGET));
        check(!owner.canReleaseRecovered(active,false,false,""));
        String[] done=idle();done[2]="1";done[3]="3";done[4]=TARGET;done[12]="1";done[21]="1";done[32]="1";done[33]="1";done[35]="1";done[36]=TARGET;
        OemAttemptSnapshot settled=parse(done);check(!owner.canReleaseRecovered(settled,true,false,""));check(!owner.canReleaseRecovered(settled,false,true,""));check(!owner.canReleaseRecovered(settled,false,false,TARGET));
        check(owner.canReleaseRecovered(settled,false,false,""));owner.releasedRecovered(settled,false,false,"");check(!owner.owned());
        rejects(wire(idle()).replace("EV6ATTEMPT13","EV6ATTEMPT4"));rejects(wire(idle())+"|extra");rejects(wire(idle())+"\n");
        String[] bad=live.clone();bad[31]="";rejects(wire(bad));bad=idle();bad[23]="1";rejects(wire(bad));
        bad=idle();bad[8]="true";rejects(wire(bad));bad=idle();bad[1]="123:999:1";rejects(wire(bad));
        bad=idle();bad[3]="01";rejects(wire(bad));bad=idle();bad[18]="TRUE";rejects(wire(bad));
        owner.arm(clean,TARGET);check(!owner.accept(active));check(!owner.needsReview());check(owner.accept(settled));
        check(!OemAttemptOwner.preparationCleanupReady(clean,settled));
        System.out.println("OEM_ATTEMPT13_CHECKS="+checks);
    }
}
