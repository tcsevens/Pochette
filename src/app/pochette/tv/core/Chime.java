package app.pochette.tv.core;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** A short synthesised bell, about five seconds, played by the TV when no Sonos clip can be played. */
public final class Chime {
    private Chime() { }

    private static final int RATE = 22050;

    private static short[] bell() {
        double[] notes = {1046.5, 1318.5, 1568.0, 2093.0, 1568.0, 1318.5, 1046.5};
        double[] at = {0.0, 0.55, 1.1, 1.65, 2.5, 3.05, 3.6};
        int n = RATE * 5;
        double[] mix = new double[n];
        for (int k = 0; k < notes.length; k++) {
            int s0 = (int) (at[k] * RATE);
            for (int i = s0; i < n; i++) {
                double t = (i - s0) / (double) RATE;
                double env = Math.exp(-t * 2.6) * Math.min(1.0, t * 400);
                if (env < 0.001) break;
                double f = notes[k];
                double v = Math.sin(2 * Math.PI * f * t) + 0.35 * Math.sin(2 * Math.PI * f * 2.76 * t) * Math.exp(-t * 4) + 0.15 * Math.sin(2 * Math.PI * f * 5.4 * t) * Math.exp(-t * 7);
                mix[i] += v * env;
            }
        }
        double peak = 0.001;
        for (int i = 0; i < n; i++) peak = Math.max(peak, Math.abs(mix[i]));
        short[] out = new short[n];
        for (int i = 0; i < n; i++) out[i] = (short) (mix[i] / peak * 0.55 * 32767);
        return out;
    }

    public static void play() {
        new Thread(new Runnable() {
            @Override public void run() {
                AudioTrack t = null;
                try {
                    short[] pcm = bell();
                    t = new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                            new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                            pcm.length * 2, AudioTrack.MODE_STATIC, 0);
                    t.write(pcm, 0, pcm.length);
                    t.play();
                    Thread.sleep(5500);
                } catch (Exception e) {
                    Journal.w("Minuteur", e);
                } finally {
                    try { if (t != null) t.release(); } catch (Exception ignored) { }
                }
            }
        }, "pochette-chime").start();
    }
}
