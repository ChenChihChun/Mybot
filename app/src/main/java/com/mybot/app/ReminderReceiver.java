package com.mybot.app;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import android.app.NotificationManager;

public class ReminderReceiver extends BroadcastReceiver {

    private static final int REMINDER_NOTIFICATION_ID = 9999;

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationHelper.createNotificationChannel(context);

        ExpenseDbHelper db = new ExpenseDbHelper(context);
        boolean hasToday = db.hasExpenseToday();

        String title = "Mybot - 每日記帳提醒";
        String text = hasToday ? "今天已有記帳紀錄，記得確認是否完整" : "今天還沒有記帳，快來記錄今天的花費吧！";

        Intent tapIntent = new Intent(context, ExpenseActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "mybot_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(REMINDER_NOTIFICATION_ID, builder.build());
        }
    }
}
