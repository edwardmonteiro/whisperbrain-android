package com.edward.whisperbrain;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioRouting;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.io.File;
import java.util.Locale;
import java.util.Set;

/** Main-thread controller. Synthesizes locally, then verifies a private playback route. */
public final class PrivateVoice {
    public interface Listener {
        void ready(String route);
        void finished();
        void failed(String message);
    }
    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AudioManager manager;
    private final AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private AudioDeviceInfo selected;
    private AudioFocusRequest focus;
    private AudioDeviceCallback devices;
    private AudioManager.OnCommunicationDeviceChangedListener routing;
    private TextToSpeech tts;
    private MediaPlayer player;
    private File wave;
    private String utterance = "";
    private boolean closed, routeReady, voiceReady, readySent, focusGranted;
    private volatile boolean busy, playing;

    public PrivateVoice(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
        manager = this.context.getSystemService(AudioManager.class);
    }
    public boolean isBusy() { return busy; }
    public boolean isPlaying() { return playing; }
    public boolean routeIsValid() {
        if (closed || selected == null) return false;
        AudioDeviceInfo actual = manager.getCommunicationDevice();
        return actual != null && actual.getId() == selected.getId() && isPrivate(actual);
    }
    private static boolean isHeadset(AudioDeviceInfo d) {
        int t = d.getType();
        return t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || t == AudioDeviceInfo.TYPE_BLE_HEADSET
                || t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || t == AudioDeviceInfo.TYPE_USB_HEADSET;
    }
    private static boolean isPrivate(AudioDeviceInfo d) {
        return isHeadset(d) || d.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE;
    }
    public void start(String language, boolean headphonesOnly) {
        try {
            for (AudioDeviceInfo d : manager.getAvailableCommunicationDevices()) {
                if (isHeadset(d)) { selected = d; break; }
            }
            if (selected == null && !headphonesOnly) {
                for (AudioDeviceInfo d : manager.getAvailableCommunicationDevices()) {
                    if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) { selected = d; break; }
                }
            }
            if (selected == null) { fail("Connect calling-capable earbuds, or allow the phone earpiece in settings."); return; }
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes).setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(change -> {
                        if (change < 0) fail("Audio was interrupted. Your session has stopped.");
                    }, main).build();
            focusGranted = manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            if (!focusGranted) { fail("Audio is in use. Finish your call or other audio session first."); return; }
            manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            devices = new AudioDeviceCallback() {
                @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
                    for (AudioDeviceInfo d : removed) if (selected != null && d.getId() == selected.getId()) {
                        fail("Your audio device disconnected. Your session has stopped."); return;
                    }
                }
            };
            manager.registerAudioDeviceCallback(devices, main);
            routing = actual -> {
                if (routeReady && !closed && !routeIsValid()) fail("Audio route changed. Your session has stopped.");
            };
            manager.addOnCommunicationDeviceChangedListener(context.getMainExecutor(), routing);
            if (!manager.setCommunicationDevice(selected)) { fail("Android could not select that private audio route."); return; }
            main.postDelayed(() -> {
                if (closed) return;
                if (!routeIsValid()) { fail("Private audio did not become ready. Try reconnecting your earbuds."); return; }
                routeReady = true; maybeReady();
            }, 1800);
            // The selected offline voice prevents advice text being forwarded to a cloud TTS engine.
            tts = new TextToSpeech(context, status -> main.post(() -> initVoice(status, language)));
            main.postDelayed(() -> {
                if (!closed && !readySent) fail("Voice setup timed out. Install an offline text-to-speech voice in Android settings.");
            }, 10_000);
        } catch (Exception e) { fail("Private audio setup failed. Check Nearby devices and audio permissions."); }
    }
    private void initVoice(int status, String language) {
        if (closed) return;
        if (status != TextToSpeech.SUCCESS || tts == null) { fail("Android text-to-speech is unavailable."); return; }
        Locale locale = Locale.forLanguageTag(language);
        try {
            tts.setLanguage(locale);
            Voice choice = null;
            Set<Voice> voices = tts.getVoices();
            if (voices != null) for (Voice voice : voices) {
                if (!voice.isNetworkConnectionRequired() && voice.getLocale().getLanguage().equals(locale.getLanguage())) {
                    if (choice == null || voice.getLocale().getCountry().equals(locale.getCountry())) choice = voice;
                }
            }
            if (choice == null || tts.setVoice(choice) != TextToSpeech.SUCCESS) {
                fail("Install an offline " + locale.getDisplayLanguage() + " voice in Android text-to-speech settings."); return;
            }
            tts.setSpeechRate(0.92f);
            tts.setAudioAttributes(attributes);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) {
                    main.post(() -> { if (!closed && id.equals(utterance)) playWave(); });
                }
                @Override public void onError(String id) {
                    main.post(() -> { if (!closed && id.equals(utterance)) fail("The offline voice could not speak. Check its downloaded voice data."); });
                }
            });
            voiceReady = true; maybeReady();
        } catch (Exception e) { fail("Voice setup failed. Check your offline voice settings."); }
    }
    private void maybeReady() {
        if (closed || readySent || !voiceReady || !routeReady) return;
        readySent = true;
        listener.ready(selected.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                ? "Phone earpiece · hold your phone to your ear" : "Earbuds · " + selected.getProductName());
    }
    public void speak(String text) {
        if (closed || !readySent) return;
        if (!routeIsValid()) { fail("Private audio route is unavailable."); return; }
        cancel();
        try {
            busy = true;
            wave = File.createTempFile("wb-voice-", ".wav", context.getCacheDir());
            utterance = "wb-" + System.nanoTime();
            Bundle params = new Bundle();
            if (tts.synthesizeToFile(text, params, wave, utterance) != TextToSpeech.SUCCESS) {
                fail("Voice synthesis failed."); return;
            }
            String id = utterance;
            main.postDelayed(() -> {
                if (!closed && busy && id.equals(utterance)) fail("Voice playback timed out.");
            }, 20_000);
        } catch (Exception e) { fail("Could not prepare private voice playback."); }
    }
    private void playWave() {
        if (!routeIsValid()) { fail("Audio route changed before playback."); return; }
        try {
            MediaPlayer current = new MediaPlayer(); player = current;
            current.setAudioAttributes(attributes);
            if (!current.setPreferredDevice(selected)) { fail("Android could not route this voice privately."); return; }
            current.setVolume(0f, 0f);
            current.setDataSource(wave.getAbsolutePath());
            current.addOnRoutingChangedListener((AudioRouting.OnRoutingChangedListener) router -> {
                if (closed || player != current || !playing) return;
                AudioDeviceInfo actual = current.getRoutedDevice();
                if (actual != null && actual.getId() != selected.getId()) {
                    current.setVolume(0f, 0f); fail("Playback route changed. Your session has stopped.");
                }
            }, main);
            current.setOnPreparedListener(p -> {
                if (closed || player != current) return;
                if (!routeIsValid()) { fail("Private audio changed before playback."); return; }
                playing = true; p.start(); verifyPlayback(current, 0);
            });
            current.setOnCompletionListener(p -> {
                if (player != current) return;
                cancel(); listener.finished();
            });
            current.setOnErrorListener((p, what, extra) -> { fail("Private voice playback failed."); return true; });
            current.prepareAsync();
        } catch (Exception e) { fail("Private voice playback could not start."); }
    }
    private void verifyPlayback(MediaPlayer current, int attempt) {
        main.postDelayed(() -> {
            if (closed || player != current || !playing) return;
            AudioDeviceInfo actual = current.getRoutedDevice();
            if (actual != null && actual.getId() == selected.getId() && routeIsValid()) {
                // Rewind the muted verification interval so the first word is not lost.
                current.pause();
                current.setOnSeekCompleteListener(p -> {
                    if (closed || player != current) return;
                    if (!routeIsValid()) { fail("Private audio changed before speech."); return; }
                    p.setVolume(0.22f, 0.22f); p.start();
                });
                current.seekTo(0);
            } else if (actual == null && attempt < 8) {
                verifyPlayback(current, attempt + 1);
            } else fail("The actual playback route could not be verified. Audio remains muted.");
        }, 80);
    }
    public void cancel() {
        utterance = ""; busy = false; playing = false;
        if (tts != null) tts.stop();
        if (player != null) {
            MediaPlayer old = player; player = null;
            try { old.setVolume(0f, 0f); old.release(); } catch (Exception ignored) {}
        }
        if (wave != null) { wave.delete(); wave = null; }
    }
    private void fail(String message) {
        if (closed) return;
        shutdown(); listener.failed(message);
    }
    public void shutdown() {
        if (closed) return;
        closed = true; cancel(); main.removeCallbacksAndMessages(null);
        if (tts != null) { tts.shutdown(); tts = null; }
        try {
            if (routing != null) manager.removeOnCommunicationDeviceChangedListener(routing);
            if (devices != null) manager.unregisterAudioDeviceCallback(devices);
            manager.clearCommunicationDevice();
            if (manager.getMode() == AudioManager.MODE_IN_COMMUNICATION) manager.setMode(AudioManager.MODE_NORMAL);
            if (focusGranted && focus != null) manager.abandonAudioFocusRequest(focus);
        } catch (Exception ignored) {}
    }
}
