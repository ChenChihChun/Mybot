package com.mybot.app;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;
import java.util.Locale;

public class WaterReminderReceiver extends BroadcastReceiver {

    private static final int NOTIFICATION_ID = 8900;

    @Override
    public void onReceive(Context context, Intent intent) {
        // Check active hours
        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int startHour = WaterDbHelper.getRemindStartHour(context);
        int endHour = WaterDbHelper.getRemindEndHour(context);
        if (currentHour < startHour || currentHour >= endHour) {
            return;
        }

        // Check if goal already reached
        WaterDbHelper db = new WaterDbHelper(context);
        int todayTotal = db.getTodayTotal();
        int goal = WaterDbHelper.getGoal(context);
        if (todayTotal >= goal) {
            return;
        }

        NotificationHelper.createNotificationChannel(context);

        String text = String.format(Locale.US,
                "\uD83D\uDCA7 \u8A72\u559D\u6C34\u4E86\uFF01\u4ECA\u65E5\u5DF2\u559D %d/%d ml", todayTotal, goal);

        Intent tapIntent = new Intent(context, WaterActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "mybot_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Mybot - \u559D\u6C34\u63D0\u9192")
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, builder.build());
        }
    }
}
