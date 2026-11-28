package app.pochette.tv.sonos;

import app.pochette.tv.core.Ambiance;
import app.pochette.tv.core.ImageUtil;
import app.pochette.tv.core.Prefs;
import app.pochette.tv.core.T;
import app.pochette.tv.core.TrackState;
import app.pochette.tv.spotify.SpotifyClient;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background loop that follows a Sonos group and reports what it plays.
 * One instance per screen (activity or screensaver).
 */
public final class Poller {
    public static final class Snapshot {
        public TrackState state;      // null while searching / disconnected
        public Bitmap art, bg;
        public int accent = 0xFFEDEDED;
        public int[] palette;
        /** Next tracks of the queue (empty when unknown, e.g. Spotify Connect or radio). */
        public List<SonosClient.Item> upNext = new ArrayList<SonosClient.Item>();
        public String status;         // human readable status when state is null
        public String coordinatorUuid; // group currently shown
        public List<SonosClient.Room> rooms = new ArrayList<SonosClient.Room>();
    }

    public interface Listener { void onSnapshot(Snapshot s); }

    private final Prefs prefs;
    private final Listener listener;
    public final SpotifyClient spotify;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService cmd = Executors.newSingleThreadExecutor();
    private final Object wake = new Object();
    private volatile boolean running;
    private volatile long intervalMs;
    private Thread thread;

    private SonosClient.Topology topo;
    private volatile long topoAt;
    private volatile String currentCoordUuid;
    private volatile String currentIp;
    private String lastUri = "", mediaTitle = "", mediaArt = null, mediaUri = "", mediaMeta = "";
    private long lastSavedAt;
    private String lastSavedKey = "";
    private String artUrl;
    private Bitmap art, bg;
    private int accent = 0xFFEDEDED;
    private int[] palette;
    private int failures;
    private volatile boolean fetchArt = true;

    /** The background watcher only needs the transport state, not the artwork. */
    public void setFetchArt(boolean b) { fetchArt = b; }

    public Poller(Prefs prefs, long intervalMs, Listener l) {
        this.prefs = prefs;
        this.spotify = new SpotifyClient(prefs);
        this.intervalMs = intervalMs;
        this.listener = l;
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(new Runnable() {
            @Override public void run() { loop(); }
        }, "pochette-poller");
        thread.start();
    }

    public void stop() {
        running = false;
        poke();
    }

    public void setInterval(long ms) { intervalMs = ms; poke(); }

    /** Forget topology (after settings change). */
    public void reset() {
        synchronized (wake) { topo = null; }
        poke();
    }

    public void poke() {
        synchronized (wake) { wake.notifyAll(); }
    }

    // ------------------------------------------------------------ commands

    public void playPause(final boolean playing) { command(playing ? 1 : 0); }
    // ------------------------------------------------------------ volume (group volume, like the Sonos app)
    private volatile int vol = -1;
    private volatile boolean mute;
    private volatile long volHold;

    /** Group volume 0..100, -1 while unknown. */
    public int volume() { return vol; }
    public boolean muted() { return mute; }

    public void volumeStep(int delta) {
        final String ip = currentIp;
        if (ip == null || vol < 0) return;
        final int v = Math.max(0, Math.min(100, vol + delta));
        vol = v;
        volHold = SystemClock.elapsedRealtime() + 2500;
        cmd.execute(new Runnable() {
            @Override public void run() {
                try { SonosClient.setGroupVolume(ip, v); } catch (Exception e) { app.pochette.tv.core.Journal.w("Sonos", "volume: " + e.getMessage()); }
            }
        });
    }

    // ------------------------------------------------------------ per-room volume (only the rooms of the playing group)
    private volatile int[] roomVol = new int[0];

    /** The rooms of the group that is playing, the coordinator first. */
    public List<SonosClient.Room> groupRooms() {
        List<SonosClient.Room> out = new ArrayList<SonosClient.Room>();
        SonosClient.Topology t = topo;
        String coord = currentCoordUuid;
        if (t == null || coord == null) return out;
        for (SonosClient.Room r : t.rooms) if (coord.equals(r.coordinatorUuid) && r.uuid.equals(coord)) out.add(r);
        for (SonosClient.Room r : t.rooms) if (coord.equals(r.coordinatorUuid) && !r.uuid.equals(coord)) out.add(r);
        return out;
    }

    public int[] roomVolumes() { return roomVol; }

    /** Reads each room's own volume, then calls back (the caller redraws). */
    public void loadRoomVolumes(final Runnable done) {
        final List<SonosClient.Room> rooms = groupRooms();
        roomVol = new int[rooms.size()];
        for (int i = 0; i < roomVol.length; i++) roomVol[i] = -1;
        cmd.execute(new Runnable() {
            @Override public void run() {
                for (int i = 0; i < rooms.size(); i++) {
                    try { roomVol[i] = SonosClient.getVolume(rooms.get(i).ip); } catch (Exception e) { app.pochette.tv.core.Journal.w("Sonos", "room volume: " + e.getMessage()); }
                    if (done != null) done.run();
                }
            }
        });
    }

    public void setRoomVolume(final int idx, int v) {
        final List<SonosClient.Room> rooms = groupRooms();
        if (idx < 0 || idx >= rooms.size() || idx >= roomVol.length) return;
        final int nv = Math.max(0, Math.min(100, v));
        roomVol[idx] = nv;
        volHold = SystemClock.elapsedRealtime() + 2500;
        final String ip = rooms.get(idx).ip;
        cmd.execute(new Runnable() {
            @Override public void run() {
                try { SonosClient.setVolume(ip, nv); } catch (Exception e) { app.pochette.tv.core.Journal.w("Sonos", "room volume: " + e.getMessage()); }
            }
        });
    }

    // ------------------------------------------------------------ kitchen timer chime
    /**
     * Rings the speakers' own chime on each room of the playing group: the speaker lowers the music, rings, and
     * brings the music back by itself. {@code fallback} runs on a worker thread when that is not possible.
     */
    public void chime(final Runnable fallback) {
        final String ip = currentIp;
        final List<SonosClient.Room> rooms = groupRooms();
        cmd.execute(new Runnable() {
            @Override public void run() {
                try {
                    if (ip == null || rooms.isEmpty()) throw new Exception("no speaker");
                    String hh = SonosWs.household(ip);
                    for (int pass = 0; pass < 2; pass++) {
                        for (SonosClient.Room r : rooms) {
                            org.json.JSONObject b = new org.json.JSONObject();
                            b.put("name", "Pochette");
                            b.put("appId", "app.pochette.tv");
                            b.put("clipType", "CHIME");
                            SonosWs.call(r.ip, "audioClip:1", "loadAudioClip", hh, null, r.uuid, b);
                        }
                        if (pass == 0) SystemClock.sleep(2600);
                    }
                } catch (Exception e) {
                    app.pochette.tv.core.Journal.w("Minuteur", "clip: " + e.getMessage());
                    if (fallback != null) fallback.run();
                }
            }
        });
    }

