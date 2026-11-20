package app.pochette.tv.theme;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.media.MediaMetadataRetriever;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Small still of each ambiance's wallpaper (drawn scene, or a frame of its clip) for the menu cards and the home tiles. */
public final class ThemePreview {
    static final int W = 480, H = 270;
    private static Context ctx;
    private static final Map<Integer, Bitmap> cache = new HashMap<Integer, Bitmap>();
    private static final Set<Integer> busy = new HashSet<Integer>();
    private static Runnable onReady;

    public static void init(Context c, Runnable ready) { ctx = c.getApplicationContext(); onReady = ready; }

    /** The preview, or null while it is being prepared (onReady fires when it exists) or when the theme has none. */
    public static synchronized Bitmap get(int theme) {
        if (theme <= 0 || ctx == null) return null;
        Bitmap b = cache.get(theme);
        if (b != null) return b;
        if (cache.containsKey(theme) || busy.contains(theme)) return null;
        busy.add(theme);
        final int th = theme;
        new Thread(new Runnable() {
            @Override public void run() {
                Bitmap r = null;
                try { r = build(th); } catch (Throwable ignored) { }
                synchronized (ThemePreview.class) { cache.put(th, r); busy.remove(th); }
                Runnable cb = onReady;
                if (cb != null && r != null) cb.run();
            }
        }).start();
        return null;
    }

    /** Forget a picture (the owner replaced or removed a custom video). */
    public static synchronized void forget(int theme) { cache.remove(theme); }

    private static Bitmap build(int theme) throws Exception {
        String v = Themes.video(theme);
        if (v != null) {
            boolean file = v.startsWith("file:");
            int id = file ? 1 : ctx.getResources().getIdentifier(v, "raw", ctx.getPackageName());
            if (id == 0) return null;
            AssetFileDescriptor fd = file ? null : ctx.getResources().openRawResourceFd(id);
            MediaMetadataRetriever mr = new MediaMetadataRetriever();
            try {
                if (file) mr.setDataSource(v.substring(5)); else mr.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                Bitmap f = mr.getFrameAtTime(1500000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (f == null) f = mr.getFrameAtTime();
                if (f == null) return null;
                return Bitmap.createScaledBitmap(f, W, H, true);
            } finally { try { mr.release(); } catch (Throwable ignored) { } try { if (fd != null) fd.close(); } catch (Throwable ignored) { } }
        }
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Themes t = new Themes();
        long now = System.currentTimeMillis();
        t.drawBackground(c, theme, W, H, now);
        try { t.drawEffects(c, theme, W, H, now); } catch (Throwable ignored) { }
        return out;
    }

    /** A shader showing the preview "cover"-fitted into the rectangle (x, y, w, h). */
    public static BitmapShader shader(Bitmap b, float x, float y, float w, float h) {
        BitmapShader s = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float k = Math.max(w / b.getWidth(), h / b.getHeight());
        Matrix m = new Matrix();
        m.postScale(k, k);
        m.postTranslate(x + (w - b.getWidth() * k) / 2f, y + (h - b.getHeight() * k) / 2f);
        s.setLocalMatrix(m);
        return s;
    }
}
