import java.io.IOException;

/** Strict private observation reply. No timer/log/native pointer is an ack. */
public final class OemAttemptSnapshot {
    public static final int WORK_MASK=2047,SOURCE_INTEGRITY_MASK=4|8|64|128;
    public final String receiver,target;
    public final long sequence,revision,sessionEpoch;
    public final int dispatches,connectCalls,queued,reentryIntent,connectEntries,uncertaintyReasons,transportOpenMask,transportFault,failedWiredRetirements,actualSessionTransport,requestActualMismatches;
    public final boolean unknown,complete,workSettled,preflightRejected,recoveryReady,transportsSettled;
    public final int pendingRequestMask,repeatedPendingRequests,unknownRequestBindings,groupRequestedModes,mixedPendingRequests;
    public final boolean sessionIdentityVerified;
    public final int transportBlocker,lifecycleConfidence,requestCount,acceptedRequests,rejectedRequests;
    public final String sessionTarget,lastFinalizedTarget;
    public final long lastFinalizedEpoch;
    private OemAttemptSnapshot(String[] fields) throws IOException {
        receiver=fields[1];
        if(!receiver.matches("[1-9][0-9]{0,7}:[1-9][0-9]{0,18}:[1-9][0-9]{0,18}"))throw invalid();
        for(String part:receiver.split(":"))number(part);
        sequence=number(fields[2]);revision=number(fields[3]);target=fields[4];
        if(sequence==0) {if(target.length()!=0)throw invalid();}
        else if(target.length()>0 && !target.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}"))throw invalid();
        dispatches=counter(fields[5]);connectCalls=counter(fields[6]);queued=counter(fields[7]);
        if((queued & ~WORK_MASK)!=0)throw invalid();
        unknown=flag(fields[8]);complete=flag(fields[9]);reentryIntent=counter(fields[10]);
        workSettled=flag(fields[11]);connectEntries=counter(fields[12]);
        actualSessionTransport=counter(fields[20]);sessionEpoch=number(fields[21]);requestActualMismatches=counter(fields[22]);
        if(actualSessionTransport>2 || (actualSessionTransport>0 && (sessionEpoch==0
            || (counter(fields[16]) & (1<<(actualSessionTransport-1)))==0)))throw invalid();
        failedWiredRetirements=counter(fields[19]);transportOpenMask=counter(fields[16]);transportFault=counter(fields[17]);transportsSettled=flag(fields[18]);
        pendingRequestMask=counter(fields[23]);repeatedPendingRequests=counter(fields[24]);unknownRequestBindings=counter(fields[25]);
        groupRequestedModes=counter(fields[26]);mixedPendingRequests=counter(fields[27]);sessionIdentityVerified=flag(fields[28]);
        if(groupRequestedModes>7 || (transportOpenMask==0 && groupRequestedModes!=0)
            || (sessionIdentityVerified && (sessionEpoch==0 || transportOpenMask==0)))throw invalid();
        if(pendingRequestMask>7 || (pendingRequestMask & ~transportOpenMask)!=0)throw invalid();
        transportBlocker=counter(fields[29]);lifecycleConfidence=counter(fields[30]);sessionTarget=fields[31];
        requestCount=counter(fields[32]);acceptedRequests=counter(fields[33]);rejectedRequests=counter(fields[34]);
        lastFinalizedEpoch=number(fields[35]);lastFinalizedTarget=fields[36];
        if(lastFinalizedEpoch>sessionEpoch || ((lastFinalizedEpoch==0)!=(lastFinalizedTarget.length()==0))
            || (lastFinalizedEpoch>0 && !lastFinalizedTarget.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")))throw invalid();
        if(transportOpenMask>7 || transportFault>127 || transportBlocker>127
            || (transportBlocker & ~transportFault)!=0 || lifecycleConfidence>3
            || (long)acceptedRequests+rejectedRequests>requestCount)throw invalid();
        if(sessionIdentityVerified!=(!sessionTarget.equals(""))
            || (sessionIdentityVerified && !sessionTarget.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")))throw invalid();
        if((transportBlocker!=0)!=(lifecycleConfidence==3)
            || (lifecycleConfidence==0 && (transportOpenMask!=0 || sessionIdentityVerified))
            || (lifecycleConfidence==2 && (!sessionIdentityVerified || actualSessionTransport==0)))throw invalid();
        preflightRejected=flag(fields[13]);uncertaintyReasons=counter(fields[14]);recoveryReady=flag(fields[15]);
        if(uncertaintyReasons>255 || unknown!=(uncertaintyReasons!=0)
            || (sequence>0 && target.length()==0 && (uncertaintyReasons&2)==0))throw invalid();
        if(recoveryReady && (!unknown || uncertaintyReasons!=1 || sequence==0
            || dispatches!=0 || connectCalls!=0 || queued!=0 || connectEntries<1
            || (reentryIntent!=0 && reentryIntent!=2)))throw invalid();
        if(transportsSettled && (transportOpenMask!=0 || actualSessionTransport!=0 || transportBlocker!=0 || lifecycleConfidence!=0 || dispatches!=0
            || connectCalls!=0 || queued!=0 || (reentryIntent!=0 && reentryIntent!=2)
            || (uncertaintyReasons & SOURCE_INTEGRITY_MASK)!=0))throw invalid();
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
        if(reply==null || reply.length()>768 || reply.indexOf('\n')>=0 || reply.indexOf('\r')>=0)throw invalid();
        String[] fields=reply.split("\\|",-1);
        if(fields.length!=37 || !fields[0].equals("EV6ATTEMPT13"))throw invalid();
        OemAttemptSnapshot result=new OemAttemptSnapshot(fields);
        if(processGeneration==null || !result.receiver.startsWith(processGeneration+":"))
            throw new IOException("oem-attempt-receiver-mismatch");
        return result;
    }
    public boolean verifiedSession(String selected) {
        return lifecycleConfidence==2 && transportBlocker==0 && sessionIdentityVerified
            && selected!=null && selected.equalsIgnoreCase(sessionTarget)
            && (uncertaintyReasons & SOURCE_INTEGRITY_MASK)==0;
    }
    public boolean owns(String owner,long attempt,String selected) {
        return receiver.equals(owner) && sequence==attempt && target.equals(selected);
    }
}
