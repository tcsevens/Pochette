package app.pochette.tv.core;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/** The TV's address on the local network, used in the links and QR codes for the phone pages. */
public final class LocalNet {
    private LocalNet() {}

    /** The address, or "?" when there is none. */
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
}
