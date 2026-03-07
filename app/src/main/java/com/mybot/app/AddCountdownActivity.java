package com.mybot.app;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;
import java.util.Locale;

public class AddCountdownActivity extends AppCompatActivity {

    private CountdownDbHelper dbHelper;
    private long editId = -1;

    private EditText titleInput;
    private TextView dateBtn;
    private EditText noteInput;
    private Switch notifySwitch;

    private String selectedDate = "";
    private String selectedIcon = "\uD83C\uDFAF";
    private int selectedColor = UIHelper.ACCENT_BLUE;

    private static final String[] ICONS = {
            "\uD83C\uDF82", "\uD83C\uDF84", "\u2708\uFE0F", "\uD83D\uDCDD",
            "\uD83C\uDFAF", "\uD83D\uDC8D", "\uD83C\uDF93", "\u2B50"
    };
    private static final int[] COLORS = {
            Color.parseColor("#4FC3F7"), Color.parseColor("#66BB6A"),
            Color.parseColor("#EF5350"), Color.parseColor("#FFA726"),
            Color.parseColor("#AB47BC"), Color.parseColor("#FF7043"),
            Color.parseColor("#26C6DA"), Color.parseColor("#EC407A")
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        dbHelper = new CountdownDbHelper(this);
        editId = getIntent().getLongExtra("countdown_id", -1);

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        String barTitle = editId > 0 ? "\u7DE8\u8F2F\u5012\u6578\u65E5" : "\u65B0\u589E\u5012\u6578\u65E5";
        LinearLayout topBar = UIHelper.topBar(this, barTitle);
        TextView backBtn = new TextView(this);
        backBtn.setText("\u2190");
        backBtn.setTextSize(22);
        backBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        backBtn.setPadding(0, 0, UIHelper.dp(this, 16), 0);
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn, 0);
        root.addView(topBar);

