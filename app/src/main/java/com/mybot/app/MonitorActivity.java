package com.mybot.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("監聽狀態");
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, 16);

        TextView hint = new TextView(this);
        hint.setText("即時顯示攔截到的通知與簡訊");
        hint.setTextSize(13);
        hint.setTextColor(Color.GRAY);
        hint.setPadding(0, 0, 0, 16);

        ListView listView = new ListView(this);
        adapter = new LogAdapter();
        listView.setAdapter(adapter);

        root.addView(title);
        root.addView(hint);
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                adapter.notifyDataSetChanged();
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, new IntentFilter(ACTION_NEW_LOG), Context.RECEIVER_NOT_EXPORTED);
        adapter.notifyDataSetChanged();
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
            LinearLayout card;
            if (convertView instanceof LinearLayout) {
                card = (LinearLayout) convertView;
                card.removeAllViews();
            } else {
                card = new LinearLayout(MonitorActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(16, 16, 16, 16);
            }

            NotificationLog log = (NotificationLog) getItem(position);

            // Row 1: time + source app
            LinearLayout row1 = new LinearLayout(MonitorActivity.this);
            row1.setOrientation(LinearLayout.HORIZONTAL);

            TextView timeView = new TextView(MonitorActivity.this);
            timeView.setText(sdf.format(new Date(log.timestamp)));
            timeView.setTextSize(12);
            timeView.setTextColor(Color.GRAY);

            TextView sourceView = new TextView(MonitorActivity.this);
            sourceView.setText("  " + log.sourceApp + " [" + log.source + "]");
            sourceView.setTextSize(12);
            sourceView.setTextColor(Color.parseColor("#1976D2"));

            row1.addView(timeView);
            row1.addView(sourceView);

            // Row 2: title + content
            TextView contentView = new TextView(MonitorActivity.this);
            contentView.setText(log.title + ": " + log.content);
            contentView.setTextSize(14);
            contentView.setMaxLines(2);
            contentView.setPadding(0, 4, 0, 4);

            // Row 3: AI result
            TextView aiView = new TextView(MonitorActivity.this);
            aiView.setTextSize(12);
            if (!log.analyzed) {
                aiView.setText("分析中...");
                aiView.setTextColor(Color.GRAY);
            } else if (log.offline) {
                aiView.setText("AI: 離線");
                aiView.setTextColor(Color.parseColor("#FF6F00"));
            } else if (log.isExpense) {
                aiView.setText(String.format(Locale.getDefault(),
                        "消費: $%.0f %s [%s]", log.amount, log.merchant, log.category));
                aiView.setTextColor(Color.parseColor("#D32F2F"));
                aiView.setTypeface(null, Typeface.BOLD);
            } else {
                aiView.setText("非消費");
                aiView.setTextColor(Color.GRAY);
            }

            // Divider
            View divider = new View(MonitorActivity.this);
            divider.setBackgroundColor(Color.parseColor("#E0E0E0"));
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));

            card.addView(row1);
            card.addView(contentView);
            card.addView(aiView);
            card.addView(divider);

            return card;
        }
    }
}
