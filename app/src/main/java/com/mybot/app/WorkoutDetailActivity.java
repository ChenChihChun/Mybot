package com.mybot.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

public class WorkoutDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        long planId = getIntent().getLongExtra("plan_id", -1);
        FitnessDbHelper db = new FitnessDbHelper(this);

        LinearLayout root = UIHelper.pageRoot(this);
        LinearLayout topBar = UIHelper.topBar(this, "訓練詳情");
        Button closeBtn = UIHelper.smallButton(this, "X", UIHelper.TEXT_SECONDARY);
        closeBtn.setOnClickListener(v -> finish());
        topBar.addView(closeBtn);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 16);
        content.setPadding(p, UIHelper.dp(this, 12), p, p);

        // Find plan
        String weekLabel = FitnessDbHelper.getCurrentWeekLabel();
        FitnessDbHelper.PlanDay plan = null;

        for (FitnessDbHelper.PlanDay pd : db.getWeekPlan(weekLabel)) {
            if (pd.id == planId) {
                plan = pd;
                break;
            }
        }

        if (plan == null) {
            AppLog.e("Fitness", "找不到訓練計畫: planId=" + planId);
            TextView err = new TextView(this);
            err.setText("找不到訓練計畫");
            err.setTextSize(16);
            err.setTextColor(UIHelper.TEXT_HINT);
            err.setGravity(Gravity.CENTER);
            content.addView(err);
        } else {
            AppLog.i("Fitness", "載入訓練詳情: " + plan.dayLabel + " - " + plan.focus);
            // Header card
            LinearLayout headerCard = UIHelper.card(this);

            TextView dayTitle = new TextView(this);
            dayTitle.setText(plan.dayLabel);
            dayTitle.setTextSize(24);
            dayTitle.setTextColor(UIHelper.TEXT_PRIMARY);
            dayTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

            TextView focusView = new TextView(this);
            focusView.setText("訓練重點: " + plan.focus);
            focusView.setTextSize(14);
            focusView.setTextColor(UIHelper.ACCENT_BLUE);
            focusView.setPadding(0, UIHelper.dp(this, 4), 0, 0);

            headerCard.addView(dayTitle);
            headerCard.addView(focusView);
            content.addView(headerCard);

            // Exercises
            try {
                JSONArray exercises = new JSONArray(plan.exercisesJson);
                content.addView(UIHelper.sectionHeader(this, "動作清單 (" + exercises.length() + " 個)"));

                for (int i = 0; i < exercises.length(); i++) {
                    JSONObject ex = exercises.getJSONObject(i);
                    content.addView(buildExerciseCard(ex, i + 1));
                }

                // Complete button
                int todayDow = FitnessDbHelper.getTodayDow();
                if (plan.dayOfWeek == todayDow) {
                    FitnessDbHelper.WorkoutLog todayLog = db.getTodayLog();
                    boolean done = todayLog != null && todayLog.exercisesDone > 0;

                    Button completeBtn = UIHelper.primaryButton(this, done ? "已完成今日訓練" : "完成今日訓練");
                    if (done) {
                        completeBtn.setBackground(UIHelper.roundRect(UIHelper.BG_CARD_ALT, 14, this));
                        completeBtn.setTextColor(UIHelper.ACCENT_GREEN);
                    }
                    final FitnessDbHelper.PlanDay finalPlan = plan;
                    final int exCount = exercises.length();
                    if (!done) {
                        completeBtn.setOnClickListener(v -> {
                            db.saveLog(FitnessDbHelper.getTodayStr(), finalPlan.id,
                                    exCount, exCount, 30, "");
                            AppLog.i("Fitness", "今日訓練完成(詳情頁): " + exCount + "個動作");
                            Toast.makeText(this, "太棒了！今日訓練完成！", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                    content.addView(completeBtn);
                }

            } catch (Exception e) {
                AppLog.e("Fitness", "訓練詳情解析錯誤: " + e.getMessage());
                TextView errView = new TextView(this);
                errView.setText("計畫資料格式錯誤: " + e.getMessage());
                errView.setTextSize(13);
                errView.setTextColor(UIHelper.ACCENT_RED);
                content.addView(errView);
            }
        }

        scrollView.addView(content);
        root.addView(topBar);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private LinearLayout buildExerciseCard(JSONObject ex, int index) {
        LinearLayout card = UIHelper.card(this);

        // Title row
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView numBadge = new TextView(this);
        numBadge.setText(String.valueOf(index));
        numBadge.setTextSize(14);
        numBadge.setTextColor(Color.WHITE);
        numBadge.setTypeface(Typeface.DEFAULT_BOLD);
        numBadge.setGravity(Gravity.CENTER);
        numBadge.setBackground(UIHelper.roundRect(UIHelper.ACCENT_GREEN, 20, this));
        int ns = UIHelper.dp(this, 28);
        LinearLayout.LayoutParams nbLp = new LinearLayout.LayoutParams(ns, ns);
        nbLp.setMargins(0, 0, UIHelper.dp(this, 12), 0);
        numBadge.setLayoutParams(nbLp);

        TextView nameView = new TextView(this);
        nameView.setText(ex.optString("name", ""));
        nameView.setTextSize(16);
        nameView.setTextColor(UIHelper.TEXT_PRIMARY);
        nameView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        nameView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        titleRow.addView(numBadge);
        titleRow.addView(nameView);
        card.addView(titleRow);

        // Details
        LinearLayout detailRow = new LinearLayout(this);
        detailRow.setOrientation(LinearLayout.HORIZONTAL);
        detailRow.setPadding(UIHelper.dp(this, 40), UIHelper.dp(this, 8), 0, 0);

        if (ex.has("sets") && ex.optInt("sets") > 0) {
            detailRow.addView(detailBadge(ex.optInt("sets") + " 組", UIHelper.ACCENT_BLUE));
        }
        if (ex.has("reps") && ex.optInt("reps") > 0) {
            detailRow.addView(detailBadge(ex.optInt("reps") + " 次", UIHelper.ACCENT_ORANGE));
        }
        if (ex.has("duration_sec") && ex.optInt("duration_sec") > 0) {
            detailRow.addView(detailBadge(ex.optInt("duration_sec") + " 秒", UIHelper.ACCENT_PURPLE));
        }
        if (ex.has("rest_sec") && ex.optInt("rest_sec") > 0) {
            detailRow.addView(detailBadge("休息 " + ex.optInt("rest_sec") + "秒", UIHelper.TEXT_HINT));
        }
        card.addView(detailRow);

        // Tips
        if (ex.has("tips") && !ex.optString("tips").isEmpty()) {
            TextView tips = new TextView(this);
            tips.setText(ex.optString("tips"));
            tips.setTextSize(13);
            tips.setTextColor(UIHelper.TEXT_SECONDARY);
            tips.setPadding(UIHelper.dp(this, 40), UIHelper.dp(this, 6), 0, 0);
            card.addView(tips);
        }

        // Video button
        String videoKeyword = ex.optString("video_keyword", ex.optString("name", ""));
        if (!videoKeyword.isEmpty()) {
            Button videoBtn = new Button(this);
            videoBtn.setText("觀看教學影片");
            videoBtn.setTextColor(UIHelper.ACCENT_RED);
            videoBtn.setTextSize(13);
            videoBtn.setAllCaps(false);
            videoBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            videoBtn.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.ACCENT_RED, 10, 1, this));
            videoBtn.setStateListAnimator(null);
            videoBtn.setElevation(0);
            LinearLayout.LayoutParams vbLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 40));
            vbLp.setMargins(UIHelper.dp(this, 40), UIHelper.dp(this, 10), 0, 0);
            videoBtn.setLayoutParams(vbLp);
            videoBtn.setOnClickListener(v -> {
                String query = videoKeyword + " 教學 居家運動";
                String url = "https://www.youtube.com/results?search_query=" + Uri.encode(query);
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            });
            card.addView(videoBtn);
        }

        return card;
    }

    private TextView detailBadge(String text, int color) {
        TextView badge = UIHelper.statusBadge(this, text, color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, UIHelper.dp(this, 6), 0);
        badge.setLayoutParams(lp);
        return badge;
    }
}
