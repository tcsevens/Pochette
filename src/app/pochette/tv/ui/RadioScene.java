package app.pochette.tv.ui;

import android.graphics.*;
import android.os.SystemClock;

import app.pochette.tv.core.TrackState;

/** Podcast / radio artwork: a 1950s table radio with a steaming cup of coffee. */
final class RadioScene {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);

    /** A 1950s table radio with a steaming cup of coffee. Returns the right edge of the drawing. */
    float draw(Canvas c, TrackState st, int accent, float bx, int H) {
        boolean playing = st.isPlaying();
        double t = SystemClock.uptimeMillis() / 1000.0;
        float Rw = H * 0.62f, Rh = H * 0.46f;
        float by = H * 0.53f - Rh / 2f, bottom = by + Rh;
        Shader.TileMode CL = Shader.TileMode.CLAMP;

        // soft shadow on the "table"
        for (int i = 0; i < 6; i++) {
            fill.setColor(0x12000000);
            float k = H * 0.012f * i;
            rf.set(bx - k, bottom - H * 0.004f, bx + Rw + H * 0.36f + k, bottom + H * 0.045f + k * 0.25f);
            c.drawOval(rf, fill);
        }
        // feet
        fill.setShader(null);
        fill.setColor(0xFF24140A);
        c.drawRect(bx + Rw * 0.10f, bottom - H * 0.01f, bx + Rw * 0.20f, bottom + H * 0.022f, fill);
        c.drawRect(bx + Rw * 0.80f, bottom - H * 0.01f, bx + Rw * 0.90f, bottom + H * 0.022f, fill);

        // wooden body
        float rad = Rh * 0.17f;
        rf.set(bx, by, bx + Rw, bottom);
        fill.setShader(new LinearGradient(0, by, 0, bottom, new int[]{0xFF95603A, 0xFF6E4123, 0xFF472814},
                new float[]{0f, 0.45f, 1f}, CL));
        c.drawRoundRect(rf, rad, rad, fill);
        fill.setShader(new LinearGradient(0, by, 0, by + Rh * 0.22f, 0x45FFFFFF, 0x00FFFFFF, CL));
        c.drawRoundRect(rf, rad, rad, fill);
        fill.setShader(null);
        // wood grain
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(Math.max(1f, H * 0.0018f));
        for (int i = 1; i < 14; i++) {
            float gy = by + Rh * (i / 14f);
            fill.setColor(i % 3 == 0 ? 0x1A000000 : 0x0FFFFFFF);
            Path g = new Path();
            g.moveTo(bx + rad * 0.4f, gy);
            g.quadTo(bx + Rw * 0.5f, gy + (float) (Rh * 0.02f * Math.sin(i * 1.7)), bx + Rw - rad * 0.4f, gy);
            c.drawPath(g, fill);
        }
        fill.setStyle(Paint.Style.FILL);

        // cream bakelite front panel
        float m = Rh * 0.075f;
        rf.set(bx + m, by + m, bx + Rw - m, bottom - m * 1.5f);
        fill.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0xFFEFE4C6, 0xFFCDBC93, CL));
        c.drawRoundRect(rf, rad * 0.7f, rad * 0.7f, fill);
        fill.setShader(null);

        // speaker grille with brass slats
        float gx = bx + Rw * 0.31f, gy = by + Rh * 0.47f, gr = Rh * 0.31f;
        if (playing) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(t * 5.0) * Math.sin(t * 1.3));
            fill.setColor((accent & 0x00FFFFFF) | ((int) (25 + 45 * pulse) << 24));
            c.drawCircle(gx, gy, gr * 1.10f, fill);
        }
        fill.setColor(0xFF3A2B1F);
        c.drawCircle(gx, gy, gr, fill);
        c.save();
        Path clip = new Path();
        clip.addCircle(gx, gy, gr * 0.96f, Path.Direction.CW);
        c.clipPath(clip);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(Math.max(1f, H * 0.0015f));
        fill.setColor(0x26FFFFFF);
        for (float yy = gy - gr; yy < gy + gr; yy += gr * 0.06f) c.drawLine(gx - gr, yy, gx + gr, yy, fill);
        fill.setStyle(Paint.Style.FILL);
        for (int i = -3; i <= 3; i++) {
            float yy = gy + i * gr * 0.26f;
            rf.set(gx - gr, yy - gr * 0.035f, gx + gr, yy + gr * 0.035f);
            fill.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0xFFE8C877, 0xFF9C7A35, CL));
            c.drawRect(rf, fill);
        }
        fill.setShader(null);
        c.restore();
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(gr * 0.07f);
        fill.setColor(0xFFB8913F);
        c.drawCircle(gx, gy, gr, fill);
        fill.setStyle(Paint.Style.FILL);

        // tuning dial
        float d0 = bx + Rw * 0.56f, d1 = bx + Rw * 0.90f, e0 = by + Rh * 0.19f, e1 = by + Rh * 0.47f;
        rf.set(d0, e0, d1, e1);
        int glow = playing ? 0xFFFFE7A8 : 0xFFE6D6AE;
        fill.setShader(new LinearGradient(0, e0, 0, e1, glow, 0xFFE8C987, CL));
        c.drawRoundRect(rf, Rh * 0.03f, Rh * 0.03f, fill);
        fill.setShader(null);
        fill.setColor((accent & 0x00FFFFFF) | (playing ? 0x30000000 : 0x14000000));
        c.drawRoundRect(rf, Rh * 0.03f, Rh * 0.03f, fill);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(Rh * 0.022f);
        fill.setColor(0xFFB8913F);
        c.drawRoundRect(rf, Rh * 0.03f, Rh * 0.03f, fill);
        float pad = (d1 - d0) * 0.08f;
        fill.setStrokeWidth(Math.max(1f, H * 0.0022f));
        fill.setColor(0xFF5A4630);
        for (int i = 0; i <= 20; i++) {
            float x = d0 + pad + i * (d1 - d0 - 2 * pad) / 20f;
            float len = (i % 5 == 0) ? (e1 - e0) * 0.32f : (e1 - e0) * 0.16f;
            c.drawLine(x, e0 + pad * 0.8f, x, e0 + pad * 0.8f + len, fill);
        }
        fill.setStyle(Paint.Style.FILL);
        tp.reset();
        tp.setAntiAlias(true);
        tp.setTypeface(medium);
        tp.setTextSize(Rh * 0.058f);
        tp.setColor(0xFF5A4630);
        tp.setTextAlign(Paint.Align.CENTER);
        String[] nums = {"88", "98", "108"};
        for (int i = 0; i < 3; i++) {
            float x = d0 + pad * 1.6f + i * (d1 - d0 - 3.2f * pad) / 2f;
            c.drawText(nums[i], x, e1 - pad * 1.2f, tp);
        }
        String id = st.album.length() > 0 ? st.album : st.artist;
        float frac = 0.12f + 0.76f * ((id.hashCode() & 0x7fffffff) % 1000) / 1000f;
        float nx = d0 + pad + frac * (d1 - d0 - 2 * pad);
        fill.setColor(0xFFC0392B);
        c.drawRect(nx - H * 0.0022f, e0 + pad * 0.5f, nx + H * 0.0022f, e1 - pad * 0.5f, fill);

        // knobs
        float ky = by + Rh * 0.68f, kr = Rh * 0.085f;
        float[] kxs = {bx + Rw * 0.63f, bx + Rw * 0.83f};
        float[] angles = {-35f, 50f};
        for (int i = 0; i < 2; i++) {
            float kx = kxs[i];
            fill.setColor(0x40000000);
            c.drawCircle(kx, ky + kr * 0.15f, kr * 1.05f, fill);
            fill.setShader(new RadialGradient(kx - kr * 0.3f, ky - kr * 0.3f, kr * 1.4f, 0xFF7A4E2E, 0xFF22130A, CL));
            c.drawCircle(kx, ky, kr, fill);
            fill.setShader(null);
            fill.setColor(0xFFC9A45C);
            c.drawCircle(kx, ky, kr * 0.35f, fill);
            c.save();
            c.rotate(angles[i], kx, ky);
            fill.setColor(0xFFEFE4C6);
            c.drawRect(kx - kr * 0.06f, ky - kr * 0.95f, kx + kr * 0.06f, ky - kr * 0.45f, fill);
            c.restore();
        }
        tp.setTypeface(bold);
        tp.setTextSize(Rh * 0.05f);
        tp.setLetterSpacing(0.3f);
        tp.setColor(0xFF9C7A35);
        c.drawText("POCHETTE", (kxs[0] + kxs[1]) / 2f, by + Rh * 0.84f, tp);
        tp.setLetterSpacing(0f);

        // coffee cup on its saucer
        float cupW = H * 0.13f, cupH = H * 0.10f;
        float cx0 = bx + Rw + H * 0.07f;
        float scx = cx0 + cupW / 2f;
        float sw = cupW * 1.7f;
        rf.set(scx - sw / 2f, bottom - H * 0.026f, scx + sw / 2f, bottom + H * 0.006f);
        fill.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0xFFF4EEE6, 0xFFB9AEA1, CL));
        c.drawOval(rf, fill);
        fill.setShader(null);
        float ytop = bottom - H * 0.016f - cupH;
        // handle
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(cupW * 0.09f);
        fill.setColor(0xFFD9D1C5);
        rf.set(cx0 + cupW * 0.78f, ytop + cupH * 0.14f, cx0 + cupW * 1.20f, ytop + cupH * 0.66f);
        c.drawArc(rf, -80f, 160f, false, fill);
        fill.setStyle(Paint.Style.FILL);
        // body
        Path cup = new Path();
        cup.moveTo(cx0, ytop);
        cup.lineTo(cx0 + cupW, ytop);
        cup.cubicTo(cx0 + cupW, ytop + cupH * 0.78f, cx0 + cupW * 0.86f, ytop + cupH, cx0 + cupW * 0.5f, ytop + cupH);
        cup.cubicTo(cx0 + cupW * 0.14f, ytop + cupH, cx0, ytop + cupH * 0.78f, cx0, ytop);
        cup.close();
        fill.setShader(new LinearGradient(cx0, 0, cx0 + cupW, 0, new int[]{0xFFC9BFB2, 0xFFFFFFFF, 0xFFE4DCD0, 0xFFAFA496},
                new float[]{0f, 0.3f, 0.65f, 1f}, CL));
        c.drawPath(cup, fill);
        fill.setShader(null);
        // rim and coffee
        rf.set(cx0, ytop - cupH * 0.09f, cx0 + cupW, ytop + cupH * 0.09f);
        fill.setColor(0xFFF2ECE3);
        c.drawOval(rf, fill);
        rf.set(cx0 + cupW * 0.07f, ytop - cupH * 0.06f, cx0 + cupW * 0.93f, ytop + cupH * 0.065f);
        fill.setShader(new RadialGradient(scx, ytop, cupW * 0.45f, 0xFF8E5B2E, 0xFF3A2010, CL));
        c.drawOval(rf, fill);
        fill.setShader(null);

        // steam: three wisps drifting upwards
        float base = ytop - cupH * 0.04f, Hs = H * 0.26f;
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeCap(Paint.Cap.ROUND);
        fill.setStrokeWidth(H * 0.012f);
        float[] offs = {0.30f, 0.52f, 0.72f};
        for (int k = 0; k < 3; k++) {
            double ph = k * 2.1;
            float x0 = cx0 + cupW * offs[k];
            Path w = new Path();
            for (int j = 0; j <= 30; j++) {
                float s = j / 30f;
                float yy = base - s * Hs;
                float xx = x0 + (float) (cupW * 0.30f * s * (0.5f + 0.5f * s) * Math.sin(6.5 * s - t * 1.7 + ph))
                        + (float) (cupW * 0.08f * s * Math.sin(t * 0.4 + ph));
                if (j == 0) w.moveTo(xx, yy); else w.lineTo(xx, yy);
            }
            int a = (int) (110 * (0.55 + 0.45 * Math.sin(t * 0.8 + ph * 1.3)));
            fill.setShader(new LinearGradient(0, base, 0, base - Hs,
                    new int[]{0x00FFFFFF, (a << 24) | 0xFFFFFF, ((a / 3) << 24) | 0xFFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.18f, 0.6f, 1f}, CL));
            c.drawPath(w, fill);
        }
        fill.setShader(null);
        fill.setStyle(Paint.Style.FILL);
        fill.setStrokeCap(Paint.Cap.BUTT);
        return scx + sw / 2f;
    }
}
