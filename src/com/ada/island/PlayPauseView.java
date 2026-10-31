package com.ada.island;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Triangle when paused, two bars when playing. */
public class PlayPauseView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tri = new Path();
    private final RectF rect = new RectF();
    private boolean playing = false;
    private int color = Ui.TEXT;

    public PlayPauseView(Context c) {
        super(c);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
    }

    public void setPlaying(boolean v) {
        if (playing != v) {
            playing = v;
            invalidate();
        }
    }

    public void setTint(int c) {
        color = c;
        p.setColor(c);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth();
        int h = getHeight();
        float size = Math.min(w, h) * 0.62f;
        float cx = w / 2f;
        float cy = h / 2f;
        p.setColor(color);
        if (playing) {
            float bw = size * 0.26f;
            float gap = size * 0.22f;
            float top = cy - size / 2f;
            rect.set(cx - gap / 2f - bw, top, cx - gap / 2f, top + size);
            cv.drawRoundRect(rect, bw * 0.35f, bw * 0.35f, p);
            rect.set(cx + gap / 2f, top, cx + gap / 2f + bw, top + size);
            cv.drawRoundRect(rect, bw * 0.35f, bw * 0.35f, p);
        } else {
            tri.reset();
            tri.moveTo(cx - size * 0.26f, cy - size / 2f);
            tri.lineTo(cx + size * 0.40f, cy);
            tri.lineTo(cx - size * 0.26f, cy + size / 2f);
            tri.close();
            cv.drawPath(tri, p);
        }
    }
}
