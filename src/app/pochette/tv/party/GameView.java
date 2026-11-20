package app.pochette.tv.party;

import app.pochette.tv.core.T;
import app.pochette.tv.media.Lyrics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/** Full-screen game display: flat shapes and text only, no names while the music is a mystery. */
public final class GameView extends View {
    private static final int BG = 0xFF16102E, BG_STOP = 0xFF2A0F12, LABEL = 0xFFB8A8F0, DIM = 0x66FFFFFF, ACCENT = 0xFFFF7A5C, GOLD = 0xFFFFE9A8;

    private Game game;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final RectF rf = new RectF();
    private final Rect src = new Rect();

    public GameView(Context c) {
        super(c);
        setFocusable(false);
        setSoundEffectsEnabled(false);
        setVisibility(GONE);
    }

    public void bind(Game g) { game = g; }

    public boolean handleKey(int code) { return game != null && game.onKey(code); }

    // ------------------------------------------------------------------ helpers

    private void text(Canvas c, String s, float x, float y, float size, int color, Typeface tf, Paint.Align al, float maxW) {
        tp.setTypeface(tf);
        tp.setTextSize(size);
        tp.setColor(color);
        tp.setTextAlign(al);
        if (maxW > 0) {
            float w = tp.measureText(s);
            if (w > maxW) tp.setTextSize(size * maxW / w);
        }
        c.drawText(s, x, y, tp);
    }

    private float measure(String s, float size, Typeface tf) {
        tp.setTypeface(tf);
        tp.setTextSize(size);
        return tp.measureText(s);
    }

    private void rect(Canvas c, float l, float t, float r, float b, int color) {
        fill.setColor(color);
        c.drawRect(l, t, r, b, fill);
    }

    private static int alpha(int color, float a) {
        int al = Math.max(0, Math.min(255, (int) (((color >>> 24) & 0xFF) * a)));
        return (al << 24) | (color & 0xFFFFFF);
    }

    private void equalizer(Canvas c, float cx, float cy, float s, int color, boolean live) {
        float t = SystemClock.uptimeMillis() / 1000f;
        int n = 15;
        float bw = 16f * s, gap = 9f * s, total = n * bw + (n - 1) * gap;
        float x = cx - total / 2f;
        for (int i = 0; i < n; i++) {
            float k = live ? 0.5f + 0.5f * (float) Math.sin(t * (3.2f + (i % 4) * 0.7f) + i * 1.3f) : 0.12f;
            float h = (24f + 120f * k) * s;
            rect(c, x, cy - h / 2f, x + bw, cy + h / 2f, color);
            x += bw + gap;
        }
    }

    private void bar(Canvas c, float cx, float y, float w, float h, float frac, int color) {
        rect(c, cx - w / 2f, y, cx + w / 2f, y + h, alpha(0xFFFFFFFF, 0.12f));
        rect(c, cx - w / 2f, y, cx - w / 2f + w * Math.max(0f, Math.min(1f, frac)), y + h, color);
    }

    private void scoreboard(Canvas c, float W, float s) {
        if (!game.scored()) return;
        float w = 74f * s, h = 52f * s, gap = 8f * s;
        float x = W - 64f * s - (game.teams * w + (game.teams - 1) * gap);
        for (int i = 0; i < game.teams; i++) {
            rect(c, x, 36f * s, x + w, 36f * s + h, Game.TEAM_COLORS[i]);
            text(c, String.valueOf(game.scores[i]), x + w / 2f, 36f * s + 37f * s, 28f * s, 0xFFFFFFFF, bold, Paint.Align.CENTER, 0);
            x += w + gap;
        }
    }

    private void chips(Canvas c, float W, float y, float s, boolean selectable) {
        int n = game.teams;
        float w = 132f * s, h = 62f * s, gap = 12f * s;
        float x = (W - (n * w + (n - 1) * gap)) / 2f;
        for (int i = 0; i < n; i++) {
            rect(c, x, y, x + w, y + h, Game.TEAM_COLORS[i]);
            if (selectable && i == game.teamSel) {
                float b = 4f * s;
                rect(c, x - b, y - b, x + w + b, y, 0xFFFFFFFF);
                rect(c, x - b, y + h, x + w + b, y + h + b, 0xFFFFFFFF);
                rect(c, x - b, y, x, y + h, 0xFFFFFFFF);
                rect(c, x + w, y, x + w + b, y + h, 0xFFFFFFFF);
            }
            text(c, T.t(Game.TEAM_NAMES[i]), x + w / 2f, y + 26f * s, 19f * s, 0xFFFFFFFF, medium, Paint.Align.CENTER, w - 12f * s);
            text(c, game.scores[i] + (game.scores[i] > 1 ? " pts" : " pt"), x + w / 2f, y + 50f * s, 17f * s, 0xFFFFFFFF, regular, Paint.Align.CENTER, 0);
            x += w + gap;
        }
    }

