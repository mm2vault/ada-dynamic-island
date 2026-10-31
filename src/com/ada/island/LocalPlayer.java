package com.ada.island;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/** Built-in ambience plus user-selected local audio documents. */
public class LocalPlayer {

    public interface Listener {
        void onPlayerChanged();
    }

    public static final String[] TITLES = {"Gece Yolculuğu", "Derin Odak", "Yağmurlu Pencere"};
    private static final String[] FILES = {"gece.mp3", "odak.mp3", "yagmur.mp3"};

    private static LocalPlayer instance;

    public static synchronized LocalPlayer get(Context c) {
        if (instance == null) instance = new LocalPlayer(c.getApplicationContext());
        return instance;
    }

    private final Context ctx;
    private MediaPlayer mp;
    private int index = -1;
    private ArrayList<Uri> selectedUris;
    private ArrayList<String> selectedTitles;
    private int selectedPosition;
    private Listener listener;

    private LocalPlayer(Context c) {
        ctx = c;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public boolean isPlaying() {
        try {
            return mp != null && mp.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasSelection() {
        return mp != null && (selectedUris != null || index >= 0);
    }

    public boolean isUserTrack() {
        return selectedUris != null;
    }

    public int index() {
        return index;
    }

    public String title() {
        if (selectedUris != null && selectedPosition >= 0 && selectedPosition < selectedTitles.size()) {
            return selectedTitles.get(selectedPosition);
        }
        return index >= 0 && index < TITLES.length ? TITLES[index] : "Ada Radyo";
    }

    public String subtitle() {
        if (selectedUris != null) {
            return "telefonundan · " + (selectedPosition + 1) + "/" + selectedUris.size();
        }
        return "ada radyo · yerleşik çalar";
    }

    private void notifyChanged() {
        if (listener != null) listener.onPlayerChanged();
    }

    public void play(int i) {
        if (i < 0 || i >= FILES.length) return;
        stopInternal();
        clearUserSelection();
        index = i;
        try {
            MediaPlayer player = createPlayer();
            AssetFileDescriptor afd = ctx.getAssets().openFd(FILES[i]);
            try {
                player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            } finally {
                afd.close();
            }
            player.setLooping(true);
            prepare(player);
        } catch (Exception e) {
            clearAfterFailure();
        }
        notifyChanged();
    }

    public void playUri(Uri uri, String title) {
        ArrayList<Uri> uris = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        if (uri == null) return;
        uris.add(uri);
        titles.add(title == null || title.trim().isEmpty() ? "Telefondaki müzik" : title);
        playUris(uris, titles);
    }

    public void playUris(List<Uri> uris, List<String> titles) {
        if (uris == null || uris.isEmpty()) return;
        stopInternal();
        index = -1;
        selectedUris = new ArrayList<>(uris);
        selectedTitles = new ArrayList<>();
        for (int i = 0; i < selectedUris.size(); i++) {
            String name = titles != null && i < titles.size() ? titles.get(i) : "Telefondaki müzik";
            selectedTitles.add(name == null || name.trim().isEmpty() ? "Telefondaki müzik" : name);
        }
        selectedPosition = 0;
        startSelectedUri();
    }

    private void startSelectedUri() {
        if (selectedUris == null || selectedPosition < 0 || selectedPosition >= selectedUris.size()) return;
        try {
            MediaPlayer player = createPlayer();
            player.setDataSource(ctx, selectedUris.get(selectedPosition));
            player.setLooping(false);
            prepare(player);
        } catch (Exception e) {
            clearAfterFailure();
        }
        notifyChanged();
    }

    private MediaPlayer createPlayer() {
        MediaPlayer player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        mp = player;
        return player;
    }

    private void prepare(final MediaPlayer player) {
        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer prepared) {
                try {
                    prepared.start();
                } catch (Exception ignored) {
                }
                notifyChanged();
            }
        });
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer completed) {
                if (selectedUris != null && selectedUris.size() > 1) {
                    try {
                        completed.release();
                    } catch (Exception ignored) {
                    }
                    if (mp == completed) mp = null;
                    selectedPosition = (selectedPosition + 1) % selectedUris.size();
                    startSelectedUri();
                } else {
                    notifyChanged();
                }
            }
        });
        player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer failed, int what, int extra) {
                clearAfterFailure();
                notifyChanged();
                return true;
            }
        });
        player.prepareAsync();
    }

    private void clearAfterFailure() {
        stopInternal();
        index = -1;
        clearUserSelection();
    }

    private void clearUserSelection() {
        selectedUris = null;
        selectedTitles = null;
        selectedPosition = 0;
    }

    public void toggle() {
        if (mp == null) {
            if (index >= 0) play(index);
            return;
        }
        try {
            if (mp.isPlaying()) {
                mp.pause();
            } else {
                mp.start();
            }
        } catch (Exception ignored) {
        }
        notifyChanged();
    }

    public void next() {
        if (selectedUris != null) {
            if (selectedUris.size() > 1) {
                selectedPosition = (selectedPosition + 1) % selectedUris.size();
                stopInternal();
                startSelectedUri();
            }
            return;
        }
        int i = (index + 1 + FILES.length) % FILES.length;
        play(i);
    }

    public void prev() {
        if (selectedUris != null) {
            if (selectedUris.size() > 1) {
                selectedPosition = (selectedPosition - 1 + selectedUris.size()) % selectedUris.size();
                stopInternal();
                startSelectedUri();
            }
            return;
        }
        int i = (index - 1 + FILES.length) % FILES.length;
        play(i);
    }

    public void stop() {
        stopInternal();
        index = -1;
        clearUserSelection();
        notifyChanged();
    }

    private void stopInternal() {
        if (mp != null) {
            try {
                mp.setOnPreparedListener(null);
                mp.setOnCompletionListener(null);
                mp.setOnErrorListener(null);
            } catch (Exception ignored) {
            }
            try {
                mp.stop();
            } catch (Exception ignored) {
            }
            try {
                mp.release();
            } catch (Exception ignored) {
            }
            mp = null;
        }
    }
}
