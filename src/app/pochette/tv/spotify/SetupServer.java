package app.pochette.tv.spotify;

import app.pochette.tv.core.LocalNet;
import app.pochette.tv.core.Prefs;
import app.pochette.tv.core.T;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Enumeration;

/**
 * A tiny web page served by the TV on the local network, so the Spotify Client ID and the sign-in code can be
 * entered from a phone instead of typing with a remote. Runs only while the user keeps the setup open.
 */
public final class SetupServer {
    public interface Listener { void changed(); }

    private final SpotifyClient spotify;
    private final Prefs prefs;
    private final Listener listener;
    private ServerSocket server;
    private volatile boolean running;
    private volatile String message = "";
    private volatile boolean visited, signInStarted, signedIn;
    private int port;
    private long startedAt;

    public SetupServer(SpotifyClient spotify, Prefs prefs, Listener l) { this.spotify = spotify; this.prefs = prefs; this.listener = l; }

    public boolean isRunning() { return running; }
    /** The phone has opened the page / pressed "Connect to Spotify" since the server started. */
    public boolean visited() { return visited; }
    public boolean signInStarted() { return signInStarted; }
    /** The sign-in was completed from the phone during this setup. */
    public boolean signedIn() { return signedIn; }
    public String url() { return "http://" + localIp() + ":" + port; }

    public static String localIp() { return LocalNet.ip(); }

    public synchronized boolean start() {
        if (running) return true;
        for (int p = 8080; p < 8090; p++) {
            try { server = new ServerSocket(p); port = p; break; } catch (Exception ignored) { server = null; }
        }
        if (server == null) return false;
        visited = false;
        signInStarted = false;
        signedIn = false;
        running = true;
        startedAt = System.currentTimeMillis();
        Thread t = new Thread(new Runnable() { @Override public void run() { loop(); } }, "pochette-setup");
        t.setDaemon(true);
        t.start();
        return true;
    }

