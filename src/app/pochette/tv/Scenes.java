package app.pochette.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.LinearGradient;
import android.graphics.Shader;

/**
 * Richer ambiance scenes (original artwork, drawn with shapes): several planes, soft light and slow motion.
 * Each scene has a static background built once (build) and moving details drawn every frame (effects).
 */
final class Scenes {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private static final Shader.TileMode CL = Shader.TileMode.CLAMP;

    // ------------------------------------------------------------------ helpers

    static float fr(float v) { return v - (float) Math.floor(v); }

    /** Deterministic pseudo-random value in 0..1. */
    static float rnd(int i, int j) {
        int h = i * 374761393 + j * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / 16777216f;
    }

    /** Soft round light: full colour in the middle, fading smoothly to nothing at r. */
    static void glow(Canvas c, Paint q, float x, float y, float r, int color) {
        int a = color >>> 24, rgb = color & 0xFFFFFF;
        q.setStyle(Paint.Style.FILL);
        Scenes2.sh(q, new RadialGradient(x, y, r, new int[]{color, (a * 45 / 100) << 24 | rgb, (a * 12 / 100) << 24 | rgb, rgb},
                new float[]{0f, 0.35f, 0.7f, 1f}, CL));
        c.drawCircle(x, y, r, q);
        q.setShader(null);
    }

    static void vgrad(Canvas c, Paint q, float l, float t, float r, float b, int[] cols, float[] pos) {
        q.setStyle(Paint.Style.FILL);
        Scenes2.sh(q, new LinearGradient(0, t, 0, b, cols, pos, CL));
        c.drawRect(l, t, r, b, q);
        q.setShader(null);
    }

    // ------------------------------------------------------------------ backgrounds

    static Bitmap build(int theme, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path pa = new Path();
        switch (theme) {
            default: nightBg(c, q, pa, w, h); break;
        }
        return b;
    }

    // ---- chalet in the snow ------------------------------------------------------------------------------------

    static void ridge(Canvas c, Paint q, Path pa, int w, int h, float y, float amp, float f1, float f2, float ph, int top, int bottom) {
        pa.reset();
        pa.moveTo(0, h);
        for (int x = 0; x <= w; x += Math.max(2, w / 160)) {
            float u = x / (float) w;
            float yy = y + amp * (float) (Math.sin(u * f1 + ph) * 0.6 + Math.sin(u * f2 + ph * 2.1) * 0.4);
            pa.lineTo(x, yy);
        }
        pa.lineTo(w, h);
        pa.close();
        q.setStyle(Paint.Style.FILL);
        Scenes2.sh(q, new LinearGradient(0, y - amp, 0, h, top, bottom, CL));
        c.drawPath(pa, q);
        q.setShader(null);
    }

    static void pine(Canvas c, Paint q, Path pa, float x, float base, float h, int body, int snow) {
        float w0 = h * 0.36f;
        q.setStyle(Paint.Style.FILL);
        q.setColor(0xFF1A1410);
        c.drawRect(x - h * 0.02f, base - h * 0.08f, x + h * 0.02f, base, q);
        for (int t = 0; t < 4; t++) {
            float ty = base - h + t * h * 0.2f, th = h * 0.36f;
            float w = w0 * (0.42f + 0.58f * (t + 1) / 4f);
            pa.reset(); pa.moveTo(x, ty); pa.lineTo(x - w / 2f, ty + th); pa.lineTo(x + w / 2f, ty + th); pa.close();
            q.setColor(body); c.drawPath(pa, q);
            pa.reset(); pa.moveTo(x, ty); pa.lineTo(x + w * 0.5f, ty + th); pa.lineTo(x + w * 0.1f, ty + th * 0.88f); pa.lineTo(x - w * 0.05f, ty + th * 0.5f); pa.close();
            q.setColor(snow); c.drawPath(pa, q);                      // snow lying on the right side of each tier
        }
    }

    // ---- rain on a window --------------------------------------------------------------------------------------

    // ---- starry night ------------------------------------------------------------------------------------------

