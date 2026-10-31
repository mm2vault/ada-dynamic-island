package com.ada.island;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** A visible official YouTube IFrame player; playback pauses when Ada leaves the foreground. */
public class YouTubePlayerActivity extends Activity {
    public static final String EXTRA_VIDEO_ID = "video_id";
    public static final String EXTRA_TITLE = "video_title";

    private WebView webView;
    private LinearLayout root;
    private View fullScreenView;
    private WebChromeClient.CustomViewCallback fullScreenCallback;
    private WebChromeClient playerChrome;
    private String videoId;
    private String title;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{6,20}")) {
            Toast.makeText(this, "YouTube video kimliği geçersiz.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        getWindow().setStatusBarColor(0xFF08080D);
        getWindow().setNavigationBarColor(0xFF08080D);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF08080D);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 16));

        TextView back = Ui.tv(this, "‹  Geri", 14f, Ui.MUTED, true);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView heading = Ui.tv(this, title == null || title.trim().isEmpty() ? "YouTube oynatıcı" : title,
                15f, Ui.TEXT, true);
        heading.setMaxLines(2);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = Ui.dp(this, 12);
        root.addView(heading, titleLp);

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF000000);
        playerChrome = new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullScreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullScreenView = view;
                fullScreenCallback = callback;
                setContentView(view);
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }

            @Override
            public void onHideCustomView() {
                if (fullScreenView == null) return;
                setContentView(root);
                getWindow().getDecorView().setSystemUiVisibility(0);
                fullScreenView = null;
                if (fullScreenCallback != null) fullScreenCallback.onCustomViewHidden();
                fullScreenCallback = null;
            }
        };
        webView.setWebChromeClient(playerChrome);
        webView.setWebViewClient(new WebViewClient());
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        int playerHeight = Math.max(Ui.dp(this, 200), getResources().getDisplayMetrics().widthPixels * 9 / 16);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, playerHeight));

        TextView note = Ui.tv(this,
                "Resmî YouTube oynatıcısı. Çalma yalnızca bu ekran açıkken sürer; arka planda dinlemek için resmî uygulamaya geç.",
                11f, Ui.MUTED, false);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = Ui.dp(this, 12);
        root.addView(note, noteLp);

        TextView official = Ui.tv(this, "YouTube Music uygulamasında aç", 12.5f, 0xFFE8E8F2, true);
        official.setGravity(Gravity.CENTER);
        official.setBackground(Ui.round(0x1AFFFFFF, Ui.dpf(this, 14), Math.max(1, Ui.dp(this, 1)), 0x22FFFFFF));
        official.setPadding(Ui.dp(this, 12), Ui.dp(this, 13), Ui.dp(this, 12), Ui.dp(this, 13));
        LinearLayout.LayoutParams officialLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        officialLp.topMargin = Ui.dp(this, 12);
        root.addView(official, officialLp);
        official.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { openOfficialApp(); }
        });

        setContentView(root);
        webView.loadDataWithBaseURL("https://www.youtube.com", playerHtml(videoId), "text/html", "UTF-8", null);
    }

    private String playerHtml(String id) {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'>"
                + "<style>html,body,#player{margin:0;width:100%;height:100%;background:#000;overflow:hidden}</style></head><body>"
                + "<div id='player'></div><script>var player;function onYouTubeIframeAPIReady(){player=new YT.Player('player',{"
                + "width:'100%',height:'100%',videoId:'" + id + "',playerVars:{playsinline:1,controls:1,autoplay:0,rel:0,origin:'https://www.youtube.com'},"
                + "events:{onReady:function(){},onError:function(){}}});}</script>"
                + "<script src='https://www.youtube.com/iframe_api'></script></body></html>";
    }

    private void openOfficialApp() {
        String url = "https://music.youtube.com/watch?v=" + Uri.encode(videoId);
        String[] packages = new String[]{"com.google.android.apps.youtube.music", "com.google.android.youtube"};
        for (String packageName : packages) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage(packageName);
                startActivity(intent);
                return;
            } catch (Exception ignored) {
            }
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "YouTube açılamadı.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    public void onBackPressed() {
        if (fullScreenView != null && playerChrome != null) {
            playerChrome.onHideCustomView();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.evaluateJavascript("if(window.player&&window.player.pauseVideo){window.player.pauseVideo();}", null);
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
