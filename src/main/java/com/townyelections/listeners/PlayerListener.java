package com.townyelections.listeners;

import com.palmergames.bukkit.towny.event.TownRemoveResidentEvent;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.townyelections.TownyElections;
import com.townyelections.integration.Constituency;
import com.townyelections.manager.CommandConfig;
import com.townyelections.manager.ElectionManager;
import com.townyelections.manager.MessageManager;
import com.townyelections.model.Election;
import com.townyelections.model.ElectionPhase;
import com.townyelections.model.ElectionScope;
import com.townyelections.util.DurationUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listens for player joins (to notify of active elections) and for residents
 * leaving a town (to withdraw them from any election they were part of).
 */
public class PlayerListener implements Listener {

    private final TownyElections plugin;
    private final ElectionManager elections;
    private final MessageManager messages;

    public PlayerListener(TownyElections plugin) {
        this.plugin = plugin;
        this.elections = plugin.getElectionManager();
        this.messages = plugin.getMessageManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfigManager().isNotifyOnJoin()) {
            return;
        }
        Player player = event.getPlayer();
        // Delay slightly so Towny has fully loaded the resident on login.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Town town = plugin.getTownyHook().getPlayerTown(player);
            if (town != null) {
                notifyElection(player, elections.getElection(town), ElectionScope.TOWN);
            }
            if (plugin.getConfigManager().isNationElectionsEnabled()) {
                Nation nation = plugin.getTownyHook().getPlayerNation(player);
                if (nation != null) {
                    notifyElection(player, elections.getElection(nation.getUUID()), ElectionScope.NATION);
                }
            }
        }, 40L);
    }

    private void notifyElection(Player player, Election election, ElectionScope scope) {
        if (election == null || election.getPhase() == ElectionPhase.CONCLUDED
                || election.getPhase() == ElectionPhase.CANCELLED) {
            return;
        }
        String run = commandLiteral(CommandConfig.RUN, scope);
        if (election.getPhase() == ElectionPhase.NOMINATION) {
            messages.send(player, "election.started-nomination", MessageManager.placeholders(
                    "town", election.getTownName(),
                    "duration", DurationUtil.format(election.getMillisRemaining()),
                    "label", "election",
                    "run", run));
        } else {
            messages.send(player, "election.time-remaining-voting", MessageManager.placeholders(
                    "time", DurationUtil.format(election.getMillisRemaining())));
        }
    }

    private String commandLiteral(String action, ElectionScope scope) {
        String literal = plugin.getCommandConfig().literal(action);
        if (scope == ElectionScope.NATION) {
            return plugin.getCommandConfig().literal(CommandConfig.NATION) + " " + literal;
        }
        return literal;
    }

    @EventHandler
    public void onResidentLeaveTown(TownRemoveResidentEvent event) {
        // This event may fire off the main thread; hop back on before touching state.
        final Resident resident = event.getResident();
        final Town town = event.getTown();
        if (resident == null || town == null) {
            return;
        }
        final Nation nation = town.getNationOrNull();
        Runnable task = () -> {
            cleanupTown(resident, town);
            if (nation != null) {
                cleanupNation(resident, nation);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private void cleanupTown(Resident resident, Town town) {
        Election election = elections.getElection(town.getUUID());
        removeFromElection(resident, election);
    }

    /**
     * When someone leaves a town they may or may not still belong to that town's
     * nation (they could have moved to another town in it). Only clean up the
     * nation election if they are no longer a resident of the nation.
     */
    private void cleanupNation(Resident resident, Nation nation) {
        Election election = elections.getElection(nation.getUUID());
        if (election == null) {
            return;
        }
        Constituency constituency = plugin.getTownyHook().of(nation);
        if (constituency.isResident(resident.getUUID())) {
            return;
        }
        removeFromElection(resident, election);
    }

    private void removeFromElection(Resident resident, Election election) {
        if (election == null) {
            return;
        }
        if (election.isCandidate(resident.getUUID())) {
            election.removeCandidate(resident.getUUID());
            elections.save();
        } else if (election.hasVoted(resident.getUUID())) {
            // Remove their ballot so tallies reflect only current members.
            election.removeBallot(resident.getUUID());
            elections.save();
        }
    }
}
