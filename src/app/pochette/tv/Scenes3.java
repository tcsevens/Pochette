package app.pochette.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/** Ambiance scenes, third batch: chill office, Montreal, Corsica, Christmas and the record shop. Original artwork drawn with shapes. */
final class Scenes3 {
    private static final Shader.TileMode CL = Shader.TileMode.CLAMP;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();

    static boolean handles(int theme) {
        return theme == Ambiance.T_RECORD;
    }

    private static float rnd(int i, int j) { return Scenes.rnd(i, j); }
    private static int alpha(int c, float a) { return Scenes2.alpha(c, a); }
    private static void sh(Paint q, Shader s) { Scenes2.sh(q, s); }
    private void glow(Canvas c, float x, float y, float r, int col) { Scenes.glow(c, p, x, y, r, col); }
    private void blob(Canvas c, float x, float y, float rx, float ry, int col) { c.save(); c.scale(1f, ry / rx, x, y); glow(c, x, y, rx, col); c.restore(); }
    private static float sm(float x) { x = Math.max(0f, Math.min(1f, x)); return x * x * (3f - 2f * x); }

    // ------------------------------------------------------------------ static layers

    static Bitmap build(int theme, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path pa = new Path();
        switch (theme) {
            default: recordBg(c, q, pa, w, h); break;
        }
        return b;
    }

    // ---- the Mediterranean view, shared by the office window and the Corsica scene ----------------------------------

    /** Draws the sea view into the rectangle; returns nothing, the horizon is at y + 0.56 h. */
    // ---- chill office: a desk in front of a window over the sea ----------------------------------------------------


    private static void book(Canvas c, Paint q, Path pa, float x, float y, float bw, float bh, float rot, int cover, int pages) {
        c.save(); c.rotate(rot, x, y);
        q.setStyle(Paint.Style.FILL);
        q.setColor(cover); c.drawRect(x - bw / 2f, y - bh, x + bw / 2f, y, q);
        q.setColor(pages); c.drawRect(x - bw / 2f + bw * 0.03f, y - bh + bh * 0.2f, x + bw / 2f, y - bh * 0.2f, q);
        q.setColor(0x33000000); c.drawRect(x - bw / 2f, y - bh, x - bw / 2f + bw * 0.04f, y, q);
        q.setColor(0x66FFD27A); c.drawRect(x - bw * 0.3f, y - bh * 0.62f, x - bw * 0.08f, y - bh * 0.5f, q);
        c.restore();
    }

    // ---- Corsica: the bay from a cliff ---------------------------------------------------------------------------

    // ---- Montreal: the skyline across the river at dusk ------------------------------------------------------------


    // ---- Christmas: the tree, a garland, gifts -----------------------------------------------------------------------


    // ---- record shop: shelves of sleeves, a turntable in close-up ---------------------------------------------------

