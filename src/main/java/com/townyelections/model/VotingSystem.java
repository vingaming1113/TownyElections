package com.townyelections.model;

import java.util.Locale;

/**
 * Electoral system used to collect and count ballots in one election. The
 * system is chosen from config when the election starts and persisted with the
 * election so a config change never re-interprets ballots mid-race.
 */
public enum VotingSystem {
    /** Each voter picks exactly one candidate; the most votes wins. */
    PLURALITY,
    /**
     * Voters rank candidates in order of preference. The count runs
     * instant-runoff rounds: the weakest candidate is eliminated and their
     * ballots transfer to each voter's next surviving preference until one
     * candidate holds a majority of the continuing ballots.
     */
    RANKED_CHOICE,
    /** Voters approve any number of candidates; the most approvals wins. */
    APPROVAL;

    /** Message key holding the display name for this system. */
    public String messageKey() {
        return "election.system-" + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static VotingSystem fromString(String value, VotingSystem fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return VotingSystem.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