        // Content
        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 16);
        content.setPadding(p, p, p, p);

        // Title
        content.addView(label("\u6A19\u984C"));
        titleInput = UIHelper.styledInput(this, "\u4F8B\u5982\uFF1A\u751F\u65E5\u3001\u65C5\u884C\u3001\u8003\u8A66...");
        content.addView(titleInput);

        // Date picker
        content.addView(label("\u76EE\u6A19\u65E5\u671F"));
        dateBtn = new TextView(this);
        dateBtn.setText("\u9EDE\u6B64\u9078\u64C7\u65E5\u671F");
        dateBtn.setTextSize(16);
        dateBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        dateBtn.setBackground(UIHelper.roundRectStroke(UIHelper.BG_INPUT, Color.parseColor("#2E4050"), 14, 1, this));
        int dp = UIHelper.dp(this, 16);
        dateBtn.setPadding(dp, UIHelper.dp(this, 14), dp, UIHelper.dp(this, 14));
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateLp.setMargins(0, UIHelper.dp(this, 6), 0, UIHelper.dp(this, 6));
        dateBtn.setLayoutParams(dateLp);
        dateBtn.setOnClickListener(v -> showDatePicker());
        content.addView(dateBtn);

        // Note
        content.addView(label("\u5099\u8A3B"));
        noteInput = UIHelper.styledInput(this, "\u9078\u586B");
        content.addView(noteInput);

        // Icon picker
        content.addView(label("\u5716\u793A"));
        content.addView(buildIconPicker());

        // Color picker
        content.addView(label("\u984F\u8272"));
        content.addView(buildColorPicker());

        // Notify toggle
        LinearLayout notifyRow = new LinearLayout(this);
        notifyRow.setOrientation(LinearLayout.HORIZONTAL);
        notifyRow.setGravity(Gravity.CENTER_VERTICAL);
        notifyRow.setPadding(0, UIHelper.dp(this, 12), 0, UIHelper.dp(this, 12));

        TextView notifyLabel = new TextView(this);
        notifyLabel.setText("\u5230\u671F\u63D0\u9192");
        notifyLabel.setTextSize(15);
        notifyLabel.setTextColor(UIHelper.TEXT_PRIMARY);
        notifyLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        notifySwitch = new Switch(this);
        notifySwitch.setChecked(false);

        notifyRow.addView(notifyLabel);
        notifyRow.addView(notifySwitch);
        content.addView(notifyRow);

        // Save button
        android.widget.Button saveBtn = UIHelper.primaryButton(this, "\u5132\u5B58");
        saveBtn.setOnClickListener(v -> save());
        content.addView(saveBtn);

        // Load edit data
        if (editId > 0) {
            CountdownDbHelper.Countdown cd = dbHelper.getById(editId);
            if (cd != null) {
                titleInput.setText(cd.title);
                selectedDate = cd.targetDate;
                dateBtn.setText(cd.targetDate);
                noteInput.setText(cd.note);
                if (cd.icon != null && !cd.icon.isEmpty()) selectedIcon = cd.icon;
                selectedColor = cd.color;
                notifySwitch.setChecked(cd.notify);
            }
        }

        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(UIHelper.TEXT_SECONDARY);
        tv.setPadding(UIHelper.dp(this, 4), UIHelper.dp(this, 12), 0, UIHelper.dp(this, 4));
        return tv;
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
            dateBtn.setText(selectedDate);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private LinearLayout buildIconPicker() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 8));
        final TextView[] iconViews = new TextView[ICONS.length];

        for (int i = 0; i < ICONS.length; i++) {
            final String icon = ICONS[i];
            TextView tv = new TextView(this);
            tv.setText(icon);
            tv.setTextSize(26);
            tv.setGravity(Gravity.CENTER);
            int size = UIHelper.dp(this, 44);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(0, 0, UIHelper.dp(this, 8), 0);
            tv.setLayoutParams(lp);
            tv.setBackground(UIHelper.roundRect(
                    icon.equals(selectedIcon) ? UIHelper.BG_CARD_ALT : UIHelper.BG_INPUT, 12, this));
            iconViews[i] = tv;

            tv.setOnClickListener(v -> {
                selectedIcon = icon;
                for (int j = 0; j < iconViews.length; j++) {
                    iconViews[j].setBackground(UIHelper.roundRect(
                            ICONS[j].equals(selectedIcon) ? UIHelper.BG_CARD_ALT : UIHelper.BG_INPUT, 12, this));
                }
            });
            row.addView(tv);
        }
        return row;
    }

    private LinearLayout buildColorPicker() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 8));
        final android.view.View[] colorViews = new android.view.View[COLORS.length];

        for (int i = 0; i < COLORS.length; i++) {
            final int color = COLORS[i];
            android.view.View dot = new android.view.View(this);
            int size = UIHelper.dp(this, 32);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(0, 0, UIHelper.dp(this, 10), 0);
            dot.setLayoutParams(lp);
            dot.setBackground(UIHelper.roundRect(color, 16, this));
            if (color == selectedColor) {
                dot.setAlpha(1f);
            } else {
                dot.setAlpha(0.5f);
            }
            colorViews[i] = dot;

            dot.setOnClickListener(v -> {
                selectedColor = color;
                for (int j = 0; j < colorViews.length; j++) {
                    colorViews[j].setAlpha(COLORS[j] == selectedColor ? 1f : 0.5f);
                }
            });
            row.addView(dot);
        }
        return row;
    }

    private void save() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "\u8ACB\u8F38\u5165\u6A19\u984C", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedDate.isEmpty()) {
            Toast.makeText(this, "\u8ACB\u9078\u64C7\u65E5\u671F", Toast.LENGTH_SHORT).show();
            return;
        }

        String note = noteInput.getText().toString().trim();
        boolean notify = notifySwitch.isChecked();

        if (editId > 0) {
            dbHelper.update(editId, title, selectedDate, note, selectedIcon, selectedColor, notify);
        } else {
            dbHelper.insert(title, selectedDate, note, selectedIcon, selectedColor, notify);
        }
        finish();
    }
}
