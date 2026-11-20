package app.pochette.tv.party;

import app.pochette.tv.core.LocalNet;
import app.pochette.tv.core.T;
import app.pochette.tv.sonos.SonosClient;
import app.pochette.tv.spotify.SpotifyClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared queue: guests scan a QR code, search a song on a small web page served by the TV box and add it to the
 * Spotify queue. Nothing to install or sign in to for the guests; the search uses the owner's Spotify connection.
 */
public final class QueueServer {
    public interface Listener { void added(String title, String artist); boolean enqueue(String uri, String title) throws Exception; }

    private final SpotifyClient spotify;
    private final Listener listener;
    private ServerSocket server;
    private volatile boolean running;
    private int port;
    public final String token;
    private final List<String> recent = new ArrayList<String>();
    private final Map<String, Long> lastAdd = new HashMap<String, Long>();

    public QueueServer(SpotifyClient s, String token, Listener l) { spotify = s; this.token = token; listener = l; }

    public boolean isRunning() { return running; }

    public boolean start() {
        if (running) return true;
        for (int p = 8110; p < 8130; p++) {
            try { server = new ServerSocket(p); port = p; break; } catch (Exception ignored) { server = null; }
        }
        if (server == null) return false;
        running = true;
        Thread t = new Thread(new Runnable() { @Override public void run() { loop(); } }, "pochette-queue");
        t.setDaemon(true);
        t.start();
        return true;
    }

