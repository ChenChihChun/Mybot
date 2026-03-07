package com.mybot.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CalendarCache {

    private static final long CALENDARS_TTL = 30 * 60 * 1000L;  // 30 min
    private static final long EVENTS_TTL = 5 * 60 * 1000L;      // 5 min

    // Calendar list cache
    private static List<GoogleCalendarClient.CalendarInfo> cachedCalendars;
    private static long calendarsTimestamp = 0;

    // Events cache keyed by "yyyy-MM" month string
    private static final Map<String, List<GoogleCalendarClient.EventInfo>> cachedEvents = new HashMap<>();
    private static final Map<String, Long> eventsTimestamps = new HashMap<>();

    // --- Calendar list ---

    public static boolean hasCalendars() {
        return cachedCalendars != null && !cachedCalendars.isEmpty();
    }

    public static boolean isCalendarsValid() {
        return hasCalendars() && (System.currentTimeMillis() - calendarsTimestamp) < CALENDARS_TTL;
    }

    public static List<GoogleCalendarClient.CalendarInfo> getCalendars() {
        return cachedCalendars;
    }

    public static void putCalendars(List<GoogleCalendarClient.CalendarInfo> calendars) {
        cachedCalendars = new ArrayList<>(calendars);
        calendarsTimestamp = System.currentTimeMillis();
    }

    // --- Events ---

    public static boolean hasEvents(String monthKey) {
        return cachedEvents.containsKey(monthKey);
    }

    public static boolean isEventsValid(String monthKey) {
        Long ts = eventsTimestamps.get(monthKey);
        return ts != null && (System.currentTimeMillis() - ts) < EVENTS_TTL;
    }

    public static List<GoogleCalendarClient.EventInfo> getEvents(String monthKey) {
        List<GoogleCalendarClient.EventInfo> list = cachedEvents.get(monthKey);
        return list != null ? list : new ArrayList<>();
    }

    public static void putEvents(String monthKey, List<GoogleCalendarClient.EventInfo> events) {
        cachedEvents.put(monthKey, new ArrayList<>(events));
        eventsTimestamps.put(monthKey, System.currentTimeMillis());
    }

    // --- Invalidation ---

    public static void invalidateMonth(String monthKey) {
        cachedEvents.remove(monthKey);
        eventsTimestamps.remove(monthKey);
    }

    public static void invalidateAllEvents() {
        cachedEvents.clear();
        eventsTimestamps.clear();
    }

    public static void clearAll() {
        cachedCalendars = null;
        calendarsTimestamp = 0;
        cachedEvents.clear();
        eventsTimestamps.clear();
    }

    // --- Utility ---

    public static String monthKey(java.util.Calendar cal) {
        return String.format(java.util.Locale.US, "%04d-%02d",
                cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1);
    }

    public static long getCalendarsAge() {
        if (calendarsTimestamp == 0) return -1;
        return System.currentTimeMillis() - calendarsTimestamp;
    }

    public static long getEventsAge(String monthKey) {
        Long ts = eventsTimestamps.get(monthKey);
        if (ts == null) return -1;
        return System.currentTimeMillis() - ts;
    }
}
