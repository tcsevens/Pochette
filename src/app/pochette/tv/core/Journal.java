package app.pochette.tv.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small local log kept in the app's private storage (never sent anywhere): errors, crashes and a few key events.
 * Two files of at most 128 KB are used in turn, so the log never grows. Repeated identical lines are merged.
 */
public final class Journal {
    private static final long MAX = 128 * 1024;
    private static File dir;
    private static final Map<String, Long> last = new HashMap<String, Long>();
    private static final SimpleDateFormat FMT = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);

    private Journal() { }

    /** Opens the log and records crashes. Safe to call more than once. */
    public static synchronized void init(File filesDir) {
        if (dir != null) return;
        dir = new File(filesDir, "log");
        dir.mkdirs();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try { write("CRASH", "thread " + t.getName() + "\n" + trace(e)); } catch (Throwable ignored) { }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    private static String version = "";
    public static void setVersion(String v) { version = v == null ? "" : v; }
    public static String appVersion() { return version; }

    public static void i(String tag, String msg) { add("I", tag, msg); }
    public static void w(String tag, String msg) { add("W", tag, msg); }
    public static void e(String tag, String msg) { add("E", tag, msg); }
    public static void w(String tag, Throwable t) { add("W", tag, brief(t)); }
    public static void e(String tag, String msg, Throwable t) { add("E", tag, msg + ": " + brief(t)); }

    private static String brief(Throwable t) {
        if (t == null) return "";
        StackTraceElement[] st = t.getStackTrace();
        return t.getClass().getSimpleName() + (t.getMessage() != null ? " " + t.getMessage() : "") + (st.length > 0 ? " @" + st[0].getFileName() + ":" + st[0].getLineNumber() : "");
    }

    private static String trace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 4000 ? s.substring(0, 4000) : s;
    }

    private static void add(String level, String tag, String msg) {
        if (dir == null) return;
        long now = System.currentTimeMillis();
        String key = level + tag + msg;
        synchronized (Journal.class) {
            Long t = last.get(key);
            if (t != null && now - t < 60000) return;          // same line again within a minute: skip
            if (last.size() > 200) last.clear();
            last.put(key, now);
        }
        write(level + " " + tag, msg);
    }

    private static synchronized void write(String head, String msg) {
        try { android.util.Log.i("Pochette", head + " " + msg); } catch (Throwable ignored) { }      // also visible with adb logcat
        if (dir == null) return;
        try {
            File cur = new File(dir, "journal.txt");
            if (cur.length() > MAX) {
                File old = new File(dir, "journal.1.txt");
                old.delete();
                cur.renameTo(old);
            }
            FileOutputStream o = new FileOutputStream(cur, true);
            try { o.write((FMT.format(new Date()) + " " + head + " " + msg + "\n").getBytes("UTF-8")); } finally { o.close(); }
        } catch (Throwable ignored) { }
    }

    private static String read(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            try {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                return b.toString("UTF-8");
            } finally { in.close(); }
        } catch (Throwable e) { return ""; }
    }

    /** Whole log, oldest first. */
    public static synchronized String full() {
        if (dir == null) return "";
        return read(new File(dir, "journal.1.txt")) + read(new File(dir, "journal.txt"));
    }

    /** The last n lines, newest last. */
    public static List<String> tail(int n) {
        String[] all = full().split("\n");
        List<String> o = new ArrayList<String>();
        for (int i = Math.max(0, all.length - n); i < all.length; i++) if (all[i].length() > 0) o.add(all[i]);
        return o;
    }

    public static synchronized void clear() {
        if (dir == null) return;
        new File(dir, "journal.txt").delete();
        new File(dir, "journal.1.txt").delete();
        last.clear();
    }
}
