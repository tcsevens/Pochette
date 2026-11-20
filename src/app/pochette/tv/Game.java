package app.pochette.tv;

import android.graphics.Bitmap;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Party games played on top of a Sonos playlist: blind test, musical chairs, "don't forget the lyrics".
 * Pure logic: GameView draws it, MainActivity feeds it snapshots and keys. Timers use the real clock.
 */
public final class Game {

    public static final int BLIND = 0, CHAIRS = 1, LYRICS = 2, PIXEL = 3;
    public static final String[] NAMES = {"Blind test", "Chaise musicale", "N'oubliez pas les paroles", "Pochette pixellisée"};

    public static final int P_LOAD = 0, P_GUESS = 1, P_REVEAL = 2, P_DANCE = 3, P_STOP = 4,
            P_LYR_PLAY = 5, P_LYR_WAIT = 6, P_LYR_REVEAL = 7, P_PODIUM = 8, P_ERROR = 9, P_PIXEL = 10;
    public static final long PIXEL_MS = 20000;

    public static final String[] TEAM_NAMES = {"Rouge", "Bleue", "Verte", "Jaune", "Violette", "Orange"};
    public static final int[] TEAM_COLORS = {0xFFE0524F, 0xFF3F8DE0, 0xFF3FB87A, 0xFFE8B93A, 0xFF9A6BE0, 0xFFFF8A3A};

    public static final long GUESS_MS = 12000, REVEAL_MS = 15000, TITLE_DELAY_MS = 1000,
            CHAIR_MIN_S = 8, CHAIR_MAX_S = 35, STOP_MS = 6000, LYR_REVEAL_MS = 25000;

    public interface Host {
        void gameFinished();
    }

    private final Poller poller;
    private final Host host;
    private final Random rnd = new Random();

    // configuration
    public int mode, teams = 3, rounds = 20, difficulty, startMode = 1;

    // state
    public int phase = P_LOAD;
    public int round;
    public int[] scores = new int[6];
    public int teamSel;
    public boolean awarded;
    public String album = "";
    public long phaseAt;
    public long phaseDur;
    public String message = "";
    public boolean active;
    public boolean finalPodium;

    // current track
    public String title = "", artist = "";
    public Bitmap art;
    private TrackState track;
    private String prevKey = "";
    private boolean firstRound;
    private long waitAt;
    private int retries, skips;
    private boolean waitingTrack;
    private long cutAtMs;                 // chairs: how long to play
    private long playIssuedAt, playStartMs;
    private boolean audioPending;
    private long lastSnapAt;
    private long lastSnapPos;
    private TrackState lastState;

    // lyrics
    public Lyrics.Result lyr;
    public int keyLine = -1;
    public int hiddenFromChar;
    private long pauseAtMs;
    public int lyricOffset;
    private Lyrics.Result pendingLyr;

    public Game(Poller poller, Host host) { this.poller = poller; this.host = host; }

    public void configure(int mode, int teams, int rounds, int difficulty, int startMode) {
        this.mode = mode;
        this.teams = Math.max(2, Math.min(6, teams));
        this.rounds = rounds;
        this.difficulty = difficulty;
        this.startMode = startMode;
    }

    public boolean scored() { return mode != CHAIRS; }


    public long phaseElapsed() { return SystemClock.elapsedRealtime() - phaseAt; }

    private void setPhase(int p, long dur) { phase = p; phaseAt = SystemClock.elapsedRealtime(); phaseDur = dur; }

    // ------------------------------------------------------------------ lifecycle

    public void start(Ambiance a) {
        active = true;
        finalPodium = false;
        round = 0;
        skips = 0;
        for (int i = 0; i < scores.length; i++) scores[i] = 0;
        teamSel = 0;
        message = T.t("Lancement de la playlist…");
        setPhase(P_LOAD, 0);
        poller.setInterval(400);              // react quickly to the speaker's real state
        poller.launchAmbiance(a, new Poller.Done() {
            @Override public void done(String error) {
                if (!active) return;
                if (error != null) { fail(T.t("Impossible de lancer la playlist : ") + error); return; }
                nextRound(true);
            }
        });
    }

    public void stop() {
        if (!active) return;
        active = false;
        poller.setInterval(1000);
        poller.gamePause();
        host.gameFinished();
    }

    /** The screen went away: forget the game without touching the speakers. */
    public void abort() { active = false; }

    private void fail(String msg) {
        message = msg;
        setPhase(P_ERROR, 0);
        poller.gamePause();
    }

    private void showPodium() {
        setPhase(P_PODIUM, 0);
        poller.gamePause();
    }

    /** ▶ during a countdown: drop this song (already known, already played, failed to load…) without using up a round. */
    private void skipTrack() {
        round--;
        nextRound(false);
    }

