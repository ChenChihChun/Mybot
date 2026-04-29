package com.mybot.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

public class TimerBubbleService extends Service {

    public static final String EXTRA_DURATION_SECONDS = "duration_seconds";
    public static final String ACTION_STOP = "TIMER_STOP";
    private static final String CHANNEL_ID = "timer_bubble_channel";
    private static final int NOTIF_ID = 9001;

    private WindowManager windowManager;
    private View bubbleView;
    private WindowManager.LayoutParams params;
    private CountDownTimer countDownTimer;
    private TextView timerText;
    private long remainingMillis;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        long durationSeconds = intent != null ? intent.getLongExtra(EXTRA_DURATION_SECONDS, 60) : 60;
        remainingMillis = durationSeconds * 1000L;

        startForeground(NOTIF_ID, buildNotification(formatTime(remainingMillis)));
        showBubble();
        startTimer(remainingMillis);

        AppLog.i("Timer", "浮動計時器啟動: " + durationSeconds + " 秒");
        return START_NOT_STICKY;
    }

    private void showBubble() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        int size = UIHelper.dp(this, 84);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#E61A2733")); // semi-transparent dark card
        bg.setStroke(UIHelper.dp(this, 2), UIHelper.ACCENT_BLUE);
        bubble.setBackground(bg);

        TextView closeBtn = new TextView(this);
        closeBtn.setText("\u00D7");
        closeBtn.setTextColor(UIHelper.TEXT_SECONDARY);
        closeBtn.setTextSize(13);
        closeBtn.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        closeBtn.setPadding(0, 0, UIHelper.dp(this, 8), 0);
        closeBtn.setOnClickListener(v -> stopSelf());

        timerText = new TextView(this);
        timerText.setTextColor(UIHelper.TEXT_PRIMARY);
        timerText.setTextSize(17);
        timerText.setGravity(Gravity.CENTER);
        timerText.setText(formatTime(remainingMillis));

        bubble.addView(closeBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 20)));
        bubble.addView(timerText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        bubbleView = bubble;

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                size, size,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = UIHelper.dp(this, 16);
        params.y = UIHelper.dp(this, 200);

        windowManager.addView(bubbleView, params);
        setupDrag(bubble);
    }

    private void setupDrag(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initX, initY;
            private float initTouchX, initTouchY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = params.x;
                        initY = params.y;
                        initTouchX = event.getRawX();
                        initTouchY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initTouchX);
                        int dy = (int) (event.getRawY() - initTouchY);
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) moved = true;
                        params.x = initX + dx;
                        params.y = initY + dy;
                        if (windowManager != null && bubbleView != null) {
                            windowManager.updateViewLayout(bubbleView, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        return moved;
                }
                return false;
            }
        });
    }

    private void startTimer(long millis) {
        countDownTimer = new CountDownTimer(millis, 500) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingMillis = millisUntilFinished;
                if (timerText != null) {
                    timerText.setText(formatTime(millisUntilFinished));
                }
            }

            @Override
            public void onFinish() {
                remainingMillis = 0;
                if (timerText != null) {
                    timerText.setText("Done!");
                    timerText.setTextColor(UIHelper.ACCENT_GREEN);
                }
                onTimerFinished();
                new Handler(Looper.getMainLooper()).postDelayed(() -> stopSelf(), 3000);
            }
        }.start();
    }

    private void onTimerFinished() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            long[] pattern = {0, 300, 150, 300, 150, 600};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification("計時完成！"));
        AppLog.i("Timer", "浮動計時器完成");
    }

    private Notification buildNotification(String text) {
        NotificationChannel chan = new NotificationChannel(
                CHANNEL_ID, "計時器", NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(chan);

        Intent stopIntent = new Intent(this, TimerBubbleService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("\u23F1 浮動計時器")
                .setContentText(text)
                .addAction(android.R.drawable.ic_delete, "停止", stopPi)
                .setOngoing(true)
                .build();
    }

    private String formatTime(long millis) {
        long totalSec = (millis + 999) / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) {
            return String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", h, m, s);
        }
        return String.format(java.util.Locale.getDefault(), "%d:%02d", m, s);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
        if (bubbleView != null && windowManager != null) {
            try { windowManager.removeView(bubbleView); } catch (Exception ignored) {}
        }
        AppLog.i("Timer", "浮動計時器關閉");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
