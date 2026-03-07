package com.mybot.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BridgeClient {

    private static final String BASE_URL = "http://127.0.0.1:8765";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Store last error for debugging
    private static String lastError = "";

    public static String getLastError() {
        return lastError;
    }

    public interface AnalyzeCallback {
        void onResult(NotificationLog log);
    }

    public interface CategorizeCallback {
        void onResult(String category, boolean offline);
    }

    public interface HealthCallback {
        void onResult(boolean online, String message);
    }

    public static void healthCheck(HealthCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(BASE_URL + "/health");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int code = conn.getResponseCode();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                }
                conn.disconnect();

                String body = sb.toString();
                mainHandler.post(() -> callback.onResult(code == 200, "HTTP " + code + ": " + body));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                mainHandler.post(() -> callback.onResult(false, err));
            }
        });
    }

    public static void categorize(String merchant, String description, double amount, CategorizeCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("task", "categorize_expense");
                body.put("merchant", merchant);
                body.put("description", description);
                body.put("amount", amount);

                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString());
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    JSONObject json = new JSONObject(response);
                    if (json.optBoolean("success", false)) {
                        JSONObject r = json.getJSONObject("result");
                        String category = r.optString("category", "");
                        mainHandler.post(() -> callback.onResult(category, false));
                        return;
                    }
                    // Bridge responded but success=false, not offline
                    mainHandler.post(() -> callback.onResult("", false));
                    return;
                }
                lastError = error;
                mainHandler.post(() -> callback.onResult("", true));
            } catch (Exception e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                mainHandler.post(() -> callback.onResult("", true));
            }
        });
    }

    public static void analyze(NotificationLog log, AnalyzeCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("task", "analyze_notification");
                body.put("source", log.source);
                body.put("app", log.sourceApp);
                body.put("title", log.title);
                body.put("content", log.content);

                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString());
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    JSONObject json = new JSONObject(response);
                    if (json.optBoolean("success", false)) {
                        JSONObject r = json.getJSONObject("result");
                        log.isExpense = r.optBoolean("is_expense", false);
                        log.amount = r.optDouble("amount", 0);
                        log.currency = r.optString("currency", "TWD");
                        log.category = r.optString("category", "");
                        log.merchant = r.optString("merchant", "");
                        log.description = r.optString("description", "");
                        log.confidence = r.optDouble("confidence", 0);
                    }
                    log.analyzed = true;
                    log.offline = false;
                } else {
                    log.analyzed = true;
                    log.offline = true;
                    log.errorMsg = error;
                }
            } catch (Exception e) {
                log.analyzed = true;
                log.offline = true;
                log.errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            mainHandler.post(() -> callback.onResult(log));
        });
    }

    public interface WorkoutCallback {
        void onResult(String responseJson, boolean offline, String error);
    }

    public static void generateWorkoutPlan(double height, double weight, String goal,
                                            String level, String feedback, WorkoutCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("task", "generate_workout_plan");
                body.put("height_cm", height);
                body.put("weight_kg", weight);
                body.put("bmi", Math.round(weight / Math.pow(height / 100.0, 2) * 10) / 10.0);
                body.put("goal", goal);
                body.put("level", level);
                if (feedback != null && !feedback.isEmpty()) {
                    body.put("feedback", feedback);
                }
                body.put("prompt", "請為這位用戶生成一週七天的居家無器材運動計畫。"
                        + "回傳 JSON 格式，包含 days 陣列，每天包含: "
                        + "day_of_week(1-7), day_label(週一~週日), focus(訓練重點), "
                        + "exercises 陣列(每個動作: name, sets, reps, rest_sec, duration_sec, tips, video_keyword)。"
                        + "video_keyword 是用來搜尋 YouTube 教學影片的關鍵字。"
                        + "每天安排4-6個動作，包含熱身和收操。根據用戶的目標和等級調整強度。");

                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 130000);
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    mainHandler.post(() -> callback.onResult(response, false, null));
                } else {
                    lastError = error;
                    mainHandler.post(() -> callback.onResult(null, true, error));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                mainHandler.post(() -> callback.onResult(null, true, err));
            }
        });
    }

    public interface ScreenshotCallback {
        void onResult(String responseJson, boolean offline, String error);
    }

    public static void analyzeScreenshot(String base64Image, ScreenshotCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("task", "analyze_expense_screenshot");
                body.put("image_base64", base64Image);
                body.put("prompt", "請分析這張螢幕截圖，判斷是否包含消費/付款/交易資訊。"
                        + "如果有，回傳: {\"is_expense\": true, \"amount\": 數字, \"currency\": \"TWD\", "
                        + "\"merchant\": \"商家名稱\", \"category\": \"類別\", \"description\": \"描述\"}。"
                        + "如果沒有消費資訊，回傳: {\"is_expense\": false}。"
                        + "類別請從以下選擇：餐飲、交通、購物、娛樂、醫療、教育、生活、其他。");

                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 60000);
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    mainHandler.post(() -> callback.onResult(response, false, null));
                } else {
                    lastError = error;
                    mainHandler.post(() -> callback.onResult(null, true, error));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                mainHandler.post(() -> callback.onResult(null, true, err));
            }
        });
    }

    public interface CalendarParseCallback {
        void onResult(String responseJson, boolean offline, String error);
    }

    public static void parseCalendarEvent(String userText, CalendarParseCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("task", "parse_calendar_event");
                body.put("text", userText);
                body.put("today", new java.text.SimpleDateFormat("yyyy-MM-dd (E)", java.util.Locale.TAIWAN)
                        .format(new java.util.Date()));
                body.put("prompt", "請解析以下文字，判斷使用者想要新增什麼日曆事件。"
                        + "回傳 JSON 格式：{\"events\": [{\"title\": \"...\", \"start_date\": \"YYYY-MM-DD\", "
                        + "\"start_time\": \"HH:mm\", \"end_date\": \"YYYY-MM-DD\", \"end_time\": \"HH:mm\", "
                        + "\"all_day\": false, \"description\": \"...\", \"location\": \"...\"}]}。"
                        + "如果是多天或多個事件，events 陣列就放多筆。"
                        + "如果是全天事件，all_day 設為 true，不需要 start_time/end_time。"
                        + "今天日期供參考，請推算正確的日期。");

                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 60000);
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    mainHandler.post(() -> callback.onResult(response, false, null));
                } else {
                    lastError = error;
                    mainHandler.post(() -> callback.onResult(null, true, error));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                mainHandler.post(() -> callback.onResult(null, true, err));
            }
        });
    }

    public interface StockAnalysisCallback {
        void onResult(String analysis, boolean offline, String error);
    }

    public static void analyzeStock(String stockInfo, StockAnalysisCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("task", "analyze_stock");
                body.put("stock_info", stockInfo);
                body.put("prompt", "你是一位專業的台股分析師。根據以下股票即時數據和技術指標，"
                        + "請提供簡潔的投資分析評語（約200-300字），包含：\n"
                        + "1. 當前股價表現評估\n"
                        + "2. 技術面分析（均線、RSI等指標解讀）\n"
                        + "3. 結合國際市場背景的趨勢判斷\n"
                        + "4. 未來短中期展望與成長性評估\n"
                        + "5. 投資建議（偏多/偏空/觀望）\n\n"
                        + "請直接回覆純文字分析，不要回傳 JSON。"
                        + "注意：這僅供參考，不構成投資建議。");

                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 60000);
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    // Extract analysis text from response with maximum flexibility
                    String analysis = extractAnalysis(response);
                    if (analysis != null && !analysis.isEmpty()) {
                        mainHandler.post(() -> callback.onResult(analysis, false, null));
                    } else {
                        // Show raw response for debugging
                        String raw = response.length() > 500 ? response.substring(0, 500) + "..." : response;
                        mainHandler.post(() -> callback.onResult(null, false, "無法解析回應:\n" + raw));
                    }
                    return;
                }
                lastError = error;
                mainHandler.post(() -> callback.onResult(null, true, error));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                mainHandler.post(() -> callback.onResult(null, true, err));
            }
        });
    }

    private static String extractAnalysis(String response) {
        try {
            JSONObject json = new JSONObject(response);
            if (!json.optBoolean("success", false)) return null;

            Object resultObj = json.opt("result");
            if (resultObj == null) return null;

            // Case 1: result is a plain string
            if (resultObj instanceof String) {
                return (String) resultObj;
            }

            // Case 2: result is a JSONObject — search all string fields
            if (resultObj instanceof JSONObject) {
                JSONObject r = (JSONObject) resultObj;
                // Try known field names first
                String[] keys = {"response", "text", "content", "answer", "message",
                        "analysis", "reply", "output", "data", "result"};
                for (String key : keys) {
                    String val = r.optString(key, "");
                    if (!val.isEmpty() && val.length() > 10) return val;
                }
                // Fallback: find the longest string value in the object
                String longest = "";
                java.util.Iterator<String> it = r.keys();
                while (it.hasNext()) {
                    String key = it.next();
                    Object val = r.opt(key);
                    if (val instanceof String && ((String) val).length() > longest.length()) {
                        longest = (String) val;
                    }
                }
                if (!longest.isEmpty()) return longest;
                return r.toString();
            }

            // Case 3: anything else
            return resultObj.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String[] postJsonWithError(String urlStr, String jsonBody) {
        return postJsonWithError(urlStr, jsonBody, 30000);
    }

    /**
     * Returns [responseBody, errorMessage]. responseBody is null on failure.
     */
    private static String[] postJsonWithError(String urlStr, String jsonBody, int readTimeoutMs) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(readTimeoutMs);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            StringBuilder sb = new StringBuilder();

            // Read response body (or error stream for non-200)
            BufferedReader br;
            if (code >= 200 && code < 300) {
                br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            } else if (conn.getErrorStream() != null) {
                br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
            } else {
                conn.disconnect();
                return new String[]{null, "HTTP " + code + " (no body)"};
            }

            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            conn.disconnect();

            if (code >= 200 && code < 300) {
                return new String[]{sb.toString(), null};
            } else {
                return new String[]{null, "HTTP " + code + ": " + sb.toString()};
            }
        } catch (Exception e) {
            return new String[]{null, e.getClass().getSimpleName() + ": " + e.getMessage()};
        }
    }
}
