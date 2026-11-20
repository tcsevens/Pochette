package app.pochette.tv.sonos;

import app.pochette.tv.core.T;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal client for the Sonos local UPnP API (port 1400). No cloud, no account. */
public final class SonosClient {
    private SonosClient() {}

    public static final String AVT = "urn:schemas-upnp-org:service:AVTransport:1";
    public static final String ZGT = "urn:schemas-upnp-org:service:ZoneGroupTopology:1";
    public static final String RC = "urn:schemas-upnp-org:service:RenderingControl:1";

    // ------------------------------------------------------------------ model

    public static final class Room {
        public String uuid;
        public String name;
        public String ip;
        public String groupId;
        public String coordinatorUuid;
        public boolean isCoordinator() { return uuid != null && uuid.equals(coordinatorUuid); }
    }

    public static final class Topology {
        public final List<Room> rooms = new ArrayList<Room>();
        public final Map<String, Room> byUuid = new LinkedHashMap<String, Room>();

        public List<Room> coordinators() {
            List<Room> out = new ArrayList<Room>();
            for (Room r : rooms) if (r.isCoordinator()) out.add(r);
            return out;
        }

        public Room coordinatorFor(String uuid) {
            Room r = byUuid.get(uuid);
            if (r == null) return null;
            Room c = byUuid.get(r.coordinatorUuid);
            return c != null ? c : r;
        }

        /** "Salon", "Salon + Move" (two rooms) or "Salon + 2" (more) for the group a coordinator leads. */
        public String roomsLabel(Room coordinator) {
            String first = coordinator.name == null ? "" : coordinator.name;
            int extra = 0;
            String other = "";
            for (Room r : rooms) {
                if (r != coordinator && coordinator.uuid.equals(r.coordinatorUuid)) { extra++; if (extra == 1) other = r.name == null ? "" : r.name; }
            }
            if (extra == 0) return first;
            return first + " + " + (extra == 1 ? other : String.valueOf(extra));
        }

