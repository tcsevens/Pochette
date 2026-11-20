package app.pochette.tv.ui;

import android.graphics.*;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;

import app.pochette.tv.core.Ambiance;
import app.pochette.tv.core.T;
import app.pochette.tv.theme.Scenes2;
import app.pochette.tv.theme.ThemePreview;
import app.pochette.tv.theme.Themes;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/** The welcome screen shown when nothing plays: time-of-day background, big clock, and the ambiance shortcut tiles. */
final class IdleScreen {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final Typeface thin = Typeface.create("sans-serif-thin", Typeface.NORMAL);
    private final SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm", T.locale());
    private final SimpleDateFormat dateFmt = new SimpleDateFormat(T.datePattern(), T.locale());

    private List<Ambiance> shortcuts;
    private int idleSel = -1;
    private long idleSelAt;

    void setShortcuts(List<Ambiance> l) { shortcuts = l; }
    boolean hasShortcuts() { return shortcuts != null && !shortcuts.isEmpty(); }
    void clearSelection() { idleSel = -1; }
    /** True while a tile is highlighted (the highlight fades after a few seconds). */
    boolean active() {
        return idleSel >= 0 && shortcuts != null && idleSel < shortcuts.size()
                && SystemClock.uptimeMillis() - idleSelAt < 12000;
    }
    void move(int dir) {
        if (shortcuts == null || shortcuts.isEmpty()) return;
        boolean was = active();
        idleSel = was ? (idleSel + dir + shortcuts.size()) % shortcuts.size() : 0;
        idleSelAt = SystemClock.uptimeMillis();
    }
    Ambiance selected() { return active() ? shortcuts.get(idleSel) : null; }

    private void resetTp() {
        tp.reset();
        tp.setAntiAlias(true);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setLetterSpacing(0f);
        tp.setShader(null);
        tp.setStyle(Paint.Style.FILL);
    }


    /** Calm slate-blue background with two soft glows drifting very slowly (instead of plain black). */
    // Welcome screen palettes: night, dawn, day, dusk (top, bottom, three light blobs).
    private static final int[][] IDLE_PAL = {
        {0xFF0B1030, 0xFF1E1B4B, 0xFF5B3FD0, 0xFF1F7AA8, 0xFFB03A8C},
        {0xFF2A3266, 0xFF8A5A78, 0xFFFFA06B, 0xFF7B8CFF, 0xFFFF6FA0},
        {0xFF1D4E7A, 0xFF3E8FA8, 0xFF5FD0E0, 0xFF7FA8FF, 0xFFA0E8C0},
        {0xFF2A1F4E, 0xFF8A3F5C, 0xFFFF8A4A, 0xFFC04A9A, 0xFF5A6AE0}};
    private static final float[] IDLE_HR = {0f, 7f, 13f, 19f, 24f};
    private Bitmap bokeh;
    private final Rect bokeSrc = new Rect(0, 0, 64, 64);

    private static int mix(int a, int b, float k) {
        int r = 0;
        for (int sh = 24; sh >= 0; sh -= 8) {
            int x = (a >>> sh) & 255, y = (b >>> sh) & 255;
            r |= (int) (x + (y - x) * k) << sh;
        }
        return r;
    }

