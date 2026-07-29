package io.github.d3m1d0s.pjp;

/**
 * Encodes and decodes the quoted string form shared by source literals and
 * the text instruction format: \n, \t, \r, \\ and \" between double quotes.
 * Keeping both directions together makes the round trip lossless.
 */
public final class StringEscapes {

    private StringEscapes() {
    }

    /**
     * Turns a quoted, escaped form into the raw value. Throws
     * IllegalArgumentException on a bad escape: the lexer admits any backslash
     * pair, so this is where unknown ones get rejected.
     */
    public static String decode(String quoted) {
        // the surrounding quotes are guaranteed by the lexer and by encode()
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

    /** Turns a raw value into the quoted, escaped form, safe for a line-based format. */
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
