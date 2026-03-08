package com.mybot.app;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import android.app.NotificationManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TodoReminderReceiver extends BroadcastReceiver {

    private static final int TODO_NOTIFICATION_BASE = 7000;

    @Override
    public void onReceive(Context context, Intent intent) {
        // Reschedule next day (exact alarms are one-shot)
        ReminderHelper.scheduleTodoCheck(context);

        NotificationHelper.createNotificationChannel(context);

        TodoDbHelper db = new TodoDbHelper(context);
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd", Locale.getDefault());

        // Check overdue
        List<TodoDbHelper.Todo> overdue = db.queryOverdue();
        if (!overdue.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(overdue.size(), 5); i++) {
                if (i > 0) sb.append("、");
                sb.append(overdue.get(i).title);
            }
            if (overdue.size() > 5) sb.append("... 等").append(overdue.size()).append("項");

            sendTodoNotification(context, "Mybot - 待辦已逾期",
                    overdue.size() + " 項待辦已過期: " + sb.toString(),
                    TODO_NOTIFICATION_BASE);
        }

        // Check due soon (within 3 days)
        List<TodoDbHelper.Todo> upcoming = db.queryUpcoming(3);
        if (!upcoming.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(upcoming.size(), 5); i++) {
                TodoDbHelper.Todo t = upcoming.get(i);
                if (i > 0) sb.append("、");
                sb.append(t.title).append("(").append(sdf.format(new Date(t.deadline))).append(")");
            }
            if (upcoming.size() > 5) sb.append("... 等").append(upcoming.size()).append("項");

            sendTodoNotification(context, "Mybot - 待辦即將到期",
                    upcoming.size() + " 項待辦即將到期: " + sb.toString(),
                    TODO_NOTIFICATION_BASE + 1);
        }
    }

    private void sendTodoNotification(Context context, String title, String text, int notifId) {
        Intent tapIntent = new Intent(context, TodoActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, notifId, tapIntent,
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
            manager.notify(notifId, builder.build());
        }
    }
}
