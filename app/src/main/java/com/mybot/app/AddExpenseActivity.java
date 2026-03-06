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

public class AddExpenseActivity extends AppCompatActivity {

    private boolean aiRequesting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.parseColor("#0A1520"));

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "新增消費");
        Button closeBtn = new Button(this);
        closeBtn.setText("X");
        closeBtn.setTextColor(UIHelper.TEXT_SECONDARY);
        closeBtn.setTextSize(16);
        closeBtn.setBackground(null);
        closeBtn.setStateListAnimator(null);
        closeBtn.setOnClickListener(v -> finish());
        topBar.addView(closeBtn);

        // Form area
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 24);
        form.setPadding(p, UIHelper.dp(this, 16), p, p);

        // Amount section
        LinearLayout amountCard = UIHelper.card(this);
        LinearLayout.LayoutParams acLp = (LinearLayout.LayoutParams) amountCard.getLayoutParams();
        acLp.setMargins(0, 0, 0, UIHelper.dp(this, 16));
        amountCard.setLayoutParams(acLp);

        TextView amountLabel = new TextView(this);
        amountLabel.setText("金額 (TWD)");
        amountLabel.setTextSize(12);
        amountLabel.setTextColor(UIHelper.TEXT_SECONDARY);

        EditText amountInput = UIHelper.styledInput(this, "0");
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setTextSize(28);
        amountInput.setTextColor(UIHelper.ACCENT_RED);
        amountInput.setGravity(Gravity.CENTER);

        amountCard.addView(amountLabel);
        amountCard.addView(amountInput);

        // Detail fields
        TextView detailLabel = new TextView(this);
        detailLabel.setText("消費明細");
        detailLabel.setTextSize(12);
        detailLabel.setTextColor(UIHelper.TEXT_SECONDARY);
        detailLabel.setPadding(UIHelper.dp(this, 4), 0, 0, UIHelper.dp(this, 8));

        EditText merchantInput = UIHelper.styledInput(this, "商家名稱");
        EditText descInput = UIHelper.styledInput(this, "備註描述");

        // Category with AI button
        LinearLayout categoryRow = new LinearLayout(this);
        categoryRow.setOrientation(LinearLayout.HORIZONTAL);
        categoryRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams catRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        catRowLp.setMargins(0, UIHelper.dp(this, 6), 0, UIHelper.dp(this, 6));
        categoryRow.setLayoutParams(catRowLp);

        EditText categoryInput = UIHelper.styledInput(this, "類別 (AI 自動建議)");
        LinearLayout.LayoutParams catInputLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        catInputLp.setMargins(0, 0, UIHelper.dp(this, 8), 0);
        categoryInput.setLayoutParams(catInputLp);

        // AI suggest button
        Button aiBtn = new Button(this);
        aiBtn.setText("AI");
        aiBtn.setTextColor(Color.WHITE);
        aiBtn.setTextSize(13);
        aiBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        aiBtn.setAllCaps(false);
        aiBtn.setBackground(UIHelper.roundRect(UIHelper.ACCENT_BLUE, 10, this));
        aiBtn.setStateListAnimator(null);
        aiBtn.setElevation(0);
        int aiPad = UIHelper.dp(this, 14);
        aiBtn.setPadding(aiPad, 0, aiPad, 0);
        aiBtn.setMinimumWidth(0);
        aiBtn.setMinWidth(0);
        LinearLayout.LayoutParams aiBtnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UIHelper.dp(this, 48));
        aiBtn.setLayoutParams(aiBtnLp);

        // AI status hint
        TextView aiHint = new TextView(this);
        aiHint.setTextSize(12);
        aiHint.setTextColor(UIHelper.TEXT_HINT);
        aiHint.setPadding(UIHelper.dp(this, 4), 0, 0, UIHelper.dp(this, 4));

        aiBtn.setOnClickListener(v -> {
            if (aiRequesting) return;

            String merchant = merchantInput.getText().toString().trim();
            String desc = descInput.getText().toString().trim();
            String amountStr = amountInput.getText().toString().trim();

            if (merchant.isEmpty() && desc.isEmpty()) {
                Toast.makeText(this, "請先填寫商家或描述", Toast.LENGTH_SHORT).show();
                return;
            }

            double amount = 0;
            try { amount = Double.parseDouble(amountStr); } catch (Exception ignored) {}

            aiRequesting = true;
            aiBtn.setText("...");
            aiBtn.setBackground(UIHelper.roundRect(UIHelper.TEXT_HINT, 10, this));
            aiHint.setText("AI 分類中...");
            aiHint.setTextColor(UIHelper.ACCENT_BLUE);

            BridgeClient.categorize(merchant, desc, amount, (category, offline) -> {
                aiRequesting = false;
                aiBtn.setText("AI");
                aiBtn.setBackground(UIHelper.roundRect(UIHelper.ACCENT_BLUE, 10, this));

                if (offline) {
                    aiHint.setText("Bridge 離線，請手動輸入類別");
                    aiHint.setTextColor(UIHelper.ACCENT_ORANGE);
                } else if (category != null && !category.isEmpty()) {
                    categoryInput.setText(category);
                    aiHint.setText("AI 建議: " + category);
                    aiHint.setTextColor(UIHelper.ACCENT_GREEN);
                } else {
                    aiHint.setText("AI 無法判斷，請手動輸入");
                    aiHint.setTextColor(UIHelper.ACCENT_ORANGE);
                }
            });
        });

        categoryRow.addView(categoryInput);
        categoryRow.addView(aiBtn);

        // Buttons
        Button saveBtn = UIHelper.primaryButton(this, "儲存");
        LinearLayout.LayoutParams saveLp = (LinearLayout.LayoutParams) saveBtn.getLayoutParams();
        saveLp.setMargins(0, UIHelper.dp(this, 24), 0, UIHelper.dp(this, 8));
        saveBtn.setLayoutParams(saveLp);
        saveBtn.setOnClickListener(v -> {
            String amountStr = amountInput.getText().toString().trim();
            if (amountStr.isEmpty()) {
                Toast.makeText(this, "請輸入金額", Toast.LENGTH_SHORT).show();
                return;
            }
            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "金額格式錯誤", Toast.LENGTH_SHORT).show();
                return;
            }

            String merchant = merchantInput.getText().toString().trim();
            String category = categoryInput.getText().toString().trim();
            String desc = descInput.getText().toString().trim();

            // If no category, try AI one more time synchronously-ish
            if (category.isEmpty() && !merchant.isEmpty()) {
                Toast.makeText(this, "建議按 AI 按鈕取得類別", Toast.LENGTH_SHORT).show();
            }

            ExpenseDbHelper db = new ExpenseDbHelper(this);
            db.insert(amount, "TWD", category, merchant, desc, "手動", "");
            Toast.makeText(this, "已儲存", Toast.LENGTH_SHORT).show();
            finish();
        });

        Button cancelBtn = UIHelper.outlineButton(this, "取消");
        cancelBtn.setOnClickListener(v -> finish());

        form.addView(amountCard);
        form.addView(detailLabel);
        form.addView(merchantInput);
        form.addView(descInput);
        form.addView(categoryRow);
        form.addView(aiHint);
        form.addView(saveBtn);
        form.addView(cancelBtn);

        scrollView.addView(form);

        root.addView(topBar);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }
}
