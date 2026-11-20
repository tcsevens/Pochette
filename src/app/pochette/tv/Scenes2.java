package app.pochette.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/** Ambiance scenes, second batch: sunrise, summer sun, jazz bar, fireplace and party. Original artwork drawn with shapes. */
final class Scenes2 {
    private static final Shader.TileMode CL = Shader.TileMode.CLAMP;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    /** Tests can force a position inside the one-hour sunrise loop (seconds); negative = real clock. */
    static double testPhase = -1;
    private double startT = -1, lastT = -1;

    // ------------------------------------------------------------------ helpers

    /** Sets a shader on a paint whose colour may still hold a low alpha from an earlier draw (real Android multiplies the two). */
    static void sh(Paint q, Shader s) { q.setColor(0xFFFFFFFF); q.setShader(s); }

    static int lerp(int a, int b, float u) {
        u = Math.max(0f, Math.min(1f, u));
        int r = 0;
        for (int s = 0; s <= 24; s += 8) {
            int ca = (a >>> s) & 255, cb = (b >>> s) & 255;
            r |= ((int) (ca + (cb - ca) * u) & 255) << s;
        }
        return r;
    }

    /** Colour along evenly spaced keyframes, u in 0..1. */
    static int kf(int[] cols, float u) {
        u = Math.max(0f, Math.min(1f, u)) * (cols.length - 1);
        int i = Math.min(cols.length - 2, (int) u);
        return lerp(cols[i], cols[i + 1], u - i);
    }

    static int alpha(int color, float a) { return ((int) (Math.max(0f, Math.min(1f, a)) * 255) << 24) | (color & 0xFFFFFF); }

    private static float sm(float x) { x = Math.max(0f, Math.min(1f, x)); return x * x * (3f - 2f * x); }

    private static float rnd(int i, int j) { return Scenes.rnd(i, j); }

    private void glow(Canvas c, float x, float y, float r, int color) { Scenes.glow(c, p, x, y, r, color); }

    /** Soft ellipse of light (glow squashed vertically). */
    private void blob(Canvas c, float x, float y, float rx, float ry, int color) {
        c.save(); c.scale(1f, ry / rx, x, y); glow(c, x, y, rx, color); c.restore();
    }

    // ------------------------------------------------------------------ static layers

    static Bitmap build(int theme, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path pa = new Path();
        switch (theme) {
            case Ambiance.T_MORNING: sunriseLand(c, q, pa, w, h); break;
            case Ambiance.T_SUMMER: summerBg(c, q, pa, w, h); break;
            default: partyBg(c, q, pa, w, h); break;
        }
        return b;
    }

    static final float HORIZON = 0.72f;

    // ---- sunrise: hills and a rowing boat, transparent above the water line --------------------------------------

