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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StockClient {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Yahoo Finance API
    private static final String YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";

    // Cache suffix per code: ".TW" (上市) or ".TWO" (上櫃)
    private static final java.util.Map<String, String> suffixMap = new java.util.concurrent.ConcurrentHashMap<>();

    // Backoff state
    private static int backoffLevel = 0;

    public static int getBackoffLevel() { return backoffLevel; }

    public interface StockCallback {
        void onResult(List<StockData.StockQuote> quotes, String error);
    }

    public interface HistoryCallback {
        void onResult(List<StockData.CandleBar> candles, String error);
    }

    /**
     * Resolve Yahoo symbol suffix for a Taiwan stock code.
     * Tries .TW first, falls back to .TWO (OTC).
     */
    private static String resolveSymbol(String code) {
        String cached = suffixMap.get(code);
        if (cached != null) return code + cached;

        // Try .TW first (上市)
        String url = YAHOO_CHART_URL + code + ".TW?interval=1d&range=1d";
        String resp = httpGet(url, 10000);
        if (resp != null) {
            try {
                JSONObject json = new JSONObject(resp);
                JSONObject chart = json.optJSONObject("chart");
                if (chart != null) {
                    JSONArray results = chart.optJSONArray("result");
                    if (results != null && results.length() > 0) {
                        suffixMap.put(code, ".TW");
                        AppLog.i("Stock", code + " → .TW (上市)");
                        return code + ".TW";
                    }
                }
            } catch (Exception ignored) {}
        }

        // Try .TWO (上櫃)
        url = YAHOO_CHART_URL + code + ".TWO?interval=1d&range=1d";
        resp = httpGet(url, 10000);
        if (resp != null) {
            try {
                JSONObject json = new JSONObject(resp);
                JSONObject chart = json.optJSONObject("chart");
                if (chart != null) {
                    JSONArray results = chart.optJSONArray("result");
                    if (results != null && results.length() > 0) {
                        suffixMap.put(code, ".TWO");
                        AppLog.i("Stock", code + " → .TWO (上櫃)");
                        return code + ".TWO";
                    }
                }
            } catch (Exception ignored) {}
        }

        // Default to .TW
        AppLog.w("Stock", code + " 無法辨識市場，預設 .TW");
        suffixMap.put(code, ".TW");
        return code + ".TW";
    }

    public static void fetchStocks(List<String> codes, StockCallback callback) {
        executor.execute(() -> {
            try {
                List<StockData.StockQuote> quotes = new ArrayList<>();

                for (String code : codes) {
                    String symbol = resolveSymbol(code);
                    String url = YAHOO_CHART_URL + symbol
                            + "?interval=1m&range=1d&includePrePost=false";
                    String response = httpGet(url, 10000);

                    if (response == null || response.trim().isEmpty()) {
                        AppLog.w("Stock", "fetchStocks " + symbol + ": empty response");
                        continue;
                    }

                    try {
                        JSONObject json = new JSONObject(response);
                        JSONObject chart = json.optJSONObject("chart");
                        if (chart == null) continue;

                        JSONArray results = chart.optJSONArray("result");
                        if (results == null || results.length() == 0) continue;

                        JSONObject result = results.getJSONObject(0);
                        JSONObject meta = result.optJSONObject("meta");
                        if (meta == null) continue;

                        StockData.StockQuote q = new StockData.StockQuote();
                        q.code = code;
                        q.name = meta.optString("shortName", meta.optString("symbol", code));
                        // Clean up name: Yahoo returns "1717.TW" style, try longName
                        String longName = meta.optString("longName", "");
                        if (!longName.isEmpty()) q.name = longName;

                        q.currentPrice = meta.optDouble("regularMarketPrice", 0);
                        q.prevClose = meta.optDouble("previousClose", meta.optDouble("chartPreviousClose", 0));
                        q.volume = meta.optLong("regularMarketVolume", 0);

                        // Get OHLC from today's candle data
                        JSONObject indicators = result.optJSONObject("indicators");
                        if (indicators != null) {
                            JSONArray quoteArr = indicators.optJSONArray("quote");
                            if (quoteArr != null && quoteArr.length() > 0) {
                                JSONObject quoteData = quoteArr.getJSONObject(0);
                                JSONArray opens = quoteData.optJSONArray("open");
                                JSONArray highs = quoteData.optJSONArray("high");
                                JSONArray lows = quoteData.optJSONArray("low");

                                if (opens != null && opens.length() > 0) {
                                    // First non-null open is today's open
                                    for (int i = 0; i < opens.length(); i++) {
                                        if (!opens.isNull(i)) {
                                            q.open = opens.getDouble(i);
                                            break;
                                        }
                                    }
                                }

                                // Find high/low across all minute bars
                                double dayHigh = 0, dayLow = Double.MAX_VALUE;
                                if (highs != null) {
                                    for (int i = 0; i < highs.length(); i++) {
                                        if (!highs.isNull(i)) {
                                            double h = highs.getDouble(i);
                                            if (h > dayHigh) dayHigh = h;
                                        }
                                    }
                                }
                                if (lows != null) {
                                    for (int i = 0; i < lows.length(); i++) {
                                        if (!lows.isNull(i)) {
                                            double l = lows.getDouble(i);
                                            if (l < dayLow) dayLow = l;
                                        }
                                    }
                                }
                                if (dayHigh > 0) q.high = dayHigh;
                                if (dayLow < Double.MAX_VALUE) q.low = dayLow;
                            }
                        }

                        // Fallback: if currentPrice is 0, use prevClose
                        if (q.currentPrice <= 0 && q.prevClose > 0) {
                            q.currentPrice = q.prevClose;
                        }

                        quotes.add(q);
                    } catch (Exception e) {
                        AppLog.w("Stock", "Parse " + symbol + " failed: " + e.getMessage());
                    }
                }

                backoffLevel = 0;
                AppLog.i("Stock", "fetchStocks(Yahoo): " + quotes.size() + "檔報價取得成功");
                mainHandler.post(() -> callback.onResult(quotes.isEmpty() ? null : quotes, quotes.isEmpty() ? "No data" : null));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                AppLog.e("Stock", "fetchStocks失敗: " + err);
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    public static void fetchMultiMonthHistory(String code, int months, HistoryCallback callback) {
        executor.execute(() -> {
            try {
                String symbol = resolveSymbol(code);
                String range = months <= 6 ? "6mo" : "1y";
                String url = YAHOO_CHART_URL + symbol
                        + "?interval=1d&range=" + range;
                String response = httpGet(url, 15000);

                if (response == null || response.trim().isEmpty()) {
                    AppLog.w("Stock", "fetchHistory " + symbol + ": empty response");
                    mainHandler.post(() -> callback.onResult(null, "Empty response"));
                    return;
                }

                JSONObject json = new JSONObject(response);
                JSONObject chart = json.optJSONObject("chart");
                if (chart == null) {
                    mainHandler.post(() -> callback.onResult(null, "No chart data"));
                    return;
                }

                JSONArray results = chart.optJSONArray("result");
                if (results == null || results.length() == 0) {
                    mainHandler.post(() -> callback.onResult(null, "No results"));
                    return;
                }

                JSONObject result = results.getJSONObject(0);
                JSONArray timestamps = result.optJSONArray("timestamp");
                JSONObject indicators = result.optJSONObject("indicators");

                if (timestamps == null || indicators == null) {
                    mainHandler.post(() -> callback.onResult(new ArrayList<>(), null));
                    return;
                }

                JSONArray quoteArr = indicators.optJSONArray("quote");
                if (quoteArr == null || quoteArr.length() == 0) {
                    mainHandler.post(() -> callback.onResult(new ArrayList<>(), null));
                    return;
                }

                JSONObject quoteData = quoteArr.getJSONObject(0);
                JSONArray opens = quoteData.optJSONArray("open");
                JSONArray highs = quoteData.optJSONArray("high");
                JSONArray lows = quoteData.optJSONArray("low");
                JSONArray closes = quoteData.optJSONArray("close");
                JSONArray volumes = quoteData.optJSONArray("volume");

                List<StockData.CandleBar> candles = new ArrayList<>();
                for (int i = 0; i < timestamps.length(); i++) {
                    if (opens.isNull(i) || closes.isNull(i)) continue;

                    long ts = timestamps.getLong(i) * 1000; // Unix seconds → ms
                    double open = opens.getDouble(i);
                    double high = highs.isNull(i) ? open : highs.getDouble(i);
                    double low = lows.isNull(i) ? open : lows.getDouble(i);
                    double close = closes.getDouble(i);
                    long vol = volumes.isNull(i) ? 0 : volumes.getLong(i);

                    if (open > 0 && close > 0) {
                        candles.add(new StockData.CandleBar(ts, open, high, low, close, vol));
                    }
                }

                backoffLevel = 0;
                AppLog.i("Stock", "fetchHistory(Yahoo) " + symbol + " " + range + ": " + candles.size() + "根K棒");
                mainHandler.post(() -> callback.onResult(candles, null));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                AppLog.e("Stock", "fetchHistory失敗: " + err);
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    // Keep for backward compatibility
    public static void fetchHistory(String code, int year, int month, HistoryCallback callback) {
        fetchMultiMonthHistory(code, 6, callback);
    }

    private static String httpGet(String urlStr, int timeoutMs) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            conn.setUseCaches(false);

            int code = conn.getResponseCode();
            if (code != 200) {
                AppLog.w("Stock", "HTTP " + code + " for " + urlStr.substring(0, Math.min(100, urlStr.length())));
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
            AppLog.e("Stock", "httpGet失敗: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }
}
