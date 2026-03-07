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
            NotificationHelper.createNotificationChannel(context);
            NotificationHelper.sendNotification(context, "Mybot", "Mybot 已隨開機啟動");
            restoreAlarms(context);
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            NotificationHelper.createNotificationChannel(context);
            NotificationHelper.sendNotification(context, "Mybot", "Mybot 已更新，提醒已恢復");
            restoreAlarms(context);
        }
    }

    private void restoreAlarms(Context context) {
        ReminderHelper.restoreIfEnabled(context);
        ReminderHelper.scheduleTodoCheck(context);
        ReminderHelper.restoreFitnessIfEnabled(context);
    }
}