    private String roundLabel() {
        String m = T.t(Game.NAMES[game.mode]).toUpperCase(T.locale());
        if (game.round <= 0) return m;
        return m + T.t("  ·  MANCHE ") + game.round + (game.rounds > 0 ? " / " + game.rounds : "");
    }

    // ------------------------------------------------------------------ drawing

    @Override protected void onDraw(Canvas c) {
        if (game == null || !game.active) return;
        game.tick();
        float W = getWidth(), H = getHeight(), s = H / 720f;
        int bg = game.phase == Game.P_STOP ? BG_STOP : BG;
        c.drawColor(bg);

        if (game.phase != Game.P_PODIUM && game.phase != Game.P_ERROR) {
            text(c, roundLabel(), 64f * s, 64f * s, 18f * s, game.phase == Game.P_STOP ? 0xFFD89A94 : LABEL, medium, Paint.Align.LEFT, W * 0.5f);
            scoreboard(c, W, s);
        }

        switch (game.phase) {
            case Game.P_LOAD: drawLoad(c, W, H, s); break;
            case Game.P_GUESS: drawGuess(c, W, H, s); break;
            case Game.P_REVEAL: drawReveal(c, W, H, s); break;
            case Game.P_DANCE: drawDance(c, W, H, s); break;
            case Game.P_STOP: drawStop(c, W, H, s); break;
            case Game.P_PIXEL: drawPixel(c, W, H, s); break;
            case Game.P_LYR_PLAY:
            case Game.P_LYR_WAIT:
            case Game.P_LYR_REVEAL: drawLyrics(c, W, H, s); break;
            case Game.P_PODIUM: drawPodium(c, W, H, s); break;
            case Game.P_ERROR:
                text(c, T.t("Oups"), W / 2f, H * 0.4f, 64f * s, 0xFFFFFFFF, bold, Paint.Align.CENTER, 0);
                text(c, game.message, W / 2f, H * 0.52f, 26f * s, 0xCCFFFFFF, regular, Paint.Align.CENTER, W * 0.8f);
                text(c, T.t("OK ou Retour pour fermer"), W / 2f, H * 0.7f, 20f * s, LABEL, regular, Paint.Align.CENTER, 0);
                break;
        }
        postInvalidateDelayed(40);
    }

    private void drawLoad(Canvas c, float W, float H, float s) {
        equalizer(c, W / 2f, H * 0.42f, s, alpha(ACCENT, 0.35f), false);
        text(c, game.message, W / 2f, H * 0.62f, 28f * s, 0xDDFFFFFF, light, Paint.Align.CENTER, W * 0.8f);
        float t = (SystemClock.uptimeMillis() % 1600) / 1600f;
        rect(c, W / 2f - 120f * s, H * 0.68f, W / 2f + 120f * s, H * 0.68f + 3f * s, alpha(0xFFFFFFFF, 0.12f));
        float x = W / 2f - 120f * s + 240f * s * t;
        rect(c, Math.max(W / 2f - 120f * s, x - 40f * s), H * 0.68f, Math.min(W / 2f + 120f * s, x + 40f * s), H * 0.68f + 3f * s, ACCENT);
    }

    private void drawGuess(Canvas c, float W, float H, float s) {
        long left = Game.GUESS_MS - game.phaseElapsed();
        int secs = (int) Math.ceil(Math.max(0, left) / 1000.0);
        text(c, T.t("Quel est ce morceau ?"), W / 2f, H * 0.22f, 34f * s, 0xDDFFFFFF, light, Paint.Align.CENTER, W * 0.8f);
        equalizer(c, W / 2f, H * 0.42f, s, ACCENT, true);
        text(c, String.valueOf(secs), W / 2f, H * 0.69f, 120f * s, 0xFFFFFFFF, bold, Paint.Align.CENTER, 0);
        bar(c, W / 2f, H * 0.78f, 520f * s, 8f * s, left / (float) Game.GUESS_MS, secs <= 3 ? 0xFFFF5A4A : ACCENT);
    }

