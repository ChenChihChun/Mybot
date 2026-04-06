package com.mybot.app;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

public class FitnessActivity extends AppCompatActivity {

    private FitnessDbHelper dbHelper;
    private LinearLayout contentArea;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        dbHelper = new FitnessDbHelper(this);

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "健身教練");

        Button profileBtn = UIHelper.smallButton(this, "個人檔案", UIHelper.ACCENT_BLUE);
        profileBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, FitnessProfileActivity.class));
        });

        Button progressBtn = UIHelper.smallButton(this, "追蹤", UIHelper.ACCENT_GREEN);
        LinearLayout.LayoutParams pgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pgLp.setMargins(UIHelper.dp(this, 8), 0, 0, 0);
        progressBtn.setLayoutParams(pgLp);
        progressBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, FitnessProgressActivity.class));
        });

        topBar.addView(profileBtn);
        topBar.addView(progressBtn);

        // Scrollable content
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 16);
        contentArea.setPadding(p, UIHelper.dp(this, 12), p, p);
        scrollView.addView(contentArea);

        root.addView(topBar);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildUI();
        if (generating && statusText != null) {
            statusText.setText("AI 運動計畫生成中，請稍候...");
            statusText.setTextColor(UIHelper.ACCENT_BLUE);
            statusText.setVisibility(View.VISIBLE);
        }
    }

    private void buildUI() {
        contentArea.removeAllViews();

        FitnessDbHelper.Profile profile = dbHelper.getProfile();

        // Profile check
        if (profile == null) {
            LinearLayout emptyCard = UIHelper.card(this);
            TextView emptyMsg = new TextView(this);
            emptyMsg.setText("請先設定個人檔案\n輸入身高、體重、運動目標");
            emptyMsg.setTextSize(16);
            emptyMsg.setTextColor(UIHelper.TEXT_SECONDARY);
            emptyMsg.setGravity(Gravity.CENTER);
            emptyMsg.setPadding(0, UIHelper.dp(this, 20), 0, UIHelper.dp(this, 12));
            emptyCard.addView(emptyMsg);

            Button setupBtn = UIHelper.primaryButton(this, "設定個人檔案");
            setupBtn.setOnClickListener(v -> startActivity(new Intent(this, FitnessProfileActivity.class)));
            emptyCard.addView(setupBtn);

            contentArea.addView(emptyCard);
            return;
        }

        // Profile summary card
        LinearLayout profileCard = UIHelper.card(this);
        LinearLayout profileRow = new LinearLayout(this);
        profileRow.setOrientation(LinearLayout.HORIZONTAL);
        profileRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView profileInfo = new TextView(this);
        profileInfo.setText(String.format(Locale.getDefault(),
                "%.0fcm / %.1fkg  BMI %.1f", profile.heightCm, profile.weightKg, profile.getBmi()));
        profileInfo.setTextSize(13);
        profileInfo.setTextColor(UIHelper.TEXT_SECONDARY);
        profileInfo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView goalBadge = UIHelper.statusBadge(this, profile.getGoalLabel(), UIHelper.ACCENT_GREEN);
        TextView levelBadge = UIHelper.statusBadge(this, profile.getLevelLabel(), UIHelper.ACCENT_BLUE);
        LinearLayout.LayoutParams lbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lbLp.setMargins(UIHelper.dp(this, 6), 0, 0, 0);
        levelBadge.setLayoutParams(lbLp);

        profileRow.addView(profileInfo);
        profileRow.addView(goalBadge);
        profileRow.addView(levelBadge);
        profileCard.addView(profileRow);

        if (profile.customGoal != null && !profile.customGoal.isEmpty()) {
            TextView customGoalText = new TextView(this);
            customGoalText.setText("目標: " + profile.customGoal);
            customGoalText.setTextSize(12);
            customGoalText.setTextColor(UIHelper.TEXT_SECONDARY);
            customGoalText.setPadding(0, UIHelper.dp(this, 6), 0, 0);
            profileCard.addView(customGoalText);
        }

        contentArea.addView(profileCard);

        // Stats card
        LinearLayout statsCard = UIHelper.card(this);
        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setGravity(Gravity.CENTER);

        int streak = dbHelper.getStreak();
        int totalWorkouts = dbHelper.getTotalWorkouts();

        statsRow.addView(statItem("連續運動", streak + " 天", UIHelper.ACCENT_ORANGE));
        statsRow.addView(statItem("累計運動", totalWorkouts + " 次", UIHelper.ACCENT_BLUE));

        FitnessDbHelper.WorkoutLog todayLog = dbHelper.getTodayLog();
        String todayStatus = todayLog != null && todayLog.exercisesDone > 0 ? "已完成" : "未完成";
        int todayColor = todayLog != null && todayLog.exercisesDone > 0 ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_RED;
        statsRow.addView(statItem("今日運動", todayStatus, todayColor));

        statsCard.addView(statsRow);
        contentArea.addView(statsCard);

        // Today's workout
        contentArea.addView(UIHelper.sectionHeader(this, "今日訓練"));

        FitnessDbHelper.PlanDay todayPlan = dbHelper.getTodayPlan();
        if (todayPlan != null) {
            buildDayCard(todayPlan, true);
        } else {
            LinearLayout noPlanCard = UIHelper.card(this);
            TextView noPlanMsg = new TextView(this);
            noPlanMsg.setText("尚未生成本週運動計畫");
            noPlanMsg.setTextSize(14);
            noPlanMsg.setTextColor(UIHelper.TEXT_HINT);
            noPlanMsg.setGravity(Gravity.CENTER);
            noPlanMsg.setPadding(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8));
            noPlanCard.addView(noPlanMsg);
            contentArea.addView(noPlanCard);
        }

        // Generate plan button
        statusText = new TextView(this);
        statusText.setTextSize(12);
        statusText.setTextColor(UIHelper.TEXT_HINT);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 8));

        Button generateBtn = UIHelper.primaryButton(this, "AI 生成本週運動計畫");
        generateBtn.setOnClickListener(v -> generatePlan(profile, generateBtn));

        contentArea.addView(generateBtn);
        contentArea.addView(statusText);

        // Weekly overview
        String weekLabel = FitnessDbHelper.getCurrentWeekLabel();
        List<FitnessDbHelper.PlanDay> weekPlan = dbHelper.getWeekPlan(weekLabel);
        if (!weekPlan.isEmpty()) {
            contentArea.addView(UIHelper.sectionHeader(this, "本週計畫"));
            for (FitnessDbHelper.PlanDay pd : weekPlan) {
                buildDayCard(pd, false);
            }
        }

        // Reminder setting
        contentArea.addView(UIHelper.sectionHeader(this, "運動提醒"));
        LinearLayout reminderCard = UIHelper.card(this);
        LinearLayout reminderRow = new LinearLayout(this);
        reminderRow.setOrientation(LinearLayout.HORIZONTAL);
        reminderRow.setGravity(Gravity.CENTER_VERTICAL);

        boolean reminderEnabled = ReminderHelper.isFitnessEnabled(this);
        int rHour = ReminderHelper.getFitnessHour(this);
        int rMin = ReminderHelper.getFitnessMinute(this);

        TextView reminderInfo = new TextView(this);
        reminderInfo.setTextSize(14);
        reminderInfo.setTextColor(UIHelper.TEXT_PRIMARY);
        reminderInfo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (reminderEnabled) {
            reminderInfo.setText(String.format(Locale.getDefault(), "每日 %02d:%02d 提醒運動", rHour, rMin));
        } else {
            reminderInfo.setText("未設定運動提醒");
            reminderInfo.setTextColor(UIHelper.TEXT_HINT);
        }

        Button reminderBtn = UIHelper.smallButton(this, reminderEnabled ? "修改" : "設定", UIHelper.ACCENT_ORANGE);
        reminderBtn.setOnClickListener(v -> {
            new TimePickerDialog(this, (view, h, m) -> {
                ReminderHelper.scheduleFitnessReminder(this, h, m);
                Toast.makeText(this, String.format("已設定每日 %02d:%02d 運動提醒", h, m),
                        Toast.LENGTH_SHORT).show();
                buildUI();
            }, rHour, rMin, true).show();
        });

        reminderRow.addView(reminderInfo);
        reminderRow.addView(reminderBtn);

        if (reminderEnabled) {
            Button cancelReminderBtn = UIHelper.smallButton(this, "關閉", UIHelper.TEXT_HINT);
            LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            crLp.setMargins(UIHelper.dp(this, 8), 0, 0, 0);
            cancelReminderBtn.setLayoutParams(crLp);
            cancelReminderBtn.setOnClickListener(v -> {
                ReminderHelper.cancelFitnessReminder(this);
                Toast.makeText(this, "已關閉運動提醒", Toast.LENGTH_SHORT).show();
                buildUI();
            });
            reminderRow.addView(cancelReminderBtn);
        }

        reminderCard.addView(reminderRow);
        contentArea.addView(reminderCard);
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

    private void buildDayCard(FitnessDbHelper.PlanDay pd, boolean showExercises) {
        LinearLayout card = UIHelper.card(this);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        int todayDow = FitnessDbHelper.getTodayDow();
        boolean isToday = pd.dayOfWeek == todayDow;

        TextView dayView = new TextView(this);
        dayView.setText(pd.dayLabel);
        dayView.setTextSize(16);
        dayView.setTextColor(isToday ? UIHelper.ACCENT_GREEN : UIHelper.TEXT_PRIMARY);
        dayView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        TextView focusBadge = UIHelper.statusBadge(this, pd.focus, UIHelper.ACCENT_BLUE);
        LinearLayout.LayoutParams fbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fbLp.setMargins(UIHelper.dp(this, 10), 0, 0, 0);
        focusBadge.setLayoutParams(fbLp);

        if (isToday) {
            TextView todayBadge = UIHelper.statusBadge(this, "TODAY", UIHelper.ACCENT_GREEN);
            LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tbLp.setMargins(UIHelper.dp(this, 6), 0, 0, 0);
            todayBadge.setLayoutParams(tbLp);
            header.addView(dayView);
            header.addView(todayBadge);
            header.addView(focusBadge);
        } else {
            header.addView(dayView);
            header.addView(focusBadge);
        }

        card.addView(header);

        // Exercises
        if (showExercises || isToday) {
            try {
                JSONArray exercises = new JSONArray(pd.exercisesJson);
                for (int i = 0; i < exercises.length(); i++) {
                    JSONObject ex = exercises.getJSONObject(i);
                    card.addView(buildExerciseRow(ex, i + 1));
                }

                // Complete button for today
                if (isToday) {
                    FitnessDbHelper.WorkoutLog todayLog = dbHelper.getTodayLog();
                    boolean done = todayLog != null && todayLog.exercisesDone > 0;

                    Button completeBtn = new Button(this);
                    completeBtn.setText(done ? "已完成今日訓練" : "完成今日訓練");
                    completeBtn.setTextColor(done ? UIHelper.ACCENT_GREEN : Color.WHITE);
                    completeBtn.setTextSize(14);
                    completeBtn.setAllCaps(false);
                    completeBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                    completeBtn.setBackground(UIHelper.roundRect(
                            done ? UIHelper.BG_CARD_ALT : UIHelper.ACCENT_GREEN, 12, this));
                    completeBtn.setStateListAnimator(null);
                    completeBtn.setElevation(UIHelper.dp(this, 2));
                    LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 48));
                    cbLp.setMargins(0, UIHelper.dp(this, 12), 0, 0);
                    completeBtn.setLayoutParams(cbLp);

                    if (!done) {
                        completeBtn.setOnClickListener(v -> {
                            dbHelper.saveLog(FitnessDbHelper.getTodayStr(), pd.id,
                                    exercises.length(), exercises.length(), 30, "");
                            AppLog.i("Fitness", "今日訓練完成: " + exercises.length() + "個動作");
                            Toast.makeText(this, "太棒了！今日訓練完成！", Toast.LENGTH_SHORT).show();
                            buildUI();
                        });
                    }
                    card.addView(completeBtn);
                }
            } catch (Exception e) {
                AppLog.e("Fitness", "計畫資料解析錯誤: " + e.getMessage());
                TextView errView = new TextView(this);
                errView.setText("計畫資料格式錯誤");
                errView.setTextSize(13);
                errView.setTextColor(UIHelper.ACCENT_RED);
                card.addView(errView);
            }
        } else {
            // Just show exercise count
            try {
                JSONArray exercises = new JSONArray(pd.exercisesJson);
                TextView countView = new TextView(this);
                countView.setText(exercises.length() + " 個動作");
                countView.setTextSize(13);
                countView.setTextColor(UIHelper.TEXT_SECONDARY);
                countView.setPadding(0, UIHelper.dp(this, 6), 0, 0);
                card.addView(countView);
            } catch (Exception ignored) {}

            // Tap to expand
            card.setOnClickListener(v -> {
                Intent intent = new Intent(this, WorkoutDetailActivity.class);
                intent.putExtra("plan_id", pd.id);
                startActivity(intent);
            });
        }

        contentArea.addView(card);
    }

    private LinearLayout buildExerciseRow(JSONObject ex, int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(UIHelper.roundRect(UIHelper.BG_CARD_ALT, 10, this));
        int rp = UIHelper.dp(this, 12);
        row.setPadding(rp, UIHelper.dp(this, 10), rp, UIHelper.dp(this, 10));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, UIHelper.dp(this, 6), 0, 0);
        row.setLayoutParams(rlp);

        // Exercise info
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView nameView = new TextView(this);
        nameView.setText(index + ". " + ex.optString("name", ""));
        nameView.setTextSize(14);
        nameView.setTextColor(UIHelper.TEXT_PRIMARY);
        nameView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        TextView detailView = new TextView(this);
        String detail = "";
        if (ex.has("sets") && ex.has("reps")) {
            detail = ex.optInt("sets") + " 組 x " + ex.optInt("reps") + " 次";
        }
        if (ex.has("duration_sec") && ex.optInt("duration_sec") > 0) {
            detail = ex.optInt("duration_sec") + " 秒";
        }
        if (ex.has("rest_sec") && ex.optInt("rest_sec") > 0) {
            detail += "  休息 " + ex.optInt("rest_sec") + "秒";
        }
        detailView.setText(detail);
        detailView.setTextSize(12);
        detailView.setTextColor(UIHelper.TEXT_SECONDARY);

        if (ex.has("tips") && !ex.optString("tips").isEmpty()) {
            TextView tipsView = new TextView(this);
            tipsView.setText(ex.optString("tips"));
            tipsView.setTextSize(11);
            tipsView.setTextColor(UIHelper.TEXT_HINT);
            tipsView.setMaxLines(2);
            info.addView(nameView);
            info.addView(detailView);
            info.addView(tipsView);
        } else {
            info.addView(nameView);
            info.addView(detailView);
        }

        row.addView(info);

        // Video button
        String videoKeyword = ex.optString("video_keyword", ex.optString("name", ""));
        if (!videoKeyword.isEmpty()) {
            Button videoBtn = new Button(this);
            videoBtn.setText("影片");
            videoBtn.setTextColor(UIHelper.ACCENT_RED);
            videoBtn.setTextSize(11);
            videoBtn.setAllCaps(false);
            videoBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            videoBtn.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.ACCENT_RED, 8, 1, this));
            videoBtn.setStateListAnimator(null);
            videoBtn.setElevation(0);
            int vp = UIHelper.dp(this, 10);
            videoBtn.setPadding(vp, 0, vp, 0);
            videoBtn.setMinimumWidth(0);
            videoBtn.setMinWidth(0);
            videoBtn.setOnClickListener(v -> {
                String query = videoKeyword + " 教學 居家運動";
                String url = "https://www.youtube.com/results?search_query=" + Uri.encode(query);
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            });
            row.addView(videoBtn);
        }

        return row;
    }

    private volatile boolean generating = false;

    private void generatePlan(FitnessDbHelper.Profile profile, Button btn) {
        btn.setEnabled(false);
        btn.setText("AI 生成中...");
        statusText.setText("正在透過 AI Bridge 生成運動計畫，請稍候...\n（可離開此頁面，完成後會通知您）");
        statusText.setTextColor(UIHelper.ACCENT_BLUE);
        generating = true;
        AppLog.i("Fitness", "AI計畫生成開始: goal=" + profile.goal + " level=" + profile.level);

        // Use application context for DB operations to survive activity destruction
        FitnessDbHelper bgDb = new FitnessDbHelper(getApplicationContext());

        BridgeClient.generateWorkoutPlan(profile.heightCm, profile.weightKg,
                profile.goal, profile.customGoal, profile.level, null, (responseJson, offline, error) -> {
                    generating = false;

                    if (offline) {
                        AppLog.e("Fitness", "AI計畫生成失敗: Bridge離線 " + (error != null ? error : "無法連線"));
                        if (!isFinishing() && !isDestroyed()) {
                            btn.setEnabled(true);
                            btn.setText("AI 生成本週運動計畫");
                            statusText.setText("Bridge 離線: " + (error != null ? error : "無法連線"));
                            statusText.setTextColor(UIHelper.ACCENT_RED);
                        }
                        return;
                    }

                    // Parse and save to DB first (independent of UI)
                    boolean saved = false;
                    String errorMsg = null;
                    try {
                        JSONObject resp = new JSONObject(responseJson);
                        JSONObject result = resp.optJSONObject("result");
                        if (result == null) {
                            String text = resp.optString("text", resp.optString("response", ""));
                            int jsonStart = text.indexOf("{");
                            int jsonEnd = text.lastIndexOf("}");
                            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                                result = new JSONObject(text.substring(jsonStart, jsonEnd + 1));
                            }
                        }

                        if (result != null && result.has("days")) {
                            JSONArray days = result.getJSONArray("days");
                            String weekLabel = FitnessDbHelper.getCurrentWeekLabel();
                            bgDb.clearWeekPlan(weekLabel);

                            for (int i = 0; i < days.length(); i++) {
                                JSONObject day = days.getJSONObject(i);
                                bgDb.insertPlanDay(weekLabel,
                                        day.optInt("day_of_week", i + 1),
                                        day.optString("day_label", FitnessDbHelper.getDowLabel(i + 1)),
                                        day.optString("focus", "綜合"),
                                        day.getJSONArray("exercises").toString());
                            }

                            AppLog.i("Fitness", "AI計畫生成完成: " + days.length() + "天");
                            saved = true;
                        } else {
                            AppLog.e("Fitness", "AI計畫生成失敗: 回應格式不符");
                            errorMsg = "AI 回應格式不符，請重試";
                        }
                    } catch (Exception e) {
                        AppLog.e("Fitness", "AI計畫解析失敗: " + e.getMessage());
                        errorMsg = "解析失敗: " + e.getMessage();
                    }

                    // Update UI only if activity is still alive
                    if (!isFinishing() && !isDestroyed()) {
                        btn.setEnabled(true);
                        btn.setText("AI 生成本週運動計畫");
                        if (saved) {
                            statusText.setText("計畫已生成！");
                            statusText.setTextColor(UIHelper.ACCENT_GREEN);
                            buildUI();
                        } else {
                            statusText.setText(errorMsg != null ? errorMsg : "生成失敗");
                            statusText.setTextColor(UIHelper.ACCENT_RED);
                        }
                    } else if (saved) {
                        // Activity not visible — send notification
                        NotificationHelper.sendNotification(getApplicationContext(),
                                "健身計畫已生成", "本週運動計畫已就緒，點擊查看");
                    }
                });
    }
}
