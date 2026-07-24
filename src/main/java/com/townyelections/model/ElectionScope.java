package com.townyelections.model;

import java.util.Locale;

/**
 * The kind of Towny object an election belongs to.
 *
 * <ul>
 *   <li>{@link #TOWN} - a single town; only that town's residents may vote.</li>
 *   <li>{@link #NATION} - a nation; every resident of every town in the nation
 *       may vote.</li>
 * </ul>
 */
public enum ElectionScope {

    TOWN,
    NATION;

    /** Parse a stored/config value, falling back to the supplied default. */
    public static ElectionScope fromString(String value, ElectionScope fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return ElectionScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
