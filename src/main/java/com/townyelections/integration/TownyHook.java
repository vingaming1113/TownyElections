package com.townyelections.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.permissions.TownyPerms;
import com.townyelections.TownyElections;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Thin, defensive wrapper around the Towny API. All access to Towny goes
 * through here so that upstream API changes are isolated to a single class.
 */
public class TownyHook {

    private final TownyElections plugin;

    public TownyHook(TownyElections plugin) {
        this.plugin = plugin;
    }

    // ---- Residents / Towns -------------------------------------------------

    public Resident getResident(Player player) {
        return TownyAPI.getInstance().getResident(player.getUniqueId());
    }

    public Resident getResident(UUID uuid) {
        return TownyUniverse.getInstance().getResident(uuid);
    }

    public Resident getResidentByName(String name) {
        return TownyUniverse.getInstance().getResident(name);
    }

    public Town getTown(UUID uuid) {
        return TownyUniverse.getInstance().getTown(uuid);
    }

    public Town getTownByName(String name) {
        return TownyUniverse.getInstance().getTown(name);
    }

    /** The town a player currently belongs to, or null. */
    public Town getPlayerTown(Player player) {
        Resident resident = getResident(player);
        if (resident == null || !resident.hasTown()) {
            return null;
        }
        return resident.getTownOrNull();
    }

    public boolean isResidentOfTown(UUID residentUuid, Town town) {
        if (town == null) {
            return false;
        }
        return town.hasResident(residentUuid);
    }

    public boolean isMayor(Resident resident) {
        return resident != null && resident.isMayor();
    }

    public List<UUID> getResidentUuids(Town town) {
        List<UUID> uuids = new ArrayList<>();
        for (Resident resident : town.getResidents()) {
            uuids.add(resident.getUUID());
        }
        return uuids;
    }

    public int getResidentCount(Town town) {
        return town.getNumResidents();
    }

    // ---- Ranks -------------------------------------------------------------

    /** All town ranks defined in Towny's townyperms.yml. */
    public List<String> getAvailableTownRanks() {
        return new ArrayList<>(TownyPerms.getTownRanks());
    }

    /** Normalise user/config input to a canonical town rank, or null if invalid. */
    public String matchTownRank(String rank) {
        if (rank == null) {
            return null;
        }
        return TownyPerms.matchTownRank(rank);
    }

    /**
     * Grant a set of town ranks to a resident. Invalid ranks are skipped and
     * logged. Returns the list of ranks that were actually applied.
     */
    public List<String> grantRanks(Resident resident, List<String> ranks) {
        List<String> applied = new ArrayList<>();
        for (String rank : ranks) {
            String canonical = matchTownRank(rank);
            if (canonical == null) {
                plugin.getLogger().warning("Configured winner rank '" + rank
                        + "' is not a valid Towny town rank; skipping.");
                continue;
            }
            if (!resident.hasTownRank(canonical)) {
                resident.addTownRank(canonical);
            }
            applied.add(canonical);
        }
        if (!applied.isEmpty()) {
            resident.save();
        }
        return applied;
    }

    /** Remove a set of town ranks from a resident (used to revoke from the previous holder). */
    public void revokeRanks(Resident resident, List<String> ranks) {
        boolean changed = false;
        for (String rank : ranks) {
            String canonical = matchTownRank(rank);
            if (canonical != null && resident.hasTownRank(canonical)) {
                resident.removeTownRank(canonical);
                changed = true;
            }
        }
        if (changed) {
            resident.save();
        }
    }

    /**
     * Transfer mayorship of a town to the given resident. Returns true on success.
     */
    public boolean setMayor(Town town, Resident newMayor) {
        try {
            town.setMayor(newMayor);
            town.save();
            return true;
        } catch (Throwable ex) {
            // Fall back to a forced assignment if the checked path refuses.
            try {
                town.forceSetMayor(newMayor);
                town.save();
                return true;
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to set mayor for town " + town.getName()
                        + ": " + t.getMessage());
                return false;
            }
        }
    }

    public Resident getMayor(Town town) {
        return town.hasMayor() ? town.getMayor() : null;
    }
}
