package com.ada.island;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal clients for Gemini's REST API and OpenAI-compatible chat endpoints. */
public class AiClient {

    public static class Msg {
        public final String role;
        public final String content;

        public Msg(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public interface Cb {
        void onOk(String text);

        void onErr(String message);
    }

    public static final String SYSTEM_PROMPT =
            "Sen Ada'sın: kullanıcının Android telefonunun üstünde, ekranın tepesindeki küçük 'dynamic island' içinde yaşayan bir yapay zekâsın. "
                    + "Kullanıcının o an hangi uygulamada olduğunu, ne kadar süredir orada kaldığını, saatini, bataryasını ve çalan müziği görebilirsin. "
                    + "Türkçe, kısa (en fazla 2-3 cümle), samimi ve zeki konuş. Gereksiz liste ve başlık kullanma. "
                    + "Kullanıcıya gerçekten faydalı, bağlama uygun tek bir somut öneri veya soru sor. Asla 'yapay zeka olarak' gibi kalıplar kullanma.";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler UI = new Handler(Looper.getMainLooper());

    public static void chat(final Context ctx, final List<Msg> history, final Cb cb) {
        final Prefs prefs = new Prefs(ctx);
        if (!prefs.hasKey()) {
            UI.post(new Runnable() {
                @Override
                public void run() {
                    cb.onErr("API anahtarı yok. Ayarlar'dan seçtiğin sağlayıcının anahtarını ekle.");
                }
            });
            return;
        }
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String text = request(prefs, history, 400);
                    final String out = text;
                    UI.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onOk(out);
                        }
                    });
                } catch (final Exception e) {
                    UI.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onErr(friendly(e));
                        }
                    });
                }
            }
        });
    }

    /** Asks Ada for three short contextual questions, one per line. */
    public static void suggestions(final Context ctx, final String contextBlock, final Cb cb) {
        List<Msg> h = new ArrayList<>();
        h.add(new Msg("system", SYSTEM_PROMPT));
        h.add(new Msg("user", "Bağlam:\n" + contextBlock
                + "\n\nBana şu an sorulabilecek 3 kısa soru üret. Her satıra tek soru yaz, başka hiçbir şey yazma, "
                + "madde işareti ve numara kullanma, her soru en fazla 70 karakter olsun."));
        chat(ctx, h, cb);
    }

    public static List<String> parseLines(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String raw : text.split("\n")) {
            String s = raw.trim();
            s = s.replaceFirst("^[-*•\\d.]+\\s*", "");
            if (s.length() > 3 && s.length() < 140) out.add(s);
            if (out.size() >= 5) break;
        }
        return out;
    }

    private static String request(Prefs prefs, List<Msg> history, int maxTokens) throws Exception {
        if (prefs.isGemini()) return requestGemini(prefs, history, maxTokens);
        JSONArray arr = new JSONArray();
        for (Msg m : history) {
            JSONObject o = new JSONObject();
            o.put("role", m.role);
            o.put("content", m.content);
            arr.put(o);
        }
        JSONObject body = new JSONObject();
        body.put("model", prefs.model());
        body.put("messages", arr);
        body.put("temperature", 0.75);
        body.put("max_tokens", maxTokens);

        URL url = new URL(prefs.baseUrl() + "/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Authorization", "Bearer " + prefs.apiKey());

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        OutputStream os = conn.getOutputStream();
        os.write(payload);
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
        }
        String raw = sb.toString();
        if (code < 200 || code >= 300) {
            String snippet = raw.length() > 220 ? raw.substring(0, 220) : raw;
            throw new IllegalStateException("HTTP " + code + " " + snippet);
        }
        JSONObject res = new JSONObject(raw);
        JSONArray choices = res.getJSONArray("choices");
        JSONObject msg = choices.getJSONObject(0).getJSONObject("message");
        String content = msg.optString("content", "").trim();
        if (content.isEmpty()) throw new IllegalStateException("Boş yanıt döndü.");
        return content;
    }

    private static String requestGemini(Prefs prefs, List<Msg> history, int maxTokens) throws Exception {
        JSONArray contents = new JSONArray();
        StringBuilder systemText = new StringBuilder();
        for (Msg m : history) {
            if ("system".equals(m.role)) {
                if (systemText.length() > 0) systemText.append('\n');
                systemText.append(m.content);
                continue;
            }
            JSONObject part = new JSONObject();
            part.put("text", m.content);
            JSONArray parts = new JSONArray();
            parts.put(part);
            JSONObject item = new JSONObject();
            item.put("role", "assistant".equals(m.role) ? "model" : "user");
            item.put("parts", parts);
            contents.put(item);
        }

        JSONObject body = new JSONObject();
        body.put("contents", contents);
        if (systemText.length() > 0) {
            JSONObject sysPart = new JSONObject();
            sysPart.put("text", systemText.toString());
            JSONArray sysParts = new JSONArray();
            sysParts.put(sysPart);
            JSONObject sys = new JSONObject();
            sys.put("parts", sysParts);
            body.put("systemInstruction", sys);
        }
        JSONObject generation = new JSONObject();
        generation.put("temperature", 0.75);
        generation.put("maxOutputTokens", maxTokens);
        body.put("generationConfig", generation);
        body.put("store", false);

        String model = prefs.model().trim();
        if (model.startsWith("models/")) model = model.substring("models/".length());
        URL url = new URL(prefs.baseUrl() + "/models/" + model + ":generateContent");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("x-goog-api-key", prefs.apiKey());

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        OutputStream os = conn.getOutputStream();
        os.write(payload);
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder response = new StringBuilder();
        if (is != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) response.append(line).append('\n');
            br.close();
        }
        String raw = response.toString();
        if (code < 200 || code >= 300) {
            String snippet = raw.length() > 220 ? raw.substring(0, 220) : raw;
            throw new IllegalStateException("HTTP " + code + " " + snippet);
        }
        JSONObject result = new JSONObject(raw);
        JSONArray candidates = result.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            JSONObject feedback = result.optJSONObject("promptFeedback");
            String reason = feedback == null ? "" : feedback.optString("blockReason", "");
            throw new IllegalStateException(reason.isEmpty() ? "Gemini boş yanıt döndürdü." : "Gemini yanıtı engelledi: " + reason);
        }
        JSONObject candidate = candidates.getJSONObject(0);
        JSONObject content = candidate.optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        StringBuilder answer = new StringBuilder();
        if (parts != null) {
            for (int i = 0; i < parts.length(); i++) {
                String text = parts.getJSONObject(i).optString("text", "");
                if (!text.isEmpty()) {
                    if (answer.length() > 0) answer.append('\n');
                    answer.append(text);
                }
            }
        }
        String text = answer.toString().trim();
        if (text.isEmpty()) throw new IllegalStateException("Gemini boş yanıt döndürdü.");
        return text;
    }

    private static String friendly(Exception e) {
        String m = e.getMessage() == null ? e.toString() : e.getMessage();
        if (m.contains("HTTP 401")) return "Anahtar reddedildi (401). Anahtarı ve sağlayıcıyı kontrol et.";
        if (m.contains("HTTP 403")) return "Erişim yok (403). Model veya bölge kısıtlı olabilir.";
        if (m.contains("HTTP 404")) return "Model ya da adres bulunamadı (404). Model adını kontrol et.";
        if (m.contains("HTTP 429")) return "Kota doldu (429). Biraz sonra tekrar dene.";
        if (m.contains("HTTP 400")) return "İstek reddedildi (400). Model adı geçersiz olabilir.";
        if (m.contains("timeout") || m.contains("timed out")) return "Zaman aşımı. İnterneti kontrol et.";
        if (m.contains("Unable to resolve host") || m.contains("UnknownHost")) return "İnternet yok gibi görünüyor.";
        return "Bağlantı hatası: " + (m.length() > 140 ? m.substring(0, 140) : m);
    }
}
