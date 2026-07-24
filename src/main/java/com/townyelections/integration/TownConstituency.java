package com.townyelections.integration;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.permissions.TownyPerms;
import com.townyelections.TownyElections;
import com.townyelections.model.ElectionScope;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A {@link Constituency} backed by a single Towny {@link Town}. */
public class TownConstituency implements Constituency {

    private final TownyElections plugin;
    private final Town town;

    public TownConstituency(TownyElections plugin, Town town) {
        this.plugin = plugin;
        this.town = town;
    }

    public Town getTown() {
        return town;
    }

    @Override
    public ElectionScope scope() {
        return ElectionScope.TOWN;
    }

    @Override
    public UUID getUuid() {
        return town.getUUID();
    }

    @Override
    public String getName() {
        return town.getName();
    }

    @Override
    public int getResidentCount() {
        return town.getNumResidents();
    }

    @Override
    public boolean isResident(UUID residentUuid) {
        return residentUuid != null && town.hasResident(residentUuid);
    }

    @Override
    public List<Resident> getResidents() {
        return new ArrayList<>(town.getResidents());
    }

    @Override
    public Resident getLeader() {
        return town.hasMayor() ? town.getMayor() : null;
    }

    @Override
    public List<String> grantRanks(Resident resident, List<String> ranks) {
        List<String> applied = new ArrayList<>();
        for (String rank : ranks) {
            String canonical = TownyPerms.matchTownRank(rank);
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

    @Override
    public void revokeRanks(Resident resident, List<String> ranks) {
        boolean changed = false;
        for (String rank : ranks) {
            String canonical = TownyPerms.matchTownRank(rank);
            if (canonical != null && resident.hasTownRank(canonical)) {
                resident.removeTownRank(canonical);
                changed = true;
            }
        }
        if (changed) {
            resident.save();
        }
    }

    @Override
    public boolean setLeader(Resident resident) {
        try {
            town.setMayor(resident);
            town.save();
            return true;
        } catch (Throwable ex) {
            try {
                town.forceSetMayor(resident);
                town.save();
                return true;
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to set mayor for town " + town.getName()
                        + ": " + t.getMessage());
                return false;
            }
        }
    }
}
