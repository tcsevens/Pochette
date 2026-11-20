package app.pochette.tv.media;

import app.pochette.tv.sonos.SonosClient;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Short biographies from Wikipedia (free, no key): the song first, then the artist, in the app language with
 * English as a fallback. A page is only accepted when its title matches and (for a song) its text names the
 * artist, so a title like "Hello" never shows an unrelated page.
 */
public final class Bio {
    /** One accepted page. */
    public static final class Page {
        public String title = "", text = "", lang = "";
        public boolean fallback;       // not in the wanted language
    }

    /** What was found for a track; either part can be null. */
    public static final class Result {
        public Page song, artist;
        public boolean any() { return song != null || artist != null; }
    }

    private static final Map<String, Result> CACHE = new HashMap<String, Result>();
    private static final String[] CODES = {"fr", "en", "es"};
    private static final Pattern MUSIC = Pattern.compile(
            "groupe|chanteu|chanteuse|musici|auteur-compositeur|rappeu|rock|band|singer|musician|rapper|songwriter|"
                    + "duo|trio|compositor|cantante|banda|músic|grupo|dj\\b|music|musique|música|orchestr|ensemble");

    private Bio() {}

    /** Blocking; call from a background thread. */
    public static Result fetch(String title, String artist, int lang) {
        String key = lang + "|" + norm(artist) + "|" + norm(title);
        synchronized (CACHE) {
            Result r = CACHE.get(key);
            if (r != null) return r;
        }
        Result r = new Result();
        String main = primaryArtist(artist);
        String song = cleanTitle(title);
        String[] order = lang == 1 ? new String[]{CODES[1]} : new String[]{CODES[lang], CODES[1]};
        for (int i = 0; i < order.length && (r.song == null || r.artist == null); i++) {
            String l = order[i];
            boolean fb = i > 0;
            if (r.song == null && song.length() > 0 && main.length() > 0) {
                Page p = findSong(l, song, main);
                if (p != null) { p.fallback = fb; r.song = p; }
            }
            if (r.artist == null && main.length() > 0) {
                Page p = findArtist(l, main);
                if (p != null) { p.fallback = fb; r.artist = p; }
            }
        }
        // Only cache real answers, so a network failure is retried next time.
        if (r.any()) {
            synchronized (CACHE) { CACHE.put(key, r); }
        }
        return r;
    }

    private static Page findSong(String l, String song, String artist) {
        List<Page> cands = search(l, song + " " + artist);
        String want = norm(song), who = norm(artist);
        if (want.length() == 0 || who.length() == 0) return null;
        for (Page p : cands) {
            if (!norm(stripParen(p.title)).equals(want)) continue;
            if (!norm(p.text).contains(who)) continue;
            return p;
        }
        return null;
    }

    private static Page findArtist(String l, String artist) {
        List<Page> cands = search(l, artist);
        String want = stripThe(norm(artist));
        if (want.length() == 0) return null;
        for (Page p : cands) {
            if (!stripThe(norm(stripParen(p.title))).equals(want)) continue;
            String head = p.text.length() > 400 ? p.text.substring(0, 400) : p.text;
            if (!MUSIC.matcher(head.toLowerCase()).find()) continue;
            return p;
        }
        return null;
    }

    /** Search results with their intro text, best match first. */
    private static List<Page> search(String l, String q) {
        List<Page> out = new ArrayList<Page>();
        try {
            String url = "https://" + l + ".wikipedia.org/w/api.php?action=query&format=json&generator=search"
                    + "&gsrlimit=6&gsrsearch=" + URLEncoder.encode(q, "UTF-8")
                    + "&prop=extracts&exintro=1&explaintext=1&exlimit=max&exchars=1200&redirects=1";
            String body = get(url);
            if (body == null) return out;
            parse(body, l, out);
        } catch (Exception ignored) {}
        return out;
    }

    /** Parses a query response into pages sorted by search rank (separate for tests). */
    static void parse(String body, String lang, List<Page> out) throws Exception {
        JSONObject root = new JSONObject(body);
        JSONObject q = root.optJSONObject("query");
        if (q == null) return;
        JSONObject pages = q.optJSONObject("pages");
        if (pages == null) return;
        List<int[]> rank = new ArrayList<int[]>();
        List<Page> ps = new ArrayList<Page>();
        Iterator<String> it = pages.keys();
        while (it.hasNext()) {
            JSONObject o = pages.optJSONObject(it.next());
            if (o == null) continue;
            String ex = o.optString("extract", "").trim();
            if (ex.length() == 0) continue;
            Page p = new Page();
            p.title = o.optString("title", "");
            p.text = ex.replaceAll("\\s*\\n\\s*", "\n");
            p.lang = lang;
            rank.add(new int[]{o.optInt("index", 99), ps.size()});
            ps.add(p);
        }
        java.util.Collections.sort(rank, new java.util.Comparator<int[]>() {
            @Override public int compare(int[] a, int[] b) { return a[0] - b[0]; }
        });
        for (int[] r : rank) out.add(ps.get(r[1]));
    }

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

    // ------------------------------------------------------------------ text helpers

    static String primaryArtist(String a) {
        if (a == null) return "";
        return a.split("(?i)\\s+(feat\\.?|ft\\.|featuring|&|x|and|et)\\s+|,|;")[0].trim();
    }

    static String cleanTitle(String t) {
        if (t == null) return "";
        t = t.replaceAll("\\s*[\\(\\[].*?[\\)\\]]", "");
        t = t.replaceAll("\\s+-\\s+.*$", "");
        return t.trim();
    }

    private static String stripParen(String t) { return t.replaceAll("\\s*\\(.*?\\)\\s*$", "").trim(); }

    private static String stripThe(String n) {
        return n.startsWith("the") && n.length() > 5 ? n.substring(3) : n;
    }

    /** Lowercase, accents and punctuation removed. */
    static String norm(String s) {
        if (s == null) return "";
        String d = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return d.toLowerCase().replaceAll("[^a-z0-9]+", "");
    }
}
