package com.mybot.app;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class StockActivity extends AppCompatActivity {

    // Recommendation UI (institutional)
    private LinearLayout recContent;
    private TextView recStatus;
    private boolean recLoading = false;
    // Tracking UI (institutional)
    private LinearLayout trackingContent;
    private TextView trackingStatus;
    private boolean trackingLoading = false;
    // Watchlist UI
    private LinearLayout watchlistContent;
    private static final String PREF_WATCHLIST = "stock_watchlist";
    private static final String PREF_WATCHLIST_CACHE = "stock_watchlist_cache";
    // Chip recommendation UI
    private LinearLayout chipRecContent;
    private TextView chipRecStatus;
    private boolean chipRecLoading = false;
    // Chip tracking UI
    private LinearLayout chipTrackingContent;
    private TextView chipTrackingStatus;
    private boolean chipTrackingLoading = false;
    // Swing recommendation UI (3-6 month horizon)
    private LinearLayout swingRecContent;
    private TextView swingRecStatus;
    private boolean swingRecLoading = false;
    // Swing tracking UI
    private LinearLayout swingTrackingContent;
    private TextView swingTrackingStatus;
    private boolean swingTrackingLoading = false;
    // Tab state
    private LinearLayout instTab;
    private LinearLayout chipTab;
    private LinearLayout swingTab;
    private LinearLayout instPage;
    private LinearLayout chipPage;
    private LinearLayout swingPage;
    private int activeTab = 0; // 0=法人 1=籌碼 2=波段

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        buildUI();

        // Auto-enable stock reminder on first visit
        if (!ReminderHelper.isStockEnabled(this)) {
            ReminderHelper.scheduleStockReminder(this);
            AppLog.i("Stock", "自動啟用每日推薦通知 (08:30)");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecommendation();
        refreshWatchlistUI();
        loadTracking();
        loadChipRecommendation();
        loadChipTracking();
        loadSwingRecommendation();
        loadSwingTracking();
    }

    private void buildUI() {
        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "台股 AI 推薦");
        root.addView(topBar);

        // Tab bar
        root.addView(buildTabBar());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int cp = UIHelper.dp(this, 12);
        content.setPadding(cp, UIHelper.dp(this, 8), cp, cp);

        // === Institutional page ===
        instPage = new LinearLayout(this);
        instPage.setOrientation(LinearLayout.VERTICAL);
        instPage.addView(buildRecommendationCard());
        instPage.addView(buildWatchlistCard());
        instPage.addView(buildTrackingCard());
        content.addView(instPage);

        // === Chip page ===
        chipPage = new LinearLayout(this);
        chipPage.setOrientation(LinearLayout.VERTICAL);
        chipPage.setVisibility(View.GONE);
        chipPage.addView(buildChipRecommendationCard());
        chipPage.addView(buildChipTrackingCard());
        content.addView(chipPage);

        // === Swing page (3-6 month horizon) ===
        swingPage = new LinearLayout(this);
        swingPage.setOrientation(LinearLayout.VERTICAL);
        swingPage.setVisibility(View.GONE);
        swingPage.addView(buildSwingRecommendationCard());
        swingPage.addView(buildSwingTrackingCard());
        content.addView(swingPage);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private LinearLayout buildTabBar() {
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundColor(UIHelper.BG_CARD);
        int pad = UIHelper.dp(this, 4);
        tabBar.setPadding(UIHelper.dp(this, 12), pad, UIHelper.dp(this, 12), 0);

        instTab = buildTab("法人推薦", true);
        chipTab = buildTab("籌碼選股", false);
        swingTab = buildTab("波段精選", false);

        instTab.setOnClickListener(v -> switchTab(0));
        chipTab.setOnClickListener(v -> switchTab(1));
        swingTab.setOnClickListener(v -> switchTab(2));

        tabBar.addView(instTab);
        tabBar.addView(chipTab);
        tabBar.addView(swingTab);
        return tabBar;
    }

    private LinearLayout buildTab(String text, boolean active) {
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tab.setLayoutParams(lp);
        int pad = UIHelper.dp(this, 10);
        tab.setPadding(pad, pad, pad, UIHelper.dp(this, 6));

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(14);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(active ? UIHelper.ACCENT_ORANGE : UIHelper.TEXT_HINT);
        tab.addView(label);

        // Bottom indicator
        View indicator = new View(this);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 3));
        ilp.setMargins(UIHelper.dp(this, 20), UIHelper.dp(this, 6), UIHelper.dp(this, 20), 0);
        indicator.setLayoutParams(ilp);
        indicator.setBackgroundColor(active ? UIHelper.ACCENT_ORANGE : Color.TRANSPARENT);
        tab.addView(indicator);

        return tab;
    }

    private void switchTab(int tab) {
        if (activeTab == tab) return;
        activeTab = tab;

        LinearLayout[] tabs = {instTab, chipTab, swingTab};
        LinearLayout[] pages = {instPage, chipPage, swingPage};
        String[] names = {"法人推薦", "籌碼選股", "波段精選"};
        for (int i = 0; i < tabs.length; i++) {
            boolean active = (i == tab);
            ((TextView) tabs[i].getChildAt(0)).setTextColor(active ? UIHelper.ACCENT_ORANGE : UIHelper.TEXT_HINT);
            tabs[i].getChildAt(1).setBackgroundColor(active ? UIHelper.ACCENT_ORANGE : Color.TRANSPARENT);
            pages[i].setVisibility(active ? View.VISIBLE : View.GONE);
        }

        AppLog.i("Stock", "switchTab: " + names[tab]);
    }

    private String formatPrice(double price) {
        if (price == 0) return "--";
        DecimalFormat df = price >= 100 ? new DecimalFormat("#,##0") :
                price >= 10 ? new DecimalFormat("#,##0.0") : new DecimalFormat("#,##0.00");
        return df.format(price);
    }

    // ==================== Daily Recommendation ====================

    private LinearLayout buildRecommendationCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 16, this));
        int pad = UIHelper.dp(this, 14);
        card.setPadding(pad, pad, pad, pad);
        card.setElevation(UIHelper.dp(this, 3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 4));
        card.setLayoutParams(lp);

        // Title row
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("每日 AI 推薦");
        title.setTextSize(15);
        title.setTextColor(UIHelper.ACCENT_ORANGE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView refreshBtn = new TextView(this);
        refreshBtn.setText("更新");
        refreshBtn.setTextSize(13);
        refreshBtn.setTextColor(UIHelper.ACCENT_BLUE);
        refreshBtn.setTypeface(Typeface.DEFAULT_BOLD);
        refreshBtn.setPadding(UIHelper.dp(this, 12), UIHelper.dp(this, 6),
                UIHelper.dp(this, 12), UIHelper.dp(this, 6));
        refreshBtn.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.ACCENT_BLUE, 10, 1, this));
        refreshBtn.setOnClickListener(v -> refreshRecommendation());

        titleRow.addView(title);
        titleRow.addView(refreshBtn);
        card.addView(titleRow);

        // Content container
        recContent = new LinearLayout(this);
        recContent.setOrientation(LinearLayout.VERTICAL);
        recContent.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        card.addView(recContent);

        // Status text
        recStatus = new TextView(this);
        recStatus.setTextSize(13);
        recStatus.setTextColor(UIHelper.TEXT_HINT);
        recStatus.setText("載入中...");
        recContent.addView(recStatus);

        return card;
    }

    private void loadRecommendation() {
        if (recLoading) return;
        recLoading = true;
        recStatus.setText("載入推薦中...");
        recStatus.setVisibility(View.VISIBLE);
        AppLog.i("Stock", "loadRecommendation: 開始載入");

        BridgeClient.getStockRecommendation((data, error) -> {
            recLoading = false;
            if (error != null) {
                recStatus.setText("尚無推薦資料");
                AppLog.w("Stock", "loadRecommendation: " + error);
                return;
            }
            if (data == null) {
                recStatus.setText("尚無推薦資料");
                return;
            }
            displayRecommendation(data);
        });
    }

    private void displayRecommendation(org.json.JSONObject data) {
        recContent.removeAllViews();
        try {
            String date = data.optString("date", "");
            org.json.JSONObject rec = data.optJSONObject("data");
            if (rec == null) rec = data;

            String mood = rec.optString("market_mood", "N/A");
            String moodReason = rec.optString("mood_reason", "");

            // Mood bar
            int moodColor = mood.contains("樂觀") ? UIHelper.ACCENT_GREEN
                    : mood.contains("謹慎") ? UIHelper.ACCENT_RED : UIHelper.ACCENT_ORANGE;

            TextView moodView = new TextView(this);
            moodView.setText("市場氛圍: " + mood + (moodReason.isEmpty() ? "" : " — " + moodReason));
            moodView.setTextSize(13);
            moodView.setTextColor(moodColor);
            moodView.setTypeface(Typeface.DEFAULT_BOLD);
            moodView.setPadding(0, 0, 0, UIHelper.dp(this, 8));
            recContent.addView(moodView);

            if (!date.isEmpty()) {
                TextView dateView = new TextView(this);
                dateView.setText("更新日期: " + date);
                dateView.setTextSize(11);
                dateView.setTextColor(UIHelper.TEXT_HINT);
                dateView.setPadding(0, 0, 0, UIHelper.dp(this, 6));
                recContent.addView(dateView);
            }

            // Picks
            org.json.JSONArray picks = rec.optJSONArray("picks");
            if (picks != null && picks.length() > 0) {
                for (int i = 0; i < picks.length(); i++) {
                    org.json.JSONObject pick = picks.getJSONObject(i);
                    recContent.addView(buildPickCard(pick, i + 1));
                }
            } else {
                TextView noPicks = new TextView(this);
                noPicks.setText("今日無推薦標的");
                noPicks.setTextSize(13);
                noPicks.setTextColor(UIHelper.TEXT_SECONDARY);
                recContent.addView(noPicks);
            }

            AppLog.i("Stock", "displayRecommendation: " + (picks != null ? picks.length() : 0) + " picks, mood=" + mood);

        } catch (Exception e) {
            TextView errView = new TextView(this);
            errView.setText("解析推薦資料失敗: " + e.getMessage());
            errView.setTextSize(12);
            errView.setTextColor(UIHelper.ACCENT_RED);
            recContent.addView(errView);
            AppLog.e("Stock", "displayRecommendation解析失敗: " + e.getMessage());
        }
    }

    private LinearLayout buildPickCard(org.json.JSONObject pick, int rank) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(0xFF1E1E2E, 12, this));
        int pad = UIHelper.dp(this, 12);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4));
        card.setLayoutParams(lp);

        String symbol = pick.optString("symbol", "?");
        String name = pick.optString("name", "?");
        double price = pick.optDouble("price", 0);

        StringBuilder copy = new StringBuilder();
        copy.append("#").append(rank).append(" ").append(symbol).append(" ").append(name);
        if (price > 0) copy.append("  ").append(formatPrice(price));
        copy.append("\n");

        // Header: rank + symbol + name + price
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView rankView = new TextView(this);
        rankView.setText("#" + rank);
        rankView.setTextSize(16);
        rankView.setTextColor(UIHelper.ACCENT_ORANGE);
        rankView.setTypeface(Typeface.DEFAULT_BOLD);
        rankView.setPadding(0, 0, UIHelper.dp(this, 8), 0);

        TextView nameView = new TextView(this);
        nameView.setText(symbol + " " + name);
        nameView.setTextSize(15);
        nameView.setTextColor(UIHelper.TEXT_PRIMARY);
        nameView.setTypeface(Typeface.DEFAULT_BOLD);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // Yahoo Finance link (inline after name)
        TextView yahooLink = new TextView(this);
        yahooLink.setText("Yahoo→");
        yahooLink.setTextSize(12);
        yahooLink.setTextColor(UIHelper.ACCENT_BLUE);
        yahooLink.setPadding(UIHelper.dp(this, 6), 0, UIHelper.dp(this, 6), 0);
        yahooLink.setOnClickListener(v -> {
            String url = "https://tw.stock.yahoo.com/quote/" + symbol + ".TW";
            try {
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(url)));
            } catch (Exception ignored) {}
        });

        header.addView(rankView);
        header.addView(nameView);
        header.addView(yahooLink);
        if (price > 0) {
            TextView priceView = new TextView(this);
            priceView.setText(formatPrice(price));
            priceView.setTextSize(15);
            priceView.setTextColor(UIHelper.TEXT_PRIMARY);
            priceView.setTypeface(Typeface.DEFAULT_BOLD);
            header.addView(priceView);
        }
        card.addView(header);

        // Confidence score
        double confidence = pick.optDouble("confidence_score", -1);
        if (confidence >= 0) {
            int confPct = (int) Math.round(confidence * 100);
            int confColor = confidence >= 0.7 ? UIHelper.ACCENT_GREEN
                    : confidence >= 0.5 ? UIHelper.ACCENT_ORANGE : UIHelper.ACCENT_RED;
            TextView confView = new TextView(this);
            confView.setText("信心度 " + confPct + "%");
            confView.setTextSize(12);
            confView.setTextColor(confColor);
            confView.setTypeface(Typeface.DEFAULT_BOLD);
            confView.setBackground(UIHelper.roundRect(0xFF262640, 6, this));
            confView.setPadding(UIHelper.dp(this, 8), UIHelper.dp(this, 3),
                    UIHelper.dp(this, 8), UIHelper.dp(this, 3));
            LinearLayout.LayoutParams confLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            confLp.setMargins(0, UIHelper.dp(this, 4), 0, 0);
            confView.setLayoutParams(confLp);
            card.addView(confView);
            copy.append("信心度 ").append(confPct).append("%\n");
        }

        // Institutional + Financial summary
        String instSummary = pick.optString("institutional_summary", "");
        if (!instSummary.isEmpty()) {
            TextView instView = new TextView(this);
            instView.setText("法人: " + instSummary);
            instView.setTextSize(12);
            instView.setTextColor(UIHelper.ACCENT_BLUE);
            instView.setPadding(0, UIHelper.dp(this, 4), 0, 0);
            card.addView(instView);
            copy.append("法人: ").append(instSummary).append("\n");
        }

        String finSummary = pick.optString("financial_summary", "");
        if (!finSummary.isEmpty()) {
            TextView finView = new TextView(this);
            finView.setText("財報: " + finSummary);
            finView.setTextSize(12);
            finView.setTextColor(UIHelper.ACCENT_GREEN);
            finView.setPadding(0, UIHelper.dp(this, 2), 0, 0);
            card.addView(finView);
            copy.append("財報: ").append(finSummary).append("\n");
        }

        // Narrative (Gemini 市場敘事)
        String narrative = pick.optString("narrative", "");
        if (!narrative.isEmpty()) {
            TextView narrativeTitle = new TextView(this);
            narrativeTitle.setText("敘事");
            narrativeTitle.setTextSize(12);
            narrativeTitle.setTextColor(UIHelper.ACCENT_ORANGE);
            narrativeTitle.setTypeface(Typeface.DEFAULT_BOLD);
            narrativeTitle.setPadding(0, UIHelper.dp(this, 6), 0, UIHelper.dp(this, 2));
            card.addView(narrativeTitle);

            TextView narrativeView = new TextView(this);
            narrativeView.setText(narrative);
            narrativeView.setTextSize(13);
            narrativeView.setTextColor(UIHelper.ACCENT_BLUE);
            narrativeView.setLineSpacing(UIHelper.dp(this, 2), 1f);
            narrativeView.setPadding(0, 0, 0, UIHelper.dp(this, 4));
            card.addView(narrativeView);
            copy.append("敘事: ").append(narrative).append("\n");
        }

        // Reasons
        org.json.JSONArray reasons = pick.optJSONArray("reasons");
        if (reasons != null && reasons.length() > 0) {
            for (int i = 0; i < reasons.length(); i++) {
                String reason = reasons.optString(i, "");
                if (!reason.isEmpty()) {
                    TextView rv = new TextView(this);
                    rv.setText("• " + reason);
                    rv.setTextSize(12);
                    rv.setTextColor(UIHelper.TEXT_SECONDARY);
                    rv.setPadding(0, UIHelper.dp(this, 3), 0, 0);
                    rv.setLineSpacing(UIHelper.dp(this, 2), 1f);
                    card.addView(rv);
                    copy.append("• ").append(reason).append("\n");
                }
            }
        }

        // Risk
        String risk = pick.optString("risk", "");
        if (!risk.isEmpty()) {
            TextView riskView = new TextView(this);
            riskView.setText("風險: " + risk);
            riskView.setTextSize(12);
            riskView.setTextColor(UIHelper.ACCENT_RED);
            riskView.setPadding(0, UIHelper.dp(this, 4), 0, 0);
            card.addView(riskView);
            copy.append("風險: ").append(risk).append("\n");
        }

        // Trading strategy
        org.json.JSONObject strategy = pick.optJSONObject("strategy");
        if (strategy != null) {
            LinearLayout stratBox = new LinearLayout(this);
            stratBox.setOrientation(LinearLayout.VERTICAL);
            stratBox.setBackground(UIHelper.roundRect(0xFF262640, 8, this));
            int sp = UIHelper.dp(this, 8);
            stratBox.setPadding(sp, sp, sp, sp);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.setMargins(0, UIHelper.dp(this, 6), 0, 0);
            stratBox.setLayoutParams(slp);

            TextView stratTitle = new TextView(this);
            stratTitle.setText("操作策略");
            stratTitle.setTextSize(12);
            stratTitle.setTextColor(UIHelper.ACCENT_ORANGE);
            stratTitle.setTypeface(Typeface.DEFAULT_BOLD);
            stratBox.addView(stratTitle);

            String action = strategy.optString("action", "");
            String entry = strategy.optString("entry_price", "");
            String stopLoss = strategy.optString("stop_loss", "");
            String target = strategy.optString("target", "");
            String position = strategy.optString("position", "");
            String timing = strategy.optString("timing", "");
            String detail = strategy.optString("detail", "");

            StringBuilder sb = new StringBuilder();
            if (!action.isEmpty()) sb.append("操作: ").append(action).append("\n");
            if (!entry.isEmpty()) sb.append("進場: ").append(entry).append("\n");
            if (!stopLoss.isEmpty()) sb.append("停損: ").append(stopLoss).append("\n");
            if (!target.isEmpty()) sb.append("目標: ").append(target).append("\n");
            if (!position.isEmpty()) sb.append("部位: ").append(position).append("\n");
            if (!timing.isEmpty()) sb.append("時機: ").append(timing).append("\n");
            if (!detail.isEmpty()) sb.append(detail);

            if (sb.length() > 0) {
                TextView stratText = new TextView(this);
                stratText.setText(sb.toString().trim());
                stratText.setTextSize(12);
                stratText.setTextColor(UIHelper.TEXT_SECONDARY);
                stratText.setLineSpacing(UIHelper.dp(this, 2), 1f);
                stratText.setPadding(0, UIHelper.dp(this, 4), 0, 0);
                stratBox.addView(stratText);
                copy.append("\n操作策略\n").append(sb.toString().trim()).append("\n");
            }

            card.addView(stratBox);
        }

        final String copyText = copy.toString().trim();
        card.setOnLongClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("stock_pick", copyText));
                android.widget.Toast.makeText(this, "已複製 " + symbol + " " + name,
                        android.widget.Toast.LENGTH_SHORT).show();
                AppLog.i("Stock", "複製推薦卡: " + symbol);
            }
            return true;
        });

        return card;
    }

    private void refreshRecommendation() {
        if (recLoading) return;
        recLoading = true;
        recStatus.setText("觸發分析中（約需2-3分鐘）...");
        recStatus.setVisibility(View.VISIBLE);
        recContent.removeAllViews();
        recContent.addView(recStatus);
        AppLog.i("Stock", "refreshRecommendation: 觸發分析");

        BridgeClient.refreshStockRecommendation((data, error) -> {
            recLoading = false;
            if (error != null) {
                recStatus.setText("分析失敗: " + error);
                AppLog.e("Stock", "refreshRecommendation失敗: " + error);
                return;
            }
            loadRecommendation();
        });
    }

    // ==================== Tracking & Accuracy ====================

    private LinearLayout buildTrackingCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 16, this));
        int pad = UIHelper.dp(this, 14);
        card.setPadding(pad, pad, pad, pad);
        card.setElevation(UIHelper.dp(this, 3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 4));
        card.setLayoutParams(lp);

        // Title
        TextView title = new TextView(this);
        title.setText("推薦追蹤 & 準確度");
        title.setTextSize(15);
        title.setTextColor(UIHelper.ACCENT_PURPLE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        // Content container
        trackingContent = new LinearLayout(this);
        trackingContent.setOrientation(LinearLayout.VERTICAL);
        trackingContent.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        card.addView(trackingContent);

        // Status text
        trackingStatus = new TextView(this);
        trackingStatus.setTextSize(13);
        trackingStatus.setTextColor(UIHelper.TEXT_HINT);
        trackingStatus.setText("載入中...");
        trackingContent.addView(trackingStatus);

        return card;
    }

    private void loadTracking() {
        if (trackingLoading) return;
        trackingLoading = true;
        trackingStatus.setText("載入追蹤數據...");
        trackingStatus.setVisibility(View.VISIBLE);
        AppLog.i("Stock", "loadTracking: 開始載入");

        BridgeClient.getStockTracking((data, error) -> {
            trackingLoading = false;
            if (error != null) {
                trackingStatus.setText("無追蹤資料");
                AppLog.w("Stock", "loadTracking: " + error);
                return;
            }
            if (data == null) {
                trackingStatus.setText("無追蹤資料");
                return;
            }
            displayTracking(data);
        });
    }

    private void displayTracking(org.json.JSONObject data) {
        trackingContent.removeAllViews();
        try {
            org.json.JSONObject stats = data.optJSONObject("stats");
            org.json.JSONArray entries = data.optJSONArray("entries");

            // Stats row
            if (stats != null) {
                trackingContent.addView(buildTrackingStatsRow(stats));
            }

            // Entries list
            if (entries != null && entries.length() > 0) {
                TextView listTitle = new TextView(this);
                listTitle.setText("追蹤明細 (" + entries.length() + " 筆)");
                listTitle.setTextSize(13);
                listTitle.setTextColor(UIHelper.TEXT_HINT);
                listTitle.setPadding(0, UIHelper.dp(this, 10), 0, UIHelper.dp(this, 4));
                trackingContent.addView(listTitle);

                for (int i = 0; i < entries.length(); i++) {
                    org.json.JSONObject entry = entries.getJSONObject(i);
                    trackingContent.addView(buildTrackingEntry(entry));
                }
                AppLog.i("Stock", "displayTracking: " + entries.length() + " entries, winRate=" +
                        (stats != null ? stats.optDouble("win_rate", 0) : "N/A") + "%");
            } else {
                TextView empty = new TextView(this);
                empty.setText("尚無追蹤數據，推薦資料累積後將自動顯示");
                empty.setTextSize(13);
                empty.setTextColor(UIHelper.TEXT_HINT);
                empty.setPadding(0, UIHelper.dp(this, 4), 0, 0);
                trackingContent.addView(empty);
            }
        } catch (Exception e) {
            TextView errView = new TextView(this);
            errView.setText("解析追蹤資料失敗: " + e.getMessage());
            errView.setTextSize(12);
            errView.setTextColor(UIHelper.ACCENT_RED);
            trackingContent.addView(errView);
            AppLog.e("Stock", "displayTracking失敗: " + e.getMessage());
        }
    }

    private LinearLayout buildTrackingStatsRow(org.json.JSONObject stats) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4));

        int totalCompleted = stats.optInt("total_completed", 0);
        double winRate = stats.optDouble("win_rate", 0);
        double avgReturn = stats.optDouble("avg_return", 0);
        int totalPicks = stats.optInt("total_picks", 0);

        row.addView(buildStatBox("勝率",
                totalCompleted > 0 ? String.format("%.1f%%", winRate) : "--",
                winRate >= 50 ? UIHelper.ACCENT_GREEN : (totalCompleted > 0 ? UIHelper.ACCENT_RED : UIHelper.TEXT_HINT)));

        row.addView(buildStatBox("平均報酬",
                totalCompleted > 0 ? String.format("%+.2f%%", avgReturn) : "--",
                avgReturn >= 0 ? UIHelper.ACCENT_GREEN : (totalCompleted > 0 ? UIHelper.ACCENT_RED : UIHelper.TEXT_HINT)));

        row.addView(buildStatBox("完成/總數",
                totalCompleted + "/" + totalPicks,
                UIHelper.TEXT_SECONDARY));

        return row;
    }

    private LinearLayout buildStatBox(String label, String value, int valueColor) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(UIHelper.roundRect(0xFF1E1E2E, 10, this));
        int pad = UIHelper.dp(this, 10);
        box.setPadding(pad, UIHelper.dp(this, 8), pad, UIHelper.dp(this, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(UIHelper.dp(this, 3), 0, UIHelper.dp(this, 3), 0);
        box.setLayoutParams(lp);

        TextView valView = new TextView(this);
        valView.setText(value);
        valView.setTextSize(18);
        valView.setTextColor(valueColor);
        valView.setTypeface(Typeface.DEFAULT_BOLD);
        valView.setGravity(Gravity.CENTER);
        box.addView(valView);

        TextView lblView = new TextView(this);
        lblView.setText(label);
        lblView.setTextSize(11);
        lblView.setTextColor(UIHelper.TEXT_HINT);
        lblView.setGravity(Gravity.CENTER);
        lblView.setPadding(0, UIHelper.dp(this, 2), 0, 0);
        box.addView(lblView);

        return box;
    }

    private LinearLayout buildTrackingEntry(org.json.JSONObject entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(UIHelper.roundRect(0xFF1E1E2E, 8, this));
        int pad = UIHelper.dp(this, 8);
        row.setPadding(pad, UIHelper.dp(this, 6), pad, UIHelper.dp(this, 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 2), 0, UIHelper.dp(this, 2));
        row.setLayoutParams(lp);

        String recDate = entry.optString("rec_date", "");
        String symbol = entry.optString("symbol", "");
        String name = entry.optString("name", "");
        double recPrice = entry.optDouble("rec_price", 0);
        double currentPrice = entry.optDouble("current_price", 0);
        double returnPct = entry.optDouble("return_pct", 0);
        String status = entry.optString("status", "tracking");
        int daysTracked = entry.optInt("days_tracked", 0);

        String shortDate = recDate.length() >= 10 ? recDate.substring(5) : recDate;

        // Left: date + symbol + name
        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView nameRow = new TextView(this);
        nameRow.setText(shortDate + "  " + symbol + " " + name);
        nameRow.setTextSize(13);
        nameRow.setTextColor(UIHelper.TEXT_PRIMARY);
        leftCol.addView(nameRow);

        TextView priceRow = new TextView(this);
        priceRow.setText(formatPrice(recPrice) + " → " + formatPrice(currentPrice));
        priceRow.setTextSize(11);
        priceRow.setTextColor(UIHelper.TEXT_HINT);
        leftCol.addView(priceRow);

        // Re-recommendation history (shown beneath the original entry)
        org.json.JSONArray reDates = entry.optJSONArray("re_rec_dates");
        if (reDates != null && reDates.length() > 0) {
            StringBuilder sb = new StringBuilder("再次推薦: ");
            for (int i = 0; i < reDates.length(); i++) {
                String d = reDates.optString(i, "");
                if (d.length() >= 10) d = d.substring(5);
                if (i > 0) sb.append(", ");
                sb.append(d);
            }
            TextView reRow = new TextView(this);
            reRow.setText(sb.toString());
            reRow.setTextSize(10);
            reRow.setTextColor(UIHelper.ACCENT_ORANGE);
            leftCol.addView(reRow);
        }

        row.addView(leftCol);

        // Right: return % + status
        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setGravity(Gravity.END);

        int returnColor = returnPct > 0 ? UIHelper.ACCENT_GREEN
                : returnPct < 0 ? UIHelper.ACCENT_RED : UIHelper.TEXT_HINT;

        TextView returnView = new TextView(this);
        returnView.setText(String.format("%+.2f%%", returnPct));
        returnView.setTextSize(14);
        returnView.setTextColor(returnColor);
        returnView.setTypeface(Typeface.DEFAULT_BOLD);
        returnView.setGravity(Gravity.END);
        rightCol.addView(returnView);

        TextView statusView = new TextView(this);
        if ("completed".equals(status)) {
            statusView.setText("完成");
            statusView.setTextColor(returnPct > 0 ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_RED);
        } else {
            statusView.setText("追蹤中(" + daysTracked + "/3天)");
            statusView.setTextColor(UIHelper.ACCENT_ORANGE);
        }
        statusView.setTextSize(10);
        statusView.setGravity(Gravity.END);
        rightCol.addView(statusView);

        // Long-term "to-date" return (only shown for completed picks with newer data)
        if ("completed".equals(status) && !entry.isNull("latest_return_pct")) {
            double latestPct = entry.optDouble("latest_return_pct", 0);
            String latestDate = entry.optString("latest_date", "");
            String shortLatest = latestDate.length() >= 10 ? latestDate.substring(5) : latestDate;
            int latestColor = latestPct > 0 ? UIHelper.ACCENT_GREEN
                    : latestPct < 0 ? UIHelper.ACCENT_RED : UIHelper.TEXT_HINT;
            TextView latestView = new TextView(this);
            latestView.setText(String.format("迄今 %+.2f%% (%s)", latestPct, shortLatest));
            latestView.setTextSize(10);
            latestView.setTextColor(latestColor);
            latestView.setGravity(Gravity.END);
            rightCol.addView(latestView);
        }

        row.addView(rightCol);

        return row;
    }

    // ==================== Chip Recommendation ====================

    private LinearLayout buildChipRecommendationCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 16, this));
        int pad = UIHelper.dp(this, 14);
        card.setPadding(pad, pad, pad, pad);
        card.setElevation(UIHelper.dp(this, 3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 4));
        card.setLayoutParams(lp);

        // Title row
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("籌碼集中度選股");
        title.setTextSize(15);
        title.setTextColor(UIHelper.ACCENT_ORANGE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        titleRow.addView(title);

        // Subtitle
        TextView subtitle = new TextView(this);
        subtitle.setText("大戶持股變化");
        subtitle.setTextSize(11);
        subtitle.setTextColor(UIHelper.TEXT_HINT);
        subtitle.setPadding(UIHelper.dp(this, 8), 0, 0, 0);
        titleRow.addView(subtitle);

        card.addView(titleRow);

        // Content container
        chipRecContent = new LinearLayout(this);
        chipRecContent.setOrientation(LinearLayout.VERTICAL);
        chipRecContent.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        card.addView(chipRecContent);

        // Status text
        chipRecStatus = new TextView(this);
        chipRecStatus.setTextSize(13);
        chipRecStatus.setTextColor(UIHelper.TEXT_HINT);
        chipRecStatus.setText("載入中...");
        chipRecContent.addView(chipRecStatus);

        return card;
    }

    private void loadChipRecommendation() {
        if (chipRecLoading) return;
        chipRecLoading = true;
        chipRecStatus.setText("載入籌碼選股...");
        chipRecStatus.setVisibility(View.VISIBLE);
        AppLog.i("Stock", "loadChipRecommendation: 開始載入");

        BridgeClient.getChipRecommendation((data, error) -> {
            chipRecLoading = false;
            if (error != null) {
                chipRecStatus.setText("尚無籌碼推薦資料");
                AppLog.w("Stock", "loadChipRecommendation: " + error);
                return;
            }
            if (data == null) {
                chipRecStatus.setText("尚無籌碼推薦資料");
                return;
            }
            displayChipRecommendation(data);
        });
    }

    private void displayChipRecommendation(org.json.JSONObject data) {
        chipRecContent.removeAllViews();
        try {
            String date = data.optString("date", "");
            org.json.JSONObject rec = data.optJSONObject("data");
            if (rec == null) rec = data;

            String mood = rec.optString("market_mood", "N/A");
            String moodReason = rec.optString("mood_reason", "");

            int moodColor = mood.contains("樂觀") ? UIHelper.ACCENT_GREEN
                    : mood.contains("謹慎") ? UIHelper.ACCENT_RED : UIHelper.ACCENT_ORANGE;

            TextView moodView = new TextView(this);
            moodView.setText("市場氛圍: " + mood + (moodReason.isEmpty() ? "" : " — " + moodReason));
            moodView.setTextSize(13);
            moodView.setTextColor(moodColor);
            moodView.setTypeface(Typeface.DEFAULT_BOLD);
            moodView.setPadding(0, 0, 0, UIHelper.dp(this, 8));
            chipRecContent.addView(moodView);

            if (!date.isEmpty()) {
                TextView dateView = new TextView(this);
                dateView.setText("更新日期: " + date);
                dateView.setTextSize(11);
                dateView.setTextColor(UIHelper.TEXT_HINT);
                dateView.setPadding(0, 0, 0, UIHelper.dp(this, 6));
                chipRecContent.addView(dateView);
            }

            org.json.JSONArray picks = rec.optJSONArray("picks");
            if (picks != null && picks.length() > 0) {
                for (int i = 0; i < picks.length(); i++) {
                    org.json.JSONObject pick = picks.getJSONObject(i);
                    chipRecContent.addView(buildPickCard(pick, i + 1));
                }
            } else {
                TextView noPicks = new TextView(this);
                noPicks.setText("今日無籌碼推薦標的");
                noPicks.setTextSize(13);
                noPicks.setTextColor(UIHelper.TEXT_SECONDARY);
                chipRecContent.addView(noPicks);
            }

            AppLog.i("Stock", "displayChipRecommendation: " + (picks != null ? picks.length() : 0) + " picks");
        } catch (Exception e) {
            TextView errView = new TextView(this);
            errView.setText("解析籌碼推薦資料失敗: " + e.getMessage());
            errView.setTextSize(12);
            errView.setTextColor(UIHelper.ACCENT_RED);
            chipRecContent.addView(errView);
            AppLog.e("Stock", "displayChipRecommendation失敗: " + e.getMessage());
        }
    }

    // ==================== Chip Tracking ====================

    private LinearLayout buildChipTrackingCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 16, this));
        int pad = UIHelper.dp(this, 14);
        card.setPadding(pad, pad, pad, pad);
        card.setElevation(UIHelper.dp(this, 3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 4));
        card.setLayoutParams(lp);

        TextView title = new TextView(this);
        title.setText("籌碼追蹤 & 準確度");
        title.setTextSize(15);
        title.setTextColor(UIHelper.ACCENT_PURPLE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        chipTrackingContent = new LinearLayout(this);
        chipTrackingContent.setOrientation(LinearLayout.VERTICAL);
        chipTrackingContent.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        card.addView(chipTrackingContent);

        chipTrackingStatus = new TextView(this);
        chipTrackingStatus.setTextSize(13);
        chipTrackingStatus.setTextColor(UIHelper.TEXT_HINT);
        chipTrackingStatus.setText("載入中...");
        chipTrackingContent.addView(chipTrackingStatus);

        return card;
    }

    private void loadChipTracking() {
        if (chipTrackingLoading) return;
        chipTrackingLoading = true;
        chipTrackingStatus.setText("載入籌碼追蹤數據...");
        chipTrackingStatus.setVisibility(View.VISIBLE);
        AppLog.i("Stock", "loadChipTracking: 開始載入");

        BridgeClient.getChipTracking((data, error) -> {
            chipTrackingLoading = false;
            if (error != null) {
                chipTrackingStatus.setText("尚無籌碼追蹤資料");
                AppLog.w("Stock", "loadChipTracking: " + error);
                return;
            }
            if (data == null) {
                chipTrackingStatus.setText("尚無籌碼追蹤資料");
                return;
            }
            displayChipTracking(data);
        });
    }

    private void displayChipTracking(org.json.JSONObject data) {
        chipTrackingContent.removeAllViews();
        try {
            org.json.JSONObject stats = data.optJSONObject("stats");
            org.json.JSONArray entries = data.optJSONArray("entries");

            if (stats != null) {
                chipTrackingContent.addView(buildTrackingStatsRow(stats));
            }

            if (entries != null && entries.length() > 0) {
                TextView listTitle = new TextView(this);
                listTitle.setText("追蹤明細 (" + entries.length() + " 筆)");
                listTitle.setTextSize(13);
                listTitle.setTextColor(UIHelper.TEXT_HINT);
                listTitle.setPadding(0, UIHelper.dp(this, 10), 0, UIHelper.dp(this, 4));
                chipTrackingContent.addView(listTitle);

                for (int i = 0; i < entries.length(); i++) {
                    org.json.JSONObject entry = entries.getJSONObject(i);
                    chipTrackingContent.addView(buildTrackingEntry(entry));
                }
                AppLog.i("Stock", "displayChipTracking: " + entries.length() + " entries");
            } else {
                TextView empty = new TextView(this);
                empty.setText("尚無籌碼追蹤數據，資料累積後將自動顯示");
                empty.setTextSize(13);
                empty.setTextColor(UIHelper.TEXT_HINT);
                empty.setPadding(0, UIHelper.dp(this, 4), 0, 0);
                chipTrackingContent.addView(empty);
            }
        } catch (Exception e) {
            TextView errView = new TextView(this);
            errView.setText("解析籌碼追蹤資料失敗: " + e.getMessage());
            errView.setTextSize(12);
            errView.setTextColor(UIHelper.ACCENT_RED);
            chipTrackingContent.addView(errView);
            AppLog.e("Stock", "displayChipTracking失敗: " + e.getMessage());
        }
    }

    // ==================== Swing Recommendation (3-6 month) ====================

    private LinearLayout buildSwingRecommendationCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 16, this));
        int pad = UIHelper.dp(this, 14);
        card.setPadding(pad, pad, pad, pad);
        card.setElevation(UIHelper.dp(this, 3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 4));
        card.setLayoutParams(lp);

        TextView title = new TextView(this);
        title.setText("波段精選（3-6 個月，目標 +50%）");
        title.setTextSize(15);
        title.setTextColor(UIHelper.ACCENT_ORANGE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("每月 12 日更新（月營收公布後）· 中長線持有，非隔日沖");
        subtitle.setTextSize(11);
        subtitle.setTextColor(UIHelper.TEXT_HINT);
        subtitle.setPadding(0, UIHelper.dp(this, 2), 0, 0);
        card.addView(subtitle);

        swingRecContent = new LinearLayout(this);
        swingRecContent.setOrientation(LinearLayout.VERTICAL);
        swingRecContent.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        card.addView(swingRecContent);

        swingRecStatus = new TextView(this);
        swingRecStatus.setTextSize(13);
        swingRecStatus.setTextColor(UIHelper.TEXT_HINT);
        swingRecStatus.setText("載入中...");
        swingRecContent.addView(swingRecStatus);

        return card;
    }

    private void loadSwingRecommendation() {
        if (swingRecLoading) return;
        swingRecLoading = true;
        swingRecStatus.setText("載入波段推薦中...");
        swingRecStatus.setVisibility(View.VISIBLE);
        AppLog.i("Stock", "loadSwingRecommendation: 開始載入");

        BridgeClient.getSwingRecommendation((data, error) -> {
            swingRecLoading = false;
            if (error != null || data == null) {
                swingRecStatus.setText("尚無波段推薦資料（每月 12 日產生）");
                AppLog.w("Stock", "loadSwingRecommendation: " + (error != null ? error : "null"));
                return;
            }
            displaySwingRecommendation(data);
        });
    }

    private void displaySwingRecommendation(org.json.JSONObject data) {
        swingRecContent.removeAllViews();
        try {
            String date = data.optString("date", "");
            org.json.JSONObject rec = data.optJSONObject("data");
            if (rec == null) rec = data;

            // Market regime bar
            String regime = rec.optString("market_regime", "");
            String regimeNote = rec.optString("regime_note", "");
            if (!regime.isEmpty()) {
                int regimeColor = regime.contains("多") ? UIHelper.ACCENT_GREEN
                        : regime.contains("空") ? UIHelper.ACCENT_RED : UIHelper.ACCENT_ORANGE;
                TextView regimeView = new TextView(this);
                regimeView.setText("市場狀態: " + regime + (regimeNote.isEmpty() ? "" : " — " + regimeNote));
                regimeView.setTextSize(13);
                regimeView.setTextColor(regimeColor);
                regimeView.setTypeface(Typeface.DEFAULT_BOLD);
                regimeView.setPadding(0, 0, 0, UIHelper.dp(this, 8));
                swingRecContent.addView(regimeView);
            }

            if (!date.isEmpty()) {
                int screened = rec.optInt("candidates_screened", 0);
                TextView dateView = new TextView(this);
                dateView.setText("推薦日期: " + date
                        + (screened > 0 ? "（量化篩選 " + screened + " 檔候選）" : ""));
                dateView.setTextSize(11);
                dateView.setTextColor(UIHelper.TEXT_HINT);
                dateView.setPadding(0, 0, 0, UIHelper.dp(this, 6));
                swingRecContent.addView(dateView);
            }

            org.json.JSONArray picks = rec.optJSONArray("picks");
            if (picks != null && picks.length() > 0) {
                for (int i = 0; i < picks.length(); i++) {
                    swingRecContent.addView(buildSwingPickCard(picks.getJSONObject(i), i + 1));
                }
            } else {
                TextView noPicks = new TextView(this);
                noPicks.setText("本月無波段推薦標的");
                noPicks.setTextSize(13);
                noPicks.setTextColor(UIHelper.TEXT_SECONDARY);
                swingRecContent.addView(noPicks);
            }
            AppLog.i("Stock", "displaySwingRecommendation: " + (picks != null ? picks.length() : 0) + " picks");
        } catch (Exception e) {
            TextView errView = new TextView(this);
            errView.setText("解析波段推薦失敗: " + e.getMessage());
            errView.setTextSize(12);
            errView.setTextColor(UIHelper.ACCENT_RED);
            swingRecContent.addView(errView);
            AppLog.e("Stock", "displaySwingRecommendation失敗: " + e.getMessage());
        }
    }

    private void addSwingDetail(LinearLayout card, String label, String text, int color) {
        if (text == null || text.isEmpty()) return;
        TextView view = new TextView(this);
        view.setText(label + ": " + text);
        view.setTextSize(12);
        view.setTextColor(color);
        view.setLineSpacing(UIHelper.dp(this, 2), 1f);
        view.setPadding(0, UIHelper.dp(this, 4), 0, 0);
        card.addView(view);
    }

    private LinearLayout buildSwingPickCard(org.json.JSONObject pick, int rank) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(0xFF1E1E2E, 12, this));
        int pad = UIHelper.dp(this, 12);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4));
        card.setLayoutParams(lp);

        String symbol = pick.optString("symbol", "?").replace(".TWO", "").replace(".TW", "");
        String name = pick.optString("name", "?");
        double price = pick.optDouble("price", 0);

        // Header: rank + symbol + name + Yahoo link + price
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView rankView = new TextView(this);
        rankView.setText("#" + rank);
        rankView.setTextSize(16);
        rankView.setTextColor(UIHelper.ACCENT_ORANGE);
        rankView.setTypeface(Typeface.DEFAULT_BOLD);
        rankView.setPadding(0, 0, UIHelper.dp(this, 8), 0);

        TextView nameView = new TextView(this);
        nameView.setText(symbol + " " + name);
        nameView.setTextSize(15);
        nameView.setTextColor(UIHelper.TEXT_PRIMARY);
        nameView.setTypeface(Typeface.DEFAULT_BOLD);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView yahooLink = new TextView(this);
        yahooLink.setText("Yahoo→");
        yahooLink.setTextSize(12);
        yahooLink.setTextColor(UIHelper.ACCENT_BLUE);
        yahooLink.setPadding(UIHelper.dp(this, 6), 0, UIHelper.dp(this, 6), 0);
        yahooLink.setOnClickListener(v -> {
            String url = "https://tw.stock.yahoo.com/quote/" + symbol + ".TW";
            try {
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(url)));
            } catch (Exception ignored) {}
        });

        header.addView(rankView);
        header.addView(nameView);
        header.addView(yahooLink);
        if (price > 0) {
            TextView priceView = new TextView(this);
            priceView.setText(formatPrice(price));
            priceView.setTextSize(15);
            priceView.setTextColor(UIHelper.TEXT_PRIMARY);
            priceView.setTypeface(Typeface.DEFAULT_BOLD);
            header.addView(priceView);
        }
        card.addView(header);

        // Badges row: confidence + story stage + upside
        LinearLayout badges = new LinearLayout(this);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        badges.setPadding(0, UIHelper.dp(this, 6), 0, 0);

        double confidence = pick.optDouble("confidence", -1);
        if (confidence >= 0) {
            int confPct = (int) Math.round(confidence * 100);
            int confColor = confidence >= 0.7 ? UIHelper.ACCENT_GREEN
                    : confidence >= 0.5 ? UIHelper.ACCENT_ORANGE : UIHelper.ACCENT_RED;
            badges.addView(buildSwingBadge("信心 " + confPct + "%", confColor));
        }
        String stage = pick.optString("story_stage", "");
        if (!stage.isEmpty()) {
            int stageColor = stage.contains("早") ? UIHelper.ACCENT_GREEN
                    : stage.contains("晚") ? UIHelper.ACCENT_RED : UIHelper.ACCENT_ORANGE;
            badges.addView(buildSwingBadge("故事" + stage, stageColor));
        }
        int upside = pick.optInt("upside_pct", 0);
        if (upside > 0) {
            badges.addView(buildSwingBadge("上檔 +" + upside + "%", UIHelper.ACCENT_BLUE));
        }
        if (badges.getChildCount() > 0) card.addView(badges);

        // Thesis (投資論點)
        String thesis = pick.optString("thesis", "");
        if (!thesis.isEmpty()) {
            TextView thesisView = new TextView(this);
            thesisView.setText(thesis);
            thesisView.setTextSize(13);
            thesisView.setTextColor(UIHelper.TEXT_PRIMARY);
            thesisView.setLineSpacing(UIHelper.dp(this, 2), 1f);
            thesisView.setPadding(0, UIHelper.dp(this, 8), 0, 0);
            card.addView(thesisView);
        }

        // Catalysts (催化劑列表)
        org.json.JSONArray catalysts = pick.optJSONArray("catalysts");
        if (catalysts != null && catalysts.length() > 0) {
            TextView catTitle = new TextView(this);
            catTitle.setText("催化劑");
            catTitle.setTextSize(12);
            catTitle.setTextColor(UIHelper.ACCENT_GREEN);
            catTitle.setTypeface(Typeface.DEFAULT_BOLD);
            catTitle.setPadding(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 2));
            card.addView(catTitle);
            for (int i = 0; i < catalysts.length(); i++) {
                String cat = catalysts.optString(i, "");
                if (cat.isEmpty()) continue;
                TextView catView = new TextView(this);
                catView.setText("• " + cat);
                catView.setTextSize(12);
                catView.setTextColor(UIHelper.TEXT_SECONDARY);
                catView.setLineSpacing(UIHelper.dp(this, 1), 1f);
                catView.setPadding(UIHelper.dp(this, 4), UIHelper.dp(this, 1), 0, 0);
                card.addView(catView);
            }
        }

        // Risk + invalidation (完整原因的另一半：何時該放棄)
        addSwingDetail(card, "最大風險", pick.optString("risk", ""), UIHelper.ACCENT_RED);
        addSwingDetail(card, "失效條件", pick.optString("invalidation", ""), UIHelper.ACCENT_ORANGE);

        // Quantitative screening basis (為什麼入選量化篩選)
        org.json.JSONObject sd = pick.optJSONObject("screen_data");
        if (sd != null) {
            TextView sdTitle = new TextView(this);
            sdTitle.setText("量化入選依據");
            sdTitle.setTextSize(12);
            sdTitle.setTextColor(UIHelper.ACCENT_BLUE);
            sdTitle.setTypeface(Typeface.DEFAULT_BOLD);
            sdTitle.setPadding(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 2));
            card.addView(sdTitle);

            StringBuilder sb = new StringBuilder();
            if (!sd.isNull("yoy_latest"))
                sb.append("月營收 YoY ").append(String.format("%+.1f%%", sd.optDouble("yoy_latest")));
            if (!sd.isNull("rev_accel"))
                sb.append("（加速 ").append(String.format("%+.1f", sd.optDouble("rev_accel"))).append("pp）");
            if (sd.optBoolean("rev_new_high", false)) sb.append(" · 創12月新高");
            if (!sd.isNull("inst_20d"))
                sb.append("\n法人20日淨買超 ").append(String.format("%,d", sd.optLong("inst_20d"))).append(" 張");
            if (!sd.isNull("pct_off_high"))
                sb.append("\n距52週高 ").append(String.format("%+.1f%%", sd.optDouble("pct_off_high")));
            if (!sd.isNull("ret_6m"))
                sb.append(" · 近6月漲幅 ").append(String.format("%+.1f%%", sd.optDouble("ret_6m")));

            TextView sdView = new TextView(this);
            sdView.setText(sb.toString());
            sdView.setTextSize(12);
            sdView.setTextColor(UIHelper.TEXT_SECONDARY);
            sdView.setLineSpacing(UIHelper.dp(this, 2), 1f);
            sdView.setPadding(UIHelper.dp(this, 4), 0, 0, 0);
            card.addView(sdView);
        }

        return card;
    }

    private TextView buildSwingBadge(String text, int color) {
        TextView badge = new TextView(this);
        badge.setText(text);
        badge.setTextSize(11);
        badge.setTextColor(color);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setBackground(UIHelper.roundRect(0xFF262640, 6, this));
        badge.setPadding(UIHelper.dp(this, 8), UIHelper.dp(this, 3),
                UIHelper.dp(this, 8), UIHelper.dp(this, 3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, UIHelper.dp(this, 6), 0);
        badge.setLayoutParams(lp);
        return badge;
    }

    // ==================== Swing Tracking ====================

    private LinearLayout buildSwingTrackingCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 16, this));
        int pad = UIHelper.dp(this, 14);
        card.setPadding(pad, pad, pad, pad);
        card.setElevation(UIHelper.dp(this, 3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 4));
        card.setLayoutParams(lp);

        TextView title = new TextView(this);
        title.setText("波段追蹤（6 個月視野，達標 = 最大漲幅 +50%）");
        title.setTextSize(15);
        title.setTextColor(UIHelper.ACCENT_PURPLE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        swingTrackingContent = new LinearLayout(this);
        swingTrackingContent.setOrientation(LinearLayout.VERTICAL);
        swingTrackingContent.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        card.addView(swingTrackingContent);

        swingTrackingStatus = new TextView(this);
        swingTrackingStatus.setTextSize(13);
        swingTrackingStatus.setTextColor(UIHelper.TEXT_HINT);
        swingTrackingStatus.setText("載入中...");
        swingTrackingContent.addView(swingTrackingStatus);

        return card;
    }

    private void loadSwingTracking() {
        if (swingTrackingLoading) return;
        swingTrackingLoading = true;
        swingTrackingStatus.setText("載入波段追蹤數據...");
        swingTrackingStatus.setVisibility(View.VISIBLE);

        BridgeClient.getSwingTracking((data, error) -> {
            swingTrackingLoading = false;
            if (error != null || data == null) {
                swingTrackingStatus.setText("無波段追蹤資料");
                AppLog.w("Stock", "loadSwingTracking: " + (error != null ? error : "null"));
                return;
            }
            displaySwingTracking(data);
        });
    }

    private void displaySwingTracking(org.json.JSONObject data) {
        swingTrackingContent.removeAllViews();
        try {
            org.json.JSONObject stats = data.optJSONObject("stats");
            org.json.JSONArray picks = data.optJSONArray("picks");

            if (stats != null && stats.optInt("total_picks", 0) > 0) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                row.setPadding(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4));

                int total = stats.optInt("total_picks", 0);
                int hit50 = stats.optInt("hit_50_count", 0);
                double avgReturn = stats.optDouble("avg_return", 0);
                double avgMaxGain = stats.optDouble("avg_max_gain", 0);

                row.addView(buildStatBox("達標+50%", hit50 + "/" + total,
                        hit50 > 0 ? UIHelper.ACCENT_GREEN : UIHelper.TEXT_SECONDARY));
                row.addView(buildStatBox("平均報酬", String.format("%+.1f%%", avgReturn),
                        avgReturn >= 0 ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_RED));
                row.addView(buildStatBox("平均最大漲幅", String.format("%+.1f%%", avgMaxGain),
                        avgMaxGain >= 0 ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_RED));
                swingTrackingContent.addView(row);
            }

            if (picks != null && picks.length() > 0) {
                for (int i = 0; i < picks.length(); i++) {
                    swingTrackingContent.addView(buildSwingTrackingEntry(picks.getJSONObject(i)));
                }
            } else {
                TextView empty = new TextView(this);
                empty.setText("尚無波段追蹤數據，首次推薦後將自動顯示");
                empty.setTextSize(13);
                empty.setTextColor(UIHelper.TEXT_HINT);
                empty.setPadding(0, UIHelper.dp(this, 4), 0, 0);
                swingTrackingContent.addView(empty);
            }
        } catch (Exception e) {
            TextView errView = new TextView(this);
            errView.setText("解析波段追蹤失敗: " + e.getMessage());
            errView.setTextSize(12);
            errView.setTextColor(UIHelper.ACCENT_RED);
            swingTrackingContent.addView(errView);
            AppLog.e("Stock", "displaySwingTracking失敗: " + e.getMessage());
        }
    }

    private LinearLayout buildSwingTrackingEntry(org.json.JSONObject entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(UIHelper.roundRect(0xFF1E1E2E, 8, this));
        int pad = UIHelper.dp(this, 8);
        row.setPadding(pad, UIHelper.dp(this, 6), pad, UIHelper.dp(this, 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 2), 0, UIHelper.dp(this, 2));
        row.setLayoutParams(lp);

        String recDate = entry.optString("rec_date", "");
        String symbol = entry.optString("symbol", "").replace(".TWO", "").replace(".TW", "");
        String name = entry.optString("name", "");
        double recPrice = entry.optDouble("price_at_rec", 0);
        double lastPrice = entry.optDouble("last_price", 0);
        double returnPct = entry.optDouble("return_pct", 0);
        double maxGainPct = entry.optDouble("max_gain_pct", 0);
        boolean hit50 = entry.optInt("hit_50", 0) == 1;

        String shortDate = recDate.length() >= 10 ? recDate.substring(5) : recDate;

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView nameRow = new TextView(this);
        nameRow.setText(shortDate + "  " + symbol + " " + name + (hit50 ? " ✅達標" : ""));
        nameRow.setTextSize(13);
        nameRow.setTextColor(hit50 ? UIHelper.ACCENT_GREEN : UIHelper.TEXT_PRIMARY);
        leftCol.addView(nameRow);

        TextView priceRow = new TextView(this);
        priceRow.setText(formatPrice(recPrice) + " → " + formatPrice(lastPrice)
                + "  最大漲幅 " + String.format("%+.1f%%", maxGainPct));
        priceRow.setTextSize(11);
        priceRow.setTextColor(UIHelper.TEXT_HINT);
        leftCol.addView(priceRow);

        row.addView(leftCol);

        TextView returnView = new TextView(this);
        returnView.setText(String.format("%+.1f%%", returnPct));
        returnView.setTextSize(14);
        returnView.setTextColor(returnPct > 0 ? UIHelper.ACCENT_GREEN
                : returnPct < 0 ? UIHelper.ACCENT_RED : UIHelper.TEXT_HINT);
        returnView.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(returnView);

        return row;
    }

    // ==================== Watchlist Analysis ====================

    private LinearLayout buildWatchlistCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 16, this));
        int pad = UIHelper.dp(this, 14);
        card.setPadding(pad, pad, pad, pad);
        card.setElevation(UIHelper.dp(this, 3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 4));
        card.setLayoutParams(lp);

        // Title row
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("自選股分析");
        title.setTextSize(15);
        title.setTextColor(UIHelper.ACCENT_BLUE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView addBtn = new TextView(this);
        addBtn.setText("＋新增");
        addBtn.setTextSize(13);
        addBtn.setTextColor(UIHelper.ACCENT_GREEN);
        addBtn.setTypeface(Typeface.DEFAULT_BOLD);
        addBtn.setPadding(UIHelper.dp(this, 12), UIHelper.dp(this, 6),
                UIHelper.dp(this, 12), UIHelper.dp(this, 6));
        addBtn.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.ACCENT_GREEN, 10, 1, this));
        addBtn.setOnClickListener(v -> showAddWatchlistDialog());

        titleRow.addView(title);
        titleRow.addView(addBtn);
        card.addView(titleRow);

        // Content container
        watchlistContent = new LinearLayout(this);
        watchlistContent.setOrientation(LinearLayout.VERTICAL);
        watchlistContent.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        card.addView(watchlistContent);

        return card;
    }

    private List<String> getWatchlist() {
        SharedPreferences prefs = getSharedPreferences("mybot_stock", MODE_PRIVATE);
        String json = prefs.getString(PREF_WATCHLIST, "[]");
        List<String> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getString(i));
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void saveWatchlist(List<String> list) {
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        getSharedPreferences("mybot_stock", MODE_PRIVATE)
                .edit().putString(PREF_WATCHLIST, arr.toString()).apply();
    }

    private JSONObject getWatchlistCache() {
        SharedPreferences prefs = getSharedPreferences("mybot_stock", MODE_PRIVATE);
        String json = prefs.getString(PREF_WATCHLIST_CACHE, "{}");
        try {
            return new JSONObject(json);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void updateWatchlistCache(String symbol, JSONObject data) {
        JSONObject cache = getWatchlistCache();
        try {
            JSONObject entry = cache.optJSONObject(symbol);
            if (entry == null) entry = new JSONObject();

            entry.put("name", data.optString("name", ""));
            entry.put("price", data.optDouble("current_price", 0));

            // Append to analyses history (keep last 10)
            JSONArray analyses = entry.optJSONArray("analyses");
            if (analyses == null) analyses = new JSONArray();

            JSONObject record = new JSONObject();
            record.put("date", new java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()).format(new java.util.Date()));
            record.put("data", data.toString());
            // Prepend (newest first)
            JSONArray updated = new JSONArray();
            updated.put(record);
            for (int i = 0; i < Math.min(analyses.length(), 9); i++) {
                updated.put(analyses.get(i));
            }
            entry.put("analyses", updated);

            cache.put(symbol, entry);
            getSharedPreferences("mybot_stock", MODE_PRIVATE)
                    .edit().putString(PREF_WATCHLIST_CACHE, cache.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void refreshWatchlistUI() {
        watchlistContent.removeAllViews();
        List<String> watchlist = getWatchlist();
        JSONObject cache = getWatchlistCache();

        if (watchlist.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("尚無自選股，點擊＋新增");
            empty.setTextSize(13);
            empty.setTextColor(UIHelper.TEXT_HINT);
            empty.setPadding(0, UIHelper.dp(this, 4), 0, 0);
            watchlistContent.addView(empty);
            return;
        }

        for (String symbol : watchlist) {
            JSONObject cached = cache.optJSONObject(symbol);
            watchlistContent.addView(buildWatchlistItem(symbol, cached));
        }
    }

    private LinearLayout buildWatchlistItem(String symbol, JSONObject cached) {
        String cachedName = cached != null ? cached.optString("name", "") : "";
        double cachedPrice = cached != null ? cached.optDouble("price", 0) : 0;
        JSONArray analyses = cached != null ? cached.optJSONArray("analyses") : null;

        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setBackground(UIHelper.roundRect(0xFF1E1E2E, 12, this));
        int pad = UIHelper.dp(this, 12);
        item.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4));
        item.setLayoutParams(lp);

        // Header row: symbol + name + price + analyze button
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView nameView = new TextView(this);
        String displayName = symbol + (cachedName.isEmpty() ? "" : " " + cachedName);
        nameView.setText(displayName);
        nameView.setTextSize(14);
        nameView.setTextColor(UIHelper.TEXT_PRIMARY);
        nameView.setTypeface(Typeface.DEFAULT_BOLD);
        nameView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        header.addView(nameView);

        if (cachedPrice > 0) {
            TextView priceView = new TextView(this);
            priceView.setText(formatPrice(cachedPrice));
            priceView.setTextSize(14);
            priceView.setTextColor(UIHelper.TEXT_SECONDARY);
            priceView.setPadding(0, 0, UIHelper.dp(this, 8), 0);
            header.addView(priceView);
        }

        TextView analyzeBtn = new TextView(this);
        analyzeBtn.setText(analyses != null && analyses.length() > 0 ? "重新分析" : "分析");
        analyzeBtn.setTextSize(12);
        analyzeBtn.setTextColor(UIHelper.ACCENT_ORANGE);
        analyzeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        analyzeBtn.setPadding(UIHelper.dp(this, 10), UIHelper.dp(this, 5),
                UIHelper.dp(this, 10), UIHelper.dp(this, 5));
        analyzeBtn.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.ACCENT_ORANGE, 8, 1, this));
        header.addView(analyzeBtn);

        item.addView(header);

        // Analysis results container
        LinearLayout resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        item.addView(resultsContainer);

        // Show cached analyses on load
        if (analyses != null && analyses.length() > 0) {
            showCachedAnalyses(resultsContainer, analyses);
        }

        // Analyze button click
        analyzeBtn.setOnClickListener(v -> {
            analyzeBtn.setText("分析中...");
            analyzeBtn.setEnabled(false);
            analyzeBtn.setTextColor(UIHelper.TEXT_HINT);
            AppLog.i("Stock", "自選股分析: " + symbol);

            BridgeClient.analyzeWatchlistStock(symbol, (data, error) -> {
                analyzeBtn.setEnabled(true);
                analyzeBtn.setTextColor(UIHelper.ACCENT_ORANGE);
                analyzeBtn.setText("重新分析");

                if (error != null) {
                    // Show error at top of results
                    TextView errView = new TextView(this);
                    errView.setText("分析失敗: " + error);
                    errView.setTextSize(12);
                    errView.setTextColor(UIHelper.ACCENT_RED);
                    errView.setPadding(0, UIHelper.dp(this, 6), 0, 0);
                    resultsContainer.addView(errView, 0);
                    AppLog.e("Stock", "自選股分析失敗 " + symbol + ": " + error);
                    return;
                }
                if (data == null) return;

                // Cache result (appends to history)
                updateWatchlistCache(symbol, data);

                // Update header name
                String newName = data.optString("name", "");
                if (!newName.isEmpty()) {
                    nameView.setText(symbol + " " + newName);
                }

                // Rebuild results from updated cache
                resultsContainer.removeAllViews();
                JSONObject updatedCache = getWatchlistCache().optJSONObject(symbol);
                JSONArray updatedAnalyses = updatedCache != null ? updatedCache.optJSONArray("analyses") : null;
                if (updatedAnalyses != null) {
                    showCachedAnalyses(resultsContainer, updatedAnalyses);
                }

                AppLog.i("Stock", "自選股分析完成 " + symbol + ": trend=" + data.optString("trend"));
            });
        });

        // Long press to delete
        item.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                    .setTitle("移除自選股")
                    .setMessage("確定要移除 " + symbol + (cachedName.isEmpty() ? "" : " " + cachedName) + " 嗎？")
                    .setPositiveButton("移除", (d, w) -> {
                        List<String> list = getWatchlist();
                        list.remove(symbol);
                        saveWatchlist(list);
                        // Also remove cache
                        JSONObject c = getWatchlistCache();
                        c.remove(symbol);
                        getSharedPreferences("mybot_stock", MODE_PRIVATE)
                                .edit().putString(PREF_WATCHLIST_CACHE, c.toString()).apply();
                        refreshWatchlistUI();
                        AppLog.i("Stock", "自選股移除: " + symbol);
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });

        return item;
    }

    private void showCachedAnalyses(LinearLayout container, JSONArray analyses) {
        for (int i = 0; i < analyses.length(); i++) {
            try {
                JSONObject record = analyses.getJSONObject(i);
                String date = record.optString("date", "");
                String dataStr = record.optString("data", "");
                if (dataStr.isEmpty()) continue;
                JSONObject data = new JSONObject(dataStr);

                boolean isLatest = (i == 0);

                // Date header
                TextView dateHeader = new TextView(this);
                dateHeader.setText(isLatest ? "最新分析 — " + date : "歷史分析 — " + date);
                dateHeader.setTextSize(11);
                dateHeader.setTextColor(isLatest ? UIHelper.ACCENT_ORANGE : UIHelper.TEXT_HINT);
                dateHeader.setTypeface(Typeface.DEFAULT_BOLD);
                dateHeader.setPadding(0, UIHelper.dp(this, i == 0 ? 8 : 12), 0, 0);
                container.addView(dateHeader);

                if (isLatest) {
                    // Latest: show full expanded
                    displayWatchlistAnalysis(container, data);
                } else {
                    // Older: compact summary, tap to expand
                    String trend = data.optString("trend", "?");
                    String suggestion = data.optString("suggestion", "");
                    int trendColor = trend.contains("多") ? UIHelper.ACCENT_GREEN
                            : trend.contains("空") ? UIHelper.ACCENT_RED : UIHelper.ACCENT_ORANGE;

                    LinearLayout summary = new LinearLayout(this);
                    summary.setOrientation(LinearLayout.VERTICAL);
                    summary.setPadding(0, UIHelper.dp(this, 2), 0, 0);

                    TextView trendLine = new TextView(this);
                    trendLine.setText("趨勢: " + trend + (suggestion.isEmpty() ? "" : " — " + suggestion));
                    trendLine.setTextSize(12);
                    trendLine.setTextColor(trendColor);
                    trendLine.setMaxLines(2);
                    trendLine.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    summary.addView(trendLine);

                    // Tap to expand/collapse
                    LinearLayout detailContainer = new LinearLayout(this);
                    detailContainer.setOrientation(LinearLayout.VERTICAL);
                    detailContainer.setVisibility(View.GONE);
                    summary.addView(detailContainer);

                    TextView expandHint = new TextView(this);
                    expandHint.setText("點擊展開詳情 ▾");
                    expandHint.setTextSize(10);
                    expandHint.setTextColor(UIHelper.TEXT_HINT);
                    expandHint.setPadding(0, UIHelper.dp(this, 2), 0, 0);
                    summary.addView(expandHint);

                    final JSONObject fData = data;
                    summary.setOnClickListener(v -> {
                        if (detailContainer.getVisibility() == View.GONE) {
                            if (detailContainer.getChildCount() == 0) {
                                displayWatchlistAnalysis(detailContainer, fData);
                            }
                            detailContainer.setVisibility(View.VISIBLE);
                            expandHint.setText("點擊收合 ▴");
                        } else {
                            detailContainer.setVisibility(View.GONE);
                            expandHint.setText("點擊展開詳情 ▾");
                        }
                    });

                    container.addView(summary);
                }
            } catch (Exception ignored) {}
        }
    }

    private void displayWatchlistAnalysis(LinearLayout container, JSONObject data) {
        int topPad = UIHelper.dp(this, 8);

        // Trend
        String trend = data.optString("trend", "");
        String trendReason = data.optString("trend_reason", "");
        if (!trend.isEmpty()) {
            int trendColor = trend.contains("多") ? UIHelper.ACCENT_GREEN
                    : trend.contains("空") ? UIHelper.ACCENT_RED : UIHelper.ACCENT_ORANGE;

            TextView trendView = new TextView(this);
            trendView.setText("趨勢: " + trend);
            trendView.setTextSize(14);
            trendView.setTextColor(trendColor);
            trendView.setTypeface(Typeface.DEFAULT_BOLD);
            trendView.setPadding(0, topPad, 0, 0);
            container.addView(trendView);

            if (!trendReason.isEmpty()) {
                TextView reasonView = new TextView(this);
                reasonView.setText(trendReason);
                reasonView.setTextSize(12);
                reasonView.setTextColor(UIHelper.TEXT_SECONDARY);
                reasonView.setLineSpacing(UIHelper.dp(this, 2), 1f);
                reasonView.setPadding(0, UIHelper.dp(this, 2), 0, 0);
                container.addView(reasonView);
            }
        }

        // Signals
        JSONArray signals = data.optJSONArray("signals");
        if (signals != null && signals.length() > 0) {
            for (int i = 0; i < signals.length(); i++) {
                String signal = signals.optString(i, "");
                if (!signal.isEmpty()) {
                    TextView sv = new TextView(this);
                    sv.setText("• " + signal);
                    sv.setTextSize(12);
                    sv.setTextColor(UIHelper.ACCENT_BLUE);
                    sv.setPadding(0, UIHelper.dp(this, 3), 0, 0);
                    sv.setLineSpacing(UIHelper.dp(this, 2), 1f);
                    container.addView(sv);
                }
            }
        }

        // Support / Resistance
        String support = data.optString("support", "");
        String resistance = data.optString("resistance", "");
        if (!support.isEmpty() || !resistance.isEmpty()) {
            TextView levelView = new TextView(this);
            StringBuilder levels = new StringBuilder();
            if (!support.isEmpty()) levels.append("支撐: ").append(support);
            if (!support.isEmpty() && !resistance.isEmpty()) levels.append("  /  ");
            if (!resistance.isEmpty()) levels.append("壓力: ").append(resistance);
            levelView.setText(levels.toString());
            levelView.setTextSize(12);
            levelView.setTextColor(UIHelper.ACCENT_PURPLE);
            levelView.setPadding(0, UIHelper.dp(this, 4), 0, 0);
            container.addView(levelView);
        }

        // Institutional summary
        String instSummary = data.optString("institutional_summary", "");
        if (!instSummary.isEmpty()) {
            TextView instView = new TextView(this);
            instView.setText("法人: " + instSummary);
            instView.setTextSize(12);
            instView.setTextColor(UIHelper.TEXT_SECONDARY);
            instView.setPadding(0, UIHelper.dp(this, 4), 0, 0);
            container.addView(instView);
        }

        // Financial summary
        String finSummary = data.optString("financial_summary", "");
        if (!finSummary.isEmpty()) {
            TextView finView = new TextView(this);
            finView.setText("財報: " + finSummary);
            finView.setTextSize(12);
            finView.setTextColor(UIHelper.ACCENT_GREEN);
            finView.setPadding(0, UIHelper.dp(this, 2), 0, 0);
            container.addView(finView);
        }

        // Conference summary (法說會自結 — 參考資料)
        String confSummary = data.optString("conference_summary", "");
        if (!confSummary.isEmpty()) {
            TextView confTitle = new TextView(this);
            confTitle.setText("法說會自結（參考資料）");
            confTitle.setTextSize(11);
            confTitle.setTextColor(UIHelper.ACCENT_ORANGE);
            confTitle.setTypeface(Typeface.DEFAULT_BOLD);
            confTitle.setPadding(0, UIHelper.dp(this, 6), 0, 0);
            container.addView(confTitle);

            TextView confView = new TextView(this);
            confView.setText(confSummary);
            confView.setTextSize(12);
            confView.setTextColor(UIHelper.TEXT_SECONDARY);
            confView.setLineSpacing(UIHelper.dp(this, 2), 1f);
            confView.setPadding(0, UIHelper.dp(this, 2), 0, 0);
            container.addView(confView);
        }

        // Risks
        JSONArray risks = data.optJSONArray("risks");
        if (risks != null && risks.length() > 0) {
            StringBuilder riskText = new StringBuilder("風險: ");
            for (int i = 0; i < risks.length(); i++) {
                if (i > 0) riskText.append("；");
                riskText.append(risks.optString(i, ""));
            }
            TextView riskView = new TextView(this);
            riskView.setText(riskText.toString());
            riskView.setTextSize(12);
            riskView.setTextColor(UIHelper.ACCENT_RED);
            riskView.setPadding(0, UIHelper.dp(this, 4), 0, 0);
            container.addView(riskView);
        }

        // Suggestion
        String suggestion = data.optString("suggestion", "");
        if (!suggestion.isEmpty()) {
            LinearLayout sugBox = new LinearLayout(this);
            sugBox.setOrientation(LinearLayout.VERTICAL);
            sugBox.setBackground(UIHelper.roundRect(0xFF262640, 8, this));
            int sp = UIHelper.dp(this, 8);
            sugBox.setPadding(sp, sp, sp, sp);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.setMargins(0, UIHelper.dp(this, 6), 0, 0);
            sugBox.setLayoutParams(slp);

            TextView sugTitle = new TextView(this);
            sugTitle.setText("操作建議");
            sugTitle.setTextSize(12);
            sugTitle.setTextColor(UIHelper.ACCENT_ORANGE);
            sugTitle.setTypeface(Typeface.DEFAULT_BOLD);
            sugBox.addView(sugTitle);

            TextView sugText = new TextView(this);
            sugText.setText(suggestion);
            sugText.setTextSize(12);
            sugText.setTextColor(UIHelper.TEXT_SECONDARY);
            sugText.setLineSpacing(UIHelper.dp(this, 2), 1f);
            sugText.setPadding(0, UIHelper.dp(this, 4), 0, 0);
            sugBox.addView(sugText);

            container.addView(sugBox);
        }
    }

    private void showAddWatchlistDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("輸入股票代號（如 2330）");
        input.setTextColor(UIHelper.TEXT_PRIMARY);
        input.setHintTextColor(UIHelper.TEXT_HINT);
        int dp16 = UIHelper.dp(this, 16);
        input.setPadding(dp16, dp16, dp16, dp16);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle("新增自選股")
                .setView(input)
                .setPositiveButton("新增", (dialog, which) -> {
                    String symbol = input.getText().toString().trim();
                    if (symbol.length() != 4 || !symbol.matches("\\d{4}")) {
                        android.widget.Toast.makeText(this, "請輸入4位數股票代號", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    List<String> list = getWatchlist();
                    if (list.contains(symbol)) {
                        android.widget.Toast.makeText(this, symbol + " 已在自選股中", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    list.add(symbol);
                    saveWatchlist(list);
                    refreshWatchlistUI();
                    AppLog.i("Stock", "自選股新增: " + symbol);
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
