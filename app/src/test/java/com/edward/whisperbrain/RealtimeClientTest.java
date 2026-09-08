package com.edward.whisperbrain;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/** Local WebSocket fixtures exercise the public API flow. No OpenAI credentials or paid calls. */
public class RealtimeClientTest {
    private MockWebServer server;
    private RealtimeClient client;
    private final BlockingQueue<JSONObject> sent = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> answers = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> failures = new LinkedBlockingQueue<>();
    private static final String ANSWER = "{\"context\":\"Você está preparando uma reunião.\",\"speak\":false,\"advice\":\"\",\"memory\":\"\"}";

    private void connect(String commitBehavior) throws Exception {
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                try {
                    JSONObject event = new JSONObject(text); sent.add(event);
                    switch (event.getString("type")) {
                        case "session.update": ws.send("{\"type\":\"session.updated\"}"); break;
                        case "input_audio_buffer.commit":
                            if (commitBehavior.equals("normal")) {
                                ws.send("{\"type\":\"input_audio_buffer.committed\"}");
                            } else {
                                JSONObject error = new JSONObject().put("code", "input_audio_buffer_commit_empty")
                                        .put("event_id", commitBehavior.equals("race") ? event.getString("event_id") : "unrelated-event");
                                ws.send(new JSONObject().put("type", "error").put("error", error).toString());
                            }
                            break;
                        case "response.create":
                            ws.send("{\"type\":\"response.created\"}");
                            JSONObject part = new JSONObject().put("type", "output_text").put("text", ANSWER);
                            JSONObject item = new JSONObject().put("type", "message").put("role", "assistant")
                                    .put("content", new JSONArray().put(part));
                            JSONObject response = new JSONObject().put("status", "completed").put("output", new JSONArray().put(item));
                            ws.send(new JSONObject().put("type", "response.done").put("response", response).toString());
                            break;
                        default: break;
                    }
                } catch (Exception e) { failures.add("Fixture failed: " + e.getClass().getSimpleName()); }
            }
        }));
        server.start();
        BlockingQueue<Boolean> ready = new LinkedBlockingQueue<>();
        client = new RealtimeClient(new RealtimeClient.Listener() {
            @Override public void configured() { ready.add(true); }
            @Override public void speechStarted() {}
            @Override public void speechStopped() {}
            @Override public void committed() {}
            @Override public void responseStarted() {}
            @Override public void answer(String json, long tokens) { answers.add(json); }
            @Override public void failed(String message) { failures.add(message); }
        }, server.url("/v1/realtime").toString());
        client.connect("sk-local-fixture-only", "fixture-model", "Prepare a meeting", "[]", "pt-BR");
        assertEquals(Boolean.TRUE, ready.poll(5, TimeUnit.SECONDS));
    }
    @After public void close() throws Exception {
        if (client != null) client.close();
        if (server != null) server.shutdown();
    }
    @Test public void sessionRequestsTextAndPcmWithoutAutomaticServerResponses() throws Exception {
        connect("normal");
        JSONObject session = sent.take().getJSONObject("session");
        assertEquals("text", session.getJSONArray("output_modalities").getString(0));
        JSONObject input = session.getJSONObject("audio").getJSONObject("input");
        assertEquals(24000, input.getJSONObject("format").getInt("rate"));
        assertFalse(input.getJSONObject("turn_detection").getBoolean("create_response"));
        assertFalse(input.getJSONObject("turn_detection").getBoolean("interrupt_response"));
    }
    @Test public void manualAnalysisWorksWithoutVadAndWithoutTextDeltaEvents() throws Exception {
        connect("normal"); sent.take();
        assertTrue(client.audio(new byte[4800], 4800));
        client.advise(true, true);
        assertEquals(ANSWER, answers.poll(5, TimeUnit.SECONDS));
        assertEquals("input_audio_buffer.append", sent.take().getString("type"));
        assertEquals("input_audio_buffer.commit", sent.take().getString("type"));
        assertEquals("response.create", sent.take().getString("type"));
        assertTrue(failures.isEmpty());
    }
    @Test public void aVadCommitRaceDoesNotEndTheSession() throws Exception {
        connect("race");
        client.audio(new byte[4800], 4800); client.advise(true, true);
        assertEquals(ANSWER, answers.poll(5, TimeUnit.SECONDS));
        assertTrue(failures.isEmpty());
    }
    @Test public void anUnrelatedEmptyBufferErrorIsNotSwallowed() throws Exception {
        connect("unrelated");
        client.audio(new byte[4800], 4800); client.advise(true, true);
        assertNotNull(failures.poll(5, TimeUnit.SECONDS));
        assertTrue(answers.isEmpty());
    }
    @Test public void billingErrorsAreActionableWithoutEchoingPrivateRequestData() throws Exception {
        JSONObject error = new JSONObject().put("code", "insufficient_quota").put("message", "sk-private-do-not-copy heard-private-speech");
        String message = RealtimeClient.apiError(error);
        assertTrue(message.contains("saldo"));
        assertFalse(message.contains("sk-private")); assertFalse(message.contains("heard-private"));
    }
    @Test public void finalTextUsesAssistantMessageContentOnly() throws Exception {
        JSONObject output = new JSONObject("{\"output\":[{\"type\":\"function_call\",\"text\":\"tool data\"},{\"type\":\"message\",\"role\":\"user\",\"content\":[{\"type\":\"output_text\",\"text\":\"user data\"}]}]}");
        assertEquals("delta fallback", RealtimeClient.responseText(output, "delta fallback"));
    }
}
