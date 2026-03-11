package com.mybot.app;

import android.app.AlertDialog;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.Locale;

public class TravelBudgetActivity extends AppCompatActivity {

    private long tripId;
    private TravelDbHelper db;
    private LinearLayout contentLayout;

    private static final String[] CATEGORIES = {
        "transport", "food", "accommodation", "ticket", "shopping", "other"
    };
    private static final String[] CATEGORY_NAMES = {
        "\u4EA4\u901A", "\u9910\u98F2", "\u4F4F\u5BBF", "\u9580\u7968", "\u8CFC\u7269", "\u5176\u4ED6"
    };
    private static final String[] CATEGORY_ICONS = {
        "\uD83D\uDE8C", "\uD83C\uDF74", "\uD83C\uDFE8", "\uD83C\uDFAB", "\uD83D\uDECD", "\uD83D\uDCE6"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tripId = getIntent().getLongExtra("trip_id", -1);
        if (tripId == -1) { finish(); return; }

        AppLog.i("Travel", "TravelBudgetActivity\u958B\u555F tripId=" + tripId);
        db = TravelDbHelper.getInstance(this);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBudget();
    }

    private void buildUI() {
        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(UIHelper.BG_TOP_BAR);
        int p = UIHelper.dp(this, 16);
        topBar.setPadding(p, UIHelper.dp(this, 12), p, UIHelper.dp(this, 12));
        topBar.setElevation(UIHelper.dp(this, 4));

        TextView backBtn = new TextView(this);
        backBtn.setText("\u2190");
        backBtn.setTextSize(20);
        backBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        backBtn.setPadding(0, 0, UIHelper.dp(this, 12), 0);
        backBtn.setOnClickListener(v -> finish());

        TextView titleTv = new TextView(this);
        titleTv.setText("\uD83D\uDCB0 \u9810\u7B97\u7BA1\u7406");
        titleTv.setTextSize(18);
        titleTv.setTextColor(UIHelper.TEXT_PRIMARY);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView addBtn = new TextView(this);
        addBtn.setText("\uFF0B \u8A18\u5E33");
        addBtn.setTextSize(14);
        addBtn.setTextColor(UIHelper.ACCENT_GREEN);
        addBtn.setOnClickListener(v -> showAddExpenseDialog());

        topBar.addView(backBtn);
        topBar.addView(titleTv);
        topBar.addView(addBtn);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        int cp = UIHelper.dp(this, 16);
        contentLayout.setPadding(cp, cp, cp, cp);

        scrollView.addView(contentLayout);

        root.addView(topBar);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void refreshBudget() {
        contentLayout.removeAllViews();

        Cursor c = db.getTripById(tripId);
        if (!c.moveToFirst()) { c.close(); return; }

        double estimated = c.getDouble(c.getColumnIndexOrThrow(TravelDbHelper.COL_ESTIMATED_BUDGET));
        double actual = c.getDouble(c.getColumnIndexOrThrow(TravelDbHelper.COL_ACTUAL_BUDGET));
        String itineraryJson = c.getString(c.getColumnIndexOrThrow(TravelDbHelper.COL_ITINERARY_JSON));
        c.close();

        // Parse estimated breakdown from itinerary
        double estTransport = 0, estFood = 0, estAccom = 0, estTicket = 0;
        if (itineraryJson != null) {
            try {
                JSONObject itinerary = new JSONObject(itineraryJson);
                JSONObject breakdown = itinerary.optJSONObject("cost_breakdown");
                if (breakdown != null) {
                    estTransport = breakdown.optDouble("transport", 0);
                    estFood = breakdown.optDouble("food", 0);
                    estAccom = breakdown.optDouble("accommodation", 0);
                    estTicket = breakdown.optDouble("tickets", 0);
                }
            } catch (Exception ignored) {}
        }

        // ── Overview card ──
        LinearLayout overviewCard = new LinearLayout(this);
        overviewCard.setOrientation(LinearLayout.VERTICAL);
        overviewCard.setGravity(Gravity.CENTER);
        overviewCard.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 12, this));
        int op = UIHelper.dp(this, 20);
        overviewCard.setPadding(op, op, op, op);
        LinearLayout.LayoutParams oLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        oLp.setMargins(0, 0, 0, UIHelper.dp(this, 16));
        overviewCard.setLayoutParams(oLp);

