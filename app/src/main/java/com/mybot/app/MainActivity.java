package com.mybot.app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
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

        getWindow().setStatusBarColor(Color.parseColor("#0A1520"));

        LinearLayout root = UIHelper.pageRoot(this);

        // Top section with app name
        LinearLayout topSection = new LinearLayout(this);
        topSection.setOrientation(LinearLayout.VERTICAL);
        topSection.setGravity(Gravity.CENTER);
        int p = UIHelper.dp(this, 24);
        topSection.setPadding(p, UIHelper.dp(this, 48), p, UIHelper.dp(this, 24));

        // App icon circle
        TextView iconCircle = new TextView(this);
        iconCircle.setText("M");
        iconCircle.setTextSize(36);
        iconCircle.setTextColor(Color.WHITE);
        iconCircle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        iconCircle.setGravity(Gravity.CENTER);
        iconCircle.setBackground(UIHelper.roundRect(UIHelper.ACCENT_BLUE, 40, this));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                UIHelper.dp(this, 80), UIHelper.dp(this, 80));
        iconLp.gravity = Gravity.CENTER;
        iconLp.setMargins(0, 0, 0, UIHelper.dp(this, 16));
        iconCircle.setLayoutParams(iconLp);

        TextView title = new TextView(this);
        title.setText("Mybot");
        title.setTextSize(32);
        title.setTextColor(UIHelper.TEXT_PRIMARY);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("Smart Notification Assistant");
        subtitle.setTextSize(14);
        subtitle.setTextColor(UIHelper.TEXT_SECONDARY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, UIHelper.dp(this, 4), 0, 0);

        topSection.addView(iconCircle);
        topSection.addView(title);
        topSection.addView(subtitle);

        // Status card
        LinearLayout statusCard = UIHelper.card(this);
        LinearLayout.LayoutParams statusLp = (LinearLayout.LayoutParams) statusCard.getLayoutParams();
        statusLp.setMargins(p, UIHelper.dp(this, 8), p, UIHelper.dp(this, 8));
        statusCard.setLayoutParams(statusLp);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        // Green dot
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
        LinearLayout menuSection = new LinearLayout(this);
        menuSection.setOrientation(LinearLayout.VERTICAL);
        menuSection.setPadding(p, UIHelper.dp(this, 16), p, p);

        TextView menuLabel = new TextView(this);
        menuLabel.setText("MENU");
        menuLabel.setTextSize(12);
        menuLabel.setTextColor(UIHelper.TEXT_HINT);
        menuLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        menuLabel.setLetterSpacing(0.15f);
        menuLabel.setPadding(UIHelper.dp(this, 4), 0, 0, UIHelper.dp(this, 12));

        Button btnExpenses = UIHelper.cardButton(this, "消費紀錄", "查看所有消費明細", UIHelper.ACCENT_RED);
        btnExpenses.setOnClickListener(v -> startActivity(new Intent(this, ExpenseActivity.class)));

        Button btnMonitor = UIHelper.cardButton(this, "監聽狀態", "即時通知與簡訊 Log", UIHelper.ACCENT_BLUE);
        btnMonitor.setOnClickListener(v -> startActivity(new Intent(this, MonitorActivity.class)));

        Button btnPermission = UIHelper.cardButton(this, "通知存取權限", "開啟系統設定", UIHelper.ACCENT_ORANGE);
        btnPermission.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        });

        menuSection.addView(menuLabel);
        menuSection.addView(btnExpenses);
        menuSection.addView(btnMonitor);
        menuSection.addView(btnPermission);

        // Version footer
        TextView version = new TextView(this);
        version.setText("v2.0");
        version.setTextSize(12);
        version.setTextColor(UIHelper.TEXT_HINT);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, UIHelper.dp(this, 16), 0, UIHelper.dp(this, 16));

        root.addView(topSection);
        root.addView(statusCard);
        root.addView(menuSection, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(version);

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
