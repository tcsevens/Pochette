package app.pochette.tv.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Clouds and fog drawn from seamless textures that drift at different speeds, so no loop is ever visible.
 * The day sky is drawn here; at night the layers sit over the starry-night scene.
 */
final class WeatherArt {
    static Context ctx;
    private final Bitmap[] cloud = new Bitmap[3], cloudN = new Bitmap[3], fog = new Bitmap[2];
    private boolean loaded, loadedN;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    private static Bitmap load(String name) {
        try {
            int id = ctx.getResources().getIdentifier(name, "raw", ctx.getPackageName());
            if (id == 0) return null;
            InputStream in = ctx.getResources().openRawResource(id);
            if (in == null) return null;
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            byte[] d = b.toByteArray();
            return BitmapFactory.decodeByteArray(d, 0, d.length, null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Copy with the colours multiplied (night tint). */
    private static Bitmap tint(Bitmap s, float mr, float mg, float mb) {
        if (s == null) return null;
        int w = s.getWidth(), h = s.getHeight();
        Bitmap o = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] px = new int[w];
        for (int y = 0; y < h; y++) {
            s.getPixels(px, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                int v = px[x];
                px[x] = (v & 0xFF000000) | ((int) (((v >> 16) & 255) * mr) << 16) | ((int) (((v >> 8) & 255) * mg) << 8) | (int) ((v & 255) * mb);
            }
            o.setPixels(px, 0, w, 0, y, w, 1);
        }
        return o;
    }

    private void ensure() {
        if (loaded) return;
        loaded = true;
        for (int i = 0; i < 3; i++) cloud[i] = load("wx_cloud" + (i + 1));
        for (int i = 0; i < 2; i++) fog[i] = load("wx_fog" + (i + 1));
    }

    private void ensureNight() {
        ensure();
        if (loadedN) return;
        loadedN = true;
        for (int i = 0; i < 3; i++) cloudN[i] = tint(cloud[i], 0.50f, 0.56f, 0.80f);
        for (int i = 0; i < 2; i++) if (fog[i] != null) fog[i] = tint(fog[i], 0.62f, 0.68f, 0.80f);
    }

    /** One texture repeated across the width, drifting left at a fraction of the width per second. */
    private void band(Canvas c, Bitmap b, double t, float speed, float top, float h, int W, int alpha) {
        if (b == null) return;
        float tw = b.getWidth() * h / b.getHeight();
        float x = (float) (-((t * speed * W) % tw));
        if (x > 0) x -= tw;
        p.setShader(null);
        p.setColor(0xFFFFFFFF);
        p.setAlpha(alpha);
        src.set(0, 0, b.getWidth(), b.getHeight());
        for (float xx = x; xx < W; xx += tw) {
            dst.set(xx, top, xx + tw + 1f, top + h);
            c.drawBitmap(b, src, dst, p);
        }
        p.setAlpha(255);
    }

    /** Clouds drifting in front of the night scene. */
    void nightClouds(Canvas c, int W, int H, double t) {
        ensureNight();
        float[] sc = {0.9f, 1.15f, 1.4f}, sp = {0.004f, 0.007f, 0.011f};
        int[] al = {190, 170, 150};
        for (int i = 0; i < 3; i++) band(c, cloudN[i], t, sp[i], -H * 0.12f, H * sc[i], W, al[i]);
    }

    /** Fog banks drifting in opposite directions, denser near the ground. */
    void nightFog(Canvas c, int W, int H, double t) {
        ensureNight();
        band(c, fog[0], t, 0.0035f, H * 0.38f, H * 0.62f, W, 150);
        band(c, fog[1], t, -0.006f, H * 0.52f, H * 0.55f, W, 120);
        band(c, fog[0], t, 0.009f, H * 0.66f, H * 0.4f, W, 90);
    }
}
