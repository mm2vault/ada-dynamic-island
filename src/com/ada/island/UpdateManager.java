package com.ada.island;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-confirmed updater. It checks a public feed, verifies the APK hash, then delegates installation to Android. */
public final class UpdateManager {

    private static final String FEED_URL = "https://raw.githubusercontent.com/mm2vault/ada-dynamic-island/main/update.json";
    private static final String RELEASE_PREFIX = "https://github.com/mm2vault/ada-dynamic-island/releases/download/";
    private static final int MAX_APK_BYTES = 80 * 1024 * 1024;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UpdateManager() {}

    public static final class UpdateInfo {
        public final int versionCode;
        public final String versionName;
        public final String packageName;
        public final String apkUrl;
        public final String sha256;
        public final String notes;

        UpdateInfo(int versionCode, String versionName, String packageName, String apkUrl, String sha256, String notes) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.packageName = packageName;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
            this.notes = notes;
        }
    }

    public interface CheckCallback {
        void onResult(UpdateInfo update, String error);
    }

    public interface DownloadCallback {
        void onResult(File apk, String error);
    }

    public interface InstallCallback {
        void onResult(String error);
    }

    public static File pendingFile(Context context) {
        return new File(context.getCacheDir(), "ada-update.apk");
    }

    public static void check(final Context context, final CheckCallback callback) {
        final Context app = context.getApplicationContext();
        IO.execute(new Runnable() {
            @Override
            public void run() {
                UpdateInfo info = null;
                String error = null;
                try {
                    info = fetchInfo(app);
                } catch (Exception e) {
                    error = friendly(e);
                }
                final UpdateInfo result = info;
                final String message = error;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(result, message);
                    }
                });
            }
        });
    }

    private static UpdateInfo fetchInfo(Context context) throws Exception {
        HttpURLConnection conn = openHttps(FEED_URL);
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        if (!"https".equalsIgnoreCase(conn.getURL().getProtocol())) throw new IllegalStateException("Güncelleme bağlantısı HTTPS dışına yönlendirildi.");
        if (code < 200 || code >= 300) throw new IllegalStateException("Güncelleme sunucusu HTTP " + code);
        String raw = readLimited(conn.getInputStream(), 128 * 1024);
        conn.disconnect();

        JSONObject json = new JSONObject(raw);
        String pkg = json.optString("packageName", "");
        if (!context.getPackageName().equals(pkg)) throw new IllegalStateException("Güncelleme paketi Ada ile eşleşmiyor.");
        int versionCode = json.optInt("versionCode", 0);
        String versionName = json.optString("versionName", "");
        String apkUrl = json.optString("apkUrl", "");
        String hash = json.optString("sha256", "").toLowerCase(Locale.US);
        String notes = json.optString("notes", "");
        if (versionCode < 1 || versionName.isEmpty()) throw new IllegalStateException("Sürüm bilgisi eksik.");
        if (!apkUrl.startsWith(RELEASE_PREFIX)) throw new IllegalStateException("APK adresi güvenilir HTTPS sürüm adresi değil.");
        if (!hash.matches("[0-9a-f]{64}")) throw new IllegalStateException("SHA-256 bilgisi geçersiz.");
        if (versionCode <= installedVersionCode(context)) return null;
        return new UpdateInfo(versionCode, versionName, pkg, apkUrl, hash, notes);
    }

    public static void download(final Context context, final UpdateInfo update, final DownloadCallback callback) {
        final Context app = context.getApplicationContext();
        IO.execute(new Runnable() {
            @Override
            public void run() {
                File result = null;
                String error = null;
                try {
                    result = downloadAndVerify(app, update);
                } catch (Exception e) {
                    error = friendly(e);
                }
                final File file = result;
                final String message = error;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(file, message);
                    }
                });
            }
        });
    }

    private static File downloadAndVerify(Context context, UpdateInfo update) throws Exception {
        if (update == null || !update.apkUrl.startsWith(RELEASE_PREFIX)) throw new IllegalArgumentException("Güvenilir APK adresi yok.");
        HttpURLConnection conn = openHttps(update.apkUrl);
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        int code = conn.getResponseCode();
        if (!"https".equalsIgnoreCase(conn.getURL().getProtocol())) throw new IllegalStateException("APK bağlantısı HTTPS dışına yönlendirildi.");
        if (code < 200 || code >= 300) throw new IllegalStateException("APK indirme HTTP " + code);
        long length = conn.getContentLengthLong();
        if (length > MAX_APK_BYTES) throw new IllegalStateException("APK beklenenden büyük.");

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        File target = pendingFile(context);
        long total = 0;
        byte[] buffer = new byte[16 * 1024];
        try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(target)) {
            int n;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > MAX_APK_BYTES) throw new IllegalStateException("APK boyutu sınırı aşıldı.");
                digest.update(buffer, 0, n);
                out.write(buffer, 0, n);
            }
            out.flush();
            out.getFD().sync();
        } finally {
            conn.disconnect();
        }
        String actual = hex(digest.digest());
        if (!actual.equalsIgnoreCase(update.sha256)) {
            target.delete();
            throw new IllegalStateException("İndirilen APK'nın SHA-256 doğrulaması başarısız.");
        }
        if (target.length() < 1024) {
            target.delete();
            throw new IllegalStateException("İndirilen APK boş veya eksik.");
        }
        PackageInfo apkInfo = context.getPackageManager().getPackageArchiveInfo(target.getAbsolutePath(), 0);
        if (apkInfo == null || !update.packageName.equals(apkInfo.packageName)) {
            target.delete();
            throw new IllegalStateException("APK'nın uygulama kimliği Ada ile eşleşmiyor.");
        }
        long apkVersion = Build.VERSION.SDK_INT >= 28 ? apkInfo.getLongVersionCode() : apkInfo.versionCode;
        if (apkVersion != update.versionCode) {
            target.delete();
            throw new IllegalStateException("APK sürümü yayın bilgisiyle eşleşmiyor.");
        }
        return target;
    }

    public static void install(final Context context, final File apk, final InstallCallback callback) {
        final Context app = context.getApplicationContext();
        IO.execute(new Runnable() {
            @Override
            public void run() {
                String error = null;
                try {
                    installSession(app, apk);
                } catch (Exception e) {
                    error = friendly(e);
                }
                final String message = error;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(message);
                    }
                });
            }
        });
    }

    private static void installSession(Context context, File apk) throws Exception {
        if (apk == null || !apk.isFile()) throw new IllegalStateException("Doğrulanmış APK bulunamadı.");
        if (Build.VERSION.SDK_INT >= 26 && !context.getPackageManager().canRequestPackageInstalls()) {
            throw new IllegalStateException("Android'de bu uygulamadan APK yükleme izni gerekli.");
        }
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        int sessionId = installer.createSession(params);
        boolean committed = false;
        try (PackageInstaller.Session session = installer.openSession(sessionId);
             FileInputStream in = new FileInputStream(apk);
             OutputStream out = session.openWrite("ada-update.apk", 0, apk.length())) {
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            session.fsync(out);
            Intent status = new Intent(context, UpdateInstallReceiver.class);
            status.setAction("com.ada.island.INSTALL_UPDATE_" + sessionId);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, status, flags);
            session.commit(pending.getIntentSender());
            committed = true;
        } finally {
            if (!committed) {
                try { installer.abandonSession(sessionId); } catch (Exception ignored) {}
            }
        }
        apk.delete();
    }

    private static long installedVersionCode(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        if (Build.VERSION.SDK_INT >= 28) return info.getLongVersionCode();
        return info.versionCode;
    }

    private static HttpURLConnection openHttps(String address) throws Exception {
        URL url = new URL(address);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IllegalArgumentException("Yalnız HTTPS bağlantısı kabul edilir.");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    private static String readLimited(InputStream in, int limit) throws Exception {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line);
                if (text.length() > limit) throw new IllegalStateException("Güncelleme bilgisi çok büyük.");
            }
        }
        return text.toString();
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b & 0xff));
        return out.toString();
    }

    private static String friendly(Exception e) {
        String m = e.getMessage() == null ? e.toString() : e.getMessage();
        if (m.contains("Unable to resolve host") || m.contains("UnknownHost")) return "İnternet bağlantısı yok gibi görünüyor.";
        if (m.contains("timed out") || m.contains("timeout")) return "Güncelleme bağlantısı zaman aşımına uğradı.";
        return m.length() > 180 ? m.substring(0, 180) : m;
    }
}
