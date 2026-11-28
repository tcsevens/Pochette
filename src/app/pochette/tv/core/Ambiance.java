package app.pochette.tv.core;

import app.pochette.tv.sonos.SonosClient;

import java.util.ArrayList;
import java.util.List;

/** A one-press preset: a Sonos favourite + rooms + volume + screen theme. */
public final class Ambiance {
    public static final int T_NORMAL = 0, T_SNOW = 1, T_MORNING = 2, T_SUMMER = 3, T_WAKE = 4, T_SOUL = 5, T_FIRE = 6, T_RAIN = 7, T_PLANET = 8, T_PARTY = 9, T_OFFICE = 10, T_MONTREAL = 11, T_CORSE = 12, T_XMAS = 13, T_RECORD = 14, T_AUTUMN = 15, T_HALLOWEEN = 16, T_CUSTOM1 = 17, T_CUSTOM2 = 18, T_CUSTOM3 = 19, T_WEATHER = 20, T_CLOUDS = 21, T_FOG = 22, T_SNOWFALL = 23, T_STORM = 24, T_NIGHTRAIN = 25, T_SUNSET = 26, T_DAYRAIN = 27;
    /** Themes still offered (the others were removed; saved ones fall back to Normal). */
    private static final int[] BASE = {T_NORMAL, T_SUMMER, T_AUTUMN, T_HALLOWEEN, T_SNOW, T_FIRE, T_XMAS, T_MORNING, T_WAKE, T_RAIN, T_DAYRAIN, T_NIGHTRAIN, T_STORM, T_SNOWFALL, T_FOG, T_CLOUDS, T_SUNSET, T_PLANET, T_SOUL, T_RECORD, T_PARTY};
    public static volatile int[] SHOWN = BASE;
    /** Rebuilds the list: the built-in themes, then the owner's own videos that exist. */
    public static void refresh() {
        int n = 0;
        for (int i = 0; i < CustomBg.SLOTS; i++) if (CustomBg.has(i)) n++;
        int[] o = new int[BASE.length + n];
        System.arraycopy(BASE, 0, o, 0, BASE.length);
        int k = BASE.length;
        for (int i = 0; i < CustomBg.SLOTS; i++) if (CustomBg.has(i)) o[k++] = CustomBg.themeOf(i);
        SHOWN = o;
    }
    public static boolean exists(int t) { if (t == T_WEATHER) return true; for (int x : SHOWN) if (x == t) return true; return false; }
    public static int valid(int t) { return exists(t) ? t : T_NORMAL; }
    public static int toIndex(int t) { for (int i = 0; i < SHOWN.length; i++) if (SHOWN[i] == t) return i; return 0; }
    public static int fromIndex(int i) { return SHOWN[Math.max(0, Math.min(SHOWN.length - 1, i))]; }
    public static String[] shownNames() { String[] o = new String[SHOWN.length]; for (int i = 0; i < o.length; i++) o[i] = name(SHOWN[i]); return o; }
    /** Display name of a theme (French key; the owner's own name for a custom background). */
    public static String name(int t) {
        int s = CustomBg.slotOf(t);
        if (s >= 0 && CustomBg.name(s).length() > 0) return CustomBg.name(s);
        return THEME_NAMES[Math.max(0, Math.min(t, THEME_NAMES.length - 1))];
    }
    public static final String[] THEME_NAMES = {"Normal", "Chalet", "Lever de soleil", "Été", "Réveil", "Bar jazz", "Coin du feu", "Pluie de jour", "Nuit étoilée", "Fête", "Bureau chill", "Feu de cheminée", "Corse", "Noël", "Disquaire", "Automne", "Halloween", "Mon fond 1", "Mon fond 2", "Mon fond 3", "Météo", "Nuages", "Brouillard", "Neige", "Orage", "Pluie de nuit", "Coucher de soleil", "Pluie chalet"};

