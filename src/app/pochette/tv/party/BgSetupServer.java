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
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.net.Socket;
import java.net.URLDecoder;

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
                        try { handle(s); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Serveur", ignored); }
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

    private static String nameOf(String path) {
        int i = path.indexOf("name=");
        if (i < 0) return "";
        String v = path.substring(i + 5);
        int e = v.indexOf('&');
        if (e >= 0) v = v.substring(0, e);
        try { return URLDecoder.decode(v, "UTF-8"); } catch (Exception ex) { return ""; }
    }

    /** Null when the file is a usable video, else the reason (shown on the phone). */
    private static String check(int slot, byte[] h, int hn) {
        boolean mp4 = hn >= 12 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p';
        boolean mkv = hn >= 4 && (h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45 && (h[2] & 0xFF) == 0xDF && (h[3] & 0xFF) == 0xA3;
        if (!mp4 && !mkv) return T.t("Format non accepté : envoie un fichier MP4, MOV ou MKV.");
        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(CustomBg.temp(slot).getPath());
            MediaFormat vf = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String m = f.getString(MediaFormat.KEY_MIME);
                if (m != null && m.startsWith("video/")) { vf = f; break; }
            }
            if (vf == null) return T.t("Aucune image vidéo trouvée dans ce fichier.");
            String mime = vf.getString(MediaFormat.KEY_MIME);
            if (!"video/avc".equals(mime) && !"video/hevc".equals(mime)) return T.t("Codec non accepté : utilise du H.264 ou du H.265 (HEVC).");
            int w = vf.containsKey(MediaFormat.KEY_WIDTH) ? vf.getInteger(MediaFormat.KEY_WIDTH) : 0;
            int hgt = vf.containsKey(MediaFormat.KEY_HEIGHT) ? vf.getInteger(MediaFormat.KEY_HEIGHT) : 0;
            int lo = Math.min(w, hgt), hi = Math.max(w, hgt);
            if (lo < 720) return T.t("Résolution trop basse : 720p minimum.");
            if (hi > 4096 || lo > 2160) return T.t("Résolution trop haute : 4K maximum.");
            boolean dec = false;
            for (android.media.MediaCodecInfo ci : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
                if (ci.isEncoder()) continue;
                for (String t : ci.getSupportedTypes()) if (t.equalsIgnoreCase(mime)) dec = true;
            }
            if (!dec) return T.t("Cette télé ne sait pas lire ce codec.");
            return null;
        } catch (Exception e) {
            return T.t("Fichier illisible : ce n'est pas une vidéo valide.");
        } finally { try { ex.release(); } catch (Exception ignored) { } }
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
            String bad = got == len ? check(slot, head, hn) : T.t("Envoi interrompu, réessaie.");
            if (bad != null) {
                CustomBg.temp(slot).delete();
                error = bad;
                app.pochette.tv.core.Journal.w("Fond", bad);
                reply(out, "415 Unsupported Media Type", "text/plain", bad);
                return;
            }
            CustomBg.commit(slot);
            if (path.indexOf("name=") >= 0) CustomBg.name(slot, nameOf(path));
            ThemePreview.forget(CustomBg.themeOf(slot));
            error = "";
            received++;
            reply(out, "200 OK", "text/plain", "ok");
        } else if (path.startsWith("/report")) {
            reply(out, "200 OK", "text/html", reportPage());
        } else if (path.startsWith("/log")) {
            reply(out, "200 OK", "text/plain", app.pochette.tv.core.Journal.full());
        } else if ("POST".equals(method) && path.startsWith("/name")) {
            int slot = slotOf(path);
            if (slot >= 0 && CustomBg.has(slot)) { CustomBg.name(slot, nameOf(path)); received++; }
            reply(out, "200 OK", "text/plain", "ok");
        } else if ("POST".equals(method) && path.startsWith("/del")) {
            int slot = slotOf(path);
            if (slot >= 0) { CustomBg.delete(slot); ThemePreview.forget(CustomBg.themeOf(slot)); received++; }
            reply(out, "200 OK", "text/plain", "ok");
        } else {
            reply(out, "200 OK", "text/html", page());
        }
    }

    private String reportPage() {
        String r = app.pochette.tv.core.Support.report(contextLine(), 250);
        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
         .append("<title>Pochette</title><style>body{font-family:sans-serif;background:#16102e;color:#eee;margin:0;padding:18px;max-width:700px}")
         .append("h1{font-size:22px}textarea{width:100%;height:340px;background:#0e0a20;color:#ddd;border:1px solid #443a78;font-size:12px;box-sizing:border-box}")
         .append("button,a.b{display:inline-block;padding:12px 18px;background:#ff7a5c;color:#200;border:0;font-size:16px;margin:8px 8px 0 0;text-decoration:none}a.g{background:#443a78;color:#eee}p{line-height:1.45}</style></head><body>")
         .append("<h1>Pochette · ").append(esc(T.t("Signaler un problème"))).append("</h1>")
         .append("<p>").append(esc(T.t("Ce rapport est un résumé du journal : les identifiants de ta maison (Sonos, adresses) sont masqués. Rien n'est envoyé tant que tu ne l'envoies pas toi-même."))).append("</p>")
         .append("<textarea id=\"r\" readonly>").append(esc(r)).append("</textarea><br>")
         .append("<button onclick=\"cp()\">").append(esc(T.t("Copier le rapport"))).append("</button>");
        if (app.pochette.tv.core.Support.EMAIL.length() > 0)
            h.append("<a class=\"b g\" href=\"#\" onclick=\"return mail()\">").append(esc(T.t("Envoyer par e-mail"))).append("</a>");
        h.append("<div id=\"m\"></div><script>function cp(){var t=document.getElementById('r');t.select();var ok=false;try{ok=document.execCommand('copy')}catch(e){}")
         .append("if(!ok&&navigator.clipboard){navigator.clipboard.writeText(t.value);ok=true}document.getElementById('m').textContent=ok?'").append(T.t("Copié.").replace("'", "\\'")).append("':'");
        h.append(T.t("Sélectionne le texte et copie-le.").replace("'", "\\'")).append("'}")
         .append("function mail(){var b=document.getElementById('r').value;if(b.length>1800)b=b.substring(b.length-1800);")
         .append("location.href='mailto:").append(app.pochette.tv.core.Support.EMAIL).append("?subject='+encodeURIComponent('Pochette: problem report')+'&body='+encodeURIComponent(b);return false}</script></body></html>");
        return h.toString();
    }

    private String contextLine() {
        return "App v" + app.pochette.tv.core.Journal.appVersion() + " · Android " + android.os.Build.VERSION.RELEASE + " · " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
    }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    private String page() {
        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
         .append("<title>Pochette</title><style>body{font-family:sans-serif;background:#16102e;color:#eee;margin:0;padding:18px;max-width:640px}")
         .append("h1{font-size:22px}.s{background:#2a2250;padding:12px;margin:12px 0}button{padding:10px 16px;background:#ff7a5c;color:#200;border:0;font-size:16px;margin-top:8px}")
         .append("button.g{background:#443a78;color:#eee}progress{width:100%}p{line-height:1.45}.k{color:#34d27b}</style></head><body>")
         .append("<h1>Pochette · ").append(esc(T.t("Mes fonds d'écran"))).append("</h1>")
         .append("<p>").append(esc(T.t("Formats : MP4, MOV ou MKV, en H.264 ou H.265 (HEVC). Résolution de 720p à 4K. Taille : 300 Mo maximum. Elle reste sur la télé et boucle en fond. Utilise seulement des vidéos dont tu as le droit de te servir."))).append("</p>");
        for (int i = 0; i < CustomBg.SLOTS; i++) {
            h.append("<div class=\"s\"><b>").append(esc(T.t("Mon fond ") + (i + 1))).append("</b> · ");
            if (CustomBg.has(i)) h.append("<span class=\"k\">").append(esc(T.t("en place"))).append(" (").append(CustomBg.file(i).length() / 1048576).append(" Mo)</span>");
            else h.append(esc(T.t("libre")));
            h.append("<br><input type=\"text\" maxlength=\"24\" id=\"n").append(i).append("\" placeholder=\"").append(esc(T.t("Nom du fond (facultatif)"))).append("\" value=\"").append(esc(CustomBg.name(i))).append("\" style=\"width:100%;box-sizing:border-box;padding:10px;font-size:16px;margin:8px 0\">")
             .append("<input type=\"file\" accept=\"video/mp4,video/quicktime,video/x-matroska,.mkv,.mp4,.mov\" id=\"f").append(i).append("\"><br>")
             .append("<button onclick=\"up(").append(i).append(")\">").append(esc(T.t("Envoyer"))).append("</button> ");
            if (CustomBg.has(i)) h.append("<button class=\"g\" onclick=\"ren(").append(i).append(")\">").append(esc(T.t("Renommer"))).append("</button> <button class=\"g\" onclick=\"del(").append(i).append(")\">").append(esc(T.t("Supprimer"))).append("</button>");
            h.append("<progress id=\"p").append(i).append("\" value=\"0\" max=\"1\" style=\"display:none\"></progress><div id=\"m").append(i).append("\"></div></div>");
        }
        h.append("<script>function up(i){var f=document.getElementById('f'+i).files[0];if(!f)return;var p=document.getElementById('p'+i),m=document.getElementById('m'+i);")
         .append("if(f.size>314572800){m.textContent='").append(T.t("Fichier trop gros (300 Mo maximum).").replace("'", "\\'")).append("';return}")
         .append("p.style.display='block';var x=new XMLHttpRequest();x.open('POST','/up?slot='+i+'&name='+encodeURIComponent(document.getElementById('n'+i).value));")
         .append("x.upload.onprogress=function(e){p.max=e.total;p.value=e.loaded};")
         .append("x.onload=function(){if(x.status==200)location.reload();else m.textContent=x.responseText};")
         .append("x.onerror=function(){m.textContent='Erreur'};x.send(f)}")
         .append("function ren(i){var x=new XMLHttpRequest();x.open('POST','/name?slot='+i+'&name='+encodeURIComponent(document.getElementById('n'+i).value));x.onload=function(){location.reload()};x.send()}")
         .append("function del(i){var x=new XMLHttpRequest();x.open('POST','/del?slot='+i);x.onload=function(){location.reload()};x.send()}</script>")
         .append("</body></html>");
        return h.toString();
    }
}
