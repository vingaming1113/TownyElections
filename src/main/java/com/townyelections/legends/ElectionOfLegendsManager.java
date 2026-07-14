package com.townyelections.legends;

import com.townyelections.TownyElections;
import com.townyelections.legends.boss.IdeologyBoss;
import com.townyelections.legends.engine.BossBattleArena;
import com.townyelections.legends.engine.DimensionGenerator;
import com.townyelections.legends.engine.ProphecyEngine;
import com.townyelections.legends.engine.WorldAlterationEngine;
import com.townyelections.legends.listener.AscensionListener;
import com.townyelections.legends.listener.BossBattleListener;
import com.townyelections.legends.listener.ProphecyExecutionListener;
import com.townyelections.legends.spectacle.CinematicFinale;
import com.townyelections.legends.system.AIPredictionEngine;
import com.townyelections.legends.system.AscensionSystem;
import com.townyelections.legends.system.CursesSystem;
import com.townyelections.legends.system.ElectionFeedbackSystem;
import com.townyelections.legends.system.MonumentSystem;
import com.townyelections.legends.system.SentimentAnalyzer;
import com.townyelections.legends.system.TemporalSimulator;
import com.townyelections.legends.system.VoteWeightSystem;
import com.townyelections.model.Election;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Central orchestrator for the Election of Legends system. Wires together all
 * sub-systems and drives the complete lifecycle:
 *
 * <ol>
 *   <li>Pre-election: generate prophecies, reset state</li>
 *   <li>Boss battle: spawn arena, track damage → votes</li>
 *   <li>Post-battle: determine winner, apply world alterations, ascend
 *       winner, curse losers, unlock dimension, build monument, execute
 *       prophecies</li>
 *   <li>Consequences: run for 30 days, then revert</li>
 * </ol>
 *
 * <p>This manager is created as a sibling to the existing {@code ElectionManager}
 * and hooks into its lifecycle via the {@link TownyElections} plugin instance.
 */
public class ElectionOfLegendsManager {

    private final TownyElections plugin;
    private final ElectionOfLegendsConfig config;

    // Sub-systems
    private final WorldAlterationEngine worldAlteration;
    private final ProphecyEngine prophecyEngine;
    private final AscensionSystem ascensionSystem;
    private final CursesSystem cursesSystem;
    private final MonumentSystem monumentSystem;
    private final VoteWeightSystem voteWeightSystem;
    private final TemporalSimulator temporalSimulator;
    private final DimensionGenerator dimensionGenerator;
    private final BossBattleArena bossBattleArena;

    // AI-powered systems
    private final SentimentAnalyzer sentimentAnalyzer;
    private final AIPredictionEngine aiPredictionEngine;
    private final ElectionFeedbackSystem feedbackSystem;

    // Listeners
    private BossBattleListener bossListener;
    private AscensionListener ascensionListener;
    private ProphecyExecutionListener prophecyListener;

    // State
    private boolean legendsActive;
    private CinematicFinale activeFinale;
    private Ideology winningIdeology;

    public ElectionOfLegendsManager(TownyElections plugin) {
        this.plugin = plugin;
        this.config = new ElectionOfLegendsConfig(plugin);

        this.worldAlteration = new WorldAlterationEngine(plugin);
        this.prophecyEngine = new ProphecyEngine(plugin);
        this.ascensionSystem = new AscensionSystem(plugin);
        this.cursesSystem = new CursesSystem(plugin);
        this.monumentSystem = new MonumentSystem(plugin);
        this.voteWeightSystem = new VoteWeightSystem(
                config.getBaseVoteCost(), config.getCostMultiplier(), config.getMaxVotesPerPlayer());
        this.temporalSimulator = new TemporalSimulator();
        this.dimensionGenerator = new DimensionGenerator(plugin);
        this.bossBattleArena = new BossBattleArena(plugin, voteWeightSystem);

        // AI-powered systems
        this.sentimentAnalyzer = new SentimentAnalyzer();
        this.aiPredictionEngine = new AIPredictionEngine();
        this.feedbackSystem = new ElectionFeedbackSystem(plugin);
    }

    // ========================================================================
    //  Lifecycle
    // ========================================================================

