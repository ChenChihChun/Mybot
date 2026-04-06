package com.mybot.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Polls Bridge /notifications/pending every 2 minutes via AlarmManager.
 * Displays each notification as an Android notification, then ACKs back to Bridge.
 */
public class NotificationPollReceiver extends BroadcastReceiver {

    private static final int BASE_NOTIFICATION_ID = 50000;

    @Override
    public void onReceive(Context context, Intent intent) {
        // Schedule next poll first (exact alarms are one-shot)
        ReminderHelper.scheduleNextNotificationPoll(context);

        new Thread(() -> {
            try {
                // Fetch pending notifications from Bridge
                URL url = new URL("http://127.0.0.1:8765/notifications/pending");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code != 200) {
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
                if (!json.optBoolean("success", false)) return;

                JSONArray notifications = json.optJSONArray("notifications");
                if (notifications == null || notifications.length() == 0) return;

                AppLog.i("NotificationPoll", "收到 " + notifications.length() + " 則待處理通知");

                List<Integer> ackIds = new ArrayList<>();

                for (int i = 0; i < notifications.length(); i++) {
                    try {
                        JSONObject n = notifications.getJSONObject(i);
                        int id = n.optInt("id", -1);
                        String title = n.optString("title", "Mybot");
                        String content = n.optString("content", "");
                        String notifUrl = n.optString("url", "");

                        if (id > 0 && !content.isEmpty()) {
                            NotificationHelper.sendNotification(context, title, content,
                                    notifUrl.isEmpty() ? null : notifUrl);
                            ackIds.add(id);
                            AppLog.i("NotificationPoll", "顯示通知: " + title);
                        }
                    } catch (Exception e) {
                        AppLog.w("NotificationPoll", "處理通知失敗: " + e.getMessage());
                    }
                }

                // ACK delivered notifications
                if (!ackIds.isEmpty()) {
                    ackNotifications(ackIds);
                }

            } catch (java.net.ConnectException e) {
                // Bridge not running — silent
            } catch (Exception e) {
                AppLog.w("NotificationPoll", "輪詢失敗: " + e.getMessage());
            }
        }).start();
    }

    private void ackNotifications(List<Integer> ids) {
        try {
            URL url = new URL("http://127.0.0.1:8765/notifications/ack");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            JSONObject body = new JSONObject();
            JSONArray idsArray = new JSONArray();
            for (int id : ids) {
                idsArray.put(id);
            }
            body.put("ids", idsArray);

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            int code = conn.getResponseCode();
            conn.disconnect();

            if (code == 200) {
                AppLog.i("NotificationPoll", "已確認 " + ids.size() + " 則通知");
            }
        } catch (Exception e) {
            AppLog.w("NotificationPoll", "確認通知失敗: " + e.getMessage());
        }
    }
}
