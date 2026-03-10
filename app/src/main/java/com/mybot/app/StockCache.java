package com.mybot.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class StockCache extends SQLiteOpenHelper {

    private static final String DB_NAME = "stock_cache.db";
    private static final int DB_VERSION = 1;

    // Candle data table
    private static final String TABLE_CANDLES = "candles";
    private static final String COL_CODE = "code";
    private static final String COL_TIME = "time";
    private static final String COL_OPEN = "open";
    private static final String COL_HIGH = "high";
    private static final String COL_LOW = "low";
    private static final String COL_CLOSE = "close";
    private static final String COL_VOLUME = "volume";

    // Meta table: track fetch info per stock
    private static final String TABLE_META = "meta";
    private static final String COL_FETCH_TIME = "fetch_time";
    private static final String COL_MONTHS = "months";
    private static final String COL_ACCESS_TIME = "access_time";

    private static final int MAX_STOCKS = 20;
    private static final long MAX_CANDLE_AGE_MS = 90L * 24 * 60 * 60 * 1000; // 90 days old candles

    private static StockCache instance;

    public static synchronized StockCache getInstance(Context ctx) {
        if (instance == null) {
            instance = new StockCache(ctx.getApplicationContext());
        }
        return instance;
    }

    private StockCache(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_CANDLES + " ("
                + COL_CODE + " TEXT NOT NULL, "
                + COL_TIME + " INTEGER NOT NULL, "
                + COL_OPEN + " REAL, "
                + COL_HIGH + " REAL, "
                + COL_LOW + " REAL, "
                + COL_CLOSE + " REAL, "
                + COL_VOLUME + " INTEGER, "
                + "PRIMARY KEY (" + COL_CODE + ", " + COL_TIME + "))");

        db.execSQL("CREATE TABLE " + TABLE_META + " ("
                + COL_CODE + " TEXT PRIMARY KEY, "
                + COL_FETCH_TIME + " INTEGER, "
                + COL_MONTHS + " INTEGER, "
                + COL_ACCESS_TIME + " INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CANDLES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_META);
        onCreate(db);
    }

    public List<StockData.CandleBar> load(String code) {
        List<StockData.CandleBar> candles = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        // Update access time
        ContentValues meta = new ContentValues();
        meta.put(COL_ACCESS_TIME, System.currentTimeMillis());
        db.update(TABLE_META, meta, COL_CODE + "=?", new String[]{code});

        Cursor c = db.query(TABLE_CANDLES, null, COL_CODE + "=?", new String[]{code},
                null, null, COL_TIME + " ASC");
        while (c.moveToNext()) {
            candles.add(new StockData.CandleBar(
                    c.getLong(c.getColumnIndexOrThrow(COL_TIME)),
                    c.getDouble(c.getColumnIndexOrThrow(COL_OPEN)),
                    c.getDouble(c.getColumnIndexOrThrow(COL_HIGH)),
                    c.getDouble(c.getColumnIndexOrThrow(COL_LOW)),
                    c.getDouble(c.getColumnIndexOrThrow(COL_CLOSE)),
                    c.getLong(c.getColumnIndexOrThrow(COL_VOLUME))
            ));
        }
        c.close();
        return candles;
    }

    public void save(String code, List<StockData.CandleBar> candles, int months) {
        if (candles == null || candles.isEmpty()) return;

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // Clear old data for this stock
            db.delete(TABLE_CANDLES, COL_CODE + "=?", new String[]{code});

            // Insert new candles
            for (StockData.CandleBar bar : candles) {
                ContentValues cv = new ContentValues();
                cv.put(COL_CODE, code);
                cv.put(COL_TIME, bar.startTime);
                cv.put(COL_OPEN, bar.open);
                cv.put(COL_HIGH, bar.high);
                cv.put(COL_LOW, bar.low);
                cv.put(COL_CLOSE, bar.close);
                cv.put(COL_VOLUME, bar.volume);
                db.insert(TABLE_CANDLES, null, cv);
            }

            // Update meta
            long now = System.currentTimeMillis();
            ContentValues meta = new ContentValues();
            meta.put(COL_CODE, code);
            meta.put(COL_FETCH_TIME, now);
            meta.put(COL_MONTHS, months);
            meta.put(COL_ACCESS_TIME, now);
            db.insertWithOnConflict(TABLE_META, null, meta, SQLiteDatabase.CONFLICT_REPLACE);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        cleanup(db);
    }

    public boolean isFresh(String code, int requiredMonths) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_META, new String[]{COL_FETCH_TIME, COL_MONTHS},
                COL_CODE + "=?", new String[]{code}, null, null, null);

        if (!c.moveToFirst()) {
            c.close();
            return false;
        }

        long fetchTime = c.getLong(0);
        int savedMonths = c.getInt(1);
        c.close();

        if (savedMonths < requiredMonths) return false;

        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Taipei"));
        Calendar cached = Calendar.getInstance(TimeZone.getTimeZone("Asia/Taipei"));
        cached.setTimeInMillis(fetchTime);

        int nowDow = now.get(Calendar.DAY_OF_WEEK);
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        boolean isWeekday = nowDow >= Calendar.MONDAY && nowDow <= Calendar.FRIDAY;

        boolean sameDay = now.get(Calendar.YEAR) == cached.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == cached.get(Calendar.DAY_OF_YEAR);

        if (sameDay) {
            int cachedMinutes = cached.get(Calendar.HOUR_OF_DAY) * 60 + cached.get(Calendar.MINUTE);
            if (isWeekday && nowMinutes >= 9 * 60 && nowMinutes < 13 * 60 + 30) {
                // Market hours: fresh only if fetched after 9:00 today
                return cachedMinutes >= 9 * 60;
            }
            if (isWeekday && nowMinutes >= 13 * 60 + 30) {
                // After close: fresh only if fetched after 13:30 today (has closing data)
                return cachedMinutes >= 13 * 60 + 30;
            }
            return true; // same day, before market open or weekend
        }

        // Before market open: yesterday's cache is OK
        if (nowMinutes < 9 * 60) {
            Calendar yesterday = (Calendar) now.clone();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            if (cached.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR)
                    && cached.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)) {
                return true;
            }
        }

        // Weekend: Friday cache valid through Sunday
        if (nowDow == Calendar.SATURDAY || nowDow == Calendar.SUNDAY) {
            long diffMs = now.getTimeInMillis() - fetchTime;
            return diffMs < 3 * 24 * 60 * 60 * 1000L;
        }

        return false;
    }

    public void remove(String code) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_CANDLES, COL_CODE + "=?", new String[]{code});
        db.delete(TABLE_META, COL_CODE + "=?", new String[]{code});
    }

    private void cleanup(SQLiteDatabase db) {
        // 1. Delete candles older than 90 days
        long cutoff = System.currentTimeMillis() - MAX_CANDLE_AGE_MS;
        db.delete(TABLE_CANDLES, COL_TIME + "<?", new String[]{String.valueOf(cutoff)});

        // 2. If more than MAX_STOCKS, remove least recently accessed
        Cursor c = db.query(TABLE_META, new String[]{COL_CODE},
                null, null, null, null, COL_ACCESS_TIME + " DESC");
        int count = 0;
        List<String> toRemove = new ArrayList<>();
        while (c.moveToNext()) {
            count++;
            if (count > MAX_STOCKS) {
                toRemove.add(c.getString(0));
            }
        }
        c.close();

        for (String code : toRemove) {
            db.delete(TABLE_CANDLES, COL_CODE + "=?", new String[]{code});
            db.delete(TABLE_META, COL_CODE + "=?", new String[]{code});
        }
    }
}
