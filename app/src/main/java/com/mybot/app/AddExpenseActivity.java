package com.mybot.app;

import android.graphics.Color;
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

        // Amount section with big display
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
        EditText categoryInput = UIHelper.styledInput(this, "類別 (如：餐飲、交通)");
        EditText descInput = UIHelper.styledInput(this, "備註描述");

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
        form.addView(categoryInput);
        form.addView(descInput);
        form.addView(saveBtn);
        form.addView(cancelBtn);

        scrollView.addView(form);

        root.addView(topBar);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }
}
