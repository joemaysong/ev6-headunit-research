package org.ev6.research;

/** Real BT attachment waits for the on-unit projection network to be ready. */
public final class TransportReadiness {
    public static final int NONE=0, START=1, STOP=-1;
    public boolean attached, started;
    public int attachment(boolean present,boolean apReady) {
        if(!present){int action=started?STOP:NONE;attached=false;started=false;return action;}
        attached=true;return ready(apReady);
    }
    public int ready(boolean apReady) {
        if(!attached || started || !apReady)return NONE;
        started=true;return START;
    }
    public void startFailed(){started=false;}
}
