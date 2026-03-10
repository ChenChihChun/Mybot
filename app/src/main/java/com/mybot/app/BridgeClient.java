package com.mybot.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
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
                AppLog.i("Bridge", "healthCheck: HTTP " + code);
                mainHandler.post(() -> callback.onResult(code == 200, "HTTP " + code + ": " + body));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                AppLog.e("Bridge", "healthCheck失敗: " + err);
                mainHandler.post(() -> callback.onResult(false, err));
            }
        });
    }

    public static void categorize(String merchant, String description, double amount, CategorizeCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "categorize: merchant=" + merchant + " amount=" + amount);
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
                        AppLog.i("Bridge", "categorize結果: " + category);
                        mainHandler.post(() -> callback.onResult(category, false));
                        return;
                    }
                    AppLog.w("Bridge", "categorize: success=false");
                    mainHandler.post(() -> callback.onResult("", false));
                    return;
                }
                lastError = error;
                AppLog.e("Bridge", "categorize失敗: " + error);
                mainHandler.post(() -> callback.onResult("", true));
            } catch (Exception e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                AppLog.e("Bridge", "categorize異常: " + lastError);
                mainHandler.post(() -> callback.onResult("", true));
            }
        });
    }

    public interface WorkoutCallback {
        void onResult(String responseJson, boolean offline, String error);
    }

    public static void generateWorkoutPlan(double height, double weight, String goal,
                                            String level, String feedback, WorkoutCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "generateWorkoutPlan: goal=" + goal + " level=" + level);
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
                    AppLog.i("Bridge", "workoutPlan生成成功");
                    mainHandler.post(() -> callback.onResult(response, false, null));
                } else {
                    lastError = error;
                    AppLog.e("Bridge", "workoutPlan失敗: " + error);
                    mainHandler.post(() -> callback.onResult(null, true, error));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                AppLog.e("Bridge", "workoutPlan異常: " + err);
                mainHandler.post(() -> callback.onResult(null, true, err));
            }
        });
    }

    public interface ScreenshotCallback {
        void onResult(String responseJson, boolean offline, String error);
    }

    public static void analyzeScreenshot(String base64Image, ScreenshotCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "analyzeScreenshot: imageSize=" + (base64Image != null ? base64Image.length() : 0));
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
                    AppLog.i("Bridge", "screenshot分析完成");
                    mainHandler.post(() -> callback.onResult(response, false, null));
                } else {
                    lastError = error;
                    AppLog.e("Bridge", "screenshot分析失敗: " + error);
                    mainHandler.post(() -> callback.onResult(null, true, error));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                AppLog.e("Bridge", "screenshot異常: " + err);
                mainHandler.post(() -> callback.onResult(null, true, err));
            }
        });
    }

    public interface InvoiceCallback {
        void onResult(String responseJson, boolean offline, String error);
    }

    public static void analyzeInvoice(String base64Image, InvoiceCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "analyzeInvoice: imageSize=" + (base64Image != null ? base64Image.length() : 0));
            try {
                JSONObject body = new JSONObject();
                body.put("task", "analyze_invoice");
                body.put("image_base64", base64Image);
                body.put("prompt", "\u8ACB\u5206\u6790\u9019\u5F35\u767C\u7968/\u6536\u64DA\u5716\u7247\u3002"
                        + "\u5982\u679C\u662F\u767C\u7968\u6216\u6D88\u8CBB\u6536\u64DA\uFF0C\u8ACB\u56DE\u50B3 JSON\uFF1A"
                        + "{\"is_invoice\": true, \"merchant\": \"\u5546\u5BB6\u540D\u7A31\", "
                        + "\"date\": \"YYYY-MM-DD\", \"items\": \"\u54C1\u9805\u660E\u7D30\", "
                        + "\"total\": \u6578\u5B57, \"currency\": \"TWD\", "
                        + "\"payment_method\": \"\u4ED8\u6B3E\u65B9\u5F0F\", "
                        + "\"invoice_number\": \"\u767C\u7968\u865F\u78BC\", "
                        + "\"category\": \"\u985E\u5225\"}\u3002"
                        + "\u5982\u679C\u4E0D\u662F\u767C\u7968\uFF1A{\"is_invoice\": false}\u3002"
                        + "\u985E\u5225\u8ACB\u5F9E\u4EE5\u4E0B\u9078\u64C7\uFF1A\u9910\u98F2\u3001\u4EA4\u901A\u3001\u8CFC\u7269\u3001\u5A1B\u6A02\u3001\u91AB\u7642\u3001\u6559\u80B2\u3001\u751F\u6D3B\u3001\u5176\u4ED6\u3002");

                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 60000);
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    AppLog.i("Bridge", "invoice\u5206\u6790\u5B8C\u6210");
                    mainHandler.post(() -> callback.onResult(response, false, null));
                } else {
                    lastError = error;
                    AppLog.e("Bridge", "invoice\u5206\u6790\u5931\u6557: " + error);
                    mainHandler.post(() -> callback.onResult(null, true, error));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                AppLog.e("Bridge", "invoice\u7570\u5E38: " + err);
                mainHandler.post(() -> callback.onResult(null, true, err));
            }
        });
    }

    public interface CalendarParseCallback {
        void onResult(String responseJson, boolean offline, String error);
    }

    public static void parseCalendarEvent(String userText, CalendarParseCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "parseCalendarEvent: " + (userText.length() > 50 ? userText.substring(0, 50) + "..." : userText));
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
                    AppLog.i("Bridge", "calendarEvent解析完成");
                    mainHandler.post(() -> callback.onResult(response, false, null));
                } else {
                    lastError = error;
                    AppLog.e("Bridge", "calendarEvent失敗: " + error);
                    mainHandler.post(() -> callback.onResult(null, true, error));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                AppLog.e("Bridge", "calendarEvent異常: " + err);
                mainHandler.post(() -> callback.onResult(null, true, err));
            }
        });
    }

    public interface StockAnalysisCallback {
        void onResult(String analysis, boolean offline, String error);
    }

    public static void analyzeStock(String stockInfo, StockAnalysisCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "analyzeStock: infoLen=" + stockInfo.length());
            try {
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd (E)", java.util.Locale.TAIWAN)
                        .format(new java.util.Date());

                JSONObject body = new JSONObject();
                body.put("task", "analyze_stock");
                body.put("prompt", "你是一位專業的台股分析師。今天是 " + today + "。\n\n"
                        + "【即時數據與技術指標】\n"
                        + stockInfo + "\n\n"
                        + "【任務】\n"
                        + "請先用 WebSearch 搜尋該公司最近一週的重大新聞、法說會、營收公告、外資買賣超等最新動態。\n"
                        + "搜尋關鍵字建議：公司名稱+近期新聞、產業趨勢等。\n\n"
                        + "然後根據即時技術數據 + 搜尋到的最新資訊，提供投資分析（約300-500字）：\n"
                        + "1. 技術面分析：根據均線、布林通道、RSI 指標解讀多空訊號、支撐壓力位\n"
                        + "2. 近期消息面：根據搜尋結果整理重大新聞、營收、法人動態（附上資訊來源）\n"
                        + "3. 國際局勢：搜尋相關國際趨勢對該股的影響\n"
                        + "4. 若有持倉資訊，分析目前損益狀況，建議加碼/減碼/持有的操作策略\n"
                        + "5. 短中期展望與操作建議（偏多/偏空/觀望），給出建議的支撐與壓力價位\n\n"
                        + "直接回覆純文字分析，不要回傳 JSON。"
                        + "注意：這僅供參考，不構成投資建議。");

                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 130000);
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    AppLog.i("Bridge", "stockAnalysis回應: " + (response.length() > 200 ? response.substring(0, 200) + "..." : response));
                    String analysis = extractAnalysis(response);
                    if (analysis != null && !analysis.isEmpty()) {
                        AppLog.i("Bridge", "stockAnalysis成功: " + analysis.length() + "字");
                        mainHandler.post(() -> callback.onResult(analysis, false, null));
                    } else {
                        String raw = response.length() > 500 ? response.substring(0, 500) + "..." : response;
                        AppLog.w("Bridge", "stockAnalysis格式異常: " + raw);
                        mainHandler.post(() -> callback.onResult(null, false, "回應格式異常:\n" + raw));
                    }
                    return;
                }
                lastError = error;
                AppLog.e("Bridge", "stockAnalysis失敗: " + error);
                mainHandler.post(() -> callback.onResult(null, true, error));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                AppLog.e("Bridge", "stockAnalysis異常: " + err);
                mainHandler.post(() -> callback.onResult(null, true, err));
            }
        });
    }

    private static String extractAnalysis(String response) {
        try {
            JSONObject json = new JSONObject(response);

            // Check for tool_use failure (Bridge returns stop_reason without success flag)
            Object resultObj = json.opt("result");
            if (resultObj instanceof JSONObject) {
                JSONObject r = (JSONObject) resultObj;
                String stopReason = r.optString("stop_reason", "");
                if ("tool_use".equals(stopReason) || "error_max_turns".equals(r.optString("subtype", ""))) {
                    AppLog.w("Bridge", "AI回應為 tool_use/error_max_turns，未產出分析文字");
                    return null;
                }
            }

            if (!json.optBoolean("success", false)) {
                // Some Bridge responses might not have success flag but still have text
                if (resultObj != null) {
                    String text = extractText(resultObj);
                    if (text != null && text.length() > 50) return text;
                }
                return null;
            }

            if (resultObj == null) return null;

            return extractText(resultObj);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractText(Object obj) {
        if (obj == null) return null;

        // Plain string
        if (obj instanceof String) {
            String s = (String) obj;
            return s.isEmpty() ? null : s;
        }

        // JSONArray — find longest string element or recurse
        if (obj instanceof JSONArray) {
            JSONArray arr = (JSONArray) obj;
            String longest = "";
            for (int i = 0; i < arr.length(); i++) {
                String val = extractText(arr.opt(i));
                if (val != null && val.length() > longest.length()) longest = val;
            }
            return longest.isEmpty() ? null : longest;
        }

        // JSONObject — search known keys, then all values
        if (obj instanceof JSONObject) {
            JSONObject r = (JSONObject) obj;

            // Try known field names (shallow string first)
            String[] keys = {"response", "text", "content", "answer", "message",
                    "analysis", "reply", "output", "data", "result", "choices"};
            for (String key : keys) {
                Object val = r.opt(key);
                if (val == null) continue;
                String extracted = extractText(val);
                if (extracted != null && extracted.length() > 20) return extracted;
            }

            // Fallback: find the longest string in any field (recursive)
            String longest = "";
            java.util.Iterator<String> it = r.keys();
            while (it.hasNext()) {
                String key = it.next();
                String val = extractText(r.opt(key));
                if (val != null && val.length() > longest.length()) longest = val;
            }
            return longest.isEmpty() ? null : longest;
        }

        // Number, Boolean, etc
        return null;
    }

    public interface VideoSummaryCallback {
        void onResult(JSONObject summary, String error);
    }

    public static void summarizeVideo(String url, VideoSummaryCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "summarizeVideo: " + url);
            try {
                JSONObject body = new JSONObject();
                body.put("task", "summarize_video");
                body.put("url", url);

                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 200000);
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    JSONObject json = new JSONObject(response);
                    if (json.optBoolean("success", false)) {
                        Object resultObj = json.opt("result");
                        if (resultObj instanceof JSONObject) {
                            JSONObject summary = (JSONObject) resultObj;
                            AppLog.i("Bridge", "videoSummary成功: " + summary.optString("title", ""));
                            mainHandler.post(() -> callback.onResult(summary, null));
                            return;
                        }
                        // result might be text that contains JSON
                        String text = extractText(resultObj);
                        if (text != null) {
                            // Try to parse JSON from text
                            int start = text.indexOf("{");
                            int end = text.lastIndexOf("}");
                            if (start >= 0 && end > start) {
                                try {
                                    JSONObject summary = new JSONObject(text.substring(start, end + 1));
                                    AppLog.i("Bridge", "videoSummary成功(parsed): " + summary.optString("title", ""));
                                    mainHandler.post(() -> callback.onResult(summary, null));
                                    return;
                                } catch (Exception ignored) {}
                            }
                            // Fallback: wrap plain text as summary
                            JSONObject fallback = new JSONObject();
                            fallback.put("title", "");
                            fallback.put("summary", text);
                            fallback.put("key_points", new JSONArray());
                            mainHandler.post(() -> callback.onResult(fallback, null));
                            return;
                        }
                    }
                    String errMsg = json.optString("error", "unknown error");
                    AppLog.w("Bridge", "videoSummary失敗: " + errMsg);
                    mainHandler.post(() -> callback.onResult(null, errMsg));
                    return;
                }
                lastError = error;
                AppLog.e("Bridge", "videoSummary連線失敗: " + error);
                mainHandler.post(() -> callback.onResult(null, error));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                AppLog.e("Bridge", "videoSummary異常: " + err);
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    public interface KnowledgeCategoryCallback {
        void onResult(String category, String error);
    }

    public static void categorizeKnowledge(String title, String summary, KnowledgeCategoryCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "categorizeKnowledge: " + (title.length() > 50 ? title.substring(0, 50) + "..." : title));
            try {
                JSONObject body = new JSONObject();
                body.put("task", "categorize_knowledge");
                body.put("title", title);
                body.put("summary", summary);
                body.put("prompt", "根據以下內容的標題和摘要，判斷它屬於哪個知識類別。\n"
                        + "標題：" + title + "\n"
                        + "摘要：" + summary + "\n\n"
                        + "類別請從以下選擇一個最適合的：科技、投資、財經、健康、醫療、教育、學習、"
                        + "娛樂、商業、創業、生活、心理、心靈、其他\n\n"
                        + "請以 JSON 格式回傳：{\"category\": \"類別名稱\"}");

                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 30000);
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    JSONObject json = new JSONObject(response);
                    if (json.optBoolean("success", false)) {
                        Object resultObj = json.opt("result");
                        String category = "";
                        if (resultObj instanceof JSONObject) {
                            category = ((JSONObject) resultObj).optString("category", "其他");
                        } else if (resultObj instanceof String) {
                            // Try to parse JSON from text
                            String text = (String) resultObj;
                            int start = text.indexOf("{");
                            int end = text.lastIndexOf("}");
                            if (start >= 0 && end > start) {
                                try {
                                    JSONObject parsed = new JSONObject(text.substring(start, end + 1));
                                    category = parsed.optString("category", "其他");
                                } catch (Exception ignored) {
                                    category = "其他";
                                }
                            } else {
                                category = "其他";
                            }
                        } else {
                            category = "其他";
                        }
                        AppLog.i("Bridge", "categorizeKnowledge結果: " + category);
                        final String cat = category;
                        mainHandler.post(() -> callback.onResult(cat, null));
                        return;
                    }
                    String errMsg = json.optString("error", "unknown error");
                    AppLog.w("Bridge", "categorizeKnowledge失敗: " + errMsg);
                    mainHandler.post(() -> callback.onResult("其他", null));
                    return;
                }
                lastError = error;
                AppLog.e("Bridge", "categorizeKnowledge連線失敗: " + error);
                mainHandler.post(() -> callback.onResult("其他", error));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                AppLog.e("Bridge", "categorizeKnowledge異常: " + err);
                mainHandler.post(() -> callback.onResult("其他", err));
            }
        });
    }

    public interface RemoteCodeCallback {
        void onResult(String result, boolean offline, String error);
    }

    public static void remoteCode(String task, String project, RemoteCodeCallback callback) {
        executor.execute(() -> {
            AppLog.i("RemoteDev", "remoteCode: task=" + (task.length() > 80 ? task.substring(0, 80) + "..." : task)
                    + " project=" + project);
            try {
                JSONObject body = new JSONObject();
                body.put("task", task);
                if (project != null && !project.isEmpty()) {
                    body.put("project", project);
                }
                body.put("timeout", 600);

                String[] result = postJsonWithError(BASE_URL + "/remote-code", body.toString(), 620000);
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    JSONObject json = new JSONObject(response);
                    if (json.optBoolean("success", false)) {
                        String text = json.optString("result", "");
                        AppLog.i("RemoteDev", "remoteCode成功: " + text.length() + "字");
                        mainHandler.post(() -> callback.onResult(text, false, null));
                    } else {
                        String err = json.optString("error", "unknown error");
                        AppLog.e("RemoteDev", "remoteCode失敗: " + err);
                        mainHandler.post(() -> callback.onResult(null, false, err));
                    }
                } else {
                    lastError = error;
                    AppLog.e("RemoteDev", "remoteCode連線失敗: " + error);
                    mainHandler.post(() -> callback.onResult(null, true, error));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                AppLog.e("RemoteDev", "remoteCode異常: " + err);
                mainHandler.post(() -> callback.onResult(null, true, err));
            }
        });
    }

    public interface FlightSearchCallback {
        void onResult(String responseJson, boolean offline, String error);
    }

    public static void searchFlights(String origin, String destination,
                                      String departureDate, String returnDate,
                                      String searchMode, FlightSearchCallback callback) {
        executor.execute(() -> {
            AppLog.i("Flight", "searchFlights: " + origin + "→" + destination
                    + " date=" + departureDate + " mode=" + searchMode);
            try {
                JSONObject body = new JSONObject();
                body.put("task", "search_flights");
                body.put("origin", origin);
                body.put("destination", destination);
                body.put("departure_date", departureDate);
                if (returnDate != null && !returnDate.isEmpty()) {
                    body.put("return_date", returnDate);
                }
                body.put("search_mode", searchMode);

                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 130000);
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    AppLog.i("Flight", "searchFlights完成");
                    mainHandler.post(() -> callback.onResult(response, false, null));
                } else {
                    lastError = error;
                    AppLog.e("Flight", "searchFlights失敗: " + error);
                    mainHandler.post(() -> callback.onResult(null, true, error));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                AppLog.e("Flight", "searchFlights異常: " + err);
                mainHandler.post(() -> callback.onResult(null, true, err));
            }
        });
    }

    /**
     * Synchronous flight search for use in BroadcastReceiver (runs on background thread).
     * Returns raw JSON response string or null on failure.
     */
    public static String searchFlightsSync(String origin, String destination,
                                            String departureDate, String returnDate,
                                            String searchMode) {
        try {
            JSONObject body = new JSONObject();
            body.put("task", "search_flights");
            body.put("origin", origin);
            body.put("destination", destination);
            body.put("departure_date", departureDate);
            if (returnDate != null && !returnDate.isEmpty()) {
                body.put("return_date", returnDate);
            }
            body.put("search_mode", searchMode);

            String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 130000);
            return result[0];
        } catch (Exception e) {
            AppLog.e("Flight", "searchFlightsSync異常: " + e.getMessage());
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
                AppLog.i("Bridge", "POST " + urlStr + " → " + code + " (" + sb.length() + " bytes)");
                return new String[]{sb.toString(), null};
            } else {
                String errBody = sb.toString();
                AppLog.w("Bridge", "POST " + urlStr + " → HTTP " + code + ": " + (errBody.length() > 200 ? errBody.substring(0, 200) : errBody));
                return new String[]{null, "HTTP " + code + ": " + errBody};
            }
        } catch (Exception e) {
            String err = e.getClass().getSimpleName() + ": " + e.getMessage();
            AppLog.e("Bridge", "POST " + urlStr + " 連線失敗: " + err);
            return new String[]{null, err};
        }
    }
}
