package com.mybot.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class TravelPlanActivity extends AppCompatActivity {

    private long tripId;
    private TravelDbHelper db;
    private LinearLayout contentLayout;
    private String itineraryJson;
    private String tripName;
    private String destination;
    private LinearLayout refineOverlay;
    private TextView refineStatusTv;
    private Handler refineTimerHandler;
    private long refineStartTime;
    private boolean refining = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tripId = getIntent().getLongExtra("trip_id", -1);
        if (tripId == -1) { finish(); return; }

        AppLog.i("Travel", "TravelPlanActivity\u958B\u555F tripId=" + tripId);
        db = TravelDbHelper.getInstance(this);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        buildUI();
        loadTrip();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTrip();
    }

    private void buildUI() {
        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
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
        titleTv.setTextSize(18);
        titleTv.setTextColor(UIHelper.TEXT_PRIMARY);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleTv.setTag("title");
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleTv.setLayoutParams(titleLp);

        TextView shareBtn = new TextView(this);
        shareBtn.setText("\uD83D\uDCE4");
        shareBtn.setTextSize(18);
        shareBtn.setPadding(UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8), 0);
        shareBtn.setOnClickListener(v -> shareTrip());

        topBar.addView(backBtn);
        topBar.addView(titleTv);
        topBar.addView(shareBtn);

        // Scrollable content
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        int cp = UIHelper.dp(this, 16);
        contentLayout.setPadding(cp, cp, cp, cp);

        scrollView.addView(contentLayout);

        // Bottom toolbar
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(UIHelper.BG_TOP_BAR);
        bottomBar.setGravity(Gravity.CENTER);
        int bp = UIHelper.dp(this, 12);
        bottomBar.setPadding(bp, bp, bp, bp);
        bottomBar.setElevation(UIHelper.dp(this, 4));

        TextView budgetBtn = toolbarButton("\uD83D\uDCB0 \u9810\u7B97");
        budgetBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, TravelBudgetActivity.class);
            intent.putExtra("trip_id", tripId);
            startActivity(intent);
        });

        TextView refineBtn = toolbarButton("\uD83E\uDD16 AI\u8ABF\u6574");
        refineBtn.setOnClickListener(v -> showRefineDialog());

        TextView mapBtn = toolbarButton("\uD83D\uDDFA\uFE0F \u5730\u5716");
        mapBtn.setOnClickListener(v -> openFullRoute());

        bottomBar.addView(budgetBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bottomBar.addView(refineBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bottomBar.addView(mapBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // Refine loading overlay
        refineOverlay = new LinearLayout(this);
        refineOverlay.setOrientation(LinearLayout.VERTICAL);
        refineOverlay.setGravity(Gravity.CENTER);
        refineOverlay.setBackgroundColor(0xDD0F1923);
        refineOverlay.setVisibility(View.GONE);
        refineOverlay.setClickable(true); // block touches

        ProgressBar refineSpinner = new ProgressBar(this, null, android.R.attr.progressBarStyle);
        refineOverlay.addView(refineSpinner);

        refineStatusTv = new TextView(this);
        refineStatusTv.setText("\uD83E\uDD16 AI \u6B63\u5728\u8ABF\u6574\u884C\u7A0B...");
        refineStatusTv.setTextSize(16);
        refineStatusTv.setTextColor(UIHelper.ACCENT_BLUE);
        refineStatusTv.setGravity(Gravity.CENTER);
        refineStatusTv.setPadding(0, UIHelper.dp(this, 16), 0, 0);
        refineOverlay.addView(refineStatusTv);

        // Use FrameLayout to stack overlay on content
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.addView(scrollView, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(refineOverlay, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(topBar);
        root.addView(frame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(bottomBar);

        setContentView(root);
    }

    private void loadTrip() {
        Cursor c = db.getTripById(tripId);
        if (!c.moveToFirst()) { c.close(); finish(); return; }

        tripName = c.getString(c.getColumnIndexOrThrow(TravelDbHelper.COL_NAME));
        destination = c.getString(c.getColumnIndexOrThrow(TravelDbHelper.COL_DESTINATION));
        String startDate = c.getString(c.getColumnIndexOrThrow(TravelDbHelper.COL_START_DATE));
        String endDate = c.getString(c.getColumnIndexOrThrow(TravelDbHelper.COL_END_DATE));
        int days = c.getInt(c.getColumnIndexOrThrow(TravelDbHelper.COL_DAYS));
        int people = c.getInt(c.getColumnIndexOrThrow(TravelDbHelper.COL_PEOPLE));
        double estimated = c.getDouble(c.getColumnIndexOrThrow(TravelDbHelper.COL_ESTIMATED_BUDGET));
        double actual = c.getDouble(c.getColumnIndexOrThrow(TravelDbHelper.COL_ACTUAL_BUDGET));
        itineraryJson = c.getString(c.getColumnIndexOrThrow(TravelDbHelper.COL_ITINERARY_JSON));
        c.close();

        // Update title
        TextView titleTv = (TextView) ((LinearLayout) ((LinearLayout) ((ViewGroup) findViewById(android.R.id.content))
                .getChildAt(0)).getChildAt(0)).findViewWithTag("title");
        if (titleTv != null) titleTv.setText(tripName);

        contentLayout.removeAllViews();

        // ── Overview card ──
        LinearLayout overview = new LinearLayout(this);
        overview.setOrientation(LinearLayout.VERTICAL);
        overview.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 12, this));
        int op = UIHelper.dp(this, 14);
        overview.setPadding(op, op, op, op);
        LinearLayout.LayoutParams oLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        oLp.setMargins(0, 0, 0, UIHelper.dp(this, 16));
        overview.setLayoutParams(oLp);

        addInfoRow(overview, "\uD83D\uDCC5", startDate + " ~ " + endDate + " (" + days + "\u5929)");
        addInfoRow(overview, "\uD83D\uDC64", people + "\u4EBA");
        addInfoRow(overview, "\uD83D\uDCB0", "\u9810\u7B97 NT$" + String.format(Locale.US, "%,.0f", estimated)
                + (actual > 0 ? "  |  \u5BE6\u969B NT$" + String.format(Locale.US, "%,.0f", actual) : ""));
        contentLayout.addView(overview);

        // ── Parse and display itinerary ──
        if (itineraryJson == null || itineraryJson.isEmpty()) {
            addEmptyMessage("\u884C\u7A0B\u5C1A\u672A\u751F\u6210");
            return;
        }

        try {
            JSONObject itinerary = new JSONObject(itineraryJson);
            JSONArray daysArr = itinerary.optJSONArray("days");
            if (daysArr == null) {
                addEmptyMessage("\u884C\u7A0B\u683C\u5F0F\u7121\u6CD5\u89E3\u6790");
                return;
            }

            for (int i = 0; i < daysArr.length(); i++) {
                JSONObject day = daysArr.getJSONObject(i);
                contentLayout.addView(buildDaySection(day));
            }

            // Tips
            JSONArray tips = itinerary.optJSONArray("tips");
            if (tips != null && tips.length() > 0) {
                contentLayout.addView(buildTipsCard(tips));
            }

            // Disclaimer
            TextView disclaimer = new TextView(this);
            disclaimer.setText("\u26A0\uFE0F AI \u5EFA\u8B70\u50C5\u4F9B\u53C3\u8003\uFF0C\u51FA\u767C\u524D\u8ACB\u78BA\u8A8D\u71DF\u696D\u6642\u9593\u53CA\u4EA4\u901A\u8CC7\u8A0A");
            disclaimer.setTextSize(12);
            disclaimer.setTextColor(UIHelper.TEXT_HINT);
            disclaimer.setGravity(Gravity.CENTER);
            disclaimer.setPadding(0, UIHelper.dp(this, 16), 0, UIHelper.dp(this, 16));
            contentLayout.addView(disclaimer);

        } catch (Exception e) {
            AppLog.e("Travel", "\u884C\u7A0B\u89E3\u6790\u5931\u6557: " + e.getMessage());
            addEmptyMessage("\u884C\u7A0B\u89E3\u6790\u5931\u6557: " + e.getMessage());
        }
    }

    private LinearLayout buildDaySection(JSONObject day) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sLp.setMargins(0, 0, 0, UIHelper.dp(this, 12));
        section.setLayoutParams(sLp);

        // Day header
        int dayNum = day.optInt("day", 0);
        String date = day.optString("date", "");
        String theme = day.optString("theme", "");

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(UIHelper.roundRect(UIHelper.ACCENT_BLUE, 8, this));
        header.getBackground().setAlpha(40);
        int hp = UIHelper.dp(this, 12);
        header.setPadding(hp, UIHelper.dp(this, 10), hp, UIHelper.dp(this, 10));
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hLp.setMargins(0, 0, 0, UIHelper.dp(this, 8));
        header.setLayoutParams(hLp);

        TextView dayLabel = new TextView(this);
        dayLabel.setText("Day " + dayNum);
        dayLabel.setTextSize(16);
        dayLabel.setTextColor(UIHelper.ACCENT_BLUE);
        dayLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        dayLabel.setPadding(0, 0, UIHelper.dp(this, 8), 0);

        TextView themeLabel = new TextView(this);
        String headerText = theme;
        if (!date.isEmpty()) headerText = date + " - " + theme;
        themeLabel.setText(headerText);
        themeLabel.setTextSize(14);
        themeLabel.setTextColor(UIHelper.TEXT_PRIMARY);

        header.addView(dayLabel);
        header.addView(themeLabel);
        section.addView(header);

        // Spots
        JSONArray spots = day.optJSONArray("spots");
        JSONArray transport = day.optJSONArray("transport");

        if (spots != null) {
            for (int i = 0; i < spots.length(); i++) {
                JSONObject spot = spots.optJSONObject(i);
                if (spot != null) {
                    section.addView(buildSpotCard(spot));

                    // Transport between spots
                    if (transport != null && i < transport.length()) {
                        JSONObject tr = transport.optJSONObject(i);
                        if (tr != null) {
                            section.addView(buildTransportRow(tr));
                        }
                    }
                }
            }
        }

        // Meals
        JSONArray meals = day.optJSONArray("meals");
        if (meals != null && meals.length() > 0) {
            TextView mealHeader = new TextView(this);
            mealHeader.setText("\uD83C\uDF74 \u9910\u98F2\u63A8\u85A6");
            mealHeader.setTextSize(13);
            mealHeader.setTextColor(UIHelper.TEXT_SECONDARY);
            mealHeader.setTypeface(null, Typeface.BOLD);
            mealHeader.setPadding(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 4));
            section.addView(mealHeader);

            for (int i = 0; i < meals.length(); i++) {
                JSONObject meal = meals.optJSONObject(i);
                if (meal != null) section.addView(buildMealRow(meal));
            }
        }

        // Day cost
        double dayCost = day.optDouble("day_cost", 0);
        if (dayCost > 0) {
            TextView costTv = new TextView(this);
            costTv.setText("\uD83D\uDCB0 \u7576\u65E5\u9810\u4F30\uFF1ANT$" + String.format(Locale.US, "%,.0f", dayCost));
            costTv.setTextSize(12);
            costTv.setTextColor(UIHelper.ACCENT_ORANGE);
            costTv.setPadding(0, UIHelper.dp(this, 8), 0, 0);
            section.addView(costTv);
        }

        return section;
    }

    private LinearLayout buildSpotCard(JSONObject spot) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 10, this));
        int cp = UIHelper.dp(this, 12);
        card.setPadding(cp, cp, cp, cp);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cLp.setMargins(0, 0, 0, UIHelper.dp(this, 4));
        card.setLayoutParams(cLp);

        String name = spot.optString("name", "");
        String address = spot.optString("address", "");
        int stayMin = spot.optInt("stay_minutes", 0);
        double cost = spot.optDouble("cost", 0);
        String openHours = spot.optString("open_hours", "");
        String note = spot.optString("note", "");
        double lat = spot.optDouble("lat", 0);
        double lng = spot.optDouble("lng", 0);

        // Name row with navigate button
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView nameTv = new TextView(this);
        nameTv.setText("\uD83D\uDCCD " + name);
        nameTv.setTextSize(15);
        nameTv.setTextColor(UIHelper.TEXT_PRIMARY);
        nameTv.setTypeface(null, Typeface.BOLD);
        nameTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView navBtn = new TextView(this);
        navBtn.setText("\u5C0E\u822A");
        navBtn.setTextSize(12);
        navBtn.setTextColor(UIHelper.ACCENT_BLUE);
        navBtn.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.ACCENT_BLUE, 12, 1, this));
        int np = UIHelper.dp(this, 8);
        navBtn.setPadding(np, UIHelper.dp(this, 4), np, UIHelper.dp(this, 4));
        navBtn.setOnClickListener(v -> {
            String query = (lat != 0 && lng != 0) ? lat + "," + lng : Uri.encode(name + " " + address);
            Uri uri = Uri.parse("google.navigation:q=" + query);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage("com.google.android.apps.maps");
            try {
                startActivity(intent);
            } catch (Exception e) {
                // Fallback to browser
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(name))));
            }
        });

        nameRow.addView(nameTv);
        nameRow.addView(navBtn);
        card.addView(nameRow);

        // Details
        StringBuilder details = new StringBuilder();
        if (!address.isEmpty()) details.append(address).append("\n");
        if (stayMin > 0) details.append("\u23F1 \u505C\u7559 ").append(stayMin).append(" \u5206\u9418");
        if (cost > 0) details.append("  |  \uD83C\uDFAB NT$").append(String.format(Locale.US, "%.0f", cost));
        if (!openHours.isEmpty()) details.append("\n\u23F0 ").append(openHours);
        if (!note.isEmpty()) details.append("\n\uD83D\uDCA1 ").append(note);

        if (details.length() > 0) {
            TextView detailTv = new TextView(this);
            detailTv.setText(details.toString());
            detailTv.setTextSize(12);
            detailTv.setTextColor(UIHelper.TEXT_SECONDARY);
            detailTv.setPadding(0, UIHelper.dp(this, 4), 0, 0);
            card.addView(detailTv);
        }

        return card;
    }

    private LinearLayout buildTransportRow(JSONObject tr) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int tp = UIHelper.dp(this, 8);
        row.setPadding(UIHelper.dp(this, 20), tp, 0, tp);

        String mode = tr.optString("mode", "");
        String route = tr.optString("route", "");
        int duration = tr.optInt("duration_min", 0);
        double cost = tr.optDouble("cost", 0);

        String modeIcon;
        switch (mode) {
            case "hsr": modeIcon = "\uD83D\uDE84"; break;      // 🚄
            case "train": modeIcon = "\uD83D\uDE82"; break;    // 🚂
            case "bus": modeIcon = "\uD83D\uDE8C"; break;      // 🚌
            case "car": modeIcon = "\uD83D\uDE97"; break;      // 🚗
            case "walk": modeIcon = "\uD83D\uDEB6"; break;     // 🚶
            case "taxi": modeIcon = "\uD83D\uDE95"; break;     // 🚕
            default: modeIcon = "\u27A1\uFE0F"; break;         // ➡️
        }

        TextView iconTv = new TextView(this);
        iconTv.setText(modeIcon);
        iconTv.setTextSize(14);
        iconTv.setPadding(0, 0, UIHelper.dp(this, 8), 0);

        StringBuilder info = new StringBuilder();
        if (!route.isEmpty()) info.append(route).append("  ");
        if (duration > 0) info.append(duration).append("\u5206\u9418");
        if (cost > 0) info.append("  NT$").append(String.format(Locale.US, "%.0f", cost));

        TextView infoTv = new TextView(this);
        infoTv.setText(info.toString());
        infoTv.setTextSize(12);
        infoTv.setTextColor(UIHelper.TEXT_HINT);

        // Dashed line
        TextView dash = new TextView(this);
        dash.setText("\u2502");
        dash.setTextSize(12);
        dash.setTextColor(UIHelper.TEXT_HINT);
        dash.setPadding(0, 0, UIHelper.dp(this, 8), 0);

        row.addView(dash);
        row.addView(iconTv);
        row.addView(infoTv);
        return row;
    }

    private LinearLayout buildMealRow(JSONObject meal) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(UIHelper.roundRect(UIHelper.BG_CARD_ALT, 8, this));
        int mp = UIHelper.dp(this, 10);
        row.setPadding(mp, mp, mp, mp);
        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mLp.setMargins(0, UIHelper.dp(this, 2), 0, UIHelper.dp(this, 2));
        row.setLayoutParams(mLp);

        String type = meal.optString("type", "");
        String name = meal.optString("name", "");
        String cuisine = meal.optString("cuisine", "");
        double avgPrice = meal.optDouble("avg_price", 0);

        String typeIcon;
        switch (type) {
            case "breakfast": typeIcon = "\u2615 \u65E9\u9910"; break;
            case "lunch": typeIcon = "\uD83C\uDF5C \u5348\u9910"; break;
            case "dinner": typeIcon = "\uD83C\uDF19 \u665A\u9910"; break;
            default: typeIcon = "\uD83C\uDF74 " + type; break;
        }

        TextView typeTv = new TextView(this);
        typeTv.setText(typeIcon);
        typeTv.setTextSize(12);
        typeTv.setTextColor(UIHelper.TEXT_SECONDARY);
        typeTv.setPadding(0, 0, UIHelper.dp(this, 8), 0);

        TextView nameTv = new TextView(this);
        String mealText = name;
        if (!cuisine.isEmpty()) mealText += " (" + cuisine + ")";
        if (avgPrice > 0) mealText += "  ~NT$" + String.format(Locale.US, "%.0f", avgPrice) + "/\u4EBA";
        nameTv.setText(mealText);
        nameTv.setTextSize(12);
        nameTv.setTextColor(UIHelper.TEXT_PRIMARY);

        row.addView(typeTv);
        row.addView(nameTv);
        return row;
    }

    private LinearLayout buildTipsCard(JSONArray tips) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 10, this));
        int cp = UIHelper.dp(this, 12);
        card.setPadding(cp, cp, cp, cp);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cLp.setMargins(0, UIHelper.dp(this, 8), 0, 0);
        card.setLayoutParams(cLp);

        TextView header = new TextView(this);
        header.setText("\uD83D\uDCA1 \u6CE8\u610F\u4E8B\u9805");
        header.setTextSize(14);
        header.setTextColor(UIHelper.ACCENT_ORANGE);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(0, 0, 0, UIHelper.dp(this, 4));
        card.addView(header);

        for (int i = 0; i < tips.length(); i++) {
            String tip = tips.optString(i, "");
            if (!tip.isEmpty()) {
                TextView tipTv = new TextView(this);
                tipTv.setText("\u2022 " + tip);
                tipTv.setTextSize(12);
                tipTv.setTextColor(UIHelper.TEXT_SECONDARY);
                tipTv.setPadding(0, UIHelper.dp(this, 2), 0, 0);
                card.addView(tipTv);
            }
        }
        return card;
    }

    private void addInfoRow(LinearLayout parent, String icon, String text) {
        TextView tv = new TextView(this);
        tv.setText(icon + " " + text);
        tv.setTextSize(13);
        tv.setTextColor(UIHelper.TEXT_SECONDARY);
        tv.setPadding(0, UIHelper.dp(this, 2), 0, UIHelper.dp(this, 2));
        parent.addView(tv);
    }

    private void addEmptyMessage(String message) {
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextSize(15);
        tv.setTextColor(UIHelper.TEXT_HINT);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, UIHelper.dp(this, 40), 0, 0);
        contentLayout.addView(tv);
    }

    private TextView toolbarButton(String text) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(14);
        btn.setTextColor(UIHelper.TEXT_PRIMARY);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4));
        return btn;
    }

    private void showRefineDialog() {
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        int dp = UIHelper.dp(this, 16);
        dialogLayout.setPadding(dp, dp, dp, UIHelper.dp(this, 8));

        // Prompt suggestion chips
        TextView suggestLabel = new TextView(this);
        suggestLabel.setText("\uD83D\uDCA1 \u5FEB\u901F\u9078\u64C7");
        suggestLabel.setTextSize(13);
        suggestLabel.setTextColor(UIHelper.TEXT_SECONDARY);
        suggestLabel.setPadding(0, 0, 0, UIHelper.dp(this, 8));
        dialogLayout.addView(suggestLabel);

        EditText input = new EditText(this);
        input.setHint("\u8F38\u5165\u4F60\u60F3\u8ABF\u6574\u7684\u5167\u5BB9...");
        input.setTextSize(14);
        input.setTextColor(UIHelper.TEXT_PRIMARY);
        input.setHintTextColor(UIHelper.TEXT_HINT);
        input.setMinLines(3);

        String[] suggestions = {
            "\u6211\u60F3\u591A\u52A0\u4E00\u500B\u666F\u9EDE",
            "\u7B2C_\u5929\u884C\u7A0B\u592A\u8D95\uFF0C\u8ACB\u7CBE\u7C21",
            "\u628A_\u666F\u9EDE\u63DB\u6210___",
            "\u591A\u5B89\u6392\u4E00\u4E9B\u7F8E\u98DF\u9910\u5EF3",
            "\u589E\u52A0\u89AA\u5B50\u53CB\u5584\u7684\u666F\u9EDE",
            "\u4EA4\u901A\u6539\u7528\u81EA\u99D5",
            "\u8ACB\u63A7\u5236\u6BCF\u65E5\u9810\u7B97\u5728___\u5143\u5167",
            "\u52A0\u5165\u591C\u5E02\u884C\u7A0B",
        };

        android.widget.HorizontalScrollView chipScroll = new android.widget.HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);

        for (String suggestion : suggestions) {
            TextView chip = new TextView(this);
            chip.setText(suggestion);
            chip.setTextSize(12);
            chip.setTextColor(UIHelper.ACCENT_BLUE);
            chip.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.ACCENT_BLUE, 16, 1, this));
            int cp = UIHelper.dp(this, 10);
            chip.setPadding(cp, UIHelper.dp(this, 6), cp, UIHelper.dp(this, 6));
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            chipLp.setMargins(0, 0, UIHelper.dp(this, 6), 0);
            chip.setLayoutParams(chipLp);
            chip.setOnClickListener(v -> {
                String current = input.getText().toString();
                if (current.isEmpty()) {
                    input.setText(suggestion);
                } else {
                    input.setText(current + "\uFF0C" + suggestion);
                }
                input.setSelection(input.getText().length());
            });
            chipRow.addView(chip);
        }
        chipScroll.addView(chipRow);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollLp.setMargins(0, 0, 0, UIHelper.dp(this, 12));
        chipScroll.setLayoutParams(scrollLp);
        dialogLayout.addView(chipScroll);

        // Input field
        dialogLayout.addView(input);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle("\uD83E\uDD16 AI \u8ABF\u6574\u884C\u7A0B")
                .setView(dialogLayout)
                .setPositiveButton("\u8ABF\u6574", (d, w) -> {
                    String instruction = input.getText().toString().trim();
                    if (instruction.isEmpty()) return;

                    showRefineLoading();
                    BridgeClient.refineItinerary(itineraryJson, instruction, (responseJson, offline, error) -> {
                        hideRefineLoading();
                        if (error != null) {
                            Toast.makeText(this, "\u8ABF\u6574\u5931\u6557: " + error, Toast.LENGTH_LONG).show();
                            return;
                        }
                        try {
                            JSONObject json = new JSONObject(responseJson);
                            if (json.optBoolean("success", false)) {
                                Object resultObj = json.opt("result");
                                String newJson;
                                if (resultObj instanceof JSONObject) {
                                    newJson = resultObj.toString();
                                } else {
                                    String text = String.valueOf(resultObj);
                                    int start = text.indexOf("{");
                                    int end = text.lastIndexOf("}");
                                    newJson = (start >= 0 && end > start) ? text.substring(start, end + 1) : text;
                                }
                                JSONObject newItinerary = new JSONObject(newJson);
                                double newCost = newItinerary.optDouble("total_estimated_cost", 0);
                                db.updateTripItinerary(tripId, newJson, newCost);
                                AppLog.i("Travel", "\u884C\u7A0B\u5DF2\u8ABF\u6574 tripId=" + tripId);
                                Toast.makeText(this, "\u884C\u7A0B\u5DF2\u66F4\u65B0\uFF01", Toast.LENGTH_SHORT).show();
                                loadTrip();
                            }
                        } catch (Exception e) {
                            Toast.makeText(this, "\u89E3\u6790\u5931\u6557: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("\u53D6\u6D88", null)
                .show();
    }

    private final Runnable refineTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!refining) return;
            long elapsed = (System.currentTimeMillis() - refineStartTime) / 1000;
            long min = elapsed / 60;
            long sec = elapsed % 60;
            String timeStr = min > 0
                ? String.format(Locale.US, "%d\u5206%02d\u79D2", min, sec)
                : sec + "\u79D2";
            String stage = elapsed < 30 ? "\uD83D\uDCDD \u6B63\u5728\u5206\u6790\u4FEE\u6539\u9700\u6C42..."
                : elapsed < 90 ? "\uD83D\uDD04 \u6B63\u5728\u91CD\u65B0\u898F\u5283\u884C\u7A0B..."
                : elapsed < 180 ? "\uD83D\uDCB0 \u6B63\u5728\u8A08\u7B97\u8CBB\u7528\u8207\u4EA4\u901A..."
                : "\u2705 \u5373\u5C07\u5B8C\u6210\uFF0C\u8ACB\u7A0D\u5019...";
            refineStatusTv.setText(stage + "\n\u23F1 \u5DF2\u7D93\u904E " + timeStr);
            refineTimerHandler.postDelayed(this, 1000);
        }
    };

    private void showRefineLoading() {
        refining = true;
        refineStartTime = System.currentTimeMillis();
        refineOverlay.setVisibility(View.VISIBLE);
        refineTimerHandler = new Handler(Looper.getMainLooper());
        refineTimerHandler.post(refineTimerRunnable);
    }

    private void hideRefineLoading() {
        refining = false;
        if (refineTimerHandler != null) refineTimerHandler.removeCallbacks(refineTimerRunnable);
        refineOverlay.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refineTimerHandler != null) refineTimerHandler.removeCallbacks(refineTimerRunnable);
    }

    private void openFullRoute() {
        if (itineraryJson == null) return;
        try {
            JSONObject itinerary = new JSONObject(itineraryJson);
            JSONArray daysArr = itinerary.optJSONArray("days");
            if (daysArr == null || daysArr.length() == 0) return;

            // Collect all spots from all days
            StringBuilder mapsUrl = new StringBuilder("https://www.google.com/maps/dir/");
            for (int d = 0; d < daysArr.length(); d++) {
                JSONArray spots = daysArr.getJSONObject(d).optJSONArray("spots");
                if (spots == null) continue;
                for (int s = 0; s < spots.length(); s++) {
                    JSONObject spot = spots.getJSONObject(s);
                    double lat = spot.optDouble("lat", 0);
                    double lng = spot.optDouble("lng", 0);
                    if (lat != 0 && lng != 0) {
                        mapsUrl.append(lat).append(",").append(lng).append("/");
                    } else {
                        mapsUrl.append(Uri.encode(spot.optString("name", ""))).append("/");
                    }
                }
            }
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl.toString())));
        } catch (Exception e) {
            Toast.makeText(this, "\u7121\u6CD5\u958B\u555F\u5730\u5716", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareTrip() {
        if (itineraryJson == null) return;
        try {
            JSONObject itinerary = new JSONObject(itineraryJson);
            StringBuilder sb = new StringBuilder();
            sb.append("\uD83D\uDDFA\uFE0F ").append(itinerary.optString("trip_name", tripName)).append("\n");

            JSONArray daysArr = itinerary.optJSONArray("days");
            if (daysArr != null) {
                for (int d = 0; d < daysArr.length(); d++) {
                    JSONObject day = daysArr.getJSONObject(d);
                    sb.append("\n\u2550\u2550\u2550 Day ").append(day.optInt("day"))
                            .append(": ").append(day.optString("theme", "")).append(" \u2550\u2550\u2550\n");

                    JSONArray spots = day.optJSONArray("spots");
                    if (spots != null) {
                        for (int s = 0; s < spots.length(); s++) {
                            JSONObject spot = spots.getJSONObject(s);
                            sb.append("\uD83D\uDCCD ").append(spot.optString("name", "")).append("\n");
                            String addr = spot.optString("address", "");
                            if (!addr.isEmpty()) sb.append("   ").append(addr).append("\n");
                            int stay = spot.optInt("stay_minutes", 0);
                            double cost = spot.optDouble("cost", 0);
                            if (stay > 0) sb.append("   \u505C\u7559 ").append(stay).append("\u5206\u9418");
                            if (cost > 0) sb.append(" | NT$").append(String.format(Locale.US, "%.0f", cost));
                            sb.append("\n");
                        }
                    }

                    JSONArray meals = day.optJSONArray("meals");
                    if (meals != null) {
                        for (int m = 0; m < meals.length(); m++) {
                            JSONObject meal = meals.getJSONObject(m);
                            sb.append("\uD83C\uDF74 ").append(meal.optString("name", "")).append("\n");
                        }
                    }
                }
            }

            double total = itinerary.optDouble("total_estimated_cost", 0);
            if (total > 0) {
                sb.append("\n\uD83D\uDCB0 \u9810\u4F30\u7E3D\u8CBB\u7528\uFF1ANT$").append(String.format(Locale.US, "%,.0f", total)).append("\n");
            }

            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, sb.toString());
            startActivity(Intent.createChooser(share, "\u5206\u4EAB\u884C\u7A0B"));

        } catch (Exception e) {
            Toast.makeText(this, "\u5206\u4EAB\u5931\u6557", Toast.LENGTH_SHORT).show();
        }
    }
}