    private void drawReveal(Canvas c, float W, float H, float s) {
        long el = game.phaseElapsed();
        float a1 = Math.min(1f, el / 350f);
        float a2 = Math.max(0f, Math.min(1f, (el - Game.TITLE_DELAY_MS) / 350f));
        float art = 250f * s;
        float cx = W / 2f;
        float left = cx - 430f * s;
        if (game.art != null) {
            Bitmap b = game.art;
            int sz = Math.min(b.getWidth(), b.getHeight());
            src.set((b.getWidth() - sz) / 2, (b.getHeight() - sz) / 2, (b.getWidth() - sz) / 2 + sz, (b.getHeight() - sz) / 2 + sz);
            rf.set(left, H * 0.17f, left + art, H * 0.17f + art);
            bmp.setAlpha((int) (255 * a2));
            c.drawBitmap(b, src, rf, bmp);
            bmp.setAlpha(255);
        } else {
            rect(c, left, H * 0.17f, left + art, H * 0.17f + art, alpha(0xFFFFFFFF, 0.06f * a2));
        }
        float tx = left + art + 36f * s;
        float maxW = W - tx - 70f * s;
        if (game.mode == Game.PIXEL) {
            text(c, game.album.length() > 0 ? game.album : game.title, tx, H * 0.17f + 100f * s, 50f * s, alpha(0xFFFFFFFF, a1), bold, Paint.Align.LEFT, maxW);
            text(c, game.title + (game.artist.length() > 0 ? "  ·  " + game.artist : ""), tx, H * 0.17f + 160f * s, 28f * s, alpha(GOLD, a2), regular, Paint.Align.LEFT, maxW);
        } else {
            text(c, game.artist.length() > 0 ? game.artist : "—", tx, H * 0.17f + 100f * s, 54f * s, alpha(0xFFFFFFFF, a1), bold, Paint.Align.LEFT, maxW);
            text(c, game.title, tx, H * 0.17f + 168f * s, 38f * s, alpha(GOLD, a2), regular, Paint.Align.LEFT, maxW);
        }
        chips(c, W, H * 0.67f, s, true);
        String hint = game.awarded ? T.t("Point donné · OK : manche suivante") : T.t("◀ ▶ équipe  ·  OK donne le point  ·  ▼ personne");
        text(c, hint, W / 2f, H * 0.67f + 98f * s, 18f * s, 0xFF9A8FC8, regular, Paint.Align.CENTER, 0);
        bar(c, W / 2f, H * 0.93f, 360f * s, 5f * s, 1f - el / (float) Game.REVEAL_MS, alpha(0xFFFFFFFF, 0.45f));
    }

    private void drawDance(Canvas c, float W, float H, float s) {
        equalizer(c, W / 2f, H * 0.42f, s, ACCENT, true);
        text(c, T.t("Dansez !"), W / 2f, H * 0.66f, 64f * s, 0xFFFFFFFF, bold, Paint.Align.CENTER, 0);
        text(c, T.t("Quand la musique s'arrête, asseyez-vous"), W / 2f, H * 0.76f, 24f * s, 0xAAFFFFFF, light, Paint.Align.CENTER, W * 0.8f);
    }

    private void drawStop(Canvas c, float W, float H, float s) {
        text(c, T.t("STOP !"), W / 2f, H * 0.46f, 170f * s, 0xFFFF5A4A, bold, Paint.Align.CENTER, W * 0.9f);
        text(c, T.t("Asseyez-vous"), W / 2f, H * 0.60f, 48f * s, 0xFFFFFFFF, light, Paint.Align.CENTER, 0);
        boolean blink = (SystemClock.uptimeMillis() / 700) % 2 == 0;
        text(c, T.t("OK : nouvelle musique"), W / 2f, H * 0.76f, 24f * s, blink ? 0xFFFFD0CA : 0xFFB07A74, regular, Paint.Align.CENTER, 0);
    }

    private Bitmap pxSrc, pxSmall;
    private int pxN;
    private final Paint hard = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int[] PIXEL_STEPS = {5, 7, 10, 15, 22, 34, 52, 90};

