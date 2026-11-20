package app.pochette.tv.core;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/** How guests reach the small web pages served by the TV: a friendly .local name, or the plain IP address. */
public final class LocalNet {
    /** Name announced on the local network (see MdnsResponder). */
    public static final String NAME = "pochette.local";
    private static volatile boolean preferIp;

    private LocalNet() {}

    public static void setPreferIp(boolean on) { preferIp = on; }
    public static boolean preferIp() { return preferIp; }

    /** The TV's address on the local network, or "?" when there is none. */
    public static String ip() {
        try {
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            while (e.hasMoreElements()) {
                NetworkInterface n = e.nextElement();
                if (!n.isUp() || n.isLoopback()) continue;
                Enumeration<InetAddress> a = n.getInetAddresses();
                while (a.hasMoreElements()) {
                    InetAddress i = a.nextElement();
                    if (i instanceof Inet4Address && i.isSiteLocalAddress()) return i.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "?";
    }

    /** Host used in links and QR codes. */
    public static String host() { return preferIp ? ip() : NAME; }
    /** The other way to reach the TV, shown small as a fallback; "" when links already use the IP. */
    public static String fallbackHost() { return preferIp ? "" : ip(); }
}
