package app.pochette.tv.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import app.pochette.tv.core.T;
import app.pochette.tv.core.Weather;

/** Corner card of the weather mode: current temperature, today's range and the next hours. */
final class WeatherCard {
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();

    WeatherCard() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    void draw(Canvas c, Paint tp, Typeface light, Typeface regular, int W, int H, boolean f) {
        Weather.Data d = Weather.data();
        if (d == null) return;
        float x0 = W * 0.03f, pt = H * 0.04f, h = H * 0.145f, pad = H * 0.025f;
        float big = H * 0.07f, small = H * 0.026f;
        tp.setAntiAlias(true);
        tp.setTextAlign(Paint.Align.LEFT);
        String temp = Weather.temp(d.temp, f), cond = T.t(Weather.COND_NAMES[d.cond]);
        String range = "max " + Weather.temp(d.max, f) + "   min " + Weather.temp(d.min, f);
        tp.setTypeface(light); tp.setTextSize(big);
        float tw = tp.measureText(temp);
        tp.setTypeface(regular); tp.setTextSize(small * 1.15f);
        float cw = tp.measureText(cond);
        tp.setTextSize(small * 0.9f);
        float pw = d.place.length() > 0 ? tp.measureText(d.place) : 0;
        tp.setTextSize(small);
        float rw = tp.measureText(range);
        float tx = x0 + H * 0.105f;
        float bx = tx + tw + H * 0.02f;
        float blockEnd = Math.max(bx + Math.max(cw, pw), x0 + rw);
        blockEnd = Math.max(blockEnd, x0 + rw);
        int slots = d.next.size();
        float slotW = H * 0.1f;
        float sx = blockEnd + H * 0.04f;
        float right = sx + slots * slotW;

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0x40000000);
        rf.set(x0 - pad, pt, right + (slots > 0 ? 0 : -H * 0.04f) + pad * 0.4f, pt + h);
        c.drawRoundRect(rf, H * 0.025f, H * 0.025f, fill);

        icon(c, d.cond, d.day, x0 + H * 0.04f, pt + h * 0.40f, H * 0.085f, 0xFFFFFFFF);
        tp.setTypeface(light); tp.setTextSize(big); tp.setColor(0xFFFFFFFF);
        c.drawText(temp, tx, pt + h * 0.52f, tp);
        tp.setTypeface(regular); tp.setTextSize(small * 1.15f); tp.setColor(0xFFFFFFFF);
        c.drawText(cond, bx, pt + h * 0.30f, tp);
        tp.setTextSize(small * 0.9f); tp.setColor(0xB3FFFFFF);
        if (d.place.length() > 0) c.drawText(d.place, bx, pt + h * 0.30f + small * 1.2f, tp);
        tp.setTextSize(small); tp.setColor(0xCCFFFFFF);
        c.drawText(range, x0, pt + h * 0.86f, tp);

        if (slots > 0) {
            stroke.setColor(0x33FFFFFF); stroke.setStrokeWidth(2f);
            line(c, sx - H * 0.02f, pt + h * 0.16f, sx - H * 0.02f, pt + h * 0.84f);
        }
        for (int i = 0; i < slots; i++) {
            Weather.Slot s = d.next.get(i);
            float cx = sx + slotW * (i + 0.5f);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(small * 0.95f); tp.setColor(0xB3FFFFFF);
            c.drawText(s.hour + "h", cx, pt + h * 0.26f, tp);
            icon(c, s.cond, s.day, cx, pt + h * 0.5f, small * 1.8f, 0xE6FFFFFF);
            tp.setTextSize(small * 1.1f); tp.setColor(0xFFFFFFFF);
            c.drawText(Weather.temp(s.temp, f), cx, pt + h * 0.86f, tp);
        }
        tp.setTextAlign(Paint.Align.LEFT);
    }

    // ---------------------------------------------------------------- pictograms (flat strokes)

    private void cloud(Canvas c, float cx, float cy, float s) {
        path.reset();
        float l = cx - s * 0.42f, r = cx + s * 0.42f, b = cy + s * 0.2f;
        path.moveTo(l + s * 0.12f, b);
        path.cubicTo(l - s * 0.1f, b, l - s * 0.08f, b - s * 0.34f, l + s * 0.17f, b - s * 0.32f);
        path.cubicTo(l + s * 0.2f, b - s * 0.7f, l + s * 0.68f, b - s * 0.72f, l + s * 0.74f, b - s * 0.36f);
        path.cubicTo(r + s * 0.1f, b - s * 0.4f, r + s * 0.1f, b, r - s * 0.1f, b);
        path.close();
        c.drawPath(path, stroke);
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2) {
        path.reset(); path.moveTo(x1, y1); path.lineTo(x2, y2); c.drawPath(path, stroke);
    }

    void icon(Canvas c, int cond, boolean day, float cx, float cy, float s, int color) {
        stroke.setColor(color);
        stroke.setStrokeWidth(Math.max(2f, s * 0.07f));
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        switch (cond) {
            case Weather.CLEAR:
                if (day) {
                    c.drawCircle(cx, cy, s * 0.2f, stroke);
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4;
                        line(c, cx + (float) Math.cos(a) * s * 0.32f, cy + (float) Math.sin(a) * s * 0.32f, cx + (float) Math.cos(a) * s * 0.45f, cy + (float) Math.sin(a) * s * 0.45f);
                    }
                } else {
                    path.reset();
                    path.moveTo(cx + s * 0.12f, cy - s * 0.38f);
                    path.cubicTo(cx - s * 0.46f, cy - s * 0.3f, cx - s * 0.46f, cy + s * 0.36f, cx + s * 0.14f, cy + s * 0.38f);
                    path.cubicTo(cx - s * 0.16f, cy + s * 0.18f, cx - s * 0.12f, cy - s * 0.2f, cx + s * 0.12f, cy - s * 0.38f);
                    c.drawPath(path, stroke);
                }
                break;
            case Weather.CLOUDY:
                cloud(c, cx, cy, s);
                break;
            case Weather.FOG:
                for (int i = 0; i < 4; i++) line(c, cx - s * (i % 2 == 0 ? 0.4f : 0.3f), cy - s * 0.27f + i * s * 0.18f, cx + s * (i % 2 == 0 ? 0.3f : 0.4f), cy - s * 0.27f + i * s * 0.18f);
                break;
            case Weather.RAIN:
                cloud(c, cx, cy - s * 0.12f, s);
                for (int i = 0; i < 3; i++) line(c, cx - s * 0.22f + i * s * 0.22f, cy + s * 0.22f, cx - s * 0.3f + i * s * 0.22f, cy + s * 0.4f);
                break;
            case Weather.SNOW:
                cloud(c, cx, cy - s * 0.12f, s);
                for (int i = 0; i < 3; i++) c.drawCircle(cx - s * 0.24f + i * s * 0.24f, cy + s * 0.34f, s * 0.035f, fill);
                break;
            default:
                cloud(c, cx, cy - s * 0.12f, s);
                path.reset();
                path.moveTo(cx + s * 0.04f, cy + s * 0.1f); path.lineTo(cx - s * 0.1f, cy + s * 0.28f);
                path.lineTo(cx + s * 0.06f, cy + s * 0.28f); path.lineTo(cx - s * 0.08f, cy + s * 0.46f);
                c.drawPath(path, stroke);
        }
    }
}
