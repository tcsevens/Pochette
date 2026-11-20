package app.pochette.tv.theme;

import app.pochette.tv.core.Ambiance;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

/** Backgrounds and light effects of the ambiance screens (no sound involved). */
public final class Themes {
    private final Scenes scenes = new Scenes();
    private final Scenes2 scenes2 = new Scenes2();
    private final Scenes3 scenes3 = new Scenes3();

    /** Name of the bundled clip (res/raw) that replaces the drawn scene, or null. */
    public static String video(int theme) {
        String n;
        switch (theme) {
            case Ambiance.T_SNOW: n = "vid_chalet"; break;
            case Ambiance.T_FIRE: n = "vid_coin_du_feu"; break;
            case Ambiance.T_SOUL: n = "vid_jazz"; break;
            case Ambiance.T_RAIN: n = "vid_pluie"; break;
            case Ambiance.T_XMAS: n = "vid_noel"; break;
            default: return null;
        }
        return missing.contains(n) ? null : n;   // a build without the clips falls back to a plain gradient
    }

    private static final java.util.Set<String> missing = new java.util.HashSet<String>();

    /** Notes which bundled clips are absent (the source repository does not ship them). */
    public static void probe(android.content.Context c) {
        for (String n : new String[]{"vid_chalet", "vid_coin_du_feu", "vid_jazz", "vid_pluie", "vid_noel"})
            if (c.getResources().getIdentifier(n, "raw", c.getPackageName()) == 0) missing.add(n);
    }

    static boolean rich2(int theme) { return Scenes3.handles(theme) || theme == Ambiance.T_MORNING || theme == Ambiance.T_SUMMER || theme == Ambiance.T_PARTY; }
    private Bitmap bg;
    private int bgTheme = -1, bgW, bgH;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final Path path = new Path();

    public static boolean darkText(int theme) { return theme == Ambiance.T_MORNING; }

    public static boolean animated(int theme) { return video(theme) == null && (theme == Ambiance.T_PLANET || rich2(theme)); }

    /** Accent colour of the theme, or the artwork's own colour for the normal screen. */
    public static int accent(int theme, int fallback) {
        switch (theme) {
            case Ambiance.T_SNOW: return 0xFFF2C66B;
            case Ambiance.T_MORNING: return 0xFF2B1D2A;
            case Ambiance.T_SUMMER: return 0xFFFFFFFF;
            case Ambiance.T_WAKE: return 0xFFFFFFFF;
            case Ambiance.T_SOUL: return 0xFFE0A458;
            case Ambiance.T_FIRE: return 0xFFFFB15A;
            case Ambiance.T_RAIN: return 0xFF9FC4E8;
            case Ambiance.T_PLANET: return 0xFFFFE9A8;
            case Ambiance.T_PARTY: return 0xFFFF7AC8;
            case Ambiance.T_XMAS: return 0xFFFFD27A;
            default: return fallback;
        }
    }

    /** Two colours for the menu cards. */
    public static int[] cardColors(int theme) {
        switch (theme) {
            case Ambiance.T_SNOW: return new int[]{0xFF7A1A22, 0xFF14261D};
            case Ambiance.T_MORNING: return new int[]{0xFFFFD3B5, 0xFFC9B8E8};
            case Ambiance.T_SUMMER: return new int[]{0xFFFFC85C, 0xFFE8674A};
            case Ambiance.T_WAKE: return new int[]{0xFFFF5E62, 0xFFFF9966};
            case Ambiance.T_SOUL: return new int[]{0xFF4A2A1B, 0xFF1E120D};
            case Ambiance.T_FIRE: return new int[]{0xFFB5400F, 0xFF1A0B07};
            case Ambiance.T_RAIN: return new int[]{0xFF3C5A78, 0xFF121A24};
            case Ambiance.T_PLANET: return new int[]{0xFF1B2A6B, 0xFF080C26};
            case Ambiance.T_PARTY: return new int[]{0xFF6A1FA8, 0xFF1A0B3A};
            case Ambiance.T_XMAS: return new int[]{0xFF8A2A1E, 0xFF14261D};
            case Ambiance.T_RECORD: return new int[]{0xFF6B3A1F, 0xFF1A0F0A};
            default: return new int[]{0xFF3A3340, 0xFF1C1820};
        }
    }

