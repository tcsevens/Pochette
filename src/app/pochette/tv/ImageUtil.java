package app.pochette.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class ImageUtil {
    private ImageUtil() {}

    public static Bitmap download(String url, int maxSide) {
        HttpURLConnection c = null;
        try {
            for (int redirects = 0; redirects < 4; redirects++) {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(6000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "Pochette/1.0 (Android TV)");
                int code = c.getResponseCode();
                if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                    url = new URL(new URL(url), c.getHeaderField("Location")).toString();
                    c.disconnect();
                    continue;
                }
                if (code != 200) return null;
                break;
            }
            InputStream is = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = is.read(b)) > 0) bo.write(b, 0, n);
            is.close();
            byte[] data = bo.toByteArray();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Soft, heavily blurred copy (small; draw it scaled up with filtering). */
    public static Bitmap blurred(Bitmap src) {
        int w = 96, h = 96;
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            boxBlur(px, w, h, 7, true);
            boxBlur(px, w, h, 7, false);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        if (small != src) small.recycle();
        return out;
    }

    private static void boxBlur(int[] px, int w, int h, int r, boolean horizontal) {
        int[] out = new int[px.length];
        int outer = horizontal ? h : w, inner = horizontal ? w : h;
        for (int o = 0; o < outer; o++) {
            int sr = 0, sg = 0, sb = 0, cnt = 0;
            for (int k = -r; k <= r; k++) {
                int i = clamp(k, 0, inner - 1);
                int p = px[horizontal ? o * w + i : i * w + o];
                sr += (p >> 16) & 255; sg += (p >> 8) & 255; sb += p & 255; cnt++;
            }
            for (int i = 0; i < inner; i++) {
                int idx = horizontal ? o * w + i : i * w + o;
                out[idx] = 0xff000000 | ((sr / cnt) << 16) | ((sg / cnt) << 8) | (sb / cnt);
                int add = clamp(i + r + 1, 0, inner - 1), rem = clamp(i - r, 0, inner - 1);
                int pa = px[horizontal ? o * w + add : add * w + o];
                int pr = px[horizontal ? o * w + rem : rem * w + o];
                sr += ((pa >> 16) & 255) - ((pr >> 16) & 255);
                sg += ((pa >> 8) & 255) - ((pr >> 8) & 255);
                sb += (pa & 255) - (pr & 255);
            }
        }
        System.arraycopy(out, 0, px, 0, px.length);
    }

    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    /** A lively accent colour taken from the artwork, bright enough to read on a dark background. */
    public static int accent(Bitmap src) {
        Bitmap s = Bitmap.createScaledBitmap(src, 40, 40, true);
        int[] px = new int[1600];
        s.getPixels(px, 0, 40, 0, 0, 40, 40);
        float[] weight = new float[36];
        float[][] sum = new float[36][3];
        float[] hsv = new float[3];
        for (int p : px) {
            Color.colorToHSV(p, hsv);
            float wgt = hsv[1] * hsv[1] * hsv[2];
            if (hsv[2] < 0.18f) continue;
            int b = ((int) (hsv[0] / 10f)) % 36;
            weight[b] += wgt;
            sum[b][0] += hsv[0] * wgt; sum[b][1] += hsv[1] * wgt; sum[b][2] += hsv[2] * wgt;
        }
        if (s != src) s.recycle();
        int best = -1;
        float bw = 0;
        for (int i = 0; i < 36; i++) {
            float w = weight[i] + 0.5f * (weight[(i + 35) % 36] + weight[(i + 1) % 36]);
            if (w > bw) { bw = w; best = i; }
        }
        if (best < 0 || weight[best] < 4f) return 0xFFEDEDED; // greyscale artwork
        float wt = weight[best];
        float h = sum[best][0] / wt, sat = sum[best][1] / wt, v = sum[best][2] / wt;
        sat = Math.min(0.85f, Math.max(0.45f, sat));
        v = Math.max(0.85f, v);
        return Color.HSVToColor(new float[]{h, sat, v});
    }

    /** Up to four distinct, lively colours from the artwork (for marbled vinyl). */
    public static int[] palette(Bitmap src, int accent) {
        Bitmap s = Bitmap.createScaledBitmap(src, 40, 40, true);
        int[] px = new int[1600];
        s.getPixels(px, 0, 40, 0, 0, 40, 40);
        if (s != src) s.recycle();
        float[] weight = new float[36];
        float[][] sum = new float[36][3];
        float[] hsv = new float[3];
        for (int p : px) {
            Color.colorToHSV(p, hsv);
            if (hsv[2] < 0.12f) continue;
            float wgt = 0.15f + hsv[1] * hsv[2];
            int b = ((int) (hsv[0] / 10f)) % 36;
            weight[b] += wgt;
            sum[b][0] += hsv[0] * wgt; sum[b][1] += hsv[1] * wgt; sum[b][2] += hsv[2] * wgt;
        }
        int[] out = new int[4];
        int n = 0;
        boolean[] used = new boolean[36];
        while (n < 4) {
            int best = -1;
            float bw = 0;
            for (int i = 0; i < 36; i++) {
                if (used[i] || weight[i] <= bw) continue;
                boolean near = false;
                for (int j = -2; j <= 2; j++) if (used[(i + j + 36) % 36]) near = true;
                if (near) continue;
                bw = weight[i];
                best = i;
            }
            if (best < 0 || bw < 3f) break;
            used[best] = true;
            float w = weight[best];
            out[n++] = Color.HSVToColor(new float[]{sum[best][0] / w, Math.min(0.9f, sum[best][1] / w), Math.max(0.35f, sum[best][2] / w)});
        }
        // Not enough distinct hues (monochrome art): derive shades from the accent.
        float[] a = new float[3];
        Color.colorToHSV(accent, a);
        while (n < 4) {
            float v = new float[]{0.95f, 0.55f, 0.75f, 0.35f}[n];
            out[n++] = Color.HSVToColor(new float[]{a[0], Math.min(0.8f, a[1]), v});
        }
        return out;
    }
}
