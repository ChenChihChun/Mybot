package com.mybot.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ExpenseDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "mybot_expenses.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE = "expenses";
    public static final String COL_ID = "id";
    public static final String COL_AMOUNT = "amount";
    public static final String COL_CURRENCY = "currency";
    public static final String COL_CATEGORY = "category";
    public static final String COL_MERCHANT = "merchant";
    public static final String COL_DESCRIPTION = "description";
    public static final String COL_SOURCE = "source";
    public static final String COL_RAW_TEXT = "raw_text";
    public static final String COL_CREATED_AT = "created_at";

    public ExpenseDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_AMOUNT + " REAL NOT NULL, "
                + COL_CURRENCY + " TEXT DEFAULT 'TWD', "
                + COL_CATEGORY + " TEXT, "
                + COL_MERCHANT + " TEXT, "
                + COL_DESCRIPTION + " TEXT, "
                + COL_SOURCE + " TEXT, "
                + COL_RAW_TEXT + " TEXT, "
                + COL_CREATED_AT + " INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public long insert(double amount, String currency, String category, String merchant,
                       String description, String source, String rawText) {
        return insert(amount, currency, category, merchant, description, source, rawText, System.currentTimeMillis());
    }

    public long insert(double amount, String currency, String category, String merchant,
                       String description, String source, String rawText, long createdAt) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_AMOUNT, amount);
        cv.put(COL_CURRENCY, currency);
        cv.put(COL_CATEGORY, category);
        cv.put(COL_MERCHANT, merchant);
        cv.put(COL_DESCRIPTION, description);
        cv.put(COL_SOURCE, source);
        cv.put(COL_RAW_TEXT, rawText);
        cv.put(COL_CREATED_AT, createdAt);
        return db.insert(TABLE, null, cv);
    }

    public List<Expense> queryAll(String categoryFilter) {
        SQLiteDatabase db = getReadableDatabase();
        String selection = null;
        String[] selectionArgs = null;
        if (categoryFilter != null && !categoryFilter.isEmpty()) {
            selection = COL_CATEGORY + " = ?";
            selectionArgs = new String[]{categoryFilter};
        }
        Cursor cursor = db.query(TABLE, null, selection, selectionArgs,
                null, null, COL_CREATED_AT + " DESC");

        List<Expense> list = new ArrayList<>();
        while (cursor.moveToNext()) {
            Expense e = new Expense();
            e.id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
            e.amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_AMOUNT));
            e.currency = cursor.getString(cursor.getColumnIndexOrThrow(COL_CURRENCY));
            e.category = cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY));
            e.merchant = cursor.getString(cursor.getColumnIndexOrThrow(COL_MERCHANT));
            e.description = cursor.getString(cursor.getColumnIndexOrThrow(COL_DESCRIPTION));
            e.source = cursor.getString(cursor.getColumnIndexOrThrow(COL_SOURCE));
            e.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT));
            list.add(e);
        }
        cursor.close();
        return list;
    }

    public List<String> getCategories() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT DISTINCT " + COL_CATEGORY + " FROM " + TABLE
                        + " WHERE " + COL_CATEGORY + " IS NOT NULL AND " + COL_CATEGORY + " != ''"
                        + " ORDER BY " + COL_CATEGORY, null);
        List<String> cats = new ArrayList<>();
        while (cursor.moveToNext()) {
            cats.add(cursor.getString(0));
        }
        cursor.close();
        return cats;
    }

    public List<Expense> queryByDateRange(long startMs, long endMs) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE, null,
                COL_CREATED_AT + " >= ? AND " + COL_CREATED_AT + " < ?",
                new String[]{String.valueOf(startMs), String.valueOf(endMs)},
                null, null, COL_CREATED_AT + " DESC");

        List<Expense> list = new ArrayList<>();
        while (cursor.moveToNext()) {
            Expense e = new Expense();
            e.id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
            e.amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_AMOUNT));
            e.currency = cursor.getString(cursor.getColumnIndexOrThrow(COL_CURRENCY));
            e.category = cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY));
            e.merchant = cursor.getString(cursor.getColumnIndexOrThrow(COL_MERCHANT));
            e.description = cursor.getString(cursor.getColumnIndexOrThrow(COL_DESCRIPTION));
            e.source = cursor.getString(cursor.getColumnIndexOrThrow(COL_SOURCE));
            e.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT));
            list.add(e);
        }
        cursor.close();
        return list;
    }

    public List<CategorySum> sumByCategory(long startMs, long endMs) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COL_CATEGORY + ", SUM(" + COL_AMOUNT + "), COUNT(*) FROM " + TABLE
                        + " WHERE " + COL_CREATED_AT + " >= ? AND " + COL_CREATED_AT + " < ?"
                        + " GROUP BY " + COL_CATEGORY + " ORDER BY SUM(" + COL_AMOUNT + ") DESC",
                new String[]{String.valueOf(startMs), String.valueOf(endMs)});

        List<CategorySum> list = new ArrayList<>();
        while (cursor.moveToNext()) {
            CategorySum cs = new CategorySum();
            cs.category = cursor.getString(0);
            if (cs.category == null || cs.category.isEmpty()) cs.category = "未分類";
            cs.total = cursor.getDouble(1);
            cs.count = cursor.getInt(2);
            list.add(cs);
        }
        cursor.close();
        return list;
    }

    public double sumByDateRange(long startMs, long endMs) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COALESCE(SUM(" + COL_AMOUNT + "), 0) FROM " + TABLE
                        + " WHERE " + COL_CREATED_AT + " >= ? AND " + COL_CREATED_AT + " < ?",
                new String[]{String.valueOf(startMs), String.valueOf(endMs)});
        double sum = 0;
        if (cursor.moveToFirst()) sum = cursor.getDouble(0);
        cursor.close();
        return sum;
    }

    public int countByDateRange(long startMs, long endMs) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE
                        + " WHERE " + COL_CREATED_AT + " >= ? AND " + COL_CREATED_AT + " < ?",
                new String[]{String.valueOf(startMs), String.valueOf(endMs)});
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    public boolean hasExpenseToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();
        long endOfDay = startOfDay + 86400000L;
        return countByDateRange(startOfDay, endOfDay) > 0;
    }

    public static class CategorySum {
        public String category;
        public double total;
        public int count;
    }

    public static class Expense {
        public long id;
        public double amount;
        public String currency;
        public String category;
        public String merchant;
        public String description;
        public String source;
        public long createdAt;
    }
}