    private int[] idlePalette() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        float h = cal.get(java.util.Calendar.HOUR_OF_DAY) + cal.get(java.util.Calendar.MINUTE) / 60f;
        int i = 0;
        while (i < 3 && h >= IDLE_HR[i + 1]) i++;
        float k = (h - IDLE_HR[i]) / (IDLE_HR[i + 1] - IDLE_HR[i]);
        k = k * k * (3 - 2 * k);
        int[] a = IDLE_PAL[i], b = IDLE_PAL[(i + 1) % 4], o = new int[5];
        for (int j = 0; j < 5; j++) o[j] = mix(a[j], b[j], k);
        return o;
    }


    void drawBackground(Canvas c, int W, int H) {
        double t = System.currentTimeMillis() / 1000.0;
        int[] pal = idlePalette();
        fill.setShader(null);
        Scenes2.sh(fill, new LinearGradient(0, 0, 0, H, pal[0], pal[1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, fill);
        for (int i = 0; i < 3; i++) {
            float x = W * (0.5f + 0.38f * (float) Math.sin(t / (17.0 + 6 * i) + i * 2.1));
            float y = H * (0.5f + 0.30f * (float) Math.cos(t / (21.0 + 5 * i) + i * 1.3));
            float r = W * (0.36f + 0.06f * (float) Math.sin(t / 9.0 + i));
            int col = pal[2 + i];
            Scenes2.sh(fill, new RadialGradient(x, y, r, (col & 0x00FFFFFF) | 0x80000000, col & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, fill);
        }
        fill.setShader(null);
        // Rising light dust (soft bokeh).
        if (bokeh == null) {
            bokeh = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
            Canvas bc = new Canvas(bokeh);
            Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
            Scenes2.sh(bp, new RadialGradient(32, 32, 32, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            bc.drawRect(0, 0, 64, 64, bp);
        }
        Paint bp = fill;
        for (int i = 0; i < 26; i++) {
            double ph = ((i * 0.6180339) % 1.0);
            double sp = 1.0 / (50.0 + (i * 7) % 40);
            float u = (float) ((t * sp + ph) % 1.0);
            float x = W * (float) ((i * 0.381966 + 0.07 * Math.sin(t / 13.0 + i)) % 1.0);
            float y = H * (1.08f - 1.16f * u);
            float r = H * (0.012f + 0.004f * (i % 5)) * (1f + (i % 3));
            float tw = 0.55f + 0.45f * (float) Math.sin(t * (0.6 + 0.1 * (i % 4)) + i);
            float fade = Math.min(1f, Math.min(u, 1f - u) * 6f);
            int al = (int) (70 * tw * fade);
            if (al <= 0) continue;
            bp.setShader(null);
            bp.setColor(Color.argb(al, 255, 255, 255));
            rf.set(x - r, y - r, x + r, y + r);
            c.drawBitmap(bokeh, bokeSrc, rf, bp);
        }
        // Gentle vignette
        Scenes2.sh(fill, new RadialGradient(W / 2f, H / 2f, W * 0.75f, 0x00000000, 0x66000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, fill);
        fill.setShader(null);
    }

    private String greeting() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (h < 5) return T.t("Bonne nuit");
        if (h < 12) return T.t("Bonjour");
        if (h < 18) return T.t("Bon après-midi");
        if (h < 22) return T.t("Bonsoir");
        return T.t("Bonne nuit");
    }

    void drawForeground(Canvas c, String sub, int W, int H) {
        double t = System.currentTimeMillis() / 1000.0;
        boolean tiles = shortcuts != null && !shortcuts.isEmpty();
        float cx = W / 2f + (float) (W * 0.02 * Math.sin(t / 173.0));
        float cy = (tiles ? H * 0.40f : H * 0.52f) + (float) (H * 0.012 * Math.sin(t / 211.0 + 1.3));

        resetTp();
        tp.setAntiAlias(true);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTypeface(light);
        tp.setTextSize(H * 0.036f);
        tp.setColor(0xFFFFD9A0);
        c.drawText(greeting().toUpperCase(T.locale()), cx, cy - H * 0.19f, tp);
        // thin rule under the greeting
        fill.setShader(null);
        fill.setColor(0x66FFD9A0);
        c.drawRect(cx - W * 0.02f, cy - H * 0.165f, cx + W * 0.02f, cy - H * 0.163f, fill);

        tp.setTypeface(thin);
        tp.setTextSize(H * 0.22f);
        tp.setColor(0xFFF4F5FA);
        Date d = new Date();
        c.drawText(clockFmt.format(d), cx, cy, tp);

        tp.setTypeface(light);
        tp.setTextSize(H * 0.04f);
        tp.setColor(0xFFD4D9E6);
        String date = dateFmt.format(d);
        if (date.length() > 0) date = Character.toUpperCase(date.charAt(0)) + date.substring(1);
        c.drawText(date, cx, cy + H * 0.07f, tp);

                if (sub != null && sub.length() > 0) {
            tp.setTypeface(regular);
            tp.setTextSize(H * 0.024f);
            tp.setColor(0xFFA9B1C6);
            String[] lines = sub.split("\n");
            for (int i = 0; i < lines.length; i++) c.drawText(lines[i], cx, cy + H * (0.12f + 0.035f * i), tp);
        }
        if (tiles) drawTiles(c, W, H);
    }

    private final Path tilePath = new Path();
    private final Paint tileStroke = new Paint();

    private void drawTiles(Canvas c, int W, int H) {
        boolean act = active();
        int n = shortcuts.size();
        int vis = Math.min(n, 6);
        int first = 0;
        if (act && n > vis) first = Math.max(0, Math.min(n - vis, idleSel - vis / 2));
        float tw = W * 0.145f, th = H * 0.16f, gap = W * 0.012f;
        float total = vis * tw + (vis - 1) * gap;
        float x0 = (W - total) / 2f, y0 = H * 0.70f;

        resetTp();
        tp.setAntiAlias(true);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setTypeface(regular);
        tp.setTextSize(H * 0.02f);
        tp.setColor(0xB0D4D9E6);
        c.drawText(T.t("AMBIANCES"), x0, y0 - H * 0.02f, tp);
        for (int i = 0; i < vis; i++) {
            int idx = first + i;
            Ambiance a = shortcuts.get(idx);
            boolean sel = act && idx == idleSel;
            float x = x0 + i * (tw + gap), grow = sel ? H * 0.012f : 0f;
            float l = x - grow, tp_ = y0 - grow, r = x + tw + grow, b = y0 + th + grow;
            int[] cc = Themes.cardColors(a.theme);
            fill.setShader(null);
            float rad = H * 0.022f;
            tilePath.reset();
            tilePath.addRoundRect(new RectF(l, tp_, r, b), rad, rad, Path.Direction.CW);
            c.save();
            c.clipPath(tilePath);
            Bitmap pv = ThemePreview.get(a.theme);
            if (pv != null) {
                fill.setShader(null);
                Scenes2.sh(fill, ThemePreview.shader(pv, l, tp_, r - l, b - tp_));
            } else Scenes2.sh(fill, new LinearGradient(0, tp_, 0, b, cc[0], cc[1], Shader.TileMode.CLAMP));
            fill.setAlpha(sel ? 255 : 215);
            c.drawRect(l, tp_, r, b, fill);
            Scenes2.sh(fill, new LinearGradient(0, tp_ + (b - tp_) * 0.3f, 0, b, 0x00000000, 0xCC000000, Shader.TileMode.CLAMP));
            c.drawRect(l, tp_, r, b, fill);
            fill.setShader(null);
            c.restore();
            if (sel) {
                tileStroke.setStyle(Paint.Style.STROKE);
                tileStroke.setColor(0xFFFFFFFF);
                tileStroke.setStrokeWidth(Math.max(3f, H * 0.004f));
                tileStroke.setAntiAlias(true);
                c.drawRoundRect(new RectF(l, tp_, r, b), rad, rad, tileStroke);
            }
            tp.setTypeface(sel ? regular : light);
            tp.setTextSize(H * 0.03f);
            tp.setColor(sel ? 0xFFFFFFFF : 0xEEF0F2F8);
            String nm = TextUtils.ellipsize(T.t(a.name), tp, tw - H * 0.03f, TextUtils.TruncateAt.END).toString();
            c.drawText(nm, l + H * 0.015f, b - H * 0.045f, tp);
            tp.setTypeface(light);
            tp.setTextSize(H * 0.019f);
            tp.setColor(0xA0FFFFFF);
            c.drawText(T.t(Ambiance.THEME_NAMES[Math.max(0, Math.min(a.theme, Ambiance.THEME_NAMES.length - 1))]), l + H * 0.015f, b - H * 0.018f, tp);
        }
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTypeface(light);
        tp.setTextSize(H * 0.022f);
        tp.setColor(act ? 0xFFFFFFFF : 0x90D4D9E6);
        c.drawText(act ? T.t("◀ ▶ choisir   ·   OK pour lancer") : T.t("◀ ▶ pour choisir une ambiance"), W / 2f, y0 + th + H * 0.06f, tp);
    }

}
