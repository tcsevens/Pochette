package app.pochette.tv.ui;

import app.pochette.tv.core.Ambiance;
import app.pochette.tv.core.T;
import app.pochette.tv.core.TrackState;
import app.pochette.tv.media.Lyrics;
import app.pochette.tv.sonos.Poller;
import app.pochette.tv.theme.Scenes2;
import app.pochette.tv.theme.ThemePreview;
import app.pochette.tv.theme.Themes;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Full-screen "now playing" canvas. Everything is drawn by hand and scaled to the screen height. */
public class NowPlayingView extends View {
    public static final int MODE_CLASSIC = 0, MODE_VINYL = 1, MODE_LYRICS = 2, MODE_BARE = 3, MODE_IMMERSIVE = -1;   // immersive layout retired (never selected)
    public static final String[] MODE_NAMES = {"Classique", "Vinyle", "Paroles", "Fond d'écran"};
    public static final String[] VINYL_NAMES = {"Noir", "Couleur de l'album", "Marbré"};
    public static final String[] KARAOKE_COLOR_NAMES = {"Vert", "Blanc", "Jaune", "Corail", "Cyan", "Rose"};
    public static final int[] KARAOKE_COLORS = {0xFF3FD17A, 0xFFFFFFFF, 0xFFFFE066, 0xFFFF7A5C, 0xFF4FD8F0, 0xFFFF8FC8};
    public static final String[] LYRICS_NAMES = {"Karaoké", "Ligne par ligne", "Désactivées"};
    public static final String[] NO_LYRICS_NAMES = {"Grande pochette", "Pochette + vinyle"};

    private static final long FADE_MS = 800;

    /** Everything needed to draw one state; two of them exist during a cross-fade. */
    private static final class Scene {
        TrackState st;
        Bitmap art, bg;
        int accent = 0xFFEDEDED;
        int[] palette;
        boolean idle;
        String status;
        String idleSub;
        // cached layouts
        String layoutKey;
        StaticLayout label, title, artist, album;
        Bitmap disc;
        int discStyle = -1;
        // lyrics, per track (so a cross-fade never shows the wrong layout)
        Lyrics.Result lyr;
        boolean lyrRequested;
        List<StaticLayout> lyrLayouts;
        int lyrLayoutsW;
        float lyrScroll = Float.NaN;
        int lyrView = -1, prevLyrView = -1;   // 0 = lyrics layout, 1 = cover (no lyrics)
        long lyrViewAt;
    }

    private Scene cur = new Scene(), prev;
    private long fadeStart = -1;
    private boolean gotSnap, introDone;
    private long bootAt = SystemClock.uptimeMillis(), introStart = -1;
    private int mode;
    private boolean clockEnabled = true;
    private int lyricsMode = 0, vinylStyle = 1, noLyricsStyle = 1, lyricsOffset = 0;
    private long holdSince = -1;
    private int theme = Ambiance.T_NORMAL;
    private String themeName = "";
    private final Themes themes = new Themes();
    private float insL, insR, insB, tInsL, tInsR, tInsB;
    private float vinylAngle;
    private long lastFrame;
    private long pausedSince = -1;
    private String toast;
    private long toastUntil;
    private String flash;
    private long flashUntil;
    private long seekPreviewMs = -1, seekPreviewUntil;
    private boolean seekHeld;
    private String notice, noticeBody;
    private long noticeUntil;

    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final Typeface thin = Typeface.create("sans-serif-thin", Typeface.NORMAL);

    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Rect rs = new Rect();
    private final Map<String, Bitmap> shadows = new HashMap<String, Bitmap>();
    private Bitmap sheen;
    private final SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm", T.locale());
    private final SimpleDateFormat dateFmt = new SimpleDateFormat(T.datePattern(), T.locale());

    private final Runnable tick = new Runnable() {
        @Override public void run() { invalidate(); }
    };

    public NowPlayingView(Context c) {
        super(c);
        setSoundEffectsEnabled(false);
        setKeepScreenOn(false);
        cur.idle = true;
        cur.status = T.t("Recherche des enceintes Sonos…");
    }

    // ================================================================ public API

    public void setMode(int m) {
        mode = ((m % MODE_NAMES.length) + MODE_NAMES.length) % MODE_NAMES.length;
        cur.layoutKey = null;
        if (mode == MODE_LYRICS) maybeFetchLyrics(cur);
        invalidate();
    }

    public int getMode() { return mode; }

    private int karaokeColor = KARAOKE_COLORS[0];
    public void setKaraokeColor(int i) { karaokeColor = KARAOKE_COLORS[Math.max(0, Math.min(KARAOKE_COLORS.length - 1, i))]; invalidate(); }
    public void setLyricsMode(int m) { lyricsMode = m; cur.lyrLayouts = null; maybeFetchLyrics(cur); invalidate(); }

    /** Space taken by the menu (px): the music moves aside instead of being covered. */
    private boolean hideProgress;
    public void setHideProgress(boolean h) { if (h != hideProgress) { hideProgress = h; invalidate(); } }

    public void setInsets(float left, float right, float bottom) {
        tInsL = left; tInsR = right; tInsB = bottom;
        invalidate();
    }

    /** Ambiance screen (Ambiance.T_*); name is shown in the small label above the title. */
    public void setTheme(int t, String name) {
        theme = t < 0 || t >= Ambiance.THEME_NAMES.length ? 0 : Ambiance.valid(t);
        themeName = name == null ? "" : name;
        cur.layoutKey = null;
        cur.lyrLayouts = null;
        notifyVideo();
        invalidate();
    }

    public int getTheme() { return theme; }

    /** MainActivity sets this when a video layer sits behind the view. */
    public interface VideoHost { void video(String res); }
    private VideoHost videoHost;
    private String videoNow;
    public void setVideoHost(VideoHost h) { videoHost = h; notifyVideo(); }
    private boolean videoOn() { return videoHost != null && Themes.video(theme) != null && !cur.idle; }
    private void notifyVideo() {
        if (videoHost == null) return;
        String r = videoOn() ? Themes.video(theme) : null;
        if (r == null ? videoNow == null : r.equals(videoNow)) return;
        videoNow = r;
        videoHost.video(r);
    }

    /** White text colours become dark ink on light themes. */
    private int ink(int white) {
        return Themes.darkText(theme) ? (white & 0xFF000000) | 0x2B1D2A : white;
    }

    private int acc(Scene sc) { return Themes.accent(theme, sc.accent); }

    public void setLyricsOffset(int ms) { lyricsOffset = ms; invalidate(); }

    public void setVinylStyle(int v) { vinylStyle = v >= 0 && v < VINYL_NAMES.length ? v : 1; invalidate(); }

    public void setNoLyricsStyle(int v) { noLyricsStyle = v; invalidate(); }

    public void setClockEnabled(boolean b) { clockEnabled = b; invalidate(); }

    public void showToast(String t) { showToast(t, 1800); }

    public void showToast(String t, long ms) {
        toast = t;
        toastUntil = SystemClock.uptimeMillis() + ms;
        invalidate();
    }

    /** Big transient icon in the middle (play/pause/next feedback). */
    public void flash(String glyph) {
        flash = glyph;
        flashUntil = SystemClock.uptimeMillis() + 700;
        invalidate();
    }

