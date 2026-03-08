package com.mybot.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationHelper {

    private static final String CHANNEL_ID = "mybot_channel";
    private static final String CHANNEL_NAME = "Mybot Notifications";
    private static int notificationId = 0;

    public static void createNotificationChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Mybot notification and SMS alerts");
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    public static void sendNotification(Context context, String title, String text) {
        createNotificationChannel(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(notificationId++, builder.build());
        }
    }

    public static void sendExpenseNotification(Context context, String merchant, double amount, String category) {
        sendExpenseNotification(context, merchant, amount, category, -1);
    }

    public static void sendExpenseNotification(Context context, String merchant, double amount, String category, long expenseId) {
        String title = "截圖記帳";
        String text = String.format(java.util.Locale.getDefault(),
                "%s $%.0f", merchant.isEmpty() ? "消費" : merchant, amount);
        if (category != null && !category.isEmpty()) text += " (" + category + ")";

        if (expenseId > 0) {
            createNotificationChannel(context);
            Intent intent = new Intent(context, AddExpenseActivity.class);
            intent.putExtra("expense_id", expenseId);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(context, (int) expenseId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(text + " (點擊編輯)")
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text + "\n點擊可編輯此筆消費"))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pi)
                    .setAutoCancel(true);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(notificationId++, builder.build());
            }
        } else {
            sendNotification(context, title, text);
        }
    }
}
