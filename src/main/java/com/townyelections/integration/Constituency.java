package com.townyelections.integration;

import com.palmergames.bukkit.towny.object.Resident;
import com.townyelections.model.ElectionScope;

import java.util.List;
import java.util.UUID;

/**
 * A Towny object an election can be held for - either a single town or a whole
 * nation. This abstraction lets {@link com.townyelections.manager.ElectionManager}
 * treat town and nation elections uniformly while all Towny API access stays in
 * the {@code integration} package.
 */
public interface Constituency {

    /** Whether this constituency is a town or a nation. */
    ElectionScope scope();

    /** The Towny UUID of the town or nation. */
    UUID getUuid();

    /** The current display name of the town or nation. */
    String getName();

    /** Number of eligible residents (town residents, or all nation residents). */
    int getResidentCount();

    /** Whether the given resident currently belongs to this constituency. */
    boolean isResident(UUID residentUuid);

    /** Every resident able to participate; used for broadcasts and reminders. */
    List<Resident> getResidents();

    /** The current office holder (mayor for a town, king for a nation), or null. */
    Resident getLeader();

    /**
     * Grant the configured ranks to the winner. Invalid ranks are skipped and
     * logged. Returns the ranks that were actually applied.
     */
    List<String> grantRanks(Resident resident, List<String> ranks);

    /** Remove the configured ranks from a resident (the previous holder). */
    void revokeRanks(Resident resident, List<String> ranks);

    /**
     * Transfer leadership (mayorship or kingship) to the winner. Returns true on
     * success.
     */
    boolean setLeader(Resident resident);
}
