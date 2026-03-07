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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class CountdownDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "mybot_countdown.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE = "countdowns";
    public static final String COL_ID = "id";
    public static final String COL_TITLE = "title";
    public static final String COL_TARGET_DATE = "target_date";
    public static final String COL_NOTE = "note";
    public static final String COL_ICON = "icon";
    public static final String COL_COLOR = "color";
    public static final String COL_NOTIFY = "notify";
    public static final String COL_CREATED_AT = "created_at";

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public CountdownDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TITLE + " TEXT NOT NULL, "
                + COL_TARGET_DATE + " TEXT NOT NULL, "
                + COL_NOTE + " TEXT, "
                + COL_ICON + " TEXT, "
                + COL_COLOR + " INTEGER, "
                + COL_NOTIFY + " INTEGER DEFAULT 0, "
                + COL_CREATED_AT + " INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public long insert(String title, String targetDate, String note, String icon, int color, boolean notify) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TITLE, title);
        cv.put(COL_TARGET_DATE, targetDate);
        cv.put(COL_NOTE, note);
        cv.put(COL_ICON, icon);
        cv.put(COL_COLOR, color);
        cv.put(COL_NOTIFY, notify ? 1 : 0);
        cv.put(COL_CREATED_AT, System.currentTimeMillis());
        return db.insert(TABLE, null, cv);
    }

    public void update(long id, String title, String targetDate, String note, String icon, int color, boolean notify) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TITLE, title);
        cv.put(COL_TARGET_DATE, targetDate);
        cv.put(COL_NOTE, note);
        cv.put(COL_ICON, icon);
        cv.put(COL_COLOR, color);
        cv.put(COL_NOTIFY, notify ? 1 : 0);
        db.update(TABLE, cv, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void delete(long id) {
        getWritableDatabase().delete(TABLE, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public List<Countdown> getAll() {
        List<Countdown> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, null, null, null, null, COL_TARGET_DATE + " ASC");
        while (c.moveToNext()) {
            list.add(fromCursor(c));
        }
        c.close();
        return list;
    }

    public Countdown getById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, COL_ID + "=?", new String[]{String.valueOf(id)},
                null, null, null);
        Countdown result = null;
        if (c.moveToFirst()) {
            result = fromCursor(c);
        }
        c.close();
        return result;
    }

    private Countdown fromCursor(Cursor c) {
        Countdown cd = new Countdown();
        cd.id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
        cd.title = c.getString(c.getColumnIndexOrThrow(COL_TITLE));
        cd.targetDate = c.getString(c.getColumnIndexOrThrow(COL_TARGET_DATE));
        cd.note = c.getString(c.getColumnIndexOrThrow(COL_NOTE));
        cd.icon = c.getString(c.getColumnIndexOrThrow(COL_ICON));
        cd.color = c.getInt(c.getColumnIndexOrThrow(COL_COLOR));
        cd.notify = c.getInt(c.getColumnIndexOrThrow(COL_NOTIFY)) == 1;
        cd.createdAt = c.getLong(c.getColumnIndexOrThrow(COL_CREATED_AT));
        return cd;
    }

    public static class Countdown {
        public long id;
        public String title;
        public String targetDate;
        public String note;
        public String icon;
        public int color;
        public boolean notify;
        public long createdAt;

        public long getDaysRemaining() {
            try {
                Date target = DATE_FORMAT.parse(targetDate);
                Calendar todayCal = Calendar.getInstance();
                todayCal.set(Calendar.HOUR_OF_DAY, 0);
                todayCal.set(Calendar.MINUTE, 0);
                todayCal.set(Calendar.SECOND, 0);
                todayCal.set(Calendar.MILLISECOND, 0);
                long diff = target.getTime() - todayCal.getTimeInMillis();
                return TimeUnit.MILLISECONDS.toDays(diff);
            } catch (Exception e) {
                return 0;
            }
        }

        public boolean isToday() {
            return getDaysRemaining() == 0;
        }
    }
}