    private void drawPixel(Canvas c, float W, float H, float s) {
        long el = game.phaseElapsed();
        float frac = Math.max(0f, Math.min(1f, el / (float) Game.PIXEL_MS));
        text(c, T.t("Devinez !"), W / 2f, H * 0.12f, 34f * s, 0xDDFFFFFF, light, Paint.Align.CENTER, 0);
        float size = 470f * s;
        float x = (W - size) / 2f, y = H * 0.19f;
        Bitmap b = game.art;
        if (b != null) {
            int step = Math.min(PIXEL_STEPS.length - 1, (int) (frac * PIXEL_STEPS.length));
            int n = PIXEL_STEPS[step];
            if (b != pxSrc || n != pxN || pxSmall == null) {
                int sz = Math.min(b.getWidth(), b.getHeight());
                Bitmap sq = Bitmap.createBitmap(b, (b.getWidth() - sz) / 2, (b.getHeight() - sz) / 2, sz, sz);
                pxSmall = Bitmap.createScaledBitmap(sq, n, n, true);
                pxSrc = b;
                pxN = n;
            }
            src.set(0, 0, pxSmall.getWidth(), pxSmall.getHeight());
            rf.set(x, y, x + size, y + size);
            hard.setFilterBitmap(false);
            c.drawBitmap(pxSmall, src, rf, hard);
        } else {
            rect(c, x, y, x + size, y + size, alpha(0xFFFFFFFF, 0.06f));
            text(c, T.t("Pas de pochette pour ce morceau"), W / 2f, y + size / 2f, 22f * s, 0x88FFFFFF, regular, Paint.Align.CENTER, size - 40f * s);
        }
        bar(c, W / 2f, H * 0.90f, 470f * s, 7f * s, 1f - frac, ACCENT);
    }

    private void drawLyrics(Canvas c, float W, float H, float s) {
        Lyrics.Result r = game.lyr;
        if (r == null) return;
        boolean wait = game.phase == Game.P_LYR_WAIT, reveal = game.phase == Game.P_LYR_REVEAL;
        int cur;
        if (wait || reveal) cur = game.keyLine;
        else cur = Math.max(0, Math.min(r.lines.size() - 1, r.indexAt(game.lyricPos())));
        float cy = H * 0.50f;
        float maxW = W * 0.86f;
        for (int d = -1; d <= 0; d++) {   // the line just sung stays, the next one only shows when it starts
            int i = cur + d;
            if (i < 0 || i >= r.lines.size()) continue;
            float y = cy + d * 78f * s;
            float a = d == 0 ? 1f : (Math.abs(d) == 1 ? 0.42f : 0.18f);
            if (i == game.keyLine) drawKeyLine(c, W, y, s, maxW, wait, reveal, i == cur, r);
            else if (d == 0) drawSungLine(c, r.lines.get(i), W, y, s, maxW);
            else text(c, r.lines.get(i).text, W / 2f, y, (d == 0 ? 46f : 36f) * s, alpha(0xFFFFFFFF, a), regular, Paint.Align.CENTER, maxW);
        }
        if (wait) {
            text(c, T.t("À vous de chanter !"), W / 2f, H * 0.17f, 40f * s, GOLD, bold, Paint.Align.CENTER, 0);
            text(c, T.t("OK : révéler les paroles"), W / 2f, H * 0.86f, 22f * s, LABEL, regular, Paint.Align.CENTER, 0);
        } else if (reveal) {
            text(c, T.t("Les paroles étaient…"), W / 2f, H * 0.17f, 26f * s, LABEL, light, Paint.Align.CENTER, 0);
            chips(c, W, H * 0.74f, s, true);
            String hint = game.awarded ? T.t("Point donné · OK : manche suivante") : T.t("◀ ▶ équipe  ·  OK donne le point  ·  ▼ personne");
            text(c, hint, W / 2f, H * 0.74f + 98f * s, 18f * s, 0xFF9A8FC8, regular, Paint.Align.CENTER, 0);
        }
    }

    private void drawSungLine(Canvas c, Lyrics.Line l, float W, float y, float s, float maxW) {
        float size = 46f * s;
        tp.setTypeface(medium);
        tp.setTextSize(size);
        float w = tp.measureText(l.text);
        if (w > maxW) { size = size * maxW / w; w = maxW; }
        float x = (W - w) / 2f;
        text(c, l.text, x, y, size, alpha(0xFFFFFFFF, 0.38f), medium, Paint.Align.LEFT, 0);
        float sung = l.sungChars(game.lyricPos());
        int n = Math.max(0, Math.min(l.text.length(), (int) sung));
        float sw = measure(l.text.substring(0, n), size, medium);
        c.save();
        c.clipRect(x, y - size * 1.2f, x + sw, y + size * 0.4f);
        text(c, l.text, x, y, size, 0xFFFFFFFF, medium, Paint.Align.LEFT, 0);
        c.restore();
    }

