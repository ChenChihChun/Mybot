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

        // ── Dashboard ──
        content.addView(UIHelper.sectionHeader(this, "DASHBOARD"));

        LinearLayout dashRow1 = gridRow();
        int gap = UIHelper.dp(this, 8);

        LinearLayout dashExpense = UIHelper.dashboardCard(this,
                "\uD83D\uDCB0", "--", "今日消費", UIHelper.ACCENT_RED);
        dashExpense.setOnClickListener(v -> startActivity(new Intent(this, ExpenseActivity.class)));

        LinearLayout dashTodo = UIHelper.dashboardCard(this,
                "\u2705", "--", "待辦事項", UIHelper.ACCENT_GREEN);
        dashTodo.setOnClickListener(v -> startActivity(new Intent(this, TodoActivity.class)));

        dashRow1.addView(dashExpense, gridCellLp(0));
        dashRow1.addView(dashTodo, gridCellLp(gap));
        content.addView(dashRow1);

        LinearLayout dashRow2 = gridRow();

        LinearLayout dashKnowledge = UIHelper.dashboardCard(this,
                "\uD83D\uDCDA", "--", "知識庫", UIHelper.ACCENT_BLUE);
        dashKnowledge.setOnClickListener(v -> startActivity(new Intent(this, KnowledgeActivity.class)));

        LinearLayout dashStock = UIHelper.dashboardCard(this,
                "\uD83D\uDCC8", "--", "台股追蹤", UIHelper.ACCENT_ORANGE);
        dashStock.setOnClickListener(v -> startActivity(new Intent(this, StockActivity.class)));

        dashRow2.addView(dashKnowledge, gridCellLp(0));
        dashRow2.addView(dashStock, gridCellLp(gap));
        content.addView(dashRow2);

        // Load dashboard data async
        loadDashboardData(dashExpense, dashTodo, dashKnowledge);

        // ── Feature grid (3 columns) ──
        content.addView(UIHelper.sectionHeader(this, "FEATURES"));
        int g = UIHelper.dp(this, 8);

        // Row 1: 消費紀錄, 待辦事項, 日曆
        LinearLayout fRow1 = gridRow3();
        addCompact(fRow1, "\uD83D\uDCB0", "消費紀錄", UIHelper.ACCENT_RED,
                v -> startActivity(new Intent(this, ExpenseActivity.class)), 0);
        addCompact(fRow1, "\u2705", "待辦事項", UIHelper.ACCENT_GREEN,
                v -> startActivity(new Intent(this, TodoActivity.class)), g);
        addCompact(fRow1, "\uD83D\uDCC5", "日曆", UIHelper.ACCENT_BLUE,
                v -> startActivity(new Intent(this, CalendarActivity.class)), g);
        content.addView(fRow1);

        // Row 2: 健身教練, 台股追蹤, 倒數日
        LinearLayout fRow2 = gridRow3();
        addCompact(fRow2, "\uD83D\uDCAA", "健身教練", UIHelper.ACCENT_PURPLE,
                v -> startActivity(new Intent(this, FitnessActivity.class)), 0);
        addCompact(fRow2, "\uD83D\uDCC8", "台股追蹤", UIHelper.ACCENT_ORANGE,
                v -> startActivity(new Intent(this, StockActivity.class)), g);
        addCompact(fRow2, "\u23F3", "倒數日", UIHelper.ACCENT_BLUE,
                v -> startActivity(new Intent(this, CountdownActivity.class)), g);
        content.addView(fRow2);

        // Row 3: 習慣追蹤, 喝水提醒, 音樂管理
        LinearLayout fRow3 = gridRow3();
        addCompact(fRow3, "\uD83D\uDCCA", "習慣追蹤", UIHelper.ACCENT_PURPLE,
                v -> startActivity(new Intent(this, HabitActivity.class)), 0);
        addCompact(fRow3, "\uD83D\uDCA7", "喝水提醒", UIHelper.ACCENT_BLUE,
                v -> startActivity(new Intent(this, WaterActivity.class)), g);
        addCompact(fRow3, "\uD83C\uDFB5", "音樂管理", UIHelper.ACCENT_ORANGE,
                v -> startActivity(new Intent(this, MusicActivity.class)), g);
        content.addView(fRow3);

        // Row 4: 影片摘要, 知識庫
        LinearLayout fRow4 = gridRow3();
        addCompact(fRow4, "\uD83C\uDFAC", "影片摘要", UIHelper.ACCENT_RED,
                v -> startActivity(new Intent(this, YouTubeActivity.class)), 0);
        addCompact(fRow4, "\uD83D\uDCDA", "知識庫", UIHelper.ACCENT_BLUE,
                v -> startActivity(new Intent(this, KnowledgeActivity.class)), g);
        View ph1 = new View(this);
        ph1.setVisibility(View.INVISIBLE);
        fRow4.addView(ph1, gridCellLp(g));
        content.addView(fRow4);

        // ── Tools (3 columns) ──
        content.addView(UIHelper.sectionHeader(this, "TOOLS"));

        LinearLayout tRow1 = gridRow3();
        addCompact(tRow1, "\uD83D\uDCF7", "截圖分析", UIHelper.ACCENT_RED,
                v -> toggleFloatingCapture(), 0);
        addCompact(tRow1, "\uD83E\uDDFE", "發票掃描", UIHelper.ACCENT_ORANGE,
                v -> startActivity(new Intent(this, InvoiceActivity.class)), g);
        addCompact(tRow1, "\uD83D\uDCBB", "遠端開發", UIHelper.ACCENT_BLUE,
                v -> startActivity(new Intent(this, RemoteDevActivity.class)), g);
        content.addView(tRow1);

        LinearLayout tRow2 = gridRow3();
        addCompact(tRow2, "\uD83D\uDCCB", "系統日誌", UIHelper.TEXT_SECONDARY,
                v -> startActivity(new Intent(this, LogActivity.class)), 0);
        View ph2 = new View(this);
        ph2.setVisibility(View.INVISIBLE);
        tRow2.addView(ph2, gridCellLp(g));
        View ph3 = new View(this);
        ph3.setVisibility(View.INVISIBLE);
        tRow2.addView(ph3, gridCellLp(g));
        content.addView(tRow2);

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
        lp.setMargins(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4));
        row.setLayoutParams(lp);
        return row;
    }

    private LinearLayout gridRow3() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4));
        row.setLayoutParams(lp);
        return row;
    }

    private void addCompact(LinearLayout row, String icon, String label, int color,
                            View.OnClickListener listener, int leftMargin) {
        LinearLayout card = UIHelper.compactCard(this, icon, label, color);
        card.setOnClickListener(listener);
        row.addView(card, gridCellLp(leftMargin));
    }

    private LinearLayout.LayoutParams gridCellLp(int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(leftMargin, 0, 0, 0);
        return lp;
    }

    private void loadDashboardData(LinearLayout dashExpense, LinearLayout dashTodo, LinearLayout dashKnowledge) {
        new Thread(() -> {
            // Today's expenses
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            long dayStart = cal.getTimeInMillis();
            long dayEnd = dayStart + 86400_000L;

            ExpenseDbHelper expDb = new ExpenseDbHelper(this);
            double todaySum = expDb.sumByDateRange(dayStart, dayEnd);
            String expText = todaySum > 0 ? "$" + String.format("%.0f", todaySum) : "$0";

            // Pending todos
            TodoDbHelper todoDb = new TodoDbHelper(this);
            int pending = todoDb.countPending();
            String todoText = String.valueOf(pending);

            // Knowledge count
            KnowledgeDbHelper kDb = new KnowledgeDbHelper(this);
            int kCount = kDb.getCount();
            String kText = String.valueOf(kCount);

            runOnUiThread(() -> {
                TextView ev = dashExpense.findViewWithTag("dashboard_value");
                if (ev != null) ev.setText(expText);

                TextView tv = dashTodo.findViewWithTag("dashboard_value");
                if (tv != null) tv.setText(todoText);

                TextView kv = dashKnowledge.findViewWithTag("dashboard_value");
                if (kv != null) kv.setText(kText);
            });
        }).start();
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
