package app.pochette.tv.core;

import java.util.regex.Pattern;

/** The "report a problem" page: a redacted copy of the log that the user sends or pastes by himself. */
public final class Support {
    private Support() {}

    /** Address used by the "send by e-mail" button of the report page. Empty: the button is hidden. */
    public static final String EMAIL = "pochette.support@gmail.com";

    private static final Pattern[] PATTERNS = {
        Pattern.compile("Sonos_[A-Za-z0-9._-]+"),
        Pattern.compile("RINCON_[0-9A-Fa-f]+(:\\d+)?"),
        Pattern.compile("lc_[0-9a-f]{16,}"),
        Pattern.compile("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?\\b"),
        Pattern.compile("\\b[0-9A-Fa-f]{2}([:-][0-9A-Fa-f]{2}){5}(:[0-9A-Fa-f])?\\b"),
    };
    private static final String[] LABELS = { "Sonos_[masque]", "RINCON_[masque]", "lc_[masque]", "[ip]", "[mac]" };

    /** Removes what identifies a home: household, speakers, serial numbers, addresses. */
    public static String redact(String s) {
        for (int i = 0; i < PATTERNS.length; i++) s = PATTERNS[i].matcher(s).replaceAll(LABELS[i]);
        return s;
    }

    /** Context lines + the last lines of the journal, redacted. */
    public static String report(String context, int lines) {
        StringBuilder b = new StringBuilder();
        b.append("Pochette report\n").append(context).append("\n\n");
        java.util.List<String> tail = Journal.tail(lines);
        for (String l : tail) b.append(l).append('\n');
        return redact(b.toString());
    }
}
