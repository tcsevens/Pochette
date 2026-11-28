package app.pochette.tv.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Weather mode: current conditions and short forecast from Open-Meteo (no account, no key).
 * The place is the city typed in Settings, or found from the public IP address; no location permission is used.
 */
public final class Weather {
    public static final int CLEAR = 0, CLOUDY = 1, FOG = 2, RAIN = 3, SNOW = 4, STORM = 5;
    public static final String[] COND_NAMES = {"Dégagé", "Nuageux", "Brouillard", "Pluie", "Neige", "Orage"};
    /** Refresh periods offered, in minutes. */
    public static final int[] REFRESH_MIN = {15, 30, 60, 180, 360};

    /** One forecast slot. */
    public static final class Slot {
        public int hour, cond;
        public double temp;
        public boolean day;
    }

    public static final class Data {
        public int cond;
        public boolean day;
        public double temp, max, min;
        public int tomorrowCond;
        public double tomorrowMax, tomorrowMin;
        public String place = "";
        public long at;
        public final List<Slot> next = new ArrayList<Slot>();
    }

    private static volatile Data data;
    private static volatile boolean busy, again;

    public static Data data() { return demo != null ? demo : data; }

    // ---- demo: every background in turn, with made-up figures
    private static volatile Data demo;
    public static boolean demoActive() { return demo != null; }
    public static final int DEMO_STEPS = 10;
    /** Shows step i (0..9) of the demo; -1 ends it. Returns a short label of what is shown. */
    public static String demoStep(int i) {
        if (i < 0 || i >= DEMO_STEPS) { demo = null; return ""; }
        int[] cond = {CLEAR, CLEAR, CLOUDY, CLOUDY, FOG, FOG, RAIN, RAIN, SNOW, STORM};
        boolean[] day = {true, false, true, false, true, false, true, false, true, false};
        double[] t = {18, 9, 12, 5, 4, 1, 10, 6, -4, 14};
        Data d = new Data();
        d.cond = cond[i]; d.day = day[i]; d.temp = t[i]; d.max = t[i] + 3; d.min = t[i] - 6; d.place = "Demo"; d.at = System.currentTimeMillis();
        int[] nx = {CLOUDY, RAIN, SNOW, CLEAR};
        for (int k = 0; k < 4; k++) { Slot s = new Slot(); s.hour = (12 + 3 * (k + 1)) % 24; s.cond = nx[(k + i) % 4]; s.temp = t[i] - k; s.day = s.hour >= 7 && s.hour < 19; d.next.add(s); }
        demo = d;
        return COND_NAMES[d.cond];
    }
    /** For the visual tests. */
    public static void inject(Data d) { data = d; }

    // ---------------------------------------------------------------- conditions

