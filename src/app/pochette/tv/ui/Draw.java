package app.pochette.tv.ui;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

/** Small stateless drawing helpers shared by the views. */
final class Draw {
    private Draw() {}

    /** Multiplies the RGB channels of a colour by f (clamped) and makes it opaque. */
    static int shade(int c, float f) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * f));
        int g = Math.min(255, (int) (((c >> 8) & 255) * f));
        int b = Math.min(255, (int) ((c & 255) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Builds a single-style, ellipsized text layout. */
    static StaticLayout layout(String text, Typeface tf, float size, int color, int width, int maxLines,
                                Layout.Alignment al, float spacing) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(color);
        p.setLetterSpacing(spacing);
        if (text == null) text = "";
        return StaticLayout.Builder.obtain(text, 0, text.length(), p, Math.max(1, width))
                .setAlignment(al)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .setLineSpacing(0f, 0.95f)
                .build();
    }
}