        // Percentage
        double pct = estimated > 0 ? (actual / estimated) * 100 : 0;
        int pctColor = pct > 100 ? UIHelper.ACCENT_RED : (pct > 80 ? UIHelper.ACCENT_ORANGE : UIHelper.ACCENT_GREEN);

        TextView pctTv = new TextView(this);
        pctTv.setText(String.format(Locale.US, "%.0f%%", pct));
        pctTv.setTextSize(36);
        pctTv.setTextColor(pctColor);
        pctTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        TextView summaryTv = new TextView(this);
        summaryTv.setText("\u5DF2\u82B1\u8CBB NT$" + String.format(Locale.US, "%,.0f", actual)
                + " / \u9810\u7B97 NT$" + String.format(Locale.US, "%,.0f", estimated));
        summaryTv.setTextSize(14);
        summaryTv.setTextColor(UIHelper.TEXT_SECONDARY);
        summaryTv.setPadding(0, UIHelper.dp(this, 4), 0, 0);

        // Progress bar
        LinearLayout progressBg = new LinearLayout(this);
        progressBg.setBackground(UIHelper.roundRect(UIHelper.BG_INPUT, 6, this));
        LinearLayout.LayoutParams pbgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 8));
        pbgLp.setMargins(0, UIHelper.dp(this, 12), 0, 0);
        progressBg.setLayoutParams(pbgLp);

        View progressFill = new View(this);
        progressFill.setBackground(UIHelper.roundRect(pctColor, 6, this));
        float fillPct = Math.min((float) pct / 100f, 1f);
        progressFill.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, fillPct));
        progressBg.addView(progressFill);
        if (fillPct < 1f) {
            View empty = new View(this);
            empty.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - fillPct));
            progressBg.addView(empty);
        }

        overviewCard.addView(pctTv);
        overviewCard.addView(summaryTv);
        overviewCard.addView(progressBg);

        if (pct > 100) {
            TextView warning = new TextView(this);
            warning.setText("\u26A0\uFE0F \u5DF2\u8D85\u51FA\u9810\u7B97\uFF01");
            warning.setTextSize(13);
            warning.setTextColor(UIHelper.ACCENT_RED);
            warning.setPadding(0, UIHelper.dp(this, 8), 0, 0);
            overviewCard.addView(warning);
        } else if (pct > 80) {
            TextView warning = new TextView(this);
            warning.setText("\u26A0\uFE0F \u5DF2\u4F7F\u7528\u9810\u7B97 80% \u4EE5\u4E0A");
            warning.setTextSize(13);
            warning.setTextColor(UIHelper.ACCENT_ORANGE);
            warning.setPadding(0, UIHelper.dp(this, 8), 0, 0);
            overviewCard.addView(warning);
        }

        contentLayout.addView(overviewCard);

        // ── Category breakdown ──
        TextView catHeader = new TextView(this);
        catHeader.setText("\u5206\u985E\u660E\u7D30");
        catHeader.setTextSize(14);
        catHeader.setTextColor(UIHelper.TEXT_SECONDARY);
        catHeader.setTypeface(null, Typeface.BOLD);
        catHeader.setPadding(0, 0, 0, UIHelper.dp(this, 8));
        contentLayout.addView(catHeader);

        double[] estimatedCats = {estTransport, estFood, estAccom, estTicket, 0, 0};
        for (int i = 0; i < CATEGORIES.length; i++) {
            double catActual = db.getExpenseSumByCategory(tripId, CATEGORIES[i]);
            contentLayout.addView(buildCategoryRow(CATEGORY_ICONS[i], CATEGORY_NAMES[i],
                    estimatedCats[i], catActual));
        }

        // ── Expense list ──
        TextView expHeader = new TextView(this);
        expHeader.setText("\u82B1\u8CBB\u7D00\u9304");
        expHeader.setTextSize(14);
        expHeader.setTextColor(UIHelper.TEXT_SECONDARY);
        expHeader.setTypeface(null, Typeface.BOLD);
        expHeader.setPadding(0, UIHelper.dp(this, 16), 0, UIHelper.dp(this, 8));
        contentLayout.addView(expHeader);

        Cursor expenses = db.getExpensesByTrip(tripId);
        if (expenses.getCount() == 0) {
            TextView noExp = new TextView(this);
            noExp.setText("\u9084\u6C92\u6709\u82B1\u8CBB\u7D00\u9304\uFF0C\u9EDE\u64CA\u53F3\u4E0A\u89D2\u300C\uFF0B \u8A18\u5E33\u300D\u65B0\u589E");
            noExp.setTextSize(13);
            noExp.setTextColor(UIHelper.TEXT_HINT);
            noExp.setGravity(Gravity.CENTER);
            noExp.setPadding(0, UIHelper.dp(this, 20), 0, 0);
            contentLayout.addView(noExp);
        } else {
            while (expenses.moveToNext()) {
                long expId = expenses.getLong(expenses.getColumnIndexOrThrow(TravelDbHelper.COL_ID));
                int dayNum = expenses.getInt(expenses.getColumnIndexOrThrow(TravelDbHelper.COL_DAY_NUMBER));
                String cat = expenses.getString(expenses.getColumnIndexOrThrow(TravelDbHelper.COL_CATEGORY));
                String desc = expenses.getString(expenses.getColumnIndexOrThrow(TravelDbHelper.COL_DESCRIPTION));
                double amt = expenses.getDouble(expenses.getColumnIndexOrThrow(TravelDbHelper.COL_AMOUNT));

                contentLayout.addView(buildExpenseRow(expId, dayNum, cat, desc, amt));
            }
        }
        expenses.close();
    }

    private LinearLayout buildCategoryRow(String icon, String name, double estimated, double actual) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 8, this));
        int rp = UIHelper.dp(this, 12);
        row.setPadding(rp, rp, rp, rp);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rLp.setMargins(0, 0, 0, UIHelper.dp(this, 6));
        row.setLayoutParams(rLp);

        TextView iconTv = new TextView(this);
        iconTv.setText(icon);
        iconTv.setTextSize(16);
        iconTv.setPadding(0, 0, UIHelper.dp(this, 8), 0);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView nameTv = new TextView(this);
        nameTv.setText(name);
        nameTv.setTextSize(13);
        nameTv.setTextColor(UIHelper.TEXT_PRIMARY);

        TextView detailTv = new TextView(this);
        String detail = "\u5BE6\u969B NT$" + String.format(Locale.US, "%,.0f", actual);
        if (estimated > 0) detail += " / \u9810\u4F30 NT$" + String.format(Locale.US, "%,.0f", estimated);
        detailTv.setText(detail);
        detailTv.setTextSize(11);
        detailTv.setTextColor(UIHelper.TEXT_HINT);

        info.addView(nameTv);
        info.addView(detailTv);

        row.addView(iconTv);
        row.addView(info);
        return row;
    }

    private LinearLayout buildExpenseRow(long expId, int dayNum, String cat, String desc, double amt) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(UIHelper.roundRect(UIHelper.BG_CARD_ALT, 8, this));
        int rp = UIHelper.dp(this, 10);
        row.setPadding(rp, rp, rp, rp);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rLp.setMargins(0, 0, 0, UIHelper.dp(this, 4));
        row.setLayoutParams(rLp);

        // Find category icon
        String icon = "\uD83D\uDCE6";
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equals(cat)) { icon = CATEGORY_ICONS[i]; break; }
        }

        TextView iconTv = new TextView(this);
        iconTv.setText(icon);
        iconTv.setTextSize(14);
        iconTv.setPadding(0, 0, UIHelper.dp(this, 8), 0);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView descTv = new TextView(this);
        descTv.setText((desc != null && !desc.isEmpty() ? desc : cat) + (dayNum > 0 ? " (Day " + dayNum + ")" : ""));
        descTv.setTextSize(13);
        descTv.setTextColor(UIHelper.TEXT_PRIMARY);

        info.addView(descTv);

        TextView amtTv = new TextView(this);
        amtTv.setText("NT$" + String.format(Locale.US, "%,.0f", amt));
        amtTv.setTextSize(14);
        amtTv.setTextColor(UIHelper.ACCENT_RED);
        amtTv.setTypeface(null, Typeface.BOLD);

        row.addView(iconTv);
        row.addView(info);
        row.addView(amtTv);

        // Long press to delete
        row.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                    .setTitle("\u522A\u9664\u82B1\u8CBB")
                    .setMessage("\u78BA\u5B9A\u8981\u522A\u9664\u9019\u7B46\u82B1\u8CBB\u55CE\uFF1F")
                    .setPositiveButton("\u522A\u9664", (d, w) -> {
                        db.deleteExpense(expId, tripId);
                        Toast.makeText(this, "\u5DF2\u522A\u9664", Toast.LENGTH_SHORT).show();
                        refreshBudget();
                    })
                    .setNegativeButton("\u53D6\u6D88", null)
                    .show();
            return true;
        });

        return row;
    }

    private void showAddExpenseDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int fp = UIHelper.dp(this, 16);
        form.setPadding(fp, fp, fp, fp);

        // Category spinner (as buttons)
        TextView catLabel = new TextView(this);
        catLabel.setText("\u985E\u5225");
        catLabel.setTextColor(UIHelper.TEXT_SECONDARY);
        form.addView(catLabel);

        final String[] selectedCat = {CATEGORIES[0]};
        LinearLayout catRow = new LinearLayout(this);
        catRow.setOrientation(LinearLayout.HORIZONTAL);
        android.widget.HorizontalScrollView catScroll = new android.widget.HorizontalScrollView(this);
        catScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams catScrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        catScrollLp.setMargins(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 12));
        catScroll.setLayoutParams(catScrollLp);

        java.util.List<TextView> catBtns = new java.util.ArrayList<>();
        for (int i = 0; i < CATEGORIES.length; i++) {
            final int idx = i;
            TextView btn = new TextView(this);
            btn.setText(CATEGORY_ICONS[i] + " " + CATEGORY_NAMES[i]);
            btn.setTextSize(13);
            btn.setGravity(Gravity.CENTER);
            int cp = UIHelper.dp(this, 10);
            btn.setPadding(cp, UIHelper.dp(this, 6), cp, UIHelper.dp(this, 6));
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bLp.setMargins(0, 0, UIHelper.dp(this, 6), 0);
            btn.setLayoutParams(bLp);

            if (i == 0) {
                btn.setTextColor(UIHelper.ACCENT_GREEN);
                btn.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.ACCENT_GREEN, 16, 1, this));
            } else {
                btn.setTextColor(UIHelper.TEXT_HINT);
                btn.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.TEXT_HINT, 16, 1, this));
            }

            btn.setOnClickListener(v -> {
                selectedCat[0] = CATEGORIES[idx];
                for (TextView b : catBtns) {
                    b.setTextColor(UIHelper.TEXT_HINT);
                    b.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.TEXT_HINT, 16, 1, this));
                }
                btn.setTextColor(UIHelper.ACCENT_GREEN);
                btn.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.ACCENT_GREEN, 16, 1, this));
            });
            catRow.addView(btn);
            catBtns.add(btn);
        }
        catScroll.addView(catRow);
        form.addView(catScroll);

        // Day number
        EditText dayInput = new EditText(this);
        dayInput.setHint("\u7B2C\u5E7E\u5929\uFF08\u53EF\u7559\u7A7A\uFF09");
        dayInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        dayInput.setTextColor(UIHelper.TEXT_PRIMARY);
        dayInput.setHintTextColor(UIHelper.TEXT_HINT);
        form.addView(dayInput);

        // Description
        EditText descInput = new EditText(this);
        descInput.setHint("\u8AAA\u660E\uFF08\u4F8B\uFF1A\u9AD8\u9435\u8ECA\u7968\uFF09");
        descInput.setTextColor(UIHelper.TEXT_PRIMARY);
        descInput.setHintTextColor(UIHelper.TEXT_HINT);
        form.addView(descInput);

        // Amount
        EditText amountInput = new EditText(this);
        amountInput.setHint("\u91D1\u984D (NT$)");
        amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setTextColor(UIHelper.TEXT_PRIMARY);
        amountInput.setHintTextColor(UIHelper.TEXT_HINT);
        form.addView(amountInput);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle("\u65B0\u589E\u82B1\u8CBB")
                .setView(form)
                .setPositiveButton("\u65B0\u589E", (d, w) -> {
                    String amtStr = amountInput.getText().toString().trim();
                    if (amtStr.isEmpty()) {
                        Toast.makeText(this, "\u8ACB\u8F38\u5165\u91D1\u984D", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double amount = Double.parseDouble(amtStr);
                    int dayNum = 0;
                    String dayStr = dayInput.getText().toString().trim();
                    if (!dayStr.isEmpty()) dayNum = Integer.parseInt(dayStr);
                    String desc = descInput.getText().toString().trim();

                    db.insertExpense(tripId, dayNum, selectedCat[0], desc, amount);
                    AppLog.i("Travel", "\u65B0\u589E\u82B1\u8CBB: " + selectedCat[0] + " NT$" + amount);
                    Toast.makeText(this, "\u5DF2\u8A18\u9304", Toast.LENGTH_SHORT).show();
                    refreshBudget();
                })
                .setNegativeButton("\u53D6\u6D88", null)
                .show();
    }
}
