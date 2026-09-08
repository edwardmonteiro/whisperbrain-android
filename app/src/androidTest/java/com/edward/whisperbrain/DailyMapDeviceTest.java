package com.edward.whisperbrain;

import android.content.*;
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
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DailyMapDeviceTest {
    private Context context;private NotebookStore store;private String day,zone,namespace;private long start;
    @Before public void prepare()throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();store=NotebookStore.get(context);DailyMapAi.cancel();WhatsAppCapture.setEnabled(context,false);clear();new Vault(context).forgetKey();
        namespace=UUID.randomUUID().toString();zone=ZoneId.systemDefault().getId();day=LocalDate.now(ZoneId.of(zone)).toString();start=DailyMapData.start(day,zone);
    }
    private void clear()throws Exception {JSONArray sessions=store.sessions();for(int i=0;i<sessions.length();i++)store.deleteSession(sessions.getJSONObject(i).getString("id"));}
    private void capture(String chat,String thread,String text,long time)throws Exception {
        WhatsAppNotice notice=new WhatsAppNotice("com.whatsapp",namespace+":"+thread,chat,"Contato de exemplo","example",text,time,false,"message");assertEquals(1,store.captureNotifications(List.of(notice),start-1,time+1000));
    }
    private JSONObject input()throws Exception {return DailyMapData.input(store.dailyNotifications(day,zone),day,zone);}
    private JSONObject topic(String id,String label,String summary,String... refs)throws Exception {return new JSONObject().put("id",id).put("label",label).put("summary",summary).put("reason","Solicitação mencionada nas notificações recebidas.").put("source_ids",new JSONArray(Arrays.asList(refs)));}
    private JSONObject createMap()throws Exception {
        capture("Projeto · exemplo","project","Precisamos confirmar o prazo e o orçamento da visita ao projeto Atlas.",start+10*3600000);
        capture("Viagem · exemplo","travel","Podemos reservar as passagens para visitar o projeto Atlas?",start+11*3600000);
        capture("Projeto · exemplo","project","Por favor, confirme a data da visita com a equipe.",start+12*3600000);
        JSONObject selected=input();JSONArray notes=store.dailyNotifications(day,zone);String a=notes.getJSONObject(0).getString("id"),b=notes.getJSONObject(1).getString("id"),c=notes.getJSONObject(2).getString("id");
        JSONArray topics=new JSONArray().put(topic("T1","Projeto Atlas","Foram recebidos pedidos sobre prazo e orçamento da visita.",a,c))
                .put(topic("T2","Viagem da equipe","Uma mensagem solicita a reserva de passagens para a visita.",b))
                .put(topic("T3","Data da visita","Foi recebida uma solicitação para confirmar a data com a equipe.",c));
        JSONArray links=new JSONArray().put(new JSONObject().put("from","T1").put("to","T2").put("label","visita ao mesmo projeto").put("reason","As mensagens citam a visita ao projeto Atlas.").put("source_ids",new JSONArray(List.of(a,b))));
        JSONObject result=DailyMapData.result(new JSONObject().put("topics",topics).put("links",links),selected,"synthetic-test-fixture",System.currentTimeMillis());store.saveDailyMap(result,selected.getJSONArray("originals"));return result;
    }
    @Test public void savedMapIsEncryptedAndPersistsWithoutNetworkOrMicrophone()throws Exception {
        JSONObject map=createMap();try(Cursor cursor=store.getReadableDatabase().rawQuery("SELECT payload FROM daily_maps",null)){assertTrue(cursor.moveToFirst());assertFalse(cursor.getString(0).contains("Atlas"));assertFalse(cursor.getString(0).contains("source_ids"));}
        store.close();assertEquals(3,store.dailyMap(day,zone).getJSONArray("topics").length());assertEquals(map.getString("id"),store.dailyMap(day,zone).getString("id"));assertFalse(DailyMapAi.running);assertFalse(SessionState.active);GraphData.validate(store.snapshot());
    }
    @Test public void editsInvalidateDerivedMapsAndOldResponsesCannotResurrectThem()throws Exception {
        JSONObject map=createMap(),selected=input();String id=selected.getJSONArray("originals").getJSONObject(0).getString("id");store.updateNote(id,"Nota revisada","Texto alterado pelo usuário.");
        assertNull(store.dailyMap(day,zone));assertEquals(2,store.dailyNotifications(day,zone).length());
        try{store.saveDailyMap(map,selected.getJSONArray("originals"));fail("Stale source should fail");}catch(IllegalArgumentException expected){}
        assertNull(store.dailyMap(day,zone));
    }
    @Test public void deletionRemovesTheMapAndRejectsAnInflightResult()throws Exception {
        JSONObject map=createMap(),selected=input();store.deleteNode(selected.getJSONArray("originals").getJSONObject(0).getString("id"));assertNull(store.dailyMap(day,zone));
        try{store.saveDailyMap(map,selected.getJSONArray("originals"));fail("Deleted source should fail");}catch(IllegalArgumentException expected){}
        GraphData.validate(store.snapshot());
    }
    @Test public void newMessagesKeepThePriorMapButMakeItsCoverageStale()throws Exception {
        JSONObject original=createMap(),selected=input();capture("Nova conversa","new","Uma nova solicitação recebida.",start+13*3600000);JSONObject updated=input();
        assertEquals(4,updated.getInt("total"));assertNotEquals(original.getString("input_fingerprint"),updated.getString("fingerprint"));assertNotNull(store.dailyMap(day,zone));
        store.saveDailyMap(original,selected.getJSONArray("originals"));assertEquals(3,store.dailyMap(day,zone).getInt("selected"));
    }
    @Test public void backupRoundTripRewiresEvidenceAndIncludesReadableDayNotes()throws Exception {
        JSONObject original=createMap();Set<String> before=DailyMapData.ids(original.getJSONArray("source_ids"));ByteArrayOutputStream out=new ByteArrayOutputStream();NotebookBackup.exportAll(context,out);boolean markdown=false;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))){ZipEntry entry;while((entry=zip.getNextEntry())!=null)if(entry.getName().startsWith("days/")&&entry.getName().endsWith(".md"))markdown=true;}
        assertTrue(markdown);clear();assertNull(store.dailyMap(day,zone));NotebookBackup.importAll(context,new ByteArrayInputStream(out.toByteArray()));
        JSONObject restored=store.dailyMap(day,zone);assertNotNull(restored);Set<String> after=DailyMapData.ids(restored.getJSONArray("source_ids"));assertTrue(Collections.disjoint(before,after));assertTrue(restored.getBoolean("imported"));
        for(String id:after)assertEquals("notification",store.node(id).getString("origin"));GraphData.validate(store.snapshot());
    }
    @Test public void invalidMapBackupLeavesAllExistingDataIntact()throws Exception {
        JSONObject map=createMap(),snapshot=store.snapshot();snapshot.getJSONArray("daily_maps").getJSONObject(0).getJSONArray("topics").getJSONObject(0).getJSONArray("source_ids").put(0,"missing-message");
        try{store.importSnapshot(snapshot,Map.of());fail();}catch(IllegalArgumentException expected){}
        assertEquals(3,store.nodes(null).length());assertEquals(map.getString("id"),store.dailyMap(day,zone).getString("id"));
    }
    @Test public void versionTwoMigrationPreservesNotificationData()throws Exception {
        capture("Antes da atualização","before","Mensagem preservada da versão 0.3.",start+10*3600000);String id=store.nodes(null).getJSONObject(0).getString("id");
        store.getWritableDatabase().execSQL("DROP TABLE daily_maps");store.getWritableDatabase().execSQL("DROP INDEX nodes_created");store.getWritableDatabase().setVersion(2);store.close();
        assertEquals(3,store.getReadableDatabase().getVersion());assertTrue(store.node(id).getString("body").contains("preservada"));assertEquals(1,input().getInt("selected"));assertNull(store.dailyMap(day,zone));
    }
    @Test public void emptyScreenExplainsCaptureAndDoesNotCallTheApi()throws Exception {
        try(ActivityScenario<DailyMapActivity> scenario=ActivityScenario.launch(new Intent(context,DailyMapActivity.class))){await(scenario,false);
            scenario.onActivity(a->{a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);assertFalse(findButton(a.getWindow().getDecorView(),"Analisar meu dia · IA").isEnabled());assertNotNull(findButton(a.getWindow().getDecorView(),"Configurar captura do WhatsApp"));});
            screenshot("day-empty.png");assertFalse(DailyMapAi.running);assertNull(store.dailyMap(day,zone));}
    }
    @Test public void dailyMapAndEvidenceRenderAndSurviveActivityRecreation()throws Exception {
        createMap();try(ActivityScenario<DailyMapActivity> scenario=ActivityScenario.launch(new Intent(context,DailyMapActivity.class))){await(scenario,true);
            scenario.onActivity(a->{a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);assertEquals(3,findGraph(a.getWindow().getDecorView()).topicCount());});screenshot("day-map.png");
            scenario.recreate();await(scenario,true);scenario.onActivity(a->{a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);a.showTopic("T1");assertNotNull(a.detailDialog);a.detailDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);});screenshot("day-theme.png");
            assertFalse(DailyMapAi.running);assertEquals(3,store.nodes(null).length());}
    }
    private void await(ActivityScenario<DailyMapActivity> scenario,boolean graph)throws Exception {
        long until=System.currentTimeMillis()+7000;AtomicBoolean ready=new AtomicBoolean();while(System.currentTimeMillis()<until){scenario.onActivity(a->ready.set(graph?findGraph(a.getWindow().getDecorView())!=null:findButton(a.getWindow().getDecorView(),"Analisar meu dia · IA")!=null));if(ready.get())return;Thread.sleep(50);}fail("Day screen did not finish loading");
    }
    private static Button findButton(View v,String text){if(v instanceof Button&&text.contentEquals(((Button)v).getText()))return (Button)v;if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){Button b=findButton(group.getChildAt(i),text);if(b!=null)return b;}}return null;}
    private static DailyMapView findGraph(View v){if(v instanceof DailyMapView)return (DailyMapView)v;if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){DailyMapView graph=findGraph(group.getChildAt(i));if(graph!=null)return graph;}}return null;}
    private void screenshot(String name)throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(800,5000);
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);File dir=new File(context.getExternalFilesDir(null),"qa");assertTrue(dir.exists()||dir.mkdirs());
        try(OutputStream out=new FileOutputStream(new File(dir,name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();shell("mkdir -p /sdcard/Download/whisperbrain-qa");shell("cp "+new File(dir,name).getAbsolutePath()+" /sdcard/Download/whisperbrain-qa/"+name);
    }
    private void shell(String command)throws Exception {try(android.os.ParcelFileDescriptor p=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(p)){byte[] bytes=new byte[1024];while(in.read(bytes)!=-1){}}}
}
