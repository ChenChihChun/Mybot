package com.mybot.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            // Create notification channel on boot
            NotificationHelper.createNotificationChannel(context);
            NotificationHelper.sendNotification(context, "Mybot", "Mybot 已隨開機啟動");
            // Restore daily reminder alarm
            ReminderHelper.restoreIfEnabled(context);
            // Restore TODO deadline check
            ReminderHelper.scheduleTodoCheck(context);
        }
    }
}