        /** "Salon + Cuisine" style name for the group a coordinator leads. */
        public String groupName(Room coordinator) {
            StringBuilder sb = new StringBuilder(coordinator.name == null ? "" : coordinator.name);
            int extra = 0;
            for (Room r : rooms) {
                if (r != coordinator && coordinator.uuid.equals(r.coordinatorUuid)) extra++;
            }
            if (extra > 0) sb.append(" + ").append(extra);
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------ SOAP

    public static String soap(String ip, String path, String service, String action, String args, int timeoutMs) throws Exception {
        String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                + "<s:Body><u:" + action + " xmlns:u=\"" + service + "\">" + args + "</u:" + action + "></s:Body></s:Envelope>";
        URL url = new URL("http://" + ip + ":1400" + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        try {
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            c.setRequestProperty("SOAPACTION", "\"" + service + "#" + action + "\"");
            byte[] data = body.getBytes("UTF-8");
            c.setFixedLengthStreamingMode(data.length);
            OutputStream os = c.getOutputStream();
            os.write(data);
            os.close();
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String resp = is == null ? "" : readAll(is);
            if (code >= 400) {
                app.pochette.tv.core.Journal.e("Sonos", action + " -> HTTP " + code + " " + resp + " | args: " + (args.length() > 1500 ? args.substring(0, 1500) : args));
                String ec = tag(resp, "errorCode");
                throw new Exception(action + T.t(" refusé par Sonos (HTTP ") + code + (ec.length() > 0 ? T.t(", erreur ") + ec : "") + ")");
            }
            return resp;
        } finally {
            c.disconnect();
        }
    }

    public static String avt(String ip, String action, String extraArgs) throws Exception {
        return soap(ip, "/MediaRenderer/AVTransport/Control", AVT, action,
                "<InstanceID>0</InstanceID>" + (extraArgs == null ? "" : extraArgs),
                action.equals("AddURIToQueue") ? 25000 : action.equals("SetAVTransportURI") || action.equals("RemoveAllTracksFromQueue") ? 10000 : 2500);
    }

    public static void play(String ip) throws Exception { avt(ip, "Play", "<Speed>1</Speed>"); }
    public static void pause(String ip) throws Exception { avt(ip, "Pause", null); }
    public static void next(String ip) throws Exception { avt(ip, "Next", null); }
    public static void previous(String ip) throws Exception { avt(ip, "Previous", null); }

    /** Jump to a position (seconds) in the current track. */
    public static void seek(String ip, int seconds) throws Exception {
        int h = seconds / 3600, m = (seconds / 60) % 60, s = seconds % 60;
        String t = h + ":" + (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
        avt(ip, "Seek", "<Unit>REL_TIME</Unit><Target>" + t + "</Target>");
    }

    // ------------------------------------------------------------------ volume, play mode, sleep timer

    public static int getVolume(String ip) throws Exception {
        String r = soap(ip, "/MediaRenderer/RenderingControl/Control", RC, "GetVolume", "<InstanceID>0</InstanceID><Channel>Master</Channel>", 2500);
        try { return Integer.parseInt(tag(r, "CurrentVolume").trim()); } catch (Exception e) { return -1; }
    }

    public static void setVolume(String ip, int percent) throws Exception {
        soap(ip, "/MediaRenderer/RenderingControl/Control", RC, "SetVolume",
                "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>" + Math.max(0, Math.min(100, percent)) + "</DesiredVolume>", 2500);
    }

    public static final String GRC = "urn:schemas-upnp-org:service:GroupRenderingControl:1";

    /** Group volume, as the Sonos app shows it (the rooms keep their differences). Ask the coordinator. */
    public static int getGroupVolume(String ip) throws Exception {
        String r = soap(ip, "/MediaRenderer/GroupRenderingControl/Control", GRC, "GetGroupVolume", "<InstanceID>0</InstanceID>", 2500);
        try { return Integer.parseInt(tag(r, "CurrentVolume").trim()); } catch (Exception e) { return -1; }
    }

    public static void setGroupVolume(String ip, int percent) throws Exception {
        soap(ip, "/MediaRenderer/GroupRenderingControl/Control", GRC, "SetGroupVolume",
                "<InstanceID>0</InstanceID><DesiredVolume>" + Math.max(0, Math.min(100, percent)) + "</DesiredVolume>", 2500);
    }

    public static boolean getGroupMute(String ip) throws Exception {
        String r = soap(ip, "/MediaRenderer/GroupRenderingControl/Control", GRC, "GetGroupMute", "<InstanceID>0</InstanceID>", 2500);
        return "1".equals(tag(r, "CurrentMute").trim());
    }

    public static void setGroupMute(String ip, boolean mute) throws Exception {
        soap(ip, "/MediaRenderer/GroupRenderingControl/Control", GRC, "SetGroupMute",
                "<InstanceID>0</InstanceID><DesiredMute>" + (mute ? 1 : 0) + "</DesiredMute>", 2500);
    }

    public static void setShuffle(String ip, boolean shuffle) throws Exception {
        avt(ip, "SetPlayMode", "<NewPlayMode>" + (shuffle ? "SHUFFLE_NOREPEAT" : "NORMAL") + "</NewPlayMode>");
    }

    /** minutes = 0 cancels the timer. */
    public static void sleepTimer(String ip, int minutes) throws Exception {
        String d = minutes <= 0 ? "" : two(minutes / 60) + ":" + two(minutes % 60) + ":00";
        avt(ip, "ConfigureSleepTimer", "<NewSleepTimerDuration>" + d + "</NewSleepTimerDuration>");
    }

    private static String two(int v) { return v < 10 ? "0" + v : String.valueOf(v); }

    public static void setUri(String ip, String uri, String meta) throws Exception {
        avt(ip, "SetAVTransportURI", "<CurrentURI>" + escape(uri) + "</CurrentURI><CurrentURIMetaData>" + escape(meta) + "</CurrentURIMetaData>");
    }

    public static void clearQueue(String ip) throws Exception { avt(ip, "RemoveAllTracksFromQueue", null); }

    public static void addToQueue(String ip, String uri, String meta) throws Exception {
        addToQueue0(ip, uri, meta);
    }

    /** Metadata rebuilt from the favourite's address (Spotify-style containers), used if Sonos rejects the stored one. */
    public static String rebuildMeta(Item it) {
        String u = it.uri;
        int i = u.indexOf(':');
        if (i < 0 || !u.startsWith("x-rincon-cpcontainer:")) return null;
        String id = u.substring(i + 1);
        int q = id.indexOf('?');
        if (q >= 0) id = id.substring(0, q);
        Matcher m = Pattern.compile("sid=(\\d+)").matcher(u);
        int sid = m.find() ? Integer.parseInt(m.group(1)) : 12;
        String cls = it.meta.contains("object.container.album") ? "object.container.album.musicAlbum" : "object.container.playlistContainer";
        return "<DIDL-Lite xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" "
                + "xmlns:r=\"urn:schemas-rinconnetworks-com:metadata-1-0/\" xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\">"
                + "<item id=\"" + id + "\" parentID=\"\" restricted=\"true\"><dc:title>" + escape(it.title) + "</dc:title>"
                + "<upnp:class>" + cls + "</upnp:class><desc id=\"cdudn\" nameSpace=\"urn:schemas-rinconnetworks-com:metadata-1-0/\">SA_RINCON"
                + (sid * 256 + 7) + "_X_#Svc" + (sid * 256 + 7) + "-0-Token</desc></item></DIDL-Lite>";
    }

    /** Metadata for a Sonos playlist (saved queue) found with Browse("SQ:"). */
    public static String playlistMeta(Item it) {
        return "<DIDL-Lite xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" "
                + "xmlns:r=\"urn:schemas-rinconnetworks-com:metadata-1-0/\" xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\">"
                + "<container id=\"" + escape(it.objId) + "\" parentID=\"SQ:\" restricted=\"true\"><dc:title>" + escape(it.title) + "</dc:title>"
                + "<upnp:class>object.container.playlistContainer</upnp:class><desc id=\"cdudn\" nameSpace=\"urn:schemas-rinconnetworks-com:metadata-1-0/\">"
                + "RINCON_AssociatedZPUDN</desc></container></DIDL-Lite>";
    }

    public static void addToQueueAlt(String ip, String uri, String meta) throws Exception {
        avt(ip, "AddURIToQueue", "<EnqueuedURI>" + escape(uri) + "</EnqueuedURI><EnqueuedURIMetaData>" + escape(meta)
                + "</EnqueuedURIMetaData><DesiredFirstTrackNumberEnqueued>1</DesiredFirstTrackNumberEnqueued><EnqueueAsNext>1</EnqueueAsNext>");
    }

    /** Inserts a track at a given position (1-based) of the Sonos queue. */
    public static void addToQueueAt(String ip, String uri, String meta, int pos) throws Exception {
        avt(ip, "AddURIToQueue", "<EnqueuedURI>" + escape(uri) + "</EnqueuedURI><EnqueuedURIMetaData>" + escape(meta)
                + "</EnqueuedURIMetaData><DesiredFirstTrackNumberEnqueued>" + pos + "</DesiredFirstTrackNumberEnqueued><EnqueueAsNext>0</EnqueueAsNext>");
    }

    private static void addToQueue0(String ip, String uri, String meta) throws Exception {
        avt(ip, "AddURIToQueue", "<EnqueuedURI>" + escape(uri) + "</EnqueuedURI><EnqueuedURIMetaData>" + escape(meta)
                + "</EnqueuedURIMetaData><DesiredFirstTrackNumberEnqueued>0</DesiredFirstTrackNumberEnqueued><EnqueueAsNext>0</EnqueueAsNext>");
    }

    public static void seekTrack(String ip, int trackNr) throws Exception {
        avt(ip, "Seek", "<Unit>TRACK_NR</Unit><Target>" + trackNr + "</Target>");
    }

    // ------------------------------------------------------------------ browsing (favourites, queue)

    public static final String CD = "urn:schemas-upnp-org:service:ContentDirectory:1";

    public static final class Item {
        public String title = "", artist = "", album = "", artUrl, uri = "", meta = "", desc = "";
        public int trackNr;
        public String objId = "";
        /** 1 = Sonos favourite, 2 = Sonos playlist, 3 = "what is playing now" (the current queue). */
        public int kind = 1;
        public android.graphics.Bitmap thumb;

        /** Radio-like item: played directly; anything else goes through the queue. */
        public boolean isStream() {
            String u = uri.toLowerCase();
            return meta.contains("audioBroadcast") || u.startsWith("x-sonosapi-stream:") || u.startsWith("x-sonosapi-radio:")
                    || u.startsWith("x-rincon-mp3radio:") || u.startsWith("x-sonosapi-hls:") || u.startsWith("aac:")
                    || u.startsWith("hls-radio:");
        }
    }

    /** objectId: "FV:2" = Sonos favourites, "Q:0" = the queue. */
    public static List<Item> browse(String ip, String objectId, int start, int count) throws Exception {
        String resp = soap(ip, "/MediaServer/ContentDirectory/Control", CD, "Browse",
                "<ObjectID>" + objectId + "</ObjectID><BrowseFlag>BrowseDirectChildren</BrowseFlag><Filter>*</Filter><StartingIndex>"
                        + start + "</StartingIndex><RequestedCount>" + count + "</RequestedCount><SortCriteria></SortCriteria>", objectId.equals("FV:2") ? 15000 : 5000);
        String didl = unescape(tag(resp, "Result"));
        List<Item> out = new ArrayList<Item>();
        Matcher m = Pattern.compile("<(item|container)\\b([^>]*)>(.*?)</\\1>", Pattern.DOTALL).matcher(didl);
        int n = 0;
        while (m.find()) {
            String b = m.group(3);
            Item it = new Item();
            it.objId = attr(m.group(2), "id");
            it.title = unescape(tag(b, "title")).trim();
            it.artist = unescape(tag(b, "creator")).trim();
            it.album = unescape(tag(b, "album")).trim();
            it.desc = unescape(tag(b, "description")).trim();
            it.uri = unescape(tag(b, "res")).trim();
            it.meta = unescape(tag(b, "resMD")).trim();
            String art = unescape(tag(b, "albumArtURI")).trim();
            if (art.length() > 0) it.artUrl = art.startsWith("/") ? "http://" + ip + ":1400" + art : art;
            it.trackNr = start + (++n);
            out.add(it);
        }
        return out;
    }

    /** Make the player at memberIp join the group led by coordinatorUuid. */
    public static void joinGroup(String memberIp, String coordinatorUuid) throws Exception {
        avt(memberIp, "SetAVTransportURI", "<CurrentURI>x-rincon:" + coordinatorUuid + "</CurrentURI><CurrentURIMetaData></CurrentURIMetaData>");
    }

    /** Take the player at ip out of its group (it stops playing). */
    public static void leaveGroup(String ip) throws Exception {
        avt(ip, "BecomeCoordinatorOfStandaloneGroup", null);
    }

    /** Hand the group (and the music) over to another member; the old coordinator leaves. */
    public static void delegate(String coordinatorIp, String newCoordinatorUuid) throws Exception {
        avt(coordinatorIp, "DelegateGroupCoordinationTo",
                "<NewCoordinator>" + newCoordinatorUuid + "</NewCoordinator><RejoinGroup>0</RejoinGroup>");
    }

    // ------------------------------------------------------------------ topology

    public static Topology topology(String ip) throws Exception {
        String resp = soap(ip, "/ZoneGroupTopology/Control", ZGT, "GetZoneGroupState", "", 3000);
        String state = unescape(tag(resp, "ZoneGroupState"));
        Topology t = new Topology();
        Matcher g = Pattern.compile("<ZoneGroup\\b([^>]*)>(.*?)</ZoneGroup>", Pattern.DOTALL).matcher(state);
        while (g.find()) {
            String gAttrs = g.group(1);
            String coord = attr(gAttrs, "Coordinator");
            String gid = attr(gAttrs, "ID");
            Matcher m = Pattern.compile("<ZoneGroupMember\\b([^>]*?)/?>", Pattern.DOTALL).matcher(g.group(2));
            while (m.find()) {
                String a = m.group(1);
                if ("1".equals(attr(a, "Invisible"))) continue; // surrounds / sub / bonded parts
                Room r = new Room();
                r.uuid = attr(a, "UUID");
                r.name = unescape(attr(a, "ZoneName"));
                r.ip = hostOf(attr(a, "Location"));
                r.groupId = gid;
                r.coordinatorUuid = coord;
                if (r.uuid == null || r.ip == null) continue;
                if (t.byUuid.containsKey(r.uuid)) continue;
                t.rooms.add(r);
                t.byUuid.put(r.uuid, r);
            }
        }
        if (t.rooms.isEmpty()) throw new Exception(T.t("Topologie vide"));
        return t;
    }

    // ------------------------------------------------------------------ discovery

    /** SSDP discovery. Returns IPs of Sonos players that answered. */
    public static List<String> ssdp(int waitMs) {
        Set<String> ips = new LinkedHashSet<String>();
        DatagramSocket s = null;
        try {
            s = new DatagramSocket();
            s.setSoTimeout(400);
            String msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\n"
                    + "ST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n\r\n";
            byte[] d = msg.getBytes("UTF-8");
            InetAddress group = InetAddress.getByName("239.255.255.250");
            for (int i = 0; i < 2; i++) s.send(new DatagramPacket(d, d.length, group, 1900));
            long end = System.currentTimeMillis() + waitMs;
            byte[] buf = new byte[2048];
            while (System.currentTimeMillis() < end) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try {
                    s.receive(p);
                } catch (SocketTimeoutException e) {
                    continue;
                }
                String r = new String(p.getData(), 0, p.getLength(), "UTF-8");
                if (r.toLowerCase().contains("sonos") || r.contains("ZonePlayer")) {
                    ips.add(p.getAddress().getHostAddress());
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (s != null) s.close();
        }
        return new ArrayList<String>(ips);
    }

    /** Fallback: probe every host of our /24 networks on port 1400. */
    public static List<String> scanSubnets() {
        final List<String> found = Collections.synchronizedList(new ArrayList<String>());
        List<String> prefixes = localPrefixes();
        if (prefixes.isEmpty()) return found;
        ExecutorService ex = Executors.newFixedThreadPool(48);
        for (String prefix : prefixes) {
            for (int i = 1; i < 255; i++) {
                final String ip = prefix + i;
                ex.execute(new Runnable() {
                    @Override public void run() {
                        Socket so = new Socket();
                        try {
                            so.connect(new InetSocketAddress(ip, 1400), 350);
                            so.close();
                            if (isSonos(ip)) found.add(ip);
                        } catch (Exception ignored) {
                        } finally {
                            try { so.close(); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                        }
                    }
                });
            }
        }
        ex.shutdown();
        try { ex.awaitTermination(15, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return new ArrayList<String>(found);
    }

    public static boolean isSonos(String ip) {
        try {
            URL u = new URL("http://" + ip + ":1400/xml/device_description.xml");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(1200);
            c.setReadTimeout(1500);
            String s = readAll(c.getInputStream());
            c.disconnect();
            return s.contains("Sonos") || s.contains("ZonePlayer");
        } catch (Exception e) {
            return false;
        }
    }

    public static List<String> localPrefixes() {
        List<String> out = new ArrayList<String>();
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en != null && en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> as = ni.getInetAddresses();
                while (as.hasMoreElements()) {
                    InetAddress a = as.nextElement();
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        String h = a.getHostAddress();
                        String p = h.substring(0, h.lastIndexOf('.') + 1);
                        if (!out.contains(p)) out.add(p);
                    }
                }
            }
        } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    public static String readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = is.read(b)) > 0) bo.write(b, 0, n);
        is.close();
        return bo.toString("UTF-8");
    }

    /** Text content of the first <name>…</name> (namespace prefix optional). */
    public static String tag(String xml, String name) {
        if (xml == null) return "";
        Matcher m = Pattern.compile("<(?:[\\w-]+:)?" + Pattern.quote(name) + "(?:\\s[^>]*)?>(.*?)</(?:[\\w-]+:)?" + Pattern.quote(name) + ">",
                Pattern.DOTALL).matcher(xml);
        return m.find() ? m.group(1) : "";
    }

    public static String attr(String attrs, String name) {
        Matcher m = Pattern.compile("\\b" + Pattern.quote(name) + "=\"([^\"]*)\"").matcher(attrs);
        return m.find() ? m.group(1) : null;
    }

    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    public static String unescape(String s) {
        if (s == null) return null;
        if (s.indexOf('&') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '&') {
                int semi = s.indexOf(';', i);
                if (semi > i && semi - i <= 10) {
                    String ent = s.substring(i + 1, semi);
                    String rep = null;
                    if (ent.equals("amp")) rep = "&";
                    else if (ent.equals("lt")) rep = "<";
                    else if (ent.equals("gt")) rep = ">";
                    else if (ent.equals("quot")) rep = "\"";
                    else if (ent.equals("apos")) rep = "'";
                    else if (ent.startsWith("#x") || ent.startsWith("#X")) {
                        try { rep = new String(Character.toChars(Integer.parseInt(ent.substring(2), 16))); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                    } else if (ent.startsWith("#")) {
                        try { rep = new String(Character.toChars(Integer.parseInt(ent.substring(1)))); } catch (Exception ignored) { app.pochette.tv.core.Journal.w("Sonos", ignored); }
                    }
                    if (rep != null) {
                        sb.append(rep);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            sb.append(ch);
            i++;
        }
        return sb.toString();
    }

    public static String hostOf(String url) {
        if (url == null) return null;
        try { return new URL(url).getHost(); } catch (Exception e) { return null; }
    }

    /** "0:03:25" -> 205 */
    public static int hms(String t) {
        if (t == null) return 0;
        t = t.trim();
        if (t.length() == 0 || t.startsWith("NOT")) return 0;
        String[] p = t.split(":");
        int sec = 0;
        try {
            for (String x : p) sec = sec * 60 + (int) Double.parseDouble(x);
        } catch (Exception e) { return 0; }
        return sec;
    }
}
