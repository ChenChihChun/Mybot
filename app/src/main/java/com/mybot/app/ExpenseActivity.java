package com.mybot.app;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExpenseActivity extends AppCompatActivity {

    private ExpenseDbHelper dbHelper;
    private ListView listView;
    private ExpenseAdapter adapter;
    private String currentFilter = null;
    private Spinner categorySpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        dbHelper = new ExpenseDbHelper(this);

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "消費紀錄");

        Button reportBtn = UIHelper.smallButton(this, "報表", UIHelper.ACCENT_GREEN);
        reportBtn.setOnClickListener(v -> startActivity(new Intent(this, ReportActivity.class)));

        Button reminderBtn = UIHelper.smallButton(this, "提醒", UIHelper.ACCENT_ORANGE);
        LinearLayout.LayoutParams reminderLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        reminderLp.setMargins(UIHelper.dp(this, 8), 0, 0, 0);
        reminderBtn.setLayoutParams(reminderLp);
        reminderBtn.setOnClickListener(v -> showReminderSettings());

        Button addBtn = UIHelper.smallButton(this, "+ 新增", UIHelper.ACCENT_BLUE);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addLp.setMargins(UIHelper.dp(this, 8), 0, 0, 0);
        addBtn.setLayoutParams(addLp);
        addBtn.setOnClickListener(v -> startActivity(new Intent(this, AddExpenseActivity.class)));

        topBar.addView(reportBtn);
        topBar.addView(reminderBtn);
        topBar.addView(addBtn);

        // Filter bar
        LinearLayout filterBar = new LinearLayout(this);
        filterBar.setOrientation(LinearLayout.HORIZONTAL);
        filterBar.setGravity(Gravity.CENTER_VERTICAL);
        filterBar.setBackgroundColor(UIHelper.BG_CARD);
        int fp = UIHelper.dp(this, 20);
        filterBar.setPadding(fp, UIHelper.dp(this, 12), fp, UIHelper.dp(this, 12));

        TextView filterLabel = new TextView(this);
        filterLabel.setText("類別篩選");
        filterLabel.setTextSize(13);
        filterLabel.setTextColor(UIHelper.TEXT_SECONDARY);
        filterLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        filterLabel.setPadding(0, 0, UIHelper.dp(this, 12), 0);

        categorySpinner = new Spinner(this);
        categorySpinner.setBackgroundColor(Color.TRANSPARENT);
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (view instanceof TextView) {
                    ((TextView) view).setTextColor(UIHelper.ACCENT_BLUE);
                    ((TextView) view).setTextSize(14);
                }
                String selected = (String) parent.getItemAtPosition(position);
                currentFilter = "全部".equals(selected) ? null : selected;
                refreshList();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        filterBar.addView(filterLabel);
        filterBar.addView(categorySpinner, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // List
        listView = new ListView(this);
        listView.setBackgroundColor(UIHelper.BG_PRIMARY);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        int listPad = UIHelper.dp(this, 16);
        listView.setPadding(listPad, UIHelper.dp(this, 8), listPad, listPad);
        listView.setClipToPadding(false);
        adapter = new ExpenseAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            ExpenseDbHelper.Expense e = adapter.data.get(position);
            showExpenseActions(e);
        });

        // Empty state
        TextView emptyView = new TextView(this);
        emptyView.setText("尚無消費紀錄");
        emptyView.setTextSize(16);
        emptyView.setTextColor(UIHelper.TEXT_HINT);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, UIHelper.dp(this, 60), 0, 0);
        root.addView(emptyView);
        listView.setEmptyView(emptyView);

        root.addView(topBar, 0);
        root.addView(filterBar, 1);
        root.addView(listView, 2, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        AppLog.i("Expense", "消費紀錄頁面已開啟");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCategories();
        refreshList();
    }

    private void refreshCategories() {
        List<String> cats = dbHelper.getCategories();
        List<String> items = new ArrayList<>();
        items.add("全部");
        items.addAll(cats);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(spinnerAdapter);
    }

    private void showReminderSettings() {
        boolean enabled = ReminderHelper.isEnabled(this);
        int hour = ReminderHelper.getHour(this);
        int minute = ReminderHelper.getMinute(this);

        if (enabled) {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("記帳提醒");
            builder.setMessage(String.format("目前提醒時間: %02d:%02d\n\n選擇操作：", hour, minute));
            builder.setPositiveButton("修改時間", (d, w) -> pickReminderTime());
            builder.setNegativeButton("關閉提醒", (d, w) -> {
                ReminderHelper.cancelReminder(this);
                Toast.makeText(this, "已關閉每日提醒", Toast.LENGTH_SHORT).show();
            });
            builder.setNeutralButton("取消", null);
            builder.show();
        } else {
            pickReminderTime();
        }
    }

    private void pickReminderTime() {
        int hour = ReminderHelper.getHour(this);
        int minute = ReminderHelper.getMinute(this);
        new TimePickerDialog(this, (view, h, m) -> {
            ReminderHelper.scheduleReminder(this, h, m);
            Toast.makeText(this, String.format("已設定每日 %02d:%02d 提醒記帳", h, m),
                    Toast.LENGTH_SHORT).show();
        }, hour, minute, true).show();
    }

    private void showExpenseActions(ExpenseDbHelper.Expense e) {
        String info = (e.merchant != null && !e.merchant.isEmpty() ? e.merchant : "無商家")
                + "  $" + String.format(Locale.getDefault(), "%.0f", e.amount);
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(info)
                .setItems(new String[]{"編輯", "刪除"}, (d, which) -> {
                    if (which == 0) {
                        AppLog.i("Expense", "編輯消費: id=" + e.id + " amount=" + e.amount + " merchant=" + e.merchant);
                        Intent intent = new Intent(this, AddExpenseActivity.class);
                        intent.putExtra("expense_id", e.id);
                        startActivity(intent);
                    } else {
                        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                                .setTitle("確認刪除")
                                .setMessage("確定要刪除這筆消費紀錄？")
                                .setPositiveButton("刪除", (d2, w2) -> {
                                    dbHelper.delete(e.id);
                                    AppLog.i("Expense", "刪除消費: id=" + e.id + " amount=" + e.amount + " merchant=" + e.merchant);
                                    refreshList();
                                    Toast.makeText(this, "已刪除", Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void refreshList() {
        adapter.data = dbHelper.queryAll(currentFilter);
        adapter.notifyDataSetChanged();
    }

    private class ExpenseAdapter extends BaseAdapter {
        List<ExpenseDbHelper.Expense> data = new ArrayList<>();
        final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());

        @Override
        public int getCount() { return data.size(); }

        @Override
        public Object getItem(int position) { return data.get(position); }

        @Override
        public long getItemId(int position) { return data.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout card = UIHelper.card(ExpenseActivity.this);

            ExpenseDbHelper.Expense e = data.get(position);

            // Top row: merchant + amount
            LinearLayout topRow = new LinearLayout(ExpenseActivity.this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView merchantView = new TextView(ExpenseActivity.this);
            merchantView.setText(e.merchant != null && !e.merchant.isEmpty() ? e.merchant : "-");
            merchantView.setTextSize(16);
            merchantView.setTextColor(UIHelper.TEXT_PRIMARY);
            merchantView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            merchantView.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView amountView = new TextView(ExpenseActivity.this);
            amountView.setText(String.format(Locale.getDefault(), "-$%.0f", e.amount));
            amountView.setTextSize(18);
            amountView.setTextColor(UIHelper.ACCENT_RED);
            amountView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

            topRow.addView(merchantView);
            topRow.addView(amountView);

            // Bottom row: date + category badge
            LinearLayout bottomRow = new LinearLayout(ExpenseActivity.this);
            bottomRow.setOrientation(LinearLayout.HORIZONTAL);
            bottomRow.setGravity(Gravity.CENTER_VERTICAL);
            bottomRow.setPadding(0, UIHelper.dp(ExpenseActivity.this, 10), 0, 0);

            TextView dateView = new TextView(ExpenseActivity.this);
            dateView.setText(sdf.format(new Date(e.createdAt)));
            dateView.setTextSize(12);
            dateView.setTextColor(UIHelper.TEXT_SECONDARY);
            dateView.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            bottomRow.addView(dateView);

            if (e.category != null && !e.category.isEmpty()) {
                TextView badge = UIHelper.statusBadge(ExpenseActivity.this, e.category, UIHelper.ACCENT_PURPLE);
                bottomRow.addView(badge);
            }

            card.addView(topRow);
            card.addView(bottomRow);

            return card;
        }
    }
}
