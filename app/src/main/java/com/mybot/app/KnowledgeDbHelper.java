package com.mybot.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class KnowledgeDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "mybot_knowledge.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE = "knowledge";
    public static final String COL_ID = "id";
    public static final String COL_TITLE = "title";
    public static final String COL_SUMMARY = "summary";
    public static final String COL_KEY_POINTS = "key_points";
    public static final String COL_SOURCE_URL = "source_url";
    public static final String COL_CATEGORY = "category";
    public static final String COL_CREATED_AT = "created_at";

    public KnowledgeDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TITLE + " TEXT NOT NULL, "
                + COL_SUMMARY + " TEXT NOT NULL, "
                + COL_KEY_POINTS + " TEXT, "
                + COL_SOURCE_URL + " TEXT, "
                + COL_CATEGORY + " TEXT, "
                + COL_CREATED_AT + " INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public long insert(String title, String summary, String keyPoints, String sourceUrl, String category) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TITLE, title);
        cv.put(COL_SUMMARY, summary);
        cv.put(COL_KEY_POINTS, keyPoints);
        cv.put(COL_SOURCE_URL, sourceUrl);
        cv.put(COL_CATEGORY, category);
        cv.put(COL_CREATED_AT, System.currentTimeMillis());
        return db.insert(TABLE, null, cv);
    }

    public void delete(long id) {
        getWritableDatabase().delete(TABLE, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public List<Knowledge> getAll() {
        List<Knowledge> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, null, null, null, null, COL_CREATED_AT + " DESC");
        while (c.moveToNext()) {
            list.add(fromCursor(c));
        }
        c.close();
        return list;
    }

    public List<Knowledge> getByCategory(String category) {
        List<Knowledge> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, COL_CATEGORY + "=?", new String[]{category},
                null, null, COL_CREATED_AT + " DESC");
        while (c.moveToNext()) {
            list.add(fromCursor(c));
        }
        c.close();
        return list;
    }

    public List<Knowledge> search(String query) {
        List<Knowledge> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String like = "%" + query + "%";
        Cursor c = db.query(TABLE, null,
                COL_TITLE + " LIKE ? OR " + COL_SUMMARY + " LIKE ? OR " + COL_KEY_POINTS + " LIKE ?",
                new String[]{like, like, like},
                null, null, COL_CREATED_AT + " DESC");
        while (c.moveToNext()) {
            list.add(fromCursor(c));
        }
        c.close();
        return list;
    }

    public List<String> getAllCategories() {
        List<String> categories = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT DISTINCT " + COL_CATEGORY + " FROM " + TABLE
                + " WHERE " + COL_CATEGORY + " IS NOT NULL AND " + COL_CATEGORY + " != ''"
                + " ORDER BY " + COL_CATEGORY, null);
        while (c.moveToNext()) {
            categories.add(c.getString(0));
        }
        c.close();
        return categories;
    }

    public int getCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        return count;
    }

    private Knowledge fromCursor(Cursor c) {
        Knowledge k = new Knowledge();
        k.id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
        k.title = c.getString(c.getColumnIndexOrThrow(COL_TITLE));
        k.summary = c.getString(c.getColumnIndexOrThrow(COL_SUMMARY));
        k.keyPoints = c.getString(c.getColumnIndexOrThrow(COL_KEY_POINTS));
        k.sourceUrl = c.getString(c.getColumnIndexOrThrow(COL_SOURCE_URL));
        k.category = c.getString(c.getColumnIndexOrThrow(COL_CATEGORY));
        k.createdAt = c.getLong(c.getColumnIndexOrThrow(COL_CREATED_AT));
        return k;
    }

    public static class Knowledge {
        public long id;
        public String title;
        public String summary;
        public String keyPoints;
        public String sourceUrl;
        public String category;
        public long createdAt;
    }
}
