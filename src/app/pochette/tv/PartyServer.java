package app.pochette.tv;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Local web page for the party games: the phone of the player who draws or describes. It shows the secret word
 * (so the TV can stay public), carries the drawing canvas and the « found » / « pass » buttons.
 */
public final class PartyServer {

    public static final class Stroke {
        public final int color;
        public final float[] pts;       // x,y pairs, 0..1 of the canvas
        Stroke(int color, float[] pts) { this.color = color; this.pts = pts; }
    }

    private ServerSocket server;
    private volatile boolean running;
    private int port;
    public final String token = Integer.toString(100000 + new Random().nextInt(900000), 36);

    // what the page shows (set by the game)
    private volatile String mode = "none";          // none | draw | taboo
    private volatile String word = "";
    private volatile String[] forbidden = new String[0];
    private volatile int color = 0xFFFFFFFF;
    private volatile int remaining;
    private volatile int gen;                       // bumps to clear the phone's canvas
    private volatile long lastPoll;

    private final List<Stroke> strokes = new ArrayList<Stroke>();
    private final List<String> events = new ArrayList<String>();

    public boolean start() {
        if (running) return true;
        for (int p = 8090; p < 8110; p++) {
            try { server = new ServerSocket(p); port = p; break; } catch (Exception ignored) { server = null; }
        }
        if (server == null) return false;
        running = true;
        Thread t = new Thread(new Runnable() { @Override public void run() { loop(); } }, "pochette-party");
        t.setDaemon(true);
        t.start();
        return true;
    }

