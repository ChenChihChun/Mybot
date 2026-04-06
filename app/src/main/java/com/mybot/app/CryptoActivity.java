package com.mybot.app;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

public class CryptoActivity extends AppCompatActivity {

    private TextView priceText, changeText, highLowText, volumeText;
    private LinearLayout simContent, tradeContent, strategyContent, evolutionContent;
    private TextView simStatus, tradeStatus;
    private boolean hasSimulation = false;
    private boolean strategyEnabled = false;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = this::refreshPrice;
    private static final int REFRESH_INTERVAL = 5000; // 5 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        AppLog.i("Crypto", "CryptoActivity opened");
        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPrice();
        loadSimulation();
        loadTrades();
        loadStrategy();
        loadEvolution();
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void refreshPrice() {
        BridgeClient.getCryptoPrice((data, error) -> {
            if (data != null) {
                double price = data.optDouble("price", 0);
                double change = data.optDouble("change_24h", 0);
                double high = data.optDouble("high_24h", 0);
                double low = data.optDouble("low_24h", 0);
                double vol = data.optDouble("volume", 0);

                priceText.setText("$" + formatUsd(price));
                changeText.setText((change >= 0 ? "+" : "") + String.format("%.2f%%", change));
                changeText.setTextColor(change >= 0 ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_RED);
                highLowText.setText("H: $" + formatUsd(high) + "  L: $" + formatUsd(low));
                volumeText.setText("Vol: " + formatVol(vol) + " BTC");

                if (data.optBoolean("stale", false)) {
                    priceText.setAlpha(0.5f);
                } else {
                    priceText.setAlpha(1f);
                }
            }
            // Schedule next refresh
            refreshHandler.removeCallbacks(refreshRunnable);
            refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
        });
        // Also refresh simulation to update unrealized P&L with latest price
        if (hasSimulation) {
            BridgeClient.getCryptoSimulation((simData, simErr) -> {
                if (simData != null) {
                    simContent.removeAllViews();
                    displaySimulation(simData);
                }
            });
        }
        // Refresh strategy signal
        loadStrategy();
    }