    private static void nightBg(Canvas c, Paint q, Path pa, int w, int h) {
        vgrad(c, q, 0, 0, w, h, new int[]{0xFF04061A, 0xFF0B1240, 0xFF1A2A6B, 0xFF2B4A86}, new float[]{0f, 0.45f, 0.8f, 1f});
        // milky way: a diagonal band of soft clouds and dense dust
        for (int i = 0; i < 26; i++) {
            float u = i / 25f;
            float x = w * (0.05f + 0.9f * u), y = h * (0.62f - 0.5f * u) + (rnd(i, 31) - 0.5f) * h * 0.08f;
            glow(c, q, x, y, h * (0.16f + 0.12f * rnd(i, 32)), ((int) (22 + 22 * rnd(i, 33)) << 24) | (i % 3 == 0 ? 0xA070E0 : 0x6FA3E8));
        }
        for (int i = 0; i < 900; i++) {
            float u = rnd(i, 41), off = (rnd(i, 42) + rnd(i, 43) - 1f) * h * 0.13f;
            float x = w * (0.02f + 0.96f * u), y = h * (0.64f - 0.52f * u) + off;
            if (rnd(i, 44) < 0.35f) { x = rnd(i, 45) * w; y = rnd(i, 46) * h * 0.85f; }
            q.setColor(((int) (35 + 150 * rnd(i, 47)) << 24) | (rnd(i, 48) < 0.2f ? 0xFFE0B0 : 0xDCE8FF));
            c.drawCircle(x, y, h * (0.0008f + 0.0012f * rnd(i, 49)), q);
        }
        // a few distant planets, soft and small, to give depth
        float[][] far = {{0.18f, 0.30f, 0.030f}, {0.44f, 0.17f, 0.018f}};
        int[] farCol = {0xFF6F5AB8, 0xFF4F8FB8};
        for (int i = 0; i < far.length; i++) {
            float fx = w * far[i][0], fy = h * far[i][1], fr = h * far[i][2];
            glow(c, q, fx, fy, fr * 3.2f, 0x286FA3D8);
            q.setColor(farCol[i]); c.drawCircle(fx, fy, fr, q);
            q.setColor(0x55000000); c.drawCircle(fx + fr * 0.35f, fy + fr * 0.25f, fr * 0.9f, q);
            q.setColor(farCol[i]); c.drawCircle(fx - fr * 0.1f, fy - fr * 0.08f, fr * 0.88f, q);
        }
        // moon with halo
        float mx = w * 0.92f, my = h * 0.20f, mr = h * 0.05f;
        glow(c, q, mx, my, mr * 5f, 0x40E9D9A8);
        {   // true crescent: outer circle minus an offset circle, built as a polygon
            float dx = mr * 0.42f, dy = -mr * 0.14f, r2 = mr * 0.92f;
            pa.reset(); boolean first = true;
            for (int i = 0; i <= 120; i++) {
                double a = Math.PI * 2 * i / 120;
                float x = mx + (float) Math.cos(a) * mr, y = my + (float) Math.sin(a) * mr;
                float ddx = x - (mx + dx), ddy = y - (my + dy);
                if (ddx * ddx + ddy * ddy > r2 * r2) { if (first) { pa.moveTo(x, y); first = false; } else pa.lineTo(x, y); }
            }
            for (int i = 120; i >= 0; i--) {
                double a = Math.PI * 2 * i / 120;
                float x = mx + dx + (float) Math.cos(a) * r2, y = my + dy + (float) Math.sin(a) * r2;
                float ddx = x - mx, ddy = y - my;
                if (ddx * ddx + ddy * ddy < mr * mr) pa.lineTo(x, y);
            }
            pa.close(); q.setColor(0xFFE9D9A8); c.drawPath(pa, q);
        }
        Scenes2.sh(q, new RadialGradient(w / 2f, h / 2f, w * 0.75f, 0x00000000, 0x55000000, CL));
        c.drawRect(0, 0, w, h, q); q.setShader(null);
    }

    // ------------------------------------------------------------------ moving details

