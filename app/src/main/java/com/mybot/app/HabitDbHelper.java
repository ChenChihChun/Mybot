package com.mybot.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HabitDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "mybot_habit.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE_HABITS = "habits";
    public static final String TABLE_LOGS = "habit_logs";

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public HabitDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_HABITS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, "
                + "icon TEXT, "
                + "color INTEGER, "
                + "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_LOGS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "habit_id INTEGER NOT NULL, "
                + "date TEXT NOT NULL, "
                + "completed INTEGER DEFAULT 1, "
                + "UNIQUE(habit_id, date), "
                + "FOREIGN KEY(habit_id) REFERENCES " + TABLE_HABITS + "(id) ON DELETE CASCADE)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HABITS);
        onCreate(db);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    public long insertHabit(String name, String icon, int color) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("icon", icon);
        cv.put("color", color);
        cv.put("created_at", System.currentTimeMillis());
        return db.insert(TABLE_HABITS, null, cv);
    }

    public void updateHabit(long id, String name, String icon, int color) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("icon", icon);
        cv.put("color", color);
        db.update(TABLE_HABITS, cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteHabit(long id) {
        getWritableDatabase().delete(TABLE_HABITS, "id=?", new String[]{String.valueOf(id)});
    }

    public List<Habit> getAllHabits() {
        List<Habit> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_HABITS, null, null, null, null, null, "created_at ASC");
        while (c.moveToNext()) {
            Habit h = new Habit();
            h.id = c.getLong(c.getColumnIndexOrThrow("id"));
            h.name = c.getString(c.getColumnIndexOrThrow("name"));
            h.icon = c.getString(c.getColumnIndexOrThrow("icon"));
            h.color = c.getInt(c.getColumnIndexOrThrow("color"));
            h.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
            list.add(h);
        }
        c.close();
        return list;
    }

    public void toggleLog(long habitId, String date) {
        SQLiteDatabase db = getWritableDatabase();
        if (isCompleted(habitId, date)) {
            db.delete(TABLE_LOGS, "habit_id=? AND date=?",
                    new String[]{String.valueOf(habitId), date});
        } else {
            ContentValues cv = new ContentValues();
            cv.put("habit_id", habitId);
            cv.put("date", date);
            cv.put("completed", 1);
            db.insertWithOnConflict(TABLE_LOGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    public boolean isCompleted(long habitId, String date) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT 1 FROM " + TABLE_LOGS
                + " WHERE habit_id=? AND date=? AND completed=1",
                new String[]{String.valueOf(habitId), date});
        boolean result = c.moveToFirst();
        c.close();
        return result;
    }

    public Set<Long> getCompletedHabitIds(String date) {
        Set<Long> ids = new HashSet<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT habit_id FROM " + TABLE_LOGS
                + " WHERE date=? AND completed=1", new String[]{date});
        while (c.moveToNext()) {
            ids.add(c.getLong(0));
        }
        c.close();
        return ids;
    }

    public int getStreak(long habitId) {
        Calendar cal = Calendar.getInstance();
        int streak = 0;
        while (true) {
            String date = DATE_FORMAT.format(cal.getTime());
            if (isCompleted(habitId, date)) {
                streak++;
                cal.add(Calendar.DAY_OF_YEAR, -1);
            } else {
                break;
            }
        }
        return streak;
    }

    public int getLongestStreak(long habitId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT date FROM " + TABLE_LOGS
                + " WHERE habit_id=? AND completed=1 ORDER BY date ASC",
                new String[]{String.valueOf(habitId)});
        int longest = 0;
        int current = 0;
        Calendar prev = null;
        while (c.moveToNext()) {
            try {
                Date d = DATE_FORMAT.parse(c.getString(0));
                Calendar cal = Calendar.getInstance();
                cal.setTime(d);
                if (prev != null) {
                    long diffDays = (cal.getTimeInMillis() - prev.getTimeInMillis()) / (24 * 60 * 60 * 1000);
                    if (diffDays == 1) {
                        current++;
                    } else {
                        current = 1;
                    }
                } else {
                    current = 1;
                }
                if (current > longest) longest = current;
                prev = cal;
            } catch (Exception ignored) {}
        }
        c.close();
        return longest;
    }

    public float getCompletionRate(long habitId, int lastNDays) {
        Calendar cal = Calendar.getInstance();
        int completed = 0;
        for (int i = 0; i < lastNDays; i++) {
            if (isCompleted(habitId, DATE_FORMAT.format(cal.getTime()))) {
                completed++;
            }
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return lastNDays > 0 ? (float) completed / lastNDays : 0;
    }

    public Map<String, Boolean> getCompletionMap(long habitId, int year, int month) {
        Map<String, Boolean> map = new HashMap<>();
        String prefix = String.format(Locale.US, "%04d-%02d", year, month);
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT date FROM " + TABLE_LOGS
                + " WHERE habit_id=? AND date LIKE ? AND completed=1",
                new String[]{String.valueOf(habitId), prefix + "%"});
        while (c.moveToNext()) {
            map.put(c.getString(0), true);
        }
        c.close();
        return map;
    }

    public static String todayStr() {
        return DATE_FORMAT.format(new Date());
    }

    public static class Habit {
        public long id;
        public String name;
        public String icon;
        public int color;
        public long createdAt;
    }
}
