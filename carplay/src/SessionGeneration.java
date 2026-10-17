package org.ev6.research;

/** Exact process-generation binding for a privileged, verified-idle reset. */
public final class SessionGeneration {
    public static boolean matches(String proof,String command,String stat,String apReady) {
        if(!"0".equals(apReady) || proof==null || !proof.matches("[1-9][0-9]{0,7}:[1-9][0-9]{0,18}"))return false;
        if(!"com.daudio.app.dipo".equals(command) || stat==null)return false;
        int end=stat.lastIndexOf(')');int open=stat.indexOf(" (");
        if(open<=0 || end<=open || end+2>=stat.length())return false;
        String[] fields=stat.substring(end+2).trim().split("\\s+");
        if(fields.length<20 || !fields[19].matches("[1-9][0-9]{0,18}"))return false;
        return proof.equals(stat.substring(0,open)+":"+fields[19]);
    }
}
