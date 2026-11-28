package app.pochette.tv.ui;

import app.pochette.tv.core.Ambiance;
import app.pochette.tv.core.KitchenTimer;
import app.pochette.tv.core.T;
import app.pochette.tv.sonos.SonosClient;
import app.pochette.tv.theme.ThemePreview;
import app.pochette.tv.theme.Themes;

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
    public static final int SEC_PLAY = 0, SEC_AMB = 1, SEC_FAV = 2, SEC_GAMES = 3, SEC_ROOMS = 4, SEC_DISPLAY = 5, SEC_SETTINGS = 6, SEC_HELP = 7;
    public static final String[] SEC_NAMES = {"Lecture", "Ambiances", "Favoris", "Jeux", "Pièces", "Affichage", "Réglages", "À propos"};
    private static final int[] SEC_ICONS = {0, 5, 8, 6, 1, 2, 3, 4};

    /** One ambiance card of the grid. theme = Ambiance.T_*, or -1 for the "new" card. */
    public static final class Card {
        public String name, sub;
        public int theme;
        public int c0, c1, icon = -1;     // game cards: own colours and pictogram
        public Card(String name, String sub, int theme) { this.name = name; this.sub = sub; this.theme = theme; }
        public Card(String name, String sub, int c0, int c1, int icon) { this.name = name; this.sub = sub; this.theme = -2; this.c0 = c0; this.c1 = c1; this.icon = icon; }
    }
    public static final int ROW_CHIPS = 0, ROW_TOGGLE = 1, ROW_ACTION = 2, ROW_CHECK = 3, ROW_TEXT = 4, ROW_BUTTON = 5, ROW_SLIDER = 6;

    public static final class Row {
        public String id, label;
        public int kind;
        public String[] options;
        public int selected;
        /** Slider rows: selected goes from 0 to max. */
        public int max;
        public boolean on;
        /** Chips row drawn as a strip of preview cards instead of pills. */
        public boolean cards;
        public int dot;     // text rows: 0 none, 1 green, 2 orange, 3 grey (a small flat square before the text)

        static Row make(String id, int kind, String label) {
            Row r = new Row();
            r.id = id; r.kind = kind; r.label = label;
            return r;
        }
        public static Row chips(String id, String label, String[] opts, int sel) {
            Row r = make(id, ROW_CHIPS, label); r.options = T.arr(opts); r.selected = sel; return r;
        }
        public static Row cards(String id, String label, String[] opts, int sel) { Row r = chips(id, label, opts, sel); r.cards = true; return r; }
        public static Row toggle(String id, String label, boolean on) { Row r = make(id, ROW_TOGGLE, label); r.on = on; return r; }
        public static Row slider(String id, String label, int value, int max) { Row r = make(id, ROW_SLIDER, label); r.selected = value; r.max = max; return r; }
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
        /** The rooms of the playing group, their own volumes (-1 while unknown), setting one, and reading them again. */
        String[] roomNames();
        int[] roomVolumes();
        void roomVolumeSet(int idx, int vol);
        void roomVolumesLoad();
        /** Kitchen timer: the duration last chosen (minutes), remembering it, starting and cancelling. */
        int timerMin();
        void timerMin(int minutes);
        void timerStart(long ms);
        void timerCancel();
        /** Room taken by the overlay on the left, right and bottom (px), so the music can move aside. */
        void insets(float left, float right, float bottom);
        /** Ambiances: the cards, launching one (OK) and editing one (long OK). */
        List<Card> cards();
        List<Card> gameCards();
        boolean gameGrid();
        /** Name of the game being set up, or null. */
        String gameTitle();
        void gamePick(int index);
        void cardLaunch(int index);
        void cardEdit(int index);
        /** True while the Ambiances section shows rows (editor, playlist picker) instead of the cards. */
        boolean ambRows();
        /** Reorder mode of the Ambiances cards: on, a card picked up, pick up / put down, move a card, Back. */
        boolean ambReorder();
        boolean ambGrabbed();
        void ambGrab();
        int ambMove(int from, int to);
        void ambReorderBack();
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
    /** One focus ring and one "selected" colour for every kind of control. */
    private static final int FOCUS = 0xFFF2C66B, OK_GREEN = 0xFF34D27B;

    private final Host host;
    private boolean visible;
    private int zone = Z_TRANSPORT, tIdx = 1, railIdx = SEC_AMB, rowIdx;
    private int previewSection = -1;
    private int gridIdx, qIdx, gridRow0;
    private boolean okDown, okLong;
    private boolean rv;                  // the per-room volume panel is open
    private int rvIdx;                   // 0 = whole group, then one row per room
    private boolean tm;                  // the timer panel is open
    private int tmRow, tmMin = 10;       // row 0 = duration + start, row 1 = stop (when running)
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
    private final Icons icons = new Icons();
    private final RectF irf = new RectF();
    private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            // the left rail and its pages stay until BACK; only the bottom bar closes by itself
            if (rv || tm) {                         // these panels close by themselves after 10 s without a key
                if (SystemClock.uptimeMillis() - lastActive > 10000) { hide(); return; }
                postDelayed(this, 500);
                return;
            }
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

    private int railAlpha = 0xD9, panelAlpha = 0xCC, stripBgAlpha = 0xD0 - 2 * (0xD0 - 0x30) / 10;

    /** Bottom player strip background, from 0 (as before) to 10 (almost see-through). */
    public void setStripTransparency(int t) {
        int v = Math.max(0, Math.min(10, t));
        stripBgAlpha = 0xD0 - v * (0xD0 - 0x30) / 10;
        invalidate();
    }

    /** Transparency from 0 (almost opaque) to 10 (very transparent). */
    public void setTransparency(int t) {
        int v = Math.max(0, Math.min(10, t));
        railAlpha = 0xFA - v * (0xFA - 0x59) / 10;
        panelAlpha = 0xF8 - v * (0xF8 - 0x50) / 10;
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
        rv = false;
        tm = false;
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
        boolean toContent = code == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean toEdge = code == KeyEvent.KEYCODE_DPAD_LEFT;
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
        if (rv) {                                                          // per-room volume panel
            int rows = 1 + host.roomNames().length;
            if (back || (ok && first)) { rv = false; }
            else if (up && first) rvIdx = Math.max(0, rvIdx - 1);
            else if (down && first) rvIdx = Math.min(rows - 1, rvIdx + 1);
            else if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int dir = code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1, step = e.getRepeatCount() < 12 ? 2 : 4;
                if (rvIdx == 0) host.volumeStep(dir, e.getRepeatCount());
                else {
                    int[] v = host.roomVolumes();
                    int cur = rvIdx - 1 < v.length ? v[rvIdx - 1] : -1;
                    if (cur >= 0) host.roomVolumeSet(rvIdx - 1, cur + dir * step);
                }
            }
            bump();
            invalidate();
            return true;
        }
        if (tm) {                                                          // kitchen timer panel
            boolean run = KitchenTimer.running();
            int rowsN = run ? 2 : 1;
            if (back) tm = false;
            else if (up && first) tmRow = Math.max(0, tmRow - 1);
            else if (down && first) tmRow = Math.min(rowsN - 1, tmRow + 1);
            else if ((code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) && tmRow == 0) {
                int dir = code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1, step = e.getRepeatCount() < 10 ? 1 : 5;
                tmMin = Math.max(1, Math.min(240, tmMin + dir * step));
                host.timerMin(tmMin);
            } else if (ok && first) {
                if (tmRow == 0) host.timerStart(tmMin * 60000L); else host.timerCancel();
                tm = false;
            }
            bump();
            invalidate();
            return true;
        }
        bump();
        if (back) {
            if (!first) return true;
            if (zone == Z_GRID && railIdx == SEC_AMB && host.ambReorder()) { host.ambReorderBack(); gridIdx = Math.min(gridIdx, Math.max(0, gridCards().size() - 1)); invalidate(); return true; }
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
            if (tIdx == 5 && (goRight || goLeft) && first) {                // the alarm clock, far right
                if (goLeft) {
                    if (hasQ) { zone = Z_QUEUE; qIdx = Math.max(0, Math.min(qShown, Math.min(3, host.upNext().size())) - 1); }
                    else tIdx = 3;
                } else if (toEdge) { zone = Z_RAIL; enterRailItem(); }
            } else if ((goRight || goLeft) && first) {
                // left to right: info (4), previous (0), play (1), next (2), volume (3), alarm (5)
                int pos = tIdx == 4 ? 0 : tIdx + 1;
                if (goRight && pos < 4) tIdx = T_ORDER[pos + 1];
                else if (goLeft && pos > 0) tIdx = T_ORDER[pos - 1];
                else if (goRight && hasQ) { zone = Z_QUEUE; qIdx = 0; }
                else if (goRight && !toEdge) tIdx = 5;
                else if (toEdge) { zone = Z_RAIL; enterRailItem(); }
            } else if (tIdx == 3 && (up || down)) {
                host.volumeStep(up ? 1 : -1, e.getRepeatCount());
            } else if (up && first) {
                if (host.durationMs() > 0) zone = Z_SEEK; else { zone = Z_RAIL; enterRailItem(); }
            } else if (ok && tIdx == 3) {                                   // volume: OK mutes, a long OK opens the rooms
                if (first) { okDown = true; okLong = false; }
                else if (okDown && !okLong && e.getRepeatCount() >= 6) {
                    okLong = true;
                    rv = true; rvIdx = 0;
                    host.roomVolumesLoad();
                }
            } else if (ok && first && tIdx == 5) {
                tm = true; tmRow = 0; tmMin = host.timerMin();
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
                else { zone = Z_TRANSPORT; tIdx = 5; }
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
            if (railIdx == SEC_AMB && host.ambReorder()) {          // reorder mode: OK picks a card up or puts it down, arrows move it
                if (ok) { if (first) host.ambGrab(); }
                else if (host.ambGrabbed()) {
                    int to = gridIdx;
                    if (code == KeyEvent.KEYCODE_DPAD_RIGHT) to = gridIdx + 1;
                    else if (code == KeyEvent.KEYCODE_DPAD_LEFT) to = gridIdx - 1;
                    else if (down) to = gridIdx + 3;
                    else if (up) to = gridIdx - 3;
                    if (to >= 0 && to < n) gridIdx = host.ambMove(gridIdx, to);
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { if (col < 2 && gridIdx + 1 < n) gridIdx++; }
                else if (code == KeyEvent.KEYCODE_DPAD_LEFT) { if (col > 0) gridIdx--; }
                else if (down) { if (gridIdx + 3 < n) gridIdx += 3; else if (gridIdx / 3 < (n - 1) / 3) gridIdx = n - 1; }
                else if (up) { if (gridIdx >= 3) gridIdx -= 3; }
                invalidate();
                return true;
            }
            if (ok) {
                if (first) { okDown = true; okLong = false; }
                else if (okDown && !okLong && railIdx == SEC_AMB) {            // held: edit instead of launching
                    okLong = true;
                    host.cardEdit(gridIdx);
                    if (host.ambRows()) openAmbRows();
                }
            } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { if (col < 2 && gridIdx + 1 < n) gridIdx++; }
            else if (code == KeyEvent.KEYCODE_DPAD_LEFT) { if (col > 0) gridIdx--; else if (first) zone = Z_RAIL; }
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
        } else if (r.kind == ROW_SLIDER && (toContent || toEdge)) {
            int nv = Math.max(0, Math.min(r.max, r.selected + (code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1)));
            if (nv != r.selected) { r.selected = nv; host.onChoice(railIdx, r, nv); }
            else if (toEdge && first) zone = Z_RAIL;
        } else if (toEdge && first) {
            zone = Z_RAIL;
        } else if (ok && first) {
            if (r.kind == ROW_CHIPS) {
                int pick = chipCursor < 0 ? r.selected : chipCursor;
                if (pick != r.selected) host.onChoice(railIdx, r, pick);
                chipCursor = -1;
            } else if (r.focusable() && r.kind != ROW_SLIDER) {
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
            if (!okLong) {
                if (railIdx == SEC_GAMES) host.gamePick(gridIdx);
                else { host.cardLaunch(gridIdx); if (railIdx == SEC_AMB && host.ambReorder()) gridIdx = 0; }
            }
            bump();
            return true;
        }
        if (okKey && visible && zone == Z_TRANSPORT && okDown) {
            okDown = false;
            if (!okLong && tIdx == 3) host.transport(3);
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

    // ---- warning banner drawn above everything this view paints (the music view sits underneath the open menu)
    private String toast;
    private long toastUntil;
    public void showToast(String t, long ms) { toast = t; toastUntil = SystemClock.uptimeMillis() + ms; invalidate(); }

    private void drawToast(Canvas c) {
        long now = SystemClock.uptimeMillis();
        if (toast == null || now >= toastUntil) return;
        int W = getWidth(), H = getHeight();
        float s = H / 720f;
        float a = Math.min(1f, (toastUntil - now) / 300f);
        resetTp();
        tp.setAntiAlias(true);
        tp.setTypeface(medium);
        tp.setTextSize(24f * s);
        tp.setTextAlign(Paint.Align.CENTER);
        String t = ellipsize(toast, W * 0.86f);
        float tw = tp.measureText(t);
        float top = 36f * s, h = 56f * s;
        rf.set(W / 2f - tw / 2f - 28f * s, top, W / 2f + tw / 2f + 28f * s, top + h);
        fill.setShader(null);
        fill.setColor(((int) (245 * a) << 24) | 0xF2B134);
        c.drawRoundRect(rf, h / 2f, h / 2f, fill);
        tp.setColor(((int) (255 * a) << 24) | 0x1A1208);
        c.drawText(t, W / 2f, top + h / 2f + 8.5f * s, tp);
        tp.setTextAlign(Paint.Align.LEFT);
        postInvalidateDelayed(40);
    }

    @Override protected void onDraw(Canvas c) {
        drawMain(c);
        if (visible && rv) drawRoomVol(c, getWidth(), getHeight(), getHeight() / 720f);
        if (visible && tm) drawTimerPanel(c, getWidth(), getHeight(), getHeight() / 720f);
        drawToast(c);
    }

    private void drawTimerPanel(Canvas c, int W, int H, float s) {
        boolean run = KitchenTimer.running();
        fill.setShader(null);
        fill.setColor(0xB0000000);
        c.drawRect(0, 0, W, H, fill);
        float pw = Math.min(W - 80f * s, 560f * s), rh = 70f * s;
        float ph = 190f * s + (run ? 2 : 1) * rh;
        float px = (W - pw) / 2f, py = (H - ph) / 2f;
        fill.setColor(0xFF1C1626);
        rf.set(px, py, px + pw, py + ph);
        c.drawRoundRect(rf, 22f * s, 22f * s, fill);
        resetTp();
        tp.setTypeface(bold); tp.setTextSize(24f * s); tp.setColor(0xFFFFFFFF);
        c.drawText(T.t("Minuteur"), px + 28f * s, py + 44f * s, tp);
        if (run) {
            tp.setTypeface(regular); tp.setTextSize(15f * s); tp.setColor(0xFF5DCAA5);
            tp.setTextAlign(Paint.Align.RIGHT);
            c.drawText(T.t("En cours : ") + KitchenTimer.format(KitchenTimer.remaining()), px + pw - 28f * s, py + 44f * s, tp);
            tp.setTextAlign(Paint.Align.LEFT);
        }
        String d = tmMin >= 60 ? (tmMin / 60) + " h" + (tmMin % 60 > 0 ? " " + (tmMin % 60 < 10 ? "0" : "") + tmMin % 60 : "") : tmMin + " min";
        tp.setTypeface(bold); tp.setTextSize(54f * s); tp.setColor(0xFFFFFFFF);
        tp.setTextAlign(Paint.Align.CENTER);
        c.drawText(d, px + pw / 2f, py + 112f * s, tp);
        tp.setTextAlign(Paint.Align.LEFT);
        float bx = px + 40f * s, bw = pw - 80f * s, by = py + 128f * s;
        fill.setColor(0x33FFFFFF);
        rf.set(bx, by, bx + bw, by + 6f * s);
        c.drawRoundRect(rf, 3f * s, 3f * s, fill);
        fill.setColor(0xFF5DCAA5);
        rf.set(bx, by, bx + bw * tmMin / 240f, by + 6f * s);
        c.drawRoundRect(rf, 3f * s, 3f * s, fill);
        tp.setTypeface(regular); tp.setTextSize(14f * s); tp.setColor(0x99FFFFFF);
        c.drawText(T.t("◀ ▶ minute par minute (maintenu : plus vite)   Retour : fermer"), px + 28f * s, py + 168f * s, tp);
        String[] lab = {run ? T.t("Relancer avec cette durée") : T.t("Lancer le minuteur"), T.t("Arrêter le minuteur")};
        for (int i = 0; i < (run ? 2 : 1); i++) {
            float y = py + 190f * s + i * rh;
            if (i == tmRow) {
                fill.setColor(0x33FFFFFF);
                rf.set(px + 14f * s, y, px + pw - 14f * s, y + rh - 10f * s);
                c.drawRoundRect(rf, 14f * s, 14f * s, fill);
            }
            resetTp();
            tp.setTypeface(i == tmRow ? bold : regular); tp.setTextSize(20f * s); tp.setColor(i == 1 ? 0xFFFFB199 : 0xFFFFFFFF);
            c.drawText(lab[i], px + 34f * s, y + 36f * s, tp);
        }
    }

    /** The alarm clock of the bottom bar: a ring, two bells and the hands. */
    private void drawAlarm(Canvas c, float cx, float cy, float s, int col) {
        stroke.setColor(col);
        stroke.setStrokeWidth(2.2f * s);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        c.drawCircle(cx, cy + 1f * s, 9f * s, stroke);
        c.drawLine(cx, cy + 1f * s, cx, cy - 4f * s, stroke);
        c.drawLine(cx, cy + 1f * s, cx + 3.5f * s, cy + 3.5f * s, stroke);
        c.drawLine(cx - 8f * s, cy - 9f * s, cx - 12f * s, cy - 5f * s, stroke);
        c.drawLine(cx + 8f * s, cy - 9f * s, cx + 12f * s, cy - 5f * s, stroke);
        c.drawLine(cx - 6f * s, cy + 11f * s, cx - 9f * s, cy + 14f * s, stroke);
        c.drawLine(cx + 6f * s, cy + 11f * s, cx + 9f * s, cy + 14f * s, stroke);
    }

    /** The volume of each room of the playing group, on its own page over the screen. */
    private void drawRoomVol(Canvas c, int W, int H, float s) {
        String[] names = host.roomNames();
        int[] vols = host.roomVolumes();
        int rows = 1 + names.length;
        fill.setShader(null);
        fill.setColor(0xB0000000);
        c.drawRect(0, 0, W, H, fill);
        float pw = Math.min(W - 80f * s, 620f * s), rh = 64f * s;
        float ph = 96f * s + rows * rh + 20f * s;
        float px = (W - pw) / 2f, py = Math.max(30f * s, (H - ph) / 2f);
        fill.setColor(0xFF1C1626);
        rf.set(px, py, px + pw, py + ph);
        c.drawRoundRect(rf, 22f * s, 22f * s, fill);
        resetTp();
        tp.setTypeface(bold); tp.setTextSize(24f * s); tp.setColor(0xFFFFFFFF);
        c.drawText(T.t("Volume par pièce"), px + 28f * s, py + 44f * s, tp);
        tp.setTypeface(regular); tp.setTextSize(14f * s); tp.setColor(0x99FFFFFF);
        c.drawText(T.t("▲▼ pièce   ◀ ▶ régler   OK ou Retour : fermer"), px + 28f * s, py + 70f * s, tp);
        for (int i = 0; i < rows; i++) {
            float y = py + 96f * s + i * rh;
            boolean f = i == rvIdx;
            if (f) {
                fill.setColor(0x33FFFFFF);
                rf.set(px + 14f * s, y, px + pw - 14f * s, y + rh - 6f * s);
                c.drawRoundRect(rf, 14f * s, 14f * s, fill);
            }
            String name = i == 0 ? T.t("Tout le groupe") : names[i - 1];
            int v = i == 0 ? host.volume() : (i - 1 < vols.length ? vols[i - 1] : -1);
            resetTp();
            tp.setTypeface(f ? bold : regular); tp.setTextSize(18f * s); tp.setColor(0xFFFFFFFF);
            c.drawText(ellipsize(name, pw * 0.4f), px + 30f * s, y + 28f * s, tp);
            tp.setTextAlign(Paint.Align.RIGHT);
            c.drawText(v < 0 ? "–" : String.valueOf(v), px + pw - 30f * s, y + 28f * s, tp);
            float bx = px + 30f * s, bw = pw - 60f * s, by = y + 40f * s;
            fill.setColor(0x33FFFFFF);
            rf.set(bx, by, bx + bw, by + 6f * s);
            c.drawRoundRect(rf, 3f * s, 3f * s, fill);
            if (v >= 0) {
                fill.setColor(i == 0 ? 0xFFFFC857 : 0xFF5DCAA5);
                rf.set(bx, by, bx + bw * v / 100f, by + 6f * s);
                c.drawRoundRect(rf, 3f * s, 3f * s, fill);
            }
        }
    }

    private void drawMain(Canvas c) {
        reportInsets(getHeight());
        int W = getWidth(), H = getHeight();
        float s = H / 720f;
        if (!visible) {
            long now = SystemClock.uptimeMillis();
            stripA = stripAlpha(now);
            if (host.stripOn() && stripA > 0.01f) drawTransport(c, W, H, s);
            if (stripSec > 0 && !host.isPlaying()) postInvalidateDelayed(1000);      // paused: re-check once a second (the dark screen hides the bar)
            if (stripSec > 0 && now < stripUntil && host.isPlaying()) postInvalidateDelayed(stripUntil - now > 800 ? 250 : 40);
            return;
        }
        stripA = 1f;
        boolean expanded = zone == Z_RAIL || zone == Z_PANEL || zone == Z_GRID;
        boolean panel = expanded && railIdx != SEC_PLAY;

        float rw = (expanded ? 250f : 96f) * s;
        float rx = 0;

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
                icons.icon(c, SEC_ICONS[i], rf.left + 16f * s + 11f * s, iy + itemH / 2f, 22f * s, fg);
                resetTp();
                tp.setTypeface(focused || current ? bold : regular); tp.setTextSize(18f * s); tp.setColor(fg);
                c.drawText(T.t(SEC_NAMES[i]), rf.left + 54f * s, iy + itemH / 2f + 6.5f * s, tp);
            } else {
                float cx = rx + rw / 2f;
                rf.set(cx - 28f * s, iy, cx + 28f * s, iy + itemH);
                icons.icon(c, SEC_ICONS[i], cx, iy + itemH / 2f, 26f * s, fg);
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
        if (expanded) drawSay(c, W, H, s);
    }

    private String sayTitle, sayText;
    private long sayUntil;

    /** Message shown above the menu itself (the screen's own toasts would sit behind it). */
    public void say(String title, String text, long ms) {
        sayTitle = title; sayText = text;
        sayUntil = SystemClock.uptimeMillis() + ms;
        invalidate();
        postInvalidateDelayed(ms + 50);
    }

    private void drawSay(Canvas c, int W, int H, float s) {
        if (sayText == null || SystemClock.uptimeMillis() > sayUntil) return;
        float bw = Math.min(W * 0.62f, 820f * s), pad = 22f * s;
        StaticLayout l = textLayout(sayText, bw - 2 * pad, s);
        float th = sayTitle != null && sayTitle.length() > 0 ? 30f * s : 0f;
        float bh = l.getHeight() + th + 2 * pad;
        float bx = (W - bw) / 2f, by = H - 64f * s - bh;
        rf.set(bx, by, bx + bw, by + bh);
        fill.setColor(0xF2201722);
        c.drawRoundRect(rf, 18f * s, 18f * s, fill);
        stroke.setColor(FOCUS); stroke.setStrokeWidth(2f * s);
        c.drawRoundRect(rf, 18f * s, 18f * s, stroke);
        if (th > 0) {
            resetTp();
            tp.setTypeface(bold); tp.setTextSize(19f * s); tp.setColor(0xFFFFFFFF);
            c.drawText(sayTitle, bx + pad, by + pad + 18f * s, tp);
        }
        c.save(); c.translate(bx + pad, by + pad + th); l.draw(c); c.restore();
    }

    /** Ambiance cards: three per row, across the whole free width. */
    private void drawGrid(Canvas c, int W, int H, float s, float rx, float rw) {
        float px = rx + rw, pw = W - rw;
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
        boolean reo = !games && host.ambReorder(), grab = reo && host.ambGrabbed();
        c.drawText(reo ? T.t("Réorganiser : choisis une ambiance, OK pour la saisir, les flèches pour la déplacer, OK pour la poser.")
                : T.t(games ? T.t("Choisis un jeu : la télé est le maître du jeu.") : T.t("Un OK : la playlist, les pièces, le volume et l'écran qui va avec.")), px + pad, 108f * s, tp);

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
            float rad = 30f * s;
            if (f) {
                fill.setColor(grab ? 0xFFFFC857 : FOCUS);
                rf.inset(grab ? -11f * s : -7f * s, grab ? -11f * s : -7f * s);
                c.drawRoundRect(rf, rad + 7f * s, rad + 7f * s, fill);
                fill.setColor(0xFF120A0E);
                rf.inset(3f * s, 3f * s);
                c.drawRoundRect(rf, rad + 4f * s, rad + 4f * s, fill);
                rf.inset(4f * s, 4f * s);
            }
            int fg = 0xFFFFFFFF;
            if (cd.icon >= 0) {
                fill.setShader(new LinearGradient(x, y, x + cw, y + ch, cd.c0, cd.c1, Shader.TileMode.CLAMP));
                c.drawRoundRect(rf, rad, rad, fill);
                fill.setShader(null);
                icons.gameIcon(c, cd.icon, x + cw - 20f * s - 44f * s, y + 20f * s + 44f * s, 88f * s);
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
            if (cd.icon < 0) icons.cardIcon(c, cd.theme, x + 22f * s + 17f * s, y + 22f * s + 17f * s, 34f * s, fg);
            resetTp();
            tp.setTypeface(bold); tp.setTextSize(Math.min(24f * s, ch * 0.13f)); tp.setColor(fg);
            float ty = y + ch - 22f * s - 22f * s;
            c.drawText(ellipsize(cd.name, cw - 44f * s), x + 22f * s, ty, tp);
            tp.setTypeface(regular); tp.setTextSize(14f * s); tp.setColor((fg & 0x00FFFFFF) | 0xC0000000);
            c.drawText(ellipsize(cd.sub, cw - 44f * s), x + 22f * s, ty + 22f * s, tp);
            if (f && !games && !reo && cd.theme >= 0) {                   // small reminder that a long press edits this card
                tp.setTypeface(bold); tp.setTextSize(13f * s);
                String hint = T.t("maintenir OK : modifier");
                float hw = tp.measureText(hint) + 22f * s, hh = 26f * s;
                rf.set(x + cw - 14f * s - hw, y + 14f * s, x + cw - 14f * s, y + 14f * s + hh);
                fill.setShader(null); fill.setColor(0xB3000000);
                c.drawRoundRect(rf, hh / 2f, hh / 2f, fill);
                tp.setColor(0xFFFFFFFF);
                c.drawText(hint, rf.left + 11f * s, rf.top + hh * 0.69f, tp);
            }
        }
        resetTp();
        tp.setTypeface(regular); tp.setTextSize(17f * s); tp.setColor(0xB3FFFFFF);
        if (zone == Z_GRID && !games && !reo) {                          // the way to edit a card must be easy to find
            tp.setTextSize(19f * s); tp.setColor(0xE6FFFFFF);
            c.drawText(T.t("OK : lancer   ·   maintenir OK : modifier"), px + pad, H - 26f * s, tp);
        } else {
            c.drawText(reo ? (grab ? T.t("▲▼ ◀ ▶ déplacer   OK poser   Retour") : T.t("▲▼ ◀ ▶ choisir   OK saisir   Retour : terminer"))
                    : zone == Z_GRID ? T.t("▲▼ ◀ ▶ se déplacer   OK lancer   maintenir OK : modifier   Retour") : "▶" + T.t(" ou OK pour entrer"),
                    px + pad, H - 26f * s, tp);
        }
    }

    private void drawPanel(Canvas c, int W, int H, float s, float rx, float rw) {
        float pw = 480f * s;
        float px = rx + rw;
        fill.setColor((panelAlpha << 24) | 0x160E12);
        c.drawRect(px, 0, px + pw, H, fill);
        fill.setColor(0x14FFFFFF);
        c.drawRect(px, 0, px + 1, H, fill);
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
        String gt = railIdx == SEC_GAMES ? host.gameTitle() : null;
        c.drawText(gt != null ? gt : T.t(SEC_NAMES[railIdx]), px + pad, 56f * s + 26f * s, tp);

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
        tp.setTypeface(regular); tp.setTextSize(17f * s); tp.setColor(0xB3FFFFFF);
        String hint = zone == Z_PANEL ? T.t("▲▼ ◀ ▶ se déplacer   OK choisir   Retour") : "▶" + T.t(" ou OK pour entrer");
        c.drawText(hint, px + pad, H - 26f * s, tp);
    }

    private float rowHeight(Row r, float cw, float s) {
        switch (r.kind) {
            case ROW_CHIPS: if (r.cards) return 130f * s; return 14f * s + 12f * s + chipLines(r, cw, s) * (40f * s) + (chipLines(r, cw, s) - 1) * 8f * s;
            case ROW_TOGGLE: return 46f * s;
            case ROW_SLIDER: return 70f * s;
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

    /** Rounded plate behind a control; the focused one gets the gold ring. */
    private void plate(Canvas c, float x, float y, float cw, float h, float s, boolean focused) {
        rf.set(x - 14f * s, y - 6f * s, x + cw + 14f * s, y + h + 6f * s);
        fill.setColor(0x14FFFFFF);
        c.drawRoundRect(rf, 14f * s, 14f * s, fill);
        if (focused) {
            stroke.setColor(FOCUS); stroke.setStrokeWidth(3f * s);
            c.drawRoundRect(rf, 14f * s, 14f * s, stroke);
        }
    }

    /** Green round tick, or an empty circle, centred on (cx, cy). */
    private void tick(Canvas c, boolean on, float cx, float cy, float r, float s) {
        if (on) {
            fill.setColor(OK_GREEN);
            c.drawCircle(cx, cy, r, fill);
            stroke.setColor(0xFF0B2A17); stroke.setStrokeWidth(2.4f * s);
            Path p = new Path();
            p.moveTo(cx - r * 0.45f, cy + r * 0.02f); p.lineTo(cx - r * 0.1f, cy + r * 0.38f); p.lineTo(cx + r * 0.5f, cy - r * 0.34f);
            c.drawPath(p, stroke);
        } else {
            stroke.setColor(0x73FFFFFF); stroke.setStrokeWidth(2f * s);
            c.drawCircle(cx, cy, r, stroke);
        }
    }

    /** Strip of equal preview cards for a chips row (the display style picker). */
    private void drawCardStrip(Canvas c, Row r, float x, float y, float cw, float s, boolean focused) {
        resetTp();
        tp.setTypeface(medium); tp.setTextSize(14f * s); tp.setLetterSpacing(0.1f);
        tp.setColor(focused ? 0xFFFFFFFF : 0x99FFFFFF);
        c.drawText(r.label.toUpperCase(T.locale()), x, y + 12f * s, tp);
        tp.setLetterSpacing(0f);
        int n = r.options.length;
        float gap = 10f * s, w = (cw - (n - 1) * gap) / n, top = y + 26f * s, h = 92f * s;
        int cursor = chipCursor < 0 ? r.selected : chipCursor;
        for (int i = 0; i < n; i++) {
            float cx = x + i * (w + gap);
            boolean sel = i == r.selected;
            rf.set(cx, top, cx + w, top + h);
            fill.setColor(sel ? 0x2EFFFFFF : 0x1AFFFFFF);
            c.drawRoundRect(rf, 12f * s, 12f * s, fill);
            if (focused && i == cursor) {
                stroke.setColor(FOCUS); stroke.setStrokeWidth(3f * s);
                c.drawRoundRect(rf, 12f * s, 12f * s, stroke);
            }
            modePreview(c, modeIdOf(r.options[i], i), cx + w * 0.18f, top + 10f * s, w * 0.64f, 44f * s, s);
            resetTp();
            tp.setTypeface(sel ? bold : regular); tp.setTextSize(13f * s); tp.setColor(0xFFFFFFFF);
            tp.setTextAlign(Paint.Align.CENTER);
            c.drawText(ellipsize(r.options[i], w - 8f * s), cx + w / 2f, top + h - 12f * s, tp);
            tp.setTextAlign(Paint.Align.LEFT);
            if (sel) tick(c, true, cx + w - 12f * s, top + 12f * s, 8f * s, s);
        }
    }

    /** Style id behind a picker card, found by its name (the picker order differs from the ids). */
    private static int modeIdOf(String name, int fallback) {
        for (int k = 0; k < NowPlayingView.MODE_NAMES.length; k++) if (T.t(NowPlayingView.MODE_NAMES[k]).equals(name)) return k;
        return fallback;
    }

    /** Tiny flat sketch of each display style (0 classic, 1 illustrated, 2 lyrics, 3 wallpaper only, 4 clean). */
    private void modePreview(Canvas c, int mode, float x, float y, float w, float h, float s) {
        fill.setColor(0x26FFFFFF);
        switch (mode) {
            case 0:
                rf.set(x, y + h * 0.1f, x + h * 0.8f, y + h * 0.9f); c.drawRoundRect(rf, 3f * s, 3f * s, fill);
                fill.setColor(0x80FFFFFF);
                c.drawRect(x + h * 0.95f, y + h * 0.3f, x + w, y + h * 0.4f, fill);
                c.drawRect(x + h * 0.95f, y + h * 0.55f, x + w * 0.85f, y + h * 0.63f, fill);
                break;
            case 1:
                fill.setColor(0x80FFFFFF);
                c.drawCircle(x + w / 2f, y + h / 2f, h * 0.45f, fill);
                fill.setColor(0x26000000);
                c.drawCircle(x + w / 2f, y + h / 2f, h * 0.12f, fill);
                break;
            case 2:
                fill.setColor(0x59FFFFFF);
                c.drawRect(x, y + h * 0.1f, x + w, y + h * 0.2f, fill);
                c.drawRect(x, y + h * 0.7f, x + w * 0.8f, y + h * 0.8f, fill);
                fill.setColor(OK_GREEN);
                c.drawRect(x, y + h * 0.4f, x + w * 0.9f, y + h * 0.52f, fill);
                break;
            case 4:
                rf.set(x + w / 2f - h * 0.4f, y + h * 0.1f, x + w / 2f + h * 0.4f, y + h * 0.9f); c.drawRoundRect(rf, 3f * s, 3f * s, fill);
                break;
            default:
                fill.setShader(new LinearGradient(x, y, x + w, y + h, 0x66F2C66B, 0x666B7BD1, Shader.TileMode.CLAMP));
                rf.set(x, y, x + w, y + h); c.drawRoundRect(rf, 5f * s, 5f * s, fill);
                fill.setShader(null);
        }
    }

    private void drawRow(Canvas c, Row r, float x, float y, float cw, float h, float s, boolean focused) {
        if (r.kind != ROW_TEXT && r.kind != ROW_BUTTON && !(r.kind == ROW_CHIPS && r.cards)) plate(c, x, y, cw, h, s, focused);
        if (r.kind == ROW_CHIPS && r.cards) { drawCardStrip(c, r, x, y, cw, s, focused); return; }
        if (r.kind == ROW_SLIDER) {
            resetTp();
            tp.setTypeface(medium); tp.setTextSize(14f * s); tp.setLetterSpacing(0.1f);
            tp.setColor(focused ? 0xFFFFFFFF : 0x99FFFFFF);
            c.drawText(r.label.toUpperCase(T.locale()), x, y + 12f * s, tp);
            tp.setLetterSpacing(0f);
            float tx0 = x, tx1 = x + cw - 80f * s, ty = y + 44f * s, th = 6f * s;
            float frac = r.max > 0 ? r.selected / (float) r.max : 0f;
            rf.set(tx0, ty - th / 2f, tx1, ty + th / 2f);
            fill.setColor(0x33FFFFFF); c.drawRoundRect(rf, th / 2f, th / 2f, fill);
            rf.set(tx0, ty - th / 2f, tx0 + (tx1 - tx0) * frac, ty + th / 2f);
            fill.setColor(0xFFFFFFFF); c.drawRoundRect(rf, th / 2f, th / 2f, fill);
            float kx = tx0 + (tx1 - tx0) * frac;
            fill.setColor(0xFFFFFFFF); c.drawCircle(kx, ty, 11f * s, fill);
            if (focused) { stroke.setColor(FOCUS); stroke.setStrokeWidth(3f * s); c.drawCircle(kx, ty, 15f * s, stroke); }
            tp.setTypeface(medium); tp.setTextSize(16f * s); tp.setColor(0xFFFFFFFF); tp.setTextAlign(Paint.Align.RIGHT);
            c.drawText((r.max > 0 ? r.selected * 100 / r.max : 0) + " %", x + cw, ty + 6f * s, tp);
            tp.setTextAlign(Paint.Align.LEFT);
            return;
        }
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
                fill.setColor(sel ? 0xFFFFFFFF : 0x1AFFFFFF);
                c.drawRoundRect(rf, rf.height() / 2, rf.height() / 2, fill);
                if (ring) {
                    stroke.setColor(FOCUS); stroke.setStrokeWidth(3f * s);
                    rf.inset(-3f * s, -3f * s);
                    c.drawRoundRect(rf, rf.height() / 2, rf.height() / 2, stroke);
                    rf.inset(3f * s, 3f * s);
                }
                tp.setColor(sel ? 0xFF170D12 : 0xFFFFFFFF);
                c.drawText(r.options[i], cx + 16f * s, cy + 25.5f * s, tp);
                cx += w + 8f * s;
            }
            return;
        }
        if (r.kind == ROW_BUTTON) {
            float t = 6f * s;
            rf.set(x - 14f * s, y + t, x + cw + 14f * s, y + h - t);
            fill.setColor(0xFF8E4435);
            c.drawRoundRect(rf, 14f * s, 14f * s, fill);
            if (focused) {
                stroke.setColor(FOCUS); stroke.setStrokeWidth(3f * s);
                c.drawRoundRect(rf, 14f * s, 14f * s, stroke);
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
        resetTp();
        tp.setTypeface(focused ? medium : regular); tp.setTextSize(17f * s); tp.setColor(0xFFFFFFFF);
        float ty = y + h / 2f + 6f * s;
        if (r.kind == ROW_CHECK) {
            c.drawText(ellipsize(r.label, cw - 50f * s), x, ty, tp);
            tick(c, r.on, x + cw - 11f * s, y + h / 2f, 11f * s, s);
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

    /**
     * Far right of the bottom bar: the room / Spotify marks and the alarm clock. Everything sits on the line of the
     * underline of the "up next" titles (ulY), so the bar reads as one row.
     */
    private void rightColumn(Canvas c, float rEdge, float marksR, float top, float ph, float s, float ulY) {
        int a = (int) (255 * stripA);
        String rlab = host.roomsLabel();
        boolean hasRooms = rlab != null && rlab.length() > 0;
        float mt = top + 24f * s;                                                      // first mark on the baseline of the elapsed / remaining times
        roomsMark(c, marksR, mt, s, true, a);
        spotifyMark(c, marksR, hasRooms ? mt + 20f * s : mt, s, true, a);
        boolean f = visible && zone == Z_TRANSPORT && tIdx == 5, run = KitchenTimer.running();
        float ax = rEdge - 12f * s;
        drawAlarm(c, ax, ulY - (run ? 34f : 21f) * s, s, al(run ? 0xFF5DCAA5 : f ? 0xFFFFFFFF : 0x99FFFFFF));
        if (run) {
            resetTp();
            tp.setTypeface(medium); tp.setTextSize(11f * s); tp.setColor(al(0xFF5DCAA5)); tp.setTextAlign(Paint.Align.RIGHT);
            c.drawText(KitchenTimer.format(KitchenTimer.remaining()), rEdge, ulY - 8f * s, tp);
            tp.setTextAlign(Paint.Align.LEFT);
            postInvalidateDelayed(500);
        }
        if (f) { fill.setColor(al(ACCENT)); c.drawRect(ax - 16f * s, ulY, ax + 16f * s, ulY + 3f * s, fill); }
    }

    private void drawTransport(Canvas c, int W, int H, float s) {
        List<SonosClient.Item> q = host.upNext();
        int n = Math.min(3, q.size());
        long dur = host.durationMs();
        float ph = stripHeight(s), top = H - ph;
        float x0 = 120f * s;
        fill.setShader(null);
        fill.setColor(al((stripBgAlpha << 24) | 0x0A0507));
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
        // The fewer titles are queued, the more room the controls get (and when none, they sit in the middle).
        final float k = n >= 3 ? 1f : n == 2 ? 1.15f : n == 1 ? 1.35f : 1.5f;
        float shift = 0f;
        if (n == 0) shift = W / 2f - (44f * s + x0 + (188f + 152f) * k * s) / 2f + 22f * s;
        // transport icons
        c.save();
        c.translate(shift, 0f);
        float[] cxs = {x0 + 16f * k * s, x0 + 80f * k * s, x0 + 144f * k * s};
        for (int i = 0; i < 3; i++) {
            boolean f = visible && zone == Z_TRANSPORT && i == tIdx;
            int kind = i == 0 ? 10 : i == 2 ? 12 : (host.isPlaying() ? 11 : 0);
            icons.icon(c, kind, cxs[i], rowCy, (i == 1 ? 30f : 26f) * k * s, al(f ? 0xFFFFFFFF : 0xCCFFFFFF));
            if (f) { fill.setColor(al(ACCENT)); c.drawRect(cxs[i] - 16f * k * s, rowCy + 20f * k * s, cxs[i] + 16f * k * s, rowCy + 23f * k * s, fill); }
        }
        c.restore();
        final float rEdge = W - x0 + 60f * s;                 // right edge shared by the end time, the alarm clock and its time
        final float marksR = rEdge - 52f * s;
        {   // info: small square speech bubble with an "i", bottom left
            boolean f = visible && zone == Z_TRANSPORT && tIdx == 4;
            c.save();
            c.translate(shift, 0f);
            float ix = 58f * s, h = 12f * s;
            c.scale(k, k, ix, rowCy);
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
            c.restore();
        }
        float volEnd;
        {   // volume: speaker, 10 flat squares, number
            boolean f = visible && zone == Z_TRANSPORT && tIdx == 3;
            int v = host.volume();
            boolean mute = host.muted();
            float vx = x0 + 188f * k * s;
            c.save();
            c.translate(shift, 0f);
            c.scale(k, k, vx, rowCy);
            int col = mute ? al(ACCENT) : al(f ? 0xFFFFFFFF : 0xCCFFFFFF);
            icons.icon(c, 1, vx + 11f * s, rowCy, 22f * s, col);
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
            c.restore();
            volEnd = vx + (volEnd - vx) * k;
        }
        if (n == 0) { qShown = 0; rightColumn(c, rEdge, marksR, top, ph, s, rowCy + 20f * k * s); return; }
        float dx = volEnd + 24f * s;
        fill.setColor(al(0x26FFFFFF));
        c.drawRect(dx, rowCy - 20f * s, dx + 1f * s, rowCy + 20f * s, fill);
        float qx = dx + 28f * s, iw = 272f * s, ta = 44f * s;
        float mwMax = 0;
        String rl = host.roomsLabel();
        if (rl != null && rl.length() > 0) mwMax = Math.max(mwMax, markWidth(rl.toUpperCase(T.locale()), s));
        if (host.spotifyState() != 0) mwMax = Math.max(mwMax, markWidth("SPOTIFY", s));
        float markLeft = marksR - mwMax;
        // As many titles as fit left of the room / Spotify marks, sharing the free width (3 when there is room).
        float avail = markLeft - 24f * s - qx;
        int shown = 0;
        for (int cnt = n; cnt >= 1; cnt--) {
            float w = Math.min((cnt == 1 ? 440f : 300f) * s, avail / cnt);
            if (w - ta - 36f * s >= 110f * s) { shown = cnt; iw = w; break; }
        }
        n = shown;
        qShown = n;
        if (n == 0) { rightColumn(c, rEdge, marksR, top, ph, s, rowCy + 27f * s); return; }
        final float big = n == 1 ? 1.35f : n == 2 ? 1.12f : 1f;      // a lone title gets a bigger cover and bigger text
        rightColumn(c, rEdge, marksR, top, ph, s, rowCy + 27f * big * s);
        ta *= big;
        resetTp();
        tp.setTypeface(medium); tp.setTextSize(11f * s); tp.setLetterSpacing(0.12f); tp.setColor(al(0x8CFFFFFF));
        c.drawText(n == 1 ? T.t("ENSUITE") : T.t("À SUIVRE"), qx, rowCy - 30f * s - (big - 1f) * 20f * s, tp);
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
            tp.setTypeface(f ? bold : medium); tp.setTextSize(15f * big * s); tp.setColor(al(0xFFFFFFFF));
            c.drawText(ellipsize(it.title, mw), tx, rowCy - 3f * big * s, tp);
            tp.setTypeface(regular); tp.setTextSize(12f * big * s); tp.setColor(al(0x99FFFFFF));
            c.drawText(ellipsize(it.artist, mw), tx, rowCy + 15f * big * s, tp);
            if (f) { fill.setColor(al(ACCENT)); c.drawRect(ix, rowCy + 27f * big * s, ix + iw - 24f * s, rowCy + 30f * big * s, fill); }
        }
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
