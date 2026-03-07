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
import android.widget.Button;
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

        // Top section - hero area
        LinearLayout topSection = new LinearLayout(this);
        topSection.setOrientation(LinearLayout.VERTICAL);
        topSection.setGravity(Gravity.CENTER);
        topSection.setBackgroundColor(UIHelper.BG_TOP_BAR);
        int p = UIHelper.dp(this, 24);
        topSection.setPadding(p, UIHelper.dp(this, 40), p, UIHelper.dp(this, 32));
        topSection.setElevation(UIHelper.dp(this, 4));

        // App icon circle
        TextView iconCircle = new TextView(this);
        iconCircle.setText("M");
        iconCircle.setTextSize(32);
        iconCircle.setTextColor(Color.WHITE);
        iconCircle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        iconCircle.setGravity(Gravity.CENTER);
        iconCircle.setBackground(UIHelper.roundRect(UIHelper.ACCENT_GREEN, 22, this));
        iconCircle.setElevation(UIHelper.dp(this, 4));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                UIHelper.dp(this, 72), UIHelper.dp(this, 72));
        iconLp.gravity = Gravity.CENTER;
        iconLp.setMargins(0, 0, 0, UIHelper.dp(this, 16));
        iconCircle.setLayoutParams(iconLp);

        TextView title = new TextView(this);
        title.setText("Mybot");
        title.setTextSize(30);
        title.setTextColor(UIHelper.TEXT_PRIMARY);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.05f);

        TextView subtitle = new TextView(this);
        subtitle.setText("Smart Notification Assistant");
        subtitle.setTextSize(13);
        subtitle.setTextColor(UIHelper.TEXT_SECONDARY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, UIHelper.dp(this, 6), 0, 0);
        subtitle.setLetterSpacing(0.03f);

        topSection.addView(iconCircle);
        topSection.addView(title);
        topSection.addView(subtitle);

        // Scrollable content
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(p, UIHelper.dp(this, 20), p, p);

        // Status card
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
        statusHint.setText("請確認已開啟通知存取權限");
        statusHint.setTextSize(13);
        statusHint.setTextColor(UIHelper.TEXT_SECONDARY);
        statusHint.setPadding(0, UIHelper.dp(this, 8), 0, 0);

        statusCard.addView(statusRow);
        statusCard.addView(statusHint);

        // Menu section
        content.addView(statusCard);
        content.addView(UIHelper.sectionHeader(this, "MENU"));

        Button btnExpenses = UIHelper.cardButton(this, "消費紀錄", "查看所有消費明細", UIHelper.ACCENT_RED);
        btnExpenses.setOnClickListener(v -> startActivity(new Intent(this, ExpenseActivity.class)));

        Button btnTodo = UIHelper.cardButton(this, "待辦事項", "TO DO 時間管理", UIHelper.ACCENT_GREEN);
        btnTodo.setOnClickListener(v -> startActivity(new Intent(this, TodoActivity.class)));

        Button btnFitness = UIHelper.cardButton(this, "健身教練", "AI 居家運動計畫", UIHelper.ACCENT_PURPLE);
        btnFitness.setOnClickListener(v -> startActivity(new Intent(this, FitnessActivity.class)));

        Button btnMonitor = UIHelper.cardButton(this, "監聽狀態", "即時通知與簡訊 Log", UIHelper.ACCENT_BLUE);
        btnMonitor.setOnClickListener(v -> startActivity(new Intent(this, MonitorActivity.class)));

        Button btnPermission = UIHelper.cardButton(this, "通知存取權限", "開啟系統設定", UIHelper.ACCENT_ORANGE);
        btnPermission.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        content.addView(btnExpenses);
        content.addView(btnTodo);
        content.addView(btnFitness);
        content.addView(btnMonitor);
        content.addView(btnPermission);

        // Version footer
        TextView version = new TextView(this);
        version.setText("v2.3");
        version.setTextSize(11);
        version.setTextColor(UIHelper.TEXT_HINT);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, UIHelper.dp(this, 24), 0, UIHelper.dp(this, 16));
        content.addView(version);

        scrollView.addView(content);

        root.addView(topSection);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
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
