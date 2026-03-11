package com.mybot.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class TravelDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "mybot_travel.db";
    private static final int DB_VERSION = 1;

    // ── trips table ──
    public static final String TABLE_TRIPS = "trips";
    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";
    public static final String COL_DESTINATION = "destination";
    public static final String COL_START_DATE = "start_date";
    public static final String COL_END_DATE = "end_date";
    public static final String COL_DAYS = "days";
    public static final String COL_PEOPLE = "people";
    public static final String COL_PREFERENCES = "preferences";
    public static final String COL_TRANSPORT_MODE = "transport_mode";
    public static final String COL_STATUS = "status";
    public static final String COL_ITINERARY_JSON = "itinerary_json";
    public static final String COL_ESTIMATED_BUDGET = "estimated_budget";
    public static final String COL_ACTUAL_BUDGET = "actual_budget";
    public static final String COL_NOTES = "notes";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";

    // ── trip_expenses table ──
    public static final String TABLE_EXPENSES = "trip_expenses";
    public static final String COL_TRIP_ID = "trip_id";
    public static final String COL_DAY_NUMBER = "day_number";
    public static final String COL_CATEGORY = "category";
    public static final String COL_DESCRIPTION = "description";
    public static final String COL_AMOUNT = "amount";

    // ── achievements table ──
    public static final String TABLE_ACHIEVEMENTS = "achievements";
    public static final String COL_ACH_ID = "id";
    public static final String COL_UNLOCKED_AT = "unlocked_at";
    public static final String COL_PROGRESS = "progress";

    // ── travel_stats table ──
    public static final String TABLE_STATS = "travel_stats";
    public static final String COL_REGION = "region";
    public static final String COL_COMPLETED_AT = "completed_at";
    public static final String COL_TOTAL_SPOTS = "total_spots";
    public static final String COL_TOTAL_COST = "total_cost";

    // Status constants
    public static final String STATUS_PLANNING = "planning";
    public static final String STATUS_ONGOING = "ongoing";
    public static final String STATUS_COMPLETED = "completed";

    private static TravelDbHelper instance;

    public static synchronized TravelDbHelper getInstance(Context ctx) {
        if (instance == null) {
            instance = new TravelDbHelper(ctx.getApplicationContext());
        }
        return instance;
    }

    private TravelDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_TRIPS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_NAME + " TEXT NOT NULL, "
                + COL_DESTINATION + " TEXT NOT NULL, "
                + COL_START_DATE + " TEXT NOT NULL, "
                + COL_END_DATE + " TEXT NOT NULL, "
                + COL_DAYS + " INTEGER NOT NULL, "
                + COL_PEOPLE + " INTEGER NOT NULL DEFAULT 1, "
                + COL_PREFERENCES + " TEXT, "
                + COL_TRANSPORT_MODE + " TEXT DEFAULT 'public', "
                + COL_STATUS + " TEXT DEFAULT 'planning', "
                + COL_ITINERARY_JSON + " TEXT, "
                + COL_ESTIMATED_BUDGET + " REAL DEFAULT 0, "
                + COL_ACTUAL_BUDGET + " REAL DEFAULT 0, "
                + COL_NOTES + " TEXT, "
                + COL_CREATED_AT + " INTEGER NOT NULL, "
                + COL_UPDATED_AT + " INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_EXPENSES + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TRIP_ID + " INTEGER NOT NULL, "
                + COL_DAY_NUMBER + " INTEGER, "
                + COL_CATEGORY + " TEXT NOT NULL, "
                + COL_DESCRIPTION + " TEXT, "
                + COL_AMOUNT + " REAL NOT NULL, "
                + COL_CREATED_AT + " INTEGER NOT NULL, "
                + "FOREIGN KEY (" + COL_TRIP_ID + ") REFERENCES " + TABLE_TRIPS + "(" + COL_ID + ") ON DELETE CASCADE)");

        db.execSQL("CREATE TABLE " + TABLE_ACHIEVEMENTS + " ("
                + COL_ACH_ID + " TEXT PRIMARY KEY, "
                + COL_UNLOCKED_AT + " INTEGER DEFAULT 0, "
                + COL_PROGRESS + " INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE " + TABLE_STATS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TRIP_ID + " INTEGER NOT NULL, "
                + COL_REGION + " TEXT NOT NULL, "
                + COL_COMPLETED_AT + " INTEGER NOT NULL, "
                + COL_TOTAL_SPOTS + " INTEGER DEFAULT 0, "
                + COL_TOTAL_COST + " REAL DEFAULT 0, "
                + "FOREIGN KEY (" + COL_TRIP_ID + ") REFERENCES " + TABLE_TRIPS + "(" + COL_ID + ") ON DELETE CASCADE)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future migrations
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        db.execSQL("PRAGMA foreign_keys=ON");
    }

    // ── Trip CRUD ──

    public long insertTrip(String name, String destination, String startDate, String endDate,
                           int days, int people, String preferences, String transportMode,
                           String itineraryJson, double estimatedBudget) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        long now = System.currentTimeMillis();
        cv.put(COL_NAME, name);
        cv.put(COL_DESTINATION, destination);
        cv.put(COL_START_DATE, startDate);
        cv.put(COL_END_DATE, endDate);
        cv.put(COL_DAYS, days);
        cv.put(COL_PEOPLE, people);
        cv.put(COL_PREFERENCES, preferences);
        cv.put(COL_TRANSPORT_MODE, transportMode);
        cv.put(COL_STATUS, STATUS_PLANNING);
        cv.put(COL_ITINERARY_JSON, itineraryJson);
        cv.put(COL_ESTIMATED_BUDGET, estimatedBudget);
        cv.put(COL_CREATED_AT, now);
        cv.put(COL_UPDATED_AT, now);
        return db.insert(TABLE_TRIPS, null, cv);
    }

    public void updateTripItinerary(long id, String itineraryJson, double estimatedBudget) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_ITINERARY_JSON, itineraryJson);
        cv.put(COL_ESTIMATED_BUDGET, estimatedBudget);
        cv.put(COL_UPDATED_AT, System.currentTimeMillis());
        db.update(TABLE_TRIPS, cv, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void updateTripStatus(long id, String status) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_STATUS, status);
        cv.put(COL_UPDATED_AT, System.currentTimeMillis());
        db.update(TABLE_TRIPS, cv, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void updateTripNotes(long id, String notes) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_NOTES, notes);
        cv.put(COL_UPDATED_AT, System.currentTimeMillis());
        db.update(TABLE_TRIPS, cv, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void deleteTrip(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_TRIPS, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public Cursor getTripById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_TRIPS, null, COL_ID + "=?", new String[]{String.valueOf(id)},
                null, null, null);
    }

    public Cursor getTripsByStatus(String status) {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_TRIPS, null, COL_STATUS + "=?", new String[]{status},
                null, null, COL_UPDATED_AT + " DESC");
    }

    public Cursor getAllTrips() {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_TRIPS, null, null, null, null, null, COL_UPDATED_AT + " DESC");
    }

    public int getTripCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_TRIPS, null);
        int count = 0;
        if (c.moveToFirst()) count = c.getInt(0);
        c.close();
        return count;
    }

    public int getCompletedTripCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_TRIPS
                + " WHERE " + COL_STATUS + "=?", new String[]{STATUS_COMPLETED});
        int count = 0;
        if (c.moveToFirst()) count = c.getInt(0);
        c.close();
        return count;
    }

    // ── Trip Expenses ──

    public long insertExpense(long tripId, int dayNumber, String category,
                              String description, double amount) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TRIP_ID, tripId);
        cv.put(COL_DAY_NUMBER, dayNumber);
        cv.put(COL_CATEGORY, category);
        cv.put(COL_DESCRIPTION, description);
        cv.put(COL_AMOUNT, amount);
        cv.put(COL_CREATED_AT, System.currentTimeMillis());
        long id = db.insert(TABLE_EXPENSES, null, cv);

        // Update trip actual budget
        updateActualBudget(tripId);
        return id;
    }

    public void deleteExpense(long expenseId, long tripId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_EXPENSES, COL_ID + "=?", new String[]{String.valueOf(expenseId)});
        updateActualBudget(tripId);
    }

    private void updateActualBudget(long tripId) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT COALESCE(SUM(" + COL_AMOUNT + "),0) FROM " + TABLE_EXPENSES
                + " WHERE " + COL_TRIP_ID + "=?", new String[]{String.valueOf(tripId)});
        double total = 0;
        if (c.moveToFirst()) total = c.getDouble(0);
        c.close();

        ContentValues cv = new ContentValues();
        cv.put(COL_ACTUAL_BUDGET, total);
        cv.put(COL_UPDATED_AT, System.currentTimeMillis());
        db.update(TABLE_TRIPS, cv, COL_ID + "=?", new String[]{String.valueOf(tripId)});
    }

    public Cursor getExpensesByTrip(long tripId) {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_EXPENSES, null, COL_TRIP_ID + "=?",
                new String[]{String.valueOf(tripId)}, null, null, COL_DAY_NUMBER + " ASC, " + COL_CREATED_AT + " ASC");
    }

    public double getExpenseSumByCategory(long tripId, String category) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COALESCE(SUM(" + COL_AMOUNT + "),0) FROM " + TABLE_EXPENSES
                + " WHERE " + COL_TRIP_ID + "=? AND " + COL_CATEGORY + "=?",
                new String[]{String.valueOf(tripId), category});
        double sum = 0;
        if (c.moveToFirst()) sum = c.getDouble(0);
        c.close();
        return sum;
    }

    // ── Achievements ──

    public boolean isAchievementUnlocked(String achId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_ACHIEVEMENTS, new String[]{COL_UNLOCKED_AT},
                COL_ACH_ID + "=?", new String[]{achId}, null, null, null);
        boolean unlocked = false;
        if (c.moveToFirst()) {
            unlocked = c.getLong(0) > 0;
        }
        c.close();
        return unlocked;
    }

    public void unlockAchievement(String achId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_ACH_ID, achId);
        cv.put(COL_UNLOCKED_AT, System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_ACHIEVEMENTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void updateAchievementProgress(String achId, int progress) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_ACH_ID, achId);
        cv.put(COL_PROGRESS, progress);
        db.insertWithOnConflict(TABLE_ACHIEVEMENTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public int getAchievementProgress(String achId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_ACHIEVEMENTS, new String[]{COL_PROGRESS},
                COL_ACH_ID + "=?", new String[]{achId}, null, null, null);
        int progress = 0;
        if (c.moveToFirst()) progress = c.getInt(0);
        c.close();
        return progress;
    }

    public int getUnlockedAchievementCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_ACHIEVEMENTS
                + " WHERE " + COL_UNLOCKED_AT + " > 0", null);
        int count = 0;
        if (c.moveToFirst()) count = c.getInt(0);
        c.close();
        return count;
    }

    // ── Travel Stats ──

    public void insertStat(long tripId, String region, int totalSpots, double totalCost) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TRIP_ID, tripId);
        cv.put(COL_REGION, region);
        cv.put(COL_COMPLETED_AT, System.currentTimeMillis());
        cv.put(COL_TOTAL_SPOTS, totalSpots);
        cv.put(COL_TOTAL_COST, totalCost);
        db.insert(TABLE_STATS, null, cv);
    }

    public List<String> getVisitedRegions() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT DISTINCT " + COL_REGION + " FROM " + TABLE_STATS, null);
        List<String> regions = new ArrayList<>();
        while (c.moveToNext()) {
            regions.add(c.getString(0));
        }
        c.close();
        return regions;
    }

    public int getCompletedTripCountByPreference(String preference) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_TRIPS
                + " WHERE " + COL_STATUS + "=? AND " + COL_PREFERENCES + " LIKE ?",
                new String[]{STATUS_COMPLETED, "%" + preference + "%"});
        int count = 0;
        if (c.moveToFirst()) count = c.getInt(0);
        c.close();
        return count;
    }
}
