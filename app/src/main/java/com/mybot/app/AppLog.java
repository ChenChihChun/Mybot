package com.mybot.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppLog extends SQLiteOpenHelper {

    private static final String DB_NAME = "app_log.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "logs";
    private static final long MAX_AGE_MS = 10L * 24 * 60 * 60 * 1000; // 10 days

    private static AppLog instance;

    public static synchronized AppLog getInstance(Context ctx) {
        if (instance == null) {
            instance = new AppLog(ctx.getApplicationContext());
        }
        return instance;
    }

    private AppLog(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "timestamp INTEGER NOT NULL, "
                + "level TEXT NOT NULL, "
                + "tag TEXT NOT NULL, "
                + "message TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_timestamp ON " + TABLE + " (timestamp)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    // ── Static convenience methods (fire-and-forget) ──

    private static Context appCtx;

    public static void init(Context ctx) {
        appCtx = ctx.getApplicationContext();
        // Cleanup old logs on init
        getInstance(appCtx).cleanup();
    }

    public static void i(String tag, String message) {
        write("INFO", tag, message);
    }

    public static void w(String tag, String message) {
        write("WARN", tag, message);
    }

    public static void e(String tag, String message) {
        write("ERROR", tag, message);
    }

    private static void write(String level, String tag, String message) {
        if (appCtx == null) return;
        try {
            SQLiteDatabase db = getInstance(appCtx).getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("timestamp", System.currentTimeMillis());
            cv.put("level", level);
            cv.put("tag", tag);
            cv.put("message", message);
            db.insert(TABLE, null, cv);
        } catch (Exception ignored) {
            // Don't crash the app for logging failures
        }
    }

    // ── Query methods ──

    public static class LogEntry {
        public long id;
        public long timestamp;
        public String level;
        public String tag;
        public String message;

        public String format() {
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());
            return "[" + sdf.format(new Date(timestamp)) + "] "
                    + level + "/" + tag + ": " + message;
        }
    }

    public List<LogEntry> query(int limit) {
        List<LogEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, null, null, null, null,
                "timestamp DESC", String.valueOf(limit));
        while (c.moveToNext()) {
            LogEntry e = new LogEntry();
            e.id = c.getLong(c.getColumnIndexOrThrow("id"));
            e.timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"));
            e.level = c.getString(c.getColumnIndexOrThrow("level"));
            e.tag = c.getString(c.getColumnIndexOrThrow("tag"));
            e.message = c.getString(c.getColumnIndexOrThrow("message"));
            list.add(e);
        }
        c.close();
        return list;
    }

    public int count() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    public void cleanup() {
        try {
            long cutoff = System.currentTimeMillis() - MAX_AGE_MS;
            getWritableDatabase().delete(TABLE, "timestamp < ?",
                    new String[]{String.valueOf(cutoff)});
        } catch (Exception ignored) {
        }
    }

    public void clearAll() {
        try {
            getWritableDatabase().delete(TABLE, null, null);
        } catch (Exception ignored) {
        }
    }
}
