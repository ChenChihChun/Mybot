package com.mybot.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

public class YouTubeActivity extends AppCompatActivity {

    private EditText urlInput;
    private TextView btnSummarize;
    private ProgressBar progress;
    private LinearLayout resultContainer;
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        buildUI();
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UIHelper.BG_PRIMARY);

        // Top bar
        root.addView(UIHelper.topBar(this, "\uD83C\uDFAC 影片摘要"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(24));

        // URL input area
        LinearLayout inputCard = new LinearLayout(this);
        inputCard.setOrientation(LinearLayout.VERTICAL);
        inputCard.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 12, this));
        inputCard.setPadding(dp(14), dp(12), dp(14), dp(14));

        TextView label = new TextView(this);
        label.setText("YouTube 網址");
        label.setTextColor(UIHelper.TEXT_SECONDARY);
        label.setTextSize(12);
        inputCard.addView(label);

        urlInput = new EditText(this);
        urlInput.setHint("貼上 YouTube 連結...");
        urlInput.setHintTextColor(UIHelper.TEXT_HINT);
        urlInput.setTextColor(UIHelper.TEXT_PRIMARY);
        urlInput.setTextSize(14);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setMaxLines(2);
        urlInput.setBackground(null);
        urlInput.setPadding(0, dp(8), 0, dp(4));
        inputCard.addView(urlInput);

        // Button row
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        btnRow.setPadding(0, dp(8), 0, 0);

        // Paste button
        TextView btnPaste = new TextView(this);
        btnPaste.setText("貼上");
        btnPaste.setTextColor(UIHelper.TEXT_SECONDARY);
        btnPaste.setTextSize(13);
        btnPaste.setPadding(dp(14), dp(8), dp(14), dp(8));
        btnPaste.setBackground(UIHelper.roundRectStroke(UIHelper.BG_CARD, UIHelper.TEXT_HINT, 8, 1, this));
        btnPaste.setOnClickListener(v -> pasteFromClipboard());

        // Summarize button
        btnSummarize = new TextView(this);
        btnSummarize.setText("摘要");
        btnSummarize.setTextColor(Color.WHITE);
        btnSummarize.setTextSize(13);
        btnSummarize.setTypeface(null, Typeface.BOLD);
        btnSummarize.setPadding(dp(20), dp(8), dp(20), dp(8));
        btnSummarize.setBackground(UIHelper.roundRect(UIHelper.ACCENT_RED, 8, this));
        btnSummarize.setOnClickListener(v -> startSummarize());

        LinearLayout.LayoutParams pasteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pasteLp.setMargins(0, 0, dp(8), 0);
        btnRow.addView(btnPaste, pasteLp);
        btnRow.addView(btnSummarize);
        inputCard.addView(btnRow);

        content.addView(inputCard);

        // Progress
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        progress.setPadding(0, dp(8), 0, 0);
        content.addView(progress);

        // Status text
        TextView hint = new TextView(this);
        hint.setText("支援 YouTube 影片連結，將自動擷取字幕並產生重點摘要");
        hint.setTextColor(UIHelper.TEXT_HINT);
        hint.setTextSize(11);
        hint.setPadding(0, dp(8), 0, dp(16));
        content.addView(hint);

        // Result area
        resultContainer = new LinearLayout(this);
        resultContainer.setOrientation(LinearLayout.VERTICAL);
        resultContainer.setVisibility(View.GONE);
        content.addView(resultContainer);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence text = clip.getItemAt(0).getText();
                if (text != null) {
                    urlInput.setText(text);
                    urlInput.setSelection(text.length());
                }
            }
        }
    }

    private void startSummarize() {
        if (isLoading) return;

        String url = urlInput.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "請輸入 YouTube 網址", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!url.contains("youtube.com") && !url.contains("youtu.be")) {
            Toast.makeText(this, "請輸入有效的 YouTube 連結", Toast.LENGTH_SHORT).show();
            return;
        }

        isLoading = true;
        progress.setVisibility(View.VISIBLE);
        btnSummarize.setText("分析中...");
        btnSummarize.setEnabled(false);
        resultContainer.setVisibility(View.GONE);
        resultContainer.removeAllViews();

        AppLog.i("YouTube", "開始摘要: " + url);

        BridgeClient.summarizeVideo(url, (summary, error) -> {
            isLoading = false;
            progress.setVisibility(View.GONE);
            btnSummarize.setText("摘要");
            btnSummarize.setEnabled(true);

            if (error != null) {
                AppLog.e("YouTube", "摘要失敗: " + error);
                showError(error);
                return;
            }

            if (summary != null) {
                AppLog.i("YouTube", "摘要完成");
                showResult(summary);
            }
        });
    }

    private void showError(String error) {
        resultContainer.removeAllViews();
        resultContainer.setVisibility(View.VISIBLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(Color.parseColor("#2D1515"), 12, this));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView tv = new TextView(this);
        tv.setText("❌ " + error);
        tv.setTextColor(UIHelper.ACCENT_RED);
        tv.setTextSize(13);
        card.addView(tv);
        resultContainer.addView(card);
    }

    private void showResult(JSONObject summary) {
        resultContainer.removeAllViews();
        resultContainer.setVisibility(View.VISIBLE);

        String title = summary.optString("title", "");
        String summaryText = summary.optString("summary", "");
        JSONArray keyPoints = summary.optJSONArray("key_points");
        JSONArray topics = summary.optJSONArray("topics");

        // Title card
        if (!title.isEmpty()) {
            LinearLayout titleCard = new LinearLayout(this);
            titleCard.setOrientation(LinearLayout.VERTICAL);
            titleCard.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 12, this));
            titleCard.setPadding(dp(14), dp(12), dp(14), dp(12));

            TextView tvTitle = new TextView(this);
            tvTitle.setText("\uD83C\uDFAC " + title);
            tvTitle.setTextColor(UIHelper.TEXT_PRIMARY);
            tvTitle.setTextSize(15);
            tvTitle.setTypeface(null, Typeface.BOLD);
            titleCard.addView(tvTitle);

            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleLp.setMargins(0, 0, 0, dp(8));
            resultContainer.addView(titleCard, titleLp);
        }

        // Summary card
        if (!summaryText.isEmpty()) {
            LinearLayout sumCard = new LinearLayout(this);
            sumCard.setOrientation(LinearLayout.VERTICAL);
            sumCard.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 12, this));
            sumCard.setPadding(dp(14), dp(12), dp(14), dp(12));

            TextView sumLabel = new TextView(this);
            sumLabel.setText("📋 摘要");
            sumLabel.setTextColor(UIHelper.ACCENT_BLUE);
            sumLabel.setTextSize(13);
            sumLabel.setTypeface(null, Typeface.BOLD);
            sumCard.addView(sumLabel);

            TextView sumText = new TextView(this);
            sumText.setText(summaryText);
            sumText.setTextColor(UIHelper.TEXT_PRIMARY);
            sumText.setTextSize(14);
            sumText.setPadding(0, dp(6), 0, 0);
            sumText.setLineSpacing(dp(3), 1f);
            sumCard.addView(sumText);

            LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sumLp.setMargins(0, 0, 0, dp(8));
            resultContainer.addView(sumCard, sumLp);
        }

        // Key points card
        if (keyPoints != null && keyPoints.length() > 0) {
            LinearLayout pointsCard = new LinearLayout(this);
            pointsCard.setOrientation(LinearLayout.VERTICAL);
            pointsCard.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 12, this));
            pointsCard.setPadding(dp(14), dp(12), dp(14), dp(12));

            TextView pointsLabel = new TextView(this);
            pointsLabel.setText("\uD83D\uDD11 重點");
            pointsLabel.setTextColor(UIHelper.ACCENT_ORANGE);
            pointsLabel.setTextSize(13);
            pointsLabel.setTypeface(null, Typeface.BOLD);
            pointsCard.addView(pointsLabel);

            for (int i = 0; i < keyPoints.length(); i++) {
                String point = keyPoints.optString(i, "");
                if (point.isEmpty()) continue;

                TextView tv = new TextView(this);
                tv.setText("• " + point);
                tv.setTextColor(UIHelper.TEXT_PRIMARY);
                tv.setTextSize(13);
                tv.setPadding(dp(4), dp(6), 0, 0);
                tv.setLineSpacing(dp(2), 1f);
                pointsCard.addView(tv);
            }

            LinearLayout.LayoutParams ptLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ptLp.setMargins(0, 0, 0, dp(8));
            resultContainer.addView(pointsCard, ptLp);
        }

        // Topics (as wrapping text)
        if (topics != null && topics.length() > 0) {
            StringBuilder tagStr = new StringBuilder();
            for (int i = 0; i < topics.length(); i++) {
                String tag = topics.optString(i, "");
                if (tag.isEmpty()) continue;
                if (tagStr.length() > 0) tagStr.append("  ");
                tagStr.append("#").append(tag);
            }
            TextView tagsView = new TextView(this);
            tagsView.setText(tagStr.toString());
            tagsView.setTextColor(UIHelper.ACCENT_BLUE);
            tagsView.setTextSize(12);
            tagsView.setLineSpacing(dp(6), 1f);
            tagsView.setPadding(0, dp(8), 0, 0);
            resultContainer.addView(tagsView);
        }

        // Button row: copy + save to knowledge base
        LinearLayout resultBtnRow = new LinearLayout(this);
        resultBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        resultBtnRow.setGravity(Gravity.END);
        resultBtnRow.setPadding(0, dp(12), 0, 0);

        // Save to knowledge base button
        TextView saveKbBtn = new TextView(this);
        saveKbBtn.setText("\uD83D\uDCDA 儲存到知識庫");
        saveKbBtn.setTextColor(UIHelper.ACCENT_GREEN);
        saveKbBtn.setTextSize(12);
        saveKbBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        saveKbBtn.setBackground(UIHelper.roundRectStroke(UIHelper.BG_CARD, UIHelper.ACCENT_GREEN, 8, 1, this));
        saveKbBtn.setOnClickListener(v -> {
            String sourceUrl = urlInput.getText().toString().trim();
            saveToKnowledgeBase(title, summaryText, keyPoints, sourceUrl, saveKbBtn);
        });

        // Copy button
        TextView copyBtn = new TextView(this);
        copyBtn.setText("複製摘要");
        copyBtn.setTextColor(UIHelper.TEXT_SECONDARY);
        copyBtn.setTextSize(12);
        copyBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        copyBtn.setBackground(UIHelper.roundRectStroke(UIHelper.BG_CARD, UIHelper.TEXT_HINT, 8, 1, this));
        copyBtn.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            if (!title.isEmpty()) sb.append("【").append(title).append("】\n\n");
            if (!summaryText.isEmpty()) sb.append("摘要：\n").append(summaryText).append("\n\n");
            if (keyPoints != null && keyPoints.length() > 0) {
                sb.append("重點：\n");
                for (int i = 0; i < keyPoints.length(); i++) {
                    sb.append("• ").append(keyPoints.optString(i, "")).append("\n");
                }
            }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("video_summary", sb.toString()));
            Toast.makeText(this, "已複製", Toast.LENGTH_SHORT).show();
        });

        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveLp.setMargins(0, 0, dp(8), 0);
        resultBtnRow.addView(saveKbBtn, saveLp);
        resultBtnRow.addView(copyBtn);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.gravity = Gravity.END;
        resultContainer.addView(resultBtnRow, rowLp);
    }

    private void saveToKnowledgeBase(String title, String summaryText,
                                     JSONArray keyPoints, String sourceUrl, TextView saveBtn) {
        AppLog.i("Knowledge", "儲存到知識庫: " + title);
        saveBtn.setText("儲存中...");
        saveBtn.setEnabled(false);

        // Build key_points string
        StringBuilder kpBuilder = new StringBuilder();
        if (keyPoints != null) {
            for (int i = 0; i < keyPoints.length(); i++) {
                String pt = keyPoints.optString(i, "");
                if (!pt.isEmpty()) {
                    if (kpBuilder.length() > 0) kpBuilder.append("\n");
                    kpBuilder.append("\u2022 ").append(pt);
                }
            }
        }
        String keyPointsStr = kpBuilder.toString();

        // AI categorize then save
        BridgeClient.categorizeKnowledge(title, summaryText, (category, error) -> {
            if (error != null) {
                AppLog.w("Knowledge", "AI分類失敗，使用預設: " + error);
            }

            KnowledgeDbHelper dbHelper = new KnowledgeDbHelper(this);
            long id = dbHelper.insert(title, summaryText, keyPointsStr, sourceUrl, category);
            dbHelper.close();

            if (id > 0) {
                AppLog.i("Knowledge", "儲存成功: id=" + id + " category=" + category);
                saveBtn.setText("\u2705 已儲存");
                saveBtn.setTextColor(UIHelper.ACCENT_GREEN);
                Toast.makeText(this, "已儲存到知識庫 [" + category + "]", Toast.LENGTH_SHORT).show();
            } else {
                AppLog.e("Knowledge", "儲存失敗");
                saveBtn.setText("\uD83D\uDCDA 儲存到知識庫");
                saveBtn.setEnabled(true);
                Toast.makeText(this, "儲存失敗", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int dp(int v) {
        return UIHelper.dp(this, v);
    }
}
