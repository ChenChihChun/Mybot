package com.mybot.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApodActivity extends AppCompatActivity {

    private LinearLayout listContainer;
    private TextView statusLabel;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<String, Bitmap> imageCache = new ConcurrentHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "\uD83C\uDF0C \u5929\u6587\u6BCF\u65E5\u4E00\u5716");
        TextView backBtn = new TextView(this);
        backBtn.setText("\u2190");
        backBtn.setTextSize(22);
        backBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        backBtn.setPadding(0, 0, UIHelper.dp(this, 16), 0);
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn, 0);

        // Auto-wallpaper toggle button
        TextView wallpaperToggle = new TextView(this);
        updateWallpaperToggle(wallpaperToggle);
        wallpaperToggle.setTextSize(12);
        wallpaperToggle.setPadding(UIHelper.dp(this, 12), UIHelper.dp(this, 6),
                UIHelper.dp(this, 12), UIHelper.dp(this, 6));
        wallpaperToggle.setOnClickListener(v -> {
            boolean enabled = ReminderHelper.isApodWallpaperEnabled(this);
            if (enabled) {
                ReminderHelper.cancelApodWallpaper(this);
                AppLog.i("APOD", "自動桌布已關閉");
            } else {
                ReminderHelper.scheduleApodWallpaper(this);
                AppLog.i("APOD", "自動桌布已開啟，每日08:00更換");
            }
            updateWallpaperToggle(wallpaperToggle);
        });
        topBar.addView(wallpaperToggle);

        // Manual "change wallpaper now" button
        TextView wallpaperNow = new TextView(this);
        wallpaperNow.setText("\uD83C\uDFA8 \u7ACB\u5373\u63DB"); // 🎨 立即換
        wallpaperNow.setTextSize(12);
        wallpaperNow.setTextColor(UIHelper.ACCENT_PURPLE);
        wallpaperNow.setPadding(UIHelper.dp(this, 8), UIHelper.dp(this, 6),
                UIHelper.dp(this, 12), UIHelper.dp(this, 6));
        wallpaperNow.setOnClickListener(v -> {
            Toast.makeText(this, "開始更換桌布\u2026", Toast.LENGTH_SHORT).show();
            AppLog.i("APOD", "使用者手動觸發更換桌布");
            ApodWallpaperReceiver.changeWallpaperAsync(getApplicationContext(), "手動",
                    (success, msg) -> mainHandler.post(() ->
                            Toast.makeText(ApodActivity.this,
                                    (success ? "\u2705 " : "\u26A0\uFE0F ") + msg,
                                    Toast.LENGTH_LONG).show()));
        });
        topBar.addView(wallpaperNow);

        root.addView(topBar);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 16);
        content.setPadding(p, p, p, p);

        statusLabel = new TextView(this);
        statusLabel.setText("\u8F09\u5165\u4E2D...");
        statusLabel.setTextSize(13);
        statusLabel.setTextColor(UIHelper.TEXT_HINT);
        statusLabel.setGravity(Gravity.CENTER);
        statusLabel.setPadding(0, UIHelper.dp(this, 40), 0, 0);
        content.addView(statusLabel);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer);

        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        fetchApodHistory();
    }

    private void fetchApodHistory() {
        executor.execute(() -> {
            try {
                URL url = new URL("http://127.0.0.1:8765/apod/history");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    mainHandler.post(() -> statusLabel.setText("\u8F09\u5165\u5931\u6557 (HTTP " + code + ")"));
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
                    mainHandler.post(() -> statusLabel.setText("\u8F09\u5165\u5931\u6557"));
                    return;
                }

                JSONObject data = json.getJSONObject("data");
                JSONArray entries = data.getJSONArray("entries");
                mainHandler.post(() -> displayEntries(entries));

            } catch (java.net.ConnectException e) {
                mainHandler.post(() -> statusLabel.setText("Bridge \u672A\u9023\u7DDA"));
            } catch (Exception e) {
                AppLog.w("APOD", "\u8F09\u5165\u5931\u6557: " + e.getMessage());
                mainHandler.post(() -> statusLabel.setText("\u8F09\u5165\u5931\u6557"));
            }
        });
    }

    private void displayEntries(JSONArray entries) {
        listContainer.removeAllViews();
        if (entries.length() == 0) {
            statusLabel.setText("\u9084\u6C92\u6709 APOD \u8CC7\u6599");
            return;
        }
        statusLabel.setText("\u5171 " + entries.length() + " \u7BC7\u5929\u6587\u65E5\u5716");

        for (int i = 0; i < entries.length(); i++) {
            try {
                JSONObject entry = entries.getJSONObject(i);
                listContainer.addView(buildApodCard(entry));
            } catch (Exception e) {
                AppLog.w("APOD", "\u5EFA\u7ACB\u5361\u7247\u5931\u6557: " + e.getMessage());
            }
        }
    }

    private LinearLayout buildApodCard(JSONObject entry) {
        String title = entry.optString("title", "").replace("\uD83C\uDF0C ", "");
        String summary = entry.optString("summary", "");
        String date = entry.optString("date", "");
        String imageUrl = entry.optString("image_url", "");
        String sourceUrl = entry.optString("source_url", "");

        LinearLayout card = UIHelper.card(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, UIHelper.dp(this, 12));
        card.setLayoutParams(cardLp);

        // Image
        if (!imageUrl.isEmpty()) {
            ImageView imageView = new ImageView(this);
            int imgHeight = UIHelper.dp(this, 200);
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, imgHeight);
            imgLp.setMargins(0, 0, 0, UIHelper.dp(this, 10));
            imageView.setLayoutParams(imgLp);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setBackgroundColor(UIHelper.BG_INPUT);

            // Rounded corners
            GradientDrawable clipShape = new GradientDrawable();
            clipShape.setCornerRadius(UIHelper.dp(this, 10));
            imageView.setClipToOutline(true);
            imageView.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                            UIHelper.dp(ApodActivity.this, 10));
                }
            });

            loadImage(imageView, imageUrl);
            card.addView(imageView);
        }

        // Header row: date + 查看原圖
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView dateView = new TextView(this);
        dateView.setText(date);
        dateView.setTextSize(11);
        dateView.setTextColor(UIHelper.TEXT_HINT);
        dateView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(dateView);

        if (!imageUrl.isEmpty()) {
            TextView viewOriginal = new TextView(this);
            viewOriginal.setText("\uD83D\uDD17 \u67E5\u770B\u539F\u5716");
            viewOriginal.setTextSize(12);
            viewOriginal.setTextColor(UIHelper.ACCENT_BLUE);
            viewOriginal.setOnClickListener(v -> openUrl(imageUrl));
            header.addView(viewOriginal);
        }

        card.addView(header);

        // Title
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(15);
        titleView.setTextColor(UIHelper.TEXT_PRIMARY);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleView.setPadding(0, UIHelper.dp(this, 6), 0, UIHelper.dp(this, 4));
        card.addView(titleView);

        // Summary
        if (!summary.isEmpty()) {
            TextView summaryView = new TextView(this);
            summaryView.setText(summary);
            summaryView.setTextSize(13);
            summaryView.setTextColor(UIHelper.TEXT_SECONDARY);
            summaryView.setLineSpacing(UIHelper.dp(this, 2), 1f);
            card.addView(summaryView);
        }

        // Source link
        if (!sourceUrl.isEmpty()) {
            TextView srcView = new TextView(this);
            srcView.setText("APOD \u539F\u59CB\u9801\u9762 \u2197");
            srcView.setTextSize(11);
            srcView.setTextColor(UIHelper.ACCENT_PURPLE);
            srcView.setPadding(0, UIHelper.dp(this, 8), 0, 0);
            srcView.setOnClickListener(v -> openUrl(sourceUrl));
            card.addView(srcView);
        }

        return card;
    }

    private void updateWallpaperToggle(TextView toggle) {
        boolean enabled = ReminderHelper.isApodWallpaperEnabled(this);
        if (enabled) {
            toggle.setText("\uD83C\uDF05 \u81EA\u52D5\u684C\u5E03 ON");
            toggle.setTextColor(UIHelper.ACCENT_GREEN);
        } else {
            toggle.setText("\uD83C\uDF05 \u81EA\u52D5\u684C\u5E03 OFF");
            toggle.setTextColor(UIHelper.TEXT_HINT);
        }
    }

    private void openUrl(String url) {
        if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }
    }

    private void loadImage(ImageView imageView, String url) {
        Bitmap cached = imageCache.get(url);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        executor.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                InputStream is = conn.getInputStream();

                // First pass: get dimensions
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                byte[] bytes = readAllBytes(is);
                is.close();
                conn.disconnect();

                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                int targetWidth = 800;
                int sampleSize = 1;
                if (opts.outWidth > targetWidth) {
                    sampleSize = Math.round((float) opts.outWidth / targetWidth);
                }

                // Second pass: decode scaled
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = sampleSize;
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);

                if (bmp != null) {
                    imageCache.put(url, bmp);
                    mainHandler.post(() -> imageView.setImageBitmap(bmp));
                }
            } catch (Exception e) {
                AppLog.w("APOD", "\u5716\u7247\u8F09\u5165\u5931\u6557: " + e.getMessage());
            }
        });
    }

    private static final int MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10 MB

    private byte[] readAllBytes(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        int total = 0;
        while ((n = is.read(buf)) != -1) {
            total += n;
            if (total > MAX_IMAGE_SIZE) throw new Exception("Image too large");
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