    private static void sunriseLand(Canvas c, Paint q, Path pa, int w, int h) {
        float yH = h * HORIZON;
        for (int layer = 0; layer < 2; layer++) {                       // far blue mountains, then nearer hills, only at both ends
            pa.reset(); pa.moveTo(0, yH + 2);
            for (int x = 0; x <= w; x += Math.max(2, w / 200)) {
                float u = x / (float) w;
                float mask = (float) Math.pow(Math.abs(u - 0.5f) * 2f, layer == 0 ? 1.3 : 1.9);
                float bump = (float) (Math.sin(u * (layer == 0 ? 17 : 11) + layer) * 0.5 + Math.sin(u * (layer == 0 ? 41 : 27)) * 0.25 + 0.75);
                pa.lineTo(x, yH - h * (layer == 0 ? 0.20f : 0.13f) * mask * bump);
            }
            pa.lineTo(w, yH + 2); pa.close();
            q.setStyle(Paint.Style.FILL);
            Scenes2.sh(q, new LinearGradient(0, yH - h * 0.25f, 0, yH, layer == 0 ? 0xFF9C8FC4 : 0xFF6F6A9E, layer == 0 ? 0xFFC6B3D6 : 0xFF8C86B4, CL));
            c.drawPath(pa, q); q.setShader(null);
        }
        for (int i = 0; i < 34; i++) {                                  // pines on the far shores
            float u = i < 17 ? 0.02f + 0.30f * rnd(i, 1) : 0.68f + 0.30f * rnd(i, 1);
            float mask = (float) Math.pow(Math.abs(u - 0.5f) * 2f, 1.9);
            float base = yH - h * 0.13f * mask * 0.35f + 2, ph = h * (0.03f + 0.05f * rnd(i, 2));
            Scenes.pine(c, q, pa, u * w, base, ph, 0xFF4E4A7A, 0x00000000);
        }
        // foreground reeds along the bottom
        q.setStyle(Paint.Style.FILL); q.setColor(0xFF2D2A4A);
        for (int i = 0; i < 70; i++) {
            float x = w * (i + rnd(i, 3)) / 70f, hh = h * (0.05f + 0.08f * rnd(i, 4));
            if (x > w * 0.12f && x < w * 0.88f) hh *= 0.3f;
            float lean = (rnd(i, 5) - 0.5f) * h * 0.03f, bw = h * 0.0035f;
            pa.reset(); pa.moveTo(x - bw, h); pa.lineTo(x + lean, h - hh); pa.lineTo(x + bw, h); pa.close();
            c.drawPath(pa, q);
        }
        q.setStyle(Paint.Style.FILL);
        float bx = w * 0.66f, by = h * 0.865f, s = h / 1080f;           // a little rowing boat
        pa.reset(); pa.moveTo(bx - 64 * s, by - 10 * s); pa.lineTo(bx + 70 * s, by - 10 * s); pa.lineTo(bx + 48 * s, by + 8 * s); pa.lineTo(bx - 44 * s, by + 8 * s); pa.close();
        q.setColor(0xFF2D2A4A); c.drawPath(pa, q);
        q.setColor(0xFF3E3A63); c.drawRect(bx - 2 * s, by - 52 * s, bx + 2 * s, by - 10 * s, q);
        pa.reset(); pa.moveTo(bx + 2 * s, by - 52 * s); pa.lineTo(bx + 40 * s, by - 16 * s); pa.lineTo(bx + 2 * s, by - 16 * s); pa.close();
        q.setColor(0xFFF2E6D0); c.drawPath(pa, q);
    }

    // ---- summer: sea and a warm sky ----------------------------------------------------------------------------

    private static void summerBg(Canvas c, Paint q, Path pa, int w, int h) {
        q.setStyle(Paint.Style.FILL);
        Scenes2.sh(q, new LinearGradient(0, 0, w * 0.7f, h, new int[]{0xFFFFD27A, 0xFFFFA245, 0xFFF06A55, 0xFF8E3A6E}, new float[]{0f, 0.35f, 0.7f, 1f}, CL));
        c.drawRect(0, 0, w, h, q); q.setShader(null);
        float yS = h * 0.86f;
        Scenes2.sh(q, new LinearGradient(0, yS, 0, h, new int[]{0xFFB4485F, 0xFF6A2A5C, 0xFF3B1A48}, new float[]{0f, 0.5f, 1f}, CL));
        c.drawRect(0, yS, w, h, q); q.setShader(null);
        q.setColor(0x30FFE2A0); c.drawRect(0, yS - 1, w, yS + 2, q);
        for (int i = 0; i < 40; i++) {                                  // far sea glints
            float y = yS + (h - yS) * (0.05f + 0.9f * rnd(i, 1)), x = w * rnd(i, 2), ww = w * (0.01f + 0.03f * rnd(i, 3)) * (0.5f + (y - yS) / (h - yS));
            q.setColor(0x33FFE6B0); c.drawRect(x, y, x + ww, y + Math.max(1.5f, h * 0.0025f), q);
        }
        q.setColor(0x2E000000); c.drawRect(0, 0, w, h, q);              // keeps white text readable
        Scenes2.sh(q, new RadialGradient(w / 2f, h / 2f, w * 0.75f, 0x00000000, 0x44000000, CL));
        c.drawRect(0, 0, w, h, q); q.setShader(null);
    }

    // ---- jazz bar ------------------------------------------------------------------------------------------------

    static final float JAZZ_STAGE = 0.80f;
    static final float[][] TABLES = {{0.06f, 0.26f, 0.47f, 0.66f, 0.86f}, {0.16f, 0.37f, 0.56f, 0.77f, 0.97f}};

    // ---- fireplace ------------------------------------------------------------------------------------------------

    static final float FP_L = 0.45f, FP_R = 0.84f, FP_T = 0.775f, MANTEL = 0.735f;

    // ---- party ----------------------------------------------------------------------------------------------------

