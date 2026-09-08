import com.edward.whisperbrain.AdvicePolicy;

/** Deterministic behavioral checks; run without an Android SDK or API key. */
public final class PolicyChecks {
    private static int checks;
    private static void expect(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static AdvicePolicy heard(long start, long end) {
        AdvicePolicy p = new AdvicePolicy();
        p.speechStarted(start); p.speechStopped(end); p.committed(); return p;
    }
    public static void main(String[] args) {
        AdvicePolicy empty = new AdvicePolicy();
        expect(!empty.request(5000, true, false, false), "Never advise without heard context");
        expect(!empty.request(5000, false, true, false), "Manual requests also require context");

        AdvicePolicy p = heard(0, 1000);
        expect(!p.request(2499, true, false, false), "Wait for the entire quiet interval");
        expect(p.request(2500, true, false, false), "First eligible pause can trigger a check");
        expect(!p.request(2501, true, true, false), "Do not overlap responses even for a manual request");
        p.completed();
        expect(!p.request(50000, true, false, false), "Do not loop on silence without new audio");
        expect(p.request(50000, false, true, false), "A deliberate manual request may reuse heard context");
        p.completed();

        AdvicePolicy cooldown = heard(0, 1000);
        expect(cooldown.request(2500, true, false, false), "Initial check"); cooldown.completed();
        cooldown.speechStarted(6000); cooldown.speechStopped(7000); cooldown.committed();
        expect(!cooldown.request(22499, true, false, false), "Enforce a 20-second interval");
        expect(cooldown.request(22500, true, false, false), "New speech remains eligible after cooldown");
        cooldown.completed(); cooldown.voiceFinished(26000);
        cooldown.speechStarted(30000); cooldown.speechStopped(31000); cooldown.committed();
        expect(!cooldown.request(45999, true, false, false), "The cooldown restarts after spoken advice finishes");
        expect(cooldown.request(46000, true, false, false), "Allow a later useful pause");

        AdvicePolicy busy = heard(0, 1000);
        expect(!busy.request(5000, true, false, true), "Never overlap local voice playback");
        busy.speechStarted(5000);
        expect(!busy.request(6000, true, true, false), "Even a manual nudge waits for speech to stop");
        busy.speechStopped(6500); busy.committed();
        expect(!busy.request(9000, false, false, false), "Manual mode stays quiet by itself");
        expect(busy.request(9000, false, true, false), "Manual mode works at a quiet moment");
        System.out.println(checks + " intervention policy checks passed.");
    }
}