    public void toggleMute() {
        final String ip = currentIp;
        if (ip == null) return;
        final boolean m = !mute;
        mute = m;
        volHold = SystemClock.elapsedRealtime() + 2500;
        cmd.execute(new Runnable() {
            @Override public void run() {
                try { SonosClient.setGroupMute(ip, m); } catch (Exception e) { app.pochette.tv.core.Journal.w("Sonos", "mute: " + e.getMessage()); }
            }
        });
    }

    public void next() { command(2); }
    public void previous() { command(3); }

    public void seek(final int seconds) {
        final String ip = currentIp;
        if (ip == null) return;
        cmd.execute(new Runnable() {
            @Override public void run() {
                try { SonosClient.seek(ip, Math.max(0, seconds)); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                SystemClock.sleep(200);
                poke();
            }
        });
    }

    private void command(final int what) {
        final String ip = currentIp;
        if (ip == null) return;
        cmd.execute(new Runnable() {
            @Override public void run() {
                try {
                    if (what == 0) SonosClient.play(ip);
                    else if (what == 1) SonosClient.pause(ip);
                    else if (what == 2) SonosClient.next(ip);
                    else SonosClient.previous(ip);
                } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                SystemClock.sleep(150);
                poke();
            }
        });
    }

    // ------------------------------------------------------------ grouping

    public interface Done { void done(String error); }

    /**
     * Make the group led by {@code coordUuid} contain exactly the rooms in {@code wanted}.
     * If the current coordinator is removed, the music is handed over to another wanted room first.
     */
    public void applyGroup(final String coordUuid, final List<String> wanted, final Done cb) {
        final SonosClient.Topology t = topo;
        cmd.execute(new Runnable() {
            @Override public void run() {
                String err = null;
                try {
                    if (t == null || wanted.isEmpty()) throw new Exception(T.t("rien à faire"));
                    SonosClient.Room coord = t.byUuid.get(coordUuid);
                    if (coord == null) throw new Exception("groupe introuvable");
                    // 1. add the new rooms to the current group
                    for (String u : wanted) {
                        SonosClient.Room r = t.byUuid.get(u);
                        if (r == null || coordUuid.equals(r.coordinatorUuid)) continue;
                        SonosClient.joinGroup(r.ip, coordUuid);
                    }
                    // 2. remove members that are no longer wanted (except the coordinator)
                    for (SonosClient.Room r : t.rooms) {
                        if (!coordUuid.equals(r.coordinatorUuid) || r.uuid.equals(coordUuid)) continue;
                        if (!wanted.contains(r.uuid)) SonosClient.leaveGroup(r.ip);
                    }
                    // 3. coordinator removed: hand the music over to another wanted room
                    if (!wanted.contains(coordUuid)) {
                        SonosClient.delegate(coord.ip, wanted.get(0));
                        currentCoordUuid = wanted.get(0);
                        if (prefs.roomUuid().equals(coordUuid)) prefs.roomUuid(wanted.get(0));
                    }
                } catch (Exception e) {
                    err = e.getMessage();
                }
                SystemClock.sleep(800);
                synchronized (wake) { topoAt = 0; }
                poke();
                final String fe = err;
                main.post(new Runnable() { @Override public void run() { cb.done(fe); } });
            }
        });
    }

    // ------------------------------------------------------------ loop

    /** Journal what the speaker really plays 5 s after a favourite was started. */
    /** A random track number of the queue loaded on the speaker (1 when it cannot be read). */
    private int randomTrack(String ip) {
        try {
            String mi = SonosClient.avt(ip, "GetMediaInfo", null);
            int n = Integer.parseInt(SonosClient.tag(mi, "NrTracks").trim());
            if (n > 1) { int r = 1 + new java.util.Random().nextInt(n); app.pochette.tv.core.Journal.i("Sonos", "game: starting on track " + r + " of " + n); return r; }
        } catch (Exception ex) { app.pochette.tv.core.Journal.w("Sonos", ex); }
        return 1;
    }

    private void verifyLater(final String ip, final String coordUuid, final String fav, final boolean shuffle) {
        new Thread(new Runnable() { @Override public void run() {
            try {
                Thread.sleep(5000);
                String hh = SonosWs.household(ip);
                app.pochette.tv.core.Journal.i("Sonos", "5 s after \"" + fav + "\": playing " + SonosWs.nowPlaying(ip, SonosWs.groupFor(ip, hh, coordUuid))
                        + " (shuffle asked: " + (shuffle ? "on" : "off") + ", speaker play mode: " + playMode(ip) + ")");
            } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
        } }).start();
    }

    private static String playMode(String ip) {
        try { return SonosClient.tag(SonosClient.avt(ip, "GetTransportSettings", null), "PlayMode"); }
        catch (Exception e) { return "?"; }
    }

    /** The speaker we polled is no longer the group coordinator (the group changed): refresh the topology, show nothing meanwhile. */
    private static final class StaleTopology extends Exception {
        StaleTopology(String m) { super(m); }
    }

    private void loop() {
        while (running) {
            long wait = intervalMs;
            try {
                if (topo == null || SystemClock.elapsedRealtime() - topoAt > 30000) {
                    SonosClient.Topology t = findTopology();
                    if (t == null) {
                        post(null, T.t("Aucune enceinte Sonos trouvée sur le réseau.\nMaintiens OK pour les réglages."));
                        wait = 8000;
                        sleep(wait);
                        continue;
                    }
                    topo = t;
                    topoAt = SystemClock.elapsedRealtime();
                }
                SonosClient.Room coord = chooseTarget();
                if (coord == null) throw new Exception(T.t("pas de pièce"));
                TrackState st = fetch(coord);
                updateArt(st);
                failures = 0;
                post(st, null);
            } catch (StaleTopology e) {
                app.pochette.tv.core.Journal.i("Sonos", "group changed (" + e.getMessage() + "): refreshing the speakers");
                synchronized (wake) { topoAt = 0; }
                wait = 300;
            } catch (Exception e) {
                failures++;
                if (failures >= 3) {
                    topo = null;
                    post(null, T.t("Connexion à la Sonos perdue… nouvelle recherche."));
                }
                wait = Math.max(wait, 2000);
            }
            sleep(wait);
        }
        cmd.shutdownNow();
    }

    private void sleep(long ms) {
        synchronized (wake) {
            try { wake.wait(ms); } catch (InterruptedException ignored) {}
        }
    }

    private SonosClient.Topology findTopology() {
        Set<String> candidates = new LinkedHashSet<String>();
        String manual = prefs.manualIp().trim();
        if (manual.length() > 0) candidates.add(manual);
        if (prefs.lastIp().length() > 0) candidates.add(prefs.lastIp());
        SonosClient.Topology t = tryAll(candidates);
        if (t != null) return t;

        post(null, T.t("Recherche des enceintes Sonos…"));
        t = tryAll(new LinkedHashSet<String>(SonosClient.ssdp(2500)));
        if (t != null) return t;
        t = tryAll(new LinkedHashSet<String>(SonosClient.scanSubnets()));
        return t;
    }

    private SonosClient.Topology tryAll(Set<String> ips) {
        for (String ip : ips) {
            try {
                SonosClient.Topology t = SonosClient.topology(ip);
                prefs.lastIp(ip);
                return t;
            } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
        }
        return null;
    }

    private SonosClient.Room chooseTarget() throws Exception {
        String wanted = prefs.roomUuid();
        if (wanted.length() > 0 && topo.byUuid.containsKey(wanted)) {
            return topo.coordinatorFor(wanted);
        }
        // Automatic: stick with the current group while it plays, otherwise take any group playing music.
        List<SonosClient.Room> coords = topo.coordinators();
        SonosClient.Room current = currentCoordUuid == null ? null : topo.byUuid.get(currentCoordUuid);
        if (current != null && !current.isCoordinator()) current = topo.coordinatorFor(current.uuid);
        if (coords.size() <= 1) return coords.isEmpty() ? current : coords.get(0);
        if (current != null && isPlayingMusic(current)) return current;
        for (SonosClient.Room r : coords) {
            if (r == current) continue;
            if (isPlayingMusic(r)) return r;
        }
        return current != null ? current : coords.get(0);
    }

    private boolean isPlayingMusic(SonosClient.Room r) {
        try {
            String ti = SonosClient.avt(r.ip, "GetTransportInfo", null);
            if (TrackState.parseTransport(SonosClient.tag(ti, "CurrentTransportState")) != TrackState.PLAYING) return false;
            String pi = SonosClient.avt(r.ip, "GetPositionInfo", null);
            return !SonosClient.tag(pi, "TrackURI").startsWith("x-sonos-htastream:");
        } catch (Exception e) {
            return false;
        }
    }

    private TrackState fetch(SonosClient.Room coord) throws Exception {
        String ip = coord.ip;
        String ti = SonosClient.avt(ip, "GetTransportInfo", null);
        long t0 = SystemClock.elapsedRealtime();
        String pi = SonosClient.avt(ip, "GetPositionInfo", null);
        long t1 = SystemClock.elapsedRealtime();

        TrackState st = new TrackState();
        st.coordinatorIp = ip;
        st.roomName = topo.groupName(coord);
        if (spotify != null) spotify.room = st.roomName;
        st.roomsLabel = topo.roomsLabel(coord);
        if (SystemClock.elapsedRealtime() >= volHold) {      // a local change is not overwritten by an older reading
            try { vol = SonosClient.getGroupVolume(ip); mute = SonosClient.getGroupMute(ip); }
            catch (Exception ex) {
                if (String.valueOf(ex.getMessage()).contains("701")) throw new StaleTopology("not the coordinator any more");
                app.pochette.tv.core.Journal.w("Sonos", ex);
            }
        }
        st.transport = TrackState.parseTransport(SonosClient.tag(ti, "CurrentTransportState"));
        st.uri = SonosClient.unescape(SonosClient.tag(pi, "TrackURI"));
        st.durationSec = SonosClient.hms(SonosClient.tag(pi, "TrackDuration"));
        st.positionSec = SonosClient.hms(SonosClient.tag(pi, "RelTime"));
        st.stampMs = (t0 + t1) / 2;
        lockPosition(st, SonosClient.tag(pi, "TrackURI") + "#" + SonosClient.tag(pi, "Track"));

        String didl = SonosClient.unescape(SonosClient.tag(pi, "TrackMetaData"));
        if ("NOT_IMPLEMENTED".equals(didl)) didl = "";
        st.title = clean(SonosClient.tag(didl, "title"));
        st.artist = clean(SonosClient.tag(didl, "creator"));
        st.album = clean(SonosClient.tag(didl, "album"));
        String art = clean(SonosClient.tag(didl, "albumArtURI"));
        String stream = clean(SonosClient.tag(didl, "streamContent"));

        st.isTv = st.uri.startsWith("x-sonos-htastream:");
        st.isLineIn = st.uri.startsWith("x-rincon-stream:");

        // Media info (station / container) – only when the container URI may have changed.
        if (!st.uri.equals(lastUri) || st.durationSec == 0) {
            try {
                String mi = SonosClient.avt(ip, "GetMediaInfo", null);
                String cur = SonosClient.unescape(SonosClient.tag(mi, "CurrentURI"));
                String md = SonosClient.unescape(SonosClient.tag(mi, "CurrentURIMetaData"));
                mediaUri = cur;
                mediaMeta = md;
                mediaTitle = clean(SonosClient.tag(md, "title"));
                mediaArt = clean(SonosClient.tag(md, "albumArtURI"));
                if (cur.startsWith("x-sonos-htastream:")) st.isTv = true;
                if (st.uri.length() == 0) st.uri = cur;
            } catch (Exception ignored) {
                mediaTitle = "";
                mediaArt = null;
                mediaUri = "";
                mediaMeta = "";
            }
            lastUri = st.uri;
        }
        if (mediaUri.startsWith("x-rincon:")) throw new StaleTopology("this speaker follows another one");

        st.source = TrackState.sourceFor(st.uri);
        String cls = SonosClient.tag(didl, "class").toLowerCase();
        String lu = st.uri.toLowerCase();
        st.isPodcast = cls.contains("podcast") || cls.contains("audiobook") || lu.contains("episode") || lu.contains("podcast");
        st.isRadio = st.durationSec == 0 && !st.isLineIn && !st.isTv && st.uri.length() > 0
                && (stream.length() > 0 || "Radio".equals(st.source) || "TuneIn".equals(st.source)
                    || "Sonos Radio".equals(st.source) || st.uri.startsWith("x-sonosapi-stream:")
                    || st.uri.startsWith("x-rincon-mp3radio:") || st.uri.startsWith("aac:"));

        if (st.isRadio) {
            String station = mediaTitle.startsWith("x-") ? "" : mediaTitle;
            if (stream.length() > 0) applyStream(st, stream);
            else if (st.title.startsWith("x-") || st.title.length() == 0) st.title = station;
            if (station.length() > 0 && !station.equals(st.title)) st.album = station;
            if (st.source.length() == 0) st.source = "Radio";
            if (art.length() == 0 && mediaArt != null) art = mediaArt;
        }
        if (st.isLineIn && st.title.length() == 0) st.title = T.t("Entrée ligne");
        if (st.title.startsWith("x-") || st.title.startsWith("http")) st.title = "";

        if (art.length() > 0) st.artUrl = art.startsWith("/") ? "http://" + ip + ":1400" + art : art;
        rememberLast(st, coord, SonosClient.tag(pi, "Track"));
        updateQueue(st, ip, SonosClient.tag(pi, "Track"));
        currentIp = ip;
        currentCoordUuid = coord.uuid;
        return st;
    }

    // Sonos reports the position in whole seconds. Each poll says: true position = clock + offset, with
    // offset in [RelTime - clock, RelTime + 999 - clock]. Intersecting these windows over successive polls
    // narrows the offset to a few tens of ms after ~10 s (instead of up to a full second): what karaoke needs.
    private String syncKey = "";
    private long offLo, offHi;
    private boolean syncPlaying;

    private void lockPosition(TrackState st, String key) {
        boolean playing = st.transport == TrackState.PLAYING;
        long lo = st.positionSec * 1000L - st.stampMs, hi = lo + 999;
        if (key.equals(syncKey) && syncPlaying && playing) {
            offLo -= 2; offHi += 2;                  // allow slow clock drift
            long nLo = Math.max(offLo, lo), nHi = Math.min(offHi, hi);
            if (nLo > nHi) { nLo = lo; nHi = hi; }   // seek or jump: start over
            offLo = nLo; offHi = nHi;
        } else {
            offLo = lo; offHi = hi;
        }
        syncKey = key;
        syncPlaying = playing;
        st.baseMs = st.stampMs + (offLo + offHi) / 2;
        if (!playing) st.baseMs = st.positionSec * 1000L;
    }

    // ------------------------------------------------------------ queue ("À suivre")

    private volatile List<SonosClient.Item> upNext = new ArrayList<SonosClient.Item>();
    private volatile String queueKey = "";
    private String lastLoggedUri = "";
    private long queueAt;

    private volatile int curTrackNr;
    private int ourAhead, ourLastTrack;

    /** Shared queue: when Sonos plays from its own queue, a guest's song goes right after the current track
     *  (and after the guests' songs already waiting), so "Next" really plays it. False = not applicable. */
    public synchronized boolean enqueueSonos(String spUri, String title) throws Exception {
        final String ip = currentIp;
        int track = curTrackNr;
        if (ip == null || track <= 0 || mediaUri == null || !mediaUri.startsWith("x-rincon-queue:")) return false;
        if (!spUri.startsWith("spotify:track:")) return false;
        String id = spUri.substring("spotify:track:".length());
        String sid = "12", sn = "1";
        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("sid=(\\d+)").matcher(lastUri == null ? "" : lastUri);
        if (m1.find()) sid = m1.group(1);
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("sn=(\\d+)").matcher(lastUri == null ? "" : lastUri);
        if (m2.find()) sn = m2.group(1);
        if (track > ourLastTrack && ourLastTrack > 0) ourAhead = Math.max(0, ourAhead - (track - ourLastTrack));
        else if (ourLastTrack == 0 || track < ourLastTrack) ourAhead = 0;
        ourLastTrack = track;
        String enc = "spotify%3atrack%3a" + id;
        String uri = "x-sonos-spotify:" + enc + "?sid=" + sid + "&flags=8232&sn=" + sn;
        int svc = Integer.parseInt(sid) * 256 + 7;
        String meta = "<DIDL-Lite xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" "
                + "xmlns:r=\"urn:schemas-rinconnetworks-com:metadata-1-0/\" xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\">"
                + "<item id=\"00032020" + enc + "\" parentID=\"\" restricted=\"true\"><dc:title>" + title.replace("&", "&amp;").replace("<", "&lt;")
                + "</dc:title><upnp:class>object.item.audioItem.musicTrack</upnp:class><desc id=\"cdudn\" nameSpace=\"urn:schemas-rinconnetworks-com:metadata-1-0/\">SA_RINCON"
                + svc + "_X_#Svc" + svc + "-0-Token</desc></item></DIDL-Lite>";
        SonosClient.addToQueueAt(ip, uri, meta, track + 1 + ourAhead);
        ourAhead++;
        spQueueKey = "";
        queueKey = "";
        poke();
        return true;
    }

    private void updateQueue(TrackState st, final String ip, String trackNr) {
        try { curTrackNr = Integer.parseInt(trackNr.trim()); } catch (Exception ignored) { curTrackNr = 0; }
        if (!fetchArt) return;
        int track = 0;
        try { track = Integer.parseInt(trackNr.trim()); } catch (Exception ignored) {}
        if (!mediaUri.equals(lastLoggedUri)) {
            lastLoggedUri = mediaUri;
            app.pochette.tv.core.Journal.i("Sonos", "queue: uri=" + (mediaUri.length() > 40 ? mediaUri.substring(0, 40) : mediaUri)
                + " spotifyEnabled=" + spotify.enabled() + " connected=" + spotify.connected() + " music=" + st.hasMusic());
        }
        if (mediaUri.startsWith("x-sonos-vli:") && spotify.enabled() && st.hasMusic()) {
            spotifyQueue(st);
            return;
        }
        if (mediaUri.startsWith("x-sonos-vli:") && st.hasMusic() && !spotify.enabled()) {
            wsNext(st);       // Spotify Connect without the Spotify link: the speaker still knows the next track
            return;
        }
        if (mediaUri.startsWith("x-sonos-vli:") || mediaUri.startsWith("x-rincon:") || mediaUri.startsWith("x-sonos-htastream:")
                || st.isTv || st.isLineIn || st.isRadio || st.isPodcast || mediaUri.length() == 0 || track <= 0 || !st.hasMusic()) {
            queueKey = "";
            if (!upNext.isEmpty()) upNext = new ArrayList<SonosClient.Item>();
            return;
        }
        final String key = ip + "#" + track;
        long now = SystemClock.elapsedRealtime();
        if (key.equals(queueKey) && now - queueAt < 60000) return;
        queueKey = key;
        queueAt = now;
        final int start = track;   // 0-based index of the next track
        final List<SonosClient.Item> old = upNext;
        cmd.execute(new Runnable() {
            @Override public void run() {
                try {
                    List<SonosClient.Item> l = SonosClient.browse(ip, "Q:0", start, 4);
                    for (SonosClient.Item it : l) {
                        for (SonosClient.Item o : old) if (o.thumb != null && o.artUrl != null && o.artUrl.equals(it.artUrl)) it.thumb = o.thumb;
                    }
                    upNext = l;
                    poke();
                    for (SonosClient.Item it : l) if (it.thumb == null && it.artUrl != null) it.thumb = ImageUtil.download(it.artUrl, 160);
                    poke();
                } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
            }
        });
    }

    /** Diagnostic switch (Settings > Troubleshooting): UPnP only, with the 1004206c container prefix. */
    private String wsNextKey = "";
    private long wsNextAt, wsThumbAt;

    /** "Up next" with one track, from the speaker's own websocket API (no Spotify link needed). */
    private void wsNext(final TrackState st) {
        final String ip = currentIp;
        final String coord = currentCoordUuid;
        if (ip == null) return;
        String key = st.title + "|" + st.artist;
        long now = SystemClock.elapsedRealtime();
        if (key.equals(wsNextKey) && now - wsNextAt < 30000) {
            // same track: only retry the cover (the speaker may not have it yet for an upcoming title)
            final List<SonosClient.Item> cur = upNext;
            if (!cur.isEmpty() && cur.get(0).thumb == null && cur.get(0).artUrl != null && now - wsThumbAt > 8000) {
                wsThumbAt = now;
                cmd.execute(new Runnable() {
                    @Override public void run() {
                        SonosClient.Item it = cur.get(0);
                        it.thumb = ImageUtil.download(it.artUrl, 160);
                        app.pochette.tv.core.Journal.i("Sonos", "Up next cover " + (it.thumb != null ? "ok" : "unavailable") + ": " + it.artUrl);
                        if (it.thumb != null) poke();
                    }
                });
            }
            return;
        }
        wsNextKey = key;
        wsNextAt = now;
        queueKey = "";
        cmd.execute(new Runnable() {
            @Override public void run() {
                try {
                    String hh = SonosWs.household(ip);
                    SonosClient.Item n = SonosWs.nextTrack(ip, SonosWs.groupFor(ip, hh, coord));
                    List<SonosClient.Item> l = new ArrayList<SonosClient.Item>();
                    if (n != null && (n.artUrl == null || n.artUrl.length() == 0))
                        n.artUrl = app.pochette.tv.core.CoverLookup.find(n.artist, n.title, n.album);      // the speaker gives no cover for this one
                    if (n != null) {
                        n.kind = 5;      // jump = skip to next
                        l.add(n);
                    }
                    upNext = l;
                    poke();
                    app.pochette.tv.core.Journal.i("Sonos", "Up next (websocket): " + (n == null ? "none" : n.title + " | cover " + n.artUrl));
                    for (SonosClient.Item it : l) if (it.thumb == null && it.artUrl != null) {
                        it.thumb = ImageUtil.download(it.artUrl, 160);
                        app.pochette.tv.core.Journal.i("Sonos", "Up next cover " + (it.thumb != null ? "ok" : "unavailable"));
                    }
                    poke();
                } catch (Exception e) { app.pochette.tv.core.Journal.w("Sonos", e); }
            }
        });
    }

    private String spQueueKey = "";
    private long spQueueAt;

    /** "Up next" while Spotify Connect plays: the queue comes from the Spotify Web API. */
    private void spotifyQueue(TrackState st) {
        prefs.spLast(true);
        String key = st.title + "|" + st.artist;
        long now = SystemClock.elapsedRealtime();
        if (key.equals(spQueueKey) && now - spQueueAt < 45000) return;
        spQueueKey = key;
        spQueueAt = now;
        queueKey = "";
        final List<SonosClient.Item> old = upNext;
        cmd.execute(new Runnable() {
            @Override public void run() {
                try {
                    List<SonosClient.Item> l = spotify.queue(4);
                    app.pochette.tv.core.Journal.i("Sonos", "Spotify queue: " + l.size() + " items");
                    for (SonosClient.Item it : l) {
                        for (SonosClient.Item o : old) if (o.thumb != null && o.artUrl != null && o.artUrl.equals(it.artUrl)) it.thumb = o.thumb;
                    }
                    upNext = l;
                    poke();
                    for (SonosClient.Item it : l) if (it.thumb == null && it.artUrl != null) it.thumb = ImageUtil.download(it.artUrl, 160);
                    poke();
                } catch (Exception e) {
                    app.pochette.tv.core.Journal.w("Sonos", "Spotify queue: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Skip ahead in the Spotify queue (the n-th next track). Sonos is a restricted Spotify device, so the Web API
     * refuses this (403); the soundbar's own Next is used n times instead.
     */
    public void spotifyJump(final int n) {
        final String ip = currentIp;
        if (ip == null || n < 1) return;
        cmd.execute(new Runnable() {
            @Override public void run() {
                for (int i = 0; i < n; i++) {
                    try { SonosClient.next(ip); }
                    catch (Exception e) { app.pochette.tv.core.Journal.w("Sonos", "Spotify jump next " + (i + 1) + "/" + n + ": " + e.getMessage()); }
                    if (i + 1 < n) SystemClock.sleep(450);
                }
                SystemClock.sleep(500);
                spQueueKey = "";
                poke();
            }
        });
    }

    /** Jump to a track of the queue (1-based number). */
    public void jumpTo(final int trackNr) {
        final String ip = currentIp;
        if (ip == null) return;
        cmd.execute(new Runnable() {
            @Override public void run() {
                try { SonosClient.seekTrack(ip, trackNr); SonosClient.play(ip); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                SystemClock.sleep(200);
                poke();
            }
        });
    }

    // ------------------------------------------------------------ games

    /** Pauses and (unless it is the first round) moves to the next track, silently. */
    public void gameNext(final boolean first) {
        final String ip = currentIp;
        if (ip == null) return;
        cmd.execute(new Runnable() {
            @Override public void run() {
                try { SonosClient.pause(ip); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                if (!first) {
                    try { SonosClient.next(ip); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                    SystemClock.sleep(450);
                    try { SonosClient.pause(ip); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                }
                SystemClock.sleep(150);
                poke();
            }
        });
    }

    /** Jumps to a position of the current track and plays from there. */
    public void gamePlayAt(final int seconds) {
        final String ip = currentIp;
        if (ip == null) return;
        cmd.execute(new Runnable() {
            @Override public void run() {
                try { SonosClient.seek(ip, Math.max(0, seconds)); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                SystemClock.sleep(120);
                try { SonosClient.play(ip); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                SystemClock.sleep(300);
                poke();
            }
        });
    }

    public void gamePause() {
        final String ip = currentIp;
        if (ip == null) return;
        cmd.execute(new Runnable() {
            @Override public void run() {
                try { SonosClient.pause(ip); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                SystemClock.sleep(150);
                poke();
            }
        });
    }

    public void gamePlay() {
        final String ip = currentIp;
        if (ip == null) return;
        cmd.execute(new Runnable() {
            @Override public void run() {
                try { SonosClient.play(ip); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                SystemClock.sleep(150);
                poke();
            }
        });
    }

    // ------------------------------------------------------------ ambiances

    public interface Favs { void got(List<SonosClient.Item> favs, String error); }

    public void loadFavorites(final Favs cb) {
        final SonosClient.Topology t = topo;
        cmd.execute(new Runnable() {
            @Override public void run() {
                List<SonosClient.Item> l = new ArrayList<SonosClient.Item>();
                String err = null;
                try {
                    String ip = currentIp != null ? currentIp : (t != null ? t.rooms.get(0).ip : null);
                    if (ip == null) throw new Exception(T.t("aucune enceinte trouvée"));
                    if (lastUri != null && lastUri.startsWith("x-rincon-queue:")) {
                        SonosClient.Item now = new SonosClient.Item();
                        now.kind = 3;
                        now.title = Ambiance.NOW;
                        now.desc = T.t("la file d'attente actuelle");
                        l.add(now);
                    }
                    for (SonosClient.Item it : SonosClient.browse(ip, "FV:2", 0, 100)) {
                        if (it.uri.length() > 0) l.add(it);   // shortcuts to Sonos menus (e.g. "Discover Sonos Radio") can't be played
                    }
                    try {
                        // favourites UPnP cannot see or open (Spotify ones with an empty resource): ask the websocket API
                        for (SonosClient.Item w : SonosWs.favorites(ip, SonosWs.household(ip))) {
                            boolean have = false;
                            for (SonosClient.Item e : l) if (e.kind == 1 && e.title.equals(w.title)) { have = true; break; }
                            if (!have) l.add(w);
                        }
                    } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                    try {
                        for (SonosClient.Item it : SonosClient.browse(ip, "SQ:", 0, 100)) {
                            if (it.uri.length() == 0) continue;
                            it.kind = 2;
                            it.desc = T.t("Playlist Sonos");
                            l.add(it);
                        }
                    } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                } catch (Exception e) {
                    err = e.getMessage();
                }
                final List<SonosClient.Item> fl = l;
                final String fe = err;
                main.post(new Runnable() { @Override public void run() { cb.got(fl, fe); } });
            }
        });
    }

    /** Rooms, volume, playlist, shuffle and sleep timer in one go. */
    public void launchAmbiance(final Ambiance a, final Done cb) {
        cmd.execute(new Runnable() {
            @Override public void run() {
                String err = null;
                try {
                    VolumeRamp.cancel();
                    SonosClient.Topology t = topo;
                    if (t == null || t.rooms.isEmpty()) throw new Exception(T.t("aucune enceinte trouvée"));
                    String anyIp = currentIp != null ? currentIp : t.rooms.get(0).ip;
                    SonosClient.Item fav = null;
                    if (Ambiance.NOW.equals(a.fav)) {
                        if (lastUri == null || !lastUri.startsWith("x-rincon-queue:"))
                            throw new Exception(T.t("ce qui joue n'est pas une file d'attente Sonos (Spotify Connect, radio ou TV) : lance d'abord une playlist ou un album"));
                        fav = new SonosClient.Item();
                        fav.kind = 3;
                        fav.uri = lastUri;
                    } else if (a.fav.startsWith(Ambiance.PL)) {
                        String want = a.fav.substring(Ambiance.PL.length());
                        for (SonosClient.Item it : SonosClient.browse(anyIp, "SQ:", 0, 100)) if (it.title.equals(want)) { fav = it; break; }
                        if (fav == null) throw new Exception(T.t("la playlist Sonos « ") + want + T.t(" » n'existe plus"));
                        fav.kind = 2;
                        fav.meta = SonosClient.playlistMeta(fav);
                    } else {
                        for (SonosClient.Item it : SonosClient.browse(anyIp, "FV:2", 0, 100)) if (it.title.equals(a.fav)) { fav = it; break; }
                    }
                    String wsId = null;
                    boolean spotifyFav = false;
                    if ((fav == null || fav.uri.length() == 0) && !Ambiance.NOW.equals(a.fav) && !a.fav.startsWith(Ambiance.PL)) {
                        // Spotify favourites are refused by UPnP on many setups (HTTP 500 / error 804): the local websocket API opens them
                        try {
                            for (SonosClient.Item w : SonosWs.favorites(anyIp, SonosWs.household(anyIp))) if (w.title.equals(a.fav)) { wsId = w.wsId; break; }
                        } catch (Exception ex) { app.pochette.tv.core.Journal.w("Sonos", ex); }
                        app.pochette.tv.core.Journal.i("Sonos", "websocket lookup for favourite \"" + a.fav + "\": " + (wsId == null ? "not found" : "id " + wsId) + (spotifyFav ? " (Spotify favourite)" : ""));
                    }
                    app.pochette.tv.core.Journal.i("Sonos", "launch \"" + a.fav + "\": " + (fav == null ? "not in UPnP list" : "kind=" + fav.kind + " uri=" + fav.uri + " meta=" + fav.meta.length() + " chars") + " ws=" + (wsId == null ? "no" : "yes") + " rooms=" + t.rooms.size() + " shuffle=" + (a.shuffle ? "on" : "off"));
                    if (wsId == null && fav == null) throw new Exception(T.t("le favori « ") + a.fav + T.t(" » n'est plus dans tes favoris Sonos"));
                    if (wsId == null && fav.uri.length() == 0) throw new Exception(T.t("le favori « ") + a.fav + T.t(" » est un raccourci de menu Sonos, pas une musique : choisis une playlist ou une radio dans l'ambiance"));

                    // 1. rooms
                    List<SonosClient.Room> wanted = new ArrayList<SonosClient.Room>();
                    for (String u : a.rooms) { SonosClient.Room r = t.byUuid.get(u); if (r != null) wanted.add(r); }
                    SonosClient.Room coord = null;
                    if (wanted.isEmpty()) {
                        coord = currentCoordUuid != null ? t.coordinatorFor(currentCoordUuid) : null;
                        if (coord == null) coord = t.coordinators().get(0);
                        for (SonosClient.Room r : t.rooms) if (coord.uuid.equals(r.coordinatorUuid)) wanted.add(r);
                    } else {
                        for (SonosClient.Room r : wanted) if (r.isCoordinator() && r.uuid.equals(currentCoordUuid)) coord = r;
                        if (coord == null) for (SonosClient.Room r : wanted) if (r.isCoordinator()) { coord = r; break; }
                        boolean wasCoord = coord != null;
                        if (coord == null) {
                            coord = wanted.get(0);
                            SonosClient.leaveGroup(coord.ip);
                            SystemClock.sleep(500);
                        }
                        for (SonosClient.Room r : wanted) {
                            if (r == coord) continue;
                            if (!wasCoord || !coord.uuid.equals(r.coordinatorUuid)) SonosClient.joinGroup(r.ip, coord.uuid);
                        }
                        if (wasCoord) {
                            for (SonosClient.Room r : t.rooms) {
                                if (r != coord && coord.uuid.equals(r.coordinatorUuid) && !wanted.contains(r)) SonosClient.leaveGroup(r.ip);
                            }
                        }
                    }
                    String ip = coord.ip;

                    // 2. volume (before the music starts)
                    List<String> ips = new ArrayList<String>();
                    for (SonosClient.Room r : wanted) ips.add(r.ip);
                    if (a.ramp) {
                        int target = a.volume;
                        if (target < 0) { try { target = SonosClient.getVolume(ips.get(0)); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); } }
                        if (target <= 0) target = 30;
                        int from = Math.min(5, target);
                        for (String i : ips) { try { SonosClient.setVolume(i, from); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); } }
                        VolumeRamp.start(ips, from, target, 180000);
                    } else if (a.volume >= 0) {
                        for (String i : ips) { try { SonosClient.setVolume(i, a.volume); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); } }
                    }

                    // 3. music
                    boolean wsDone = false;
                    if (wsId != null) {
                        try {
                            String hh = SonosWs.household(ip);
                            SonosWs.loadFavorite(ip, SonosWs.groupFor(ip, hh, coord.uuid), wsId);
                            wsDone = true;
                            app.pochette.tv.core.Journal.i("Sonos", "favourite started through the websocket API: " + a.fav);
                            try { SonosClient.setShuffle(ip, a.shuffle); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                            if (a.randomStart) { SystemClock.sleep(1200); try { SonosClient.seekTrack(ip, randomTrack(ip)); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); } }
                        } catch (Exception ex) {
                            app.pochette.tv.core.Journal.w("Sonos", ex);
                            if (fav == null || fav.uri.length() == 0) throw ex;      // nothing else to try; otherwise fall back to UPnP
                        }
                    }
                    if (wsDone) {
                        // started
                    } else if (fav.kind == 3) {
                        // keep the queue that is already loaded: only reshuffle and start from its first track
                        SonosClient.setUri(ip, "x-rincon-queue:" + coord.uuid + "#0", "");
                        try { SonosClient.setShuffle(ip, a.shuffle); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                        try { SonosClient.seekTrack(ip, a.randomStart ? randomTrack(ip) : 1); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                    } else if (fav.isStream()) {
                        SonosClient.setUri(ip, fav.uri, fav.meta);
                    } else {
                        boolean queued = false;
                        String errs = "";
                        List<String> metas = new ArrayList<String>();
                        metas.add(fav.meta);
                        String rebuilt = SonosClient.rebuildMeta(fav);
                        if (rebuilt != null && !rebuilt.equals(fav.meta)) metas.add(rebuilt);
                        for (String mt : metas) {
                            try {
                                SonosClient.clearQueue(ip);
                                SonosClient.addToQueue(ip, fav.uri, mt);
                                queued = true;
                                break;
                            } catch (Exception ex) { errs += (errs.length() > 0 ? " · " : "") + ex.getMessage(); }
                            try {
                                SonosClient.addToQueueAlt(ip, fav.uri, mt);
                                queued = true;
                                break;
                            } catch (Exception ex) { errs += " · " + ex.getMessage(); }
                        }
                        if (!queued) {
                            app.pochette.tv.core.Journal.e("Sonos", T.t("Ambiance « ") + a.fav + " » : uri=" + fav.uri + " meta=" + fav.meta + " rebuilt=" + rebuilt + " erreurs=" + errs);
                            // the speaker refuses it over UPnP (error 804 on some setups): the local websocket API can still open it
                            try {
                                String wid = null;
                                for (SonosClient.Item w : SonosWs.favorites(anyIp, SonosWs.household(anyIp))) if (w.title.equals(a.fav)) { wid = w.wsId; break; }
                                if (wid != null) {
                                    String hh2 = SonosWs.household(ip);
                                    SonosWs.loadFavorite(ip, SonosWs.groupFor(ip, hh2, coord.uuid), wid);
                                    SonosClient.setShuffle(ip, a.shuffle);
                                    wsDone = true;
                                    app.pochette.tv.core.Journal.i("Sonos", "UPnP refused it, favourite started through the websocket API: " + a.fav);
                                }
                            } catch (Exception ex) { app.pochette.tv.core.Journal.w("Sonos", ex); }
                            if (!wsDone) {
                            String u = fav.uri;
                            throw new Exception(errs + T.t(" · favori : ") + u.substring(0, Math.min(60, u.length())) + T.t(" · métadonnées ") + fav.meta.length() + " car.");
                            }
                        }
                        if (!wsDone) {
                            SonosClient.setUri(ip, "x-rincon-queue:" + coord.uuid + "#0", "");
                            try { SonosClient.setShuffle(ip, a.shuffle); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                            try { SonosClient.seekTrack(ip, a.randomStart ? randomTrack(ip) : 1); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                        }
                    }
                    SonosClient.play(ip);
                    verifyLater(ip, coord.uuid, a.fav, a.shuffle);
                    try { SonosClient.sleepTimer(ip, a.sleepMin); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }

                    currentIp = ip;
                    currentCoordUuid = coord.uuid;
                    queueKey = "";
                } catch (Exception e) {
                    err = e.getMessage() == null ? T.t("erreur réseau") : e.getMessage();
                }
                SystemClock.sleep(600);
                synchronized (wake) { topoAt = 0; }
                poke();
                final String fe = err;
                main.post(new Runnable() { @Override public void run() { cb.done(fe); } });
            }
        });
    }

    /** Remember what is playing so Play can bring it back later (also after the soundbar switched to TV). */
    private void rememberLast(TrackState st, SonosClient.Room coord, String trackNr) {
        if (!st.isPlaying() || !st.hasMusic() || st.isTv || mediaUri.length() == 0) return;
        if (mediaUri.startsWith("x-sonos-vli:") || mediaUri.startsWith("x-sonos-htastream:") || mediaUri.startsWith("x-rincon:")) return;
        int track = 0;
        try { track = Integer.parseInt(trackNr.trim()); } catch (Exception ignored) {}
        String key = mediaUri + "#" + track;
        long now = SystemClock.elapsedRealtime();
        if (key.equals(lastSavedKey) && now - lastSavedAt < 15000) return;
        lastSavedKey = key;
        lastSavedAt = now;
        prefs.saveLast(mediaUri, mediaMeta, track, st.positionSec, coord.uuid);
    }

    /**
     * Play: resume what the group has loaded, or reload the last music Pochette saw
     * (the queue, a playlist or a radio) when the soundbar is on TV or nothing is loaded.
     */
    public void resumeLast(final Done cb) {
        final SonosClient.Topology t = topo;
        cmd.execute(new Runnable() {
            @Override public void run() {
                String err = null;
                try {
                    String ip = currentIp;
                    String uuid = currentCoordUuid;
                    String saved = prefs.lastCoord();
                    if (t != null && saved.length() > 0 && t.byUuid.containsKey(saved)) {
                        SonosClient.Room c = t.coordinatorFor(saved);
                        ip = c.ip;
                        uuid = c.uuid;
                    }
                    if (ip == null) throw new Exception(T.t("aucune enceinte trouvée"));
                    String mi = SonosClient.avt(ip, "GetMediaInfo", null);
                    String cur = SonosClient.unescape(SonosClient.tag(mi, "CurrentURI"));
                    boolean loaded = cur.length() > 0 && !cur.startsWith("x-sonos-htastream:") && !cur.startsWith("x-sonos-vli:")
                            && !cur.startsWith("x-rincon:");
                    boolean vli = cur.startsWith("x-sonos-vli:");
                    boolean spotifyCase = spotify.enabled() && !vli && !loaded && prefs.spLast();
                    if (vli) {
                        SonosClient.play(ip);   // the Connect session is still there (paused): a plain Play resumes it, a Spotify transfer would break it
                    } else
                    if (spotifyCase) {
                        String room = "";
                        if (t != null && uuid != null && t.byUuid.containsKey(uuid)) room = t.byUuid.get(uuid).name;
                        try {
                            spotify.resume(room);
                        } catch (Exception se) {
                            if (cur.startsWith("x-sonos-vli:")) SonosClient.play(ip);   // the Connect session may still answer
                            else throw se;
                        }
                    } else if (loaded) {
                        SonosClient.play(ip);
                    } else {
                        String uri = prefs.lastUri();
                        if (uri.length() == 0) throw new Exception(T.t("aucune musique récente à relancer"));
                        boolean queue = uri.startsWith("x-rincon-queue:");
                        if (queue && uuid != null) uri = "x-rincon-queue:" + uuid + "#0";
                        SonosClient.avt(ip, "SetAVTransportURI", "<CurrentURI>" + SonosClient.escape(uri) + "</CurrentURI><CurrentURIMetaData>"
                                + SonosClient.escape(queue ? "" : prefs.lastMeta()) + "</CurrentURIMetaData>");
                        if (queue && prefs.lastTrack() > 0) {
                            try {
                                SonosClient.avt(ip, "Seek", "<Unit>TRACK_NR</Unit><Target>" + prefs.lastTrack() + "</Target>");
                                if (prefs.lastPos() > 5) SonosClient.seek(ip, prefs.lastPos());
                            } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                        }
                        SonosClient.play(ip);
                    }
                } catch (Exception e) {
                    err = e.getMessage();
                }
                SystemClock.sleep(300);
                poke();
                final String fe = err;
                if (cb != null) main.post(new Runnable() { @Override public void run() { cb.done(fe); } });
            }
        });
    }

    private static void applyStream(TrackState st, String stream) {
        if (stream.contains("|TITLE ") || stream.startsWith("TYPE=")) {
            for (String part : stream.split("\\|")) {
                if (part.startsWith("TITLE ")) st.title = part.substring(6).trim();
                else if (part.startsWith("ARTIST ")) st.artist = part.substring(7).trim();
                else if (part.startsWith("ALBUM ")) st.album = part.substring(6).trim();
            }
            return;
        }
        int dash = stream.indexOf(" - ");
        if (dash > 0) {
            st.artist = stream.substring(0, dash).trim();
            st.title = stream.substring(dash + 3).trim();
        } else {
            st.title = stream;
        }
    }

    private static String clean(String s) {
        if (s == null) return "";
        s = SonosClient.unescape(s).trim();
        return s;
    }

    private void updateArt(TrackState st) {
        if (!fetchArt) return;
        String url = st.hasMusic() ? st.artUrl : null;
        if (url == null) {
            if (artUrl != null) { artUrl = null; art = null; bg = null; accent = 0xFFEDEDED; palette = null; }
            return;
        }
        if (url.equals(artUrl)) return;
        Bitmap a = ImageUtil.download(url, 1200);
        artUrl = url;
        if (a == null) { art = null; bg = null; accent = 0xFFEDEDED; palette = null; return; }
        art = a;
        bg = ImageUtil.blurred(a);
        accent = ImageUtil.accent(a);
        palette = ImageUtil.palette(a, accent);
    }

    private void post(TrackState st, String status) {
        final Snapshot s = new Snapshot();
        s.state = st;
        s.status = status;
        s.art = st == null ? null : art;
        s.bg = st == null ? null : bg;
        s.accent = accent;
        s.palette = st == null ? null : palette;
        s.coordinatorUuid = currentCoordUuid;
        if (st != null && st.hasMusic()) s.upNext = upNext;
        SonosClient.Topology t = topo;
        if (t != null) s.rooms.addAll(t.rooms);
        main.post(new Runnable() {
            @Override public void run() { if (running) listener.onSnapshot(s); }
        });
    }
}
