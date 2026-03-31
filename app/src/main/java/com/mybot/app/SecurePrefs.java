package com.mybot.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
    private static boolean encryptionAvailable = true;

    public static synchronized SharedPreferences get(Context ctx) {
        if (instance != null) return instance;

        // First attempt
        instance = tryCreateEncrypted(ctx);
        if (instance != null) {
            migrateIfNeeded(ctx);
            return instance;
        }

        // Second attempt: clear corrupted master key and retry
        AppLog.w("SecurePrefs", "首次初始化失敗，嘗試清除損壞的 key 後重建...");
        try {
            ctx.getApplicationContext().getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().clear().apply();
        } catch (Exception ignored) {}

        instance = tryCreateEncrypted(ctx);
        if (instance != null) {
            AppLog.i("SecurePrefs", "重建加密儲存成功");
            migrateIfNeeded(ctx);
            return instance;
        }

        // Final fallback: in-memory only — refuse to persist tokens in plaintext
        encryptionAvailable = false;
        AppLog.e("SecurePrefs", "加密儲存不可用 — OAuth token 僅保留在記憶體中，重啟後需重新登入");
        instance = new InMemorySharedPreferences();
        return instance;
    }

    private static SharedPreferences tryCreateEncrypted(Context ctx) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    SECURE_PREFS_NAME,
                    masterKeyAlias,
                    ctx.getApplicationContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            AppLog.e("SecurePrefs", "EncryptedSharedPreferences初始化失敗: " + e.getMessage());
            return null;
        }
    }

    public static boolean isEncryptionAvailable() {
        return encryptionAvailable;
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

    /**
     * In-memory SharedPreferences that never persists to disk.
     * Used as a safe fallback when encryption is unavailable.
     * Tokens will work for the current session but won't survive app restart.
     */
    private static class InMemorySharedPreferences implements SharedPreferences {
        private final Map<String, Object> store = new HashMap<>();

        @Override public Map<String, ?> getAll() { return new HashMap<>(store); }
        @Nullable @Override public String getString(String key, @Nullable String defValue) {
            Object v = store.get(key); return v instanceof String ? (String) v : defValue;
        }
        @Override public int getInt(String key, int defValue) {
            Object v = store.get(key); return v instanceof Integer ? (Integer) v : defValue;
        }
        @Override public long getLong(String key, long defValue) {
            Object v = store.get(key); return v instanceof Long ? (Long) v : defValue;
        }
        @Override public float getFloat(String key, float defValue) {
            Object v = store.get(key); return v instanceof Float ? (Float) v : defValue;
        }
        @Override public boolean getBoolean(String key, boolean defValue) {
            Object v = store.get(key); return v instanceof Boolean ? (Boolean) v : defValue;
        }
        @Nullable @Override public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
            Object v = store.get(key); return v instanceof Set ? (Set<String>) v : defValues;
        }
        @Override public boolean contains(String key) { return store.containsKey(key); }
        @Override public Editor edit() { return new InMemoryEditor(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}

        private class InMemoryEditor implements Editor {
            private final Map<String, Object> pending = new HashMap<>();
            private boolean clear = false;

            @Override public Editor putString(String key, @Nullable String value) { pending.put(key, value); return this; }
            @Override public Editor putStringSet(String key, @Nullable Set<String> values) { pending.put(key, values); return this; }
            @Override public Editor putInt(String key, int value) { pending.put(key, value); return this; }
            @Override public Editor putLong(String key, long value) { pending.put(key, value); return this; }
            @Override public Editor putFloat(String key, float value) { pending.put(key, value); return this; }
            @Override public Editor putBoolean(String key, boolean value) { pending.put(key, value); return this; }
            @Override public Editor remove(String key) { pending.put(key, null); return this; }
            @Override public Editor clear() { clear = true; return this; }
            @Override public boolean commit() { apply(); return true; }
            @Override public void apply() {
                if (clear) store.clear();
                for (Map.Entry<String, Object> e : pending.entrySet()) {
                    if (e.getValue() == null) store.remove(e.getKey());
                    else store.put(e.getKey(), e.getValue());
                }
                pending.clear();
            }
        }
    }
}