    private static void partyBg(Canvas c, Paint q, Path pa, int w, int h) {
        q.setStyle(Paint.Style.FILL);
        Scenes2.sh(q, new LinearGradient(0, 0, 0, h, new int[]{0xFF0A0524, 0xFF170A3C, 0xFF260E4E}, new float[]{0f, 0.6f, 1f}, CL));
        c.drawRect(0, 0, w, h, q); q.setShader(null);
        float yF = h * 0.90f;                                           // glossy dance floor, tiles in perspective
        Scenes2.sh(q, new LinearGradient(0, yF, 0, h, new int[]{0xFF2A1262, 0xFF120730}, new float[]{0f, 1f}, CL));
        c.drawRect(0, yF, w, h, q); q.setShader(null);
        for (int i = -12; i <= 12; i++) { q.setStyle(Paint.Style.STROKE); q.setStrokeWidth(1.5f); q.setColor(0x30B9A0FF); c.drawLine(w / 2f + i * w * 0.012f, yF, w / 2f + i * w * 0.09f, h, q); }
        for (int i = 1; i < 4; i++) { float y = yF + (h - yF) * (i * i / 9f); q.setStrokeWidth(1.5f); q.setColor(0x30B9A0FF); c.drawLine(0, y, w, y, q); }
        q.setStyle(Paint.Style.FILL);
        Scenes2.sh(q, new RadialGradient(w / 2f, h / 2f, w * 0.75f, 0x00000000, 0x66000000, CL));
        c.drawRect(0, 0, w, h, q); q.setShader(null);
    }

    // ------------------------------------------------------------------ live layers

    static final int[] PARTY_COLS = {0xFFFF3FA4, 0xFF3FD8FF, 0xFFFFE23F, 0xFF7CFF5B, 0xFFB26BFF, 0xFFFF7A3F};
    static final double BPM = 118.0;

    /** 0..1 pulse that peaks on every beat of the fixed party tempo. */
    static float beat(double t) {
        double b = t * BPM / 60.0;
        return (float) Math.exp(-4.0 * (b - Math.floor(b)));
    }

    /** Drawn before the background bitmap: only the sunrise has a sky that changes over time. */
    void sky(Canvas c, int theme, int W, int H, double t) {
        if (theme != Ambiance.T_MORNING) return;
        double ph;
        if (testPhase >= 0) ph = testPhase;
        else {
            if (startT < 0 || t - lastT > 3.0) startT = t;
            lastT = t;
            ph = (t - startT) % 3600.0;
        }
        float u = (float) (ph / 3600.0);
        // sun height: rises during the first half hour, keeps climbing gently, sets quickly at the very end so the loop is seamless
        float hgt = u < 0.5f ? -0.12f + 1.12f * (u / 0.5f) : 1.0f + 0.15f * ((u - 0.5f) / 0.5f);
        if (u > 0.97f) hgt = hgt + (-0.12f - hgt) * sm((u - 0.97f) / 0.03f);
        float cp = Math.max(0f, Math.min(1f, (hgt + 0.12f) / 1.0f));
        float yH = H * HORIZON;
        int top = kf(new int[]{0xFF8E7BC0, 0xFFC8A6D8, 0xFF9CCBF0, 0xFF7DBDF0}, cp);
        int mid = kf(new int[]{0xFFF08FA0, 0xFFF9B7B0, 0xFFCDE6F5, 0xFFBFE3F7}, cp);
        int hor = kf(new int[]{0xFFFF8A3D, 0xFFFFB45C, 0xFFFFE1A0, 0xFFFFF4D0}, cp);
        int sun = kf(new int[]{0xFFFF7A33, 0xFFFFA83F, 0xFFFFD84F, 0xFFFFF2A0}, cp);
        p.setStyle(Paint.Style.FILL);
        Scenes2.sh(p, new LinearGradient(0, 0, 0, yH, new int[]{top, mid, hor}, new float[]{0f, 0.55f, 1f}, CL));
        c.drawRect(0, 0, W, yH + 2, p); p.setShader(null);
        float sx = W * (0.74f + 0.10f * Math.max(0f, Math.min(1.15f, hgt)) / 1.15f), sy = yH - hgt * H * 0.46f;
        float sr = H * (0.085f - 0.03f * Math.max(0f, Math.min(1f, hgt)));
        glow(c, sx, sy, H * (0.55f + 0.2f * cp), alpha(sun, 0.45f));
        glow(c, sx, sy, H * 0.22f, alpha(sun, 0.7f));
        p.setColor(sun); c.drawCircle(sx, sy, sr, p);
        p.setColor(alpha(0xFFFFFFFF, 0.35f)); c.drawCircle(sx - sr * 0.15f, sy - sr * 0.15f, sr * 0.7f, p);
        // clouds
        int cloud = lerp(0xFFFFB79A, 0xFFFFFFFF, cp);
        for (int i = 0; i < 6; i++) {
            float x = (float) ((W * rnd(i, 11) + t * (6 + 5 * rnd(i, 12))) % (W * 1.4)) - W * 0.2f;
            float y = H * (0.10f + 0.34f * rnd(i, 13)), rw = W * (0.07f + 0.05f * rnd(i, 14));
            for (int k = 0; k < 4; k++) blob(c, x + (k - 1.5f) * rw * 0.55f, y + (k % 2) * H * 0.008f, rw, rw * 0.28f, alpha(cloud, 0.42f));
        }
        // lake
        int w0 = lerp(hor, 0xFF6F86B8, 0.45f), w1 = lerp(w0, 0xFF3A4E80, 0.75f);
        Scenes2.sh(p, new LinearGradient(0, yH, 0, H, w0, w1, CL));
        c.drawRect(0, yH, W, H, p); p.setShader(null);
        float vis = Math.max(0f, Math.min(1f, hgt * 3f));
        for (int i = 0; i < 18; i++) {                                  // sun reflection on the water
            float y = yH + H * 0.012f * (i + 1) * (1f + i * 0.14f);
            float ww = sr * (0.5f + i * 0.11f) * (0.6f + 0.4f * (float) Math.sin(t * 0.9 + i * 1.7));
            float x = sx + (float) Math.sin(t * 0.7 + i * 0.9) * W * 0.006f;
            p.setColor(alpha(sun, vis * (0.75f - i * 0.03f) * (0.55f + 0.45f * (float) Math.sin(t * 1.3 + i * 2.1))));
            c.drawRect(x - ww, y, x + ww, y + Math.max(2f, H * 0.004f), p);
        }
        // pale light across the water at dawn
        blob(c, sx, yH + H * 0.02f, W * 0.30f, H * 0.03f, alpha(sun, 0.35f * vis));
        horizonHaze = hor;
    }

