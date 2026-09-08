package com.edward.whisperbrain;

/** Pure policy: no Android state, wall clock, or network dependencies. */
public final class AdvicePolicy {
    public static final long QUIET_MS = 1500;
    public static final long COOLDOWN_MS = 20_000;
    private boolean speaking, freshAudio, hasContext, inFlight;
    private long lastSpeech = -1, lastRequest = -1;

    public void speechStarted(long now) { speaking = true; lastSpeech = now; }
    public void speechStopped(long now) { speaking = false; lastSpeech = now; }
    public void committed() { freshAudio = true; hasContext = true; }
    public void completed() { inFlight = false; }
    public void voiceFinished(long now) { lastRequest = now; }
    public boolean hasContext() { return hasContext; }
    public boolean isInFlight() { return inFlight; }
    public boolean isSpeaking() { return speaking; }
    public boolean request(long now, boolean automatic, boolean manual, boolean playing) {
        if (inFlight || speaking || playing || !hasContext) return false;
        if (lastSpeech < 0 || now - lastSpeech < QUIET_MS) return false;
        if (!manual && (!automatic || !freshAudio)) return false;
        if (!manual && lastRequest >= 0 && now - lastRequest < COOLDOWN_MS) return false;
        inFlight = true;
        freshAudio = false;
        lastRequest = now;
        return true;
    }
}
