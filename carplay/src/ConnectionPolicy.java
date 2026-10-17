/** Single active projection; an explicit stop remains paused for this boot. */
public final class ConnectionPolicy {
    public String target="";
    public boolean paused, connected, disconnecting;
    private String attempted="";
    public boolean begin(String address, boolean manual) {
        if (connected || disconnecting || address==null || !address.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")) return false;
        if (paused && !manual) return false;
        if (manual) { paused=false; attempted=""; }
        target=address;
        return true;
    }
    public boolean canRequest(String token, boolean nativeReady, boolean knownPeer) {
        return !paused && !connected && !disconnecting && target.length()>0
            && (nativeReady || knownPeer) && token.length()>0 && !token.equals(attempted);
    }
    public static boolean nativeIdentityReady(String epoch,String recordEpoch,boolean selectedNativeRecord,boolean selectedHfp) {
        return epoch.length()>0 && selectedNativeRecord && (epoch.equals(recordEpoch) || selectedHfp);
    }
    public static boolean oemSessionReady(boolean nativeActive,boolean sessionPublished,boolean controlOwner,String selected,String oemAddress) {
        return nativeActive && sessionPublished && controlOwner && selected!=null
            && selected.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}") && selected.equalsIgnoreCase(oemAddress);
    }
    public static boolean originalAssociationCleared(String address) {
        return address==null || address.length()==0;
    }
    public static boolean userStopApplies(String selected,String requested,boolean active) {
        return active && selected!=null && selected.length()>0 && selected.equals(requested);
    }
    public static boolean needsBluetoothFallback(boolean nativeReady,boolean rememberedDevice,boolean requested,boolean discoveryWindowElapsed) {
        return !nativeReady && rememberedDevice && !requested && discoveryWindowElapsed;
    }
    public void requested(String token) { attempted=token; }
    public void started() { connected=true; disconnecting=false; }
    public void stopByUser() { paused=true; disconnecting=true; }
    public void ended() { connected=false; disconnecting=false; target=""; }
}
