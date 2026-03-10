package com.mybot.app;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class FlightCheckReceiver extends BroadcastReceiver {

    private static final int NOTIFICATION_ID_BASE = 9100;

    @Override
    public void onReceive(Context context, Intent intent) {
        AppLog.i("Flight", "航班檢查觸發");

        // Reschedule next check first (exact alarms are one-shot)
        if (FlightWatchDbHelper.isFlightCheckEnabled(context)) {
            ReminderHelper.scheduleNextFlightCheck(context);
        }

        // Use goAsync for long-running work
        PendingResult pendingResult = goAsync();

        new Thread(() -> {
            try {
                FlightWatchDbHelper db = new FlightWatchDbHelper(context);
                List<FlightWatchDbHelper.FlightWatch> watches = db.getEnabled();

                if (watches.isEmpty()) {
                    AppLog.i("Flight", "無啟用的航班監控");
                    pendingResult.finish();
                    return;
                }

                AppLog.i("Flight", "檢查 " + watches.size() + " 個航班監控");

                for (int i = 0; i < watches.size(); i++) {
                    FlightWatchDbHelper.FlightWatch watch = watches.get(i);

                    if (i > 0) {
                        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    }

                    try {
                        checkFlight(context, db, watch);
                    } catch (Exception e) {
                        AppLog.e("Flight", "檢查航班失敗 id=" + watch.id + ": " + e.getMessage());
                    }
                }

                AppLog.i("Flight", "航班檢查完成");
            } catch (Exception e) {
                AppLog.e("Flight", "航班檢查異常: " + e.getMessage());
            } finally {
                pendingResult.finish();
            }
        }).start();
    }

    private void checkFlight(Context context, FlightWatchDbHelper db,
                              FlightWatchDbHelper.FlightWatch watch) {
        AppLog.i("Flight", "檢查: " + watch.origin + "→" + watch.destination
                + " date=" + watch.departureDate);

        String response = BridgeClient.searchFlightsSync(
                watch.origin, watch.destination,
                watch.departureDate, watch.returnDate, watch.searchMode,
                watch.roundTrip, watch.preferredAirlines);

        if (response == null) {
            AppLog.w("Flight", "搜尋失敗 id=" + watch.id);
            return;
        }

        try {
            JSONObject json = new JSONObject(response);
            if (!json.optBoolean("success", false)) {
                AppLog.w("Flight", "Bridge回應失敗: " + json.optString("error", ""));
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
                    AppLog.w("Flight", "無法解析結果 id=" + watch.id);
                    return;
                }
            } else {
                AppLog.w("Flight", "結果格式異常 id=" + watch.id);
                return;
            }

            double cheapestPrice = result.optDouble("cheapest_price", 0);
            String resultJson = result.toString();

            long now = System.currentTimeMillis();
            db.updateCheckResult(watch.id, now, cheapestPrice, resultJson);

            AppLog.i("Flight", "id=" + watch.id + " 最低價=" + cheapestPrice
                    + " 目標價=" + watch.targetPrice
                    + " 上次最低=" + watch.lastLowestPrice);

            // Notify logic:
            // - If target_price > 0: notify when price <= target OR drop >10%
            // - If target_price == 0 (auto-track): notify on first result + every >10% drop
            boolean shouldNotify = false;
            String reason = "";

            if (cheapestPrice > 0 && watch.targetPrice > 0
                    && cheapestPrice <= watch.targetPrice) {
                shouldNotify = true;
                reason = "達到目標價 $" + String.format("%.0f", watch.targetPrice);
            } else if (cheapestPrice > 0 && watch.lastLowestPrice > 0
                    && cheapestPrice < watch.lastLowestPrice * 0.9) {
                shouldNotify = true;
                long drop = Math.round((1 - cheapestPrice / watch.lastLowestPrice) * 100);
                reason = "降價 " + drop + "% (前次 $" + String.format("%.0f", watch.lastLowestPrice) + ")";
            } else if (cheapestPrice > 0 && watch.lastLowestPrice <= 0) {
                // First check — send initial price notification
                shouldNotify = true;
                reason = "首次查詢結果";
            }

            if (shouldNotify) {
                sendFlightNotification(context, watch, cheapestPrice, reason, result);
            }
        } catch (Exception e) {
            AppLog.e("Flight", "解析結果異常 id=" + watch.id + ": " + e.getMessage());
        }
    }

    private void sendFlightNotification(Context context, FlightWatchDbHelper.FlightWatch watch,
                                         double price, String reason, JSONObject result) {
        NotificationHelper.createNotificationChannel(context);

        String title = "✈ 航班價格通知";
        String currency = watch.currency != null ? watch.currency : "TWD";
        String text = String.format("%s→%s %s $%.0f (%s)",
                watch.origin, watch.destination, watch.departureDate,
                price, reason);

        // Add flight details
        StringBuilder detail = new StringBuilder(text);
        JSONArray flights = result.optJSONArray("flights");
        if (flights != null && flights.length() > 0) {
            JSONObject cheapest = flights.optJSONObject(0);
            if (cheapest != null) {
                String airline = cheapest.optString("airline", "");
                String depTime = cheapest.optString("departure_time", "");
                int stops = cheapest.optInt("stops", -1);
                if (!airline.isEmpty()) detail.append("\n").append(airline);
                if (!depTime.isEmpty()) detail.append(" ").append(depTime);
                if (stops >= 0) detail.append(stops == 0 ? " 直飛" : " 轉" + stops + "次");
            }
        }

        Intent tapIntent = new Intent(context, FlightActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context,
                NOTIFICATION_ID_BASE + (int) watch.id, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "mybot_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(detail.toString()))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID_BASE + (int) watch.id, builder.build());
        }

        AppLog.i("Flight", "發送通知: " + text);
    }
}
