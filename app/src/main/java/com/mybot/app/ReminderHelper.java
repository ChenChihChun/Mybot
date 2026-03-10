package com.mybot.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import android.os.Build;

import java.util.Calendar;

public class ReminderHelper {

    /** Safely schedule an exact alarm, falling back to setAndAllowWhileIdle on SecurityException. */
    private static void safeSetExact(AlarmManager am, long triggerAtMillis, PendingIntent pi) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
                return;
            }
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        } catch (SecurityException e) {
            // Fallback: inexact but Doze-friendly
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        }
    }

    private static final String PREFS_NAME = "mybot_reminder";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_HOUR = "hour";
    private static final String KEY_MINUTE = "minute";
    private static final int REQUEST_CODE = 8888;

    public static void scheduleReminder(Context context, int hour, int minute) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_ENABLED, true)
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .apply();

        setAlarm(context, hour, minute);
    }

    public static void cancelReminder(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ENABLED, false).apply();

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = getPendingIntent(context);
        if (am != null) {
            am.cancel(pi);
        }
    }

    public static void restoreIfEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_ENABLED, false)) {
            int hour = prefs.getInt(KEY_HOUR, 21);
            int minute = prefs.getInt(KEY_MINUTE, 0);
            setAlarm(context, hour, minute);
        }
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    public static int getHour(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_HOUR, 21);
    }

    public static int getMinute(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_MINUTE, 0);
    }

    private static void setAlarm(Context context, int hour, int minute) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // If the time already passed today, schedule for tomorrow
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        PendingIntent pi = getPendingIntent(context);
        safeSetExact(am, cal.getTimeInMillis(), pi);
    }

    /** Reschedule daily reminder for the next day. Called from ReminderReceiver. */
    public static void scheduleNextDailyReminder(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_ENABLED, false)) {
            int hour = prefs.getInt(KEY_HOUR, 21);
            int minute = prefs.getInt(KEY_MINUTE, 0);
            setAlarm(context, hour, minute);
        }
    }

    private static PendingIntent getPendingIntent(Context context) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // --- Fitness reminder ---
    private static final String FITNESS_PREFS = "mybot_fitness_reminder";
    private static final int FITNESS_REQUEST_CODE = 8866;

    public static void scheduleFitnessReminder(Context context, int hour, int minute) {
        SharedPreferences prefs = context.getSharedPreferences(FITNESS_PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_ENABLED, true)
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .apply();

        setFitnessAlarm(context, hour, minute);
    }

    private static void setFitnessAlarm(Context context, int hour, int minute) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        Intent intent = new Intent(context, FitnessReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, FITNESS_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        safeSetExact(am, cal.getTimeInMillis(), pi);
    }

    /** Reschedule fitness reminder for the next day. Called from FitnessReminderReceiver. */
    public static void scheduleNextFitnessReminder(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(FITNESS_PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_ENABLED, false)) {
            setFitnessAlarm(context, prefs.getInt(KEY_HOUR, 18), prefs.getInt(KEY_MINUTE, 0));
        }
    }

    public static void cancelFitnessReminder(Context context) {
        context.getSharedPreferences(FITNESS_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, false).apply();
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, FitnessReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, FITNESS_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am != null) am.cancel(pi);
    }

    public static void restoreFitnessIfEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(FITNESS_PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_ENABLED, false)) {
            scheduleFitnessReminder(context,
                    prefs.getInt(KEY_HOUR, 18), prefs.getInt(KEY_MINUTE, 0));
        }
    }

    public static boolean isFitnessEnabled(Context context) {
        return context.getSharedPreferences(FITNESS_PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    public static int getFitnessHour(Context context) {
        return context.getSharedPreferences(FITNESS_PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_HOUR, 18);
    }

    public static int getFitnessMinute(Context context) {
        return context.getSharedPreferences(FITNESS_PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_MINUTE, 0);
    }

    // --- Water reminder ---
    private static final String WATER_PREFS = "mybot_water_settings";
    private static final int WATER_REQUEST_CODE = 8900;

    public static void scheduleWaterReminder(Context context) {
        WaterDbHelper.setRemindEnabled(context, true);
        scheduleNextWaterAlarm(context);
    }

    /** Schedule a single exact alarm for the next water reminder. */
    public static void scheduleNextWaterAlarm(Context context) {
        int intervalMin = WaterDbHelper.getRemindInterval(context);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, WaterReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, WATER_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long intervalMs = intervalMin * 60 * 1000L;
        long triggerAt = System.currentTimeMillis() + intervalMs;

        safeSetExact(am, triggerAt, pi);
    }

    public static void cancelWaterReminder(Context context) {
        WaterDbHelper.setRemindEnabled(context, false);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, WaterReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, WATER_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am != null) am.cancel(pi);
    }

    public static void restoreWaterIfEnabled(Context context) {
        if (WaterDbHelper.isRemindEnabled(context)) {
            scheduleWaterReminder(context);
        }
    }

    // --- Flight price check (every 6 hours) ---
    private static final int FLIGHT_REQUEST_CODE = 9100;
    private static final long FLIGHT_INTERVAL_MS = 6 * 60 * 60 * 1000L; // 6 hours

    public static void scheduleFlightCheck(Context context) {
        FlightWatchDbHelper.setFlightCheckEnabled(context, true);
        scheduleNextFlightCheck(context);
    }

    public static void scheduleNextFlightCheck(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, FlightCheckReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, FLIGHT_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerAt = System.currentTimeMillis() + FLIGHT_INTERVAL_MS;
        safeSetExact(am, triggerAt, pi);
    }

    public static void cancelFlightCheck(Context context) {
        FlightWatchDbHelper.setFlightCheckEnabled(context, false);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, FlightCheckReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, FLIGHT_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am != null) am.cancel(pi);
    }

    public static void restoreFlightIfEnabled(Context context) {
        if (FlightWatchDbHelper.isFlightCheckEnabled(context)) {
            scheduleNextFlightCheck(context);
        }
    }

    public static long getNextFlightCheckTime() {
        return System.currentTimeMillis() + FLIGHT_INTERVAL_MS;
    }

    // --- TODO daily check (runs at 09:00 daily) ---
    private static final int TODO_REQUEST_CODE = 8877;

    public static void scheduleTodoCheck(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        Intent intent = new Intent(context, TodoReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, TODO_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        safeSetExact(am, cal.getTimeInMillis(), pi);
    }
}