    /** Load config and register listeners. Called from plugin onEnable. */
    public void load() {
        config.load();

        if (!config.isEnabled()) {
            plugin.getLogger().info("Election of Legends is disabled in config.");
            return;
        }

        // Register listeners
        bossListener = new BossBattleListener(plugin, bossBattleArena);
        ascensionListener = new AscensionListener(plugin, ascensionSystem);
        prophecyListener = new ProphecyExecutionListener(plugin, prophecyEngine);

        Bukkit.getPluginManager().registerEvents(bossListener, plugin);
        Bukkit.getPluginManager().registerEvents(ascensionListener, plugin);
        Bukkit.getPluginManager().registerEvents(prophecyListener, plugin);

        plugin.getLogger().info("Election of Legends loaded. Next trigger at election #"
                + (config.getGlobalElectionCount() + config.getTriggerEveryNthElection()
                - (config.getGlobalElectionCount() % config.getTriggerEveryNthElection())));
    }

    /** Clean up on disable. */
    public void shutdown() {
        if (activeFinale != null) {
            activeFinale.cancel();
        }
        worldAlteration.revert();
        prophecyEngine.cancel();
        prophecyListener.stopContinuousEffects();
        bossBattleArena.endBattle();
        dimensionGenerator.closeAllDimensions();
    }

    // ========================================================================
    //  Election hook — called by ElectionManager when elections conclude
    // ========================================================================

    /**
     * Called by the existing {@code ElectionManager} when any election concludes.
     * Checks whether this should trigger a Legends event.
     *
     * @param election the concluded election
     * @param winnerUuid the winner's UUID (may be null)
     * @param winnerName the winner's name (may be null)
     * @param losingCandidates names of losing candidates
     */
    public void onElectionConcluded(Election election, UUID winnerUuid, String winnerName,
                                     List<String> losingCandidates) {
        if (!config.isEnabled()) return;

        int count = config.incrementAndGetGlobalElectionCount();

        if (!config.shouldTriggerLegends()) {
            return; // Not the Nth election yet
        }

        // IT'S TIME
        triggerLegendsEvent(election.getTownUuid(), winnerUuid, winnerName, losingCandidates);
    }

    // ========================================================================
    //  Legends event trigger
    // ========================================================================

    private void triggerLegendsEvent(UUID townUuid, UUID winnerUuid, String winnerName,
                                      List<String> losingCandidates) {
        plugin.getLogger().info("§6§l=== ELECTION OF LEGENDS TRIGGERED === Election #"
                + config.getGlobalElectionCount());

        legendsActive = true;

        // Reset previous state
        voteWeightSystem.reset();
        ascensionSystem.revokeAll();
        cursesSystem.revokeAll();
        worldAlteration.revert();
        prophecyEngine.cancel();
        prophecyListener.stopContinuousEffects();

        // Start AI-powered tracking
        sentimentAnalyzer.startTracking();
        aiPredictionEngine.reset();
        feedbackSystem.start();

        // Generate prophecies (preliminary — not ideology-biased yet)
        prophecyEngine.selectProphecies(config.getProphecyCount(), null);

        // Prepare arena
        World arenaWorld = Bukkit.getWorld(config.getArenaWorld());
        if (arenaWorld == null) {
            arenaWorld = Bukkit.getWorlds().get(0);
        }
        Location arenaCenter = new Location(arenaWorld, config.getArenaX(), config.getArenaY(), config.getArenaZ());
        bossBattleArena.prepareArena(arenaCenter, config.getArenaRadius());

        // Wire AI-powered systems into the battle arena
        bossBattleArena.setFeedbackSystem(feedbackSystem);
        bossBattleArena.setSentimentAnalyzer(sentimentAnalyzer);

        // Announce
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§6§l⚡ §e§lELECTION OF LEGENDS §6§l⚡");
        Bukkit.broadcastMessage("§7The " + config.getGlobalElectionCount()
                + getOrdinalSuffix(config.getGlobalElectionCount()) + " election has triggered");
        Bukkit.broadcastMessage("§7a reality-warping battle for the fate of this world!");
        Bukkit.broadcastMessage("§7Four ideologies clash. Only one shall reshape reality.");
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§c⚔ Warmonger §8| §b⛏ Builder §8| §e💰 Merchant §8| §d🔮 Mystic");
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§7Use §e/election legends vote <ideology> §7to purchase weighted votes!");
        Bukkit.broadcastMessage("§7The boss battle begins in §c60 seconds§7. Gather at the arena!");
        Bukkit.broadcastMessage("");

        // Schedule the finale after 60 seconds
        String effectiveWinner = winnerName != null ? winnerName : "Unknown";
        UUID effectiveUuid = winnerUuid != null ? winnerUuid : UUID.randomUUID();
        List<String> losers = losingCandidates != null ? losingCandidates : List.of();

        // Bias prophecies toward a random ideology initially; re-selected after battle
        prophecyEngine.selectProphecies(config.getProphecyCount(), null);

        activeFinale = new CinematicFinale(plugin, bossBattleArena, prophecyEngine,
                ascensionSystem, cursesSystem, monumentSystem, dimensionGenerator,
                losers, effectiveWinner, effectiveUuid,
                config.getGlobalElectionCount(), config.getConsequencesDurationMs());

        Bukkit.getScheduler().runTaskLater(plugin, activeFinale, 1200L); // 60 seconds
    }

