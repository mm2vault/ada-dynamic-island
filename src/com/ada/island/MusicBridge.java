package com.ada.island;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.provider.Settings;

import java.util.List;

/** Reads and controls whatever music app is currently playing, via MediaSession. */
public class MusicBridge {

    public static class Track {
        public String title = "";
        public String artist = "";
        public String pkg = "";
        public boolean playing = false;
        public MediaController ctrl = null;
    }

    public static boolean hasAccess(Context c) {
        try {
            String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
            return s != null && s.contains(c.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private static Track cached;
    private static long cachedAt = 0L;

    public static Track current(Context c) {
        long now = System.currentTimeMillis();
        if (now - cachedAt < 700) return cached;
        cachedAt = now;
        cached = read(c);
        return cached;
    }

    private static Track read(Context c) {
        if (!hasAccess(c)) return null;
        try {
            MediaSessionManager msm = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName cn = new ComponentName(c, MusicNotificationListener.class);
            List<MediaController> list = msm.getActiveSessions(cn);
            Track best = null;
            for (MediaController mc : list) {
                MediaMetadata md = mc.getMetadata();
                if (md == null) continue;
                String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                if (title == null || title.trim().isEmpty()) continue;
                Track t = new Track();
                t.title = title.trim();
                String artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null) artist = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                t.artist = artist == null ? "" : artist.trim();
                t.pkg = mc.getPackageName();
                PlaybackState ps = mc.getPlaybackState();
                t.playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
                t.ctrl = mc;
                if (t.playing) return t;
                if (best == null) best = t;
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    public static void toggle(MediaController ctrl) {
        if (ctrl == null) return;
        try {
            PlaybackState ps = ctrl.getPlaybackState();
            if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) {
                ctrl.getTransportControls().pause();
            } else {
                ctrl.getTransportControls().play();
            }
        } catch (Exception ignored) {
        }
    }

    public static void next(MediaController ctrl) {
        if (ctrl == null) return;
        try {
            ctrl.getTransportControls().skipToNext();
        } catch (Exception ignored) {
        }
    }

    public static void prev(MediaController ctrl) {
        if (ctrl == null) return;
        try {
            ctrl.getTransportControls().skipToPrevious();
        } catch (Exception ignored) {
        }
    }
}
