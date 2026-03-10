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
        route.setText(getAirportLabel(watch.origin) + " → " + getAirportLabel(watch.destination));
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

    // ── Airport data: IATA, Chinese city, country, region ──
    private static final String[][] AIRPORTS = {
        // 台灣
        {"TPE", "台北桃園", "台灣", "🇹🇼"},
        {"TSA", "台北松山", "台灣", "🇹🇼"},
        {"KHH", "高雄", "台灣", "🇹🇼"},
        {"RMQ", "台中", "台灣", "🇹🇼"},
        // 日本
        {"NRT", "東京成田", "日本", "🇯🇵"},
        {"HND", "東京羽田", "日本", "🇯🇵"},
        {"KIX", "大阪關西", "日本", "🇯🇵"},
        {"ITM", "大阪伊丹", "日本", "🇯🇵"},
        {"CTS", "札幌新千歲", "日本", "🇯🇵"},
        {"FUK", "福岡", "日本", "🇯🇵"},
        {"NGO", "名古屋中部", "日本", "🇯🇵"},
        {"OKA", "沖繩那霸", "日本", "🇯🇵"},
        // 韓國
        {"ICN", "首爾仁川", "韓國", "🇰🇷"},
        {"GMP", "首爾金浦", "韓國", "🇰🇷"},
        {"PUS", "釜山", "韓國", "🇰🇷"},
        {"CJU", "濟州", "韓國", "🇰🇷"},
        // 東南亞
        {"BKK", "曼谷素萬那普", "泰國", "🇹🇭"},
        {"DMK", "曼谷廊曼", "泰國", "🇹🇭"},
        {"CNX", "清邁", "泰國", "🇹🇭"},
        {"SIN", "新加坡樟宜", "新加坡", "🇸🇬"},
        {"KUL", "吉隆坡", "馬來西亞", "🇲🇾"},
        {"MNL", "馬尼拉", "菲律賓", "🇵🇭"},
        {"CEB", "宿霧", "菲律賓", "🇵🇭"},
        {"SGN", "胡志明市", "越南", "🇻🇳"},
        {"HAN", "河內", "越南", "🇻🇳"},
        {"DPS", "峇里島", "印尼", "🇮🇩"},
        {"CGK", "雅加達", "印尼", "🇮🇩"},
        {"REP", "暹粒(吳哥窟)", "柬埔寨", "🇰🇭"},
        {"RGN", "仰光", "緬甸", "🇲🇲"},
        // 港澳中國
        {"HKG", "香港", "香港", "🇭🇰"},
        {"MFM", "澳門", "澳門", "🇲🇴"},
        {"PVG", "上海浦東", "中國", "🇨🇳"},
        {"SHA", "上海虹橋", "中國", "🇨🇳"},
        {"PEK", "北京首都", "中國", "🇨🇳"},
        {"CAN", "廣州", "中國", "🇨🇳"},
        {"SZX", "深圳", "中國", "🇨🇳"},
        {"CTU", "成都", "中國", "🇨🇳"},
        {"XMN", "廈門", "中國", "🇨🇳"},
        // 歐洲
        {"LHR", "倫敦希斯洛", "英國", "🇬🇧"},
        {"CDG", "巴黎戴高樂", "法國", "🇫🇷"},
        {"FRA", "法蘭克福", "德國", "🇩🇪"},
        {"AMS", "阿姆斯特丹", "荷蘭", "🇳🇱"},
        {"FCO", "羅馬", "義大利", "🇮🇹"},
        {"BCN", "巴塞隆納", "西班牙", "🇪🇸"},
        {"VIE", "維也納", "奧地利", "🇦🇹"},
        {"ZRH", "蘇黎世", "瑞士", "🇨🇭"},
        {"IST", "伊斯坦堡", "土耳其", "🇹🇷"},
        {"PRG", "布拉格", "捷克", "🇨🇿"},
        // 美洲
        {"LAX", "洛杉磯", "美國", "🇺🇸"},
        {"SFO", "舊金山", "美國", "🇺🇸"},
        {"JFK", "紐約乘甘迺迪", "美國", "🇺🇸"},
        {"SEA", "西雅圖", "美國", "🇺🇸"},
        {"YVR", "溫哥華", "加拿大", "🇨🇦"},
        // 大洋洲
        {"SYD", "雪梨", "澳洲", "🇦🇺"},
        {"MEL", "墨爾本", "澳洲", "🇦🇺"},
        {"AKL", "奧克蘭", "紐西蘭", "🇳🇿"},
        // 中東
        {"DXB", "杜拜", "阿聯酋", "🇦🇪"},
        {"DOH", "杜哈", "卡達", "🇶🇦"},
    };

    private static String getAirportLabel(String iata) {
        for (String[] a : AIRPORTS) {
            if (a[0].equalsIgnoreCase(iata)) {
                return iata + " " + a[1];
            }
        }
        return iata;
    }

    private void showAirportPicker(String title, TextView targetView, String[] selectedCode) {
        // Build filterable list with search
        LinearLayout pickerLayout = new LinearLayout(this);
        pickerLayout.setOrientation(LinearLayout.VERTICAL);
        int dp = UIHelper.dp(this, 16);
        pickerLayout.setPadding(dp, dp, dp, 0);

        EditText searchInput = new EditText(this);
        searchInput.setHint("搜尋城市、國家或代碼...");
        searchInput.setTextSize(14);
        pickerLayout.addView(searchInput);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 350)));

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer);
        pickerLayout.addView(scrollView);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(pickerLayout)
                .setNegativeButton("取消", null)
                .create();

        // Populate airport list
        Runnable populateList = () -> {
            listContainer.removeAllViews();
            String query = searchInput.getText().toString().trim().toLowerCase();

            String lastCountry = "";
            for (String[] airport : AIRPORTS) {
                String code = airport[0];
                String city = airport[1];
                String country = airport[2];
                String flag = airport[3];

                // Filter by search query
                if (!query.isEmpty()) {
                    if (!code.toLowerCase().contains(query)
                            && !city.toLowerCase().contains(query)
                            && !country.toLowerCase().contains(query)) {
                        continue;
                    }
                }

                // Country header
                if (!country.equals(lastCountry)) {
                    TextView header = new TextView(FlightActivity.this);
                    header.setText(flag + " " + country);
                    header.setTextSize(12);
                    header.setTextColor(UIHelper.ACCENT_BLUE);
                    header.setTypeface(Typeface.DEFAULT_BOLD);
                    header.setPadding(0, UIHelper.dp(FlightActivity.this, 10), 0,
                            UIHelper.dp(FlightActivity.this, 4));
                    listContainer.addView(header);
                    lastCountry = country;
                }

                // Airport item
                TextView item = new TextView(FlightActivity.this);
                item.setText(code + "  " + city);
                item.setTextSize(15);
                item.setTextColor(UIHelper.TEXT_PRIMARY);
                int itemPad = UIHelper.dp(FlightActivity.this, 10);
                item.setPadding(UIHelper.dp(FlightActivity.this, 8), itemPad, 0, itemPad);
                item.setOnClickListener(v -> {
                    selectedCode[0] = code;
                    targetView.setText(code + " " + city + " (" + country + ")");
                    targetView.setTextColor(UIHelper.TEXT_PRIMARY);
                    dialog.dismiss();
                });
                listContainer.addView(item);
            }

            if (listContainer.getChildCount() == 0) {
                TextView empty = new TextView(FlightActivity.this);
                empty.setText("找不到符合的機場");
                empty.setTextSize(14);
                empty.setTextColor(UIHelper.TEXT_HINT);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, UIHelper.dp(FlightActivity.this, 20), 0, 0);
                listContainer.addView(empty);
            }
        };

        populateList.run();

        // Live search filter
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                populateList.run();
            }
        });

        dialog.show();
    }

    private void showAddDialog() {
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        int dp = UIHelper.dp(this, 16);
        dialogLayout.setPadding(dp, dp, dp, 0);

        // Origin
        TextView originLabel = new TextView(this);
        originLabel.setText("出發地");
        originLabel.setTextSize(13);
        dialogLayout.addView(originLabel);

        TextView originPicker = new TextView(this);
        originPicker.setText("點擊選擇出發機場");
        originPicker.setTextSize(14);
        originPicker.setTextColor(UIHelper.ACCENT_BLUE);
        originPicker.setPadding(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8));
        final String[] originCode = {""};
        originPicker.setOnClickListener(v -> showAirportPicker("選擇出發機場", originPicker, originCode));
        dialogLayout.addView(originPicker);

        // Destination
        TextView destLabel = new TextView(this);
        destLabel.setText("目的地");
        destLabel.setTextSize(13);
        destLabel.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        dialogLayout.addView(destLabel);

        TextView destPicker = new TextView(this);
        destPicker.setText("點擊選擇目的地機場");
        destPicker.setTextSize(14);
        destPicker.setTextColor(UIHelper.ACCENT_BLUE);
        destPicker.setPadding(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8));
        final String[] destCode = {""};
        destPicker.setOnClickListener(v -> showAirportPicker("選擇目的地機場", destPicker, destCode));
        dialogLayout.addView(destPicker);

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
                    String origin = originCode[0].trim().toUpperCase();
                    String dest = destCode[0].trim().toUpperCase();
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
