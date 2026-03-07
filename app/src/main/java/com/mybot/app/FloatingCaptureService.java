package com.mybot.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class FloatingCaptureService extends Service {

    private static final String CHANNEL_ID = "floating_capture";
    private static final int NOTIFICATION_ID = 2001;

    private WindowManager windowManager;
    private View floatingButton;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler;
    private boolean capturing = false;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        if (intent != null && intent.hasExtra("resultCode")) {
            int resultCode = intent.getIntExtra("resultCode", 0);
            Intent data = intent.getParcelableExtra("data");

            if (data != null) {
                MediaProjectionManager mpm = (MediaProjectionManager)
                        getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                mediaProjection = mpm.getMediaProjection(resultCode, data);
                setupImageReader();
                createFloatingButton();
            }
        }

        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "截圖記帳", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("懸浮截圖按鈕服務");
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, FloatingCaptureService.class);
        stopIntent.setAction("STOP");
        PendingIntent stopPi = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("截圖記帳運行中")
                .setContentText("點擊懸浮按鈕截圖分析消費")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .addAction(android.R.drawable.ic_delete, "停止", stopPi)
                .setOngoing(true)
                .build();
    }

    private void setupImageReader() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight,
                PixelFormat.RGBA_8888, 2);
    }

    private void createFloatingButton() {
        TextView btn = new TextView(this);
        btn.setText("\uD83D\uDCF7"); // camera emoji
        btn.setTextSize(24);
        btn.setGravity(Gravity.CENTER);
        btn.setBackgroundColor(0xCC1A2733);
        btn.setPadding(20, 20, 20, 20);

        int size = (int) (56 * getResources().getDisplayMetrics().density);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = screenWidth - size - 20;
        params.y = screenHeight / 3;

        // Drag support
        final int[] lastX = {0}, lastY = {0};
        final int[] initX = {0}, initY = {0};
        final boolean[] moved = {false};

        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastX[0] = (int) event.getRawX();
                    lastY[0] = (int) event.getRawY();
                    initX[0] = lastX[0];
                    initY[0] = lastY[0];
                    moved[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) event.getRawX() - lastX[0];
                    int dy = (int) event.getRawY() - lastY[0];
                    params.x += dx;
                    params.y += dy;
                    windowManager.updateViewLayout(floatingButton, params);
                    lastX[0] = (int) event.getRawX();
                    lastY[0] = (int) event.getRawY();
                    if (Math.abs(event.getRawX() - initX[0]) > 10 ||
                            Math.abs(event.getRawY() - initY[0]) > 10) {
                        moved[0] = true;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved[0]) {
                        captureScreen();
                    }
                    return true;
            }
            return false;
        });

        floatingButton = btn;
        windowManager.addView(floatingButton, params);
    }

    private void captureScreen() {
        if (capturing) return;
        capturing = true;

        // Hide floating button during capture
        floatingButton.setVisibility(View.INVISIBLE);

        handler.postDelayed(() -> {
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    screenWidth, screenHeight, screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, handler);

            handler.postDelayed(() -> {
                Image image = imageReader.acquireLatestImage();
                if (image != null) {
                    Bitmap bitmap = imageToBitmap(image);
                    image.close();

                    if (virtualDisplay != null) {
                        virtualDisplay.release();
                        virtualDisplay = null;
                    }

                    // Show button again
                    floatingButton.setVisibility(View.VISIBLE);

                    if (bitmap != null) {
                        analyzeScreenshot(bitmap);
                    } else {
                        Toast.makeText(this, "截圖失敗", Toast.LENGTH_SHORT).show();
                        capturing = false;
                    }
                } else {
                    if (virtualDisplay != null) {
                        virtualDisplay.release();
                        virtualDisplay = null;
                    }
                    floatingButton.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "截圖失敗，請重試", Toast.LENGTH_SHORT).show();
                    capturing = false;
                }
            }, 300);
        }, 200);
    }

    private Bitmap imageToBitmap(Image image) {
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;

            Bitmap bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride, screenHeight,
                    Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);

            // Crop to actual screen size if needed
            if (bitmap.getWidth() > screenWidth) {
                Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
                bitmap.recycle();
                return cropped;
            }
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private void analyzeScreenshot(Bitmap bitmap) {
        // Compress and convert to base64
        // Scale down for faster transfer
        int maxDim = 1080;
        float scale = Math.min((float) maxDim / bitmap.getWidth(),
                (float) maxDim / bitmap.getHeight());
        if (scale < 1.0f) {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                    (int) (bitmap.getWidth() * scale),
                    (int) (bitmap.getHeight() * scale), true);
            bitmap.recycle();
            bitmap = scaled;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        bitmap.recycle();
        String base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

        Toast.makeText(this, "AI 分析中...", Toast.LENGTH_SHORT).show();

        BridgeClient.analyzeScreenshot(base64, (result, offline, error) -> {
            capturing = false;

            if (offline || result == null) {
                handler.post(() -> Toast.makeText(this,
                        "分析失敗" + (error != null ? ": " + error : ""),
                        Toast.LENGTH_LONG).show());
                return;
            }

            try {
                org.json.JSONObject json = new org.json.JSONObject(result);
                if (!json.optBoolean("success", false)) {
                    handler.post(() -> Toast.makeText(this,
                            "AI 未辨識到消費資訊", Toast.LENGTH_SHORT).show());
                    return;
                }

                org.json.JSONObject r = json.getJSONObject("result");
                boolean isExpense = r.optBoolean("is_expense", false);

                if (!isExpense) {
                    handler.post(() -> Toast.makeText(this,
                            "畫面中未偵測到消費資訊", Toast.LENGTH_SHORT).show());
                    return;
                }

                double amount = r.optDouble("amount", 0);
                String currency = r.optString("currency", "TWD");
                String merchant = r.optString("merchant", "");
                String category = r.optString("category", "");
                String description = r.optString("description", "");

                // Save to DB
                ExpenseDbHelper db = new ExpenseDbHelper(this);
                db.insert(amount, currency, category, merchant, description,
                        "截圖", "", System.currentTimeMillis());

                handler.post(() -> {
                    String msg = String.format("已記錄: %s $%.0f", merchant, amount);
                    if (!category.isEmpty()) msg += " (" + category + ")";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

                    // Also send a notification
                    NotificationHelper.sendExpenseNotification(this, merchant, amount, category);
                });
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this,
                        "解析錯誤: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingButton != null) {
            windowManager.removeView(floatingButton);
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
        if (imageReader != null) {
            imageReader.close();
        }
    }
}
