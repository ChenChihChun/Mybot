package com.mybot.app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NotificationHelper.createNotificationChannel(this);
        requestPermissions();
        ReminderHelper.restoreIfEnabled(this);
        ReminderHelper.scheduleTodoCheck(this);
        ReminderHelper.restoreFitnessIfEnabled(this);

        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        LinearLayout root = UIHelper.pageRoot(this);

        // ── Hero area ──
        LinearLayout topSection = new LinearLayout(this);
        topSection.setOrientation(LinearLayout.VERTICAL);
        topSection.setGravity(Gravity.CENTER);
        topSection.setBackgroundColor(UIHelper.BG_TOP_BAR);
        int p = UIHelper.dp(this, 24);
        topSection.setPadding(p, UIHelper.dp(this, 16), p, UIHelper.dp(this, 14));
        topSection.setElevation(UIHelper.dp(this, 4));

        // Compact hero: icon + text in horizontal row
        LinearLayout heroRow = new LinearLayout(this);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        heroRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView iconCircle = new TextView(this);
        iconCircle.setText("\uD83E\uDD16");
        iconCircle.setTextSize(28);
        iconCircle.setGravity(Gravity.CENTER);
        iconCircle.setBackground(UIHelper.roundRect(Color.parseColor("#1B3A4B"), 18, this));
        iconCircle.setElevation(UIHelper.dp(this, 4));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                UIHelper.dp(this, 52), UIHelper.dp(this, 52));
        iconLp.setMargins(0, 0, UIHelper.dp(this, 14), 0);
        iconCircle.setLayoutParams(iconLp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Mybot");
        title.setTextSize(24);
        title.setTextColor(UIHelper.TEXT_PRIMARY);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setLetterSpacing(0.04f);

        TextView subtitle = new TextView(this);
        subtitle.setText("Smart Notification Assistant");
        subtitle.setTextSize(12);
        subtitle.setTextColor(UIHelper.TEXT_SECONDARY);
        subtitle.setPadding(0, UIHelper.dp(this, 2), 0, 0);
        subtitle.setLetterSpacing(0.03f);

        textCol.addView(title);
        textCol.addView(subtitle);
        heroRow.addView(iconCircle);
        heroRow.addView(textCol);
        topSection.addView(heroRow);

        // ── Scrollable content ──
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int cp = UIHelper.dp(this, 16);
        content.setPadding(cp, UIHelper.dp(this, 16), cp, cp);

        // ── Status card ──
        LinearLayout statusCard = UIHelper.card(this);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView dot = new TextView(this);
        dot.setBackground(UIHelper.roundRect(UIHelper.ACCENT_GREEN, 20, this));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                UIHelper.dp(this, 10), UIHelper.dp(this, 10));
        dotLp.setMargins(0, 0, UIHelper.dp(this, 12), 0);
        dot.setLayoutParams(dotLp);

        TextView statusText = new TextView(this);
        statusText.setText("監聽服務運行中");
        statusText.setTextSize(15);
        statusText.setTextColor(UIHelper.ACCENT_GREEN);
        statusText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        statusRow.addView(dot);
        statusRow.addView(statusText);

        TextView statusHint = new TextView(this);
        statusHint.setText("通知與簡訊監聽已啟動，AI 自動分析運行中");
        statusHint.setTextSize(12);
        statusHint.setTextColor(UIHelper.TEXT_SECONDARY);
        statusHint.setPadding(0, UIHelper.dp(this, 6), 0, 0);

        statusCard.addView(statusRow);
        statusCard.addView(statusHint);
        content.addView(statusCard);

        // ── Feature grid ──
        content.addView(UIHelper.sectionHeader(this, "FEATURES"));

        // Row 1: 消費紀錄 + 待辦事項
        LinearLayout row1 = gridRow();
        LinearLayout cardExpense = UIHelper.featureCard(this,
                "\uD83D\uDCB0", "消費紀錄", "消費明細·報表·提醒", UIHelper.ACCENT_RED, 40);
        cardExpense.setOnClickListener(v -> startActivity(new Intent(this, ExpenseActivity.class)));

        LinearLayout cardTodo = UIHelper.featureCard(this,
                "\u2705", "待辦事項", "TODO·時間管理", UIHelper.ACCENT_GREEN, 40);
        cardTodo.setOnClickListener(v -> startActivity(new Intent(this, TodoActivity.class)));

        row1.addView(cardExpense, gridCellLp(0));
        row1.addView(cardTodo, gridCellLp(UIHelper.dp(this, 10)));
        content.addView(row1);

        // Row 2: Google 日曆 + 健身教練
        LinearLayout row2 = gridRow();
        LinearLayout cardCalendar = UIHelper.featureCard(this,
                "\uD83D\uDCC5", "Google 日曆", "AI 行事曆管理", UIHelper.ACCENT_BLUE, 40);
        cardCalendar.setOnClickListener(v -> startActivity(new Intent(this, CalendarActivity.class)));

        LinearLayout cardFitness = UIHelper.featureCard(this,
                "\uD83D\uDCAA", "健身教練", "AI 居家運動計畫", UIHelper.ACCENT_PURPLE, 40);
        cardFitness.setOnClickListener(v -> startActivity(new Intent(this, FitnessActivity.class)));

        row2.addView(cardCalendar, gridCellLp(0));
        row2.addView(cardFitness, gridCellLp(UIHelper.dp(this, 10)));
        content.addView(row2);

        // Row 3: 監聽狀態 + 通知權限
        content.addView(UIHelper.sectionHeader(this, "SYSTEM"));

        LinearLayout row3 = gridRow();
        LinearLayout cardMonitor = UIHelper.featureCard(this,
                "\uD83D\uDCE1", "監聽狀態", "通知·簡訊 Log", UIHelper.ACCENT_BLUE, 30);
        cardMonitor.setOnClickListener(v -> startActivity(new Intent(this, MonitorActivity.class)));

        LinearLayout cardPerm = UIHelper.featureCard(this,
                "\u2699\uFE0F", "通知權限", "開啟系統設定", UIHelper.ACCENT_ORANGE, 30);
        cardPerm.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        row3.addView(cardMonitor, gridCellLp(0));
        row3.addView(cardPerm, gridCellLp(UIHelper.dp(this, 10)));
        content.addView(row3);

        // ── Version footer ──
        TextView version = new TextView(this);
        version.setText("v2.5");
        version.setTextSize(11);
        version.setTextColor(UIHelper.TEXT_HINT);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, UIHelper.dp(this, 20), 0, UIHelper.dp(this, 16));
        content.addView(version);

        scrollView.addView(content);

        root.addView(topSection);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private LinearLayout gridRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 5), 0, UIHelper.dp(this, 5));
        row.setLayoutParams(lp);
        return row;
    }

    private LinearLayout.LayoutParams gridCellLp(int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(leftMargin, 0, 0, 0);
        return lp;
    }

    private void requestPermissions() {
        String[] perms;
        if (Build.VERSION.SDK_INT >= 33) {
            perms = new String[]{
                    android.Manifest.permission.RECEIVE_SMS,
                    android.Manifest.permission.READ_SMS,
                    "android.permission.POST_NOTIFICATIONS"
            };
        } else {
            perms = new String[]{
                    android.Manifest.permission.RECEIVE_SMS,
                    android.Manifest.permission.READ_SMS
            };
        }

        boolean needRequest = false;
        for (String perm : perms) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }

        if (needRequest) {
            ActivityCompat.requestPermissions(this, perms, PERMISSION_CODE);
        }
    }
}
