package com.mybot.app;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CreateTripActivity extends AppCompatActivity {

    private EditText destInput;
    private EditText daysInput;
    private EditText peopleInput;
    private EditText budgetInput;
    private TextView startDateTv;
    private String startDate = "";
    private String transportMode = "public";
    private String accommodationType = "\u98EF\u5E97";
    private final List<String> selectedPrefs = new ArrayList<>();
    private ProgressBar loading;
    private Button generateBtn;
    private TravelDbHelper db;

    // Region quick buttons
    private static final String[] REGIONS = {
        "\u5317\u5317\u57FA", "\u6843\u7AF9\u82D7", "\u4E2D\u5F70\u6295",
        "\u96F2\u5609\u5357", "\u9AD8\u5C4F", "\u5B9C\u82B1\u6771", "\u96E2\u5CF6"
    };
    private static final String[] REGION_DEFAULTS = {
        "\u53F0\u5317", "\u65B0\u7AF9", "\u53F0\u4E2D", "\u53F0\u5357", "\u9AD8\u96C4", "\u82B1\u84EE", "\u6F8E\u6E56"
    };

    private static final String[] PREFERENCES = {
        "\u7F8E\u98DF", "\u81EA\u7136", "\u6587\u9752", "\u89AA\u5B50", "\u5192\u96AA",
        "\u6EAB\u6CC9", "\u6D77\u7058", "\u591C\u5E02", "\u6B77\u53F2\u6587\u5316"
    };

    private static final String[] ACCOMMODATION_TYPES = {
        "\u98EF\u5E97", "\u6C11\u5BBF", "\u9752\u65C5", "\u9732\u71DF"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLog.i("Travel", "CreateTripActivity\u958B\u555F");
        db = TravelDbHelper.getInstance(this);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        buildUI();
    }

    private void buildUI() {
        LinearLayout root = UIHelper.pageRoot(this);

        // ── Top bar ──
        LinearLayout topBar = UIHelper.topBar(this, "\u65B0\u589E\u884C\u7A0B");
        TextView backBtn = new TextView(this);
        backBtn.setText("\u2190 ");
        backBtn.setTextSize(20);
        backBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn, 0);

        // ── Scrollable form ──
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 16);
        form.setPadding(p, p, p, p);

        // Destination
        form.addView(label("\u76EE\u7684\u5730"));
        destInput = styledInput("\u8F38\u5165\u76EE\u7684\u5730\uFF0C\u4F8B\u5982\uFF1A\u82B1\u84EE\u3001\u53F0\u5357\u3001\u58A8\u4E01");
        form.addView(destInput);

        // Region quick buttons
        LinearLayout regionRow = new LinearLayout(this);
        regionRow.setOrientation(LinearLayout.HORIZONTAL);
        regionRow.setPadding(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 8));
        for (int i = 0; i < REGIONS.length; i++) {
            final int idx = i;
            TextView chip = new TextView(this);
            chip.setText(REGIONS[i]);
            chip.setTextSize(12);
            chip.setTextColor(UIHelper.ACCENT_BLUE);
            chip.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.ACCENT_BLUE, 16, 1, this));
            int cp = UIHelper.dp(this, 8);
            chip.setPadding(cp, UIHelper.dp(this, 4), cp, UIHelper.dp(this, 4));
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipLp.setMargins(0, 0, UIHelper.dp(this, 6), 0);
            chip.setLayoutParams(chipLp);
            chip.setOnClickListener(v -> destInput.setText(REGION_DEFAULTS[idx]));
            regionRow.addView(chip);
        }
        ScrollView regionScroll = new ScrollView(this);
        regionScroll.setScrollbarFadingEnabled(true);
        // Use HorizontalScrollView
        android.widget.HorizontalScrollView hScroll = new android.widget.HorizontalScrollView(this);
        hScroll.setHorizontalScrollBarEnabled(false);
        hScroll.addView(regionRow);
        form.addView(hScroll);

        // Start date
        form.addView(label("\u51FA\u767C\u65E5\u671F"));
        startDateTv = new TextView(this);
        startDateTv.setText("\u9EDE\u64CA\u9078\u64C7\u65E5\u671F");
        startDateTv.setTextSize(15);
        startDateTv.setTextColor(UIHelper.TEXT_HINT);
        startDateTv.setBackground(UIHelper.roundRect(UIHelper.BG_INPUT, 8, this));
        int ip = UIHelper.dp(this, 12);
        startDateTv.setPadding(ip, ip, ip, ip);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateLp.setMargins(0, 0, 0, UIHelper.dp(this, 12));
        startDateTv.setLayoutParams(dateLp);
        startDateTv.setOnClickListener(v -> showDatePicker());
        form.addView(startDateTv);

        // Days + People (side by side)
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        int gap = UIHelper.dp(this, 8);

        LinearLayout daysCol = new LinearLayout(this);
        daysCol.setOrientation(LinearLayout.VERTICAL);
        daysCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        daysCol.addView(label("\u5929\u6578"));
        daysInput = styledInput("2");
        daysInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        daysCol.addView(daysInput);

        LinearLayout peopleCol = new LinearLayout(this);
        peopleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams peopleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        peopleLp.setMargins(gap, 0, 0, 0);
        peopleCol.setLayoutParams(peopleLp);
        peopleCol.addView(label("\u4EBA\u6578"));
        peopleInput = styledInput("2");
        peopleInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        peopleCol.addView(peopleInput);

        row1.addView(daysCol);
        row1.addView(peopleCol);
        form.addView(row1);

        // Budget
        form.addView(label("\u9810\u7B97 (NT$)"));
        budgetInput = styledInput("10000");
        budgetInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        form.addView(budgetInput);

        // Transport mode toggle
        form.addView(label("\u4EA4\u901A\u65B9\u5F0F"));
        LinearLayout transportRow = new LinearLayout(this);
        transportRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trLp.setMargins(0, 0, 0, UIHelper.dp(this, 12));
        transportRow.setLayoutParams(trLp);

        TextView publicBtn = toggleButton("\uD83D\uDE8C \u5927\u773E\u904B\u8F38", true);
        TextView carBtn = toggleButton("\uD83D\uDE97 \u81EA\u99D5", false);

        publicBtn.setOnClickListener(v -> {
            transportMode = "public";
            setToggleActive(publicBtn, true);
            setToggleActive(carBtn, false);
        });
        carBtn.setOnClickListener(v -> {
            transportMode = "car";
            setToggleActive(publicBtn, false);
            setToggleActive(carBtn, true);
        });

        transportRow.addView(publicBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(UIHelper.dp(this, 8), 0));
        transportRow.addView(spacer);
        transportRow.addView(carBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        form.addView(transportRow);

        // Preferences
        form.addView(label("\u65C5\u904A\u504F\u597D\uFF08\u53EF\u591A\u9078\uFF09"));
        LinearLayout prefsContainer = new LinearLayout(this);
        prefsContainer.setOrientation(LinearLayout.HORIZONTAL);
        android.widget.HorizontalScrollView prefScroll = new android.widget.HorizontalScrollView(this);
        prefScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams prefScrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        prefScrollLp.setMargins(0, 0, 0, UIHelper.dp(this, 12));
        prefScroll.setLayoutParams(prefScrollLp);

        for (String pref : PREFERENCES) {
            TextView chip = new TextView(this);
            chip.setText(pref);
            chip.setTextSize(13);
            chip.setTextColor(UIHelper.TEXT_SECONDARY);
            chip.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.TEXT_HINT, 16, 1, this));
            int cPad = UIHelper.dp(this, 10);
            chip.setPadding(cPad, UIHelper.dp(this, 6), cPad, UIHelper.dp(this, 6));
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cLp.setMargins(0, 0, UIHelper.dp(this, 6), 0);
            chip.setLayoutParams(cLp);

            chip.setOnClickListener(v -> {
                if (selectedPrefs.contains(pref)) {
                    selectedPrefs.remove(pref);
                    chip.setTextColor(UIHelper.TEXT_SECONDARY);
                    chip.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.TEXT_HINT, 16, 1, this));
                } else {
                    selectedPrefs.add(pref);
                    chip.setTextColor(UIHelper.ACCENT_GREEN);
                    chip.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.ACCENT_GREEN, 16, 1, this));
                }
            });
            prefsContainer.addView(chip);
        }
        prefScroll.addView(prefsContainer);
        form.addView(prefScroll);

        // Accommodation type
        form.addView(label("\u4F4F\u5BBF\u504F\u597D"));
        LinearLayout accomRow = new LinearLayout(this);
        accomRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams accomLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        accomLp.setMargins(0, 0, 0, UIHelper.dp(this, 16));
        accomRow.setLayoutParams(accomLp);

        List<TextView> accomBtns = new ArrayList<>();
        for (int i = 0; i < ACCOMMODATION_TYPES.length; i++) {
            final String type = ACCOMMODATION_TYPES[i];
            TextView btn = toggleButton(type, i == 0);
            btn.setOnClickListener(v -> {
                accommodationType = type;
                for (TextView b : accomBtns) setToggleActive(b, false);
                setToggleActive(btn, true);
            });
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) btnLp.setMargins(UIHelper.dp(this, 4), 0, 0, 0);
            accomRow.addView(btn, btnLp);
            accomBtns.add(btn);
        }
        form.addView(accomRow);

        // Loading
        loading = new ProgressBar(this, null, android.R.attr.progressBarStyle);
        loading.setVisibility(View.GONE);
        LinearLayout.LayoutParams loadLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        loadLp.gravity = Gravity.CENTER;
        loadLp.setMargins(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8));
        loading.setLayoutParams(loadLp);
        form.addView(loading);

        // Loading text
        TextView loadingText = new TextView(this);
        loadingText.setText("AI \u6B63\u5728\u898F\u5283\u884C\u7A0B\uFF0C\u9700\u8981\u7D04 2-5 \u5206\u9418...");
        loadingText.setTextSize(13);
        loadingText.setTextColor(UIHelper.TEXT_HINT);
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setVisibility(View.GONE);
        form.addView(loadingText);

        // Generate button
        generateBtn = UIHelper.primaryButton(this, "\uD83E\uDD16 AI \u751F\u6210\u884C\u7A0B");
        LinearLayout.LayoutParams genLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 50));
        genLp.setMargins(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 20));
        generateBtn.setLayoutParams(genLp);
        generateBtn.setOnClickListener(v -> {
            if (validateForm()) {
                generateBtn.setEnabled(false);
                generateBtn.setText("\u751F\u6210\u4E2D...");
                loading.setVisibility(View.VISIBLE);
                loadingText.setVisibility(View.VISIBLE);
                doGenerate();
            }
        });
        form.addView(generateBtn);

        scrollView.addView(form);
        root.addView(topBar);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private boolean validateForm() {
        String dest = destInput.getText().toString().trim();
        if (dest.isEmpty()) {
            Toast.makeText(this, "\u8ACB\u8F38\u5165\u76EE\u7684\u5730", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (startDate.isEmpty()) {
            Toast.makeText(this, "\u8ACB\u9078\u64C7\u51FA\u767C\u65E5\u671F", Toast.LENGTH_SHORT).show();
            return false;
        }
        String daysStr = daysInput.getText().toString().trim();
        if (daysStr.isEmpty() || Integer.parseInt(daysStr) < 1) {
            Toast.makeText(this, "\u8ACB\u8F38\u5165\u5929\u6578", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void doGenerate() {
        String dest = destInput.getText().toString().trim();
        int days = Integer.parseInt(daysInput.getText().toString().trim());
        int people = 2;
        try { people = Integer.parseInt(peopleInput.getText().toString().trim()); } catch (Exception ignored) {}
        double budget = 10000;
        try { budget = Double.parseDouble(budgetInput.getText().toString().trim()); } catch (Exception ignored) {}

        String prefsStr = String.join("、", selectedPrefs);
        if (prefsStr.isEmpty()) prefsStr = "\u7121\u7279\u5225\u504F\u597D";

        final int finalPeople = people;
        final double finalBudget = budget;
        final int finalDays = days;

        BridgeClient.generateItinerary(dest, days, people, prefsStr, transportMode,
                startDate, budget, accommodationType, (responseJson, offline, error) -> {
                    generateBtn.setEnabled(true);
                    generateBtn.setText("\uD83E\uDD16 AI \u751F\u6210\u884C\u7A0B");
                    findViewById(android.R.id.content).findViewWithTag("loading");
                    loading.setVisibility(View.GONE);

                    if (error != null) {
                        AppLog.e("Travel", "\u884C\u7A0B\u751F\u6210\u5931\u6557: " + error);
                        Toast.makeText(this, "\u751F\u6210\u5931\u6557: " + error, Toast.LENGTH_LONG).show();
                        return;
                    }

                    try {
                        JSONObject json = new JSONObject(responseJson);
                        if (!json.optBoolean("success", false)) {
                            Toast.makeText(this, "\u751F\u6210\u5931\u6557: " + json.optString("error", "unknown"),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        Object resultObj = json.opt("result");
                        String itineraryJson;
                        if (resultObj instanceof JSONObject) {
                            itineraryJson = resultObj.toString();
                        } else if (resultObj instanceof String) {
                            // Try to extract JSON from text
                            String text = (String) resultObj;
                            int start = text.indexOf("{");
                            int end = text.lastIndexOf("}");
                            if (start >= 0 && end > start) {
                                itineraryJson = text.substring(start, end + 1);
                            } else {
                                itineraryJson = text;
                            }
                        } else {
                            itineraryJson = String.valueOf(resultObj);
                        }

                        // Parse to get trip name and cost
                        JSONObject itinerary = new JSONObject(itineraryJson);
                        String tripName = itinerary.optString("trip_name",
                                dest + " " + finalDays + "\u65E5\u904A");
                        double estimatedCost = itinerary.optDouble("total_estimated_cost", finalBudget);

                        // Calculate end date
                        Calendar cal = Calendar.getInstance();
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                        cal.setTime(sdf.parse(startDate));
                        cal.add(Calendar.DAY_OF_MONTH, finalDays - 1);
                        String endDate = sdf.format(cal.getTime());

                        // Save to database
                        long tripId = db.insertTrip(tripName, dest, startDate, endDate,
                                finalDays, finalPeople, String.join(",", selectedPrefs),
                                transportMode, itineraryJson, estimatedCost);

                        AppLog.i("Travel", "\u884C\u7A0B\u5DF2\u5132\u5B58 id=" + tripId + " name=" + tripName);
                        Toast.makeText(this, "\u884C\u7A0B\u5DF2\u751F\u6210\uFF01", Toast.LENGTH_SHORT).show();

                        // Open plan view
                        Intent intent = new Intent(this, TravelPlanActivity.class);
                        intent.putExtra("trip_id", tripId);
                        startActivity(intent);
                        finish();

                    } catch (Exception e) {
                        AppLog.e("Travel", "\u884C\u7A0B\u89E3\u6790\u5931\u6557: " + e.getMessage());
                        Toast.makeText(this, "\u884C\u7A0B\u89E3\u6790\u5931\u6557\uFF0C\u8ACB\u91CD\u8A66", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            startDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            startDateTv.setText(startDate);
            startDateTv.setTextColor(UIHelper.TEXT_PRIMARY);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(UIHelper.TEXT_SECONDARY);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setPadding(0, 0, 0, UIHelper.dp(this, 4));
        return tv;
    }

    private EditText styledInput(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextSize(15);
        et.setTextColor(UIHelper.TEXT_PRIMARY);
        et.setHintTextColor(UIHelper.TEXT_HINT);
        et.setBackground(UIHelper.roundRect(UIHelper.BG_INPUT, 8, this));
        int p = UIHelper.dp(this, 12);
        et.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, UIHelper.dp(this, 12));
        et.setLayoutParams(lp);
        return et;
    }

    private TextView toggleButton(String text, boolean active) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        int tp = UIHelper.dp(this, 10);
        tv.setPadding(tp, tp, tp, tp);
        setToggleActive(tv, active);
        return tv;
    }

    private void setToggleActive(TextView tv, boolean active) {
        if (active) {
            tv.setTextColor(UIHelper.ACCENT_GREEN);
            tv.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.ACCENT_GREEN, 8, 1, this));
        } else {
            tv.setTextColor(UIHelper.TEXT_HINT);
            tv.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.TEXT_HINT, 8, 1, this));
        }
    }
}
