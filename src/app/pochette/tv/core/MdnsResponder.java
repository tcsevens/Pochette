package app.pochette.tv.core;

import android.content.Context;
import android.net.wifi.WifiManager;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;

/**
 * Answers multicast-DNS questions for {@link LocalNet#NAME} with the TV's address, so phones can open
 * http://pochette.local:PORT. Best effort: phones that cannot resolve .local names use the IP address instead.
 */
public final class MdnsResponder {
    private static final String GROUP = "224.0.0.251";
    private static final int PORT = 5353;
    private volatile boolean running;
    private MulticastSocket sock;
    private WifiManager.MulticastLock lock;

    public synchronized void start(Context ctx) {
        if (running) return;
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) { lock = wm.createMulticastLock("pochette-mdns"); lock.setReferenceCounted(false); lock.acquire(); }
        } catch (Exception ignored) {}
        try {
            sock = new MulticastSocket(null);
            sock.setReuseAddress(true);
            sock.bind(new InetSocketAddress(PORT));
            sock.joinGroup(InetAddress.getByName(GROUP));
        } catch (Exception e) {
            stop();
            return;
        }
        running = true;
        Thread t = new Thread(new Runnable() { @Override public void run() { loop(); } }, "pochette-mdns");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void stop() {
        running = false;
        try { if (sock != null) sock.close(); } catch (Exception ignored) {}
        sock = null;
        try { if (lock != null && lock.isHeld()) lock.release(); } catch (Exception ignored) {}
        lock = null;
    }

    private void loop() {
        byte[] buf = new byte[1500];
        while (running) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                sock.receive(p);
                if (asksForUs(buf, p.getLength())) reply(buf, p);
            } catch (Exception e) {
                if (!running) break;
            }
        }
    }

    /** True when the packet is a query (not a response) with a question for our name, type A or ANY, class IN. */
    private static boolean asksForUs(byte[] d, int len) {
        if (len < 12 || (d[2] & 0x80) != 0) return false;
        int qd = ((d[4] & 255) << 8) | (d[5] & 255);
        int pos = 12;
        for (int q = 0; q < qd; q++) {
            StringBuilder name = new StringBuilder();
            while (pos < len && d[pos] != 0) {
                int l = d[pos] & 255;
                if (l > 63 || pos + 1 + l > len) return false;
                if (name.length() > 0) name.append('.');
                name.append(new String(d, pos + 1, l).toLowerCase());
                pos += 1 + l;
            }
            pos += 1;
            if (pos + 4 > len) return false;
            int type = ((d[pos] & 255) << 8) | (d[pos + 1] & 255), cls = (((d[pos + 2] & 255) << 8) | (d[pos + 3] & 255)) & 0x7fff;
            pos += 4;
            if (name.toString().equals(LocalNet.NAME) && (type == 1 || type == 255) && cls == 1) return true;
        }
        return false;
    }

    private void reply(byte[] query, DatagramPacket from) throws Exception {
        String[] ipParts = LocalNet.ip().split("\\.");
        if (ipParts.length != 4) return;
        // a query from a port other than 5353 is a plain resolver: answer it directly, echoing its id
        boolean legacy = from.getPort() != PORT;
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(legacy ? query[0] : 0); o.write(legacy ? query[1] : 0);
        o.write(0x84); o.write(0x00);
        o.write(0); o.write(0);            // questions
        o.write(0); o.write(1);            // answers
        o.write(0); o.write(0); o.write(0); o.write(0);
        for (String label : LocalNet.NAME.split("\\.")) { o.write(label.length()); o.write(label.getBytes("UTF-8")); }
        o.write(0);
        o.write(0); o.write(1);            // type A
        o.write(0x80); o.write(1);         // class IN, cache-flush
        o.write(0); o.write(0); o.write(0); o.write(120);   // ttl
        o.write(0); o.write(4);
        for (String p : ipParts) o.write(Integer.parseInt(p));
        byte[] r = o.toByteArray();
        DatagramPacket out = legacy ? new DatagramPacket(r, r.length, from.getAddress(), from.getPort())
                : new DatagramPacket(r, r.length, InetAddress.getByName(GROUP), PORT);
        sock.send(out);
    }
}
