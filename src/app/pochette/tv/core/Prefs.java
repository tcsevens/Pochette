package app.pochette.tv.core;

import app.pochette.tv.ui.NowPlayingView;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private final SharedPreferences p;

    public Prefs(Context c) { p = c.getSharedPreferences("pochette", Context.MODE_PRIVATE); }

    /** "" = automatic (whichever room is playing). */
    public String roomUuid() { return p.getString("room", ""); }
    public void roomUuid(String v) { p.edit().putString("room", v).apply(); }

    /** Guest Wi-Fi shown to guests as a QR code (stored on this TV only). */
    public String wifiSsid() { return p.getString("wifi_ssid", ""); }
    public String wifiPass() { return p.getString("wifi_pass", ""); }
    public void wifi(String ssid, String pass) { p.edit().putString("wifi_ssid", ssid).putString("wifi_pass", pass).apply(); }
    public boolean wifiOffer() { return p.getBoolean("wifi_offer", true); }
    public void wifiOffer(boolean v) { p.edit().putBoolean("wifi_offer", v).apply(); }
    /** The QR payload to join the guest network, or "" when none is set up or offering is off. */
    public String wifiQr() { return wifiOffer() && wifiSsid().length() > 0 ? WifiQr.payload(wifiSsid(), wifiPass()) : ""; }
    /** Secret of the shared-queue link: stable, so a guest can keep the link; renewing it cuts old links. */
    public String queueToken() {
        String t = p.getString("queue_token", "");
        if (t.length() == 0) { t = newToken(); p.edit().putString("queue_token", t).apply(); }
        return t;
    }
    public void renewQueueToken() { p.edit().putString("queue_token", newToken()).apply(); }
    private static String newToken() { return Long.toString(Math.abs(new java.util.Random().nextLong()), 36).substring(0, 8); }
    /** Keep the shared-queue page up whenever Pochette is open, so saved links work. */
    public boolean queueKeep() { return p.getBoolean("queue_keep", true); }
    public void queueKeep(boolean v) { p.edit().putBoolean("queue_keep", v).apply(); }
    public String manualIp() { return p.getString("manual_ip", ""); }
    public void manualIp(String v) { p.edit().putString("manual_ip", v).apply(); }

    public String lastIp() { return p.getString("last_ip", ""); }
    public void lastIp(String v) { p.edit().putString("last_ip", v).apply(); }

    /** Display style: 0 Classic, 1 Illustrated, 2 Lyrics (the old "Immersif" was removed; old saved values are mapped). */
    public int mode() {
        if (p.getInt("mode_v", 0) >= 2) return Math.max(0, Math.min(2, p.getInt("mode2", 0)));
        int old = p.getInt("mode", 0);
        return old == 2 ? 1 : old == 3 ? 2 : 0;
    }
    public void mode(int v) { p.edit().putInt("mode2", v).putInt("mode_v", 2).apply(); }

    public boolean autoLaunch() { return p.getBoolean("auto", true); }
    public void autoLaunch(boolean v) { p.edit().putBoolean("auto", v).apply(); }

    /** 0 = word-by-word karaoke, 1 = line by line, 2 = off. */
    public int lyricsMode() { return p.getInt("lyrics_mode", p.getBoolean("lyrics", true) ? 0 : 2); }
    public void lyricsMode(int v) { p.edit().putInt("lyrics_mode", v).apply(); }

    /** Colour of the sung part in the karaoke lyrics (index into NowPlayingView.KARAOKE_COLORS, 0 = green). */
    public int karaokeColor() { int v = p.getInt("karaoke_color", 0); return v >= 0 && v < NowPlayingView.KARAOKE_COLORS.length ? v : 0; }
    public void karaokeColor(int v) { p.edit().putInt("karaoke_color", v).apply(); }

    /** 0 = black, 1 = album colour, 2 = marbled, 3 = picture disc. */
    public int vinylStyle() { int v = p.getInt("vinyl_style", 1); return v >= 0 && v <= 2 ? v : 1; }
    public void vinylStyle(int v) { p.edit().putInt("vinyl_style", v).apply(); }

    /** Lyrics view without lyrics: 0 = grande pochette, 1 = pochette + vinyle. */
    public int noLyricsStyle() { return p.getInt("no_lyrics", 1); }
    public void noLyricsStyle(int v) { p.edit().putInt("no_lyrics", v).apply(); }

    /** Last music played (to resume it with Play, even after the soundbar switched to TV). */
    public String lastUri() { return p.getString("last_uri", ""); }
    public String lastMeta() { return p.getString("last_meta", ""); }
    public int lastTrack() { return p.getInt("last_track", 0); }
    public int lastPos() { return p.getInt("last_pos", 0); }
    public String lastCoord() { return p.getString("last_coord", ""); }
    public void saveLast(String uri, String meta, int track, int pos, String coord) {
        p.edit().putString("last_uri", uri).putString("last_meta", meta).putInt("last_track", track)
                .putInt("last_pos", pos).putString("last_coord", coord).putBoolean("sp_last", false).apply();
    }

    public boolean resumeOnOpen() { return p.getBoolean("resume_open", false); }
    public void resumeOnOpen(boolean v) { p.edit().putBoolean("resume_open", v).apply(); }

    /** Menu opacity: 0 = very transparent, 1 = medium, 2 = almost opaque. */
    public int stripSec() { return p.getInt("strip_sec", 10); }
    public void stripSec(int v) { p.edit().putInt("strip_sec", v).apply(); }
    public int menuOpacity() { return p.getInt("menu_opacity", 1); }
    public void menuOpacity(int v) { p.edit().putInt("menu_opacity", v).apply(); }

    /** Lyrics offset in ms (positive = lyrics later). */
    public int lyricsOffset() { return p.getInt("lyrics_offset", 0); }
    public void lyricsOffset(int v) { p.edit().putInt("lyrics_offset", v).apply(); }

    public java.util.List<Ambiance> ambiances() {
        if (!p.getBoolean("amb_reset_v3", false)) {   // one-time clean start: the ready-made models are gone
            p.edit().remove("ambiances").putBoolean("amb_reset_v3", true).putBoolean("amb_vol_v2", true).apply();
        }
        java.util.List<Ambiance> l = Ambiance.decode(p.getString("ambiances", ""));
        if (!p.getBoolean("amb_vol_v2", false)) {
            // Default volume is now "unchanged": reset the old built-in numbers (30/20/35 %) that were never chosen by hand.
            boolean any = false;
            for (Ambiance a : l) {
                if (a.preset && (a.volume == 30 || a.volume == 20 || a.volume == 35)) { a.volume = -1; any = true; }
            }
            if (any) p.edit().putString("ambiances", Ambiance.encode(l)).putBoolean("amb_vol_v2", true).apply();
            else p.edit().putBoolean("amb_vol_v2", true).apply();
        }
        return l;
    }
    public void ambiances(java.util.List<Ambiance> l) { p.edit().putString("ambiances", Ambiance.encode(l)).apply(); }

    /** Screen theme (Ambiance.T_*) and the name shown with it. */
    public int theme() { return p.getInt("theme", 0); }
    public String themeName() { return p.getString("theme_name", ""); }
    public long themeAt() { return p.getLong("theme_at", 0); }
    public void theme(int t, String name) {
        p.edit().putInt("theme", t).putString("theme_name", name).putLong("theme_at", System.currentTimeMillis()).apply();
    }

    public boolean menuRight() { return p.getBoolean("menu_right", false); }
    public void menuRight(boolean v) { p.edit().putBoolean("menu_right", v).apply(); }

    public boolean showClock() { return p.getBoolean("clock", true); }
    public void showClock(boolean v) { p.edit().putBoolean("clock", v).apply(); }

    public boolean hintShown() { return p.getBoolean("hint", false); }
    public void hintShown(boolean v) { p.edit().putBoolean("hint", v).apply(); }

    // game settings
    public int gameInt(String k, int d) { return p.getInt("g_" + k, d); }
    public void setGameInt(String k, int v) { p.edit().putInt("g_" + k, v).apply(); }
    public String gameFav() { return p.getString("g_fav", ""); }
    public void gameFav(String v) { p.edit().putString("g_fav", v).apply(); }

    /** 0 = follow the system, 1 = French, 2 = English, 3 = Spanish. */
    public int lang() { return p.getInt("lang", 0); }
    public void lang(int v) { p.edit().putInt("lang", v).apply(); }

    // Spotify (optional, bring-your-own Client ID)
    public String spClientId() { return p.getString("sp_id", ""); }
    public void spClientId(String v) { p.edit().putString("sp_id", v).apply(); }
    public String spAccess() { return p.getString("sp_access", ""); }
    public long spExpiry() { return p.getLong("sp_exp", 0); }
    public String spRefresh() { return p.getString("sp_refresh", ""); }
    public void spTokens(String access, long expiryMs, String refresh) {
        SharedPreferences.Editor e = p.edit().putString("sp_access", access).putLong("sp_exp", expiryMs);
        if (refresh != null && refresh.length() > 0) e.putString("sp_refresh", refresh);
        e.apply();
    }
    public String spVerifier() { return p.getString("sp_ver", ""); }
    public String spState() { return p.getString("sp_state", ""); }
    public void spPending(String verifier, String state) { p.edit().putString("sp_ver", verifier).putString("sp_state", state).apply(); }
    public boolean spotifyOn() { return p.getBoolean("sp_on", false); }
    public void spotifyOn(boolean v) { p.edit().putBoolean("sp_on", v).apply(); }
    public boolean spLast() { return p.getBoolean("sp_last", false); }
    public void spLast(boolean v) { p.edit().putBoolean("sp_last", v).apply(); }
    public void spDisconnect() { p.edit().remove("sp_access").remove("sp_exp").remove("sp_refresh").remove("sp_ver").remove("sp_state").remove("sp_last").apply(); }
}
