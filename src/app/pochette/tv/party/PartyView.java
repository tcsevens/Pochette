package app.pochette.tv.party;

import app.pochette.tv.core.QrCode;
import app.pochette.tv.core.T;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The three party games where the TV is the game master: quiz, « Devine-moi ! » (describe without the forbidden
 * words) and drawing. Secret information goes to the phone of the player who draws or describes (PartyServer).
 */
public final class PartyView extends View {
    public static final int QUIZ = 0, TABOO = 1, DRAW = 2;
    public static final String[] NAMES = {"Quiz", "Devine-moi !", "Dessine-moi !"};
    private static final int P_READY = 0, P_PLAY = 1, P_REVEAL = 2, P_AWARD = 3, P_RESULT = 4, P_END = 5;
    private static final int[] ANS = {0xFFE0524F, 0xFF3F8DE0, 0xFF3FB87A, 0xFFE8B93A};
    private static final int[][] BG = {{0xFF2B1A5E, 0xFF0E0A24}, {0xFF7A1F3A, 0xFF1E0A14}, {0xFF0F5A5A, 0xFF08181E}};

    public interface Host { void partyClosed(); }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface bold = Typeface.create("sans-serif-black", Typeface.NORMAL);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Path path = new Path();

    private Host host;
    private PartyServer server;
    private int game, teams, turns, duration;
    private int[] scores;
    private int turn, total, phase, sel;
    private long phaseAt;
    private List<Integer> order = new ArrayList<Integer>();
    private int wi;                         // index in order for the current word / question
    private int found;                      // taboo: words found this turn
    private String lastFound = "";
    private long lastFoundAt;
    private String[] card;                  // current quiz question / taboo card
    private String word = "";               // draw: current word
    private boolean serverOk = true;

    public PartyView(Context c) {
        super(c);
        setFocusable(false);
        setSoundEffectsEnabled(false);
        setVisibility(GONE);
        line.setStyle(Paint.Style.STROKE);
    }

    public void setHost(Host h) { host = h; }
    public boolean active() { return getVisibility() == VISIBLE; }

    // ------------------------------------------------------------------ flow

    private String[] pool;

    public void start(int game, int teams, int turns, int duration, int cat) {
        this.game = game; this.teams = Math.max(2, teams); this.turns = Math.max(1, turns); this.duration = duration;
        scores = new int[this.teams];
        turn = 0; wi = 0; sel = 0;
        total = game == QUIZ ? this.turns : this.teams * this.turns;
        pool = game == QUIZ ? PartyData.quizPool(cat) : null;
        int n = game == QUIZ ? pool.length : game == TABOO ? PartyData.tabooCount() : PartyData.drawCount();
        order.clear();
        for (int i = 0; i < n; i++) order.add(i);
        Collections.shuffle(order);
        if (server != null) server.stop();
        server = null;
        serverOk = true;
        if (game != QUIZ) { server = new PartyServer(); serverOk = server.start(); }
        setVisibility(VISIBLE);
        beginTurn();
        postInvalidateOnAnimation();
    }

    public void close() {
        if (server != null) { server.stop(); server = null; }
        setVisibility(GONE);
        if (host != null) host.partyClosed();
    }

    private void setPhase(int p) { phase = p; phaseAt = SystemClock.uptimeMillis(); invalidate(); }
    private long inPhase() { return SystemClock.uptimeMillis() - phaseAt; }
    private int teamNow() { return turn % teams; }

    private void beginTurn() {
        if (turn >= total) { if (server != null) server.idle(); setPhase(P_END); return; }
        found = 0; lastFound = ""; sel = game == QUIZ ? 0 : (teamNow() + 1) % teams;
        if (game == QUIZ) { card = pool[order.get(wi % order.size())].split("\\|"); wi++; setPhase(P_PLAY); }
        else { if (server != null) server.idle(); setPhase(P_READY); }
    }

    private void nextWord() {
        wi++;
        int k = order.get(wi % order.size());
        if (game == TABOO) {
            card = PartyData.taboo(k);
            String[] f = new String[card.length - 1];
            System.arraycopy(card, 1, f, 0, f.length);
            server.show("taboo", card[0], f, PartyColors.team(teamNow()));
        }
    }

