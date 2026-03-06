package com.mybot.app;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AddExpenseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("手動新增消費");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 32);

        EditText amountInput = createInput("金額");
        EditText merchantInput = createInput("商家");
        EditText categoryInput = createInput("類別");
        EditText descInput = createInput("描述");

        Button saveBtn = new Button(this);
        saveBtn.setText("儲存");
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

        Button cancelBtn = new Button(this);
        cancelBtn.setText("取消");
        cancelBtn.setOnClickListener(v -> finish());

        root.addView(title);
        root.addView(amountInput);
        root.addView(merchantInput);
        root.addView(categoryInput);
        root.addView(descInput);
        root.addView(saveBtn);
        root.addView(cancelBtn);

        setContentView(root);
    }

    private EditText createInput(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return et;
    }
}
