package app.pochette.tv.core;

/**
 * Minimal QR code encoder (byte mode, error correction level L, versions 1 to 10), so the app can show a
 * scannable address without any library. {@link #encode} returns the module matrix, true = dark.
 */
public final class QrCode {
    private static final int[] ECC_PER_BLOCK = {7, 10, 15, 20, 26, 18, 20, 24, 30, 18};
    private static final int[] BLOCKS = {1, 1, 1, 1, 1, 2, 2, 2, 2, 4};

    private final int ver, size;
    private final boolean[][] mod;
    private final boolean[][] fn;

    private QrCode(int ver, byte[] data) {
        this.ver = ver;
        this.size = ver * 4 + 17;
        mod = new boolean[size][size];
        fn = new boolean[size][size];
        drawFunctionPatterns();
        drawCodewords(addEccAndInterleave(data));
        int best = 0;
        long bestPen = Long.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            applyMask(m);
            drawFormat(m);
            long p = penalty();
            if (p < bestPen) { bestPen = p; best = m; }
            applyMask(m);   // undo
        }
        applyMask(best);
        drawFormat(best);
    }

    /** Returns the modules, or null if the text does not fit (about 270 bytes at most). */
    public static boolean[][] encode(String text) {
        byte[] t;
        try { t = text.getBytes("UTF-8"); } catch (Exception e) { return null; }
        for (int v = 1; v <= 10; v++) {
            int cap = dataCodewords(v);
            int lenBits = v < 10 ? 8 : 16;
            int need = 4 + lenBits + t.length * 8;
            if (need > cap * 8) continue;
            int[] bits = new int[cap * 8 + 8];
            int n = 0;
            n = put(bits, n, 4, 4);
            n = put(bits, n, t.length, lenBits);
            for (byte b : t) n = put(bits, n, b & 0xFF, 8);
            n = Math.min(n + 4, cap * 8);        // terminator (zeros)
            n = (n + 7) / 8 * 8;
            byte[] data = new byte[cap];
            for (int i = 0; i < n; i++) if (bits[i] != 0) data[i / 8] |= 1 << (7 - i % 8);
            for (int i = n / 8, pad = 0xEC; i < cap; i++, pad ^= 0xEC ^ 0x11) data[i] = (byte) pad;
            return new QrCode(v, data).mod;
        }
        return null;
    }

    private static int put(int[] bits, int pos, int val, int len) {
        for (int i = len - 1; i >= 0; i--) bits[pos++] = (val >>> i) & 1;
        return pos;
    }

    // ------------------------------------------------------------ sizes

    private static int rawModules(int v) {
        int r = (16 * v + 128) * v + 64;
        if (v >= 2) {
            int a = v / 7 + 2;
            r -= (25 * a - 10) * a - 55;
            if (v >= 7) r -= 36;
        }
        return r;
    }

    private static int dataCodewords(int v) {
        return rawModules(v) / 8 - ECC_PER_BLOCK[v - 1] * BLOCKS[v - 1];
    }

    // ------------------------------------------------------------ Reed-Solomon

    private static int gfMul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private static byte[] rsDivisor(int degree) {
        byte[] r = new byte[degree];
        r[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                r[j] = (byte) gfMul(r[j] & 0xFF, root);
                if (j + 1 < degree) r[j] ^= r[j + 1];
            }
            root = gfMul(root, 0x02);
        }
        return r;
    }

    private static byte[] rsRemainder(byte[] data, int off, int len, byte[] div) {
        byte[] r = new byte[div.length];
        for (int k = 0; k < len; k++) {
            int f = (data[off + k] & 0xFF) ^ (r[0] & 0xFF);
            System.arraycopy(r, 1, r, 0, r.length - 1);
            r[r.length - 1] = 0;
            for (int i = 0; i < r.length; i++) r[i] ^= gfMul(div[i] & 0xFF, f);
        }
        return r;
    }

    private byte[] addEccAndInterleave(byte[] data) {
        int blocks = BLOCKS[ver - 1], ecc = ECC_PER_BLOCK[ver - 1];
        int raw = rawModules(ver) / 8;
        int shortLen = raw / blocks;
        int numShort = blocks - raw % blocks;
        byte[][] dat = new byte[blocks][];
        byte[][] ec = new byte[blocks][];
        byte[] div = rsDivisor(ecc);
        int k = 0;
        for (int i = 0; i < blocks; i++) {
            int dl = shortLen - ecc + (i < numShort ? 0 : 1);
            dat[i] = new byte[dl];
            System.arraycopy(data, k, dat[i], 0, dl);
            k += dl;
            ec[i] = rsRemainder(dat[i], 0, dl, div);
        }
        byte[] out = new byte[raw];
        int o = 0;
        for (int i = 0; i <= shortLen - ecc; i++)
            for (int j = 0; j < blocks; j++)
                if (i < dat[j].length) out[o++] = dat[j][i];
        for (int i = 0; i < ecc; i++)
            for (int j = 0; j < blocks; j++) out[o++] = ec[j][i];
        return out;
    }

    // ------------------------------------------------------------ drawing

    private void set(int x, int y, boolean dark) { mod[y][x] = dark; fn[y][x] = true; }

    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0); }
        finder(3, 3); finder(size - 4, 3); finder(3, size - 4);
        int[] al = alignPositions();
        for (int i = 0; i < al.length; i++)
            for (int j = 0; j < al.length; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == al.length - 1) || (i == al.length - 1 && j == 0)) continue;
                align(al[i], al[j]);
            }
        drawFormat(0);
        drawVersion();
    }

    private void finder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++)
            for (int dx = -4; dx <= 4; dx++) {
                int d = Math.max(Math.abs(dx), Math.abs(dy));
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && x < size && y >= 0 && y < size) set(x, y, d != 2 && d != 4);
            }
    }

    private void align(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++)
                set(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
    }

    private int[] alignPositions() {
        if (ver == 1) return new int[0];
        int n = ver / 7 + 2;
        int step = (ver == 32) ? 26 : (ver * 4 + n * 2 + 1) / (n * 2 - 2) * 2;
        int[] r = new int[n];
        r[0] = 6;
        for (int i = n - 1, pos = size - 7; i >= 1; i--, pos -= step) r[i] = pos;
        return r;
    }

    private void drawFormat(int mask) {
        int data = (1 << 3) | mask;            // error correction level L = 01
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = ((data << 10) | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(8, i, bit(bits, i));
        set(8, 7, bit(bits, 6));
        set(8, 8, bit(bits, 7));
        set(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(8, size - 15 + i, bit(bits, i));
        set(8, size - 8, true);
    }

    private void drawVersion() {
        if (ver < 7) return;
        int rem = ver;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        int bits = (ver << 12) | rem;
        for (int i = 0; i < 18; i++) {
            boolean b = bit(bits, i);
            int a = size - 11 + i % 3, c = i / 3;
            set(a, c, b);
            set(c, a, b);
        }
    }

    private static boolean bit(int x, int i) { return ((x >>> i) & 1) != 0; }

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++)
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean up = ((right + 1) & 2) == 0;
                    int y = up ? size - 1 - vert : vert;
                    if (!fn[y][x] && i < data.length * 8) {
                        mod[y][x] = bit(data[i >>> 3] & 0xFF, 7 - (i & 7));
                        i++;
                    }
                }
        }
    }

    private void applyMask(int m) {
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                boolean inv;
                switch (m) {
                    case 0: inv = (x + y) % 2 == 0; break;
                    case 1: inv = y % 2 == 0; break;
                    case 2: inv = x % 3 == 0; break;
                    case 3: inv = (x + y) % 3 == 0; break;
                    case 4: inv = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: inv = x * y % 2 + x * y % 3 == 0; break;
                    case 6: inv = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    default: inv = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                }
                if (inv && !fn[y][x]) mod[y][x] = !mod[y][x];
            }
    }

    // ------------------------------------------------------------ mask choice (penalty rules 1-4)

    private long penalty() {
        long p = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < size; a++) {
                int run = 1;
                for (int b = 1; b < size; b++) {
                    boolean cur = pass == 0 ? mod[a][b] : mod[b][a];
                    boolean prev = pass == 0 ? mod[a][b - 1] : mod[b - 1][a];
                    if (cur == prev) {
                        run++;
                        if (run == 5) p += 3; else if (run > 5) p++;
                    } else run = 1;
                }
            }
        }
        for (int y = 0; y < size - 1; y++)
            for (int x = 0; x < size - 1; x++) {
                boolean c = mod[y][x];
                if (c == mod[y][x + 1] && c == mod[y + 1][x] && c == mod[y + 1][x + 1]) p += 3;
            }
        int dark = 0;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) if (mod[y][x]) dark++;
        int total = size * size;
        int k = (int) ((Math.abs(dark * 20L - total * 10L) + total - 1) / total) - 1;
        p += k * 10L;
        return p;
    }
}
