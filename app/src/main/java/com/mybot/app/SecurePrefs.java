package com.mybot.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

/**
 * Encrypted SharedPreferences wrapper for sensitive data (OAuth tokens, secrets).
 * Auto-migrates from plaintext "calendar_prefs" on first access.
 */
public class SecurePrefs {

    private static final String SECURE_PREFS_NAME = "secure_prefs";
    private static final String LEGACY_PREFS_NAME = "calendar_prefs";
    private static final String KEY_MIGRATED = "migrated_from_legacy";

    // Keys for sensitive data
    public static final String KEY_WEB_CLIENT_SECRET = "web_client_secret";
    public static final String KEY_ACCESS_TOKEN = "access_token";
    public static final String KEY_TOKEN_EXPIRY = "token_expiry";

    private static SharedPreferences instance;

    public static synchronized SharedPreferences get(Context ctx) {
        if (instance != null) return instance;

        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            instance = EncryptedSharedPreferences.create(
                    SECURE_PREFS_NAME,
                    masterKeyAlias,
                    ctx.getApplicationContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            migrateIfNeeded(ctx);
        } catch (Exception e) {
            AppLog.e("SecurePrefs", "EncryptedSharedPreferences初始化失敗，使用一般模式: " + e.getMessage());
            instance = ctx.getApplicationContext()
                    .getSharedPreferences(SECURE_PREFS_NAME + "_fallback", Context.MODE_PRIVATE);
            migrateIfNeeded(ctx);
        }
        return instance;
    }

    private static void migrateIfNeeded(Context ctx) {
        if (instance.getBoolean(KEY_MIGRATED, false)) return;

        SharedPreferences legacy = ctx.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = instance.edit();

        // Migrate sensitive keys
        String secret = legacy.getString(KEY_WEB_CLIENT_SECRET, "");
        String token = legacy.getString(KEY_ACCESS_TOKEN, "");
        long expiry = legacy.getLong(KEY_TOKEN_EXPIRY, 0);

        if (!secret.isEmpty()) editor.putString(KEY_WEB_CLIENT_SECRET, secret);
        if (!token.isEmpty()) editor.putString(KEY_ACCESS_TOKEN, token);
        if (expiry > 0) editor.putLong(KEY_TOKEN_EXPIRY, expiry);
        editor.putBoolean(KEY_MIGRATED, true);
        editor.apply();

        // Remove sensitive data from legacy prefs
        legacy.edit()
                .remove(KEY_WEB_CLIENT_SECRET)
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_TOKEN_EXPIRY)
                .apply();

        AppLog.i("SecurePrefs", "已遷移敏感資料至加密儲存");
    }
}