    public String name = "Nouvelle ambiance";
    public int theme = T_NORMAL;
    /** Title of the Sonos favourite to play ("" = not chosen yet). */
    public String fav = "";
    /** Room UUIDs; empty = keep the rooms that are playing. */
    public final List<String> rooms = new ArrayList<String>();
    /** Start volume in %, -1 = leave as is. */
    public int volume = -1;
    public boolean shuffle = true;
    /** Not saved: start on a random track of the queue (music games, so the first song is never the same). */
    public boolean randomStart;
    /** Volume rises slowly to the target over 3 minutes. */
    public boolean ramp;
    public int sleepMin;
    /** Screen style imposed when launched: -1 = leave as is, otherwise NowPlayingView.MODE_*. */
    public int display = -1;
    /** Further screen options imposed when launched; -1 = leave as is. */
    public int vinyl = -1, lyrics = -1, kcolor = -1;
    /** Clock in the corner: -1 leave as is, 0 off, 1 on. */
    public int clock = -1;
    /** Weather card: -1 leave as is, 0 hide, 1 show. */
    public int weather = -1;
    public boolean preset;
    /** The built-in weather ambiance: its screen follows the weather. */
    public boolean isWeather() { return theme == T_WEATHER; }

    private static final String F = "\u001f", R = "\u001e";

    /** No built-in ambiances: each person builds their own from their Sonos favourites. */
    public static List<Ambiance> defaults() { return new ArrayList<Ambiance>(); }

    private static Ambiance make(String name, int theme, int vol, boolean ramp) {
        Ambiance a = new Ambiance();
        a.name = name; a.theme = theme; a.volume = vol; a.ramp = ramp; a.preset = true;
        return a;
    }

    public static final String NOW = "\u0001now", PL = "\u0001pl:";

    /** What is shown for a stored source (favourite title, Sonos playlist or the current queue). */
    public static String label(String fav) {
        if (NOW.equals(fav)) return T.t("Ce qui joue en ce moment");
        if (fav != null && fav.startsWith(PL)) return fav.substring(PL.length());
        return fav == null ? "" : fav;
    }

    /** The key stored for a source item. */
    public static String keyOf(SonosClient.Item it) {
        if (it.kind == 3) return NOW;
        if (it.kind == 2) return PL + it.title;
        return it.title;
    }

    public static String encode(List<Ambiance> list) {
        StringBuilder sb = new StringBuilder();
        for (Ambiance a : list) {
            StringBuilder rooms = new StringBuilder();
            for (String r : a.rooms) { if (rooms.length() > 0) rooms.append(','); rooms.append(r); }
            if (sb.length() > 0) sb.append(R);
            sb.append(a.name).append(F).append(a.theme).append(F).append(a.fav).append(F).append(rooms).append(F)
                    .append(a.volume).append(F).append(a.shuffle ? 1 : 0).append(F).append(a.ramp ? 1 : 0).append(F)
                    .append(a.sleepMin).append(F).append(a.preset ? 1 : 0).append(F).append(a.display)
                    .append(F).append(a.vinyl).append(F).append(a.lyrics).append(F).append(a.kcolor).append(F).append(a.clock).append(F).append(a.weather);
        }
        return sb.toString();
    }

    public static List<Ambiance> decode(String s) {
        if (s == null || s.length() == 0) return defaults();
        List<Ambiance> l = new ArrayList<Ambiance>();
        try {
            for (String rec : s.split(R)) {
                String[] f = rec.split(F, -1);
                if (f.length < 9) continue;
                Ambiance a = new Ambiance();
                a.name = f[0]; a.theme = valid(Integer.parseInt(f[1])); a.fav = f[2];
                for (String r : f[3].split(",")) if (r.length() > 0) a.rooms.add(r);
                a.volume = Integer.parseInt(f[4]); a.shuffle = "1".equals(f[5]); a.ramp = "1".equals(f[6]);
                a.sleepMin = Integer.parseInt(f[7]); a.preset = "1".equals(f[8]);
                a.display = f.length >= 10 ? Integer.parseInt(f[9]) : (a.theme == T_SOUL ? 1 : -1);   // older saves: jazz/soul used to force the vinyl
                if (f.length >= 14) { a.vinyl = Integer.parseInt(f[10]); a.lyrics = Integer.parseInt(f[11]); a.kcolor = Integer.parseInt(f[12]); a.clock = Integer.parseInt(f[13]); }
                if (f.length >= 15) a.weather = Integer.parseInt(f[14]);
                l.add(a);
            }
        } catch (Exception e) {
            return defaults();
        }
        return l.isEmpty() ? defaults() : l;
    }
}