    private void nextRound(boolean first) {
        round++;
        if (rounds > 0 && round > rounds) { round = rounds; showPodium(); return; }
        firstRound = first;
        awarded = false;
        teamSel = 0;
        keyLine = -1;
        lyr = null;
        art = null;
        title = ""; artist = "";
        message = mode == CHAIRS ? T.t("On prépare la prochaine musique…") : T.t("Manche ") + round + (rounds > 0 ? T.t(" sur ") + rounds : "");
        setPhase(P_LOAD, 0);
        TrackState cur = lastState;
        prevKey = cur != null && !first ? cur.trackKey() : "";
        poller.gameNext(first);
        waitingTrack = true;
        waitAt = SystemClock.elapsedRealtime();
        retries = 0;
    }

    /** A game needs a track that really changed; move on after a while if the queue has a single song. */
    private void checkTrackReady() {
        TrackState st = lastState;
        long waited = SystemClock.elapsedRealtime() - waitAt;
        if (waited < 1800) return;                      // let the pause / next settle, so the snapshot is fresh
        if (st == null || !st.hasMusic() || st.durationSec <= 0 || st.title.length() == 0) {
            if (waited > 20000) fail(T.t("Aucun morceau lisible dans cette playlist (il faut une playlist ou un album, pas une radio)."));
            return;
        }
        boolean changed = firstRound || !st.trackKey().equals(prevKey);
        if (!changed) {
            if (waited > 9000 && retries < 2) { retries++; waitAt = SystemClock.elapsedRealtime(); poller.gameNext(false); }
            else if (waited > 9000) changed = true;      // same song again (one-track queue): play it anyway
            if (!changed) return;
        }
        if (SystemClock.elapsedRealtime() - lastSnapAt > 2500 && waited < 12000) return;   // wait for a fresh snapshot
        waitingTrack = false;
        onTrackReady(st);
    }

    private void onTrackReady(final TrackState st) {
        track = st;
        final long dur = st.durationSec * 1000L;
        if (mode == BLIND) {
            pendingLyr = null;
            final String key = st.trackKey();
            Lyrics.fetch(st, new Lyrics.Callback() {
                @Override public void onLyrics(Lyrics.Result r) {
                    if (!active || track == null || !key.equals(track.trackKey()) || phase != P_LOAD) return;
                    long start = chorusStart(r, dur);
                    beginPlay(start, st);
                    setPhase(P_GUESS, GUESS_MS);
                }
            });
        } else if (mode == PIXEL) {
            final String key = st.trackKey();
            Lyrics.fetch(st, new Lyrics.Callback() {
                @Override public void onLyrics(Lyrics.Result r) {
                    if (!active || track == null || !key.equals(track.trackKey()) || phase != P_LOAD) return;
                    beginPlay(chorusStart(r, dur), st);
                    setPhase(P_PIXEL, PIXEL_MS);
                }
            });
        } else if (mode == CHAIRS) {
            long cutS = CHAIR_MIN_S + rnd.nextInt((int) (CHAIR_MAX_S - CHAIR_MIN_S + 1));
            long need = (cutS + 4) * 1000;
            long start;
            if (startMode == 0) start = 0;
            else if (startMode == 1) start = (long) (dur * (0.20 + rnd.nextDouble() * 0.35));
            else start = (long) (dur * rnd.nextDouble() * 0.7);
            if (start + need > dur) start = Math.max(0, dur - need);
            long room = (dur - start) / 1000 - 4;
            if (room < CHAIR_MIN_S) { cutS = Math.max(4, room); }
            else cutS = Math.min(cutS, room);
            cutAtMs = Math.max(4, cutS) * 1000;
            beginPlay(start, st);
            setPhase(P_DANCE, cutAtMs);
        } else {
            final String key = st.trackKey();
            Lyrics.fetch(st, new Lyrics.Callback() {
                @Override public void onLyrics(Lyrics.Result r) {
                    if (!active || track == null || !key.equals(track.trackKey()) || phase != P_LOAD) return;
                    if (!pickLine(r, dur)) {
                        skips++;
                        if (skips > 8) { fail(T.t("Pas assez de morceaux avec paroles synchronisées dans cette playlist.")); return; }
                        message = T.t("Pas de paroles pour ce morceau, on passe au suivant…");
                        round--;
                        nextRound(false);
                        return;
                    }
                    skips = 0;
                    lyr = r;
                    long start = Math.max(0, r.lines.get(keyLine).timeMs - 30000);
                    beginPlay(start, st);
                    setPhase(P_LYR_PLAY, 0);
                }
            });
        }
    }

    private void beginPlay(long startMs, TrackState st) {
        title = st.title; artist = st.artist; album = st.album;
        poller.gamePlayAt((int) (startMs / 1000));
        playIssuedAt = SystemClock.elapsedRealtime();
        playStartMs = (startMs / 1000) * 1000;
        audioPending = true;                 // the timers wait until the speaker really plays
        lastSnapPos = -1;
    }