    public void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        server = null;
    }

    public String url() { return "http://" + SetupServer.localIp() + ":" + port + "/?t=" + token; }
    public boolean connected() { return System.currentTimeMillis() - lastPoll < 3500; }

    public void show(String mode, String word, String[] forbidden, int color) {
        this.mode = mode; this.word = word == null ? "" : word; this.forbidden = forbidden == null ? new String[0] : forbidden;
        this.color = color;
        clearStrokes();
        gen++;
    }
    public void setRemaining(int sec) { remaining = sec; }
    public void idle() { show("none", "", null, 0xFFFFFFFF); }

    public synchronized List<Stroke> strokes() { return new ArrayList<Stroke>(strokes); }
    private synchronized void clearStrokes() { strokes.clear(); }
    /** Next button pressed on the phone (found, pass), or null. */
    public synchronized String pollEvent() { return events.isEmpty() ? null : events.remove(0); }

    // ------------------------------------------------------------------ http

    private void loop() {
        while (running) {
            try {
                Socket s = server.accept();
                s.setSoTimeout(4000);
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

    private static String query(String path, String name) {
        int q = path.indexOf('?');
        if (q < 0) return "";
        for (String kv : path.substring(q + 1).split("&")) {
            int e = kv.indexOf('=');
            if (e > 0 && kv.substring(0, e).equals(name)) return kv.substring(e + 1);
        }
        return "";
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
        if (len > 0 && len < 30000) {
            byte[] b = new byte[len];
            int got = 0;
            while (got < len) { int n = in.read(b, got, len - got); if (n < 0) break; got += n; }
            body = new String(b, 0, got, "UTF-8");
        }
        OutputStream out = s.getOutputStream();
        String p = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        boolean ok = token.equals(query(path, "t"));
        if ("/".equals(p)) { send(out, "text/html; charset=utf-8", ok ? page() : "<meta charset=utf-8><body style='font:20px sans-serif;padding:24px'>" + T.t("Scanne le QR code affiché sur la télé.")); return; }
        if (!ok) { send(out, "text/plain", "no"); return; }
        if ("/s".equals(p)) {
            lastPoll = System.currentTimeMillis();
            StringBuilder j = new StringBuilder();
            j.append("{\"mode\":\"").append(mode).append("\",\"word\":\"").append(js(word)).append("\",\"gen\":").append(gen)
             .append(",\"sec\":").append(remaining).append(",\"col\":\"#").append(String.format("%06x", color & 0xFFFFFF)).append("\",\"f\":[");
            String[] f = forbidden;
            for (int i = 0; i < f.length; i++) j.append(i > 0 ? "," : "").append("\"").append(js(f[i])).append("\"");
            j.append("]}");
            send(out, "application/json", j.toString());
        } else if ("POST".equals(method) && "/a".equals(p)) {
            String a = query(path, "a");
            synchronized (this) {
                if ("undo".equals(a)) { if (!strokes.isEmpty()) strokes.remove(strokes.size() - 1); }
                else if ("clear".equals(a)) strokes.clear();
                else if ("found".equals(a) || "pass".equals(a)) events.add(a);
            }
            send(out, "text/plain", "ok");
        } else if ("POST".equals(method) && "/d".equals(p)) {
            try {
                String[] kv = body.split("\\|");
                int col = (int) Long.parseLong(kv[0], 16) | 0xFF000000;
                String[] n = kv[1].split(",");
                float[] pts = new float[n.length - (n.length % 2)];
                for (int i = 0; i < pts.length; i++) pts[i] = Float.parseFloat(n[i]) / 1000f;
                synchronized (this) { strokes.add(new Stroke(col, pts)); }
            } catch (Exception ignored) { }
            send(out, "text/plain", "ok");
        } else send(out, "text/plain", "no");
    }

    private static String js(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c"); }

    private void send(OutputStream out, String type, String body) throws Exception {
        byte[] b = body.getBytes("UTF-8");
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + type + "\r\nCache-Control: no-store\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
        out.write(b);
        out.flush();
    }

    private String page() {
        String h = "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,user-scalable=no\"><title>Pochette</title><style>"
            + "*{box-sizing:border-box}body{margin:0;font-family:sans-serif;background:#16102e;color:#fff;touch-action:none;user-select:none}"
            + "#top{padding:12px 14px;display:flex;justify-content:space-between;align-items:center;font-size:18px}#w{font-size:34px;font-weight:bold;padding:0 14px}"
            + "#t{font-size:30px;font-weight:bold}canvas{display:block;margin:8px auto;background:#fff;width:96vw;max-width:520px;aspect-ratio:4/3;touch-action:none}"
            + "button{border:0;padding:14px 16px;margin:4px;font-size:18px;font-weight:bold;background:#ff7a5c;color:#200}.big{width:92%;padding:22px;font-size:24px;display:block;margin:12px auto}"
            + ".g{background:#3fb87a}.p{background:#555;color:#fff}.pal button{width:44px;height:44px;padding:0;margin:3px}"
            + "#f div{display:inline-block;background:#e0524f;margin:4px;padding:8px 12px;font-size:20px}#msg{padding:30px;text-align:center;font-size:20px;color:#b8a8f0}</style></head><body>"
            + "<div id=\"top\"><span id=\"who\">Pochette</span><span id=\"t\"></span></div><div id=\"msg\">" + esc(T.t("En attente de ton tour…")) + "</div>"
            + "<div id=\"w\"></div><div id=\"f\" style=\"padding:0 10px\"></div>"
            + "<div id=\"dr\" style=\"display:none\"><canvas id=\"c\" width=\"800\" height=\"600\"></canvas><div class=\"pal\" style=\"text-align:center\" id=\"pal\"></div>"
            + "<div style=\"text-align:center\"><button onclick=\"act('undo')\">" + esc(T.t("Annuler")) + "</button><button onclick=\"act('clear');clr()\">" + esc(T.t("Effacer")) + "</button><button class=\"g\" onclick=\"act('found')\">" + esc(T.t("Trouvé !")) + "</button></div></div>"
            + "<div id=\"tb\" style=\"display:none\"><button class=\"big g\" onclick=\"act('found')\">" + esc(T.t("Trouvé !")) + "</button><button class=\"big p\" onclick=\"act('pass')\">" + esc(T.t("Passer")) + "</button></div>"
            + "<script>var T=new URLSearchParams(location.search).get('t');var cv=document.getElementById('c'),cx=cv.getContext('2d'),col='#111',gen=-1,cur=null;"
            + "function $(i){return document.getElementById(i)}function act(a){fetch('/a?t='+T+'&a='+a,{method:'POST'})}"
            + "function clr(){cx.fillStyle='#fff';cx.fillRect(0,0,800,600)}clr();"
            + "['#111111','#e0524f','#3f8de0','#3fb87a','#e8b93a','#9a6be0','#ffffff'].forEach(function(c){var b=document.createElement('button');b.style.background=c;b.onclick=function(){col=c};$('pal').appendChild(b)});"
            + "function pos(e){var r=cv.getBoundingClientRect(),t=e.touches?e.touches[0]:e;return [(t.clientX-r.left)/r.width,(t.clientY-r.top)/r.height]}"
            + "function dn(e){e.preventDefault();var p=pos(e);cur={c:col,p:[p]};cx.strokeStyle=col;cx.lineWidth=col=='#ffffff'?28:7;cx.lineCap='round';cx.lineJoin='round';cx.beginPath();cx.moveTo(p[0]*800,p[1]*600)}"
            + "function mv(e){if(!cur)return;e.preventDefault();var p=pos(e);cur.p.push(p);cx.lineTo(p[0]*800,p[1]*600);cx.stroke()}"
            + "function up(){if(!cur)return;var s=cur;cur=null;if(s.p.length<2)s.p.push(s.p[0]);var b=s.c.substring(1)+'|'+s.p.map(function(p){return Math.round(p[0]*1000)+','+Math.round(p[1]*1000)}).join(',');"
            + "fetch('/d?t='+T,{method:'POST',body:b})}"
            + "cv.addEventListener('touchstart',dn);cv.addEventListener('touchmove',mv);cv.addEventListener('touchend',up);cv.addEventListener('mousedown',dn);cv.addEventListener('mousemove',mv);window.addEventListener('mouseup',up);"
            + "function poll(){fetch('/s?t='+T).then(function(r){return r.json()}).then(function(s){"
            + "$('msg').style.display=s.mode=='none'?'block':'none';$('dr').style.display=s.mode=='draw'?'block':'none';$('tb').style.display=s.mode=='taboo'?'block':'none';"
            + "$('w').textContent=s.word;$('w').style.color=s.col;$('t').textContent=s.mode=='none'?'':s.sec+' s';"
            + "$('f').innerHTML=s.mode=='taboo'?s.f.map(function(x){return '<div>'+x+'</div>'}).join(''):'';"
            + "if(s.gen!=gen){gen=s.gen;clr()}}).catch(function(){})}setInterval(poll,700);poll();</script></body></html>";
        return h;
    }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
}
