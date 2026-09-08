package com.edward.whisperbrain;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class GraphDataTest {
    private JSONObject session(String id)throws Exception{return new JSONObject().put("id",id).put("event","Planejamento").put("created",1000);}
    private JSONObject node(String id,String sid)throws Exception{return new JSONObject().put("id",id).put("session",sid).put("kind","note").put("origin","user").put("title","Prioridades").put("body","Organizar a próxima reunião.").put("created",1001);}
    private JSONObject edge(String from,String to)throws Exception{return new JSONObject().put("id","e1").put("from",from).put("to",to).put("state","proposed").put("origin","ai").put("relation","complementa").put("reason","Mesmo assunto.").put("created",1002);}
    private JSONObject backup()throws Exception{return new JSONObject().put("version",1).put("sessions",new JSONArray().put(session("s1")).put(session("s2"))).put("nodes",new JSONArray().put(node("n1","s1")).put(node("n2","s2"))).put("edges",new JSONArray().put(edge("n1","n2")));}
    @Test public void crossConversationEdgesSurviveInGlobalGraph()throws Exception{JSONObject b=backup();GraphData.validate(b);assertEquals(1,GraphData.filterEdges(b.getJSONArray("nodes"),b.getJSONArray("edges")).length());}
    @Test public void singleConversationProjectionHasNoDanglingEdges()throws Exception{JSONObject b=backup();assertEquals(0,GraphData.filterEdges(new JSONArray().put(node("n1","s1")),b.getJSONArray("edges")).length());}
    @Test(expected=Exception.class)public void orphanLinksCannotBeImported()throws Exception{JSONObject b=backup();b.getJSONArray("edges").getJSONObject(0).put("to","missing");GraphData.validate(b);}
    @Test(expected=Exception.class)public void idsCannotCollideAcrossRecordTypes()throws Exception{JSONObject b=backup();b.getJSONArray("nodes").getJSONObject(0).put("id","s1");GraphData.validate(b);}
    @Test(expected=Exception.class)public void attachmentTraversalIsRejected()throws Exception{JSONObject b=backup();b.getJSONArray("nodes").getJSONObject(0).put("attachment","../../private");GraphData.validate(b);}
    @Test public void inventedAndSelfLinksFromAiAreDiscarded()throws Exception{
        JSONObject s=new JSONObject().put("title","Próximo passo").put("recommendation","Defina uma prioridade.").put("whisper","Escolha uma prioridade para amanhã.").put("connections",new JSONArray()
                .put(new JSONObject().put("target_id","n2").put("relation","complementa").put("reason","Plano relacionado."))
                .put(new JSONObject().put("target_id","invented").put("relation","relaciona").put("reason","Não existe."))
                .put(new JSONObject().put("target_id","n1").put("relation","relaciona").put("reason","Própria nota.")));
        JSONObject valid=GraphData.parseSuggestion(s.toString(),Set.of("n1","n2"),"n1");assertEquals(1,valid.getJSONArray("connections").length());assertEquals("n2",valid.getJSONArray("connections").getJSONObject(0).getString("target_id"));
    }
    @Test public void emptyRecommendationIsNotSaved()throws Exception{try{GraphData.parseSuggestion("{\"title\":\"Algo\",\"recommendation\":\" \"}",Set.of(),"n1");fail();}catch(Exception expected){}}
    @Test public void markdownRetainsIdsProvenanceAndHypothesisState()throws Exception{JSONObject b=backup();String md=GraphData.markdown(b.getJSONArray("sessions").getJSONObject(0),b.getJSONArray("nodes"),b.getJSONArray("edges"));assertTrue(md.contains("[[n2]]"));assertTrue(md.contains("proposed"));assertTrue(md.contains("Origem: user"));assertTrue(md.contains("reunião"));}
}
