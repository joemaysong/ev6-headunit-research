package org.ev6.research;

/** Acknowledges delegate invocation only, never authorization or session success. */
public final class TransportDispatch {
    private TransportDispatch() {}

    public static void dispatch(TransportReadiness state, Object owner, int action) {
        if (action == TransportReadiness.NONE) return;
        if (!apply(owner, action == TransportReadiness.START)
                && action == TransportReadiness.START) state.startFailed();
    }

    public static boolean apply(Object owner, boolean start) {
        if (owner == null) return false;
        try {
            Object result = owner.getClass().getMethod("applyTransport", boolean.class)
                    .invoke(owner, start);
            // A legacy void callback is not evidence of successful dispatch.
            return Boolean.TRUE.equals(result);
        } catch (Exception failure) {
            return false;
        }
    }
}
