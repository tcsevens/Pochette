package app.pochette.tv.sonos;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Minimal client for the Sonos local websocket API (wss://speaker:1443/websocket/api).
 * One short connection per command. The speaker's certificate is self-signed, so it is accepted,
 * but only for private LAN addresses. The API key is a fixed placeholder: the local API does not check it.
 */
public final class SonosWs {
    private SonosWs() {}

    private static final String KEY = "5d5b8c4a-1b6e-4e1a-9c2f-0f6a3c9d7a10";
    private static SSLContext ctx;

    private static synchronized SSLContext ctx() throws Exception {
        if (ctx == null) {
            SSLContext c = SSLContext.getInstance("TLS");
            c.init(null, new TrustManager[] { new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            } }, new SecureRandom());
            ctx = c;
        }
        return ctx;
    }

    /** Tests only: lets the app talk to a simulated speaker on this machine. Never set in the app. */
    public static volatile boolean allowLoopback;

    static boolean isLan(String ip) {
        if (ip == null) return false;
        if (allowLoopback && ip.startsWith("127.")) return true;
        if (ip.startsWith("192.168.") || ip.startsWith("10.")) return true;
        if (ip.startsWith("172.")) {
            try { int b = Integer.parseInt(ip.split("\\.")[1]); return b >= 16 && b <= 31; } catch (Exception e) { return false; }
        }
        return false;
    }

    /** Sends one command and returns the reply: [header, body]. */
    public static JSONArray call(String ip, String namespace, String command, String household, String groupId, JSONObject body) throws Exception {
        if (!isLan(ip)) throw new Exception("not a LAN address");
        JSONObject h = new JSONObject();
        h.put("namespace", namespace);
        h.put("command", command);
        if (household != null) h.put("householdId", household);
        if (groupId != null) h.put("groupId", groupId);
        String msg = "[" + h.toString() + "," + (body == null ? "{}" : body.toString()) + "]";

        SSLSocket s = (SSLSocket) ctx().getSocketFactory().createSocket();
        try {
            s.connect(new java.net.InetSocketAddress(ip, 1443), 4000);
            s.setSoTimeout(7000);
            s.startHandshake();
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            byte[] rnd = new byte[16];
            new SecureRandom().nextBytes(rnd);
            String req = "GET /websocket/api HTTP/1.1\r\nHost: " + ip + ":1443\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + b64(rnd) + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: v1.api.smartspeaker.audio\r\nX-Sonos-Api-Key: " + KEY + "\r\n\r\n";
            out.write(req.getBytes("UTF-8"));
            out.flush();
            String head = readHead(in);
            if (!head.startsWith("HTTP/1.1 101")) throw new Exception("websocket refused: " + head.split("\r\n")[0]);
            writeText(out, msg.getBytes("UTF-8"));
            String reply = readText(in, out);
            try { writeClose(out); } catch (Exception ignored) {}
            JSONArray arr = new JSONArray(reply);
            JSONObject hd = arr.getJSONObject(0);
            if (!hd.optBoolean("success", false)) throw new Exception(hd.optString("errorCode", "error") + " " + (arr.length() > 1 ? arr.optJSONObject(1) : ""));
            return arr;
        } finally {
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    private static String b64(byte[] d) {
        String a = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length; i += 3) {
            int n = (d[i] & 255) << 16 | (i + 1 < d.length ? (d[i + 1] & 255) << 8 : 0) | (i + 2 < d.length ? d[i + 2] & 255 : 0);
            sb.append(a.charAt(n >> 18 & 63)).append(a.charAt(n >> 12 & 63));
            sb.append(i + 1 < d.length ? a.charAt(n >> 6 & 63) : '=').append(i + 2 < d.length ? a.charAt(n & 63) : '=');
        }
        return sb.toString();
    }

    private static String readHead(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            sb.append((char) c);
            int n = sb.length();
            if (n >= 4 && sb.charAt(n - 1) == '\n' && sb.charAt(n - 2) == '\r' && sb.charAt(n - 3) == '\n' && sb.charAt(n - 4) == '\r') break;
            if (n > 8192) throw new Exception("bad handshake");
        }
        return sb.toString();
    }

    private static void writeText(OutputStream out, byte[] p) throws Exception { writeFrame(out, 0x1, p); }
    private static void writeClose(OutputStream out) throws Exception { writeFrame(out, 0x8, new byte[] { 0x03, (byte) 0xE8 }); }

    private static void writeFrame(OutputStream out, int op, byte[] p) throws Exception {
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        f.write(0x80 | op);
        if (p.length < 126) f.write(0x80 | p.length);
        else if (p.length < 65536) { f.write(0x80 | 126); f.write(p.length >> 8); f.write(p.length & 255); }
        else { f.write(0x80 | 127); for (int i = 7; i >= 0; i--) f.write((int) (((long) p.length >> (8 * i)) & 255)); }
        byte[] mask = new byte[4];
        new SecureRandom().nextBytes(mask);
        f.write(mask);
        for (int i = 0; i < p.length; i++) f.write(p[i] ^ mask[i & 3]);
        out.write(f.toByteArray());
        out.flush();
    }

    private static void readFully(InputStream in, byte[] b) throws Exception {
        int o = 0;
        while (o < b.length) { int r = in.read(b, o, b.length - o); if (r < 0) throw new Exception("connection closed"); o += r; }
    }

    private static String readText(InputStream in, OutputStream out) throws Exception {
        ByteArrayOutputStream msg = new ByteArrayOutputStream();
        while (true) {
            byte[] h = new byte[2];
            readFully(in, h);
            boolean fin = (h[0] & 0x80) != 0;
            int op = h[0] & 0x0F;
            long len = h[1] & 0x7F;
            if (len == 126) { byte[] e = new byte[2]; readFully(in, e); len = ((e[0] & 255) << 8) | (e[1] & 255); }
            else if (len == 127) { byte[] e = new byte[8]; readFully(in, e); len = 0; for (int i = 0; i < 8; i++) len = (len << 8) | (e[i] & 255); }
            if (len > 4 * 1024 * 1024) throw new Exception("reply too large");
            byte[] mask = null;
            if ((h[1] & 0x80) != 0) { mask = new byte[4]; readFully(in, mask); }
            byte[] p = new byte[(int) len];
            readFully(in, p);
            if (mask != null) for (int i = 0; i < p.length; i++) p[i] ^= mask[i & 3];
            if (op == 0x9) { writeFrame(out, 0xA, p); continue; }
            if (op == 0xA) continue;
            if (op == 0x8) throw new Exception("closed by speaker");
            msg.write(p);
            if (fin) return new String(msg.toByteArray(), "UTF-8");
        }
    }

    // ---------------------------------------------------------------- helpers

    /** The household id, read from the speaker's status page. */
    public static String household(String ip) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("http://" + ip + ":1400/status/zp").openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(4000);
        try {
            InputStream in = c.getInputStream();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) > 0 && b.size() < 200000) b.write(buf, 0, r);
            String x = b.toString("UTF-8");
            int i = x.indexOf("<HouseholdControlID>");
            int j = x.indexOf("</HouseholdControlID>");
            if (i < 0 || j < 0) throw new Exception("no household");
            return x.substring(i + 20, j);
        } finally { c.disconnect(); }
    }

    /** The group whose coordinator is the given player. */
    public static String groupFor(String ip, String household, String coordUuid) throws Exception {
        JSONObject body = new JSONObject();
        body.put("includeDevices", false);
        JSONArray r = call(ip, "groups:1", "getGroups", household, null, body);
        JSONArray gs = r.getJSONObject(1).optJSONArray("groups");
        if (gs != null) for (int i = 0; i < gs.length(); i++) {
            JSONObject g = gs.getJSONObject(i);
            if (coordUuid == null || coordUuid.equals(g.optString("coordinatorId"))) return g.getString("id");
        }
        throw new Exception("group not found");
    }

    /** The favourites the speaker knows, with the playable ones only. */
    public static List<SonosClient.Item> favorites(String ip, String household) throws Exception {
        JSONArray r = call(ip, "favorites:1", "getFavorites", household, null, null);
        JSONArray items = r.getJSONObject(1).optJSONArray("items");
        List<SonosClient.Item> out = new ArrayList<SonosClient.Item>();
        if (items == null) return out;
        for (int i = 0; i < items.length(); i++) {
            JSONObject f = items.getJSONObject(i);
            JSONObject res = f.optJSONObject("resource");
            if (res == null || !res.optBoolean("playable", true)) continue;
            SonosClient.Item it = new SonosClient.Item();
            it.title = f.optString("name");
            it.desc = f.optString("description");
            it.artUrl = f.optString("imageUrl", null);
            it.wsId = f.optString("id");
            if (it.title.length() > 0 && it.wsId.length() > 0) out.add(it);
        }
        return out;
    }

    public static void loadFavorite(String ip, String groupId, String favoriteId) throws Exception {
        JSONObject body = new JSONObject();
        body.put("favoriteId", favoriteId);
        body.put("playOnCompletion", true);
        call(ip, "favorites:1", "loadFavorite", null, groupId, body);
    }

    /** What the group is playing now, for the journal: "container | track". */
    public static String nowPlaying(String ip, String groupId) throws Exception {
        JSONArray r = call(ip, "playbackMetadata:1", "getMetadataStatus", null, groupId, null);
        JSONObject b = r.getJSONObject(1);
        JSONObject c = b.optJSONObject("container");
        JSONObject cur = b.optJSONObject("currentItem");
        JSONObject tr = cur == null ? null : cur.optJSONObject("track");
        return (c == null ? "?" : c.optString("name", "?")) + " | " + (tr == null ? "?" : tr.optString("name", "?"));
    }

    /** The track after the current one, or null. */
    public static SonosClient.Item nextTrack(String ip, String groupId) throws Exception {
        JSONArray r = call(ip, "playbackMetadata:1", "getMetadataStatus", null, groupId, null);
        JSONObject n = r.getJSONObject(1).optJSONObject("nextItem");
        JSONObject t = n == null ? null : n.optJSONObject("track");
        if (t == null) return null;
        SonosClient.Item it = new SonosClient.Item();
        it.title = t.optString("name");
        JSONObject ar = t.optJSONObject("artist");
        it.artist = ar == null ? "" : ar.optString("name");
        JSONObject al = t.optJSONObject("album");
        it.album = al == null ? "" : al.optString("name");
        it.artUrl = t.optString("imageUrl", null);
        if (it.artUrl == null || it.artUrl.length() == 0) {
            JSONArray ims = t.optJSONArray("images");
            JSONObject im = ims == null ? null : ims.optJSONObject(0);
            it.artUrl = im == null ? null : im.optString("url", null);
        }
        if (it.artUrl == null || it.artUrl.length() == 0) {
            // No cover given (Spotify Connect): the speaker serves it from its own art service, built from the track id
            JSONObject id = t.optJSONObject("id");
            String obj = id == null ? "" : id.optString("objectId");
            if (obj.startsWith("spotify:track:")) {
                String sid = id.optString("serviceId", "9");
                String acc = id.optString("accountId", "sn_2");
                String sn = acc.startsWith("sn_") ? acc.substring(3) : "2";
                it.artUrl = "http://" + ip + ":1400/getaa?s=1&u=x-sonos-spotify%3aspotify%253atrack%253a" + obj.substring(14)
                        + "%3fsid%3d" + sid + "%26flags%3d8232%26sn%3d" + sn;
            }
        }
        return it.title.length() == 0 ? null : it;
    }
}
