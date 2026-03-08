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
        ReminderHelper.restoreWaterIfEnabled(this);

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
        LinearLayout row3 = gridRow();
        LinearLayout cardStock = UIHelper.featureCard(this,
                "\uD83D\uDCC8", "台股追蹤", "即時行情·技術分析", UIHelper.ACCENT_ORANGE, 40);
        cardStock.setOnClickListener(v -> startActivity(new Intent(this, StockActivity.class)));

        LinearLayout cardCountdown = UIHelper.featureCard(this,
                "\u23F3", "\u5012\u6578\u65E5", "\u91CD\u8981\u65E5\u671F\u5012\u6578", UIHelper.ACCENT_BLUE, 40);
        cardCountdown.setOnClickListener(v -> startActivity(new Intent(this, CountdownActivity.class)));

        row3.addView(cardStock, gridCellLp(0));
        row3.addView(cardCountdown, gridCellLp(UIHelper.dp(this, 10)));
        content.addView(row3);

        // Row 4
        LinearLayout row4f = gridRow();
        LinearLayout cardHabit = UIHelper.featureCard(this,
                "\uD83D\uDCCA", "\u7FD2\u6163\u8FFD\u8E64", "\u6BCF\u65E5\u6253\u5361\u00B7\u9023\u7E8C\u7D00\u9304", UIHelper.ACCENT_PURPLE, 40);
        cardHabit.setOnClickListener(v -> startActivity(new Intent(this, HabitActivity.class)));

        LinearLayout cardWater = UIHelper.featureCard(this,
                "\uD83D\uDCA7", "\u559D\u6C34\u63D0\u9192", "\u6BCF\u65E5\u98F2\u6C34\u00B7\u5B9A\u6642\u63D0\u9192", UIHelper.ACCENT_BLUE, 40);
        cardWater.setOnClickListener(v -> startActivity(new Intent(this, WaterActivity.class)));

        row4f.addView(cardHabit, gridCellLp(0));
        row4f.addView(cardWater, gridCellLp(UIHelper.dp(this, 10)));
        content.addView(row4f);

        // Row 5
        LinearLayout row5f = gridRow();
        LinearLayout cardMusic = UIHelper.featureCard(this,
                "\uD83C\uDFB5", "\u97F3\u6A02\u7BA1\u7406", "YouTube\u00B7\u5206\u985E\u00B7\u64AD\u653E", UIHelper.ACCENT_ORANGE, 40);
        cardMusic.setOnClickListener(v -> startActivity(new Intent(this, MusicActivity.class)));

        View musicSpacer = new View(this);

        row5f.addView(cardMusic, gridCellLp(0));
        row5f.addView(musicSpacer, gridCellLp(UIHelper.dp(this, 10)));
        content.addView(row5f);

        // ── Tools ──
        content.addView(UIHelper.sectionHeader(this, "TOOLS"));

        LinearLayout row4 = gridRow();
        LinearLayout cardCapture = UIHelper.featureCard(this,
                "\uD83D\uDCF7", "截圖分析消費", "懸浮按鈕·AI 辨識", UIHelper.ACCENT_RED, 35);
        cardCapture.setOnClickListener(v -> toggleFloatingCapture());

        LinearLayout cardLog = UIHelper.featureCard(this,
                "\uD83D\uDCCB", "系統日誌", "操作記錄·除錯資訊", UIHelper.TEXT_SECONDARY, 30);
        cardLog.setOnClickListener(v -> startActivity(new Intent(this, LogActivity.class)));

        row4.addView(cardCapture, gridCellLp(0));
        row4.addView(cardLog, gridCellLp(UIHelper.dp(this, 10)));
        content.addView(row4);

        LinearLayout row5 = gridRow();
        LinearLayout cardInvoice = UIHelper.featureCard(this,
                "\uD83E\uDDFE", "\u767C\u7968\u6383\u63CF\u8A18\u5E33", "\u62CD\u7167/\u76F8\u7C3F\u00B7AI \u8FA8\u8B58", UIHelper.ACCENT_ORANGE, 35);
        cardInvoice.setOnClickListener(v -> startActivity(new Intent(this, InvoiceActivity.class)));

        LinearLayout cardRemoteDev = UIHelper.featureCard(this,
                "\uD83D\uDCBB", "\u9060\u7AEF\u958B\u767C", "Slack Bot\u00B7Claude Code", UIHelper.ACCENT_BLUE, 35);
        cardRemoteDev.setOnClickListener(v -> startActivity(new Intent(this, RemoteDevActivity.class)));

        row5.addView(cardInvoice, gridCellLp(0));
        row5.addView(cardRemoteDev, gridCellLp(UIHelper.dp(this, 10)));
        content.addView(row5);

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
