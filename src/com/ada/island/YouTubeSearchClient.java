package com.ada.island;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Searches YouTube's documented Data API; playback is handed to the official YouTube Music app. */
public final class YouTubeSearchClient {
    private static final String ENDPOINT = "https://www.googleapis.com/youtube/v3/search";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private YouTubeSearchClient() {}

    public interface Callback {
        void onResults(List<Track> tracks);
        void onError(String message);
    }

    public static final class Track {
        public final String videoId;
        public final String title;
        public final String channel;

        Track(String videoId, String title, String channel) {
            this.videoId = videoId;
            this.title = title;
            this.channel = channel;
        }

        public String label() {
            return title + "\n" + channel;
        }
    }

    public static void search(final String apiKey, final String query, final Callback callback) {
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    Uri uri = Uri.parse(ENDPOINT).buildUpon()
                            .appendQueryParameter("part", "snippet")
                            .appendQueryParameter("q", query)
                            .appendQueryParameter("type", "video")
                            .appendQueryParameter("maxResults", "10")
                            .appendQueryParameter("safeSearch", "strict")
                            .appendQueryParameter("key", apiKey)
                            .build();
                    connection = (HttpURLConnection) new URL(uri.toString()).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(12000);
                    connection.setReadTimeout(20000);
                    connection.setRequestProperty("Accept", "application/json");

                    int status = connection.getResponseCode();
                    InputStream stream = status >= 200 && status < 300
                            ? connection.getInputStream() : connection.getErrorStream();
                    String raw = readAll(stream);
                    if (status < 200 || status >= 300) {
                        throw new IOException(errorMessage(status, raw));
                    }

                    JSONObject response = new JSONObject(raw);
                    JSONArray items = response.optJSONArray("items");
                    List<Track> tracks = new ArrayList<>();
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.optJSONObject(i);
                            JSONObject id = item == null ? null : item.optJSONObject("id");
                            JSONObject snippet = item == null ? null : item.optJSONObject("snippet");
                            String videoId = id == null ? "" : id.optString("videoId", "");
                            if (videoId.isEmpty() || snippet == null) continue;
                            String title = snippet.optString("title", "YouTube parçası");
                            String channel = snippet.optString("channelTitle", "YouTube");
                            tracks.add(new Track(videoId, title, channel));
                        }
                    }
                    final List<Track> result = Collections.unmodifiableList(tracks);
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResults(result);
                        }
                    });
                } catch (final Exception error) {
                    final String message = error.getMessage() == null
                            ? "YouTube araması tamamlanamadı." : error.getMessage();
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(message);
                        }
                    });
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        }
        return text.toString();
    }

    private static String errorMessage(int status, String raw) {
        try {
            JSONObject error = new JSONObject(raw).optJSONObject("error");
            String message = error == null ? "" : error.optString("message", "");
            if (!message.isEmpty()) return "YouTube API " + status + ": " + message;
        } catch (Exception ignored) {
        }
        return "YouTube API isteği başarısız (" + status + "). Anahtar ve API kısıtlamalarını kontrol et.";
    }
}
