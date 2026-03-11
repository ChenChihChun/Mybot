package com.mybot.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TravelActivity extends AppCompatActivity {

    private TravelDbHelper db;
    private LinearLayout contentLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLog.i("Travel", "TravelActivity開啟");
        db = TravelDbHelper.getInstance(this);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void buildUI() {
        LinearLayout root = UIHelper.pageRoot(this);

        // ── Top bar ──
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(UIHelper.BG_TOP_BAR);
        int p = UIHelper.dp(this, 16);
        topBar.setPadding(p, UIHelper.dp(this, 12), p, UIHelper.dp(this, 12));
        topBar.setElevation(UIHelper.dp(this, 4));

        TextView backBtn = new TextView(this);
        backBtn.setText("\u2190");
        backBtn.setTextSize(20);
        backBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        backBtn.setPadding(0, 0, UIHelper.dp(this, 12), 0);
        backBtn.setOnClickListener(v -> finish());

        TextView titleTv = new TextView(this);
        titleTv.setText("\uD83D\uDDFA\uFE0F \u65C5\u904A\u898F\u5283");
        titleTv.setTextSize(18);
        titleTv.setTextColor(UIHelper.TEXT_PRIMARY);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleTv.setLayoutParams(titleLp);

        // Achievement button
        TextView achBtn = new TextView(this);
        achBtn.setText("\uD83C\uDFC5");
        achBtn.setTextSize(18);
        achBtn.setPadding(UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8), 0);
        achBtn.setOnClickListener(v -> startActivity(new Intent(this, TravelAchievementActivity.class)));

        TextView addBtn = new TextView(this);
        addBtn.setText("\uFF0B \u65B0\u884C\u7A0B");
        addBtn.setTextSize(14);
        addBtn.setTextColor(UIHelper.ACCENT_BLUE);
        addBtn.setOnClickListener(v -> startActivity(new Intent(this, CreateTripActivity.class)));

        topBar.addView(backBtn);
        topBar.addView(titleTv);
        topBar.addView(achBtn);
        topBar.addView(addBtn);

        // ── Scrollable content ──
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        int cp = UIHelper.dp(this, 16);
        contentLayout.setPadding(cp, cp, cp, cp);

        scrollView.addView(contentLayout);

        root.addView(topBar);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private void refreshList() {
        contentLayout.removeAllViews();

        // Achievement summary bar
        int unlocked = db.getUnlockedAchievementCount();
        LinearLayout achBar = new LinearLayout(this);
        achBar.setOrientation(LinearLayout.HORIZONTAL);
        achBar.setGravity(Gravity.CENTER_VERTICAL);
        achBar.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 10, this));
        int ap = UIHelper.dp(this, 12);
        achBar.setPadding(ap, ap, ap, ap);
        LinearLayout.LayoutParams achLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        achLp.setMargins(0, 0, 0, UIHelper.dp(this, 16));
        achBar.setLayoutParams(achLp);
        achBar.setOnClickListener(v -> startActivity(new Intent(this, TravelAchievementActivity.class)));

        TextView achIcon = new TextView(this);
        achIcon.setText("\uD83C\uDFC5");
        achIcon.setTextSize(16);
        achIcon.setPadding(0, 0, UIHelper.dp(this, 8), 0);

        TextView achText = new TextView(this);
        achText.setText("\u5DF2\u89E3\u9396 " + unlocked + "/" + TravelAchievementManager.TOTAL_ACHIEVEMENTS + " \u500B\u6210\u5C31");
        achText.setTextSize(13);
        achText.setTextColor(UIHelper.TEXT_SECONDARY);
        achText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView achArrow = new TextView(this);
        achArrow.setText("\u203A");
        achArrow.setTextSize(18);
        achArrow.setTextColor(UIHelper.TEXT_HINT);

        achBar.addView(achIcon);
        achBar.addView(achText);
        achBar.addView(achArrow);
        contentLayout.addView(achBar);

        // Trip sections
        addTripSection(TravelDbHelper.STATUS_ONGOING, "\uD83D\uDE80 \u9032\u884C\u4E2D");
        addTripSection(TravelDbHelper.STATUS_PLANNING, "\uD83D\uDCDD \u898F\u5283\u4E2D");
        addTripSection(TravelDbHelper.STATUS_COMPLETED, "\u2705 \u5DF2\u5B8C\u6210");

        // Empty state
        if (db.getTripCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("\u9084\u6C92\u6709\u884C\u7A0B\u559C\uFF0C\u9EDE\u64CA\u53F3\u4E0A\u89D2\u300C\uFF0B \u65B0\u884C\u7A0B\u300D\u958B\u59CB\u898F\u5283\uFF01");
            empty.setTextSize(15);
            empty.setTextColor(UIHelper.TEXT_HINT);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, UIHelper.dp(this, 60), 0, 0);
            contentLayout.addView(empty);
        }
    }

    private void addTripSection(String status, String sectionTitle) {
        Cursor cursor = db.getTripsByStatus(status);
        if (cursor.getCount() == 0) {
            cursor.close();
            return;
        }

        // Section header
        TextView header = new TextView(this);
        header.setText(sectionTitle);
        header.setTextSize(14);
        header.setTextColor(UIHelper.TEXT_SECONDARY);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        header.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerLp.setMargins(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 8));
        header.setLayoutParams(headerLp);
        contentLayout.addView(header);

        while (cursor.moveToNext()) {
            long id = cursor.getLong(cursor.getColumnIndexOrThrow(TravelDbHelper.COL_ID));
            String name = cursor.getString(cursor.getColumnIndexOrThrow(TravelDbHelper.COL_NAME));
            String dest = cursor.getString(cursor.getColumnIndexOrThrow(TravelDbHelper.COL_DESTINATION));
            String startDate = cursor.getString(cursor.getColumnIndexOrThrow(TravelDbHelper.COL_START_DATE));
            String endDate = cursor.getString(cursor.getColumnIndexOrThrow(TravelDbHelper.COL_END_DATE));
            int days = cursor.getInt(cursor.getColumnIndexOrThrow(TravelDbHelper.COL_DAYS));
            int people = cursor.getInt(cursor.getColumnIndexOrThrow(TravelDbHelper.COL_PEOPLE));
            double estimated = cursor.getDouble(cursor.getColumnIndexOrThrow(TravelDbHelper.COL_ESTIMATED_BUDGET));
            double actual = cursor.getDouble(cursor.getColumnIndexOrThrow(TravelDbHelper.COL_ACTUAL_BUDGET));

            contentLayout.addView(buildTripCard(id, name, dest, startDate, endDate,
                    days, people, estimated, actual, status));
        }
        cursor.close();
    }

    private LinearLayout buildTripCard(long id, String name, String dest, String startDate,
                                        String endDate, int days, int people,
                                        double estimated, double actual, String status) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 12, this));
        int cp = UIHelper.dp(this, 14);
        card.setPadding(cp, cp, cp, cp);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, UIHelper.dp(this, 10));
        card.setLayoutParams(cardLp);

        // Row 1: Name + destination
        TextView nameTv = new TextView(this);
        nameTv.setText(name != null && !name.isEmpty() ? name : dest + " " + days + "\u65E5\u904A");
        nameTv.setTextSize(16);
        nameTv.setTextColor(UIHelper.TEXT_PRIMARY);
        nameTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        // Row 2: Date range + people
        TextView dateTv = new TextView(this);
        dateTv.setText("\uD83D\uDCC5 " + startDate + " ~ " + endDate + "  \uD83D\uDC64 " + people + "\u4EBA");
        dateTv.setTextSize(13);
        dateTv.setTextColor(UIHelper.TEXT_SECONDARY);
        dateTv.setPadding(0, UIHelper.dp(this, 4), 0, 0);

        // Row 3: Budget info
        TextView budgetTv = new TextView(this);
        String budgetStr = "\uD83D\uDCB0 \u9810\u7B97 NT$" + String.format(Locale.US, "%,.0f", estimated);
        if (actual > 0) {
            budgetStr += "  |  \u5BE6\u969B NT$" + String.format(Locale.US, "%,.0f", actual);
        }
        budgetTv.setText(budgetStr);
        budgetTv.setTextSize(12);
        budgetTv.setTextColor(UIHelper.TEXT_HINT);
        budgetTv.setPadding(0, UIHelper.dp(this, 2), 0, 0);

        card.addView(nameTv);
        card.addView(dateTv);
        card.addView(budgetTv);

        // Click to view
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, TravelPlanActivity.class);
            intent.putExtra("trip_id", id);
            startActivity(intent);
        });

        // Long press to manage
        card.setOnLongClickListener(v -> {
            showTripOptions(id, name, status);
            return true;
        });

        return card;
    }

    private void showTripOptions(long id, String name, String currentStatus) {
        String[] options;
        if (TravelDbHelper.STATUS_PLANNING.equals(currentStatus)) {
            options = new String[]{"\u958B\u59CB\u65C5\u7A0B", "\u522A\u9664\u884C\u7A0B"};
        } else if (TravelDbHelper.STATUS_ONGOING.equals(currentStatus)) {
            options = new String[]{"\u5B8C\u6210\u65C5\u7A0B", "\u522A\u9664\u884C\u7A0B"};
        } else {
            options = new String[]{"\u91CD\u65B0\u898F\u5283", "\u522A\u9664\u884C\u7A0B"};
        }

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle(name)
                .setItems(options, (dialog, which) -> {
                    if (TravelDbHelper.STATUS_PLANNING.equals(currentStatus)) {
                        if (which == 0) {
                            db.updateTripStatus(id, TravelDbHelper.STATUS_ONGOING);
                            AppLog.i("Travel", "行程開始: id=" + id);
                            Toast.makeText(this, "\u65C5\u7A0B\u5DF2\u958B\u59CB\uFF01", Toast.LENGTH_SHORT).show();
                            refreshList();
                        } else {
                            confirmDelete(id, name);
                        }
                    } else if (TravelDbHelper.STATUS_ONGOING.equals(currentStatus)) {
                        if (which == 0) {
                            db.updateTripStatus(id, TravelDbHelper.STATUS_COMPLETED);
                            TravelAchievementManager.checkAndUnlock(this, id);
                            AppLog.i("Travel", "行程完成: id=" + id);
                            Toast.makeText(this, "\u65C5\u7A0B\u5DF2\u5B8C\u6210\uFF01", Toast.LENGTH_SHORT).show();
                            refreshList();
                        } else {
                            confirmDelete(id, name);
                        }
                    } else {
                        if (which == 0) {
                            db.updateTripStatus(id, TravelDbHelper.STATUS_PLANNING);
                            AppLog.i("Travel", "重新規劃: id=" + id);
                            refreshList();
                        } else {
                            confirmDelete(id, name);
                        }
                    }
                })
                .show();
    }

    private void confirmDelete(long id, String name) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle("\u522A\u9664\u884C\u7A0B")
                .setMessage("\u78BA\u5B9A\u8981\u522A\u9664\u300C" + name + "\u300D\u55CE\uFF1F\u6B64\u64CD\u4F5C\u7121\u6CD5\u5FA9\u539F\u3002")
                .setPositiveButton("\u522A\u9664", (d, w) -> {
                    db.deleteTrip(id);
                    AppLog.i("Travel", "行程刪除: id=" + id);
                    Toast.makeText(this, "\u5DF2\u522A\u9664", Toast.LENGTH_SHORT).show();
                    refreshList();
                })
                .setNegativeButton("\u53D6\u6D88", null)
                .show();
    }
}
