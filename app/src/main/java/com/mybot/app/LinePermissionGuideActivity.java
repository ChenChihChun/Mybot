package com.mybot.app;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Guides the user through enabling NotificationListenerService access.
 * Handles Android 13+ "Restricted Settings" for sideloaded APKs.
 */
public class LinePermissionGuideActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        LinearLayout root = UIHelper.pageRoot(this);

        LinearLayout topBar = UIHelper.topBar(this, "LINE 消費監聽設定");
        TextView backBtn = new TextView(this);
        backBtn.setText("\u2190");
        backBtn.setTextSize(22);
        backBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        backBtn.setPadding(0, 0, UIHelper.dp(this, 16), 0);
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn, 0);
        root.addView(topBar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 20);
        content.setPadding(p, p, p, UIHelper.dp(this, 40));

        // Status card
        LinearLayout statusCard = UIHelper.card(this);
        TextView statusTitle = new TextView(this);
        statusTitle.setTextSize(16);
        statusTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        statusTitle.setPadding(0, 0, 0, UIHelper.dp(this, 8));

        TextView statusDesc = new TextView(this);
        statusDesc.setTextSize(14);
        statusDesc.setTextColor(UIHelper.TEXT_SECONDARY);
        statusDesc.setLineSpacing(UIHelper.dp(this, 4), 1f);

        boolean enabled = LineExpenseListenerService.isEnabled(this);
        if (enabled) {
            statusTitle.setText("✓ 已啟用 LINE 消費監聽");
            statusTitle.setTextColor(UIHelper.ACCENT_GREEN);
            statusDesc.setText("Mybot 正在監聽 LINE 消費訊息，收到消費通知時會自動記錄到「消費紀錄」。\n\n點擊下方按鈕可管理通知存取權限。");
        } else {
            statusTitle.setText("✗ 尚未啟用");
            statusTitle.setTextColor(UIHelper.ACCENT_RED);
            statusDesc.setText("需要開啟「通知存取權」才能讓 Mybot 監聽 LINE 消費訊息。\n\n請依照下方步驟操作：");
        }

        statusCard.addView(statusTitle);
        statusCard.addView(statusDesc);
        content.addView(statusCard);

        // Step-by-step guide
        content.addView(UIHelper.sectionHeader(this, "開啟步驟"));

        // Android 13+ restricted settings warning
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LinearLayout warningCard = UIHelper.card(this);
            warningCard.setBackgroundColor(0xFF2A1F00);
            TextView warningTitle = new TextView(this);
            warningTitle.setText("⚠  Android 13+ 受限設定");
            warningTitle.setTextSize(14);
            warningTitle.setTextColor(UIHelper.ACCENT_ORANGE);
            warningTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            warningTitle.setPadding(0, 0, 0, UIHelper.dp(this, 6));

            TextView warningDesc = new TextView(this);
            warningDesc.setTextSize(13);
            warningDesc.setTextColor(UIHelper.TEXT_SECONDARY);
            warningDesc.setLineSpacing(UIHelper.dp(this, 4), 1f);
            warningDesc.setText(
                "由於 Mybot 為手動安裝（非 Play Store），Android 預設會擋住通知存取權。\n\n" +
                "需先「允許受限設定」才能繼續：\n\n" +
                "1. 進入 設定 → 應用程式 → Mybot\n" +
                "2. 點右上角 ⋮ 選單\n" +
                "3. 選「允許受限設定」\n" +
                "4. 驗證 PIN 或指紋\n\n" +
                "完成後再點下方「開啟通知存取設定」。"
            );

            warningCard.addView(warningTitle);
            warningCard.addView(warningDesc);
            content.addView(warningCard);

            addSpace(content, 8);
        }

        // Normal steps card
        LinearLayout stepsCard = UIHelper.card(this);
        String[][] steps = {
            {"1", "開啟通知存取設定（點下方按鈕）"},
            {"2", "在清單中找到「Mybot」"},
            {"3", "開啟開關，系統會詢問確認"},
            {"4", "返回此頁確認狀態變為「已啟用」"},
        };
        for (String[] step : steps) {
            stepsCard.addView(makeStepRow(step[0], step[1]));
        }
        content.addView(stepsCard);

        addSpace(content, 20);

        // Open settings button
        android.widget.Button openBtn = UIHelper.primaryButton(this, "開啟通知存取設定");
        openBtn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception e) {
                android.widget.Toast.makeText(this,
                        "請手動前往：設定 → 應用程式 → 特殊應用程式存取 → 通知存取",
                        android.widget.Toast.LENGTH_LONG).show();
            }
        });
        content.addView(openBtn);

        addSpace(content, 12);

        // Info about what's monitored
        content.addView(UIHelper.sectionHeader(this, "監聽說明"));
        LinearLayout infoCard = UIHelper.card(this);
        TextView infoText = new TextView(this);
        infoText.setTextSize(13);
        infoText.setTextColor(UIHelper.TEXT_SECONDARY);
        infoText.setLineSpacing(UIHelper.dp(this, 4), 1f);
        infoText.setText(
            "• 只處理 LINE 的銀行/支付消費通知\n" +
            "• 自動辨識金額、商家、分類（餐飲/交通/購物等）\n" +
            "• 60 秒內相同金額+商家只記一筆（防重複）\n" +
            "• 記帳後推送通知，點擊可直接編輯\n" +
            "• 其他 LINE 訊息（朋友聊天等）不受影響\n\n" +
            "支援格式：LINE Pay、玉山、國泰、台新、中信、富邦等主要銀行"
        );
        infoCard.addView(infoText);
        content.addView(infoCard);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebuild UI to reflect updated permission status
        recreate();
    }

    private LinearLayout makeStepRow(String number, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.TOP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, UIHelper.dp(this, 12));
        row.setLayoutParams(lp);

        TextView num = new TextView(this);
        num.setText(number);
        num.setTextSize(14);
        num.setTextColor(UIHelper.ACCENT_BLUE);
        num.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        num.setGravity(Gravity.CENTER);
        num.setBackground(UIHelper.roundRect(0xFF0D2137, 12, this));
        LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(
                UIHelper.dp(this, 28), UIHelper.dp(this, 28));
        numLp.setMargins(0, 0, UIHelper.dp(this, 12), 0);
        num.setLayoutParams(numLp);

        TextView desc = new TextView(this);
        desc.setText(text);
        desc.setTextSize(14);
        desc.setTextColor(UIHelper.TEXT_PRIMARY);
        desc.setLineSpacing(UIHelper.dp(this, 3), 1f);
        desc.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        row.addView(num);
        row.addView(desc);
        return row;
    }

    private void addSpace(LinearLayout parent, int dp) {
        android.view.View space = new android.view.View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, dp)));
        parent.addView(space);
    }
}
