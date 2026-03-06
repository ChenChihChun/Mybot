package com.mybot.app;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashSet;
import java.util.Set;

public class NotificationService extends NotificationListenerService {

    private final Set<String> seenKeys = new HashSet<>();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn.getPackageName().equals(getPackageName())) {
            return;
        }

        // Dedup: same notification key only processed once
        String notiKey = sbn.getKey();
        if (seenKeys.contains(notiKey)) {
            return;
        }
        seenKeys.add(notiKey);

        // Prevent memory leak: cap at 500
        if (seenKeys.size() > 500) {
            seenKeys.clear();
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

        // Log and analyze (no push notification for non-expense)
        NotificationLog log = new NotificationLog(appName, title, content, "通知");
        MonitorActivity.logs.add(log);
        sendBroadcast(new Intent(MonitorActivity.ACTION_NEW_LOG));

        String rawText = title + " " + content;
        analyzeAndStore(log, rawText);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Remove from seen so if same notification reappears later it gets processed
        seenKeys.remove(sbn.getKey());
    }

    private void analyzeAndStore(NotificationLog log, String rawText) {
        BridgeClient.analyze(log, result -> {
            sendBroadcast(new Intent(MonitorActivity.ACTION_NEW_LOG));
            if (result.analyzed && !result.offline
                    && result.isExpense && result.confidence >= 0.7) {
                ExpenseDbHelper db = new ExpenseDbHelper(this);
                db.insert(result.amount, result.currency, result.category,
                        result.merchant, result.description, "通知", rawText);
                String pushMsg = String.format("已記錄消費: %s $%.0f [%s]",
                        result.merchant, result.amount, result.category);
                NotificationHelper.sendNotification(this, "Mybot - 消費記錄", pushMsg);
            }
        });
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
