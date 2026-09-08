package com.edward.whisperbrain;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NotebookDeviceTest {
    private Context context; private NotebookStore store;
    @Before public void prepare()throws Exception{context=InstrumentationRegistry.getInstrumentation().getTargetContext();store=NotebookStore.get(context);JSONArray all=store.sessions();for(int i=0;i<all.length();i++)store.deleteSession(all.getJSONObject(i).getString("id"));new Vault(context).forgetKey();}
    private JSONObject session(String name)throws Exception{return store.createSession(name);}
    private JSONObject note(JSONObject s,String text)throws Exception{return store.addNode(s.getString("id"),text,text,"note","user");}
    @Test public void encryptedNotesAndEdgesPersistAfterDatabaseReopen()throws Exception{
        JSONObject s=session("Persistência local"),a=note(s,"Conteúdo privado de teste 9347"),b=note(s,"Segunda ideia");store.link(a.getString("id"),b.getString("id"),"complementa","Relacionadas","user","accepted");
        try(Cursor cursor=store.getReadableDatabase().rawQuery("SELECT payload FROM nodes",null)){while(cursor.moveToNext())assertFalse(cursor.getString(0).contains("Conteúdo privado"));}
        store.close();assertEquals("Conteúdo privado de teste 9347",store.node(a.getString("id")).getString("body"));assertEquals(1,store.edges().length());
    }
    @Test public void deletingOneConversationRemovesItsCrossEdgesOnly()throws Exception{
        JSONObject s1=session("A"),s2=session("B"),a=note(s1,"Nota A"),b=note(s2,"Nota B");store.link(a.getString("id"),b.getString("id"),"relaciona","","user","accepted");store.deleteSession(s1.getString("id"));assertEquals(1,store.nodes(null).length());assertEquals(0,store.edges().length());assertEquals("Nota B",store.node(b.getString("id")).getString("body"));
    }
    @Test public void backupRoundTripRestoresAudioAndCrossConversationLinks()throws Exception{
        JSONObject s1=session("Projeto"),s2=session("Reunião"),a=note(s1,"Alinhar prioridades"),b=note(s2,"Definir prazo");store.link(a.getString("id"),b.getString("id"),"depende de","Hipótese para revisar","ai","proposed");
        byte[] pcm=new byte[]{0,0,100,0,-100,-1,42,0};String attachment=AudioArchive.save(context,new ByteArrayInputStream(pcm));store.addAudio(s1.getString("id"),attachment,"pcm24k",1);
        ByteArrayOutputStream zip=new ByteArrayOutputStream();NotebookBackup.exportAll(context,zip);
        String before=a.getString("id");assertEquals(2,NotebookBackup.importAll(context,new ByteArrayInputStream(zip.toByteArray())));assertEquals(4,store.sessions().length());assertEquals(2,store.edges().length());assertEquals("Alinhar prioridades",store.node(before).getString("body"));
        JSONArray all=store.nodes(null);boolean copied=false;for(int i=0;i<all.length();i++){JSONObject n=all.getJSONObject(i);if(n.optString("kind").equals("audio")&&!n.optString("attachment").equals(attachment)){try(InputStream in=AudioArchive.open(context,n.getString("attachment"))){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[100];int count;while((count=in.read(buf))!=-1)out.write(buf,0,count);assertArrayEquals(pcm,out.toByteArray());}copied=true;}}
        assertTrue(copied);GraphData.validate(store.snapshot());
    }
    @Test public void invalidImportDoesNotModifyExistingNotebook()throws Exception{
        JSONObject s=session("Preservar"),a=note(s,"Não remover");JSONObject bad=store.snapshot();bad.put("edges",new JSONArray().put(new JSONObject().put("id","edge").put("from",a.getString("id")).put("to","missing").put("state","proposed").put("relation","x").put("created",1)));
        try{store.importSnapshot(bad,Map.of());fail();}catch(Exception expected){}assertEquals(1,store.sessions().length());assertEquals(1,store.nodes(null).length());
    }
    @Test public void aiContextRespectsConversationScopeAndKeepsProposalsSeparate()throws Exception{
        JSONObject s1=session("Planejamento"),s2=session("Acompanhamento"),a=note(s1,"Orçamento prazo prioridades"),b=note(s2,"Orçamento confirmado para prioridades");
        assertEquals(1,NotebookAi.context(a,store.nodes(null),false).length());assertEquals(2,NotebookAi.context(a,store.nodes(null),true).length());
        JSONObject req=NotebookAi.request("fixture-model",a,NotebookAi.context(a,store.nodes(null),true));assertFalse(req.getBoolean("store"));assertEquals("json_schema",req.getJSONObject("text").getJSONObject("format").getString("type"));
        JSONObject result=new JSONObject().put("title","Revisar orçamento").put("recommendation","Confira os valores antes de decidir.").put("whisper","Confira o orçamento.").put("connections",new JSONArray().put(new JSONObject().put("target_id",b.getString("id")).put("relation","complementa").put("reason","Mesmo orçamento.")));
        JSONObject recommendation=store.saveSuggestion(a.getString("id"),result,"fixture-model");assertEquals("ai",recommendation.getString("origin"));assertEquals(2,store.edges().length());assertEquals("proposed",store.edges().getJSONObject(0).getString("state"));String id=store.edges().getJSONObject(0).getString("id");store.acceptEdge(id);assertEquals("accepted",store.edges().getJSONObject(0).getString("state"));
    }
    @Test public void textFlowWorksWithoutMicrophoneOrApiAndGraphRenders()throws Exception{
        JSONObject s=session("Planejamento · exemplo"),other=session("Revisão · exemplo");JSONObject prior=note(s,"Quero esclarecer a decisão principal"),across=note(other,"A decisão depende do prazo");store.link(prior.getString("id"),across.getString("id"),"depende de","Ligação de exemplo","user","accepted");
        Intent intent=new Intent(context,NotebookActivity.class).putExtra("session",s.getString("id"));
        try(ActivityScenario<NotebookActivity> scenario=ActivityScenario.launch(intent)){
            scenario.onActivity(a->{a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);EditText editor=findEditor(a.getWindow().getDecorView());assertNotNull(editor);editor.setText("Definir três prioridades para a próxima reunião");Button save=findButton(a.getWindow().getDecorView(),"Salvar neurônio");assertNotNull(save);save.performClick();});
            assertEquals(2,store.nodes(s.getString("id")).length());assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));assertFalse(NotebookAi.running);assertFalse(SessionState.active);screenshot("session.png");
            scenario.onActivity(a->{Button graph=findButton(a.getWindow().getDecorView(),"Grafo desta conversa");assertNotNull(graph);graph.performClick();BrainGraphView view=findGraph(a.getWindow().getDecorView());assertNotNull(view);assertEquals(2,view.nodeCount());});screenshot("conversation-graph.png");
        }
        try(ActivityScenario<NotebookActivity> scenario=ActivityScenario.launch(new Intent(context,NotebookActivity.class))){scenario.onActivity(a->{a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);assertNotNull(findButton(a.getWindow().getDecorView(),"+ Nova sessão"));});screenshot("home.png");
            scenario.onActivity(a->{findButton(a.getWindow().getDecorView(),"Grafo de todas as conversas").performClick();assertEquals(3,findGraph(a.getWindow().getDecorView()).nodeCount());});screenshot("global-graph.png");}
    }
    private static EditText findEditor(View view){if(view instanceof EditText && "Texto do novo neurônio".contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return (EditText)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){EditText e=findEditor(group.getChildAt(i));if(e!=null)return e;}}return null;}
    private static Button findButton(View view,String text){if(view instanceof Button && text.contentEquals(((Button)view).getText()))return (Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button b=findButton(group.getChildAt(i),text);if(b!=null)return b;}}return null;}
    private static BrainGraphView findGraph(View view){if(view instanceof BrainGraphView)return (BrainGraphView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){BrainGraphView g=findGraph(group.getChildAt(i));if(g!=null)return g;}}return null;}
    private void screenshot(String name)throws Exception{InstrumentationRegistry.getInstrumentation().waitForIdleSync();Bitmap shot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(shot);File dir=new File(context.getExternalFilesDir(null),"qa");assertTrue(dir.exists()||dir.mkdirs());try(OutputStream out=new FileOutputStream(new File(dir,name))){assertTrue(shot.compress(Bitmap.CompressFormat.PNG,100,out));}shot.recycle();shell("mkdir -p /sdcard/Download/whisperbrain-qa");shell("cp "+new File(dir,name).getAbsolutePath()+" /sdcard/Download/whisperbrain-qa/"+name);}
    private void shell(String command)throws Exception{try(android.os.ParcelFileDescriptor pfd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)){byte[] b=new byte[1024];while(in.read(b)!=-1){}}}
}