    // ------------------------------------------------------------------ chorus / key line

    private static String norm(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    /** First occurrence of the most repeated line; about 35 % of the song when there are no synced lyrics. */
    static long chorusStart(Lyrics.Result r, long dur) {
        long fallback = (long) (dur * 0.35);
        long best = fallback;
        if (r != null && r.synced && r.lines.size() > 6) {
            Map<String, Integer> count = new HashMap<String, Integer>();
            Map<String, Long> first = new HashMap<String, Long>();
            for (Lyrics.Line l : r.lines) {
                String n = norm(l.text);
                if (n.length() < 12) continue;
                Integer c = count.get(n);
                count.put(n, c == null ? 1 : c + 1);
                if (c == null) first.put(n, l.timeMs);
            }
            int bc = 1;
            for (Lyrics.Line l : r.lines) {
                String n = norm(l.text);
                Integer c = count.get(n);
                if (c != null && c > bc) { bc = c; best = first.get(n); }
            }
            if (bc < 2) best = fallback;
            else best = Math.max(0, best - 800);
        }
        long maxStart = Math.max(0, dur - 28000);
        return Math.max(0, Math.min(best, maxStart));
    }

    private static List<String> words(String s) {
        List<String> w = new ArrayList<String>();
        for (String x : s.trim().split("\\s+")) if (x.length() > 0) w.add(x);
        return w;
    }

    private boolean pickLine(Lyrics.Result r, long dur) {
        if (r == null || !r.synced || r.lines.size() < 6) return false;
        Map<String, Integer> count = new HashMap<String, Integer>();
        for (Lyrics.Line l : r.lines) {
            String n = norm(l.text);
            Integer c = count.get(n);
            count.put(n, c == null ? 1 : c + 1);
        }
        List<Integer> pref = new ArrayList<Integer>(), any = new ArrayList<Integer>();
        for (int i = 2; i < r.lines.size(); i++) {
            Lyrics.Line l = r.lines.get(i);
            int n = words(l.text).size();
            if (n < 5 || n > 13) continue;
            if (l.timeMs < 34000 || l.timeMs > dur - 25000) continue;
            any.add(i);
            Integer c = count.get(norm(l.text));
            if (c != null && c >= 2) pref.add(i);
        }
        List<Integer> pool = !pref.isEmpty() ? pref : any;
        if (pool.isEmpty()) return false;
        keyLine = pool.get(rnd.nextInt(pool.size()));
        Lyrics.Line l = r.lines.get(keyLine);
        List<String> w = words(l.text);
        int hide = difficulty == 0 ? (w.size() >= 7 ? 3 : 2) : Math.max(3, (w.size() + 1) / 2);
        int firstHidden = w.size() - hide;
        int pos = 0, ci = 0;
        for (int i = 0; i < firstHidden; i++) {
            int at = l.text.indexOf(w.get(i), ci);
            ci = at + w.get(i).length();
        }
        int at = l.text.indexOf(w.get(firstHidden), ci);
        hiddenFromChar = Math.max(0, at);
        long t = l.timeMs;
        while (t < l.endMs && l.sungChars(t) < hiddenFromChar) t += 20;
        pauseAtMs = t;
        return true;
    }

    /** The key line split into what stays visible and what is hidden. */
    public String shownPart() {
        if (lyr == null || keyLine < 0) return "";
        return lyr.lines.get(keyLine).text.substring(0, Math.min(hiddenFromChar, lyr.lines.get(keyLine).text.length()));
    }

    public String hiddenPart() {
        if (lyr == null || keyLine < 0) return "";
        String t = lyr.lines.get(keyLine).text;
        return t.substring(Math.min(hiddenFromChar, t.length()));
    }

    // ------------------------------------------------------------------ feeding

    public void onSnapshot(Poller.Snapshot s) {
        if (!active) return;
        lastState = s.state;
        lastSnapAt = SystemClock.elapsedRealtime();
        if (s.state != null) lastSnapPos = s.state.positionMs();
        if (s.art != null && track != null && s.state != null && track.trackKey().equals(s.state.trackKey())) art = s.art;
    }

    /** Called every frame. */
    public void tick() {
        if (!active) return;
        long now = SystemClock.elapsedRealtime();
        if (audioPending && (phase == P_GUESS || phase == P_PIXEL || phase == P_DANCE)) {
            boolean heard = lastState != null && lastState.isPlaying() && lastSnapAt > playIssuedAt + 300
                    && lastState.positionMs() >= playStartMs + 350;
            if (heard || now - playIssuedAt > 9000) audioPending = false;
            phaseAt = now;                   // start (or keep at zero) the countdown from the first audible moment
            if (audioPending) return;
        }
        switch (phase) {
            case P_LOAD:
                if (waitingTrack) checkTrackReady();
                break;
            case P_GUESS:
                if (phaseElapsed() >= GUESS_MS) { setPhase(P_REVEAL, REVEAL_MS); }
                break;
            case P_REVEAL:
                if (phaseElapsed() >= REVEAL_MS) nextRound(false);
                break;
            case P_DANCE:
                if (phaseElapsed() >= cutAtMs) {
                    poller.gamePause();
                    setPhase(P_STOP, STOP_MS);
                }
                break;
            case P_STOP:
                break;                                   // the next music starts on a key press only
            case P_LYR_PLAY:
                if (lyr != null && now - playIssuedAt > 1200 && lastState != null && lastState.isPlaying()) {
                    long pos = lastState.positionMs() - lyricOffset;
                    if (pos >= pauseAtMs - 250) {
                        poller.gamePause();
                        setPhase(P_LYR_WAIT, 0);
                    }
                }
                break;
            case P_LYR_REVEAL:
                if (phaseElapsed() >= LYR_REVEAL_MS) nextRound(false);
                break;
            case P_PIXEL:
                if (phaseElapsed() >= PIXEL_MS) setPhase(P_REVEAL, REVEAL_MS);
                break;
        }
    }

    /** Position used for the karaoke fill (ms, lyrics offset applied). */
    public long lyricPos() {
        if (lastState == null) return 0;
        return lastState.positionMs() - lyricOffset;
    }

    public boolean playing() { return lastState != null && lastState.isPlaying(); }

    // ------------------------------------------------------------------ keys

    private void award(int team) {
        if (awarded || team < 0 || team >= teams) return;
        scores[team]++;
        awarded = true;
        teamSel = team;
    }

    public boolean onKey(int code) {
        boolean ok = code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_ENTER
                || code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean left = code == android.view.KeyEvent.KEYCODE_DPAD_LEFT, right = code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean down = code == android.view.KeyEvent.KEYCODE_DPAD_DOWN;
        boolean skip = code == android.view.KeyEvent.KEYCODE_MEDIA_NEXT || code == android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
        int digit = -1;
        if (code >= android.view.KeyEvent.KEYCODE_1 && code <= android.view.KeyEvent.KEYCODE_6) digit = code - android.view.KeyEvent.KEYCODE_1;
        if (code >= android.view.KeyEvent.KEYCODE_NUMPAD_1 && code <= android.view.KeyEvent.KEYCODE_NUMPAD_6) digit = code - android.view.KeyEvent.KEYCODE_NUMPAD_1;

        if (code == android.view.KeyEvent.KEYCODE_BACK) {
            if (phase == P_PODIUM || phase == P_ERROR || !scored()) { stop(); return true; }
            showPodium();
            return true;
        }
        switch (phase) {
            case P_PODIUM:
                if (ok) stop();
                return true;
            case P_ERROR:
                if (ok) stop();
                return true;
            case P_REVEAL:
            case P_LYR_REVEAL:
                if (left) teamSel = (teamSel + teams - 1) % teams;
                else if (right) teamSel = (teamSel + 1) % teams;
                else if (digit >= 0 && digit < teams) { award(digit); }
                else if (ok) {
                    if (!awarded) award(teamSel); else nextRound(false);
                } else if (down || skip) nextRound(false);
                return true;
            case P_LYR_WAIT:
                if (ok || skip) {
                    setPhase(P_LYR_REVEAL, LYR_REVEAL_MS);
                    poller.gamePlay();
                    awarded = false;
                }
                return true;
            case P_STOP:
                if (ok || skip) nextRound(false);
                return true;
            case P_PIXEL:
                if (right) skipTrack();
                else if (ok || skip) setPhase(P_REVEAL, REVEAL_MS);
                return true;
            case P_GUESS:
                if (right) skipTrack();
                else if (ok || skip) setPhase(P_REVEAL, REVEAL_MS);
                return true;
            case P_DANCE:
                if (right) skipTrack();
                else if (skip) { poller.gamePause(); setPhase(P_STOP, STOP_MS); }
                return true;
            case P_LYR_PLAY:
                if (right) skipTrack();
                return true;
        }
        return true;
    }

    /** Teams sorted by score, best first. */
    public int[] ranking() {
        Integer[] idx = new Integer[teams];
        for (int i = 0; i < teams; i++) idx[i] = i;
        java.util.Arrays.sort(idx, new java.util.Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) { return scores[b] - scores[a] != 0 ? scores[b] - scores[a] : a - b; }
        });
        int[] out = new int[teams];
        for (int i = 0; i < teams; i++) out[i] = idx[i];
        return out;
    }
}
