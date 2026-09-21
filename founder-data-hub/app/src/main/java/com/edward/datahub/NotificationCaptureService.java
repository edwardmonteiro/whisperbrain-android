package com.edward.datahub;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotificationCaptureService extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getPackageName() == null) return;
        String category = sbn.getNotification() == null ? "" : String.valueOf(sbn.getNotification().category);
        // Privacy default: never store title, body, extras, contact names, or message content.
        new EventDb(getApplicationContext()).add("notification", sbn.getPackageName(), "category="+category);
    }
}