    private void startPlay() {
        if (game == TABOO) {
            wi++;
            int k = order.get(wi % order.size());
            card = PartyData.taboo(k);
            String[] f = new String[card.length - 1];
            System.arraycopy(card, 1, f, 0, f.length);
            server.show("taboo", card[0], f, PartyColors.team(teamNow()));
        } else if (game == DRAW) {
            word = PartyData.drawWord(order.get(wi % order.size()));
            wi++;
            server.show("draw", word, null, PartyColors.team(teamNow()));
        }
        setPhase(P_PLAY);
    }

    private void endTurn() { turn++; beginTurn(); }

    private int remaining() {
        long left = (game == QUIZ ? 20000L : duration * 1000L) - inPhase();
        return (int) Math.max(0, (left + 999) / 1000);
    }

    private void tick() {
        if (phase == P_PLAY) {
            if (server != null) server.setRemaining(remaining());
            if (remaining() <= 0) {
                if (game == QUIZ) setPhase(P_REVEAL);
                else if (game == TABOO) { server.idle(); setPhase(P_RESULT); }
                else { setPhase(P_REVEAL); }
            }
            if (server != null) {
                String e;
                while ((e = server.pollEvent()) != null) {
                    if (game == TABOO && phase == P_PLAY) {
                        if ("found".equals(e)) { scores[teamNow()]++; found++; lastFound = card[0]; lastFoundAt = SystemClock.uptimeMillis(); nextWord(); }
                        else if ("pass".equals(e)) nextWord();
                    } else if (game == DRAW && phase == P_PLAY && "found".equals(e)) { server.idle(); setPhase(P_AWARD); }
                }
            }
        }
        if (phase == P_RESULT && inPhase() > 6000) endTurn();
    }

    // ------------------------------------------------------------------ keys

    public void handleKey(int code) {
        if (code == KeyEvent.KEYCODE_BACK) { close(); return; }
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean left = code == KeyEvent.KEYCODE_DPAD_LEFT, right = code == KeyEvent.KEYCODE_DPAD_RIGHT, down = code == KeyEvent.KEYCODE_DPAD_DOWN;
        if (phase == P_END) { if (ok) close(); return; }
        if (game == QUIZ) {
            if (phase == P_PLAY && ok) setPhase(P_REVEAL);
            else if (phase == P_PLAY && down) { card = pool[order.get(wi % order.size())].split("\\|"); wi++; setPhase(P_PLAY); }   // skip this question
            else if (phase == P_REVEAL) {
                if (left) sel = (sel + teams - 1) % teams;
                else if (right) sel = (sel + 1) % teams;
                else if (ok) { scores[sel]++; endTurn(); }
                else if (down) endTurn();
            }
        } else if (game == TABOO) {
            if (phase == P_READY && ok) startPlay();
            else if (phase == P_RESULT && ok) endTurn();
        } else {
            if (phase == P_READY && ok) startPlay();
            else if (phase == P_PLAY && ok) { if (server != null) server.idle(); setPhase(P_AWARD); }
            else if (phase == P_REVEAL && ok) endTurn();
            else if (phase == P_AWARD) {
                if (left) { sel = (sel + teams - 1) % teams; if (sel == teamNow()) sel = (sel + teams - 1) % teams; }
                else if (right) { sel = (sel + 1) % teams; if (sel == teamNow()) sel = (sel + 1) % teams; }
                else if (ok) { scores[sel]++; scores[teamNow()]++; endTurn(); }
                else if (down) endTurn();
            }
        }
        invalidate();
    }

    // ------------------------------------------------------------------ drawing

    private float s;
    private int W, H;

    private void txt(String t, float x, float y, float size, int color, Typeface tf, Paint.Align a) {
        tp.setTypeface(tf); tp.setTextSize(size * s); tp.setColor(color); tp.setTextAlign(a);
        c_.drawText(t, x, y, tp);
    }

