package com.mybot.app;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WaterDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "mybot_water.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE = "water_logs";

    private static final String PREFS_NAME = "mybot_water_settings";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.US);

    public WaterDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "amount_ml INTEGER NOT NULL, "
                + "date TEXT NOT NULL, "
                + "time TEXT NOT NULL, "
                + "created_at INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public long addLog(int amountMl) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        Date now = new Date();
        cv.put("amount_ml", amountMl);
        cv.put("date", DATE_FORMAT.format(now));
        cv.put("time", TIME_FORMAT.format(now));
        cv.put("created_at", System.currentTimeMillis());
        return db.insert(TABLE, null, cv);
    }

    public void deleteLog(long id) {
        getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
    }

    public List<WaterLog> getTodayLogs() {
        String today = DATE_FORMAT.format(new Date());
        List<WaterLog> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, "date=?", new String[]{today},
                null, null, "created_at DESC");
        while (c.moveToNext()) {
            WaterLog log = new WaterLog();
            log.id = c.getLong(c.getColumnIndexOrThrow("id"));
            log.amountMl = c.getInt(c.getColumnIndexOrThrow("amount_ml"));
            log.date = c.getString(c.getColumnIndexOrThrow("date"));
            log.time = c.getString(c.getColumnIndexOrThrow("time"));
            list.add(log);
        }
        c.close();
        return list;
    }

    public int getTodayTotal() {
        String today = DATE_FORMAT.format(new Date());
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount_ml), 0) FROM " + TABLE
                + " WHERE date=?", new String[]{today});
        int total = 0;
        if (c.moveToFirst()) total = c.getInt(0);
        c.close();
        return total;
    }

    public Map<String, Integer> getDailyTotals(int lastNDays) {
        Map<String, Integer> map = new LinkedHashMap<>();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -(lastNDays - 1));
        for (int i = 0; i < lastNDays; i++) {
            map.put(DATE_FORMAT.format(cal.getTime()), 0);
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        SQLiteDatabase db = getReadableDatabase();
        Calendar startCal = Calendar.getInstance();
        startCal.add(Calendar.DAY_OF_YEAR, -(lastNDays - 1));
        String startDate = DATE_FORMAT.format(startCal.getTime());

        Cursor c = db.rawQuery("SELECT date, SUM(amount_ml) FROM " + TABLE
                + " WHERE date >= ? GROUP BY date", new String[]{startDate});
        while (c.moveToNext()) {
            String date = c.getString(0);
            int total = c.getInt(1);
            if (map.containsKey(date)) {
                map.put(date, total);
            }
        }
        c.close();
        return map;
    }

    public int getWeekAverage() {
        Map<String, Integer> daily = getDailyTotals(7);
        int sum = 0;
        int days = 0;
        for (int val : daily.values()) {
            if (val > 0) {
                sum += val;
                days++;
            }
        }
        return days > 0 ? sum / days : 0;
    }

    // Settings helpers
    public static int getGoal(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt("goal_ml", 2000);
    }

    public static void setGoal(Context context, int goalMl) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt("goal_ml", goalMl).apply();
    }

    public static int getRemindInterval(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt("remind_interval", 120);
    }

    public static void setRemindInterval(Context context, int minutes) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt("remind_interval", minutes).apply();
    }

    public static int getRemindStartHour(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt("remind_start_hour", 8);
    }

    public static int getRemindEndHour(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt("remind_end_hour", 22);
    }

    public static void setRemindHours(Context context, int startHour, int endHour) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt("remind_start_hour", startHour)
                .putInt("remind_end_hour", endHour)
                .apply();
    }

    public static boolean isRemindEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean("remind_enabled", false);
    }

    public static void setRemindEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean("remind_enabled", enabled).apply();
    }

    public static class WaterLog {
        public long id;
        public int amountMl;
        public String date;
        public String time;
    }
}
