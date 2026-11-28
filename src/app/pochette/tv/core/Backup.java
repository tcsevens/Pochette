package app.pochette.tv.core;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Settings and ambiances as one small JSON file, so they can move to another TV or survive a reinstall.
 * Left out on purpose: sign-in tokens, the guest Wi-Fi password and the room's network address; the owner's own
 * background videos are too large and stay on the TV.
 */
public final class Backup {
    private Backup() { }

    private static final Set<String> SKIP = new HashSet<String>();
    static {
        String[] k = {"sp_access", "sp_exp", "sp_refresh", "sp_state", "sp_ver", "sp_last", "sp_on", "wifi_pass", "wifi_ssid", "queue_token",
                "last_ip", "last_meta", "last_pos", "last_track", "last_uri", "last_coord", "manual_ip", "resume_open", "wx_aat", "wx_acity", "wx_alat", "wx_alon"};
        for (int i = 0; i < k.length; i++) SKIP.add(k[i]);
    }

    public static String export(SharedPreferences p, String version) {
        try {
            JSONObject root = new JSONObject();
            root.put("app", "pochette");
            root.put("format", 1);
            root.put("version", version);
            JSONObject o = new JSONObject();
            for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
                String key = e.getKey();
                Object v = e.getValue();
                if (SKIP.contains(key) || v == null) continue;
                JSONObject one = new JSONObject();
                if (v instanceof Boolean) { one.put("t", "b"); one.put("v", ((Boolean) v).booleanValue()); }
                else if (v instanceof Integer) { one.put("t", "i"); one.put("v", ((Integer) v).intValue()); }
                else if (v instanceof Long) { one.put("t", "l"); one.put("v", ((Long) v).longValue()); }
                else if (v instanceof Float) { one.put("t", "f"); one.put("v", ((Float) v).doubleValue()); }
                else if (v instanceof String) { one.put("t", "s"); one.put("v", (String) v); }
                else continue;
                o.put(key, one);
            }
            root.put("prefs", o);
            return root.toString(1);
        } catch (Exception ex) {
            Journal.w("Sauvegarde", ex);
            return "";
        }
    }

    /** Applies a backup. Returns the number of settings restored, or -1 when the file is not a Pochette backup. */
    public static int restore(SharedPreferences p, String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (!"pochette".equals(root.optString("app")) || root.optInt("format", 0) != 1) return -1;
            JSONObject o = root.getJSONObject("prefs");
            SharedPreferences.Editor ed = p.edit();
            int n = 0;
            JSONArray names = o.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                String key = names.getString(i);
                if (SKIP.contains(key)) continue;
                JSONObject one = o.getJSONObject(key);
                String t = one.optString("t");
                if ("b".equals(t)) ed.putBoolean(key, one.getBoolean("v"));
                else if ("i".equals(t)) ed.putInt(key, one.getInt("v"));
                else if ("l".equals(t)) ed.putLong(key, one.getLong("v"));
                else if ("f".equals(t)) ed.putFloat(key, (float) one.getDouble("v"));
                else if ("s".equals(t)) ed.putString(key, one.getString("v"));
                else continue;
                n++;
            }
            ed.commit();
            return n;
        } catch (Exception ex) {
            return -1;
        }
    }
}
