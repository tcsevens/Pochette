package app.pochette.tv.core;

/** One kitchen timer for the whole app: the time left is always computed from the end time, so nothing drifts. */
public final class KitchenTimer {
    private KitchenTimer() { }

    /** Durations offered in the menu, in minutes. */
    public static final int[] MINUTES = {1, 3, 5, 8, 10, 15, 20, 30, 45, 60};
    private static final long DONE_SHOWN_MS = 12000;

    private static volatile long endAt, totalMs, doneAt;
    private static volatile boolean running;

    public static void start(long ms) {
        totalMs = Math.max(1000, ms);
        endAt = System.currentTimeMillis() + totalMs;
        doneAt = 0;
        running = true;
    }

    public static void cancel() { running = false; doneAt = 0; }
    public static boolean running() { return running; }
    public static long remaining() { return running ? Math.max(0, endAt - System.currentTimeMillis()) : 0; }
    /** Share of the time still left, 1 at the start and 0 at the end. */
    public static float fraction() { return running ? Math.max(0f, Math.min(1f, remaining() / (float) totalMs)) : 0f; }

    /** True once, the moment the time runs out. */
    public static boolean expired() {
        if (running && System.currentTimeMillis() >= endAt) { running = false; doneAt = System.currentTimeMillis(); return true; }
        return false;
    }

    /** The "time's up" badge stays on screen for a few seconds after the chime. */
    public static boolean doneShown() { return doneAt > 0 && System.currentTimeMillis() - doneAt < DONE_SHOWN_MS; }

    public static String format(long ms) {
        long s = (ms + 999) / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return (h > 0 ? h + ":" + (m < 10 ? "0" : "") : "") + m + ":" + (sec < 10 ? "0" : "") + sec;
    }
}
