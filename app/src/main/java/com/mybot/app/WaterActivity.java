package com.mybot.app;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WaterActivity extends AppCompatActivity {

    private WaterDbHelper dbHelper;
    private LinearLayout contentContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        dbHelper = new WaterDbHelper(this);

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "\uD83D\uDCA7 \u559D\u6C34\u63D0\u9192");
        TextView backBtn = new TextView(this);
        backBtn.setText("\u2190");
        backBtn.setTextSize(22);
        backBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        backBtn.setPadding(0, 0, UIHelper.dp(this, 16), 0);
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn, 0);

        TextView settingsBtn = new TextView(this);
        settingsBtn.setText("\u2699\uFE0F");
        settingsBtn.setTextSize(22);
        settingsBtn.setOnClickListener(v -> showSettingsDialog());
        topBar.addView(settingsBtn);
        root.addView(topBar);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 16);
        contentContainer.setPadding(p, p, p, p);

        scrollView.addView(contentContainer);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUI();
    }

    private void refreshUI() {
        contentContainer.removeAllViews();

        int goal = WaterDbHelper.getGoal(this);
        int todayTotal = dbHelper.getTodayTotal();

        // Progress card
        LinearLayout progressCard = UIHelper.card(this);

        TextView progressIcon = new TextView(this);
        progressIcon.setText("\uD83D\uDCA7");
        progressIcon.setTextSize(40);
        progressIcon.setGravity(Gravity.CENTER);
        progressIcon.setPadding(0, 0, 0, UIHelper.dp(this, 8));
        progressCard.addView(progressIcon);

        TextView progressText = new TextView(this);
        progressText.setText(String.format(Locale.US, "%d / %d ml", todayTotal, goal));
        progressText.setTextSize(24);
        progressText.setTextColor(UIHelper.TEXT_PRIMARY);
        progressText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        progressText.setGravity(Gravity.CENTER);
        progressCard.addView(progressText);

        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(goal);
        progressBar.setProgress(Math.min(todayTotal, goal));
        progressBar.setProgressDrawable(createProgressDrawable());
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 10));
        pbLp.setMargins(0, UIHelper.dp(this, 10), 0, UIHelper.dp(this, 8));
        progressBar.setLayoutParams(pbLp);
        progressCard.addView(progressBar);

        // Motivational text
        TextView motivation = new TextView(this);
        float pct = goal > 0 ? (float) todayTotal / goal : 0;
        String motText;
        if (pct >= 1.0f) {
            motText = "\uD83C\uDF89 \u592A\u68D2\u4E86\uFF01\u4ECA\u5929\u5DF2\u9054\u6210\u76EE\u6A19\uFF01";
        } else if (pct >= 0.75f) {
            motText = "\uD83D\uDCAA \u5FEB\u5B8C\u6210\u4E86\uFF01\u518D\u52A0\u6CB9\uFF01";
        } else if (pct >= 0.5f) {
            motText = "\uD83D\uDE0A \u5DF2\u904E\u534A\uFF0C\u7E7C\u7E8C\u4FDD\u6301\uFF01";
        } else if (pct > 0) {
            motText = "\uD83D\uDCA7 \u7E7C\u7E8C\u52A0\u6CB9\uFF0C\u591A\u559D\u6C34\u5C0D\u8EAB\u9AD4\u597D\uFF01";
        } else {
            motText = "\u2615 \u4ECA\u5929\u9084\u6C92\u559D\u6C34\u5594\uFF0C\u4F86\u4E00\u676F\u5427\uFF01";
        }
        motivation.setText(motText);
        motivation.setTextSize(13);
        motivation.setTextColor(UIHelper.ACCENT_BLUE);
        motivation.setGravity(Gravity.CENTER);
        progressCard.addView(motivation);

        contentContainer.addView(progressCard);

        // Quick-add buttons
        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qrLp.setMargins(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8));
        quickRow.setLayoutParams(qrLp);

        int[] amounts = {150, 250, 500};
        String[] labels = {"+150", "+250", "+500"};
        for (int i = 0; i < amounts.length; i++) {
            final int amt = amounts[i];
            TextView btn = new TextView(this);
            btn.setText(labels[i] + "ml");
            btn.setTextSize(14);
            btn.setTextColor(UIHelper.ACCENT_BLUE);
            btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            btn.setGravity(Gravity.CENTER);
            btn.setBackground(UIHelper.roundRectStroke(UIHelper.BG_CARD, UIHelper.ACCENT_BLUE, 12, 1, this));
            int hp = UIHelper.dp(this, 16);
            int vp = UIHelper.dp(this, 12);
            btn.setPadding(hp, vp, hp, vp);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            btnLp.setMargins(UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4), 0);
            btn.setLayoutParams(btnLp);
            btn.setOnClickListener(v -> {
                dbHelper.addLog(amt);
                AppLog.i("Water", "記錄飲水: " + amt + "ml");
                refreshUI();
            });
            quickRow.addView(btn);
        }

        // Custom amount button
        TextView customBtn = new TextView(this);
        customBtn.setText("\u81EA\u8A02");
        customBtn.setTextSize(14);
        customBtn.setTextColor(UIHelper.TEXT_SECONDARY);
        customBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        customBtn.setGravity(Gravity.CENTER);
        customBtn.setBackground(UIHelper.roundRectStroke(UIHelper.BG_CARD, UIHelper.TEXT_HINT, 12, 1, this));
        int hp = UIHelper.dp(this, 16);
        int vp = UIHelper.dp(this, 12);
        customBtn.setPadding(hp, vp, hp, vp);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        cLp.setMargins(UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4), 0);
        customBtn.setLayoutParams(cLp);
        customBtn.setOnClickListener(v -> showCustomAmountDialog());
        quickRow.addView(customBtn);

        contentContainer.addView(quickRow);

        // 7-day history
        contentContainer.addView(buildHistoryCard());

        // Today's log list
        contentContainer.addView(buildTodayLogList());
    }

    private LinearLayout buildHistoryCard() {
        LinearLayout card = UIHelper.card(this);

        TextView title = new TextView(this);
        title.setText("\u8FD1 7 \u5929\u7D00\u9304");
        title.setTextSize(14);
        title.setTextColor(UIHelper.TEXT_PRIMARY);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setPadding(0, 0, 0, UIHelper.dp(this, 8));
        card.addView(title);

        int weekAvg = dbHelper.getWeekAverage();
        TextView avgText = new TextView(this);
        avgText.setText(String.format(Locale.US, "\u9031\u5E73\u5747\uFF1A%d ml", weekAvg));
        avgText.setTextSize(12);
        avgText.setTextColor(UIHelper.TEXT_SECONDARY);
        avgText.setPadding(0, 0, 0, UIHelper.dp(this, 10));
        card.addView(avgText);

        int goal = WaterDbHelper.getGoal(this);
        Map<String, Integer> daily = dbHelper.getDailyTotals(7);

        // Simple bar chart
        LinearLayout barRow = new LinearLayout(this);
        barRow.setOrientation(LinearLayout.HORIZONTAL);
        barRow.setGravity(Gravity.BOTTOM);
        int maxHeight = UIHelper.dp(this, 80);

        for (Map.Entry<String, Integer> entry : daily.entrySet()) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            colLp.setMargins(UIHelper.dp(this, 2), 0, UIHelper.dp(this, 2), 0);
            col.setLayoutParams(colLp);

            // Amount label
            int val = entry.getValue();
            TextView valLabel = new TextView(this);
            valLabel.setText(val > 0 ? String.valueOf(val) : "");
            valLabel.setTextSize(9);
            valLabel.setTextColor(UIHelper.TEXT_HINT);
            valLabel.setGravity(Gravity.CENTER);
            col.addView(valLabel);

            // Bar
            View bar = new View(this);
            float ratio = goal > 0 ? Math.min((float) val / goal, 1.0f) : 0;
            int barHeight = Math.max((int) (maxHeight * ratio), UIHelper.dp(this, 4));
            int barColor = val >= goal ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_BLUE;
            bar.setBackground(UIHelper.roundRect(barColor, 4, this));
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, barHeight);
            barLp.setMargins(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4));
            bar.setLayoutParams(barLp);
            col.addView(bar);

            // Day label
            String date = entry.getKey();
            TextView dayLabel = new TextView(this);
            dayLabel.setText(date.substring(date.length() - 2));
            dayLabel.setTextSize(10);
            dayLabel.setTextColor(UIHelper.TEXT_HINT);
            dayLabel.setGravity(Gravity.CENTER);
            col.addView(dayLabel);

            barRow.addView(col);
        }

        card.addView(barRow);
        return card;
    }

    private LinearLayout buildTodayLogList() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 16));

        TextView header = new TextView(this);
        header.setText("\u4ECA\u65E5\u8A18\u9304");
        header.setTextSize(14);
        header.setTextColor(UIHelper.TEXT_SECONDARY);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        header.setPadding(UIHelper.dp(this, 4), UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8));
        section.addView(header);

        List<WaterDbHelper.WaterLog> logs = dbHelper.getTodayLogs();
        if (logs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("\u4ECA\u5929\u9084\u6C92\u6709\u8A18\u9304");
            empty.setTextSize(13);
            empty.setTextColor(UIHelper.TEXT_HINT);
            empty.setPadding(UIHelper.dp(this, 4), UIHelper.dp(this, 4), 0, 0);
            section.addView(empty);
        } else {
            for (WaterDbHelper.WaterLog log : logs) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 10, this));
                int rp = UIHelper.dp(this, 12);
                row.setPadding(rp, UIHelper.dp(this, 10), rp, UIHelper.dp(this, 10));
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, UIHelper.dp(this, 3), 0, UIHelper.dp(this, 3));
                row.setLayoutParams(rowLp);

                TextView timeView = new TextView(this);
                timeView.setText(log.time);
                timeView.setTextSize(14);
                timeView.setTextColor(UIHelper.TEXT_SECONDARY);
                timeView.setPadding(0, 0, UIHelper.dp(this, 16), 0);

                TextView amtView = new TextView(this);
                amtView.setText(log.amountMl + " ml");
                amtView.setTextSize(14);
                amtView.setTextColor(UIHelper.TEXT_PRIMARY);
                amtView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                TextView delBtn = new TextView(this);
                delBtn.setText("\u2716");
                delBtn.setTextSize(14);
                delBtn.setTextColor(UIHelper.ACCENT_RED);
                delBtn.setOnClickListener(v -> {
                    dbHelper.deleteLog(log.id);
                    refreshUI();
                });

                row.addView(timeView);
                row.addView(amtView);
                row.addView(delBtn);
                section.addView(row);
            }
        }

        return section;
    }

    private void showCustomAmountDialog() {
        EditText input = UIHelper.styledInput(this, "\u8F38\u5165 ml \u6578");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        new AlertDialog.Builder(this)
                .setTitle("\u81EA\u8A02\u6C34\u91CF")
                .setView(input)
                .setPositiveButton("\u65B0\u589E", (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) {
                        try {
                            int amt = Integer.parseInt(text);
                            if (amt > 0 && amt <= 5000) {
                                dbHelper.addLog(amt);
                                AppLog.i("Water", "記錄飲水(自訂): " + amt + "ml");
                                refreshUI();
                            } else {
                                Toast.makeText(this, "\u8ACB\u8F38\u5165 1-5000 ml", Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "\u8ACB\u8F38\u5165\u6709\u6548\u6578\u5B57", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("\u53D6\u6D88", null)
                .show();
    }

    private void showSettingsDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 20);
        layout.setPadding(p, p, p, p);

        // Goal
        TextView goalLabel = new TextView(this);
        goalLabel.setText("\u6BCF\u65E5\u76EE\u6A19 (ml)");
        goalLabel.setTextSize(14);
        goalLabel.setTextColor(UIHelper.TEXT_PRIMARY);
        layout.addView(goalLabel);

        EditText goalInput = new EditText(this);
        goalInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        goalInput.setText(String.valueOf(WaterDbHelper.getGoal(this)));
        goalInput.setTextColor(UIHelper.TEXT_PRIMARY);
        layout.addView(goalInput);

        // Reminder toggle
        LinearLayout remindRow = new LinearLayout(this);
        remindRow.setOrientation(LinearLayout.HORIZONTAL);
        remindRow.setGravity(Gravity.CENTER_VERTICAL);
        remindRow.setPadding(0, UIHelper.dp(this, 12), 0, UIHelper.dp(this, 4));

        TextView remindLabel = new TextView(this);
        remindLabel.setText("\u5B9A\u6642\u63D0\u9192");
        remindLabel.setTextSize(14);
        remindLabel.setTextColor(UIHelper.TEXT_PRIMARY);
        remindLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Switch remindSwitch = new Switch(this);
        remindSwitch.setChecked(WaterDbHelper.isRemindEnabled(this));

        remindRow.addView(remindLabel);
        remindRow.addView(remindSwitch);
        layout.addView(remindRow);

        // Interval
        TextView intervalLabel = new TextView(this);
        intervalLabel.setText(String.format(Locale.US, "\u63D0\u9192\u9593\u9694\uFF1A%d \u5206\u9418", WaterDbHelper.getRemindInterval(this)));
        intervalLabel.setTextSize(13);
        intervalLabel.setTextColor(UIHelper.TEXT_SECONDARY);
        layout.addView(intervalLabel);

        EditText intervalInput = new EditText(this);
        intervalInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        intervalInput.setText(String.valueOf(WaterDbHelper.getRemindInterval(this)));
        intervalInput.setTextColor(UIHelper.TEXT_PRIMARY);
        intervalInput.setHint("\u5206\u9418");
        layout.addView(intervalInput);

        // Active hours
        TextView hoursLabel = new TextView(this);
        hoursLabel.setText(String.format(Locale.US, "\u63D0\u9192\u6642\u6BB5\uFF1A%d:00 - %d:00",
                WaterDbHelper.getRemindStartHour(this), WaterDbHelper.getRemindEndHour(this)));
        hoursLabel.setTextSize(13);
        hoursLabel.setTextColor(UIHelper.TEXT_SECONDARY);
        hoursLabel.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        layout.addView(hoursLabel);

        new AlertDialog.Builder(this)
                .setTitle("\u559D\u6C34\u8A2D\u5B9A")
                .setView(layout)
                .setPositiveButton("\u5132\u5B58", (d, w) -> {
                    try {
                        int goal = Integer.parseInt(goalInput.getText().toString().trim());
                        if (goal > 0) WaterDbHelper.setGoal(this, goal);
                    } catch (NumberFormatException ignored) {}

                    try {
                        int interval = Integer.parseInt(intervalInput.getText().toString().trim());
                        if (interval >= 15) WaterDbHelper.setRemindInterval(this, interval);
                    } catch (NumberFormatException ignored) {}

                    boolean enabled = remindSwitch.isChecked();
                    WaterDbHelper.setRemindEnabled(this, enabled);
                    AppLog.i("Water", "設定變更: 目標=" + WaterDbHelper.getGoal(this) + "ml 提醒=" + enabled);
                    if (enabled) {
                        ReminderHelper.scheduleWaterReminder(this);
                    } else {
                        ReminderHelper.cancelWaterReminder(this);
                    }
                    refreshUI();
                })
                .setNegativeButton("\u53D6\u6D88", null)
                .show();
    }

    private android.graphics.drawable.Drawable createProgressDrawable() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UIHelper.BG_INPUT);
        bg.setCornerRadius(UIHelper.dp(this, 5));

        GradientDrawable progress = new GradientDrawable();
        progress.setColor(UIHelper.ACCENT_BLUE);
        progress.setCornerRadius(UIHelper.dp(this, 5));

        android.graphics.drawable.ClipDrawable clip = new android.graphics.drawable.ClipDrawable(
                progress, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL);

        android.graphics.drawable.LayerDrawable layer = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{bg, clip});
        layer.setId(0, android.R.id.background);
        layer.setId(1, android.R.id.progress);
        return layer;
    }
}
