package com.mybot.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class FloatingCaptureService extends Service {

    private static final String CHANNEL_ID = "floating_capture";
    private static final int NOTIFICATION_ID = 2001;

    private WindowManager windowManager;
    private View floatingButton;
    private View dismissZone;       // X zone at bottom
    private TextView dismissText;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler;
    private boolean capturing = false;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    private float density;

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
        density = metrics.density;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle stop action from notification
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

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
                createDismissZone();
                createFloatingButton();
                AppLog.i("Capture", "截圖服務啟動");
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
                .setContentText("拖曳按鈕到底部 X 可關閉")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .addAction(android.R.drawable.ic_delete, "停止", stopPi)
                .setOngoing(true)
                .build();
    }

    private void setupImageReader() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight,
                PixelFormat.RGBA_8888, 2);
    }

    // ── Dismiss zone (X at bottom) ──

    private void createDismissZone() {
        int zoneHeight = (int) (80 * density);

        FrameLayout container = new FrameLayout(this);

        // Background gradient (transparent → dark)
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xCC000000});
        container.setBackground(bg);

        // X circle
        dismissText = new TextView(this);
        dismissText.setText("✕");
        dismissText.setTextSize(22);
        dismissText.setTextColor(Color.WHITE);
        dismissText.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xFFEF5350);
        dismissText.setBackground(circle);
        int circleSize = (int) (48 * density);
        FrameLayout.LayoutParams circleLp = new FrameLayout.LayoutParams(circleSize, circleSize);
        circleLp.gravity = Gravity.CENTER;
        dismissText.setLayoutParams(circleLp);

        container.addView(dismissText);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, zoneHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;

        dismissZone = container;
        dismissZone.setVisibility(View.GONE);
        windowManager.addView(dismissZone, params);
    }

    private void showDismissZone() {
        if (dismissZone != null) dismissZone.setVisibility(View.VISIBLE);
    }

    private void hideDismissZone() {
        if (dismissZone != null) dismissZone.setVisibility(View.GONE);
        if (dismissText != null) {
            ((GradientDrawable) dismissText.getBackground()).setColor(0xFFEF5350);
            dismissText.setScaleX(1f);
            dismissText.setScaleY(1f);
        }
    }

    private boolean isOverDismissZone(float rawY) {
        return rawY > screenHeight - (100 * density);
    }

    private void highlightDismissZone(boolean highlight) {
        if (dismissText == null) return;
        if (highlight) {
            ((GradientDrawable) dismissText.getBackground()).setColor(0xFFFF1744);
            dismissText.setScaleX(1.3f);
            dismissText.setScaleY(1.3f);
        } else {
            ((GradientDrawable) dismissText.getBackground()).setColor(0xFFEF5350);
            dismissText.setScaleX(1f);
            dismissText.setScaleY(1f);
        }
    }

    // ── Floating button ──

    private void createFloatingButton() {
        TextView btn = new TextView(this);
        btn.setText("\uD83D\uDCF7");
        btn.setTextSize(24);
        btn.setGravity(Gravity.CENTER);

        // Round button style
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.OVAL);
        btnBg.setColor(0xE61A2733);
        btnBg.setStroke((int) (2 * density), 0x66FFFFFF);
        btn.setBackground(btnBg);

        int size = (int) (56 * density);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = screenWidth - size - 20;
        params.y = screenHeight / 3;

        final int[] lastX = {0}, lastY = {0};
        final int[] initX = {0}, initY = {0};
        final boolean[] moved = {false};
        final boolean[] dragging = {false};

        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastX[0] = (int) event.getRawX();
                    lastY[0] = (int) event.getRawY();
                    initX[0] = lastX[0];
                    initY[0] = lastY[0];
                    moved[0] = false;
                    dragging[0] = false;
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
                        if (!dragging[0]) {
                            dragging[0] = true;
                            showDismissZone();
                        }
                    }

                    // Highlight dismiss zone when hovering
                    highlightDismissZone(isOverDismissZone(event.getRawY()));
                    return true;

                case MotionEvent.ACTION_UP:
                    if (dragging[0] && isOverDismissZone(event.getRawY())) {
                        // Dropped on dismiss zone — stop service
                        hideDismissZone();
                        stopSelf();
                        return true;
                    }
                    hideDismissZone();
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

    // ── Screen capture ──

    private void captureScreen() {
        if (capturing) return;
        capturing = true;

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

                    floatingButton.setVisibility(View.VISIBLE);

                    if (bitmap != null) {
                        AppLog.i("Capture", "截圖完成，開始分析");
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

    // ── AI analysis ──

    private void analyzeScreenshot(Bitmap bitmap) {
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

                ExpenseDbHelper db = new ExpenseDbHelper(this);
                long insertedId = db.insert(amount, currency, category, merchant, description,
                        "截圖", "", System.currentTimeMillis());

                handler.post(() -> {
                    String msg = String.format("已記錄: %s $%.0f", merchant, amount);
                    if (!category.isEmpty()) msg += " (" + category + ")";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    NotificationHelper.sendExpenseNotification(this, merchant, amount, category, insertedId);
                });
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this,
                        "解析錯誤: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // ── Cleanup ──

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppLog.i("Capture", "截圖服務停止");
        if (floatingButton != null) {
            try { windowManager.removeView(floatingButton); } catch (Exception ignored) {}
        }
        if (dismissZone != null) {
            try { windowManager.removeView(dismissZone); } catch (Exception ignored) {}
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
