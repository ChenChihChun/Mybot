package com.mybot.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class FitnessDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "mybot_fitness.db";
    private static final int DB_VERSION = 1;

    // Profile table
    public static final String T_PROFILE = "fitness_profile";
    // Workout plan table
    public static final String T_PLAN = "workout_plans";
    // Workout log table (daily completion)
    public static final String T_LOG = "workout_logs";
    // Weight records table
    public static final String T_WEIGHT = "weight_records";

    public FitnessDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_PROFILE + " ("
                + "id INTEGER PRIMARY KEY, "
                + "height_cm REAL, "
                + "weight_kg REAL, "
                + "goal TEXT, "           // reduce_fat, build_muscle, flexibility, stamina, general
                + "level TEXT, "          // beginner, intermediate, advanced
                + "updated_at INTEGER)");

        db.execSQL("CREATE TABLE " + T_PLAN + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "week_label TEXT, "     // e.g. "2026-W10"
                + "day_of_week INTEGER, " // 1=Mon ~ 7=Sun
                + "day_label TEXT, "      // e.g. "週一"
                + "focus TEXT, "          // e.g. "上半身", "核心", "有氧"
                + "exercises_json TEXT, " // JSON array of exercises
                + "generated_at INTEGER)");

        db.execSQL("CREATE TABLE " + T_LOG + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "date_str TEXT, "       // yyyy-MM-dd
                + "plan_id INTEGER, "
                + "exercises_done INTEGER DEFAULT 0, "
                + "total_exercises INTEGER DEFAULT 0, "
                + "duration_min INTEGER DEFAULT 0, "
                + "notes TEXT, "
                + "logged_at INTEGER)");

        db.execSQL("CREATE TABLE " + T_WEIGHT + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "weight_kg REAL, "
                + "recorded_at INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + T_PROFILE);
        db.execSQL("DROP TABLE IF EXISTS " + T_PLAN);
        db.execSQL("DROP TABLE IF EXISTS " + T_LOG);
        db.execSQL("DROP TABLE IF EXISTS " + T_WEIGHT);
        onCreate(db);
    }

    // --- Profile ---
    public Profile getProfile() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_PROFILE, null, null, null, null, null, null);
        Profile p = null;
        if (c.moveToFirst()) {
            p = new Profile();
            p.heightCm = c.getDouble(c.getColumnIndexOrThrow("height_cm"));
            p.weightKg = c.getDouble(c.getColumnIndexOrThrow("weight_kg"));
            p.goal = c.getString(c.getColumnIndexOrThrow("goal"));
            p.level = c.getString(c.getColumnIndexOrThrow("level"));
            p.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        }
        c.close();
        return p;
    }

    public void saveProfile(double height, double weight, String goal, String level) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("id", 1);
        cv.put("height_cm", height);
        cv.put("weight_kg", weight);
        cv.put("goal", goal);
        cv.put("level", level);
        cv.put("updated_at", System.currentTimeMillis());
        db.insertWithOnConflict(T_PROFILE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);

        // Also record weight
        recordWeight(weight);
    }

    // --- Plans ---
    public void clearWeekPlan(String weekLabel) {
        getWritableDatabase().delete(T_PLAN, "week_label = ?", new String[]{weekLabel});
    }

    public long insertPlanDay(String weekLabel, int dayOfWeek, String dayLabel,
                              String focus, String exercisesJson) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("week_label", weekLabel);
        cv.put("day_of_week", dayOfWeek);
        cv.put("day_label", dayLabel);
        cv.put("focus", focus);
        cv.put("exercises_json", exercisesJson);
        cv.put("generated_at", System.currentTimeMillis());
        return db.insert(T_PLAN, null, cv);
    }

    public List<PlanDay> getWeekPlan(String weekLabel) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_PLAN, null, "week_label = ?", new String[]{weekLabel},
                null, null, "day_of_week ASC");
        List<PlanDay> list = new ArrayList<>();
        while (c.moveToNext()) {
            PlanDay pd = new PlanDay();
            pd.id = c.getLong(c.getColumnIndexOrThrow("id"));
            pd.weekLabel = c.getString(c.getColumnIndexOrThrow("week_label"));
            pd.dayOfWeek = c.getInt(c.getColumnIndexOrThrow("day_of_week"));
            pd.dayLabel = c.getString(c.getColumnIndexOrThrow("day_label"));
            pd.focus = c.getString(c.getColumnIndexOrThrow("focus"));
            pd.exercisesJson = c.getString(c.getColumnIndexOrThrow("exercises_json"));
            pd.generatedAt = c.getLong(c.getColumnIndexOrThrow("generated_at"));
            list.add(pd);
        }
        c.close();
        return list;
    }

    public PlanDay getTodayPlan() {
        String weekLabel = getCurrentWeekLabel();
        int dow = getTodayDow();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_PLAN, null, "week_label = ? AND day_of_week = ?",
                new String[]{weekLabel, String.valueOf(dow)}, null, null, null);
        PlanDay pd = null;
        if (c.moveToFirst()) {
            pd = new PlanDay();
            pd.id = c.getLong(c.getColumnIndexOrThrow("id"));
            pd.weekLabel = c.getString(c.getColumnIndexOrThrow("week_label"));
            pd.dayOfWeek = c.getInt(c.getColumnIndexOrThrow("day_of_week"));
            pd.dayLabel = c.getString(c.getColumnIndexOrThrow("day_label"));
            pd.focus = c.getString(c.getColumnIndexOrThrow("focus"));
            pd.exercisesJson = c.getString(c.getColumnIndexOrThrow("exercises_json"));
            pd.generatedAt = c.getLong(c.getColumnIndexOrThrow("generated_at"));
        }
        c.close();
        return pd;
    }

    // --- Logs ---
    public WorkoutLog getTodayLog() {
        return getLogByDate(getTodayStr());
    }

    public WorkoutLog getLogByDate(String dateStr) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_LOG, null, "date_str = ?", new String[]{dateStr},
                null, null, null);
        WorkoutLog log = null;
        if (c.moveToFirst()) {
            log = logFromCursor(c);
        }
        c.close();
        return log;
    }

    public void saveLog(String dateStr, long planId, int exercisesDone, int totalExercises,
                        int durationMin, String notes) {
        SQLiteDatabase db = getWritableDatabase();
        // Delete existing log for same date
        db.delete(T_LOG, "date_str = ?", new String[]{dateStr});
        ContentValues cv = new ContentValues();
        cv.put("date_str", dateStr);
        cv.put("plan_id", planId);
        cv.put("exercises_done", exercisesDone);
        cv.put("total_exercises", totalExercises);
        cv.put("duration_min", durationMin);
        cv.put("notes", notes);
        cv.put("logged_at", System.currentTimeMillis());
        db.insert(T_LOG, null, cv);
    }

    public List<WorkoutLog> getRecentLogs(int limit) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_LOG, null, null, null, null, null,
                "date_str DESC", String.valueOf(limit));
        List<WorkoutLog> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(logFromCursor(c));
        }
        c.close();
        return list;
    }

    public int getStreak() {
        Calendar cal = Calendar.getInstance();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        int streak = 0;
        for (int i = 0; i < 365; i++) {
            String dateStr = sdf.format(cal.getTime());
            WorkoutLog log = getLogByDate(dateStr);
            if (log != null && log.exercisesDone > 0) {
                streak++;
            } else if (i > 0) {
                break;
            } else {
                // Today not done yet, check yesterday
                cal.add(Calendar.DAY_OF_YEAR, -1);
                continue;
            }
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }

    public int getTotalWorkouts() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + T_LOG + " WHERE exercises_done > 0", null);
        int count = 0;
        if (c.moveToFirst()) count = c.getInt(0);
        c.close();
        return count;
    }

    // --- Weight ---
    public void recordWeight(double weightKg) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("weight_kg", weightKg);
        cv.put("recorded_at", System.currentTimeMillis());
        db.insert(T_WEIGHT, null, cv);
    }

    public List<WeightRecord> getWeightHistory(int limit) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_WEIGHT, null, null, null, null, null,
                "recorded_at DESC", String.valueOf(limit));
        List<WeightRecord> list = new ArrayList<>();
        while (c.moveToNext()) {
            WeightRecord wr = new WeightRecord();
            wr.weightKg = c.getDouble(c.getColumnIndexOrThrow("weight_kg"));
            wr.recordedAt = c.getLong(c.getColumnIndexOrThrow("recorded_at"));
            list.add(wr);
        }
        c.close();
        return list;
    }

    // --- Helpers ---
    public static String getCurrentWeekLabel() {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        int year = cal.get(Calendar.YEAR);
        int week = cal.get(Calendar.WEEK_OF_YEAR);
        return year + "-W" + String.format(java.util.Locale.getDefault(), "%02d", week);
    }

    public static int getTodayDow() {
        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        // Convert to Mon=1 ~ Sun=7
        return dow == Calendar.SUNDAY ? 7 : dow - 1;
    }

    public static String getTodayStr() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());
    }

    public static String getDowLabel(int dow) {
        String[] labels = {"", "週一", "週二", "週三", "週四", "週五", "週六", "週日"};
        return dow >= 1 && dow <= 7 ? labels[dow] : "";
    }

    private WorkoutLog logFromCursor(Cursor c) {
        WorkoutLog log = new WorkoutLog();
        log.id = c.getLong(c.getColumnIndexOrThrow("id"));
        log.dateStr = c.getString(c.getColumnIndexOrThrow("date_str"));
        log.planId = c.getLong(c.getColumnIndexOrThrow("plan_id"));
        log.exercisesDone = c.getInt(c.getColumnIndexOrThrow("exercises_done"));
        log.totalExercises = c.getInt(c.getColumnIndexOrThrow("total_exercises"));
        log.durationMin = c.getInt(c.getColumnIndexOrThrow("duration_min"));
        log.notes = c.getString(c.getColumnIndexOrThrow("notes"));
        log.loggedAt = c.getLong(c.getColumnIndexOrThrow("logged_at"));
        return log;
    }

    // --- Data classes ---
    public static class Profile {
        public double heightCm;
        public double weightKg;
        public String goal;
        public String level;
        public long updatedAt;

        public double getBmi() {
            if (heightCm <= 0) return 0;
            double m = heightCm / 100.0;
            return weightKg / (m * m);
        }

        public String getGoalLabel() {
            if (goal == null) return "一般健身";
            switch (goal) {
                case "reduce_fat": return "減脂瘦身";
                case "build_muscle": return "增肌塑形";
                case "flexibility": return "柔軟度提升";
                case "stamina": return "體能耐力";
                default: return "一般健身";
            }
        }

        public String getLevelLabel() {
            if (level == null) return "初學者";
            switch (level) {
                case "intermediate": return "中階";
                case "advanced": return "進階";
                default: return "初學者";
            }
        }
    }

    public static class PlanDay {
        public long id;
        public String weekLabel;
        public int dayOfWeek;
        public String dayLabel;
        public String focus;
        public String exercisesJson;
        public long generatedAt;
    }

    public static class WorkoutLog {
        public long id;
        public String dateStr;
        public long planId;
        public int exercisesDone;
        public int totalExercises;
        public int durationMin;
        public String notes;
        public long loggedAt;
    }

    public static class WeightRecord {
        public double weightKg;
        public long recordedAt;
    }
}
