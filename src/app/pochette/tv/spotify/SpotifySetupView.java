package app.pochette.tv.spotify;

import app.pochette.tv.core.QrCode;
import app.pochette.tv.core.T;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen page that walks through connecting Spotify: step 0 (create a free developer app), then the phone
 * page with a QR code. Flat shapes and text only, like the games.
 */
public final class SpotifySetupView extends View {
    public interface Host { void closed(boolean connected); }

    private static final int BG = 0xFF16102E, PANEL = 0xFF2A2150, LABEL = 0xFFB8A8F0, ACCENT = 0xFFFF7A5C, GOLD = 0xFFFFE9A8;
    private static final String DASHBOARD = "https://developer.spotify.com/dashboard";

    private final Host host;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface mono = Typeface.MONOSPACE;

    private SetupServer server;
    private SpotifyClient spotify;
    private int page;                       // 0 = step 0 (preparation), 1 = connect from the phone
    private boolean serverFailed;
    private String qrText = "";
    private boolean[][] qr;

    public SpotifySetupView(Context c, Host host) {
        super(c);
        this.host = host;
        setFocusable(false);
        setSoundEffectsEnabled(false);
        setVisibility(GONE);
    }

    public boolean active() { return getVisibility() == VISIBLE; }

    /** Opens the page: step 0 first when there is no Client ID yet, otherwise straight to the connection. */
    public void open(SetupServer server, SpotifyClient spotify, boolean intro) {
        this.server = server;
        this.spotify = spotify;
        page = intro ? 0 : 1;
        serverFailed = false;
        if (page == 1) startServer();
        setVisibility(VISIBLE);
        invalidate();
        removeCallbacks(tick);
        postDelayed(tick, 500);
    }

    private void startServer() { serverFailed = !server.start(); }

