package app.pochette.tv.sonos;

import android.os.SystemClock;

import java.util.List;

/** Slow volume rise used by the Wake-up ambiance: one background thread nudges every room once a second. */
public final class VolumeRamp {
    private VolumeRamp() {}

    // Static so that the rise finishes even if the screen is left.
    private static volatile Thread rampThread;
    public static volatile int rampFrom, rampTo;
    public static volatile long rampStart, rampMs;

    /** 0..1 while a slow volume rise is running, -1 otherwise. */
    public static float fraction() {
        if (rampThread == null || rampMs <= 0) return -1f;
        return Math.max(0f, Math.min(1f, (SystemClock.elapsedRealtime() - rampStart) / (float) rampMs));
    }

    static void cancel() {
        Thread t = rampThread;
        rampThread = null;
        if (t != null) t.interrupt();
    }

    static void start(final List<String> ips, int from, int to, long ms) {
        cancel();
        rampFrom = from; rampTo = to; rampMs = ms; rampStart = SystemClock.elapsedRealtime();
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                int last = rampFrom;
                try {
                    while (rampThread == Thread.currentThread()) {
                        float f = Math.min(1f, (SystemClock.elapsedRealtime() - rampStart) / (float) rampMs);
                        int v = Math.round(rampFrom + (rampTo - rampFrom) * f);
                        if (v != last) {
                            last = v;
                            for (String ip : ips) { try { SonosClient.setVolume(ip, v); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); } }
                        }
                        if (f >= 1f) break;
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException ignored) {}
                if (rampThread == Thread.currentThread()) rampThread = null;
            }
        }, "pochette-ramp");
        t.setDaemon(true);
        rampThread = t;
        t.start();
    }
}
