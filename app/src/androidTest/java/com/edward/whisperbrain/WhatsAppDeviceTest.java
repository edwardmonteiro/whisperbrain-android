package com.edward.whisperbrain;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.service.notification.StatusBarNotification;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WhatsAppDeviceTest {
    private Context context;private NotebookStore store;private long since,now;
    private final Person me=new Person.Builder().setName("Você").setKey("self").build();
    private final Person sender=new Person.Builder().setName("Contato · exemplo").setKey("contact-1").build();
    @Before public void prepare()throws Exception{
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();store=NotebookStore.get(context);WhatsAppCapture.setEnabled(context,false);
        JSONArray sessions=store.sessions();for(int i=0;i<sessions.length();i++)store.deleteSession(sessions.getJSONObject(i).getString("id"));
        store.getWritableDatabase().delete("notification_threads",null,null);store.getWritableDatabase().delete("notification_seen",null,null);new Vault(context).forgetKey();now=System.currentTimeMillis();since=now-10000;
    }
    @After public void stop()throws Exception{WhatsAppCapture.setEnabled(context,false);}
    private NotificationCompat.MessagingStyle style(){return new NotificationCompat.MessagingStyle(me).setConversationTitle("Planejamento · exemplo").setGroupConversation(true);}
    private StatusBarNotification notification(String pkg,String thread,NotificationCompat.MessagingStyle style,long posted){
        Notification n=new NotificationCompat.Builder(context,"fixture").setSmallIcon(android.R.drawable.ic_dialog_info).setCategory(Notification.CATEGORY_MESSAGE).setShortcutId(thread).setStyle(style).setWhen(posted).build();
        return new StatusBarNotification(pkg,pkg,7,"fixture",10000,0,0,n,android.os.Process.myUserHandle(),posted);
    }
    private WhatsAppNotice message(String chat,String thread,String text,long time){return new WhatsAppNotice("com.whatsapp",thread,chat,"Contato · exemplo","sender",text,time,false,"message");}
    @Test public void extractsOnlyIncomingNewMessagesAndIgnoresHistoricEntries(){
        NotificationCompat.MessagingStyle s=style().addMessage("Anterior à ativação",since-1,sender).addMessage("Definir o prazo amanhã",now-100,sender).addMessage("Minha resposta",now-50,(Person)null)
                .addHistoricMessage(new NotificationCompat.MessagingStyle.Message("Histórico separado",now-10,sender));
        List<WhatsAppNotice> out=WhatsAppNotificationParser.parse(notification("com.whatsapp","chat-1",s,now),since,now,false);
        assertEquals(1,out.size());assertEquals("Definir o prazo amanhã",out.get(0).text);assertEquals("Contato · exemplo",out.get(0).sender);assertEquals("Planejamento · exemplo",out.get(0).chat);assertTrue(out.get(0).group);
    }
    @Test public void rejectsOtherAppsSummariesCallsAndPreActivationNotifications(){
        NotificationCompat.MessagingStyle s=style().addMessage("Mensagem",now-10,sender);
        assertTrue(WhatsAppNotificationParser.parse(notification("com.example.other","chat",s,now),since,now,true).isEmpty());
        assertTrue(WhatsAppNotificationParser.parse(notification("com.whatsapp.w4b","chat",s,now),since,now,false).isEmpty());
        assertTrue(WhatsAppNotificationParser.parse(notification("com.whatsapp","chat",s,since),since,now,false).isEmpty());
        StatusBarNotification summary=notification("com.whatsapp","chat",s,now);summary.getNotification().flags|=Notification.FLAG_GROUP_SUMMARY;assertTrue(WhatsAppNotificationParser.parse(summary,since,now,false).isEmpty());
        StatusBarNotification call=notification("com.whatsapp","chat",s,now);call.getNotification().category=Notification.CATEGORY_CALL;assertTrue(WhatsAppNotificationParser.parse(call,since,now,false).isEmpty());
        assertEquals(1,WhatsAppNotificationParser.parse(notification("com.whatsapp.w4b","chat",s,now),since,now,true).size());
    }
    @Test public void mediaNotificationCreatesOnlyAnHonestPlaceholder(){
        NotificationCompat.MessagingStyle s=style().addMessage(new NotificationCompat.MessagingStyle.Message("",now-10,sender).setData("audio/ogg",Uri.parse("content://fixture-do-not-read/audio")));
        List<WhatsAppNotice> out=WhatsAppNotificationParser.parse(notification("com.whatsapp","chat",s,now),since,now,false);
        assertEquals(1,out.size());assertTrue(out.get(0).text.contains("arquivo não foi capturado"));
    }
    @Test public void reusedNotificationSlotsDoNotMergeDifferentChatTitles(){
        NotificationCompat.MessagingStyle a=style().setConversationTitle("Conversa A").addMessage("Primeira",now-10,sender),b=style().setConversationTitle("Conversa B").addMessage("Segunda",now-10,sender);
        WhatsAppNotice first=WhatsAppNotificationParser.parse(notification("com.whatsapp",null,a,now),since,now,false).get(0);
        WhatsAppNotice second=WhatsAppNotificationParser.parse(notification("com.whatsapp",null,b,now),since,now,false).get(0);assertNotEquals(first.thread,second.thread);
        assertNotEquals(WhatsAppNotificationParser.parse(notification("com.whatsapp","",a,now),since,now,false).get(0).thread,WhatsAppNotificationParser.parse(notification("com.whatsapp","",b,now),since,now,false).get(0).thread);
    }
    @Test public void legacyFallbackRequiresVisibleTextAndRecentTimestamp(){
        Notification n=new NotificationCompat.Builder(context,"fixture").setSmallIcon(android.R.drawable.ic_dialog_info).setCategory(Notification.CATEGORY_MESSAGE).setContentTitle("Contato · exemplo").setContentText("Reunião às 15h").setWhen(now-50).build();
        StatusBarNotification sbn=new StatusBarNotification("com.whatsapp","com.whatsapp",8,"legacy",10000,0,0,n,android.os.Process.myUserHandle(),now);
        assertEquals(1,WhatsAppNotificationParser.parse(sbn,since,now,false).size());n.when=since-1;assertTrue(WhatsAppNotificationParser.parse(sbn,since,now,false).isEmpty());n.when=now-50;n.extras.putCharSequence(Notification.EXTRA_TEXT,"Conteúdo oculto");assertTrue(WhatsAppNotificationParser.parse(sbn,since,now,false).isEmpty());
    }
    @Test public void deduplicationSurvivesReopenAndDeletionWithoutLosingRepeatedNewText()throws Exception{
        WhatsAppNotice m=message("Teste","stable","ok",now-100);assertEquals(1,store.captureNotifications(List.of(m),since,now));String id=store.nodes(null).getJSONObject(0).getString("id");
        store.close();assertEquals(0,store.captureNotifications(List.of(m),since,now));store.deleteNode(id);assertEquals(0,store.captureNotifications(List.of(m),since,now));
        assertEquals(1,store.captureNotifications(List.of(message("Teste","stable","ok",now-50)),since,now));assertEquals(1,store.nodes(null).length());GraphData.validate(store.snapshot());
    }
    @Test public void threadIdentityAndDatesCreateSeparateSessionsWithConversationEdges()throws Exception{
        long day1=java.time.LocalDate.now().atTime(12,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),day2=day1+86400000L;
        store.captureNotifications(List.of(message("Mesmo nome","a","Primeira",day1),message("Mesmo nome","b","Outra pessoa",day1+1)),day1-1000,day1+1000);
        assertEquals(2,store.sessions().length());store.captureNotifications(List.of(message("Mesmo nome","a","Segundo dia",day2)),day1-1000,day2+1000);
        assertEquals(3,store.sessions().length());assertEquals(1,store.edges().length());assertEquals("notification",store.edges().getJSONObject(0).getString("origin"));
        String from=store.edges().getJSONObject(0).getString("from");store.deleteSession(store.node(from).getString("session"));assertEquals(0,store.edges().length());GraphData.validate(store.snapshot());
    }
    @Test public void notificationNotesRemainEncryptedAndPortable()throws Exception{
        store.captureNotifications(List.of(message("Conversa privada 7193","private","Mensagem privada 9385",now-100)),since,now);
        try(Cursor c=store.getReadableDatabase().rawQuery("SELECT payload FROM nodes UNION ALL SELECT payload FROM sessions",null)){while(c.moveToNext()){assertFalse(c.getString(0).contains("privada"));}}
        try(Cursor c=store.getReadableDatabase().rawQuery("SELECT thread_key FROM notification_threads",null)){assertTrue(c.moveToFirst());assertEquals(64,c.getString(0).length());}
        JSONObject n=store.nodes(null).getJSONObject(0);assertEquals("notification",n.getString("origin"));assertEquals(now-100,n.getLong("created"));assertEquals("Contato · exemplo",n.getString("sender"));
        ByteArrayOutputStream zip=new ByteArrayOutputStream();NotebookBackup.exportAll(context,zip);assertEquals(1,NotebookBackup.importAll(context,new ByteArrayInputStream(zip.toByteArray())));assertEquals(2,store.nodes(null).length());GraphData.validate(store.snapshot());
    }
    @Test public void versionOneDatabaseMigratesWithoutChangingNotesOrEdges()throws Exception{
        JSONObject s=store.createSession("Caderno 0.2"),a=store.addNode(s.getString("id"),"Preservar","Conteúdo anterior","note","user"),b=store.addNode(s.getString("id"),"Ligação","Outra nota","note","user");
        store.link(a.getString("id"),b.getString("id"),"relaciona","Antes da atualização","user","accepted");
        store.getWritableDatabase().execSQL("DROP TABLE notification_threads");store.getWritableDatabase().execSQL("DROP TABLE notification_seen");store.getWritableDatabase().setVersion(1);store.close();
        assertEquals("Conteúdo anterior",store.node(a.getString("id")).getString("body"));assertEquals(3,store.getReadableDatabase().getVersion());assertEquals(1,store.edges().length());
        assertEquals(1,store.captureNotifications(List.of(message("Nova","new","Depois da atualização",now-100)),since,now));assertEquals(3,store.nodes(null).length());
    }
}
