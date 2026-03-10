package com.mybot.app;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class FlightWatchDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "mybot_flight.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE = "flight_watches";
    public static final String COL_ID = "id";
    public static final String COL_ORIGIN = "origin";
    public static final String COL_DESTINATION = "destination";
    public static final String COL_DEPARTURE_DATE = "departure_date";
    public static final String COL_RETURN_DATE = "return_date";
    public static final String COL_SEARCH_MODE = "search_mode";
    public static final String COL_TARGET_PRICE = "target_price";
    public static final String COL_CURRENCY = "currency";
    public static final String COL_ENABLED = "enabled";
    public static final String COL_LAST_CHECKED = "last_checked";
    public static final String COL_LAST_LOWEST_PRICE = "last_lowest_price";
    public static final String COL_LAST_RESULT_JSON = "last_result_json";
    public static final String COL_CREATED_AT = "created_at";

    private static final String PREFS_NAME = "mybot_flight_settings";
    private static final String KEY_CHECK_ENABLED = "flight_check_enabled";

    public FlightWatchDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_ORIGIN + " TEXT NOT NULL, "
                + COL_DESTINATION + " TEXT NOT NULL, "
                + COL_DEPARTURE_DATE + " TEXT NOT NULL, "
                + COL_RETURN_DATE + " TEXT, "
                + COL_SEARCH_MODE + " TEXT NOT NULL DEFAULT 'date', "
                + COL_TARGET_PRICE + " REAL NOT NULL, "
                + COL_CURRENCY + " TEXT NOT NULL DEFAULT 'TWD', "
                + COL_ENABLED + " INTEGER NOT NULL DEFAULT 1, "
                + COL_LAST_CHECKED + " INTEGER DEFAULT 0, "
                + COL_LAST_LOWEST_PRICE + " REAL DEFAULT 0, "
                + COL_LAST_RESULT_JSON + " TEXT, "
                + COL_CREATED_AT + " INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public long insert(String origin, String destination, String departureDate,
                       String returnDate, String searchMode, double targetPrice, String currency) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_ORIGIN, origin);
        cv.put(COL_DESTINATION, destination);
        cv.put(COL_DEPARTURE_DATE, departureDate);
        cv.put(COL_RETURN_DATE, returnDate);
        cv.put(COL_SEARCH_MODE, searchMode);
        cv.put(COL_TARGET_PRICE, targetPrice);
        cv.put(COL_CURRENCY, currency);
        cv.put(COL_ENABLED, 1);
        cv.put(COL_CREATED_AT, System.currentTimeMillis());
        return db.insert(TABLE, null, cv);
    }

    public void delete(long id) {
        getWritableDatabase().delete(TABLE, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public List<FlightWatch> getAll() {
        List<FlightWatch> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, null, null, null, null, COL_CREATED_AT + " DESC");
        while (c.moveToNext()) {
            list.add(fromCursor(c));
        }
        c.close();
        return list;
    }

    public List<FlightWatch> getEnabled() {
        List<FlightWatch> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, COL_ENABLED + "=1", null, null, null, COL_CREATED_AT + " DESC");
        while (c.moveToNext()) {
            list.add(fromCursor(c));
        }
        c.close();
        return list;
    }

    public FlightWatch getById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, COL_ID + "=?", new String[]{String.valueOf(id)},
                null, null, null);
        FlightWatch result = null;
        if (c.moveToFirst()) {
            result = fromCursor(c);
        }
        c.close();
        return result;
    }

    public void updateCheckResult(long id, long checkedAt, double lowestPrice, String resultJson) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_LAST_CHECKED, checkedAt);
        cv.put(COL_LAST_LOWEST_PRICE, lowestPrice);
        cv.put(COL_LAST_RESULT_JSON, resultJson);
        db.update(TABLE, cv, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void setEnabled(long id, boolean enabled) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_ENABLED, enabled ? 1 : 0);
        db.update(TABLE, cv, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    private FlightWatch fromCursor(Cursor c) {
        FlightWatch fw = new FlightWatch();
        fw.id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
        fw.origin = c.getString(c.getColumnIndexOrThrow(COL_ORIGIN));
        fw.destination = c.getString(c.getColumnIndexOrThrow(COL_DESTINATION));
        fw.departureDate = c.getString(c.getColumnIndexOrThrow(COL_DEPARTURE_DATE));
        fw.returnDate = c.getString(c.getColumnIndexOrThrow(COL_RETURN_DATE));
        fw.searchMode = c.getString(c.getColumnIndexOrThrow(COL_SEARCH_MODE));
        fw.targetPrice = c.getDouble(c.getColumnIndexOrThrow(COL_TARGET_PRICE));
        fw.currency = c.getString(c.getColumnIndexOrThrow(COL_CURRENCY));
        fw.enabled = c.getInt(c.getColumnIndexOrThrow(COL_ENABLED)) == 1;
        fw.lastChecked = c.getLong(c.getColumnIndexOrThrow(COL_LAST_CHECKED));
        fw.lastLowestPrice = c.getDouble(c.getColumnIndexOrThrow(COL_LAST_LOWEST_PRICE));
        fw.lastResultJson = c.getString(c.getColumnIndexOrThrow(COL_LAST_RESULT_JSON));
        fw.createdAt = c.getLong(c.getColumnIndexOrThrow(COL_CREATED_AT));
        return fw;
    }

    // --- SharedPrefs helpers for global flight check toggle ---
    public static boolean isFlightCheckEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_CHECK_ENABLED, false);
    }

    public static void setFlightCheckEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_CHECK_ENABLED, enabled).apply();
    }

    public static class FlightWatch {
        public long id;
        public String origin;
        public String destination;
        public String departureDate;
        public String returnDate;
        public String searchMode;
        public double targetPrice;
        public String currency;
        public boolean enabled;
        public long lastChecked;
        public double lastLowestPrice;
        public String lastResultJson;
        public long createdAt;
    }
}