    private void buildUI() {
        LinearLayout root = UIHelper.pageRoot(this);
        root.addView(UIHelper.topBar(this, "BTC 模擬交易"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int cp = UIHelper.dp(this, 12);
        content.setPadding(cp, UIHelper.dp(this, 8), cp, cp);

        content.addView(buildPriceCard());
        content.addView(buildStrategyCard());
        content.addView(buildEvolutionCard());
        content.addView(buildSimulationCard());
        content.addView(buildTradeCard());

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    // ==================== Price Card ====================

    private LinearLayout buildPriceCard() {
        LinearLayout card = makeCard();

        // Title
        TextView title = new TextView(this);
        title.setText("BTC / USDT");
        title.setTextSize(15);
        title.setTextColor(UIHelper.ACCENT_ORANGE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        // Price (big)
        priceText = new TextView(this);
        priceText.setText("--");
        priceText.setTextSize(32);
        priceText.setTextColor(UIHelper.TEXT_PRIMARY);
        priceText.setTypeface(Typeface.DEFAULT_BOLD);
        priceText.setPadding(0, UIHelper.dp(this, 4), 0, 0);
        card.addView(priceText);

        // Change %
        changeText = new TextView(this);
        changeText.setText("--");
        changeText.setTextSize(16);
        changeText.setTextColor(UIHelper.TEXT_HINT);
        changeText.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(changeText);

        // High/Low
        highLowText = new TextView(this);
        highLowText.setText("H: --  L: --");
        highLowText.setTextSize(12);
        highLowText.setTextColor(UIHelper.TEXT_SECONDARY);
        highLowText.setPadding(0, UIHelper.dp(this, 6), 0, 0);
        card.addView(highLowText);

        // Volume
        volumeText = new TextView(this);
        volumeText.setText("Vol: --");
        volumeText.setTextSize(12);
        volumeText.setTextColor(UIHelper.TEXT_SECONDARY);
        card.addView(volumeText);

        return card;
    }

    // ==================== Simulation Card ====================

    private LinearLayout buildSimulationCard() {
        LinearLayout card = makeCard();

        // Title row
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("模擬交易");
        title.setTextSize(15);
        title.setTextColor(UIHelper.ACCENT_ORANGE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView resetBtn = new TextView(this);
        resetBtn.setText("重置");
        resetBtn.setTextSize(12);
        resetBtn.setTextColor(UIHelper.TEXT_HINT);
        resetBtn.setPadding(UIHelper.dp(this, 8), UIHelper.dp(this, 4),
                UIHelper.dp(this, 8), UIHelper.dp(this, 4));
        resetBtn.setOnClickListener(v -> showResetDialog());

        titleRow.addView(title);
        titleRow.addView(resetBtn);
        card.addView(titleRow);

        // Content
        simContent = new LinearLayout(this);
        simContent.setOrientation(LinearLayout.VERTICAL);
        simContent.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        card.addView(simContent);

        simStatus = new TextView(this);
        simStatus.setTextSize(13);
        simStatus.setTextColor(UIHelper.TEXT_HINT);
        simStatus.setText("載入中...");
        simContent.addView(simStatus);

        return card;
    }

    private void loadSimulation() {
        BridgeClient.getCryptoSimulation((data, error) -> {
            simContent.removeAllViews();
            if (error != null) {
                showSetupUI();
                return;
            }
            if (data == null) {
                showSetupUI();
                return;
            }
            hasSimulation = true;
            displaySimulation(data);
        });
    }

    private void showSetupUI() {
        hasSimulation = false;
        simContent.removeAllViews();

        TextView label = new TextView(this);
        label.setText("設定初始金額 (USDT)");
        label.setTextSize(14);
        label.setTextColor(UIHelper.TEXT_PRIMARY);
        simContent.addView(label);

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("例如: 10000");
        input.setTextColor(UIHelper.TEXT_PRIMARY);
        input.setHintTextColor(UIHelper.TEXT_HINT);
        input.setTextSize(16);
        input.setBackground(UIHelper.roundRect(0xFF1E1E2E, 8, this));
        input.setPadding(UIHelper.dp(this, 12), UIHelper.dp(this, 10),
                UIHelper.dp(this, 12), UIHelper.dp(this, 10));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.setMargins(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8));
        input.setLayoutParams(inputLp);
        simContent.addView(input);

        TextView startBtn = new TextView(this);
        startBtn.setText("開始模擬");
        startBtn.setTextSize(15);
        startBtn.setTextColor(Color.WHITE);
        startBtn.setTypeface(Typeface.DEFAULT_BOLD);
        startBtn.setGravity(Gravity.CENTER);
        startBtn.setBackground(UIHelper.roundRect(UIHelper.ACCENT_GREEN, 10, this));
        startBtn.setPadding(0, UIHelper.dp(this, 12), 0, UIHelper.dp(this, 12));
        startBtn.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            double balance;
            try {
                balance = Double.parseDouble(text);
                if (balance <= 0) return;
            } catch (NumberFormatException e) {
                return;
            }
            AppLog.i("Crypto", "Creating simulation with balance: " + balance);
            BridgeClient.createCryptoSimulation(balance, (d, err) -> {
                if (err != null) {
                    AppLog.e("Crypto", "Create simulation failed: " + err);
                    return;
                }
                AppLog.i("Crypto", "Simulation created");
                loadSimulation();
            });
        });
        simContent.addView(startBtn);
    }

    private void displaySimulation(JSONObject data) {
        double balance = data.optDouble("current_balance", 0);
        double btcHeld = data.optDouble("btc_held", 0);
        double avgPrice = data.optDouble("avg_buy_price", 0);
        double unrealizedPnl = data.optDouble("unrealized_pnl", 0);
        double realizedPnl = data.optDouble("realized_pnl", 0);
        double totalPnl = data.optDouble("total_pnl", 0);
        double totalValue = data.optDouble("total_value", 0);
        double startBalance = data.optDouble("starting_balance", 0);
        double winRate = data.optDouble("win_rate", 0);
        int winCount = data.optInt("win_count", 0);
        int lossCount = data.optInt("loss_count", 0);

        // Total value & return
        double returnPct = startBalance > 0 ? (totalPnl / startBalance * 100) : 0;
        int returnColor = totalPnl >= 0 ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_RED;

        addStatRow("總資產", "$" + formatUsd(totalValue),
                (totalPnl >= 0 ? "+" : "") + formatUsd(totalPnl) + " (" + String.format("%.2f%%", returnPct) + ")",
                returnColor);
        addStatRow("可用 USDT", "$" + formatUsd(balance), null, 0);
        addStatRow("持有 BTC", formatBtc(btcHeld), btcHeld > 0 ? "均價 $" + formatUsd(avgPrice) : null, UIHelper.TEXT_HINT);
        addStatRow("未實現損益", (unrealizedPnl >= 0 ? "+" : "") + "$" + formatUsd(Math.abs(unrealizedPnl)),
                null, unrealizedPnl >= 0 ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_RED);
        addStatRow("已實現損益", (realizedPnl >= 0 ? "+" : "") + "$" + formatUsd(Math.abs(realizedPnl)),
                null, realizedPnl >= 0 ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_RED);
        addStatRow("勝率", String.format("%.1f%%", winRate),
                winCount + " 勝 / " + lossCount + " 負", UIHelper.TEXT_HINT);

        // Buy / Sell buttons
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnRowLp.setMargins(0, UIHelper.dp(this, 12), 0, 0);
        btnRow.setLayoutParams(btnRowLp);

        btnRow.addView(makeTradeButton("買入 BUY", UIHelper.ACCENT_GREEN, "buy"));
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(UIHelper.dp(this, 12), 1));
        btnRow.addView(spacer);
        btnRow.addView(makeTradeButton("賣出 SELL", UIHelper.ACCENT_RED, "sell"));

        simContent.addView(btnRow);
    }

    private void addStatRow(String label, String value, String sub, int subColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4));

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(13);
        labelTv.setTextColor(UIHelper.TEXT_SECONDARY);
        labelTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setGravity(Gravity.END);

