package app.pochette.tv.media;

import app.pochette.tv.core.TrackState;
import app.pochette.tv.sonos.SonosClient;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Synced lyrics from LRCLIB (free, open database: https://lrclib.net). */
public final class Lyrics {
    public static final class Line {
        public final long timeMs;
        public final String text;
        /** Start of the next line (or a few seconds later for the last one). */
        public long endMs;
        /** Word-level timing from "enhanced LRC" (<mm:ss.xx> tags), when the database has it. */
        int[] wordChars;
        long[] wordTimes;
        Line(long t, String s) { timeMs = t; text = s; }

        public boolean hasWordTiming() { return wordTimes != null && wordTimes.length > 0; }

        /**
         * How many characters of the line have been sung at positionMs (fractional), for the karaoke fill.
         * Uses real word timestamps when available, otherwise spreads the line evenly over an estimated
         * singing duration (about 90 ms per character, never longer than the gap to the next line).
         */
        public float sungChars(long pos) {
            int n = text.length();
            if (n == 0 || pos <= timeMs) return 0f;
            if (hasWordTiming()) {
                int k = -1;
                for (int i = 0; i < wordTimes.length; i++) if (wordTimes[i] <= pos) k = i;
                if (k < 0) return 0f;
                int start = wordChars[k];
                int end = k + 1 < wordChars.length ? wordChars[k + 1] : n;
                long t0 = wordTimes[k];
                long t1 = k + 1 < wordTimes.length ? wordTimes[k + 1] : Math.min(endMs, t0 + Math.max(400, (end - start) * 110L));
                float f = t1 > t0 ? Math.min(1f, (pos - t0) / (float) (t1 - t0)) : 1f;
                return start + f * (end - start);
            }
            long gap = Math.max(300, endMs - timeMs);
            float dur = Math.min(gap * 0.92f, Math.max(1000f, n * 90f));
            return Math.min(1f, (pos - timeMs) / dur) * n;
        }
    }

    public static final class Result {
        public final String key;
        public final List<Line> lines = new ArrayList<Line>();
        public boolean synced;
        public boolean instrumental;
        public boolean notFound;
        Result(String key) { this.key = key; }

        /** Index of the line being sung at positionMs, -1 before the first one. */
        public int indexAt(long positionMs) {
            int idx = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).timeMs <= positionMs + 250) idx = i; else break;
            }
            return idx;
        }
    }

    public interface Callback { void onLyrics(Result r); }

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Result> CACHE = new LinkedHashMap<String, Result>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Result> e) { return size() > 40; }
    };

    public static void fetch(final TrackState st, final Callback cb) {
        final String key = st.trackKey();
        final String title = st.title, artist = st.artist, album = st.album;
        final int duration = st.durationSec;
        synchronized (CACHE) {
            Result cached = CACHE.get(key);
            if (cached != null) { cb.onLyrics(cached); return; }
        }
        EXEC.execute(new Runnable() {
            @Override public void run() {
                final Result r = load(key, title, artist, album, duration);
                synchronized (CACHE) { CACHE.put(key, r); }
                MAIN.post(new Runnable() { @Override public void run() { cb.onLyrics(r); } });
            }
        });
    }

    private static Result load(String key, String title, String artist, String album, int duration) {
        Result r = new Result(key);
        String t = simplify(title);
        if (t.length() == 0 || artist.length() == 0) { r.notFound = true; return r; }
        try {
            JSONObject best = null;
            String q = "track_name=" + enc(t) + "&artist_name=" + enc(primaryArtist(artist));
            if (album.length() > 0) q += "&album_name=" + enc(album);
            if (duration > 0) q += "&duration=" + duration;
            String body = get("https://lrclib.net/api/get?" + q);
            if (body != null) best = new JSONObject(body);
            if (best == null || (isEmpty(best.optString("syncedLyrics")) && !best.optBoolean("instrumental"))) {
                String s = get("https://lrclib.net/api/search?track_name=" + enc(t) + "&artist_name=" + enc(primaryArtist(artist)));
                if (s != null) {
                    JSONArray arr = new JSONArray(s);
                    JSONObject plain = null;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        int d = o.optInt("duration", 0);
                        boolean durOk = duration == 0 || d == 0 || Math.abs(d - duration) <= 4;
                        if (!durOk) continue;
                        if (!isEmpty(o.optString("syncedLyrics"))) { best = o; plain = null; break; }
                        if (plain == null && !isEmpty(o.optString("plainLyrics"))) plain = o;
                    }
                    if (plain != null && (best == null || isEmpty(best.optString("plainLyrics")))) best = plain;
                }
            }
            if (best == null) { r.notFound = true; return r; }
            if (best.optBoolean("instrumental")) { r.instrumental = true; return r; }
            String synced = best.optString("syncedLyrics");
            if (!isEmpty(synced)) {
                parseLrc(synced, r.lines);
                r.synced = !r.lines.isEmpty();
            }
            if (r.lines.isEmpty()) {
                String plain = best.optString("plainLyrics");
                if (!isEmpty(plain)) {
                    String[] ls = plain.split("\n");
                    long total = Math.max(1, duration) * 1000L;
                    for (int i = 0; i < ls.length; i++) {
                        r.lines.add(new Line((long) (total * (i + 0.5) / (ls.length + 1)), ls[i].trim()));
                    }
                    for (int i = 0; i < r.lines.size(); i++) {
                        r.lines.get(i).endMs = i + 1 < r.lines.size() ? r.lines.get(i + 1).timeMs : r.lines.get(i).timeMs + 5000;
                    }
                }
            }
            if (r.lines.isEmpty()) r.notFound = true;
        } catch (Exception e) {
            r.notFound = true;
        }
        return r;
    }

    private static final Pattern WORD = Pattern.compile("<(\\d+):(\\d+)(?:[.:](\\d+))?>");
    private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?\\]");

    static void parseLrc(String lrc, List<Line> out) {
        for (String raw : lrc.split("\n")) {
            Matcher m = LRC.matcher(raw);
            List<Long> times = new ArrayList<Long>();
            int end = 0;
            while (m.find() && m.start() == end) {
                long ms = Long.parseLong(m.group(1)) * 60000L + Long.parseLong(m.group(2)) * 1000L;
                if (m.group(3) != null) {
                    String f = m.group(3);
                    long frac = Long.parseLong(f);
                    if (f.length() == 1) frac *= 100; else if (f.length() == 2) frac *= 10;
                    ms += frac;
                }
                times.add(ms);
                end = m.end();
            }
            if (times.isEmpty()) continue;
            String rest = raw.substring(end);
            // Enhanced LRC: "<00:12.40> word <00:12.90> word" gives the start of each word.
            List<Integer> wc = new ArrayList<Integer>();
            List<Long> wt = new ArrayList<Long>();
            StringBuilder clean = new StringBuilder();
            Matcher w = WORD.matcher(rest);
            int last = 0;
            while (w.find()) {
                appendNormalized(clean, rest.substring(last, w.start()), wc);
                long ms = Long.parseLong(w.group(1)) * 60000L + Long.parseLong(w.group(2)) * 1000L;
                if (w.group(3) != null) {
                    String f = w.group(3);
                    long frac = Long.parseLong(f);
                    if (f.length() == 1) frac *= 100; else if (f.length() == 2) frac *= 10;
                    ms += frac;
                }
                wt.add(ms);
                wc.add(-1); // filled when the word's first character arrives
                last = w.end();
            }
            appendNormalized(clean, rest.substring(last), wc);
            String text = clean.toString().trim();
            for (int i = 0; i < wc.size(); i++) if (wc.get(i) < 0 || wc.get(i) > text.length()) wc.set(i, text.length());
            for (Long t : times) {
                Line ln = new Line(t, text);
                if (!wt.isEmpty()) {
                    ln.wordChars = new int[wt.size()];
                    ln.wordTimes = new long[wt.size()];
                    for (int i = 0; i < wt.size(); i++) { ln.wordChars[i] = wc.get(i); ln.wordTimes[i] = wt.get(i); }
                }
                out.add(ln);
            }
        }
        java.util.Collections.sort(out, new java.util.Comparator<Line>() {
            @Override public int compare(Line a, Line b) { return a.timeMs < b.timeMs ? -1 : (a.timeMs == b.timeMs ? 0 : 1); }
        });
        for (int i = 0; i < out.size(); i++) {
            out.get(i).endMs = i + 1 < out.size() ? out.get(i + 1).timeMs : out.get(i).timeMs + 5000;
        }
    }

    /** Append with collapsed whitespace; resolves pending word starts (-1) to the next visible character. */
    private static void appendNormalized(StringBuilder sb, String seg, List<Integer> wc) {
        for (int i = 0; i < seg.length(); i++) {
            char ch = seg.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') sb.append(' ');
            } else {
                for (int k = 0; k < wc.size(); k++) if (wc.get(k) < 0) wc.set(k, sb.length());
                sb.append(ch);
            }
        }
    }

    /** "Song (Remastered 2011) - Live" -> "Song" */
    static String simplify(String t) {
        String s = t.replaceAll("(?i)\\s*[\\(\\[][^\\)\\]]*(remaster|version|live|edit|mix|mono|stereo|deluxe|feat\\.?|ft\\.)[^\\)\\]]*[\\)\\]]", "");
        s = s.replaceAll("(?i)\\s+-\\s+.*(remaster|version|live|edit|mix|mono|stereo).*$", "");
        return s.trim();
    }

    static String primaryArtist(String a) {
        String s = a.split("(?i)\\s+(feat\\.?|ft\\.|featuring|&|x)\\s+|,")[0];
        return s.trim();
    }

    private static boolean isEmpty(String s) { return s == null || s.length() == 0 || "null".equals(s); }

    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    private static String get(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "Pochette/1.0 (Android TV now-playing screen)");
            if (c.getResponseCode() != 200) return null;
            return SonosClient.readAll(c.getInputStream());
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
