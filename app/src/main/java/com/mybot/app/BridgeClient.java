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

    /**
     * Returns [responseBody, errorMessage]. responseBody is null on failure.
     */
    private static String[] postJsonWithError(String urlStr, String jsonBody) {
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
