package com.townyelections.integration;

import com.palmergames.bukkit.towny.object.Resident;

import java.util.List;
import java.util.UUID;

/**
 * A Towny town an election can be held for.
 */
public interface Constituency {

    /** The Towny UUID of the town. */
    UUID getUuid();

    /** The current display name of the town. */
    String getName();

    /** Number of eligible residents. */
    int getResidentCount();

    /** Whether the given resident currently belongs to this constituency. */
    boolean isResident(UUID residentUuid);

    /** Every resident able to participate; used for broadcasts and reminders. */
    List<Resident> getResidents();

    /** The current mayor, or null. */
    Resident getLeader();

    /**
     * Grant the configured ranks to the winner. Invalid ranks are skipped and
     * logged. Returns the ranks that were actually applied.
     */
    List<String> grantRanks(Resident resident, List<String> ranks);

    /** Remove the configured ranks from a resident (the previous holder). */
    void revokeRanks(Resident resident, List<String> ranks);

    /** Transfer mayorship to the winner. */
    boolean setLeader(Resident resident);
}
