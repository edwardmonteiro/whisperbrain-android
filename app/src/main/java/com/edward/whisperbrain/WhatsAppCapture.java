package com.edward.whisperbrain;

import android.app.NotificationManager;
import android.content.*;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Local opt-in, admission cutoff and queue cancellation share a lock with the final database write. */
public final class WhatsAppCapture {
    static final Object LOCK=new Object();
    private static volatile boolean connected;
    private static final AtomicLong revision=new AtomicLong();
    private WhatsAppCapture(){}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("whatsapp_capture",Context.MODE_PRIVATE);}
    public static final class Admission {
        final String generation;final long since;final boolean enabled,business;
        Admission(SharedPreferences p){generation=p.getString("generation","");since=p.getLong("since",0);enabled=p.getBoolean("enabled",false);business=p.getBoolean("business",false);}
    }
    static Admission admission(Context c){synchronized(LOCK){return new Admission(prefs(c));}}
    public static boolean enabled(Context c){return admission(c).enabled;}
    public static boolean business(Context c){return admission(c).business;}
    public static boolean connected(){return connected;}
    public static long revision(){return revision.get();}
    public static ComponentName component(Context c){return new ComponentName(c,WhatsAppNotificationService.class);}
    public static boolean permitted(Context c){return c.getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(component(c));}
    public static void setEnabled(Context c,boolean enabled)throws Exception{
        synchronized(LOCK){if(!prefs(c).edit().putBoolean("enabled",enabled).putLong("since",System.currentTimeMillis()).putString("generation",UUID.randomUUID().toString()).putString("error","").commit())throw new Exception("Não foi possível salvar a preferência.");revision.incrementAndGet();}
        if(enabled)reconnect(c);
    }
    public static void setBusiness(Context c,boolean business)throws Exception{
        synchronized(LOCK){if(!prefs(c).edit().putBoolean("business",business).putLong("since",System.currentTimeMillis()).putString("generation",UUID.randomUUID().toString()).commit())throw new Exception("Não foi possível salvar a preferência.");revision.incrementAndGet();}
    }
    public static void reconnect(Context c){if(enabled(c)&&permitted(c)&&!connected)try{NotificationListenerService.requestRebind(component(c));}catch(RuntimeException e){error(c,"O Android ainda não conectou a captura. Confira a permissão.");}}
    static void listenerConnected(Context c){synchronized(LOCK){
        connected=false;
        if(!prefs(c).edit().putString("generation",UUID.randomUUID().toString()).commit()){error(c,"Não foi possível iniciar a captura local.");return;}
        connected=true;revision.incrementAndGet();
    }}
    static void listenerDisconnected(){synchronized(LOCK){connected=false;revision.incrementAndGet();}}
    static boolean accepts(Context c,Admission ticket){Admission now=new Admission(prefs(c));return ticket.enabled&&now.enabled&&connected&&ticket.generation.equals(now.generation)&&permitted(c);}
    static void process(Context c,StatusBarNotification sbn,Admission ticket,long receivedAt){
        try{
            List<WhatsAppNotice> messages=WhatsAppNotificationParser.parse(sbn,ticket.since,receivedAt,ticket.business);
            synchronized(LOCK){if(!accepts(c,ticket))return;
                int saved=NotebookStore.get(c).captureNotifications(messages,ticket.since,receivedAt);
                SharedPreferences p=prefs(c);SharedPreferences.Editor edit=p.edit().putLong("received",p.getLong("received",0)+1).putLong("last_received",receivedAt);
                if(saved>0)edit.putLong("saved",p.getLong("saved",0)+saved).putLong("last_saved",receivedAt).putString("error","");
                else edit.putLong("ignored",p.getLong("ignored",0)+1);
                edit.apply();revision.incrementAndGet();
            }
        }catch(Exception e){error(c,"Uma notificação não pôde ser salva. Confira o espaço disponível no telefone.");}
    }
    static void error(Context c,String message){synchronized(LOCK){prefs(c).edit().putString("error",message).apply();revision.incrementAndGet();}}
    public static String status(Context c){if(!enabled(c))return "Captura pausada";if(!permitted(c))return "Aguardando permissão do Android";if(!connected)return "Aguardando conexão do Android";return "Capturando novas mensagens";}
    public static String diagnostics(Context c){SharedPreferences p=prefs(c);return "WhisperBrain 0.4.0-alpha\nWhatsApp: "+status(c)+"\nAtivado: "+enabled(c)+"\nPermissão: "+permitted(c)+"\nConectado: "+connected+"\nWhatsApp Business: "+business(c)+"\nNotificações processadas: "+p.getLong("received",0)+"\nMensagens salvas: "+p.getLong("saved",0)+"\nSem mensagem nova aproveitável: "+p.getLong("ignored",0)+"\nAviso: "+p.getString("error","");}
}
