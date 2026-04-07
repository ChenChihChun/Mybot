package com.mybot.app;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class DreamActivity extends AppCompatActivity {

    private static final String TAG = "Dream";
    private EditText input;
    private Button submitBtn;
    private LinearLayout resultCard;
    private TextView symbolText;
    private TextView interpretationText;
    private TextView moodText;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        AppLog.i(TAG, "開啟夢境記錄");
        buildUI();
    }

    private void buildUI() {
        LinearLayout root = UIHelper.pageRoot(this);
        root.addView(UIHelper.topBar(this, "\uD83C\uDF19 夢境解析"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(24));

        TextView hint = new TextView(this);
        hint.setText("寫下你昨晚的夢，AI 將以榮格意象風格為你解讀。");
        hint.setTextColor(UIHelper.TEXT_SECONDARY);
        hint.setTextSize(14);
        hint.setPadding(0, dp(4), 0, dp(12));
        content.addView(hint);

        input = UIHelper.styledInput(this, "夢的內容…例如：我在森林裡迷路，遇到一隻會說話的鹿…");
        input.setMinLines(5);
        input.setGravity(Gravity.TOP | Gravity.START);
        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inLp.setMargins(0, 0, 0, dp(12));
        content.addView(input, inLp);

        submitBtn = UIHelper.primaryButton(this, "\u2728 解析夢境");
        submitBtn.setOnClickListener(v -> doAnalyze());
        content.addView(submitBtn);

        statusText = new TextView(this);
        statusText.setTextColor(UIHelper.TEXT_HINT);
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(16), 0, 0);
        statusText.setVisibility(View.GONE);
        content.addView(statusText);

        // Result card
        resultCard = UIHelper.card(this);
        resultCard.setVisibility(View.GONE);

        TextView symbolLabel = new TextView(this);
        symbolLabel.setText("關鍵意象");
        symbolLabel.setTextColor(UIHelper.ACCENT_BLUE);
        symbolLabel.setTextSize(13);
        symbolLabel.setTypeface(null, Typeface.BOLD);
        resultCard.addView(symbolLabel);

        symbolText = new TextView(this);
        symbolText.setTextColor(UIHelper.TEXT_PRIMARY);
        symbolText.setTextSize(20);
        symbolText.setTypeface(null, Typeface.BOLD);
        symbolText.setPadding(0, dp(4), 0, dp(12));
        resultCard.addView(symbolText);

        TextView interpLabel = new TextView(this);
        interpLabel.setText("解析");
        interpLabel.setTextColor(UIHelper.ACCENT_BLUE);
        interpLabel.setTextSize(13);
        interpLabel.setTypeface(null, Typeface.BOLD);
        resultCard.addView(interpLabel);

        interpretationText = new TextView(this);
        interpretationText.setTextColor(UIHelper.TEXT_PRIMARY);
        interpretationText.setTextSize(15);
        interpretationText.setLineSpacing(dp(4), 1f);
        interpretationText.setPadding(0, dp(4), 0, dp(12));
        resultCard.addView(interpretationText);

        TextView moodLabel = new TextView(this);
        moodLabel.setText("情緒氛圍");
        moodLabel.setTextColor(UIHelper.ACCENT_BLUE);
        moodLabel.setTextSize(13);
        moodLabel.setTypeface(null, Typeface.BOLD);
        resultCard.addView(moodLabel);

        moodText = new TextView(this);
        moodText.setTextColor(UIHelper.TEXT_SECONDARY);
        moodText.setTextSize(15);
        moodText.setPadding(0, dp(4), 0, 0);
        resultCard.addView(moodText);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, dp(20), 0, 0);
        content.addView(resultCard, cardLp);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void doAnalyze() {
        String dream = input.getText().toString().trim();
        if (dream.length() < 5) {
            Toast.makeText(this, "請至少寫下幾句夢的內容", Toast.LENGTH_SHORT).show();
            return;
        }
        AppLog.i(TAG, "送出解析: 字數=" + dream.length());
        submitBtn.setEnabled(false);
        submitBtn.setText("解析中…");
        statusText.setText("Claude 正在以榮格意象解讀…");
        statusText.setVisibility(View.VISIBLE);
        resultCard.setVisibility(View.GONE);

        BridgeClient.analyzeDream(dream, (symbol, interpretation, mood, error) -> {
            submitBtn.setEnabled(true);
            submitBtn.setText("\u2728 解析夢境");
            if (error != null || symbol == null) {
                statusText.setText("解析失敗: " + (error != null ? error : "未知錯誤"));
                AppLog.e(TAG, "解析失敗: " + error);
                return;
            }
            statusText.setVisibility(View.GONE);
            symbolText.setText(symbol);
            interpretationText.setText(interpretation);
            moodText.setText(mood);
            resultCard.setVisibility(View.VISIBLE);
            AppLog.i(TAG, "解析完成: " + symbol);
        });
    }

    private int dp(int v) {
        return UIHelper.dp(this, v);
    }
}
