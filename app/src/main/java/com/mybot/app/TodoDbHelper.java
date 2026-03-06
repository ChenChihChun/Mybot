package com.mybot.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class TodoDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "mybot_todo.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE = "todos";
    public static final String COL_ID = "id";
    public static final String COL_TITLE = "title";
    public static final String COL_DESCRIPTION = "description";
    public static final String COL_PRIORITY = "priority"; // 0=low, 1=medium, 2=high
    public static final String COL_CATEGORY = "category";
    public static final String COL_DEADLINE = "deadline"; // nullable, millis
    public static final String COL_COMPLETED = "completed"; // 0 or 1
    public static final String COL_COMPLETED_AT = "completed_at"; // nullable, millis
    public static final String COL_CREATED_AT = "created_at";

    public TodoDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TITLE + " TEXT NOT NULL, "
                + COL_DESCRIPTION + " TEXT, "
                + COL_PRIORITY + " INTEGER DEFAULT 1, "
                + COL_CATEGORY + " TEXT, "
                + COL_DEADLINE + " INTEGER, "
                + COL_COMPLETED + " INTEGER DEFAULT 0, "
                + COL_COMPLETED_AT + " INTEGER, "
                + COL_CREATED_AT + " INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public long insert(String title, String description, int priority, String category, Long deadline) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TITLE, title);
        cv.put(COL_DESCRIPTION, description);
        cv.put(COL_PRIORITY, priority);
        cv.put(COL_CATEGORY, category);
        if (deadline != null) cv.put(COL_DEADLINE, deadline);
        cv.put(COL_COMPLETED, 0);
        cv.put(COL_CREATED_AT, System.currentTimeMillis());
        return db.insert(TABLE, null, cv);
    }

    public void update(long id, String title, String description, int priority, String category, Long deadline) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TITLE, title);
        cv.put(COL_DESCRIPTION, description);
        cv.put(COL_PRIORITY, priority);
        cv.put(COL_CATEGORY, category);
        if (deadline != null) {
            cv.put(COL_DEADLINE, deadline);
        } else {
            cv.putNull(COL_DEADLINE);
        }
        db.update(TABLE, cv, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public void markCompleted(long id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_COMPLETED, 1);
        cv.put(COL_COMPLETED_AT, System.currentTimeMillis());
        db.update(TABLE, cv, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public void markUncompleted(long id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_COMPLETED, 0);
        cv.putNull(COL_COMPLETED_AT);
        db.update(TABLE, cv, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public void delete(long id) {
        getWritableDatabase().delete(TABLE, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public List<Todo> queryPending(String categoryFilter, String search) {
        return query(false, categoryFilter, search, null, null);
    }

    public List<Todo> queryCompleted(String categoryFilter, String search, Long periodStart, Long periodEnd) {
        return query(true, categoryFilter, search, periodStart, periodEnd);
    }

    private List<Todo> query(boolean completed, String categoryFilter, String search,
                             Long periodStart, Long periodEnd) {
        SQLiteDatabase db = getReadableDatabase();
        StringBuilder where = new StringBuilder(COL_COMPLETED + " = ?");
        List<String> args = new ArrayList<>();
        args.add(completed ? "1" : "0");

        if (categoryFilter != null && !categoryFilter.isEmpty()) {
            where.append(" AND ").append(COL_CATEGORY).append(" = ?");
            args.add(categoryFilter);
        }
        if (search != null && !search.isEmpty()) {
            where.append(" AND (").append(COL_TITLE).append(" LIKE ? OR ")
                    .append(COL_DESCRIPTION).append(" LIKE ?)");
            args.add("%" + search + "%");
            args.add("%" + search + "%");
        }
        if (periodStart != null && periodEnd != null) {
            String dateCol = completed ? COL_COMPLETED_AT : COL_CREATED_AT;
            where.append(" AND ").append(dateCol).append(" >= ? AND ")
                    .append(dateCol).append(" < ?");
            args.add(String.valueOf(periodStart));
            args.add(String.valueOf(periodEnd));
        }

        String orderBy = completed
                ? COL_COMPLETED_AT + " DESC"
                : COL_PRIORITY + " DESC, " + COL_DEADLINE + " IS NULL, " + COL_DEADLINE + " ASC, " + COL_CREATED_AT + " DESC";

        Cursor cursor = db.query(TABLE, null, where.toString(),
                args.toArray(new String[0]), null, null, orderBy);

        List<Todo> list = new ArrayList<>();
        while (cursor.moveToNext()) {
            list.add(fromCursor(cursor));
        }
        cursor.close();
        return list;
    }

    public Todo getById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE, null, COL_ID + " = ?",
                new String[]{String.valueOf(id)}, null, null, null);
        Todo todo = null;
        if (cursor.moveToFirst()) {
            todo = fromCursor(cursor);
        }
        cursor.close();
        return todo;
    }

    public List<Todo> queryUpcoming(int withinDays) {
        SQLiteDatabase db = getReadableDatabase();
        long now = System.currentTimeMillis();
        long future = now + (long) withinDays * 86400000L;
        Cursor cursor = db.query(TABLE, null,
                COL_COMPLETED + " = 0 AND " + COL_DEADLINE + " IS NOT NULL AND "
                        + COL_DEADLINE + " <= ? AND " + COL_DEADLINE + " >= ?",
                new String[]{String.valueOf(future), String.valueOf(now)},
                null, null, COL_DEADLINE + " ASC");

        List<Todo> list = new ArrayList<>();
        while (cursor.moveToNext()) {
            list.add(fromCursor(cursor));
        }
        cursor.close();
        return list;
    }

    public List<Todo> queryOverdue() {
        SQLiteDatabase db = getReadableDatabase();
        long now = System.currentTimeMillis();
        Cursor cursor = db.query(TABLE, null,
                COL_COMPLETED + " = 0 AND " + COL_DEADLINE + " IS NOT NULL AND " + COL_DEADLINE + " < ?",
                new String[]{String.valueOf(now)},
                null, null, COL_DEADLINE + " ASC");

        List<Todo> list = new ArrayList<>();
        while (cursor.moveToNext()) {
            list.add(fromCursor(cursor));
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

    public int countPending() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE + " WHERE " + COL_COMPLETED + " = 0", null);
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    public int countCompleted() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE + " WHERE " + COL_COMPLETED + " = 1", null);
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    private Todo fromCursor(Cursor cursor) {
        Todo t = new Todo();
        t.id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
        t.title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE));
        t.description = cursor.getString(cursor.getColumnIndexOrThrow(COL_DESCRIPTION));
        t.priority = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PRIORITY));
        t.category = cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY));
        int deadlineIdx = cursor.getColumnIndexOrThrow(COL_DEADLINE);
        t.deadline = cursor.isNull(deadlineIdx) ? null : cursor.getLong(deadlineIdx);
        t.completed = cursor.getInt(cursor.getColumnIndexOrThrow(COL_COMPLETED)) == 1;
        int completedAtIdx = cursor.getColumnIndexOrThrow(COL_COMPLETED_AT);
        t.completedAt = cursor.isNull(completedAtIdx) ? null : cursor.getLong(completedAtIdx);
        t.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT));
        return t;
    }

    public static class Todo {
        public long id;
        public String title;
        public String description;
        public int priority; // 0=low, 1=medium, 2=high
        public String category;
        public Long deadline;
        public boolean completed;
        public Long completedAt;
        public long createdAt;

        public boolean isOverdue() {
            return !completed && deadline != null && deadline < System.currentTimeMillis();
        }

        public boolean isDueSoon(int days) {
            if (completed || deadline == null) return false;
            long now = System.currentTimeMillis();
            return deadline >= now && deadline <= now + (long) days * 86400000L;
        }
    }
}
