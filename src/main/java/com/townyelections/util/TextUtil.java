package com.townyelections.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for colourising text. Supports legacy {@code &} codes and hex
 * codes in the form {@code &#RRGGBB}, converting them into Adventure
 * {@link Component}s (Paper's native text API).
 */
public final class TextUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_INPUT = Pattern.compile("#?([A-Fa-f0-9]{6})");

    /** Named colours accepted by {@link #parseColor(String)} mapped to their legacy code. */
    private static final Map<String, String> NAMED_COLORS = Map.ofEntries(
            Map.entry("black", "&0"),
            Map.entry("dark_blue", "&1"),
            Map.entry("dark_green", "&2"),
            Map.entry("dark_aqua", "&3"),
            Map.entry("dark_red", "&4"),
            Map.entry("dark_purple", "&5"),
            Map.entry("gold", "&6"),
            Map.entry("gray", "&7"),
            Map.entry("grey", "&7"),
            Map.entry("dark_gray", "&8"),
            Map.entry("dark_grey", "&8"),
            Map.entry("blue", "&9"),
            Map.entry("green", "&a"),
            Map.entry("aqua", "&b"),
            Map.entry("cyan", "&b"),
            Map.entry("red", "&c"),
            Map.entry("light_purple", "&d"),
            Map.entry("pink", "&d"),
            Map.entry("magenta", "&d"),
            Map.entry("yellow", "&e"),
            Map.entry("white", "&f"));

    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.builder()
                    .character('\u00A7') // section sign
                    .hexColors()
                    .build();

    private TextUtil() {
    }

    /**
     * Translate a raw string containing {@code &} colour codes and {@code &#hex}
     * codes into an Adventure {@link Component}.
     */
    public static Component colorize(String input) {
        if (input == null) {
            return Component.empty();
        }
        return SERIALIZER.deserialize(legacy(input));
    }

    /** Translate supported colour codes into Bukkit legacy section-sign text. */
    public static String legacy(String input) {
        if (input == null) {
            return "";
        }
        return translateCodes(input);
    }

    /**
     * Validate and normalise a user-supplied colour into a legacy code string
     * (for example {@code &c} or {@code &#aabbcc}) suitable for prefixing text.
     * Accepts named colours ({@code red}, {@code dark_blue}, ...), single legacy
     * codes ({@code &c} or {@code c}), and hex ({@code #RRGGBB} or
     * {@code &#RRGGBB}). Only colours are accepted, not formatting codes.
     * Returns {@code null} if the input is not a recognised colour.
     */
    public static String parseColor(String input) {
        if (input == null) {
            return null;
        }
        String value = input.trim();
        if (value.isEmpty()) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);

        // Hex, with or without a leading '&'.
        String hexCandidate = lower.startsWith("&") ? lower.substring(1) : lower;
        Matcher hex = HEX_INPUT.matcher(hexCandidate);
        if (hex.matches()) {
            return "&#" + hex.group(1);
        }

        // Named colour.
        String named = NAMED_COLORS.get(lower);
        if (named != null) {
            return named;
        }

        // Single legacy colour code, e.g. "&c" or "c".
        String code = lower.startsWith("&") ? lower.substring(1) : lower;
        if (code.length() == 1 && "0123456789abcdef".indexOf(code.charAt(0)) > -1) {
            return "&" + code;
        }
        return null;
    }

    private static String translateCodes(String input) {
        // Convert &#RRGGBB -> section-sign x <RRGGBB hex sequence> understood by the serializer.
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("\u00A7x");
            for (char c : hex.toCharArray()) {
                replacement.append('\u00A7').append(c);
            }
            matcher.appendReplacement(builder, replacement.toString());
        }
        matcher.appendTail(builder);

        // Convert remaining legacy '&' codes to section sign.
        String out = builder.toString();
        StringBuilder legacy = new StringBuilder(out.length());
        char[] chars = out.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '&' && i + 1 < chars.length
                    && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(chars[i + 1]) > -1) {
                legacy.append('\u00A7');
            } else {
                legacy.append(chars[i]);
            }
        }
        return legacy.toString();
    }
}
