package com.mybot.app;

import android.content.Context;
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
    private TextView statusText, titleView, verseView, chorusView, bridgeView;
    private LinearLayout resultCard, bridgeSection;
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
        TextView cardTitle = new TextView(this);
        cardTitle.setText("\u270D\uFE0F 創作設定");
        cardTitle.setTextColor(UIHelper.TEXT_PRIMARY);
        cardTitle.setTextSize(16);
        cardTitle.setTypeface(null, Typeface.BOLD);
        cardTitle.setPadding(0, 0, 0, UIHelper.dp(this, 8));
        inputCard.addView(cardTitle);

        inputCard.addView(label("情緒"));
        emotionSpinner = newSpinner(EMOTIONS);
        inputCard.addView(wrapSpinner(emotionSpinner));
        inputCard.addView(label("主題"));
        themeSpinner = newSpinner(THEMES);
        inputCard.addView(wrapSpinner(themeSpinner));
        inputCard.addView(label("曲風"));
        styleSpinner = newSpinner(STYLES);
        inputCard.addView(wrapSpinner(styleSpinner));

        generateBtn = new Button(this);
        generateBtn.setText("\u2728 生成歌詞");
        generateBtn.setTextColor(Color.WHITE);
        generateBtn.setTextSize(15);
        generateBtn.setTypeface(null, Typeface.BOLD);
        generateBtn.setAllCaps(false);
        generateBtn.setBackground(UIHelper.roundRect(UIHelper.ACCENT_PURPLE, 14, this));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 48));
        blp.topMargin = UIHelper.dp(this, 16);
        generateBtn.setLayoutParams(blp);
        generateBtn.setOnClickListener(v -> generate());
        inputCard.addView(generateBtn);

        content.addView(inputCard);

        statusText = new TextView(this);
        statusText.setTextColor(UIHelper.TEXT_SECONDARY);
        statusText.setTextSize(13);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, UIHelper.dp(this, 12), 0, UIHelper.dp(this, 4));
        content.addView(statusText);

        // Result card
        resultCard = UIHelper.card(this);
        resultCard.setVisibility(View.GONE);

        titleView = new TextView(this);
        titleView.setTextColor(UIHelper.ACCENT_PURPLE);
        titleView.setTextSize(22);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4));
        resultCard.addView(titleView);

        View divider = new View(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                UIHelper.dp(this, 60), UIHelper.dp(this, 2));
        dlp.gravity = Gravity.CENTER_HORIZONTAL;
        dlp.bottomMargin = UIHelper.dp(this, 12);
        divider.setLayoutParams(dlp);
        divider.setBackgroundColor(UIHelper.ACCENT_PURPLE);
        resultCard.addView(divider);

        resultCard.addView(sectionHeader("主歌", UIHelper.ACCENT_BLUE));
        verseView = lyricsText();
        resultCard.addView(verseView);

        resultCard.addView(sectionHeader("副歌", UIHelper.ACCENT_ORANGE));
        chorusView = lyricsText();
        resultCard.addView(chorusView);

        bridgeSection = new LinearLayout(this);
        bridgeSection.setOrientation(LinearLayout.VERTICAL);
        bridgeSection.addView(sectionHeader("Bridge", UIHelper.ACCENT_GREEN));
        bridgeView = lyricsText();
        bridgeSection.addView(bridgeView);
        resultCard.addView(bridgeSection);

        shareBtn = new Button(this);
        shareBtn.setText("\uD83D\uDCE4 分享歌詞");
        shareBtn.setTextColor(Color.WHITE);
        shareBtn.setTextSize(15);
        shareBtn.setTypeface(null, Typeface.BOLD);
        shareBtn.setAllCaps(false);
        shareBtn.setBackground(UIHelper.roundRect(UIHelper.ACCENT_BLUE, 14, this));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 46));
        slp.topMargin = UIHelper.dp(this, 16);
        shareBtn.setLayoutParams(slp);
        shareBtn.setOnClickListener(v -> share());
        resultCard.addView(shareBtn);

        content.addView(resultCard);
        setContentView(root);
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(UIHelper.TEXT_SECONDARY);
        tv.setTextSize(13);
        tv.setPadding(UIHelper.dp(this, 4), UIHelper.dp(this, 10), 0, UIHelper.dp(this, 4));
        return tv;
    }

    private TextView sectionHeader(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(13);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setAllCaps(true);
        tv.setLetterSpacing(0.1f);
        tv.setPadding(UIHelper.dp(this, 10), UIHelper.dp(this, 14), 0, UIHelper.dp(this, 4));
        return tv;
    }

    private TextView lyricsText() {
        TextView tv = new TextView(this);
        tv.setTextColor(UIHelper.TEXT_PRIMARY);
        tv.setTextSize(16);
        tv.setLineSpacing(UIHelper.dp(this, 4), 1.25f);
        int p = UIHelper.dp(this, 12);
        tv.setPadding(p, p, p, p);
        tv.setBackground(UIHelper.roundRect(UIHelper.BG_INPUT, 12, this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        return tv;
    }

    private LinearLayout wrapSpinner(Spinner sp) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackground(UIHelper.roundRect(UIHelper.BG_INPUT, 12, this));
        int h = UIHelper.dp(this, 6);
        wrap.setPadding(UIHelper.dp(this, 8), h, UIHelper.dp(this, 8), h);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrap.setLayoutParams(lp);
        wrap.addView(sp);
        return wrap;
    }

    private Spinner newSpinner(String[] items) {
        final Context ctx = this;
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(UIHelper.TEXT_PRIMARY);
                v.setTextSize(15);
                return v;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextColor(UIHelper.TEXT_PRIMARY);
                v.setBackgroundColor(UIHelper.BG_CARD_ALT);
                v.setPadding(UIHelper.dp(ctx, 16), UIHelper.dp(ctx, 12),
                        UIHelper.dp(ctx, 16), UIHelper.dp(ctx, 12));
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);
        sp.setPopupBackgroundDrawable(UIHelper.roundRect(UIHelper.BG_CARD_ALT, 10, this));
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
            titleView.setText("《" + currentTitle + "》");
            verseView.setText(verse);
            chorusView.setText(chorus);
            if (!bridgeLine.isEmpty()) {
                bridgeView.setText(bridgeLine);
                bridgeSection.setVisibility(View.VISIBLE);
            } else {
                bridgeSection.setVisibility(View.GONE);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("《").append(currentTitle).append("》\n\n");
            sb.append("[主歌]\n").append(verse).append("\n\n");
            sb.append("[副歌]\n").append(chorus);
            if (!bridgeLine.isEmpty()) {
                sb.append("\n\n[Bridge]\n").append(bridgeLine);
            }
            currentLyrics = sb.toString();
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
