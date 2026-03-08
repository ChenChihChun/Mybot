package com.mybot.app;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import android.app.NotificationManager;

public class FitnessReminderReceiver extends BroadcastReceiver {

    private static final int NOTIFICATION_ID = 8800;

    @Override
    public void onReceive(Context context, Intent intent) {
        // Reschedule next day (exact alarms are one-shot)
        ReminderHelper.scheduleNextFitnessReminder(context);

        NotificationHelper.createNotificationChannel(context);

        FitnessDbHelper db = new FitnessDbHelper(context);
        FitnessDbHelper.WorkoutLog todayLog = db.getTodayLog();
        boolean done = todayLog != null && todayLog.exercisesDone > 0;

        String title = "Mybot - 運動時間到了！";
        String text = done
                ? "今天已完成運動，做得好！繼續保持！"
                : "今天還沒運動，快來完成今日訓練吧！";

        int streak = db.getStreak();
        if (!done && streak > 0) {
            text += String.format(" (目前連續 %d 天，不要中斷！)", streak);
        }

        Intent tapIntent = new Intent(context, FitnessActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, tapIntent,
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
            manager.notify(NOTIFICATION_ID, builder.build());
        }
    }
}
