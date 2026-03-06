# Mybot Android App - 接續開發 Prompt

我有一個已經在運作的 Android app 專案，需要你幫我繼續擴充功能。以下是目前的完整程式碼和專案狀態。

## 專案資訊
- **GitHub Repo**: https://github.com/ChenChihChun/Mybot.git
- **Branch**: main
- **Package**: com.mybot.app
- **當前版本**: versionCode 4, versionName "1.3"
- **CI/CD**: GitHub Actions 自動編譯 debug APK（push to main 觸發）

## 技術規格
- 純 Java（不用 Kotlin）
- minSdk 26, targetSdk 33, compileSdk 33
- AGP 7.4.2 + Gradle 7.5.1 + JDK 17
- 使用 AndroidX（core:1.10.1, appcompat:1.6.1）
- kotlin-stdlib 需要用 resolutionStrategy force 到 1.8.10 避免衝突

## 目前功能
1. **NotificationListenerService** — 監聽所有 app 通知，發送本地通知：「[通知] {app名稱}: {標題} - {內容}」
2. **BroadcastReceiver (SMS)** — 監聯收到的 SMS，發送本地通知：「[簡訊] 來自 {號碼}: {內容}」
3. **BroadcastReceiver (Boot)** — 開機自動啟動
4. **MainActivity** — 簡單 UI 頁面，請求權限並提供按鈕跳轉通知存取設定
5. **NotificationHelper** — 統一管理本地通知發送

## 目前完整程式碼

### 專案結構
```
Mybot/
├── .github/workflows/build.yml
├── .gitignore
├── build.gradle (root)
├── settings.gradle
├── gradle.properties
├── app/build.gradle
└── app/src/main/
    ├── AndroidManifest.xml
    └── java/com/mybot/app/
        ├── MainActivity.java
        ├── NotificationService.java
        ├── SmsReceiver.java
        ├── BootReceiver.java
        └── NotificationHelper.java
    └── res/values/strings.xml
```

### settings.gradle
```groovy
rootProject.name = 'Mybot'
include ':app'
```

### build.gradle (root)
```groovy
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:7.4.2'
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
```

### app/build.gradle
```groovy
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.mybot.app'
    compileSdk 33

    defaultConfig {
        applicationId "com.mybot.app"
        minSdk 26
        targetSdk 33
        versionCode 4
        versionName "1.3"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}

configurations.all {
    resolutionStrategy {
        force 'org.jetbrains.kotlin:kotlin-stdlib:1.8.10'
        force 'org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.10'
        force 'org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.10'
    }
}

dependencies {
    implementation 'androidx.core:core:1.10.1'
    implementation 'androidx.appcompat:appcompat:1.6.1'
}
```

### gradle.properties
```
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
```

### AndroidManifest.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.RECEIVE_SMS" />
    <uses-permission android:name="android.permission.READ_SMS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:allowBackup="true"
        android:icon="@android:drawable/ic_dialog_info"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.AppCompat.Light.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".NotificationService"
            android:exported="true"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

        <receiver
            android:name=".SmsReceiver"
            android:exported="true"
            android:permission="android.permission.BROADCAST_SMS">
            <intent-filter android:priority="999">
                <action android:name="android.provider.Telephony.SMS_RECEIVED" />
            </intent-filter>
        </receiver>

        <receiver
            android:name=".BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.QUICKBOOT_POWERON" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

### MainActivity.java
```java
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

        layout.addView(title);
        layout.addView(status);
        layout.addView(btnNotification);

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
```

### NotificationService.java
```java
package com.mybot.app;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotificationService extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn.getPackageName().equals(getPackageName())) {
            return;
        }

        String appName = getAppName(sbn);
        String title = "";
        String content = "";

        if (sbn.getNotification().extras != null) {
            CharSequence titleCs = sbn.getNotification().extras.getCharSequence("android.title");
            CharSequence textCs = sbn.getNotification().extras.getCharSequence("android.text");
            if (titleCs != null) {
                title = titleCs.toString();
            }
            if (textCs != null) {
                content = textCs.toString();
            }
        }

        String message = "[通知] " + appName + ": " + title + " - " + content;
        NotificationHelper.sendNotification(this, "Mybot - 通知監聽", message);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
    }

    private String getAppName(StatusBarNotification sbn) {
        try {
            return getPackageManager()
                    .getApplicationLabel(
                            getPackageManager().getApplicationInfo(sbn.getPackageName(), 0))
                    .toString();
        } catch (Exception e) {
            return sbn.getPackageName();
        }
    }
}
```

### SmsReceiver.java
```java
package com.mybot.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            return;
        }

        Object[] pdus = (Object[]) bundle.get("pdus");
        String format = bundle.getString("format");
        if (pdus == null) {
            return;
        }

        for (Object pdu : pdus) {
            SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
            String sender = smsMessage.getDisplayOriginatingAddress();
            String body = smsMessage.getDisplayMessageBody();

            String message = "[簡訊] 來自 " + sender + ": " + body;
            NotificationHelper.sendNotification(context, "Mybot - 簡訊監聽", message);
        }
    }
}
```

### BootReceiver.java
```java
package com.mybot.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            NotificationHelper.createNotificationChannel(context);
            NotificationHelper.sendNotification(context, "Mybot", "Mybot 已隨開機啟動");
        }
    }
}
```

### NotificationHelper.java
```java
package com.mybot.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationHelper {

    private static final String CHANNEL_ID = "mybot_channel";
    private static final String CHANNEL_NAME = "Mybot Notifications";
    private static int notificationId = 0;

    public static void createNotificationChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Mybot notification and SMS alerts");
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    public static void sendNotification(Context context, String title, String text) {
        createNotificationChannel(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(notificationId++, builder.build());
        }
    }
}
```

### res/values/strings.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Mybot</string>
    <string name="notification_channel_id">mybot_channel</string>
    <string name="notification_channel_name">Mybot Notifications</string>
</resources>
```

### .github/workflows/build.yml
```yaml
name: Build Debug APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '7.5.1'

      - name: Build debug APK
        run: gradle assembleDebug

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: mybot-v${{ github.run_number }}-debug
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 7
```

## 重要注意事項
- 修改程式碼時請提供完整的檔案內容，我會直接覆蓋到專案中
- 每次改版請幫我遞增 versionCode 和 versionName
- 不要使用 Kotlin，純 Java
- 不需要 gradle wrapper jar，CI 用 `gradle/actions/setup-gradle` 直接安裝
- 請告訴我需要新增或修改哪些檔案，我會手動更新到 GitHub

---

## 我想要新增的功能：
（在這裡描述你想要的新功能）
