package app.pochette.tv.party;

import app.pochette.tv.core.CustomBg;
import app.pochette.tv.core.LocalNet;
import app.pochette.tv.core.T;
import app.pochette.tv.theme.ThemePreview;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A small web page on the local network where the owner sends their own background videos from a phone.
 * Runs only while the "add a background" screen is open. Files go to the app's private storage.
 */
public final class BgSetupServer {
    private static final long MAX_BYTES = 300L * 1024 * 1024;
    private ServerSocket server;
    private volatile boolean running;
    private volatile int received;       // number of videos received since start
    private volatile String error = "";
    private int port;

    public boolean isRunning() { return running; }
    public int received() { return received; }
    public String error() { return error; }
    public String url() { return "http://" + LocalNet.ip() + ":" + port; }

    public synchronized boolean start() {
        if (running) return true;
        for (int p = 8090; p < 8100; p++) {
            try { server = new ServerSocket(p); port = p; break; } catch (Exception ignored) { server = null; }
        }
        if (server == null) return false;
        received = 0; error = "";
        running = true;
        Thread t = new Thread(new Runnable() { @Override public void run() { loop(); } }, "pochette-bg");
        t.setDaemon(true);
        t.start();
        return true;
    }

    public synchronized void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        server = null;
    }

    private void loop() {
        while (running) {
            try {
                final Socket s = server.accept();
                s.setSoTimeout(15000);
                new Thread(new Runnable() {
                    @Override public void run() {
                        try { handle(s); } catch (Exception ignored) { }
                        try { s.close(); } catch (Exception ignored) { }
                    }
                }).start();
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

    private static int slotOf(String path) {
        int i = path.indexOf("slot=");
        if (i < 0) return -1;
        try { int n = Integer.parseInt(path.substring(i + 5).replaceAll("[^0-9].*", "")); return n >= 0 && n < CustomBg.SLOTS ? n : -1; }
        catch (Exception e) { return -1; }
    }

    private void reply(OutputStream out, String status, String type, String body) throws Exception {
        byte[] b = body.getBytes("UTF-8");
        out.write(("HTTP/1.1 " + status + "\r\nContent-Type: " + type + "; charset=utf-8\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
        out.write(b);
        out.flush();
    }

    private void handle(Socket s) throws Exception {
        InputStream in = s.getInputStream();
        String line = readLine(in);
        if (line.length() == 0) return;
        String[] rl = line.split(" ");
        String method = rl[0], path = rl.length > 1 ? rl[1] : "/";
        long len = 0;
        String h;
        while ((h = readLine(in)).length() > 0) if (h.toLowerCase().startsWith("content-length:")) len = Long.parseLong(h.substring(15).trim());
        OutputStream out = s.getOutputStream();
        if ("POST".equals(method) && path.startsWith("/up")) {
            int slot = slotOf(path);
            if (slot < 0 || len <= 0 || len > MAX_BYTES) { reply(out, "400 Bad Request", "text/plain", T.t("Fichier trop gros (300 Mo maximum).")); return; }
            FileOutputStream fo = new FileOutputStream(CustomBg.temp(slot));
            byte[] buf = new byte[65536];
            long got = 0;
            byte[] head = new byte[12];
            int hn = 0;
            try {
                while (got < len) {
                    int n = in.read(buf, 0, (int) Math.min(buf.length, len - got));
                    if (n < 0) break;
                    for (int i = 0; i < n && hn < 12; i++) head[hn++] = buf[i];
                    fo.write(buf, 0, n);
                    got += n;
                }
            } finally { fo.close(); }
            boolean mp4 = got == len && hn >= 12 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p';
            if (!mp4) {
                CustomBg.temp(slot).delete();
                error = T.t("Ce fichier n'est pas une vidéo MP4.");
                reply(out, "415 Unsupported Media Type", "text/plain", error);
                return;
            }
            CustomBg.commit(slot);
            ThemePreview.forget(CustomBg.themeOf(slot));
            error = "";
            received++;
            reply(out, "200 OK", "text/plain", "ok");
        } else if ("POST".equals(method) && path.startsWith("/del")) {
            int slot = slotOf(path);
            if (slot >= 0) { CustomBg.delete(slot); ThemePreview.forget(CustomBg.themeOf(slot)); received++; }
            reply(out, "200 OK", "text/plain", "ok");
        } else {
            reply(out, "200 OK", "text/html", page());
        }
    }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    private String page() {
        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
         .append("<title>Pochette</title><style>body{font-family:sans-serif;background:#16102e;color:#eee;margin:0;padding:18px;max-width:640px}")
         .append("h1{font-size:22px}.s{background:#2a2250;padding:12px;margin:12px 0}button{padding:10px 16px;background:#ff7a5c;color:#200;border:0;font-size:16px;margin-top:8px}")
         .append("button.g{background:#443a78;color:#eee}progress{width:100%}p{line-height:1.45}.k{color:#34d27b}</style></head><body>")
         .append("<h1>Pochette · ").append(esc(T.t("Mes fonds d'écran"))).append("</h1>")
         .append("<p>").append(esc(T.t("Envoie une vidéo MP4 (H.264, 720p ou 1080p, sans son de préférence, 300 Mo maximum). Elle reste sur la télé et boucle en fond. Utilise seulement des vidéos dont tu as le droit de te servir."))).append("</p>");
        for (int i = 0; i < CustomBg.SLOTS; i++) {
            h.append("<div class=\"s\"><b>").append(esc(T.t("Mon fond ") + (i + 1))).append("</b> · ");
            if (CustomBg.has(i)) h.append("<span class=\"k\">").append(esc(T.t("en place"))).append(" (").append(CustomBg.file(i).length() / 1048576).append(" Mo)</span>");
            else h.append(esc(T.t("libre")));
            h.append("<br><input type=\"file\" accept=\"video/mp4,video/*\" id=\"f").append(i).append("\"><br>")
             .append("<button onclick=\"up(").append(i).append(")\">").append(esc(T.t("Envoyer"))).append("</button> ");
            if (CustomBg.has(i)) h.append("<button class=\"g\" onclick=\"del(").append(i).append(")\">").append(esc(T.t("Supprimer"))).append("</button>");
            h.append("<progress id=\"p").append(i).append("\" value=\"0\" max=\"1\" style=\"display:none\"></progress><div id=\"m").append(i).append("\"></div></div>");
        }
        h.append("<script>function up(i){var f=document.getElementById('f'+i).files[0];if(!f)return;var p=document.getElementById('p'+i),m=document.getElementById('m'+i);")
         .append("p.style.display='block';var x=new XMLHttpRequest();x.open('POST','/up?slot='+i);")
         .append("x.upload.onprogress=function(e){p.max=e.total;p.value=e.loaded};")
         .append("x.onload=function(){if(x.status==200)location.reload();else m.textContent=x.responseText};")
         .append("x.onerror=function(){m.textContent='Erreur'};x.send(f)}")
         .append("function del(i){var x=new XMLHttpRequest();x.open('POST','/del?slot='+i);x.onload=function(){location.reload()};x.send()}</script>")
         .append("</body></html>");
        return h.toString();
    }
}
