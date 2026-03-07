package com.mybot.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StockClient {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String REALTIME_URL = "https://mis.twse.com.tw/stock/api/getStockInfo.jsp";
    private static final String HISTORY_URL = "https://www.twse.com.tw/exchangeReport/STOCK_DAY";

    // Rate limiting: track last request times
    private static final long[] requestTimes = new long[3];
    private static int requestIndex = 0;

    // Backoff state
    private static int backoffLevel = 0; // 0=normal, 1=10s, 2=30s, 3=60s
    private static long lastBlockedTime = 0;

    public static int getBackoffLevel() { return backoffLevel; }

    public interface StockCallback {
        void onResult(List<StockData.StockQuote> quotes, String error);
    }

    public interface HistoryCallback {
        void onResult(List<StockData.CandleBar> candles, String error);
    }

    public static void fetchStocks(List<String> codes, StockCallback callback) {
        executor.execute(() -> {
            try {
                enforceRateLimit();

                StringBuilder exCh = new StringBuilder();
                for (int i = 0; i < codes.size(); i++) {
                    if (i > 0) exCh.append("|");
                    exCh.append("tse_").append(codes.get(i)).append(".tw");
                }

                String urlStr = REALTIME_URL + "?ex_ch=" + exCh + "&json=1&delay=0";
                String response = httpGet(urlStr, 10000);

                if (response == null || response.trim().isEmpty()) {
                    handleBlock();
                    mainHandler.post(() -> callback.onResult(null, "Empty response"));
                    return;
                }

                // Detect non-JSON (blocked)
                if (!response.trim().startsWith("{")) {
                    handleBlock();
                    mainHandler.post(() -> callback.onResult(null, "blocked"));
                    return;
                }

                // Reset backoff on success
                backoffLevel = 0;

                JSONObject json = new JSONObject(response);
                JSONArray msgArray = json.optJSONArray("msgArray");
                if (msgArray == null) {
                    mainHandler.post(() -> callback.onResult(null, "No data"));
                    return;
                }

                List<StockData.StockQuote> quotes = new ArrayList<>();
                for (int i = 0; i < msgArray.length(); i++) {
                    JSONObject item = msgArray.getJSONObject(i);
                    StockData.StockQuote q = new StockData.StockQuote();
                    q.code = item.optString("c", "");
                    q.name = item.optString("n", "");
                    q.time = item.optString("t", "");

                    String zStr = item.optString("z", "-");
                    q.currentPrice = parseDouble(zStr);
                    q.open = parseDouble(item.optString("o", "-"));
                    q.high = parseDouble(item.optString("h", "-"));
                    q.low = parseDouble(item.optString("l", "-"));
                    q.prevClose = parseDouble(item.optString("y", "-"));
                    q.volume = parseLong(item.optString("v", "0"));

                    // If current price not available, use prev close
                    if (q.currentPrice <= 0 && q.prevClose > 0) {
                        q.currentPrice = q.prevClose;
                    }

                    quotes.add(q);
                }

                mainHandler.post(() -> callback.onResult(quotes, null));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    public static void fetchHistory(String code, int year, int month, HistoryCallback callback) {
        executor.execute(() -> {
            try {
                enforceRateLimit();

                String dateStr = String.format(Locale.US, "%04d%02d01", year, month);
                String urlStr = HISTORY_URL + "?response=json&date=" + dateStr + "&stockNo=" + code;
                String response = httpGet(urlStr, 15000);

                if (response == null || response.trim().isEmpty()) {
                    handleBlock();
                    mainHandler.post(() -> callback.onResult(null, "Empty response"));
                    return;
                }

                if (!response.trim().startsWith("{")) {
                    handleBlock();
                    mainHandler.post(() -> callback.onResult(null, "blocked"));
                    return;
                }

                backoffLevel = 0;

                JSONObject json = new JSONObject(response);
                JSONArray data = json.optJSONArray("data");
                if (data == null) {
                    mainHandler.post(() -> callback.onResult(new ArrayList<>(), null));
                    return;
                }

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
                List<StockData.CandleBar> candles = new ArrayList<>();

                for (int i = 0; i < data.length(); i++) {
                    JSONArray row = data.getJSONArray(i);
                    // Format: [日期, 成交股數, 成交金額, 開盤價, 最高價, 最低價, 收盤價, 漲跌價差, 成交筆數]
                    String dateField = row.getString(0);
                    // TWSE uses ROC year (民國年), convert: 114/01/02 → 2025/01/02
                    String[] dateParts = dateField.split("/");
                    if (dateParts.length == 3) {
                        int rocYear = Integer.parseInt(dateParts[0].trim());
                        String fullDate = (rocYear + 1911) + "/" + dateParts[1] + "/" + dateParts[2];
                        long timestamp = sdf.parse(fullDate).getTime();

                        double open = parseDouble(row.getString(3).replace(",", ""));
                        double high = parseDouble(row.getString(4).replace(",", ""));
                        double low = parseDouble(row.getString(5).replace(",", ""));
                        double close = parseDouble(row.getString(6).replace(",", ""));
                        long volume = parseLong(row.getString(1).replace(",", ""));

                        if (open > 0 && close > 0) {
                            candles.add(new StockData.CandleBar(timestamp, open, high, low, close, volume));
                        }
                    }
                }

                mainHandler.post(() -> callback.onResult(candles, null));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    public static void fetchMultiMonthHistory(String code, int months, HistoryCallback callback) {
        executor.execute(() -> {
            try {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                List<StockData.CandleBar> allCandles = new ArrayList<>();

                for (int m = months - 1; m >= 0; m--) {
                    java.util.Calendar c = (java.util.Calendar) cal.clone();
                    c.add(java.util.Calendar.MONTH, -m);
                    int year = c.get(java.util.Calendar.YEAR);
                    int month = c.get(java.util.Calendar.MONTH) + 1;

                    enforceRateLimit();

                    String dateStr = String.format(Locale.US, "%04d%02d01", year, month);
                    String urlStr = HISTORY_URL + "?response=json&date=" + dateStr + "&stockNo=" + code;
                    String response = httpGet(urlStr, 15000);

                    if (response != null && response.trim().startsWith("{")) {
                        backoffLevel = 0;
                        JSONObject json = new JSONObject(response);
                        JSONArray data = json.optJSONArray("data");
                        if (data != null) {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
                            for (int i = 0; i < data.length(); i++) {
                                JSONArray row = data.getJSONArray(i);
                                String dateField = row.getString(0);
                                String[] dateParts = dateField.split("/");
                                if (dateParts.length == 3) {
                                    int rocYear = Integer.parseInt(dateParts[0].trim());
                                    String fullDate = (rocYear + 1911) + "/" + dateParts[1] + "/" + dateParts[2];
                                    long timestamp = sdf.parse(fullDate).getTime();
                                    double open = parseDouble(row.getString(3).replace(",", ""));
                                    double high = parseDouble(row.getString(4).replace(",", ""));
                                    double low = parseDouble(row.getString(5).replace(",", ""));
                                    double close = parseDouble(row.getString(6).replace(",", ""));
                                    long volume = parseLong(row.getString(1).replace(",", ""));
                                    if (open > 0 && close > 0) {
                                        allCandles.add(new StockData.CandleBar(timestamp, open, high, low, close, volume));
                                    }
                                }
                            }
                        }
                    } else {
                        handleBlock();
                    }

                    // Respect rate limit between months
                    Thread.sleep(1500);
                }

                mainHandler.post(() -> callback.onResult(allCandles, null));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    private static void enforceRateLimit() throws InterruptedException {
        long now = System.currentTimeMillis();
        synchronized (requestTimes) {
            // Check if 3 requests within 5 seconds
            if (requestTimes[requestIndex] > 0 && now - requestTimes[requestIndex] < 5000) {
                long wait = 5000 - (now - requestTimes[requestIndex]);
                Thread.sleep(wait);
            }
            requestTimes[requestIndex] = System.currentTimeMillis();
            requestIndex = (requestIndex + 1) % requestTimes.length;
        }
    }

    private static void handleBlock() {
        lastBlockedTime = System.currentTimeMillis();
        if (backoffLevel < 3) backoffLevel++;
    }

    private static String httpGet(String urlStr, int timeoutMs) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            int code = conn.getResponseCode();
            if (code != 200) {
                conn.disconnect();
                return null;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            conn.disconnect();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static double parseDouble(String s) {
        if (s == null || s.equals("-") || s.isEmpty()) return 0;
        try { return Double.parseDouble(s.replace(",", "")); }
        catch (NumberFormatException e) { return 0; }
    }

    private static long parseLong(String s) {
        if (s == null || s.equals("-") || s.isEmpty()) return 0;
        try { return Long.parseLong(s.replace(",", "")); }
        catch (NumberFormatException e) { return 0; }
    }
}
