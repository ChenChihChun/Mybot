package com.mybot.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AddHabitActivity extends AppCompatActivity {

    private HabitDbHelper dbHelper;
    private long editId = -1;

    private EditText nameInput;
    private String selectedIcon = "\uD83C\uDFAF";
    private int selectedColor = UIHelper.ACCENT_BLUE;

    private static final String[] ICONS = {
            "\uD83D\uDCDA", "\uD83E\uDDD8\u200D\u2642\uFE0F", "\uD83C\uDFC3\u200D\u2642\uFE0F",
            "\uD83D\uDCA7", "\uD83C\uDFB5", "\u270D\uFE0F", "\uD83C\uDF31", "\uD83D\uDD2C"
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

        dbHelper = new HabitDbHelper(this);
        editId = getIntent().getLongExtra("habit_id", -1);

        LinearLayout root = UIHelper.pageRoot(this);

        String barTitle = editId > 0 ? "\u7DE8\u8F2F\u7FD2\u6163" : "\u65B0\u589E\u7FD2\u6163";
        LinearLayout topBar = UIHelper.topBar(this, barTitle);
        TextView backBtn = new TextView(this);
        backBtn.setText("\u2190");
        backBtn.setTextSize(22);
        backBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        backBtn.setPadding(0, 0, UIHelper.dp(this, 16), 0);
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn, 0);
        root.addView(topBar);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 16);
        content.setPadding(p, p, p, p);

        // Name
        content.addView(label("\u7FD2\u6163\u540D\u7A31"));
        nameInput = UIHelper.styledInput(this, "\u4F8B\u5982\uFF1A\u95B1\u8B80\u3001\u904B\u52D5\u3001\u5192\u60F3...");
        content.addView(nameInput);

        // Icon picker
        content.addView(label("\u5716\u793A"));
        content.addView(buildIconPicker());

        // Color picker
        content.addView(label("\u984F\u8272"));
        content.addView(buildColorPicker());

        // Save button
        android.widget.Button saveBtn = UIHelper.primaryButton(this, "\u5132\u5B58");
        saveBtn.setOnClickListener(v -> save());
        content.addView(saveBtn);

        // Load edit data
        if (editId > 0) {
            java.util.List<HabitDbHelper.Habit> all = dbHelper.getAllHabits();
            for (HabitDbHelper.Habit h : all) {
                if (h.id == editId) {
                    nameInput.setText(h.name);
                    if (h.icon != null && !h.icon.isEmpty()) selectedIcon = h.icon;
                    selectedColor = h.color;
                    break;
                }
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
            dot.setAlpha(color == selectedColor ? 1f : 0.5f);
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
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "\u8ACB\u8F38\u5165\u7FD2\u6163\u540D\u7A31", Toast.LENGTH_SHORT).show();
            return;
        }

        if (editId > 0) {
            dbHelper.updateHabit(editId, name, selectedIcon, selectedColor);
            AppLog.i("Habit", "編輯習慣: " + name);
        } else {
            dbHelper.insertHabit(name, selectedIcon, selectedColor);
            AppLog.i("Habit", "新增習慣: " + name);
        }
        finish();
    }
}
