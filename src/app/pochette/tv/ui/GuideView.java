package app.pochette.tv.ui;

import app.pochette.tv.core.T;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.View;

import java.util.Random;

/**
 * The quick guide: three full-screen pages drawn like hand sketches (how to move around, then what each menu does).
 * Shown once at the first launch, and again from Settings › Guide.
 */
public final class GuideView extends View {
    public interface Host { void guideClosed(); }

    private static final int BG = 0xFF160E12, INK = 0xFFF4ECE0, DIM = 0xFFB0A6A0, ACC = 0xFFF2C66B, CORAL = 0xFFFF7A5C;
    private static final int PAGES = 4;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random rnd = new Random();
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private Host host;
    private int page;
    private float s = 1f;

    public GuideView(Context c) {
        super(c);
        setFocusable(false);
        setSoundEffectsEnabled(false);
        setVisibility(GONE);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setHost(Host h) { host = h; }
    public boolean active() { return getVisibility() == VISIBLE; }

    public void open() { page = 0; setVisibility(VISIBLE); invalidate(); }

    public void close() {
        if (!active()) return;
        setVisibility(GONE);
        if (host != null) host.guideClosed();
    }

    public void handleKey(int code) {
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (code == KeyEvent.KEYCODE_BACK) close();
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_DOWN || ok) {
            if (page < PAGES - 1) { page++; invalidate(); } else if (ok) close();
        } else if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_UP) {
            if (page > 0) { page--; invalidate(); }
        }
    }

    // ---------------------------------------------------------------- sketch helpers (1080-high design units)

    private void line(Canvas c, float x1, float y1, float x2, float y2, int col, float w, float j) {
        for (int pass = 0; pass < 2; pass++) {
            int n = Math.max(2, (int) (Math.hypot(x2 - x1, y2 - y1) / 40f));
            path.reset();
            for (int i = 0; i <= n; i++) {
                float t = i / (float) n;
                float x = x1 + (x2 - x1) * t + (rnd.nextFloat() * 2 - 1) * j;
                float y = y1 + (y2 - y1) * t + (rnd.nextFloat() * 2 - 1) * j;
                if (i == 0) path.moveTo(x * s, y * s); else path.lineTo(x * s, y * s);
            }
            stroke.setColor(col); stroke.setStrokeWidth(w * s);
            c.drawPath(path, stroke);
        }
    }

    private void rect(Canvas c, float x0, float y0, float x1, float y1, int col, float w, float j) {
        line(c, x0, y0, x1, y0, col, w, j); line(c, x1, y0, x1, y1, col, w, j);
        line(c, x1, y1, x0, y1, col, w, j); line(c, x0, y1, x0, y0, col, w, j);
    }

    private void circle(Canvas c, float cx, float cy, float r, int col, float w) {
        for (int pass = 0; pass < 2; pass++) {
            path.reset();
            for (int i = 0; i <= 40; i++) {
                double a = i / 40.0 * 2 * Math.PI + (rnd.nextFloat() - 0.5f) * 0.1;
                float rr = r + (rnd.nextFloat() * 2 - 1) * 2.5f;
                float x = (float) (cx + rr * Math.cos(a)), y = (float) (cy + rr * Math.sin(a));
                if (i == 0) path.moveTo(x * s, y * s); else path.lineTo(x * s, y * s);
            }
            stroke.setColor(col); stroke.setStrokeWidth(w * s);
            c.drawPath(path, stroke);
        }
    }

    /** Open arrow head: u, d, l or r. */
    private void tri(Canvas c, float cx, float cy, char dir, int col, float size) {
        float a = size, b = size * 0.7f, d = size * 0.8f;
        switch (dir) {
            case 'u': line(c, cx - a, cy + b, cx, cy - d, col, 4, 1.5f); line(c, cx, cy - d, cx + a, cy + b, col, 4, 1.5f); break;
            case 'd': line(c, cx - a, cy - b, cx, cy + d, col, 4, 1.5f); line(c, cx, cy + d, cx + a, cy - b, col, 4, 1.5f); break;
            case 'l': line(c, cx + b, cy - a, cx - d, cy, col, 4, 1.5f); line(c, cx - d, cy, cx + b, cy + a, col, 4, 1.5f); break;
            default: line(c, cx - b, cy - a, cx + d, cy, col, 4, 1.5f); line(c, cx + d, cy, cx - b, cy + a, col, 4, 1.5f);
        }
    }

    private void txt(Canvas c, String t, float x, float y, float size, int col, Typeface tf, Paint.Align al) {
        tp.setTypeface(tf); tp.setTextSize(size * s); tp.setColor(col); tp.setTextAlign(al);
        c.drawText(t, x * s, y * s, tp);
    }

    /** Wrapped paragraph; returns the y where the next block can start. */
    private float para(Canvas c, float x, float y, String head, String body, float maxW) {
        txt(c, head, x, y + 34, 40, ACC, bold, Paint.Align.LEFT);
        float yy = y + 56 + 14;
        tp.setTypeface(regular); tp.setTextSize(29 * s);
        String rest = body;
        while (rest.length() > 0) {
            int n = tp.breakText(rest, true, maxW * s, null);
            if (n < rest.length()) {
                int sp = rest.lastIndexOf(' ', n);
                if (sp > 0) n = sp;
            }
            String ln = rest.substring(0, n).trim();
            txt(c, ln, x, yy, 29, INK, regular, Paint.Align.LEFT);
            rest = rest.substring(n).trim();
            yy += 38;
        }
        return yy + 16;
    }

    // ---------------------------------------------------------------- pages

    @Override protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        s = H / 1080f;
        float w = W / s;                       // width in design units
        rnd.setSeed(1234L + page * 77L);       // same strokes at every redraw
        fill.setColor(BG);
        c.drawRect(0, 0, W, H, fill);

        String[] titles = {T.t("Bienvenue dans Pochette"), T.t("Le menu (1/2)"), T.t("Le menu (2/2)"), T.t("À toi de jouer !")};
        txt(c, titles[page], 110, 140, 64, INK, bold, Paint.Align.LEFT);
        line(c, 110, 170, 560, 170, ACC, 5, 2);
        txt(c, (page + 1) + " / " + PAGES, w - 110, 118, 30, DIM, medium, Paint.Align.RIGHT);

        if (page == 0) pageNavigate(c, w);
        else if (page == 1) pageMenuReading(c, w);
        else if (page == 3) pageStart(c, w);
        else pageMenu(c, w, new int[]{4, 5, 6, 7}, new String[]{"Pièces", "Affichage", "Réglages", "À propos"},
                new String[]{T.t("Choisis toi-même où le son est diffusé."),
                        T.t("Tout se configure ici : style, vinyle, paroles, fond d'écran, horloge, transparence. Tu peux tout tester en direct."),
                        T.t("Enceinte suivie, lancement automatique, langue, Wi-Fi des invités, Spotify, et ce Guide."),
                        T.t("Version, crédits et vie privée.")},
                "", false);

        String nav = page < PAGES - 1 ? T.t("▶ ou OK : suivant   ·   Retour : fermer") : T.t("◀ : précédent   ·   OK ou Retour : fermer");
        txt(c, nav, w - 110, 1030, 26, DIM, regular, Paint.Align.RIGHT);
    }

    private void pageNavigate(Canvas c, float w) {
        // television
        rect(c, 120, 300, 820, 720, INK, 5, 3);
        rect(c, 150, 330, 790, 690, DIM, 3, 2);
        rect(c, 200, 400, 420, 620, CORAL, 4, 2);
        circle(c, 310, 510, 60, ACC, 3);
        line(c, 480, 440, 710, 440, INK, 5, 1.5f);
        line(c, 480, 500, 650, 500, INK, 5, 1.5f);
        line(c, 480, 555, 680, 555, INK, 5, 1.5f);
        line(c, 420, 760, 520, 760, INK, 5, 2);
        line(c, 470, 720, 470, 760, INK, 5, 2);
        // remote
        float cx = 1030, cy = 560;
        rect(c, cx - 140, cy - 210, cx + 140, cy + 230, INK, 5, 3);
        circle(c, cx, cy - 40, 64, ACC, 4);
        txt(c, "OK", cx, cy - 40 + 11, 32, ACC, bold, Paint.Align.CENTER);
        tri(c, cx, cy - 130, 'u', INK, 20); tri(c, cx, cy + 50, 'd', INK, 20);
        tri(c, cx - 102, cy - 40, 'l', INK, 16); tri(c, cx + 102, cy - 40, 'r', INK, 16);
        rect(c, cx - 50, cy + 110, cx + 50, cy + 160, DIM, 3, 2);
        txt(c, T.t("Retour"), cx, cy + 142, 22, DIM, medium, Paint.Align.CENTER);
        // legend
        float x = 1260, mw = w - 110 - x, y = 270;
        y = para(c, x, y, T.t("OK ou une flèche"), T.t("Ouvre le menu."), mw);
        y = para(c, x, y, T.t("▲ ▼ (menu fermé)"), T.t("Change le style d'affichage."), mw);
        y = para(c, x, y, T.t("◀ ▶ maintenu"), T.t("Recule ou avance dans la chanson."), mw);
        para(c, x, y, T.t("Retour"), T.t("Ferme le menu ou revient en arrière."), mw);
        txt(c, T.t("Tu retrouveras ce guide à tout moment dans Réglages › Guide."), 110, 985, 28, DIM, regular, Paint.Align.LEFT);
    }

    /** Page 2/4: reduced menu rail, Lecture last, and a sketch of the bottom control bar. */
    private void pageMenuReading(Canvas c, float w) {
        c.save();
        c.translate(48 * s, 79 * s);
        c.scale(0.62f, 0.62f);
        float x0 = 130, y0 = 260;
        rect(c, x0 - 30, y0 - 40, x0 + 400, y0 + 8 * 88 - 20, DIM, 3, 2);
        for (int i = 0; i < MenuOverlay.SEC_NAMES.length; i++) {
            float y = y0 + i * 88;
            boolean on = i < 4;
            if (on) rect(c, x0 - 10, y - 8, x0 + 380, y + 62, CORAL, 5, 2);
            circle(c, x0 + 32, y + 27, 16, on ? CORAL : DIM, 3);
            txt(c, T.t(MenuOverlay.SEC_NAMES[i]), x0 + 75, y + 27 + 12, 34, on ? INK : DIM, on ? bold : regular, Paint.Align.LEFT);
        }
        c.restore();
        float x = 460, mw = w - 110 - x, y = 205;
        y = para(c, x, y, T.t("Ambiances"), T.t("Des présélections en un clic : la musique, les pièces, le volume, le fond d'écran et le style d'affichage sont mémorisés ensemble. Le mode Météo en est une : le fond d'écran s'adapte seul selon la météo et l'heure de chez toi."), mw);
        y = para(c, x, y, T.t("Favoris"), T.t("Tes favoris Sonos, à lancer depuis la télé."), mw);
        y = para(c, x, y, T.t("Jeux"), T.t("La section des party games : blind test, quiz et autres, à jouer avec tes invités."), mw);
        y = para(c, x, y, T.t("Lecture"), T.t("La barre du bas commande la musique, comme dessiné ci-dessous."), mw);

        // bottom bar sketch
        float by = y - 4;
        rrect(c, x, by, w - 110, by + 84, 14, DIM, 3);
        float cy = by + 42;
        circle(c, x + 50, cy, 18, INK, 4);
        txt(c, "i", x + 50, cy + 9, 26, INK, bold, Paint.Align.CENTER);
        tri(c, x + 130, cy, 'l', INK, 14); tri(c, x + 152, cy, 'l', INK, 14);
        circle(c, x + 230, cy, 26, ACC, 4);
        tri(c, x + 234, cy, 'r', ACC, 14);
        tri(c, x + 310, cy, 'r', INK, 14); tri(c, x + 332, cy, 'r', INK, 14);
        for (int i = 0; i < 5; i++) rect(c, x + 410 + i * 30, cy - 14, x + 432 + i * 30, cy + 14, i < 3 ? ACC : DIM, 4, 1.5f);
        txt(c, T.t("À SUIVRE"), x + 630, cy - 8, 22, DIM, medium, Paint.Align.LEFT);
        txt(c, T.t("Titre suivant — Artiste"), x + 630, cy + 24, 26, INK, regular, Paint.Align.LEFT);
        float ax = x + 1290;
        circle(c, ax, cy + 4, 20, INK, 4);
        line(c, ax, cy - 8, ax, cy + 4, INK, 4, 1); line(c, ax, cy + 4, ax + 9, cy + 9, INK, 4, 1);
        line(c, ax - 18, cy - 24, ax - 8, cy - 15, INK, 4, 1); line(c, ax + 18, cy - 24, ax + 8, cy - 15, INK, 4, 1);
        line(c, ax - 22, cy + 36, ax + 22, cy + 36, ACC, 4, 1);

        float[] tx = {x + 230, x + 480, x + 820, ax};
        for (float t : tx) line(c, t, by + 90, t, by + 118, CORAL, 4, 1);
        float ly = by + 150;
        txt(c, T.t("Pause, suivant"), tx[0], ly, 26, INK, regular, Paint.Align.CENTER);
        txt(c, T.t("Flèches ▲ ▼ : volume"), tx[1], ly, 26, INK, regular, Paint.Align.CENTER);
        txt(c, T.t("Un appui : muet"), tx[1], ly + 34, 26, INK, regular, Paint.Align.CENTER);
        txt(c, T.t("Appui long : volume de toutes les pièces"), tx[1], ly + 68, 26, INK, regular, Paint.Align.CENTER);
        txt(c, T.t("Titres à suivre"), tx[2], ly, 26, INK, regular, Paint.Align.CENTER);
        txt(c, T.t("Minuteur"), tx[3], ly, 26, INK, regular, Paint.Align.CENTER);
    }

    private void pageMenu(Canvas c, float w, int[] hl, String[] heads, String[] bodies, String footer, boolean closing) {
        float x0 = 130, y0 = 260;
        rect(c, x0 - 30, y0 - 40, x0 + 400, y0 + 8 * 88 - 20, DIM, 3, 2);
        for (int i = 0; i < MenuOverlay.SEC_NAMES.length; i++) {
            float y = y0 + i * 88;
            boolean on = false;
            for (int h : hl) if (h == i) on = true;
            if (on) rect(c, x0 - 10, y - 8, x0 + 380, y + 62, CORAL, 5, 2);
            circle(c, x0 + 32, y + 27, 16, on ? CORAL : DIM, 3);
            txt(c, T.t(MenuOverlay.SEC_NAMES[i]), x0 + 75, y + 27 + 12, 34, on ? INK : DIM, on ? bold : regular, Paint.Align.LEFT);
        }
        float x = 720, mw = w - 110 - x, y = 240;
        for (int i = 0; i < heads.length; i++) y = para(c, x, y, T.t(heads[i]), bodies[i], mw);
        if (footer.length() > 0) txt(c, footer, 110, 985, 28, DIM, regular, Paint.Align.LEFT);
    }

    /** Rounded sketchy rectangle. */
    private void rrect(Canvas c, float x0, float y0, float x1, float y1, float r, int col, float w) {
        for (int pass = 0; pass < 2; pass++) {
            path.reset();
            boolean first = true;
            float[][] arcs = {{x1 - r, y0 + r, -90, 0}, {x1 - r, y1 - r, 0, 90}, {x0 + r, y1 - r, 90, 180}, {x0 + r, y0 + r, 180, 270}};
            for (float[] a : arcs) {
                for (int i = 0; i <= 8; i++) {
                    double ang = Math.toRadians(a[2] + (a[3] - a[2]) * i / 8.0);
                    float x = (float) (a[0] + r * Math.cos(ang)) + (rnd.nextFloat() * 2 - 1) * 2;
                    float y = (float) (a[1] + r * Math.sin(ang)) + (rnd.nextFloat() * 2 - 1) * 2;
                    if (first) { path.moveTo(x * s, y * s); first = false; } else path.lineTo(x * s, y * s);
                }
            }
            path.close();
            stroke.setColor(col); stroke.setStrokeWidth(w * s);
            c.drawPath(path, stroke);
        }
    }

    private void waves(Canvas c, float cx, float cy, int col, int n) {
        for (int i = 0; i < n; i++) {
            float r = 26 + i * 26;
            path.reset();
            for (int a = -40; a <= 40; a += 8) {
                double ang = Math.toRadians(a);
                float x = (float) (cx + r * Math.cos(ang)) + (rnd.nextFloat() - 0.5f) * 3;
                float y = (float) (cy + r * Math.sin(ang)) + (rnd.nextFloat() - 0.5f) * 3;
                if (a == -40) path.moveTo(x * s, y * s); else path.lineTo(x * s, y * s);
            }
            stroke.setColor(col); stroke.setStrokeWidth(4 * s);
            c.drawPath(path, stroke);
        }
    }

    private void note(Canvas c, float x, float y, int col, float k) {
        float r = 11 * k;
        circle(c, x, y, r * 0.8f, col, 6 * k);
        line(c, x + r - 2, y, x + r - 2, y - 46 * k, col, 5, 1);
        line(c, x + r - 2, y - 46 * k, x + r + 20 * k, y - 28 * k, col, 5, 1);
    }

    private void satellite(Canvas c, float cx, float y0) {
        rrect(c, cx - 34, y0, cx + 34, y0 + 170, 16, INK, 5);
        circle(c, cx, y0 + 46, 14, DIM, 3); circle(c, cx, y0 + 112, 22, DIM, 3);
        line(c, cx - 24, y0 + 170, cx + 24, y0 + 170, INK, 5, 1.5f); line(c, cx, y0 + 170, cx, y0 + 195, INK, 5, 1.5f);
    }

    private void pageStart(Canvas c, float w) {
        // smartphone -> television with a record, around it a sound system
        float px = 130, py = 350;
        rrect(c, px, py, px + 190, py + 400, 34, INK, 5);
        rrect(c, px + 12, py + 14, px + 178, py + 386, 24, DIM, 3);
        rrect(c, px + 61, py + 22, px + 129, py + 38, 8, INK, 3);
        line(c, px + 65, py + 374, px + 125, py + 374, INK, 5, 1);
        rect(c, px + 36, py + 78, px + 154, py + 196, CORAL, 4, 2);
        circle(c, px + 95, py + 137, 26, ACC, 3);
        line(c, px + 36, py + 226, px + 130, py + 226, INK, 4, 1); line(c, px + 36, py + 252, px + 90, py + 252, DIM, 4, 1);
        circle(c, px + 95, py + 312, 26, ACC, 4);
        line(c, px + 88, py + 302, px + 88, py + 322, ACC, 4, 1); line(c, px + 88, py + 302, px + 106, py + 312, ACC, 4, 1); line(c, px + 106, py + 312, px + 88, py + 322, ACC, 4, 1);
        waves(c, 345, 550, CORAL, 3);
        // television
        rect(c, 560, 330, 1060, 590, INK, 5, 3);
        rect(c, 585, 352, 1035, 568, DIM, 3, 2);
        circle(c, 810, 460, 58, CORAL, 4); circle(c, 810, 460, 34, ACC, 3); circle(c, 810, 460, 8, INK, 3);
        line(c, 700, 590, 700, 615, INK, 5, 2); line(c, 920, 590, 920, 615, INK, 5, 2);
        // sound system: soundbar, satellites, subwoofer
        rrect(c, 580, 640, 1040, 698, 18, INK, 5);
        for (int i = 0; i < 5; i++) circle(c, 640 + i * 85, 669, 10, DIM, 3);
        satellite(c, 505, 420); satellite(c, 1115, 420);
        rrect(c, 1070, 650, 1180, 760, 14, INK, 5);
        circle(c, 1125, 705, 35, DIM, 3); circle(c, 1125, 705, 13, DIM, 3);
        note(c, 1000, 430, ACC, 1.2f);
        note(c, 650, 500, ACC, 1.0f);
        float x = 1290, mw = w - 110 - x, y = 330;
        y = para(c, x, y, T.t("Pour commencer"), T.t("Lance une musique depuis ton smartphone (appli Sonos) ou depuis le menu Favoris de Pochette."), mw);
        txt(c, T.t("Bonne ambiance !"), x, y + 50, 50, CORAL, bold, Paint.Align.LEFT);
        txt(c, T.t("Retrouve ce guide à tout moment dans Réglages › Guide."), 110, 985, 28, DIM, regular, Paint.Align.LEFT);
    }
}
