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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ReportActivity extends AppCompatActivity {

    private ExpenseDbHelper dbHelper;
    private LinearLayout contentArea;
    private int currentMode = 0; // 0=month, 1=quarter, 2=year
    private final Calendar currentPeriod = Calendar.getInstance();

    private TextView monthTab, quarterTab, yearTab;
    private TextView periodLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        dbHelper = new ExpenseDbHelper(this);

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "消費報表");

        // Tab bar
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundColor(UIHelper.BG_CARD);
        tabBar.setGravity(Gravity.CENTER);
        int tp = UIHelper.dp(this, 8);
        tabBar.setPadding(tp, tp, tp, tp);

        monthTab = createTab("月報");
        quarterTab = createTab("季報");
        yearTab = createTab("年報");

        monthTab.setOnClickListener(v -> switchMode(0));
        quarterTab.setOnClickListener(v -> switchMode(1));
        yearTab.setOnClickListener(v -> switchMode(2));

        tabBar.addView(monthTab);
        tabBar.addView(quarterTab);
        tabBar.addView(yearTab);

        // Period navigation
        LinearLayout navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER);
        navBar.setBackgroundColor(UIHelper.BG_CARD_ALT);
        int np = UIHelper.dp(this, 12);
        navBar.setPadding(np, UIHelper.dp(this, 10), np, UIHelper.dp(this, 10));

        TextView prevBtn = navButton("<");
        prevBtn.setOnClickListener(v -> navigate(-1));

        periodLabel = new TextView(this);
        periodLabel.setTextSize(16);
        periodLabel.setTextColor(UIHelper.TEXT_PRIMARY);
        periodLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        periodLabel.setGravity(Gravity.CENTER);
        periodLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView nextBtn = navButton(">");
        nextBtn.setOnClickListener(v -> navigate(1));

        navBar.addView(prevBtn);
        navBar.addView(periodLabel);
        navBar.addView(nextBtn);

        // Content
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        int cp = UIHelper.dp(this, 16);
        contentArea.setPadding(cp, UIHelper.dp(this, 12), cp, cp);
        scrollView.addView(contentArea);

        root.addView(topBar);
        root.addView(tabBar);
        root.addView(navBar);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        AppLog.i("Expense", "消費報表頁面已開啟");
        switchMode(0);
    }

    private TextView createTab(String text) {
        TextView tab = new TextView(this);
        tab.setText(text);
        tab.setTextSize(14);
        tab.setGravity(Gravity.CENTER);
        tab.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        int h = UIHelper.dp(this, 20);
        int v = UIHelper.dp(this, 8);
        tab.setPadding(h, v, h, v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        int m = UIHelper.dp(this, 4);
        lp.setMargins(m, 0, m, 0);
        tab.setLayoutParams(lp);
        return tab;
    }

    private TextView navButton(String text) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(20);
        btn.setTextColor(UIHelper.ACCENT_BLUE);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        int s = UIHelper.dp(this, 44);
        btn.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return btn;
    }

    private void switchMode(int mode) {
        currentMode = mode;
        currentPeriod.setTimeInMillis(System.currentTimeMillis());

        monthTab.setBackground(UIHelper.roundRect(mode == 0 ? UIHelper.ACCENT_BLUE : UIHelper.BG_CARD_ALT, 8, this));
        monthTab.setTextColor(mode == 0 ? Color.WHITE : UIHelper.TEXT_SECONDARY);
        quarterTab.setBackground(UIHelper.roundRect(mode == 1 ? UIHelper.ACCENT_BLUE : UIHelper.BG_CARD_ALT, 8, this));
        quarterTab.setTextColor(mode == 1 ? Color.WHITE : UIHelper.TEXT_SECONDARY);
        yearTab.setBackground(UIHelper.roundRect(mode == 2 ? UIHelper.ACCENT_BLUE : UIHelper.BG_CARD_ALT, 8, this));
        yearTab.setTextColor(mode == 2 ? Color.WHITE : UIHelper.TEXT_SECONDARY);

        refreshReport();
    }

    private void navigate(int direction) {
        switch (currentMode) {
            case 0: currentPeriod.add(Calendar.MONTH, direction); break;
            case 1: currentPeriod.add(Calendar.MONTH, direction * 3); break;
            case 2: currentPeriod.add(Calendar.YEAR, direction); break;
        }
        refreshReport();
    }

    private long[] getDateRange() {
        Calendar start = (Calendar) currentPeriod.clone();
        Calendar end;

        switch (currentMode) {
            case 0: // Month
                start.set(Calendar.DAY_OF_MONTH, 1);
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
                end = (Calendar) start.clone();
                end.add(Calendar.MONTH, 1);
                break;
            case 1: // Quarter
                int q = start.get(Calendar.MONTH) / 3;
                start.set(Calendar.MONTH, q * 3);
                start.set(Calendar.DAY_OF_MONTH, 1);
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
                end = (Calendar) start.clone();
                end.add(Calendar.MONTH, 3);
                break;
            default: // Year
                start.set(Calendar.MONTH, 0);
                start.set(Calendar.DAY_OF_MONTH, 1);
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
                end = (Calendar) start.clone();
                end.add(Calendar.YEAR, 1);
                break;
        }
        return new long[]{start.getTimeInMillis(), end.getTimeInMillis()};
    }

    private String getPeriodText() {
        int year = currentPeriod.get(Calendar.YEAR);
        int month = currentPeriod.get(Calendar.MONTH) + 1;
        switch (currentMode) {
            case 0: return year + " 年 " + month + " 月";
            case 1:
                int q = currentPeriod.get(Calendar.MONTH) / 3 + 1;
                return year + " 年 Q" + q;
            default: return year + " 年";
        }
    }

    private void refreshReport() {
        periodLabel.setText(getPeriodText());
        contentArea.removeAllViews();

        String[] modeNames = {"月報", "季報", "年報"};
        AppLog.i("Expense", "生成報表: " + modeNames[currentMode] + " " + getPeriodText());

        long[] range = getDateRange();
        double totalAmount = dbHelper.sumByDateRange(range[0], range[1]);
        int totalCount = dbHelper.countByDateRange(range[0], range[1]);
        List<ExpenseDbHelper.CategorySum> categories = dbHelper.sumByCategory(range[0], range[1]);

        AppLog.i("Expense", "報表結果: 共" + totalCount + "筆, 總額$" + String.format(Locale.getDefault(), "%.0f", totalAmount) + ", " + categories.size() + "個類別");

        // Summary card
        LinearLayout summaryCard = UIHelper.card(this);

        TextView summaryTitle = new TextView(this);
        summaryTitle.setText("總覽");
        summaryTitle.setTextSize(14);
        summaryTitle.setTextColor(UIHelper.TEXT_SECONDARY);
        summaryTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        TextView totalView = new TextView(this);
        totalView.setText(String.format(Locale.getDefault(), "$%,.0f", totalAmount));
        totalView.setTextSize(36);
        totalView.setTextColor(UIHelper.ACCENT_RED);
        totalView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        totalView.setPadding(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 4));

        TextView countView = new TextView(this);
        countView.setText(totalCount + " 筆消費");
        countView.setTextSize(14);
        countView.setTextColor(UIHelper.TEXT_SECONDARY);

        // Daily average
        long[] dr = getDateRange();
        long days = Math.max(1, (dr[1] - dr[0]) / 86400000L);
        long actualDays = Math.min(days,
                (System.currentTimeMillis() - dr[0]) / 86400000L + 1);
        if (actualDays < 1) actualDays = 1;
        double dailyAvg = totalAmount / actualDays;

        TextView avgView = new TextView(this);
        avgView.setText(String.format(Locale.getDefault(), "日均消費 $%,.0f", dailyAvg));
        avgView.setTextSize(13);
        avgView.setTextColor(UIHelper.ACCENT_BLUE);
        avgView.setPadding(0, UIHelper.dp(this, 4), 0, 0);

        summaryCard.addView(summaryTitle);
        summaryCard.addView(totalView);
        summaryCard.addView(countView);
        summaryCard.addView(avgView);
        contentArea.addView(summaryCard);

        // Category breakdown
        if (!categories.isEmpty()) {
            LinearLayout catCard = UIHelper.card(this);
            LinearLayout.LayoutParams catLp = (LinearLayout.LayoutParams) catCard.getLayoutParams();
            catLp.setMargins(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 4));
            catCard.setLayoutParams(catLp);

            TextView catTitle = new TextView(this);
            catTitle.setText("類別明細");
            catTitle.setTextSize(14);
            catTitle.setTextColor(UIHelper.TEXT_SECONDARY);
            catTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            catTitle.setPadding(0, 0, 0, UIHelper.dp(this, 12));
            catCard.addView(catTitle);

            for (int i = 0; i < categories.size(); i++) {
                ExpenseDbHelper.CategorySum cs = categories.get(i);
                int barColor = UIHelper.getCategoryColor(cs.category);
                double pct = totalAmount > 0 ? cs.total / totalAmount * 100 : 0;

                // Category row
                LinearLayout catRow = new LinearLayout(this);
                catRow.setOrientation(LinearLayout.VERTICAL);
                catRow.setPadding(0, UIHelper.dp(this, 6), 0, UIHelper.dp(this, 6));

                // Name + amount row
                LinearLayout nameRow = new LinearLayout(this);
                nameRow.setOrientation(LinearLayout.HORIZONTAL);
                nameRow.setGravity(Gravity.CENTER_VERTICAL);

                // Color dot
                TextView dot = new TextView(this);
                dot.setBackground(UIHelper.roundRect(barColor, 20, this));
                LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                        UIHelper.dp(this, 10), UIHelper.dp(this, 10));
                dotLp.setMargins(0, 0, UIHelper.dp(this, 10), 0);
                dot.setLayoutParams(dotLp);

                TextView catName = new TextView(this);
                catName.setText(cs.category);
                catName.setTextSize(14);
                catName.setTextColor(UIHelper.TEXT_PRIMARY);
                catName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                TextView catCount = new TextView(this);
                catCount.setText(cs.count + "筆");
                catCount.setTextSize(12);
                catCount.setTextColor(UIHelper.TEXT_HINT);
                catCount.setPadding(UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8), 0);

                TextView catAmount = new TextView(this);
                catAmount.setText(String.format(Locale.getDefault(), "$%,.0f", cs.total));
                catAmount.setTextSize(14);
                catAmount.setTextColor(UIHelper.TEXT_PRIMARY);
                catAmount.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

                nameRow.addView(dot);
                nameRow.addView(catName);
                nameRow.addView(catCount);
                nameRow.addView(catAmount);

                // Progress bar
                LinearLayout barBg = new LinearLayout(this);
                barBg.setBackground(UIHelper.roundRect(UIHelper.BG_CARD_ALT, 4, this));
                LinearLayout.LayoutParams barBgLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 6));
                barBgLp.setMargins(UIHelper.dp(this, 20), UIHelper.dp(this, 6), 0, 0);
                barBg.setLayoutParams(barBgLp);

                View bar = new View(this);
                bar.setBackground(UIHelper.roundRect(barColor, 4, this));
                LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT);
                barLp.weight = (float) pct;
                bar.setLayoutParams(barLp);

                View spacer = new View(this);
                LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT);
                spacerLp.weight = (float) (100 - pct);
                spacer.setLayoutParams(spacerLp);

                barBg.addView(bar);
                barBg.addView(spacer);

                // Percentage
                TextView pctView = new TextView(this);
                pctView.setText(String.format(Locale.getDefault(), "%.1f%%", pct));
                pctView.setTextSize(11);
                pctView.setTextColor(UIHelper.TEXT_HINT);
                pctView.setPadding(UIHelper.dp(this, 20), UIHelper.dp(this, 2), 0, 0);

                catRow.addView(nameRow);
                catRow.addView(barBg);
                catRow.addView(pctView);
                catCard.addView(catRow);

                if (i < categories.size() - 1) {
                    catCard.addView(UIHelper.divider(this));
                }
            }

            contentArea.addView(catCard);
        }

        // Empty state
        if (totalCount == 0) {
            TextView emptyView = new TextView(this);
            emptyView.setText("此期間無消費紀錄");
            emptyView.setTextSize(16);
            emptyView.setTextColor(UIHelper.TEXT_HINT);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(0, UIHelper.dp(this, 40), 0, 0);
            contentArea.addView(emptyView);
        }
    }
}
