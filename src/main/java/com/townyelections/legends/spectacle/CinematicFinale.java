package com.townyelections.legends.spectacle;

import com.townyelections.TownyElections;
import com.townyelections.legends.Ideology;
import com.townyelections.legends.engine.BossBattleArena;
import com.townyelections.legends.engine.DimensionGenerator;
import com.townyelections.legends.engine.ProphecyEngine;
import com.townyelections.legends.system.AscensionSystem;
import com.townyelections.legends.system.CursesSystem;
import com.townyelections.legends.system.MonumentSystem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates a 5-10 minute cinematic spectacle that plays at the conclusion
 * of the Election of Legends. The sequence includes:
 * <ol>
 *   <li>Gather all players at the arena</li>
 *   <li>Boss battle music and spawns</li>
 *   <li>Real-time damage tracking on action bars</li>
 *   <li>Dramatic winner announcement with explosions</li>
 *   <li>Lightning strikes and particle storms</li>
 *   <li>Winner's dimension opening</li>
 *   <li>Monument building itself</li>
 *   <li>Results announcement</li>
 *   <li>Rewards distribution</li>
 *   <li>Server enters "New Age" for 30 days</li>
 * </ol>
 *
 * <p>The finale is driven by a series of scheduled tasks, each triggering the
 * next step in the sequence.
 */
public class CinematicFinale implements Runnable {

    private final TownyElections plugin;
    private final BossBattleArena arena;
    private final ProphecyEngine prophecies;
    private final AscensionSystem ascension;
    private final CursesSystem curses;
    private final MonumentSystem monument;
    private final DimensionGenerator dimensions;

    private final List<String> losingCandidates;
    private final String winnerName;
    private final UUID winnerUuid;
    private final int electionNumber;
    private final long consequencesDurationMs;

    private BukkitTask primaryTask;
    private List<BukkitTask> childTasks = new ArrayList<>();

    public CinematicFinale(TownyElections plugin, BossBattleArena arena, ProphecyEngine prophecies,
                           AscensionSystem ascension, CursesSystem curses, MonumentSystem monument,
                           DimensionGenerator dimensions, List<String> losingCandidates,
                           String winnerName, UUID winnerUuid, int electionNumber,
                           long consequencesDurationMs) {
        this.plugin = plugin;
        this.arena = arena;
        this.prophecies = prophecies;
        this.ascension = ascension;
        this.curses = curses;
        this.monument = monument;
        this.dimensions = dimensions;
        this.losingCandidates = losingCandidates;
        this.winnerName = winnerName;
        this.winnerUuid = winnerUuid;
        this.electionNumber = electionNumber;
        this.consequencesDurationMs = consequencesDurationMs;
    }

