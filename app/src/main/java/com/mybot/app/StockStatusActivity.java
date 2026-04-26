package com.mybot.app;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.Iterator;

public class StockStatusActivity extends AppCompatActivity {

    private LinearLayout listContainer;
    private TextView summaryText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "\uD83D\uDCCA \u80A1\u7968\u8CC7\u6599\u72C0\u614B");
        TextView backBtn = new TextView(this);
        backBtn.setText("\u2190");
        backBtn.setTextSize(22);
        backBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        backBtn.setPadding(0, 0, UIHelper.dp(this, 16), 0);
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn, 0);
        root.addView(topBar);

        // Summary card
        LinearLayout summaryCard = UIHelper.card(this);
        summaryText = new TextView(this);
        summaryText.setTextSize(15);
        summaryText.setTextColor(UIHelper.TEXT_PRIMARY);
        summaryText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        summaryCard.addView(summaryText);

        TextView hint = new TextView(this);
        hint.setText("\u6BCF\u65E5 23:00 \u81EA\u52D5\u66F4\u65B0\u80A1\u50F9/\u7C4C\u78BC\uFF0C\u6BCF\u9031\u4E94\u66F4\u65B0\u5927\u6236\u6301\u80A1");
        hint.setTextSize(12);
        hint.setTextColor(UIHelper.TEXT_HINT);
        hint.setPadding(0, UIHelper.dp(this, 4), 0, 0);
        summaryCard.addView(hint);

        root.addView(summaryCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Scroll + list
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 16);
        listContainer.setPadding(p, UIHelper.dp(this, 8), p, p);

        scrollView.addView(listContainer);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);

        AppLog.i("StockStatus", "StockStatusActivity opened");
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStatus();
    }

    private void loadStatus() {
        listContainer.removeAllViews();
        summaryText.setText("\u8F09\u5165\u4E2D...");

        BridgeClient.getStockStatus((data, error) -> {
            if (error != null) {
                summaryText.setText("\u8F09\u5165\u5931\u6557");
                AppLog.e("StockStatus", "\u8F09\u5165\u5931\u6557: " + error);

                TextView errorText = new TextView(this);
                errorText.setText(error);
                errorText.setTextColor(UIHelper.ACCENT_RED);
                errorText.setTextSize(14);
                listContainer.addView(errorText);
                return;
            }
            if (data == null) return;

            // Count healthy items
            int total = 0;
            int healthy = 0;
            String[] order = {"market_data", "institutional", "shareholder_dist", "financials", "news", "recommendations"};

            for (String key : order) {
                JSONObject item = data.optJSONObject(key);
                if (item != null) {
                    total++;
                    String latestDate = item.optString("latest_date", null);
                    if (latestDate != null && !latestDate.isEmpty()) {
                        healthy++;
                    }
                }
            }

            summaryText.setText(healthy + "/" + total + " \u9805\u8CC7\u6599\u6B63\u5E38");

            // Build cards in order
            for (String key : order) {
                JSONObject item = data.optJSONObject(key);
                if (item != null) {
                    listContainer.addView(buildStatusCard(key, item));
                }
            }

            AppLog.i("StockStatus", "\u8F09\u5165\u5B8C\u6210: " + healthy + "/" + total + " \u6B63\u5E38");
        });
    }

    private LinearLayout buildStatusCard(String key, JSONObject item) {
        String name = item.optString("name", key);
        String latestDate = item.optString("latest_date", null);
        int count = item.optInt("count", 0);
        boolean isToday = item.optBoolean("is_today", false);

        LinearLayout card = UIHelper.card(this);

        // Row 1: status dot + name
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        // Status dot
        View dot = new View(this);
        int dotSize = UIHelper.dp(this, 10);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
        dotLp.setMargins(0, 0, UIHelper.dp(this, 10), 0);
        dot.setLayoutParams(dotLp);

        int dotColor;
        if (latestDate == null || latestDate.isEmpty()) {
            dotColor = UIHelper.ACCENT_RED; // No data
        } else if (isToday) {
            dotColor = UIHelper.ACCENT_GREEN; // Up to date
        } else {
            dotColor = UIHelper.ACCENT_ORANGE; // Has data but not today
        }
        dot.setBackground(UIHelper.roundRect(dotColor, 5, this));
        row1.addView(dot);

        // Name
        TextView nameText = new TextView(this);
        nameText.setText(name);
        nameText.setTextSize(16);
        nameText.setTextColor(UIHelper.TEXT_PRIMARY);
        nameText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        nameText.setLayoutParams(nameLp);
        row1.addView(nameText);

        // Count badge
        if (count > 0) {
            TextView countBadge = new TextView(this);
            countBadge.setText(formatCount(count));
            countBadge.setTextSize(12);
            countBadge.setTextColor(UIHelper.TEXT_SECONDARY);
            countBadge.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 8, this));
            countBadge.setPadding(UIHelper.dp(this, 8), UIHelper.dp(this, 2),
                    UIHelper.dp(this, 8), UIHelper.dp(this, 2));
            row1.addView(countBadge);
        }

        card.addView(row1);

        // Row 2: latest date info
        TextView dateText = new TextView(this);
        if (latestDate == null || latestDate.isEmpty()) {
            dateText.setText("\u7121\u8CC7\u6599");
            dateText.setTextColor(UIHelper.ACCENT_RED);
        } else {
            String statusText = isToday ? " (\u4ECA\u65E5)" : "";
            dateText.setText("\u6700\u65B0: " + latestDate + statusText);
            dateText.setTextColor(isToday ? UIHelper.ACCENT_GREEN : UIHelper.TEXT_SECONDARY);
        }
        dateText.setTextSize(13);
        dateText.setPadding(UIHelper.dp(this, 20), UIHelper.dp(this, 4), 0, 0);
        card.addView(dateText);

        // Row 3: description based on key
        String desc = getDescription(key);
        if (desc != null) {
            TextView descText = new TextView(this);
            descText.setText(desc);
            descText.setTextSize(12);
            descText.setTextColor(UIHelper.TEXT_HINT);
            descText.setPadding(UIHelper.dp(this, 20), UIHelper.dp(this, 2), 0, 0);
            card.addView(descText);
        }

        return card;
    }

    private String formatCount(int count) {
        if (count >= 1000) {
            return String.format("%.1fK", count / 1000.0);
        }
        return String.valueOf(count);
    }

    private String getDescription(String key) {
        switch (key) {
            case "market_data":
                return "\u958B\u76E4/\u6536\u76E4/\u6700\u9AD8/\u6700\u4F4E/\u6210\u4EA4\u91CF";
            case "institutional":
                return "\u5916\u8CC7/\u6295\u4FE1\u8CB7\u8CE3\u8D85";
            case "shareholder_dist":
                return "TDCC \u5927\u6236\u6301\u80A1\u6BD4\u4F8B (\u6BCF\u9031\u4E94)";
            case "financials":
                return "\u5B63\u5831: EPS/\u6BDB\u5229/\u71DF\u6536";
            case "news":
                return "\u76F8\u95DC\u65B0\u805E\u6458\u8981";
            case "recommendations":
                return "AI \u6BCF\u65E5\u63A8\u85A6\u7D50\u679C";
            default:
                return null;
        }
    }
}
