package app.pochette.tv.core;

import android.os.SystemClock;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** What a Sonos group is doing right now. */
public final class TrackState {
    public static final int STOPPED = 0, PLAYING = 1, PAUSED = 2, TRANSITIONING = 3;

    public String roomName = "";
    /** Rooms playing, for the discreet menu mark: "Salon", "Salon + Move" or "Salon + 2". */
    public String roomsLabel = "";
    public String coordinatorIp;
    public int transport = STOPPED;
    public String title = "", artist = "", album = "";
    public String artUrl;
    public String source = "";
    public String uri = "";
    public int durationSec;
    public int positionSec;
    /** Position in ms at stampMs, refined below the 1 s resolution Sonos reports (see Poller). */
    public long baseMs = -1;
    public long stampMs = SystemClock.elapsedRealtime();
    public boolean isTv, isLineIn, isRadio, isPodcast;

    /** True when there is music worth showing (not TV, not empty). */
    public boolean hasMusic() {
        if (isTv) return false;
        if (uri == null || uri.length() == 0) return false;
        return title.length() > 0 || artist.length() > 0 || isLineIn || isRadio;
    }

    public boolean isPlaying() {
        return transport == PLAYING || transport == TRANSITIONING;
    }

    /** Interpolated playback position in milliseconds. */
    public long positionMs() {
        long p = baseMs >= 0 ? baseMs : positionSec * 1000L;
        if (transport == PLAYING) p += SystemClock.elapsedRealtime() - stampMs;
        if (durationSec > 0) p = Math.min(p, durationSec * 1000L);
        return p;
    }

    /** Key that changes when the song changes. */
    public String trackKey() {
        return title + "\u0001" + artist + "\u0001" + album;
    }

    public static int parseTransport(String s) {
        if ("PLAYING".equals(s)) return PLAYING;
        if ("PAUSED_PLAYBACK".equals(s)) return PAUSED;
        if ("TRANSITIONING".equals(s)) return TRANSITIONING;
        return STOPPED;
    }

    private static final Pattern SID = Pattern.compile("sid=(\\d+)");

    public static String sourceFor(String uri) {
        if (uri == null) return "";
        String u = uri.toLowerCase();
        if (u.startsWith("x-sonos-htastream:")) return "TV";
        if (u.startsWith("x-rincon-stream:")) return T.t("Entrée ligne");
        if (u.startsWith("x-file-cifs:") || u.startsWith("x-smb:") || u.startsWith("x-sonos-smb:")) return T.t("Bibliothèque");
        if (u.contains("airplay")) return "AirPlay";
        if (u.contains("spotify")) return "Spotify";
        Matcher m = SID.matcher(u);
        if (m.find()) {
            String n = serviceName(Integer.parseInt(m.group(1)));
            if (n != null) return n;
        }
        if (u.startsWith("x-rincon-mp3radio:") || u.startsWith("x-sonosapi-stream:") || u.startsWith("aac:") || u.startsWith("x-sonosapi-radio:"))
            return "Radio";
        if (u.startsWith("x-sonos-vli:")) return T.t("Diffusion");
        return "";
    }

    static String serviceName(int sid) {
        switch (sid) {
            case 2: return "Deezer";
            case 9: case 12: return "Spotify";
            case 31: return "Qobuz";
            case 160: return "SoundCloud";
            case 174: return "TIDAL";
            case 201: return "Amazon Music";
            case 204: return "Apple Music";
            case 254: return "TuneIn";
            case 284: return "YouTube Music";
            case 303: return "Sonos Radio";
            case 333: return "TuneIn";
            default: return null;
        }
    }
}