    private void drawKeyLine(Canvas c, float W, float y, float s, float maxW, boolean wait, boolean reveal, boolean center, Lyrics.Result r) {
        float size = center ? 46f * s : 36f * s;
        String shown = game.shownPart(), hidden = game.hiddenPart();
        String full = shown + hidden;
        float w = measure(full, size, medium);
        if (w > maxW) { size = size * maxW / w; w = maxW; }
        float x = (W - w) / 2f;
        float sw = measure(shown, size, medium);
        Lyrics.Line l = r.lines.get(game.keyLine);
        // sung part of the visible words
        text(c, shown, x, y, size, alpha(0xFFFFFFFF, 0.38f), medium, Paint.Align.LEFT, 0);
        if (!wait && !reveal) {
            float sung = Math.min(shown.length(), l.sungChars(game.lyricPos()));
            float fw = measure(shown.substring(0, (int) sung), size, medium);
            c.save();
            c.clipRect(x, y - size * 1.2f, x + fw, y + size * 0.4f);
            text(c, shown, x, y, size, 0xFFFFFFFF, medium, Paint.Align.LEFT, 0);
            c.restore();
        } else {
            text(c, shown, x, y, size, 0xFFFFFFFF, medium, Paint.Align.LEFT, 0);
        }
        if (reveal) {
            text(c, hidden, x + sw, y, size, GOLD, bold, Paint.Align.LEFT, 0);
        } else {
            boolean blink = !wait || (SystemClock.uptimeMillis() / 500) % 2 == 0;
            float px = x + sw;
            String[] ws = hidden.split(" ", -1);
            float spaceW = measure(" ", size, medium);
            for (String wd : ws) {
                float ww = measure(wd, size, medium);
                if (wd.length() > 0) rect(c, px, y - size * 0.62f, px + ww, y + size * 0.06f, blink ? alpha(GOLD, wait ? 0.55f : 0.30f) : alpha(GOLD, 0.2f));
                px += ww + spaceW;
            }
        }
    }

    private void drawPodium(Canvas c, float W, float H, float s) {
        text(c, T.t("PODIUM"), W / 2f, 96f * s, 30f * s, GOLD, medium, Paint.Align.CENTER, 0);
        int[] rank = game.ranking();
        float colW = 190f * s, gap = 16f * s;
        int[] order = {1, 0, 2};      // 2nd, 1st, 3rd from left to right
        float baseY = H * 0.74f;
        float[] heights = {0.50f, 0.36f, 0.26f};
        for (int k = 0; k < 3; k++) {
            int pos = order[k];
            if (pos >= rank.length) continue;
            int team = rank[pos];
            float x = W / 2f + (k - 1) * (colW + gap) - colW / 2f;
            float h = H * heights[pos];
            rect(c, x, baseY - h, x + colW, baseY, Game.TEAM_COLORS[team]);
            text(c, String.valueOf(pos + 1), x + colW / 2f, baseY - h + 70f * s, 60f * s, 0xFFFFFFFF, bold, Paint.Align.CENTER, 0);
            text(c, T.t(Game.TEAM_NAMES[team]), x + colW / 2f, baseY - h + 110f * s, 24f * s, 0xFFFFFFFF, medium, Paint.Align.CENTER, colW - 16f * s);
            text(c, game.scores[team] + (game.scores[team] > 1 ? T.t(" points") : T.t(" point")), x + colW / 2f, baseY - h + 140f * s, 21f * s, 0xFFFFFFFF, regular, Paint.Align.CENTER, 0);
        }
        if (rank.length > 3) {
            StringBuilder sb = new StringBuilder();
            for (int i = 3; i < rank.length; i++) {
                if (sb.length() > 0) sb.append("   ·   ");
                sb.append(i + 1).append(". ").append(T.t(Game.TEAM_NAMES[rank[i]])).append(' ').append(game.scores[rank[i]]);
            }
            text(c, sb.toString(), W / 2f, baseY + 44f * s, 22f * s, 0xAAFFFFFF, regular, Paint.Align.CENTER, W * 0.85f);
        }
        text(c, T.t("OK ou Retour pour fermer"), W / 2f, H * 0.93f, 20f * s, LABEL, regular, Paint.Align.CENTER, 0);
    }
}