    public void close(boolean connected) {
        removeCallbacks(tick);
        if (server != null) server.stop();
        setVisibility(GONE);
        host.closed(connected);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!active()) return;
            if (server != null && server.signedIn() && spotify.connected()) { close(true); return; }
            invalidate();
            postDelayed(this, 500);
        }
    };

    public boolean handleKey(int code) {
        if (code == KeyEvent.KEYCODE_BACK) { close(false); return true; }
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (page == 0 && ok) { page = 1; startServer(); invalidate(); }
        else if (page == 1 && code == KeyEvent.KEYCODE_DPAD_LEFT) { page = 0; invalidate(); }
        return true;
    }

    // ------------------------------------------------------------------ drawing helpers

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
        String line = "";
        for (String w : s.split(" ")) {
            String t = line.length() == 0 ? w : line + " " + w;
            if (line.length() > 0 && measure(t, size, tf) > maxW) { out.add(line); line = w; }
            else line = t;
        }
        if (line.length() > 0) out.add(line);
        return out;
    }

    /** Draws a wrapped paragraph and returns the y below it. */
    private float paragraph(Canvas c, String s, float x, float y, float size, int color, Typeface tf, float maxW) {
        float lh = size * 1.3f;
        for (String l : wrap(s, size, tf, maxW)) {
            text(c, l, x, y, size, color, tf, Paint.Align.LEFT);
            y += lh;
        }
        return y;
    }

    private float lines(String s, float size, Typeface tf, float maxW) { return wrap(s, size, tf, maxW).size() * size * 1.3f; }

    private void monoBox(Canvas c, String s, float x, float y, float size) {
        float w = measure(s, size, mono);
        rect(c, x, y, x + w + size * 0.9f, y + size * 1.55f, PANEL);
        text(c, s, x + size * 0.45f, y + size * 1.12f, size, GOLD, mono, Paint.Align.LEFT);
    }

    private void step(Canvas c, float x, float y, float s, String label, int state, String after, float maxW, float size) {
        // state 0 = later, 1 = current, 2 = done; (x, y) = left edge and baseline of the first text line
        float box = size * 1.25f;
        float top = y - size * 0.36f - box / 2f;
        int bg = state == 2 ? ACCENT : state == 1 ? 0xFFFFFFFF : PANEL;
        int fg = state == 2 || state == 1 ? BG : LABEL;
        rect(c, x, top, x + box, top + box, bg);
        if (state == 2) {
            tp.setColor(fg);
            tp.setStrokeWidth(Math.max(3f, box * 0.1f));
            c.drawLine(x + box * 0.22f, top + box * 0.52f, x + box * 0.42f, top + box * 0.74f, tp);
            c.drawLine(x + box * 0.42f, top + box * 0.74f, x + box * 0.80f, top + box * 0.28f, tp);
        } else {
            float ts = size * 0.8f;
            text(c, after, x + box / 2f, top + box / 2f + ts * 0.36f, ts, fg, medium, Paint.Align.CENTER);
        }
        paragraph(c, label, x + box + 18f * s, y, size, state == 2 ? LABEL : 0xFFFFFFFF, regular, maxW - box - 18f * s);
    }

    private void drawQr(Canvas c, float cx, float top, float size, String content) {
        if (qr == null || !content.equals(qrText)) { qr = QrCode.encode(content); qrText = content; }
        if (qr == null) return;
        int n = qr.length;
        int mp = Math.max(1, (int) (size / (n + 4)));
        float side = mp * (n + 4);
        float l = cx - side / 2f;
        rect(c, l, top, l + side, top + side, 0xFFFFFFFF);
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++)
                if (qr[y][x]) rect(c, l + (x + 2) * mp, top + (y + 2) * mp, l + (x + 3) * mp, top + (y + 3) * mp, BG);
    }

    // ------------------------------------------------------------------ drawing

    @Override protected void onDraw(Canvas c) {
        float W = getWidth(), H = getHeight(), s = H / 1080f;
        c.drawColor(BG);
        if (spotify == null) return;
        float x0 = W * 0.055f, colW = W * (page == 0 ? 0.60f : 0.54f);
        float size = 30f * s;

        text(c, (page == 0 ? T.t("Spotify · étape 0 sur 4 · préparation") : T.t("Spotify · configuration")).toUpperCase(T.locale()),
                x0, H * 0.105f, 22f * s, LABEL, medium, Paint.Align.LEFT);
        float y = paragraph(c, page == 0 ? T.t("Il te faut un compte développeur Spotify (gratuit)") : T.t("Connecte Spotify depuis ton téléphone"),
                x0, H * 0.18f, 50f * s, 0xFFFFFFFF, medium, colW) + 26f * s;

        if (page == 0) drawIntro(c, W, H, s, x0, y, colW, size);
        else drawConnect(c, W, H, s, x0, y, colW, size);
    }

    private void drawIntro(Canvas c, float W, float H, float s, float x0, float y, float colW, float size) {
        String[] items = {
                T.t("Ton compte Spotify doit être Premium. Le compte développeur est gratuit et se crée avec le même identifiant."),
                T.t("Sur ton téléphone, ouvre le tableau de bord (QR code à droite) et connecte-toi."),
                T.t("Crée une app : nom libre, par exemple « Pochette », et coche « Web API »."),
                T.t("Ajoute cette adresse de redirection, à l'identique, puis enregistre :"),
                T.t("Note le Client ID de l'app : tu le colleras à l'étape suivante.")};
        String[] marks = {"a", "b", "c", "d", "e"};
        float box = size * 1.25f;
        float tw = colW - box - 18f * s;
        for (int i = 0; i < items.length; i++) {
            step(c, x0, y, s, items[i], 0, marks[i], colW, size);
            float h = lines(items[i], size, regular, tw);
            y += h;
            if (i == 3) {
                monoBox(c, SpotifyClient.REDIRECT, x0 + box + 18f * s, y - size * 0.6f, size * 0.95f);
                y += size * 1.75f;
            }
            y += 14f * s;
        }
        // button + hint
        float by = H * 0.90f;
        String b = T.t("OK : j'ai tout");
        float bw = measure(b, 28f * s, medium) + 56f * s;
        rect(c, x0, by - 34f * s, x0 + bw, by + 18f * s, ACCENT);
        text(c, b, x0 + bw / 2f, by + 3f * s, 28f * s, BG, medium, Paint.Align.CENTER);
        text(c, T.t("Retour : fermer"), x0 + bw + 36f * s, by + 3f * s, 24f * s, LABEL, regular, Paint.Align.LEFT);

        float cx = W * 0.79f, q = H * 0.44f, top = H * 0.24f;
        drawQr(c, cx, top, q, DASHBOARD);
        text(c, "developer.spotify.com/dashboard", cx, top + q + 62f * s, 26f * s, 0xFFFFFFFF, mono, Paint.Align.CENTER);
        text(c, T.t("Cette étape ne se fait qu'une fois."), cx, top + q + 112f * s, 24f * s, LABEL, regular, Paint.Align.CENTER);
    }

    private void drawConnect(Canvas c, float W, float H, float s, float x0, float y, float colW, float size) {
        boolean visited = server != null && server.visited();
        boolean idDone = spotify.configured();
        boolean signIn = server != null && server.signInStarted();
        boolean done = server != null && server.signedIn();
        boolean[] ok = {visited || idDone, idDone, signIn || done, done};
        String[] todo = {T.t("Scanne le QR code, ou tape l'adresse"), T.t("Colle ton Client ID"),
                T.t("Connecte-toi à Spotify et accepte"), T.t("Colle l'adresse de la page en erreur")};
        String[] did = {T.t("Page ouverte sur le téléphone"), T.t("Client ID reçu"),
                T.t("Connexion Spotify acceptée"), T.t("Adresse reçue")};
        int current = -1, count = 0;
        for (int i = 0; i < 4; i++) { if (ok[i]) count++; else if (current < 0) current = i; }
        float box = size * 1.25f;
        float tw = colW - box - 18f * s;
        for (int i = 0; i < 4; i++) {
            String label = ok[i] ? did[i] : todo[i];
            step(c, x0, y, s, label, ok[i] ? 2 : (i == current ? 1 : 0), String.valueOf(i + 1), colW, size);
            y += lines(label, size, regular, tw) + 20f * s;
        }
        // progress squares + status
        float fy = H * 0.90f, sq = 28f * s;
        for (int i = 0; i < 4; i++) rect(c, x0 + i * (sq + 10f * s), fy - sq, x0 + i * (sq + 10f * s) + sq, fy, i < count ? ACCENT : PANEL);
        String[] st = {T.t("En attente du téléphone…"), T.t("En attente du Client ID…"),
                T.t("En attente de la connexion…"), T.t("En attente de l'adresse collée…")};
        String status = current < 0 ? T.t("Spotify est connecté.") : st[current];
        text(c, status + "  ·  " + T.t("Retour pour fermer"), x0 + 4 * (sq + 10f * s) + 14f * s, fy - 4f * s, 24f * s, LABEL, regular, Paint.Align.LEFT);

        float cx = W * 0.77f, q = H * 0.46f, top = H * 0.22f;
        if (serverFailed || server == null || !server.isRunning()) {
            text(c, T.t("Impossible d'ouvrir la page de configuration"), cx, H * 0.45f, 28f * s, 0xFFFFFFFF, medium, Paint.Align.CENTER);
            return;
        }
        String url = server.url();
        drawQr(c, cx, top, q, url);
        text(c, url, cx, top + q + 70f * s, 36f * s, GOLD, mono, Paint.Align.CENTER);
    }
}
