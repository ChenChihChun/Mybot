package com.mybot.app;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

/**
 * Fires weekday 08:30 — fetches today's stock recommendation from Bridge
 * and shows an Android notification with the picks.
 */
public class StockReminderReceiver extends BroadcastReceiver {

    private static final int NOTIFICATION_ID = 9200;

    @Override
    public void onReceive(Context context, Intent intent) {
        // Schedule next alarm first (exact alarms are one-shot)
        ReminderHelper.scheduleNextStockReminder(context);

        // Skip weekends
        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return;

        AppLog.i("Stock", "StockReminderReceiver: 開始取得推薦");

        // Fetch recommendation in a background thread (BroadcastReceiver has ~10s limit)
        new Thread(() -> {
            try {
                URL url = new URL("http://127.0.0.1:8765/stock/recommend");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    AppLog.w("Stock", "StockReminderReceiver: HTTP " + code);
                    return;
                }

                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                if (!json.optBoolean("success", false)) {
                    AppLog.w("Stock", "StockReminderReceiver: 無推薦資料");
                    return;
                }

                JSONObject data = json.optJSONObject("data");
                if (data == null) return;

                JSONObject rec = data.optJSONObject("data");
                if (rec == null) rec = data;

                String mood = rec.optString("market_mood", "");
                JSONArray picks = rec.optJSONArray("picks");

                StringBuilder content = new StringBuilder();
                if (picks != null && picks.length() > 0) {
                    for (int i = 0; i < picks.length(); i++) {
                        JSONObject pick = picks.getJSONObject(i);
                        String symbol = pick.optString("symbol", "");
                        String name = pick.optString("name", "");
                        if (i > 0) content.append("  ");
                        content.append(symbol).append(" ").append(name);
                    }
                } else {
                    content.append("今日無推薦標的");
                }
                if (!mood.isEmpty()) {
                    content.append(" | ").append(mood);
                }

                showNotification(context, content.toString());
                AppLog.i("Stock", "StockReminderReceiver: 通知已發送");

            } catch (Exception e) {
                AppLog.e("Stock", "StockReminderReceiver失敗: " + e.getMessage());
            }
        }).start();
    }

    private void showNotification(Context context, String text) {
        NotificationHelper.createNotificationChannel(context);

        Intent tapIntent = new Intent(context, StockActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "mybot_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("台股今日推薦")
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
