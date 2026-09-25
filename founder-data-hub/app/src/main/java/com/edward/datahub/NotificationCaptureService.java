package com.edward.datahub;

import android.app.KeyguardManager;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotificationCaptureService extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if(sbn==null||sbn.getPackageName()==null)return;

        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        boolean screen=pm!=null&&pm.isInteractive();
        boolean unlocked=screen&&(km==null||!km.isDeviceLocked());

        String category=sbn.getNotification()==null?"":String.valueOf(sbn.getNotification().category);
        String origin=unlocked?"unknown":"background";

        new EventDb(getApplicationContext()).add(
                "NOTIFICATION_RECEIVED",
                sbn.getPackageName(),
                "category="+category,
                origin,
                screen
        );
    }
}
