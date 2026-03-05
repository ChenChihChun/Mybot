package com.mybot.app;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotificationService extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // Skip our own notifications to avoid infinite loop
        if (sbn.getPackageName().equals(getPackageName())) {
            return;
        }

        String appName = getAppName(sbn);
        String title = "";
        String content = "";

        if (sbn.getNotification().extras != null) {
            CharSequence titleCs = sbn.getNotification().extras.getCharSequence("android.title");
            CharSequence textCs = sbn.getNotification().extras.getCharSequence("android.text");
            if (titleCs != null) {
                title = titleCs.toString();
            }
            if (textCs != null) {
                content = textCs.toString();
            }
        }

        String message = "[通知] " + appName + ": " + title + " - " + content;
        NotificationHelper.sendNotification(this, "Mybot - 通知監聽", message);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // No action needed
    }

    private String getAppName(StatusBarNotification sbn) {
        try {
            return getPackageManager()
                    .getApplicationLabel(
                            getPackageManager().getApplicationInfo(sbn.getPackageName(), 0))
                    .toString();
        } catch (Exception e) {
            return sbn.getPackageName();
        }
    }
}