    private int horizonHaze = 0xFFFFE1A0;
    private static Bitmap[] spr;
    private final Rect srcR = new Rect(0, 0, 64, 64);
    private final RectF dstR = new RectF();

    void effects(Canvas c, int theme, int W, int H, double t) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        switch (theme) {
            case Ambiance.T_MORNING: sunriseFx(c, W, H, t); break;
            case Ambiance.T_SUMMER: summer(c, W, H, t); break;
            default: party(c, W, H, t); break;
        }
    }

    private void sunriseFx(Canvas c, int W, int H, double t) {
        float yH = H * HORIZON;
        Scenes2.sh(p, new LinearGradient(0, yH - H * 0.18f, 0, yH + 2, alpha(horizonHaze, 0f), alpha(horizonHaze, 0.45f), CL));
        c.drawRect(0, yH - H * 0.18f, W, yH + 2, p); p.setShader(null);       // morning haze over the far hills
        for (int f = 0; f < 3; f++) {                                       // a few birds crossing, now and then
            double cyc = (t + f * 37.0) % 71.0;
            if (cyc > 18.0) continue;
            float k = (float) (cyc / 18.0);
            for (int b = 0; b < 3; b++) {
                float x = W * (1.05f - 1.15f * k) + b * W * 0.022f, y = H * (0.20f + 0.05f * f + 0.02f * b) + (float) Math.sin(t * 0.8 + b) * H * 0.01f;
                float flap = (float) Math.sin(t * 7 + b * 1.3 + f) * H * 0.008f;
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2f, H * 0.0028f)); p.setColor(0xCC3A2E5A);
                path.reset(); path.moveTo(x - H * 0.014f, y - flap); path.quadTo(x - H * 0.006f, y - H * 0.008f, x, y); path.quadTo(x + H * 0.006f, y - H * 0.008f, x + H * 0.014f, y - flap);
                c.drawPath(path, p);
            }
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void summer(Canvas c, int W, int H, double t) {
        float sx = W * 0.055f, sy = H * 0.075f, R = H * 0.115f;
        float breathe = (float) (1.0 + 0.04 * Math.sin(t * 0.8));
        glow(c, sx, sy, H * 1.05f * breathe, 0x66FFE9A8);
        glow(c, sx, sy, H * 0.42f, 0xAAFFF6D0);
        for (int i = 0; i < 14; i++) {                                      // slowly turning rays
            float ang = (float) (i * 360.0 / 14 + t * 2.2 + 6 * Math.sin(t * 0.4 + i));
            float len = H * (0.9f + 0.35f * rnd(i, 21)), wd = H * (0.02f + 0.03f * rnd(i, 22));
            c.save(); c.rotate(ang, sx, sy);
            path.reset(); path.moveTo(sx, sy - wd * 0.3f); path.lineTo(sx + len, sy - wd); path.lineTo(sx + len, sy + wd); path.lineTo(sx, sy + wd * 0.3f); path.close();
            Scenes2.sh(p, new LinearGradient(sx, sy, sx + len, sy, 0x40FFF2C0, 0x00FFF2C0, CL));
            c.drawPath(path, p); p.setShader(null); c.restore();
        }
        blob(c, sx, sy, W * 0.55f, H * 0.02f, 0x44FFFFFF);                  // anamorphic streak
        p.setColor(0xFFFFFDF0); c.drawCircle(sx, sy, R, p);
        glow(c, sx, sy, R * 1.5f, 0x88FFFFFF);
        // lens ghosts on the line through the centre of the screen
        float dx = W * 0.5f - sx, dy = H * 0.5f - sy, dl = (float) Math.sqrt(dx * dx + dy * dy);
        float[] pos = {0.30f, 0.52f, 0.78f, 1.05f, 1.40f, 1.75f}, rad = {0.035f, 0.075f, 0.045f, 0.11f, 0.05f, 0.09f};
        int[] col = {0xFF7CFFD0, 0xFFFFB46B, 0xFFB48CFF, 0xFFFFE08C, 0xFF7CC8FF, 0xFFFF8CC8};
        for (int i = 0; i < pos.length; i++) {
            float drift = (float) Math.sin(t * 0.35 + i) * 0.015f;
            float gx = sx + dx * (pos[i] + drift), gy = sy + dy * (pos[i] + drift), r = H * rad[i];
            float a = (0.55f + 0.45f * (float) Math.sin(t * 0.9 + i * 1.3)) * (i % 2 == 0 ? 0.08f : 0.11f);
            p.setStyle(Paint.Style.FILL); p.setColor(alpha(col[i], a)); c.drawCircle(gx, gy, r, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2f, r * 0.12f)); p.setColor(alpha(col[i], a * 1.4f)); c.drawCircle(gx, gy, r, p);
        }
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 30; i++) {                                      // golden dust in the warm air
            float life = (float) ((t * (0.01 + 0.01 * rnd(i, 31)) + rnd(i, 32)) % 1.0);
            float x = W * rnd(i, 33) + (float) Math.sin(t * 0.3 + i) * W * 0.02f, y = H * (0.9f - 0.8f * life);
            glow(c, x, y, H * (0.004f + 0.006f * rnd(i, 34)), alpha(0xFFFFE2A0, 0.6f * (float) Math.sin(Math.PI * life)));
        }
        // sparkles on the sea
        float yS = H * 0.86f;
        for (int i = 0; i < 26; i++) {
            float tw = (float) Math.max(0, Math.sin(t * (1.0 + rnd(i, 41)) + i * 2.3));
            float x = W * (0.02f + 0.5f * rnd(i, 42)) , y = yS + (H - yS) * rnd(i, 43);
            p.setColor(alpha(0xFFFFF0C0, 0.9f * tw * tw)); c.drawRect(x, y, x + W * 0.006f * (1 + rnd(i, 44)), y + Math.max(2f, H * 0.003f), p);
        }
    }

    private void party(Canvas c, int W, int H, double t) {
        float b = beat(t);
        for (int i = 0; i < 5; i++) {                                        // sweeping coloured beams from the ceiling
            float sx = W * (0.14f + 0.18f * i), len = H * 1.25f;
            float ang = (float) (Math.sin(t * (0.45 + 0.08 * i) + i * 1.9) * 38.0);
            int col = PARTY_COLS[(i + (int) (t / 8.0)) % PARTY_COLS.length];
            float a = 0.20f + 0.16f * b;
            c.save(); c.rotate(ang, sx, 0);
            path.reset(); path.moveTo(sx - H * 0.006f, 0); path.lineTo(sx + H * 0.006f, 0); path.lineTo(sx + H * 0.15f, len); path.lineTo(sx - H * 0.15f, len); path.close();
            Scenes2.sh(p, new LinearGradient(sx, 0, sx, len, alpha(col, a), alpha(col, 0f), CL));
            c.drawPath(path, p); p.setShader(null); c.restore();
            glow(c, sx, 0, H * 0.05f, alpha(col, 0.55f));
        }
        // disco ball
        float bx = W * 0.80f, by = H * 0.14f, br = H * 0.075f;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f); p.setColor(0x88FFFFFF); c.drawLine(bx, 0, bx, by - br, p); p.setStyle(Paint.Style.FILL);
        glow(c, bx, by, br * 3.2f, alpha(0xFFB9A0FF, 0.25f + 0.2f * b));
        p.setStyle(Paint.Style.FILL);
        Scenes2.sh(p, new RadialGradient(bx - br * 0.35f, by - br * 0.35f, br * 1.5f, new int[]{0xFFF4F0FF, 0xFFA59ACF, 0xFF3A3470}, new float[]{0f, 0.5f, 1f}, CL));
        c.drawCircle(bx, by, br, p); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1.5f, H * 0.0016f));
        for (int k = 0; k < 8; k++) {                                        // mirror grid: meridians turn, parallels stay
            float ph = (float) (k * Math.PI / 8 + t * 0.45), rx = br * Math.abs((float) Math.cos(ph));
            p.setColor(0x38FFFFFF); c.drawOval(bx - rx, by - br, bx + rx, by + br, p);
        }
        for (int j = -3; j <= 3; j++) {
            float yy = by + br * j / 4f, hw = br * (float) Math.sqrt(1 - (j / 4f) * (j / 4f));
            p.setColor(0x30FFFFFF); c.drawLine(bx - hw, yy, bx + hw, yy, p);
        }
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 4; i++) {                                        // a few soft glints that fade in and out
            double ph = (t * 0.6 + i * 0.25) % 1.0;
            float a2 = (float) Math.sin(Math.PI * ph);
            float ga = rnd(i + (int) (t * 0.6 + i * 0.25), 74) * 6.2832f, gr = br * 0.8f * rnd(i + (int) (t * 0.6 + i * 0.25), 75);
            float gx = bx + (float) Math.cos(ga) * gr, gy = by + (float) Math.sin(ga) * gr;
            glow(c, gx, gy, br * 0.35f * a2, alpha(0xFFFFFFFF, 0.9f * a2));
        }
        for (int i = 0; i < 26; i++) {                                       // light spots wandering over the room
            float a = (float) (rnd(i, 73) * 6.2832 + t * (0.12 + 0.1 * rnd(i, 74)) * (i % 2 == 0 ? 1 : -1));
            float x = W * 0.5f + (float) Math.cos(a) * W * (0.25f + 0.32f * rnd(i, 75)), y = H * 0.5f + (float) Math.sin(a * 1.3) * H * 0.42f;
            int col = PARTY_COLS[i % PARTY_COLS.length];
            glow(c, x, y, H * (0.018f + 0.01f * b), alpha(col, 0.55f));
        }
        for (int i = 0; i < 70; i++) {                                       // discreet confetti
            float spd = H * (0.04f + 0.07f * rnd(i, 81));
            float y = (float) ((rnd(i, 82) * (H + 40) + spd * t) % (H + 40)) - 20;
            float x = W * rnd(i, 83) + (float) Math.sin(t * (0.5 + rnd(i, 84)) + i) * H * 0.03f;
            float ang = (float) (t * (60 + 120 * rnd(i, 85)) + i * 40), sz = H * (0.006f + 0.008f * rnd(i, 86));
            p.setColor(alpha(PARTY_COLS[i % PARTY_COLS.length], 0.75f));
            c.save(); c.rotate(ang, x, y); c.drawRect(x - sz, y - sz * 0.5f, x + sz, y + sz * 0.5f, p); c.restore();
        }
        for (int i = 0; i < 48; i++) {                                       // equalizer along the floor, locked to the tempo
            float k = i / 47f;
            float lv = 0.35f + 0.35f * (float) Math.abs(Math.sin(t * (2.0 + 0.11 * i) + i * 0.9)) + 0.30f * b * (1f - Math.abs(k - 0.5f));
            float bw = W / 48f, hh = H * 0.11f * lv;
            p.setColor(alpha(PARTY_COLS[(int) (k * PARTY_COLS.length) % PARTY_COLS.length], 0.55f));
            c.drawRect(i * bw + bw * 0.15f, H - hh, (i + 1) * bw - bw * 0.15f, H, p);
        }
        blob(c, W * 0.5f, H * 0.97f, W * 0.55f, H * 0.06f, alpha(PARTY_COLS[(int) (t * BPM / 60.0 / 4) % PARTY_COLS.length], 0.18f + 0.22f * b));
    }
}
