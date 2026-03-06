package com.mybot.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TodoActivity extends AppCompatActivity {

    private TodoDbHelper dbHelper;
    private ListView listView;
    private TodoAdapter adapter;
    private TextView pendingTab, completedTab;
    private TextView statsView;
    private EditText searchInput;
    private boolean showingCompleted = false;

    // Completed view period
    private int periodMode = 0; // 0=all, 1=month, 2=year
    private final Calendar periodCal = Calendar.getInstance();
    private LinearLayout periodNav;
    private TextView periodLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        dbHelper = new TodoDbHelper(this);

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "TO DO");

        android.widget.Button addBtn = UIHelper.smallButton(this, "+ 新增", UIHelper.ACCENT_BLUE);
        addBtn.setOnClickListener(v -> startActivity(new Intent(this, AddTodoActivity.class)));
        topBar.addView(addBtn);

        // Stats bar
        LinearLayout statsBar = new LinearLayout(this);
        statsBar.setOrientation(LinearLayout.HORIZONTAL);
        statsBar.setGravity(Gravity.CENTER_VERTICAL);
        statsBar.setBackgroundColor(UIHelper.BG_CARD);
        int sp = UIHelper.dp(this, 20);
        statsBar.setPadding(sp, UIHelper.dp(this, 10), sp, UIHelper.dp(this, 10));

        statsView = new TextView(this);
        statsView.setTextSize(12);
        statsView.setTextColor(UIHelper.TEXT_SECONDARY);
        statsBar.addView(statsView);

        // Tab bar
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundColor(UIHelper.BG_CARD_ALT);
        tabBar.setGravity(Gravity.CENTER);
        int tp = UIHelper.dp(this, 8);
        tabBar.setPadding(tp, tp, tp, tp);

        pendingTab = createTab("未完成");
        completedTab = createTab("已完成");
        pendingTab.setOnClickListener(v -> switchTab(false));
        completedTab.setOnClickListener(v -> switchTab(true));

        tabBar.addView(pendingTab);
        tabBar.addView(completedTab);

        // Search bar
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setBackgroundColor(UIHelper.BG_PRIMARY);
        int srp = UIHelper.dp(this, 16);
        searchBar.setPadding(srp, UIHelper.dp(this, 8), srp, UIHelper.dp(this, 4));

        searchInput = new EditText(this);
        searchInput.setHint("搜尋待辦事項...");
        searchInput.setHintTextColor(UIHelper.TEXT_HINT);
        searchInput.setTextColor(UIHelper.TEXT_PRIMARY);
        searchInput.setTextSize(14);
        searchInput.setBackground(UIHelper.roundRectStroke(UIHelper.BG_INPUT, Color.parseColor("#2E4050"), 12, 1, this));
        int sip = UIHelper.dp(this, 14);
        searchInput.setPadding(sip, UIHelper.dp(this, 10), sip, UIHelper.dp(this, 10));
        searchInput.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { refreshList(); }
        });
        searchBar.addView(searchInput);

        // Period navigation (for completed view)
        periodNav = new LinearLayout(this);
        periodNav.setOrientation(LinearLayout.HORIZONTAL);
        periodNav.setGravity(Gravity.CENTER);
        periodNav.setBackgroundColor(UIHelper.BG_CARD);
        int np = UIHelper.dp(this, 8);
        periodNav.setPadding(np, UIHelper.dp(this, 6), np, UIHelper.dp(this, 6));
        periodNav.setVisibility(View.GONE);

        TextView modeAll = createMiniTab("全部");
        TextView modeMonth = createMiniTab("月");
        TextView modeYear = createMiniTab("年");
        modeAll.setOnClickListener(v -> switchPeriod(0));
        modeMonth.setOnClickListener(v -> switchPeriod(1));
        modeYear.setOnClickListener(v -> switchPeriod(2));

        TextView prevBtn = new TextView(this);
        prevBtn.setText("<");
        prevBtn.setTextSize(18);
        prevBtn.setTextColor(UIHelper.ACCENT_BLUE);
        prevBtn.setTypeface(Typeface.DEFAULT_BOLD);
        prevBtn.setGravity(Gravity.CENTER);
        prevBtn.setLayoutParams(new LinearLayout.LayoutParams(UIHelper.dp(this, 36), UIHelper.dp(this, 36)));
        prevBtn.setOnClickListener(v -> navigatePeriod(-1));

        periodLabel = new TextView(this);
        periodLabel.setTextSize(14);
        periodLabel.setTextColor(UIHelper.TEXT_PRIMARY);
        periodLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        periodLabel.setGravity(Gravity.CENTER);
        periodLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView nextBtn = new TextView(this);
        nextBtn.setText(">");
        nextBtn.setTextSize(18);
        nextBtn.setTextColor(UIHelper.ACCENT_BLUE);
        nextBtn.setTypeface(Typeface.DEFAULT_BOLD);
        nextBtn.setGravity(Gravity.CENTER);
        nextBtn.setLayoutParams(new LinearLayout.LayoutParams(UIHelper.dp(this, 36), UIHelper.dp(this, 36)));
        nextBtn.setOnClickListener(v -> navigatePeriod(1));

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER);
        modeRow.addView(modeAll);
        modeRow.addView(modeMonth);
        modeRow.addView(modeYear);

        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER);
        navRow.addView(prevBtn);
        navRow.addView(periodLabel);
        navRow.addView(nextBtn);

        periodNav.setOrientation(LinearLayout.VERTICAL);
        periodNav.addView(modeRow);
        periodNav.addView(navRow);

        // List
        listView = new ListView(this);
        listView.setBackgroundColor(UIHelper.BG_PRIMARY);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        int listPad = UIHelper.dp(this, 16);
        listView.setPadding(listPad, UIHelper.dp(this, 8), listPad, listPad);
        listView.setClipToPadding(false);
        adapter = new TodoAdapter();
        listView.setAdapter(adapter);

        // Empty state
        TextView emptyView = new TextView(this);
        emptyView.setTextSize(16);
        emptyView.setTextColor(UIHelper.TEXT_HINT);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, UIHelper.dp(this, 60), 0, 0);
        root.addView(emptyView);
        listView.setEmptyView(emptyView);

        root.addView(topBar, 0);
        root.addView(statsBar, 1);
        root.addView(tabBar, 2);
        root.addView(searchBar, 3);
        root.addView(periodNav, 4);
        root.addView(listView, 5, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        switchTab(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
        updateStats();
    }

    private TextView createTab(String text) {
        TextView tab = new TextView(this);
        tab.setText(text);
        tab.setTextSize(14);
        tab.setGravity(Gravity.CENTER);
        tab.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        int h = UIHelper.dp(this, 24);
        int v = UIHelper.dp(this, 8);
        tab.setPadding(h, v, h, v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        int m = UIHelper.dp(this, 4);
        lp.setMargins(m, 0, m, 0);
        tab.setLayoutParams(lp);
        return tab;
    }

    private TextView createMiniTab(String text) {
        TextView tab = new TextView(this);
        tab.setText(text);
        tab.setTextSize(12);
        tab.setGravity(Gravity.CENTER);
        tab.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        int h = UIHelper.dp(this, 14);
        int v = UIHelper.dp(this, 4);
        tab.setPadding(h, v, h, v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = UIHelper.dp(this, 3);
        lp.setMargins(m, 0, m, UIHelper.dp(this, 6));
        tab.setLayoutParams(lp);
        return tab;
    }

    private void switchTab(boolean completed) {
        showingCompleted = completed;

        pendingTab.setBackground(UIHelper.roundRect(!completed ? UIHelper.ACCENT_BLUE : UIHelper.BG_CARD, 8, this));
        pendingTab.setTextColor(!completed ? Color.WHITE : UIHelper.TEXT_SECONDARY);
        completedTab.setBackground(UIHelper.roundRect(completed ? UIHelper.ACCENT_GREEN : UIHelper.BG_CARD, 8, this));
        completedTab.setTextColor(completed ? Color.WHITE : UIHelper.TEXT_SECONDARY);

        periodNav.setVisibility(completed ? View.VISIBLE : View.GONE);

        // Update empty view text
        ListView lv = listView;
        if (lv.getEmptyView() instanceof TextView) {
            ((TextView) lv.getEmptyView()).setText(completed ? "尚無已完成事項" : "太棒了，沒有待辦事項！");
        }

        refreshList();
    }

    private void switchPeriod(int mode) {
        periodMode = mode;
        periodCal.setTimeInMillis(System.currentTimeMillis());

        // Update mini tab styles
        LinearLayout modeRow = (LinearLayout) periodNav.getChildAt(0);
        for (int i = 0; i < modeRow.getChildCount(); i++) {
            TextView tab = (TextView) modeRow.getChildAt(i);
            boolean active = i == mode;
            tab.setBackground(UIHelper.roundRect(active ? UIHelper.ACCENT_BLUE : UIHelper.BG_CARD_ALT, 6, this));
            tab.setTextColor(active ? Color.WHITE : UIHelper.TEXT_SECONDARY);
        }

        LinearLayout navRow = (LinearLayout) periodNav.getChildAt(1);
        navRow.setVisibility(mode == 0 ? View.GONE : View.VISIBLE);

        refreshList();
    }

    private void navigatePeriod(int direction) {
        if (periodMode == 1) {
            periodCal.add(Calendar.MONTH, direction);
        } else if (periodMode == 2) {
            periodCal.add(Calendar.YEAR, direction);
        }
        refreshList();
    }

    private long[] getPeriodRange() {
        if (periodMode == 0) return null;

        Calendar start = (Calendar) periodCal.clone();
        Calendar end;

        if (periodMode == 1) { // month
            start.set(Calendar.DAY_OF_MONTH, 1);
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            end = (Calendar) start.clone();
            end.add(Calendar.MONTH, 1);
        } else { // year
            start.set(Calendar.MONTH, 0);
            start.set(Calendar.DAY_OF_MONTH, 1);
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            end = (Calendar) start.clone();
            end.add(Calendar.YEAR, 1);
        }
        return new long[]{start.getTimeInMillis(), end.getTimeInMillis()};
    }

    private String getPeriodText() {
        if (periodMode == 1) {
            return periodCal.get(Calendar.YEAR) + " 年 " + (periodCal.get(Calendar.MONTH) + 1) + " 月";
        } else {
            return periodCal.get(Calendar.YEAR) + " 年";
        }
    }

    private void updateStats() {
        int pending = dbHelper.countPending();
        int completed = dbHelper.countCompleted();
        int total = pending + completed;
        String rate = total > 0 ? String.format(Locale.getDefault(), "%.0f%%", completed * 100.0 / total) : "0%";
        statsView.setText(String.format("待辦 %d 項 | 已完成 %d 項 | 完成率 %s", pending, completed, rate));
    }

    private void refreshList() {
        String search = searchInput.getText().toString().trim();
        if (search.isEmpty()) search = null;

        if (showingCompleted) {
            long[] range = getPeriodRange();
            Long start = range != null ? range[0] : null;
            Long end = range != null ? range[1] : null;
            adapter.data = dbHelper.queryCompleted(null, search, start, end);
            if (periodMode > 0) {
                periodLabel.setText(getPeriodText());
            }
        } else {
            adapter.data = dbHelper.queryPending(null, search);
        }
        adapter.notifyDataSetChanged();
        updateStats();
    }

    private void showItemActions(TodoDbHelper.Todo todo) {
        List<String> options = new ArrayList<>();
        if (!todo.completed) {
            options.add("標記完成");
            options.add("編輯");
        } else {
            options.add("恢復為未完成");
        }
        options.add("刪除");

        new AlertDialog.Builder(this)
                .setTitle(todo.title)
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    String action = options.get(which);
                    switch (action) {
                        case "標記完成":
                            dbHelper.markCompleted(todo.id);
                            refreshList();
                            Toast.makeText(this, "已完成", Toast.LENGTH_SHORT).show();
                            break;
                        case "恢復為未完成":
                            dbHelper.markUncompleted(todo.id);
                            refreshList();
                            Toast.makeText(this, "已恢復", Toast.LENGTH_SHORT).show();
                            break;
                        case "編輯":
                            Intent intent = new Intent(this, AddTodoActivity.class);
                            intent.putExtra("todo_id", todo.id);
                            startActivity(intent);
                            break;
                        case "刪除":
                            new AlertDialog.Builder(this)
                                    .setTitle("確認刪除")
                                    .setMessage("確定要刪除「" + todo.title + "」嗎？")
                                    .setPositiveButton("刪除", (dd, ww) -> {
                                        dbHelper.delete(todo.id);
                                        refreshList();
                                        Toast.makeText(this, "已刪除", Toast.LENGTH_SHORT).show();
                                    })
                                    .setNegativeButton("取消", null)
                                    .show();
                            break;
                    }
                })
                .show();
    }

    private class TodoAdapter extends BaseAdapter {
        List<TodoDbHelper.Todo> data = new ArrayList<>();
        final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd", Locale.getDefault());
        final SimpleDateFormat sdfFull = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());

        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int position) { return data.get(position); }
        @Override public long getItemId(int position) { return data.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout card = UIHelper.card(TodoActivity.this);
            TodoDbHelper.Todo todo = data.get(position);

            // Priority indicator + title row
            LinearLayout titleRow = new LinearLayout(TodoActivity.this);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);

            // Priority dot
            int priorityColor;
            switch (todo.priority) {
                case 2: priorityColor = UIHelper.ACCENT_RED; break;
                case 0: priorityColor = UIHelper.TEXT_HINT; break;
                default: priorityColor = UIHelper.ACCENT_ORANGE; break;
            }
            TextView dot = new TextView(TodoActivity.this);
            dot.setBackground(UIHelper.roundRect(priorityColor, 20, TodoActivity.this));
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                    UIHelper.dp(TodoActivity.this, 10), UIHelper.dp(TodoActivity.this, 10));
            dotLp.setMargins(0, 0, UIHelper.dp(TodoActivity.this, 10), 0);
            dot.setLayoutParams(dotLp);

            TextView titleView = new TextView(TodoActivity.this);
            titleView.setText(todo.title);
            titleView.setTextSize(16);
            titleView.setTextColor(todo.completed ? UIHelper.TEXT_HINT : UIHelper.TEXT_PRIMARY);
            titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            if (todo.completed) {
                titleView.setPaintFlags(titleView.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            }
            titleView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            titleRow.addView(dot);
            titleRow.addView(titleView);

            // Category badge
            if (todo.category != null && !todo.category.isEmpty()) {
                TextView badge = UIHelper.statusBadge(TodoActivity.this, todo.category, UIHelper.ACCENT_PURPLE);
                LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                badgeLp.setMargins(UIHelper.dp(TodoActivity.this, 8), 0, 0, 0);
                badge.setLayoutParams(badgeLp);
                titleRow.addView(badge);
            }

            card.addView(titleRow);

            // Description
            if (todo.description != null && !todo.description.isEmpty()) {
                TextView descView = new TextView(TodoActivity.this);
                descView.setText(todo.description);
                descView.setTextSize(13);
                descView.setTextColor(UIHelper.TEXT_SECONDARY);
                descView.setMaxLines(2);
                descView.setPadding(UIHelper.dp(TodoActivity.this, 20), UIHelper.dp(TodoActivity.this, 6), 0, 0);
                card.addView(descView);
            }

            // Bottom row: deadline + status
            LinearLayout bottomRow = new LinearLayout(TodoActivity.this);
            bottomRow.setOrientation(LinearLayout.HORIZONTAL);
            bottomRow.setGravity(Gravity.CENTER_VERTICAL);
            bottomRow.setPadding(UIHelper.dp(TodoActivity.this, 20),
                    UIHelper.dp(TodoActivity.this, 8), 0, 0);

            if (todo.deadline != null) {
                TextView deadlineView = new TextView(TodoActivity.this);
                String dateStr = sdfFull.format(new Date(todo.deadline));
                deadlineView.setText("截止 " + dateStr);
                deadlineView.setTextSize(12);

                if (todo.isOverdue()) {
                    deadlineView.setTextColor(UIHelper.ACCENT_RED);
                    // Overdue badge
                    TextView overdueBadge = UIHelper.statusBadge(TodoActivity.this, "已逾期", UIHelper.ACCENT_RED);
                    LinearLayout.LayoutParams obLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    obLp.setMargins(UIHelper.dp(TodoActivity.this, 8), 0, 0, 0);
                    overdueBadge.setLayoutParams(obLp);
                    bottomRow.addView(deadlineView);
                    bottomRow.addView(overdueBadge);
                } else if (todo.isDueSoon(3)) {
                    deadlineView.setTextColor(UIHelper.ACCENT_ORANGE);
                    TextView soonBadge = UIHelper.statusBadge(TodoActivity.this, "即將到期", UIHelper.ACCENT_ORANGE);
                    LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    sbLp.setMargins(UIHelper.dp(TodoActivity.this, 8), 0, 0, 0);
                    soonBadge.setLayoutParams(sbLp);
                    bottomRow.addView(deadlineView);
                    bottomRow.addView(soonBadge);
                } else {
                    deadlineView.setTextColor(UIHelper.TEXT_SECONDARY);
                    bottomRow.addView(deadlineView);
                }
            } else if (!todo.completed) {
                TextView noDeadline = new TextView(TodoActivity.this);
                noDeadline.setText("無截止日期");
                noDeadline.setTextSize(12);
                noDeadline.setTextColor(UIHelper.TEXT_HINT);
                bottomRow.addView(noDeadline);
            }

            if (todo.completed && todo.completedAt != null) {
                TextView completedView = new TextView(TodoActivity.this);
                completedView.setText("完成於 " + sdfFull.format(new Date(todo.completedAt)));
                completedView.setTextSize(12);
                completedView.setTextColor(UIHelper.ACCENT_GREEN);
                completedView.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                completedView.setGravity(Gravity.END);
                bottomRow.addView(completedView);
            }

            card.addView(bottomRow);

            card.setOnClickListener(v -> showItemActions(todo));

            return card;
        }
    }
}
