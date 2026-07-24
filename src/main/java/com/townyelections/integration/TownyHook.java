package com.townyelections.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.townyelections.TownyElections;
import com.townyelections.model.Election;
import com.townyelections.model.ElectionScope;
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

    public int getResidentCount(Town town) {
        return town.getNumResidents();
    }

    // ---- Nations -----------------------------------------------------------

    public Nation getNation(UUID uuid) {
        return TownyUniverse.getInstance().getNation(uuid);
    }

    public Nation getNationByName(String name) {
        return TownyUniverse.getInstance().getNation(name);
    }

    /** All nations currently registered with Towny. */
    public List<Nation> getNations() {
        return new ArrayList<>(TownyUniverse.getInstance().getNations());
    }

    /** The nation a player currently belongs to, or null. */
    public Nation getPlayerNation(Player player) {
        Resident resident = getResident(player);
        if (resident == null || !resident.hasNation()) {
            return null;
        }
        return resident.getNationOrNull();
    }

    // ---- Constituencies ----------------------------------------------------

    /** Wrap a town as a {@link Constituency}. */
    public Constituency of(Town town) {
        return town == null ? null : new TownConstituency(plugin, town);
    }

    /** Wrap a nation as a {@link Constituency}. */
    public Constituency of(Nation nation) {
        return nation == null ? null : new NationConstituency(plugin, nation);
    }

    /** Resolve the live {@link Constituency} an election belongs to, or null if gone. */
    public Constituency constituencyFor(Election election) {
        if (election == null) {
            return null;
        }
        if (election.getScope() == ElectionScope.NATION) {
            return of(getNation(election.getTownUuid()));
        }
        return of(getTown(election.getTownUuid()));
    }
}
