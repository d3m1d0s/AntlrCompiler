package cz.university;

/**
 * The one place that knows how string values are written down, both in
 * source literals and in the text instruction format: surrounded by quotes,
 * with \n, \t, \r, \\ and \" escapes. Keeping encode and decode together
 * makes the round trip through output.out lossless.
 */
public final class StringEscapes {

    private StringEscapes() {
    }

    /** Turns a quoted, escaped form into the raw value. */
    public static String decode(String quoted) {
        String body = quoted.substring(1, quoted.length() - 1);
        StringBuilder result = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c != '\\') {
                result.append(c);
                continue;
            }
            i++;
            if (i == body.length()) {
                throw new IllegalArgumentException("Dangling backslash");
            }
            char escaped = body.charAt(i);
            switch (escaped) {
                case 'n' -> result.append('\n');
                case 't' -> result.append('\t');
                case 'r' -> result.append('\r');
                case '\\' -> result.append('\\');
                case '"' -> result.append('"');
                default -> throw new IllegalArgumentException("Unknown escape sequence '\\" + escaped + "'");
            }
        }
        return result.toString();
    }

    /** Turns a raw value into the quoted, escaped form, safe for a line based format. */
    public static String encode(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2);
        result.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\n' -> result.append("\\n");
                case '\t' -> result.append("\\t");
                case '\r' -> result.append("\\r");
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                default -> result.append(c);
            }
        }
        return result.append('"').toString();
    }
}