    /** Show where a fast-forward / rewind will land, while the key is held (held=true) and briefly after. */
    public void setSeekPreview(long ms, boolean held) {
        seekPreviewMs = ms;
        seekHeld = held;
        seekPreviewUntil = ms < 0 ? 0 : SystemClock.uptimeMillis() + (held ? 60000 : 2500);
        invalidate();
    }

    /** Position to display: the seek preview while it is active, else the live position. */
    public long displayPositionMs(TrackState st) {
        if (seekPreviewMs >= 0 && SystemClock.uptimeMillis() < seekPreviewUntil) return seekPreviewMs;
        return st.positionMs();
    }

    /** A help card at the bottom of the screen (dismissed by time or by hideNotice). */
    public void showNotice(String title, String body, long ms) {
        notice = title;
        noticeBody = body;
        noticeUntil = SystemClock.uptimeMillis() + ms;
        invalidate();
    }

    public boolean hideNotice() {
        boolean was = notice != null && SystemClock.uptimeMillis() < noticeUntil;
        notice = null;
        invalidate();
        return was;
    }

    /** True when the screen currently shows music (not the clock). */
    public boolean isShowingMusic() { return !cur.idle; }

    public TrackState state() { return cur.st; }

    // ---- "home": the clock + ambiance tiles shown over a playing track (music keeps playing underneath)
    private boolean home;
    private Poller.Snapshot lastSnap;
    public boolean homeOn() { return home; }
    public void setHome(boolean on) {
        if (home == on) return;
        home = on;
        idleSel = -1;
        if (lastSnap != null) setSnapshot(lastSnap);
    }

    public void setSnapshot(Poller.Snapshot s) {
        lastSnap = s;
        boolean first = !gotSnap;
        if (first) { gotSnap = true; if (SystemClock.uptimeMillis() - bootAt < 2500) introStart = SystemClock.uptimeMillis(); }   // else a screen is already showing: no black dip
        Scene n = new Scene();
        n.st = s.state;
        n.art = s.art;
        n.bg = s.bg;
        n.accent = s.accent;
        n.palette = s.palette;
        n.status = s.status;
        long now = SystemClock.uptimeMillis();
        TrackState st = s.state;
        if (st != null && st.transport == TrackState.PAUSED) {
            if (pausedSince < 0) pausedSince = now;
        } else {
            pausedSince = -1;
        }
        boolean longPause = false;   // a paused player stays on screen, however long the pause lasts
        n.idle = st == null || !st.hasMusic() || st.transport == TrackState.STOPPED || longPause;
        boolean homing = home && !n.idle;
        if (homing) {
            n.idle = true;
            n.idleSub = "♪  " + st.title + (st.artist.length() > 0 ? "  —  " + st.artist : "");
        } else if (n.idle) {
            if (st == null) n.idleSub = s.status;
            else if (st.isTv) n.idleSub = st.roomName + T.t(" · son de la TV");
            else n.idleSub = T.t("En attente de musique · ") + st.roomName;
        }

        boolean changed = n.idle != cur.idle
                || (!n.idle && (!cur.st.trackKey().equals(st.trackKey()) || n.art != cur.art));
        // While Sonos switches tracks it briefly reports an empty/half-filled state (no title, no artwork yet):
        // keep the current screen for up to 3 s instead of flashing through it.
        boolean transient_ = !home && !cur.idle && st != null && (st.transport == TrackState.TRANSITIONING
                || (n.idle && !st.isTv && st.transport != TrackState.STOPPED && st.transport != TrackState.PAUSED)
                || (!n.idle && changed && n.art == null && cur.art != null && st.artUrl == null));
        if (changed && transient_) {
            if (holdSince < 0) holdSince = now;
            if (now - holdSince < 3000) { invalidate(); return; }
        }
        holdSince = -1;
        if (changed) {
            n.lyrView = cur.lyrView >= 0 ? cur.lyrView : 0;
            prev = cur;
            cur = n;
            fadeStart = now;
            if (first) { prev = null; fadeStart = -1; }   // the first screen fades in from black, not from the clock
            maybeFetchLyrics(cur);
        } else {
            // Same scene: refresh the live data but keep caches.
            if (!n.idle && (cur.st == null || cur.st.transport != st.transport || !cur.st.roomName.equals(st.roomName)
                    || !cur.st.source.equals(st.source))) {
                cur.layoutKey = null;
            }
            cur.st = st;
            cur.status = n.status;
            cur.idleSub = n.idleSub;
            cur.accent = n.accent;
            cur.palette = n.palette;
            cur.bg = n.bg;
        }
        invalidate();
    }

    // ================================================================ lyrics

    private void maybeFetchLyrics(final Scene sc) {
        if (sc.idle || sc.st == null || lyricsMode == 2 || mode != MODE_LYRICS || sc.lyrRequested) return;
        sc.lyrRequested = true;
        if (sc.lyrView < 0) sc.lyrView = 0;
        Lyrics.fetch(sc.st, new Lyrics.Callback() {
            @Override public void onLyrics(Lyrics.Result r) {
                if (sc.st == null || !r.key.equals(sc.st.trackKey())) return;
                sc.lyr = r;
                sc.lyrLayouts = null;
                int v = hasLyrics(sc) ? 0 : 1;
                if (v != sc.lyrView) {
                    sc.prevLyrView = sc.lyrView;
                    sc.lyrView = v;
                    sc.lyrViewAt = SystemClock.uptimeMillis();
                }
                invalidate();
            }
        });
    }

    private int lyrViewOf(Scene sc) {
        if (lyricsMode == 2) return 1;
        return sc.lyrView < 0 ? 0 : sc.lyrView;
    }

