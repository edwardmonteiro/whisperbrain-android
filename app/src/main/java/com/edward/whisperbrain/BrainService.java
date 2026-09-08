package com.edward.whisperbrain;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import java.io.File;

public final class BrainService extends Service {
    public static final String START = "com.edward.whisperbrain.START";
    public static final String STOP = "com.edward.whisperbrain.STOP";
    public static final String ASK = "com.edward.whisperbrain.ASK";
    private final Handler main = new Handler(Looper.getMainLooper());
    private RealtimeClient api;
    private PrivateVoice voice;
    private AdvicePolicy policy;
    private AudioRecord recorder;
    private PowerManager.WakeLock wake;
    private volatile boolean capturing;
    private volatile long sentBytes, resumeCaptureAt;
    private volatile int level;
    private boolean automatic, manual, stopping;
    private volatile long generation;
    private long deadline, requestedAt;

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { finish("Session ended."); return START_NOT_STICKY; }
        String action = intent.getAction();
        if (STOP.equals(action)) finish("Microphone and connection are off.");
        else if (ASK.equals(action)) {
            if (SessionState.active && policy != null && policy.hasContext()) {
                manual = true;
                SessionState.detail = "Your nudge is queued for the next quiet moment."; SessionState.changed();
            } else {
                SessionState.detail = "Listen to a complete sentence first."; SessionState.changed();
                if (!SessionState.active) stopSelf();
            }
        } else if (START.equals(action) && !SessionState.active) startSession();
        return START_NOT_STICKY;
    }
    private Notification notification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel("session", "Listening session", NotificationManager.IMPORTANCE_LOW);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        nm.createNotificationChannel(channel);
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, BrainService.class).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "session").setSmallIcon(R.drawable.ic_mic)
                .setContentTitle("WhisperBrain · active session")
                .setContentText("Live microphone audio goes to your AI provider. Tap Stop to end.")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build()).build();
    }
    private void startSession() {
        stopping = false;
        long id = ++generation;
        SessionState.reset(); SessionState.active = true; SessionState.started = SystemClock.elapsedRealtime();
        SessionState.status = "Preparing private audio"; SessionState.detail = "Microphone starts after the connection is ready.";
        SessionState.changed();
        try {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                finish("Microphone permission is required."); return;
            }
            startForeground(7, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            Vault vault = new Vault(this);
            String key = vault.get("api_key", "").trim();
            String model = vault.get("model", "gpt-realtime-2.1-mini").trim();
            String goal = vault.get("goal", "Help me think clearly and ask better questions.");
            String language = vault.get("language", "en-US");
            String memories = vault.memories().toString();
            if (!(key.startsWith("sk-") || key.startsWith("ek_")) || key.contains("\n") || key.contains("\r")) {
                finish("Add your own OpenAI API key in Connection settings."); return;
            }
            if (!model.matches("[A-Za-z0-9._-]{1,100}")) { finish("Check the model name in Connection settings."); return; }
            int minutes = Integer.parseInt(vault.get("minutes", "10"));
            minutes = Math.max(1, Math.min(45, minutes));
            deadline = SessionState.started + minutes * 60_000L;
            automatic = Boolean.parseBoolean(vault.get("automatic", "true"));
            boolean headphonesOnly = Boolean.parseBoolean(vault.get("headphones_only", "false"));
            policy = new AdvicePolicy(); manual = false; requestedAt = 0; sentBytes = 0; level = 0; resumeCaptureAt = 0;
            wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WhisperBrain:session");
            wake.acquire(minutes * 60_000L + 5_000L);
            // Clean up only our own temporary synthesized advice after a prior interrupted process.
            File[] leftovers = getCacheDir().listFiles((dir, name) -> name.startsWith("wb-voice-") && name.endsWith(".wav"));
            if (leftovers != null) for (File file : leftovers) file.delete();
            voice = new PrivateVoice(this, new PrivateVoice.Listener() {
                @Override public void ready(String route) { postFor(id, () -> {
                    SessionState.route = route; SessionState.status = "Connecting to your AI"; SessionState.changed();
                    connect(id, key, model, goal, memories, language);
                }); }
                @Override public void finished() { postFor(id, () -> {
                    resumeCaptureAt = SystemClock.elapsedRealtime() + 300;
                    policy.voiceFinished(SystemClock.elapsedRealtime());
                    SessionState.status = "Listening"; SessionState.detail = "Waiting for a useful moment."; SessionState.changed();
                }); }
                @Override public void failed(String message) { postFor(id, () -> finish(message)); }
            });
            voice.start(language, headphonesOnly);
            main.post(tick);
            main.postDelayed(() -> {
                if (generation == id && SessionState.active && !SessionState.connected) finish("Connection setup timed out. Check audio settings, internet, and API access.");
            }, 30_000);
        } catch (Exception e) { finish("Session setup failed. Check permissions and your saved connection settings."); }
    }
    private void connect(long id, String key, String model, String goal, String memories, String language) {
        api = new RealtimeClient(new RealtimeClient.Listener() {
            @Override public void configured() { postFor(id, () -> startCapture(id)); }
            @Override public void speechStarted() { postFor(id, () -> {
                policy.speechStarted(SystemClock.elapsedRealtime());
                // Avoid delivering a pending nudge if someone has started speaking again.
                if (voice != null && voice.isBusy() && !voice.isPlaying()) voice.cancel();
                SessionState.status = "Listening"; SessionState.detail = "Speech detected.";
            }); }
            @Override public void speechStopped() { postFor(id, () -> policy.speechStopped(SystemClock.elapsedRealtime())); }
            @Override public void committed() { postFor(id, () -> policy.committed()); }
            @Override public void answer(String json, long tokens) { postFor(id, () -> handleAdvice(json, tokens)); }
            @Override public void failed(String message) { postFor(id, () -> finish(message)); }
        });
        try { api.connect(key, model, goal, memories, language); }
        catch (Exception e) { finish("Could not open the live-audio connection."); }
    }
    @SuppressWarnings("MissingPermission")
    private void startCapture(long id) {
        try {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                finish("Microphone permission was removed."); return;
            }
            int minimum = AudioRecord.getMinBufferSize(24000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) { finish("This device does not expose the required 24 kHz microphone format."); return; }
            AudioRecord capture = new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                    .setBufferSizeInBytes(Math.max(minimum * 2, 9600)).build();
            recorder = capture;
            if (capture.getState() != AudioRecord.STATE_INITIALIZED) {
                recorder = null; capture.release(); finish("Microphone initialization failed."); return;
            }
            // Ask for the phone microphone to hear the room. Android may pair input with Bluetooth output.
            for (AudioDeviceInfo d : getSystemService(AudioManager.class).getDevices(AudioManager.GET_DEVICES_INPUTS)) {
                if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) { capture.setPreferredDevice(d); break; }
            }
            capture.startRecording();
            if (capture.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                recorder = null; capture.release(); finish("Another app may be using the microphone."); return;
            }
            capturing = true;
            RealtimeClient live = api; PrivateVoice privateVoice = voice;
            Thread worker = new Thread(() -> {
                byte[] pcm = new byte[4800];
                try {
                    while (capturing && generation == id) {
                        int count = capture.read(pcm, 0, pcm.length, AudioRecord.READ_BLOCKING);
                        if (!capturing || generation != id) break;
                        if (count <= 0) { postFor(id, () -> finish("Microphone capture was interrupted.")); break; }
                        double sum = 0;
                        for (int i = 0; i + 1 < count; i += 2) {
                            short sample = (short) ((pcm[i] & 255) | (pcm[i + 1] << 8)); sum += (double) sample * sample;
                        }
                        level = Math.min(100, (int) (Math.sqrt(sum / Math.max(1, count / 2)) / 32768.0 * 400));
                        // This first version deliberately omits input during its own brief spoken advice.
                        if (!privateVoice.isPlaying() && SystemClock.elapsedRealtime() >= resumeCaptureAt) {
                            if (!live.audio(pcm, count)) break;
                            sentBytes += count;
                        }
                    }
                } catch (Exception e) { postFor(id, () -> finish("Microphone capture failed.")); }
                finally { try { capture.release(); } catch (Exception ignored) {} }
            }, "WhisperBrain-mic");
            worker.start();
            SessionState.connected = true; SessionState.status = "Listening";
            SessionState.detail = automatic ? "Automatic nudges · 20-second cooldown" : "Listening · tap Nudge me when you want advice";
            SessionState.changed();
        } catch (Exception e) {
            if (recorder != null) { try { recorder.release(); } catch (Exception ignored) {} recorder = null; }
            finish("Microphone could not start. Check microphone access in Android settings.");
        }
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!SessionState.active || stopping) return;
            long now = SystemClock.elapsedRealtime();
            if (now >= deadline) { finish("Your session time limit was reached. Start another session to continue."); return; }
            if (requestedAt > 0 && policy.isInFlight() && now - requestedAt > 30_000) {
                finish("The AI response timed out. Recording and the connection have stopped."); return;
            }
            if (SessionState.connected && voice != null) {
                if (!voice.routeIsValid()) { finish("Private audio route changed. Your session has stopped."); return; }
                if (policy.request(now, automatic, manual, voice.isBusy())) {
                    boolean requestedManually = manual; manual = false; requestedAt = now;
                    SessionState.status = "Considering a nudge"; SessionState.detail = "Still listening while the AI considers your context.";
                    api.advise(requestedManually);
                }
                try {
                    AudioDeviceInfo source = recorder == null ? null : recorder.getRoutedDevice();
                    if (source != null) SessionState.input = source.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC
                            ? "Phone microphone" : "Input: " + source.getProductName();
                } catch (Exception ignored) {}
            }
            SessionState.level = level; SessionState.audioBytes = sentBytes; SessionState.changed();
            main.postDelayed(this, 500);
        }
    };
    private void handleAdvice(String json, long tokens) {
        policy.completed(); requestedAt = 0; SessionState.tokens += tokens;
        try {
            Advice a = Advice.parse(json);
            if (!a.context.isEmpty()) SessionState.context = a.context;
            SessionState.memory = a.memory;
            if (!a.text.isEmpty()) SessionState.advice = a.text;
            SessionState.status = "Listening";
            SessionState.detail = "No new spoken nudge needed.";
            if (a.speak) {
                SessionState.suggestions++;
                if (!policy.isSpeaking() && !voice.isBusy() && voice.routeIsValid()) {
                    SessionState.status = "A quiet nudge";
                    SessionState.detail = "Microphone input is omitted during spoken advice to prevent feedback.";
                    voice.speak(a.text);
                } else SessionState.detail = "A nudge is on screen. Speech resumed, so it was not read aloud.";
            }
        } catch (Exception e) {
            SessionState.status = "Listening";
            SessionState.detail = "The AI reply was not usable. It was not spoken or saved.";
        }
        SessionState.changed();
    }
    private void postFor(long id, Runnable action) {
        main.post(() -> { if (generation == id && SessionState.active && !stopping) action.run(); });
    }
    private void finish(String reason) {
        if (stopping) return;
        stopping = true; generation++; capturing = false;
        main.removeCallbacksAndMessages(null);
        if (recorder != null) {
            try { recorder.stop(); } catch (Exception ignored) {}
            // The recording thread owns release once it has started.
            recorder = null;
        }
        if (api != null) { api.close(); api = null; }
        if (voice != null) { voice.shutdown(); voice = null; }
        if (wake != null) { if (wake.isHeld()) wake.release(); wake = null; }
        SessionState.active = false; SessionState.connected = false; SessionState.level = 0;
        SessionState.status = "Session ended"; SessionState.detail = reason; SessionState.changed();
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @Override public void onDestroy() { if (!stopping) finish("Session stopped. Microphone and connection are off."); super.onDestroy(); }
    @Override public void onTaskRemoved(Intent rootIntent) { finish("App closed. Microphone and connection are off."); }
}
