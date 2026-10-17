package org.ev6.research;

/** Identity of the backend Service instance, never a phone/session identity. */
public final class BackendOwnership {
    private static Object owner;
    private BackendOwnership() {}
    public static synchronized void claim(Object value) {
        if(value==null)throw new IllegalArgumentException("backend owner");
        owner=value;
    }
    public static synchronized boolean current(Object value) {
        return value!=null && owner==value;
    }
    public static synchronized void release(Object value) {
        if(owner==value)owner=null;
    }
}