    void effects(Canvas c, int theme, int W, int H, double t) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        nightSky(c, W, H, t);
    }

    private void nightSky(Canvas c, int W, int H, double t) {
        // twinkling stars with a small halo
        for (int i = 0; i < 70; i++) {
            float x = rnd(i, 81) * W, y = rnd(i, 82) * H * 0.8f;
            float tw = (float) (0.5 + 0.5 * Math.sin(t * (0.5 + 0.9 * rnd(i, 83)) + i * 1.9));
            float r = H * (0.0016f + 0.0022f * rnd(i, 84));
            glow(c, p, x, y, r * 6f, ((int) (60 * tw) << 24) | 0xBFD4FF);
            p.setColor(((int) (120 + 135 * tw) << 24) | 0xFFFFFF);
            c.drawCircle(x, y, r, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, H * 0.0018f));
        for (int i = 0; i < 5; i++) {                                    // sparkling crosses on the brightest stars
            float x = W * (0.08f + 0.2f * i + 0.04f * rnd(i, 91)), y = H * (0.10f + 0.30f * rnd(i, 92));
            float tw = (float) (0.5 + 0.5 * Math.sin(t * 1.1 + i * 2.2));
            float s = H * (0.008f + 0.010f * tw);
            p.setColor(((int) (70 + 150 * tw) << 24) | 0xFFE9A8);
            c.drawLine(x - s, y, x + s, y, p); c.drawLine(x, y - s, x, y + s, p);
        }
        double cyc = t % 19.0;                                           // a shooting star every now and then
        if (cyc < 1.3) {
            float u = (float) (cyc / 1.3);
            float x = W * (0.70f - 0.40f * u), y = H * (0.06f + 0.26f * u);
            for (int k = 0; k < 14; k++) {
                float kk = k / 14f;
                p.setColor(((int) (210 * Math.sin(Math.PI * u) * (1 - kk)) << 24) | 0xFFFFFF);
                c.drawLine(x + kk * W * 0.09f, y - kk * H * 0.045f, x + (kk + 0.08f) * W * 0.09f, y - (kk + 0.08f) * H * 0.045f, p);
            }
        }
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 26; i++) {                                   // slow motes of light drifting up, in front of the sky
            float life = (float) ((t * (0.012 + 0.012 * rnd(i, 101)) + rnd(i, 102)) % 1.0);
            float x = W * rnd(i, 103) + (float) Math.sin(t * 0.3 + i) * W * 0.01f, y = H * (1.0f - life);
            glow(c, p, x, y, H * (0.004f + 0.006f * rnd(i, 104)), ((int) (90 * Math.sin(Math.PI * life)) << 24) | 0xBFD4FF);
        }
        planet(c, W, H, t);
    }

    // ------------------------------------------------------------------ the little-prince asteroids

    private static final int[] W_BASE = {0xFF9A7A8C, 0xFF5E6A86, 0xFF8C86A8, 0xFF5F8A78, 0xFFA88A5E, 0xFF7A5E8E};
    private static final int[] W_RIM = {0xFFF2C9A0, 0xFF9FC4FF, 0xFFE0D4FF, 0xFFBDEBC8, 0xFFFFE0A0, 0xFFE8B8FF};
    private static final double PLANET_CYCLE = 56.0;
    private float pcx, pcy, ppr, pth;

    /** Screen position of the point at radius r from the planet centre, angle a (rad) from the planet's own "up". */
    private float spx(float r, float a) { return pcx + r * (float) Math.sin(pth + a); }
    private float spy(float r, float a) { return pcy - r * (float) Math.cos(pth + a); }

    private static float smooth(float x) { x = Math.max(0f, Math.min(1f, x)); return x * x * (3f - 2f * x); }

    private void planet(Canvas c, int W, int H, double t) {
        int k = (int) (t / PLANET_CYCLE) % 6;
        float u = (float) ((t % PLANET_CYCLE) / PLANET_CYCLE);
        float e = u < 0.07f ? 1f - smooth(u / 0.07f) : (u > 0.93f ? smooth((u - 0.93f) / 0.07f) : 0f);
        pth = (0.5f - u) * (float) Math.toRadians(44);
        ppr = H * 0.46f; pcx = W * 0.76f; pcy = H * 0.86f + ppr + e * H * 0.62f;
        int base = W_BASE[k], rim = W_RIM[k];
        glow(c, p, pcx, pcy, ppr * 1.16f, (0x50 << 24) | (rim & 0xFFFFFF));
        p.setStyle(Paint.Style.FILL); p.setColor(base);
        c.drawCircle(pcx, pcy, ppr, p);
        c.save();
        c.translate(pcx, pcy);
        c.rotate((float) Math.toDegrees(pth), 0, 0);
        int sd = 200 + k * 40;
        for (int i = 0; i < 16; i++) {                                   // broad tonal patches (plains, dust)
            float an = rnd(i, sd) * 6.2832f, rr = ppr * (0.55f + 0.38f * rnd(i, sd + 1));
            float x = (float) Math.sin(an) * rr, y = -(float) Math.cos(an) * rr, w = ppr * (0.05f + 0.08f * rnd(i, sd + 2)), h2 = w * (0.4f + 0.4f * rnd(i, sd + 3));
            p.setColor(rnd(i, sd + 4) < 0.5f ? 0x1CFFFFFF : 0x1E000000);
            c.drawOval(x - w, y - h2, x + w, y + h2, p);
        }
        for (int i = 0; i < 14; i++) {                                   // craters: dark bowl, bright rim
            float an = rnd(i, sd + 5) * 6.2832f, rr = ppr * (0.60f + 0.34f * rnd(i, sd + 6));
            float x = (float) Math.sin(an) * rr, y = -(float) Math.cos(an) * rr, r = ppr * (0.012f + 0.035f * rnd(i, sd + 7));
            p.setColor(0x38FFFFFF); c.drawCircle(x, y, r * 1.15f, p);
            p.setColor(0x55000000); c.drawCircle(x + r * 0.12f, y + r * 0.15f, r, p);
        }
        for (int i = 0; i < 70; i++) {                                   // fine grain
            float an = rnd(i, sd + 8) * 6.2832f, rr = ppr * (0.5f + 0.48f * rnd(i, sd + 9));
            p.setColor(rnd(i, sd + 10) < 0.5f ? 0x30FFFFFF : 0x30000000);
            c.drawCircle((float) Math.sin(an) * rr, -(float) Math.cos(an) * rr, H * 0.0016f, p);
        }
        float sway = (float) Math.sin(t * 0.7) * H * 0.006f;
        if (k == 0) {
            stand(c, 0.30f); cone(c, 0f, H * 0.035f, H * 0.04f, 0xFF4A3750, false); c.restore();
            stand(c, 0f);
            p.setStyle(Paint.Style.FILL); p.setColor(0x16FFFFFF); c.drawCircle(0, -H * 0.075f, H * 0.082f, p);
            float ph = H * 0.115f;
            path.reset(); path.moveTo(0, H * 0.004f); path.quadTo(sway * 0.5f, -ph * 0.55f, sway, -ph);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(3f, H * 0.005f)); p.setColor(0xFF7BBF6A); c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL);
            leaf(c, sway * 0.3f, -ph * 0.38f, -H * 0.04f, 0xFF7BBF6A); leaf(c, sway * 0.6f, -ph * 0.62f, H * 0.045f, 0xFF6AAE5C);
            float bud = (float) (1f + 0.06f * Math.sin(t * 1.4));
            glow(c, p, sway, -ph - H * 0.01f, H * 0.06f, 0x40E8665A);
            p.setColor(0xFFE8665A); c.drawCircle(sway, -ph - H * 0.01f, H * 0.013f * bud, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2f, H * 0.003f)); p.setColor(0x90FFFFFF);   // the glass globe
            c.drawCircle(0, -H * 0.075f, H * 0.082f, p);
            p.setStrokeWidth(Math.max(2f, H * 0.004f)); p.setColor(0x60FFFFFF);
            c.drawArc(new android.graphics.RectF(-H * 0.062f, -H * 0.137f, H * 0.062f, -H * 0.013f), 200f, 40f, false, p);
            p.setStyle(Paint.Style.FILL);
            c.restore();
        } else if (k == 1) {
            stand(c, -0.22f); cone(c, 0f, H * 0.065f, H * 0.085f, 0xFF2B2E44, true); c.restore();
            stand(c, 0.20f); cone(c, 0f, H * 0.04f, H * 0.045f, 0xFF2B2E44, false); c.restore();
        } else if (k == 2) {
            stand(c, 0f);
            p.setColor(0xFF1E1A2E);
            c.drawRect(-H * 0.0025f, -H * 0.12f, H * 0.0025f, H * 0.004f, p);
            c.drawRect(-H * 0.014f, -H * 0.004f, H * 0.014f, H * 0.004f, p);
            c.drawRect(-H * 0.011f, -H * 0.148f, H * 0.011f, -H * 0.12f, p);
            c.restore();
        } else if (k == 4) {                                             // the fox among the wheat
            stand(c, 0f);
            for (int i = 0; i < 26; i++) {
                float x = (i - 13) * H * 0.0075f + (rnd(i, 3) - 0.5f) * H * 0.004f, hh = H * (0.05f + 0.025f * rnd(i, 4));
                float sw = (float) Math.sin(t * 1.3 + i * 0.7) * H * 0.004f;
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1.5f, H * 0.0022f)); p.setColor(0xFFD9B35A);
                c.drawLine(x, H * 0.004f, x + sw, -hh, p);
                p.setStyle(Paint.Style.FILL); c.drawOval(x + sw - H * 0.003f, -hh - H * 0.012f, x + sw + H * 0.003f, -hh + H * 0.002f, p);
            }
            p.setStyle(Paint.Style.FILL); p.setColor(0xFFD9772E);                 // fox, sitting, seen from the side
            float fx = H * 0.07f;
            path.reset(); path.moveTo(fx - H * 0.02f, 0); path.lineTo(fx - H * 0.016f, -H * 0.045f); path.lineTo(fx + H * 0.006f, -H * 0.05f);
            path.lineTo(fx + H * 0.012f, -H * 0.075f); path.lineTo(fx + H * 0.02f, -H * 0.05f); path.lineTo(fx + H * 0.036f, -H * 0.044f);
            path.lineTo(fx + H * 0.026f, -H * 0.035f); path.lineTo(fx + H * 0.016f, 0); path.close();
            c.drawPath(path, p);
            float tw = (float) Math.sin(t * 1.1) * H * 0.006f;
            path.reset(); path.moveTo(fx - H * 0.02f, -H * 0.006f); path.quadTo(fx - H * 0.055f, -H * 0.01f + tw, fx - H * 0.058f, -H * 0.04f + tw);
            path.quadTo(fx - H * 0.036f, -H * 0.03f, fx - H * 0.016f, -H * 0.03f); path.close();
            c.drawPath(path, p);
            p.setColor(0xFFF6E9D0); c.drawCircle(fx + H * 0.032f, -H * 0.04f, H * 0.005f, p);
            c.restore();
        } else if (k == 5) {                                             // the king's throne
            stand(c, 0f);
            p.setColor(0xFF3B2A52);
            c.drawRect(-H * 0.03f, -H * 0.02f, H * 0.03f, H * 0.004f, p);
            c.drawRect(-H * 0.03f, -H * 0.09f, -H * 0.018f, -H * 0.02f, p); c.drawRect(H * 0.018f, -H * 0.09f, H * 0.03f, -H * 0.02f, p);
            c.drawRect(-H * 0.03f, -H * 0.13f, H * 0.03f, -H * 0.09f, p);
            p.setColor(0xFFB8344A); c.drawRect(-H * 0.018f, -H * 0.075f, H * 0.018f, -H * 0.02f, p);   // cape / seat
            p.setColor(0xFFF2D6A8); c.drawCircle(0, -H * 0.09f, H * 0.012f, p);                    // head
            p.setColor(0xFFFFD35A);                                                                // crown
            path.reset(); path.moveTo(-H * 0.012f, -H * 0.098f); path.lineTo(-H * 0.012f, -H * 0.112f); path.lineTo(-H * 0.006f, -H * 0.105f); path.lineTo(0, -H * 0.115f);
            path.lineTo(H * 0.006f, -H * 0.105f); path.lineTo(H * 0.012f, -H * 0.112f); path.lineTo(H * 0.012f, -H * 0.098f); path.close(); c.drawPath(path, p);
            c.restore();
        } else {
            stand(c, 0f); c.scale(0.85f, 0.85f);
            path.reset(); path.moveTo(-H * 0.05f, H * 0.006f); path.quadTo(-H * 0.02f, -H * 0.01f, -H * 0.014f, -H * 0.06f);
            path.lineTo(-H * 0.02f, -H * 0.11f); path.lineTo(H * 0.02f, -H * 0.11f); path.lineTo(H * 0.014f, -H * 0.06f);
            path.quadTo(H * 0.02f, -H * 0.01f, H * 0.05f, H * 0.006f); path.close();
            p.setColor(0xFF4A3A34); c.drawPath(path, p);
            float[][] cr = {{-0.07f, -0.125f, 0.055f}, {0.06f, -0.13f, 0.06f}, {0f, -0.15f, 0.06f}};
            for (float[] q : cr) { p.setColor(0xFF2D5B49); c.drawOval(H * (q[0] - q[2]), H * (q[1] - 0.03f), H * (q[0] + q[2]), H * (q[1] + 0.03f), p); }
            p.setColor(0xFF3F7D5F); c.drawOval(-H * 0.045f, -H * 0.175f, H * 0.045f, -H * 0.138f, p);
            c.restore();
            stand(c, 0.26f);
            leaf(c, 0, 0, -H * 0.018f, 0xFF7BBF6A); leaf(c, 0, 0, H * 0.02f, 0xFF6AAE5C);
            c.restore();
        }
        c.restore();
        // light from the moon: shaded side, thin lit rim
        Scenes2.sh(p, new RadialGradient(pcx + ppr * 0.45f, pcy - ppr * 0.75f, ppr * 1.5f, new int[]{0x00000000, 0x00000000, 0xB8070A20},
                new float[]{0f, 0.5f, 1f}, CL));
        c.drawCircle(pcx, pcy, ppr, p); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2f, H * 0.004f)); p.setColor((0xCC << 24) | (rim & 0xFFFFFF));
        c.drawCircle(pcx, pcy, ppr, p); p.setStyle(Paint.Style.FILL);
        // living light in screen space
        if (k == 1) {
            float vx = spx(ppr + H * 0.085f, -0.22f), vy = spy(ppr + H * 0.085f, -0.22f);
            glow(c, p, vx, vy, H * 0.07f, 0x70FF7A3A);
            for (int i = 0; i < 7; i++) {                                // smoke
                float life = (float) ((t * 0.12 + i / 7.0) % 1.0);
                float x = vx + (float) Math.sin(t * 0.6 + i * 1.7) * H * 0.012f * life + life * H * 0.03f, y = vy - life * H * 0.20f;
                glow(c, p, x, y, H * (0.018f + 0.05f * life), ((int) (110 * Math.sin(Math.PI * life)) << 24) | 0x9A98B4);
            }
            for (int i = 0; i < 5; i++) {                                // embers
                float life = (float) ((t * 0.3 + rnd(i, 1)) % 1.0);
                float x = vx + (rnd(i, 2) - 0.5f) * H * 0.05f * life, y = vy - life * H * 0.12f;
                p.setColor(((int) (230 * (1 - life)) << 24) | 0xFFB45A); c.drawCircle(x, y, H * 0.0028f, p);
            }
        } else if (k == 2) {
            float on = smooth((float) (Math.sin(t * 0.9) * 2.5 + 0.5));
            float lx = spx(ppr + H * 0.134f, 0f), ly = spy(ppr + H * 0.134f, 0f);
            glow(c, p, lx, ly, H * (0.16f + 0.02f * (float) Math.sin(t * 3)), ((int) (150 * on) << 24) | 0xFFC56B);
            p.setColor(((int) (255 * (0.25f + 0.75f * on)) << 24) | 0xFFE3A0);
            c.drawCircle(lx, ly, H * 0.008f, p);
        }
    }

    /** Positions the canvas (already in planet frame) at angle a on the surface, "up" pointing away from the centre. */
    private void stand(Canvas c, float a) {
        c.save();
        c.rotate((float) Math.toDegrees(a), 0, 0);
        c.translate(0, -ppr + ppr * 0.003f);
    }

    private void cone(Canvas c, float x, float w, float h, int color, boolean lava) {
        path.reset(); path.moveTo(x - w, ppr * 0.01f); path.lineTo(x - w * 0.28f, -h); path.lineTo(x + w * 0.28f, -h); path.lineTo(x + w, ppr * 0.01f); path.close();
        p.setStyle(Paint.Style.FILL); p.setColor(color); c.drawPath(path, p);
        p.setColor(lava ? 0xFFFF8A4A : 0xFF14101C);
        c.drawOval(x - w * 0.28f, -h - h * 0.05f, x + w * 0.28f, -h + h * 0.05f, p);
    }

    private void leaf(Canvas c, float x, float y, float dx, int color) {
        path.reset();
        path.moveTo(x, y);
        path.quadTo(x + dx * 0.9f, y - Math.abs(dx) * 0.2f, x + dx, y - Math.abs(dx) * 0.8f);
        path.quadTo(x + dx * 0.2f, y - Math.abs(dx) * 0.7f, x, y);
        path.close();
        p.setColor(color);
        c.drawPath(path, p);
    }
}