    /**
     * Begin the cinematic finale. This is the entry point — it schedules the
     * entire sequence of events.
     */
    @Override
    public void run() {
        broadcastTitle("§6§l⚔ THE FINALE BEGINS ⚔", "§7All players, gather at the arena!", 20, 80, 20);
        Bukkit.broadcastMessage("§6§l========================================");
        Bukkit.broadcastMessage("§6§l  ELECTION OF LEGENDS — THE FINALE");
        Bukkit.broadcastMessage("§6§l========================================");

        // Step 1: Teleport all players to arena (after 5 seconds)
        scheduleChild(() -> gatherPlayersAtArena(), 100L);

        // Step 2: Start boss battle (after 10 seconds)
        scheduleChild(() -> {
            broadcastAction("§c§lBosses spawn in 3...");
            scheduleChild(() -> broadcastAction("§c§l2..."), 40L);
            scheduleChild(() -> broadcastAction("§c§l1..."), 80L);
            scheduleChild(() -> {
                broadcastAction("§c§lFIGHT!");
                arena.startBattle();
            }, 120L);
        }, 200L);

        // Step 3: Monitor battle — poll for completion every 5 seconds
        primaryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!arena.isBattleActive()) {
                handleBattleEnd();
                primaryTask.cancel();
            }
        }, 300L, 100L);
    }

    private void gatherPlayersAtArena() {
        Location center = arena.getArenaCenter();
        if (center == null) return;

        World world = center.getWorld();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(center.clone().add(
                    (Math.random() - 0.5) * 10, 0, (Math.random() - 0.5) * 10));
        }
        world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        world.spawnParticle(Particle.PORTAL, center, 200, 10, 5, 10, 0.3);
        broadcastAction("§dAll players have been gathered at the arena!");
    }

    private void handleBattleEnd() {
        Ideology winner = arena.getWinningIdeology();
        if (winner == null) {
            Bukkit.broadcastMessage("§c§lThe battle ended without a clear victor. The old ways persist.");
            return;
        }

        Location center = arena.getArenaCenter();

        // Dramatic announcement
        broadcastTitle(winner.getChatColor() + "§l" + winner.getDisplayName().toUpperCase() + " WINS!",
                "§7" + winner.getSlogan(), 20, 100, 40);

        scheduleChild(() -> {
            // Massive explosion particles
            World world = center.getWorld();
            world.strikeLightningEffect(center);
            world.spawnParticle(Particle.EXPLOSION_HUGE, center, 5, 3, 3, 3, 0);
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLOSION, 2.0f, 0.5f);
            world.playSound(center, Sound.ENTITY_WITHER_DEATH, 2.0f, 0.8f);
            broadcastAction(winner.getChatColor() + "§l" + winner.getDisplayName() + " has conquered!");
        }, 60L);

        // Prophecies execution
        scheduleChild(() -> {
            Bukkit.broadcastMessage("§5§lThe Prophecies begin to unfold...");
            prophecies.executeProphecies(30);
        }, 160L);

        // Ascend winner, curse losers
        scheduleChild(() -> {
            Player winnerPlayer = Bukkit.getPlayer(winnerUuid);
            if (winnerPlayer != null) {
                ascension.ascend(winnerPlayer, winner, consequencesDurationMs);
            }
            for (String loserName : losingCandidates) {
                Player loser = Bukkit.getPlayer(loserName);
                if (loser != null) {
                    curses.applyCurse(loser, winner, consequencesDurationMs);
                }
            }
        }, 200L);

        // Unlock dimension
        scheduleChild(() -> {
            Bukkit.broadcastMessage("§5§lA new dimension tears open...");
            dimensions.generateDimension(winner);
        }, 260L);

        // Build monument
        scheduleChild(() -> {
            if (center != null) {
                monument.buildMonumentAnimated(center, winner, winnerName, winnerUuid, electionNumber);
            }
        }, 300L);

        // Final broadcast
        scheduleChild(() -> {
            Bukkit.broadcastMessage("§6§l========================================");
            Bukkit.broadcastMessage("§6§l  THE " + winner.getDisplayName().toUpperCase() + " AGE BEGINS");
            Bukkit.broadcastMessage("§6§l  " + winner.getSlogan());
            Bukkit.broadcastMessage("§6§l  A new era for the next 30 days.");
            Bukkit.broadcastMessage("§6§l========================================");

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendTitle(winner.getChatColor() + "§lA NEW AGE BEGINS",
                        "§7The " + winner.getDisplayName() + "s rule for 30 days", 20, 100, 40);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }, 500L);
    }

    /** Cancel the finale (cleanup). */
    public void cancel() {
        if (primaryTask != null) {
            primaryTask.cancel();
        }
        for (BukkitTask task : childTasks) {
            task.cancel();
        }
        childTasks.clear();
    }

    // ========================================================================
    //  Helpers
    // ========================================================================

    private void scheduleChild(Runnable runnable, long delayTicks) {
        childTasks.add(Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks));
    }

    private void broadcastTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    private void broadcastAction(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(message);
        }
    }
}
