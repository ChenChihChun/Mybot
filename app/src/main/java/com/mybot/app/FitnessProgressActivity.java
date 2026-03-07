package com.mybot.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FitnessProgressActivity extends AppCompatActivity {

    private FitnessDbHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        dbHelper = new FitnessDbHelper(this);

        LinearLayout root = UIHelper.pageRoot(this);

        LinearLayout topBar = UIHelper.topBar(this, "運動追蹤");
        Button closeBtn = UIHelper.smallButton(this, "X", UIHelper.TEXT_SECONDARY);
        closeBtn.setOnClickListener(v -> finish());
        topBar.addView(closeBtn);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 16);
        content.setPadding(p, UIHelper.dp(this, 12), p, p);

        // Stats card
        content.addView(UIHelper.sectionHeader(this, "運動統計"));
        LinearLayout statsCard = UIHelper.card(this);

        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setGravity(Gravity.CENTER);

        int streak = dbHelper.getStreak();
        int total = dbHelper.getTotalWorkouts();

        statsRow.addView(statItem("連續天數", streak + " 天", UIHelper.ACCENT_ORANGE));
        statsRow.addView(statItem("累計運動", total + " 次", UIHelper.ACCENT_BLUE));
        statsRow.addView(statItem("本週", getThisWeekCount() + " / 7", UIHelper.ACCENT_GREEN));

        statsCard.addView(statsRow);
        content.addView(statsCard);

        // Weight tracking
        content.addView(UIHelper.sectionHeader(this, "體重追蹤"));

        LinearLayout weightInputCard = UIHelper.card(this);
        LinearLayout weightRow = new LinearLayout(this);
        weightRow.setOrientation(LinearLayout.HORIZONTAL);
        weightRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText weightInput = new EditText(this);
        weightInput.setHint("輸入今日體重 (kg)");
        weightInput.setHintTextColor(UIHelper.TEXT_HINT);
        weightInput.setTextColor(UIHelper.TEXT_PRIMARY);
        weightInput.setTextSize(16);
        weightInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        weightInput.setBackground(UIHelper.roundRectStroke(UIHelper.BG_INPUT, Color.parseColor("#2E4050"), 12, 1, this));
        int wp = UIHelper.dp(this, 14);
        weightInput.setPadding(wp, UIHelper.dp(this, 10), wp, UIHelper.dp(this, 10));
        weightInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button recordBtn = UIHelper.smallButton(this, "記錄", UIHelper.ACCENT_GREEN);
        LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rbLp.setMargins(UIHelper.dp(this, 10), 0, 0, 0);
        recordBtn.setLayoutParams(rbLp);
        recordBtn.setOnClickListener(v -> {
            String wStr = weightInput.getText().toString().trim();
            if (wStr.isEmpty()) return;
            try {
                double w = Double.parseDouble(wStr);
                dbHelper.recordWeight(w);
                weightInput.setText("");
                Toast.makeText(this, "已記錄體重 " + w + " kg", Toast.LENGTH_SHORT).show();
                recreate();
            } catch (Exception e) {
                Toast.makeText(this, "格式錯誤", Toast.LENGTH_SHORT).show();
            }
        });

        weightRow.addView(weightInput);
        weightRow.addView(recordBtn);
        weightInputCard.addView(weightRow);
        content.addView(weightInputCard);

        // Weight history
        List<FitnessDbHelper.WeightRecord> weights = dbHelper.getWeightHistory(30);
        if (!weights.isEmpty()) {
            LinearLayout weightCard = UIHelper.card(this);

            // Min/max/current
            double min = Double.MAX_VALUE, max = 0, current = weights.get(0).weightKg;
            for (FitnessDbHelper.WeightRecord wr : weights) {
                if (wr.weightKg < min) min = wr.weightKg;
                if (wr.weightKg > max) max = wr.weightKg;
            }
            double change = weights.size() > 1 ? current - weights.get(weights.size() - 1).weightKg : 0;

            LinearLayout wStatsRow = new LinearLayout(this);
            wStatsRow.setOrientation(LinearLayout.HORIZONTAL);
            wStatsRow.setGravity(Gravity.CENTER);

            wStatsRow.addView(statItem("目前", String.format(Locale.getDefault(), "%.1f", current), UIHelper.TEXT_PRIMARY));
            wStatsRow.addView(statItem("最低", String.format(Locale.getDefault(), "%.1f", min), UIHelper.ACCENT_GREEN));
            wStatsRow.addView(statItem("最高", String.format(Locale.getDefault(), "%.1f", max), UIHelper.ACCENT_RED));
            String changeStr = (change >= 0 ? "+" : "") + String.format(Locale.getDefault(), "%.1f", change);
            int changeColor = change > 0 ? UIHelper.ACCENT_RED : change < 0 ? UIHelper.ACCENT_GREEN : UIHelper.TEXT_SECONDARY;
            wStatsRow.addView(statItem("變化", changeStr, changeColor));

            weightCard.addView(wStatsRow);
            weightCard.addView(UIHelper.divider(this));

            // Simple text chart
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd", Locale.getDefault());
            double range = max - min;
            if (range < 0.1) range = 1;

            for (int i = 0; i < Math.min(weights.size(), 14); i++) {
                FitnessDbHelper.WeightRecord wr = weights.get(i);
                LinearLayout wRow = new LinearLayout(this);
                wRow.setOrientation(LinearLayout.HORIZONTAL);
                wRow.setGravity(Gravity.CENTER_VERTICAL);
                wRow.setPadding(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4));

                TextView dateView = new TextView(this);
                dateView.setText(sdf.format(new Date(wr.recordedAt)));
                dateView.setTextSize(12);
                dateView.setTextColor(UIHelper.TEXT_SECONDARY);
                dateView.setLayoutParams(new LinearLayout.LayoutParams(
                        UIHelper.dp(this, 50), ViewGroup.LayoutParams.WRAP_CONTENT));

                // Bar
                double pct = (wr.weightKg - min) / range;
                LinearLayout barBg = new LinearLayout(this);
                barBg.setBackground(UIHelper.roundRect(UIHelper.BG_CARD_ALT, 4, this));
                LinearLayout.LayoutParams barBgLp = new LinearLayout.LayoutParams(
                        0, UIHelper.dp(this, 8), 1);
                barBgLp.setMargins(UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8), 0);
                barBg.setLayoutParams(barBgLp);

                android.view.View bar = new android.view.View(this);
                int barColor = pct > 0.7 ? UIHelper.ACCENT_RED : pct > 0.3 ? UIHelper.ACCENT_ORANGE : UIHelper.ACCENT_GREEN;
                bar.setBackground(UIHelper.roundRect(barColor, 4, this));
                bar.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, (float) Math.max(pct, 0.05)));

                android.view.View spacer = new android.view.View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, (float) (1 - Math.max(pct, 0.05))));

                barBg.addView(bar);
                barBg.addView(spacer);

                TextView valueView = new TextView(this);
                valueView.setText(String.format(Locale.getDefault(), "%.1f", wr.weightKg));
                valueView.setTextSize(12);
                valueView.setTextColor(UIHelper.TEXT_PRIMARY);
                valueView.setLayoutParams(new LinearLayout.LayoutParams(
                        UIHelper.dp(this, 45), ViewGroup.LayoutParams.WRAP_CONTENT));
                valueView.setGravity(Gravity.END);

                wRow.addView(dateView);
                wRow.addView(barBg);
                wRow.addView(valueView);
                weightCard.addView(wRow);
            }

            content.addView(weightCard);
        }

        // Recent workout logs
        content.addView(UIHelper.sectionHeader(this, "最近運動紀錄"));
        List<FitnessDbHelper.WorkoutLog> logs = dbHelper.getRecentLogs(14);
        if (logs.isEmpty()) {
            LinearLayout emptyCard = UIHelper.card(this);
            TextView emptyMsg = new TextView(this);
            emptyMsg.setText("尚無運動紀錄");
            emptyMsg.setTextSize(14);
            emptyMsg.setTextColor(UIHelper.TEXT_HINT);
            emptyMsg.setGravity(Gravity.CENTER);
            emptyCard.addView(emptyMsg);
            content.addView(emptyCard);
        } else {
            for (FitnessDbHelper.WorkoutLog log : logs) {
                LinearLayout logCard = UIHelper.card(this);
                LinearLayout logRow = new LinearLayout(this);
                logRow.setOrientation(LinearLayout.HORIZONTAL);
                logRow.setGravity(Gravity.CENTER_VERTICAL);

                TextView dateView = new TextView(this);
                dateView.setText(log.dateStr);
                dateView.setTextSize(14);
                dateView.setTextColor(UIHelper.TEXT_PRIMARY);
                dateView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                dateView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                TextView doneView = new TextView(this);
                doneView.setText(log.exercisesDone + "/" + log.totalExercises + " 完成");
                doneView.setTextSize(13);
                doneView.setTextColor(log.exercisesDone >= log.totalExercises
                        ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_ORANGE);

                logRow.addView(dateView);
                logRow.addView(doneView);
                logCard.addView(logRow);
                content.addView(logCard);
            }
        }

        scrollView.addView(content);
        root.addView(topBar);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private int getThisWeekCount() {
        List<FitnessDbHelper.WorkoutLog> logs = dbHelper.getRecentLogs(7);
        int count = 0;
        for (FitnessDbHelper.WorkoutLog log : logs) {
            if (log.exercisesDone > 0) count++;
        }
        return count;
    }

    private LinearLayout statItem(String label, String value, int color) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(18);
        valueView.setTextColor(color);
        valueView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        valueView.setGravity(Gravity.CENTER);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(11);
        labelView.setTextColor(UIHelper.TEXT_SECONDARY);
        labelView.setGravity(Gravity.CENTER);
        labelView.setPadding(0, UIHelper.dp(this, 4), 0, 0);

        item.addView(valueView);
        item.addView(labelView);
        return item;
    }
}
