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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Fires daily at 21:30 — fetches AI knowledge discoveries from Bridge
 * and imports them into the local Knowledge database.
 */
public class KnowledgeSyncReceiver extends BroadcastReceiver {

    private static final int NOTIFICATION_ID = 9300;

    @Override
    public void onReceive(Context context, Intent intent) {
        // Schedule next alarm first (exact alarms are one-shot)
        ReminderHelper.scheduleNextKnowledgeSync(context);

        AppLog.i("Knowledge", "KnowledgeSyncReceiver: 開始同步AI知識");

        new Thread(() -> {
            try {
                // Step 1: Fetch unsynced discoveries from Bridge
                URL url = new URL("http://127.0.0.1:8765/knowledge/discoveries");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    AppLog.w("Knowledge", "KnowledgeSyncReceiver: HTTP " + code);
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
                    AppLog.w("Knowledge", "KnowledgeSyncReceiver: 請求失敗");
                    return;
                }

                JSONObject data = json.optJSONObject("data");
                if (data == null) return;

                JSONArray entries = data.optJSONArray("entries");
                if (entries == null || entries.length() == 0) {
                    AppLog.i("Knowledge", "KnowledgeSyncReceiver: 無新知識");
                    return;
                }

                // Step 2: Import entries into local Knowledge DB
                KnowledgeDbHelper dbHelper = new KnowledgeDbHelper(context);
                List<Integer> syncedIds = new ArrayList<>();
                int imported = 0;

                for (int i = 0; i < entries.length(); i++) {
                    try {
                        JSONObject entry = entries.getJSONObject(i);
                        int entryId = entry.optInt("id", -1);
                        String title = entry.optString("title", "");
                        String summary = entry.optString("summary", "");
                        String keyPoints = entry.optString("key_points", "");
                        String sourceUrl = entry.optString("source_url", "");
                        String category = entry.optString("category", "科技");

                        if (title.isEmpty() || summary.isEmpty()) continue;

                        long result = dbHelper.insert(title, summary, keyPoints, sourceUrl, category);
                        if (result != -1) {
                            imported++;
                        }
                        if (entryId > 0) {
                            syncedIds.add(entryId);
                        }
                    } catch (Exception e) {
                        AppLog.w("Knowledge", "匯入知識條目失敗: " + e.getMessage());
                    }
                }

                AppLog.i("Knowledge", "KnowledgeSyncReceiver: 匯入 " + imported + " 則知識");

                // Step 3: Mark entries as synced on Bridge
                if (!syncedIds.isEmpty()) {
                    markSynced(syncedIds);
                }

                // Step 4: Show notification
                if (imported > 0) {
                    showNotification(context, "今日新增 " + imported + " 則 AI 前沿知識");
                }

            } catch (java.net.ConnectException e) {
                AppLog.w("Knowledge", "KnowledgeSyncReceiver: Bridge未運行");
            } catch (Exception e) {
                AppLog.e("Knowledge", "KnowledgeSyncReceiver失敗: " + e.getMessage());
            }
        }).start();
    }

    private void markSynced(List<Integer> ids) {
        try {
            URL url = new URL("http://127.0.0.1:8765/knowledge/discoveries/synced");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

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
                AppLog.i("Knowledge", "已標記 " + ids.size() + " 則為已同步");
            } else {
                AppLog.w("Knowledge", "標記同步失敗: HTTP " + code);
            }
        } catch (Exception e) {
            AppLog.w("Knowledge", "標記同步異常: " + e.getMessage());
        }
    }

    private void showNotification(Context context, String text) {
        NotificationHelper.createNotificationChannel(context);

        Intent tapIntent = new Intent(context, KnowledgeActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "mybot_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🧠 AI 知識更新")
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
