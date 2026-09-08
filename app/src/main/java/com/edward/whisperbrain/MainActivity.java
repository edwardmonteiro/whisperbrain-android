package com.edward.whisperbrain;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONArray;

public final class MainActivity extends Activity {
    private final int BG = Color.rgb(11, 16, 24), SURFACE = Color.rgb(23, 32, 45);
    private final int INK = Color.rgb(240, 247, 252), MUTED = Color.rgb(172, 189, 209);
    private final int ACCENT = Color.rgb(113, 241, 203);
    private Vault vault;
    private TextView status, detail, meta, context, advice, hearing;
    private Button start, nudge, connection, test, saveNote, memory;
    private EditText goal;
    private Switch automatic;
    private Switch saveAudio, saveTranscript;
    private String notebookSession = "";
    private ProgressBar meter;
    private PrivateVoice testVoice;
    private boolean testing;
    private int pendingAction;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setDecorFitsSystemWindows(false);
        vault = new Vault(this);
        notebookSession = SessionState.active ? SessionState.sessionId : getIntent().getStringExtra("session");
        if (notebookSession == null) notebookSession = "";
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root = column(); root.setPadding(dp(22), dp(22), dp(22), dp(30));
        scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        root.addView(label("WHISPERBRAIN", 16, ACCENT, true));
        root.addView(label("Escuta ao vivo", 30, INK, true));
        root.addView(label("Android pessoal · 0.2.0-alpha", 14, MUTED, false));
        if (!notebookSession.isEmpty()) try { root.addView(label("# " + NotebookStore.get(this).session(notebookSession).getString("event"),18,ACCENT,true)); } catch (Exception ignored) {}
        Button notebook = button("← Voltar ao caderno", SURFACE, INK); root.addView(notebook);
        notebook.setOnClickListener(v -> { startActivity(new Intent(this,NotebookActivity.class).putExtra("session",SessionState.active?SessionState.sessionId:notebookSession).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); });
        gap(root, 22);

