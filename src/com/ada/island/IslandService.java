package com.ada.island;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.Service;
import android.animation.ArgbEvaluator;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Dynamic Island itself: a floating, always-on-top capsule that shows music,
 * watches context and talks to the AI.
 */
public class IslandService extends Service {

    public static final String ACTION_SHOW = "com.ada.island.action.SHOW";
    public static final String ACTION_HIDE = "com.ada.island.action.HIDE";
    public static final String ACTION_REFRESH = "com.ada.island.action.REFRESH";
    public static final String ACTION_ASK = "com.ada.island.action.ASK";
    public static final String EXTRA_Q = "q";

    private static final String CHANNEL = "ada_island";
    private static final int NOTIF_ID = 41;

    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    public static volatile boolean running = false;

    private WindowManager wm;
    private Prefs prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private FrameLayout root;
    private LinearLayout capsule;
    private GradientDrawable capsuleBg;
    private View collapsedView;
    private View expandedView;
    private PulseView dotSmall;
    private WaveView wave;
    private TextView pillTitle;
    private TextView musicBadge;
    private ValueAnimator badgePulse;

    private TextView exStatus;
    private TextView exTrack;
    private TextView exArtist;
    private TextView exCtx;
    private TextView exAnswer;
    private PlayPauseView exPlay;
    private LinearLayout chipBox;
    private ScrollView answerScroll;

    private WindowManager.LayoutParams lp;
    private int padPx;
    private int contentW;
    private int collapsedH;
    private float radiusPx;

