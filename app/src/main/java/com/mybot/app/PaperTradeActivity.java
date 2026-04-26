package com.mybot.app;

import android.app.AlertDialog;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DecimalFormat;

public class PaperTradeActivity extends AppCompatActivity {

    // Overview card
    private TextView totalValueText;
    private TextView cashText;
    private TextView holdingsValueText;
    private TextView returnText;
    private TextView todayPlText;
    private TextView statusText;

    // Holdings card
    private LinearLayout holdingsContent;
    private TextView holdingsStatus;
    private boolean holdingsLoading = false;

    // Trades card
    private LinearLayout tradesContent;
    private TextView tradesStatus;
    private boolean tradesLoading = false;

    // Strategy card
    private LinearLayout strategyContent;
    private TextView strategyStatus;
    private boolean strategyLoading = false;

    // Performance card
    private LinearLayout performanceContent;
    private TextView performanceStatus;
    private boolean performanceLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        buildUI();
        AppLog.i("PaperTrade", "Activity opened");
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOverview();
        loadHoldings();
        loadTrades();
        loadStrategy();
        loadPerformance();
    }

    private void buildUI() {
        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar with controls
        LinearLayout topBar = buildTopBar();
        root.addView(topBar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int cp = UIHelper.dp(this, 12);
        content.setPadding(cp, UIHelper.dp(this, 8), cp, cp);

        // Cards
        content.addView(buildOverviewCard());
        content.addView(buildHoldingsCard());
        content.addView(buildTradesCard());
        content.addView(buildStrategyCard());
        content.addView(buildPerformanceCard());

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private LinearLayout buildTopBar() {
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(UIHelper.BG_TOP_BAR);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int h = UIHelper.dp(this, 16);
        topBar.setPadding(UIHelper.dp(this, 12), h, UIHelper.dp(this, 12), h);

        TextView backBtn = new TextView(this);
        backBtn.setText("<");
        backBtn.setTextSize(20);
        backBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        backBtn.setTypeface(Typeface.DEFAULT_BOLD);
        backBtn.setPadding(UIHelper.dp(this, 8), 0, UIHelper.dp(this, 16), 0);
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn);

        TextView title = new TextView(this);
        title.setText("AI Trader");
        title.setTextSize(18);
        title.setTextColor(UIHelper.TEXT_PRIMARY);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        title.setLayoutParams(titleLp);
        topBar.addView(title);

        // Toggle button
        TextView toggleBtn = new TextView(this);
        toggleBtn.setText("||");
        toggleBtn.setTextSize(14);
        toggleBtn.setTextColor(UIHelper.ACCENT_ORANGE);
        toggleBtn.setPadding(UIHelper.dp(this, 12), UIHelper.dp(this, 6),
                UIHelper.dp(this, 12), UIHelper.dp(this, 6));
        toggleBtn.setBackground(UIHelper.roundRect(Color.parseColor("#3D2A00"), 8, this));
        toggleBtn.setOnClickListener(v -> showToggleDialog());
        topBar.addView(toggleBtn);

        return topBar;
    }

    // ==================== Overview Card ====================

    private LinearLayout buildOverviewCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 16, this));
        int pad = UIHelper.dp(this, 14);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 6), 0, UIHelper.dp(this, 6));
        card.setLayoutParams(lp);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = new TextView(this);
        icon.setText("\uD83D\uDCCA");
        icon.setTextSize(18);
        icon.setPadding(0, 0, UIHelper.dp(this, 8), 0);
        header.addView(icon);

        TextView titleView = new TextView(this);
        titleView.setText("Portfolio Overview");
        titleView.setTextSize(16);
        titleView.setTextColor(UIHelper.TEXT_PRIMARY);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(titleView);

        card.addView(header);

        // Status indicator
        statusText = new TextView(this);
        statusText.setTextSize(11);
        statusText.setTextColor(UIHelper.TEXT_HINT);
        statusText.setPadding(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 8));
        card.addView(statusText);

        // Total value (big number)
        totalValueText = new TextView(this);
        totalValueText.setText("--");
        totalValueText.setTextSize(28);
        totalValueText.setTextColor(UIHelper.ACCENT_ORANGE);
        totalValueText.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(totalValueText);

        // Return percentage
        returnText = new TextView(this);
        returnText.setText("-- %");
        returnText.setTextSize(14);
        returnText.setTextColor(UIHelper.TEXT_SECONDARY);
        returnText.setPadding(0, 0, 0, UIHelper.dp(this, 12));
        card.addView(returnText);

        // Stats row
        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);

        // Cash
        LinearLayout cashCol = new LinearLayout(this);
        cashCol.setOrientation(LinearLayout.VERTICAL);
        cashCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView cashLabel = new TextView(this);
        cashLabel.setText("Cash");
        cashLabel.setTextSize(11);
        cashLabel.setTextColor(UIHelper.TEXT_HINT);
        cashCol.addView(cashLabel);
        cashText = new TextView(this);
        cashText.setText("--");
        cashText.setTextSize(14);
        cashText.setTextColor(UIHelper.TEXT_PRIMARY);
        cashCol.addView(cashText);
        statsRow.addView(cashCol);

        // Holdings value
        LinearLayout holdingsCol = new LinearLayout(this);
        holdingsCol.setOrientation(LinearLayout.VERTICAL);
        holdingsCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView holdingsLabel = new TextView(this);
        holdingsLabel.setText("Holdings");
        holdingsLabel.setTextSize(11);
        holdingsLabel.setTextColor(UIHelper.TEXT_HINT);
        holdingsCol.addView(holdingsLabel);
        holdingsValueText = new TextView(this);
        holdingsValueText.setText("--");
        holdingsValueText.setTextSize(14);
        holdingsValueText.setTextColor(UIHelper.TEXT_PRIMARY);
        holdingsCol.addView(holdingsValueText);
        statsRow.addView(holdingsCol);

        // Today P/L
        LinearLayout todayCol = new LinearLayout(this);
        todayCol.setOrientation(LinearLayout.VERTICAL);
        todayCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView todayLabel = new TextView(this);
        todayLabel.setText("Today");
        todayLabel.setTextSize(11);
        todayLabel.setTextColor(UIHelper.TEXT_HINT);
        todayCol.addView(todayLabel);
        todayPlText = new TextView(this);
        todayPlText.setText("--");
        todayPlText.setTextSize(14);
        todayPlText.setTextColor(UIHelper.TEXT_PRIMARY);
        todayCol.addView(todayPlText);
        statsRow.addView(todayCol);

        card.addView(statsRow);

        return card;
    }

    private void loadOverview() {
        BridgeClient.getPaperTradeOverview((success, data, error) -> {
            if (success && data != null) {
                try {
                    double totalValue = data.optDouble("total_value", 0);
                    double cash = data.optDouble("cash", 0);
                    double holdingsValue = data.optDouble("holdings_value", 0);
                    double returnPct = data.optDouble("total_return_pct", 0);
                    double todayPl = data.optDouble("today_pl", 0);
                    String status = data.optString("status", "active");
                    int version = data.optInt("strategy_version", 1);

                    totalValueText.setText(formatMoney(totalValue));
                    cashText.setText(formatMoney(cash));
                    holdingsValueText.setText(formatMoney(holdingsValue));

                    String returnStr = String.format("%s%.2f%%", returnPct >= 0 ? "+" : "", returnPct);
                    returnText.setText(returnStr);
                    returnText.setTextColor(returnPct >= 0 ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_RED);

                    String todayStr = String.format("%s%s", todayPl >= 0 ? "+" : "", formatMoney(todayPl));
                    todayPlText.setText(todayStr);
                    todayPlText.setTextColor(todayPl >= 0 ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_RED);

                    String statusStr = status.equals("paused") ? "PAUSED" : "ACTIVE";
                    statusText.setText(statusStr + " | Strategy v" + version);
                    statusText.setTextColor(status.equals("paused") ? UIHelper.ACCENT_RED : UIHelper.ACCENT_GREEN);

                    AppLog.i("PaperTrade", "Overview loaded: " + formatMoney(totalValue));
                } catch (Exception e) {
                    AppLog.e("PaperTrade", "Overview parse error: " + e.getMessage());
                }
            } else {
                statusText.setText("Loading...");
                statusText.setTextColor(UIHelper.TEXT_HINT);
            }
        });
    }

    // ==================== Holdings Card ====================

    private LinearLayout buildHoldingsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 16, this));
        int pad = UIHelper.dp(this, 14);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 6), 0, UIHelper.dp(this, 6));
        card.setLayoutParams(lp);

        // Header
        TextView titleView = new TextView(this);
        titleView.setText("\uD83D\uDCBC Current Holdings");
        titleView.setTextSize(15);
        titleView.setTextColor(UIHelper.TEXT_PRIMARY);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setPadding(0, 0, 0, UIHelper.dp(this, 10));
        card.addView(titleView);

        holdingsStatus = new TextView(this);
        holdingsStatus.setText("Loading...");
        holdingsStatus.setTextSize(12);
        holdingsStatus.setTextColor(UIHelper.TEXT_HINT);
        card.addView(holdingsStatus);

        holdingsContent = new LinearLayout(this);
        holdingsContent.setOrientation(LinearLayout.VERTICAL);
        card.addView(holdingsContent);

        return card;
    }

    private void loadHoldings() {
        if (holdingsLoading) return;
        holdingsLoading = true;

        BridgeClient.getPaperTradeHoldings((success, data, error) -> {
            holdingsLoading = false;
            holdingsContent.removeAllViews();

            if (success && data != null) {
                try {
                    if (data.length() == 0) {
                        holdingsStatus.setText("No holdings");
                        holdingsStatus.setVisibility(View.VISIBLE);
                        return;
                    }

                    holdingsStatus.setVisibility(View.GONE);

                    for (int i = 0; i < data.length(); i++) {
                        JSONObject h = data.getJSONObject(i);
                        holdingsContent.addView(buildHoldingRow(h));
                    }

                    AppLog.i("PaperTrade", "Holdings loaded: " + data.length());
                } catch (Exception e) {
                    holdingsStatus.setText("Parse error");
                    holdingsStatus.setVisibility(View.VISIBLE);
                    AppLog.e("PaperTrade", "Holdings parse error: " + e.getMessage());
                }
            } else {
                holdingsStatus.setText(error != null ? error : "Failed to load");
                holdingsStatus.setVisibility(View.VISIBLE);
            }
        });
    }

    private LinearLayout buildHoldingRow(JSONObject h) throws Exception {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8));

        // First line: symbol + name + unrealized P/L
        LinearLayout line1 = new LinearLayout(this);
        line1.setOrientation(LinearLayout.HORIZONTAL);

        TextView symbolText = new TextView(this);
        symbolText.setText(h.getString("symbol") + " " + h.optString("name", ""));
        symbolText.setTextSize(14);
        symbolText.setTextColor(UIHelper.TEXT_PRIMARY);
        symbolText.setTypeface(Typeface.DEFAULT_BOLD);
        symbolText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        line1.addView(symbolText);

        double plPct = h.optDouble("unrealized_pl_pct", 0);
        double plAmt = h.optDouble("unrealized_pl", 0);
        TextView plText = new TextView(this);
        plText.setText(String.format("%s%.1f%% (%s)", plPct >= 0 ? "+" : "", plPct, formatMoney(plAmt)));
        plText.setTextSize(13);
        plText.setTextColor(plPct >= 0 ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_RED);
        line1.addView(plText);

        row.addView(line1);

        // Second line: shares + avg cost + current price + days held
        LinearLayout line2 = new LinearLayout(this);
        line2.setOrientation(LinearLayout.HORIZONTAL);
        line2.setPadding(0, UIHelper.dp(this, 2), 0, 0);

        String details = String.format("%d shares @ %.2f | Now: %.2f | %d days",
                h.optInt("shares", 0),
                h.optDouble("avg_cost", 0),
                h.optDouble("current_price", 0),
                h.optInt("days_held", 0));

        TextView detailText = new TextView(this);
        detailText.setText(details);
        detailText.setTextSize(11);
        detailText.setTextColor(UIHelper.TEXT_HINT);
        line2.addView(detailText);

        row.addView(line2);

        // Third line: entry reason
        String reason = h.optString("entry_reason", "");
        if (!reason.isEmpty()) {
            TextView reasonText = new TextView(this);
            reasonText.setText(reason);
            reasonText.setTextSize(11);
            reasonText.setTextColor(UIHelper.TEXT_SECONDARY);
            reasonText.setPadding(0, UIHelper.dp(this, 2), 0, 0);
            row.addView(reasonText);
        }

        return row;
    }

    // ==================== Trades Card ====================

    private LinearLayout buildTradesCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 16, this));
        int pad = UIHelper.dp(this, 14);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 6), 0, UIHelper.dp(this, 6));
        card.setLayoutParams(lp);

        TextView titleView = new TextView(this);
        titleView.setText("\uD83D\uDCDD Recent Trades");
        titleView.setTextSize(15);
        titleView.setTextColor(UIHelper.TEXT_PRIMARY);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setPadding(0, 0, 0, UIHelper.dp(this, 10));
        card.addView(titleView);

        tradesStatus = new TextView(this);
        tradesStatus.setText("Loading...");
        tradesStatus.setTextSize(12);
        tradesStatus.setTextColor(UIHelper.TEXT_HINT);
        card.addView(tradesStatus);

        tradesContent = new LinearLayout(this);
        tradesContent.setOrientation(LinearLayout.VERTICAL);
        card.addView(tradesContent);

        return card;
    }

    private void loadTrades() {
        if (tradesLoading) return;
        tradesLoading = true;

        BridgeClient.getPaperTradeTrades(10, (success, data, error) -> {
            tradesLoading = false;
            tradesContent.removeAllViews();

            if (success && data != null) {
                try {
                    if (data.length() == 0) {
                        tradesStatus.setText("No trades yet");
                        tradesStatus.setVisibility(View.VISIBLE);
                        return;
                    }

                    tradesStatus.setVisibility(View.GONE);

                    for (int i = 0; i < Math.min(data.length(), 10); i++) {
                        JSONObject t = data.getJSONObject(i);
                        tradesContent.addView(buildTradeRow(t));
                    }

                    AppLog.i("PaperTrade", "Trades loaded: " + data.length());
                } catch (Exception e) {
                    tradesStatus.setText("Parse error");
                    tradesStatus.setVisibility(View.VISIBLE);
                }
            } else {
                tradesStatus.setText(error != null ? error : "Failed to load");
                tradesStatus.setVisibility(View.VISIBLE);
            }
        });
    }

    private LinearLayout buildTradeRow(JSONObject t) throws Exception {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, UIHelper.dp(this, 6), 0, UIHelper.dp(this, 6));

        String side = t.getString("side");
        boolean isBuy = side.equals("buy");

        // Side indicator
        TextView sideText = new TextView(this);
        sideText.setText(isBuy ? "BUY" : "SELL");
        sideText.setTextSize(11);
        sideText.setTextColor(isBuy ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_RED);
        sideText.setTypeface(Typeface.DEFAULT_BOLD);
        sideText.setPadding(0, 0, UIHelper.dp(this, 8), 0);
        row.addView(sideText);

        // Symbol + details
        LinearLayout detailCol = new LinearLayout(this);
        detailCol.setOrientation(LinearLayout.VERTICAL);
        detailCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView symbolText = new TextView(this);
        symbolText.setText(t.getString("symbol") + " x" + t.getInt("shares"));
        symbolText.setTextSize(13);
        symbolText.setTextColor(UIHelper.TEXT_PRIMARY);
        detailCol.addView(symbolText);

        String dateStr = t.optString("created_at", "").substring(0, 10);
        TextView dateText = new TextView(this);
        dateText.setText(dateStr + " @ " + String.format("%.2f", t.getDouble("price")));
        dateText.setTextSize(11);
        dateText.setTextColor(UIHelper.TEXT_HINT);
        detailCol.addView(dateText);

        row.addView(detailCol);

        // Profit (for sells)
        if (!isBuy && !t.isNull("profit_pct")) {
            double profitPct = t.getDouble("profit_pct");
            TextView profitText = new TextView(this);
            profitText.setText(String.format("%s%.1f%%", profitPct >= 0 ? "+" : "", profitPct));
            profitText.setTextSize(13);
            profitText.setTextColor(profitPct >= 0 ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_RED);
            row.addView(profitText);
        }

        return row;
    }

    // ==================== Strategy Card ====================

    private LinearLayout buildStrategyCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 16, this));
        int pad = UIHelper.dp(this, 14);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 6), 0, UIHelper.dp(this, 6));
        card.setLayoutParams(lp);

        TextView titleView = new TextView(this);
        titleView.setText("\uD83E\uDDEC Strategy");
        titleView.setTextSize(15);
        titleView.setTextColor(UIHelper.TEXT_PRIMARY);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setPadding(0, 0, 0, UIHelper.dp(this, 10));
        card.addView(titleView);

        strategyStatus = new TextView(this);
        strategyStatus.setText("Loading...");
        strategyStatus.setTextSize(12);
        strategyStatus.setTextColor(UIHelper.TEXT_HINT);
        card.addView(strategyStatus);

        strategyContent = new LinearLayout(this);
        strategyContent.setOrientation(LinearLayout.VERTICAL);
        card.addView(strategyContent);

        return card;
    }

    private void loadStrategy() {
        if (strategyLoading) return;
        strategyLoading = true;

        BridgeClient.getPaperTradeStrategy((success, data, error) -> {
            strategyLoading = false;
            strategyContent.removeAllViews();

            if (success && data != null) {
                try {
                    strategyStatus.setVisibility(View.GONE);

                    int version = data.optInt("version", 1);
                    String desc = data.optString("description", "Momentum strategy");

                    TextView versionText = new TextView(this);
                    versionText.setText("Version " + version + ": " + desc);
                    versionText.setTextSize(13);
                    versionText.setTextColor(UIHelper.TEXT_PRIMARY);
                    strategyContent.addView(versionText);

                    // Stats
                    Double winRate = data.isNull("win_rate") ? null : data.getDouble("win_rate");
                    Double avgReturn = data.isNull("avg_return") ? null : data.getDouble("avg_return");

                    if (winRate != null || avgReturn != null) {
                        String statsStr = "";
                        if (winRate != null) statsStr += String.format("Win: %.0f%%", winRate);
                        if (avgReturn != null) statsStr += String.format("  Avg: %.1f%%", avgReturn);

                        TextView statsText = new TextView(this);
                        statsText.setText(statsStr);
                        statsText.setTextSize(12);
                        statsText.setTextColor(UIHelper.TEXT_SECONDARY);
                        statsText.setPadding(0, UIHelper.dp(this, 4), 0, 0);
                        strategyContent.addView(statsText);
                    }

                    // Recent decisions
                    JSONArray decisions = data.optJSONArray("recent_decisions");
                    if (decisions != null && decisions.length() > 0) {
                        TextView decisionHeader = new TextView(this);
                        decisionHeader.setText("Recent AI Decisions:");
                        decisionHeader.setTextSize(12);
                        decisionHeader.setTextColor(UIHelper.TEXT_HINT);
                        decisionHeader.setPadding(0, UIHelper.dp(this, 10), 0, UIHelper.dp(this, 4));
                        strategyContent.addView(decisionHeader);

                        for (int i = 0; i < Math.min(decisions.length(), 3); i++) {
                            JSONObject d = decisions.getJSONObject(i);
                            String type = d.optString("type", "");
                            String reasoning = d.optString("reasoning", "");
                            String time = d.optString("timestamp", "").substring(11, 16);

                            TextView decText = new TextView(this);
                            decText.setText(time + " [" + type + "] " + reasoning);
                            decText.setTextSize(11);
                            decText.setTextColor(UIHelper.TEXT_SECONDARY);
                            decText.setPadding(0, UIHelper.dp(this, 2), 0, 0);
                            strategyContent.addView(decText);
                        }
                    }

                    AppLog.i("PaperTrade", "Strategy loaded: v" + version);
                } catch (Exception e) {
                    strategyStatus.setText("Parse error");
                    strategyStatus.setVisibility(View.VISIBLE);
                }
            } else {
                strategyStatus.setText(error != null ? error : "Failed to load");
                strategyStatus.setVisibility(View.VISIBLE);
            }
        });
    }

    // ==================== Performance Card ====================

    private LinearLayout buildPerformanceCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 16, this));
        int pad = UIHelper.dp(this, 14);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 6), 0, UIHelper.dp(this, 6));
        card.setLayoutParams(lp);

        TextView titleView = new TextView(this);
        titleView.setText("\uD83C\uDFC6 Performance");
        titleView.setTextSize(15);
        titleView.setTextColor(UIHelper.TEXT_PRIMARY);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setPadding(0, 0, 0, UIHelper.dp(this, 10));
        card.addView(titleView);

        performanceStatus = new TextView(this);
        performanceStatus.setText("Loading...");
        performanceStatus.setTextSize(12);
        performanceStatus.setTextColor(UIHelper.TEXT_HINT);
        card.addView(performanceStatus);

        performanceContent = new LinearLayout(this);
        performanceContent.setOrientation(LinearLayout.VERTICAL);
        card.addView(performanceContent);

        return card;
    }

    private void loadPerformance() {
        if (performanceLoading) return;
        performanceLoading = true;

        BridgeClient.getPaperTradePerformance((success, data, error) -> {
            performanceLoading = false;
            performanceContent.removeAllViews();

            if (success && data != null) {
                try {
                    performanceStatus.setVisibility(View.GONE);

                    // Stats grid
                    LinearLayout statsGrid = new LinearLayout(this);
                    statsGrid.setOrientation(LinearLayout.HORIZONTAL);

                    // Left column
                    LinearLayout leftCol = new LinearLayout(this);
                    leftCol.setOrientation(LinearLayout.VERTICAL);
                    leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    addStatRow(leftCol, "Total Trades", String.valueOf(data.optInt("total_trades", 0)));
                    addStatRow(leftCol, "Win Rate", String.format("%.1f%%", data.optDouble("win_rate", 0)));
                    addStatRow(leftCol, "Best Trade", String.format("+%.1f%%", data.optDouble("max_win", 0)));
                    statsGrid.addView(leftCol);

                    // Right column
                    LinearLayout rightCol = new LinearLayout(this);
                    rightCol.setOrientation(LinearLayout.VERTICAL);
                    rightCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    addStatRow(rightCol, "Total Return", String.format("%.2f%%", data.optDouble("total_return", 0)));
                    addStatRow(rightCol, "Avg Return", String.format("%.2f%%", data.optDouble("avg_return", 0)));
                    addStatRow(rightCol, "Worst Trade", String.format("%.1f%%", data.optDouble("max_loss", 0)));
                    statsGrid.addView(rightCol);

                    performanceContent.addView(statsGrid);

                    AppLog.i("PaperTrade", "Performance loaded");
                } catch (Exception e) {
                    performanceStatus.setText("Parse error");
                    performanceStatus.setVisibility(View.VISIBLE);
                }
            } else {
                performanceStatus.setText(error != null ? error : "Failed to load");
                performanceStatus.setVisibility(View.VISIBLE);
            }
        });
    }

    private void addStatRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, UIHelper.dp(this, 3), 0, UIHelper.dp(this, 3));

        TextView labelText = new TextView(this);
        labelText.setText(label);
        labelText.setTextSize(12);
        labelText.setTextColor(UIHelper.TEXT_HINT);
        labelText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(labelText);

        TextView valueText = new TextView(this);
        valueText.setText(value);
        valueText.setTextSize(13);
        valueText.setTextColor(UIHelper.TEXT_PRIMARY);
        row.addView(valueText);

        parent.addView(row);
    }

    // ==================== Actions ====================

    private void showToggleDialog() {
        BridgeClient.getPaperTradeOverview((success, data, error) -> {
            if (!success || data == null) {
                android.widget.Toast.makeText(this, "Unable to check status", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            String status = data.optString("status", "active");
            boolean isPaused = status.equals("paused");

            String[] options = isPaused ?
                    new String[]{"Resume Trading", "Reset Simulation"} :
                    new String[]{"Pause Trading", "Reset Simulation"};

            new AlertDialog.Builder(this, R.style.DarkDialogTheme)
                    .setTitle("AI Trader Control")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            toggleTrading(!isPaused);
                        } else {
                            confirmReset();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void toggleTrading(boolean pause) {
        BridgeClient.togglePaperTrade(pause, (success, error) -> {
            if (success) {
                String msg = pause ? "Trading paused" : "Trading resumed";
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
                AppLog.i("PaperTrade", msg);
                loadOverview();
            } else {
                android.widget.Toast.makeText(this, "Failed: " + error, android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmReset() {
        new AlertDialog.Builder(this, R.style.DarkDialogTheme)
                .setTitle("Reset Simulation")
                .setMessage("This will clear all holdings, trades, and reset to initial capital. Continue?")
                .setPositiveButton("Reset", (dialog, which) -> resetSimulation())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void resetSimulation() {
        BridgeClient.resetPaperTrade((success, error) -> {
            if (success) {
                android.widget.Toast.makeText(this, "Simulation reset", android.widget.Toast.LENGTH_SHORT).show();
                AppLog.i("PaperTrade", "Simulation reset");
                loadOverview();
                loadHoldings();
                loadTrades();
                loadPerformance();
            } else {
                android.widget.Toast.makeText(this, "Failed: " + error, android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==================== Helpers ====================

    private String formatMoney(double amount) {
        if (Math.abs(amount) >= 1000000) {
            return String.format("%.1fM", amount / 1000000);
        } else if (Math.abs(amount) >= 10000) {
            return String.format("%.0fK", amount / 1000);
        } else {
            return new DecimalFormat("#,##0").format(amount);
        }
    }
}