    private float wrap(String t, float x, float y, float w, float size, int color, Typeface tf, Layout.Alignment al) {
        tp.setTypeface(tf); tp.setTextSize(size * s); tp.setColor(color); tp.setTextAlign(Paint.Align.LEFT);
        StaticLayout l = StaticLayout.Builder.obtain(t, 0, t.length(), tp, Math.max(1, (int) w)).setAlignment(al).setIncludePad(false).setLineSpacing(0f, 1.05f).build();
        c_.save(); c_.translate(x, y); l.draw(c_); c_.restore();
        return l.getHeight();
    }

    private Canvas c_;

    @Override protected void onDraw(Canvas c) {
        c_ = c;
        W = getWidth(); H = getHeight(); s = H / 1080f;
        tick();
        long now = SystemClock.uptimeMillis();
        float t = now / 1000f;
        fill.setShader(new LinearGradient(0, 0, 0, H, BG[game][0], BG[game][1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, fill);
        fill.setShader(null);
        for (int i = 0; i < 9; i++) {                                    // slow diagonal bands
            float x = ((i * W / 8f) + t * 22f * s) % (W + 400 * s) - 200 * s;
            path.reset(); path.moveTo(x, 0); path.lineTo(x + 140 * s, 0); path.lineTo(x - 200 * s, H); path.lineTo(x - 340 * s, H); path.close();
            fill.setColor(0x0CFFFFFF);
            c.drawPath(path, fill);
        }
        // header
        fill.setColor(0xFFFFFFFF); c.drawRect(60 * s, 54 * s, 70 * s, 98 * s, fill);
        txt(T.t(NAMES[game]).toUpperCase(T.locale()), 90 * s, 90 * s, 40, 0xFFFFFFFF, bold, Paint.Align.LEFT);
        if (phase != P_END) txt(game == QUIZ ? T.t("Question ") + (turn + 1) + " / " + total : T.t("Tour ") + (turn + 1) + " / " + total, 90 * s, 124 * s, 24, 0xB0FFFFFF, regular, Paint.Align.LEFT);
        drawScores(c);
        if (phase == P_END) drawPodium(c);
        else if (game == QUIZ) drawQuiz(c);
        else if (game == TABOO) drawTaboo(c);
        else drawDraw(c);
        if (phase == P_PLAY || phase == P_RESULT || server != null || phase == P_END) postInvalidateOnAnimation();
    }

    private void drawScores(Canvas c) {
        float sz = 78 * s, gap = 14 * s, x = W - 60 * s - teams * (sz + gap) + gap;
        for (int i = 0; i < teams; i++) {
            boolean cur = game != QUIZ && phase != P_END && i == teamNow();
            fill.setColor(PartyColors.team(i)); c.drawRect(x, 50 * s, x + sz, 50 * s + sz, fill);
            if (cur) { line.setColor(0xFFFFFFFF); line.setStrokeWidth(6 * s); c.drawRect(x - 4 * s, 46 * s, x + sz + 4 * s, 54 * s + sz, line); }
            txt(String.valueOf(scores[i]), x + sz / 2f, 50 * s + sz * 0.70f, 44, 0xFFFFFFFF, bold, Paint.Align.CENTER);
            x += sz + gap;
        }
    }

    private String teamName(int i) { return T.t("Équipe ") + T.t(Game.TEAM_NAMES[i % Game.TEAM_NAMES.length]).toLowerCase(T.locale()); }

    private void drawTeamPicker(Canvas c, float y, boolean skipDrawer) {
        float sz = 120 * s, gap = 24 * s, x = (W - teams * (sz + gap) + gap) / 2f;
        for (int i = 0; i < teams; i++) {
            boolean dim = skipDrawer && i == teamNow();
            fill.setColor(dim ? 0x33FFFFFF : PartyColors.team(i)); c.drawRect(x, y, x + sz, y + sz, fill);
            if (i == sel && !dim) { line.setColor(0xFFFFFFFF); line.setStrokeWidth(8 * s); c.drawRect(x - 6 * s, y - 6 * s, x + sz + 6 * s, y + sz + 6 * s, line); }
            x += sz + gap;
        }
    }

    private void drawTimer(Canvas c, float y, float frac, int col) {
        fill.setColor(0x33FFFFFF); c.drawRect(60 * s, y, W - 60 * s, y + 18 * s, fill);
        fill.setColor(col); c.drawRect(60 * s, y, 60 * s + (W - 120 * s) * Math.max(0f, Math.min(1f, frac)), y + 18 * s, fill);
    }

    // ---- quiz
    private void drawQuiz(Canvas c) {
        wrap(card[0], 90 * s, 200 * s, W - 180 * s, 60, 0xFFFFFFFF, bold, Layout.Alignment.ALIGN_NORMAL);
        float bw = (W - 180 * s - 30 * s) / 2f, bh = 170 * s, y0 = 470 * s;
        int right = Integer.parseInt(card[5]);
        for (int i = 0; i < 4; i++) {
            float x = 90 * s + (i % 2) * (bw + 30 * s), y = y0 + (i / 2) * (bh + 30 * s);
            boolean dim = phase == P_REVEAL && i != right;
            fill.setColor(dim ? 0x22FFFFFF : 0x30FFFFFF); c.drawRect(x, y, x + bw, y + bh, fill);
            fill.setColor(dim ? (ANS[i] & 0x00FFFFFF) | 0x55000000 : ANS[i]); c.drawRect(x, y, x + 120 * s, y + bh, fill);
            txt(String.valueOf((char) ('A' + i)), x + 60 * s, y + bh * 0.66f, 70, 0xFFFFFFFF, bold, Paint.Align.CENTER);
            tp.setTypeface(medium); tp.setTextSize(46 * s); tp.setColor(dim ? 0x66FFFFFF : 0xFFFFFFFF); tp.setTextAlign(Paint.Align.LEFT);
            String a = android.text.TextUtils.ellipsize(card[1 + i], tp, bw - 150 * s, android.text.TextUtils.TruncateAt.END).toString();
            c.drawText(a, x + 144 * s, y + bh * 0.62f, tp);
            if (phase == P_REVEAL && i == right) { line.setColor(0xFFFFFFFF); line.setStrokeWidth(8 * s); c.drawRect(x, y, x + bw, y + bh, line); }
        }
        if (phase == P_PLAY) {
            drawTimer(c, 950 * s, 1f - inPhase() / 20000f, 0xFFF2C66B);
            txt(remaining() + " s", W - 60 * s, 930 * s, 34, 0xFFFFFFFF, bold, Paint.Align.RIGHT);
            txt(T.t("OK : montrer la réponse   ·   ▼ : passer la question"), 60 * s, 1040 * s, 24, 0x90FFFFFF, regular, Paint.Align.LEFT);
        } else {
            txt(T.t("Quelle équipe a trouvé ?"), W / 2f, 905 * s, 30, 0xFFFFFFFF, medium, Paint.Align.CENTER);
            drawTeamPicker(c, 925 * s, false);
            txt(T.t("◀ ▶ équipe  ·  OK donne le point  ·  ▼ personne"), 60 * s, 1070 * s, 22, 0x90FFFFFF, regular, Paint.Align.LEFT);
        }
    }

    // ---- qr / phone link
    private void drawQr(Canvas c, float x, float y, float size) {
        boolean[][] m = QrCode.encode(server.url());
        int n = m.length;
        float q = size / (n + 4);
        fill.setColor(0xFFFFFFFF); c.drawRect(x, y, x + size, y + size, fill);
        fill.setColor(0xFF000000);
        for (int r = 0; r < n; r++) for (int k = 0; k < n; k++) if (m[r][k]) c.drawRect(x + (k + 2) * q, y + (r + 2) * q, x + (k + 3) * q, y + (r + 3) * q, fill);
    }

    private void drawReady(Canvas c, String what) {
        int tc = teamNow();
        fill.setColor(PartyColors.team(tc)); c.drawRect(90 * s, 220 * s, 100 * s, 560 * s, fill);
        txt(teamName(tc).toUpperCase(T.locale()), 130 * s, 290 * s, 72, 0xFFFFFFFF, bold, Paint.Align.LEFT);
        wrap(what, 130 * s, 330 * s, 900 * s, 38, 0xE0FFFFFF, regular, Layout.Alignment.ALIGN_NORMAL);
        if (!serverOk) { txt(T.t("Impossible de démarrer la page pour téléphone"), 130 * s, 640 * s, 32, 0xFFFFB199, medium, Paint.Align.LEFT); return; }
        boolean con = server.connected();
        txt(con ? T.t("Téléphone connecté") : T.t("Scanne ce QR code avec ton téléphone"), 130 * s, 700 * s, 32, con ? 0xFF7CFF8A : 0xFFFFFFFF, medium, Paint.Align.LEFT);
        txt(T.t("(même Wi-Fi que la télé)"), 130 * s, 740 * s, 24, 0x90FFFFFF, regular, Paint.Align.LEFT);
        txt(T.t("OK : démarrer le tour"), 130 * s, 860 * s, 40, 0xFFF2C66B, bold, Paint.Align.LEFT);
        drawQr(c, W - 90 * s - 560 * s, 260 * s, 560 * s);
        txt(server.url().replace("http://", ""), W - 90 * s - 280 * s, 860 * s, 22, 0x90FFFFFF, regular, Paint.Align.CENTER);
    }

    // ---- taboo
    private void drawTaboo(Canvas c) {
        int tc = teamNow();
        if (phase == P_READY) {
            drawReady(c, T.t("Celui qui décrit regarde son téléphone : il y voit le mot et les mots interdits. Les autres devinent !"));
            return;
        }
        if (phase == P_PLAY) {
            fill.setColor(PartyColors.team(tc)); c.drawRect(0, 150 * s, W, 170 * s, fill);
            txt(String.valueOf(remaining()), W / 2f, 600 * s, 380, 0xFFFFFFFF, bold, Paint.Align.CENTER);
            txt(teamName(tc).toUpperCase(T.locale()), W / 2f, 240 * s, 46, 0xFFFFFFFF, bold, Paint.Align.CENTER);
            txt(T.t("Trouvés : ") + found, W / 2f, 760 * s, 56, 0xFFFFFFFF, medium, Paint.Align.CENTER);
            if (lastFound.length() > 0 && SystemClock.uptimeMillis() - lastFoundAt < 2500) txt("✔ " + lastFound, W / 2f, 850 * s, 48, 0xFF7CFF8A, bold, Paint.Align.CENTER);
            drawTimer(c, 950 * s, remaining() / (float) duration, PartyColors.team(tc));
            txt(T.t("Sur le téléphone : « Trouvé ! » ou « Passer »"), 60 * s, 1040 * s, 24, 0x90FFFFFF, regular, Paint.Align.LEFT);
        } else {
            txt(T.t("Temps écoulé !"), W / 2f, 420 * s, 110, 0xFFFFFFFF, bold, Paint.Align.CENTER);
            txt(teamName(tc) + " : " + found + " " + (found > 1 ? T.t("mots trouvés") : T.t("mot trouvé")), W / 2f, 560 * s, 56, PartyColors.team(tc), medium, Paint.Align.CENTER);
            txt(T.t("OK : équipe suivante"), W / 2f, 800 * s, 36, 0xFFF2C66B, bold, Paint.Align.CENTER);
        }
    }

    // ---- drawing
    private void drawDraw(Canvas c) {
        int tc = teamNow();
        if (phase == P_READY) {
            drawReady(c, T.t("Le dessinateur reçoit le mot sur son téléphone et dessine avec son doigt. Les autres devinent à voix haute !"));
            return;
        }
        if (phase == P_PLAY || phase == P_REVEAL || phase == P_AWARD) {
            float ch = 660 * s, cw = ch * 4f / 3f, cx0 = (W - cw) / 2f, cy0 = 170 * s;
            fill.setColor(0xFFFFFFFF); c.drawRect(cx0, cy0, cx0 + cw, cy0 + ch, fill);
            if (server != null) {
                c.save(); c.clipRect(cx0, cy0, cx0 + cw, cy0 + ch);
                line.setStrokeCap(Paint.Cap.ROUND); line.setStrokeJoin(Paint.Join.ROUND);
                for (PartyServer.Stroke st : server.strokes()) {
                    line.setColor(st.color); line.setStrokeWidth((st.color == 0xFFFFFFFF ? 28f : 7f) * cw / 800f);
                    path.reset();
                    for (int i = 0; i + 1 < st.pts.length; i += 2) {
                        float px = cx0 + st.pts[i] * cw, py = cy0 + st.pts[i + 1] * ch;
                        if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
                    }
                    if (st.pts.length == 2) path.lineTo(cx0 + st.pts[0] * cw + 0.1f, cy0 + st.pts[1] * ch);
                    c.drawPath(path, line);
                }
                c.restore();
                line.setStrokeCap(Paint.Cap.BUTT); line.setStrokeJoin(Paint.Join.MITER);
            }
            line.setColor(PartyColors.team(tc)); line.setStrokeWidth(8 * s); c.drawRect(cx0, cy0, cx0 + cw, cy0 + ch, line);
        }
        if (phase == P_PLAY) {
            drawTimer(c, 900 * s, remaining() / (float) duration, PartyColors.team(tc));
            txt(remaining() + " s", W - 60 * s, 880 * s, 34, 0xFFFFFFFF, bold, Paint.Align.RIGHT);
            txt(teamName(tc) + " " + T.t("dessine"), 60 * s, 880 * s, 34, 0xFFFFFFFF, bold, Paint.Align.LEFT);
            txt(T.t("OK : quelqu'un a trouvé"), 60 * s, 1040 * s, 24, 0x90FFFFFF, regular, Paint.Align.LEFT);
        } else if (phase == P_REVEAL) {
            txt(T.t("Temps écoulé ! C'était : ") + word, W / 2f, 930 * s, 54, 0xFFFFFFFF, bold, Paint.Align.CENTER);
            txt(T.t("OK : équipe suivante"), W / 2f, 1010 * s, 30, 0xFFF2C66B, bold, Paint.Align.CENTER);
        } else if (phase == P_AWARD) {
            txt(T.t("C'était : ") + word, W / 2f, 895 * s, 48, 0xFFFFFFFF, bold, Paint.Align.CENTER);
            txt(T.t("Quelle équipe a trouvé ?"), W / 2f, 945 * s, 28, 0xD0FFFFFF, medium, Paint.Align.CENTER);
            float sz = 70 * s, gap = 16 * s, x = (W - teams * (sz + gap) + gap) / 2f;
            for (int i = 0; i < teams; i++) {
                boolean dim = i == tc;
                fill.setColor(dim ? 0x33FFFFFF : PartyColors.team(i)); c.drawRect(x, 965 * s, x + sz, 965 * s + sz, fill);
                if (i == sel && !dim) { line.setColor(0xFFFFFFFF); line.setStrokeWidth(6 * s); c.drawRect(x - 4 * s, 961 * s, x + sz + 4 * s, 969 * s + sz, line); }
                x += sz + gap;
            }
        }
    }

    // ---- podium
    private void drawPodium(Canvas c) {
        Integer[] idx = new Integer[teams];
        for (int i = 0; i < teams; i++) idx[i] = i;
        final int[] sc = scores;
        java.util.Arrays.sort(idx, new java.util.Comparator<Integer>() { @Override public int compare(Integer a, Integer b) { return sc[b] - sc[a]; } });
        txt(T.t("PODIUM"), W / 2f, 250 * s, 96, 0xFFF2C66B, bold, Paint.Align.CENTER);
        int max = Math.max(1, sc[idx[0]]);
        float y = 320 * s, rowH = Math.min(100 * s, 560 * s / teams);
        for (int r = 0; r < teams; r++) {
            int i = idx[r];
            float w = (W - 500 * s) * (0.15f + 0.85f * sc[i] / (float) max);
            fill.setColor(PartyColors.team(i)); c.drawRect(250 * s, y, 250 * s + w, y + rowH - 14 * s, fill);
            txt(String.valueOf(r + 1), 190 * s, y + rowH * 0.66f, 48, 0xFFFFFFFF, bold, Paint.Align.CENTER);
            txt(teamName(i) + "  ·  " + sc[i], 280 * s, y + rowH * 0.66f, 36, 0xFFFFFFFF, bold, Paint.Align.LEFT);
            y += rowH;
        }
        txt(T.t("OK pour fermer"), W / 2f, 1010 * s, 28, 0xB0FFFFFF, regular, Paint.Align.CENTER);
    }
}
