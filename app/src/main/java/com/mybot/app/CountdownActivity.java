package com.mybot.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class CountdownActivity extends AppCompatActivity {

    private CountdownDbHelper dbHelper;
    private LinearLayout listContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        dbHelper = new CountdownDbHelper(this);

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "\u23F3 \u5012\u6578\u65E5");
        TextView backBtn = new TextView(this);
        backBtn.setText("\u2190");
        backBtn.setTextSize(22);
        backBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        backBtn.setPadding(0, 0, UIHelper.dp(this, 16), 0);
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn, 0);

        TextView addBtn = new TextView(this);
        addBtn.setText("\uFF0B");
        addBtn.setTextSize(24);
        addBtn.setTextColor(UIHelper.ACCENT_GREEN);
        addBtn.setOnClickListener(v -> startActivity(new Intent(this, AddCountdownActivity.class)));
        topBar.addView(addBtn);
        root.addView(topBar);

        // Scrollable content
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 16);
        listContainer.setPadding(p, p, p, p);

        scrollView.addView(listContainer);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        listContainer.removeAllViews();

        List<CountdownDbHelper.Countdown> items = dbHelper.getAll();

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("\u9084\u6C92\u6709\u5012\u6578\u65E5\uFF0C\u9EDE\u53F3\u4E0A\u89D2 \uFF0B \u65B0\u589E");
            empty.setTextSize(15);
            empty.setTextColor(UIHelper.TEXT_HINT);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, UIHelper.dp(this, 60), 0, 0);
            listContainer.addView(empty);
            return;
        }

        for (CountdownDbHelper.Countdown item : items) {
            listContainer.addView(buildCard(item));
        }
    }

    private LinearLayout buildCard(CountdownDbHelper.Countdown item) {
        LinearLayout card = UIHelper.card(this);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        // Icon
        TextView iconView = new TextView(this);
        iconView.setText(item.icon != null && !item.icon.isEmpty() ? item.icon : "\uD83D\uDCC5");
        iconView.setTextSize(28);
        iconView.setPadding(0, 0, UIHelper.dp(this, 12), 0);

        // Title + date column
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView titleView = new TextView(this);
        titleView.setText(item.title);
        titleView.setTextSize(16);
        titleView.setTextColor(UIHelper.TEXT_PRIMARY);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        TextView dateView = new TextView(this);
        dateView.setText(item.targetDate);
        dateView.setTextSize(12);
        dateView.setTextColor(UIHelper.TEXT_SECONDARY);
        dateView.setPadding(0, UIHelper.dp(this, 2), 0, 0);

        textCol.addView(titleView);
        textCol.addView(dateView);

        // Days badge
        long days = item.getDaysRemaining();
        TextView badge = new TextView(this);
        int badgeColor;
        String badgeText;
        if (days < 0) {
            badgeColor = UIHelper.ACCENT_RED;
            badgeText = "\u5DF2\u904E " + Math.abs(days) + " \u5929";
        } else if (days == 0) {
            badgeColor = UIHelper.ACCENT_GREEN;
            badgeText = "\u5C31\u662F\u4ECA\u5929\uFF01";
        } else if (days <= 7) {
            badgeColor = UIHelper.ACCENT_ORANGE;
            badgeText = days + " \u5929";
        } else {
            badgeColor = UIHelper.ACCENT_BLUE;
            badgeText = days + " \u5929";
        }
        badge.setText(badgeText);
        badge.setTextSize(14);
        badge.setTextColor(Color.WHITE);
        badge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        badge.setBackground(UIHelper.roundRect(badgeColor, 12, this));
        int bph = UIHelper.dp(this, 12);
        int bpv = UIHelper.dp(this, 6);
        badge.setPadding(bph, bpv, bph, bpv);

        topRow.addView(iconView);
        topRow.addView(textCol);
        topRow.addView(badge);
        card.addView(topRow);

        // Note
        if (item.note != null && !item.note.isEmpty()) {
            TextView noteView = new TextView(this);
            noteView.setText(item.note);
            noteView.setTextSize(13);
            noteView.setTextColor(UIHelper.TEXT_HINT);
            noteView.setPadding(0, UIHelper.dp(this, 8), 0, 0);
            card.addView(noteView);
        }

        // Click to edit, long press to delete
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddCountdownActivity.class);
            intent.putExtra("countdown_id", item.id);
            startActivity(intent);
        });

        card.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("\u522A\u9664\u5012\u6578\u65E5")
                    .setMessage("\u78BA\u5B9A\u8981\u522A\u9664\u300C" + item.title + "\u300D\u55CE\uFF1F")
                    .setPositiveButton("\u522A\u9664", (d, w) -> {
                        dbHelper.delete(item.id);
                        refreshList();
                    })
                    .setNegativeButton("\u53D6\u6D88", null)
                    .show();
            return true;
        });

        return card;
    }
}