    /**
     * Called after the boss battle ends and the winning ideology is determined.
     * Applies permanent world alterations.
     */
    public void applyWinningConsequences(Ideology winner) {
        this.winningIdeology = winner;

        // Re-select prophecies biased toward winner
        prophecyEngine.selectProphecies(config.getProphecyCount(), winner);

        // Apply world alterations for 30 days
        worldAlteration.applyAlterations(winner, config.getConsequencesDurationMs());

        // Start continuous prophecy effects
        prophecyListener.startContinuousEffects();

        plugin.getLogger().info("Applied " + winner.getDisplayName()
                + " consequences for " + config.getConsequencesDurationDays() + " days.");
    }

    // ========================================================================
    //  Manual/admin triggers
    // ========================================================================

    /** Manually trigger a legends event (admin command). */
    public void forceTriggerLegends() {
        triggerLegendsEvent(null, null, "The People", List.of());
    }

    /** Force-revert all legends consequences (admin command). */
    public void forceRevertConsequences() {
        worldAlteration.revert();
        prophecyEngine.cancel();
        prophecyListener.stopContinuousEffects();
        ascensionSystem.revokeAll();
        cursesSystem.revokeAll();
        dimensionGenerator.closeAllDimensions();
        sentimentAnalyzer.stopTracking();
        feedbackSystem.stop();
        aiPredictionEngine.reset();
        legendsActive = false;
        winningIdeology = null;
        Bukkit.broadcastMessage("§c§lAll Election of Legends consequences have been forcefully reverted.");
    }

    // ========================================================================
    //  Accessors
    // ========================================================================

    public boolean isLegendsActive() { return legendsActive; }
    public Ideology getWinningIdeology() { return winningIdeology; }
    public ElectionOfLegendsConfig getConfig() { return config; }
    public WorldAlterationEngine getWorldAlteration() { return worldAlteration; }
    public ProphecyEngine getProphecyEngine() { return prophecyEngine; }
    public AscensionSystem getAscensionSystem() { return ascensionSystem; }
    public CursesSystem getCursesSystem() { return cursesSystem; }
    public MonumentSystem getMonumentSystem() { return monumentSystem; }
    public VoteWeightSystem getVoteWeightSystem() { return voteWeightSystem; }
    public TemporalSimulator getTemporalSimulator() { return temporalSimulator; }
    public DimensionGenerator getDimensionGenerator() { return dimensionGenerator; }
    public BossBattleArena getBossBattleArena() { return bossBattleArena; }
    public SentimentAnalyzer getSentimentAnalyzer() { return sentimentAnalyzer; }
    public AIPredictionEngine getAIPredictionEngine() { return aiPredictionEngine; }
    public ElectionFeedbackSystem getFeedbackSystem() { return feedbackSystem; }

    // ========================================================================
    //  Utility
    // ========================================================================

    private String getOrdinalSuffix(int number) {
        if (number % 100 >= 11 && number % 100 <= 13) return "th";
        return switch (number % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }
}