        TextView valueTv = new TextView(this);
        valueTv.setText(value);
        valueTv.setTextSize(14);
        valueTv.setTextColor(UIHelper.TEXT_PRIMARY);
        valueTv.setTypeface(Typeface.DEFAULT_BOLD);
        valueTv.setGravity(Gravity.END);
        rightCol.addView(valueTv);

        if (sub != null) {
            TextView subTv = new TextView(this);
            subTv.setText(sub);
            subTv.setTextSize(11);
            subTv.setTextColor(subColor != 0 ? subColor : UIHelper.TEXT_HINT);
            subTv.setGravity(Gravity.END);
            rightCol.addView(subTv);
        }

        row.addView(labelTv);
        row.addView(rightCol);
        simContent.addView(row);
    }

    private TextView makeTradeButton(String text, int color, String side) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(14);
        btn.setTextColor(Color.WHITE);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(UIHelper.roundRect(color, 10, this));
        btn.setPadding(0, UIHelper.dp(this, 12), 0, UIHelper.dp(this, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> showTradeDialog(side));
        return btn;
    }

    private void showTradeDialog(String side) {
        boolean isBuy = "buy".equals(side);
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("USDT 金額");
        input.setTextColor(UIHelper.TEXT_PRIMARY);
        input.setHintTextColor(UIHelper.TEXT_HINT);
        input.setPadding(UIHelper.dp(this, 16), UIHelper.dp(this, 12),
                UIHelper.dp(this, 16), UIHelper.dp(this, 12));

        new AlertDialog.Builder(this)
                .setTitle(isBuy ? "買入 BTC" : "賣出 BTC")
                .setMessage("輸入 USDT 金額")
                .setView(input)
                .setPositiveButton("確認", (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    double amount;
                    try {
                        amount = Double.parseDouble(text);
                        if (amount <= 0) return;
                    } catch (NumberFormatException e) {
                        return;
                    }
                    AppLog.i("Crypto", side + " " + amount + " USDT");
                    BridgeClient.executeCryptoTrade(side, amount, (data, err) -> {
                        if (err != null) {
                            AppLog.w("Crypto", "Trade failed: " + err);
                            new AlertDialog.Builder(this)
                                    .setTitle("交易失敗")
                                    .setMessage(err)
                                    .setPositiveButton("OK", null)
                                    .show();
                            return;
                        }
                        AppLog.i("Crypto", "Trade success: " + side);
                        loadSimulation();
                        loadTrades();
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showResetDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("新的初始金額 (USDT)");
        input.setTextColor(UIHelper.TEXT_PRIMARY);
        input.setHintTextColor(UIHelper.TEXT_HINT);
        input.setPadding(UIHelper.dp(this, 16), UIHelper.dp(this, 12),
                UIHelper.dp(this, 16), UIHelper.dp(this, 12));

        new AlertDialog.Builder(this)
                .setTitle("重置模擬")
                .setMessage("這將清除所有交易紀錄")
                .setView(input)
                .setPositiveButton("確認重置", (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    double balance;
                    try {
                        balance = Double.parseDouble(text);
                        if (balance <= 0) return;
                    } catch (NumberFormatException e) {
                        return;
                    }
                    AppLog.i("Crypto", "Reset simulation with balance: " + balance);
                    BridgeClient.resetCryptoSimulation(balance, (data, err) -> {
                        if (err != null) {
                            AppLog.e("Crypto", "Reset failed: " + err);
                            return;
                        }
                        AppLog.i("Crypto", "Simulation reset");
                        loadSimulation();
                        loadTrades();
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== Strategy Card ====================

    private LinearLayout buildStrategyCard() {
        LinearLayout card = makeCard();

        // Title row
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("HA 策略");
        title.setTextSize(15);
        title.setTextColor(UIHelper.ACCENT_ORANGE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView toggleBtn = new TextView(this);
        toggleBtn.setTextSize(13);
        toggleBtn.setTypeface(Typeface.DEFAULT_BOLD);
        toggleBtn.setPadding(UIHelper.dp(this, 12), UIHelper.dp(this, 6),
                UIHelper.dp(this, 12), UIHelper.dp(this, 6));
        updateToggleBtn(toggleBtn, false);
        toggleBtn.setOnClickListener(v -> {
            boolean newState = !strategyEnabled;
            AppLog.i("Crypto", "Strategy toggle: " + newState);
            BridgeClient.toggleCryptoStrategy(newState, (data, err) -> {
                if (data != null) {
                    strategyEnabled = data.optBoolean("enabled", false);
                    updateToggleBtn(toggleBtn, strategyEnabled);
                    loadStrategy();
                }
            });
        });

        titleRow.addView(title);
        titleRow.addView(toggleBtn);
        card.addView(titleRow);

        // Content
        strategyContent = new LinearLayout(this);
        strategyContent.setOrientation(LinearLayout.VERTICAL);
        strategyContent.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        card.addView(strategyContent);

        TextView hint = new TextView(this);
        hint.setText("載入中...");
        hint.setTextSize(12);
        hint.setTextColor(UIHelper.TEXT_HINT);
        strategyContent.addView(hint);

        return card;
    }

    private void updateToggleBtn(TextView btn, boolean enabled) {
        if (enabled) {
            btn.setText("運行中");
            btn.setTextColor(UIHelper.ACCENT_GREEN);
            btn.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.ACCENT_GREEN, 10, 1, this));
        } else {
            btn.setText("已停止");
            btn.setTextColor(UIHelper.TEXT_HINT);
            btn.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.TEXT_HINT, 10, 1, this));
        }
    }

    private void loadStrategy() {
        BridgeClient.getCryptoStrategySignal((data, error) -> {
            strategyContent.removeAllViews();
            if (error != null || data == null) {
                TextView tv = new TextView(this);
                tv.setText("無法取得策略信號");
                tv.setTextSize(12);
                tv.setTextColor(UIHelper.TEXT_HINT);
                strategyContent.addView(tv);
                return;
            }

            strategyEnabled = data.optBoolean("strategy_enabled", false);
            String signal = data.optString("signal", "hold");
            String reason = data.optString("reason", "");
            int consecutive = data.optInt("consecutive", 0);

            // Signal indicator
            int signalColor;
            String signalText;
            switch (signal) {
                case "buy":
                    signalColor = UIHelper.ACCENT_GREEN;
                    signalText = "BUY";
                    break;
                case "sell":
                    signalColor = UIHelper.ACCENT_RED;
                    signalText = "SELL";
                    break;
                default:
                    signalColor = UIHelper.TEXT_HINT;
                    signalText = "HOLD";
                    break;
            }

            // Signal row
            LinearLayout signalRow = new LinearLayout(this);
            signalRow.setOrientation(LinearLayout.HORIZONTAL);
            signalRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView signalBadge = new TextView(this);
            signalBadge.setText(signalText);
            signalBadge.setTextSize(16);
            signalBadge.setTextColor(Color.WHITE);
            signalBadge.setTypeface(Typeface.DEFAULT_BOLD);
            signalBadge.setGravity(Gravity.CENTER);
            signalBadge.setBackground(UIHelper.roundRect(signalColor, 8, this));
            signalBadge.setPadding(UIHelper.dp(this, 14), UIHelper.dp(this, 6),
                    UIHelper.dp(this, 14), UIHelper.dp(this, 6));
            signalRow.addView(signalBadge);

            TextView reasonTv = new TextView(this);
            reasonTv.setText(reason);
            reasonTv.setTextSize(13);
            reasonTv.setTextColor(UIHelper.TEXT_PRIMARY);
            reasonTv.setPadding(UIHelper.dp(this, 10), 0, 0, 0);
            signalRow.addView(reasonTv);

            strategyContent.addView(signalRow);

            // Active filters info
            String activeFilters = data.optString("active_filters", "HA");
            int version = data.optInt("version", 0);
            String versionText = version > 0 ? "v" + version + " " : "";

            TextView desc = new TextView(this);
            desc.setText(versionText + activeFilters + "\n自動交易: 買入用 50% 餘額，賣出全部持倉");
            desc.setTextSize(11);
            desc.setTextColor(UIHelper.TEXT_HINT);
            desc.setPadding(0, UIHelper.dp(this, 8), 0, 0);
            strategyContent.addView(desc);
        });
    }

    // ==================== Evolution Card ====================

    private LinearLayout buildEvolutionCard() {
        LinearLayout card = makeCard();

        TextView title = new TextView(this);
        title.setText("策略進化");
        title.setTextSize(15);
        title.setTextColor(UIHelper.ACCENT_ORANGE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        evolutionContent = new LinearLayout(this);
        evolutionContent.setOrientation(LinearLayout.VERTICAL);
        evolutionContent.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        card.addView(evolutionContent);

        TextView hint = new TextView(this);
        hint.setText("載入中...");
        hint.setTextSize(12);
        hint.setTextColor(UIHelper.TEXT_HINT);
        evolutionContent.addView(hint);

        return card;
    }

    private void loadEvolution() {
        BridgeClient.getCryptoStrategyVersions((data, error) -> {
            evolutionContent.removeAllViews();
            if (error != null || data == null) {
                TextView tv = new TextView(this);
                tv.setText("無法取得策略版本");
                tv.setTextSize(12);
                tv.setTextColor(UIHelper.TEXT_HINT);
                evolutionContent.addView(tv);
                return;
            }
            try {
                JSONArray versions = data.optJSONArray("versions");
                if (versions == null || versions.length() == 0) {
                    TextView tv = new TextView(this);
                    tv.setText("尚無策略版本");
                    tv.setTextSize(12);
                    tv.setTextColor(UIHelper.TEXT_HINT);
                    evolutionContent.addView(tv);
                    return;
                }
                for (int i = 0; i < versions.length() && i < 10; i++) {
                    JSONObject v = versions.getJSONObject(i);
                    evolutionContent.addView(buildVersionRow(v));
                }
            } catch (Exception e) {
                AppLog.e("Crypto", "loadEvolution error: " + e.getMessage());
            }
        });
    }

    private LinearLayout buildVersionRow(JSONObject v) {
        boolean isActive = v.optInt("is_active", 0) == 1;
        int versionNum = v.optInt("version", 0);
        double winRate = v.optDouble("win_rate", 0);
        int wins = v.optInt("win_count", 0);
        int losses = v.optInt("loss_count", 0);
        String filtersDesc = v.optString("filters_desc", "HA");
        String description = v.optString("description", "");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UIHelper.dp(this, 5), 0, UIHelper.dp(this, 5));

        // Version badge
        TextView badge = new TextView(this);
        badge.setText("v" + versionNum);
        badge.setTextSize(12);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        int badgeSize = UIHelper.dp(this, 32);
        badge.setLayoutParams(new LinearLayout.LayoutParams(badgeSize, badgeSize));
        if (isActive) {
            badge.setTextColor(Color.WHITE);
            badge.setBackground(UIHelper.roundRect(UIHelper.ACCENT_GREEN, 8, this));
        } else {
            badge.setTextColor(UIHelper.TEXT_HINT);
            badge.setBackground(UIHelper.roundRect(0xFF2A2A3A, 8, this));
        }
        row.addView(badge);

        // Details
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(UIHelper.dp(this, 8), 0, 0, 0);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        details.setLayoutParams(detailLp);

        TextView filtersTv = new TextView(this);
        filtersTv.setText(filtersDesc);
        filtersTv.setTextSize(12);
        filtersTv.setTextColor(isActive ? UIHelper.TEXT_PRIMARY : UIHelper.TEXT_SECONDARY);
        details.addView(filtersTv);

        if (!description.isEmpty()) {
            TextView descTv = new TextView(this);
            descTv.setText(description.length() > 40 ? description.substring(0, 40) + "..." : description);
            descTv.setTextSize(10);
            descTv.setTextColor(UIHelper.TEXT_HINT);
            details.addView(descTv);
        }

        row.addView(details);

        // Win rate
        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setGravity(Gravity.END);

        int total = wins + losses;
        if (total > 0) {
            TextView rateTv = new TextView(this);
            rateTv.setText(String.format("%.1f%%", winRate));
            rateTv.setTextSize(14);
            rateTv.setTypeface(Typeface.DEFAULT_BOLD);
            rateTv.setGravity(Gravity.END);
            if (winRate >= 55) {
                rateTv.setTextColor(UIHelper.ACCENT_GREEN);
            } else if (winRate < 45) {
                rateTv.setTextColor(UIHelper.ACCENT_RED);
            } else {
                rateTv.setTextColor(UIHelper.TEXT_HINT);
            }
            rightCol.addView(rateTv);

            TextView countTv = new TextView(this);
            countTv.setText(wins + "勝/" + losses + "負");
            countTv.setTextSize(10);
            countTv.setTextColor(UIHelper.TEXT_HINT);
            countTv.setGravity(Gravity.END);
            rightCol.addView(countTv);
        } else {
            TextView noData = new TextView(this);
            noData.setText(isActive ? "運行中" : "無數據");
            noData.setTextSize(11);
            noData.setTextColor(UIHelper.TEXT_HINT);
            noData.setGravity(Gravity.END);
            rightCol.addView(noData);
        }

        row.addView(rightCol);
        return row;
    }

    // ==================== Trade History Card ====================

    private LinearLayout buildTradeCard() {
        LinearLayout card = makeCard();

        TextView title = new TextView(this);
        title.setText("交易紀錄");
        title.setTextSize(15);
        title.setTextColor(UIHelper.ACCENT_ORANGE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        tradeContent = new LinearLayout(this);
        tradeContent.setOrientation(LinearLayout.VERTICAL);
        tradeContent.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        card.addView(tradeContent);

        tradeStatus = new TextView(this);
        tradeStatus.setTextSize(12);
        tradeStatus.setTextColor(UIHelper.TEXT_HINT);
        tradeStatus.setText("載入中...");
        tradeContent.addView(tradeStatus);

        return card;
    }

    private void loadTrades() {
        BridgeClient.getCryptoTrades((data, error) -> {
            tradeContent.removeAllViews();
            if (error != null || data == null) {
                TextView empty = new TextView(this);
                empty.setText("尚無交易紀錄");
                empty.setTextSize(12);
                empty.setTextColor(UIHelper.TEXT_HINT);
                tradeContent.addView(empty);
                return;
            }
            try {
                JSONArray trades = data.optJSONArray("trades");
                if (trades == null || trades.length() == 0) {
                    TextView empty = new TextView(this);
                    empty.setText("尚無交易紀錄");
                    empty.setTextSize(12);
                    empty.setTextColor(UIHelper.TEXT_HINT);
                    tradeContent.addView(empty);
                    return;
                }
                for (int i = 0; i < trades.length() && i < 20; i++) {
                    JSONObject t = trades.getJSONObject(i);
                    tradeContent.addView(buildTradeRow(t));
                }
            } catch (Exception e) {
                AppLog.e("Crypto", "displayTrades error: " + e.getMessage());
            }
        });
    }

    private LinearLayout buildTradeRow(JSONObject trade) {
        boolean isBuy = "buy".equals(trade.optString("side"));
        double qty = trade.optDouble("quantity", 0);
        double price = trade.optDouble("price", 0);
        double total = trade.optDouble("total_value", 0);
        double pnl = trade.optDouble("pnl", Double.NaN);
        String time = trade.optString("created_at", "").replace("T", " ");
        if (time.length() > 16) time = time.substring(0, 16);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UIHelper.dp(this, 6), 0, UIHelper.dp(this, 6));

        // Side indicator
        TextView sideTv = new TextView(this);
        sideTv.setText(isBuy ? "買" : "賣");
        sideTv.setTextSize(13);
        sideTv.setTextColor(isBuy ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_RED);
        sideTv.setTypeface(Typeface.DEFAULT_BOLD);
        sideTv.setGravity(Gravity.CENTER);
        int sideSize = UIHelper.dp(this, 28);
        sideTv.setLayoutParams(new LinearLayout.LayoutParams(sideSize, sideSize));
        sideTv.setBackground(UIHelper.roundRect(isBuy ? 0xFF1A3A2A : 0xFF3A1A1A, 6, this));
        row.addView(sideTv);

        // Details
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(UIHelper.dp(this, 8), 0, 0, 0);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        details.setLayoutParams(detailLp);

        TextView info = new TextView(this);
        info.setText(formatBtc(qty) + " BTC @ $" + formatUsd(price));
        info.setTextSize(12);
        info.setTextColor(UIHelper.TEXT_PRIMARY);
        details.addView(info);

        TextView timeTv = new TextView(this);
        timeTv.setText(time);
        timeTv.setTextSize(10);
        timeTv.setTextColor(UIHelper.TEXT_HINT);
        details.addView(timeTv);

        row.addView(details);

        // Amount / PnL
        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setGravity(Gravity.END);

        TextView totalTv = new TextView(this);
        totalTv.setText("$" + formatUsd(total));
        totalTv.setTextSize(12);
        totalTv.setTextColor(UIHelper.TEXT_PRIMARY);
        totalTv.setGravity(Gravity.END);
        rightCol.addView(totalTv);

        if (!Double.isNaN(pnl)) {
            TextView pnlTv = new TextView(this);
            pnlTv.setText((pnl >= 0 ? "+" : "") + "$" + formatUsd(Math.abs(pnl)));
            pnlTv.setTextSize(11);
            pnlTv.setTextColor(pnl >= 0 ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_RED);
            pnlTv.setGravity(Gravity.END);
            rightCol.addView(pnlTv);
        }

        row.addView(rightCol);
        return row;
    }

    // ==================== Helpers ====================

    private LinearLayout makeCard() {
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
        return card;
    }

    private String formatUsd(double v) {
        if (v == 0) return "0.00";
        return new DecimalFormat("#,##0.00").format(v);
    }

    private String formatBtc(double v) {
        if (v == 0) return "0";
        return new DecimalFormat("0.########").format(v);
    }

    private String formatVol(double v) {
        if (v >= 1000) return new DecimalFormat("#,##0").format(v);
        return new DecimalFormat("#,##0.00").format(v);
    }
}
