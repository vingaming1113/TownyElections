package com.townyelections.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for colourising text. Supports legacy {@code &} codes and hex
 * codes in the form {@code &#RRGGBB}, converting them into Adventure
 * {@link Component}s (Paper's native text API).
 */
public final class TextUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
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
