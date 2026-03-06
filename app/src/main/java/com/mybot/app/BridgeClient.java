package com.mybot.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BridgeClient {

    private static final String BASE_URL = "http://localhost:8765";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient client = new OkHttpClient();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface AnalyzeCallback {
        void onResult(NotificationLog log);
    }

    public static void analyze(NotificationLog log, AnalyzeCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("task", "analyze_notification");
            body.put("source", log.source);
            body.put("app", log.sourceApp);
            body.put("title", log.title);
            body.put("content", log.content);

            Request request = new Request.Builder()
                    .url(BASE_URL + "/analyze")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.analyzed = true;
                    log.offline = true;
                    mainHandler.post(() -> callback.onResult(log));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);

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
                    } catch (Exception e) {
                        log.analyzed = true;
                        log.offline = true;
                    }
                    mainHandler.post(() -> callback.onResult(log));
                }
            });
        } catch (Exception e) {
            log.analyzed = true;
            log.offline = true;
            mainHandler.post(() -> callback.onResult(log));
        }
    }
}
