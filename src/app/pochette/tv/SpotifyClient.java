package app.pochette.tv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional Spotify Web API access. Nothing is built in: each user brings their own Client ID (a free
 * developer app on their Premium account) and signs in with PKCE, so no secret and no shared key exist.
 */
public final class SpotifyClient {

    public static final String REDIRECT = "http://127.0.0.1:8888/callback";
    public static final String SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing";

    // overridable for tests
    static String API = "https://api.spotify.com/v1";
    static String ACCOUNTS = "https://accounts.spotify.com";

    private final Prefs prefs;

    public SpotifyClient(Prefs prefs) { this.prefs = prefs; }

    public boolean configured() { return prefs.spClientId().length() > 0; }
    public boolean connected() { return configured() && prefs.spRefresh().length() > 0; }
    /** Connected and switched on by the user. */
    public boolean enabled() { return prefs.spotifyOn() && connected(); }

    // ------------------------------------------------------------------ PKCE

    private static final char[] B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

    /** Base64 URL-safe without padding (RFC 4648 section 5). */
    static String b64url(byte[] d) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (; i + 2 < d.length; i += 3) {
            int n = ((d[i] & 0xFF) << 16) | ((d[i + 1] & 0xFF) << 8) | (d[i + 2] & 0xFF);
            sb.append(B64[n >> 18 & 63]).append(B64[n >> 12 & 63]).append(B64[n >> 6 & 63]).append(B64[n & 63]);
        }
        int rest = d.length - i;
        if (rest == 1) {
            int n = (d[i] & 0xFF) << 16;
            sb.append(B64[n >> 18 & 63]).append(B64[n >> 12 & 63]);
        } else if (rest == 2) {
            int n = ((d[i] & 0xFF) << 16) | ((d[i + 1] & 0xFF) << 8);
            sb.append(B64[n >> 18 & 63]).append(B64[n >> 12 & 63]).append(B64[n >> 6 & 63]);
        }
        return sb.toString();
    }

    static String challenge(String verifier) throws Exception {
        return b64url(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes("US-ASCII")));
    }

    private static String random(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return b64url(b);
    }

    /** Address the user opens (on their phone) to sign in. A new verifier is generated each time. */
    public String authUrl() throws Exception {
        String ver = random(48), state = random(12);
        prefs.spPending(ver, state);
        return ACCOUNTS + "/authorize?response_type=code&client_id=" + enc(prefs.spClientId())
                + "&redirect_uri=" + enc(REDIRECT) + "&code_challenge_method=S256&code_challenge=" + challenge(ver)
                + "&state=" + enc(state) + "&scope=" + enc(SCOPES);
    }

    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    private static String param(String url, String name) throws Exception {
        int q = url.indexOf('?');
        String qs = q >= 0 ? url.substring(q + 1) : url;
        int h = qs.indexOf('#');
        if (h >= 0) qs = qs.substring(0, h);
        for (String kv : qs.split("&")) {
            int e = kv.indexOf('=');
            if (e > 0 && kv.substring(0, e).equals(name)) return URLDecoder.decode(kv.substring(e + 1), "UTF-8");
        }
        return null;
    }

    /** Finishes the sign-in from the address the browser ended on (…/callback?code=…&state=…). */
    public void completeSignIn(String pasted) throws Exception {
        pasted = pasted == null ? "" : pasted.trim();
        String err = param(pasted, "error");
        if (err != null) throw new Exception(T.t("Spotify a refusé la connexion : ") + err);
        String code = param(pasted, "code");
        if (code == null) throw new Exception(T.t("Cette adresse ne contient pas de code Spotify. Copie l'adresse complète de la page qui s'est affichée après la connexion."));
        String state = param(pasted, "state");
        if (state != null && prefs.spState().length() > 0 && !state.equals(prefs.spState()))
            throw new Exception(T.t("Cette connexion est périmée. Reprends depuis le bouton « Se connecter à Spotify »."));
        String body = "grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(REDIRECT)
                + "&client_id=" + enc(prefs.spClientId()) + "&code_verifier=" + enc(prefs.spVerifier());
        storeTokens(new JSONObject(postForm(ACCOUNTS + "/api/token", body)));
    }

    private void storeTokens(JSONObject j) throws Exception {
        long exp = System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000L;
        prefs.spTokens(j.getString("access_token"), exp, j.optString("refresh_token", ""));
        health = 1;
    }

    /** 0 = unknown, 1 = last exchange with Spotify worked, 2 = sign-in refused (must reconnect). Network trouble does not count. */
    private volatile int health = 0;
    public int health() { return health; }
    public boolean needsReconnect() { return connected() && health == 2; }

    private synchronized String access() throws Exception {
        if (!connected()) throw new Exception(T.t("Spotify n'est pas connecté"));
        if (prefs.spAccess().length() == 0 || prefs.spExpiry() - 60000 < System.currentTimeMillis()) {
            String body = "grant_type=refresh_token&refresh_token=" + enc(prefs.spRefresh()) + "&client_id=" + enc(prefs.spClientId());
            try {
                storeTokens(new JSONObject(postForm(ACCOUNTS + "/api/token", body)));
            } catch (Exception e) {
                String m = e.getMessage() == null ? "" : e.getMessage();
                if (m.startsWith("Spotify 400") || m.startsWith("Spotify 401")) health = 2;   // refresh token revoked or expired
                throw e;
            }
        }
        return prefs.spAccess();
    }

    // ------------------------------------------------------------------ HTTP

    private static String read(HttpURLConnection c, boolean error) throws Exception {
        InputStream in = error ? c.getErrorStream() : c.getInputStream();
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    private static String postForm(String url, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            OutputStream o = c.getOutputStream();
            o.write(body.getBytes("UTF-8"));
            o.close();
            int code = c.getResponseCode();
            String txt = read(c, code >= 400);
            if (code >= 400) {
                String d = txt;
                try { JSONObject e = new JSONObject(txt); d = e.optString("error_description", e.optString("error", txt)); } catch (Exception ignored) {}
                throw new Exception("Spotify " + code + " : " + d);
            }
            return txt;
        } finally { c.disconnect(); }
    }

    /** Result of an API call: code (200 or 204 when fine) and body. */
    private static final class Res { int code; String body = ""; }

    private Res call(String method, String path, String json) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            String tok = access();
            HttpURLConnection c = (HttpURLConnection) new URL(API + path).openConnection();
            try {
                c.setConnectTimeout(8000);
                c.setReadTimeout(10000);
                c.setRequestMethod(method);
                c.setRequestProperty("Authorization", "Bearer " + tok);
                if (json != null || "PUT".equals(method) || "POST".equals(method)) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    OutputStream o = c.getOutputStream();
                    if (json != null) o.write(json.getBytes("UTF-8"));
                    o.close();
                }
                Res r = new Res();
                r.code = c.getResponseCode();
                r.body = read(c, r.code >= 400);
                if (r.code == 401 && attempt == 0) { prefs.spTokens("", 0, null); continue; }
                if (r.code == 401) health = 2;
                if (r.code < 400) health = 1;
                if (r.code >= 400) {
                    String d = r.body;
                    try { JSONObject e = new JSONObject(r.body).getJSONObject("error"); d = e.optString("message", r.body); } catch (Exception ignored) {}
                    if (r.code == 403) d += " (" + T.t("compte Premium requis, ou Client ID non autorisé") + ")";
                    if (r.code == 404) d = T.t("aucun appareil Spotify actif : lance une musique depuis Spotify vers cette enceinte");
                    throw new Exception("Spotify " + r.code + " : " + d);
                }
                return r;
            } finally { c.disconnect(); }
        }
        throw new Exception("Spotify 401");
    }

    // ------------------------------------------------------------------ playback

    private static String names(JSONArray a) {
        StringBuilder sb = new StringBuilder();
        if (a != null) for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(o.optString("name"));
        }
        return sb.toString();
    }

    static SonosClient.Item toItem(JSONObject t, int nr) {
        SonosClient.Item it = new SonosClient.Item();
        it.kind = 4;
        it.title = t.optString("name");
        it.artist = names(t.optJSONArray("artists"));
        JSONObject al = t.optJSONObject("album");
        if (al != null) {
            it.album = al.optString("name");
            JSONArray im = al.optJSONArray("images");
            String best = null;
            int bw = Integer.MAX_VALUE;
            if (im != null) for (int i = 0; i < im.length(); i++) {
                JSONObject o = im.optJSONObject(i);
                if (o == null) continue;
                int w = o.optInt("width", 0);
                if (w >= 100 && w < bw) { bw = w; best = o.optString("url"); }
                else if (best == null) best = o.optString("url");
            }
            it.artUrl = best;
        }
        it.uri = t.optString("uri");
        it.trackNr = nr;
        return it;
    }

    /** The next tracks of the Spotify queue (what "Up next" shows when Spotify Connect plays). */
    public List<SonosClient.Item> queue(int max) throws Exception {
        Res r = call("GET", "/me/player/queue", null);
        android.util.Log.i("Pochette", "Spotify /queue http " + r.code + " len=" + r.body.length()
            + (r.body.length() > 0 ? " " + r.body.substring(0, Math.min(160, r.body.length())) : ""));
        List<SonosClient.Item> out = new ArrayList<SonosClient.Item>();
        if (r.code == 204 || r.body.length() == 0) return out;
        JSONArray q = new JSONObject(r.body).optJSONArray("queue");
        if (q != null) for (int i = 0; i < q.length() && out.size() < max; i++) {
            JSONObject t = q.optJSONObject(i);
            if (t != null) out.add(toItem(t, i + 1));
        }
        return out;
    }

    /** Track search for the shared queue page. */
    public List<SonosClient.Item> search(String q, int max) throws Exception {
        Res r = call("GET", "/search?type=track&limit=" + max + "&q=" + enc(q), null);
        List<SonosClient.Item> out = new ArrayList<SonosClient.Item>();
        JSONObject tr = new JSONObject(r.body).optJSONObject("tracks");
        JSONArray a = tr == null ? null : tr.optJSONArray("items");
        if (a != null) for (int i = 0; i < a.length() && out.size() < max; i++) {
            JSONObject t = a.optJSONObject(i);
            if (t != null) out.add(toItem(t, i + 1));
        }
        return out;
    }

    /** Adds a track at the end of the Spotify queue of the active device. */
    /** Name of the Sonos room being shown (set by the poller), so the queue add targets it. */
    public volatile String room = "";

    public void addToQueue(String uri) throws Exception {
        // No device_id on purpose: naming the Sonos device made Spotify transfer playback and cut the sound.
        call("POST", "/me/player/queue?uri=" + enc(uri), null);
    }

    public static final class Device {
        public String id, name;
        public boolean active;
    }

    public List<Device> devices() throws Exception {
        Res r = call("GET", "/me/player/devices", null);
        List<Device> out = new ArrayList<Device>();
        JSONArray a = new JSONObject(r.body).optJSONArray("devices");
        if (a != null) for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Device d = new Device();
            d.id = o.optString("id");
            d.name = o.optString("name");
            d.active = o.optBoolean("is_active");
            out.add(d);
        }
        return out;
    }

    static Device pickDevice(List<Device> l, String room) {
        String r = room == null ? "" : room.trim().toLowerCase();
        if (r.length() > 0) {
            for (Device d : l) if (d.name.trim().toLowerCase().equals(r)) return d;
            for (Device d : l) if (d.name.toLowerCase().contains(r) || r.contains(d.name.trim().toLowerCase())) return d;
        }
        for (Device d : l) if (d.active) return d;
        return l.isEmpty() ? null : l.get(0);
    }

    /** Resume playback on the Sonos room, starting the Spotify session there if needed. */
    public void resume(String roomName) throws Exception {
        Device d = pickDevice(devices(), roomName);
        if (d == null || d.id.length() == 0) throw new Exception(T.t("aucun appareil Spotify trouvé : ouvre Spotify et choisis cette enceinte une fois"));
        try {
            call("PUT", "/me/player/play?device_id=" + enc(d.id), null);
        } catch (Exception e) {
            call("PUT", "/me/player", "{\"device_ids\":[\"" + d.id + "\"],\"play\":true}");
        }
    }

    /** Pause the Spotify Connect session itself (a UPnP pause/play breaks the Connect stream on the speaker). */
    public void pause() throws Exception {
        call("PUT", "/me/player/pause", null);
    }

    public void next(int times) throws Exception {
        for (int i = 0; i < times; i++) {
            call("POST", "/me/player/next", null);
            if (i + 1 < times) try { Thread.sleep(250); } catch (InterruptedException ignored) {}
        }
    }
}