        LinearLayout live = card(root);
        status = label(SessionState.status, 24, INK, true); live.addView(status);
        detail = label(SessionState.detail, 16, MUTED, false); live.addView(detail);
        hearing = label(SessionState.hearing, 16, ACCENT, false); live.addView(hearing);
        context = label(SessionState.context, 16, INK, false); live.addView(context);
        gap(live, 12);
        meter = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        meter.setMax(100); meter.setProgressTintList(ColorStateList.valueOf(ACCENT));
        meter.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(44, 61, 79)));
        meter.setContentDescription("Microphone level");
        live.addView(meter, new LinearLayout.LayoutParams(-1, dp(8)));
        meta = label("", 14, MUTED, false); live.addView(meta);
        start = button("Iniciar escuta", ACCENT, BG); live.addView(start);
        start.setOnClickListener(v -> {
            if (SessionState.active) {
                startService(new Intent(this, BrainService.class).setAction(BrainService.STOP));
            } else {
                try {
                    if (vault.get("api_key", "").isEmpty()) { settings(); return; }
                    vault.put("goal", goal.getText().toString().trim());
                    vault.put("automatic", String.valueOf(automatic.isChecked()));
                    vault.put("save_session_audio", String.valueOf(saveAudio.isChecked()));
                    vault.put("save_transcript", String.valueOf(saveTranscript.isChecked()));
                    permissions(1);
                } catch (Exception e) { toast("Could not read or save settings. Your session has not started."); }
            }
        });
        nudge = button("Analisar agora", Color.rgb(40, 54, 73), INK); live.addView(nudge);
        nudge.setOnClickListener(v -> startService(new Intent(this, BrainService.class).setAction(BrainService.ASK)));
        live.addView(label("Fale uma frase e pause por 3 segundos. Analisar agora também funciona sem esperar o detector de pausa.", 14, MUTED, false));
        live.addView(label("Nesta escuta, áudio e notas de contexto vão à OpenAI. Resumos e dicas ficam salvos na conversa local. Use com a concordância dos participantes.", 14, MUTED, false));
        saveTranscript = new Switch(this); saveTranscript.setText("Salvar transcrições na conversa"); saveTranscript.setTextColor(INK); saveTranscript.setChecked(Boolean.parseBoolean(read("save_transcript","true")));live.addView(saveTranscript);
        saveAudio = new Switch(this); saveAudio.setText("Guardar também o áudio local");saveAudio.setTextColor(INK);saveAudio.setChecked(Boolean.parseBoolean(read("save_session_audio","false")));live.addView(saveAudio);
        live.addView(label("Transcrição usa a API e pode conter erros. Áudio local ocupa cerca de 2,9 MB por minuto; só é guardado quando ativado.",14,MUTED,false));
        gap(root, 18);

        LinearLayout next = card(root);
        next.addView(label("SUA PRÓXIMA DICA", 14, ACCENT, true));
        advice = label(SessionState.advice, 22, INK, true); next.addView(advice);
        gap(next, 12);
        saveNote = button("Review a memory", Color.rgb(40, 54, 73), INK); next.addView(saveNote);
        saveNote.setOnClickListener(v -> editNote(SessionState.memory));
        gap(root, 18);

        LinearLayout intention = card(root);
        intention.addView(label("What matters in this conversation?", 18, INK, true));
        goal = field("Help me ask a better question. Notice unclear assumptions.", false, 500);
        goal.setMinLines(2); goal.setMaxLines(4);
        goal.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        goal.setText(read("goal", "Help me think clearly and ask better questions."));
        intention.addView(goal);
        automatic = new Switch(this); automatic.setText("Dicas automáticas"); automatic.setTextSize(16);
        automatic.setTextColor(INK); automatic.setPadding(0, dp(12), 0, dp(12));
        automatic.setChecked(Boolean.parseBoolean(read("automatic", "true"))); intention.addView(automatic);
        intention.addView(label("Analisa nas pausas ou após 20 segundos de fala contínua detectada. A voz aguarda uma pausa. Você também pode analisar manualmente.", 14, MUTED, false));
        gap(root, 16);
        test = button("Testar áudio privado", SURFACE, INK); root.addView(test);
        test.setOnClickListener(v -> permissions(2));
        root.addView(label("Use calling-capable earbuds, or hold the phone’s earpiece to your ear. Start at a comfortable volume.", 14, MUTED, false));
        connection = button("Configurações de conexão e voz", SURFACE, INK); root.addView(connection);
        connection.setOnClickListener(v -> settings());
        Button diagnostic = button("Copiar diagnóstico", SURFACE, INK); root.addView(diagnostic);
        diagnostic.setOnClickListener(v -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("WhisperBrain", SessionState.diagnostics()));
            toast("Diagnóstico copiado, sem chave, conversa ou memórias.");
        });
        memory = button("Your approved memories", SURFACE, INK); root.addView(memory);
        memory.setOnClickListener(v -> memories());
        root.addView(label("Only notes you approve are saved. Live audio is not recorded to a file by this app. API usage is billed separately from ChatGPT.", 14, MUTED, false));
        setContentView(scroll);
        render();
        if (getIntent().getBooleanExtra("settings",false)) root.post(this::settings);
    }
    @Override public void onResume() { super.onResume(); SessionState.observer = this::render; render(); }
    @Override public void onPause() {
        SessionState.observer = null;
        if (testVoice != null) { testVoice.shutdown(); testVoice = null; testing = false; }
        if (!SessionState.active) {
            try { vault.put("goal", goal.getText().toString()); vault.put("automatic", String.valueOf(automatic.isChecked())); }
            catch (Exception ignored) {}
        }
        super.onPause();
    }
    private void render() {
        if (status == null) return;
        status.setText(SessionState.status); detail.setText(SessionState.detail); hearing.setText(SessionState.hearing);
        context.setText(SessionState.context); advice.setText(SessionState.advice); meter.setProgress(SessionState.level);
        String info = SessionState.route;
        if (!SessionState.input.isEmpty()) info += "\n" + SessionState.input;
        if (SessionState.active) {
            long seconds = (SystemClock.elapsedRealtime() - SessionState.started) / 1000;
            info += String.format(Locale.US, "\n%02d:%02d · %d dicas\n%.1f MB de áudio enviado · %d tokens informados",
                    seconds / 60, seconds % 60, SessionState.suggestions,
                    SessionState.audioBytes / 1_000_000.0, SessionState.tokens);
        }
        if (SessionState.active || SessionState.requests > 0 || SessionState.audioBytes > 0)
            info += String.format(Locale.US, "\nFalas detectadas: %d · trechos: %d\nAnálises pedidas: %d · respostas: %d",
                    SessionState.speechEvents, SessionState.turns, SessionState.requests, SessionState.replies);
        meta.setText(info.trim());
        start.setText(SessionState.active ? "Parar escuta" : "Iniciar escuta");
        start.setBackground(round(SessionState.active ? Color.rgb(255, 167, 159) : ACCENT, 14));
        start.setEnabled(!testing && (!SessionState.savingAudio || SessionState.active)); nudge.setEnabled(SessionState.connected && !testing && !SessionState.requestInFlight);
        nudge.setText(SessionState.requestInFlight ? "Analisando…" : "Analisar agora");
        connection.setEnabled(!SessionState.active && !testing); test.setEnabled(!SessionState.active && !testing);
        goal.setEnabled(!SessionState.active && !testing); automatic.setEnabled(!SessionState.active && !testing);
        saveAudio.setEnabled(!SessionState.active && !testing); saveTranscript.setEnabled(!SessionState.active && !testing);
        saveNote.setEnabled(!SessionState.memory.isEmpty());
    }
    private void permissions(int action) {
        pendingAction = action;
        ArrayList<String> needed = new ArrayList<>();
        if (action == 1 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECORD_AUDIO);
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (action == 1 && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), 71);
        else afterPermissions();
    }
    @Override public void onRequestPermissionsResult(int code, String[] names, int[] results) {
        super.onRequestPermissionsResult(code, names, results);
        if (code == 71) afterPermissions();
    }
    private void afterPermissions() {
        int action = pendingAction; pendingAction = 0;
        if (action == 1) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                toast("Allow microphone access to start listening."); return;
            }
            try { startForegroundService(new Intent(this, BrainService.class).setAction(BrainService.START).putExtra("session",notebookSession)); }
            catch (Exception e) { toast("Android could not start the session. Keep the app open and try again."); }
        } else if (action == 2) testAudio();
    }
    private void testAudio() {
        if (SessionState.active || testing) return;
        testing = true; SessionState.status = "Testing private audio";
        SessionState.detail = "This test uses Android’s offline voice. It does not use the microphone or AI API."; render();
        String language = read("language", "pt-BR");
        testVoice = new PrivateVoice(this, new PrivateVoice.Listener() {
            @Override public void ready(String route) {
                if (!testing || testVoice == null) return;
                SessionState.route = route; render();
                testVoice.speak(language.startsWith("pt")
                        ? "Este é seu teste de áudio privado. Mantenha o volume confortável."
                        : "This is your private audio test. Keep the volume comfortable.");
            }
            @Override public void finished() {
                if (testVoice != null) { testVoice.shutdown(); testVoice = null; }
                testing = false; SessionState.status = "Audio test finished";
                SessionState.detail = "If you heard the voice privately, you’re ready to start."; render();
            }
            @Override public void failed(String message) {
                testVoice = null; testing = false; SessionState.status = "Check your audio";
                SessionState.detail = message; render();
            }
        });
        testVoice.start(language, Boolean.parseBoolean(read("headphones_only", "false")));
    }
    private void settings() {
        if (SessionState.active) { toast("Stop the session before changing its settings."); return; }
        LinearLayout content = column(); content.setPadding(dp(22), dp(8), dp(22), dp(12));
        content.addView(label("Your API key", 16, INK, true));
        EditText key = field(read("api_key", "").isEmpty() ? "Paste your OpenAI API key here" : "A key is saved. Leave blank to keep it.", true, 600);
        content.addView(key);
        content.addView(label("Personal prototype: your own key is encrypted on this phone. Never paste it into GitHub or chat.", 14, MUTED, false));
        content.addView(label("Realtime model", 16, INK, true));
        EditText model = field("gpt-realtime-2.1-mini", false, 100); model.setText(read("model", "gpt-realtime-2.1-mini")); content.addView(model);
        content.addView(label("Modelo para texto e sinapses",16,INK,true));
        EditText textModel=field("gpt-4.1-mini",false,100);textModel.setText(read("text_model","gpt-4.1-mini"));content.addView(textModel);
        content.addView(label("Advice language", 16, INK, true));
        Spinner language = select(new String[]{"English (US)", "Português (Brasil)"});
        language.setSelection(read("language", "pt-BR").startsWith("pt") ? 1 : 0); content.addView(language);
        content.addView(label("Stop automatically after", 16, INK, true));
        Spinner minutes = select(new String[]{"10 minutes", "20 minutes", "45 minutes"});
        String stored = read("minutes", "10"); minutes.setSelection(stored.equals("45") ? 2 : stored.equals("20") ? 1 : 0); content.addView(minutes);
        Switch headphones = new Switch(this); headphones.setText("Require earbuds"); headphones.setTextColor(INK);
        headphones.setTextSize(16); headphones.setChecked(Boolean.parseBoolean(read("headphones_only", "false"))); content.addView(headphones);
        content.addView(label("When off, the phone earpiece is allowed. The app never deliberately selects the loudspeaker.", 14, MUTED, false));
        Button voices = button("Install Android voice data", SURFACE, INK); content.addView(voices);
        voices.setOnClickListener(v -> {
            try { startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)); }
            catch (Exception e) { toast("Open Android Settings and search for Text-to-speech output, then install voice data."); }
        });
        Button clear = button("Remove saved API key", SURFACE, INK); content.addView(clear);
        clear.setOnClickListener(v -> { vault.forgetKey(); key.setText(""); key.setHint("Paste your OpenAI API key here"); toast("Saved API key removed."); });
        content.addView(label("Live audio uses paid API credits. Choose a model available to your API project. No API key is needed to compile this app.", 14, MUTED, false));
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Connection & voice")
                .setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newKey = key.getText().toString().trim(), newModel = model.getText().toString().trim(), newTextModel=textModel.getText().toString().trim();
            if (!newKey.isEmpty() && (!(newKey.startsWith("sk-") || newKey.startsWith("ek_")) || newKey.matches("(?s).*\\s.*"))) {
                key.setError("Use your OpenAI API key or a valid Realtime client secret."); return;
            }
            if (!newModel.matches("[A-Za-z0-9._-]{1,100}")) { model.setError("Use the exact model ID."); return; }
            if (!newTextModel.matches("[A-Za-z0-9._-]{1,100}")) {textModel.setError("Confira o modelo de texto.");return;}
            try {
                if (!newKey.isEmpty()) vault.put("api_key", newKey);
                vault.put("model", newModel); vault.put("language", language.getSelectedItemPosition() == 1 ? "pt-BR" : "en-US");
                vault.put("text_model",newTextModel);
                vault.put("minutes", new String[]{"10", "20", "45"}[minutes.getSelectedItemPosition()]);
                vault.put("headphones_only", String.valueOf(headphones.isChecked()));
                key.setText(""); dialog.dismiss(); toast("Settings saved. Test private audio before listening.");
            } catch (Exception e) { toast("Encrypted settings could not be saved."); }
        }));
        dialog.show();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    private void memories() {
        try {
            JSONArray notes = vault.memories(); String[] choices = new String[notes.length()];
            for (int i = 0; i < notes.length(); i++) choices[i] = notes.getString(i);
            new AlertDialog.Builder(this).setTitle("Approved memories · " + choices.length + "/20")
                    .setItems(choices, (d, index) -> new AlertDialog.Builder(this).setTitle("Saved note")
                            .setMessage(choices[index] + "\n\nApproved notes are sent to the AI when you start a session.")
                            .setNegativeButton("Close", null).setPositiveButton("Delete", (dd, w) -> {
                                try { vault.deleteMemory(index); toast("Note deleted. A running session keeps its existing context until stopped."); }
                                catch (Exception e) { toast("Could not delete the note."); }
                            }).show())
                    .setNeutralButton("Add a note", (d, w) -> editNote(""))
                    .setPositiveButton("Close", null).show();
        } catch (Exception e) { toast("Encrypted memories could not be read."); }
    }
    private void editNote(String draft) {
        EditText text = field("One fact or preference you want to remember", false, 400);
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        text.setMinLines(3); text.setText(draft);
        LinearLayout body = column(); body.setPadding(dp(22), 0, dp(22), dp(12));
        body.addView(label("Review this note before saving. Approved notes are used from the next session onward.", 16, MUTED, false)); body.addView(text);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Approve a memory")
                .setView(body).setNegativeButton("Cancel", null).setPositiveButton("Save note", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { vault.remember(text.getText().toString()); dialog.dismiss(); toast("Memory approved and saved."); }
            catch (Exception e) { toast("Use 1–400 characters. Keep at most 20 approved notes."); }
        })); dialog.show();
    }
    private String read(String name, String fallback) {
        try { return vault.get(name, fallback); } catch (Exception e) { return fallback; }
    }
    private Spinner select(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter);
        spinner.setMinimumHeight(dp(52)); return spinner;
    }
    private EditText field(String hint, boolean secret, int limit) {
        EditText field = new EditText(this); field.setHint(hint); field.setTextSize(16);
        field.setTextColor(INK); field.setHintTextColor(MUTED); field.setPadding(dp(10), dp(12), dp(10), dp(12));
        field.setMinHeight(dp(52)); field.setSingleLine(true);
        field.setInputType(secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        field.setSaveEnabled(!secret); return field;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = column(); card.setPadding(dp(18), dp(18), dp(18), dp(18)); card.setBackground(round(SURFACE, 20));
        parent.addView(card, new LinearLayout.LayoutParams(-1, -2)); return card;
    }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color);
        v.setPadding(0, dp(5), 0, dp(6)); v.setLineSpacing(dp(3), 1f);
        if (bold) v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); return v;
    }
    private Button button(String text, int fill, int ink) {
        Button b = new Button(this); b.setText(text); b.setTextSize(16); b.setAllCaps(false); b.setTextColor(ink);
        b.setMinHeight(dp(54)); b.setPadding(dp(12), dp(12), dp(12), dp(12)); b.setBackground(round(fill, 14));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(10), 0, dp(4)); b.setLayoutParams(p); return b;
    }
    private GradientDrawable round(int fill, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); return d;
    }
    private void gap(LinearLayout l, int height) { l.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private int dp(int value) { return (int) (getResources().getDisplayMetrics().density * value + 0.5f); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
