package com.mybot.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GoogleCalendarClient {

    private static final String BASE = "https://www.googleapis.com/calendar/v3";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static class CalendarInfo {
        public String id;
        public String summary;
        public String colorId;
        public boolean primary;

        public CalendarInfo(String id, String summary, String colorId, boolean primary) {
            this.id = id;
            this.summary = summary;
            this.colorId = colorId;
            this.primary = primary;
        }
    }

    public static class EventInfo {
        public String id;
        public String summary;
        public String description;
        public String location;
        public String startTime;
        public String endTime;
        public boolean allDay;
        public String calendarId;
        public String calendarName;
        public String status;

        public EventInfo() {}
    }

    public interface CalendarListCallback {
        void onResult(List<CalendarInfo> calendars, String error);
    }

    public interface EventListCallback {
        void onResult(List<EventInfo> events, String error);
    }

    public interface CreateEventCallback {
        void onResult(boolean success, String eventId, String error);
    }

    public static void listCalendars(String accessToken, CalendarListCallback callback) {
        executor.execute(() -> {
            try {
                String response = httpGet(BASE + "/users/me/calendarList", accessToken);
                JSONObject json = new JSONObject(response);
                JSONArray items = json.optJSONArray("items");
                List<CalendarInfo> list = new ArrayList<>();
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        if (!"reader".equals(item.optString("accessRole")) ||
                                "owner".equals(item.optString("accessRole")) ||
                                "writer".equals(item.optString("accessRole"))) {
                            list.add(new CalendarInfo(
                                    item.getString("id"),
                                    item.optString("summary", ""),
                                    item.optString("colorId", ""),
                                    item.optBoolean("primary", false)
                            ));
                        }
                    }
                }
                mainHandler.post(() -> callback.onResult(list, null));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    public static void listEvents(String accessToken, String calendarId,
                                   String timeMin, String timeMax, EventListCallback callback) {
        executor.execute(() -> {
            try {
                String url = BASE + "/calendars/" + URLEncoder.encode(calendarId, "UTF-8") + "/events"
                        + "?orderBy=startTime&singleEvents=true&maxResults=100"
                        + "&timeMin=" + URLEncoder.encode(timeMin, "UTF-8")
                        + "&timeMax=" + URLEncoder.encode(timeMax, "UTF-8");

                String response = httpGet(url, accessToken);
                JSONObject json = new JSONObject(response);
                JSONArray items = json.optJSONArray("items");
                List<EventInfo> list = new ArrayList<>();
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        EventInfo ev = new EventInfo();
                        ev.id = item.getString("id");
                        ev.summary = item.optString("summary", "(no title)");
                        ev.description = item.optString("description", "");
                        ev.location = item.optString("location", "");
                        ev.status = item.optString("status", "");
                        ev.calendarId = calendarId;

                        JSONObject start = item.optJSONObject("start");
                        JSONObject end = item.optJSONObject("end");
                        if (start != null) {
                            if (start.has("dateTime")) {
                                ev.startTime = start.getString("dateTime");
                                ev.allDay = false;
                            } else if (start.has("date")) {
                                ev.startTime = start.getString("date");
                                ev.allDay = true;
                            }
                        }
                        if (end != null) {
                            if (end.has("dateTime")) {
                                ev.endTime = end.getString("dateTime");
                            } else if (end.has("date")) {
                                ev.endTime = end.getString("date");
                            }
                        }

                        if (!"cancelled".equals(ev.status)) {
                            list.add(ev);
                        }
                    }
                }
                mainHandler.post(() -> callback.onResult(list, null));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    public static void listAllEvents(String accessToken, List<CalendarInfo> calendars,
                                      String timeMin, String timeMax, EventListCallback callback) {
        executor.execute(() -> {
            List<EventInfo> allEvents = new ArrayList<>();
            String firstError = null;

            for (CalendarInfo cal : calendars) {
                try {
                    String url = BASE + "/calendars/" + URLEncoder.encode(cal.id, "UTF-8") + "/events"
                            + "?orderBy=startTime&singleEvents=true&maxResults=50"
                            + "&timeMin=" + URLEncoder.encode(timeMin, "UTF-8")
                            + "&timeMax=" + URLEncoder.encode(timeMax, "UTF-8");

                    String response = httpGet(url, accessToken);
                    JSONObject json = new JSONObject(response);
                    JSONArray items = json.optJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            EventInfo ev = new EventInfo();
                            ev.id = item.getString("id");
                            ev.summary = item.optString("summary", "(no title)");
                            ev.description = item.optString("description", "");
                            ev.location = item.optString("location", "");
                            ev.status = item.optString("status", "");
                            ev.calendarId = cal.id;
                            ev.calendarName = cal.summary;

                            JSONObject start = item.optJSONObject("start");
                            JSONObject end = item.optJSONObject("end");
                            if (start != null) {
                                if (start.has("dateTime")) {
                                    ev.startTime = start.getString("dateTime");
                                    ev.allDay = false;
                                } else if (start.has("date")) {
                                    ev.startTime = start.getString("date");
                                    ev.allDay = true;
                                }
                            }
                            if (end != null) {
                                if (end.has("dateTime")) {
                                    ev.endTime = end.getString("dateTime");
                                } else if (end.has("date")) {
                                    ev.endTime = end.getString("date");
                                }
                            }

                            if (!"cancelled".equals(ev.status)) {
                                allEvents.add(ev);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (firstError == null) {
                        firstError = e.getClass().getSimpleName() + ": " + e.getMessage();
                    }
                }
            }

            // Sort by startTime
            allEvents.sort((a, b) -> {
                String sa = a.startTime != null ? a.startTime : "";
                String sb = b.startTime != null ? b.startTime : "";
                return sa.compareTo(sb);
            });

            List<EventInfo> finalEvents = allEvents;
            String finalError = firstError;
            mainHandler.post(() -> {
                if (finalEvents.isEmpty() && finalError != null) {
                    callback.onResult(null, finalError);
                } else {
                    callback.onResult(finalEvents, null);
                }
            });
        });
    }

    public static void createEvent(String accessToken, String calendarId,
                                    String summary, String description, String location,
                                    String startDateTime, String endDateTime,
                                    boolean allDay, CreateEventCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject event = new JSONObject();
                event.put("summary", summary);
                if (description != null && !description.isEmpty()) {
                    event.put("description", description);
                }
                if (location != null && !location.isEmpty()) {
                    event.put("location", location);
                }

                JSONObject start = new JSONObject();
                JSONObject end = new JSONObject();
                if (allDay) {
                    start.put("date", startDateTime);
                    end.put("date", endDateTime);
                } else {
                    start.put("dateTime", startDateTime);
                    start.put("timeZone", "Asia/Taipei");
                    end.put("dateTime", endDateTime);
                    end.put("timeZone", "Asia/Taipei");
                }
                event.put("start", start);
                event.put("end", end);

                String url = BASE + "/calendars/" + URLEncoder.encode(calendarId, "UTF-8") + "/events";
                String response = httpPost(url, accessToken, event.toString());
                JSONObject result = new JSONObject(response);

                if (result.has("id")) {
                    String eventId = result.getString("id");
                    mainHandler.post(() -> callback.onResult(true, eventId, null));
                } else {
                    String err = result.optString("error", "Unknown error");
                    mainHandler.post(() -> callback.onResult(false, null, err));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                mainHandler.post(() -> callback.onResult(false, null, err));
            }
        });
    }

    private static String httpGet(String urlStr, String accessToken) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        int code = conn.getResponseCode();
        BufferedReader br;
        if (code >= 200 && code < 300) {
            br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            br = new BufferedReader(new InputStreamReader(
                    conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream(),
                    StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        conn.disconnect();

        if (code < 200 || code >= 300) {
            throw new RuntimeException("HTTP " + code + ": " + sb.toString());
        }
        return sb.toString();
    }

    private static String httpPost(String urlStr, String accessToken, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        BufferedReader br;
        if (code >= 200 && code < 300) {
            br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            br = new BufferedReader(new InputStreamReader(
                    conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream(),
                    StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        conn.disconnect();

        if (code < 200 || code >= 300) {
            throw new RuntimeException("HTTP " + code + ": " + sb.toString());
        }
        return sb.toString();
    }
}
