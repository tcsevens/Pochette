package app.pochette.tv.menu;

/** Small helpers for rows whose options map to stored values. */
final class Choices {
    private Choices() { }

    /** Index of {@code v} in {@code a}, or 0 when absent. */
    static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return 0;
    }
}
