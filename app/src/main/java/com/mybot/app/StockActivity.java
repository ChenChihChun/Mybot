package com.mybot.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.DecimalFormat;

public class StockActivity extends AppCompatActivity {

    // Recommendation UI
    private LinearLayout recContent;
    private TextView recStatus;
    private boolean recLoading = false;
    // Tracking UI
    private LinearLayout trackingContent;
    private TextView trackingStatus;
    private boolean trackingLoading = false;

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
        loadTracking();
    }

    private void buildUI() {
        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "台股 AI 推薦");
        root.addView(topBar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int cp = UIHelper.dp(this, 12);
        content.setPadding(cp, UIHelper.dp(this, 8), cp, cp);

        // Daily Recommendation card
        content.addView(buildRecommendationCard());

        // Tracking & Accuracy card
        content.addView(buildTrackingCard());

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
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

        // Header: rank + symbol + name + price
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
        nameView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        header.addView(rankView);
        header.addView(nameView);
        if (price > 0) {
            TextView priceView = new TextView(this);
            priceView.setText(formatPrice(price));
            priceView.setTextSize(15);
            priceView.setTextColor(UIHelper.TEXT_PRIMARY);
            priceView.setTypeface(Typeface.DEFAULT_BOLD);
            header.addView(priceView);
        }
        card.addView(header);

        // Yahoo Finance link
        TextView yahooLink = new TextView(this);
        yahooLink.setText("Yahoo 個股頁面 →");
        yahooLink.setTextSize(12);
        yahooLink.setTextColor(UIHelper.ACCENT_BLUE);
        yahooLink.setPadding(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 2));
        yahooLink.setOnClickListener(v -> {
            String url = "https://tw.stock.yahoo.com/quote/" + symbol + ".TW";
            try {
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(url)));
            } catch (Exception ignored) {}
        });
        card.addView(yahooLink);

        // Institutional + Financial summary
        String instSummary = pick.optString("institutional_summary", "");
        if (!instSummary.isEmpty()) {
            TextView instView = new TextView(this);
            instView.setText("法人: " + instSummary);
            instView.setTextSize(12);
            instView.setTextColor(UIHelper.ACCENT_BLUE);
            instView.setPadding(0, UIHelper.dp(this, 4), 0, 0);
            card.addView(instView);
        }

        String finSummary = pick.optString("financial_summary", "");
        if (!finSummary.isEmpty()) {
            TextView finView = new TextView(this);
            finView.setText("財報: " + finSummary);
            finView.setTextSize(12);
            finView.setTextColor(UIHelper.ACCENT_GREEN);
            finView.setPadding(0, UIHelper.dp(this, 2), 0, 0);
            card.addView(finView);
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
            }

            card.addView(stratBox);
        }

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
            statusView.setText("追蹤中(" + daysTracked + "/14天)");
            statusView.setTextColor(UIHelper.ACCENT_ORANGE);
        }
        statusView.setTextSize(10);
        statusView.setGravity(Gravity.END);
        rightCol.addView(statusView);

        row.addView(rightCol);

        return row;
    }
}
