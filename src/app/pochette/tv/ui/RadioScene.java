package app.pochette.tv.ui;

import android.graphics.*;
import android.os.SystemClock;

import app.pochette.tv.core.TrackState;

/**
 * Podcast / radio artwork: a flat, translucent line-drawing of a small radio
 * (dotted speaker that ripples while playing, tuner with a drifting needle).
 * Only white tints and the track accent are used, so it sits on any wallpaper.
 */
final class RadioScene {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();

    RadioScene() {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Draws the radio starting at bx; returns the right edge of the drawing. */
    float draw(Canvas c, TrackState st, int accent, float bx, int H) {
        boolean playing = st.isPlaying();
        double t = SystemClock.uptimeMillis() / 1000.0;
        float u = H / 720f;                                   // 1 unit = 1px on a 720p screen
        float w = H * 0.46f, h = H * 0.29f;
        float by = (H - h) / 2f + H * 0.02f;
        float lw = 2.5f * u;
        line.setStrokeWidth(lw);

        // antenna
        line.setColor(0x8CFFFFFF);
        c.drawLine(bx + w * 0.72f, by, bx + w * 0.90f, by - h * 0.30f, line);
        fill.setColor(0xCCFFFFFF);
        c.drawCircle(bx + w * 0.90f, by - h * 0.30f, 4.5f * u, fill);

        // body
        float rad = h * 0.14f;
        rf.set(bx, by, bx + w, by + h);
        fill.setColor(0x1AFFFFFF);
        c.drawRoundRect(rf, rad, rad, fill);
        line.setColor(0x80FFFFFF);
        c.drawRoundRect(rf, rad, rad, line);

        // speaker: dots on a grid, clipped to a circle; they swell in a slow ripple while playing
        float sr = h * 0.36f, scx = bx + w * 0.27f, scy = by + h * 0.5f;
        int n = 11;
        float step = sr * 2f / (n - 1);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float dx = -sr + i * step, dy = -sr + j * step;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > sr * 1.02f) continue;
                float k = playing ? 0.5f + 0.5f * (float) Math.sin(t * 3.0 - d / sr * 5.0) : 0.4f;
                fill.setColor(((int) (70 + 140 * k) << 24) | 0xFFFFFF);
                c.drawCircle(scx + dx, scy + dy, (2.0f + 1.6f * k) * u, fill);
            }
        }
        line.setColor(0x59FFFFFF);
        c.drawCircle(scx, scy, sr + step * 0.75f, line);

        // tuner window with ticks and a drifting needle
        float tx0 = bx + w * 0.54f, tx1 = bx + w * 0.92f, ty0 = by + h * 0.18f, ty1 = by + h * 0.46f;
        rf.set(tx0, ty0, tx1, ty1);
        line.setColor(0x66FFFFFF);
        c.drawRoundRect(rf, 6f * u, 6f * u, line);
        line.setStrokeWidth(1.6f * u);
        line.setColor(0x73FFFFFF);
        for (int i = 0; i <= 16; i++) {
            float x = tx0 + (tx1 - tx0) * (0.08f + 0.84f * i / 16f);
            float len = (i % 4 == 0 ? 0.42f : 0.24f) * (ty1 - ty0);
            c.drawLine(x, ty1 - 5f * u, x, ty1 - 5f * u - len, line);
        }
        float nk = 0.5f + 0.5f * (float) Math.sin(t / 37.0);   // very slow drift, so it never looks busy
        float nx = tx0 + (tx1 - tx0) * (0.12f + 0.76f * nk);
        line.setStrokeWidth(2.6f * u);
        line.setColor(accent | 0xFF000000);
        c.drawLine(nx, ty0 + 5f * u, nx, ty1 - 5f * u, line);

        // two knobs
        line.setStrokeWidth(lw);
        float kr = h * 0.095f, ky = by + h * 0.72f;
        for (int i = 0; i < 2; i++) {
            float kx = bx + w * (0.64f + 0.17f * i);
            line.setColor(0x80FFFFFF);
            c.drawCircle(kx, ky, kr, line);
            double a = -0.9 + i * 1.4 + (playing ? 0.15 * Math.sin(t / 5.0 + i) : 0);
            c.drawLine(kx, ky, kx + (float) Math.sin(a) * kr * 0.8f, ky - (float) Math.cos(a) * kr * 0.8f, line);
        }
        line.setStrokeWidth(lw);

        // feet
        line.setColor(0x66FFFFFF);
        c.drawLine(bx + w * 0.12f, by + h + 7f * u, bx + w * 0.22f, by + h + 7f * u, line);
        c.drawLine(bx + w * 0.78f, by + h + 7f * u, bx + w * 0.88f, by + h + 7f * u, line);
        return bx + w;
    }
}
