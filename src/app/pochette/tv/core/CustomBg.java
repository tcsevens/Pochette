package app.pochette.tv.core;

import android.content.Context;

import java.io.File;

/** Videos the owner sent from a phone, kept in the app's private storage (three slots). */
public final class CustomBg {
    public static final int SLOTS = 3;
    private static File dir;

    private CustomBg() { }

    public static void init(Context c) {
        dir = new File(c.getFilesDir(), "bg");
        dir.mkdirs();
        Ambiance.refresh();
    }

    public static File file(int slot) { return new File(dir, "bg" + slot + ".mp4"); }
    public static File temp(int slot) { return new File(dir, "bg" + slot + ".part"); }
    public static boolean has(int slot) { return dir != null && file(slot).length() > 0; }
    public static int slotOf(int theme) { int s = theme - Ambiance.T_CUSTOM1; return s >= 0 && s < SLOTS ? s : -1; }
    public static int themeOf(int slot) { return Ambiance.T_CUSTOM1 + slot; }

    /** The upload finished: the temporary file becomes the slot's video. */
    public static synchronized boolean commit(int slot) {
        File f = file(slot);
        f.delete();
        boolean ok = temp(slot).renameTo(f);
        Ambiance.refresh();
        return ok;
    }

    public static synchronized void delete(int slot) {
        file(slot).delete();
        temp(slot).delete();
        Ambiance.refresh();
    }

    /** First empty slot, or -1. */
    public static int freeSlot() { for (int i = 0; i < SLOTS; i++) if (!has(i)) return i; return -1; }
}
