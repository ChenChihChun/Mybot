package com.mybot.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class QuizActivity extends AppCompatActivity {

    private static final String TAG = "Quiz";
    private LinearLayout contentContainer;
    private TextView statusText;

    private JSONArray questions;
    private int currentIndex = 0;
    private int score = 0;
    private boolean answered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        AppLog.i(TAG, "開啟地圖小知識問答");
        buildUI();
        fetchQuiz();
    }

    private void buildUI() {
        LinearLayout root = UIHelper.pageRoot(this);
        root.addView(UIHelper.topBar(this, "\uD83C\uDF0D 地圖小知識"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(dp(16), dp(12), dp(16), dp(24));

        statusText = new TextView(this);
        statusText.setTextColor(UIHelper.TEXT_SECONDARY);
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(40), 0, 0);
        statusText.setText("載入中...");
        contentContainer.addView(statusText);

        scroll.addView(contentContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void fetchQuiz() {
        new Thread(() -> {
            try {
                URL url = new URL("http://127.0.0.1:8765/quiz/geography");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(90000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    AppLog.e(TAG, "取得題目失敗: HTTP " + code);
                    runOnUiThread(() -> statusText.setText("取得題目失敗 (HTTP " + code + ")"));
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }

                JSONObject json = new JSONObject(sb.toString());
                if (!json.optBoolean("success", false)) {
                    String err = json.optString("error", "未知錯誤");
                    AppLog.e(TAG, "API 錯誤: " + err);
                    runOnUiThread(() -> statusText.setText("取得題目失敗: " + err));
                    return;
                }

                questions = json.getJSONObject("data").getJSONArray("questions");
                int count = questions.length();
                AppLog.i(TAG, "取得 " + count + " 道題目");

                runOnUiThread(() -> {
                    statusText.setVisibility(View.GONE);
                    showQuestion(0);
                });

            } catch (Exception e) {
                AppLog.e(TAG, "取得題目例外: " + e.getMessage());
                runOnUiThread(() -> statusText.setText("連線失敗，請確認 Bridge 運作中"));
            }
        }).start();
    }

    private void showQuestion(int index) {
        answered = false;
        contentContainer.removeAllViews();

        if (questions == null || index >= questions.length()) {
            showResult();
            return;
        }

        try {
            JSONObject q = questions.getJSONObject(index);
            String questionText = q.getString("question");
            JSONArray options = q.getJSONArray("options");
            String correctAnswer = q.getString("answer");

            // Progress indicator
            TextView progress = new TextView(this);
            progress.setText("第 " + (index + 1) + " / " + questions.length() + " 題");
            progress.setTextColor(UIHelper.ACCENT_BLUE);
            progress.setTextSize(14);
            progress.setTypeface(null, Typeface.BOLD);
            progress.setPadding(0, dp(4), 0, dp(12));
            contentContainer.addView(progress);

            // Score so far
            if (index > 0) {
                TextView scoreLabel = new TextView(this);
                scoreLabel.setText("目前得分: " + score + " / " + index);
                scoreLabel.setTextColor(UIHelper.TEXT_HINT);
                scoreLabel.setTextSize(13);
                scoreLabel.setPadding(0, 0, 0, dp(12));
                contentContainer.addView(scoreLabel);
            }

            // Question card
            LinearLayout qCard = UIHelper.card(this);
            TextView qText = new TextView(this);
            qText.setText(questionText);
            qText.setTextColor(UIHelper.TEXT_PRIMARY);
            qText.setTextSize(17);
            qText.setLineSpacing(dp(4), 1f);
            qCard.addView(qText);
            LinearLayout.LayoutParams qLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            qLp.setMargins(0, 0, 0, dp(16));
            contentContainer.addView(qCard, qLp);

            // Option buttons
            String[] labels = {"A", "B", "C", "D"};
            Button[] optionBtns = new Button[4];

            for (int i = 0; i < 4 && i < options.length(); i++) {
                String label = labels[i];
                String optText = options.getString(i);

                Button btn = new Button(this);
                btn.setText(label + ". " + optText);
                btn.setTextColor(UIHelper.TEXT_PRIMARY);
                btn.setTextSize(15);
                btn.setAllCaps(false);
                btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                btn.setBackground(UIHelper.roundRectStroke(
                        UIHelper.BG_CARD, UIHelper.TEXT_HINT, 12, 1, this));
                btn.setPadding(dp(16), dp(14), dp(16), dp(14));
                btn.setElevation(dp(2));

                LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                btnLp.setMargins(0, dp(6), 0, dp(6));

                optionBtns[i] = btn;
                final int optIndex = i;

                btn.setOnClickListener(v -> {
                    if (answered) return;
                    answered = true;

                    boolean correct = label.equals(correctAnswer);
                    if (correct) {
                        score++;
                        btn.setBackground(UIHelper.roundRectStroke(
                                Color.parseColor("#1B3B1B"), UIHelper.ACCENT_GREEN, 12, 2, this));
                        btn.setTextColor(UIHelper.ACCENT_GREEN);
                        AppLog.i(TAG, "第 " + (index + 1) + " 題答對");
                    } else {
                        btn.setBackground(UIHelper.roundRectStroke(
                                Color.parseColor("#3B1B1B"), UIHelper.ACCENT_RED, 12, 2, this));
                        btn.setTextColor(UIHelper.ACCENT_RED);

                        // Highlight correct answer
                        for (int j = 0; j < 4; j++) {
                            if (labels[j].equals(correctAnswer) && optionBtns[j] != null) {
                                optionBtns[j].setBackground(UIHelper.roundRectStroke(
                                        Color.parseColor("#1B3B1B"), UIHelper.ACCENT_GREEN, 12, 2, this));
                                optionBtns[j].setTextColor(UIHelper.ACCENT_GREEN);
                            }
                        }
                        AppLog.i(TAG, "第 " + (index + 1) + " 題答錯，正解: " + correctAnswer);
                    }

                    // Disable all buttons
                    for (Button b : optionBtns) {
                        if (b != null) b.setEnabled(false);
                    }

                    // Next button after 1 second
                    v.postDelayed(() -> {
                        if (index + 1 < questions.length()) {
                            showQuestion(index + 1);
                        } else {
                            showResult();
                        }
                    }, 1200);
                });

                contentContainer.addView(btn, btnLp);
            }

        } catch (Exception e) {
            AppLog.e(TAG, "顯示題目錯誤: " + e.getMessage());
            TextView err = new TextView(this);
            err.setText("題目載入錯誤");
            err.setTextColor(UIHelper.ACCENT_RED);
            contentContainer.addView(err);
        }
    }

    private void showResult() {
        contentContainer.removeAllViews();
        int total = questions != null ? questions.length() : 0;

        AppLog.i(TAG, "作答完成，得分: " + score + "/" + total);

        // Result card
        LinearLayout card = UIHelper.card(this);

        TextView emoji = new TextView(this);
        String face;
        if (total > 0 && score == total) face = "\uD83C\uDF1F";
        else if (total > 0 && score >= total * 0.6) face = "\uD83D\uDE0A";
        else face = "\uD83D\uDCAA";
        emoji.setText(face);
        emoji.setTextSize(48);
        emoji.setGravity(Gravity.CENTER);
        emoji.setPadding(0, dp(8), 0, dp(8));
        card.addView(emoji);

        TextView title = new TextView(this);
        title.setText("測驗結束！");
        title.setTextColor(UIHelper.TEXT_PRIMARY);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        card.addView(title);

        TextView scoreText = new TextView(this);
        scoreText.setText(score + " / " + total);
        scoreText.setTextColor(UIHelper.ACCENT_BLUE);
        scoreText.setTextSize(36);
        scoreText.setTypeface(null, Typeface.BOLD);
        scoreText.setGravity(Gravity.CENTER);
        scoreText.setPadding(0, dp(4), 0, dp(16));
        card.addView(scoreText);

        String msg;
        if (total > 0 && score == total) msg = "完美！你是地理達人！";
        else if (total > 0 && score >= total * 0.6) msg = "不錯！繼續累積地理知識！";
        else msg = "加油！下次會更好！";

        TextView msgText = new TextView(this);
        msgText.setText(msg);
        msgText.setTextColor(UIHelper.TEXT_SECONDARY);
        msgText.setTextSize(16);
        msgText.setGravity(Gravity.CENTER);
        msgText.setPadding(0, 0, 0, dp(8));
        card.addView(msgText);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, dp(24), 0, dp(24));
        contentContainer.addView(card, cardLp);

        // Retry button
        Button retryBtn = UIHelper.primaryButton(this, "\uD83D\uDD04 再玩一次");
        retryBtn.setOnClickListener(v -> {
            currentIndex = 0;
            score = 0;
            AppLog.i(TAG, "重新開始測驗");
            showQuestion(0);
        });
        contentContainer.addView(retryBtn);

        // Close button
        Button closeBtn = UIHelper.outlineButton(this, "返回");
        closeBtn.setOnClickListener(v -> finish());
        contentContainer.addView(closeBtn);
    }

    private int dp(int v) {
        return UIHelper.dp(this, v);
    }
}
