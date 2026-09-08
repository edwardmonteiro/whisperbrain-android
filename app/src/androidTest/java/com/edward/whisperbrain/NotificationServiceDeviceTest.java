package com.edward.whisperbrain;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.service.notification.StatusBarNotification;
import android.view.*;
import android.widget.*;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NotificationServiceDeviceTest {
    @Test public void systemBindingPauseResumePermissionAndVisibleControlsWork()throws Exception{
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();NotebookStore store=NotebookStore.get(c);WhatsAppCapture.setEnabled(c,false);new Vault(c).forgetKey();
        JSONArray sessions=store.sessions();for(int i=0;i<sessions.length();i++)store.deleteSession(sessions.getJSONObject(i).getString("id"));
        store.getWritableDatabase().delete("notification_threads",null,null);store.getWritableDatabase().delete("notification_seen",null,null);WhatsAppCapture.prefs(c).edit().clear().commit();
        String component=c.getPackageName()+"/.WhatsAppNotificationService";
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        try{
            shell("cmd notification disallow_listener "+component);WhatsAppCapture.setEnabled(c,true);shell("cmd notification allow_listener "+component);
            await(()->WhatsAppCapture.permitted(c)&&WhatsAppCapture.connected());SystemClock.sleep(30);
            long first=System.currentTimeMillis();WhatsAppCapture.Admission ticket=WhatsAppCapture.admission(c);
            WhatsAppCapture.process(c,incoming(c,first,first,"Confirmar o horário · exemplo"),ticket,first);
            assertEquals(1,store.nodes(null).length());assertFalse(NotebookAi.running);assertFalse(SessionState.active);
            shell("pm grant "+c.getPackageName()+" android.permission.POST_NOTIFICATIONS");
            manager.createNotificationChannel(new NotificationChannel("notification-fixture","Fixture",NotificationManager.IMPORTANCE_DEFAULT));
            manager.notify(937,new Notification.Builder(c,"notification-fixture").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Outra origem · exemplo").setContentText("Esta notificação é do próprio app.").build());
            SystemClock.sleep(600);assertEquals(1,store.nodes(null).length());
            try(ActivityScenario<WhatsAppActivity> scenario=ActivityScenario.launch(WhatsAppActivity.class)){
                scenario.onActivity(a->{a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);assertNotNull(button(a.getWindow().getDecorView(),"Pausar captura"));});screenshot(c,"whatsapp-capture.png");
                scenario.onActivity(a->button(a.getWindow().getDecorView(),"Pausar captura").performClick());assertFalse(WhatsAppCapture.enabled(c));
            }
            long paused=System.currentTimeMillis();WhatsAppCapture.process(c,incoming(c,paused,paused,"Mensagem durante a pausa"),ticket,paused);assertEquals(1,store.nodes(null).length());
            SystemClock.sleep(30);WhatsAppCapture.setEnabled(c,true);SystemClock.sleep(30);WhatsAppCapture.Admission resumed=WhatsAppCapture.admission(c);long after=System.currentTimeMillis();
            WhatsAppCapture.process(c,incoming(c,paused,after,"Mensagem durante a pausa"),resumed,after);assertEquals(1,store.nodes(null).length());
            WhatsAppCapture.process(c,incoming(c,after,after,"Nova após retomar"),resumed,after);assertEquals(2,store.nodes(null).length());
            shell("cmd notification disallow_listener "+component);await(()->!WhatsAppCapture.permitted(c)&&!WhatsAppCapture.connected());
            long revoked=System.currentTimeMillis();WhatsAppCapture.process(c,incoming(c,revoked,revoked,"Sem permissão"),WhatsAppCapture.admission(c),revoked);assertEquals(2,store.nodes(null).length());
            String diagnostics=WhatsAppCapture.diagnostics(c);assertFalse(diagnostics.contains("Confirmar"));assertFalse(diagnostics.contains("Contato · exemplo"));
        }finally{WhatsAppCapture.setEnabled(c,false);shell("cmd notification disallow_listener "+component);manager.cancel(937);manager.deleteNotificationChannel("notification-fixture");}
    }
    private StatusBarNotification incoming(Context c,long time,long posted,String text){
        Person sender=new Person.Builder().setName("Contato · exemplo").setKey("fixture-sender").build();
        NotificationCompat.MessagingStyle style=new NotificationCompat.MessagingStyle(new Person.Builder().setName("Você").setKey("self").build()).addMessage(text,time,sender);
        Notification n=new NotificationCompat.Builder(c,"fixture").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Contato · exemplo").setShortcutId("fixture-contact").setCategory(Notification.CATEGORY_MESSAGE).setWhen(posted).setStyle(style).build();
        return new StatusBarNotification("com.whatsapp","com.whatsapp",19,"fixture",10000,0,n,android.os.Process.myUserHandle(),posted);
    }
    private void await(BooleanSupplier ready){long end=SystemClock.elapsedRealtime()+15000;while(!ready.getAsBoolean()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);assertTrue("Android listener did not reach the expected state",ready.getAsBoolean());}
    private static Button button(View v,String name){if(v instanceof Button&&name.contentEquals(((Button)v).getText()))return (Button)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Button found=button(g.getChildAt(i),name);if(found!=null)return found;}}return null;}
    private void screenshot(Context c,String name)throws Exception{
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(800,5000);
        Bitmap shot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(shot);File dir=new File(c.getExternalFilesDir(null),"qa");assertTrue(dir.exists()||dir.mkdirs());
        try(OutputStream out=new FileOutputStream(new File(dir,name))){assertTrue(shot.compress(Bitmap.CompressFormat.PNG,100,out));}shot.recycle();shell("mkdir -p /sdcard/Download/whisperbrain-qa");shell("cp "+new File(dir,name).getAbsolutePath()+" /sdcard/Download/whisperbrain-qa/"+name);
    }
    private void shell(String command)throws Exception{try(ParcelFileDescriptor pfd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(pfd)){byte[] buf=new byte[1024];while(in.read(buf)!=-1){}}}
}
