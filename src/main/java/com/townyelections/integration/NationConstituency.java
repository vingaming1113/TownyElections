package com.townyelections.integration;

import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.permissions.TownyPerms;
import com.townyelections.TownyElections;
import com.townyelections.model.ElectionScope;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A {@link Constituency} backed by a Towny {@link Nation}. */
public class NationConstituency implements Constituency {

    private final TownyElections plugin;
    private final Nation nation;

    public NationConstituency(TownyElections plugin, Nation nation) {
        this.plugin = plugin;
        this.nation = nation;
    }

    public Nation getNation() {
        return nation;
    }

    @Override
    public ElectionScope scope() {
        return ElectionScope.NATION;
    }

    @Override
    public UUID getUuid() {
        return nation.getUUID();
    }

    @Override
    public String getName() {
        return nation.getName();
    }

    @Override
    public int getResidentCount() {
        return nation.getNumResidents();
    }

    @Override
    public boolean isResident(UUID residentUuid) {
        if (residentUuid == null) {
            return false;
        }
        Resident resident = TownyUniverse.getInstance().getResident(residentUuid);
        return resident != null && nation.hasResident(resident);
    }

    @Override
    public List<Resident> getResidents() {
        return new ArrayList<>(nation.getResidents());
    }

    @Override
    public Resident getLeader() {
        return nation.hasKing() ? nation.getKing() : null;
    }

    @Override
    public List<String> grantRanks(Resident resident, List<String> ranks) {
        List<String> applied = new ArrayList<>();
        for (String rank : ranks) {
            String canonical = TownyPerms.matchNationRank(rank);
            if (canonical == null) {
                plugin.getLogger().warning("Configured winner rank '" + rank
                        + "' is not a valid Towny nation rank; skipping.");
                continue;
            }
            if (!resident.hasNationRank(canonical)) {
                resident.addNationRank(canonical);
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
            String canonical = TownyPerms.matchNationRank(rank);
            if (canonical != null && resident.hasNationRank(canonical)) {
                resident.removeNationRank(canonical);
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
            nation.setKing(resident);
            nation.save();
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to set king for nation " + nation.getName()
                    + ": " + t.getMessage());
            return false;
        }
    }
}
