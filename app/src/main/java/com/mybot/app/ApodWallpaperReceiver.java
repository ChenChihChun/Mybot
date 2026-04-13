package com.mybot.app;

import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Daily receiver (08:00) — fetches the latest APOD image from Bridge
 * and sets it as the device wallpaper (home screen).
 */
public class ApodWallpaperReceiver extends BroadcastReceiver {

    private static final int MAX_IMAGE_SIZE = 15 * 1024 * 1024; // 15 MB

    @Override
    public void onReceive(Context context, Intent intent) {
        // Schedule next alarm first (exact alarms are one-shot)
        ReminderHelper.scheduleNextApodWallpaper(context);

        AppLog.i("APOD", "ApodWallpaperReceiver: 開始更換桌布");

        new Thread(() -> {
            try {
                // Step 1: Fetch latest APOD entry from Bridge
                URL url = new URL("http://127.0.0.1:8765/apod/history");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    AppLog.w("APOD", "ApodWallpaperReceiver: Bridge HTTP " + code);
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
                    AppLog.w("APOD", "ApodWallpaperReceiver: 請求失敗");
                    return;
                }

                JSONObject data = json.optJSONObject("data");
                if (data == null) return;

                JSONArray entries = data.optJSONArray("entries");
                if (entries == null || entries.length() == 0) {
                    AppLog.w("APOD", "ApodWallpaperReceiver: 無APOD資料");
                    return;
                }

                // Get the latest entry
                JSONObject latest = entries.getJSONObject(0);
                String imageUrl = latest.optString("image_url", "");
                String title = latest.optString("title", "");
                String date = latest.optString("date", "");

                if (imageUrl.isEmpty()) {
                    AppLog.w("APOD", "ApodWallpaperReceiver: 無圖片URL");
                    return;
                }

                // Skip video entries (MP4)
                if (imageUrl.endsWith(".mp4") || imageUrl.endsWith(".webm")) {
                    AppLog.i("APOD", "ApodWallpaperReceiver: 今日為影片，跳過桌布更換 (" + date + ")");
                    return;
                }

                // Step 2: Download the image
                AppLog.i("APOD", "ApodWallpaperReceiver: 下載圖片 " + date + " - " + title);
                Bitmap bitmap = downloadImage(imageUrl);
                if (bitmap == null) {
                    AppLog.w("APOD", "ApodWallpaperReceiver: 圖片下載失敗");
                    return;
                }

                // Step 3: Set as wallpaper (home screen only)
                WallpaperManager wm = WallpaperManager.getInstance(context);
                wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM);
                bitmap.recycle();

                AppLog.i("APOD", "ApodWallpaperReceiver: 桌布已更換 — " + date + " " + title);

            } catch (java.net.ConnectException e) {
                AppLog.w("APOD", "ApodWallpaperReceiver: Bridge未運行");
            } catch (Exception e) {
                AppLog.e("APOD", "ApodWallpaperReceiver失敗: " + e.getMessage());
            }
        }).start();
    }

    private Bitmap downloadImage(String imageUrl) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(imageUrl).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);

            InputStream is = conn.getInputStream();
            byte[] bytes = readAllBytes(is);
            is.close();
            conn.disconnect();

            if (bytes.length == 0) return null;

            // Decode with appropriate scaling for wallpaper
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);

            // Scale down if image is very large (> 4096px wide)
            int sampleSize = 1;
            if (opts.outWidth > 4096) {
                sampleSize = Math.round((float) opts.outWidth / 4096);
            }

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSize;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);

        } catch (Exception e) {
            AppLog.w("APOD", "圖片下載異常: " + e.getMessage());
            return null;
        }
    }

    private byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int len;
        while ((len = is.read(buffer)) != -1) {
            total += len;
            if (total > MAX_IMAGE_SIZE) {
                throw new RuntimeException("圖片超過 " + (MAX_IMAGE_SIZE / 1024 / 1024) + "MB 限制");
            }
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }
}
