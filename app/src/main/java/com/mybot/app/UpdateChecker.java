package com.mybot.app;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateChecker {

    private static final String GITHUB_API = "https://api.github.com/repos/ChenChihChun/Mybot/releases/latest";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface UpdateCallback {
        void onResult(boolean hasUpdate, String latestVersion, int latestCode,
                      String downloadUrl, String releaseNotes, String error);
    }

    public static int getCurrentVersionCode(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String getCurrentVersionName(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    public static void checkForUpdate(Context ctx, UpdateCallback callback) {
        AppLog.i("Update", "開始檢查更新");
        executor.execute(() -> {
            try {
                URL url = new URL(GITHUB_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    mainHandler.post(() -> callback.onResult(false, null, 0, null, null,
                            "HTTP " + code));
                    conn.disconnect();
                    return;
                }

                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                conn.disconnect();

                JSONObject release = new JSONObject(sb.toString());
                String tagName = release.optString("tag_name", "");
                String body = release.optString("body", "");

                // Parse version code from tag (format: v2.5-c17 or v2.5)
                int remoteCode = parseVersionCode(tagName);
                String remoteName = parseVersionName(tagName);
                int currentCode = getCurrentVersionCode(ctx);

                // Find APK download URL
                String apkUrl = null;
                JSONArray assets = release.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString("name", "");
                        if (name.endsWith(".apk")) {
                            apkUrl = asset.getString("browser_download_url");
                            break;
                        }
                    }
                }

                boolean hasUpdate = remoteCode > currentCode && apkUrl != null;
                String finalApkUrl = apkUrl;
                if (hasUpdate) {
                    AppLog.i("Update", "有新版本: v" + remoteName + " (code " + remoteCode + ")");
                } else {
                    AppLog.i("Update", "已是最新版本 (current=" + currentCode + " remote=" + remoteCode + ")");
                }
                mainHandler.post(() -> callback.onResult(hasUpdate, remoteName, remoteCode,
                        finalApkUrl, body, null));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                mainHandler.post(() -> callback.onResult(false, null, 0, null, null, err));
            }
        });
    }

    private static int parseVersionCode(String tag) {
        // tag format: v2.5-c17 → extract 17
        try {
            if (tag.contains("-c")) {
                return Integer.parseInt(tag.substring(tag.indexOf("-c") + 2));
            }
            // Fallback: try parsing after 'v' as simple version
            String ver = tag.startsWith("v") ? tag.substring(1) : tag;
            // Convert version like 2.5 to code: 25
            return (int) (Double.parseDouble(ver) * 10);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String parseVersionName(String tag) {
        // tag format: v2.5-c17 → extract 2.5
        try {
            String name = tag.startsWith("v") ? tag.substring(1) : tag;
            if (name.contains("-")) {
                name = name.substring(0, name.indexOf("-"));
            }
            return name;
        } catch (Exception e) {
            return tag;
        }
    }

    public static void showUpdateDialog(Context ctx, String latestVersion, int latestCode,
                                         String downloadUrl, String releaseNotes) {
        String currentVer = getCurrentVersionName(ctx);
        int currentCode = getCurrentVersionCode(ctx);

        String message = "目前版本: v" + currentVer + " (code " + currentCode + ")\n"
                + "最新版本: v" + latestVersion + " (code " + latestCode + ")";
        if (releaseNotes != null && !releaseNotes.isEmpty()) {
            message += "\n\n更新內容:\n" + releaseNotes;
        }

        new AlertDialog.Builder(ctx, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("發現新版本")
                .setMessage(message)
                .setPositiveButton("下載更新", (d, w) -> downloadAndInstall(ctx, downloadUrl, latestVersion))
                .setNegativeButton("稍後", null)
                .show();
    }

    public static void downloadAndInstall(Context ctx, String downloadUrl, String version) {
        AppLog.i("Update", "開始下載更新: v" + version);
        Toast.makeText(ctx, "開始下載更新...", Toast.LENGTH_SHORT).show();

        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        request.setTitle("Mybot v" + version);
        request.setDescription("下載更新中...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setMimeType("application/vnd.android.package-archive");

        String fileName = "mybot-v" + version + ".apk";
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

        long downloadId = dm.enqueue(request);

        // Listen for download complete — must use RECEIVER_EXPORTED for system broadcast
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    try { context.unregisterReceiver(this); } catch (Exception ignored) {}
                    installApk(context, dm, downloadId, fileName);
                }
            }
        };

        if (Build.VERSION.SDK_INT >= 33) {
            ctx.registerReceiver(receiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED);
        } else {
            ctx.registerReceiver(receiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

    private static void installApk(Context ctx, DownloadManager dm, long downloadId, String fileName) {
        // Try DownloadManager URI first (most reliable)
        Uri downloadUri = dm.getUriForDownloadedFile(downloadId);
        if (downloadUri != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(downloadUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(intent);
                return;
            } catch (Exception ignored) {}
        }

        // Fallback: use FileProvider
        File apkFile = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), fileName);

        if (!apkFile.exists()) {
            Toast.makeText(ctx, "APK 檔案不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri = FileProvider.getUriForFile(ctx,
                ctx.getPackageName() + ".fileprovider", apkFile);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    /**
     * Delete old mybot APK files from Downloads directory.
     * Call on app startup to clean up after successful update.
     */
    public static void cleanOldApks(Context ctx) {
        try {
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloads == null || !downloads.exists()) return;

            File[] apks = downloads.listFiles((dir, name) ->
                    name.startsWith("mybot-v") && name.endsWith(".apk"));
            if (apks == null) return;

            int deleted = 0;
            for (File apk : apks) {
                if (apk.delete()) deleted++;
            }
            if (deleted > 0) {
                AppLog.i("Update", "已清理 " + deleted + " 個舊 APK 檔案");
            }
        } catch (Exception e) {
            AppLog.w("Update", "清理舊APK失敗: " + e.getMessage());
        }
    }

    public static void showNoUpdateDialog(Context ctx) {
        String currentVer = getCurrentVersionName(ctx);
        int currentCode = getCurrentVersionCode(ctx);
        new AlertDialog.Builder(ctx, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("版本檢查")
                .setMessage("目前版本: v" + currentVer + " (code " + currentCode + ")\n\n已經是最新版本！")
                .setPositiveButton("確定", null)
                .show();
    }
}
