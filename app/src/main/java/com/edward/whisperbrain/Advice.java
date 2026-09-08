package com.edward.whisperbrain;

import org.json.JSONObject;

public final class Advice {
    public final String context, text, memory;
    public final boolean speak;
    private Advice(String context, String text, String memory, boolean speak) {
        this.context = context; this.text = text; this.memory = memory; this.speak = speak;
    }
    public static Advice parse(String raw) throws Exception {
        JSONObject j = new JSONObject(raw.trim());
        String context = bounded(j.optString("context", ""), 320);
        String text = bounded(j.optString("advice", ""), 240);
        String memory = bounded(j.optString("memory", ""), 400);
        boolean speak = j.optBoolean("speak", false) && !text.isEmpty();
        // Do not read a long or malformed response aloud.
        if (text.split("\\s+").length > 22) speak = false;
        return new Advice(context, text, memory, speak);
    }
    private static String bounded(String s, int max) {
        String clean = s.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
