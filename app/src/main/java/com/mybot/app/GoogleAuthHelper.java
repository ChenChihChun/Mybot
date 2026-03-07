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
                callback.onResult(true, null);
            } else {
                callback.onResult(false, "Sign-in returned null account");
            }
        } catch (ApiException e) {
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
            String clientSecret = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString("web_client_secret", "");

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
                ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putString("access_token", accessToken)
                        .putLong("token_expiry", System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000)
                        .apply();
                return new String[]{accessToken, null};
            } else {
                return new String[]{null, "Token exchange failed: HTTP " + code + " " + sb.toString()};
            }
        } catch (Exception e) {
            return new String[]{null, e.getClass().getSimpleName() + ": " + e.getMessage()};
        }
    }

    public static void getCachedOrFreshToken(Context ctx, TokenCallback callback) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String cached = prefs.getString("access_token", "");
        long expiry = prefs.getLong("token_expiry", 0);

        if (!cached.isEmpty() && System.currentTimeMillis() < expiry - 60000) {
            callback.onResult(cached, null);
            return;
        }
        getAccessToken(ctx, callback);
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
                    ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                            .remove("access_token").remove("token_expiry").apply();
                    callback.onResult(true, null);
                });
    }
}
