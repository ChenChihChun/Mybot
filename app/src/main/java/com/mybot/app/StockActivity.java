package com.mybot.app;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class StockActivity extends AppCompatActivity {

    private static final long UPDATE_INTERVAL = 10_000;
    private static final String PREFS_NAME = "stock_prefs";
    private static final String KEY_WATCHLIST = "watchlist";
    private static final String KEY_AI_PREFIX = "ai_analysis_";
    private static final String KEY_COST_PREFIX = "cost_";
    private static final String KEY_SHARES_PREFIX = "shares_";

    private Handler updateHandler;
    private boolean isRunning = false;

    private List<String> watchlist = new ArrayList<>();
    private String selectedCode = null;
    private Map<String, StockData.StockQuote> quoteMap = new LinkedHashMap<>();
    private Map<String, List<StockData.TickRecord>> tickMap = new HashMap<>();

    // UI references
    private LinearLayout chipContainer;
    private LinearLayout infoCard;
    private StockChartView chartView;
    private LinearLayout periodBar;
    private LinearLayout indicatorBar;
    private LinearLayout aiCard;
    private TextView aiContent;
    private TextView aiBtn;
    private TextView aiDeleteBtn;
    private TextView statusText;
    private TextView tvName, tvPrice, tvChange, tvOpen, tvHigh, tvLow, tvPrevClose, tvVolume;
    private LinearLayout costRow;
    private TextView tvCost;

    private String currentPeriod = "day"; // 1m, 5m, 15m, day, week, month
    private List<StockData.CandleBar> historicalCandles = null;
    private boolean loadingHistory = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        updateHandler = new Handler(Looper.getMainLooper());
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        loadWatchlist();
        buildUI();
        if (!watchlist.isEmpty()) {
            selectedCode = watchlist.get(0);
            refreshChips();
            loadAiAnalysis(selectedCode);
            loadHistoricalData();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isRunning = true;
        scheduleUpdate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isRunning = false;
        updateHandler.removeCallbacksAndMessages(null);
    }

    private void buildUI() {
        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "台股追蹤");
        TextView addBtn = new TextView(this);
        addBtn.setText("+");
        addBtn.setTextSize(24);
        addBtn.setTextColor(UIHelper.ACCENT_ORANGE);
        addBtn.setTypeface(Typeface.DEFAULT_BOLD);
        addBtn.setPadding(UIHelper.dp(this, 12), 0, UIHelper.dp(this, 4), 0);
        addBtn.setOnClickListener(v -> showAddDialog());
        topBar.addView(addBtn);
        root.addView(topBar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int cp = UIHelper.dp(this, 12);
        content.setPadding(cp, UIHelper.dp(this, 8), cp, cp);

        // Chip scroll
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipContainer = new LinearLayout(this);
        chipContainer.setOrientation(LinearLayout.HORIZONTAL);
        int chipPad = UIHelper.dp(this, 4);
        chipContainer.setPadding(0, chipPad, 0, chipPad);
        chipScroll.addView(chipContainer);
        content.addView(chipScroll);

        // Info card
        infoCard = new LinearLayout(this);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 16, this));
        int infoPad = UIHelper.dp(this, 14);
        infoCard.setPadding(infoPad, infoPad, infoPad, infoPad);
        infoCard.setElevation(UIHelper.dp(this, 3));
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.setMargins(0, UIHelper.dp(this, 6), 0, UIHelper.dp(this, 6));
        infoCard.setLayoutParams(infoLp);
        infoCard.setVisibility(View.GONE);

        // Name row
        tvName = new TextView(this);
        tvName.setTextSize(18);
        tvName.setTextColor(UIHelper.TEXT_PRIMARY);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        infoCard.addView(tvName);

        // Price row
        LinearLayout priceRow = new LinearLayout(this);
        priceRow.setOrientation(LinearLayout.HORIZONTAL);
        priceRow.setGravity(Gravity.CENTER_VERTICAL);
        priceRow.setPadding(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 8));

        tvPrice = new TextView(this);
        tvPrice.setTextSize(32);
        tvPrice.setTextColor(UIHelper.TEXT_PRIMARY);
        tvPrice.setTypeface(Typeface.DEFAULT_BOLD);

        tvChange = new TextView(this);
        tvChange.setTextSize(16);
        tvChange.setPadding(UIHelper.dp(this, 12), 0, 0, 0);

        priceRow.addView(tvPrice);
        priceRow.addView(tvChange);
        infoCard.addView(priceRow);

        // Detail grid
        LinearLayout detailGrid = new LinearLayout(this);
        detailGrid.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout col1 = detailColumn();
        tvOpen = detailItem(col1, "開盤");
        tvHigh = detailItem(col1, "最高");
        tvLow = detailItem(col1, "最低");

        LinearLayout col2 = detailColumn();
        tvPrevClose = detailItem(col2, "昨收");
        tvVolume = detailItem(col2, "成交量");

        detailGrid.addView(col1, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        detailGrid.addView(col2, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        infoCard.addView(detailGrid);

        // Cost / P&L row
        costRow = new LinearLayout(this);
        costRow.setOrientation(LinearLayout.HORIZONTAL);
        costRow.setGravity(Gravity.CENTER_VERTICAL);
        costRow.setPadding(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 4));
        costRow.setVisibility(View.GONE);

        tvCost = new TextView(this);
        tvCost.setTextSize(13);
        tvCost.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        costRow.addView(tvCost);

        TextView costEditBtn = new TextView(this);
        costEditBtn.setText("✏ 成本");
        costEditBtn.setTextSize(12);
        costEditBtn.setTextColor(UIHelper.ACCENT_BLUE);
        costEditBtn.setPadding(UIHelper.dp(this, 8), UIHelper.dp(this, 4),
                UIHelper.dp(this, 8), UIHelper.dp(this, 4));
        costEditBtn.setOnClickListener(v -> showCostDialog());
        costRow.addView(costEditBtn);

        infoCard.addView(costRow);

        content.addView(infoCard);

        // Chart
        chartView = new StockChartView(this);
        LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 350));
        chartLp.setMargins(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4));
        chartView.setLayoutParams(chartLp);
        chartView.setVisibility(View.GONE);
        content.addView(chartView);

        // Indicator toggle buttons
        indicatorBar = new LinearLayout(this);
        indicatorBar.setOrientation(LinearLayout.HORIZONTAL);
        indicatorBar.setGravity(Gravity.CENTER);
        indicatorBar.setPadding(0, UIHelper.dp(this, 2), 0, UIHelper.dp(this, 2));
        indicatorBar.setVisibility(View.GONE);

        String[] indLabels = {"MA", "BBand", "RSI", "Vol"};
        for (String label : indLabels) {
            TextView btn = new TextView(this);
            btn.setText(label);
            btn.setTextSize(12);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(UIHelper.dp(this, 10), UIHelper.dp(this, 5),
                    UIHelper.dp(this, 10), UIHelper.dp(this, 5));
            btn.setTag(label);
            btn.setOnClickListener(v -> toggleIndicator(label));
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnLp.setMargins(UIHelper.dp(this, 3), 0, UIHelper.dp(this, 3), 0);
            btn.setLayoutParams(btnLp);
            indicatorBar.addView(btn);
        }
        updateIndicatorButtons();
        content.addView(indicatorBar);

        // Period buttons
        periodBar = new LinearLayout(this);
        periodBar.setOrientation(LinearLayout.HORIZONTAL);
        periodBar.setGravity(Gravity.CENTER);
        periodBar.setPadding(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 8));
        periodBar.setVisibility(View.GONE);

        String[] periods = {"1分", "5分", "15分", "日", "週", "月"};
        String[] periodKeys = {"1m", "5m", "15m", "day", "week", "month"};
        for (int i = 0; i < periods.length; i++) {
            final String key = periodKeys[i];
            TextView btn = new TextView(this);
            btn.setText(periods[i]);
            btn.setTextSize(13);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(UIHelper.dp(this, 12), UIHelper.dp(this, 6),
                    UIHelper.dp(this, 12), UIHelper.dp(this, 6));
            btn.setTag(key);
            btn.setOnClickListener(v -> switchPeriod(key));
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            btnLp.setMargins(UIHelper.dp(this, 2), 0, UIHelper.dp(this, 2), 0);
            btn.setLayoutParams(btnLp);
            periodBar.addView(btn);
        }
        updatePeriodButtons();
        content.addView(periodBar);

        // AI Analysis card
        aiCard = new LinearLayout(this);
        aiCard.setOrientation(LinearLayout.VERTICAL);
        aiCard.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 16, this));
        int aiPad = UIHelper.dp(this, 14);
        aiCard.setPadding(aiPad, aiPad, aiPad, aiPad);
        aiCard.setElevation(UIHelper.dp(this, 3));
        LinearLayout.LayoutParams aiLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aiLp.setMargins(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 4));
        aiCard.setLayoutParams(aiLp);
        aiCard.setVisibility(View.GONE);

        // AI title row
        LinearLayout aiTitleRow = new LinearLayout(this);
        aiTitleRow.setOrientation(LinearLayout.HORIZONTAL);
        aiTitleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView aiTitle = new TextView(this);
        aiTitle.setText("AI 分析評語");
        aiTitle.setTextSize(15);
        aiTitle.setTextColor(UIHelper.TEXT_PRIMARY);
        aiTitle.setTypeface(Typeface.DEFAULT_BOLD);
        aiTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        aiBtn = new TextView(this);
        aiBtn.setText("取得分析");
        aiBtn.setTextSize(13);
        aiBtn.setTextColor(UIHelper.ACCENT_BLUE);
        aiBtn.setTypeface(Typeface.DEFAULT_BOLD);
        aiBtn.setPadding(UIHelper.dp(this, 12), UIHelper.dp(this, 6),
                UIHelper.dp(this, 12), UIHelper.dp(this, 6));
        aiBtn.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.ACCENT_BLUE, 10, 1, this));
        aiBtn.setOnClickListener(v -> requestAiAnalysis());

        aiDeleteBtn = new TextView(this);
        aiDeleteBtn.setText("刪除");
        aiDeleteBtn.setTextSize(13);
        aiDeleteBtn.setTextColor(UIHelper.ACCENT_RED);
        aiDeleteBtn.setTypeface(Typeface.DEFAULT_BOLD);
        aiDeleteBtn.setPadding(UIHelper.dp(this, 12), UIHelper.dp(this, 6),
                UIHelper.dp(this, 12), UIHelper.dp(this, 6));
        aiDeleteBtn.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.ACCENT_RED, 10, 1, this));
        aiDeleteBtn.setVisibility(View.GONE);
        aiDeleteBtn.setOnClickListener(v -> deleteAiAnalysis());

        aiTitleRow.addView(aiTitle);
        aiTitleRow.addView(aiBtn);
        aiTitleRow.addView(aiDeleteBtn);
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        delLp.setMargins(UIHelper.dp(this, 6), 0, 0, 0);
        aiDeleteBtn.setLayoutParams(delLp);
        aiCard.addView(aiTitleRow);

        aiContent = new TextView(this);
        aiContent.setTextSize(13);
        aiContent.setTextColor(UIHelper.TEXT_SECONDARY);
        aiContent.setLineSpacing(UIHelper.dp(this, 3), 1f);
        aiContent.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        aiCard.addView(aiContent);

        content.addView(aiCard);

        // Status
        statusText = new TextView(this);
        statusText.setTextSize(12);
        statusText.setTextColor(UIHelper.TEXT_HINT);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 16));
        content.addView(statusText);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private LinearLayout detailColumn() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        return col;
    }

    private TextView detailItem(LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, UIHelper.dp(this, 2), 0, UIHelper.dp(this, 2));

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextSize(12);
        lbl.setTextColor(UIHelper.TEXT_HINT);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(UIHelper.dp(this, 48), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView val = new TextView(this);
        val.setTextSize(13);
        val.setTextColor(UIHelper.TEXT_SECONDARY);

        row.addView(lbl);
        row.addView(val);
        parent.addView(row);
        return val;
    }

    private void showAddDialog() {
        EditText input = new EditText(this);
        input.setHint("輸入股票代碼 (如 2330)");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(Color.BLACK);
        input.setHintTextColor(Color.GRAY);
        int pad = UIHelper.dp(this, 16);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("新增自選股")
                .setView(input)
                .setPositiveButton("新增", (d, w) -> {
                    String code = input.getText().toString().trim();
                    if (!code.isEmpty() && !watchlist.contains(code)) {
                        watchlist.add(code);
                        saveWatchlist();
                        AppLog.i("Stock", "新增自選股: " + code);
                        if (selectedCode == null) selectedCode = code;
                        refreshChips();
                        fetchQuotes();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void refreshChips() {
        chipContainer.removeAllViews();
        for (String code : watchlist) {
            TextView chip = new TextView(this);
            StockData.StockQuote q = quoteMap.get(code);

            String text;
            if (q != null && q.currentPrice > 0) {
                double pct = q.getChangePercent();
                text = code + " " + q.name + "\n$" + formatPrice(q.currentPrice)
                        + " " + (pct >= 0 ? "+" : "") + String.format("%.1f%%", pct);
            } else {
                text = code + "\n載入中...";
            }
            chip.setText(text);
            chip.setTextSize(12);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(UIHelper.dp(this, 12), UIHelper.dp(this, 8),
                    UIHelper.dp(this, 12), UIHelper.dp(this, 8));

            boolean selected = code.equals(selectedCode);
            int accentColor;
            if (q != null && q.currentPrice > 0) {
                accentColor = q.getChange() >= 0 ? UIHelper.ACCENT_RED : UIHelper.ACCENT_GREEN;
            } else {
                accentColor = UIHelper.TEXT_HINT;
            }

            if (selected) {
                chip.setBackground(UIHelper.roundRect(Color.argb(60,
                        Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)), 12, this));
                chip.setTextColor(UIHelper.TEXT_PRIMARY);
            } else {
                chip.setBackground(UIHelper.roundRectStroke(UIHelper.BG_CARD, accentColor, 12, 1, this));
                chip.setTextColor(UIHelper.TEXT_SECONDARY);
            }

            chip.setOnClickListener(v -> {
                selectedCode = code;
                historicalCandles = null;
                loadAiAnalysis(code);
                refreshChips();
                updateInfoCard();
                updateChart();
                if (isHistoricalPeriod()) loadHistoricalData();
            });

            chip.setOnLongClickListener(v -> {
                StockData.StockQuote sq = quoteMap.get(code);
                String label = code + (sq != null ? " " + sq.name : "");
                String[] items = {"設定成本", "刪除"};
                new AlertDialog.Builder(this)
                        .setTitle(label)
                        .setItems(items, (d, which) -> {
                            if (which == 0) {
                                selectedCode = code;
                                refreshChips();
                                updateInfoCard();
                                showCostDialog();
                            } else {
                                new AlertDialog.Builder(this)
                                        .setTitle("確定刪除 " + code + "?")
                                        .setPositiveButton("刪除", (dd, ww) -> {
                                            watchlist.remove(code);
                                            saveWatchlist();
                                            AppLog.i("Stock", "移除自選股: " + code);
                                            tickMap.remove(code);
                                            quoteMap.remove(code);
                                            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                                            editor.remove(KEY_AI_PREFIX + code);
                                            editor.remove(KEY_COST_PREFIX + code);
                                            editor.remove(KEY_SHARES_PREFIX + code);
                                            editor.apply();
                                            StockCache.getInstance(StockActivity.this).remove(code);
                                            if (code.equals(selectedCode)) {
                                                selectedCode = watchlist.isEmpty() ? null : watchlist.get(0);
                                                historicalCandles = null;
                                                if (selectedCode != null) loadAiAnalysis(selectedCode);
                                            }
                                            refreshChips();
                                            updateInfoCard();
                                            updateChart();
                                        })
                                        .setNegativeButton("取消", null)
                                        .show();
                            }
                        })
                        .show();
                return true;
            });

            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipLp.setMargins(0, 0, UIHelper.dp(this, 8), 0);
            chip.setLayoutParams(chipLp);
            chipContainer.addView(chip);
        }
    }

    private void updateInfoCard() {
        if (selectedCode == null) {
            infoCard.setVisibility(View.GONE);
            chartView.setVisibility(View.GONE);
            periodBar.setVisibility(View.GONE);
            indicatorBar.setVisibility(View.GONE);
            aiCard.setVisibility(View.GONE);
            return;
        }
        infoCard.setVisibility(View.VISIBLE);
        chartView.setVisibility(View.VISIBLE);
        periodBar.setVisibility(View.VISIBLE);
        indicatorBar.setVisibility(View.VISIBLE);
        aiCard.setVisibility(View.VISIBLE);

        StockData.StockQuote q = quoteMap.get(selectedCode);
        if (q == null) {
            tvName.setText(selectedCode);
            tvPrice.setText("--");
            tvChange.setText("");
            tvOpen.setText("--");
            tvHigh.setText("--");
            tvLow.setText("--");
            tvPrevClose.setText("--");
            tvVolume.setText("--");
            return;
        }

        tvName.setText(q.code + " " + q.name);

        if (q.currentPrice > 0) {
            tvPrice.setText(formatPrice(q.currentPrice));
            double change = q.getChange();
            double pct = q.getChangePercent();
            String sign = change >= 0 ? "+" : "";
            tvChange.setText(sign + formatPrice(change) + " (" + sign + String.format("%.2f%%", pct) + ")");
            tvChange.setTextColor(change >= 0 ? UIHelper.ACCENT_RED : UIHelper.ACCENT_GREEN);
            tvPrice.setTextColor(change >= 0 ? UIHelper.ACCENT_RED : UIHelper.ACCENT_GREEN);
        } else {
            tvPrice.setText("--");
            tvChange.setText("");
        }

        tvOpen.setText(q.open > 0 ? formatPrice(q.open) : "--");
        tvHigh.setText(q.high > 0 ? formatPrice(q.high) : "--");
        tvLow.setText(q.low > 0 ? formatPrice(q.low) : "--");
        tvPrevClose.setText(q.prevClose > 0 ? formatPrice(q.prevClose) : "--");
        tvVolume.setText(q.volume > 0 ? formatVolume(q.volume) : "--");

        // Cost / P&L
        updateCostDisplay(q);
    }

    private void updateChart() {
        if (selectedCode == null) return;

        List<StockData.CandleBar> candles;
        double price = 0;
        StockData.StockQuote q = quoteMap.get(selectedCode);
        if (q != null) price = q.currentPrice;

        if (isHistoricalPeriod()) {
            if (historicalCandles == null || historicalCandles.isEmpty()) {
                chartView.setData(null, null, null, null, null, null, null, 0);
                return;
            }
            if ("week".equals(currentPeriod)) {
                candles = StockData.aggregateWeekly(historicalCandles);
            } else if ("month".equals(currentPeriod)) {
                candles = StockData.aggregateMonthly(historicalCandles);
            } else {
                candles = historicalCandles;
            }
        } else {
            List<StockData.TickRecord> ticks = tickMap.get(selectedCode);
            if (ticks == null || ticks.isEmpty()) {
                chartView.setData(null, null, null, null, null, null, null, 0);
                return;
            }
            int interval;
            switch (currentPeriod) {
                case "5m": interval = 5; break;
                case "15m": interval = 15; break;
                default: interval = 1; break;
            }
            candles = StockData.aggregateCandles(ticks, interval);
        }

        if (candles == null || candles.isEmpty()) {
            chartView.setData(null, null, null, null, null, null, null, 0);
            return;
        }

        // Convert to Heikin-Ashi
        List<StockData.CandleBar> haCandles = StockData.toHeikinAshi(candles);

        double[] ma5 = StockData.calcMA(haCandles, 5);
        double[] ma10 = StockData.calcMA(haCandles, 10);
        double[] ma20 = StockData.calcMA(haCandles, 20);
        double[][] bband = StockData.calcBollinger(haCandles, 20, 2.0);
        double[] rsi5 = StockData.calcRSI(haCandles, 5);
        double[] rsi10 = StockData.calcRSI(haCandles, 10);

        chartView.setData(haCandles, ma5, ma10, ma20, bband, rsi5, rsi10, price);
    }

    private boolean isHistoricalPeriod() {
        return "day".equals(currentPeriod) || "week".equals(currentPeriod) || "month".equals(currentPeriod);
    }

    private void switchPeriod(String period) {
        currentPeriod = period;
        updatePeriodButtons();
        if (isHistoricalPeriod()) {
            if (historicalCandles == null && !loadingHistory) {
                loadHistoricalData();
            } else {
                updateChart();
            }
        } else {
            updateChart();
        }
    }

    private void toggleIndicator(String label) {
        switch (label) {
            case "MA": chartView.showMA = !chartView.showMA; break;
            case "BBand": chartView.showBBand = !chartView.showBBand; break;
            case "RSI": chartView.showRSI = !chartView.showRSI; break;
            case "Vol": chartView.showVol = !chartView.showVol; break;
        }
        updateIndicatorButtons();
        chartView.invalidate();
    }

    private void updateIndicatorButtons() {
        for (int i = 0; i < indicatorBar.getChildCount(); i++) {
            TextView btn = (TextView) indicatorBar.getChildAt(i);
            String label = (String) btn.getTag();
            boolean on;
            int color;
            switch (label) {
                case "MA": on = chartView.showMA; color = Color.parseColor("#4FC3F7"); break;
                case "BBand": on = chartView.showBBand; color = Color.parseColor("#80DEEA"); break;
                case "RSI": on = chartView.showRSI; color = Color.parseColor("#FFD54F"); break;
                case "Vol": on = chartView.showVol; color = Color.parseColor("#66BB6A"); break;
                default: on = true; color = UIHelper.TEXT_SECONDARY; break;
            }
            if (on) {
                btn.setBackground(UIHelper.roundRect(Color.argb(50,
                        Color.red(color), Color.green(color), Color.blue(color)), 8, this));
                btn.setTextColor(color);
            } else {
                btn.setBackground(UIHelper.roundRectStroke(UIHelper.BG_CARD, UIHelper.TEXT_HINT, 8, 1, this));
                btn.setTextColor(UIHelper.TEXT_HINT);
            }
        }
    }

    private void updatePeriodButtons() {
        for (int i = 0; i < periodBar.getChildCount(); i++) {
            TextView btn = (TextView) periodBar.getChildAt(i);
            String key = (String) btn.getTag();
            if (key.equals(currentPeriod)) {
                btn.setBackground(UIHelper.roundRect(UIHelper.ACCENT_ORANGE, 8, this));
                btn.setTextColor(Color.WHITE);
            } else {
                btn.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 8, this));
                btn.setTextColor(UIHelper.TEXT_SECONDARY);
            }
        }
    }

    private void loadHistoricalData() {
        if (selectedCode == null || loadingHistory) return;

        int months = "month".equals(currentPeriod) ? 12 : 6;

        // Try cache first
        StockCache cache = StockCache.getInstance(this);
        if (cache.isFresh(selectedCode, months)) {
            List<StockData.CandleBar> cached = cache.load(selectedCode);
            if (cached != null && !cached.isEmpty()) {
                historicalCandles = cached;
                updateChart();
                updateStatus();
                return;
            }
        }

        loadingHistory = true;
        statusText.setText("載入歷史資料...");

        final String code = selectedCode;
        final int m = months;
        StockClient.fetchMultiMonthHistory(code, m, (candles, error) -> {
            loadingHistory = false;
            if (candles != null && !candles.isEmpty()) {
                historicalCandles = candles;
                StockCache.getInstance(this).save(code, candles, m);
                updateChart();
                updateStatus();
            } else {
                // Fallback: try stale cache
                List<StockData.CandleBar> stale = StockCache.getInstance(this).load(code);
                if (stale != null && !stale.isEmpty()) {
                    historicalCandles = stale;
                    updateChart();
                    statusText.setText("使用快取資料（更新失敗）");
                } else {
                    statusText.setText("歷史資料載入失敗" + (error != null ? ": " + error : ""));
                }
            }
        });
    }

    private void scheduleUpdate() {
        if (!isRunning) return;
        fetchQuotes();
        updateHandler.postDelayed(() -> scheduleUpdate(), getUpdateInterval());
    }

    private long getUpdateInterval() {
        int bl = StockClient.getBackoffLevel();
        if (bl >= 3) return 60_000;
        if (bl >= 2) return 30_000;
        if (bl >= 1) return 10_000;
        return UPDATE_INTERVAL;
    }

    private void fetchQuotes() {
        if (watchlist.isEmpty()) {
            statusText.setText("點擊右上角 + 新增自選股");
            return;
        }

        if (!isMarketOpen()) {
            // Still fetch once to get latest data, but don't auto-update
            updateStatus();
        }

        StockClient.fetchStocks(watchlist, (quotes, error) -> {
            if (quotes != null) {
                for (StockData.StockQuote q : quotes) {
                    quoteMap.put(q.code, q);
                    // Record tick
                    if (q.currentPrice > 0) {
                        List<StockData.TickRecord> ticks = tickMap.get(q.code);
                        if (ticks == null) {
                            ticks = new ArrayList<>();
                            tickMap.put(q.code, ticks);
                        }
                        ticks.add(new StockData.TickRecord(
                                System.currentTimeMillis(), q.currentPrice, q.volume));
                    }
                }
                refreshChips();
                updateInfoCard();
                if (!isHistoricalPeriod()) updateChart();
            }
            updateStatus();
        });
    }

    private void updateStatus() {
        int bl = StockClient.getBackoffLevel();
        if (bl > 0) {
            statusText.setText("API 限速中，降低更新頻率 (" + getUpdateInterval() / 1000 + "秒)");
            statusText.setTextColor(UIHelper.ACCENT_ORANGE);
            return;
        }
        statusText.setTextColor(UIHelper.TEXT_HINT);
        if (isMarketOpen()) {
            statusText.setText("開盤中 (每10秒更新)");
        } else {
            statusText.setText("休市");
        }
    }

    private boolean isMarketOpen() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Taipei"));
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return false;
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int min = cal.get(Calendar.MINUTE);
        int time = hour * 60 + min;
        return time >= 9 * 60 && time <= 13 * 60 + 30;
    }

    private void updateCostDisplay(StockData.StockQuote q) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        float cost = prefs.getFloat(KEY_COST_PREFIX + selectedCode, 0);
        int shares = prefs.getInt(KEY_SHARES_PREFIX + selectedCode, 0);

        if (cost > 0 && shares > 0 && q.currentPrice > 0) {
            costRow.setVisibility(View.VISIBLE);
            double totalCost = cost * shares;
            double marketValue = q.currentPrice * shares;
            double pnl = marketValue - totalCost;
            double pnlPct = (pnl / totalCost) * 100;
            String sign = pnl >= 0 ? "+" : "";

            tvCost.setText(String.format("成本 %s × %d股 | 損益: %s%s (%s%.1f%%)",
                    formatPrice(cost), shares,
                    sign, formatPrice(Math.abs(pnl) >= 1000 ? Math.round(pnl) : pnl),
                    sign, pnlPct));
            tvCost.setTextColor(pnl >= 0 ? UIHelper.ACCENT_RED : UIHelper.ACCENT_GREEN);
        } else if (cost > 0 || shares > 0) {
            costRow.setVisibility(View.VISIBLE);
            tvCost.setText(String.format("成本 %s × %d股", cost > 0 ? formatPrice(cost) : "--", shares));
            tvCost.setTextColor(UIHelper.TEXT_SECONDARY);
        } else {
            costRow.setVisibility(View.GONE);
        }
    }

    private void showCostDialog() {
        if (selectedCode == null) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        float savedCost = prefs.getFloat(KEY_COST_PREFIX + selectedCode, 0);
        int savedShares = prefs.getInt(KEY_SHARES_PREFIX + selectedCode, 0);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(UIHelper.BG_CARD);
        int pad = UIHelper.dp(this, 16);
        layout.setPadding(pad, pad, pad, pad);

        TextView costLabel = new TextView(this);
        costLabel.setText("每股成本");
        costLabel.setTextColor(UIHelper.TEXT_SECONDARY);
        costLabel.setTextSize(13);
        layout.addView(costLabel);

        EditText costInput = UIHelper.styledInput(this, "例: 580.5");
        costInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (savedCost > 0) costInput.setText(String.valueOf(savedCost));
        layout.addView(costInput);

        TextView sharesLabel = new TextView(this);
        sharesLabel.setText("持有股數");
        sharesLabel.setTextColor(UIHelper.TEXT_SECONDARY);
        sharesLabel.setTextSize(13);
        sharesLabel.setPadding(0, UIHelper.dp(this, 12), 0, 0);
        layout.addView(sharesLabel);

        EditText sharesInput = UIHelper.styledInput(this, "例: 1000");
        sharesInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (savedShares > 0) sharesInput.setText(String.valueOf(savedShares));
        layout.addView(sharesInput);

        StockData.StockQuote q = quoteMap.get(selectedCode);
        String title = "設定成本 — " + selectedCode + (q != null ? " " + q.name : "");

        new androidx.appcompat.app.AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle(title)
                .setView(layout)
                .setPositiveButton("儲存", (d, w) -> {
                    String costStr = costInput.getText().toString().trim();
                    String sharesStr = sharesInput.getText().toString().trim();
                    SharedPreferences.Editor editor = prefs.edit();
                    if (!costStr.isEmpty()) {
                        try {
                            editor.putFloat(KEY_COST_PREFIX + selectedCode, Float.parseFloat(costStr));
                        } catch (NumberFormatException ignored) {}
                    } else {
                        editor.remove(KEY_COST_PREFIX + selectedCode);
                    }
                    if (!sharesStr.isEmpty()) {
                        try {
                            editor.putInt(KEY_SHARES_PREFIX + selectedCode, Integer.parseInt(sharesStr));
                        } catch (NumberFormatException ignored) {}
                    } else {
                        editor.remove(KEY_SHARES_PREFIX + selectedCode);
                    }
                    editor.apply();
                    AppLog.i("Stock", "設定成本: " + selectedCode + " cost=" + costStr + " shares=" + sharesStr);
                    if (q != null) updateCostDisplay(q);
                })
                .setNeutralButton("清除", (d, w) -> {
                    prefs.edit()
                            .remove(KEY_COST_PREFIX + selectedCode)
                            .remove(KEY_SHARES_PREFIX + selectedCode)
                            .apply();
                    AppLog.i("Stock", "清除成本: " + selectedCode);
                    costRow.setVisibility(View.GONE);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void requestAiAnalysis() {
        if (selectedCode == null) return;
        StockData.StockQuote q = quoteMap.get(selectedCode);
        if (q == null || q.currentPrice <= 0) {
            aiContent.setText("尚無報價資料，請稍後再試");
            return;
        }

        AppLog.i("Stock", "AI分析開始: " + selectedCode);
        aiBtn.setText("分析中...");
        aiBtn.setTextColor(UIHelper.TEXT_HINT);
        aiBtn.setEnabled(false);
        aiContent.setText("AI 正在分析中，請稍候...");

        // Build context info
        StringBuilder info = new StringBuilder();
        info.append("股票代碼: ").append(q.code).append("\n");
        info.append("股票名稱: ").append(q.name).append("\n");
        info.append("現價: ").append(formatPrice(q.currentPrice)).append("\n");
        info.append("昨收: ").append(formatPrice(q.prevClose)).append("\n");
        info.append("漲跌幅: ").append(String.format("%.2f%%", q.getChangePercent())).append("\n");
        info.append("開盤: ").append(formatPrice(q.open)).append("\n");
        info.append("最高: ").append(formatPrice(q.high)).append("\n");
        info.append("最低: ").append(formatPrice(q.low)).append("\n");
        info.append("成交量: ").append(formatVolume(q.volume)).append("\n");

        // Add technical indicators if available
        if (historicalCandles != null && !historicalCandles.isEmpty()) {
            List<StockData.CandleBar> dc = historicalCandles;
            int size = dc.size();
            double[] ma5v = StockData.calcMA(dc, 5);
            double[] ma20v = StockData.calcMA(dc, 20);
            double[] rsiv = StockData.calcRSI(dc, 14);
            if (size > 0 && !Double.isNaN(ma5v[size - 1]))
                info.append("MA5: ").append(formatPrice(ma5v[size - 1])).append("\n");
            if (size > 0 && !Double.isNaN(ma20v[size - 1]))
                info.append("MA20: ").append(formatPrice(ma20v[size - 1])).append("\n");
            if (size > 0 && !Double.isNaN(rsiv[size - 1]))
                info.append("RSI(14): ").append(String.format("%.1f", rsiv[size - 1])).append("\n");

            // Recent 5 days trend
            info.append("近5日收盤: ");
            int start = Math.max(0, size - 5);
            for (int i = start; i < size; i++) {
                if (i > start) info.append(" → ");
                info.append(formatPrice(dc.get(i).close));
            }
            info.append("\n");
        }

        // Add cost position info if available
        SharedPreferences costPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        float costVal = costPrefs.getFloat(KEY_COST_PREFIX + selectedCode, 0);
        int sharesVal = costPrefs.getInt(KEY_SHARES_PREFIX + selectedCode, 0);
        if (costVal > 0 && sharesVal > 0) {
            double totalCost = costVal * sharesVal;
            double marketVal = q.currentPrice * sharesVal;
            double pnl = marketVal - totalCost;
            double pnlPct = (pnl / totalCost) * 100;
            info.append("\n【持倉資訊】\n");
            info.append("持有股數: ").append(sharesVal).append("股\n");
            info.append("每股成本: ").append(formatPrice(costVal)).append("\n");
            info.append("總成本: ").append(String.format("%.0f", totalCost)).append("\n");
            info.append("市值: ").append(String.format("%.0f", marketVal)).append("\n");
            info.append("未實現損益: ").append(String.format("%+.0f (%.1f%%)", pnl, pnlPct)).append("\n");
        }

        BridgeClient.analyzeStock(info.toString(), (result, offline, error) -> {
            aiBtn.setText("取得分析");
            aiBtn.setTextColor(UIHelper.ACCENT_BLUE);
            aiBtn.setEnabled(true);

            if (result != null && !result.isEmpty()) {
                AppLog.i("Stock", "AI分析完成: " + selectedCode);
                aiContent.setText(result);
                saveAiAnalysis(selectedCode, result);
                aiDeleteBtn.setVisibility(View.VISIBLE);
            } else if (offline) {
                AppLog.w("Stock", "AI分析失敗(離線): " + selectedCode);
                aiContent.setText("Bridge 離線，無法取得 AI 分析\n" + (error != null ? error : ""));
            } else {
                AppLog.e("Stock", "AI分析失敗: " + selectedCode + " " + (error != null ? error : ""));
                aiContent.setText("分析失敗" + (error != null ? ": " + error : ""));
            }
        });
    }

    private void saveAiAnalysis(String code, String analysis) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putString(KEY_AI_PREFIX + code, analysis).apply();
    }

    private void loadAiAnalysis(String code) {
        String saved = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_AI_PREFIX + code, "");
        if (!saved.isEmpty()) {
            aiContent.setText(saved);
            aiDeleteBtn.setVisibility(View.VISIBLE);
        } else {
            aiContent.setText("點擊「取得分析」讓 AI 評估此股票");
            aiDeleteBtn.setVisibility(View.GONE);
        }
    }

    private void deleteAiAnalysis() {
        if (selectedCode == null) return;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().remove(KEY_AI_PREFIX + selectedCode).apply();
        aiContent.setText("點擊「取得分析」讓 AI 評估此股票");
        aiDeleteBtn.setVisibility(View.GONE);
    }

    private void loadWatchlist() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String saved = prefs.getString(KEY_WATCHLIST, "");
        watchlist.clear();
        if (!saved.isEmpty()) {
            for (String code : saved.split(",")) {
                String trimmed = code.trim();
                if (!trimmed.isEmpty()) watchlist.add(trimmed);
            }
        }
    }

    private void saveWatchlist() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < watchlist.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(watchlist.get(i));
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putString(KEY_WATCHLIST, sb.toString()).apply();
    }

    private String formatPrice(double price) {
        if (price >= 100) return String.format("%.0f", price);
        if (price >= 10) return String.format("%.1f", price);
        return String.format("%.2f", price);
    }

    private String formatVolume(long vol) {
        if (vol >= 100_000_000) return String.format("%.1f億", vol / 100_000_000.0);
        if (vol >= 10000) return String.format("%.0f萬", vol / 10000.0);
        return String.valueOf(vol);
    }
}
