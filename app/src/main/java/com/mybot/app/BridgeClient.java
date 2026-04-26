package com.mybot.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
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

    public static void categorize(String merchant, String description, double amount,
                                   List<String> existingCategories, CategorizeCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "categorize: merchant=" + merchant + " amount=" + amount);
            try {
                JSONObject body = new JSONObject();
                body.put("task", "categorize_expense");
                body.put("merchant", merchant);
                body.put("description", description);
                body.put("amount", amount);
                if (existingCategories != null && !existingCategories.isEmpty()) {
                    body.put("existing_categories", new org.json.JSONArray(existingCategories));
                }

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

    public interface LyricsCallback {
        void onResult(JSONObject lyrics, String error);
    }

    public static void generateLyrics(String emotion, String theme, String style, LyricsCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "generateLyrics: " + emotion + "/" + theme + "/" + style);
            try {
                JSONObject body = new JSONObject();
                body.put("task", "generate_lyrics");
                body.put("emotion", emotion);
                body.put("theme", theme);
                body.put("style", style);
                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 70000);
                String response = result[0];
                String error = result[1];
                if (response != null) {
                    JSONObject json = new JSONObject(response);
                    if (json.optBoolean("success", false)) {
                        JSONObject r = json.getJSONObject("result");
                        AppLog.i("Bridge", "generateLyrics成功: " + r.optString("title"));
                        mainHandler.post(() -> callback.onResult(r, null));
                        return;
                    }
                    AppLog.w("Bridge", "generateLyrics: success=false");
                    mainHandler.post(() -> callback.onResult(null, "生成失敗"));
                    return;
                }
                lastError = error;
                AppLog.e("Bridge", "generateLyrics失敗: " + error);
                mainHandler.post(() -> callback.onResult(null, error));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                AppLog.e("Bridge", "generateLyrics異常: " + err);
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    public interface WorkoutCallback {
        void onResult(String responseJson, boolean offline, String error);
    }

    public static void generateWorkoutPlan(double height, double weight, String goal,
                                            String customGoal, String level, String feedback, WorkoutCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "generateWorkoutPlan: goal=" + goal + " customGoal=" + customGoal + " level=" + level);
            try {
                JSONObject body = new JSONObject();
                body.put("task", "generate_workout_plan");
                body.put("height_cm", height);
                body.put("weight_kg", weight);
                body.put("bmi", Math.round(weight / Math.pow(height / 100.0, 2) * 10) / 10.0);
                body.put("goal", goal);
                if (customGoal != null && !customGoal.isEmpty()) {
                    body.put("custom_goal", customGoal);
                }
                body.put("level", level);
                if (feedback != null && !feedback.isEmpty()) {
                    body.put("feedback", feedback);
                }
                String customGoalPrompt = (customGoal != null && !customGoal.isEmpty())
                        ? "用戶的具體目標描述: 「" + customGoal + "」。請特別針對此描述調整訓練方向，並在計畫中說明如何幫助達成此目標。"
                        : "";
                body.put("prompt", "請為這位用戶生成一週七天的居家無器材運動計畫。"
                        + customGoalPrompt
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

    public static void analyzeScreenshot(String base64Image, List<String> existingCategories, ScreenshotCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "analyzeScreenshot: imageSize=" + (base64Image != null ? base64Image.length() : 0));
            try {
                JSONObject body = new JSONObject();
                body.put("task", "analyze_expense_screenshot");
                body.put("image_base64", base64Image);
                String catInstruction;
                if (existingCategories != null && !existingCategories.isEmpty()) {
                    catInstruction = "類別請優先從以下已有類別選擇：" + String.join("、", existingCategories)
                            + "。只有當已有類別都無法合理涵蓋時，才可以新增一個新類別。";
                } else {
                    catInstruction = "類別請從以下選擇：餐飲、交通、購物、娛樂、醫療、教育、生活、其他。";
                }
                body.put("prompt", "請分析這張螢幕截圖，判斷是否包含消費/付款/交易資訊。"
                        + "如果有，回傳: {\"is_expense\": true, \"amount\": 數字, \"currency\": \"TWD\", "
                        + "\"merchant\": \"商家名稱\", \"category\": \"類別\", \"description\": \"描述\"}。"
                        + "如果沒有消費資訊，回傳: {\"is_expense\": false}。"
                        + catInstruction);

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

    public static void analyzeInvoice(String base64Image, List<String> existingCategories, InvoiceCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "analyzeInvoice: imageSize=" + (base64Image != null ? base64Image.length() : 0));
            try {
                JSONObject body = new JSONObject();
                body.put("task", "analyze_invoice");
                body.put("image_base64", base64Image);
                String catInstruction;
                if (existingCategories != null && !existingCategories.isEmpty()) {
                    catInstruction = "類別請優先從以下已有類別選擇：" + String.join("、", existingCategories)
                            + "。只有當已有類別都無法合理涵蓋時，才可以新增一個新類別。";
                } else {
                    catInstruction = "類別請從以下選擇：餐飲、交通、購物、娛樂、醫療、教育、生活、其他。";
                }
                body.put("prompt", "請分析這張發票/收據圖片。"
                        + "如果是發票或消費收據，請回傳 JSON："
                        + "{\"is_invoice\": true, \"merchant\": \"商家名稱\", "
                        + "\"date\": \"YYYY-MM-DD\", \"items\": \"品項明細\", "
                        + "\"total\": 數字, \"currency\": \"TWD\", "
                        + "\"payment_method\": \"付款方式\", "
                        + "\"invoice_number\": \"發票號碼\", "
                        + "\"category\": \"類別\"}。"
                        + "如果不是發票：{\"is_invoice\": false}。"
                        + catInstruction);

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

    public interface DreamCallback {
        void onResult(String symbol, String interpretation, String mood, String error);
    }

    public static void analyzeDream(String dream, DreamCallback callback) {
        executor.execute(() -> {
            AppLog.i("Dream", "analyzeDream: " + (dream.length() > 50 ? dream.substring(0, 50) + "..." : dream));
            try {
                JSONObject body = new JSONObject();
                body.put("task", "analyze_dream");
                body.put("dream", dream);

                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 60000);
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    JSONObject json = new JSONObject(response);
                    if (json.optBoolean("success", false)) {
                        Object resultObj = json.opt("result");
                        JSONObject parsed = null;
                        if (resultObj instanceof JSONObject) {
                            parsed = (JSONObject) resultObj;
                        } else if (resultObj instanceof String) {
                            String text = (String) resultObj;
                            int start = text.indexOf("{");
                            int end = text.lastIndexOf("}");
                            if (start >= 0 && end > start) {
                                try {
                                    parsed = new JSONObject(text.substring(start, end + 1));
                                } catch (Exception ignored) {}
                            }
                        }
                        if (parsed != null) {
                            String symbol = parsed.optString("symbol", "");
                            String interpretation = parsed.optString("interpretation", "");
                            String mood = parsed.optString("mood", "");
                            AppLog.i("Dream", "解析成功: " + symbol);
                            mainHandler.post(() -> callback.onResult(symbol, interpretation, mood, null));
                            return;
                        }
                    }
                    String errMsg = json.optString("error", "解析失敗");
                    AppLog.w("Dream", "失敗: " + errMsg);
                    mainHandler.post(() -> callback.onResult(null, null, null, errMsg));
                    return;
                }
                AppLog.e("Dream", "連線失敗: " + error);
                mainHandler.post(() -> callback.onResult(null, null, null, error));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                AppLog.e("Dream", "異常: " + err);
                mainHandler.post(() -> callback.onResult(null, null, null, err));
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
                                      String searchMode, boolean roundTrip,
                                      String preferredAirlines, boolean directOnly,
                                      FlightSearchCallback callback) {
        executor.execute(() -> {
            AppLog.i("Flight", "searchFlights: " + origin + "→" + destination
                    + " date=" + departureDate + " mode=" + searchMode
                    + " roundTrip=" + roundTrip);
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
                body.put("round_trip", roundTrip);
                if (preferredAirlines != null && !preferredAirlines.isEmpty()) {
                    body.put("preferred_airlines", preferredAirlines);
                }
                body.put("direct_only", directOnly);

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
                                            String searchMode, boolean roundTrip,
                                            String preferredAirlines, boolean directOnly) {
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
            body.put("round_trip", roundTrip);
            if (preferredAirlines != null && !preferredAirlines.isEmpty()) {
                body.put("preferred_airlines", preferredAirlines);
            }
            body.put("direct_only", directOnly);

            String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 130000);
            return result[0];
        } catch (Exception e) {
            AppLog.e("Flight", "searchFlightsSync異常: " + e.getMessage());
            return null;
        }
    }

    // ── Travel Planning ──

    public interface TravelCallback {
        void onResult(String responseJson, boolean offline, String error);
    }

    public static void generateItinerary(String destination, int days, int people,
                                          String preferencesJson, String transportMode,
                                          String startDate, double budget,
                                          String accommodationType, TravelCallback callback) {
        executor.execute(() -> {
            AppLog.i("Travel", "generateItinerary: dest=" + destination + " days=" + days
                    + " people=" + people + " transport=" + transportMode);
            try {
                JSONObject body = new JSONObject();
                body.put("task", "generate_itinerary");
                body.put("destination", destination);
                body.put("days", days);
                body.put("people", people);
                body.put("preferences", preferencesJson);
                body.put("transport_mode", transportMode);
                body.put("start_date", startDate);
                body.put("budget", budget);
                body.put("accommodation_type", accommodationType);

                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 630000);
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    AppLog.i("Travel", "行程生成成功");
                    mainHandler.post(() -> callback.onResult(response, false, null));
                } else {
                    lastError = error;
                    AppLog.e("Travel", "行程生成失敗: " + error);
                    mainHandler.post(() -> callback.onResult(null, true, error));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                AppLog.e("Travel", "行程生成異常: " + err);
                mainHandler.post(() -> callback.onResult(null, true, err));
            }
        });
    }

    public static void refineItinerary(String currentItineraryJson, String instruction,
                                        TravelCallback callback) {
        executor.execute(() -> {
            AppLog.i("Travel", "refineItinerary: instruction=" + (instruction.length() > 50
                    ? instruction.substring(0, 50) + "..." : instruction));
            try {
                JSONObject body = new JSONObject();
                body.put("task", "refine_itinerary");
                body.put("current_itinerary", currentItineraryJson);
                body.put("instruction", instruction);

                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 400000);
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    AppLog.i("Travel", "行程調整成功");
                    mainHandler.post(() -> callback.onResult(response, false, null));
                } else {
                    lastError = error;
                    AppLog.e("Travel", "行程調整失敗: " + error);
                    mainHandler.post(() -> callback.onResult(null, true, error));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                AppLog.e("Travel", "行程調整異常: " + err);
                mainHandler.post(() -> callback.onResult(null, true, err));
            }
        });
    }

    public static void optimizeRoute(String spotsJson, String transportMode,
                                      TravelCallback callback) {
        executor.execute(() -> {
            AppLog.i("Travel", "optimizeRoute: transportMode=" + transportMode);
            try {
                JSONObject body = new JSONObject();
                body.put("task", "optimize_route");
                body.put("spots", spotsJson);
                body.put("transport_mode", transportMode);

                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 200000);
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    AppLog.i("Travel", "路線優化成功");
                    mainHandler.post(() -> callback.onResult(response, false, null));
                } else {
                    lastError = error;
                    AppLog.e("Travel", "路線優化失敗: " + error);
                    mainHandler.post(() -> callback.onResult(null, true, error));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                AppLog.e("Travel", "路線優化異常: " + err);
                mainHandler.post(() -> callback.onResult(null, true, err));
            }
        });
    }

    public static void searchAttractions(String region, String type, String preferences,
                                          TravelCallback callback) {
        executor.execute(() -> {
            AppLog.i("Travel", "searchAttractions: region=" + region + " type=" + type);
            try {
                JSONObject body = new JSONObject();
                body.put("task", "search_attractions");
                body.put("region", region);
                body.put("type", type);
                body.put("preferences", preferences);

                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 130000);
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    AppLog.i("Travel", "景點搜尋成功");
                    mainHandler.post(() -> callback.onResult(response, false, null));
                } else {
                    lastError = error;
                    AppLog.e("Travel", "景點搜尋失敗: " + error);
                    mainHandler.post(() -> callback.onResult(null, true, error));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                lastError = err;
                AppLog.e("Travel", "景點搜尋異常: " + err);
                mainHandler.post(() -> callback.onResult(null, true, err));
            }
        });
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

    // --- Stock Recommendation ---

    public interface StockRecommendationCallback {
        void onResult(JSONObject recommendation, String error);
    }

    /**
     * GET /stock/recommend — fetch today's AI stock recommendation from Bridge cache.
     */
    public static void getStockRecommendation(StockRecommendationCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "getStockRecommendation: 取得每日推薦");
            try {
                URL url = new URL(BASE_URL + "/stock/recommend");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                int code = conn.getResponseCode();
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                conn.disconnect();

                if (code == 200) {
                    JSONObject json = new JSONObject(sb.toString());
                    if (json.optBoolean("success", false)) {
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            AppLog.i("Bridge", "stockRecommendation成功: date=" + data.optString("date"));
                            mainHandler.post(() -> callback.onResult(data, null));
                            return;
                        }
                    }
                    String msg = json.optString("error", "無推薦資料");
                    AppLog.w("Bridge", "stockRecommendation無資料: " + msg);
                    mainHandler.post(() -> callback.onResult(null, msg));
                } else {
                    AppLog.e("Bridge", "stockRecommendation HTTP " + code);
                    mainHandler.post(() -> callback.onResult(null, "HTTP " + code));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                AppLog.e("Bridge", "stockRecommendation失敗: " + err);
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    /**
     * GET /stock/chip/recommend — fetch today's chip-based recommendation.
     */
    public static void getChipRecommendation(StockRecommendationCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "getChipRecommendation: 取得籌碼選股");
            try {
                URL url = new URL(BASE_URL + "/stock/chip/recommend");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                int code = conn.getResponseCode();
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                conn.disconnect();

                if (code == 200) {
                    JSONObject json = new JSONObject(sb.toString());
                    if (json.optBoolean("success", false)) {
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            AppLog.i("Bridge", "chipRecommendation成功: date=" + data.optString("date"));
                            mainHandler.post(() -> callback.onResult(data, null));
                            return;
                        }
                    }
                    String msg = json.optString("error", "無籌碼推薦資料");
                    AppLog.w("Bridge", "chipRecommendation無資料: " + msg);
                    mainHandler.post(() -> callback.onResult(null, msg));
                } else {
                    AppLog.e("Bridge", "chipRecommendation HTTP " + code);
                    mainHandler.post(() -> callback.onResult(null, "HTTP " + code));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                AppLog.e("Bridge", "chipRecommendation失敗: " + err);
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    /**
     * GET /stock/chip/tracking — fetch chip recommendation tracking data.
     */
    public static void getChipTracking(StockRecommendationCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "getChipTracking: 取得籌碼追蹤數據");
            try {
                URL url = new URL(BASE_URL + "/stock/chip/tracking");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                int code = conn.getResponseCode();
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                conn.disconnect();

                if (code == 200) {
                    JSONObject json = new JSONObject(sb.toString());
                    if (json.optBoolean("success", false)) {
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            AppLog.i("Bridge", "chipTracking成功: " + data.optJSONObject("stats"));
                            mainHandler.post(() -> callback.onResult(data, null));
                            return;
                        }
                    }
                    String msg = json.optString("error", "無籌碼追蹤資料");
                    AppLog.w("Bridge", "chipTracking無資料: " + msg);
                    mainHandler.post(() -> callback.onResult(null, msg));
                } else {
                    AppLog.e("Bridge", "chipTracking HTTP " + code);
                    mainHandler.post(() -> callback.onResult(null, "HTTP " + code));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                AppLog.e("Bridge", "chipTracking失敗: " + err);
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    /**
     * GET /stock/tracking — fetch recommendation tracking & accuracy data.
     */
    public static void getStockTracking(StockRecommendationCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "getStockTracking: 取得追蹤數據");
            try {
                URL url = new URL(BASE_URL + "/stock/tracking");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                int code = conn.getResponseCode();
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                conn.disconnect();

                if (code == 200) {
                    JSONObject json = new JSONObject(sb.toString());
                    if (json.optBoolean("success", false)) {
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            AppLog.i("Bridge", "stockTracking成功: " + data.optJSONObject("stats"));
                            mainHandler.post(() -> callback.onResult(data, null));
                            return;
                        }
                    }
                    String msg = json.optString("error", "無追蹤資料");
                    AppLog.w("Bridge", "stockTracking無資料: " + msg);
                    mainHandler.post(() -> callback.onResult(null, msg));
                } else {
                    AppLog.e("Bridge", "stockTracking HTTP " + code);
                    mainHandler.post(() -> callback.onResult(null, "HTTP " + code));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                AppLog.e("Bridge", "stockTracking失敗: " + err);
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    /**
     * POST /stock/refresh — trigger full stock analysis pipeline.
     */
    public static void refreshStockRecommendation(StockRecommendationCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "refreshStockRecommendation: 觸發分析");
            try {
                JSONObject body = new JSONObject();
                body.put("action", "refresh");

                String[] result = postJsonWithError(BASE_URL + "/stock/refresh", body.toString(), 300000);
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    JSONObject json = new JSONObject(response);
                    if (json.optBoolean("success", false)) {
                        JSONObject data = json.optJSONObject("data");
                        AppLog.i("Bridge", "refreshStockRecommendation成功");
                        mainHandler.post(() -> callback.onResult(data, null));
                    } else {
                        String msg = json.optString("error", "分析失敗");
                        AppLog.w("Bridge", "refreshStockRecommendation失敗: " + msg);
                        mainHandler.post(() -> callback.onResult(null, msg));
                    }
                } else {
                    AppLog.e("Bridge", "refreshStockRecommendation失敗: " + error);
                    mainHandler.post(() -> callback.onResult(null, error));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                AppLog.e("Bridge", "refreshStockRecommendation異常: " + err);
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    /**
     * POST /stock/watchlist-analyze — analyze a single watchlist stock.
     */
    public static void analyzeWatchlistStock(String symbol, StockRecommendationCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "analyzeWatchlistStock: " + symbol);
            try {
                JSONObject body = new JSONObject();
                body.put("symbol", symbol);

                String[] result = postJsonWithError(BASE_URL + "/stock/watchlist-analyze", body.toString(), 120000);
                String response = result[0];
                String error = result[1];

                if (response != null) {
                    JSONObject json = new JSONObject(response);
                    if (json.optBoolean("success", false)) {
                        JSONObject data = json.optJSONObject("data");
                        AppLog.i("Bridge", "analyzeWatchlistStock成功: " + symbol +
                                " trend=" + (data != null ? data.optString("trend") : "null"));
                        mainHandler.post(() -> callback.onResult(data, null));
                    } else {
                        String msg = json.optString("error", "分析失敗");
                        AppLog.w("Bridge", "analyzeWatchlistStock失敗: " + msg);
                        mainHandler.post(() -> callback.onResult(null, msg));
                    }
                } else {
                    AppLog.e("Bridge", "analyzeWatchlistStock失敗: " + error);
                    mainHandler.post(() -> callback.onResult(null, error));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                AppLog.e("Bridge", "analyzeWatchlistStock異常: " + err);
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    // ── Cron Manager ──────────────────────────────────────────────────────────

    public interface CronCallback {
        void onResult(JSONObject data, String error);
    }

    /**
     * GET /cron/jobs — list all cron jobs with status.
     */
    public static void getCronJobs(CronCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "getCronJobs: 取得排程列表");
            try {
                URL url = new URL(BASE_URL + "/cron/jobs");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                conn.disconnect();

                if (code == 200) {
                    JSONObject json = new JSONObject(sb.toString());
                    if (json.optBoolean("success", false)) {
                        JSONObject data = json.optJSONObject("data");
                        AppLog.i("Bridge", "getCronJobs成功: " + (data != null ? data.optJSONArray("jobs").length() + "個排程" : "null"));
                        mainHandler.post(() -> callback.onResult(data, null));
                        return;
                    }
                    String msg = json.optString("error", "無排程資料");
                    mainHandler.post(() -> callback.onResult(null, msg));
                } else {
                    mainHandler.post(() -> callback.onResult(null, "HTTP " + code));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                AppLog.e("Bridge", "getCronJobs失敗: " + err);
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    /**
     * POST /cron/jobs/toggle — enable/disable a cron job.
     */
    public static void toggleCronJob(String jobId, boolean enabled, CronCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "toggleCronJob: " + jobId + " → " + (enabled ? "啟用" : "停用"));
            try {
                JSONObject body = new JSONObject();
                body.put("id", jobId);
                body.put("enabled", enabled);
                String[] result = postJsonWithError(BASE_URL + "/cron/jobs/toggle", body.toString());
                if (result[0] != null) {
                    JSONObject json = new JSONObject(result[0]);
                    if (json.optBoolean("success", false)) {
                        AppLog.i("Bridge", "toggleCronJob成功: " + jobId);
                        mainHandler.post(() -> callback.onResult(json.optJSONObject("data"), null));
                        return;
                    }
                    String msg = json.optString("error", "操作失敗");
                    mainHandler.post(() -> callback.onResult(null, msg));
                } else {
                    mainHandler.post(() -> callback.onResult(null, result[1]));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                AppLog.e("Bridge", "toggleCronJob失敗: " + err);
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    /**
     * POST /cron/jobs/run — manually trigger a cron job immediately.
     */
    public static void runCronJob(String jobId, CronCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "runCronJob: " + jobId);
            try {
                JSONObject body = new JSONObject();
                body.put("id", jobId);
                String[] result = postJsonWithError(BASE_URL + "/cron/jobs/run", body.toString());
                if (result[0] != null) {
                    JSONObject json = new JSONObject(result[0]);
                    if (json.optBoolean("success", false)) {
                        AppLog.i("Bridge", "runCronJob成功: " + jobId);
                        mainHandler.post(() -> callback.onResult(json.optJSONObject("data"), null));
                        return;
                    }
                    String msg = json.optString("error", "執行失敗");
                    mainHandler.post(() -> callback.onResult(null, msg));
                } else {
                    mainHandler.post(() -> callback.onResult(null, result[1]));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                AppLog.e("Bridge", "runCronJob失敗: " + err);
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    public interface AnalyzeNotificationCallback {
        void onResult(boolean isExpense, double amount, String currency,
                      String category, String merchant, String description, float confidence);
    }

    /**
     * POST /analyze (task=analyze_notification) — AI parses a LINE/notification for expense data.
     */
    public static void analyzeNotification(String source, String title, String content,
                                           List<String> existingCategories,
                                           AnalyzeNotificationCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "analyzeNotification: [" + source + "] " + title);
            try {
                JSONObject body = new JSONObject();
                body.put("task", "analyze_notification");
                body.put("source", source);
                body.put("title", title);
                body.put("content", content);
                if (existingCategories != null && !existingCategories.isEmpty()) {
                    body.put("existing_categories", new JSONArray(existingCategories));
                }
                String[] result = postJsonWithError(BASE_URL + "/analyze", body.toString(), 30000);
                if (result[0] != null) {
                    JSONObject json = new JSONObject(result[0]);
                    if (json.optBoolean("success", false)) {
                        JSONObject r = json.getJSONObject("result");
                        boolean isExpense = r.optBoolean("is_expense", false);
                        double amount = r.optDouble("amount", 0);
                        String currency = r.optString("currency", "TWD");
                        String category = r.optString("category", "未分類");
                        String merchant = r.optString("merchant", "");
                        String description = r.optString("description", "");
                        float confidence = (float) r.optDouble("confidence", 0.0);
                        AppLog.i("Bridge", String.format("analyzeNotification結果: is_expense=%b amount=%.0f conf=%.2f", isExpense, amount, confidence));
                        mainHandler.post(() -> callback.onResult(isExpense, amount, currency, category, merchant, description, confidence));
                        return;
                    }
                }
                AppLog.w("Bridge", "analyzeNotification無結果或失敗");
                mainHandler.post(() -> callback.onResult(false, 0, "TWD", "", "", "", 0));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                AppLog.e("Bridge", "analyzeNotification失敗: " + err);
                mainHandler.post(() -> callback.onResult(false, 0, "TWD", "", "", "", 0));
            }
        });
    }

    /**
     * POST /cron/jobs/schedule — update cron job schedule.
     */
    public static void updateCronSchedule(String jobId, String schedule, CronCallback callback) {
        executor.execute(() -> {
            AppLog.i("Bridge", "updateCronSchedule: " + jobId + " → " + schedule);
            try {
                JSONObject body = new JSONObject();
                body.put("id", jobId);
                body.put("schedule", schedule);
                String[] result = postJsonWithError(BASE_URL + "/cron/jobs/schedule", body.toString());
                if (result[0] != null) {
                    JSONObject json = new JSONObject(result[0]);
                    if (json.optBoolean("success", false)) {
                        AppLog.i("Bridge", "updateCronSchedule成功: " + jobId);
                        mainHandler.post(() -> callback.onResult(json.optJSONObject("data"), null));
                        return;
                    }
                    String msg = json.optString("error", "操作失敗");
                    mainHandler.post(() -> callback.onResult(null, msg));
                } else {
                    mainHandler.post(() -> callback.onResult(null, result[1]));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                AppLog.e("Bridge", "updateCronSchedule失敗: " + err);
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

}
