package com.mybot.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MonitorActivity extends AppCompatActivity {

    public static final String ACTION_NEW_LOG = "com.mybot.app.NEW_LOG";
    public static final List<NotificationLog> logs = new ArrayList<>();

    private LogAdapter adapter;
    private BroadcastReceiver receiver;
    private TextView countView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.parseColor("#0A1520"));

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "監聽狀態");

        countView = new TextView(this);
        countView.setTextSize(13);
        countView.setTextColor(UIHelper.TEXT_SECONDARY);
        countView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        updateCount();
        topBar.addView(countView);

        // Status indicator bar
        LinearLayout statusBar = new LinearLayout(this);
        statusBar.setOrientation(LinearLayout.HORIZONTAL);
        statusBar.setGravity(Gravity.CENTER_VERTICAL);
        statusBar.setBackgroundColor(UIHelper.BG_CARD);
        int sp = UIHelper.dp(this, 16);
        statusBar.setPadding(sp, UIHelper.dp(this, 10), sp, UIHelper.dp(this, 10));

        TextView dot = new TextView(this);
        dot.setBackground(UIHelper.roundRect(UIHelper.ACCENT_GREEN, 20, this));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                UIHelper.dp(this, 8), UIHelper.dp(this, 8));
        dotLp.setMargins(0, 0, UIHelper.dp(this, 8), 0);
        dot.setLayoutParams(dotLp);

        TextView statusText = new TextView(this);
        statusText.setText("即時監聽中 - 攔截到的通知與簡訊會顯示在下方");
        statusText.setTextSize(12);
        statusText.setTextColor(UIHelper.TEXT_SECONDARY);

        statusBar.addView(dot);
        statusBar.addView(statusText);

        // List
        ListView listView = new ListView(this);
        listView.setBackgroundColor(UIHelper.BG_PRIMARY);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        int listPad = UIHelper.dp(this, 12);
        listView.setPadding(listPad, UIHelper.dp(this, 8), listPad, listPad);
        listView.setClipToPadding(false);
        adapter = new LogAdapter();
        listView.setAdapter(adapter);

        // Empty state
        TextView emptyView = new TextView(this);
        emptyView.setText("等待通知或簡訊...");
        emptyView.setTextSize(16);
        emptyView.setTextColor(UIHelper.TEXT_HINT);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, UIHelper.dp(this, 60), 0, 0);
        root.addView(emptyView);
        listView.setEmptyView(emptyView);

        root.addView(topBar, 0);
        root.addView(statusBar, 1);
        root.addView(listView, 2, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                adapter.notifyDataSetChanged();
                updateCount();
            }
        };
    }

    private void updateCount() {
        countView.setText(logs.size() + " 筆");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, new IntentFilter(ACTION_NEW_LOG), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, new IntentFilter(ACTION_NEW_LOG));
        }
        adapter.notifyDataSetChanged();
        updateCount();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    private class LogAdapter extends BaseAdapter {
        final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        @Override
        public int getCount() { return logs.size(); }

        @Override
        public Object getItem(int position) { return logs.get(logs.size() - 1 - position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout card = UIHelper.card(MonitorActivity.this);

            NotificationLog log = (NotificationLog) getItem(position);

            // Row 1: time + source badge
            LinearLayout row1 = new LinearLayout(MonitorActivity.this);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setGravity(Gravity.CENTER_VERTICAL);

            TextView timeView = new TextView(MonitorActivity.this);
            timeView.setText(sdf.format(new Date(log.timestamp)));
            timeView.setTextSize(12);
            timeView.setTextColor(UIHelper.TEXT_HINT);
            timeView.setPadding(0, 0, UIHelper.dp(MonitorActivity.this, 8), 0);

            TextView sourceBadge = UIHelper.statusBadge(MonitorActivity.this,
                    log.sourceApp,
                    "簡訊".equals(log.source) ? UIHelper.ACCENT_GREEN : UIHelper.ACCENT_BLUE);

            TextView typeBadge = UIHelper.statusBadge(MonitorActivity.this,
                    log.source,
                    Color.parseColor("#37474F"));
            LinearLayout.LayoutParams typeLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            typeLp.setMargins(UIHelper.dp(MonitorActivity.this, 6), 0, 0, 0);
            typeBadge.setLayoutParams(typeLp);

            row1.addView(timeView);
            row1.addView(sourceBadge);
            row1.addView(typeBadge);

            // Row 2: content
            TextView contentView = new TextView(MonitorActivity.this);
            String displayContent = log.title;
            if (log.content != null && !log.content.isEmpty()) {
                displayContent += ": " + log.content;
            }
            contentView.setText(displayContent);
            contentView.setTextSize(14);
            contentView.setTextColor(UIHelper.TEXT_PRIMARY);
            contentView.setMaxLines(2);
            contentView.setPadding(0, UIHelper.dp(MonitorActivity.this, 8), 0,
                    UIHelper.dp(MonitorActivity.this, 8));

            // Row 3: AI result
            LinearLayout aiRow = new LinearLayout(MonitorActivity.this);
            aiRow.setOrientation(LinearLayout.HORIZONTAL);
            aiRow.setGravity(Gravity.CENTER_VERTICAL);
            aiRow.setBackground(UIHelper.roundRect(UIHelper.BG_CARD_ALT, 8, MonitorActivity.this));
            int aiPad = UIHelper.dp(MonitorActivity.this, 10);
            aiRow.setPadding(aiPad, UIHelper.dp(MonitorActivity.this, 6),
                    aiPad, UIHelper.dp(MonitorActivity.this, 6));

            TextView aiIcon = new TextView(MonitorActivity.this);
            aiIcon.setTextSize(11);
            aiIcon.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            aiIcon.setPadding(0, 0, UIHelper.dp(MonitorActivity.this, 8), 0);

            TextView aiText = new TextView(MonitorActivity.this);
            aiText.setTextSize(13);

            if (!log.analyzed) {
                aiIcon.setText("AI");
                aiIcon.setTextColor(UIHelper.TEXT_HINT);
                aiText.setText("分析中...");
                aiText.setTextColor(UIHelper.TEXT_HINT);
            } else if (log.offline) {
                aiIcon.setText("AI");
                aiIcon.setTextColor(UIHelper.ACCENT_ORANGE);
                aiText.setText("Bridge 離線");
                aiText.setTextColor(UIHelper.ACCENT_ORANGE);
            } else if (log.isExpense) {
                aiIcon.setText("$");
                aiIcon.setTextColor(UIHelper.ACCENT_RED);
                aiText.setText(String.format(Locale.getDefault(),
                        "消費 $%.0f  %s  [%s]", log.amount, log.merchant, log.category));
                aiText.setTextColor(UIHelper.ACCENT_RED);
                aiText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            } else {
                aiIcon.setText("AI");
                aiIcon.setTextColor(UIHelper.TEXT_SECONDARY);
                aiText.setText("非消費通知");
                aiText.setTextColor(UIHelper.TEXT_SECONDARY);
            }

            aiRow.addView(aiIcon);
            aiRow.addView(aiText);

            card.addView(row1);
            card.addView(contentView);
            card.addView(aiRow);

            return card;
        }
    }
}