    private static void recordBg(Canvas c, Paint q, Path pa, int w, int h) {
        float s = h / 1080f;
        q.setStyle(Paint.Style.FILL);
        sh(q, new LinearGradient(0, 0, 0, h, new int[]{0xFF2A1A12, 0xFF1C110C, 0xFF120A07}, new float[]{0f, 0.6f, 1f}, CL));
        c.drawRect(0, 0, w, h, q); q.setShader(null);
        // wall shelves loaded with records seen from the spine
        int[] spine = {0xFF8A2A2A, 0xFF2A4A6A, 0xFFB08A3A, 0xFF3A5A3A, 0xFF6A3A6A, 0xFFD8C8A8, 0xFF1E1E26, 0xFF9A5A2A};
        for (int sh = 0; sh < 3; sh++) {
            float y = h * (0.16f + 0.17f * sh);
            for (float x = 0; x < w; ) {
                int i = (int) (x / s) + sh * 777;
                float bw2 = (7 + 6 * rnd(i, 1)) * s, bh2 = (118 + 20 * rnd(i, 2)) * s;
                if (rnd(i, 3) < 0.07f) { x += 40 * s; continue; }
                int col = spine[(int) (rnd(i, 4) * spine.length)];
                q.setColor(col); c.drawRect(x, y - bh2, x + bw2, y, q);
                q.setColor(0x38000000); c.drawRect(x + bw2 - 2 * s, y - bh2, x + bw2, y, q);
                q.setColor(0x40FFFFFF); c.drawRect(x + 1 * s, y - bh2 + 14 * s, x + bw2 - 1 * s, y - bh2 + 18 * s, q);
                x += bw2 + 1 * s;
            }
            q.setColor(0xFF4A2E1A); c.drawRect(0, y, w, y + 10 * s, q);
            q.setColor(0x44000000); c.drawRect(0, y + 10 * s, w, y + 24 * s, q);
        }
        sh(q, new LinearGradient(0, 0, 0, h * 0.74f, 0x99100804, 0xCC100804, CL)); c.drawRect(0, 0, w, h * 0.74f, q); q.setShader(null);
        // framed sleeves on the wall, abstract
        int[][] fr = {{0xFF2A6AB8, 0xFFF0D060}, {0xFFB8283A, 0xFFF4E8C8}, {0xFF2A9A5A, 0xFF1E1E26}};
        for (int i = 0; i < 3; i++) {
            float fx = w * (0.06f + 0.10f * i), fy = h * 0.58f;
            q.setColor(0xFF140C08); c.drawRect(fx - 4 * s, fy - 4 * s, fx + 100 * s, fy + 100 * s, q);
            q.setColor(fr[i][0]); c.drawRect(fx, fy, fx + 96 * s, fy + 96 * s, q);
            q.setColor(fr[i][1]); c.drawCircle(fx + 48 * s, fy + 48 * s, 28 * s, q);
            q.setColor(fr[i][0]); c.drawCircle(fx + 48 * s, fy + 48 * s, 10 * s, q);
        }
        // the counter with the turntable
        float yc = h * 0.74f;
        sh(q, new LinearGradient(0, yc, 0, h, new int[]{0xFF6A4228, 0xFF4A2C1A, 0xFF2A180E}, new float[]{0f, 0.4f, 1f}, CL));
        c.drawRect(0, yc, w, h, q); q.setShader(null);
        q.setColor(0xFFA87848); c.drawRect(0, yc, w, yc + 4 * s, q);
        // plinth (slightly perspective)
        float pl = w * 0.44f, pr2 = w * 0.985f, pt = h * 0.745f, pb = h * 1.02f;
        pa.reset(); pa.moveTo(pl + 20 * s, pt); pa.lineTo(pr2 - 20 * s, pt); pa.lineTo(pr2, pb); pa.lineTo(pl, pb); pa.close();
        sh(q, new LinearGradient(0, pt, 0, pb, new int[]{0xFF2E2E34, 0xFF1A1A1F, 0xFF101014}, new float[]{0f, 0.5f, 1f}, CL)); c.drawPath(pa, q); q.setShader(null);
        q.setColor(0x44FFFFFF); c.drawRect(pl + 20 * s, pt, pr2 - 20 * s, pt + 3 * s, q);
        // controls on the plinth: a knob, two speed buttons, an amber light
        float kx = pl + w * 0.045f, ky = h * 0.93f;
        q.setColor(0xFF0A0A0C); c.drawCircle(kx, ky, 30 * s, q);
        q.setColor(0xFF9A9AA4); c.drawCircle(kx, ky, 25 * s, q);
        q.setColor(0xFFC8C8D2); c.drawCircle(kx - 4 * s, ky - 4 * s, 18 * s, q);
        q.setColor(0xFF2A2A30); c.drawRect(kx - 2 * s, ky - 25 * s, kx + 2 * s, ky - 8 * s, q);
        q.setColor(0xFF9A9AA4); c.drawRect(kx + 60 * s, ky - 16 * s, kx + 100 * s, ky + 12 * s, q); c.drawRect(kx + 110 * s, ky - 16 * s, kx + 150 * s, ky + 12 * s, q);
        q.setColor(0xFF1A1A1F); c.drawRect(kx + 64 * s, ky - 12 * s, kx + 96 * s, ky + 8 * s, q); c.drawRect(kx + 114 * s, ky - 12 * s, kx + 146 * s, ky + 8 * s, q);
        // platter: an ellipse seen from above the counter
        float cxp = w * 0.665f, cyp = h * 0.855f, rx = w * 0.170f, ry = rx * 0.40f;
        q.setColor(0xFF0A0A0C); c.drawOval(cxp - rx - 14 * s, cyp - ry - 8 * s, cxp + rx + 14 * s, cyp + ry + 18 * s, q);
        q.setColor(0xFF8A8A94); c.drawOval(cxp - rx - 8 * s, cyp - ry - 4 * s, cxp + rx + 8 * s, cyp + ry + 10 * s, q);
        q.setColor(0xFF2A2A30); c.drawOval(cxp - rx - 4 * s, cyp - ry - 2 * s, cxp + rx + 4 * s, cyp + ry + 6 * s, q);
        // tonearm base and arm rest
        q.setColor(0xFF0A0A0C); c.drawCircle(w * 0.945f, h * 0.80f, 34 * s, q);
        q.setColor(0xFFB8B8C2); c.drawCircle(w * 0.945f, h * 0.80f, 28 * s, q);
        q.setColor(0xFF6A6A74); c.drawCircle(w * 0.945f, h * 0.80f, 14 * s, q);
        q.setShader(new RadialGradient(w / 2f, h / 2f, w * 0.75f, 0x00000000, 0x77000000, CL));
        c.drawRect(0, 0, w, h, q); q.setShader(null);
    }