    /** WMO weather code to one of the six conditions. */
    public static int condOf(int code) {
        if (code <= 1) return CLEAR;
        if (code <= 3) return CLOUDY;
        if (code == 45 || code == 48) return FOG;
        if (code >= 95) return STORM;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 51) return RAIN;
        return CLOUDY;
    }

    /** What to show behind the music, from the latest data (the clock decides until the first answer). */
    public static final int K_CLEAR_DAY = 0, K_CLEAR_NIGHT = 1, K_CLOUD_DAY = 2, K_CLOUD_NIGHT = 3, K_FOG_DAY = 4, K_FOG_NIGHT = 5,
            K_RAIN_DAY = 6, K_RAIN_NIGHT = 7, K_SNOW = 8, K_STORM = 9;

    public static int kind() {
        Data d = data();
        int cond = CLEAR;
        boolean day;
        if (d != null) { cond = d.cond; day = d.day; }
        else { int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY); day = h >= 7 && h < 19; }
        switch (cond) {
            case CLOUDY: return day ? K_CLOUD_DAY : K_CLOUD_NIGHT;
            case FOG: return day ? K_FOG_DAY : K_FOG_NIGHT;
            case RAIN: return day ? K_RAIN_DAY : K_RAIN_NIGHT;
            case SNOW: return K_SNOW;
            case STORM: return K_STORM;
            default: return day ? K_CLEAR_DAY : K_CLEAR_NIGHT;
        }
    }

    /** Bundled clip for a kind, or null when the scene is drawn. */
    public static String videoOf(int kind) {
        switch (kind) {
            case K_CLEAR_DAY: return "vid_beau_temps";
            case K_CLOUD_DAY: return "vid_nuages";
            case K_FOG_DAY: return "vid_brouillard";
            case K_RAIN_DAY: return "vid_pluie_jour";
            case K_RAIN_NIGHT: return "vid_pluie_nuit";
            case K_SNOW: return "vid_neige";
            case K_STORM: return "vid_orage";
            default: return null;
        }
    }

    // ---------------------------------------------------------------- units

    public static boolean fahrenheit(Prefs p) {
        int u = p.weatherUnits();
        if (u == 1) return false;
        if (u == 2) return true;
        String c = Locale.getDefault().getCountry();
        return "US".equals(c) || "LR".equals(c) || "MM".equals(c) || "BS".equals(c) || "BZ".equals(c) || "KY".equals(c) || "PW".equals(c);
    }

    public static String temp(double celsius, boolean f) {
        long v = Math.round(f ? celsius * 9.0 / 5.0 + 32.0 : celsius);
        return (v == 0 ? 0 : v) + "°";
    }

    // ---------------------------------------------------------------- refresh

    /** Refreshes in the background; done runs on that thread when the data changed or the attempt ended. */
    public static void refresh(final Prefs p, final Runnable done) {
        if (busy) { again = true; return; }
        busy = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    double[] ll = locate(p);
                    if (ll == null) return;
                    String name = p.weatherLat().length() > 0 ? p.weatherCity() : p.weatherAutoCity();
                    String url = "https://api.open-meteo.com/v1/forecast?latitude=" + ll[0] + "&longitude=" + ll[1]
                            + "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day"
                            + "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=2&timezone=auto";
                    Data d = parse(get(url), name);
                    if (d != null) data = d;
                } catch (Exception e) {
                    Journal.w("Weather", e);
                } finally {
                    busy = false;
                    if (done != null) done.run();
                    if (again) { again = false; refresh(p, done); }
                }
            }
        }, "weather").start();
    }

    /** Coordinates of the typed city, else the IP-based place (cached for a day). */
    private static double[] locate(Prefs p) throws Exception {
        if (p.weatherLat().length() > 0) {
            try { return new double[]{Double.parseDouble(p.weatherLat()), Double.parseDouble(p.weatherLon())}; } catch (NumberFormatException e) { /* fall through */ }
        }
        long now = System.currentTimeMillis();
        if (p.weatherAutoAt() > 0 && now - p.weatherAutoAt() < 24L * 3600 * 1000 && p.weatherAutoLat().length() > 0)
            return new double[]{Double.parseDouble(p.weatherAutoLat()), Double.parseDouble(p.weatherAutoLon())};
        String[] r = parseIp(tryGet("https://ipwho.is/"));
        if (r == null) r = parseIp(tryGet("https://ipapi.co/json/"));
        if (r == null) {
            if (p.weatherAutoLat().length() > 0) return new double[]{Double.parseDouble(p.weatherAutoLat()), Double.parseDouble(p.weatherAutoLon())};
            return null;
        }
        p.weatherAuto(r[0], r[1], r[2]);
        return new double[]{Double.parseDouble(r[0]), Double.parseDouble(r[1])};
    }

    /** Looks a city up by name: {lat, lon, display name} or null. */
    public static String[] geocode(String name, String lang) {
        try {
            String u = "https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&language=" + lang + "&name=" + URLEncoder.encode(name, "UTF-8");
            return parseGeo(get(u));
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- parsing (public for the tests)

    public static String[] parseGeo(String json) {
        try {
            JSONArray a = new JSONObject(json).optJSONArray("results");
            if (a == null || a.length() == 0) return null;
            JSONObject o = a.getJSONObject(0);
            String n = o.optString("name", "");
            String reg = o.optString("admin1", "");
            if (reg.length() > 0 && !reg.equals(n)) n += ", " + reg;
            return new String[]{String.valueOf(o.getDouble("latitude")), String.valueOf(o.getDouble("longitude")), n};
        } catch (Exception e) {
            return null;
        }
    }

    public static String[] parseIp(String json) {
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            if (o.has("success") && !o.optBoolean("success", true)) return null;
            if (o.optBoolean("error", false)) return null;
            if (!o.has("latitude") || !o.has("longitude")) return null;
            return new String[]{String.valueOf(o.getDouble("latitude")), String.valueOf(o.getDouble("longitude")), o.optString("city", "")};
        } catch (Exception e) {
            return null;
        }
    }

    public static Data parse(String json, String place) {
        try {
            JSONObject o = new JSONObject(json);
            JSONObject cur = o.getJSONObject("current");
            Data d = new Data();
            d.place = place == null ? "" : place;
            d.temp = cur.getDouble("temperature_2m");
            d.cond = condOf(cur.getInt("weather_code"));
            d.day = cur.optInt("is_day", 1) == 1;
            d.at = System.currentTimeMillis();
            JSONObject h = o.getJSONObject("hourly");
            JSONArray ht = h.getJSONArray("time"), hv = h.getJSONArray("temperature_2m"), hc = h.getJSONArray("weather_code");
            JSONArray hd = h.optJSONArray("is_day");
            String now = cur.getString("time");
            int i = 0;
            for (int k = 0; k < ht.length(); k++) if (ht.getString(k).compareTo(now) <= 0) i = k;
            for (int step = 3; step <= 12; step += 3) {
                int k = i + step;
                if (k >= ht.length()) break;
                Slot s = new Slot();
                String t = ht.getString(k);
                s.hour = Integer.parseInt(t.substring(11, 13));
                s.temp = hv.getDouble(k);
                s.cond = condOf(hc.getInt(k));
                s.day = hd != null ? hd.optInt(k, 1) == 1 : (s.hour >= 7 && s.hour < 19);
                d.next.add(s);
            }
            JSONObject dl = o.optJSONObject("daily");
            if (dl != null) {
                JSONArray mx = dl.getJSONArray("temperature_2m_max"), mn = dl.getJSONArray("temperature_2m_min"), dc = dl.getJSONArray("weather_code");
                d.max = mx.getDouble(0); d.min = mn.getDouble(0);
                if (mx.length() > 1) { d.tomorrowMax = mx.getDouble(1); d.tomorrowMin = mn.getDouble(1); d.tomorrowCond = condOf(dc.getInt(1)); }
            }
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- http

    private static String tryGet(String u) {
        try { return get(u); } catch (Exception e) { return null; }
    }

    private static String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "Pochette");
        try {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return new String(b.toByteArray(), "UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
