package com.mybot.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GoogleAuthHelper {

    public static final int RC_SIGN_IN = 9001;
    private static final String CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar";
    private static final String YOUTUBE_SCOPE = "https://www.googleapis.com/auth/youtube.force-ssl";
    private static final String PREFS_NAME = "calendar_prefs";
    private static final String KEY_WEB_CLIENT_ID = "web_client_id";
    private static final String KEY_DEFAULT_CALENDAR = "default_calendar_id";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface SignInCallback {
        void onResult(boolean success, String error);
    }

    public interface TokenCallback {
        void onResult(String token, String error);
    }

    public static void saveWebClientId(Context ctx, String clientId) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_WEB_CLIENT_ID, clientId).apply();
    }

    public static String getWebClientId(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_WEB_CLIENT_ID, "");
    }

    public static void saveDefaultCalendar(Context ctx, String calendarId) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_DEFAULT_CALENDAR, calendarId).apply();
    }

    public static String getDefaultCalendar(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_DEFAULT_CALENDAR, "primary");
    }

    public static boolean isSignedIn(Context ctx) {
        return GoogleSignIn.getLastSignedInAccount(ctx) != null;
    }

    public static GoogleSignInAccount getAccount(Context ctx) {
        return GoogleSignIn.getLastSignedInAccount(ctx);
    }

    public static Intent getSignInIntent(Context ctx) {
        String webClientId = getWebClientId(ctx);
        if (webClientId.isEmpty()) {
            return null;
        }
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestServerAuthCode(webClientId)
                .requestScopes(new Scope(CALENDAR_SCOPE), new Scope(YOUTUBE_SCOPE))
                .build();
        GoogleSignInClient client = GoogleSignIn.getClient(ctx, gso);
        return client.getSignInIntent();
    }

    public static void handleSignInResult(Intent data, SignInCallback callback) {
        try {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null) {
                String email = account.getEmail();
                String masked = (email != null && email.contains("@"))
                        ? email.substring(0, Math.min(3, email.indexOf("@"))) + "***" + email.substring(email.indexOf("@"))
                        : "***";
                AppLog.i("Auth", "Google登入成功: " + masked);
                callback.onResult(true, null);
            } else {
                AppLog.w("Auth", "Google登入失敗: null account");
                callback.onResult(false, "Sign-in returned null account");
            }
        } catch (ApiException e) {
            AppLog.e("Auth", "Google登入失敗: code " + e.getStatusCode());
            callback.onResult(false, "Sign-in failed: code " + e.getStatusCode());
        }
    }

    public static void getAccessToken(Context ctx, TokenCallback callback) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(ctx);
        if (account == null) {
            callback.onResult(null, "Not signed in");
            return;
        }
        String webClientId = getWebClientId(ctx);
        if (webClientId.isEmpty()) {
            callback.onResult(null, "Web Client ID not set");
            return;
        }

        // Silently refresh the token
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestServerAuthCode(webClientId)
                .requestScopes(new Scope(CALENDAR_SCOPE), new Scope(YOUTUBE_SCOPE))
                .build();
        GoogleSignInClient client = GoogleSignIn.getClient(ctx, gso);
        client.silentSignIn().addOnCompleteListener(task -> {
            try {
                GoogleSignInAccount refreshed = task.getResult(ApiException.class);
                String authCode = refreshed.getServerAuthCode();
                if (authCode != null) {
                    // Exchange auth code for access token
                    executor.execute(() -> {
                        String[] result = exchangeAuthCode(ctx, authCode);
                        mainHandler.post(() -> callback.onResult(result[0], result[1]));
                    });
                } else {
                    callback.onResult(null, "No auth code returned");
                }
            } catch (ApiException e) {
                callback.onResult(null, "Token refresh failed: code " + e.getStatusCode());
            }
        });
    }

    private static String[] exchangeAuthCode(Context ctx, String authCode) {
        try {
            String webClientId = getWebClientId(ctx);
            // Read client secret from prefs (set during setup)
            String clientSecret = SecurePrefs.get(ctx)
                    .getString(SecurePrefs.KEY_WEB_CLIENT_SECRET, "");

            java.net.URL url = new java.net.URL("https://oauth2.googleapis.com/token");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            String body = "code=" + java.net.URLEncoder.encode(authCode, "UTF-8")
                    + "&client_id=" + java.net.URLEncoder.encode(webClientId, "UTF-8")
                    + "&client_secret=" + java.net.URLEncoder.encode(clientSecret, "UTF-8")
                    + "&grant_type=authorization_code"
                    + "&redirect_uri=";

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            java.io.BufferedReader br;
            if (code >= 200 && code < 300) {
                br = new java.io.BufferedReader(new java.io.InputStreamReader(
                        conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            } else {
                br = new java.io.BufferedReader(new java.io.InputStreamReader(
                        conn.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8));
            }
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            conn.disconnect();

            if (code >= 200 && code < 300) {
                org.json.JSONObject json = new org.json.JSONObject(sb.toString());
                String accessToken = json.getString("access_token");
                // Cache token
                SecurePrefs.get(ctx).edit()
                        .putString(SecurePrefs.KEY_ACCESS_TOKEN, accessToken)
                        .putLong(SecurePrefs.KEY_TOKEN_EXPIRY, System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000)
                        .apply();
                AppLog.i("Auth", "Token交換成功");
                return new String[]{accessToken, null};
            } else {
                AppLog.e("Auth", "Token交換失敗: HTTP " + code);
                return new String[]{null, "Token exchange failed: HTTP " + code + " " + sb.toString()};
            }
        } catch (Exception e) {
            AppLog.e("Auth", "Token交換異常: " + e.getMessage());
            return new String[]{null, e.getClass().getSimpleName() + ": " + e.getMessage()};
        }
    }

    /**
     * Returns a cached token if valid, otherwise refreshes.
     * 60-second buffer: refresh early to avoid using a token that expires mid-request.
     */
    public static void getCachedOrFreshToken(Context ctx, TokenCallback callback) {
        SharedPreferences prefs = SecurePrefs.get(ctx);
        String cached = prefs.getString(SecurePrefs.KEY_ACCESS_TOKEN, "");
        long expiry = prefs.getLong(SecurePrefs.KEY_TOKEN_EXPIRY, 0);

        // Validate: non-empty, looks like a Google token, not expired (with 60s buffer)
        if (!cached.isEmpty() && expiry > 0
                && cached.startsWith("ya29.")
                && System.currentTimeMillis() < expiry - 60000) {
            callback.onResult(cached, null);
            return;
        }

        // Clear invalid cached token
        if (!cached.isEmpty() && (expiry == 0 || !cached.startsWith("ya29."))) {
            AppLog.w("Auth", "快取 token 無效，清除並重新取得");
            prefs.edit()
                    .remove(SecurePrefs.KEY_ACCESS_TOKEN)
                    .remove(SecurePrefs.KEY_TOKEN_EXPIRY)
                    .apply();
        }

        getAccessTokenWithRetry(ctx, callback, 0);
    }

    private static final int MAX_TOKEN_RETRIES = 2;
    private static final long[] RETRY_DELAYS_MS = {0, 2000, 5000};

    private static void getAccessTokenWithRetry(Context ctx, TokenCallback callback, int attempt) {
        getAccessToken(ctx, (token, error) -> {
            if (token != null) {
                callback.onResult(token, null);
            } else if (attempt < MAX_TOKEN_RETRIES) {
                long delay = RETRY_DELAYS_MS[attempt + 1];
                AppLog.w("Auth", "Token 取得失敗 (第" + (attempt + 1) + "次)，" + delay + "ms 後重試: " + error);
                mainHandler.postDelayed(() ->
                        getAccessTokenWithRetry(ctx, callback, attempt + 1), delay);
            } else {
                AppLog.e("Auth", "Token 取得失敗，已重試 " + MAX_TOKEN_RETRIES + " 次: " + error);
                callback.onResult(null, error);
            }
        });
    }

    public static void signOut(Context ctx, SignInCallback callback) {
        String webClientId = getWebClientId(ctx);
        if (webClientId.isEmpty()) {
            callback.onResult(false, "Web Client ID not set");
            return;
        }
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestServerAuthCode(webClientId)
                .requestScopes(new Scope(CALENDAR_SCOPE), new Scope(YOUTUBE_SCOPE))
                .build();
        GoogleSignIn.getClient(ctx, gso).signOut()
                .addOnCompleteListener(task -> {
                    SecurePrefs.get(ctx).edit()
                            .remove(SecurePrefs.KEY_ACCESS_TOKEN)
                            .remove(SecurePrefs.KEY_TOKEN_EXPIRY).apply();
                    AppLog.i("Auth", "已登出Google帳號");
                    callback.onResult(true, null);
                });
    }
}
