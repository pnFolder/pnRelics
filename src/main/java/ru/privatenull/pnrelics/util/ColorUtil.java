package ru.privatenull.pnrelics.util;

/** Converts &amp; codes, &amp;#RRGGBB, &amp;#RRGGBBAA and legacy x-hex colors into section-sign text. */
public final class ColorUtil {

    private static final char SECTION = '§';
    private static final String CODES = "0123456789abcdefklmnor";

    private ColorUtil() {
    }

    public static String colorize(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); ) {
            char c = value.charAt(i);
            if (c != '&' && c != SECTION) {
                result.append(c);
                i++;
                continue;
            }
            if (c == '&' && i + 1 < value.length() && value.charAt(i + 1) == '#') {
                if (isHex(value, i + 2, 8)) { // alpha is not supported by Minecraft text and is dropped
                    appendHex(result, value.substring(i + 2, i + 8));
                    i += 10;
                    continue;
                }
                if (isHex(value, i + 2, 6)) {
                    appendHex(result, value.substring(i + 2, i + 8));
                    i += 8;
                    continue;
                }
            }
            if (i + 13 < value.length() && Character.toLowerCase(value.charAt(i + 1)) == 'x' && legacyHex(value, i)) {
                StringBuilder hex = new StringBuilder(6);
                for (int p = i + 3; p <= i + 13; p += 2) hex.append(value.charAt(p));
                appendHex(result, hex.toString());
                i += 14;
                continue;
            }
            if (i + 1 < value.length() && CODES.indexOf(Character.toLowerCase(value.charAt(i + 1))) >= 0) {
                result.append(SECTION).append(Character.toLowerCase(value.charAt(i + 1)));
                i += 2;
                continue;
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }

    private static void appendHex(StringBuilder result, String hex) {
        result.append(SECTION).append('x');
        for (char digit : hex.toLowerCase().toCharArray()) result.append(SECTION).append(digit);
    }

    private static boolean legacyHex(String value, int index) {
        for (int p = index + 2; p <= index + 12; p += 2) {
            char marker = value.charAt(p);
            if ((marker != '&' && marker != SECTION) || !isHex(value, p + 1, 1)) return false;
        }
        return true;
    }

    private static boolean isHex(String value, int start, int count) {
        if (start + count > value.length()) return false;
        for (int i = start; i < start + count; i++) {
            if (Character.digit(value.charAt(i), 16) < 0) return false;
        }
        return true;
    }
}
