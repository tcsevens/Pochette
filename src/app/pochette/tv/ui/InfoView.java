package app.pochette.tv.ui;

import app.pochette.tv.core.T;
import app.pochette.tv.media.Bio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Info page opened from the "i" button of the player bar: a short Wikipedia text about the song, or about the
 * artist when the song has no page. Flat shapes and text only.
 */
public final class InfoView extends View {
    private static final int BG = 0xFF16102E, PANEL = 0xFF2A2150, LABEL = 0xFFB8A8F0, GOLD = 0xFFFFE9A8, BODY = 0xFFE6DEFF;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);

    private String title = "", artist = "", album = "", rooms = "";
    private boolean loading;
    private Bio.Result result;
    private int which;            // 0 song, 1 artist
    private int scroll;           // first visible line
    private int maxScroll;
    private int gen;

    public InfoView(Context c) {
        super(c);
        setFocusable(false);
        setSoundEffectsEnabled(false);
        setVisibility(GONE);
    }

    public boolean active() { return getVisibility() == VISIBLE; }

    public void open(String title, String artist, String album, String rooms) {
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
        this.rooms = rooms == null ? "" : rooms;
        result = null;
        loading = true;
        which = 0;
        scroll = 0;
        final int g = ++gen;
        final int lang = T.lang();
        final String t = this.title, a = this.artist;
        setVisibility(VISIBLE);
        invalidate();
        new Thread(new Runnable() {
            @Override public void run() {
                final Bio.Result r = Bio.fetch(t, a, lang);
                post(new Runnable() {
                    @Override public void run() {
                        if (g != gen) return;
                        result = r;
                        loading = false;
                        which = r.song != null ? 0 : 1;
                        scroll = 0;
                        invalidate();
                    }
                });
            }
        }, "bio").start();
    }

    public void close() {
        gen++;
        setVisibility(GONE);
    }

    public boolean handleKey(int code) {
        if (code == KeyEvent.KEYCODE_BACK) { close(); return true; }
        boolean both = result != null && result.song != null && result.artist != null;
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) { scroll = Math.min(maxScroll, scroll + 2); invalidate(); }
        else if (code == KeyEvent.KEYCODE_DPAD_UP) { scroll = Math.max(0, scroll - 2); invalidate(); }
        else if (both && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            which = which == 0 ? 1 : 0;
            scroll = 0;
            invalidate();
        }
        return true;
    }

    // ------------------------------------------------------------------ drawing

    private void rect(Canvas c, float l, float t, float r, float b, int color) {
        fill.setColor(color);
        c.drawRect(l, t, r, b, fill);
    }

    private void text(Canvas c, String s, float x, float y, float size, int color, Typeface tf, Paint.Align al) {
        tp.setTypeface(tf);
        tp.setTextSize(size);
        tp.setColor(color);
        tp.setTextAlign(al);
        c.drawText(s, x, y, tp);
    }

    private float measure(String s, float size, Typeface tf) {
        tp.setTypeface(tf);
        tp.setTextSize(size);
        return tp.measureText(s);
    }

    private List<String> wrap(String s, float size, Typeface tf, float maxW) {
        List<String> out = new ArrayList<String>();
        for (String para : s.split("\n")) {
            String line = "";
            for (String w : para.split(" ")) {
                String t = line.length() == 0 ? w : line + " " + w;
                if (line.length() > 0 && measure(t, size, tf) > maxW) { out.add(line); line = w; }
                else line = t;
            }
            out.add(line);
        }
        return out;
    }

    @Override protected void onDraw(Canvas c) {
        float W = getWidth(), H = getHeight(), s = H / 1080f;
        c.drawColor(BG);
        float pl = W * 0.07f, pr = W * 0.93f, pt = H * 0.09f, pb = H * 0.91f;
        rect(c, pl, pt, pr, pb, PANEL);
        float pad = 44f * s, x = pl + pad, w = pr - pl - 2 * pad;

        Bio.Page page = result == null ? null : (which == 0 ? result.song : result.artist);
        String label = page == null ? T.t("Infos") : (which == 0 ? T.t("À propos de la chanson") : T.t("À propos du groupe"));
        text(c, label.toUpperCase(T.locale()), x, pt + 56f * s, 20f * s, GOLD, medium, Paint.Align.LEFT);
        if (page != null) {
            text(c, ("Wikipédia · " + page.lang + " · CC BY-SA 4.0").toUpperCase(T.locale()), pr - pad, pt + 56f * s, 18f * s, LABEL, regular, Paint.Align.RIGHT);
        }

        String head = page != null ? page.title : (title.length() > 0 ? title : artist);
        text(c, head, x, pt + 116f * s, 44f * s, 0xFFFFFFFF, bold, Paint.Align.LEFT);
        String sub = artist;
        if (which == 0 && album.length() > 0) sub = artist.length() > 0 ? artist + "  ·  " + album : album;
        if (sub.length() > 0 && !(page != null && which == 1)) text(c, sub, x, pt + 156f * s, 24f * s, LABEL, regular, Paint.Align.LEFT);

        float bodyTop = pt + 190f * s, bodyBot = pb - 84f * s;
        if (loading) {
            text(c, T.t("Chargement…"), pl + (pr - pl) / 2f, (bodyTop + bodyBot) / 2f, 28f * s, LABEL, regular, Paint.Align.CENTER);
        } else if (page == null) {
            float cx = pl + (pr - pl) / 2f, cy = (bodyTop + bodyBot) / 2f, b = 52f * s;
            rect(c, cx - b / 2f, cy - b - 6f * s, cx + b / 2f, cy - 6f * s, LABEL);
            rect(c, cx - b / 2f + 3f * s, cy - b - 3f * s, cx + b / 2f - 3f * s, cy - 9f * s, PANEL);
            text(c, "i", cx, cy - 18f * s, 34f * s, LABEL, medium, Paint.Align.CENTER);
            text(c, T.t("Pas d'information disponible"), cx, cy + 40f * s, 30f * s, 0xFFFFFFFF, regular, Paint.Align.CENTER);
        } else {
            float size = 26f * s, lh = size * 1.42f;
            List<String> lines = wrap(page.text, size, regular, w - 24f * s);
            int visible = Math.max(1, (int) ((bodyBot - bodyTop) / lh));
            maxScroll = Math.max(0, lines.size() - visible);
            if (scroll > maxScroll) scroll = maxScroll;
            float y = bodyTop + size;
            for (int i = scroll; i < lines.size() && i < scroll + visible; i++) {
                text(c, lines.get(i), x, y, size, BODY, regular, Paint.Align.LEFT);
                y += lh;
            }
            if (maxScroll > 0) {
                float trackH = bodyBot - bodyTop, th = Math.max(30f * s, trackH * visible / lines.size());
                float ty = bodyTop + (trackH - th) * scroll / maxScroll;
                rect(c, pr - pad + 14f * s, bodyTop, pr - pad + 18f * s, bodyBot, 0x33FFFFFF);
                rect(c, pr - pad + 12f * s, ty, pr - pad + 20f * s, ty + th, LABEL);
            }
            if (page.fallback) {
                text(c, T.t("Pas de version dans ta langue : texte en anglais"), x, bodyBot + 30f * s, 18f * s, LABEL, regular, Paint.Align.LEFT);
            }
        }

        // footer: rooms on the left, key hints on the right
        float fy = pb - 28f * s;
        if (rooms.length() > 0) {
            text(c, (T.t("Enceintes") + " · " + rooms).toUpperCase(T.locale()), x, fy, 16f * s, LABEL, regular, Paint.Align.LEFT);
        }
        boolean both = result != null && result.song != null && result.artist != null;
        String hints = (maxScroll > 0 ? T.t("▲▼ défiler") + "     " : "")
                + (both ? T.t("◀ ▶ chanson / groupe") + "     " : "") + T.t("RETOUR fermer");
        text(c, hints, pr - pad, fy, 18f * s, LABEL, regular, Paint.Align.RIGHT);
    }
}
