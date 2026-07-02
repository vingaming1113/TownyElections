package com.townyelections.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses human-friendly duration strings such as {@code "2d"}, {@code "10m"},
 * {@code "1w3d12h"} into milliseconds, and formats milliseconds back to a
 * readable string.
 */
public final class DurationUtil {

    private static final Pattern TOKEN = Pattern.compile("(\\d+)\\s*([wdhms])", Pattern.CASE_INSENSITIVE);

    private DurationUtil() {
    }

    /**
     * Parse a duration string into milliseconds. Returns {@code fallbackMillis}
     * if the input is null, blank, or cannot be parsed.
     */
    public static long parseMillis(String input, long fallbackMillis) {
        if (input == null || input.isBlank()) {
            return fallbackMillis;
        }

        String trimmed = input.trim();
        if ("0".equals(trimmed)) {
            return 0L;
        }

        Matcher matcher = TOKEN.matcher(trimmed);
        long total = 0L;
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            long value;
            try {
                value = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ex) {
                return fallbackMillis;
            }
            String unit = matcher.group(2).toLowerCase();
            long multiplier = switch (unit) {
                case "w" -> 7L * 24L * 60L * 60L * 1000L;
                case "d" -> 24L * 60L * 60L * 1000L;
                case "h" -> 60L * 60L * 1000L;
                case "m" -> 60L * 1000L;
                case "s" -> 1000L;
                default -> 0L;
            };
            try {
                total = Math.addExact(total, Math.multiplyExact(value, multiplier));
            } catch (ArithmeticException ex) {
                return fallbackMillis;
            }
        }
        return matched ? total : fallbackMillis;
    }

    /**
     * Format milliseconds into a compact readable duration, e.g. "2d 4h 30m".
     * Returns "0s" for non-positive values.
     */
    public static String format(long millis) {
        if (millis <= 0) {
            return "0s";
        }
        long seconds = millis / 1000L;
        long days = seconds / 86400L;
        seconds %= 86400L;
        long hours = seconds / 3600L;
        seconds %= 3600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        // Only show seconds when the total is under an hour, to keep it tidy.
        if (seconds > 0 && days == 0 && hours == 0) {
            sb.append(seconds).append("s ");
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? "0s" : result;
    }
}
