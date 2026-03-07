package com.mybot.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class MusicShareReceiver extends AppCompatActivity {

    private MusicDbHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new MusicDbHelper(this);

        Intent intent = getIntent();
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            finish();
            return;
        }

        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText == null || sharedText.isEmpty()) {
            Toast.makeText(this, "\u7121\u6CD5\u8B58\u5225\u5206\u4EAB\u5167\u5BB9", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String videoId = extractVideoIdFromText(sharedText);
        if (videoId == null) {
            AppLog.w("Music", "分享內容無法識別YouTube連結: " + sharedText);
            Toast.makeText(this, "\u7121\u6CD5\u8B58\u5225 YouTube \u9023\u7D50", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Extract title from shared text (YouTube shares "Title - URL" format)
        String sharedTitle = extractTitleFromText(sharedText);
        AppLog.i("Music", "收到YouTube分享: videoId=" + videoId + " title=" + sharedTitle);

        showSaveDialog(videoId, sharedTitle);
    }

    private void showSaveDialog(String videoId, String sharedTitle) {
        List<MusicDbHelper.Category> cats = db.getAllCategories();
        List<String> catNames = new ArrayList<>();
        catNames.add("(\u7121\u5206\u985E)");
        for (MusicDbHelper.Category c : cats) {
            catNames.add((c.icon != null ? c.icon + " " : "") + c.name);
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = UIHelper.dp(this, 20);
        container.setPadding(pad, pad, pad, pad);
        container.setBackgroundColor(UIHelper.BG_PRIMARY);

        TextView titleLabel = new TextView(this);
        titleLabel.setText(sharedTitle != null ? sharedTitle : videoId);
        titleLabel.setTextSize(14);
        titleLabel.setTextColor(UIHelper.TEXT_PRIMARY);
        titleLabel.setMaxLines(3);
        titleLabel.setPadding(0, 0, 0, UIHelper.dp(this, 12));
        container.addView(titleLabel);

        TextView catLabel = new TextView(this);
        catLabel.setText("\u5206\u985E:");
        catLabel.setTextSize(13);
        catLabel.setTextColor(UIHelper.TEXT_SECONDARY);
        container.addView(catLabel);

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, catNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        container.addView(spinner);

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("\uD83C\uDFB5 \u65B0\u589E\u5230\u97F3\u6A02\u7BA1\u7406")
                .setView(container)
                .setPositiveButton("\u5132\u5B58", (d, w) -> {
                    int selectedIndex = spinner.getSelectedItemPosition();
                    long categoryId = 0;
                    if (selectedIndex > 0 && selectedIndex <= cats.size()) {
                        categoryId = cats.get(selectedIndex - 1).id;
                    }

                    // Try to get video info from API, then save
                    saveVideo(videoId, sharedTitle, categoryId);
                })
                .setNegativeButton("\u53D6\u6D88", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void saveVideo(String videoId, String fallbackTitle, long categoryId) {
        // Try API first for accurate info
        if (GoogleAuthHelper.isSignedIn(this)) {
            GoogleAuthHelper.getCachedOrFreshToken(this, (token, error) -> {
                if (token != null) {
                    YouTubeClient.getVideoInfo(token, videoId, (videos, err) -> {
                        if (videos != null && !videos.isEmpty()) {
                            YouTubeClient.VideoInfo v = videos.get(0);
                            long id = db.insertOrUpdateSong(v.videoId, v.title, v.channelTitle,
                                    v.thumbnailUrl, null, null);
                            if (categoryId > 0) db.setSongCategory(id, categoryId);
                            AppLog.i("Music", "分享新增歌曲: " + v.title + " (" + v.videoId + ")");
                            Toast.makeText(this, "\u5DF2\u65B0\u589E: " + v.title, Toast.LENGTH_SHORT).show();
                        } else {
                            saveFallback(videoId, fallbackTitle, categoryId);
                        }
                        finish();
                    });
                } else {
                    saveFallback(videoId, fallbackTitle, categoryId);
                    finish();
                }
            });
        } else {
            saveFallback(videoId, fallbackTitle, categoryId);
            finish();
        }
    }

    private void saveFallback(String videoId, String fallbackTitle, long categoryId) {
        String title = fallbackTitle != null ? fallbackTitle : "YouTube Video";
        long id = db.insertOrUpdateSong(videoId, title, "", "", null, null);
        if (categoryId > 0) db.setSongCategory(id, categoryId);
        AppLog.i("Music", "分享新增歌曲(fallback): " + title + " (" + videoId + ")");
        Toast.makeText(this, "\u5DF2\u65B0\u589E: " + title, Toast.LENGTH_SHORT).show();
    }

    private String extractVideoIdFromText(String text) {
        // YouTube shares text like "Title\nhttps://youtu.be/XXX" or just the URL
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            String vid = MusicActivity.extractVideoId(line);
            if (vid != null) return vid;
        }
        // Try the whole text as URL
        return MusicActivity.extractVideoId(text.trim());
    }

    private String extractTitleFromText(String text) {
        String[] lines = text.split("\n");
        if (lines.length > 1) {
            // First line is usually the title
            String firstLine = lines[0].trim();
            if (!firstLine.isEmpty() && !firstLine.startsWith("http")) {
                return firstLine;
            }
        }
        return null;
    }
}
