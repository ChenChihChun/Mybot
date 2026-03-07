package com.mybot.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class MusicDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "mybot_music.db";
    private static final int DB_VERSION = 1;

    public static class Category {
        public long id;
        public String name;
        public String icon;
        public int sortOrder;
        public long createdAt;

        public Category(long id, String name, String icon, int sortOrder, long createdAt) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.sortOrder = sortOrder;
            this.createdAt = createdAt;
        }
    }

    public static class Song {
        public long id;
        public String videoId;
        public String title;
        public String channelTitle;
        public String thumbnailUrl;
        public long categoryId;
        public String categoryName;
        public boolean isFavorite;
        public int sortOrder;
        public String playlistId;
        public String playlistItemId;
        public long createdAt;

        public Song() {}
    }

    public MusicDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE categories ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL UNIQUE,"
                + "icon TEXT,"
                + "sort_order INTEGER DEFAULT 0,"
                + "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE songs ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "video_id TEXT NOT NULL UNIQUE,"
                + "title TEXT NOT NULL,"
                + "channel_title TEXT,"
                + "thumbnail_url TEXT,"
                + "category_id INTEGER,"
                + "is_favorite INTEGER DEFAULT 0,"
                + "sort_order INTEGER DEFAULT 0,"
                + "playlist_id TEXT,"
                + "playlist_item_id TEXT,"
                + "created_at INTEGER NOT NULL,"
                + "FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE SET NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    // ── Category CRUD ──

    public long insertCategory(String name, String icon) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("icon", icon);
        cv.put("sort_order", 0);
        cv.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("categories", null, cv);
    }

    public void updateCategory(long id, String name, String icon) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("icon", icon);
        getWritableDatabase().update("categories", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteCategory(long id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.putNull("category_id");
        db.update("songs", cv, "category_id=?", new String[]{String.valueOf(id)});
        db.delete("categories", "id=?", new String[]{String.valueOf(id)});
    }

    public List<Category> getAllCategories() {
        List<Category> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, name, icon, sort_order, created_at FROM categories ORDER BY sort_order, name", null);
        while (c.moveToNext()) {
            list.add(new Category(c.getLong(0), c.getString(1), c.getString(2),
                    c.getInt(3), c.getLong(4)));
        }
        c.close();
        return list;
    }

    // ── Song CRUD ──

    public long insertOrUpdateSong(String videoId, String title, String channelTitle,
                                    String thumbnailUrl, String playlistId, String playlistItemId) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id FROM songs WHERE video_id=?", new String[]{videoId});
        if (c.moveToFirst()) {
            long id = c.getLong(0);
            c.close();
            ContentValues cv = new ContentValues();
            cv.put("title", title);
            cv.put("channel_title", channelTitle);
            cv.put("thumbnail_url", thumbnailUrl);
            if (playlistId != null) cv.put("playlist_id", playlistId);
            if (playlistItemId != null) cv.put("playlist_item_id", playlistItemId);
            db.update("songs", cv, "id=?", new String[]{String.valueOf(id)});
            return id;
        }
        c.close();
        ContentValues cv = new ContentValues();
        cv.put("video_id", videoId);
        cv.put("title", title);
        cv.put("channel_title", channelTitle);
        cv.put("thumbnail_url", thumbnailUrl);
        cv.put("playlist_id", playlistId);
        cv.put("playlist_item_id", playlistItemId);
        cv.put("created_at", System.currentTimeMillis());
        return db.insert("songs", null, cv);
    }

    public void deleteSong(long id) {
        getWritableDatabase().delete("songs", "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteSongByVideoId(String videoId) {
        getWritableDatabase().delete("songs", "video_id=?", new String[]{videoId});
    }

    public List<Song> getAllSongs() {
        return querySongs("SELECT s.*, c.name as cat_name FROM songs s LEFT JOIN categories c ON s.category_id=c.id ORDER BY s.sort_order, s.title", null);
    }

    public List<Song> getSongsByCategory(long categoryId) {
        return querySongs("SELECT s.*, c.name as cat_name FROM songs s LEFT JOIN categories c ON s.category_id=c.id WHERE s.category_id=? ORDER BY s.sort_order, s.title",
                new String[]{String.valueOf(categoryId)});
    }

    public List<Song> getUncategorizedSongs() {
        return querySongs("SELECT s.*, NULL as cat_name FROM songs s WHERE s.category_id IS NULL ORDER BY s.sort_order, s.title", null);
    }

    public List<Song> getFavorites() {
        return querySongs("SELECT s.*, c.name as cat_name FROM songs s LEFT JOIN categories c ON s.category_id=c.id WHERE s.is_favorite=1 ORDER BY s.sort_order, s.title", null);
    }

    public Song getSongByVideoId(String videoId) {
        List<Song> list = querySongs("SELECT s.*, c.name as cat_name FROM songs s LEFT JOIN categories c ON s.category_id=c.id WHERE s.video_id=?",
                new String[]{videoId});
        return list.isEmpty() ? null : list.get(0);
    }

    public void setSongCategory(long songId, long categoryId) {
        ContentValues cv = new ContentValues();
        if (categoryId <= 0) {
            cv.putNull("category_id");
        } else {
            cv.put("category_id", categoryId);
        }
        getWritableDatabase().update("songs", cv, "id=?", new String[]{String.valueOf(songId)});
    }

    public void toggleFavorite(long songId) {
        getWritableDatabase().execSQL(
                "UPDATE songs SET is_favorite = CASE WHEN is_favorite=1 THEN 0 ELSE 1 END WHERE id=?",
                new Object[]{songId});
    }

    public int getSongCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM songs", null);
        c.moveToFirst();
        int count = c.getInt(0);
        c.close();
        return count;
    }

    private List<Song> querySongs(String sql, String[] args) {
        List<Song> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        while (c.moveToNext()) {
            Song s = new Song();
            s.id = c.getLong(c.getColumnIndexOrThrow("id"));
            s.videoId = c.getString(c.getColumnIndexOrThrow("video_id"));
            s.title = c.getString(c.getColumnIndexOrThrow("title"));
            s.channelTitle = c.getString(c.getColumnIndexOrThrow("channel_title"));
            s.thumbnailUrl = c.getString(c.getColumnIndexOrThrow("thumbnail_url"));
            int catIdx = c.getColumnIndex("category_id");
            s.categoryId = (catIdx >= 0 && !c.isNull(catIdx)) ? c.getLong(catIdx) : 0;
            s.isFavorite = c.getInt(c.getColumnIndexOrThrow("is_favorite")) == 1;
            s.sortOrder = c.getInt(c.getColumnIndexOrThrow("sort_order"));
            s.playlistId = c.getString(c.getColumnIndexOrThrow("playlist_id"));
            s.playlistItemId = c.getString(c.getColumnIndexOrThrow("playlist_item_id"));
            s.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
            int catNameIdx = c.getColumnIndex("cat_name");
            s.categoryName = (catNameIdx >= 0 && !c.isNull(catNameIdx)) ? c.getString(catNameIdx) : null;
            list.add(s);
        }
        c.close();
        return list;
    }
}