    // ================================================================ drawing

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(tick);
    }

    @Override protected void onDraw(Canvas c) {
        notifyVideo();
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        int W = getWidth(), H = getHeight();
        lyricsAnimating = false;
        if (!videoOn()) c.drawColor(Color.BLACK);   // with a clip behind the view, paint nothing so it shows through

        float fade = fadeStart < 0 ? 1f : Math.min(1f, (now - fadeStart) / (float) FADE_MS);
        fade = fade * fade * (3 - 2 * fade);
        if (fade >= 1f) prev = null;

        if (!cur.idle && cur.st != null && cur.st.isPlaying()) vinylAngle = (vinylAngle + dt * 200f) % 360f;

        boolean insetsMoving = Math.abs(tInsL - insL) + Math.abs(tInsR - insR) + Math.abs(tInsB - insB) > 0.5f;
        float ease = 1f - (float) Math.pow(0.001, dt == 0 ? 0.016 : dt);   // ~frame-rate independent smoothing
        insL += (tInsL - insL) * ease; insR += (tInsR - insR) * ease; insB += (tInsB - insB) * ease;
        if (!insetsMoving) { insL = tInsL; insR = tInsR; insB = tInsB; }

        boolean booting = !gotSnap && now - bootAt < 2500;   // plain black while Sonos is being found
        if (booting) {
            postInvalidateDelayed(120);
            return;
        }
        if (!gotSnap && introStart < 0 && !introDone) { introStart = now; }   // nothing found yet: fade the calm screen in once
        introDone = true;
        if (prev != null) {
            // Backgrounds cross-dissolve (no dip to black); the old text/cover fades out first,
            // then the new one fades in, so two titles are never on screen at the same time.
            drawScene(c, prev, W, H, 1f, now, PART_BG);
            drawScene(c, cur, W, H, fade, now, PART_BG);
            float out = Math.max(0f, 1f - fade * 2f), in = Math.max(0f, fade * 2f - 1f);
            if (out > 0.001f) drawScene(c, prev, W, H, out, now, PART_FG);
            if (in > 0.001f) drawScene(c, cur, W, H, in, now, PART_FG);
        } else {
            drawScene(c, cur, W, H, 1f, now, PART_ALL);
        }

        boolean anim = drawOverlays(c, W, H, now);
        if (introStart >= 0) {
            float k = Math.min(1f, (now - introStart) / 1100f);
            if (k >= 1f) introStart = -1;
            else {
                fill.setShader(null);
                fill.setColor(Color.argb((int) (255 * (1f - k * k * (3 - 2 * k))), 0, 0, 0));
                c.drawRect(0, 0, W, H, fill);
                anim = true;
            }
        }

        boolean radioScene = !cur.idle && mode == MODE_VINYL && cur.st != null && (cur.st.isPodcast || cur.st.isRadio);
        boolean playingNow = !cur.idle && cur.st != null && cur.st.isPlaying();
        boolean spinning = radioScene || (playingNow && mode == MODE_VINYL)
                || (playingNow && mode == MODE_LYRICS && lyrViewOf(cur) == 1 && noLyricsStyle == 1)
                || (playingNow && mode == MODE_LYRICS && hasLyrics(cur) && lyricsMode == 0);
        boolean scrolling = !cur.idle && mode == MODE_LYRICS && lyricsAnimating;
        removeCallbacks(tick);
        boolean themeAnim = cur.idle || (Themes.animated(theme) || (theme == Ambiance.T_WAKE && Poller.rampFraction() >= 0f));
        if (prev != null || spinning || scrolling || anim || insetsMoving || holdSince >= 0 || themeAnim) {
            postInvalidateOnAnimation();
        } else {
            postDelayed(tick, 250);
            lastFrame = 0;
        }
    }

    private static final int PART_ALL = 0, PART_BG = 1, PART_FG = 2;

    private void drawScene(Canvas c, Scene sc, int W, int H, float alpha, long now, int part) {
        int save = -1;
        if (alpha < 0.999f) save = c.saveLayerAlpha(0, 0, W, H, (int) (alpha * 255), Canvas.ALL_SAVE_FLAG);
        if (sc.idle) {
            if (part != PART_FG) drawIdleBg(c, W, H);
            if (part != PART_BG) drawIdle(c, sc, W, H, now);
        } else drawPlaying(c, sc, W, H, now, part);
        if (save >= 0) c.restoreToCount(save);
    }

    // ---------------------------------------------------------------- idle (clock)

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
    private List<Ambiance> shortcuts;
    private int idleSel = -1;
    private long idleSelAt;

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

    public void setShortcuts(List<Ambiance> l) { shortcuts = l; }
    public boolean isIdle() { return cur.idle; }
    public boolean idleActive() {
        return cur.idle && idleSel >= 0 && shortcuts != null && idleSel < shortcuts.size()
                && SystemClock.uptimeMillis() - idleSelAt < 12000;
    }
    public void idleMove(int dir) {
        if (shortcuts == null || shortcuts.isEmpty()) return;
        boolean was = idleActive();
        idleSel = was ? (idleSel + dir + shortcuts.size()) % shortcuts.size() : 0;
        idleSelAt = SystemClock.uptimeMillis();
        invalidate();
    }
    public Ambiance idleSelected() { return idleActive() ? shortcuts.get(idleSel) : null; }
    public boolean hasShortcuts() { return shortcuts != null && !shortcuts.isEmpty(); }

    private void drawIdleBg(Canvas c, int W, int H) {
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

    private void drawIdle(Canvas c, Scene sc, int W, int H, long now) {
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

        String sub = sc.idleSub != null ? sc.idleSub : sc.status;
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
        boolean act = idleActive();
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

    // ---------------------------------------------------------------- playing

    private void drawPlaying(Canvas c, Scene sc, int W, int H, long now, int part) {
        partyNow = now;
        TrackState st = sc.st;
        if (part != PART_FG) {
            if (theme == Ambiance.T_NORMAL) drawBackground(c, sc, W, H);
            else if (videoOn()) {                                  // the clip plays behind this view: only dim it for the text
                fill.setShader(null); fill.setColor(0x55000000); c.drawRect(0, 0, W, H, fill);
                Scenes2.sh(fill, new RadialGradient(W / 2f, H / 2f, W * 0.8f, 0x00000000, 0x77000000, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, W, H, fill); fill.setShader(null);
            } else if (Themes.video(theme) != null) drawBackground(c, sc, W, H);
            else { themes.drawBackground(c, theme, W, H, now); themes.drawEffects(c, theme, W, H, now); }
        }
        if (part == PART_BG) return;
        if (theme == Ambiance.T_WAKE) { drawWake(c, sc, W, H); return; }
        if (mode == MODE_BARE) return;   // wallpaper only: nothing drawn over it

        // When the menu is open the music shrinks into the free space instead of being covered.
        int outer = c.save();
        float aw = W - insL - insR, ah = H - insB;
        float k = Math.min(1f, Math.min(aw / W, ah / H));
        if (k < 0.999f || insL > 0.5f) {
            c.translate(insL + (aw - W * k) / 2f, (ah - H * k) / 2f);
            c.scale(k, k);
        }

        double t = System.currentTimeMillis() / 1000.0;
        float dx = (float) (H * 0.012 * Math.sin(t / 37.0));
        float dy = (float) (H * 0.009 * Math.sin(t / 53.0 + 0.7));
        c.save();
        c.translate(dx, dy);

        String key = mode + "|" + theme + themeName + "|" + W + "|" + H + "|" + st.trackKey() + "|" + st.transport + "|" + st.roomName + "|" + st.source;
        boolean paused = st.transport == TrackState.PAUSED;

        if (mode == MODE_IMMERSIVE) {
            float A = H * 0.56f;
            float ax = (W - A) / 2f, ay = H * 0.085f;
            drawArt(c, sc, ax, ay, A, paused);
            int tw = (int) (W * 0.7f);
            buildLayouts(sc, key, tw, H, Layout.Alignment.ALIGN_CENTER, 1);
            float y = ay + A + H * 0.045f;
            float x = (W - tw) / 2f;
            y = drawLayout(c, sc.title, x, y) + H * 0.008f;
            y = drawLayout(c, sc.artist, x, y) + H * 0.03f;
            float pw = W * 0.30f;
            drawProgress(c, sc, (W - pw) / 2f, y, pw, H, true);
        } else if (mode == MODE_LYRICS) {
            int v = lyrViewOf(sc);
            long since = now - sc.lyrViewAt;
            if (sc.prevLyrView >= 0 && sc.prevLyrView != v && since < 500 && lyricsMode != 2) {
                float f = since / 500f;
                f = f * f * (3 - 2 * f);
                drawLyricsView(c, sc, sc.prevLyrView, key, W, H, paused, 1f);
                drawLyricsView(c, sc, v, key, W, H, paused, f);
                lyricsAnimating = true;
            } else {
                drawLyricsView(c, sc, v, key, W, H, paused, 1f);
            }
        } else {
            float A, ax, ay, textX, cover = 0;
            boolean radioScene = mode == MODE_VINYL && (st.isPodcast || st.isRadio);
            if (radioScene) {
                A = H * 0.60f;
                ay = (H - A) / 2f;
                float right = drawRadioScene(c, sc, W * 0.06f, H);
                textX = right + W * 0.045f;
                cover = H * 0.15f;
            } else if (mode == MODE_VINYL) {
                // Sleeve with the record sliding out of it, spinning.
                A = H * 0.60f;
                ax = W * 0.06f;
                ay = (H - A) / 2f;
                float D = A * 0.94f;
                float dcx = ax + A * 1.0f;
                drawVinyl(c, sc, dcx - D / 2f, ay + (A - D) / 2f, D);
                drawArt(c, sc, ax, ay, A, paused);
                textX = dcx + D / 2f + W * 0.045f;
            } else {
                A = H * 0.58f;
                ax = W * 0.09f;
                ay = (H - A) / 2f;
                drawArt(c, sc, ax, ay, A, paused);
                textX = ax + A + W * 0.06f;
            }
            int tw = (int) (W * 0.93f - textX);
            buildLayouts(sc, key, tw, H, Layout.Alignment.ALIGN_NORMAL, 2);
            float blockH = sc.label.getHeight() + H * 0.025f + sc.title.getHeight() + H * 0.012f + sc.artist.getHeight()
                    + (sc.album != null ? H * 0.008f + sc.album.getHeight() : 0) + H * 0.06f + H * 0.04f
                    + (cover > 0 ? cover + H * 0.035f : 0);
            float y = ay + A / 2f - blockH / 2f;
            if (cover > 0) {
                drawArt(c, sc, textX, y, cover, paused);
                y += cover + H * 0.035f;
            }
            y = drawLayout(c, sc.label, textX, y) + H * 0.025f;
            y = drawLayout(c, sc.title, textX, y) + H * 0.012f;
            y = drawLayout(c, sc.artist, textX, y);
            if (sc.album != null) y = drawLayout(c, sc.album, textX, y + H * 0.008f);
            y += H * 0.06f;
            drawProgress(c, sc, textX, y, Math.min(tw, W * 0.42f), H, false);
        }
        c.restore();

        c.restoreToCount(outer);
        if (clockEnabled) {   // drawn outside the scaled content so it stays in the real corner
            resetTp();
            tp.setAntiAlias(true);
            tp.setTypeface(light);
            tp.setTextSize(H * 0.036f);
            tp.setColor(ink(0x99FFFFFF));
            tp.setTextAlign(Paint.Align.RIGHT);
            c.drawText(clockFmt.format(new Date()), W * 0.985f, H * 0.075f, tp);
        }
    }

    /** view 0 = cover + lyrics, 1 = big cover (no lyrics). */
    private void drawLyricsView(Canvas c, Scene sc, int view, String key, int W, int H, boolean paused, float alpha) {
        int save = -1;
        if (alpha < 0.999f) save = c.saveLayerAlpha(0, 0, W, H, (int) (alpha * 255), Canvas.ALL_SAVE_FLAG);
        if (view == 1) {
            drawNoLyrics(c, sc, key, W, H, paused);
        } else {
            float A = H * 0.40f;
            float ax = W * 0.075f, ay = H * 0.17f;
            drawArt(c, sc, ax, ay, A, paused);
            buildLayouts(sc, key, (int) A, H, Layout.Alignment.ALIGN_NORMAL, 2);
            float y = ay + A + H * 0.04f;
            y = drawLayout(c, sc.title, ax, y) + H * 0.006f;
            y = drawLayout(c, sc.artist, ax, y) + H * 0.03f;
            drawProgress(c, sc, ax, y, A, H, false);
            float lx = ax + A + W * 0.07f;
            if (hasLyrics(sc)) drawLyrics(c, sc, lx, W * 0.95f - lx, H);
        }
        if (save >= 0) c.restoreToCount(save);
    }

    /** Wake-up: big clock, greeting, the slow volume rise and a small card for the track. */
    private void drawWake(Canvas c, Scene sc, int W, int H) {
        TrackState st = sc.st;
        Date d = new Date();
        resetTp();
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTypeface(thin);
        tp.setTextSize(H * 0.25f);
        tp.setColor(0xFFFFFFFF);
        c.drawText(clockFmt.format(d), W / 2f, H * 0.33f, tp);
        tp.setTypeface(regular);
        tp.setTextSize(H * 0.04f);
        tp.setColor(0xE6FFFFFF);
        String date = dateFmt.format(d);
        if (date.length() > 0) date = Character.toUpperCase(date.charAt(0)) + date.substring(1);
        int hour = Integer.parseInt(new SimpleDateFormat("H", T.locale()).format(d));
        String hello = hour < 12 ? T.t("Bonjour !") : (hour < 18 ? T.t("Bon après-midi !") : T.t("Bonsoir !"));
        c.drawText(hello + "  " + date, W / 2f, H * 0.41f, tp);

        float rampF = Poller.rampFraction();
        if (rampF >= 0f) {
            float bw = H * 0.72f, bx = (W - bw) / 2f, by = H * 0.53f, bh = H * 0.014f;
            resetTp();
            tp.setTypeface(medium);
            tp.setTextSize(H * 0.021f);
            tp.setLetterSpacing(0.1f);
            tp.setColor(0xFFFFFFFF);
            c.drawText(T.t("LE VOLUME MONTE DOUCEMENT"), bx, by - H * 0.02f, tp);
            tp.setTextAlign(Paint.Align.RIGHT);
            c.drawText("vers " + Poller.rampTo + " %", bx + bw, by - H * 0.02f, tp);
            rf.set(bx, by, bx + bw, by + bh);
            fill.setColor(0x40FFFFFF);
            c.drawRoundRect(rf, bh / 2, bh / 2, fill);
            float cur = Poller.rampFrom + (Poller.rampTo - Poller.rampFrom) * rampF;
            rf.set(bx, by, bx + Math.max(bh, bw * cur / Math.max(1, Poller.rampTo)), by + bh);
            fill.setColor(0xFFFFFFFF);
            c.drawRoundRect(rf, bh / 2, bh / 2, fill);
        }

        // track card
        float ch = H * 0.14f, a = H * 0.10f, pad = H * 0.02f;
        String title = st.title.length() > 0 ? st.title : T.t("Titre inconnu");
        String sub = st.artist.length() > 0 ? st.artist + "  ·  " + st.roomName : st.roomName;
        resetTp();
        tp.setTypeface(bold);
        tp.setTextSize(H * 0.031f);
        float maxW = W * 0.5f;
        String tt = TextUtils.ellipsize(title, tp, maxW, TextUtils.TruncateAt.END).toString();
        float tw = tp.measureText(tt);
        tp.setTypeface(regular);
        tp.setTextSize(H * 0.023f);
        String ss = TextUtils.ellipsize(sub, tp, maxW, TextUtils.TruncateAt.END).toString();
        tw = Math.max(tw, tp.measureText(ss));
        float cw = pad + a + pad + tw + pad * 1.6f;
        float cx = (W - cw) / 2f, cy = H - H * 0.08f - ch;
        rf.set(cx, cy, cx + cw, cy + ch);
        fill.setColor(0x59281432);
        c.drawRoundRect(rf, H * 0.028f, H * 0.028f, fill);
        if (sc.art != null) {
            c.save();
            Path p = new Path();
            RectF r = new RectF(cx + pad, cy + pad, cx + pad + a, cy + pad + a);
            p.addRoundRect(r, H * 0.014f, H * 0.014f, Path.Direction.CW);
            c.clipPath(p);
            centerCropSrc(sc.art);
            bmp.setAlpha(255);
            c.drawBitmap(sc.art, rs, r, bmp);
            c.restore();
        }
        float tx = cx + pad + a + pad;
        tp.setTypeface(bold);
        tp.setTextSize(H * 0.031f);
        tp.setColor(0xFFFFFFFF);
        c.drawText(tt, tx, cy + ch * 0.46f, tp);
        tp.setTypeface(regular);
        tp.setTextSize(H * 0.023f);
        tp.setColor(0xD9FFFFFF);
        c.drawText(ss, tx, cy + ch * 0.72f, tp);
    }

    private void drawBackground(Canvas c, Scene sc, int W, int H) {
        if (sc.bg != null) {
            int bw = sc.bg.getWidth(), bh = sc.bg.getHeight();
            float want = (float) H / W;
            int sh = Math.min(bh, Math.round(bw * want));
            rs.set(0, (bh - sh) / 2, bw, (bh - sh) / 2 + sh);
            rf.set(0, 0, W, H);
            bmp.setAlpha(255);
            c.drawBitmap(sc.bg, rs, rf, bmp);
            fill.setShader(new LinearGradient(0, 0, 0, H, 0x8C000000, 0xC8000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, fill);
            fill.setShader(new RadialGradient(W / 2f, H / 2f, W * 0.75f, 0x00000000, 0x80000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, fill);
            fill.setShader(null);
        } else {
            c.drawColor(0xFF101010);
            int a = (sc.accent & 0x00FFFFFF) | 0x30000000;
            fill.setShader(new RadialGradient(W * 0.25f, H * 0.4f, W * 0.7f, a, 0x00000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, fill);
            fill.setShader(null);
        }
    }

    private long partyNow;

    private void drawArt(Canvas c, Scene sc, float x, float y, float A, boolean paused) {
        boolean pulse = theme == Ambiance.T_PARTY && !paused;
        if (pulse) { float k = 1f + 0.02f * Scenes2.beat(partyNow / 1000.0); c.save(); c.scale(k, k, x + A / 2f, y + A / 2f); }
        float rad = A * 0.022f;
        Bitmap sh = shadow("sq", (int) A, rad);
        float pad = (sh.getWidth() - A) / 2f;
        bmp.setAlpha(255);
        c.drawBitmap(sh, x - pad, y - pad + A * 0.025f, bmp);

        c.save();
        Path p = new Path();
        rf.set(x, y, x + A, y + A);
        p.addRoundRect(rf, rad, rad, Path.Direction.CW);
        c.clipPath(p);
        if (sc.art != null) {
            centerCropSrc(sc.art);
            c.drawBitmap(sc.art, rs, rf, bmp);
        } else {
            fill.setShader(new LinearGradient(x, y, x + A, y + A, 0xFF2C2C2C, 0xFF1A1A1A, Shader.TileMode.CLAMP));
            c.drawRect(rf, fill);
            fill.setShader(null);
            resetTp();
            tp.setAntiAlias(true);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(A * 0.32f);
            tp.setColor(0x55FFFFFF);
            String g = sc.st != null && sc.st.isRadio ? "📻" : "♪";
            c.drawText(g, x + A / 2f, y + A / 2f + A * 0.11f, tp);
        }
        if (paused) {
            fill.setColor(0x73000000);
            c.drawRect(rf, fill);
        }
        c.restore();
        if (pulse) c.restore();
    }

    private void centerCropSrc(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight(), s = Math.min(w, h);
        rs.set((w - s) / 2, (h - s) / 2, (w - s) / 2 + s, (h - s) / 2 + s);
    }

    private void drawVinyl(Canvas c, Scene sc, float x, float y, float D) {
        int d = (int) D;
        if (sc.disc == null || sc.disc.getWidth() != d || sc.discStyle != vinylStyle) {
            sc.disc = makeDisc(sc, vinylStyle, d);
            sc.discStyle = vinylStyle;
        }
        Bitmap sh = shadow("ci", d, 0);
        float pad = (sh.getWidth() - D) / 2f;
        bmp.setAlpha(255);
        c.drawBitmap(sh, x - pad, y - pad + D * 0.02f, bmp);
        c.save();
        c.rotate(vinylAngle, x + D / 2f, y + D / 2f);
        c.drawBitmap(sc.disc, x, y, bmp);
        c.restore();
        if (sheen == null || sheen.getWidth() != d) sheen = makeSheen(d);
        c.drawBitmap(sheen, x, y, bmp);
    }

    private Bitmap makeDisc(Scene sc, int style, int D) {
        Bitmap art = sc.art;
        int accent = sc.accent;
        Bitmap b = Bitmap.createBitmap(D, D, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float r = D / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Shader.TileMode CL = Shader.TileMode.CLAMP;
        int[] pal = sc.palette != null ? sc.palette : new int[]{accent, shade(accent, 0.6f), shade(accent, 1.25f), shade(accent, 0.4f)};

        // 1. the vinyl itself
        if (style == 1) {
            // translucent coloured vinyl, in the album's dominant colour
            p.setShader(new RadialGradient(r, r, r, new int[]{shade(accent, 1.25f), accent, shade(accent, 0.62f)},
                    new float[]{0f, 0.55f, 1f}, CL));
            c.drawCircle(r, r, r, p);
        } else if (style == 2) {
            // marbled: swirl of the artwork's colours
            p.setShader(new SweepGradient(r * 0.94f, r * 1.05f,
                    new int[]{pal[0], pal[1], pal[2], pal[3], pal[0], pal[2], pal[1], pal[0]}, null));
            c.drawCircle(r, r, r, p);
            float[][] blobs = {{0.30f, 0.70f, 0.42f}, {0.72f, 0.28f, 0.38f}, {0.70f, 0.75f, 0.30f}, {0.25f, 0.25f, 0.30f}};
            for (int i = 0; i < blobs.length; i++) {
                int col = pal[(i + 1) % 4];
                p.setShader(new RadialGradient(D * blobs[i][0], D * blobs[i][1], D * blobs[i][2],
                        (col & 0x00FFFFFF) | 0x99000000, col & 0x00FFFFFF, CL));
                c.drawCircle(r, r, r, p);
            }
        } else if (style == 3 && art != null) {
            // picture disc: the artwork printed on the whole record
            c.save();
            Path clip = new Path();
            clip.addCircle(r, r, r, Path.Direction.CW);
            c.clipPath(clip);
            int w = art.getWidth(), h = art.getHeight(), s = Math.min(w, h);
            p.setShader(null);
            c.drawBitmap(art, new Rect((w - s) / 2, (h - s) / 2, (w - s) / 2 + s, (h - s) / 2 + s), new RectF(0, 0, D, D), new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            c.restore();
        } else {
            p.setShader(new RadialGradient(r, r, r, new int[]{0xFF202020, 0xFF0C0C0C, 0xFF181818, 0xFF0A0A0A},
                    new float[]{0f, 0.45f, 0.93f, 1f}, CL));
            c.drawCircle(r, r, r, p);
        }
        p.setShader(null);

        // 2. grooves
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, D / 900f));
        boolean dark = style == 0;
        int i = 0;
        float inner = style == 3 ? 0.16f : 0.40f;
        for (float rr = r * inner; rr < r * 0.965f; rr += r * 0.0105f, i++) {
            if (dark) p.setColor((i % 3 == 0) ? 0x22FFFFFF : 0x10FFFFFF);
            else p.setColor((i % 3 == 0) ? 0x2A000000 : (style == 3 ? 0x12FFFFFF : 0x16000000));
            c.drawCircle(r, r, rr, p);
        }
        p.setStrokeWidth(Math.max(1f, D / 300f));
        p.setColor(dark ? 0x30FFFFFF : 0x40000000);
        c.drawCircle(r, r, r - p.getStrokeWidth() / 2f, p);
        p.setStyle(Paint.Style.FILL);

        // 3. centre label
        float lr = r * 0.36f;
        if (style == 2) {
            p.setColor(0xFFF1E6D2);
            c.drawCircle(r, r, lr, p);
            String name = sc.st != null && sc.st.artist.length() > 0 ? sc.st.artist : (sc.st != null ? sc.st.title : "");
            StaticLayout l = layout(name.toUpperCase(T.locale()), bold, lr * 0.20f, shade(pal[0], 0.55f) | 0xFF000000,
                    (int) (lr * 1.45f), 3, Layout.Alignment.ALIGN_CENTER, 0.08f);
            c.save();
            c.translate(r - lr * 0.725f, r - l.getHeight() / 2f - lr * 0.18f);
            l.draw(c);
            c.restore();
        } else if (style == 3) {
            p.setColor(0x55000000);
            c.drawCircle(r, r, r * 0.09f, p);
        } else {
            c.save();
            Path clip = new Path();
            clip.addCircle(r, r, lr, Path.Direction.CW);
            c.clipPath(clip);
            if (art != null) {
                int w = art.getWidth(), h = art.getHeight(), s = Math.min(w, h);
                c.drawBitmap(art, new Rect((w - s) / 2, (h - s) / 2, (w - s) / 2 + s, (h - s) / 2 + s),
                        new RectF(r - lr, r - lr, r + lr, r + lr), new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
                // a printed label is a bit duller than the artwork on the sleeve
                Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
                dim.setColor(0x38000000);
                c.drawCircle(r, r, lr, dim);
            } else {
                p.setColor(accent);
                c.drawCircle(r, r, lr, p);
            }
            c.restore();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, D / 500f));
            p.setColor(0x33000000);
            c.drawCircle(r, r, lr, p);
            p.setStyle(Paint.Style.FILL);
        }
        p.setColor(0xFF0A0A0A);
        c.drawCircle(r, r, r * 0.028f, p);
        return b;
    }

    private static int shade(int c, float f) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * f));
        int g = Math.min(255, (int) (((c >> 8) & 255) * f));
        int b = Math.min(255, (int) ((c & 255) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private Bitmap makeSheen(int D) {
        Bitmap b = Bitmap.createBitmap(D, D, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float r = D / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new SweepGradient(r, r,
                new int[]{0x00FFFFFF, 0x1CFFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x1CFFFFFF, 0x00FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.08f, 0.18f, 0.5f, 0.58f, 0.68f, 1f}));
        c.drawCircle(r, r, r, p);
        p.setShader(null);
        p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
        c.drawCircle(r, r, r * 0.37f, p);
        return b;
    }

    private Bitmap shadow(String kind, int size, float rad) {
        String k = kind + size;
        Bitmap b = shadows.get(k);
        if (b != null) return b;
        int blur = Math.max(4, size / 14);
        int full = size + blur * 4;
        b = Bitmap.createBitmap(full, full, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xB0000000);
        p.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
        float o = blur * 2;
        if ("ci".equals(kind)) c.drawCircle(full / 2f, full / 2f, size / 2f, p);
        else c.drawRoundRect(new RectF(o, o, o + size, o + size), rad, rad, p);
        shadows.put(k, b);
        return b;
    }

    // ---------------------------------------------------------------- lyrics view without lyrics

    private boolean hasLyrics(Scene sc) {
        Lyrics.Result l = sc.lyr;
        return lyricsMode != 2 && l != null && !l.notFound && !l.instrumental && !l.lines.isEmpty();
    }

    /** Big centred cover (optionally with the record sliding out of it), title and progress below. */
    private void drawNoLyrics(Canvas c, Scene sc, String key, int W, int H, boolean paused) {
        float s = H / 720f;
        float A = (noLyricsStyle == 1 ? 420f : 440f) * s;
        float groupW = noLyricsStyle == 1 ? 640f * s : A;
        float gx = (W - groupW) / 2f, gy = (noLyricsStyle == 1 ? 60f : 56f) * s;
        if (noLyricsStyle == 1) {
            float D = 400f * s;
            drawVinyl(c, sc, gx + 290f * s, gy + 10f * s, D);
        }
        drawArt(c, sc, gx, gy, A, paused);
        int tw = (int) (W * 0.7f);
        buildLayouts(sc, key + "|nolyr", tw, H, Layout.Alignment.ALIGN_CENTER, 1);
        float y = gy + A + 26f * s;
        float x = (W - tw) / 2f;
        y = drawLayout(c, sc.title, x, y) + 6f * s;
        y = drawLayout(c, sc.artist, x, y) + 18f * s;
        float pw = 420f * s;
        drawProgress(c, sc, (W - pw) / 2f, y, pw, H, true);
    }

    // ---------------------------------------------------------------- podcast / radio scene

    /** A 1950s table radio with a steaming cup of coffee. Returns the right edge of the drawing. */
    private float drawRadioScene(Canvas c, Scene sc, float bx, int H) {
        TrackState st = sc.st;
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
            fill.setColor((sc.accent & 0x00FFFFFF) | ((int) (25 + 45 * pulse) << 24));
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
        fill.setColor((sc.accent & 0x00FFFFFF) | (playing ? 0x30000000 : 0x14000000));
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
        resetTp();
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

    // ---------------------------------------------------------------- text

    private void buildLayouts(Scene sc, String key, int width, int H, Layout.Alignment al, int titleLines) {
        if (key.equals(sc.layoutKey)) return;
        sc.layoutKey = key;
        TrackState st = sc.st;
        boolean big = mode == MODE_CLASSIC || mode == MODE_VINYL;
        boolean small = mode == MODE_LYRICS;

        String lab = st.roomName;
        if (theme != Ambiance.T_NORMAL && themeName.length() > 0) lab = T.t("Ambiance ") + themeName + "  ·  " + lab;
        else if (st.source.length() > 0) lab += "  ·  " + st.source;
        if (st.transport == TrackState.PAUSED) lab = T.t("En pause  ·  ") + lab;
        sc.label = layout(lab.toUpperCase(T.locale()), medium, H * 0.024f, theme == Ambiance.T_SNOW ? 0xFFF2C66B : ink(0xA6FFFFFF), width, 1, al, 0.14f);

        boolean noLyr = key.endsWith("|nolyr");
        if (noLyr) { big = false; small = false; }
        String title = st.title.length() > 0 ? st.title : (st.isLineIn ? T.t("Entrée ligne") : T.t("Titre inconnu"));
        float ts = big ? H * 0.068f : (small ? H * 0.036f : (noLyr ? H * 0.05f : H * 0.058f));
        if (big && title.length() > 34) ts = H * 0.056f;
        sc.title = layout(title, bold, ts, ink(0xFFFFFFFF), width, titleLines, al, -0.01f);

        String artist = st.artist;
        if (artist.length() == 0 && st.isRadio && st.album.length() > 0) artist = st.album;
        sc.artist = layout(artist, regular, big ? H * 0.042f : (small ? H * 0.028f : (noLyr ? H * 0.032f : H * 0.036f)), ink(0xE6FFFFFF), width, 1, al, 0f);

        String album = st.album;
        if (album.equals(artist)) album = "";
        sc.album = big && album.length() > 0 ? layout(album, light, H * 0.032f, ink(0x99FFFFFF), width, 1, al, 0f) : null;
    }

    private StaticLayout layout(String text, Typeface tf, float size, int color, int width, int maxLines,
                                Layout.Alignment al, float spacing) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(color);
        p.setLetterSpacing(spacing);
        if (text == null) text = "";
        return StaticLayout.Builder.obtain(text, 0, text.length(), p, Math.max(1, width))
                .setAlignment(al)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .setLineSpacing(0f, 0.95f)
                .build();
    }

    private float drawLayout(Canvas c, StaticLayout l, float x, float y) {
        if (l == null) return y;
        c.save();
        c.translate(x, y);
        l.draw(c);
        c.restore();
        return y + l.getHeight();
    }

    // ---------------------------------------------------------------- progress

    private void drawProgress(Canvas c, Scene sc, float x, float y, float w, int H, boolean centered) {
        TrackState st = sc.st;
        float bh = Math.max(3f, H * 0.0065f);
        resetTp();
        tp.setAntiAlias(true);
        if (st.isLineIn || hideProgress) return;
        if (st.durationSec <= 0) {
            // Live radio: small pill instead of a progress bar.
            tp.setTypeface(medium);
            tp.setTextSize(H * 0.022f);
            tp.setLetterSpacing(0.15f);
            String s = "● EN DIRECT";
            float tw = tp.measureText(s);
            float px = centered ? x + (w - tw) / 2f - H * 0.02f : x;
            rf.set(px, y, px + tw + H * 0.04f, y + H * 0.045f);
            fill.setColor(ink(0x26FFFFFF));
            c.drawRoundRect(rf, rf.height() / 2, rf.height() / 2, fill);
            tp.setColor(acc(sc));
            c.drawText(s, px + H * 0.02f, y + H * 0.031f, tp);
            return;
        }
        long pos = displayPositionMs(st);
        float frac = Math.max(0f, Math.min(1f, pos / (st.durationSec * 1000f)));
        rf.set(x, y, x + w, y + bh);
        fill.setColor(ink(0x33FFFFFF));
        c.drawRoundRect(rf, bh / 2, bh / 2, fill);
        rf.set(x, y, x + Math.max(bh, w * frac), y + bh);
        fill.setColor(acc(sc));
        c.drawRoundRect(rf, bh / 2, bh / 2, fill);

        tp.setTypeface(regular);
        tp.setTextSize(H * 0.024f);
        tp.setColor(ink(0x99FFFFFF));
        float ty = y + bh + H * 0.036f;
        tp.setTextAlign(Paint.Align.LEFT);
        c.drawText(fmt(pos / 1000), x, ty, tp);
        tp.setTextAlign(Paint.Align.RIGHT);
        c.drawText("-" + fmt(Math.max(0, st.durationSec - pos / 1000)), x + w, ty, tp);
    }

    private static String fmt(long s) {
        long m = s / 60, r = s % 60;
        if (m >= 60) return (m / 60) + ":" + two(m % 60) + ":" + two(r);
        return m + ":" + two(r);
    }

    private static String two(long v) { return v < 10 ? "0" + v : String.valueOf(v); }

    // ---------------------------------------------------------------- lyrics

    private boolean lyricsAnimating;

    private void drawLyrics(Canvas c, Scene sc, float x, float w, int H) {
                resetTp();
        tp.setAntiAlias(true);
        tp.setTypeface(medium);
        tp.setTextSize(H * 0.04f);
        tp.setColor(0x80FFFFFF);
        float midY = H * 0.5f;
        int lw = (int) w;
        if (sc.lyrLayouts == null || sc.lyrLayoutsW != lw) {
            sc.lyrLayouts = new ArrayList<StaticLayout>();
            sc.lyrLayoutsW = lw;
            for (Lyrics.Line ln : sc.lyr.lines) {
                String s = ln.text.length() == 0 ? "♪" : ln.text;
                sc.lyrLayouts.add(layout(s, bold, H * 0.052f, ink(0xFFFFFFFF), lw, 4, Layout.Alignment.ALIGN_NORMAL, -0.01f));
            }
            sc.lyrScroll = Float.NaN;
        }
        float gap = H * 0.03f;
        long lpos = sc.st.positionMs() - lyricsOffset;
        int idx = sc.lyr.indexAt(lpos);
        float target = 0, acc = 0;
        float[] tops = new float[sc.lyrLayouts.size()];
        for (int i = 0; i < sc.lyrLayouts.size(); i++) {
            tops[i] = acc;
            if (i == Math.max(0, idx)) target = acc + sc.lyrLayouts.get(i).getHeight() / 2f;
            acc += sc.lyrLayouts.get(i).getHeight() + gap;
        }
        if (idx < 0) target -= H * 0.12f;
        if (Float.isNaN(sc.lyrScroll)) sc.lyrScroll = target;
        float diff = target - sc.lyrScroll;
        if (Math.abs(diff) > 0.5f) {
            sc.lyrScroll += diff * 0.14f;
            lyricsAnimating = true;
        } else {
            sc.lyrScroll = target;
        }

        c.save();
        c.clipRect(x - H * 0.05f, H * (clockEnabled ? 0.13f : 0.06f), x + w + H * 0.05f, H * 0.94f);
        for (int i = 0; i < sc.lyrLayouts.size(); i++) {
            StaticLayout l = sc.lyrLayouts.get(i);
            float top = midY + tops[i] - sc.lyrScroll;
            if (top > H || top + l.getHeight() < 0) continue;
            float centerDist = Math.abs(top + l.getHeight() / 2f - midY) / (H * 0.5f);
            int a;
            if (i == idx) a = 255;
            else a = (int) (255 * Math.max(0.06f, 0.42f - centerDist * 0.45f));
            if (!sc.lyr.synced && i != idx) a = (int) (a * 0.9f);
            float scale = i == idx ? 1f : 0.92f;
            Lyrics.Line ln = sc.lyr.lines.get(i);
            boolean karaoke = i == idx && lyricsMode == 0 && sc.lyr.synced && ln.text.length() > 0;
            if (karaoke) {
                // Unsung part dimmed, sung part bright: the fill sweeps through the words.
                float sung = ln.sungChars(lpos);
                int s1 = c.saveLayerAlpha(x - H * 0.05f, top - H * 0.02f, x + w + H * 0.05f, top + l.getHeight() + H * 0.02f, 105,
                        Canvas.ALL_SAVE_FLAG);
                c.translate(x, top);
                l.draw(c);
                c.restoreToCount(s1);
                if (sung > 0f) {
                    int len = ln.text.length();
                    int off = Math.min(len, (int) sung);
                    float frac = sung - off;
                    int li;
                    float fx;
                    if (off >= len) {
                        li = l.getLineCount() - 1;
                        fx = w + H;
                    } else {
                        li = l.getLineForOffset(off);
                        float x0 = l.getPrimaryHorizontal(off);
                        fx = x0 + frac * l.getPaint().measureText(ln.text.substring(off, off + 1));
                    }
                    int origColor = l.getPaint().getColor();
                    l.getPaint().setColor(karaokeColor);      // the sung part takes the chosen colour
                    c.save();
                    c.translate(x, top);
                    if (li > 0) {
                        c.save();
                        c.clipRect(-H, -H, w + H, l.getLineTop(li));
                        l.draw(c);
                        c.restore();
                    }
                    c.save();
                    c.clipRect(-H, l.getLineTop(li), fx, l.getLineBottom(li));
                    l.draw(c);
                    c.restore();
                    c.restore();
                    l.getPaint().setColor(origColor);
                }
                continue;
            }
            int s = c.saveLayerAlpha(x - H * 0.05f, top - H * 0.02f, x + w + H * 0.05f, top + l.getHeight() + H * 0.02f, a,
                    Canvas.ALL_SAVE_FLAG);
            c.translate(x, top);
            c.scale(scale, scale, 0, l.getHeight() / 2f);
            l.draw(c);
            c.restoreToCount(s);
        }
        c.restore();
        if (!sc.lyr.synced) {
            tp.setTextSize(H * 0.022f);
            tp.setColor(0x66FFFFFF);
            c.drawText(T.t("Défilement approximatif (paroles non synchronisées)"), x, H * 0.97f, tp);
        }
    }

    // ---------------------------------------------------------------- overlays

    private boolean drawOverlays(Canvas c, int W, int H, long now) {
        boolean anim = false;
        if (toast != null && now < toastUntil) {
            float a = Math.min(1f, (toastUntil - now) / 300f);
            resetTp();
            tp.setAntiAlias(true);
            tp.setTypeface(medium);
            tp.setTextSize(H * 0.032f);
            tp.setTextAlign(Paint.Align.CENTER);
            float tw = tp.measureText(toast);
            rf.set(W / 2f - tw / 2f - H * 0.035f, H * 0.05f, W / 2f + tw / 2f + H * 0.035f, H * 0.05f + H * 0.07f);
            fill.setColor(Color.argb((int) (190 * a), 20, 20, 20));
            c.drawRoundRect(rf, rf.height() / 2, rf.height() / 2, fill);
            tp.setColor(Color.argb((int) (255 * a), 255, 255, 255));
            c.drawText(toast, W / 2f, H * 0.05f + H * 0.047f, tp);
            anim = true;
        }
        if (seekPreviewMs >= 0 && seekHeld && cur.st != null && cur.st.durationSec > 0) {
            String txt = fmt(seekPreviewMs / 1000) + "  /  " + fmt(cur.st.durationSec);
            resetTp();
            tp.setAntiAlias(true);
            tp.setTypeface(bold);
            tp.setTextSize(H * 0.05f);
            tp.setTextAlign(Paint.Align.CENTER);
            float tw = tp.measureText(txt);
            rf.set(W / 2f - tw / 2f - H * 0.04f, H * 0.045f, W / 2f + tw / 2f + H * 0.04f, H * 0.045f + H * 0.09f);
            fill.setColor(0xCC000000);
            c.drawRoundRect(rf, rf.height() / 2, rf.height() / 2, fill);
            tp.setColor(0xFFFFFFFF);
            c.drawText(txt, W / 2f, H * 0.045f + H * 0.064f, tp);
            anim = true;
        }
        if (notice != null && now < noticeUntil) {
            float cw = W * 0.56f, pad = H * 0.03f;
            StaticLayout body = layout(noticeBody, regular, H * 0.026f, 0xE6FFFFFF, (int) (cw - 2 * pad - H * 0.05f), 6,
                    Layout.Alignment.ALIGN_NORMAL, 0f);
            float ch = pad * 2 + H * 0.04f + H * 0.012f + body.getHeight();
            float cx = (W - cw) / 2f, cy = H - H * 0.06f - ch;
            rf.set(cx, cy, cx + cw, cy + ch);
            fill.setColor(0xF0201418);
            c.drawRoundRect(rf, H * 0.02f, H * 0.02f, fill);
            fill.setColor(0xFFE8A33A);
            c.drawCircle(cx + pad + H * 0.018f, cy + pad + H * 0.02f, H * 0.018f, fill);
            resetTp();
            tp.setAntiAlias(true);
            tp.setTypeface(bold);
            tp.setTextSize(H * 0.024f);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setColor(0xFF201418);
            c.drawText("!", cx + pad + H * 0.018f, cy + pad + H * 0.029f, tp);
            tp.setTextAlign(Paint.Align.LEFT);
            tp.setTextSize(H * 0.032f);
            tp.setColor(0xFFFFFFFF);
            c.drawText(notice, cx + pad + H * 0.05f, cy + pad + H * 0.032f, tp);
            c.save();
            c.translate(cx + pad + H * 0.05f, cy + pad + H * 0.04f + H * 0.012f);
            body.draw(c);
            c.restore();
            postDelayed(tick, 500);
        }
        if (flash != null && now < flashUntil) {
            float p = 1f - (flashUntil - now) / 700f;
            float a = p < 0.2f ? p / 0.2f : 1f - (p - 0.2f) / 0.8f;
            float R = H * (0.09f + 0.02f * p);
            fill.setColor(Color.argb((int) (150 * a), 0, 0, 0));
            c.drawCircle(W / 2f, H / 2f, R, fill);
            fill.setColor(Color.argb((int) (255 * a), 255, 255, 255));
            drawGlyph(c, flash, W / 2f, H / 2f, R * 0.42f);
            anim = true;
        }
        return anim;
    }

    /** Transport icons drawn as shapes (no font/emoji dependency). kind: play, pause, next, prev. */
    private void drawGlyph(Canvas c, String kind, float cx, float cy, float s) {
        Path p = new Path();
        if ("pause".equals(kind)) {
            float bw = s * 0.42f, gap = s * 0.36f;
            rf.set(cx - gap / 2 - bw, cy - s, cx - gap / 2, cy + s);
            c.drawRoundRect(rf, bw * 0.2f, bw * 0.2f, fill);
            rf.set(cx + gap / 2, cy - s, cx + gap / 2 + bw, cy + s);
            c.drawRoundRect(rf, bw * 0.2f, bw * 0.2f, fill);
            return;
        }
        if ("play".equals(kind)) {
            p.moveTo(cx - s * 0.7f, cy - s);
            p.lineTo(cx + s, cy);
            p.lineTo(cx - s * 0.7f, cy + s);
            p.close();
            c.drawPath(p, fill);
            return;
        }
        float dir = "next".equals(kind) ? 1f : -1f;
        float h = s * 0.8f;
        for (int i = 0; i < 2; i++) {
            float ox = cx + dir * (i == 0 ? -s * 0.85f : -s * 0.05f);
            p.reset();
            p.moveTo(ox, cy - h);
            p.lineTo(ox + dir * s * 0.8f, cy);
            p.lineTo(ox, cy + h);
            p.close();
            c.drawPath(p, fill);
        }
        float bx = cx + dir * s * 0.8f;
        rf.set(Math.min(bx, bx + dir * s * 0.18f), cy - h, Math.max(bx, bx + dir * s * 0.18f), cy + h);
        c.drawRect(rf, fill);
    }

    /** Paint.reset() does not reliably restore the text alignment on every TV: set everything explicitly. */
    private void resetTp() {
        tp.reset();
        tp.setAntiAlias(true);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setLetterSpacing(0f);
        tp.setShader(null);
        tp.setStyle(Paint.Style.FILL);
    }
}
