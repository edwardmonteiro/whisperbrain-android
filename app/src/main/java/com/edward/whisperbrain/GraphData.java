package com.edward.whisperbrain;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Portable graph rules. IDs, provenance and referential integrity never come from model trust. */
public final class GraphData {
    private GraphData() {}
    public static final int VERSION = 1;
    public static String id() { return UUID.randomUUID().toString(); }
    public static String text(String value, int max) { return value == null ? "" : value.trim().substring(0, Math.min(value.trim().length(), max)); }
    public static String required(String value, int max) {
        String s = text(value, max); if (s.isEmpty()) throw new IllegalArgumentException("Preencha o texto."); return s;
    }
    public static JSONArray filterEdges(JSONArray nodes, JSONArray edges) throws Exception {
        Set<String> ids = new HashSet<>(); for (int i=0;i<nodes.length();i++) ids.add(nodes.getJSONObject(i).getString("id"));
        JSONArray result = new JSONArray();
        for (int i=0;i<edges.length();i++) {
            JSONObject edge=edges.getJSONObject(i);
            if(ids.contains(edge.getString("from")) && ids.contains(edge.getString("to"))) result.put(edge);
        }
        return result;
    }
    public static void validate(JSONObject backup) throws Exception {
        if (backup.getInt("version") != VERSION) throw new IllegalArgumentException("Versão de backup não reconhecida.");
        JSONArray sessions=backup.getJSONArray("sessions"), nodes=backup.getJSONArray("nodes"), edges=backup.getJSONArray("edges");
        if(sessions.length()>5000 || nodes.length()>30000 || edges.length()>100000) throw new IllegalArgumentException("Backup excede o limite de importação.");
        Set<String> sid=new HashSet<>(), nid=new HashSet<>(), eid=new HashSet<>(), everyId=new HashSet<>();
        for(int i=0;i<sessions.length();i++) {
            JSONObject s=sessions.getJSONObject(i);
            if(!sid.add(required(s.getString("id"),100)) || !everyId.add(s.getString("id"))) throw new IllegalArgumentException("Sessão duplicada.");
            required(s.getString("event"),120); s.getLong("created");
        }
        Set<String> kinds=Set.of("note","transcript","audio","summary","recommendation");
        for(int i=0;i<nodes.length();i++) {
            JSONObject n=nodes.getJSONObject(i);
            if(!nid.add(required(n.getString("id"),100)) || !everyId.add(n.getString("id")) || !sid.contains(n.getString("session"))) throw new IllegalArgumentException("Neurônio sem sessão ou duplicado.");
            if(!kinds.contains(n.getString("kind"))) throw new IllegalArgumentException("Tipo de neurônio inválido.");
            if(n.getString("body").length()>20000 || n.getString("title").length()>160) throw new IllegalArgumentException("Nota grande demais.");
            n.getLong("created");
            String a=n.optString("attachment");
            if(n.getString("kind").equals("audio") && (a.isEmpty() || !Set.of("pcm24k","m4a").contains(n.optString("format"))))throw new IllegalArgumentException("Formato do áudio inválido.");
            if(!a.isEmpty() && !a.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Anexo inválido.");
            if(!Set.of("user","ai","asr","notification").contains(n.getString("origin"))) throw new IllegalArgumentException("Origem inválida.");
        }
        for(int i=0;i<edges.length();i++) {
            JSONObject e=edges.getJSONObject(i);
            if(!eid.add(e.getString("id")) || !everyId.add(e.getString("id")) || !nid.contains(e.getString("from")) || !nid.contains(e.getString("to")) || e.getString("from").equals(e.getString("to"))) throw new IllegalArgumentException("Sinapse inválida.");
            if(!Set.of("proposed","accepted").contains(e.getString("state"))) throw new IllegalArgumentException("Estado inválido.");
            required(e.getString("relation"),80); e.getLong("created");
            if(e.optString("reason").length()>2000) throw new IllegalArgumentException("Justificativa grande demais.");
        }
    }
    public static JSONObject parseSuggestion(String raw, Set<String> allowedIds, String focusId) throws Exception {
        JSONObject in=new JSONObject(raw), out=new JSONObject();
        out.put("title",required(in.getString("title"),120));
        out.put("recommendation",required(in.getString("recommendation"),3000));
        JSONArray clean=new JSONArray(), links=in.getJSONArray("connections"); Set<String> seen=new HashSet<>();
        for(int i=0;i<Math.min(links.length(),8);i++) {
            JSONObject link=links.getJSONObject(i); String target=link.getString("target_id");
            if(!allowedIds.contains(target) || target.equals(focusId) || !seen.add(target)) continue;
            clean.put(new JSONObject().put("target_id",target).put("relation",required(link.getString("relation"),80)).put("reason",required(link.getString("reason"),600)));
        }
        String whisper=text(in.optString("whisper"),220);
        if(whisper.isEmpty() || whisper.split("\\s+").length>24)whisper="Há uma recomendação nova no seu caderno. Veja quando puder.";
        return out.put("whisper",whisper).put("connections",clean);
    }
    public static String markdown(JSONObject session, JSONArray nodes, JSONArray edges) throws Exception {
        StringBuilder out=new StringBuilder("# "+session.getString("event")+"\n\nData: "+new Date(session.getLong("created"))+"\n\n");
        for(int i=0;i<nodes.length();i++) {
            JSONObject n=nodes.getJSONObject(i);out.append("## ").append(n.getString("title")).append("\n\n")
                .append("ID: ").append(n.getString("id")).append(" · Origem: ").append(n.getString("origin")).append("\n\n")
                .append(n.getString("body")).append("\n\n");
        }
        out.append("## Sinapses\n\n");
        for(int i=0;i<edges.length();i++) {JSONObject e=edges.getJSONObject(i);out.append("- [[").append(e.getString("from")).append("]] → [[").append(e.getString("to")).append("]] — ").append(e.getString("relation")).append(" (").append(e.getString("state")).append("): ").append(e.optString("reason")).append("\n");}
        return out.toString();
    }
}
