/** One bracketed read of real OEM state. This is consistency checking, not an
 * atomic cross-process snapshot or a replacement native session identifier.
 */
public final class SessionEvidence {
    public final OemAttemptSnapshot observation;
    public final boolean nativeActive,controlOwner;
    public final String backendState,association;
    public SessionEvidence(OemAttemptSnapshot observation,boolean active,boolean owner,String backend,String address) {
        this.observation=observation;nativeActive=active;controlOwner=owner;
        backendState=backend;association=address==null?"":address;
    }
    public boolean activeFor(String selected) {
        return nativeActive && controlOwner && selected!=null && selected.equalsIgnoreCase(association)
            && observation!=null && observation.verifiedSession(selected)
            && (observation.actualSessionTransport==1 || backendState.equals("1"));
    }
    public boolean inactive() {
        return !nativeActive && backendState.equals("0") && association.length()==0;
    }
    public boolean canSubmitStop(String selected,boolean submitted) {
        if(observation==null || selected==null || selected.length()==0)return false;
        if(nativeActive) return controlOwner && selected.equalsIgnoreCase(association);
        // Uncertain backend state or a changed actual association blocks STOP.
        // Pending OEM work may be stopped through its original targeted API.
        return submitted && backendState.equals("0") && association.length()==0
            && selected.equals(observation.target)
            && (observation.sessionTarget.length()==0 || selected.equals(observation.sessionTarget));
    }
}