    public void drawBackground(Canvas c, int theme, int W, int H, long nowMs) {
        if (theme == Ambiance.T_MORNING) scenes2.sky(c, theme, W, H, nowMs / 1000.0);
        if (bg == null || bgTheme != theme || bgW != W || bgH != H) {
            boolean rich = theme == Ambiance.T_PLANET;
            bg = rich ? Scenes.build(theme, Math.max(2, W / 2), Math.max(2, H / 2)) : rich2(theme) ? (Scenes3.handles(theme) ? Scenes3.build(theme, Math.max(2, W / 2), Math.max(2, H / 2)) : Scenes2.build(theme, Math.max(2, W / 2), Math.max(2, H / 2))) : build(theme, Math.max(2, W / 4), Math.max(2, H / 4));
            bgTheme = theme; bgW = W; bgH = H;
        }
        src.set(0, 0, bg.getWidth(), bg.getHeight());
        dst.set(0, 0, W, H);
        p.setShader(null);
        p.setColor(0xFFFFFFFF);
        c.drawBitmap(bg, src, dst, p);
    }

    private static Bitmap build(int theme, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        Shader.TileMode CL = Shader.TileMode.CLAMP;
        switch (theme) {
            case Ambiance.T_MORNING:
                lin(c, q, w, h, 0, 0, 0, h, new int[]{0xFFC9B8E8, 0xFFF3B9C8, 0xFFFFD3B5, 0xFFFFE6C7}, new float[]{0f, 0.45f, 0.75f, 1f});
                q.setShader(new RadialGradient(0.76f * w, 0.84f * h, 0.30f * w, new int[]{0xFFFFF6DA, 0xCCFFE2A8, 0x00FFE2A8},
                        new float[]{0f, 0.4f, 1f}, CL));
                c.drawRect(0, 0, w, h, q);
                rad(c, q, w, h, 0.30f * w, 0.25f * h, 0.35f * w, 0x66FFFFFF);
                break;
            case Ambiance.T_SUMMER:
                lin(c, q, w, h, 0, 0, w, h, new int[]{0xFFFFB347, 0xFFFF8C5A, 0xFFC8475A}, new float[]{0f, 0.45f, 1f});
                q.setShader(new RadialGradient(0.12f * w, 0.05f * h, 0.55f * w, new int[]{0xF2FFFADC, 0x80FFE6A0, 0x00FFC878},
                        new float[]{0f, 0.4f, 1f}, CL));
                c.drawRect(0, 0, w, h, q);
                q.setShader(null);
                q.setColor(0x2E000000);   // keeps white text readable
                c.drawRect(0, 0, w, h, q);
                break;
            case Ambiance.T_WAKE:
                lin(c, q, w, h, 0, 0, 0, h, new int[]{0xFF3B2A6B, 0xFFC2456B, 0xFFFF8A5B, 0xFFFFC56B}, new float[]{0f, 0.45f, 0.75f, 1f});
                q.setShader(new RadialGradient(0.5f * w, 1.05f * h, 0.34f * w, new int[]{0xF2FFF0BE, 0x80FFD278, 0x00FFC878},
                        new float[]{0f, 0.5f, 1f}, CL));
                c.drawRect(0, 0, w, h, q);
                break;
            case Ambiance.T_PLANET:
                lin(c, q, w, h, 0, 0, 0, h, new int[]{0xFF070A22, 0xFF0F1642, 0xFF1B2A6B}, new float[]{0f, 0.65f, 1f});
                break;
            default: {   // themes whose clip is missing: their menu colours as a soft gradient
                int[] cc = cardColors(theme);
                lin(c, q, w, h, 0, 0, w, h, new int[]{cc[0], cc[1]}, new float[]{0f, 1f});
                rad(c, q, w, h, 0.75f * w, 0.70f * h, 0.55f * w, 0x33FFFFFF);
                break;
            }
        }
        return b;
    }

