package app.pochette.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Side navigation bar + transport controls, drawn by hand so that it never plays a system "click"
 * (a click makes some soundbars switch to the TV input and cut the music).
 */
public class MenuOverlay extends View {

    public static final int SEC_PLAY = 0, SEC_AMB = 1, SEC_GAMES = 2, SEC_ROOMS = 3, SEC_DISPLAY = 4, SEC_SETTINGS = 5, SEC_HELP = 6;
    public static final String[] SEC_NAMES = {"Lecture", "Ambiances", "Jeux", "Pièces", "Affichage", "Réglages", "À propos"};
    private static final int[] SEC_ICONS = {0, 5, 6, 1, 2, 3, 4};

    /** One ambiance card of the grid. theme = Ambiance.T_*, or -1 for the "new" card. */
    public static final class Card {
        public String name, sub;
        public int theme;
        public int c0, c1, icon = -1;     // game cards: own colours and pictogram
        public Card(String name, String sub, int theme) { this.name = name; this.sub = sub; this.theme = theme; }
        public Card(String name, String sub, int c0, int c1, int icon) { this.name = name; this.sub = sub; this.theme = -2; this.c0 = c0; this.c1 = c1; this.icon = icon; }
    }
    public static final int ROW_CHIPS = 0, ROW_TOGGLE = 1, ROW_ACTION = 2, ROW_CHECK = 3, ROW_TEXT = 4, ROW_BUTTON = 5;

    public static final class Row {
        public String id, label;
        public int kind;
        public String[] options;
        public int selected;
        public boolean on;
        public int dot;     // text rows: 0 none, 1 green, 2 orange, 3 grey (a small flat square before the text)

        static Row make(String id, int kind, String label) {
            Row r = new Row();
            r.id = id; r.kind = kind; r.label = label;
            return r;
        }
        public static Row chips(String id, String label, String[] opts, int sel) {
            Row r = make(id, ROW_CHIPS, label); r.options = T.arr(opts); r.selected = sel; return r;
        }
        public static Row toggle(String id, String label, boolean on) { Row r = make(id, ROW_TOGGLE, label); r.on = on; return r; }
        public static Row action(String id, String label) { return make(id, ROW_ACTION, label); }
        public static Row check(String id, String label, boolean on) { Row r = make(id, ROW_CHECK, label); r.on = on; return r; }
        public static Row button(String id, String label) { return make(id, ROW_BUTTON, label); }
        public static Row text(String label) { return make(null, ROW_TEXT, label); }
        public static Row head(String label) { Row r = make(null, ROW_TEXT, label); r.dot = -1; return r; }
        public static Row status(String label, int dot) { Row r = make(null, ROW_TEXT, label); r.dot = dot; return r; }
        boolean focusable() { return kind != ROW_TEXT; }
    }

    public interface Host {
        void onOpen(int section);
        List<Row> rows(int section);
        void onChoice(int section, Row row, int index);
        void onActivate(int section, Row row);
        void transport(int which); // 0 previous, 1 play/pause, 2 next
        boolean isPlaying();
        /** Current (or previewed) position and duration in ms; duration 0 = live stream. */
        long positionMs();
        long durationMs();
        void seekStep(int dir, int repeat);
        void seekCommit();
        /** True when a music screen is showing, so the player strip is shown at the bottom. */
        boolean stripOn();
        /** Spotify indicator shown in the menu bars (not clickable): 0 hidden, 1 connected (green), 2 problem (red). */
        int spotifyState();
        /** Rooms playing, shown small in the menu bars ("" = hidden). */
        String roomsLabel();
        /** Group volume 0..100 (-1 unknown), mute state, and a volume step (dir +1 / -1, repeat count of the key). */
        int volume();
        boolean muted();
        void volumeStep(int dir, int repeat);
        /** Room taken by the overlay on the left, right and bottom (px), so the music can move aside. */
        void insets(float left, float right, float bottom);
        /** Ambiances: the cards, launching one (OK) and editing one (long OK). */
        List<Card> cards();
        List<Card> gameCards();
        boolean gameGrid();
        void gamePick(int index);
        void cardLaunch(int index);
        void cardEdit(int index);
        /** True while the Ambiances section shows rows (editor, playlist picker) instead of the cards. */
        boolean ambRows();
        /** Back inside a section; true when the host went one page back itself. */
        boolean onBack(int section);
        /** Next tracks of the queue and jumping to one of them. */
        List<SonosClient.Item> upNext();
        void queueJump(int index);
    }

    private static final int[] T_ORDER = {4, 0, 1, 2, 3};
    private static final int Z_TRANSPORT = 0, Z_RAIL = 1, Z_PANEL = 2, Z_SEEK = 3, Z_GRID = 4, Z_QUEUE = 5;
    private static final long HIDE_TRANSPORT_MS = 6000, HIDE_MENU_MS = 30000;
    private static final int ACCENT = 0xFFE8664A;

