import java.io.IOException;

/** Strict private observation reply. No timer/log/native pointer is an ack. */
public final class OemAttemptSnapshot {
    public static final int WORK_MASK=2047;
    public final String receiver,target;
    public final long sequence,revision;
    public final int dispatches,connectCalls,queued,reentryIntent,connectEntries;
    public final boolean unknown,complete,workSettled,preflightRejected;
    private OemAttemptSnapshot(String[] fields) throws IOException {
        receiver=fields[1];
        if(!receiver.matches("[1-9][0-9]{0,7}:[1-9][0-9]{0,18}:[1-9][0-9]{0,18}"))throw invalid();
        for(String part:receiver.split(":"))number(part);
        sequence=number(fields[2]);revision=number(fields[3]);target=fields[4];
        if(sequence==0) {if(target.length()!=0)throw invalid();}
        else if(!target.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}"))throw invalid();
        dispatches=counter(fields[5]);connectCalls=counter(fields[6]);queued=counter(fields[7]);
        if((queued & ~WORK_MASK)!=0)throw invalid();
        unknown=flag(fields[8]);complete=flag(fields[9]);reentryIntent=counter(fields[10]);
        workSettled=flag(fields[11]);connectEntries=counter(fields[12]);
        preflightRejected=flag(fields[13]);
        if(reentryIntent>3)throw invalid();
        if(connectCalls>connectEntries || (sequence==0 && connectEntries!=0))throw invalid();
        if(workSettled && (sequence==0 || unknown || dispatches!=0 || connectCalls!=0 || queued!=0))throw invalid();
        if(complete && (!workSettled || reentryIntent!=0))throw invalid();
        if(preflightRejected && !workSettled)throw invalid();
    }
    private static IOException invalid(){return new IOException("oem-attempt-observation-schema");}
    private static long number(String text) throws IOException {
        if(!text.matches("0|[1-9][0-9]{0,18}"))throw invalid();
        try{return Long.parseLong(text);}catch(NumberFormatException invalid){throw invalid();}
    }
    private static int counter(String text) throws IOException {
        long value=number(text);if(value>Integer.MAX_VALUE)throw invalid();return (int)value;
    }
    private static boolean flag(String text) throws IOException {
        if(text.equals("true"))return true;if(text.equals("false"))return false;throw invalid();
    }
    public static OemAttemptSnapshot parse(String reply,String processGeneration) throws IOException {
        if(reply==null || reply.length()>512 || reply.indexOf('\n')>=0 || reply.indexOf('\r')>=0)throw invalid();
        String[] fields=reply.split("\\|",-1);
        if(fields.length!=14 || !fields[0].equals("EV6ATTEMPT4"))throw invalid();
        OemAttemptSnapshot result=new OemAttemptSnapshot(fields);
        if(processGeneration==null || !result.receiver.startsWith(processGeneration+":"))
            throw new IOException("oem-attempt-receiver-mismatch");
        return result;
    }
    public boolean owns(String owner,long attempt,String selected) {
        return receiver.equals(owner) && sequence==attempt && target.equals(selected);
    }
}
