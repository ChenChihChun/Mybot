package com.mybot.app;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AddTodoActivity extends AppCompatActivity {

    private final Calendar deadlineCal = Calendar.getInstance();
    private boolean hasDeadline = false;
    private TextView deadlineDisplay;
    private int selectedPriority = 1; // default medium
    private TextView lowBtn, medBtn, highBtn;
    private long editId = -1;

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        editId = getIntent().getLongExtra("todo_id", -1);
        boolean isEdit = editId != -1;

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, isEdit ? "編輯待辦" : "新增待辦");
        Button closeBtn = UIHelper.smallButton(this, "X", UIHelper.TEXT_SECONDARY);
        closeBtn.setOnClickListener(v -> finish());
        topBar.addView(closeBtn);

        // Form
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 24);
        form.setPadding(p, UIHelper.dp(this, 16), p, p);

        // Title
        form.addView(fieldLabel("標題 *"));
        EditText titleInput = UIHelper.styledInput(this, "待辦事項名稱");
        form.addView(titleInput);

        // Description
        form.addView(fieldLabel("描述"));
        EditText descInput = UIHelper.styledInput(this, "補充說明（選填）");
        descInput.setMinLines(2);
        form.addView(descInput);

        // Category
        form.addView(fieldLabel("分類"));
        EditText categoryInput = UIHelper.styledInput(this, "例如：工作、生活、學習");
        form.addView(categoryInput);

        // Priority
        form.addView(fieldLabel("優先等級"));
        LinearLayout priorityRow = new LinearLayout(this);
        priorityRow.setOrientation(LinearLayout.HORIZONTAL);
        priorityRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams prLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        prLp.setMargins(0, UIHelper.dp(this, 6), 0, UIHelper.dp(this, 12));
        priorityRow.setLayoutParams(prLp);

        lowBtn = priorityTab("低", UIHelper.TEXT_HINT);
        medBtn = priorityTab("中", UIHelper.ACCENT_ORANGE);
        highBtn = priorityTab("高", UIHelper.ACCENT_RED);

        lowBtn.setOnClickListener(v -> setPriority(0));
        medBtn.setOnClickListener(v -> setPriority(1));
        highBtn.setOnClickListener(v -> setPriority(2));

        priorityRow.addView(lowBtn);
        priorityRow.addView(medBtn);
        priorityRow.addView(highBtn);
        form.addView(priorityRow);

        // Deadline
        form.addView(fieldLabel("截止日期"));
        LinearLayout deadlineCard = UIHelper.card(this);

        LinearLayout deadlineRow = new LinearLayout(this);
        deadlineRow.setOrientation(LinearLayout.HORIZONTAL);
        deadlineRow.setGravity(Gravity.CENTER_VERTICAL);

        deadlineDisplay = new TextView(this);
        deadlineDisplay.setText("未設定（永久待辦）");
        deadlineDisplay.setTextSize(16);
        deadlineDisplay.setTextColor(UIHelper.TEXT_HINT);
        deadlineDisplay.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button pickBtn = UIHelper.smallButton(this, "選擇", UIHelper.ACCENT_BLUE);
        pickBtn.setOnClickListener(v -> showDatePicker());

        Button clearBtn = UIHelper.smallButton(this, "清除", UIHelper.TEXT_HINT);
        LinearLayout.LayoutParams clLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clLp.setMargins(UIHelper.dp(this, 8), 0, 0, 0);
        clearBtn.setLayoutParams(clLp);
        clearBtn.setOnClickListener(v -> {
            hasDeadline = false;
            deadlineDisplay.setText("未設定（永久待辦）");
            deadlineDisplay.setTextColor(UIHelper.TEXT_HINT);
        });

        deadlineRow.addView(deadlineDisplay);
        deadlineRow.addView(pickBtn);
        deadlineRow.addView(clearBtn);
        deadlineCard.addView(deadlineRow);
        form.addView(deadlineCard);

        // Buttons
        Button saveBtn = UIHelper.primaryButton(this, isEdit ? "儲存修改" : "新增待辦");
        LinearLayout.LayoutParams saveLp = (LinearLayout.LayoutParams) saveBtn.getLayoutParams();
        saveLp.setMargins(0, UIHelper.dp(this, 24), 0, UIHelper.dp(this, 8));
        saveBtn.setLayoutParams(saveLp);
        saveBtn.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            if (title.isEmpty()) {
                AppLog.w("Todo", "儲存失敗: 標題為空");
                Toast.makeText(this, "請輸入標題", Toast.LENGTH_SHORT).show();
                return;
            }

            String desc = descInput.getText().toString().trim();
            String category = categoryInput.getText().toString().trim();
            Long deadline = hasDeadline ? deadlineCal.getTimeInMillis() : null;

            TodoDbHelper db = new TodoDbHelper(this);
            if (isEdit) {
                db.update(editId, title, desc, selectedPriority, category, deadline);
                AppLog.i("Todo", "更新待辦: " + title);
                Toast.makeText(this, "已更新", Toast.LENGTH_SHORT).show();
            } else {
                db.insert(title, desc, selectedPriority, category, deadline);
                AppLog.i("Todo", "新增待辦: " + title);
                Toast.makeText(this, "已新增", Toast.LENGTH_SHORT).show();
            }
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

        setPriority(1);

        // Load existing data if editing
        if (isEdit) {
            TodoDbHelper db = new TodoDbHelper(this);
            TodoDbHelper.Todo todo = db.getById(editId);
            if (todo == null) {
                AppLog.e("Todo", "找不到待辦 id=" + editId);
            }
            if (todo != null) {
                titleInput.setText(todo.title);
                descInput.setText(todo.description);
                categoryInput.setText(todo.category);
                setPriority(todo.priority);
                if (todo.deadline != null) {
                    hasDeadline = true;
                    deadlineCal.setTimeInMillis(todo.deadline);
                    deadlineDisplay.setText(sdf.format(deadlineCal.getTime()));
                    deadlineDisplay.setTextColor(UIHelper.TEXT_PRIMARY);
                }
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

    private TextView priorityTab(String text, int color) {
        TextView tab = new TextView(this);
        tab.setText(text);
        tab.setTextSize(14);
        tab.setGravity(Gravity.CENTER);
        tab.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        int h = UIHelper.dp(this, 24);
        int v = UIHelper.dp(this, 10);
        tab.setPadding(h, v, h, v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        int m = UIHelper.dp(this, 4);
        lp.setMargins(m, 0, m, 0);
        tab.setLayoutParams(lp);
        return tab;
    }

    private void setPriority(int priority) {
        selectedPriority = priority;

        lowBtn.setBackground(UIHelper.roundRect(priority == 0 ? UIHelper.TEXT_HINT : UIHelper.BG_CARD_ALT, 10, this));
        lowBtn.setTextColor(priority == 0 ? Color.WHITE : UIHelper.TEXT_HINT);

        medBtn.setBackground(UIHelper.roundRect(priority == 1 ? UIHelper.ACCENT_ORANGE : UIHelper.BG_CARD_ALT, 10, this));
        medBtn.setTextColor(priority == 1 ? Color.WHITE : UIHelper.ACCENT_ORANGE);

        highBtn.setBackground(UIHelper.roundRect(priority == 2 ? UIHelper.ACCENT_RED : UIHelper.BG_CARD_ALT, 10, this));
        highBtn.setTextColor(priority == 2 ? Color.WHITE : UIHelper.ACCENT_RED);
    }

    private void showDatePicker() {
        new DatePickerDialog(this, (view, year, month, day) -> {
            deadlineCal.set(Calendar.YEAR, year);
            deadlineCal.set(Calendar.MONTH, month);
            deadlineCal.set(Calendar.DAY_OF_MONTH, day);
            deadlineCal.set(Calendar.HOUR_OF_DAY, 23);
            deadlineCal.set(Calendar.MINUTE, 59);
            deadlineCal.set(Calendar.SECOND, 59);
            hasDeadline = true;
            deadlineDisplay.setText(sdf.format(deadlineCal.getTime()));
            deadlineDisplay.setTextColor(UIHelper.TEXT_PRIMARY);
        }, deadlineCal.get(Calendar.YEAR),
                deadlineCal.get(Calendar.MONTH),
                deadlineCal.get(Calendar.DAY_OF_MONTH)).show();
    }
}
