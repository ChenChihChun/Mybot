package com.mybot.app;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

public class CronActivity extends AppCompatActivity {

    private LinearLayout listContainer;
    private TextView summaryText;
    private TextView crondStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "\u23F0 \u6392\u7A0B\u7BA1\u7406");
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

        crondStatus = new TextView(this);
        crondStatus.setTextSize(13);
        crondStatus.setPadding(0, UIHelper.dp(this, 4), 0, 0);
        summaryCard.addView(crondStatus);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadJobs();
    }

    private void loadJobs() {
        listContainer.removeAllViews();
        summaryText.setText("載入中...");
        crondStatus.setText("");

        BridgeClient.getCronJobs((data, error) -> {
            if (error != null) {
                summaryText.setText("載入失敗");
                crondStatus.setText(error);
                crondStatus.setTextColor(0xFFFF5252);
                return;
            }
            if (data == null) return;

            boolean crondRunning = data.optBoolean("crond_running", false);
            crondStatus.setText("crond: " + (crondRunning ? "\u2705 \u904B\u884C\u4E2D" : "\u274C \u672A\u904B\u884C"));
            crondStatus.setTextColor(crondRunning ? 0xFF66BB6A : 0xFFFF5252);

            JSONArray jobs = data.optJSONArray("jobs");
            if (jobs == null || jobs.length() == 0) {
                summaryText.setText("沒有排程");
                return;
            }

            int total = jobs.length();
            int enabled = 0;
            for (int i = 0; i < total; i++) {
                JSONObject job = jobs.optJSONObject(i);
                if (job != null && job.optBoolean("enabled", true)) enabled++;
            }
            summaryText.setText(total + " \u500B\u6392\u7A0B \u00B7 " + enabled + " \u555F\u7528 \u00B7 " + (total - enabled) + " \u505C\u7528");

            for (int i = 0; i < jobs.length(); i++) {
                JSONObject job = jobs.optJSONObject(i);
                if (job != null) {
                    listContainer.addView(buildJobCard(job));
                }
            }
        });
    }

    private LinearLayout buildJobCard(JSONObject job) {
        String id = job.optString("id");
        String name = job.optString("name");
        String scheduleDisplay = job.optString("schedule_display");
        String schedule = job.optString("schedule");
        boolean enabled = job.optBoolean("enabled", true);
        String status = job.optString("status", "unknown");
        String lastRun = job.optString("last_run", null);
        String lastError = job.optString("last_error", null);

        LinearLayout card = UIHelper.card(this);
        if (!enabled) card.setAlpha(0.5f);

        // Row 1: status light + name + toggle
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        // Status light
        View light = new View(this);
        int lightSize = UIHelper.dp(this, 12);
        LinearLayout.LayoutParams lightLp = new LinearLayout.LayoutParams(lightSize, lightSize);
        lightLp.setMargins(0, 0, UIHelper.dp(this, 10), 0);
        light.setLayoutParams(lightLp);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(statusColor(status));
        light.setBackground(dot);
        row1.addView(light);

        // Name
        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextSize(15);
        nameView.setTextColor(UIHelper.TEXT_PRIMARY);
        nameView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        nameView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row1.addView(nameView);

        // Toggle switch
        Switch toggle = new Switch(this);
        toggle.setChecked(enabled);
        toggle.setOnCheckedChangeListener((btn, isChecked) -> {
            BridgeClient.toggleCronJob(id, isChecked, (result, err) -> {
                if (err != null) {
                    Toast.makeText(this, "操作失敗: " + err, Toast.LENGTH_SHORT).show();
                    toggle.setChecked(!isChecked);
                } else {
                    card.setAlpha(isChecked ? 1f : 0.5f);
                }
            });
        });
        row1.addView(toggle);
        card.addView(row1);

        // Row 2: schedule + edit button
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        row2.setPadding(0, UIHelper.dp(this, 6), 0, 0);

        TextView schedView = new TextView(this);
        schedView.setText("\u23F0 " + scheduleDisplay);
        schedView.setTextSize(13);
        schedView.setTextColor(UIHelper.TEXT_SECONDARY);
        schedView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row2.addView(schedView);

        TextView editBtn = new TextView(this);
        editBtn.setText("\u270F\uFE0F");
        editBtn.setTextSize(18);
        editBtn.setPadding(UIHelper.dp(this, 12), 0, 0, 0);
        editBtn.setOnClickListener(v -> showScheduleDialog(id, name, schedule));
        row2.addView(editBtn);

        card.addView(row2);

        // Row 3: last run info
        TextView lastRunView = new TextView(this);
        lastRunView.setTextSize(12);
        lastRunView.setPadding(0, UIHelper.dp(this, 4), 0, 0);
        if (lastRun != null && !lastRun.equals("null") && !lastRun.isEmpty()) {
            lastRunView.setText("\u6700\u5F8C\u57F7\u884C: " + lastRun);
            lastRunView.setTextColor(UIHelper.TEXT_HINT);
        } else {
            lastRunView.setText("\u6700\u5F8C\u57F7\u884C: \u7121\u8A18\u9304");
            lastRunView.setTextColor(UIHelper.TEXT_HINT);
        }
        card.addView(lastRunView);

        // Row 4: error info (if any)
        if (lastError != null && !lastError.equals("null") && !lastError.isEmpty()) {
            TextView errorView = new TextView(this);
            errorView.setText("\u26A0 " + lastError);
            errorView.setTextSize(11);
            errorView.setTextColor(0xFFFF5252);
            errorView.setPadding(0, UIHelper.dp(this, 2), 0, 0);
            errorView.setMaxLines(2);
            card.addView(errorView);
        }

        return card;
    }

    private int statusColor(String status) {
        switch (status) {
            case "ok":       return 0xFF66BB6A;  // green
            case "error":    return 0xFFFF5252;  // red
            case "stale":    return 0xFFFFCA28;  // yellow
            case "disabled": return 0xFF616161;  // gray
            default:         return 0xFF9E9E9E;  // light gray
        }
    }

    private void showScheduleDialog(String jobId, String jobName, String currentSchedule) {
        String[] parts = currentSchedule.split("\\s+");
        if (parts.length < 5) return;

        int curMinute = 0, curHour = 0;
        try {
            curMinute = Integer.parseInt(parts[0]);
            curHour = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ignored) {}

        String curDow = parts[4];

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(UIHelper.dp(this, 24), UIHelper.dp(this, 16),
                UIHelper.dp(this, 24), UIHelper.dp(this, 8));
        dialogLayout.setBackgroundColor(UIHelper.BG_CARD);

        // Time pickers row
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER);

        NumberPicker hourPicker = new NumberPicker(this);
        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        hourPicker.setValue(curHour);
        hourPicker.setWrapSelectorWheel(true);

        TextView colon = new TextView(this);
        colon.setText(" : ");
        colon.setTextSize(24);
        colon.setTextColor(UIHelper.TEXT_PRIMARY);

        NumberPicker minutePicker = new NumberPicker(this);
        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        minutePicker.setValue(curMinute);
        minutePicker.setWrapSelectorWheel(true);

        timeRow.addView(hourPicker);
        timeRow.addView(colon);
        timeRow.addView(minutePicker);
        dialogLayout.addView(timeRow);

        // Day of week checkboxes
        TextView dowLabel = new TextView(this);
        dowLabel.setText("\u9078\u64C7\u57F7\u884C\u65E5");
        dowLabel.setTextSize(14);
        dowLabel.setTextColor(UIHelper.TEXT_SECONDARY);
        dowLabel.setPadding(0, UIHelper.dp(this, 16), 0, UIHelper.dp(this, 8));
        dialogLayout.addView(dowLabel);

        String[] dayNames = {"\u65E5", "\u4E00", "\u4E8C", "\u4E09", "\u56DB", "\u4E94", "\u516D"};
        LinearLayout dowRow = new LinearLayout(this);
        dowRow.setOrientation(LinearLayout.HORIZONTAL);
        dowRow.setGravity(Gravity.CENTER);
        CheckBox[] checkboxes = new CheckBox[7];

        boolean[] selectedDays = parseDow(curDow);

        for (int i = 0; i < 7; i++) {
            CheckBox cb = new CheckBox(this);
            cb.setText(dayNames[i]);
            cb.setTextColor(UIHelper.TEXT_PRIMARY);
            cb.setTextSize(12);
            cb.setChecked(selectedDays[i]);
            checkboxes[i] = cb;
            dowRow.addView(cb);
        }
        dialogLayout.addView(dowRow);

        // Preview text
        TextView preview = new TextView(this);
        preview.setTextSize(13);
        preview.setTextColor(UIHelper.ACCENT_BLUE);
        preview.setPadding(0, UIHelper.dp(this, 12), 0, 0);
        dialogLayout.addView(preview);

        Runnable updatePreview = () -> {
            String newSchedule = buildSchedule(hourPicker.getValue(), minutePicker.getValue(), checkboxes);
            preview.setText("\u9810\u89BD: " + cronToDisplay(newSchedule));
        };
        hourPicker.setOnValueChangedListener((p, o, n) -> updatePreview.run());
        minutePicker.setOnValueChangedListener((p, o, n) -> updatePreview.run());
        for (CheckBox cb : checkboxes) cb.setOnCheckedChangeListener((b, c) -> updatePreview.run());
        updatePreview.run();

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(jobName + " \u6392\u7A0B\u8A2D\u5B9A")
                .setView(dialogLayout)
                .setPositiveButton("\u78BA\u8A8D", (d, w) -> {
                    String newSchedule = buildSchedule(hourPicker.getValue(), minutePicker.getValue(), checkboxes);
                    BridgeClient.updateCronSchedule(jobId, newSchedule, (result, err) -> {
                        if (err != null) {
                            Toast.makeText(this, "更新失敗: " + err, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "排程已更新", Toast.LENGTH_SHORT).show();
                            loadJobs();
                        }
                    });
                })
                .setNegativeButton("\u53D6\u6D88", null)
                .show();
    }

    private boolean[] parseDow(String dow) {
        boolean[] days = new boolean[7];
        if (dow.equals("*")) {
            for (int i = 0; i < 7; i++) days[i] = true;
        } else if (dow.equals("1-5")) {
            for (int i = 1; i <= 5; i++) days[i] = true;
        } else if (dow.equals("0,6") || dow.equals("6,0")) {
            days[0] = true; days[6] = true;
        } else {
            for (String part : dow.split(",")) {
                part = part.trim();
                if (part.contains("-")) {
                    String[] range = part.split("-");
                    try {
                        int from = Integer.parseInt(range[0]);
                        int to = Integer.parseInt(range[1]);
                        for (int i = from; i <= to; i++) {
                            if (i >= 0 && i < 7) days[i] = true;
                        }
                    } catch (NumberFormatException ignored) {}
                } else {
                    try {
                        int d = Integer.parseInt(part);
                        if (d >= 0 && d < 7) days[d] = true;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return days;
    }

    private String buildSchedule(int hour, int minute, CheckBox[] checkboxes) {
        boolean allChecked = true;
        boolean noneChecked = true;
        for (CheckBox cb : checkboxes) {
            if (cb.isChecked()) noneChecked = false;
            else allChecked = false;
        }

        String dow;
        if (allChecked || noneChecked) {
            dow = "*";
        } else {
            // Check for weekdays pattern
            boolean weekdays = true;
            for (int i = 1; i <= 5; i++) {
                if (!checkboxes[i].isChecked()) weekdays = false;
            }
            if (weekdays && !checkboxes[0].isChecked() && !checkboxes[6].isChecked()) {
                dow = "1-5";
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 7; i++) {
                    if (checkboxes[i].isChecked()) {
                        if (sb.length() > 0) sb.append(",");
                        sb.append(i);
                    }
                }
                dow = sb.toString();
            }
        }

        return minute + " " + hour + " * * " + dow;
    }

    private String cronToDisplay(String schedule) {
        String[] parts = schedule.split("\\s+");
        if (parts.length < 5) return schedule;
        String time = String.format("%02d:%02d", Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
        String dow = parts[4];
        if (dow.equals("*")) return "\u6BCF\u5929 " + time;
        if (dow.equals("1-5")) return "\u9031\u4E00~\u4E94 " + time;
        if (dow.equals("0")) return "\u6BCF\u9031\u65E5 " + time;
        String[] dayNames = {"\u65E5", "\u4E00", "\u4E8C", "\u4E09", "\u56DB", "\u4E94", "\u516D"};
        StringBuilder sb = new StringBuilder("\u9031");
        for (String d : dow.split(",")) {
            try {
                int idx = Integer.parseInt(d.trim());
                if (idx >= 0 && idx < 7) sb.append(dayNames[idx]);
            } catch (NumberFormatException ignored) {}
        }
        return sb + " " + time;
    }
}
