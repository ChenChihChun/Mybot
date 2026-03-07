package com.mybot.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogActivity extends AppCompatActivity {

    private LinearLayout logContainer;
    private TextView countText;
    private static final int PAGE_SIZE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        LinearLayout root = UIHelper.pageRoot(this);

        // ── Top bar ──
        LinearLayout topBar = UIHelper.topBar(this, "系統日誌");

        // Copy all button
        TextView copyAllBtn = new TextView(this);
        copyAllBtn.setText("複製全部");
        copyAllBtn.setTextSize(14);
        copyAllBtn.setTextColor(UIHelper.ACCENT_BLUE);
        copyAllBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        copyAllBtn.setPadding(UIHelper.dp(this, 12), UIHelper.dp(this, 8),
                UIHelper.dp(this, 4), UIHelper.dp(this, 8));
        copyAllBtn.setOnClickListener(v -> copyAll());

        // Clear button
        TextView clearBtn = new TextView(this);
        clearBtn.setText("清除");
        clearBtn.setTextSize(14);
        clearBtn.setTextColor(UIHelper.ACCENT_RED);
        clearBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        clearBtn.setPadding(UIHelper.dp(this, 8), UIHelper.dp(this, 8),
                UIHelper.dp(this, 4), UIHelper.dp(this, 8));
        clearBtn.setOnClickListener(v -> confirmClear());

        topBar.addView(copyAllBtn);
        topBar.addView(clearBtn);

        // ── Count info ──
        countText = new TextView(this);
        countText.setTextSize(12);
        countText.setTextColor(UIHelper.TEXT_HINT);
        countText.setPadding(UIHelper.dp(this, 16), UIHelper.dp(this, 8),
                UIHelper.dp(this, 16), UIHelper.dp(this, 4));

        // ── Scroll content ──
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        logContainer = new LinearLayout(this);
        logContainer.setOrientation(LinearLayout.VERTICAL);
        int pad = UIHelper.dp(this, 10);
        logContainer.setPadding(pad, pad, pad, pad);

        scrollView.addView(logContainer);

        root.addView(topBar);
        root.addView(countText);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);

        loadLogs();
    }

    private void loadLogs() {
        logContainer.removeAllViews();

        AppLog appLog = AppLog.getInstance(this);
        List<AppLog.LogEntry> logs = appLog.query(PAGE_SIZE);
        int total = appLog.count();

        countText.setText("共 " + total + " 筆（顯示最新 " + Math.min(total, PAGE_SIZE) + " 筆，自動清除 10 天前記錄）");

        if (logs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暫無日誌記錄");
            empty.setTextSize(15);
            empty.setTextColor(UIHelper.TEXT_HINT);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, UIHelper.dp(this, 60), 0, 0);
            logContainer.addView(empty);
            return;
        }

        SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String lastDate = "";

        for (AppLog.LogEntry entry : logs) {
            String dateStr = dateFmt.format(new Date(entry.timestamp));

            // Date separator
            if (!dateStr.equals(lastDate)) {
                lastDate = dateStr;
                TextView dateHeader = new TextView(this);
                dateHeader.setText("── " + dateStr + " ──");
                dateHeader.setTextSize(12);
                dateHeader.setTextColor(UIHelper.TEXT_SECONDARY);
                dateHeader.setTypeface(Typeface.DEFAULT_BOLD);
                dateHeader.setGravity(Gravity.CENTER);
                dateHeader.setPadding(0, UIHelper.dp(this, 12), 0, UIHelper.dp(this, 6));
                logContainer.addView(dateHeader);
            }

            // Log entry card
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 10, this));
            int cp = UIHelper.dp(this, 10);
            card.setPadding(cp, UIHelper.dp(this, 8), cp, UIHelper.dp(this, 8));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(0, UIHelper.dp(this, 2), 0, UIHelper.dp(this, 2));
            card.setLayoutParams(cardLp);

            // Header row: time + level badge + tag
            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView timeView = new TextView(this);
            timeView.setText(timeFmt.format(new Date(entry.timestamp)));
            timeView.setTextSize(11);
            timeView.setTextColor(UIHelper.TEXT_HINT);
            timeView.setTypeface(Typeface.MONOSPACE);

            // Level badge
            int badgeColor;
            switch (entry.level) {
                case "ERROR": badgeColor = UIHelper.ACCENT_RED; break;
                case "WARN": badgeColor = UIHelper.ACCENT_ORANGE; break;
                default: badgeColor = UIHelper.ACCENT_BLUE; break;
            }
            TextView levelBadge = new TextView(this);
            levelBadge.setText(entry.level);
            levelBadge.setTextSize(9);
            levelBadge.setTextColor(Color.WHITE);
            levelBadge.setTypeface(Typeface.DEFAULT_BOLD);
            levelBadge.setBackground(UIHelper.roundRect(badgeColor, 6, this));
            int bh = UIHelper.dp(this, 5);
            int bv = UIHelper.dp(this, 2);
            levelBadge.setPadding(bh, bv, bh, bv);
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeLp.setMargins(UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8), 0);
            levelBadge.setLayoutParams(badgeLp);

            TextView tagView = new TextView(this);
            tagView.setText(entry.tag);
            tagView.setTextSize(11);
            tagView.setTextColor(UIHelper.ACCENT_GREEN);
            tagView.setTypeface(Typeface.DEFAULT_BOLD);

            header.addView(timeView);
            header.addView(levelBadge);
            header.addView(tagView);

            // Message
            TextView msgView = new TextView(this);
            msgView.setText(entry.message);
            msgView.setTextSize(12);
            msgView.setTextColor(UIHelper.TEXT_PRIMARY);
            msgView.setPadding(0, UIHelper.dp(this, 4), 0, 0);
            msgView.setTextIsSelectable(true);

            card.addView(header);
            card.addView(msgView);

            // Tap to copy this entry
            String formatted = entry.format();
            card.setOnClickListener(v -> {
                ClipboardManager clip = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clip.setPrimaryClip(ClipData.newPlainText("log", formatted));
                Toast.makeText(this, "已複製此筆日誌", Toast.LENGTH_SHORT).show();
            });

            logContainer.addView(card);
        }
    }

    private void copyAll() {
        List<AppLog.LogEntry> logs = AppLog.getInstance(this).query(PAGE_SIZE);
        if (logs.isEmpty()) {
            Toast.makeText(this, "無日誌可複製", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (AppLog.LogEntry e : logs) {
            sb.append(e.format()).append("\n");
        }
        ClipboardManager clip = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clip.setPrimaryClip(ClipData.newPlainText("logs", sb.toString()));
        Toast.makeText(this, "已複製 " + logs.size() + " 筆日誌", Toast.LENGTH_SHORT).show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("清除日誌")
                .setMessage("確定要清除所有日誌記錄嗎？")
                .setPositiveButton("清除", (d, w) -> {
                    AppLog.getInstance(this).clearAll();
                    loadLogs();
                    Toast.makeText(this, "已清除所有日誌", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
