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

public class YouTubeClient {

    private static final String BASE = "https://www.googleapis.com/youtube/v3";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static class PlaylistInfo {
        public String id;
        public String title;
        public String description;
        public int itemCount;
        public String thumbnailUrl;
        public String publishedAt;

        public PlaylistInfo(String id, String title, String description, int itemCount,
                            String thumbnailUrl, String publishedAt) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.itemCount = itemCount;
            this.thumbnailUrl = thumbnailUrl;
            this.publishedAt = publishedAt;
        }
    }

    public static class VideoInfo {
        public String id;
        public String videoId;
        public String title;
        public String channelTitle;
        public String thumbnailUrl;
        public String playlistItemId;
        public int position;

        public VideoInfo(String videoId, String title, String channelTitle,
                         String thumbnailUrl, String playlistItemId, int position) {
            this.videoId = videoId;
            this.title = title;
            this.channelTitle = channelTitle;
            this.thumbnailUrl = thumbnailUrl;
            this.playlistItemId = playlistItemId;
            this.position = position;
        }
    }

    public interface PlaylistListCallback {
        void onResult(List<PlaylistInfo> playlists, String error);
    }

    public interface VideoListCallback {
        void onResult(List<VideoInfo> videos, String error);
    }

    public interface MutateCallback {
        void onResult(boolean success, String error);
    }

    public static void listMyPlaylists(String accessToken, PlaylistListCallback callback) {
        executor.execute(() -> {
            try {
                String url = BASE + "/playlists?part=snippet,contentDetails&mine=true&maxResults=50";
                String response = httpGet(url, accessToken);
                JSONObject json = new JSONObject(response);
                JSONArray items = json.optJSONArray("items");
                List<PlaylistInfo> list = new ArrayList<>();
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        JSONObject snippet = item.getJSONObject("snippet");
                        JSONObject contentDetails = item.optJSONObject("contentDetails");
                        String thumbUrl = "";
                        JSONObject thumbs = snippet.optJSONObject("thumbnails");
                        if (thumbs != null) {
                            JSONObject def = thumbs.optJSONObject("medium");
                            if (def == null) def = thumbs.optJSONObject("default");
                            if (def != null) thumbUrl = def.optString("url", "");
                        }
                        list.add(new PlaylistInfo(
                                item.getString("id"),
                                snippet.optString("title", ""),
                                snippet.optString("description", ""),
                                contentDetails != null ? contentDetails.optInt("itemCount", 0) : 0,
                                thumbUrl,
                                snippet.optString("publishedAt", "")
                        ));
                    }
                }
                mainHandler.post(() -> callback.onResult(list, null));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    public static void listPlaylistItems(String accessToken, String playlistId, VideoListCallback callback) {
        executor.execute(() -> {
            try {
                List<VideoInfo> allVideos = new ArrayList<>();
                String pageToken = null;
                do {
                    String url = BASE + "/playlistItems?part=snippet&playlistId="
                            + URLEncoder.encode(playlistId, "UTF-8") + "&maxResults=50";
                    if (pageToken != null) {
                        url += "&pageToken=" + URLEncoder.encode(pageToken, "UTF-8");
                    }
                    String response = httpGet(url, accessToken);
                    JSONObject json = new JSONObject(response);
                    JSONArray items = json.optJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            JSONObject snippet = item.getJSONObject("snippet");
                            JSONObject resourceId = snippet.optJSONObject("resourceId");
                            if (resourceId == null) continue;
                            String videoId = resourceId.optString("videoId", "");
                            if (videoId.isEmpty()) continue;
                            String thumbUrl = "";
                            JSONObject thumbs = snippet.optJSONObject("thumbnails");
                            if (thumbs != null) {
                                JSONObject def = thumbs.optJSONObject("medium");
                                if (def == null) def = thumbs.optJSONObject("default");
                                if (def != null) thumbUrl = def.optString("url", "");
                            }
                            allVideos.add(new VideoInfo(
                                    videoId,
                                    snippet.optString("title", ""),
                                    snippet.optString("videoOwnerChannelTitle", ""),
                                    thumbUrl,
                                    item.getString("id"),
                                    snippet.optInt("position", i)
                            ));
                        }
                    }
                    pageToken = json.optString("nextPageToken", null);
                } while (pageToken != null);
                mainHandler.post(() -> callback.onResult(allVideos, null));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    public static void createPlaylist(String accessToken, String title, String description,
                                       String privacyStatus, MutateCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                JSONObject snippet = new JSONObject();
                snippet.put("title", title);
                snippet.put("description", description != null ? description : "");
                body.put("snippet", snippet);
                JSONObject status = new JSONObject();
                status.put("privacyStatus", privacyStatus != null ? privacyStatus : "private");
                body.put("status", status);

                String url = BASE + "/playlists?part=snippet,status";
                httpPost(url, accessToken, body.toString());
                mainHandler.post(() -> callback.onResult(true, null));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                mainHandler.post(() -> callback.onResult(false, err));
            }
        });
    }

    public static void addVideoToPlaylist(String accessToken, String playlistId, String videoId,
                                           MutateCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                JSONObject snippet = new JSONObject();
                snippet.put("playlistId", playlistId);
                JSONObject resourceId = new JSONObject();
                resourceId.put("kind", "youtube#video");
                resourceId.put("videoId", videoId);
                snippet.put("resourceId", resourceId);
                body.put("snippet", snippet);

                String url = BASE + "/playlistItems?part=snippet";
                httpPost(url, accessToken, body.toString());
                mainHandler.post(() -> callback.onResult(true, null));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                mainHandler.post(() -> callback.onResult(false, err));
            }
        });
    }

    public static void removeFromPlaylist(String accessToken, String playlistItemId,
                                           MutateCallback callback) {
        executor.execute(() -> {
            try {
                String url = BASE + "/playlistItems?id=" + URLEncoder.encode(playlistItemId, "UTF-8");
                httpDelete(url, accessToken);
                mainHandler.post(() -> callback.onResult(true, null));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                mainHandler.post(() -> callback.onResult(false, err));
            }
        });
    }

    public static void searchVideos(String accessToken, String query, VideoListCallback callback) {
        executor.execute(() -> {
            try {
                String url = BASE + "/search?part=snippet&q="
                        + URLEncoder.encode(query, "UTF-8") + "&type=video&maxResults=10";
                String response = httpGet(url, accessToken);
                JSONObject json = new JSONObject(response);
                JSONArray items = json.optJSONArray("items");
                List<VideoInfo> list = new ArrayList<>();
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        JSONObject snippet = item.getJSONObject("snippet");
                        JSONObject idObj = item.getJSONObject("id");
                        String videoId = idObj.optString("videoId", "");
                        if (videoId.isEmpty()) continue;
                        String thumbUrl = "";
                        JSONObject thumbs = snippet.optJSONObject("thumbnails");
                        if (thumbs != null) {
                            JSONObject def = thumbs.optJSONObject("medium");
                            if (def == null) def = thumbs.optJSONObject("default");
                            if (def != null) thumbUrl = def.optString("url", "");
                        }
                        list.add(new VideoInfo(
                                videoId,
                                snippet.optString("title", ""),
                                snippet.optString("channelTitle", ""),
                                thumbUrl,
                                null,
                                i
                        ));
                    }
                }
                mainHandler.post(() -> callback.onResult(list, null));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                mainHandler.post(() -> callback.onResult(null, err));
            }
        });
    }

    public static void getVideoInfo(String accessToken, String videoId, VideoListCallback callback) {
        executor.execute(() -> {
            try {
                String url = BASE + "/videos?part=snippet&id=" + URLEncoder.encode(videoId, "UTF-8");
                String response = httpGet(url, accessToken);
                JSONObject json = new JSONObject(response);
                JSONArray items = json.optJSONArray("items");
                List<VideoInfo> list = new ArrayList<>();
                if (items != null && items.length() > 0) {
                    JSONObject item = items.getJSONObject(0);
                    JSONObject snippet = item.getJSONObject("snippet");
                    String thumbUrl = "";
                    JSONObject thumbs = snippet.optJSONObject("thumbnails");
                    if (thumbs != null) {
                        JSONObject def = thumbs.optJSONObject("medium");
                        if (def == null) def = thumbs.optJSONObject("default");
                        if (def != null) thumbUrl = def.optString("url", "");
                    }
                    list.add(new VideoInfo(
                            videoId,
                            snippet.optString("title", ""),
                            snippet.optString("channelTitle", ""),
                            thumbUrl,
                            null,
                            0
                    ));
                }
                mainHandler.post(() -> callback.onResult(list, null));
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                mainHandler.post(() -> callback.onResult(null, err));
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

    private static void httpDelete(String urlStr, String accessToken) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        int code = conn.getResponseCode();
        conn.disconnect();

        if (code < 200 || code >= 300) {
            throw new RuntimeException("HTTP " + code);
        }
    }
}