    public void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        server = null;
    }

    public String url() { return "http://" + LocalNet.ip() + ":" + port + "/?t=" + token; }
    public synchronized List<String> recent() { return new ArrayList<String>(recent); }

    private void loop() {
        while (running) {
            try {
                Socket s = server.accept();
                s.setSoTimeout(5000);
                try { handle(s); } catch (Exception ignored) { }
                try { s.close(); } catch (Exception ignored) { }
            } catch (Exception e) {
                if (!running) break;
            }
        }
    }

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0 && c != '\n') if (c != '\r') b.write(c);
        return b.toString("UTF-8");
    }

    private static String query(String path, String name) throws Exception {
        int q = path.indexOf('?');
        if (q < 0) return "";
        for (String kv : path.substring(q + 1).split("&")) {
            int e = kv.indexOf('=');
            if (e > 0 && kv.substring(0, e).equals(name)) return URLDecoder.decode(kv.substring(e + 1), "UTF-8");
        }
        return "";
    }

    private void handle(Socket s) throws Exception {
        InputStream in = s.getInputStream();
        String line = readLine(in);
        if (line.length() == 0) return;
        String[] rl = line.split(" ");
        String method = rl[0], path = rl.length > 1 ? rl[1] : "/";
        String h;
        while ((h = readLine(in)).length() > 0) { }
        OutputStream out = s.getOutputStream();
        String p = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        boolean ok = token.equals(query(path, "t"));
        if ("/".equals(p)) { send(out, "text/html; charset=utf-8", ok ? page() : "<meta charset=utf-8><body style='font:20px sans-serif;padding:24px'>" + T.t("Scanne le QR code affiché sur la télé.")); return; }
        if (!ok) { send(out, "text/plain", "no"); return; }
        if ("/q".equals(p)) {
            String q = query(path, "s").trim();
            StringBuilder j = new StringBuilder("[");
            if (q.length() > 1) {
                try {
                    List<SonosClient.Item> r = spotify.search(q, 8);
                    for (int i = 0; i < r.size(); i++) {
                        SonosClient.Item it = r.get(i);
                        j.append(i > 0 ? "," : "").append("{\"u\":\"").append(js(it.uri)).append("\",\"t\":\"").append(js(it.title)).append("\",\"a\":\"").append(js(it.artist)).append("\"}");
                    }
                } catch (Exception e) { }
            }
            send(out, "application/json", j.append("]").toString());
        } else if ("POST".equals(method) && "/add".equals(p)) {
            String uri = query(path, "u"), title = query(path, "n"), artist = query(path, "a");
            String ip = s.getInetAddress().getHostAddress();
            long now = System.currentTimeMillis();
            Long last;
            synchronized (this) { last = lastAdd.get(ip); }
            if (!uri.startsWith("spotify:track:")) { send(out, "text/plain", T.t("Morceau invalide")); return; }
            if (last != null && now - last < 12000) { send(out, "text/plain", T.t("Doucement : un morceau toutes les 12 secondes.")); return; }
            try {
                if (listener == null || !listener.enqueue(uri, title)) spotify.addToQueue(uri);
                synchronized (this) {
                    lastAdd.put(ip, now);
                    recent.add(0, title + " — " + artist);
                    while (recent.size() > 8) recent.remove(recent.size() - 1);
                }
                if (listener != null) listener.added(title, artist);
                send(out, "text/plain", "ok");
            } catch (Exception e) {
                String m = String.valueOf(e.getMessage());
                send(out, "text/plain", m.contains("404") ? T.t("Lance d'abord une musique avec Spotify sur la Sonos.") : T.t("Impossible d'ajouter ce morceau.") + " (" + m.replace("Spotify ", "").replace("\"", "'") + ")");
            }
        } else send(out, "text/plain", "no");
    }

    private static String js(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c").replace("\n", " "); }
    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    private void send(OutputStream out, String type, String body) throws Exception {
        byte[] b = body.getBytes("UTF-8");
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + type + "\r\nCache-Control: no-store\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
        out.write(b);
        out.flush();
    }

    private String page() {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Pochette</title><style>"
            + "body{margin:0;font-family:sans-serif;background:#16102e;color:#fff}h1{font-size:22px;margin:0;padding:18px}#b{padding:0 14px}"
            + "input{width:100%;box-sizing:border-box;padding:14px;font-size:18px;border:0}"
            + ".r{display:flex;align-items:center;justify-content:space-between;background:#2a2250;margin:8px 0;padding:10px 12px}.r div{min-width:0;flex:1}"
            + ".r b{display:block;font-size:17px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.r span{font-size:14px;color:#b8a8f0}"
            + "button{border:0;padding:12px 16px;margin-left:10px;font-size:16px;font-weight:bold;background:#ff7a5c;color:#200}#m{padding:10px 14px;color:#ffe9a8;min-height:22px}</style></head><body>"
            + "<h1>" + esc(T.t("Ajoute une chanson")) + "</h1><div id=\"b\"><input id=\"s\" placeholder=\"" + esc(T.t("Titre ou artiste")) + "\" autocomplete=\"off\"><div id=\"m\"></div><div id=\"l\"></div></div><p style=\"padding:6px 14px;color:#b8a8f0;font-size:13px\">" + esc(T.t("Astuce : ajoute cette page à tes favoris ou à l'écran d'accueil pour revenir quand tu veux.")) + "</p>"
            + "<script>var T=new URLSearchParams(location.search).get('t'),tm=null,res=[];function $(i){return document.getElementById(i)}"
            + "$('s').oninput=function(){clearTimeout(tm);tm=setTimeout(go,450)};"
            + "function go(){var v=$('s').value;fetch('/q?t='+T+'&s='+encodeURIComponent(v)).then(function(r){return r.json()}).then(function(a){res=a;var h='';a.forEach(function(x,i){h+='<div class=\"r\"><div><b></b><span></span></div><button onclick=\"add('+i+')\">+</button></div>'});$('l').innerHTML=h;"
            + "var d=$('l').children;a.forEach(function(x,i){d[i].querySelector('b').textContent=x.t;d[i].querySelector('span').textContent=x.a})})}"
            + "function add(i){var x=res[i];fetch('/add?t='+T+'&u='+encodeURIComponent(x.u)+'&n='+encodeURIComponent(x.t)+'&a='+encodeURIComponent(x.a),{method:'POST'}).then(function(r){return r.text()}).then(function(t){$('m').textContent=t=='ok'?'" + esc(T.t("Ajouté à la file d'attente !")).replace("'", "\\'") + "':t})}"
            + "</script></body></html>";
    }
}
