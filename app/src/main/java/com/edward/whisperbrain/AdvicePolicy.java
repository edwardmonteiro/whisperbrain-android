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
    public void responseStarted() { freshAudio = false; }
    public void completed() { inFlight = false; }
    public void voiceFinished(long now) { lastRequest = now; }
    public boolean hasContext() { return hasContext; }
    public boolean isInFlight() { return inFlight; }
    public boolean isSpeaking() { return speaking; }
    /** The caller verifies that audio was sent before explicitly committing it. */
    public boolean requestSnapshot(long now, boolean playing) {
        if (inFlight || playing) return false;
        inFlight = true; freshAudio = false; lastRequest = now;
        return true;
    }
    /** Keep the screen useful during long turns; spoken output still waits for quiet. */
    public boolean requestCheckpoint(long now, boolean automatic, boolean playing) {
        if (!automatic || !speaking || lastSpeech < 0 || now - lastSpeech < COOLDOWN_MS) return false;
        if (lastRequest >= 0 && now - lastRequest < COOLDOWN_MS) return false;
        return requestSnapshot(now, playing);
    }
    public boolean quietForVoice(long now) {
        return !speaking && lastSpeech >= 0 && now - lastSpeech >= QUIET_MS;
    }
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
