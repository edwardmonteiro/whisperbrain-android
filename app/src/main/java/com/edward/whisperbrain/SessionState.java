package com.edward.whisperbrain;

/** Main-thread UI state. Current conversation state is deliberately not persisted. */
public final class SessionState {
    private SessionState() {}
    public static boolean active, connected;
    public static String status = "Ready when you are", detail = "Test your audio, then start a session.";
    public static String context = "Your conversation context will appear here.";
    public static String advice = "A useful question. A clearer next step. Only when it helps.";
    public static String memory = "", route = "", input = "";
    public static long started, tokens, audioBytes;
    public static int level, suggestions;
    public static Runnable observer;
    public static void changed() { if (observer != null) observer.run(); }
    public static void reset() {
        connected = false; context = "Listening for context…"; advice = "Waiting for something useful to add.";
        memory = ""; route = ""; input = ""; tokens = 0; audioBytes = 0; level = 0; suggestions = 0;
    }
}
