package com.townyelections.listeners;

import com.palmergames.bukkit.towny.event.TownRemoveResidentEvent;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.townyelections.TownyElections;
import com.townyelections.manager.CommandConfig;
import com.townyelections.manager.ElectionManager;
import com.townyelections.manager.MessageManager;
import com.townyelections.model.Election;
import com.townyelections.model.ElectionPhase;
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
            if (town == null) {
                return;
            }
            Election election = elections.getElection(town);
            if (election == null || election.getPhase() == ElectionPhase.CONCLUDED
                    || election.getPhase() == ElectionPhase.CANCELLED) {
                return;
            }

            String label = "election";
            if (election.getPhase() == ElectionPhase.NOMINATION) {
                messages.send(player, "election.started-nomination", MessageManager.placeholders(
                        "town", town.getName(),
                        "duration", DurationUtil.format(election.getMillisRemaining()),
                        "label", label,
                        "run", plugin.getCommandConfig().literal(CommandConfig.RUN)));
            } else {
                messages.send(player, "election.time-remaining-voting", MessageManager.placeholders(
                        "time", DurationUtil.format(election.getMillisRemaining())));
            }
        }, 40L);
    }

    @EventHandler
    public void onResidentLeaveTown(TownRemoveResidentEvent event) {
        // This event may fire off the main thread; hop back on before touching state.
        final Resident resident = event.getResident();
        final Town town = event.getTown();
        if (resident == null || town == null) {
            return;
        }
        Runnable task = () -> {
            Election election = elections.getElection(town.getUUID());
            if (election == null) {
                return;
            }
            // If the departing resident was a candidate, withdraw them.
            if (election.isCandidate(resident.getUUID())) {
                election.removeCandidate(resident.getUUID());
                elections.save();
            } else if (election.hasVoted(resident.getUUID())) {
                // Remove their ballot so tallies reflect only current residents.
                election.removeBallot(resident.getUUID());
                elections.save();
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
