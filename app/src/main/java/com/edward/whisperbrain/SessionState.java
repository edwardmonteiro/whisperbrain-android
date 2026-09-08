package com.edward.whisperbrain;

/** Main-thread UI state. Current conversation state is deliberately not persisted. */
public final class SessionState {
    private SessionState() {}
    public static boolean active, connected, requestInFlight, savingAudio;
    public static String sessionId = "";
    public static String status = "Pronto para começar", detail = "Teste o áudio e depois inicie a escuta.";
    public static String context = "O resumo do que a IA entendeu aparecerá aqui.";
    public static String advice = "A useful question. A clearer next step. Only when it helps.";
    public static String memory = "", route = "", input = "";
    public static String hearing = "Microfone e API desligados.";
    public static long started, tokens, audioBytes;
    public static int level, suggestions, speechEvents, turns, requests, replies;
    public static Runnable observer;
    public static void changed() { if (observer != null) observer.run(); }
    public static void reset() {
        connected = false; context = "Aguardando fala para analisar…"; advice = "Aguardando uma oportunidade de ajudar.";
        memory = ""; route = ""; input = ""; tokens = 0; audioBytes = 0; level = 0; suggestions = 0;
        speechEvents = 0; turns = 0; requests = 0; replies = 0; requestInFlight = false;
        hearing = "Preparando a conexão; microfone ainda desligado.";
    }
    public static String diagnostics() {
        return "WhisperBrain 0.4.0-alpha\nStatus: " + status + "\nDetalhe: " + detail
                + "\nEscuta: " + hearing + "\nConectado: " + connected + "\nMicrofone: " + input
                + "\nSaída: " + route + "\nNível: " + level + "%\nBytes de áudio enviados: " + audioBytes
                + "\nFalas detectadas: " + speechEvents + "\nTrechos recebidos: " + turns
                + "\nAnálises pedidas: " + requests + "\nRespostas: " + replies;
    }
}
