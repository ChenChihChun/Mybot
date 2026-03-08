package com.mybot.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HabitActivity extends AppCompatActivity {

    private HabitDbHelper dbHelper;
    private LinearLayout listContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        dbHelper = new HabitDbHelper(this);

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "\uD83D\uDCCA \u7FD2\u6163\u8FFD\u8E64");
        TextView backBtn = new TextView(this);
        backBtn.setText("\u2190");
        backBtn.setTextSize(22);
        backBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        backBtn.setPadding(0, 0, UIHelper.dp(this, 16), 0);
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn, 0);

        TextView addBtn = new TextView(this);
        addBtn.setText("\uFF0B");
        addBtn.setTextSize(24);
        addBtn.setTextColor(UIHelper.ACCENT_GREEN);
        addBtn.setOnClickListener(v -> startActivity(new Intent(this, AddHabitActivity.class)));
        topBar.addView(addBtn);
        root.addView(topBar);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 16);
        listContainer.setPadding(p, p, p, p);

        scrollView.addView(listContainer);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        listContainer.removeAllViews();

        String today = HabitDbHelper.todayStr();
        List<HabitDbHelper.Habit> habits = dbHelper.getAllHabits();

        if (habits.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("\u9084\u6C92\u6709\u7FD2\u6163\uFF0C\u9EDE\u53F3\u4E0A\u89D2 \uFF0B \u65B0\u589E");
            empty.setTextSize(15);
            empty.setTextColor(UIHelper.TEXT_HINT);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, UIHelper.dp(this, 60), 0, 0);
            listContainer.addView(empty);
            return;
        }

        Set<Long> completedIds = dbHelper.getCompletedHabitIds(today);
        int total = habits.size();
        int done = completedIds.size();

        // Progress card
        LinearLayout progressCard = UIHelper.card(this);
        TextView progressTitle = new TextView(this);
        progressTitle.setText(String.format(Locale.US, "\u4ECA\u65E5\u9032\u5EA6\uFF1A%d/%d \u5B8C\u6210", done, total));
        progressTitle.setTextSize(16);
        progressTitle.setTextColor(UIHelper.TEXT_PRIMARY);
        progressTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        progressCard.addView(progressTitle);

        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(total);
        progressBar.setProgress(done);
        progressBar.setProgressDrawable(createProgressDrawable());
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 8));
        pbLp.setMargins(0, UIHelper.dp(this, 10), 0, 0);
        progressBar.setLayoutParams(pbLp);
        progressCard.addView(progressBar);

        if (done == total && total > 0) {
            TextView congrats = new TextView(this);
            congrats.setText("\uD83C\uDF89 \u592A\u68D2\u4E86\uFF01\u4ECA\u5929\u5168\u90E8\u5B8C\u6210\uFF01");
            congrats.setTextSize(13);
            congrats.setTextColor(UIHelper.ACCENT_GREEN);
            congrats.setPadding(0, UIHelper.dp(this, 8), 0, 0);
            progressCard.addView(congrats);
        }
        listContainer.addView(progressCard);

        // Habit list
        for (HabitDbHelper.Habit habit : habits) {
            boolean completed = completedIds.contains(habit.id);
            listContainer.addView(buildHabitCard(habit, completed, today));
        }
    }

    private LinearLayout buildHabitCard(HabitDbHelper.Habit habit, boolean completed, String today) {
        LinearLayout card = UIHelper.card(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // Icon
        TextView iconView = new TextView(this);
        iconView.setText(habit.icon != null && !habit.icon.isEmpty() ? habit.icon : "\uD83D\uDCCB");
        iconView.setTextSize(26);
        iconView.setPadding(0, 0, UIHelper.dp(this, 12), 0);

        // Name + streak
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView nameView = new TextView(this);
        nameView.setText(habit.name);
        nameView.setTextSize(16);
        nameView.setTextColor(UIHelper.TEXT_PRIMARY);
        nameView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        textCol.addView(nameView);

        int streak = dbHelper.getStreak(habit.id);
        if (streak > 0) {
            TextView streakView = new TextView(this);
            streakView.setText("\uD83D\uDD25" + streak + "\u5929");
            streakView.setTextSize(12);
            streakView.setTextColor(UIHelper.ACCENT_ORANGE);
            streakView.setPadding(0, UIHelper.dp(this, 2), 0, 0);
            textCol.addView(streakView);
        }

        // Check circle
        TextView checkBtn = new TextView(this);
        int checkSize = UIHelper.dp(this, 36);
        checkBtn.setGravity(Gravity.CENTER);
        checkBtn.setTextSize(18);
        LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(checkSize, checkSize);
        checkBtn.setLayoutParams(checkLp);

        if (completed) {
            checkBtn.setText("\u2705");
            checkBtn.setBackground(UIHelper.roundRect(Color.argb(40, 102, 187, 106), 18, this));
        } else {
            checkBtn.setText("\u25CB");
            checkBtn.setTextColor(UIHelper.TEXT_HINT);
            checkBtn.setBackground(UIHelper.roundRect(UIHelper.BG_INPUT, 18, this));
        }

        checkBtn.setOnClickListener(v -> {
            boolean wasCompleted = completed;
            dbHelper.toggleLog(habit.id, today);
            AppLog.i("Habit", (wasCompleted ? "取消打卡" : "打卡") + ": " + habit.name);
            refreshList();
        });

        row.addView(iconView);
        row.addView(textCol);
        row.addView(checkBtn);
        card.addView(row);

        // Click for edit, long press for stats
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddHabitActivity.class);
            intent.putExtra("habit_id", habit.id);
            startActivity(intent);
        });

        card.setOnLongClickListener(v -> {
            showStatsDialog(habit);
            return true;
        });

        return card;
    }

    private void showStatsDialog(HabitDbHelper.Habit habit) {
        int streak = dbHelper.getStreak(habit.id);
        int longestStreak = dbHelper.getLongestStreak(habit.id);
        float rate7 = dbHelper.getCompletionRate(habit.id, 7);
        float rate30 = dbHelper.getCompletionRate(habit.id, 30);

        Calendar cal = Calendar.getInstance();
        Map<String, Boolean> monthMap = dbHelper.getCompletionMap(
                habit.id, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "\u8FD1 7 \u5929\u5B8C\u6210\u7387\uFF1A%.0f%%\n", rate7 * 100));
        sb.append(String.format(Locale.US, "\u8FD1 30 \u5929\u5B8C\u6210\u7387\uFF1A%.0f%%\n", rate30 * 100));
        sb.append(String.format(Locale.US, "\u76EE\u524D\u9023\u7E8C\uFF1A%d \u5929\n", streak));
        sb.append(String.format(Locale.US, "\u6700\u9577\u9023\u7E8C\uFF1A%d \u5929\n\n", longestStreak));

        // Mini calendar
        sb.append("\u672C\u6708\u8A18\u9304\uFF1A\n");
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int d = 1; d <= daysInMonth; d++) {
            String dateStr = String.format(Locale.US, "%04d-%02d-%02d", year, month, d);
            sb.append(monthMap.containsKey(dateStr) ? "\uD83D\uDFE2" : "\u26AA");
            if (d % 7 == 0) sb.append("\n");
        }

        new AlertDialog.Builder(this)
                .setTitle(habit.icon + " " + habit.name + " \u7D71\u8A08")
                .setMessage(sb.toString())
                .setPositiveButton("\u78BA\u5B9A", null)
                .setNegativeButton("\u522A\u9664\u7FD2\u6163", (d, w) -> {
                    new AlertDialog.Builder(this)
                            .setTitle("\u78BA\u5B9A\u522A\u9664\uFF1F")
                            .setMessage("\u6240\u6709\u8A18\u9304\u5C07\u4E00\u4F75\u522A\u9664")
                            .setPositiveButton("\u522A\u9664", (d2, w2) -> {
                                dbHelper.deleteHabit(habit.id);
                                AppLog.i("Habit", "刪除習慣: " + habit.name);
                                refreshList();
                            })
                            .setNegativeButton("\u53D6\u6D88", null)
                            .show();
                })
                .show();
    }

    private android.graphics.drawable.Drawable createProgressDrawable() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UIHelper.BG_INPUT);
        bg.setCornerRadius(UIHelper.dp(this, 4));

        GradientDrawable progress = new GradientDrawable();
        progress.setColor(UIHelper.ACCENT_GREEN);
        progress.setCornerRadius(UIHelper.dp(this, 4));

        android.graphics.drawable.ClipDrawable clip = new android.graphics.drawable.ClipDrawable(
                progress, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL);

        android.graphics.drawable.LayerDrawable layer = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{bg, clip});
        layer.setId(0, android.R.id.background);
        layer.setId(1, android.R.id.progress);
        return layer;
    }
}
