package app.pochette.tv.party;

import app.pochette.tv.core.LocalNet;
import app.pochette.tv.core.Prefs;
import app.pochette.tv.core.T;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;

/**
 * A tiny web page on the local network where the owner types the guest Wi-Fi name and password from a phone
 * (typing with a remote is painful). Runs only while the setup screen is open.
 */
public final class WifiSetupServer {
    private final Prefs prefs;
    private ServerSocket server;
    private volatile boolean running, saved;
    private int port;

    public WifiSetupServer(Prefs prefs) { this.prefs = prefs; }

    public boolean isRunning() { return running; }
    /** The phone has submitted the form. */
    public boolean saved() { return saved; }
    public String url() { return "http://" + LocalNet.ip() + ":" + port; }

    public synchronized boolean start() {
        if (running) return true;
        for (int p = 8090; p < 8100; p++) {
            try { server = new ServerSocket(p); port = p; break; } catch (Exception ignored) { server = null; }
        }
        if (server == null) return false;
        saved = false;
        running = true;
        Thread t = new Thread(new Runnable() { @Override public void run() { loop(); } }, "pochette-wifi");
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
                try { handle(s); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Serveur", ignored); }
                try { s.close(); } catch (Exception ignored) {}
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

    private void handle(Socket s) throws Exception {
        InputStream in = s.getInputStream();
        String line = readLine(in);
        if (line.length() == 0) return;
        String[] rl = line.split(" ");
        String method = rl[0], path = rl.length > 1 ? rl[1] : "/";
        int len = 0;
        String h;
        while ((h = readLine(in)).length() > 0) if (h.toLowerCase().startsWith("content-length:")) len = Integer.parseInt(h.substring(15).trim());
        String body = "";
        if (len > 0 && len < 4000) {
            byte[] b = new byte[len];
            int got = 0;
            while (got < len) { int n = in.read(b, got, len - got); if (n < 0) break; got += n; }
            body = new String(b, 0, got, "UTF-8");
        }
        OutputStream out = s.getOutputStream();
        if ("POST".equals(method) && path.startsWith("/save")) {
            String ssid = form(body, "ssid").trim();
            if (ssid.length() > 0) { prefs.wifi(ssid, form(body, "pass")); saved = true; }
            out.write("HTTP/1.1 303 See Other\r\nLocation: /\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes("UTF-8"));
        } else {
            byte[] b = page().getBytes("UTF-8");
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            out.write(b);
        }
        out.flush();
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

    private String page() {
        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
         .append("<title>Pochette · Wi-Fi</title><style>body{font-family:sans-serif;background:#16102e;color:#eee;margin:0;padding:18px;max-width:640px}")
         .append("h1{font-size:22px}input[type=text]{width:100%;box-sizing:border-box;padding:12px;font-size:16px;margin:6px 0 14px}")
         .append("button{padding:12px 18px;background:#ff7a5c;color:#200;border:0;font-size:16px}.m{background:#2a2250;padding:10px;margin:12px 0}p{line-height:1.45}</style></head><body>")
         .append("<h1>Pochette · ").append(esc(T.t("Wi-Fi des invités"))).append("</h1>");
        if (saved) h.append("<div class=\"m\">").append(esc(T.t("Enregistré. Tu peux fermer cette page."))).append("</div>");
        h.append("<p>").append(esc(T.t("Ces informations restent sur la télé. Elles servent à afficher un QR code qui connecte tes invités. Idéalement, utilise ton réseau invités."))).append("</p>")
         .append("<form method=\"post\" action=\"/save\"><label>").append(esc(T.t("Nom du réseau"))).append("</label>")
         .append("<input type=\"text\" name=\"ssid\" autocomplete=\"off\" value=\"").append(esc(prefs.wifiSsid())).append("\">")
         .append("<label>").append(esc(T.t("Mot de passe (vide si le réseau est ouvert)"))).append("</label>")
         .append("<input type=\"text\" name=\"pass\" autocomplete=\"off\" value=\"\">")
         .append("<button>").append(esc(T.t("Enregistrer"))).append("</button></form></body></html>");
        return h.toString();
    }
}
