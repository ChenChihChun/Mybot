package com.mybot.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class LyricsActivity extends AppCompatActivity {

    private static final String TAG = "Lyrics";

    private static final String[] EMOTIONS = {"思念", "失戀", "熱戀", "孤獨", "勇敢", "懷舊"};
    private static final String[] THEMES = {"青春", "城市夜晚", "回家的路", "夢想", "海邊", "雨天"};
    private static final String[] STYLES = {"流行抒情", "民謠", "搖滾", "電子", "嘻哈", "爵士"};

    private Spinner emotionSpinner, themeSpinner, styleSpinner;
    private Button generateBtn, shareBtn;
    private TextView resultText, statusText;
    private LinearLayout resultCard;
    private String currentLyrics = "";
    private String currentTitle = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        AppLog.i(TAG, "開啟歌詞填詞生成器");
        buildUI();
    }

    private void buildUI() {
        LinearLayout root = UIHelper.pageRoot(this);
        root.addView(UIHelper.topBar(this, "\uD83C\uDFB5 歌詞生成"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = UIHelper.dp(this, 16);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content);
        root.addView(scroll);

        // Input card
        LinearLayout inputCard = UIHelper.card(this);
        inputCard.addView(label("情緒"));
        emotionSpinner = newSpinner(EMOTIONS);
        inputCard.addView(emotionSpinner);
        inputCard.addView(label("主題"));
        themeSpinner = newSpinner(THEMES);
        inputCard.addView(themeSpinner);
        inputCard.addView(label("曲風"));
        styleSpinner = newSpinner(STYLES);
        inputCard.addView(styleSpinner);

        generateBtn = new Button(this);
        generateBtn.setText("\u2728 生成歌詞");
        generateBtn.setTextColor(Color.WHITE);
        generateBtn.setBackgroundColor(UIHelper.ACCENT_PURPLE);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = UIHelper.dp(this, 12);
        generateBtn.setLayoutParams(blp);
        generateBtn.setOnClickListener(v -> generate());
        inputCard.addView(generateBtn);

        content.addView(inputCard);

        statusText = new TextView(this);
        statusText.setTextColor(Color.LTGRAY);
        statusText.setPadding(0, UIHelper.dp(this, 12), 0, UIHelper.dp(this, 4));
        content.addView(statusText);

        // Result card
        resultCard = UIHelper.card(this);
        resultCard.setVisibility(View.GONE);

        resultText = new TextView(this);
        resultText.setTextColor(Color.WHITE);
        resultText.setTextSize(16);
        resultText.setLineSpacing(0, 1.4f);
        resultCard.addView(resultText);

        shareBtn = new Button(this);
        shareBtn.setText("\uD83D\uDCE4 分享");
        shareBtn.setTextColor(Color.WHITE);
        shareBtn.setBackgroundColor(UIHelper.ACCENT_BLUE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = UIHelper.dp(this, 12);
        shareBtn.setLayoutParams(slp);
        shareBtn.setOnClickListener(v -> share());
        resultCard.addView(shareBtn);

        content.addView(resultCard);
        setContentView(root);
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.LTGRAY);
        tv.setPadding(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 4));
        return tv;
    }

    private Spinner newSpinner(String[] items) {
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, items);
        sp.setAdapter(adapter);
        return sp;
    }

    private void generate() {
        String emotion = (String) emotionSpinner.getSelectedItem();
        String theme = (String) themeSpinner.getSelectedItem();
        String style = (String) styleSpinner.getSelectedItem();
        AppLog.i(TAG, "生成歌詞: " + emotion + "/" + theme + "/" + style);
        statusText.setText("生成中…（約 30-60 秒）");
        generateBtn.setEnabled(false);
        resultCard.setVisibility(View.GONE);

        BridgeClient.generateLyrics(emotion, theme, style, (lyrics, error) -> {
            generateBtn.setEnabled(true);
            if (lyrics == null) {
                statusText.setText("生成失敗：" + (error != null ? error : "未知錯誤"));
                AppLog.e(TAG, "生成失敗: " + error);
                return;
            }
            currentTitle = lyrics.optString("title", "無題");
            String verse = lyrics.optString("verse", "");
            String chorus = lyrics.optString("chorus", "");
            String bridgeLine = lyrics.optString("bridge_line", "");
            StringBuilder sb = new StringBuilder();
            sb.append("《").append(currentTitle).append("》\n\n");
            sb.append("[主歌]\n").append(verse).append("\n\n");
            sb.append("[副歌]\n").append(chorus);
            if (!bridgeLine.isEmpty()) {
                sb.append("\n\n[Bridge]\n").append(bridgeLine);
            }
            currentLyrics = sb.toString();
            resultText.setText(currentLyrics);
            resultCard.setVisibility(View.VISIBLE);
            statusText.setText("完成");
            AppLog.i(TAG, "生成成功: " + currentTitle);
        });
    }

    private void share() {
        if (currentLyrics.isEmpty()) {
            Toast.makeText(this, "尚未生成歌詞", Toast.LENGTH_SHORT).show();
            return;
        }
        AppLog.i(TAG, "分享歌詞: " + currentTitle);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, currentTitle);
        send.putExtra(Intent.EXTRA_TEXT, currentLyrics);
        startActivity(Intent.createChooser(send, "分享歌詞"));
    }
}
