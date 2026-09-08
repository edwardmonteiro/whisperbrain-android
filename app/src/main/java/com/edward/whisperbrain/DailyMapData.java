package com.edward.whisperbrain;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Portable rules for an evidence-backed map of received notifications, never inferred replies. */
public final class DailyMapData {
    public static final int MAX_MESSAGES=120, MAX_BODY=1400, MAX_CHARS=60000, MAX_TOPICS=8, MAX_LINKS=8;
    private DailyMapData() {}
    public static String key(String day,String zone){return LocalDate.parse(day)+"@"+ZoneId.of(zone).getId();}
    public static long start(String day,String zone){return LocalDate.parse(day).atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli();}
    public static long end(String day,String zone){return LocalDate.parse(day).plusDays(1).atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli();}
    public static boolean eligible(JSONObject n){return "notification".equals(n.optString("origin")) && WhatsAppNotice.allowed(n.optString("source_app"),true)
            && n.optBoolean("notification_only") && !n.optBoolean("edited_by_user") && !n.optString("body").trim().isEmpty();}
    public static JSONArray day(JSONArray nodes,String day,String zone)throws Exception {
        long from=start(day,zone),to=end(day,zone);List<JSONObject> selected=new ArrayList<>();
        for(int i=0;i<nodes.length();i++){JSONObject n=nodes.getJSONObject(i);long time=n.optLong("created");if(eligible(n)&&time>=from&&time<to)selected.add(n);}
        selected.sort(Comparator.comparingLong((JSONObject n)->n.optLong("created")).thenComparing(n->n.optString("id")));
        return new JSONArray(selected);
    }
    public static JSONObject message(JSONObject n)throws Exception {return new JSONObject().put("id",n.getString("id")).put("conversation_id",n.getString("session"))
            .put("chat",GraphData.text(n.optString("source_chat"),100)).put("sender",GraphData.text(n.optString("sender"),160))
            .put("text",n.getString("body")).put("timestamp",n.getLong("created")).put("source_app",n.optString("source_app"))
            .put("direction","received").put("timestamp_source",n.optString("timestamp_source","notification"));}
    public static String fingerprint(JSONArray nodes)throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        for(int i=0;i<nodes.length();i++){
            JSONObject n=nodes.getJSONObject(i);
            // Fixed field order is independent of JSONObject implementation or serialization order.
            String row=new JSONArray(List.of(n.getString("id"),n.getString("session"),n.optString("source_chat"),n.optString("sender"),n.optString("body"),n.optLong("created"),n.optString("source_app"),n.optString("timestamp_source"),n.optBoolean("edited_by_user"))).toString();
            hash.update(row.getBytes(StandardCharsets.UTF_8));hash.update((byte)'\n');
        }
        StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    /** Round-robin recent messages from distinct saved conversations, bounded by serialized characters. */
    public static JSONObject input(JSONArray all,String day,String zone)throws Exception {
        JSONArray eligible=day(all,day,zone);LinkedHashMap<String,ArrayDeque<JSONObject>> groups=new LinkedHashMap<>();
        for(int i=eligible.length()-1;i>=0;i--){JSONObject n=eligible.getJSONObject(i);groups.computeIfAbsent(n.getString("session"),k->new ArrayDeque<>()).add(n);}
        JSONArray messages=new JSONArray(), originals=new JSONArray();int chars=0,truncated=0;boolean progress=true,full=false;
        while(progress&&!full&&messages.length()<MAX_MESSAGES){progress=false;
            for(ArrayDeque<JSONObject> queue:groups.values()){
                if(queue.isEmpty())continue;progress=true;JSONObject n=queue.removeFirst(),m=message(n);String body=m.getString("text"),shortBody=GraphData.text(body,MAX_BODY);m.put("text",shortBody);
                int length=m.toString().length();if(chars+length>MAX_CHARS){full=true;break;}
                if(shortBody.length()<body.length())truncated++;messages.put(m);originals.put(n);chars+=length;if(messages.length()>=MAX_MESSAGES)break;
            }
        }
        return new JSONObject().put("day",day).put("zone",zone).put("total",eligible.length()).put("conversations",groups.size())
                .put("selected",messages.length()).put("truncated",truncated).put("characters",chars).put("fingerprint",fingerprint(eligible))
                .put("messages",messages).put("originals",originals);
    }
    public static Set<String> ids(JSONArray array)throws Exception {Set<String> out=new LinkedHashSet<>();for(int i=0;i<array.length();i++)out.add(array.getString(i));return out;}
    private static JSONArray references(JSONArray raw,Set<String> allowed)throws Exception {
        if(raw.length()==0||raw.length()>MAX_MESSAGES)throw new IllegalArgumentException("Tema sem mensagens de origem válidas.");
        LinkedHashSet<String> clean=new LinkedHashSet<>();for(int i=0;i<raw.length();i++){String id=raw.getString(i);if(!allowed.contains(id))throw new IllegalArgumentException("A IA citou uma mensagem fora da análise.");clean.add(id);}
        return new JSONArray(clean);
    }
    public static JSONObject parse(String raw,Set<String> allowed)throws Exception {
        if(raw.length()>150000)throw new IllegalArgumentException("Resposta grande demais.");JSONObject in=new JSONObject(raw);JSONArray topics=in.getJSONArray("topics"),links=in.getJSONArray("links"),cleanTopics=new JSONArray(),cleanLinks=new JSONArray();
        if(topics.length()>MAX_TOPICS||links.length()>MAX_LINKS)throw new IllegalArgumentException("O mapa excedeu o limite de temas.");
        Map<String,Set<String>> evidence=new LinkedHashMap<>();Set<String> labels=new HashSet<>();
        for(int i=0;i<topics.length();i++){
            JSONObject t=topics.getJSONObject(i);String id=t.getString("id"),label=GraphData.required(t.getString("label"),80);
            if(!id.matches("T[1-8]")||evidence.containsKey(id)||!labels.add(label.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Temas repetidos ou inválidos.");
            JSONArray refs=references(t.getJSONArray("source_ids"),allowed);evidence.put(id,ids(refs));
            cleanTopics.put(new JSONObject().put("id",id).put("label",label).put("summary",GraphData.required(t.getString("summary"),700))
                    .put("reason",GraphData.required(t.getString("reason"),350)).put("source_ids",refs));
        }
        Set<String> pairs=new HashSet<>();
        for(int i=0;i<links.length();i++){
            JSONObject link=links.getJSONObject(i);String from=link.getString("from"),to=link.getString("to");
            if(from.equals(to)||!evidence.containsKey(from)||!evidence.containsKey(to))throw new IllegalArgumentException("Ligação sem temas válidos.");
            JSONArray refs=references(link.getJSONArray("source_ids"),allowed);Set<String> joint=new HashSet<>(evidence.get(from));joint.addAll(evidence.get(to));Set<String> selected=ids(refs);
            if(!joint.containsAll(selected)||Collections.disjoint(selected,evidence.get(from))||Collections.disjoint(selected,evidence.get(to)))throw new IllegalArgumentException("Ligação sem evidências dos dois temas.");
            String pair=from.compareTo(to)<0?from+":"+to:to+":"+from;if(!pairs.add(pair))continue;
            cleanLinks.put(new JSONObject().put("from",from).put("to",to).put("label",GraphData.required(link.getString("label"),65))
                    .put("reason",GraphData.required(link.getString("reason"),350)).put("source_ids",refs));
        }
        return new JSONObject().put("topics",cleanTopics).put("links",cleanLinks);
    }
    public static JSONObject result(JSONObject parsed,JSONObject input,String model,long now)throws Exception {
        JSONArray sources=new JSONArray();JSONArray messages=input.getJSONArray("messages");for(int i=0;i<messages.length();i++)sources.put(messages.getJSONObject(i).getString("id"));
        JSONObject clean=parse(parsed.toString(),ids(sources));
        return clean.put("id",key(input.getString("day"),input.getString("zone"))).put("day",input.getString("day")).put("zone",input.getString("zone"))
                .put("created",now).put("model",model).put("source_ids",sources).put("input_fingerprint",input.getString("fingerprint"))
                .put("total",input.getInt("total")).put("selected",sources.length()).put("truncated",input.getInt("truncated"));
    }
    public static void validateSaved(JSONObject map,JSONArray nodes)throws Exception {
        String day=map.getString("day"),zone=map.getString("zone");if(!key(day,zone).equals(map.getString("id")))throw new IllegalArgumentException("Data de mapa inválida.");
        JSONArray sources=map.getJSONArray("source_ids");Set<String> refs=ids(sources),eligible=new HashSet<>();JSONArray daily=day(nodes,day,zone);for(int i=0;i<daily.length();i++)eligible.add(daily.getJSONObject(i).getString("id"));
        if(sources.length()==0||sources.length()>MAX_MESSAGES||refs.size()!=sources.length()||!eligible.containsAll(refs))throw new IllegalArgumentException("Mapa com origem ausente ou inválida.");
        if(map.getInt("selected")!=sources.length()||map.getInt("total")<sources.length()||map.getInt("truncated")<0||map.getInt("truncated")>sources.length())throw new IllegalArgumentException("Contagem de mapa inválida.");
        map.getLong("created");GraphData.required(map.getString("model"),100);GraphData.required(map.getString("input_fingerprint"),128);
        if(map.toString().length()>150000)throw new IllegalArgumentException("Mapa grande demais.");parse(map.toString(),refs);
    }
    public static JSONObject remap(JSONObject original,Map<String,String> ids)throws Exception {
        JSONObject map=new JSONObject(original.toString());replace(map.getJSONArray("source_ids"),ids);
        for(String field:List.of("topics","links")){JSONArray list=map.getJSONArray(field);for(int i=0;i<list.length();i++)replace(list.getJSONObject(i).getJSONArray("source_ids"),ids);}
        return map.put("input_fingerprint","imported").put("imported",true);
    }
    private static void replace(JSONArray refs,Map<String,String> ids)throws Exception {for(int i=0;i<refs.length();i++){String id=ids.get(refs.getString(i));if(id==null)throw new IllegalArgumentException("Mensagem ausente na importação.");refs.put(i,id);}}
}
