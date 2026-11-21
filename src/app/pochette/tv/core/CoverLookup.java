package app.pochette.tv.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Finds a cover for a track the speaker gives no picture for (Spotify Connect "next track").
 * Only "artist + title" is sent, to the public Deezer search (no account, no key), then to Apple's public
 * iTunes search if Deezer finds nothing. Results are remembered for the session.
 */
public final class CoverLookup {
    private CoverLookup() {}

    private static final Map<String, String> cache = new HashMap<String, String>();

    public static synchronized String find(String artist, String title, String album) {
        String key = artist + "|" + title;
        if (cache.containsKey(key)) return cache.get(key);
        String tt = trimTitle(title), aa = trimTitle(album);
        String url = null, src = "";
        // each attempt is independent: a failure of one service must not stop the next
        for (int i = 0; i < 5 && url == null; i++) {
            try {
                if (i == 0) { url = deezer("artist:\"" + clean(artist) + "\" track:\"" + clean(tt) + "\""); src = "deezer"; }
                else if (i == 1) { url = itunes(clean(artist) + " " + clean(tt), "song"); src = "itunes"; }
                else if (i == 2) { url = deezer(clean(artist) + " " + clean(tt)); src = "deezer-simple"; }
                else if (i == 3 && aa.length() > 0) { url = itunes(clean(artist) + " " + clean(aa), "album"); src = "itunes-album"; }
                else if (i == 4 && aa.length() > 0) { url = deezer(clean(artist) + " " + clean(aa)); src = "deezer-album"; }
            } catch (Exception e) { Journal.w("Cover", e); }
        }
        Journal.i("Cover", (url == null ? "not found" : "found via " + src) + ": " + artist + " | " + title);
        if (cache.size() > 60) cache.clear();
        cache.put(key, url);
        return url;
    }

    /** "Song - Remastered 2011", "Song (Live)" ... -> "Song": those suffixes often hide the right entry. */
    private static String trimTitle(String s) {
        if (s == null) return "";
        String r = s.replaceAll("\\s*[\\(\\[].*?[\\)\\]]", "").replaceAll("\\s+[-\u2013]\\s+.*$", "").trim();
        return r.length() > 0 ? r : s;
    }

    private static String clean(String s) { return s == null ? "" : s.replace("\"", " ").trim(); }

    private static String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(6000);
        c.setRequestProperty("User-Agent", "Pochette/1.0 (Android TV)");
        try {
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) > 0 && b.size() < 300000) b.write(buf, 0, r);
            return b.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    private static String deezer(String q) throws Exception {
        String body = get("https://api.deezer.com/search?limit=1&q=" + URLEncoder.encode(q, "UTF-8"));
        if (body == null) return null;
        JSONArray d = new JSONObject(body).optJSONArray("data");
        JSONObject f = d == null ? null : d.optJSONObject(0);
        JSONObject al = f == null ? null : f.optJSONObject("album");
        String u = al == null ? "" : al.optString("cover_medium");
        return u.length() > 0 ? u : null;
    }

    private static String itunes(String term, String entity) throws Exception {
        String body = get("https://itunes.apple.com/search?entity=" + entity + "&limit=1&term=" + URLEncoder.encode(term, "UTF-8"));
        if (body == null) return null;
        JSONArray d = new JSONObject(body).optJSONArray("results");
        JSONObject f = d == null ? null : d.optJSONObject(0);
        String u = f == null ? "" : f.optString("artworkUrl100");
        return u.length() > 0 ? u.replace("100x100", "300x300") : null;
    }
}
