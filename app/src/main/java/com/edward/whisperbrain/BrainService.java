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
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

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
    private boolean automatic, stopping;
    private volatile long generation;
    private long deadline, requestedAt, captureStarted, pendingVoiceAt;
    private String pendingVoice = "";
    private String notebookSession = "";
    private boolean saveLocalAudio, transcribe;
    private final Set<String> transcriptItems = new HashSet<>();

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { finish("Session ended."); return START_NOT_STICKY; }
        String action = intent.getAction();
        if (STOP.equals(action)) finish("Microphone and connection are off.");
        else if (ASK.equals(action)) {
            if (SessionState.connected && policy != null && sentBytes >= 4800) {
                long now = SystemClock.elapsedRealtime();
                if (policy.requestSnapshot(now, voice.isBusy())) requestAdvice(now, true, true);
                else SessionState.detail = "Uma análise ou leitura já está em andamento.";
            } else {
                SessionState.detail = "Aguarde a conexão e fale uma frase antes de analisar.";
                if (!SessionState.active) stopSelf();
            }
            SessionState.changed();
        } else if (START.equals(action) && !SessionState.active) startSession(intent);
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
    private void startSession(Intent intent) {
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
            NotebookStore notebook = NotebookStore.get(this);
            notebookSession = intent.getStringExtra("session");
            if (notebookSession == null || notebookSession.isEmpty()) notebookSession = notebook.createSession("Conversa ao vivo").getString("id");
            notebook.session(notebookSession); notebook.endSession(notebookSession, false);
            SessionState.sessionId = notebookSession;
            saveLocalAudio = Boolean.parseBoolean(vault.get("save_session_audio", "false"));
            transcribe = Boolean.parseBoolean(vault.get("save_transcript", "true"));
            transcriptItems.clear();
            String key = vault.get("api_key", "").trim();
            String model = vault.get("model", "gpt-realtime-2.1-mini").trim();
            String goal = vault.get("goal", "Help me think clearly and ask better questions.");
            String language = vault.get("language", "pt-BR");
            JSONArray approved = vault.memories(), local = notebook.nodes(notebookSession);
            int included = 0;
            for (int i = local.length() - 1; i >= 0 && included < 12; i--) {
                JSONObject n = local.getJSONObject(i);
                if (n.optString("kind").equals("note") && n.optString("origin").equals("user")) { approved.put(GraphData.text(n.getString("body"),600)); included++; }
            }
            String memories = approved.toString();
            if (!(key.startsWith("sk-") || key.startsWith("ek_")) || key.contains("\n") || key.contains("\r")) {
                finish("Add your own OpenAI API key in Connection settings."); return;
            }
            if (!model.matches("[A-Za-z0-9._-]{1,100}")) { finish("Check the model name in Connection settings."); return; }
            int minutes = Integer.parseInt(vault.get("minutes", "10"));
            minutes = Math.max(1, Math.min(45, minutes));
            deadline = SessionState.started + minutes * 60_000L;
            automatic = Boolean.parseBoolean(vault.get("automatic", "true"));
            boolean headphonesOnly = Boolean.parseBoolean(vault.get("headphones_only", "false"));
            policy = new AdvicePolicy(); requestedAt = 0; sentBytes = 0; level = 0; resumeCaptureAt = 0;
            captureStarted = 0; pendingVoice = ""; pendingVoiceAt = 0;
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
                    SessionState.status = "Ouvindo"; SessionState.detail = "Dica lida. Continuo acompanhando a conversa."; SessionState.changed();
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
                if (voice != null && voice.isBusy() && !voice.isPlaying()) {
                    voice.cancel();
                    pendingVoice = SessionState.advice; pendingVoiceAt = SystemClock.elapsedRealtime();
                }
                SessionState.speechEvents++;
                SessionState.hearing = "A API detectou fala. Acompanhando…";
                if (!policy.isInFlight()) SessionState.status = "Ouvindo";
                SessionState.changed();
            }); }
            @Override public void speechStopped() { postFor(id, () -> {
                policy.speechStopped(SystemClock.elapsedRealtime());
                SessionState.hearing = "Pausa detectada. Preparando a análise.";
                SessionState.changed();
            }); }
            @Override public void committed() { postFor(id, () -> {
                policy.committed(); SessionState.turns++;
                SessionState.hearing = "Trecho de áudio confirmado pela API."; SessionState.changed();
            }); }
            @Override public void responseStarted() { postFor(id, () -> policy.responseStarted()); }
            @Override public void transcript(String itemId, String text) { postFor(id, () -> {
                if (text.trim().isEmpty() || !transcriptItems.add(itemId)) return;
                try { NotebookStore.get(BrainService.this).addNode(notebookSession,"Fala transcrita",text,"transcript","asr"); }
                catch (Exception e) { finish("Não foi possível salvar a transcrição local. Confira o espaço livre."); }
            }); }
            @Override public void transcriptionFailed() { postFor(id, () -> {
                SessionState.detail = "Uma transcrição falhou. Resumos e dicas continuam disponíveis."; SessionState.changed();
            }); }
            @Override public void answer(String json, long tokens) { postFor(id, () -> handleAdvice(json, tokens)); }
            @Override public void failed(String message) { postFor(id, () -> finish(message)); }
        });
        try { api.connect(key, model, goal, memories, language, transcribe); }
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
            final AudioArchive.Writer archive = saveLocalAudio ? new AudioArchive.Writer(this) : null;
            SessionState.savingAudio = archive != null;
            final String archiveSession = notebookSession;
            RealtimeClient live = api; PrivateVoice privateVoice = voice;
            Thread worker = new Thread(() -> {
                byte[] pcm = new byte[4800];
                try {
                    while (capturing && generation == id) {
                        int count = capture.read(pcm, 0, pcm.length, AudioRecord.READ_BLOCKING);
                        if (!capturing || generation != id) break;
                        if (count <= 0) { postFor(id, () -> finish("Microphone capture was interrupted.")); break; }
                        if (archive != null) archive.write(pcm, count);
                        double sum = 0;
                        for (int i = 0; i + 1 < count; i += 2) {
                            short sample = (short) ((pcm[i] & 255) | (pcm[i + 1] << 8)); sum += (double) sample * sample;
                        }
                        double rms = Math.sqrt(sum / Math.max(1, count / 2)) / 32768.0;
                        double db = 20 * Math.log10(Math.max(0.000001, rms));
                        level = Math.max(0, Math.min(100, (int) ((db + 60) * 100 / 60)));
                        // This first version deliberately omits input during its own brief spoken advice.
                        if (!privateVoice.isPlaying() && SystemClock.elapsedRealtime() >= resumeCaptureAt) {
                            if (!live.audio(pcm, count)) break;
                            sentBytes += count;
                        }
                    }
                } catch (Exception e) { postFor(id, () -> finish("Microphone capture failed.")); }
                finally {
                    try { capture.release(); } catch (Exception ignored) {}
                    if (archive != null) {
                        try {
                            archive.close();
                            if (archive.bytes > 0) NotebookStore.get(BrainService.this).addAudio(archiveSession,archive.id,"pcm24k",archive.bytes * 1000 / 48000);
                            else archive.discard();
                        } catch (Exception e) { archive.discard(); new Handler(Looper.getMainLooper()).post(() -> {
                            if (SessionState.sessionId.equals(archiveSession)) SessionState.detail = "O áudio desta escuta não pôde ser salvo. As notas já salvas continuam no caderno."; SessionState.changed();
                        }); }
                        new Handler(Looper.getMainLooper()).post(() -> {SessionState.savingAudio=false;SessionState.changed();});
                    }
                }
            }, "WhisperBrain-mic");
            worker.start();
            captureStarted = SystemClock.elapsedRealtime();
            SessionState.connected = true; SessionState.status = "Ouvindo";
            SessionState.hearing = "Microfone ligado. Aguardando a API detectar fala.";
            SessionState.detail = "Fale uma frase e faça uma pausa de 3 segundos, ou toque em Analisar agora.";
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
                if (!pendingVoice.isEmpty() && now - pendingVoiceAt > 30_000) pendingVoice = "";
                if (!pendingVoice.isEmpty() && !policy.isInFlight() && policy.quietForVoice(now) && !voice.isBusy()) {
                    String next = pendingVoice; pendingVoice = ""; speakAdvice(next);
                }
                if (policy.request(now, automatic, false, voice.isBusy())) {
                    requestAdvice(now, false, false);
                } else if (policy.requestCheckpoint(now, automatic, voice.isBusy())) {
                    requestAdvice(now, false, true);
                }
                if (SessionState.speechEvents == 0 && captureStarted > 0 && now - captureStarted >= 12_000)
                    SessionState.hearing = "A API ainda não detectou fala. Confira a barra do microfone e toque em Analisar agora.";
                try {
                    AudioDeviceInfo source = recorder == null ? null : recorder.getRoutedDevice();
                    if (recorder != null && recorder.getActiveRecordingConfiguration() != null
                            && recorder.getActiveRecordingConfiguration().isClientSilenced()) {
                        finish("O Android silenciou o microfone deste app. Confira o acesso ao microfone e outros apps que estejam gravando."); return;
                    }
                    if (source != null) SessionState.input = source.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC
                            ? "Phone microphone" : "Input: " + source.getProductName();
                } catch (Exception ignored) {}
            }
            SessionState.level = level; SessionState.audioBytes = sentBytes;
            SessionState.requestInFlight = policy != null && policy.isInFlight(); SessionState.changed();
            main.postDelayed(this, 500);
        }
    };
    private void requestAdvice(long now, boolean manual, boolean commitAudio) {
        requestedAt = now; pendingVoice = ""; SessionState.requests++; SessionState.requestInFlight = true;
        SessionState.status = "Analisando sua fala";
        SessionState.detail = "Aguardando a resposta da API. O resultado aparecerá na tela.";
        api.advise(manual, commitAudio);
    }
    private void speakAdvice(String text) {
        SessionState.status = "Lendo a dica";
        SessionState.detail = "A entrada de áudio pausa durante a leitura para evitar eco.";
        voice.speak(text);
    }
    private void handleAdvice(String json, long tokens) {
        policy.completed(); requestedAt = 0; SessionState.requestInFlight = false;
        SessionState.tokens += tokens; SessionState.replies++;
        try {
            Advice a = Advice.parse(json);
            try {
                NotebookStore notebook = NotebookStore.get(this);
                JSONObject summary = null;
                if (!a.context.isEmpty()) summary = notebook.addNode(notebookSession,"Contexto da conversa",a.context,"summary","ai");
                if (!a.text.isEmpty()) {
                    JSONObject suggestion = notebook.addNode(notebookSession,"Dica ao vivo",a.text,"recommendation","ai");
                    if (summary != null) notebook.link(summary.getString("id"),suggestion.getString("id"),"recomenda","Dica baseada no resumo deste momento.","ai","proposed");
                }
            } catch (Exception e) { finish("A resposta chegou, mas não pôde ser salva no caderno. Confira o espaço livre."); return; }
            SessionState.context = a.context.isEmpty() ? "A API respondeu sem um resumo. Tente explicar a situação e analisar novamente." : a.context;
            SessionState.memory = a.memory;
            SessionState.advice = a.text.isEmpty() ? "Contexto analisado. Nenhuma nova dica sugerida desta vez." : a.text;
            SessionState.status = "Análise recebida";
            SessionState.detail = "Veja o resumo abaixo. A escuta continua.";
            if (a.speak) {
                SessionState.suggestions++;
                long now = SystemClock.elapsedRealtime();
                if (policy.quietForVoice(now) && !voice.isBusy() && voice.routeIsValid()) speakAdvice(a.text);
                else {
                    pendingVoice = a.text; pendingVoiceAt = now;
                    SessionState.detail = "Dica na tela. A leitura aguarda uma pausa na conversa.";
                }
            }
        } catch (Exception e) {
            SessionState.status = "Resposta fora do formato";
            SessionState.context = "A API respondeu, mas o formato não pôde ser interpretado.";
            SessionState.advice = "Toque em Analisar agora para tentar novamente.";
            SessionState.detail = "Nenhuma dica foi lida ou salva a partir dessa resposta.";
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
        pendingVoice = ""; SessionState.requestInFlight = false;
        SessionState.active = false; SessionState.connected = false; SessionState.level = 0;
        SessionState.audioBytes = sentBytes; SessionState.hearing = "Microfone e API desligados.";
        SessionState.status = "Session ended"; SessionState.detail = reason; SessionState.changed();
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @Override public void onDestroy() { if (!stopping) finish("Session stopped. Microphone and connection are off."); super.onDestroy(); }
    @Override public void onTaskRemoved(Intent rootIntent) { finish("App closed. Microphone and connection are off."); }
}
