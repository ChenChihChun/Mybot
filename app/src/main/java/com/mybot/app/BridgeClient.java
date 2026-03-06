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

    private static final String BASE_URL = "http://localhost:8765";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface AnalyzeCallback {
        void onResult(NotificationLog log);
    }

    public interface CategorizeCallback {
        void onResult(String category, boolean offline);
    }

    public static void categorize(String merchant, String description, double amount, CategorizeCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("task", "categorize_expense");
                body.put("merchant", merchant);
                body.put("description", description);
                body.put("amount", amount);

                String response = postJson(BASE_URL + "/analyze", body.toString());
                if (response != null) {
                    JSONObject json = new JSONObject(response);
                    if (json.optBoolean("success", false)) {
                        JSONObject result = json.getJSONObject("result");
                        String category = result.optString("category", "");
                        mainHandler.post(() -> callback.onResult(category, false));
                        return;
                    }
                }
                mainHandler.post(() -> callback.onResult("", true));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult("", true));
            }
        });
    }

    private static String postJson(String urlStr, String jsonBody) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code == 200) {
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
            }
            conn.disconnect();
        } catch (Exception e) {
            // ignore
        }
        return null;
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

                String response = postJson(BASE_URL + "/analyze", body.toString());
                if (response != null) {
                    JSONObject json = new JSONObject(response);
                    if (json.optBoolean("success", false)) {
                        JSONObject result = json.getJSONObject("result");
                        log.isExpense = result.optBoolean("is_expense", false);
                        log.amount = result.optDouble("amount", 0);
                        log.currency = result.optString("currency", "TWD");
                        log.category = result.optString("category", "");
                        log.merchant = result.optString("merchant", "");
                        log.description = result.optString("description", "");
                        log.confidence = result.optDouble("confidence", 0);
                    }
                    log.analyzed = true;
                    log.offline = false;
                } else {
                    log.analyzed = true;
                    log.offline = true;
                }
            } catch (Exception e) {
                log.analyzed = true;
                log.offline = true;
            }
            mainHandler.post(() -> callback.onResult(log));
        });
    }
}
