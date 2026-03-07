package com.mybot.app;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AddCalendarEventActivity extends AppCompatActivity {

    private EditText inputText;
    private LinearLayout resultLayout;
    private Button btnParse;
    private Button btnSave;
    private List<ParsedEvent> parsedEvents = new ArrayList<>();

    static class ParsedEvent {
        String title = "";
        String description = "";
        String location = "";
        String startDate = "";
        String startTime = "";
        String endDate = "";
        String endTime = "";
        boolean allDay = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "AI 新增事件");
        root.addView(topBar);

        // Content
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 16);
        content.setPadding(p, p, p, p);

        // Instructions card
        LinearLayout instrCard = UIHelper.card(this);
        TextView instrText = new TextView(this);
        instrText.setText("輸入自然語言描述，AI 將自動解析為日曆事件。\n\n"
                + "範例：\n"
                + "• 下週三下午 2 點到 4 點開會\n"
                + "• 3/15-3/17 台北出差\n"
                + "• 每週一早上 9 點英文課，共 4 週\n"
                + "• 明天晚上 7 點跟 John 吃飯，地點：信義區");
        instrText.setTextSize(13);
        instrText.setTextColor(UIHelper.TEXT_SECONDARY);
        instrCard.addView(instrText);
        content.addView(instrCard);

        // Text input
        inputText = new EditText(this);
        inputText.setHint("描述你要新增的事件...");
        inputText.setHintTextColor(UIHelper.TEXT_HINT);
        inputText.setTextColor(UIHelper.TEXT_PRIMARY);
        inputText.setTextSize(16);
        inputText.setMinLines(3);
        inputText.setGravity(Gravity.TOP | Gravity.START);
        inputText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        inputText.setBackground(UIHelper.roundRectStroke(UIHelper.BG_INPUT,
                android.graphics.Color.parseColor("#2E4050"), 14, 1, this));
        int ip = UIHelper.dp(this, 16);
        inputText.setPadding(ip, ip, ip, ip);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.setMargins(0, UIHelper.dp(this, 12), 0, UIHelper.dp(this, 8));
        inputText.setLayoutParams(inputLp);
        content.addView(inputText);

        // Parse button
        btnParse = UIHelper.primaryButton(this, "AI 解析");
        btnParse.setBackground(UIHelper.roundRect(UIHelper.ACCENT_BLUE, 14, this));
        btnParse.setOnClickListener(v -> parseWithAI());
        content.addView(btnParse);

        // Result area
        resultLayout = new LinearLayout(this);
        resultLayout.setOrientation(LinearLayout.VERTICAL);
        content.addView(resultLayout);

        // Save button (hidden initially)
        btnSave = UIHelper.primaryButton(this, "新增到日曆");
        btnSave.setVisibility(android.view.View.GONE);
        btnSave.setOnClickListener(v -> saveEvents());
        content.addView(btnSave);

        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private void parseWithAI() {
        String text = inputText.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "請輸入事件描述", Toast.LENGTH_SHORT).show();
            return;
        }

        btnParse.setEnabled(false);
        btnParse.setText("AI 解析中...");
        resultLayout.removeAllViews();
        btnSave.setVisibility(android.view.View.GONE);

        // Show loading
        TextView loading = new TextView(this);
        loading.setText("正在分析...");
        loading.setTextSize(14);
        loading.setTextColor(UIHelper.TEXT_SECONDARY);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, UIHelper.dp(this, 20), 0, 0);
        resultLayout.addView(loading);

        BridgeClient.parseCalendarEvent(text, (responseJson, offline, error) -> {
            btnParse.setEnabled(true);
            btnParse.setText("AI 解析");
            resultLayout.removeAllViews();

            if (offline || responseJson == null) {
                TextView err = new TextView(this);
                err.setText("AI 解析失敗" + (error != null ? ": " + error : ""));
                err.setTextSize(14);
                err.setTextColor(UIHelper.ACCENT_RED);
                err.setPadding(0, UIHelper.dp(this, 12), 0, 0);
                resultLayout.addView(err);
                return;
            }

            try {
                JSONObject json = new JSONObject(responseJson);
                if (!json.optBoolean("success", false)) {
                    TextView err = new TextView(this);
                    err.setText("AI 無法解析此內容");
                    err.setTextSize(14);
                    err.setTextColor(UIHelper.ACCENT_RED);
                    resultLayout.addView(err);
                    return;
                }

                JSONObject result = json.getJSONObject("result");
                JSONArray eventsArr = result.getJSONArray("events");
                parsedEvents.clear();

                resultLayout.addView(UIHelper.sectionHeader(this, "解析結果 (" + eventsArr.length() + " 個事件)"));

                for (int i = 0; i < eventsArr.length(); i++) {
                    JSONObject ev = eventsArr.getJSONObject(i);
                    ParsedEvent pe = new ParsedEvent();
                    pe.title = ev.optString("title", "");
                    pe.description = ev.optString("description", "");
                    pe.location = ev.optString("location", "");
                    pe.startDate = ev.optString("start_date", "");
                    pe.startTime = ev.optString("start_time", "");
                    pe.endDate = ev.optString("end_date", "");
                    pe.endTime = ev.optString("end_time", "");
                    pe.allDay = ev.optBoolean("all_day", false);

                    if (pe.endDate.isEmpty()) pe.endDate = pe.startDate;
                    parsedEvents.add(pe);

                    resultLayout.addView(createEventEditCard(pe, i));
                }

                if (!parsedEvents.isEmpty()) {
                    btnSave.setVisibility(android.view.View.VISIBLE);
                }
            } catch (Exception e) {
                TextView err = new TextView(this);
                err.setText("解析錯誤: " + e.getMessage());
                err.setTextSize(14);
                err.setTextColor(UIHelper.ACCENT_RED);
                resultLayout.addView(err);
            }
        });
    }

    private LinearLayout createEventEditCard(ParsedEvent pe, int index) {
        LinearLayout card = UIHelper.card(this);

        // Event number badge
        TextView numBadge = UIHelper.statusBadge(this, "事件 " + (index + 1), UIHelper.ACCENT_BLUE);
        card.addView(numBadge);

        // Title
        addFieldRow(card, "標題", pe.title, v -> pe.title = v);

        // Date/Time
        if (pe.allDay) {
            addDateRow(card, "開始日期", pe.startDate, v -> pe.startDate = v);
            addDateRow(card, "結束日期", pe.endDate, v -> pe.endDate = v);

            TextView allDayLabel = new TextView(this);
            allDayLabel.setText("(全天事件)");
            allDayLabel.setTextSize(12);
            allDayLabel.setTextColor(UIHelper.ACCENT_ORANGE);
            allDayLabel.setPadding(0, UIHelper.dp(this, 4), 0, 0);
            card.addView(allDayLabel);
        } else {
            addDateRow(card, "開始日期", pe.startDate, v -> pe.startDate = v);
            addTimeRow(card, "開始時間", pe.startTime, v -> pe.startTime = v);
            addDateRow(card, "結束日期", pe.endDate, v -> pe.endDate = v);
            addTimeRow(card, "結束時間", pe.endTime, v -> pe.endTime = v);
        }

        // Location
        if (!pe.location.isEmpty()) {
            addFieldRow(card, "地點", pe.location, v -> pe.location = v);
        }

        // Description
        if (!pe.description.isEmpty()) {
            addFieldRow(card, "備註", pe.description, v -> pe.description = v);
        }

        return card;
    }

    private interface FieldSetter {
        void set(String value);
    }

    private void addFieldRow(LinearLayout parent, String label, String value, FieldSetter setter) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UIHelper.dp(this, 8), 0, 0);

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextSize(13);
        lbl.setTextColor(UIHelper.TEXT_SECONDARY);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(
                UIHelper.dp(this, 72), ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText input = new EditText(this);
        input.setText(value);
        input.setTextColor(UIHelper.TEXT_PRIMARY);
        input.setTextSize(14);
        input.setBackground(UIHelper.roundRectStroke(UIHelper.BG_INPUT,
                android.graphics.Color.parseColor("#2E4050"), 8, 1, this));
        int pad = UIHelper.dp(this, 10);
        input.setPadding(pad, pad, pad, pad);
        input.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) setter.set(input.getText().toString().trim());
        });

        row.addView(lbl);
        row.addView(input);
        parent.addView(row);
    }

    private void addDateRow(LinearLayout parent, String label, String value, FieldSetter setter) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UIHelper.dp(this, 8), 0, 0);

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextSize(13);
        lbl.setTextColor(UIHelper.TEXT_SECONDARY);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(
                UIHelper.dp(this, 72), ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btn = UIHelper.smallButton(this, value.isEmpty() ? "選擇日期" : value, UIHelper.ACCENT_BLUE);
        btn.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            // Try parse existing date
            try {
                if (!value.isEmpty()) {
                    String[] parts = value.split("-");
                    cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
                }
            } catch (Exception ignored) {}

            new DatePickerDialog(this, (dp, y, m, d) -> {
                String date = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                btn.setText(date);
                setter.set(date);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        row.addView(lbl);
        row.addView(btn);
        parent.addView(row);
    }

    private void addTimeRow(LinearLayout parent, String label, String value, FieldSetter setter) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UIHelper.dp(this, 8), 0, 0);

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextSize(13);
        lbl.setTextColor(UIHelper.TEXT_SECONDARY);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(
                UIHelper.dp(this, 72), ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btn = UIHelper.smallButton(this, value.isEmpty() ? "選擇時間" : value, UIHelper.ACCENT_ORANGE);
        btn.setOnClickListener(v -> {
            int h = 9, m = 0;
            try {
                if (!value.isEmpty()) {
                    String[] parts = value.split(":");
                    h = Integer.parseInt(parts[0]);
                    m = Integer.parseInt(parts[1]);
                }
            } catch (Exception ignored) {}

            new TimePickerDialog(this, (tp, hour, minute) -> {
                String time = String.format(Locale.US, "%02d:%02d", hour, minute);
                btn.setText(time);
                setter.set(time);
            }, h, m, true).show();
        });

        row.addView(lbl);
        row.addView(btn);
        parent.addView(row);
    }

    private void saveEvents() {
        if (parsedEvents.isEmpty()) return;

        btnSave.setEnabled(false);
        btnSave.setText("新增中...");

        String calendarId = GoogleAuthHelper.getDefaultCalendar(this);
        final int[] saved = {0};
        final int[] failed = {0};
        final int total = parsedEvents.size();

        GoogleAuthHelper.getCachedOrFreshToken(this, (token, error) -> {
            if (token == null) {
                Toast.makeText(this, "Token 失敗: " + error, Toast.LENGTH_LONG).show();
                btnSave.setEnabled(true);
                btnSave.setText("新增到日曆");
                return;
            }

            for (ParsedEvent pe : parsedEvents) {
                String startDT, endDT;
                if (pe.allDay) {
                    startDT = pe.startDate;
                    endDT = pe.endDate;
                    // For all-day events, end date should be the day AFTER
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(sdf.parse(endDT));
                        cal.add(Calendar.DAY_OF_MONTH, 1);
                        endDT = sdf.format(cal.getTime());
                    } catch (Exception ignored) {}
                } else {
                    startDT = pe.startDate + "T" + (pe.startTime.isEmpty() ? "09:00" : pe.startTime) + ":00+08:00";
                    endDT = pe.endDate + "T" + (pe.endTime.isEmpty() ? "10:00" : pe.endTime) + ":00+08:00";
                }

                GoogleCalendarClient.createEvent(token, calendarId,
                        pe.title, pe.description, pe.location,
                        startDT, endDT, pe.allDay,
                        (success, eventId, err) -> {
                            if (success) saved[0]++;
                            else failed[0]++;

                            if (saved[0] + failed[0] >= total) {
                                btnSave.setEnabled(true);
                                btnSave.setText("新增到日曆");
                                String msg = "已新增 " + saved[0] + " 個事件";
                                if (failed[0] > 0) msg += "，" + failed[0] + " 個失敗";
                                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                                if (saved[0] > 0) {
                                    finish();
                                }
                            }
                        });
            }
        });
    }
}