    private static void lin(Canvas c, Paint q, int w, int h, float x0, float y0, float x1, float y1, int[] cols, float[] pos) {
        q.setShader(new LinearGradient(x0, y0, x1, y1, cols, pos, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, q);
        q.setShader(null);
    }

    private static void rad(Canvas c, Paint q, int w, int h, float cx, float cy, float r, int color) {
        q.setShader(new RadialGradient(cx, cy, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, q);
        q.setShader(null);
    }

    /** Moving details drawn above the background and behind the music. */
    public void drawEffects(Canvas c, int theme, int W, int H, long nowMs) {
        if (Scenes3.handles(theme)) { scenes3.effects(c, theme, W, H, nowMs / 1000.0); return; }
        if (rich2(theme)) { scenes2.effects(c, theme, W, H, nowMs / 1000.0); return; }
        if (theme == Ambiance.T_PLANET) { scenes.effects(c, theme, W, H, nowMs / 1000.0); return; }
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

    /** Fireplace: a breathing glow, soft flames at the bottom and embers drifting up. */
    private void fire(Canvas c, int W, int H, double t) {
        p.setStyle(Paint.Style.FILL);
        float breathe = (float) (0.5 + 0.5 * Math.sin(t * 1.3) * Math.sin(t * 0.37 + 1.0));
        p.setShader(new RadialGradient(W * 0.5f, H * 1.0f, W * (0.55f + 0.05f * breathe),
                ((int) (70 + 55 * breathe) << 24) | 0xE2791C, 0x00E2791C, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
        float base = H * 0.92f;
        for (int pass = 0; pass < 2; pass++) {          // two staggered rows so the fire looks irregular, not like petals
            int n = pass == 0 ? 9 : 12;
            for (int i = 0; i < n; i++) {
                float x = W * (0.14f + 0.72f * (i + 0.4f * pass) / (n - 1f)) + (pass == 1 ? W * 0.01f : 0f);
                float mid = 1f - Math.abs(x / W - 0.5f) / 0.4f;
                if (mid < 0.1f) mid = 0.1f;
                float ph = i * 2.3f + pass * 4.1f;
                float flick = (float) (0.5 + 0.5 * Math.abs(Math.sin(t * (1.1 + 0.13 * i) + ph)) * (0.7 + 0.3 * Math.sin(t * 3.1 + ph)));
                float h = H * (0.05f + 0.15f * mid) * (0.5f + 0.7f * flick) * (pass == 0 ? 1f : 0.7f);
                float w = W * (pass == 0 ? 0.085f : 0.06f);
                float sway = (float) Math.sin(t * (1.7 + 0.19 * i) + ph) * W * 0.014f;
                flame(c, x, base, w, h, sway, pass == 0 ? 0x40E2791C : 0x4DF08A26);
                flame(c, x, base, w * 0.55f, h * 0.6f, sway * 0.7f, 0x5CFFD27A);
            }
        }
        for (int i = 0; i < 34; i++) {
            float life = (float) ((t * (0.045 + 0.03 * frac(i * 0.41f)) + frac(i * 0.7548777f)) % 1.0);
            float x = W * (0.2f + 0.6f * frac(i * 0.6180339f)) + (float) Math.sin(t * 0.8 + i) * W * 0.02f;
            float y = base - life * H * (0.35f + 0.5f * frac(i * 0.29f));
            int a = (int) (210 * Math.sin(Math.PI * life));
            p.setColor((Math.max(0, a) << 24) | 0xFFB15A);
            c.drawCircle(x, y, H * (0.0014f + 0.0022f * frac(i * 0.53f)), p);
        }
    }

    private void flame(Canvas c, float x, float base, float w, float h, float sway, int color) {
        path.reset();
        path.moveTo(x - w / 2f, base);
        path.quadTo(x - w * 0.6f + sway, base - h * 0.55f, x + sway, base - h);
        path.quadTo(x + w * 0.6f + sway, base - h * 0.55f, x + w / 2f, base);
        path.close();
        p.setColor(color);
        c.drawPath(path, p);
    }

    private static float frac(float v) { return v - (float) Math.floor(v); }
}
