package app.pochette.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.View;

import java.util.List;

/** Full-screen QR code of the shared queue, with the last songs added by the guests. */
public final class ShareView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface bold = Typeface.create("sans-serif-black", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private QueueServer server;

    public ShareView(Context c) {
        super(c);
        setFocusable(false);
        setSoundEffectsEnabled(false);
        setVisibility(GONE);
    }

    public boolean active() { return getVisibility() == VISIBLE; }
    public void open(QueueServer s) { server = s; setVisibility(VISIBLE); invalidate(); }
    public void close() { setVisibility(GONE); }
    public void handleKey(int code) {
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) close();
    }

    private void txt(Canvas c, String t, float x, float y, float size, int color, Typeface tf, Paint.Align a, float s) {
        tp.setTypeface(tf); tp.setTextSize(size * s); tp.setColor(color); tp.setTextAlign(a);
        c.drawText(t, x, y, tp);
    }

    @Override protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        float s = H / 1080f;
        fill.setColor(0xFF16102E);
        c.drawRect(0, 0, W, H, fill);
        if (server == null) return;
        txt(c, T.t("File d'attente partagée"), 90 * s, 150 * s, 72, 0xFFFFFFFF, bold, Paint.Align.LEFT, s);
        txt(c, T.t("Scanne le QR code pour ajouter une chanson"), 90 * s, 215 * s, 36, 0xFFF2C66B, regular, Paint.Align.LEFT, s);
        txt(c, T.t("Aucune appli, aucun compte : juste ton navigateur (même Wi-Fi)."), 90 * s, 262 * s, 28, 0xB0FFFFFF, regular, Paint.Align.LEFT, s);
        txt(c, T.t("Ajoutées récemment"), 90 * s, 400 * s, 32, 0xFFFFFFFF, bold, Paint.Align.LEFT, s);
        List<String> rec = server.recent();
        if (rec.isEmpty()) txt(c, T.t("Rien pour l'instant"), 90 * s, 460 * s, 30, 0x80FFFFFF, regular, Paint.Align.LEFT, s);
        for (int i = 0; i < rec.size() && i < 8; i++) {
            fill.setColor(0xFFFF7A5C); c.drawRect(90 * s, (430 + i * 62) * s, 98 * s, (480 + i * 62) * s, fill);
            tp.setTypeface(regular); tp.setTextSize(32 * s); tp.setColor(0xFFFFFFFF); tp.setTextAlign(Paint.Align.LEFT);
            String l = android.text.TextUtils.ellipsize(rec.get(i), new android.text.TextPaint(tp), 900 * s, android.text.TextUtils.TruncateAt.END).toString();
            c.drawText(l, 120 * s, (468 + i * 62) * s, tp);
        }
        boolean[][] m = QrCode.encode(server.url());
        int n = m.length;
        float size = 620 * s, x0 = W - 90 * s - size, y0 = 300 * s, q = size / (n + 4);
        fill.setColor(0xFFFFFFFF); c.drawRect(x0, y0, x0 + size, y0 + size, fill);
        fill.setColor(0xFF000000);
        for (int r = 0; r < n; r++) for (int k = 0; k < n; k++) if (m[r][k]) c.drawRect(x0 + (k + 2) * q, y0 + (r + 2) * q, x0 + (k + 3) * q, y0 + (r + 3) * q, fill);
        txt(c, server.url().replace("http://", ""), x0 + size / 2f, y0 + size + 50 * s, 24, 0x90FFFFFF, regular, Paint.Align.CENTER, s);
        txt(c, T.t("OK ou Retour : fermer (la file reste ouverte)"), 90 * s, 1030 * s, 26, 0x90FFFFFF, regular, Paint.Align.LEFT, s);
        postInvalidateDelayed(1000);
    }
}
