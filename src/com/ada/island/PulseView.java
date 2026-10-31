package com.ada.island;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Glowing pulsing dot used as Ada's "alive" indicator. */
public class PulseView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float phase = 0f;
    private int color = Ui.ACCENT;
    private boolean running = true;

    public PulseView(Context c) {
        super(c);
        postInvalidateDelayed(33);
    }

    public void setDotColor(int c) {
        color = c;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        phase += 0.022f;
        if (phase > 1f) phase -= 1f;

        int w = getWidth();
        int h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) / 2f;

        float ringR = r * (0.42f + 0.55f * phase);
        int alpha = (int) (165 * (1f - phase));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.11f);
        p.setColor((color & 0x00FFFFFF) | (Math.max(0, alpha) << 24));
        cv.drawCircle(cx, cy, ringR, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setShadowLayer(r * 0.45f, 0f, 0f, color);
        cv.drawCircle(cx, cy, r * 0.36f, p);
        p.clearShadowLayer();
        if (running) postInvalidateDelayed(33);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
    }
}
