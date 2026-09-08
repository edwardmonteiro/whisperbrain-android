package com.edward.whisperbrain;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.concurrent.*;

/** System-bound listener. It never reads the WhatsApp database, sends replies or calls an AI API. */
public final class WhatsAppNotificationService extends NotificationListenerService {
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(128),r->new Thread(r,"whatsapp-notifications"));
    @Override public void onListenerConnected(){super.onListenerConnected();worker.getQueue().clear();WhatsAppCapture.listenerConnected(this);}
    @Override public void onListenerDisconnected(){WhatsAppCapture.listenerDisconnected();worker.getQueue().clear();super.onListenerDisconnected();}
    @Override public void onNotificationPosted(StatusBarNotification sbn){
        WhatsAppCapture.Admission ticket=WhatsAppCapture.admission(this);
        if(sbn==null||!ticket.enabled||!WhatsAppNotice.allowed(sbn.getPackageName(),ticket.business))return;
        long now=System.currentTimeMillis();
        try{worker.execute(()->WhatsAppCapture.process(getApplicationContext(),sbn,ticket,now));}
        catch(RejectedExecutionException e){WhatsAppCapture.error(this,"A fila ficou cheia; uma notificação foi ignorada.");}
    }
    @Override public void onDestroy(){WhatsAppCapture.listenerDisconnected();worker.shutdownNow();super.onDestroy();}
}
