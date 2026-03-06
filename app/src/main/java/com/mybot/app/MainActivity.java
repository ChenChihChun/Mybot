package com.mybot.app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NotificationHelper.createNotificationChannel(this);
        requestPermissions();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("Mybot");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);

        TextView status = new TextView(this);
        status.setText("通知監聽服務運行中\n請確認已開啟通知存取權限");
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 32, 0, 32);

        Button btnNotification = new Button(this);
        btnNotification.setText("開啟通知存取權限");
        btnNotification.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        });

        Button btnExpenses = new Button(this);
        btnExpenses.setText("消費紀錄");
        btnExpenses.setOnClickListener(v -> {
            startActivity(new Intent(this, ExpenseActivity.class));
        });

        Button btnMonitor = new Button(this);
        btnMonitor.setText("監聽狀態");
        btnMonitor.setOnClickListener(v -> {
            startActivity(new Intent(this, MonitorActivity.class));
        });

        layout.addView(title);
        layout.addView(status);
        layout.addView(btnNotification);
        layout.addView(btnExpenses);
        layout.addView(btnMonitor);

        setContentView(layout);
    }

    private void requestPermissions() {
        String[] perms;
        if (Build.VERSION.SDK_INT >= 33) {
            perms = new String[]{
                    android.Manifest.permission.RECEIVE_SMS,
                    android.Manifest.permission.READ_SMS,
                    "android.permission.POST_NOTIFICATIONS"
            };
        } else {
            perms = new String[]{
                    android.Manifest.permission.RECEIVE_SMS,
                    android.Manifest.permission.READ_SMS
            };
        }

        boolean needRequest = false;
        for (String perm : perms) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }

        if (needRequest) {
            ActivityCompat.requestPermissions(this, perms, PERMISSION_CODE);
        }
    }
}
