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

import java.util.Locale;

public class FitnessProfileActivity extends AppCompatActivity {

    private int selectedGoalIdx = 4; // default: general
    private int selectedLevelIdx = 0; // default: beginner
    private final String[] goalKeys = {"reduce_fat", "build_muscle", "flexibility", "stamina", "general"};
    private final String[] goalLabels = {"減脂瘦身", "增肌塑形", "柔軟度", "體能耐力", "一般健身"};
    private final String[] levelKeys = {"beginner", "intermediate", "advanced"};
    private final String[] levelLabels = {"初學者", "中階", "進階"};

    private TextView[] goalButtons;
    private TextView[] levelButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        LinearLayout root = UIHelper.pageRoot(this);

        LinearLayout topBar = UIHelper.topBar(this, "個人檔案");
        Button closeBtn = UIHelper.smallButton(this, "X", UIHelper.TEXT_SECONDARY);
        closeBtn.setOnClickListener(v -> finish());
        topBar.addView(closeBtn);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 24);
        form.setPadding(p, UIHelper.dp(this, 16), p, p);

        // Height
        form.addView(fieldLabel("身高 (cm)"));
        EditText heightInput = UIHelper.styledInput(this, "170");
        heightInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(heightInput);

        // Weight
        form.addView(fieldLabel("體重 (kg)"));
        EditText weightInput = UIHelper.styledInput(this, "70");
        weightInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(weightInput);

        // Goal
        form.addView(fieldLabel("運動目標"));
        LinearLayout goalRow = new LinearLayout(this);
        goalRow.setOrientation(LinearLayout.HORIZONTAL);
        goalRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams grLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        grLp.setMargins(0, UIHelper.dp(this, 6), 0, UIHelper.dp(this, 6));
        goalRow.setLayoutParams(grLp);

        // Split into 2 rows for 5 goals
        LinearLayout goalContainer = new LinearLayout(this);
        goalContainer.setOrientation(LinearLayout.VERTICAL);

        LinearLayout goalRow1 = new LinearLayout(this);
        goalRow1.setOrientation(LinearLayout.HORIZONTAL);
        goalRow1.setGravity(Gravity.CENTER);

        LinearLayout goalRow2 = new LinearLayout(this);
        goalRow2.setOrientation(LinearLayout.HORIZONTAL);
        goalRow2.setGravity(Gravity.CENTER);
        goalRow2.setPadding(0, UIHelper.dp(this, 6), 0, 0);

        goalButtons = new TextView[goalLabels.length];
        for (int i = 0; i < goalLabels.length; i++) {
            goalButtons[i] = selectTab(goalLabels[i]);
            final int idx = i;
            goalButtons[i].setOnClickListener(v -> selectGoal(idx));
            if (i < 3) goalRow1.addView(goalButtons[i]);
            else goalRow2.addView(goalButtons[i]);
        }
        goalContainer.addView(goalRow1);
        goalContainer.addView(goalRow2);
        form.addView(goalContainer);

        // Level
        form.addView(fieldLabel("體能等級"));
        LinearLayout levelRow = new LinearLayout(this);
        levelRow.setOrientation(LinearLayout.HORIZONTAL);
        levelRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lrLp.setMargins(0, UIHelper.dp(this, 6), 0, UIHelper.dp(this, 12));
        levelRow.setLayoutParams(lrLp);

        levelButtons = new TextView[levelLabels.length];
        for (int i = 0; i < levelLabels.length; i++) {
            levelButtons[i] = selectTab(levelLabels[i]);
            final int idx = i;
            levelButtons[i].setOnClickListener(v -> selectLevel(idx));
            levelRow.addView(levelButtons[i]);
        }
        form.addView(levelRow);

        // BMI display
        LinearLayout bmiCard = UIHelper.card(this);
        TextView bmiLabel = new TextView(this);
        bmiLabel.setText("BMI");
        bmiLabel.setTextSize(12);
        bmiLabel.setTextColor(UIHelper.TEXT_SECONDARY);

        TextView bmiValue = new TextView(this);
        bmiValue.setText("--");
        bmiValue.setTextSize(28);
        bmiValue.setTextColor(UIHelper.ACCENT_BLUE);
        bmiValue.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        bmiValue.setGravity(Gravity.CENTER);
        bmiValue.setPadding(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4));

        TextView bmiHint = new TextView(this);
        bmiHint.setText("正常範圍: 18.5 - 24.9");
        bmiHint.setTextSize(11);
        bmiHint.setTextColor(UIHelper.TEXT_HINT);
        bmiHint.setGravity(Gravity.CENTER);

        bmiCard.addView(bmiLabel);
        bmiCard.addView(bmiValue);
        bmiCard.addView(bmiHint);
        form.addView(bmiCard);

        // Auto calculate BMI
        android.text.TextWatcher bmiWatcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                try {
                    double h = Double.parseDouble(heightInput.getText().toString().trim());
                    double w = Double.parseDouble(weightInput.getText().toString().trim());
                    if (h > 0) {
                        double bmi = w / Math.pow(h / 100.0, 2);
                        bmiValue.setText(String.format(Locale.getDefault(), "%.1f", bmi));
                        if (bmi < 18.5) bmiValue.setTextColor(UIHelper.ACCENT_ORANGE);
                        else if (bmi <= 24.9) bmiValue.setTextColor(UIHelper.ACCENT_GREEN);
                        else bmiValue.setTextColor(UIHelper.ACCENT_RED);
                    }
                } catch (Exception ignored) {
                    bmiValue.setText("--");
                    bmiValue.setTextColor(UIHelper.ACCENT_BLUE);
                }
            }
        };
        heightInput.addTextChangedListener(bmiWatcher);
        weightInput.addTextChangedListener(bmiWatcher);

        // Save button
        Button saveBtn = UIHelper.primaryButton(this, "儲存");
        LinearLayout.LayoutParams saveLp = (LinearLayout.LayoutParams) saveBtn.getLayoutParams();
        saveLp.setMargins(0, UIHelper.dp(this, 20), 0, UIHelper.dp(this, 8));
        saveBtn.setLayoutParams(saveLp);
        saveBtn.setOnClickListener(v -> {
            String hStr = heightInput.getText().toString().trim();
            String wStr = weightInput.getText().toString().trim();
            if (hStr.isEmpty() || wStr.isEmpty()) {
                Toast.makeText(this, "請輸入身高和體重", Toast.LENGTH_SHORT).show();
                return;
            }
            double h, w;
            try {
                h = Double.parseDouble(hStr);
                w = Double.parseDouble(wStr);
            } catch (Exception e) {
                Toast.makeText(this, "格式錯誤", Toast.LENGTH_SHORT).show();
                return;
            }

            FitnessDbHelper db = new FitnessDbHelper(this);
            db.saveProfile(h, w, goalKeys[selectedGoalIdx], levelKeys[selectedLevelIdx]);
            Toast.makeText(this, "已儲存", Toast.LENGTH_SHORT).show();
            finish();
        });

        Button cancelBtn = UIHelper.outlineButton(this, "取消");
        cancelBtn.setOnClickListener(v -> finish());

        form.addView(saveBtn);
        form.addView(cancelBtn);

        scrollView.addView(form);
        root.addView(topBar);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        selectGoal(4);
        selectLevel(0);

        // Load existing profile
        FitnessDbHelper db = new FitnessDbHelper(this);
        FitnessDbHelper.Profile profile = db.getProfile();
        if (profile != null) {
            heightInput.setText(String.valueOf(profile.heightCm));
            weightInput.setText(String.valueOf(profile.weightKg));
            for (int i = 0; i < goalKeys.length; i++) {
                if (goalKeys[i].equals(profile.goal)) { selectGoal(i); break; }
            }
            for (int i = 0; i < levelKeys.length; i++) {
                if (levelKeys[i].equals(profile.level)) { selectLevel(i); break; }
            }
        }
    }

    private TextView fieldLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(12);
        label.setTextColor(UIHelper.TEXT_SECONDARY);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        label.setPadding(UIHelper.dp(this, 4), UIHelper.dp(this, 12), 0, UIHelper.dp(this, 4));
        return label;
    }

    private TextView selectTab(String text) {
        TextView tab = new TextView(this);
        tab.setText(text);
        tab.setTextSize(13);
        tab.setGravity(Gravity.CENTER);
        tab.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        int h = UIHelper.dp(this, 16);
        int v = UIHelper.dp(this, 10);
        tab.setPadding(h, v, h, v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        int m = UIHelper.dp(this, 3);
        lp.setMargins(m, 0, m, 0);
        tab.setLayoutParams(lp);
        return tab;
    }

    private void selectGoal(int idx) {
        selectedGoalIdx = idx;
        for (int i = 0; i < goalButtons.length; i++) {
            boolean active = i == idx;
            goalButtons[i].setBackground(UIHelper.roundRect(
                    active ? UIHelper.ACCENT_GREEN : UIHelper.BG_CARD_ALT, 10, this));
            goalButtons[i].setTextColor(active ? Color.WHITE : UIHelper.TEXT_SECONDARY);
        }
    }

    private void selectLevel(int idx) {
        selectedLevelIdx = idx;
        int[] colors = {UIHelper.ACCENT_GREEN, UIHelper.ACCENT_ORANGE, UIHelper.ACCENT_RED};
        for (int i = 0; i < levelButtons.length; i++) {
            boolean active = i == idx;
            levelButtons[i].setBackground(UIHelper.roundRect(
                    active ? colors[i] : UIHelper.BG_CARD_ALT, 10, this));
            levelButtons[i].setTextColor(active ? Color.WHITE : UIHelper.TEXT_SECONDARY);
        }
    }
}
