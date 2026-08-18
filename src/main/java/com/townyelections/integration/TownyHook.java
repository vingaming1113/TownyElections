package com.townyelections.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.townyelections.TownyElections;
import com.townyelections.model.Election;
import org.bukkit.entity.Player;

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

    public int getResidentCount(Town town) {
        return town.getNumResidents();
    }

    // ---- Constituencies ----------------------------------------------------

    /** Wrap a town as a {@link Constituency}. */
    public Constituency of(Town town) {
        return town == null ? null : new TownConstituency(plugin, town);
    }

    /** Resolve the live {@link Constituency} an election belongs to, or null if gone. */
    public Constituency constituencyFor(Election election) {
        if (election == null) {
            return null;
        }
        return of(getTown(election.getTownUuid()));
    }
}