    public synchronized void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        server = null;
    }

    private void loop() {
        while (running) {
            try {
                Socket s = server.accept();
                s.setSoTimeout(5000);
                try { handle(s); } catch (Exception ignored) {}
                try { s.close(); } catch (Exception ignored) {}
                if (System.currentTimeMillis() - startedAt > 30 * 60000L) stop();
            } catch (Exception e) {
                if (!running) break;
            }
        }
    }

    // ------------------------------------------------------------------ http

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0 && c != '\n') if (c != '\r') b.write(c);
        return b.toString("UTF-8");
    }

    private void handle(Socket s) throws Exception {
        InputStream in = s.getInputStream();
        String line = readLine(in);
        if (line.length() == 0) return;
        String[] rl = line.split(" ");
        String method = rl[0], path = rl.length > 1 ? rl[1] : "/";
        int len = 0;
        String h;
        while ((h = readLine(in)).length() > 0) {
            if (h.toLowerCase().startsWith("content-length:")) len = Integer.parseInt(h.substring(15).trim());
        }
        String body = "";
        if (len > 0 && len < 20000) {
            byte[] b = new byte[len];
            int got = 0;
            while (got < len) { int n = in.read(b, got, len - got); if (n < 0) break; got += n; }
            body = new String(b, 0, got, "UTF-8");
        }
        OutputStream out = s.getOutputStream();
        String p = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;

        if ("POST".equals(method) && "/id".equals(p)) {
            String id = form(body, "clientid").trim();
            if (id.matches("[0-9a-fA-F]{32}")) { prefs.spClientId(id); message = T.t("Client ID enregistré."); }
            else message = T.t("Ce Client ID n'a pas la bonne forme (32 caractères, lettres et chiffres).");
            redirect(out, "/");
        } else if ("/go".equals(p)) {
            if (!spotify.configured()) { redirect(out, "/"); return; }
            signInStarted = true;
            if (listener != null) listener.changed();
            String u = spotify.authUrl();
            out.write(("HTTP/1.1 302 Found\r\nLocation: " + u + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            out.flush();
        } else if ("POST".equals(method) && "/paste".equals(p)) {
            try {
                spotify.completeSignIn(form(body, "url"));
                prefs.spotifyOn(true);
                signedIn = true;
                message = T.t("Spotify est connecté. Tu peux fermer cette page.");
            } catch (Exception e) {
                message = T.t("Échec : ") + e.getMessage();
            }
            if (listener != null) listener.changed();
            redirect(out, "/");
        } else {
            if ("GET".equals(method)) { visited = true; if (listener != null) listener.changed(); }
            page(out);
        }
    }

    private static String form(String body, String name) throws Exception {
        for (String kv : body.split("&")) {
            int e = kv.indexOf('=');
            if (e > 0 && kv.substring(0, e).equals(name)) return URLDecoder.decode(kv.substring(e + 1), "UTF-8");
        }
        return "";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void redirect(OutputStream out, String to) throws Exception {
        out.write(("HTTP/1.1 303 See Other\r\nLocation: " + to + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
        out.flush();
    }

    private void page(OutputStream out) throws Exception {
        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
         .append("<title>Pochette · Spotify</title><style>body{font-family:sans-serif;background:#16102e;color:#eee;margin:0;padding:18px;max-width:640px}")
         .append("h1{font-size:22px}h2{font-size:17px;margin-top:26px;color:#ffb199}input[type=text]{width:100%;box-sizing:border-box;padding:12px;font-size:16px}")
         .append("button,a.b{display:inline-block;margin-top:10px;padding:12px 18px;background:#ff7a5c;color:#200;border:0;font-size:16px;text-decoration:none}")
         .append("code{background:#2a2250;padding:2px 5px;word-break:break-all}.m{background:#2a2250;padding:10px;margin:12px 0}p{line-height:1.45}</style></head><body>");
        h.append("<h1>Pochette · Spotify</h1>");
        if (message.length() > 0) h.append("<div class=\"m\">").append(esc(message)).append("</div>");
        if (spotify.connected()) {
            h.append("<p>").append(esc(T.t("Spotify est connecté."))).append("</p>");
        }
        h.append("<h2>1. ").append(esc(T.t("Ton application Spotify"))).append("</h2><p>")
         .append(esc(T.t("Ouvre developer.spotify.com/dashboard avec ton compte Premium, crée une application (coche « Web API »), et ajoute cette adresse de redirection, exactement :")))
         .append("</p><p><code>").append(esc(SpotifyClient.REDIRECT)).append("</code></p><p>")
         .append(esc(T.t("Copie ensuite son Client ID ici :"))).append("</p>")
         .append("<form method=\"post\" action=\"/id\"><input type=\"text\" name=\"clientid\" autocomplete=\"off\" value=\"").append(esc(prefs.spClientId())).append("\">")
         .append("<button>").append(esc(T.t("Enregistrer"))).append("</button></form>");
        if (spotify.configured()) {
            h.append("<h2>2. ").append(esc(T.t("Connexion"))).append("</h2><p>")
             .append(esc(T.t("Appuie sur le bouton, connecte-toi à Spotify et accepte. La page qui s'affiche ensuite sera en erreur : c'est normal.")))
             .append("</p><a class=\"b\" href=\"/go\">").append(esc(T.t("Se connecter à Spotify"))).append("</a>")
             .append("<h2>3. ").append(esc(T.t("Colle l'adresse de retour"))).append("</h2><p>")
             .append(esc(T.t("Copie l'adresse complète de cette page en erreur (elle commence par http://127.0.0.1:8888/callback?code=) et colle-la ici :")))
             .append("</p><form method=\"post\" action=\"/paste\"><input type=\"text\" name=\"url\" autocomplete=\"off\">")
             .append("<button>").append(esc(T.t("Terminer"))).append("</button></form>");
        }
        h.append("</body></html>");
        byte[] b = h.toString().getBytes("UTF-8");
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
        out.write(b);
        out.flush();
    }
}
