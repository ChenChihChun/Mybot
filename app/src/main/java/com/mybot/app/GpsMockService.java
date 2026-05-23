package com.mybot.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

public class GpsMockService extends Service {

    public static final String EXTRA_START_LAT = "start_lat";
    public static final String EXTRA_START_LNG = "start_lng";
    public static final String EXTRA_END_LAT = "end_lat";
    public static final String EXTRA_END_LNG = "end_lng";
    public static final String EXTRA_DURATION_MS = "duration_ms";

    public static final String ACTION_STOP = "com.mybot.app.GPS_MOCK_STOP";

    private static final String CHANNEL_ID = "gps_mock_channel";
    private static final int NOTIFICATION_ID = 9527;
    private static final String PROVIDER_NAME = LocationManager.GPS_PROVIDER;
    private static final long UPDATE_INTERVAL_MS = 1000; // 1 second

    // Static state tracking (reliable across Android versions)
    private static volatile boolean sIsRunning = false;
    private static volatile double sCurrentLat = 0;
    private static volatile double sCurrentLng = 0;
    private static volatile double sProgress = 0;
    private static volatile String sLastError = null;

    private LocationManager locationManager;
    private Handler handler;

    private double startLat, startLng;
    private double endLat, endLng;
    private long durationMs;
    private long startTimeMs;

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!sIsRunning) return;

            long elapsed = System.currentTimeMillis() - startTimeMs;
            double fraction = Math.min(1.0, (double) elapsed / durationMs);

            double[] pos = interpolate(startLat, startLng, endLat, endLng, fraction);
            double bearing = calculateBearing(startLat, startLng, endLat, endLng);

            // Update static state
            sCurrentLat = pos[0];
            sCurrentLng = pos[1];
            sProgress = fraction;

            setMockLocation(pos[0], pos[1], bearing);
            updateNotification(fraction, pos[0], pos[1]);

            if (fraction >= 1.0) {
                AppLog.i("GpsMock", "到達目的地: " + String.format("%.4f, %.4f", endLat, endLng));
                stopSelf();
            } else {
                handler.postDelayed(this, UPDATE_INTERVAL_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_STOP.equals(intent.getAction())) {
            AppLog.i("GpsMock", "使用者停止模擬");
            stopSelf();
            return START_NOT_STICKY;
        }

        startLat = intent.getDoubleExtra(EXTRA_START_LAT, 0);
        startLng = intent.getDoubleExtra(EXTRA_START_LNG, 0);
        endLat = intent.getDoubleExtra(EXTRA_END_LAT, 0);
        endLng = intent.getDoubleExtra(EXTRA_END_LNG, 0);
        durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 600_000);

        double distance = haversineDistance(startLat, startLng, endLat, endLng);
        AppLog.i("GpsMock", String.format("開始模擬: (%.4f, %.4f) -> (%.4f, %.4f), 距離: %.1fkm, 時長: %d分鐘",
                startLat, startLng, endLat, endLng, distance / 1000, durationMs / 60000));

        if (!setupMockProvider()) {
            sLastError = "請在「設定 > 開發者選項 > 選取模擬位置應用程式」中選擇 Mybot";
            AppLog.e("GpsMock", "無法設定 Mock Provider: " + sLastError);
            stopSelf();
            return START_NOT_STICKY;
        }
        sLastError = null;

        // Update static state
        sIsRunning = true;
        sCurrentLat = startLat;
        sCurrentLng = startLng;
        sProgress = 0;

        startForeground(NOTIFICATION_ID, buildNotification(0, startLat, startLng));

        startTimeMs = System.currentTimeMillis();
        handler.post(updateRunnable);

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        sIsRunning = false;
        handler.removeCallbacks(updateRunnable);
        removeMockProvider();
        AppLog.i("GpsMock", "服務已停止");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean setupMockProvider() {
        try {
            try {
                locationManager.removeTestProvider(PROVIDER_NAME);
            } catch (Exception ignored) {}

            if (Build.VERSION.SDK_INT >= 31) {
                locationManager.addTestProvider(
                        PROVIDER_NAME,
                        false, false, false, false, true, true, true,
                        ProviderProperties.POWER_USAGE_LOW,
                        ProviderProperties.ACCURACY_FINE);
            } else {
                locationManager.addTestProvider(
                        PROVIDER_NAME,
                        false, false, false, false, true, true, true,
                        Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
            }
            locationManager.setTestProviderEnabled(PROVIDER_NAME, true);
            AppLog.i("GpsMock", "Mock Provider 設定成功");
            return true;
        } catch (SecurityException e) {
            AppLog.e("GpsMock", "權限錯誤: " + e.getMessage());
            return false;
        } catch (Exception e) {
            AppLog.e("GpsMock", "設定 Provider 失敗: " + e.getMessage());
            return false;
        }
    }

    private void removeMockProvider() {
        try {
            locationManager.setTestProviderEnabled(PROVIDER_NAME, false);
            locationManager.removeTestProvider(PROVIDER_NAME);
        } catch (Exception ignored) {}
    }

    private void setMockLocation(double lat, double lng, double bearing) {
        Location loc = new Location(PROVIDER_NAME);
        loc.setLatitude(lat);
        loc.setLongitude(lng);
        loc.setAltitude(10.0);
        loc.setAccuracy(3.0f);
        loc.setBearing((float) bearing);
        loc.setSpeed(calculateSpeed());
        loc.setTime(System.currentTimeMillis());
        loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

        try {
            locationManager.setTestProviderLocation(PROVIDER_NAME, loc);
        } catch (Exception e) {
            AppLog.e("GpsMock", "設定位置失敗: " + e.getMessage());
        }
    }

    private float calculateSpeed() {
        double distance = haversineDistance(startLat, startLng, endLat, endLng);
        return (float) (distance / (durationMs / 1000.0));
    }

    private static double[] interpolate(double lat1, double lng1, double lat2, double lng2, double fraction) {
        double phi1 = Math.toRadians(lat1);
        double lambda1 = Math.toRadians(lng1);
        double phi2 = Math.toRadians(lat2);
        double lambda2 = Math.toRadians(lng2);

        double deltaPhi = phi2 - phi1;
        double deltaLambda = lambda2 - lambda1;

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double delta = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        if (delta < 1e-10) {
            return new double[]{lat1, lng1};
        }

        double A = Math.sin((1 - fraction) * delta) / Math.sin(delta);
        double B = Math.sin(fraction * delta) / Math.sin(delta);

        double x = A * Math.cos(phi1) * Math.cos(lambda1) + B * Math.cos(phi2) * Math.cos(lambda2);
        double y = A * Math.cos(phi1) * Math.sin(lambda1) + B * Math.cos(phi2) * Math.sin(lambda2);
        double z = A * Math.sin(phi1) + B * Math.sin(phi2);

        double phi = Math.atan2(z, Math.sqrt(x * x + y * y));
        double lambda = Math.atan2(y, x);

        return new double[]{Math.toDegrees(phi), Math.toDegrees(lambda)};
    }

    private static double calculateBearing(double lat1, double lng1, double lat2, double lng2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLambda = Math.toRadians(lng2 - lng1);

        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2)
                - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }

    private static double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000;
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lng2 - lng1);

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "GPS 模擬器",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("GPS 位置模擬服務");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification(double progress, double lat, double lng) {
        Intent stopIntent = new Intent(this, GpsMockService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent openIntent = new Intent(this, GpsMockActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int percent = (int) (progress * 100);
        long remainingMs = (long) ((1.0 - progress) * durationMs);
        String remainingText = formatDuration(remainingMs);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(String.format("GPS 模擬中 %d%%", percent))
                .setContentText(String.format("位置: %.4f, %.4f | 剩餘 %s", lat, lng, remainingText))
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setProgress(100, percent, false)
                .setContentIntent(openPending)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPending)
                .build();
    }

    private void updateNotification(double progress, double lat, double lng) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, buildNotification(progress, lat, lng));
    }

    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes > 0) {
            return String.format("%d分%d秒", minutes, seconds);
        }
        return String.format("%d秒", seconds);
    }

    // Static methods for state access
    public static boolean isRunning() {
        return sIsRunning;
    }

    public static double getCurrentLat() {
        return sCurrentLat;
    }

    public static double getCurrentLng() {
        return sCurrentLng;
    }

    public static double getProgress() {
        return sProgress;
    }

    public static String getLastError() {
        return sLastError;
    }

    public static void clearLastError() {
        sLastError = null;
    }
}
