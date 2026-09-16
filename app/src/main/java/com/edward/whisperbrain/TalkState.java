package com.edward.whisperbrain;

/** Lightweight process state for the Plaud-style recorder and live coach UI. */
public final class TalkState {
    private TalkState() {}
    public static volatile boolean active, connected, coachMode, requestInFlight;
    public static volatile long started;
    public static volatile int level, turns, suggestions;
    public static volatile String sessionId="", status="Ready", detail="", context="", tip="", transcriptTail="";
    public static volatile Runnable observer;

    public static void reset(String session, boolean coach) {
        active=true; connected=false; coachMode=coach; requestInFlight=false;
        started=android.os.SystemClock.elapsedRealtime(); level=0; turns=0; suggestions=0;
        sessionId=session; status="Connecting"; detail="Preparing microphone and AI";
        context=""; tip=""; transcriptTail=""; changed();
    }
    public static void stop(String message) {
        active=false; connected=false; requestInFlight=false; level=0;
        status="Saved"; detail=message; changed();
    }
    public static void changed() {
        Runnable r=observer;
        if(r!=null) new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }
    public static void appendTranscript(String text) {
        String clean=text==null?"":text.trim(); if(clean.isEmpty()) return;
        String next=(transcriptTail+"\n"+clean).trim();
        if(next.length()>1800) next=next.substring(next.length()-1800);
        transcriptTail=next; changed();
    }
}
