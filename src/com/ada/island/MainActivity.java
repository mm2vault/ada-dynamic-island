package com.ada.island;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Ada's home: permissions, AI key, radio and the island switch. */
public class MainActivity extends Activity {

    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int PICK_AUDIO = 810;

    private Prefs prefs;
    private LinearLayout permsBox;
    private TextView islandLabel;
    private TextView testResult;
    private TextView updateStatus;
    private EditText keyInput;
    private EditText modelInput;
    private EditText urlInput;
    private EditText musicSearch;
    private EditText youtubeApiKeyInput;
    private Spinner providerSpinner;
    private TextView toggleBtn;
    private boolean installingUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        setContentView(build());
        loadAiFields();
        maybeAutoCheckUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderPermissions();
        renderIslandState();
        maybeInstallPendingUpdate();
    }

    // ------------------------------------------------------------------- UI

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF08080C);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = Ui.col(this);
        int h = Ui.dp(this, 22);
        root.setPadding(h, Ui.dp(this, 34), h, Ui.dp(this, 40));
        scroll.addView(root, new ScrollView.LayoutParams(MATCH, WRAP));

        TextView logo = Ui.tv(this, "ada", 46f, Ui.TEXT, true);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(WRAP, WRAP);
        logoLp.topMargin = Ui.dp(this, 8);
        root.addView(logo, logoLp);

        TextView sub = Ui.tv(this, "akıllı dynamic island · android", 12.5f, Ui.MUTED, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(WRAP, WRAP);
        subLp.topMargin = Ui.dp(this, 2);
        root.addView(sub, subLp);

        // island status card
        LinearLayout statusCard = card(0.06f);
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        scLp.topMargin = Ui.dp(this, 26);
        root.addView(statusCard, scLp);

        LinearLayout statusTop = Ui.row(this);
        PulseView dot = new PulseView(this);
        statusTop.addView(dot, Ui.lp(Ui.dp(this, 20), Ui.dp(this, 20)));
        LinearLayout stText = Ui.col(this);
        TextView stTitle = Ui.tv(this, "Ada adası", 15f, Ui.TEXT, true);
        islandLabel = Ui.tv(this, "kapalı", 11.5f, Ui.MUTED, false);
        stText.addView(stTitle);
        stText.addView(islandLabel);
        LinearLayout.LayoutParams stLp = Ui.lpw(0, WRAP, 1f);
        stLp.leftMargin = Ui.dp(this, 10);
        statusTop.addView(stText, stLp);

        final TextView toggle = Ui.tv(this, "Başlat", 12.5f, 0xFF0A0A11, true);
        toggle.setGravity(Gravity.CENTER);
        toggle.setBackground(Ui.gradient(new int[]{0xFF9B7BFF, 0xFF33D6FF}, Ui.dpf(this, 18)));
        toggle.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10));
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (IslandService.running || prefs.islandOn()) stopIsland();
                else startIsland();
                renderIslandState();
            }
        });
        toggleBtn = toggle;
        statusTop.addView(toggle);
        statusCard.addView(statusTop);

        TextView hint = Ui.tv(this, "Ada ekranın en üstünde küçük bir kapsül olarak yaşar. Dokun: müzik, bağlam ve sorular.",
                11.5f, Ui.MUTED, false);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        hintLp.topMargin = Ui.dp(this, 12);
        statusCard.addView(hint, hintLp);

        // permissions
        root.addView(sectionTitle("İzinler"));
        permsBox = Ui.col(this);
        root.addView(permsBox, new LinearLayout.LayoutParams(MATCH, WRAP));

        root.addView(sectionTitle("Güncellemeler"));
        LinearLayout updateCard = card(0.05f);
        LinearLayout.LayoutParams updateCardLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        updateCardLp.topMargin = Ui.dp(this, 10);
        root.addView(updateCard, updateCardLp);
        updateCard.addView(Ui.tv(this,
                "Ada yeni sürümü uygulama açıldığında denetler. İndirilen APK doğrulanır; yükleme için Android onayı gerekir.",
                11.5f, Ui.MUTED, false));
        updateStatus = Ui.tv(this, "Henüz denetlenmedi", 11.5f, Ui.TEXT, false);
        LinearLayout.LayoutParams updateStatusLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        updateStatusLp.topMargin = Ui.dp(this, 8);
        updateCard.addView(updateStatus, updateStatusLp);
        TextView updateButton = actionButton("Şimdi güncelleme ara", false);
        LinearLayout.LayoutParams updateButtonLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        updateButtonLp.topMargin = Ui.dp(this, 8);
        updateButton.setLayoutParams(updateButtonLp);
        updateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkForUpdates(true);
            }
        });
        updateCard.addView(updateButton);

        // AI
        root.addView(sectionTitle("Yapay zekâ"));

        LinearLayout aiCard = card(0.05f);
        LinearLayout.LayoutParams aiLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        aiLp.topMargin = Ui.dp(this, 10);
        root.addView(aiCard, aiLp);

        aiCard.addView(label("Sağlayıcı"));
        providerSpinner = new Spinner(this);
        ArrayAdapter<String> providerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"OpenAI uyumlu servis", "Google Gemini API"});
        providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(providerAdapter);
        providerSpinner.setSelection(prefs.isGemini() ? 1 : 0);
        aiCard.addView(providerSpinner);

        TextView geminiPreset = actionButton("Gemini 3.8 Flash ayarlarını uygula", false);
        LinearLayout.LayoutParams geminiLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        geminiLp.topMargin = Ui.dp(this, 8);
        geminiPreset.setLayoutParams(geminiLp);
        geminiPreset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                providerSpinner.setSelection(1);
                modelInput.setText("gemini-3.8-flash");
                urlInput.setText("https://generativelanguage.googleapis.com/v1beta");
                Toast.makeText(MainActivity.this, "Gemini 3.8 Flash seçildi.", Toast.LENGTH_SHORT).show();
            }
        });
        aiCard.addView(geminiPreset);

        aiCard.addView(label("API anahtarı"));
        keyInput = field("Google AI Studio: API key / OpenAI: sk-...", true);
        aiCard.addView(keyInput);

        aiCard.addView(label("Model"));
        modelInput = field("gpt-4o-mini", false);
        aiCard.addView(modelInput);

        aiCard.addView(label("Sunucu adresi"));
        urlInput = field("https://api.openai.com/v1", false);
        aiCard.addView(urlInput);

        LinearLayout btnRow = Ui.row(this);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        brLp.topMargin = Ui.dp(this, 12);
        btnRow.setLayoutParams(brLp);

        TextView save = actionButton("Kaydet ve test et", true);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAi();
                testAi();
            }
        });
        btnRow.addView(save, Ui.lpw(0, WRAP, 1f));

        TextView openChat = actionButton("Sohbet", false);
        LinearLayout.LayoutParams ocLp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        ocLp.leftMargin = Ui.dp(this, 8);
        openChat.setLayoutParams(ocLp);
        openChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ChatActivity.class));
            }
        });
        btnRow.addView(openChat);
        aiCard.addView(btnRow);

        testResult = Ui.tv(this, "", 11.5f, 0xFFB9B9C8, false);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        trLp.topMargin = Ui.dp(this, 10);
        aiCard.addView(testResult, trLp);

        TextView prov = Ui.tv(this, "Gemini seçiliyse Google API key kullanılır. OpenAI uyumlu seçiliyse OpenAI, Groq, OpenRouter veya yerel sunucu kullanılabilir.",
                10.5f, Ui.MUTED, false);
        LinearLayout.LayoutParams pvLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        pvLp.topMargin = Ui.dp(this, 8);
        aiCard.addView(prov, pvLp);

        // behaviour
        root.addView(sectionTitle("Davranış"));
        LinearLayout behCard = card(0.05f);
        LinearLayout.LayoutParams bhLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        bhLp.topMargin = Ui.dp(this, 10);
        root.addView(behCard, bhLp);

        final Switch proactive = new Switch(this);
        proactive.setTextColor(Ui.TEXT);
        proactive.setText("Kendiliğinden soru sorsun");
        proactive.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        proactive.setChecked(prefs.proactive());
        proactive.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                prefs.setProactive(checked);
                if (checked) prefs.setLastProactive(0L);
            }
        });
        behCard.addView(proactive);

        TextView gapLabel = Ui.tv(this, "Soru sıklığı", 11.5f, Ui.MUTED, false);
        LinearLayout.LayoutParams glLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        glLp.topMargin = Ui.dp(this, 12);
        behCard.addView(gapLabel, glLp);

        LinearLayout gapRow = Ui.row(this);
        LinearLayout.LayoutParams grLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        grLp.topMargin = Ui.dp(this, 6);
        gapRow.setLayoutParams(grLp);
        gapRow.addView(gapChip("15 dk", 15), Ui.lpw(0, WRAP, 1f));
        gapRow.addView(gapChip("30 dk", 30), Ui.lpw(0, WRAP, 1f));
        gapRow.addView(gapChip("60 dk", 60), Ui.lpw(0, WRAP, 1f));
        behCard.addView(gapRow);

        final Switch aiSwitch = new Switch(this);
        aiSwitch.setTextColor(Ui.TEXT);
        aiSwitch.setText("Öneriler AI ile üretilsin");
        aiSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        aiSwitch.setChecked(prefs.aiOn());
        aiSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                prefs.setAiOn(checked);
            }
        });
        LinearLayout.LayoutParams asLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        asLp.topMargin = Ui.dp(this, 10);
        behCard.addView(aiSwitch, asLp);

        // radio
        root.addView(sectionTitle("Ada Radyo"));
        LinearLayout radioCard = card(0.05f);
        LinearLayout.LayoutParams rcLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        rcLp.topMargin = Ui.dp(this, 10);
        root.addView(radioCard, rcLp);

        TextView rh = Ui.tv(this, "Uygulamanın içinde gelen üç ortam parçası. Adadan da kontrol edilir.",
                11.5f, Ui.MUTED, false);
        radioCard.addView(rh);

        for (int i = 0; i < LocalPlayer.TITLES.length; i++) {
            final int index = i;
            TextView track = Ui.tv(this, "♪  " + LocalPlayer.TITLES[i], 12.5f, 0xFFE8E8F2, true);
            track.setBackground(Ui.round(0x12FFFFFF, Ui.dpf(this, 13), Math.max(1, Ui.dp(this, 1)), 0x1FFFFFFF));
            track.setPadding(Ui.dp(this, 12), Ui.dp(this, 11), Ui.dp(this, 12), Ui.dp(this, 11));
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(MATCH, WRAP);
            tLp.topMargin = Ui.dp(this, 8);
            radioCard.addView(track, tLp);
            track.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LocalPlayer.get(MainActivity.this).play(index);
                    Toast.makeText(MainActivity.this, "♪ " + LocalPlayer.TITLES[index], Toast.LENGTH_SHORT).show();
                }
            });
        }

        TextView stop = Ui.tv(this, "durdur", 11.5f, Ui.MUTED, false);
        stop.setGravity(Gravity.CENTER);
        stop.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LocalPlayer.get(MainActivity.this).stop();
            }
        });
        radioCard.addView(stop);

        // Search is handed to official music apps; local files are selected through SAF.
        root.addView(sectionTitle("Müzik bul ve telefondan çal"));
        LinearLayout findCard = card(0.05f);
        LinearLayout.LayoutParams findLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        findLp.topMargin = Ui.dp(this, 10);
        root.addView(findCard, findLp);

        TextView findHint = Ui.tv(this,
                "YouTube anahtarıyla ara ve seçtiğini Ada içindeki görünür oynatıcıda aç. Anahtarsız arama resmî uygulamada yapılır.",
                11.5f, Ui.MUTED, false);
        findCard.addView(findHint);

        musicSearch = field("Şarkı veya sanatçı", false);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        searchLp.topMargin = Ui.dp(this, 9);
        musicSearch.setLayoutParams(searchLp);
        findCard.addView(musicSearch);

        TextView apiHint = Ui.tv(this,
                "İsteğe bağlı YouTube Data API v3 anahtarı. Anahtar yoksa arama resmî uygulamaya aktarılır.",
                10.5f, Ui.MUTED, false);
        LinearLayout.LayoutParams apiHintLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        apiHintLp.topMargin = Ui.dp(this, 8);
        apiHint.setLayoutParams(apiHintLp);
        findCard.addView(apiHint);

        youtubeApiKeyInput = field("YouTube Data API v3 anahtarı", true);
        LinearLayout.LayoutParams apiKeyLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        apiKeyLp.topMargin = Ui.dp(this, 5);
        youtubeApiKeyInput.setLayoutParams(apiKeyLp);
        findCard.addView(youtubeApiKeyInput);

        LinearLayout providerRow = Ui.row(this);
        LinearLayout.LayoutParams providerLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        providerLp.topMargin = Ui.dp(this, 8);
        providerRow.setLayoutParams(providerLp);

        TextView spotify = actionButton("Spotify'da ara", false);
        spotify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMusicSearch("spotify");
            }
        });
        providerRow.addView(spotify, Ui.lpw(0, WRAP, 1f));

        TextView youtube = actionButton("YouTube'da ara", false);
        LinearLayout.LayoutParams youtubeLp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        youtubeLp.leftMargin = Ui.dp(this, 8);
        youtube.setLayoutParams(youtubeLp);
        youtube.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchYoutube((TextView) v);
            }
        });
        providerRow.addView(youtube);
        findCard.addView(providerRow);

        TextView localPick = actionButton("Telefondan ses dosyası seç", true);
        LinearLayout.LayoutParams localLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        localLp.topMargin = Ui.dp(this, 8);
        localPick.setLayoutParams(localLp);
        localPick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseLocalAudio();
            }
        });
        findCard.addView(localPick);

        TextView footer = Ui.tv(this, "ada 1.11 · Gemini 3.8 Flash + YouTube arama/oynatıcı + güncelleme\nAndroid sistem seçicisi yalnızca seçtiğin dosyalara erişim verir.",
                10.5f, 0xFF6E6E7C, false);
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        fLp.topMargin = Ui.dp(this, 30);
        root.addView(footer, fLp);

        return scroll;
    }

    private void openMusicSearch(String provider) {
        String query = musicSearch == null ? "" : musicSearch.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Önce şarkı veya sanatçı yaz.", Toast.LENGTH_SHORT).show();
            return;
        }
        String encoded = Uri.encode(query);
        String pkg;
        String url;
        if ("spotify".equals(provider)) {
            pkg = "com.spotify.music";
            url = "https://open.spotify.com/search/" + encoded;
        } else {
            pkg = "com.google.android.apps.youtube.music";
            url = "https://music.youtube.com/search?q=" + encoded;
        }
        Intent appIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        appIntent.setPackage(pkg);
        try {
            startActivity(appIntent);
            return;
        } catch (Exception ignored) {
        }
        if ("youtube".equals(provider)) {
            Intent youtubeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            youtubeIntent.setPackage("com.google.android.youtube");
            try {
                startActivity(youtubeIntent);
                return;
            } catch (Exception ignored) {
            }
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Bu müzik uygulaması açılamadı.", Toast.LENGTH_LONG).show();
        }
    }

    private void searchYoutube(final TextView button) {
        String query = musicSearch == null ? "" : musicSearch.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Önce şarkı veya sanatçı yaz.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String apiKey = youtubeApiKeyInput == null
                ? prefs.youtubeDataApiKey() : youtubeApiKeyInput.getText().toString().trim();
        if (apiKey.isEmpty()) {
            prefs.setYoutubeDataApiKey("");
            openMusicSearch("youtube");
            return;
        }

        prefs.setYoutubeDataApiKey(apiKey);
        button.setEnabled(false);
        button.setText("YouTube'da aranıyor…");
        YouTubeSearchClient.search(apiKey, query, new YouTubeSearchClient.Callback() {
            @Override
            public void onResults(final java.util.List<YouTubeSearchClient.Track> tracks) {
                button.setEnabled(true);
                button.setText("YouTube'da ara");
                if (tracks.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Sonuç bulunamadı.", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] labels = new String[tracks.size()];
                for (int i = 0; i < tracks.size(); i++) labels[i] = tracks.get(i).label();
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("YouTube sonuçları")
                        .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                YouTubeSearchClient.Track track = tracks.get(which);
                                openYouTubeVideo(track.videoId, track.title);
                            }
                        })
                        .setNegativeButton("Kapat", null)
                        .show();
            }

            @Override
            public void onError(String message) {
                button.setEnabled(true);
                button.setText("YouTube'da ara");
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openYouTubeVideo(String videoId, String title) {
        Intent player = new Intent(this, YouTubePlayerActivity.class);
        player.putExtra(YouTubePlayerActivity.EXTRA_VIDEO_ID, videoId);
        player.putExtra(YouTubePlayerActivity.EXTRA_TITLE, title);
        startActivity(player);
    }

    private void chooseLocalAudio() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("audio/*");
        pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(pick, PICK_AUDIO);
        } catch (Exception e) {
            Toast.makeText(this, "Android dosya seçicisi açılamadı.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_AUDIO || resultCode != RESULT_OK || data == null) return;
        java.util.ArrayList<Uri> uris = new java.util.ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) uris.add(uri);
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;

        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        java.util.ArrayList<String> titles = new java.util.ArrayList<>();
        for (Uri uri : uris) {
            try {
                if (flags != 0) getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) {
            }
            titles.add(displayName(uri));
        }
        LocalPlayer.get(this).playUris(uris, titles);
        Toast.makeText(this, uris.size() + " parça seçildi · " + titles.get(0), Toast.LENGTH_SHORT).show();
        if (Settings.canDrawOverlays(this)) {
            startIsland();
        } else {
            Toast.makeText(this, "Arka planda güvenilir çalma ve ada için 'Üstte görünme' iznini etkinleştir.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private String displayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameCol = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameCol >= 0) {
                    String name = cursor.getString(nameCol);
                    if (name != null && !name.trim().isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return "Telefondaki müzik";
    }

    private LinearLayout card(float alpha) {
        LinearLayout c = Ui.col(this);
        c.setBackground(Ui.round(0xFFFFFFFF & 0x12FFFFFF, Ui.dpf(this, 20), Math.max(1, Ui.dp(this, 1)), 0x1AFFFFFF));
        c.setPadding(Ui.dp(this, 16), Ui.dp(this, 15), Ui.dp(this, 16), Ui.dp(this, 15));
        return c;
    }

    private TextView sectionTitle(String text) {
        TextView t = Ui.tv(this, text, 12f, 0xFF8E8E9E, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        p.topMargin = Ui.dp(this, 28);
        p.bottomMargin = Ui.dp(this, 2);
        t.setLayoutParams(p);
        t.setLetterSpacing(0.12f);
        return t;
    }

    private TextView label(String text) {
        TextView t = Ui.tv(this, text, 10.5f, Ui.MUTED, false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        p.topMargin = Ui.dp(this, 12);
        p.bottomMargin = Ui.dp(this, 5);
        t.setLayoutParams(p);
        return t;
    }

    private EditText field(String hint, boolean secret) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF5E5E6E);
        e.setTextColor(Ui.TEXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        e.setBackground(Ui.round(0xFF15151E, Ui.dpf(this, 12), Math.max(1, Ui.dp(this, 1)), 0x22FFFFFF));
        e.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        e.setSingleLine(true);
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        else e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        e.setLayoutParams(p);
        return e;
    }

    private TextView actionButton(String text, boolean primary) {
        TextView t = Ui.tv(this, text, 12.5f, primary ? 0xFF0A0A11 : 0xFFE8E8F2, true);
        t.setGravity(Gravity.CENTER);
        if (primary) {
            t.setBackground(Ui.gradient(new int[]{0xFF9B7BFF, 0xFF5EE0FF}, Ui.dpf(this, 14)));
        } else {
            t.setBackground(Ui.round(0x1AFFFFFF, Ui.dpf(this, 14), Math.max(1, Ui.dp(this, 1)), 0x22FFFFFF));
        }
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        return t;
    }

    private TextView gapChip(String text, final int minutes) {
        boolean active = prefs.proactiveGapMin() == minutes;
        TextView t = Ui.tv(this, text, 12f, active ? 0xFF0A0A11 : 0xFFE8E8F2, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(active
                ? Ui.round(0xFFDFDFF5, Ui.dpf(this, 13))
                : Ui.round(0x12FFFFFF, Ui.dpf(this, 13), Math.max(1, Ui.dp(this, 1)), 0x1FFFFFFF));
        t.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.setProactiveGapMin(minutes);
                recreate();
            }
        });
        return t;
    }

    // ------------------------------------------------------------ permissions

    private void renderPermissions() {
        if (permsBox == null) return;
        permsBox.removeAllViews();

        boolean overlay = Settings.canDrawOverlays(this);
        permsBox.addView(permRow("Üstte görünme", "Ekranın üstünde kapsül çizebilmek için şart.", overlay,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        safeStart(i);
                    }
                }));

        boolean notifAccess = MusicBridge.hasAccess(this);
        permsBox.addView(permRow("Bildirim erişimi", "Hangi müzik çalıyor, onu görmek ve kontrol etmek için.",
                notifAccess, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        safeStart(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    }
                }));

        boolean usage = ContextReader.hasUsageAccess(this);
        permsBox.addView(permRow("Kullanım erişimi", "Hangi uygulamada olduğunu ve ne kadar kaldığını görmek için.",
                usage, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        safeStart(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                    }
                }));

        if (Build.VERSION.SDK_INT >= 33) {
            boolean post = checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            permsBox.addView(permRow("Bildirim izni", "Ada çalışırken kalıcı bildirimi göstermek için.",
                    post, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (Build.VERSION.SDK_INT >= 33) {
                                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
                            }
                        }
                    }));
        }

        permsBox.addView(permRow("Pil optimizasyonu", "Sistem adayı kapatmasın diye optimizasyonu kaldır.",
                true, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        safeStart(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    }
                }));
    }

    private View permRow(String title, String desc, boolean ok, final View.OnClickListener click) {
        LinearLayout row = Ui.row(this);
        row.setBackground(Ui.round(0xFF101018, Ui.dpf(this, 16), Math.max(1, Ui.dp(this, 1)), 0x14FFFFFF));
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 12), Ui.dp(this, 13));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        p.bottomMargin = Ui.dp(this, 8);
        row.setLayoutParams(p);

        LinearLayout texts = Ui.col(this);
        TextView t = Ui.tv(this, title, 13f, Ui.TEXT, true);
        TextView d = Ui.tv(this, desc, 10.5f, Ui.MUTED, false);
        texts.addView(t);
        texts.addView(d);
        LinearLayout.LayoutParams tp = Ui.lpw(0, WRAP, 1f);
        tp.rightMargin = Ui.dp(this, 10);
        row.addView(texts, tp);

        TextView badge = Ui.tv(this, ok ? "tamam" : "izin ver", 11f,
                ok ? 0xFF7BE0A8 : 0xFF0A0A11, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(ok
                ? Ui.round(0x1A7BE0A8, Ui.dpf(this, 12))
                : Ui.round(0xFFDFDFF5, Ui.dpf(this, 12)));
        badge.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        badge.setOnClickListener(ok ? null : click);
        row.addView(badge);
        row.setOnClickListener(ok ? null : click);
        return row;
    }

    private void safeStart(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Bu ayar ekranı açılamadı.", Toast.LENGTH_SHORT).show();
        }
    }

    // ----------------------------------------------------------------- state

    private void renderIslandState() {
        if (islandLabel == null) return;
        boolean on = IslandService.running || prefs.islandOn();
        islandLabel.setText(on ? "çalışıyor · ekranın üstünde" : "kapalı");
        if (toggleBtn != null) toggleBtn.setText(on ? "Durdur" : "Başlat");
    }

    private void maybeAutoCheckUpdates() {
        long now = System.currentTimeMillis();
        if (now - prefs.lastUpdateCheck() < 24L * 60L * 60L * 1000L) return;
        checkForUpdates(false);
    }

    private void checkForUpdates(final boolean manual) {
        if (updateStatus != null) updateStatus.setText("Sürüm denetleniyor…");
        UpdateManager.check(this, new UpdateManager.CheckCallback() {
            @Override
            public void onResult(UpdateManager.UpdateInfo update, String error) {
                if (isFinishing()) return;
                if (error != null) {
                    prefs.setLastUpdateCheck(0L);
                    if (updateStatus != null) updateStatus.setText("Güncelleme denetlenemedi.");
                    if (manual) Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                    return;
                }
                prefs.setLastUpdateCheck(System.currentTimeMillis());
                if (update == null) {
                    if (updateStatus != null) updateStatus.setText("Ada güncel.");
                    if (manual) Toast.makeText(MainActivity.this, "Ada'nın en son sürümündesin.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (updateStatus != null) updateStatus.setText("Yeni sürüm hazır: " + update.versionName);
                showUpdateDialog(update);
            }
        });
    }

    private void showUpdateDialog(final UpdateManager.UpdateInfo update) {
        String message = "Yeni sürüm: Ada " + update.versionName;
        if (!update.notes.isEmpty()) message += "\n\n" + update.notes;
        message += "\n\nİndirilen APK'nın SHA-256 değeri doğrulanır. Kurulum Android'in kendi onay ekranında tamamlanır.";
        new AlertDialog.Builder(this)
                .setTitle("Ada güncellemesi var")
                .setMessage(message)
                .setNegativeButton("Daha sonra", null)
                .setPositiveButton("İndir ve devam et", (dialog, which) -> downloadUpdate(update))
                .show();
    }

    private void downloadUpdate(UpdateManager.UpdateInfo update) {
        if (updateStatus != null) updateStatus.setText("İndiriliyor ve doğrulanıyor…");
        UpdateManager.download(this, update, new UpdateManager.DownloadCallback() {
            @Override
            public void onResult(java.io.File apk, String error) {
                if (isFinishing()) return;
                if (error != null || apk == null) {
                    if (updateStatus != null) updateStatus.setText("Güncelleme indirilemedi.");
                    Toast.makeText(MainActivity.this,
                            error == null ? "APK indirilemedi." : error, Toast.LENGTH_LONG).show();
                    return;
                }
                prefs.setUpdateAwaitingInstall(true);
                if (updateStatus != null) updateStatus.setText("APK doğrulandı; Android kurulumu bekleniyor.");
                maybeInstallPendingUpdate();
                if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Android'de bir izin gerekli")
                            .setMessage("APK doğrulandı. Android, bu kaynaktan güncelleme yüklemeye izin vermeni isteyecek; ardından Android'in kurulum onayı ayrıca gösterilir.")
                            .setNegativeButton("Daha sonra", null)
                            .setPositiveButton("Ayarları aç", (dialog, which) -> openInstallSourceSettings())
                            .show();
                }
            }
        });
    }

    private void openInstallSourceSettings() {
        if (Build.VERSION.SDK_INT < 26) return;
        Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName()));
        safeStart(settings);
    }

    private void maybeInstallPendingUpdate() {
        if (installingUpdate || !prefs.updateAwaitingInstall()) return;
        java.io.File apk = UpdateManager.pendingFile(this);
        if (!apk.isFile()) {
            prefs.setUpdateAwaitingInstall(false);
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) return;
        prefs.setUpdateAwaitingInstall(false);
        installingUpdate = true;
        if (updateStatus != null) updateStatus.setText("Android kurulum isteği hazırlanıyor…");
        UpdateManager.install(this, apk, new UpdateManager.InstallCallback() {
            @Override
            public void onResult(String error) {
                installingUpdate = false;
                if (error != null) {
                    if (updateStatus != null) updateStatus.setText("Kurulum başlatılamadı.");
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                } else if (updateStatus != null) {
                    updateStatus.setText("Android'in kurulum onayı bekleniyor…");
                }
            }
        });
    }

    private void startIsland() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Önce 'Üstte görünme' iznini ver.", Toast.LENGTH_LONG).show();
            safeStart(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        prefs.setIslandOn(true);
        Intent i = new Intent(this, IslandService.class);
        i.setAction(IslandService.ACTION_SHOW);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        Toast.makeText(this, "Ada ekranın üstünde.", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void stopIsland() {
        prefs.setIslandOn(false);
        Intent i = new Intent(this, IslandService.class);
        i.setAction(IslandService.ACTION_HIDE);
        try {
            startService(i);
        } catch (Exception ignored) {
        }
    }

    private void loadAiFields() {
        providerSpinner.setSelection(prefs.isGemini() ? 1 : 0);
        keyInput.setText(prefs.apiKey());
        modelInput.setText(prefs.model());
        urlInput.setText(prefs.baseUrl());
        youtubeApiKeyInput.setText(prefs.youtubeDataApiKey());
    }

    private void saveAi() {
        boolean gemini = providerSpinner != null && providerSpinner.getSelectedItemPosition() == 1;
        prefs.setProvider(gemini ? "gemini" : "openai");
        prefs.setApiKey(keyInput.getText().toString());
        String model = modelInput.getText().toString().trim();
        String endpoint = urlInput.getText().toString().trim();
        if (gemini) {
            if (model.isEmpty() || "gpt-4o-mini".equals(model)) model = "gemini-3.8-flash";
            if (endpoint.isEmpty() || "https://api.openai.com/v1".equals(endpoint)) {
                endpoint = "https://generativelanguage.googleapis.com/v1beta";
            }
        } else {
            if (model.startsWith("gemini-")) model = "gpt-4o-mini";
            if (endpoint.startsWith("https://generativelanguage.googleapis.com/")) {
                endpoint = "https://api.openai.com/v1";
            }
        }
        modelInput.setText(model);
        urlInput.setText(endpoint);
        prefs.setModel(model);
        prefs.setBaseUrl(endpoint);
    }

    private void testAi() {
        testResult.setTextColor(0xFFB9B9C8);
        testResult.setText("deniyorum…");
        java.util.List<AiClient.Msg> h = new java.util.ArrayList<>();
        h.add(new AiClient.Msg("system", AiClient.SYSTEM_PROMPT));
        h.add(new AiClient.Msg("user", "Tek cümleyle kendini tanıt."));
        AiClient.chat(this, h, new AiClient.Cb() {
            @Override
            public void onOk(String text) {
                testResult.setTextColor(0xFF7BE0A8);
                testResult.setText("✓ " + text);
                Intent refresh = new Intent(MainActivity.this, IslandService.class);
                refresh.setAction(IslandService.ACTION_REFRESH);
                try {
                    startService(refresh);
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onErr(String message) {
                testResult.setTextColor(0xFFFF8A8A);
                testResult.setText("✕ " + message);
            }
        });
    }
}
