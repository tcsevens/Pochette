package app.pochette.tv.core;

/** The text of a QR code that makes phones join a Wi-Fi network (understood by the stock camera apps). */
public final class WifiQr {
    private WifiQr() {}

    public static String payload(String ssid, String pass) {
        boolean open = pass == null || pass.length() == 0;
        return "WIFI:T:" + (open ? "nopass" : "WPA") + ";S:" + esc(ssid) + ";" + (open ? "" : "P:" + esc(pass) + ";") + ";";
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') b.append('\\');
            b.append(c);
        }
        return b.toString();
    }
}