    private boolean expanded = false;
    private boolean built = false;
    private boolean animating = false;
    private boolean asking = false;
    private long lastTouch = 0L;
    private long lastSuggestAt = 0L;
    private long lastNotifAt = 0L;
    private int tickCount = 0;
    private MusicBridge.Track track;
    private List<String> suggestions = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        createChannel();
        LocalPlayer.get(this).setListener(new LocalPlayer.Listener() {
            @Override
            public void onPlayerChanged() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        refresh(true);
                    }
                });
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startForeground(NOTIF_ID, buildNotification());
        } catch (Exception e) {
            // e.g. ForegroundServiceStartNotAllowedException on Android 12+
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent == null ? null : intent.getAction();
        if (ACTION_HIDE.equals(action)) {
            hideIsland();
            return START_NOT_STICKY;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!built) addOverlay();
        if (built) {
            prefs.setIslandOn(true);
            handler.removeCallbacks(tick);
            handler.post(tick);
            if (ACTION_REFRESH.equals(action)) {
                refresh(true);
                ensureSuggestions(true);
            } else if (ACTION_ASK.equals(action) && intent != null) {
                String q = intent.getStringExtra(EXTRA_Q);
                expand();
                if (q != null && q.length() > 0) ask(q);
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!built) return;
        try {
            wm.removeView(root);
        } catch (Exception ignored) {
        }
        built = false;
        expanded = false;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!built && prefs.islandOn()) addOverlay();
            }
        }, 150);
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        stopBadgePulse();
        LocalPlayer.get(this).setListener(null);
        if (wm != null && root != null && root.getParent() != null) {
            try {
                wm.removeView(root);
            } catch (Exception ignored) {
            }
        }
        built = false;
        super.onDestroy();
    }

    // ---------------------------------------------------------------- overlay

    private void addOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        padPx = Ui.dp(this, 6);
        contentW = Math.min(dm.widthPixels - Ui.dp(this, 24) - padPx * 2, Ui.dp(this, 400));
        if (contentW < Ui.dp(this, 240)) contentW = dm.widthPixels - padPx * 2;
        collapsedH = Ui.dp(this, 38);

        root = new FrameLayout(this);
        root.setPadding(padPx, padPx, padPx, padPx);
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE && expanded) collapse();
                return false;
            }
        });

        capsule = new LinearLayout(this);
        capsule.setOrientation(LinearLayout.VERTICAL);
        capsuleBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xF0191926, 0xF00A0A11});
        radiusPx = Ui.dpf(this, collapsedH / 2f);
        capsuleBg.setCornerRadius(radiusPx);
        capsuleBg.setStroke(Math.max(1, Ui.dp(this, 1)), 0x33FFFFFF);
        capsule.setBackground(capsuleBg);
        capsule.setElevation(Ui.dpf(this, 10f));

        collapsedView = buildCollapsed();
        expandedView = buildExpanded();
        expandedView.setVisibility(View.GONE);

        capsule.addView(collapsedView, new LinearLayout.LayoutParams(MATCH, MATCH));
        capsule.addView(expandedView, new LinearLayout.LayoutParams(MATCH, MATCH));
        root.addView(capsule, new FrameLayout.LayoutParams(MATCH, MATCH));

        lp = new WindowManager.LayoutParams(
                collapsedWidth() + padPx * 2,
                collapsedH + padPx * 2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.x = 0;
        lp.y = prefs.islandY() >= 0 ? prefs.islandY() : statusBarHeight() + Ui.dp(this, 2);

        try {
            wm.addView(root, lp);
            built = true;
            running = true;
        } catch (Exception e) {
            built = false;
            stopSelf();
            return;
        }
        refresh(true);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                maybeProactive();
            }
        }, 6000);
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : Ui.dp(this, 24);
    }

    private int collapsedWidth() {
        boolean hasTrack = (track != null && track.title.length() > 0) || LocalPlayer.get(this).hasSelection();
        return Ui.dp(this, hasTrack ? 210 : 126);
    }

    private View buildCollapsed() {
        LinearLayout pill = Ui.row(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);

        dotSmall = new PulseView(this);
        pill.addView(dotSmall, Ui.lp(Ui.dp(this, 16), Ui.dp(this, 16)));

        pillTitle = Ui.tv(this, "ada", 12.5f, Ui.TEXT, true);
        pillTitle.setSingleLine(true);
        pillTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp = Ui.lpw(0, WRAP, 1f);
        tp.leftMargin = Ui.dp(this, 8);
        pill.addView(pillTitle, tp);

        wave = new WaveView(this);
        LinearLayout.LayoutParams wp = Ui.lp(Ui.dp(this, 20), Ui.dp(this, 12));
        wp.leftMargin = Ui.dp(this, 6);
        pill.addView(wave, wp);

        pill.setOnTouchListener(new PillTouch());
        return pill;
    }

    private View buildExpanded() {
        LinearLayout col = Ui.col(this);
        col.setPadding(Ui.dp(this, 15), Ui.dp(this, 12), Ui.dp(this, 13), Ui.dp(this, 11));

        // header
        LinearLayout head = Ui.row(this);
        PulseView d = new PulseView(this);
        head.addView(d, Ui.lp(Ui.dp(this, 20), Ui.dp(this, 20)));
        LinearLayout names = Ui.col(this);
        TextView brand = Ui.tv(this, "ada", 15f, Ui.TEXT, true);
        brand.setIncludeFontPadding(false);
        exStatus = Ui.tv(this, "seni izliyor", 10.5f, Ui.MUTED, false);
        exStatus.setSingleLine(true);
        names.addView(brand);
        names.addView(exStatus);
        LinearLayout.LayoutParams np = Ui.lpw(0, WRAP, 1f);
        np.leftMargin = Ui.dp(this, 9);
        head.addView(names, np);

        TextView close = Ui.tv(this, "▾", 17f, Ui.MUTED, false);
        close.setPadding(Ui.dp(this, 12), Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 10));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collapse();
            }
        });
        head.addView(close);
        col.addView(head);

        // music card
        LinearLayout card = Ui.col(this);
        card.setBackground(Ui.round(0x14FFFFFF, Ui.dpf(this, 17), Math.max(1, Ui.dp(this, 1)), 0x18FFFFFF));
        card.setPadding(Ui.dp(this, 11), Ui.dp(this, 9), Ui.dp(this, 8), Ui.dp(this, 9));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        cardLp.topMargin = Ui.dp(this, 10);
        col.addView(card, cardLp);

        LinearLayout top = Ui.row(this);
        musicBadge = Ui.tv(this, "♪", 17f, 0xFFFFFFFF, true);
        musicBadge.setGravity(Gravity.CENTER);
        musicBadge.setBackground(Ui.gradient(new int[]{0xFF7C5CFF, 0xFF33D6FF}, Ui.dpf(this, 15)));
        top.addView(musicBadge, Ui.lp(Ui.dp(this, 42), Ui.dp(this, 42)));

        LinearLayout meta = Ui.col(this);
        exTrack = Ui.tv(this, "Sessizlik", 13.5f, Ui.TEXT, true);
        exTrack.setSingleLine(true);
        exTrack.setEllipsize(TextUtils.TruncateAt.END);
        exArtist = Ui.tv(this, "ada radyo", 10.5f, Ui.MUTED, false);
        exArtist.setSingleLine(true);
        exArtist.setEllipsize(TextUtils.TruncateAt.END);
        meta.addView(exTrack);
        meta.addView(exArtist);
        LinearLayout.LayoutParams mp = Ui.lpw(0, WRAP, 1f);
        mp.leftMargin = Ui.dp(this, 10);
        top.addView(meta, mp);

        TextView prev = ctrlBtn("‹‹");
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prevTrack();
            }
        });
        top.addView(prev);

        exPlay = new PlayPauseView(this);
        LinearLayout.LayoutParams pp = Ui.lp(Ui.dp(this, 32), Ui.dp(this, 32));
        pp.leftMargin = Ui.dp(this, 2);
        pp.rightMargin = Ui.dp(this, 2);
        top.addView(exPlay, pp);
        exPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMusic();
            }
        });

        TextView next = ctrlBtn("››");
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextTrack();
            }
        });
        top.addView(next);
        card.addView(top);

        // context line
        exCtx = Ui.tv(this, "◎ bağlam okunuyor", 11f, 0xFFB9B9C8, false);
        LinearLayout.LayoutParams ctxLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        ctxLp.topMargin = Ui.dp(this, 10);
        col.addView(exCtx, ctxLp);

        // scrollable feed: answer + suggestion chips
        answerScroll = new ScrollView(this);
        answerScroll.setVerticalScrollBarEnabled(false);
        LinearLayout feed = Ui.col(this);
        feed.setPadding(0, Ui.dp(this, 8), 0, 0);

        exAnswer = Ui.tv(this, "", 13f, 0xFFEDEDF6, false);
        exAnswer.setBackground(Ui.round(0x10FFFFFF, Ui.dpf(this, 14), Math.max(1, Ui.dp(this, 1)), 0x1FFFFFFF));
        exAnswer.setPadding(Ui.dp(this, 11), Ui.dp(this, 10), Ui.dp(this, 11), Ui.dp(this, 10));
        exAnswer.setVisibility(View.GONE);
        LinearLayout.LayoutParams anLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        anLp.bottomMargin = Ui.dp(this, 8);
        feed.addView(exAnswer, anLp);

        chipBox = Ui.col(this);
        feed.addView(chipBox, new LinearLayout.LayoutParams(MATCH, WRAP));

        answerScroll.addView(feed, new FrameLayout.LayoutParams(MATCH, WRAP));
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(MATCH, Ui.dp(this, 150));
        scLp.topMargin = Ui.dp(this, 4);
        col.addView(answerScroll, scLp);

        // bottom actions
        LinearLayout bottom = Ui.row(this);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        bLp.topMargin = Ui.dp(this, 8);
        bottom.setLayoutParams(bLp);

        TextView chatBtn = pillButton("Klavye");
        chatBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(IslandService.this, ChatActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                collapse();
            }
        });
        bottom.addView(chatBtn, weightLp(true));

        TextView radioBtn = pillButton("Radyo");
        radioBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LocalPlayer p = LocalPlayer.get(IslandService.this);
                if (p.isPlaying()) p.toggle();
                else p.play(p.index() >= 0 ? p.index() : 0);
            }
        });
        bottom.addView(radioBtn, weightLp(false));

        TextView refreshBtn = pillButton("Sor");
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askNow();
            }
        });
        bottom.addView(refreshBtn, weightLp(false));

        col.addView(bottom);
        return col;
    }

    private TextView ctrlBtn(String label) {
        TextView t = Ui.tv(this, label, 14f, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        t.setLayoutParams(Ui.lp(Ui.dp(this, 30), Ui.dp(this, 36)));
        return t;
    }

    private TextView pillButton(String label) {
        TextView t = Ui.tv(this, label, 11.5f, 0xFFE8E8F2, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.round(0x1AFFFFFF, Ui.dpf(this, 14), Math.max(1, Ui.dp(this, 1)), 0x1FFFFFFF));
        t.setPadding(Ui.dp(this, 8), Ui.dp(this, 9), Ui.dp(this, 8), Ui.dp(this, 9));
        return t;
    }

    private LinearLayout.LayoutParams weightLp(boolean first) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, WRAP, 1f);
        if (!first) p.leftMargin = Ui.dp(this, 6);
        return p;
    }

    // ------------------------------------------------------------- animation

    private void expand() {
        if (expanded || !built || animating) return;
        expanded = true;
        lastTouch = System.currentTimeMillis();
        collapsedView.setVisibility(View.GONE);
        expandedView.setVisibility(View.VISIBLE);
        expandedView.setAlpha(0f);
        refresh(true);
        ensureSuggestions(false);

        capsule.measure(View.MeasureSpec.makeMeasureSpec(contentW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int targetH = capsule.getMeasuredHeight();
        int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.66f);
        if (targetH > maxH) targetH = maxH;
        if (targetH < Ui.dp(this, 180)) targetH = Ui.dp(this, 180);

        animate(contentW + padPx * 2, targetH + padPx * 2, Ui.dpf(this, 27f), true);
        capsule.setScaleX(0.94f);
        capsule.setScaleY(0.92f);
        capsule.animate().scaleX(1f).scaleY(1f).setDuration(330)
                .setInterpolator(new OvershootInterpolator(0.8f)).start();
        expandedView.animate().alpha(1f).setDuration(230).start();
        animateExpandedChildren();
    }

    private void collapse() {
        if (!expanded || animating) return;
        expanded = false;
        asking = false;
        expandedView.animate().alpha(0f).setDuration(110).start();
        capsule.animate().scaleX(0.985f).scaleY(0.985f).setDuration(180)
                .setInterpolator(new DecelerateInterpolator(1.5f)).start();
        animate(collapsedWidth() + padPx * 2, collapsedH + padPx * 2,
                Ui.dpf(this, collapsedH / 2f), false);
    }

    private void animate(final int toW, final int toH, final float toRadius, final boolean expanding) {
        animating = true;
        final int fromW = lp.width;
        final int fromH = lp.height;
        final float fromR = radiusPx;
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(expanding ? 250 : 190);
        anim.setInterpolator(new DecelerateInterpolator(expanding ? 1.45f : 1.7f));
        final ArgbEvaluator colors = new ArgbEvaluator();
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                float f = (Float) va.getAnimatedValue();
                lp.width = Math.round(fromW + (toW - fromW) * f);
                lp.height = Math.round(fromH + (toH - fromH) * f);
                radiusPx = fromR + (toRadius - fromR) * f;
                if (capsuleBg != null) capsuleBg.setCornerRadius(radiusPx);
                int stroke = (Integer) colors.evaluate(expanding ? f : 1f - f,
                        0x664F46E5, 0x887C5CFF);
                if (capsuleBg != null) capsuleBg.setStroke(Math.max(1, Ui.dp(IslandService.this, 1)), stroke);
                safeUpdate();
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animating = false;
                if (capsuleBg != null) {
                    capsuleBg.setStroke(Math.max(1, Ui.dp(IslandService.this, 1)),
                            expanding ? 0x887C5CFF : 0x33FFFFFF);
                }
                if (!expanding) {
                    expandedView.setVisibility(View.GONE);
                    collapsedView.setVisibility(View.VISIBLE);
                }
                safeUpdate();
            }
        });
        anim.start();
    }

    private void animateExpandedChildren() {
        if (!(expandedView instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) expandedView;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(Ui.dpf(this, 9f));
            child.animate().alpha(1f).translationY(0f).setStartDelay(i * 32L)
                    .setDuration(250).setInterpolator(new DecelerateInterpolator(1.25f)).start();
        }
    }

    private void animateMusicBadge(boolean playing) {
        if (musicBadge == null) return;
        if (playing && badgePulse == null) {
            badgePulse = ValueAnimator.ofFloat(1f, 1.09f, 1f);
            badgePulse.setDuration(1050);
            badgePulse.setRepeatCount(ValueAnimator.INFINITE);
            badgePulse.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float scale = (Float) animation.getAnimatedValue();
                    if (musicBadge != null) {
                        musicBadge.setScaleX(scale);
                        musicBadge.setScaleY(scale);
                    }
                }
            });
            badgePulse.start();
        } else if (!playing) {
            stopBadgePulse();
        }
    }

    private void stopBadgePulse() {
        if (badgePulse != null) {
            badgePulse.cancel();
            badgePulse = null;
        }
        if (musicBadge != null) {
            musicBadge.setScaleX(1f);
            musicBadge.setScaleY(1f);
        }
    }

    private void safeUpdate() {
        try {
            if (root != null && root.getParent() != null) wm.updateViewLayout(root, lp);
        } catch (Exception ignored) {
        }
    }

    private class PillTouch implements View.OnTouchListener {
        private float downY;
        private int startY;
        private boolean moved;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = e.getRawY();
                    startY = lp.y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dy = (int) (e.getRawY() - downY);
                    if (Math.abs(dy) > Ui.dp(IslandService.this, 6)) moved = true;
                    if (moved) {
                        int maxY = getResources().getDisplayMetrics().heightPixels - Ui.dp(IslandService.this, 120);
                        lp.y = Math.max(0, Math.min(maxY, startY + dy));
                        safeUpdate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (moved) {
                        prefs.setIslandY(lp.y);
                    } else {
                        expand();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    // ---------------------------------------------------------------- refresh

    private void refresh(boolean full) {
        if (!built) return;
        try {
            LocalPlayer player = LocalPlayer.get(this);
            boolean localSelected = player.hasSelection();
            MusicBridge.Track t = MusicBridge.current(this);
            track = t;

            String label;
            boolean playing;
            if (localSelected) {
                label = player.title();
                playing = player.isPlaying();
            } else if (t != null) {
                label = t.title;
                playing = t.playing;
            } else {
                label = "ada";
                playing = false;
            }
            pillTitle.setText(label);
            wave.setActive(playing);
            dotSmall.setDotColor(playing ? Ui.ACCENT2 : Ui.ACCENT);
            animateMusicBadge(playing);

            if (!expanded && !animating) {
                int want = collapsedWidth() + padPx * 2;
                if (lp.width != want) {
                    lp.width = want;
                    safeUpdate();
                }
            }
            if (full) updateExpandedUi();

            long now = System.currentTimeMillis();
            if (now - lastNotifAt > 20000) {
                lastNotifAt = now;
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(NOTIF_ID, buildNotification());
            }
        } catch (Exception ignored) {
        }
    }

    private void updateExpandedUi() {
        if (expandedView == null) return;
        LocalPlayer player = LocalPlayer.get(this);
        ContextReader.Ctx ctx = ContextReader.read(this);

        if (player.hasSelection()) {
            exTrack.setText(player.title());
            exArtist.setText(player.subtitle());
            exPlay.setPlaying(player.isPlaying());
        } else if (track != null) {
            exTrack.setText(track.title);
            String sub = track.artist;
            if (sub == null || sub.isEmpty()) sub = ContextReader.labelOf(this, track.pkg);
            exArtist.setText(sub);
            exPlay.setPlaying(track.playing);
        } else {
            exTrack.setText("Sessizlik");
            exArtist.setText("Ada Radyo'yu başlat");
            exPlay.setPlaying(false);
        }

        StringBuilder sb = new StringBuilder("◎ ").append(ctx.appLabel);
        if (ctx.minutes > 0) sb.append(" · ").append(ctx.minutes).append(" dk");
        if (ctx.battery >= 0) sb.append(" · %").append(ctx.battery);
        exCtx.setText(sb.toString());

        if (!asking) {
            String status = "seni izliyor · " + ctx.appLabel;
            if (player.isPlaying() || (track != null && track.playing)) status = "müzik çalıyor";
            exStatus.setText(status);
        }
    }

    private void ensureSuggestions(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && !suggestions.isEmpty() && now - lastSuggestAt < 60000) return;
        ContextReader.Ctx ctx = ContextReader.read(this);
        if (suggestions.isEmpty() || force) {
            setSuggestions(SuggestionEngine.local(ctx));
        }
        if (!prefs.aiOn() || !prefs.hasKey()) return;
        lastSuggestAt = now;
        AiClient.suggestions(this, ctx.block(), new AiClient.Cb() {
            @Override
            public void onOk(String text) {
                List<String> parsed = AiClient.parseLines(text);
                if (parsed.size() >= 2) setSuggestions(parsed);
            }

            @Override
            public void onErr(String message) {
            }
        });
    }

    private void setSuggestions(List<String> list) {
        suggestions = list;
        if (chipBox == null) return;
        chipBox.removeAllViews();
        int max = Math.min(3, list.size());
        for (int i = 0; i < max; i++) {
            chipBox.addView(chip(list.get(i)));
        }
    }

    private View chip(final String question) {
        TextView t = Ui.tv(this, question, 12f, 0xFFE4E4EE, false);
        t.setBackground(Ui.round(0x14FFFFFF, Ui.dpf(this, 14), Math.max(1, Ui.dp(this, 1)), 0x18FFFFFF));
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ask(question);
            }
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        p.bottomMargin = Ui.dp(this, 6);
        t.setLayoutParams(p);
        return t;
    }

    // ------------------------------------------------------------------- ask

    private void askNow() {
        if (suggestions.isEmpty()) {
            ensureSuggestions(true);
            return;
        }
        String q = suggestions.get(random.nextInt(suggestions.size()));
        ask(q);
    }

    private void ask(final String question) {
        if (!built) return;
        lastTouch = System.currentTimeMillis();
        asking = true;
        answerScroll.setVisibility(View.VISIBLE);
        exAnswer.setVisibility(View.VISIBLE);
        exAnswer.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        exAnswer.setText("düşünüyorum…");
        exStatus.setText("düşünüyor…");

        ContextReader.Ctx ctx = ContextReader.read(this);
        List<AiClient.Msg> history = new ArrayList<>();
        String music = currentMusicLine();
        history.add(new AiClient.Msg("system", AiClient.SYSTEM_PROMPT
                + "\n\nŞu anki bağlam:\n" + ctx.block() + "\nMüzik: " + music));
        history.add(new AiClient.Msg("user", question));

        AiClient.chat(this, history, new AiClient.Cb() {
            @Override
            public void onOk(String text) {
                asking = false;
                exAnswer.setText(text);
                if (expandedView != null) updateExpandedUi();
            }

            @Override
            public void onErr(String message) {
                asking = false;
                exAnswer.setText(message);
                if (expandedView != null) updateExpandedUi();
            }
        });
    }

    private String currentMusicLine() {
        LocalPlayer p = LocalPlayer.get(this);
        if (p.hasSelection()) return p.title() + " (" + p.subtitle() + (p.isPlaying() ? ", çalıyor)" : ", duraklatılmış)");
        if (track != null) {
            return track.title + (track.artist.isEmpty() ? "" : " - " + track.artist)
                    + (track.playing ? " (çalıyor)" : " (duraklatılmış)");
        }
        return "hiçbir şey çalmıyor";
    }

    // ---------------------------------------------------------------- player

    private void toggleMusic() {
        LocalPlayer player = LocalPlayer.get(this);
        if (player.hasSelection()) {
            player.toggle();
        } else if (track != null && track.ctrl != null) {
            MusicBridge.toggle(track.ctrl);
        } else {
            player.play(0);
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                refresh(true);
            }
        }, 450);
    }

    private void nextTrack() {
        LocalPlayer player = LocalPlayer.get(this);
        if (player.hasSelection()) player.next();
        else if (track != null && track.ctrl != null) MusicBridge.next(track.ctrl);
        else player.play(0);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                refresh(true);
            }
        }, 450);
    }

    private void prevTrack() {
        LocalPlayer player = LocalPlayer.get(this);
        if (player.hasSelection()) player.prev();
        else if (track != null && track.ctrl != null) MusicBridge.prev(track.ctrl);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                refresh(true);
            }
        }, 450);
    }

    // -------------------------------------------------------------- proactive

    private void maybeProactive() {
        if (!built || expanded || animating) return;
        if (!prefs.proactive()) return;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (pm != null && !pm.isInteractive()) return;
        if (km != null && km.isKeyguardLocked()) return;

        long now = System.currentTimeMillis();
        if (now - prefs.lastProactive() < prefs.proactiveGapMin() * 60000L) return;

        ContextReader.Ctx ctx = ContextReader.read(this);
        List<String> local = SuggestionEngine.local(ctx);
        if (local.isEmpty()) return;
        String question = local.get(random.nextInt(Math.min(3, local.size())));
        prefs.setLastProactive(now);
        expand();
        answerScroll.setVisibility(View.VISIBLE);
        exAnswer.setVisibility(View.VISIBLE);
        exAnswer.setText(question);
        lastTouch = now;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!built) return;
            refresh(false);
            tickCount++;
            if (expanded && tickCount % 5 == 0) updateExpandedUi();
            if (expanded && !asking && System.currentTimeMillis() - lastTouch > 45000) collapse();
            if (!expanded) {
                if (tickCount % 10 == 0) maybeProactive();
            }
            handler.postDelayed(this, 1000);
        }
    };

    // ----------------------------------------------------------- notifications

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Ada adası",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Ada ekranın üstünde çalışırken gösterilir.");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent hide = new Intent(this, IslandService.class);
        hide.setAction(ACTION_HIDE);
        PendingIntent phi = PendingIntent.getService(this, 1, hide,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("ada")
                .setContentText(notificationText())
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            b.addAction(new Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat),
                    "Gizle", phi).build());
        }
        return b.build();
    }

    private String notificationText() {
        LocalPlayer p = LocalPlayer.get(this);
        if (p.isPlaying()) return "♪ " + p.title();
        if (track != null) return "♪ " + track.title + (track.playing ? "" : " (duraklatıldı)");
        return "ekranın üstünde bekliyor";
    }

    private void hideIsland() {
        prefs.setIslandOn(false);
        if (wm != null && root != null && root.getParent() != null) {
            try {
                wm.removeView(root);
            } catch (Exception ignored) {
            }
        }
        built = false;
        running = false;
        stopForeground(true);
        stopSelf();
    }
}
