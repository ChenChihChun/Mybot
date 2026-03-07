package com.mybot.app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
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
    private static final long HEALTH_CHECK_INTERVAL = 30_000; // 30 seconds

    private View bridgeDot;
    private TextView bridgeText;
    private Handler healthHandler;
    private boolean lastOnline = true; // track state change for push notification

    private final Runnable healthCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkBridgeHealth();
            healthHandler.postDelayed(this, HEALTH_CHECK_INTERVAL);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        healthHandler = new Handler(Looper.getMainLooper());

        AppLog.init(this);
        AppLog.i("System", "App啟動 v" + UpdateChecker.getCurrentVersionName(this));

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

        // Compact hero: icon + text + bridge status
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
        LinearLayout.LayoutParams textColLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textCol.setLayoutParams(textColLp);

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

        // Bridge status: dot + text (right side)
        LinearLayout bridgeStatus = new LinearLayout(this);
        bridgeStatus.setOrientation(LinearLayout.HORIZONTAL);
        bridgeStatus.setGravity(Gravity.CENTER_VERTICAL);

        int dotSize = UIHelper.dp(this, 10);
        bridgeDot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(UIHelper.TEXT_HINT); // grey initially
        bridgeDot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
        dotLp.setMargins(0, 0, UIHelper.dp(this, 5), 0);
        bridgeDot.setLayoutParams(dotLp);

        bridgeText = new TextView(this);
        bridgeText.setText("檢查中");
        bridgeText.setTextSize(11);
        bridgeText.setTextColor(UIHelper.TEXT_HINT);

        bridgeStatus.addView(bridgeDot);
        bridgeStatus.addView(bridgeText);

        heroRow.addView(iconCircle);
        heroRow.addView(textCol);
        heroRow.addView(bridgeStatus);
        topSection.addView(heroRow);

        // ── Scrollable content ──
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int cp = UIHelper.dp(this, 16);
        content.setPadding(cp, UIHelper.dp(this, 16), cp, cp);

        // ── Feature grid ──
        content.addView(UIHelper.sectionHeader(this, "FEATURES"));

        // Row 1
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

        // Row 2
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

        // Row 3
        content.addView(UIHelper.sectionHeader(this, "TOOLS"));

        LinearLayout row3 = gridRow();
        LinearLayout cardCapture = UIHelper.featureCard(this,
                "\uD83D\uDCF7", "截圖分析消費", "懸浮按鈕·AI 辨識", UIHelper.ACCENT_RED, 35);
        cardCapture.setOnClickListener(v -> toggleFloatingCapture());

        LinearLayout cardStock = UIHelper.featureCard(this,
                "\uD83D\uDCC8", "台股追蹤", "即時行情·技術分析", UIHelper.ACCENT_ORANGE, 40);
        cardStock.setOnClickListener(v -> startActivity(new Intent(this, StockActivity.class)));

        row3.addView(cardCapture, gridCellLp(0));
        row3.addView(cardStock, gridCellLp(UIHelper.dp(this, 10)));
        content.addView(row3);

        // Row 4
        LinearLayout row4 = gridRow();
        LinearLayout cardLog = UIHelper.featureCard(this,
                "\uD83D\uDCCB", "系統日誌", "操作記錄·除錯資訊", UIHelper.TEXT_SECONDARY, 30);
        cardLog.setOnClickListener(v -> startActivity(new Intent(this, LogActivity.class)));

        // Spacer for alignment
        View spacer = new View(this);

        row4.addView(cardLog, gridCellLp(0));
        row4.addView(spacer, gridCellLp(UIHelper.dp(this, 10)));
        content.addView(row4);

        // ── Version footer ──
        LinearLayout versionRow = new LinearLayout(this);
        versionRow.setOrientation(LinearLayout.HORIZONTAL);
        versionRow.setGravity(Gravity.CENTER);
        versionRow.setPadding(0, UIHelper.dp(this, 20), 0, UIHelper.dp(this, 16));

        TextView version = new TextView(this);
        version.setText("v" + UpdateChecker.getCurrentVersionName(this));
        version.setTextSize(11);
        version.setTextColor(UIHelper.TEXT_HINT);

        TextView updateBtn = new TextView(this);
        updateBtn.setText("  檢查更新");
        updateBtn.setTextSize(11);
        updateBtn.setTextColor(UIHelper.ACCENT_BLUE);
        updateBtn.setOnClickListener(v -> {
            updateBtn.setText("  檢查中...");
            updateBtn.setTextColor(UIHelper.TEXT_HINT);
            UpdateChecker.checkForUpdate(this, (hasUpdate, latestVer, latestCode, url, notes, error) -> {
                updateBtn.setText("  檢查更新");
                updateBtn.setTextColor(UIHelper.ACCENT_BLUE);
                if (hasUpdate) {
                    UpdateChecker.showUpdateDialog(this, latestVer, latestCode, url, notes);
                } else if (error != null) {
                    android.widget.Toast.makeText(this, "檢查失敗: " + error,
                            android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    UpdateChecker.showNoUpdateDialog(this);
                }
            });
        });

        versionRow.addView(version);
        versionRow.addView(updateBtn);
        content.addView(versionRow);

        // Auto check update silently
        UpdateChecker.checkForUpdate(this, (hasUpdate, latestVer, latestCode, url, notes, error) -> {
            if (hasUpdate) {
                UpdateChecker.showUpdateDialog(this, latestVer, latestCode, url, notes);
            }
        });

        scrollView.addView(content);

        root.addView(topSection);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start periodic health check
        healthHandler.removeCallbacks(healthCheckRunnable);
        healthCheckRunnable.run(); // run immediately, then every 30s
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop periodic check when not visible
        healthHandler.removeCallbacks(healthCheckRunnable);
    }

    private void checkBridgeHealth() {
        BridgeClient.healthCheck((online, message) -> {
            GradientDrawable dotBg = (GradientDrawable) bridgeDot.getBackground();
            if (online) {
                dotBg.setColor(UIHelper.ACCENT_GREEN);
                bridgeText.setText("正常");
                bridgeText.setTextColor(UIHelper.ACCENT_GREEN);
            } else {
                dotBg.setColor(UIHelper.ACCENT_RED);
                bridgeText.setText("離線");
                bridgeText.setTextColor(UIHelper.ACCENT_RED);
            }

            // Push notification when status changes to offline
            if (!online && lastOnline) {
                NotificationHelper.sendNotification(this,
                        "Mybot - Bridge 離線", "AI Bridge 無法連線: " + message);
            }
            lastOnline = online;
        });
    }

    private void toggleFloatingCapture() {
        if (!Settings.canDrawOverlays(this)) {
            android.widget.Toast.makeText(this,
                    "請先開啟「顯示在其他應用程式上層」權限", android.widget.Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        startActivity(new Intent(this, CapturePermissionActivity.class));
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
        if (Build.VERSION.SDK_INT >= 33) {
            String perm = "android.permission.POST_NOTIFICATIONS";
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{perm}, PERMISSION_CODE);
            }
        }
    }
}