    private final Host host;
    private boolean right;
    private boolean visible;
    private int zone = Z_TRANSPORT, tIdx = 1, railIdx = SEC_AMB, rowIdx;
    private int previewSection = -1;
    private int gridIdx, qIdx, gridRow0;
    private boolean okDown, okLong;
    /** Chip under the cursor in the focused row (-1 = on the current choice). Moving does not apply; OK does. */
    private int chipCursor = -1;
    private List<Row> rows = new ArrayList<Row>();
    private long lastActive;
    private float scroll;
    private float lastL = -1, lastR = -1, lastB = -1;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final RectF irf = new RectF();
    private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            // the left rail and its pages stay until BACK; only the bottom bar closes by itself
            long limit = zone == Z_TRANSPORT ? HIDE_TRANSPORT_MS : (zone == Z_QUEUE || zone == Z_SEEK) ? HIDE_MENU_MS : Long.MAX_VALUE;
            if (limit != Long.MAX_VALUE && SystemClock.uptimeMillis() - lastActive > limit) hide();
            else postDelayed(this, 500);
        }
    };

    public MenuOverlay(Context c, Host host) {
        super(c);
        this.host = host;
        setSoundEffectsEnabled(false);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setRight(boolean r) { right = r; invalidate(); }

    // ---- the player strip is part of the screen: always there, optionally fading out when idle
    private int stripSec = 10;
    private long stripUntil;
    private float stripA = 1f;
    public void setAutoHide(int sec) { stripSec = sec; wake(); }
    /** The bottom bar is showing on its auto-hide timer (not the full menu). */
    public boolean stripUp() {
        return !visible && stripSec > 0 && host.stripOn() && host.isPlaying() && stripUntil - SystemClock.uptimeMillis() > 0;
    }
    public void dismissStrip() { stripUntil = 0; invalidate(); }
    public void wake() { stripUntil = SystemClock.uptimeMillis() + Math.max(1, stripSec) * 1000L; invalidate(); }
    private int al(int col) { return (((int) ((col >>> 24) * stripA)) << 24) | (col & 0xFFFFFF); }
    private float stripAlpha(long now) {
        if (visible || stripSec <= 0) return 1f;
        if (!host.isPlaying()) {                 // paused: the bar stays, and the timer restarts when the music resumes
            stripUntil = now + Math.max(1, stripSec) * 1000L;
            return 1f;
        }
        return Math.max(0f, Math.min(1f, (stripUntil - now) / 700f));
    }
    private float stripHeight(float s) { return (host.durationMs() > 0 ? 106f : 72f) * s; }

    private int railAlpha = 0xD9, panelAlpha = 0xCC;

    /** 0 = very transparent, 1 = medium, 2 = almost opaque. */
    public void setOpacity(int level) {
        int[] rail = {0x8C, 0xD9, 0xF7}, panel = {0x80, 0xCC, 0xF5};
        int l = Math.max(0, Math.min(2, level));
        railAlpha = rail[l];
        panelAlpha = panel[l];
        invalidate();
    }

    public boolean isOpen() { return visible; }

    private boolean gridNow() { return (railIdx == SEC_AMB && !host.ambRows()) || (railIdx == SEC_GAMES && host.gameGrid()); }
    private List<Card> gridCards() { return railIdx == SEC_GAMES ? host.gameCards() : host.cards(); }

    public void show(boolean menu) {
        visible = true;
        if (menu) { zone = Z_RAIL; enterRailItem(); }
        else { zone = Z_TRANSPORT; tIdx = 1; }
        bump();
    }

    public void hide() {
        visible = false;
        wake();
        zone = Z_TRANSPORT;
        previewSection = -1;
        removeCallbacks(tick);
        invalidate();
    }

    /** Rebuild the rows of the open section (after a setting changed elsewhere). */
    public void refresh() {
        if (visible && (zone == Z_RAIL || zone == Z_PANEL || zone == Z_GRID) && railIdx != SEC_PLAY) {
            rows = host.rows(railIdx);
            if (rowIdx >= rows.size()) rowIdx = Math.max(0, rows.size() - 1);
            if (zone == Z_PANEL && (rowIdx < 0 || rowIdx >= rows.size() || !rows.get(rowIdx).focusable())) rowIdx = Math.max(0, firstFocusable());
        }
        invalidate();
    }

    private void bump() {
        lastActive = SystemClock.uptimeMillis();
        removeCallbacks(tick);
        postDelayed(tick, 500);
        invalidate();
    }

    private void enterRailItem() {
        if (railIdx != SEC_PLAY && previewSection != railIdx) {
            host.onOpen(railIdx);
            previewSection = railIdx;
        }
        rows = railIdx == SEC_PLAY ? new ArrayList<Row>() : host.rows(railIdx);
        scroll = 0;
    }

    private int firstFocusable() {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).focusable()) return i;
        return -1;
    }

    // ================================================================ keys

    /** Returns true when the key was used by the overlay. */
    public boolean onKey(int code, KeyEvent e) {
        boolean first = e.getRepeatCount() == 0;
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean toContent = code == (right ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT);
        boolean toEdge = code == (right ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT);
        boolean up = code == KeyEvent.KEYCODE_DPAD_UP, down = code == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean back = code == KeyEvent.KEYCODE_BACK;
        boolean menuKey = code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SETTINGS
                || code == KeyEvent.KEYCODE_GUIDE || code == KeyEvent.KEYCODE_INFO;

        if (!visible) {
            if (back) return false;
            if (ok || up || down || toContent || toEdge || menuKey) {
                if (first) show(menuKey);
                return true;
            }
            return false;
        }
        bump();
        if (back) {
            if (!first) return true;
            if (zone == Z_PANEL && host.onBack(railIdx)) {
                rows = host.rows(railIdx);
                chipCursor = -1;
                if (gridNow()) zone = Z_GRID;
                else rowIdx = Math.max(0, firstFocusable());
            } else if (zone == Z_PANEL || zone == Z_GRID) zone = Z_RAIL;
            else hide();
            invalidate();
            return true;
        }
        if (menuKey) { if (first) { zone = Z_RAIL; enterRailItem(); invalidate(); } return true; }

        if (zone == Z_TRANSPORT) {
            boolean hasQ = !host.upNext().isEmpty();
            boolean goRight = code == KeyEvent.KEYCODE_DPAD_RIGHT, goLeft = code == KeyEvent.KEYCODE_DPAD_LEFT;
            if ((goRight || goLeft) && first) {
                // left to right: info (4), previous (0), play (1), next (2), volume (3)
                int pos = tIdx == 4 ? 0 : tIdx + 1;
                if (goRight && pos < 4) tIdx = T_ORDER[pos + 1];
                else if (goLeft && pos > 0) tIdx = T_ORDER[pos - 1];
                else if (goRight && hasQ) { zone = Z_QUEUE; qIdx = 0; }
                else if (toEdge) { zone = Z_RAIL; enterRailItem(); }
            } else if (tIdx == 3 && (up || down)) {
                host.volumeStep(up ? 1 : -1, e.getRepeatCount());
            } else if (up && first) {
                if (host.durationMs() > 0) zone = Z_SEEK; else { zone = Z_RAIL; enterRailItem(); }
            } else if (ok && first) {
                host.transport(tIdx);
            }
            invalidate();
            return true;
        }

        if (zone == Z_QUEUE) {
            int n = Math.min(qShown, Math.min(3, host.upNext().size()));
            if (n == 0) { zone = Z_TRANSPORT; invalidate(); return true; }
            if (qIdx >= n) qIdx = n - 1;
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT && first) {
                if (qIdx < n - 1) qIdx++;
                else if (right) { zone = Z_RAIL; enterRailItem(); }
            } else if (code == KeyEvent.KEYCODE_DPAD_LEFT && first) {
                if (qIdx > 0) qIdx--;
                else { zone = Z_TRANSPORT; tIdx = 3; }
            }
            else if (up && first) { if (host.durationMs() > 0) zone = Z_SEEK; else { zone = Z_RAIL; enterRailItem(); } }
            else if (ok && first) { host.queueJump(qIdx); zone = Z_TRANSPORT; tIdx = 1; }
            invalidate();
            return true;
        }

        if (zone == Z_SEEK) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                host.seekStep(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1, e.getRepeatCount());
            } else if (down && first) {
                zone = Z_TRANSPORT; tIdx = 1;
            } else if (up && first) {
                zone = Z_RAIL; enterRailItem();
            } else if (ok && first) {
                host.transport(1);
            }
            invalidate();
            return true;
        }

        if (zone == Z_GRID) {
            int n = gridCards().size();
            if (n == 0) { zone = Z_RAIL; invalidate(); return true; }
            if (gridIdx >= n) gridIdx = n - 1;
            int col = gridIdx % 3;
            if (ok) {
                if (first) { okDown = true; okLong = false; }
                else if (okDown && !okLong && railIdx == SEC_AMB) {            // held: edit instead of launching
                    okLong = true;
                    host.cardEdit(gridIdx);
                    openAmbRows();
                }
            } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { if (col < 2 && gridIdx + 1 < n) gridIdx++; else if (right && first) zone = Z_RAIL; }
            else if (code == KeyEvent.KEYCODE_DPAD_LEFT) { if (col > 0) gridIdx--; else if (!right && first) zone = Z_RAIL; }
            else if (down) { if (gridIdx + 3 < n) gridIdx += 3; else if (gridIdx / 3 < (n - 1) / 3) gridIdx = n - 1; }
            else if (up) { if (gridIdx >= 3) gridIdx -= 3; }
            invalidate();
            return true;
        }


        if (zone == Z_RAIL) {
            if (up && railIdx > 0) { railIdx--; enterRailItem(); }
            else if (down && railIdx < SEC_NAMES.length - 1) { railIdx++; enterRailItem(); }
            else if ((toContent || ok) && first) {
                if (railIdx == SEC_PLAY) { zone = Z_TRANSPORT; tIdx = 1; }
                else if (gridNow()) {
                    host.onOpen(railIdx);
                    zone = Z_GRID;
                    okDown = false;     // this OK press opened the grid; it must not launch a card on release
                }
                else {
                    host.onOpen(railIdx);
                    rows = host.rows(railIdx);
                    rowIdx = firstFocusable();
                    chipCursor = -1;
                    zone = Z_PANEL;
                    if (rowIdx < 0) rowIdx = 0;
                }
            }
            invalidate();
            return true;
        }

        // Z_PANEL
        if (rows.isEmpty()) { if (toEdge) zone = Z_RAIL; invalidate(); return true; }
        Row r = rows.get(Math.min(rowIdx, rows.size() - 1));
        if (up || down) {
            chipCursor = -1;
            int i = rowIdx;
            do { i += up ? -1 : 1; } while (i >= 0 && i < rows.size() && !rows.get(i).focusable());
            if (i >= 0 && i < rows.size()) rowIdx = i;
            else if (firstFocusable() < 0) scroll += (up ? -1 : 1) * getHeight() * 0.08f;
        } else if (r.kind == ROW_CHIPS && (toContent || toEdge)) {
            int dir = code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
            int curChip = chipCursor < 0 ? r.selected : chipCursor;
            int n = curChip + dir;
            if (n >= 0 && n < r.options.length) {
                chipCursor = n;
            } else if (toEdge && first) {
                chipCursor = -1;
                zone = Z_RAIL;
            }
        } else if (toEdge && first) {
            zone = Z_RAIL;
        } else if (ok && first) {
            if (r.kind == ROW_CHIPS) {
                int pick = chipCursor < 0 ? r.selected : chipCursor;
                if (pick != r.selected) host.onChoice(railIdx, r, pick);
                chipCursor = -1;
            } else if (r.focusable()) {
                host.onActivate(railIdx, r);
            }
            rows = host.rows(railIdx);
            if (rowIdx >= rows.size()) rowIdx = Math.max(0, rows.size() - 1);
        }
        invalidate();
        return true;
    }

    /** Key release: commits a fast-forward / rewind made on the seek bar. */
    private void openAmbRows() {
        rows = host.rows(SEC_AMB);
        rowIdx = Math.max(0, firstFocusable());
        chipCursor = -1;
        scroll = 0;
        zone = Z_PANEL;
    }

    /** Called by the host when the Ambiances section switched between cards and rows. */
    public void ambChanged() {
        if (!visible) return;
        if (railIdx == SEC_GAMES) {
            if (!host.gameGrid()) { rows = host.rows(SEC_GAMES); rowIdx = Math.max(0, firstFocusable()); chipCursor = -1; scroll = 0; zone = Z_PANEL; }
            else if (zone == Z_PANEL) zone = Z_GRID;
            invalidate();
            return;
        }
        if (railIdx != SEC_AMB) return;
        if (host.ambRows()) openAmbRows();
        else if (zone == Z_PANEL) zone = Z_GRID;
        invalidate();
    }

    public boolean onKeyUp(int code) {
        boolean okKey = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (okKey && visible && zone == Z_GRID && okDown) {
            okDown = false;
            if (!okLong) { if (railIdx == SEC_GAMES) host.gamePick(gridIdx); else host.cardLaunch(gridIdx); }
            bump();
            return true;
        }
        if (okKey) okDown = false;
        if (visible && zone == Z_SEEK && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            host.seekCommit();
            bump();
            return true;
        }
        return false;
    }

    // ================================================================ drawing

    private void reportInsets(int H) {
        float s = H / 720f;
        float bottom = host.stripOn() ? stripHeight(s) + 12f * s : 0;
        if (bottom != lastB) {
            lastL = 0; lastR = 0; lastB = bottom;
            host.insets(0, 0, bottom);
        }
    }

    @Override protected void onDraw(Canvas c) {
        reportInsets(getHeight());
        int W = getWidth(), H = getHeight();
        float s = H / 720f;
        if (!visible) {
            long now = SystemClock.uptimeMillis();
            stripA = stripAlpha(now);
            if (host.stripOn() && stripA > 0.01f) drawTransport(c, W, H, s);
            if (stripSec > 0 && now < stripUntil && host.isPlaying()) postInvalidateDelayed(stripUntil - now > 800 ? 250 : 40);
            return;
        }
        stripA = 1f;
        boolean expanded = zone == Z_RAIL || zone == Z_PANEL || zone == Z_GRID;
        boolean panel = expanded && railIdx != SEC_PLAY;

        float rw = (expanded ? 250f : 96f) * s;
        float rx = right ? W - rw : 0;


        // ---------------------------------------------------------- rail
        if (expanded) {
            fill.setShader(null);
            fill.setColor((railAlpha << 24) | 0x080507);
            c.drawRect(rx, 0, rx + rw, H, fill);
        }
        float itemH = (expanded ? 54f : 56f) * s, gap = (expanded ? 8f : 14f) * s;
        float total = SEC_NAMES.length * itemH + (SEC_NAMES.length - 1) * gap;
        float y0 = (H - total) / 2f;
        if (expanded) {
            resetTp();
            tp.setTypeface(medium); tp.setTextSize(13f * s); tp.setLetterSpacing(0.15f); tp.setColor(0x80FFFFFF);
            c.drawText("POCHETTE", rx + 32f * s, y0 - 22f * s, tp);
            tp.setLetterSpacing(0f);
        }
        for (int i = 0; i < (expanded ? SEC_NAMES.length : 0); i++) {   // the rail only shows once you step onto it
            float iy = y0 + i * (itemH + gap);
            boolean focused = zone == Z_RAIL && i == railIdx;
            boolean current = (zone == Z_PANEL || zone == Z_GRID) && i == railIdx;
            int fg = 0xBFFFFFFF;
            if (expanded) {
                rf.set(rx + 18f * s, iy, rx + rw - 18f * s, iy + itemH);
                if (focused) { fill.setColor(0xFFFFFFFF); c.drawRoundRect(rf, 14f * s, 14f * s, fill); fg = 0xFF170D12; }
                else if (current) { fill.setColor(0x24FFFFFF); c.drawRoundRect(rf, 14f * s, 14f * s, fill); fg = 0xFFFFFFFF; }
                icon(c, SEC_ICONS[i], rf.left + 16f * s + 11f * s, iy + itemH / 2f, 22f * s, fg);
                resetTp();
                tp.setTypeface(focused || current ? bold : regular); tp.setTextSize(18f * s); tp.setColor(fg);
                c.drawText(T.t(SEC_NAMES[i]), rf.left + 54f * s, iy + itemH / 2f + 6.5f * s, tp);
            } else {
                float cx = rx + rw / 2f;
                rf.set(cx - 28f * s, iy, cx + 28f * s, iy + itemH);
                icon(c, SEC_ICONS[i], cx, iy + itemH / 2f, 26f * s, fg);
            }
        }

        if (expanded) {
            spotifyMark(c, rx + 32f * s, H - 44f * s, s, false, 255);
            roomsMark(c, rx + 32f * s, H - 70f * s, s, false, 255);
        }

        // ---------------------------------------------------------- panel
        if (panel && gridNow()) drawGrid(c, W, H, s, rx, rw);
        else if (panel) drawPanel(c, W, H, s, rx, rw);

        // ---------------------------------------------------------- transport
        if (!expanded) drawTransport(c, W, H, s);
    }

    /** Ambiance cards: three per row, across the whole free width. */
    private void drawGrid(Canvas c, int W, int H, float s, float rx, float rw) {
        float px = right ? 0 : rx + rw, pw = W - rw;
        fill.setShader(null);
        fill.setColor((panelAlpha << 24) | 0x160E12);
        c.drawRect(px, 0, px + pw, H, fill);
        float pad = 56f * s, gap = 22f * s;
        boolean games = railIdx == SEC_GAMES;
        List<Card> cards = gridCards();
        resetTp();
        tp.setTypeface(bold); tp.setTextSize(32f * s); tp.setColor(0xFFFFFFFF);
        c.drawText(T.t(games ? T.t("Jeux") : T.t("Ambiances")), px + pad, 80f * s, tp);
        tp.setTypeface(regular); tp.setTextSize(16f * s); tp.setColor(0xA6FFFFFF);
        c.drawText(T.t(games ? T.t("Choisis un jeu : la télé est le maître du jeu.") : T.t("Un OK : la playlist, les pièces, le volume et l'écran qui va avec.")), px + pad, 108f * s, tp);

        float cw = (pw - 2 * pad - 2 * gap) / 3f;
        int rowsN = (cards.size() + 2) / 3;
        int vis = Math.max(1, Math.min(rowsN, games ? 3 : 4));           // more rows than this scroll, so cards never get tiny
        int row = Math.max(0, Math.min(rowsN - 1, gridIdx / 3));
        if (row < gridRow0) gridRow0 = row;
        if (row >= gridRow0 + vis) gridRow0 = row - vis + 1;
        gridRow0 = Math.max(0, Math.min(Math.max(0, rowsN - vis), gridRow0));
        float top = 136f * s, availH = H - top - 56f * s;
        float ch = Math.min(games ? 230f * s : 196f * s, (availH - (vis - 1) * gap) / vis);
        if (rowsN > vis) {                                                 // scroll bar
            float bh = availH * vis / rowsN, by = top + (availH - bh) * gridRow0 / Math.max(1, rowsN - vis);
            fill.setColor(0x22FFFFFF); c.drawRect(px + pw - 22f * s, top, px + pw - 16f * s, top + availH, fill);
            fill.setColor(0x99FFFFFF); c.drawRect(px + pw - 22f * s, by, px + pw - 16f * s, by + bh, fill);
        }
        for (int i = gridRow0 * 3; i < Math.min(cards.size(), (gridRow0 + vis) * 3); i++) {
            Card cd = cards.get(i);
            float x = px + pad + (i % 3) * (cw + gap), y = top + (i / 3 - gridRow0) * (ch + gap);
            boolean f = zone == Z_GRID && i == gridIdx;
            rf.set(x, y, x + cw, y + ch);
            float rad = (games ? 0f : 30f) * s;
            if (f) {
                fill.setColor(0xFFF2C66B);
                rf.inset(-7f * s, -7f * s);
                c.drawRoundRect(rf, rad + 7f * s, rad + 7f * s, fill);
                fill.setColor(0xFF120A0E);
                rf.inset(3f * s, 3f * s);
                c.drawRoundRect(rf, rad + 4f * s, rad + 4f * s, fill);
                rf.inset(4f * s, 4f * s);
            }
            int fg = 0xFFFFFFFF;
            if (cd.icon >= 0) {
                fill.setShader(new LinearGradient(x, y, x + cw, y + ch, cd.c0, cd.c1, Shader.TileMode.CLAMP));
                c.drawRoundRect(rf, games ? 0f : rad, games ? 0f : rad, fill);
                fill.setShader(null);
                gameIcon(c, cd.icon, x + cw - 20f * s - 44f * s, y + 20f * s + 44f * s, 88f * s);
            } else if (cd.theme < 0) {
                stroke.setColor(0x59FFFFFF); stroke.setStrokeWidth(2f * s);
                c.drawRoundRect(rf, rad, rad, stroke);
                fg = 0xD9FFFFFF;
            } else {
                int[] cols = Themes.cardColors(cd.theme);
                android.graphics.Bitmap pv = ThemePreview.get(cd.theme);
                if (pv != null) {                                       // the card shows the wallpaper itself
                    fill.setShader(ThemePreview.shader(pv, rf.left, rf.top, rf.width(), rf.height()));
                    c.drawRoundRect(rf, rad, rad, fill);
                    fill.setShader(new LinearGradient(0, rf.top + rf.height() * 0.35f, 0, rf.bottom, 0x00000000, 0xCC000000, Shader.TileMode.CLAMP));
                    c.drawRoundRect(rf, rad, rad, fill);
                    fill.setShader(null);
                } else {
                    fill.setShader(new LinearGradient(x, y, x + cw, y + ch, cols[0], cols[1], Shader.TileMode.CLAMP));
                    c.drawRoundRect(rf, rad, rad, fill);
                    fill.setShader(null);
                    if (Themes.darkText(cd.theme) || cd.theme == Ambiance.T_SUMMER) fg = 0xFF2A1810;
                }
            }
            if (cd.icon < 0) cardIcon(c, cd.theme, x + 22f * s + 17f * s, y + 22f * s + 17f * s, 34f * s, fg);
            resetTp();
            tp.setTypeface(bold); tp.setTextSize(Math.min(24f * s, ch * 0.13f)); tp.setColor(fg);
            float ty = y + ch - 22f * s - 22f * s;
            c.drawText(ellipsize(cd.name, cw - 44f * s), x + 22f * s, ty, tp);
            tp.setTypeface(regular); tp.setTextSize(14f * s); tp.setColor((fg & 0x00FFFFFF) | 0xC0000000);
            c.drawText(ellipsize(cd.sub, cw - 44f * s), x + 22f * s, ty + 22f * s, tp);
        }
        resetTp();
        tp.setTypeface(regular); tp.setTextSize(13f * s); tp.setColor(0x80FFFFFF);
        c.drawText(zone == Z_GRID ? T.t("▲▼ ◀ ▶ se déplacer   OK lancer   maintenir OK : modifier   Retour") : (right ? "◀" : "▶") + T.t(" ou OK pour entrer"),
                px + pad, H - 28f * s, tp);
    }

    /** Big flat pictograms of the games (0 blind test, 1 chairs, 2 lyrics, 3 pixels, 4 quiz, 5 forbidden, 6 drawing). */
    private void gameIcon(Canvas c, int id, float cx, float cy, float size) {
        float k = size / 24f;
        c.save();
        c.translate(cx - 12 * k, cy - 12 * k);
        c.scale(k, k, 0, 0);
        stroke.setColor(0xE6FFFFFF);
        stroke.setStrokeWidth(1.6f);
        Path p = new Path();
        switch (id) {
            case 0: p.moveTo(5, 9); p.lineTo(5, 15); p.moveTo(9, 6); p.lineTo(9, 18); p.moveTo(13, 3); p.lineTo(13, 21); p.moveTo(17, 7); p.lineTo(17, 17); p.moveTo(21, 10); p.lineTo(21, 14); break;
            case 1: p.moveTo(6, 3); p.lineTo(6, 14); p.lineTo(18, 14); p.lineTo(18, 21); p.moveTo(6, 14); p.lineTo(6, 21); p.moveTo(6, 8); p.lineTo(14, 8); break;
            case 2: p.moveTo(4, 6); p.lineTo(20, 6); p.moveTo(4, 11); p.lineTo(16, 11); p.moveTo(4, 16); p.lineTo(20, 16); p.moveTo(4, 21); p.lineTo(11, 21); break;
            case 3: p.addRect(3, 3, 9, 9, Path.Direction.CW); p.addRect(15, 3, 21, 9, Path.Direction.CW); p.addRect(9, 9, 15, 15, Path.Direction.CW); p.addRect(3, 15, 9, 21, Path.Direction.CW); p.addRect(15, 15, 21, 21, Path.Direction.CW); break;
            case 4: p.moveTo(8, 8); p.lineTo(8, 5); p.lineTo(16, 5); p.lineTo(16, 11); p.lineTo(12, 13); p.lineTo(12, 16); p.moveTo(12, 19); p.lineTo(12, 21); break;
            case 5: p.addRect(3, 9, 21, 15, Path.Direction.CW); p.moveTo(6, 6); p.lineTo(18, 18); break;
            case 7: p.addCircle(8, 17, 3, Path.Direction.CW); p.moveTo(11, 17); p.lineTo(11, 5); p.lineTo(19, 7); p.moveTo(17, 13); p.lineTo(17, 21); p.moveTo(13, 17); p.lineTo(21, 17); break;
            default: p.moveTo(4, 20); p.lineTo(6, 14); p.lineTo(16, 4); p.lineTo(20, 8); p.lineTo(10, 18); p.lineTo(4, 20); p.moveTo(14, 6); p.lineTo(18, 10); break;
        }
        stroke.setStyle(Paint.Style.STROKE);
        c.drawPath(p, stroke);
        c.restore();
    }

    /** Small pictogram per theme: snowflake, sunrise, sun, alarm clock, record, default star, plus. */
    private void cardIcon(Canvas c, int theme, float cx, float cy, float size, int color) {
        float k = size / 24f;
        c.save();
        c.translate(cx - 12 * k, cy - 12 * k);
        c.scale(k, k, 0, 0);
        stroke.setColor(color);
        stroke.setStrokeWidth(2.1f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setShader(null);
        fill.setColor((color & 0x00FFFFFF) | 0x26000000);                  // soft round badge behind the pictogram
        c.drawCircle(12, 12, 17f, fill);
        Path p = new Path();
        switch (theme) {
            case -1:
                p.moveTo(12, 5); p.lineTo(12, 19); p.moveTo(5, 12); p.lineTo(19, 12);
                break;
            case Ambiance.T_XMAS:
                p.moveTo(12, 2.5f); p.lineTo(18, 11); p.lineTo(6, 11); p.close(); p.moveTo(12, 8); p.lineTo(20, 19); p.lineTo(4, 19); p.close(); p.moveTo(12, 19); p.lineTo(12, 22.5f);
                break;
            case Ambiance.T_SNOW:
                p.moveTo(12, 2); p.lineTo(12, 22); p.moveTo(3.3f, 7); p.lineTo(20.7f, 17); p.moveTo(3.3f, 17); p.lineTo(20.7f, 7);
                p.moveTo(9.5f, 3.5f); p.lineTo(12, 6); p.lineTo(14.5f, 3.5f); p.moveTo(9.5f, 20.5f); p.lineTo(12, 18); p.lineTo(14.5f, 20.5f);
                break;
            case Ambiance.T_MORNING:
                irf.set(7, 13, 17, 23); c.drawArc(irf, 180, 180, false, stroke);
                p.moveTo(12, 2); p.lineTo(12, 9); p.moveTo(4.2f, 10.2f); p.lineTo(5.6f, 11.6f); p.moveTo(18.4f, 11.6f); p.lineTo(19.8f, 10.2f);
                p.moveTo(1, 18); p.lineTo(3, 18); p.moveTo(21, 18); p.lineTo(23, 18); p.moveTo(1, 22); p.lineTo(23, 22);
                break;
            case Ambiance.T_SUMMER:
                c.drawCircle(12, 12, 5, stroke);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    p.moveTo(12 + (float) Math.cos(a) * 8.5f, 12 + (float) Math.sin(a) * 8.5f);
                    p.lineTo(12 + (float) Math.cos(a) * 11f, 12 + (float) Math.sin(a) * 11f);
                }
                break;
            case Ambiance.T_WAKE:
                c.drawCircle(12, 13, 8, stroke);
                p.moveTo(12, 9); p.lineTo(12, 13); p.lineTo(14.5f, 15); p.moveTo(5, 3); p.lineTo(2, 6); p.moveTo(22, 6); p.lineTo(19, 3);
                break;
            case Ambiance.T_RAIN:
                p.moveTo(7, 3); p.lineTo(5, 9); p.moveTo(13, 2); p.lineTo(10, 11); p.moveTo(19, 3); p.lineTo(17, 9);
                p.moveTo(9, 14); p.lineTo(7, 20); p.moveTo(16, 13); p.lineTo(14, 21);
                break;
            case Ambiance.T_PLANET:
                c.drawCircle(12, 17, 6, stroke); p.moveTo(12, 11); p.lineTo(12, 6);
                p.moveTo(12, 3); p.lineTo(12, 5); p.moveTo(10.5f, 4); p.lineTo(13.5f, 4); p.moveTo(19, 4); p.lineTo(19, 7);
                break;
            case Ambiance.T_FIRE:
                p.moveTo(12, 2); p.cubicTo(13, 6, 18, 8, 18, 14); p.cubicTo(18, 18, 15, 21, 12, 21);
                p.cubicTo(9, 21, 6, 18, 6, 14); p.cubicTo(6, 11, 8, 9, 9, 7); p.cubicTo(10, 9, 11, 9, 11.5f, 7.5f); p.cubicTo(11, 5, 11, 4, 12, 2);
                break;
            case Ambiance.T_PARTY:
                p.moveTo(12, 2); p.lineTo(14, 9); p.lineTo(21, 9); p.lineTo(15.5f, 13.5f); p.lineTo(17.5f, 21); p.lineTo(12, 16.5f); p.lineTo(6.5f, 21); p.lineTo(8.5f, 13.5f); p.lineTo(3, 9); p.lineTo(10, 9); p.close();
                break;
            case Ambiance.T_SOUL:
                c.drawCircle(12, 12, 10, stroke); c.drawCircle(12, 12, 6, stroke); c.drawCircle(12, 12, 2, stroke);
                break;
            default:
                p.moveTo(9, 18); p.lineTo(9, 5); p.lineTo(20, 3); p.lineTo(20, 16);
                c.drawCircle(6, 18, 3, stroke); c.drawCircle(17, 16, 3, stroke);
                break;
        }
        c.drawPath(p, stroke);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        stroke.setStrokeJoin(Paint.Join.MITER);
        c.restore();
    }

    private void drawPanel(Canvas c, int W, int H, float s, float rx, float rw) {
        float pw = 480f * s;
        float px = right ? rx - pw : rx + rw;
        fill.setColor((panelAlpha << 24) | 0x160E12);
        c.drawRect(px, 0, px + pw, H, fill);
        fill.setColor(0x14FFFFFF);
        c.drawRect(right ? px + pw - 1 : px, 0, right ? px + pw : px + 1, H, fill);
        float pad = 32f * s;
        float cw = pw - 2 * pad;

        // measure rows to scroll the focused one into view
        float[] heights = new float[rows.size()];
        float contentH = 0;
        for (int i = 0; i < rows.size(); i++) {
            heights[i] = rowHeight(rows.get(i), cw, s);
            contentH += heights[i] + (i > 0 ? 22f * s : 0);
        }
        float top = 56f * s + 28f * s + 32f * s, bottom = H - 60f * s;
        boolean focusing = zone == Z_PANEL && firstFocusable() >= 0;
        if (focusing) {
            float fy = 0;
            for (int i = 0; i < rowIdx; i++) fy += heights[i] + 22f * s;
            if (fy - scroll < 0) scroll = fy;
            if (fy + heights[rowIdx] - scroll > bottom - top) scroll = fy + heights[rowIdx] - (bottom - top);
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - (bottom - top))));

        resetTp();
        tp.setTypeface(bold); tp.setTextSize(28f * s); tp.setColor(0xFFFFFFFF);
        c.drawText(T.t(SEC_NAMES[railIdx]), px + pad, 56f * s + 26f * s, tp);

        c.save();
        c.clipRect(px, top - 8f * s, px + pw, bottom + 8f * s);
        float y = top - scroll;
        for (int i = 0; i < rows.size(); i++) {
            boolean f = zone == Z_PANEL && i == rowIdx;
            drawRow(c, rows.get(i), px + pad, y, cw, heights[i], s, f);
            y += heights[i] + 22f * s;
        }
        c.restore();

        resetTp();
        tp.setTypeface(regular); tp.setTextSize(13f * s); tp.setColor(0x80FFFFFF);
        String hint = zone == Z_PANEL ? T.t("▲▼ ◀ ▶ se déplacer   OK choisir   Retour") : (right ? "◀" : "▶") + T.t(" ou OK pour entrer");
        c.drawText(hint, px + pad, H - 28f * s, tp);
    }

    private float rowHeight(Row r, float cw, float s) {
        switch (r.kind) {
            case ROW_CHIPS: return 14f * s + 12f * s + chipLines(r, cw, s) * (40f * s) + (chipLines(r, cw, s) - 1) * 8f * s;
            case ROW_TOGGLE: return 46f * s;
            case ROW_BUTTON: return 84f * s;
            case ROW_ACTION: case ROW_CHECK: return 50f * s;
            default: return r.dot == -1 ? 44f * s : textLayout(r.label, cw, s).getHeight();
        }
    }

    private int chipLines(Row r, float cw, float s) {
        chipFont(s, false);
        int lines = 1;
        float x = 0;
        for (String o : r.options) {
            float w = tp.measureText(o) + 32f * s;
            if (x > 0 && x + w > cw) { lines++; x = 0; }
            x += w + 8f * s;
        }
        return lines;
    }

    private void chipFont(float s, boolean sel) {
        resetTp();
        tp.setTypeface(sel ? bold : regular); tp.setTextSize(15f * s);
    }

    private StaticLayout textLayout(String t, float cw, float s) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(regular); p.setTextSize(17f * s); p.setColor(0xCCFFFFFF);
        return StaticLayout.Builder.obtain(t, 0, t.length(), p, (int) cw).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f).setIncludePad(false).setEllipsize(TextUtils.TruncateAt.END).setMaxLines(12).build();
    }

    private void drawRow(Canvas c, Row r, float x, float y, float cw, float h, float s, boolean focused) {
        if (r.kind == ROW_CHIPS) {
            resetTp();
            tp.setTypeface(medium); tp.setTextSize(14f * s); tp.setLetterSpacing(0.1f);
            tp.setColor(focused ? 0xFFFFFFFF : 0x99FFFFFF);
            c.drawText(r.label.toUpperCase(T.locale()), x, y + 12f * s, tp);
            tp.setLetterSpacing(0f);
            float cx = x, cy = y + 26f * s;
            int cursor = chipCursor < 0 ? r.selected : chipCursor;
            for (int i = 0; i < r.options.length; i++) {
                boolean sel = i == r.selected;
                boolean ring = focused && i == cursor;
                chipFont(s, sel);
                float w = tp.measureText(r.options[i]) + 32f * s;
                if (cx > x && cx + w > x + cw) { cx = x; cy += 48f * s; }
                rf.set(cx, cy, cx + w, cy + 40f * s);
                if (ring) {
                    fill.setColor(ACCENT);
                    rf.inset(-6f * s, -6f * s);
                    c.drawRoundRect(rf, rf.height() / 2, rf.height() / 2, fill);
                    fill.setColor(0xFF1A1014);
                    rf.inset(3f * s, 3f * s);
                    c.drawRoundRect(rf, rf.height() / 2, rf.height() / 2, fill);
                    rf.inset(3f * s, 3f * s);
                }
                fill.setColor(sel ? 0xFFFFFFFF : 0x1AFFFFFF);
                c.drawRoundRect(rf, rf.height() / 2, rf.height() / 2, fill);
                tp.setColor(sel ? 0xFF170D12 : 0xFFFFFFFF);
                c.drawText(r.options[i], cx + 16f * s, cy + 25.5f * s, tp);
                cx += w + 8f * s;
            }
            return;
        }
        if (r.kind == ROW_BUTTON) {
            float t = 6f * s;
            rf.set(x - 14f * s, y + t, x + cw + 14f * s, y + h - t);
            fill.setColor(focused ? ACCENT : 0xFF8E4435);
            c.drawRect(rf, fill);
            if (focused) {
                fill.setColor(0xFFFFFFFF);
                c.drawRect(rf.left, rf.top, rf.right, rf.top + 3f * s, fill);
                c.drawRect(rf.left, rf.bottom - 3f * s, rf.right, rf.bottom, fill);
            }
            resetTp();
            tp.setTypeface(bold); tp.setTextSize(24f * s); tp.setColor(0xFFFFFFFF);
            tp.setTextAlign(Paint.Align.CENTER);
            c.drawText(r.label, (rf.left + rf.right) / 2f, y + h / 2f + 8.5f * s, tp);
            tp.setTextAlign(Paint.Align.LEFT);
            return;
        }
        if (r.kind == ROW_TEXT && r.dot == -1) {                       // group title
            resetTp();
            tp.setTypeface(medium); tp.setTextSize(13f * s); tp.setColor(0xFF8B93A8); tp.setLetterSpacing(0.08f);
            c.drawText(r.label.toUpperCase(), x, y + 30f * s, tp);
            tp.setLetterSpacing(0f);
            fill.setColor(0x1FFFFFFF);
            c.drawRect(x, y + 38f * s, x + cw, y + 39f * s, fill);
            return;
        }
        if (r.kind == ROW_TEXT) {
            float ind = r.dot != 0 ? 30f * s : 0f;
            StaticLayout l = textLayout(r.label, cw - ind, s);
            if (r.dot != 0) {
                fill.setColor(r.dot == 1 ? 0xFF3FD17A : r.dot == 2 ? 0xFFFFB020 : 0xFF6B6490);
                float sq = 16f * s, sy = y + 6f * s;
                c.drawRect(x, sy, x + sq, sy + sq, fill);
            }
            c.save(); c.translate(x + ind, y); l.draw(c); c.restore();
            return;
        }
        if (focused) {
            rf.set(x - 14f * s, y, x + cw + 14f * s, y + h);
            fill.setColor(0x24FFFFFF);
            c.drawRoundRect(rf, 12f * s, 12f * s, fill);
            fill.setColor(ACCENT);
            rf.set(x - 14f * s, y + 10f * s, x - 10f * s, y + h - 10f * s);
            c.drawRect(rf, fill);
        }
        resetTp();
        tp.setTypeface(focused ? medium : regular); tp.setTextSize(17f * s); tp.setColor(0xFFFFFFFF);
        float ty = y + h / 2f + 6f * s;
        if (r.kind == ROW_CHECK) {
            float bs = 22f * s, bx = x, by = y + (h - bs) / 2f;
            rf.set(bx, by, bx + bs, by + bs);
            if (r.on) {
                fill.setColor(ACCENT);
                c.drawRoundRect(rf, 5f * s, 5f * s, fill);
                stroke.setColor(0xFFFFFFFF); stroke.setStrokeWidth(2.6f * s);
                Path p = new Path();
                p.moveTo(bx + bs * 0.22f, by + bs * 0.52f);
                p.lineTo(bx + bs * 0.42f, by + bs * 0.72f);
                p.lineTo(bx + bs * 0.78f, by + bs * 0.30f);
                c.drawPath(p, stroke);
            } else {
                stroke.setColor(0x99FFFFFF); stroke.setStrokeWidth(2f * s);
                c.drawRoundRect(rf, 5f * s, 5f * s, stroke);
            }
            c.drawText(ellipsize(r.label, cw - 40f * s), x + 38f * s, ty, tp);
            return;
        }
        c.drawText(ellipsize(r.label, cw - (r.kind == ROW_TOGGLE ? 70f * s : 24f * s)), x, ty, tp);
        if (r.kind == ROW_TOGGLE) {
            float sw = 50f * s, sh = 28f * s, sx = x + cw - sw, sy = y + (h - sh) / 2f;
            rf.set(sx, sy, sx + sw, sy + sh);
            fill.setColor(r.on ? ACCENT : 0x40FFFFFF);
            c.drawRoundRect(rf, sh / 2, sh / 2, fill);
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(r.on ? sx + sw - sh / 2f : sx + sh / 2f, sy + sh / 2f, 11f * s, fill);
        } else {
            stroke.setColor(0x99FFFFFF); stroke.setStrokeWidth(2f * s);
            float ax = x + cw - 6f * s, ay = y + h / 2f;
            Path p = new Path();
            p.moveTo(ax - 6f * s, ay - 7f * s); p.lineTo(ax, ay); p.lineTo(ax - 6f * s, ay + 7f * s);
            c.drawPath(p, stroke);
        }
    }

    private String ellipsize(String t, float w) {
        return TextUtils.ellipsize(t, tp, w, TextUtils.TruncateAt.END).toString();
    }

    /** Flat full-width player band: thin progress line on top, icons (no circles), next tracks as plain text. */
    /** Small « SPOTIFY » mark, green when the connection works and red when it does not. Display only. */
    private void spotifyMark(Canvas c, float x, float y, float s, boolean alignRight, int alpha) {
        int st = host.spotifyState();
        if (st == 0 || alpha <= 0) return;
        int col = st == 1 ? 0x3FD17A : 0xFF5A4A;
        resetTp();
        tp.setTypeface(medium); tp.setTextSize(11f * s); tp.setLetterSpacing(0.12f);
        tp.setColor((alpha << 24) | col);
        float sq = 8f * s, gap = 8f * s;
        String label = "SPOTIFY";
        float tw = tp.measureText(label);
        float left = alignRight ? x - tw - gap - sq : x;
        fill.setShader(null);
        fill.setColor((alpha << 24) | col);
        c.drawRect(left, y - sq, left + sq, y, fill);
        tp.setTextAlign(Paint.Align.LEFT);
        c.drawText(label, left + sq + gap, y, tp);
        tp.setLetterSpacing(0f);
    }

    /** Rooms that play, small and grey, same style as the Spotify mark. */
    private void roomsMark(Canvas c, float x, float y, float s, boolean alignRight, int alpha) {
        String r = host.roomsLabel();
        if (r == null || r.length() == 0 || alpha <= 0) return;
        resetTp();
        tp.setTypeface(medium); tp.setTextSize(11f * s); tp.setLetterSpacing(0.12f);
        tp.setColor((alpha << 24) | 0xB8A8F0);
        float sq = 8f * s, gap = 8f * s;
        String label = r.toUpperCase(T.locale());
        float tw = tp.measureText(label);
        float left = alignRight ? x - tw - gap - sq : x;
        fill.setShader(null);
        fill.setColor((alpha << 24) | 0xB8A8F0);
        c.drawRect(left, y - sq, left + sq, y, fill);
        tp.setTextAlign(Paint.Align.LEFT);
        c.drawText(label, left + sq + gap, y, tp);
        tp.setLetterSpacing(0f);
    }

    private int qShown = 3;   // queue items that fit left of the room / Spotify marks

    private float markWidth(String label, float s) {
        resetTp();
        tp.setTypeface(medium); tp.setTextSize(11f * s); tp.setLetterSpacing(0.12f);
        float w = tp.measureText(label) + 16f * s;
        tp.setLetterSpacing(0f);
        return w;
    }

    private void drawTransport(Canvas c, int W, int H, float s) {
        List<SonosClient.Item> q = host.upNext();
        int n = Math.min(3, q.size());
        long dur = host.durationMs();
        float ph = stripHeight(s), top = H - ph;
        float x0 = 120f * s;
        fill.setShader(null);
        fill.setColor(al(0xD00A0507));
        c.drawRect(0, top, W, H, fill);
        float rowCy = top + 36f * s;
        if (dur > 0) {
            boolean f = visible && zone == Z_SEEK;
            float frac = Math.max(0f, Math.min(1f, host.positionMs() / (float) dur));
            float th = (f ? 6f : 3f) * s;
            fill.setColor(al(0x40FFFFFF));
            c.drawRect(0, top, W, top + th, fill);
            fill.setColor(al(ACCENT));
            c.drawRect(0, top, W * frac, top + th, fill);
            if (f) { fill.setColor(al(0xFFFFFFFF)); c.drawRect(W * frac - 2f * s, top - 5f * s, W * frac + 2f * s, top + 11f * s, fill); }
            resetTp();
            tp.setTypeface(regular); tp.setTextSize(13f * s); tp.setColor(al(f ? 0xFFFFFFFF : 0xB3FFFFFF));
            long p = host.positionMs() / 1000, d = dur / 1000, rem = Math.max(0, d - p);
            c.drawText(p / 60 + ":" + (p % 60 < 10 ? "0" : "") + p % 60, x0, top + 24f * s, tp);
            tp.setTextAlign(Paint.Align.RIGHT);
            c.drawText("-" + rem / 60 + ":" + (rem % 60 < 10 ? "0" : "") + rem % 60, W - x0 + 60f * s, top + 24f * s, tp);
            rowCy = top + 64f * s;
        }
        // transport icons
        float[] cxs = {x0 + 16f * s, x0 + 80f * s, x0 + 144f * s};
        for (int i = 0; i < 3; i++) {
            boolean f = visible && zone == Z_TRANSPORT && i == tIdx;
            int kind = i == 0 ? 10 : i == 2 ? 12 : (host.isPlaying() ? 11 : 0);
            icon(c, kind, cxs[i], rowCy, (i == 1 ? 30f : 26f) * s, al(f ? 0xFFFFFFFF : 0xCCFFFFFF));
            if (f) { fill.setColor(al(ACCENT)); c.drawRect(cxs[i] - 16f * s, rowCy + 20f * s, cxs[i] + 16f * s, rowCy + 23f * s, fill); }
        }
        spotifyMark(c, W - x0 + 60f * s, rowCy + 8f * s, s, true, (int) (255 * stripA));
        roomsMark(c, W - x0 + 60f * s, rowCy - 12f * s, s, true, (int) (255 * stripA));
        {   // info: small square speech bubble with an "i", bottom left
            boolean f = visible && zone == Z_TRANSPORT && tIdx == 4;
            float ix = 58f * s, h = 12f * s;
            int col = al(f ? 0xFFFFFFFF : 0x99FFFFFF);
            fill.setShader(null);
            fill.setColor(col);
            c.drawRect(ix - h, rowCy - h, ix + h, rowCy + h, fill);
            Path tail = new Path();
            tail.moveTo(ix - h + 2f * s, rowCy + h - 1f);
            tail.lineTo(ix - h - 3f * s, rowCy + h + 8f * s);
            tail.lineTo(ix - h + 11f * s, rowCy + h - 1f);
            tail.close();
            c.drawPath(tail, fill);
            fill.setColor(al(0xFF0A0507));
            c.drawRect(ix - h + 2f * s, rowCy - h + 2f * s, ix + h - 2f * s, rowCy + h - 2f * s, fill);
            fill.setColor(col);
            c.drawRect(ix - 2f * s, rowCy - 8f * s, ix + 2f * s, rowCy - 4.5f * s, fill);       // dot
            c.drawRect(ix - 2f * s, rowCy - 2f * s, ix + 2f * s, rowCy + 6f * s, fill);         // stem
            c.drawRect(ix - 5f * s, rowCy - 2f * s, ix - 2f * s, rowCy, fill);                 // top serif
            c.drawRect(ix - 5f * s, rowCy + 5f * s, ix + 5f * s, rowCy + 7f * s, fill);         // foot
            if (f) { fill.setColor(al(ACCENT)); c.drawRect(ix - 16f * s, rowCy + 20f * s, ix + 16f * s, rowCy + 23f * s, fill); }
        }
        float volEnd;
        {   // volume: speaker, 10 flat squares, number
            boolean f = visible && zone == Z_TRANSPORT && tIdx == 3;
            int v = host.volume();
            boolean mute = host.muted();
            float vx = x0 + 188f * s;
            int col = mute ? al(ACCENT) : al(f ? 0xFFFFFFFF : 0xCCFFFFFF);
            icon(c, 1, vx + 11f * s, rowCy, 22f * s, col);
            float bx = vx + 32f * s, bw = 5f * s, bh = 14f * s;
            int lit = v < 0 || mute ? 0 : Math.round(v / 10f);
            fill.setShader(null);
            for (int i = 0; i < 10; i++) {
                fill.setColor(i < lit ? al(f ? 0xFFFFFFFF : 0xCCFFFFFF) : al(0x44FFFFFF));
                c.drawRect(bx + i * (bw + 2f * s), rowCy - bh / 2f, bx + i * (bw + 2f * s) + bw, rowCy + bh / 2f, fill);
            }
            resetTp();
            tp.setTypeface(medium); tp.setTextSize(13f * s); tp.setColor(col);
            String num = mute ? T.t("MUET") : v < 0 ? "–" : String.valueOf(v);
            c.drawText(num, bx + 10f * (bw + 2f * s) + 10f * s, rowCy + 5f * s, tp);
            volEnd = bx + 10f * (bw + 2f * s) + 10f * s + tp.measureText(mute ? T.t("MUET") : "100");
            if (f) {
                tp.setTextSize(11f * s); tp.setColor(al(0xFFB8A8F0));
                c.drawText("▲▼", vx + 6f * s, rowCy - 22f * s, tp);
                fill.setColor(al(ACCENT));
                c.drawRect(vx, rowCy + 20f * s, volEnd, rowCy + 23f * s, fill);
            }
        }
        if (n == 0) { qShown = 0; return; }
        float dx = volEnd + 24f * s;
        fill.setColor(al(0x26FFFFFF));
        c.drawRect(dx, rowCy - 20f * s, dx + 1f * s, rowCy + 20f * s, fill);
        float qx = dx + 28f * s, iw = 272f * s, ta = 44f * s;
        float mwMax = 0;
        String rl = host.roomsLabel();
        if (rl != null && rl.length() > 0) mwMax = Math.max(mwMax, markWidth(rl.toUpperCase(T.locale()), s));
        if (host.spotifyState() != 0) mwMax = Math.max(mwMax, markWidth("SPOTIFY", s));
        float markLeft = (W - x0 + 60f * s) - mwMax;
        // As many titles as fit left of the room / Spotify marks, sharing the free width (3 when there is room).
        float avail = markLeft - 24f * s - qx;
        int shown = 0;
        for (int k = n; k >= 1; k--) {
            float w = Math.min(300f * s, avail / k);
            if (w - ta - 36f * s >= 110f * s) { shown = k; iw = w; break; }
        }
        n = shown;
        qShown = n;
        if (n == 0) return;
        resetTp();
        tp.setTypeface(medium); tp.setTextSize(11f * s); tp.setLetterSpacing(0.12f); tp.setColor(al(0x8CFFFFFF));
        c.drawText(T.t("À SUIVRE"), qx, rowCy - 30f * s, tp);
        for (int i = 0; i < n; i++) {
            SonosClient.Item it = q.get(i);
            float ix = qx + i * iw;
            float tx = ix + ta + 12f * s, mw = iw - ta - 36f * s;
            boolean f = visible && zone == Z_QUEUE && i == qIdx;
            irf.set(ix, rowCy - ta / 2f, ix + ta, rowCy + ta / 2f);
            if (it.thumb != null) {
                int tw = it.thumb.getWidth(), th = it.thumb.getHeight(), m = Math.min(tw, th);
                bmpPaint.setAlpha((int) (255 * stripA));
                c.drawBitmap(it.thumb, new android.graphics.Rect((tw - m) / 2, (th - m) / 2, (tw - m) / 2 + m, (th - m) / 2 + m), irf, bmpPaint);
            } else {
                fill.setColor(al(0x33FFFFFF));
                c.drawRect(irf, fill);
            }
            resetTp();
            tp.setTypeface(f ? bold : medium); tp.setTextSize(15f * s); tp.setColor(al(0xFFFFFFFF));
            c.drawText(ellipsize(it.title, mw), tx, rowCy - 3f * s, tp);
            tp.setTypeface(regular); tp.setTextSize(12f * s); tp.setColor(al(0x99FFFFFF));
            c.drawText(ellipsize(it.artist, mw), tx, rowCy + 15f * s, tp);
            if (f) { fill.setColor(al(ACCENT)); c.drawRect(ix, rowCy + 27f * s, ix + iw - 24f * s, rowCy + 30f * s, fill); }
        }
    }

    /** Stroke icons from 24-unit outlines. 0 play,1 speaker,2 layout,3 sliders,4 help,10 prev,11 pause,12 next. */
    private void icon(Canvas c, int kind, float cx, float cy, float size, int color) {
        float k = size / 24f;
        c.save();
        c.translate(cx - 12 * k, cy - 12 * k);
        c.scale(k, k, 0, 0);
        stroke.setColor(color);
        stroke.setStrokeWidth(2f);
        Path p = new Path();
        switch (kind) {
            case 0:
                p.moveTo(7, 4); p.lineTo(20, 12); p.lineTo(7, 20); p.close();
                break;
            case 1:
                irf.set(5, 2, 19, 22); c.drawRoundRect(irf, 2, 2, stroke);
                c.drawCircle(12, 14, 4, stroke);
                fill.setColor(color); c.drawCircle(12, 6, 1.1f, fill);
                break;
            case 2:
                irf.set(3, 3, 21, 21); c.drawRoundRect(irf, 2, 2, stroke);
                p.moveTo(9, 3); p.lineTo(9, 21);
                break;
            case 3:
                float[][] ls = {{4, 21, 4, 14}, {4, 10, 4, 3}, {12, 21, 12, 12}, {12, 8, 12, 3}, {20, 21, 20, 16}, {20, 12, 20, 3},
                        {1, 14, 7, 14}, {9, 8, 15, 8}, {17, 16, 23, 16}};
                for (float[] l : ls) { p.moveTo(l[0], l[1]); p.lineTo(l[2], l[3]); }
                break;
            case 4:
                c.drawCircle(12, 12, 10, stroke);
                p.moveTo(9.1f, 9f); p.cubicTo(9.5f, 6.4f, 14.9f, 6.4f, 14.9f, 10f); p.cubicTo(14.9f, 12f, 12f, 12.4f, 12f, 13.4f);
                fill.setColor(color); c.drawCircle(12, 17, 1.1f, fill);
                break;
            case 5:
                p.moveTo(12, 3); p.lineTo(14.5f, 8); p.lineTo(20, 8.8f); p.lineTo(16, 12.7f); p.lineTo(16.9f, 18.2f);
                p.lineTo(12, 15.6f); p.lineTo(7.1f, 18.2f); p.lineTo(8, 12.7f); p.lineTo(4, 8.8f); p.lineTo(9.5f, 8); p.close();
                break;
            case 6:
                irf.set(3, 3, 21, 21); c.drawRect(irf, stroke);
                fill.setColor(color);
                c.drawRect(7f, 7f, 9f, 9f, fill); c.drawRect(15f, 15f, 17f, 17f, fill); c.drawRect(11f, 11f, 13f, 13f, fill);
                c.drawRect(15f, 7f, 17f, 9f, fill); c.drawRect(7f, 15f, 9f, 17f, fill);
                break;
            case 10:
                p.moveTo(19, 20); p.lineTo(9, 12); p.lineTo(19, 4); p.close(); p.moveTo(5, 19); p.lineTo(5, 5);
                break;
            case 11:
                irf.set(6, 4, 10, 20); c.drawRect(irf, stroke);
                irf.set(14, 4, 18, 20); c.drawRect(irf, stroke);
                break;
            case 12:
                p.moveTo(5, 4); p.lineTo(15, 12); p.lineTo(5, 20); p.close(); p.moveTo(19, 5); p.lineTo(19, 19);
                break;
        }
        c.drawPath(p, stroke);
        c.restore();
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
