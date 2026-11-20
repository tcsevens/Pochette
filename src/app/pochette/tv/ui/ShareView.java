package app.pochette.tv.ui;

import app.pochette.tv.core.QrCode;
import app.pochette.tv.core.T;
import app.pochette.tv.party.QueueServer;
import app.pochette.tv.party.WifiSetupServer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.View;

import java.util.List;

/**
 * Full-screen invitation: step 1 a QR code that joins the guest Wi-Fi (skippable, only when one is set up),
 * step 2 the QR code of the shared queue with the last songs added. Also hosts the Wi-Fi setup screen.
 */
public final class ShareView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface bold = Typeface.create("sans-serif-black", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private QueueServer server;
    private WifiSetupServer setup;
    private String wifiQr = "", wifiName = "";
    private int stage;

    public ShareView(Context c) {
        super(c);
        setFocusable(false);
        setSoundEffectsEnabled(false);
        setVisibility(GONE);
    }

    public boolean active() { return getVisibility() == VISIBLE; }
    /** wifiQr = QR payload to join the guest network, "" to skip step 1. */
    public void open(QueueServer s, String wifiQr, String wifiName) {
        server = s; setup = null;
        this.wifiQr = wifiQr; this.wifiName = wifiName;
        stage = wifiQr.length() > 0 ? 0 : 1;
        setVisibility(VISIBLE); invalidate();
    }
    /** The screen with the address where the phone enters the Wi-Fi name and password. */
    public void openSetup(WifiSetupServer w) { setup = w; server = null; setVisibility(VISIBLE); invalidate(); }
    public void close() {
        if (setup != null) { setup.stop(); setup = null; }
        setVisibility(GONE);
    }
    public void handleKey(int code) {
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        if (code == KeyEvent.KEYCODE_BACK) close();
        else if (setup != null) { if (ok) close(); }
        else if (stage == 0 && (ok || code == KeyEvent.KEYCODE_DPAD_RIGHT)) { stage = 1; invalidate(); }
        else if (stage == 1 && wifiQr.length() > 0 && code == KeyEvent.KEYCODE_DPAD_LEFT) { stage = 0; invalidate(); }
        else if (ok) close();
    }

    private void txt(Canvas c, String t, float x, float y, float size, int color, Typeface tf, Paint.Align a, float s) {
        tp.setTypeface(tf); tp.setTextSize(size * s); tp.setColor(color); tp.setTextAlign(a);
        c.drawText(t, x, y, tp);
    }

    private void qr(Canvas c, String text, float x0, float y0, float size) {
        boolean[][] m = QrCode.encode(text);
        int n = m.length;
        float q = size / (n + 4);
        fill.setColor(0xFFFFFFFF); c.drawRect(x0, y0, x0 + size, y0 + size, fill);
        fill.setColor(0xFF000000);
        for (int r = 0; r < n; r++) for (int k = 0; k < n; k++) if (m[r][k]) c.drawRect(x0 + (k + 2) * q, y0 + (r + 2) * q, x0 + (k + 3) * q, y0 + (r + 3) * q, fill);
    }

    @Override protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        float s = H / 1080f;
        fill.setColor(0xFF16102E);
        c.drawRect(0, 0, W, H, fill);
        float size = 620 * s, x0 = W - 90 * s - size, y0 = 300 * s;
        if (setup != null) {
            txt(c, T.t("Wi-Fi des invités"), 90 * s, 150 * s, 72, 0xFFFFFFFF, bold, Paint.Align.LEFT, s);
            if (setup.saved()) {
                txt(c, T.t("Enregistré"), 90 * s, 240 * s, 40, 0xFF34D27B, bold, Paint.Align.LEFT, s);
                txt(c, T.t("Le QR code Wi-Fi sera proposé à tes invités."), 90 * s, 300 * s, 30, 0xB0FFFFFF, regular, Paint.Align.LEFT, s);
            } else {
                txt(c, T.t("Scanne ce QR code avec ton téléphone"), 90 * s, 240 * s, 36, 0xFFF2C66B, regular, Paint.Align.LEFT, s);
                txt(c, T.t("Tape le nom et le mot de passe du réseau invités sur la page qui s'ouvre."), 90 * s, 292 * s, 28, 0xB0FFFFFF, regular, Paint.Align.LEFT, s);
                txt(c, T.t("Ils restent sur cette télé."), 90 * s, 336 * s, 28, 0xB0FFFFFF, regular, Paint.Align.LEFT, s);
            }
            qr(c, setup.url(), x0, y0, size);
            txt(c, setup.url().replace("http://", ""), x0 + size / 2f, y0 + size + 50 * s, 24, 0x90FFFFFF, regular, Paint.Align.CENTER, s);
            if (setup.altUrl().length() > 0) txt(c, T.t("ou ") + setup.altUrl().replace("http://", ""), x0 + size / 2f, y0 + size + 84 * s, 22, 0x70FFFFFF, regular, Paint.Align.CENTER, s);
            txt(c, T.t("OK ou Retour : fermer"), 90 * s, 1030 * s, 26, 0x90FFFFFF, regular, Paint.Align.LEFT, s);
            postInvalidateDelayed(1000);
            return;
        }
        if (server == null) return;
        boolean two = wifiQr.length() > 0;
        if (stage == 0) {
            txt(c, T.t("Étape 1 sur 2"), 90 * s, 110 * s, 30, 0xB0FFFFFF, regular, Paint.Align.LEFT, s);
            txt(c, T.t("Rejoindre le Wi-Fi"), 90 * s, 190 * s, 72, 0xFFFFFFFF, bold, Paint.Align.LEFT, s);
            txt(c, T.t("Scanne ce QR code avec l'appareil photo du téléphone."), 90 * s, 255 * s, 36, 0xFFF2C66B, regular, Paint.Align.LEFT, s);
            txt(c, T.t("Réseau : ") + wifiName, 90 * s, 320 * s, 34, 0xFFFFFFFF, bold, Paint.Align.LEFT, s);
            qr(c, wifiQr, x0, y0, size);
            txt(c, T.t("Déjà connecté ? OK pour passer à l'étape 2"), 90 * s, 1030 * s, 26, 0x90FFFFFF, regular, Paint.Align.LEFT, s);
            return;
        }
        if (two) txt(c, T.t("Étape 2 sur 2"), 90 * s, 110 * s, 30, 0xB0FFFFFF, regular, Paint.Align.LEFT, s);
        txt(c, T.t("File d'attente partagée"), 90 * s, 190 * s, 72, 0xFFFFFFFF, bold, Paint.Align.LEFT, s);
        txt(c, T.t("Scanne le QR code pour ajouter une chanson"), 90 * s, 255 * s, 36, 0xFFF2C66B, regular, Paint.Align.LEFT, s);
        txt(c, T.t("Aucune appli, aucun compte : juste ton navigateur (même Wi-Fi)."), 90 * s, 302 * s, 28, 0xB0FFFFFF, regular, Paint.Align.LEFT, s);
        txt(c, T.t("Ajoutées récemment"), 90 * s, 440 * s, 32, 0xFFFFFFFF, bold, Paint.Align.LEFT, s);
        List<String> rec = server.recent();
        if (rec.isEmpty()) txt(c, T.t("Rien pour l'instant"), 90 * s, 500 * s, 30, 0x80FFFFFF, regular, Paint.Align.LEFT, s);
        for (int i = 0; i < rec.size() && i < 7; i++) {
            fill.setColor(0xFFFF7A5C); c.drawRect(90 * s, (470 + i * 62) * s, 98 * s, (520 + i * 62) * s, fill);
            tp.setTypeface(regular); tp.setTextSize(32 * s); tp.setColor(0xFFFFFFFF); tp.setTextAlign(Paint.Align.LEFT);
            String l = android.text.TextUtils.ellipsize(rec.get(i), new android.text.TextPaint(tp), 900 * s, android.text.TextUtils.TruncateAt.END).toString();
            c.drawText(l, 120 * s, (508 + i * 62) * s, tp);
        }
        qr(c, server.url(), x0, y0, size);
        txt(c, server.url().replace("http://", "").replaceAll("/\\?t=.*", ""), x0 + size / 2f, y0 + size + 50 * s, 24, 0x90FFFFFF, regular, Paint.Align.CENTER, s);
        if (server.altUrl().length() > 0) txt(c, T.t("ou ") + server.altUrl().replace("http://", "").replaceAll("/\\?t=.*", ""), x0 + size / 2f, y0 + size + 84 * s, 22, 0x70FFFFFF, regular, Paint.Align.CENTER, s);
        txt(c, two ? T.t("◀ étape 1   ·   OK ou Retour : fermer (la file reste ouverte)") : T.t("OK ou Retour : fermer (la file reste ouverte)"), 90 * s, 1030 * s, 26, 0x90FFFFFF, regular, Paint.Align.LEFT, s);
        postInvalidateDelayed(1000);
    }
}
