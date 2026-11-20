package app.pochette.tv.ui;

import android.graphics.*;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import java.util.HashMap;
import java.util.Map;

import app.pochette.tv.core.T;
import app.pochette.tv.core.TrackState;

/** Bitmaps for the vinyl layout: the record itself, its sheen and soft drop shadows. */
final class DiscArt {
    private static final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private static final Map<String, Bitmap> shadows = new HashMap<String, Bitmap>();

    private DiscArt() {}

    static Bitmap makeDisc(Bitmap art, int accent, int[] palette, TrackState st, int style, int D) {
        Bitmap b = Bitmap.createBitmap(D, D, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float r = D / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Shader.TileMode CL = Shader.TileMode.CLAMP;
        int[] pal = palette != null ? palette : new int[]{accent, Draw.shade(accent, 0.6f), Draw.shade(accent, 1.25f), Draw.shade(accent, 0.4f)};

        // 1. the vinyl itself
        if (style == 1) {
            // translucent coloured vinyl, in the album's dominant colour
            p.setShader(new RadialGradient(r, r, r, new int[]{Draw.shade(accent, 1.25f), accent, Draw.shade(accent, 0.62f)},
                    new float[]{0f, 0.55f, 1f}, CL));
            c.drawCircle(r, r, r, p);
        } else if (style == 2) {
            // marbled: swirl of the artwork's colours
            p.setShader(new SweepGradient(r * 0.94f, r * 1.05f,
                    new int[]{pal[0], pal[1], pal[2], pal[3], pal[0], pal[2], pal[1], pal[0]}, null));
            c.drawCircle(r, r, r, p);
            float[][] blobs = {{0.30f, 0.70f, 0.42f}, {0.72f, 0.28f, 0.38f}, {0.70f, 0.75f, 0.30f}, {0.25f, 0.25f, 0.30f}};
            for (int i = 0; i < blobs.length; i++) {
                int col = pal[(i + 1) % 4];
                p.setShader(new RadialGradient(D * blobs[i][0], D * blobs[i][1], D * blobs[i][2],
                        (col & 0x00FFFFFF) | 0x99000000, col & 0x00FFFFFF, CL));
                c.drawCircle(r, r, r, p);
            }
        } else if (style == 3 && art != null) {
            // picture disc: the artwork printed on the whole record
            c.save();
            Path clip = new Path();
            clip.addCircle(r, r, r, Path.Direction.CW);
            c.clipPath(clip);
            int w = art.getWidth(), h = art.getHeight(), s = Math.min(w, h);
            p.setShader(null);
            c.drawBitmap(art, new Rect((w - s) / 2, (h - s) / 2, (w - s) / 2 + s, (h - s) / 2 + s), new RectF(0, 0, D, D), new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            c.restore();
        } else {
            p.setShader(new RadialGradient(r, r, r, new int[]{0xFF202020, 0xFF0C0C0C, 0xFF181818, 0xFF0A0A0A},
                    new float[]{0f, 0.45f, 0.93f, 1f}, CL));
            c.drawCircle(r, r, r, p);
        }
        p.setShader(null);

        // 2. grooves
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, D / 900f));
        boolean dark = style == 0;
        int i = 0;
        float inner = style == 3 ? 0.16f : 0.40f;
        for (float rr = r * inner; rr < r * 0.965f; rr += r * 0.0105f, i++) {
            if (dark) p.setColor((i % 3 == 0) ? 0x22FFFFFF : 0x10FFFFFF);
            else p.setColor((i % 3 == 0) ? 0x2A000000 : (style == 3 ? 0x12FFFFFF : 0x16000000));
            c.drawCircle(r, r, rr, p);
        }
        p.setStrokeWidth(Math.max(1f, D / 300f));
        p.setColor(dark ? 0x30FFFFFF : 0x40000000);
        c.drawCircle(r, r, r - p.getStrokeWidth() / 2f, p);
        p.setStyle(Paint.Style.FILL);

        // 3. centre label
        float lr = r * 0.36f;
        if (style == 2) {
            p.setColor(0xFFF1E6D2);
            c.drawCircle(r, r, lr, p);
            String name = st != null && st.artist.length() > 0 ? st.artist : (st != null ? st.title : "");
            StaticLayout l = Draw.layout(name.toUpperCase(T.locale()), bold, lr * 0.20f, Draw.shade(pal[0], 0.55f) | 0xFF000000,
                    (int) (lr * 1.45f), 3, Layout.Alignment.ALIGN_CENTER, 0.08f);
            c.save();
            c.translate(r - lr * 0.725f, r - l.getHeight() / 2f - lr * 0.18f);
            l.draw(c);
            c.restore();
        } else if (style == 3) {
            p.setColor(0x55000000);
            c.drawCircle(r, r, r * 0.09f, p);
        } else {
            c.save();
            Path clip = new Path();
            clip.addCircle(r, r, lr, Path.Direction.CW);
            c.clipPath(clip);
            if (art != null) {
                int w = art.getWidth(), h = art.getHeight(), s = Math.min(w, h);
                c.drawBitmap(art, new Rect((w - s) / 2, (h - s) / 2, (w - s) / 2 + s, (h - s) / 2 + s),
                        new RectF(r - lr, r - lr, r + lr, r + lr), new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
                // a printed label is a bit duller than the artwork on the sleeve
                Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
                dim.setColor(0x38000000);
                c.drawCircle(r, r, lr, dim);
            } else {
                p.setColor(accent);
                c.drawCircle(r, r, lr, p);
            }
            c.restore();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, D / 500f));
            p.setColor(0x33000000);
            c.drawCircle(r, r, lr, p);
            p.setStyle(Paint.Style.FILL);
        }
        p.setColor(0xFF0A0A0A);
        c.drawCircle(r, r, r * 0.028f, p);
        return b;
    }

    static Bitmap makeSheen(int D) {
        Bitmap b = Bitmap.createBitmap(D, D, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float r = D / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new SweepGradient(r, r,
                new int[]{0x00FFFFFF, 0x1CFFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x1CFFFFFF, 0x00FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.08f, 0.18f, 0.5f, 0.58f, 0.68f, 1f}));
        c.drawCircle(r, r, r, p);
        p.setShader(null);
        p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
        c.drawCircle(r, r, r * 0.37f, p);
        return b;
    }

    static Bitmap shadow(String kind, int size, float rad) {
        String k = kind + size;
        Bitmap b = shadows.get(k);
        if (b != null) return b;
        int blur = Math.max(4, size / 14);
        int full = size + blur * 4;
        b = Bitmap.createBitmap(full, full, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xB0000000);
        p.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
        float o = blur * 2;
        if ("ci".equals(kind)) c.drawCircle(full / 2f, full / 2f, size / 2f, p);
        else c.drawRoundRect(new RectF(o, o, o + size, o + size), rad, rad, p);
        shadows.put(k, b);
        return b;
    }
}
