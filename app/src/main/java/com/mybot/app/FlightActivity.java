package com.mybot.app;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FlightActivity extends AppCompatActivity {

    private LinearLayout watchListContainer;
    private TextView statusText;
    private Switch checkToggle;
    private FlightWatchDbHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLog.i("Flight", "FlightActivity開啟");
        db = new FlightWatchDbHelper(this);

        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

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
        backBtn.setText("←");
        backBtn.setTextSize(20);
        backBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        backBtn.setPadding(0, 0, UIHelper.dp(this, 12), 0);
        backBtn.setOnClickListener(v -> finish());

        TextView titleTv = new TextView(this);
        titleTv.setText("✈ 航班監控");
        titleTv.setTextSize(18);
        titleTv.setTextColor(UIHelper.TEXT_PRIMARY);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleTv.setLayoutParams(titleLp);

        TextView addBtn = new TextView(this);
        addBtn.setText("＋ 新增");
        addBtn.setTextSize(14);
        addBtn.setTextColor(UIHelper.ACCENT_BLUE);
        addBtn.setOnClickListener(v -> showAddDialog());

        topBar.addView(backBtn);
        topBar.addView(titleTv);
        topBar.addView(addBtn);

        // ── Scrollable content ──
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int cp = UIHelper.dp(this, 16);
        content.setPadding(cp, cp, cp, cp);

        // ── Status card ──
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 12, this));
        int sp = UIHelper.dp(this, 14);
        statusCard.setPadding(sp, sp, sp, sp);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.setMargins(0, 0, 0, UIHelper.dp(this, 12));
        statusCard.setLayoutParams(statusLp);

        LinearLayout toggleRow = new LinearLayout(this);
        toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        toggleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView toggleLabel = new TextView(this);
        toggleLabel.setText("定時檢查（每6小時）");
        toggleLabel.setTextSize(14);
        toggleLabel.setTextColor(UIHelper.TEXT_PRIMARY);
        LinearLayout.LayoutParams toggleLabelLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        toggleLabel.setLayoutParams(toggleLabelLp);

        checkToggle = new Switch(this);
        checkToggle.setChecked(FlightWatchDbHelper.isFlightCheckEnabled(this));
        checkToggle.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                ReminderHelper.scheduleFlightCheck(this);
                AppLog.i("Flight", "啟用定時檢查");
                Toast.makeText(this, "已啟用航班定時檢查", Toast.LENGTH_SHORT).show();
            } else {
                ReminderHelper.cancelFlightCheck(this);
                AppLog.i("Flight", "停用定時檢查");
                Toast.makeText(this, "已停用航班定時檢查", Toast.LENGTH_SHORT).show();
            }
            updateStatus();
        });

        toggleRow.addView(toggleLabel);
        toggleRow.addView(checkToggle);
        statusCard.addView(toggleRow);

        statusText = new TextView(this);
        statusText.setTextSize(12);
        statusText.setTextColor(UIHelper.TEXT_SECONDARY);
        statusText.setPadding(0, UIHelper.dp(this, 6), 0, 0);
        statusCard.addView(statusText);

        content.addView(statusCard);

        // ── Watch list ──
        content.addView(UIHelper.sectionHeader(this, "監控清單"));

        watchListContainer = new LinearLayout(this);
        watchListContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(watchListContainer);

        scrollView.addView(content);
        root.addView(topBar);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        refreshList();
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
        updateStatus();
    }

    private void updateStatus() {
        boolean enabled = FlightWatchDbHelper.isFlightCheckEnabled(this);
        checkToggle.setChecked(enabled);
        if (enabled) {
            statusText.setText("狀態：已啟用 | 下次檢查：約6小時後");
            statusText.setTextColor(UIHelper.ACCENT_GREEN);
        } else {
            statusText.setText("狀態：已停用");
            statusText.setTextColor(UIHelper.TEXT_HINT);
        }
    }

    private void refreshList() {
        watchListContainer.removeAllViews();
        List<FlightWatchDbHelper.FlightWatch> watches = db.getAll();

        if (watches.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("尚未新增任何航班監控\n點擊右上角「＋ 新增」開始");
            empty.setTextSize(14);
            empty.setTextColor(UIHelper.TEXT_HINT);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, UIHelper.dp(this, 40), 0, 0);
            watchListContainer.addView(empty);
            return;
        }

        SimpleDateFormat timeFmt = new SimpleDateFormat("MM/dd HH:mm", Locale.US);

        for (FlightWatchDbHelper.FlightWatch watch : watches) {
            watchListContainer.addView(buildWatchCard(watch, timeFmt));
        }
    }

    private LinearLayout buildWatchCard(FlightWatchDbHelper.FlightWatch watch, SimpleDateFormat timeFmt) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(
                watch.enabled ? UIHelper.BG_CARD : UIHelper.BG_CARD_ALT, 12, this));
        int cp = UIHelper.dp(this, 14);
        card.setPadding(cp, cp, cp, cp);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, UIHelper.dp(this, 8));
        card.setLayoutParams(cardLp);

        // Row 1: route
        LinearLayout routeRow = new LinearLayout(this);
        routeRow.setOrientation(LinearLayout.HORIZONTAL);
        routeRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView route = new TextView(this);
        route.setText(watch.origin + " → " + watch.destination);
        route.setTextSize(16);
        route.setTextColor(UIHelper.TEXT_PRIMARY);
        route.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams routeLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        route.setLayoutParams(routeLp);

        TextView enabledBadge = new TextView(this);
        enabledBadge.setText(watch.enabled ? "啟用" : "停用");
        enabledBadge.setTextSize(11);
        enabledBadge.setTextColor(watch.enabled ? UIHelper.ACCENT_GREEN : UIHelper.TEXT_HINT);

        routeRow.addView(route);
        routeRow.addView(enabledBadge);
        card.addView(routeRow);

        // Row 2: date + mode
        TextView dateTv = new TextView(this);
        String dateStr = watch.departureDate;
        if (watch.returnDate != null && !watch.returnDate.isEmpty()) {
            dateStr += " ↔ " + watch.returnDate;
        }
        if ("month".equals(watch.searchMode)) {
            dateStr += " (整月)";
        }
        dateTv.setText(dateStr);
        dateTv.setTextSize(13);
        dateTv.setTextColor(UIHelper.TEXT_SECONDARY);
        dateTv.setPadding(0, UIHelper.dp(this, 4), 0, 0);
        card.addView(dateTv);

        // Row 3: prices
        LinearLayout priceRow = new LinearLayout(this);
        priceRow.setOrientation(LinearLayout.HORIZONTAL);
        priceRow.setPadding(0, UIHelper.dp(this, 6), 0, 0);

        TextView targetTv = new TextView(this);
        targetTv.setText("目標: $" + String.format("%.0f", watch.targetPrice));
        targetTv.setTextSize(13);
        targetTv.setTextColor(UIHelper.ACCENT_ORANGE);
        LinearLayout.LayoutParams priceLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        targetTv.setLayoutParams(priceLp);

        TextView lowestTv = new TextView(this);
        if (watch.lastLowestPrice > 0) {
            lowestTv.setText("最低: $" + String.format("%.0f", watch.lastLowestPrice));
            lowestTv.setTextColor(watch.lastLowestPrice <= watch.targetPrice
                    ? UIHelper.ACCENT_GREEN : UIHelper.TEXT_SECONDARY);
        } else {
            lowestTv.setText("尚未查詢");
            lowestTv.setTextColor(UIHelper.TEXT_HINT);
        }
        lowestTv.setTextSize(13);

        priceRow.addView(targetTv);
        priceRow.addView(lowestTv);
        card.addView(priceRow);

        // Row 4: last checked
        if (watch.lastChecked > 0) {
            TextView checkedTv = new TextView(this);
            checkedTv.setText("上次查詢: " + timeFmt.format(new Date(watch.lastChecked)));
            checkedTv.setTextSize(11);
            checkedTv.setTextColor(UIHelper.TEXT_HINT);
            checkedTv.setPadding(0, UIHelper.dp(this, 4), 0, 0);
            card.addView(checkedTv);
        }

        // Row 5: action buttons
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.END);
        actionRow.setPadding(0, UIHelper.dp(this, 8), 0, 0);

        // Search button
        TextView searchBtn = new TextView(this);
        searchBtn.setText("🔍 搜尋");
        searchBtn.setTextSize(13);
        searchBtn.setTextColor(UIHelper.ACCENT_BLUE);
        searchBtn.setPadding(UIHelper.dp(this, 12), UIHelper.dp(this, 6),
                UIHelper.dp(this, 12), UIHelper.dp(this, 6));
        searchBtn.setOnClickListener(v -> manualSearch(watch, card));

        // Toggle button
        TextView toggleBtn = new TextView(this);
        toggleBtn.setText(watch.enabled ? "⏸ 暫停" : "▶ 啟用");
        toggleBtn.setTextSize(13);
        toggleBtn.setTextColor(UIHelper.ACCENT_ORANGE);
        toggleBtn.setPadding(UIHelper.dp(this, 12), UIHelper.dp(this, 6),
                UIHelper.dp(this, 12), UIHelper.dp(this, 6));
        toggleBtn.setOnClickListener(v -> {
            db.setEnabled(watch.id, !watch.enabled);
            AppLog.i("Flight", (watch.enabled ? "暫停" : "啟用") + "監控 id=" + watch.id);
            refreshList();
        });

        // Delete button
        TextView deleteBtn = new TextView(this);
        deleteBtn.setText("🗑 刪除");
        deleteBtn.setTextSize(13);
        deleteBtn.setTextColor(UIHelper.ACCENT_RED);
        deleteBtn.setPadding(UIHelper.dp(this, 12), UIHelper.dp(this, 6),
                UIHelper.dp(this, 12), UIHelper.dp(this, 6));
        deleteBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("刪除監控")
                    .setMessage("確定刪除 " + watch.origin + "→" + watch.destination + " 的監控？")
                    .setPositiveButton("刪除", (d, w) -> {
                        db.delete(watch.id);
                        AppLog.i("Flight", "刪除監控 id=" + watch.id);
                        refreshList();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        actionRow.addView(searchBtn);
        actionRow.addView(toggleBtn);
        actionRow.addView(deleteBtn);
        card.addView(actionRow);

        // Tap card to show result detail
        card.setOnClickListener(v -> {
            if (watch.lastResultJson != null && !watch.lastResultJson.isEmpty()) {
                showResultDialog(watch);
            } else {
                Toast.makeText(this, "尚無搜尋結果，請先手動搜尋", Toast.LENGTH_SHORT).show();
            }
        });

        return card;
    }

    private void manualSearch(FlightWatchDbHelper.FlightWatch watch, LinearLayout card) {
        AppLog.i("Flight", "手動搜尋: " + watch.origin + "→" + watch.destination);

        // Show loading indicator
        ProgressBar progress = new ProgressBar(this);
        progress.setTag("loading");
        card.addView(progress);

        BridgeClient.searchFlights(watch.origin, watch.destination,
                watch.departureDate, watch.returnDate, watch.searchMode,
                (responseJson, offline, error) -> {
                    // Remove loading
                    View loading = card.findViewWithTag("loading");
                    if (loading != null) card.removeView(loading);

                    if (error != null) {
                        AppLog.e("Flight", "手動搜尋失敗: " + error);
                        Toast.makeText(this, "搜尋失敗: " + error, Toast.LENGTH_LONG).show();
                        return;
                    }

                    try {
                        JSONObject json = new JSONObject(responseJson);
                        if (!json.optBoolean("success", false)) {
                            String errMsg = json.optString("error", "unknown");
                            Toast.makeText(this, "搜尋失敗: " + errMsg, Toast.LENGTH_LONG).show();
                            return;
                        }

                        Object resultObj = json.opt("result");
                        JSONObject result;
                        if (resultObj instanceof JSONObject) {
                            result = (JSONObject) resultObj;
                        } else if (resultObj instanceof String) {
                            String text = (String) resultObj;
                            int start = text.indexOf("{");
                            int end = text.lastIndexOf("}");
                            if (start >= 0 && end > start) {
                                result = new JSONObject(text.substring(start, end + 1));
                            } else {
                                Toast.makeText(this, "無法解析搜尋結果", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        } else {
                            Toast.makeText(this, "搜尋結果格式異常", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        double cheapest = result.optDouble("cheapest_price", 0);
                        db.updateCheckResult(watch.id, System.currentTimeMillis(),
                                cheapest, result.toString());

                        AppLog.i("Flight", "手動搜尋完成: 最低價=" + cheapest);

                        int flightCount = 0;
                        JSONArray flights = result.optJSONArray("flights");
                        if (flights != null) flightCount = flights.length();

                        Toast.makeText(this,
                                String.format("找到 %d 個航班，最低價 $%.0f", flightCount, cheapest),
                                Toast.LENGTH_LONG).show();

                        refreshList();
                    } catch (Exception e) {
                        AppLog.e("Flight", "解析搜尋結果異常: " + e.getMessage());
                        Toast.makeText(this, "解析結果失敗", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showResultDialog(FlightWatchDbHelper.FlightWatch watch) {
        try {
            JSONObject result = new JSONObject(watch.lastResultJson);
            JSONArray flights = result.optJSONArray("flights");

            StringBuilder sb = new StringBuilder();
            sb.append(watch.origin).append(" → ").append(watch.destination).append("\n");
            sb.append("搜尋日期: ").append(result.optString("search_date", "")).append("\n");
            sb.append("最低價: $").append(String.format("%.0f", result.optDouble("cheapest_price", 0)));
            sb.append("\n\n");

            if (flights != null && flights.length() > 0) {
                int limit = Math.min(flights.length(), 10);
                for (int i = 0; i < limit; i++) {
                    JSONObject f = flights.getJSONObject(i);
                    sb.append("── 航班 ").append(i + 1).append(" ──\n");
                    sb.append("💰 $").append(String.format("%.0f", f.optDouble("price", 0)));
                    String currency = f.optString("currency", "");
                    if (!currency.isEmpty()) sb.append(" ").append(currency);
                    sb.append("\n");

                    String airline = f.optString("airline", "");
                    if (!airline.isEmpty()) sb.append("✈ ").append(airline).append("\n");

                    String depDate = f.optString("departure_date", "");
                    String depTime = f.optString("departure_time", "");
                    String arrTime = f.optString("arrival_time", "");
                    if (!depDate.isEmpty()) sb.append("📅 ").append(depDate).append(" ");
                    if (!depTime.isEmpty()) sb.append(depTime);
                    if (!arrTime.isEmpty()) sb.append(" → ").append(arrTime);
                    sb.append("\n");

                    String duration = f.optString("duration", "");
                    if (!duration.isEmpty()) sb.append("⏱ ").append(duration).append("\n");

                    int stops = f.optInt("stops", -1);
                    if (stops >= 0) sb.append(stops == 0 ? "直飛" : "轉機 " + stops + " 次").append("\n");

                    String bookingUrl = f.optString("booking_url", "");
                    if (!bookingUrl.isEmpty()) sb.append("🔗 ").append(bookingUrl).append("\n");

                    sb.append("\n");
                }
                if (flights.length() > limit) {
                    sb.append("...還有 ").append(flights.length() - limit).append(" 個航班\n");
                }
            } else {
                sb.append("無航班資料");
            }

            new AlertDialog.Builder(this)
                    .setTitle("✈ 搜尋結果")
                    .setMessage(sb.toString())
                    .setPositiveButton("確定", null)
                    .show();

        } catch (Exception e) {
            Toast.makeText(this, "無法顯示結果", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAddDialog() {
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        int dp = UIHelper.dp(this, 16);
        dialogLayout.setPadding(dp, dp, dp, 0);

        // Origin
        TextView originLabel = new TextView(this);
        originLabel.setText("出發地 (IATA代碼，如 TPE)");
        originLabel.setTextSize(13);
        dialogLayout.addView(originLabel);

        EditText originInput = new EditText(this);
        originInput.setHint("TPE");
        originInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        dialogLayout.addView(originInput);

        // Destination
        TextView destLabel = new TextView(this);
        destLabel.setText("目的地 (IATA代碼，如 NRT)");
        destLabel.setTextSize(13);
        destLabel.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        dialogLayout.addView(destLabel);

        EditText destInput = new EditText(this);
        destInput.setHint("NRT");
        destInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        dialogLayout.addView(destInput);

        // Search mode toggle
        TextView modeLabel = new TextView(this);
        modeLabel.setText("搜尋模式");
        modeLabel.setTextSize(13);
        modeLabel.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        dialogLayout.addView(modeLabel);

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        final String[] selectedMode = {"date"};

        TextView dateMode = new TextView(this);
        dateMode.setText("📅 指定日期");
        dateMode.setTextSize(14);
        dateMode.setTextColor(UIHelper.ACCENT_BLUE);
        dateMode.setPadding(dp, UIHelper.dp(this, 8), dp, UIHelper.dp(this, 8));

        TextView monthMode = new TextView(this);
        monthMode.setText("📆 整月搜尋");
        monthMode.setTextSize(14);
        monthMode.setTextColor(UIHelper.TEXT_HINT);
        monthMode.setPadding(dp, UIHelper.dp(this, 8), dp, UIHelper.dp(this, 8));

        dateMode.setOnClickListener(v -> {
            selectedMode[0] = "date";
            dateMode.setTextColor(UIHelper.ACCENT_BLUE);
            monthMode.setTextColor(UIHelper.TEXT_HINT);
        });
        monthMode.setOnClickListener(v -> {
            selectedMode[0] = "month";
            monthMode.setTextColor(UIHelper.ACCENT_BLUE);
            dateMode.setTextColor(UIHelper.TEXT_HINT);
        });

        modeRow.addView(dateMode);
        modeRow.addView(monthMode);
        dialogLayout.addView(modeRow);

        // Departure date
        TextView depLabel = new TextView(this);
        depLabel.setText("出發日期");
        depLabel.setTextSize(13);
        depLabel.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        dialogLayout.addView(depLabel);

        TextView depDatePicker = new TextView(this);
        depDatePicker.setText("點擊選擇日期");
        depDatePicker.setTextSize(14);
        depDatePicker.setTextColor(UIHelper.ACCENT_BLUE);
        depDatePicker.setPadding(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8));
        final String[] depDate = {""};
        depDatePicker.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (view, y, m, d) -> {
                depDate[0] = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                depDatePicker.setText(depDate[0]);
                depDatePicker.setTextColor(UIHelper.TEXT_PRIMARY);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        dialogLayout.addView(depDatePicker);

        // Return date (optional)
        TextView retLabel = new TextView(this);
        retLabel.setText("回程日期（選填，空白=單程）");
        retLabel.setTextSize(13);
        retLabel.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        dialogLayout.addView(retLabel);

        TextView retDatePicker = new TextView(this);
        retDatePicker.setText("點擊選擇日期（可跳過）");
        retDatePicker.setTextSize(14);
        retDatePicker.setTextColor(UIHelper.TEXT_HINT);
        retDatePicker.setPadding(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8));
        final String[] retDate = {""};
        retDatePicker.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (view, y, m, d) -> {
                retDate[0] = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                retDatePicker.setText(retDate[0]);
                retDatePicker.setTextColor(UIHelper.TEXT_PRIMARY);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        dialogLayout.addView(retDatePicker);

        // Target price
        TextView priceLabel = new TextView(this);
        priceLabel.setText("目標價格 (TWD)");
        priceLabel.setTextSize(13);
        priceLabel.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        dialogLayout.addView(priceLabel);

        EditText priceInput = new EditText(this);
        priceInput.setHint("例如 5000");
        priceInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        dialogLayout.addView(priceInput);

        new AlertDialog.Builder(this)
                .setTitle("新增航班監控")
                .setView(dialogLayout)
                .setPositiveButton("新增", (dialog, which) -> {
                    String origin = originInput.getText().toString().trim().toUpperCase();
                    String dest = destInput.getText().toString().trim().toUpperCase();
                    String priceStr = priceInput.getText().toString().trim();

                    if (origin.isEmpty() || dest.isEmpty() || depDate[0].isEmpty() || priceStr.isEmpty()) {
                        Toast.makeText(this, "請填寫所有必填欄位", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double price;
                    try {
                        price = Double.parseDouble(priceStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "價格格式不正確", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    long id = db.insert(origin, dest, depDate[0], retDate[0],
                            selectedMode[0], price, "TWD");
                    AppLog.i("Flight", "新增監控: " + origin + "→" + dest
                            + " date=" + depDate[0] + " target=$" + price + " id=" + id);

                    Toast.makeText(this, "已新增航班監控", Toast.LENGTH_SHORT).show();
                    refreshList();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
