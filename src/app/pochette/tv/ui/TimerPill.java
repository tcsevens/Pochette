package app.pochette.tv.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import app.pochette.tv.core.KitchenTimer;
import app.pochette.tv.core.T;

/** Kitchen timer: a small pill in the bottom corner with a ring that empties, drawn over any screen. */
final class TimerPill {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();

    TimerPill() {
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
    }

    /** True while something is drawn, so the caller keeps the screen refreshing. */
    boolean draw(Canvas c, Paint tp, Typeface regular, Typeface bold, int W, int H, float bottomInset) {
        boolean running = KitchenTimer.running(), done = !running && KitchenTimer.doneShown();
        if (!running && !done) return false;
        if (running && KitchenTimer.remaining() > 60000) return false;     // the alarm clock of the bottom bar shows it before that
        float h = H * 0.085f, r = h * 0.30f;
        String time = running ? KitchenTimer.format(KitchenTimer.remaining()) : T.t("Terminé");
        tp.setAntiAlias(true);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setTypeface(bold); tp.setTextSize(h * 0.40f);
        float tw = tp.measureText(time);
        float w = h * 0.30f + r * 2 + h * 0.28f + tw + h * 0.42f;
        float bottom = Math.min(H * 0.885f, H - bottomInset - H * 0.015f);       // always above the bottom bar when it shows
        float x = W * 0.97f - w, y = bottom - h;
        float pulse = done ? 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 220.0) : 0f;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(done ? blend(0xE62C2438, 0xE65DCAA5, pulse) : 0xD92C2438);
        rf.set(x, y, x + w, y + h);
        c.drawRoundRect(rf, h / 2f, h / 2f, fill);
        float cx = x + h * 0.30f + r, cy = y + h / 2f;
        ring.setStrokeWidth(h * 0.085f);
        ring.setColor(0x55FFFFFF);
        c.drawCircle(cx, cy, r, ring);
        ring.setColor(done ? 0xFFFFFFFF : 0xFF5DCAA5);
        rf.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(rf, -90f, 360f * (done ? 1f : KitchenTimer.fraction()), false, ring);
        tp.setColor(0xFFFFFFFF);
        c.drawText(time, cx + r + h * 0.28f, cy + h * 0.14f, tp);
        return true;
    }

    private static int blend(int a, int b, float t) {
        int out = 0;
        for (int s = 0; s <= 24; s += 8) {
            int ca = (a >> s) & 255, cb = (b >> s) & 255;
            out |= ((int) (ca + (cb - ca) * t) & 255) << s;
        }
        return out;
    }
}
