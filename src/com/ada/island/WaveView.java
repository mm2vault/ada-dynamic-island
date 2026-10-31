package com.ada.island;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Three little bars that dance while audio plays, rest flat when paused. */
public class WaveView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private boolean active = false;
    private float t = 0f;
    private int color = Ui.ACCENT2;

    public WaveView(Context c) {
        super(c);
        p.setColor(color);
    }

    public void setActive(boolean a) {
        if (active != a) {
            active = a;
            invalidate();
        }
        if (a) postInvalidateOnAnimation();
    }

    public void setBarColor(int c) {
        color = c;
        p.setColor(c);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth();
        int h = getHeight();
        int bars = 3;
        float gap = w * 0.16f;
        float bw = (w - gap * (bars - 1)) / bars;
        t += 0.09f;
        for (int i = 0; i < bars; i++) {
            float ph = t + i * 1.9f;
            float amp = active ? (0.35f + 0.32f * (float) (Math.sin(ph) * 0.5 + 0.5) + 0.3f * (float) (Math.sin(ph * 2.3f) * 0.5 + 0.5)) : 0.28f;
            float bh = Math.max(h * 0.14f, h * Math.min(1f, amp));
            float left = i * (bw + gap);
            float top = (h - bh) / 2f;
            r.set(left, top, left + bw, top + bh);
            cv.drawRoundRect(r, bw / 2f, bw / 2f, p);
        }
        if (active) postInvalidateOnAnimation();
    }
}