    // ------------------------------------------------------------------ live layers

    void effects(Canvas c, int theme, int W, int H, double t) {
        p.setShader(null); p.setStyle(Paint.Style.FILL);
        switch (theme) {
            default: record(c, W, H, t); break;
        }
    }

    /** Sea glitter, drifting clouds and gulls over a Mediterranean view placed in the given rectangle. */
    // ---- Corsican flag, waving on a pole in the right foreground ---------------------------------------------------------

    // ---- Christmas: gifts pile up, the milk goes down, Santa flies past the window -----------------------------------------
    static double testSanta = -1;


    private void record(Canvas c, int W, int H, double t) {
        float s = H / 1080f;
        float cxp = W * 0.665f, cyp = H * 0.855f, rx = W * 0.170f, ry = rx * 0.40f;
        float rr = rx * 0.97f, rry = ry * 0.97f;
        double rot = t * 3.49;                                                // 33 1/3 rpm
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF08080A); c.drawOval(cxp - rr, cyp - rry, cxp + rr, cyp + rry, p);
        p.setStyle(Paint.Style.STROKE);                                       // grooves
        for (int i = 0; i < 26; i++) {
            float f = 0.30f + 0.69f * i / 25f;
            p.setStrokeWidth(Math.max(1f, 1.2f * s)); p.setColor(alpha(0xFFFFFFFF, i % 5 == 0 ? 0.10f : 0.045f));
            c.drawOval(cxp - rr * f, cyp - rry * f, cxp + rr * f, cyp + rry * f, p);
        }
        p.setStyle(Paint.Style.FILL);
        for (int k = 0; k < 2; k++) {                                         // two fixed sheens, as light slides over the vinyl
            float a0 = (k == 0 ? -0.55f : 2.59f), da = 0.28f;
            path.reset(); path.moveTo(cxp, cyp);
            for (int j = 0; j <= 10; j++) { float a = a0 + da * j / 10f; path.lineTo(cxp + (float) Math.cos(a) * rr, cyp + (float) Math.sin(a) * rry); }
            path.close();
            p.setColor(0x16FFFFFF); c.drawPath(path, p);
        }
        float lr = rr * 0.34f, lry = rry * 0.34f;                              // label turns with the record
        p.setColor(0xFFD8442E); c.drawOval(cxp - lr, cyp - lry, cxp + lr, cyp + lry, p);
        p.setColor(0xFFF2E2C0); c.drawOval(cxp - lr * 0.72f, cyp - lry * 0.72f, cxp + lr * 0.72f, cyp + lry * 0.72f, p);
        for (int i = 0; i < 3; i++) {
            float a = (float) (rot + i * 2.094);
            p.setColor(0xFFC03A28); c.drawCircle(cxp + (float) Math.cos(a) * lr * 0.5f, cyp + (float) Math.sin(a) * lry * 0.5f, lr * 0.09f, p);
        }
        float la = (float) rot;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2f, 3 * s)); p.setColor(0xFF7A2418);
        c.drawLine(cxp + (float) Math.cos(la) * lr * 0.2f, cyp + (float) Math.sin(la) * lry * 0.2f, cxp + (float) Math.cos(la) * lr * 0.68f, cyp + (float) Math.sin(la) * lry * 0.68f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFD0D0D8); c.drawCircle(cxp, cyp, 5 * s, p);
        // platter strobe dots ring (fixed pattern turns slowly through the edge)
        for (int i = 0; i < 60; i++) {
            float a = (float) (rot * 1.0 + i * 6.2832 / 60);
            float x = cxp + (float) Math.cos(a) * (rx + 3 * s), y = cyp + (float) Math.sin(a) * (ry + 2 * s);
            p.setColor(0x66FFFFFF); if (Math.sin(a) > -0.2) c.drawRect(x - 1.5f * s, y - 1.5f * s, x + 1.5f * s, y + 1.5f * s, p);
        }
        // tonearm: pivot at the right, a long arm to the cartridge resting on the outer groove
        float pvx = W * 0.945f, pvy = H * 0.80f;
        float cax = cxp + rr * 0.83f, cay = cyp + rry * 0.28f + (float) Math.sin(t * 0.2) * 1.5f * s;
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(9 * s); p.setColor(0xFF6A6A74); c.drawLine(pvx, pvy, cax + 20 * s, cay - 18 * s, p);
        p.setStrokeWidth(5 * s); p.setColor(0xFFD2D2DA); c.drawLine(pvx, pvy, cax + 20 * s, cay - 18 * s, p);
        p.setStrokeWidth(8 * s); p.setColor(0xFF2A2A30); c.drawLine(pvx, pvy, pvx + 56 * s, pvy - 8 * s, p);         // counterweight
        p.setStrokeWidth(16 * s); p.setColor(0xFF1A1A1F); c.drawLine(cax + 24 * s, cay - 22 * s, cax - 6 * s, cay + 4 * s, p);   // headshell
        p.setStrokeWidth(5 * s); p.setColor(0xFFE0C060); c.drawLine(cax - 6 * s, cay + 4 * s, cax - 14 * s, cay + 12 * s, p);
        p.setStrokeCap(Paint.Cap.BUTT); p.setStyle(Paint.Style.FILL);
        float on = (float) (0.75 + 0.25 * Math.sin(t * 2.0));
        glow(c, W * 0.545f + 0 * s, H * 0.93f, 18 * s, alpha(0xFFFFA83A, 0.55f * on)); p.setColor(alpha(0xFFFFC060, 0.8f + 0.2f * on)); c.drawCircle(W * 0.545f, H * 0.93f, 4 * s, p);
        blob(c, cxp, cyp - ry * 0.2f, rx * 1.1f, ry * 0.9f, 0x12FFD9A0);       // warm lamp light over the platter
        for (int i = 0; i < 26; i++) {                                         // dust turning in the shop light
            float x = W * (0.45f + 0.5f * rnd(i, 81)) + (float) Math.sin(t * 0.3 + i) * W * 0.01f, y = H * (0.2f + 0.7f * (float) ((rnd(i, 82) + t * 0.007) % 1.0));
            p.setColor(alpha(0xFFFFE6B8, 0.3f * (float) (0.5 + 0.5 * Math.sin(t * 1.4 + i)))); c.drawCircle(x, y, H * 0.0015f, p);
        }
        // neon sign
        p.setTextSize(H * 0.045f); p.setFakeBoldText(true); p.setTextAlign(Paint.Align.CENTER);
        float fl = 1f; double cyc = t % 17.0; if (cyc > 15.6 && cyc < 16.1) fl = (float) (0.3 + 0.7 * Math.abs(Math.sin(t * 40)));
        glow(c, W * 0.80f, H * 0.075f, H * 0.12f, alpha(0xFF5AD8FF, 0.3f * fl));
        p.setColor(alpha(0xFF5AD8FF, 0.5f * fl)); c.drawText("VINYL", W * 0.80f + 2, H * 0.09f + 2, p);
        p.setColor(alpha(0xFFE0F8FF, fl)); c.drawText("VINYL", W * 0.80f, H * 0.09f, p);
        p.setFakeBoldText(false); p.setTextAlign(Paint.Align.LEFT);
    }
}
