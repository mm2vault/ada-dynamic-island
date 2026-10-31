package com.ada.island;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers for building views programmatically. */
public class Ui {

    public static final int TEXT = 0xFFF2F2F7;
    public static final int MUTED = 0xFF9A9AA8;
    public static final int ACCENT = 0xFF7C5CFF;
    public static final int ACCENT2 = 0xFF33D6FF;
    public static final int CARD = 0xFF12121A;

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static float dpf(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    public static GradientDrawable round(int fill, float radiusPx) {
        return round(fill, radiusPx, 0, 0);
    }

    public static GradientDrawable round(int fill, float radiusPx, int strokePx, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(radiusPx);
        if (strokePx > 0) d.setStroke(strokePx, strokeColor);
        return d;
    }

    public static GradientDrawable gradient(int[] colors, float radiusPx) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static TextView tv(Context c, CharSequence s, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(dpf(c, 3f), 1f);
        if (bold) t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        return t;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpw(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    public static LinearLayout.LayoutParams margins(int w, int h, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(left, top, right, bottom);
        return p;
    }

    public static View spacer(Context c, int hPx) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, hPx));
        return v;
    }

    public static int withAlpha(int color, float alpha) {
        int a = (int) (Color.alpha(color) * alpha);
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
