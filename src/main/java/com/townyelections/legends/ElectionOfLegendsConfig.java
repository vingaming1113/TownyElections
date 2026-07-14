package com.townyelections.legends;

import com.townyelections.TownyElections;
import com.townyelections.util.DurationUtil;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed, cached access to the {@code legends} section of {@code config.yml}.
 * Values are read once on load; hot paths never touch YAML.
 */
public class ElectionOfLegendsConfig {

    private final TownyElections plugin;

    private boolean enabled;
    private int triggerEveryNthElection;
    private long consequencesDurationDays;
    private long consequencesDurationMs;

    // Boss battle
    private String arenaWorld;
    private int arenaX, arenaY, arenaZ;
    private double bossHealth;
    private int arenaRadius;
    private boolean effectsEnabled;
    private boolean soundEffectsEnabled;

    // Vote weights
    private double baseVoteCost;
    private double costMultiplier;
    private int maxVotesPerPlayer;
    private boolean refundOnLoss;

    // Prophecies
    private int prophecyCount;
    private long prophecyExecutionTimeMinutes;

    // Spectacle
    private boolean buildMonument;
    private boolean unlockDimension;
    private boolean curseEffectsEnabled;
    private boolean spectatorAllowed;

    /** The global election counter used to decide when to trigger legends. */
    private int globalElectionCount;

    public ElectionOfLegendsConfig(TownyElections plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration c = plugin.getConfig();

        enabled = c.getBoolean("election-of-legends.enabled", true);
        triggerEveryNthElection = Math.max(1, c.getInt("election-of-legends.trigger-every-nth-election", 50));
        consequencesDurationDays = Math.max(1, c.getLong("election-of-legends.consequences.duration-days", 30));
        consequencesDurationMs = consequencesDurationDays * 24L * 60L * 60L * 1000L;

        arenaWorld = c.getString("election-of-legends.boss-battle.arena-world", "world");
        arenaX = c.getInt("election-of-legends.boss-battle.arena-x", 0);
        arenaY = Math.max(1, c.getInt("election-of-legends.boss-battle.arena-y", 100));
        arenaZ = c.getInt("election-of-legends.boss-battle.arena-z", 0);
        bossHealth = Math.max(100, c.getDouble("election-of-legends.boss-battle.boss-health", 500.0));
        arenaRadius = Math.max(20, c.getInt("election-of-legends.boss-battle.arena-radius", 100));
        effectsEnabled = c.getBoolean("election-of-legends.boss-battle.effects-enabled", true);
        soundEffectsEnabled = c.getBoolean("election-of-legends.boss-battle.sound-effects-enabled", true);

        baseVoteCost = Math.max(1, c.getDouble("election-of-legends.vote-weights.base-vote-cost", 100.0));
        costMultiplier = Math.max(1.1, c.getDouble("election-of-legends.vote-weights.cost-multiplier", 2.5));
        maxVotesPerPlayer = Math.max(1, c.getInt("election-of-legends.vote-weights.max-votes-per-player", 10));
        refundOnLoss = c.getBoolean("election-of-legends.vote-weights.refund-on-loss", false);

        prophecyCount = Math.max(1, c.getInt("election-of-legends.prophecies.count", 5));
        prophecyExecutionTimeMinutes = Math.max(5, c.getLong("election-of-legends.prophecies.execution-time-minutes", 30));

        buildMonument = c.getBoolean("election-of-legends.consequences.monument-builds", true);
        unlockDimension = c.getBoolean("election-of-legends.consequences.dimension-access", true);
        curseEffectsEnabled = c.getBoolean("election-of-legends.consequences.curse-effects-enabled", true);
        spectatorAllowed = c.getBoolean("election-of-legends.spectator-allowed", true);

        globalElectionCount = c.getInt("election-of-legends.internal.global-election-count", 0);
    }

    public void saveGlobalElectionCount() {
        FileConfiguration c = plugin.getConfig();
        c.set("election-of-legends.internal.global-election-count", globalElectionCount);
        plugin.saveConfig();
    }

    // ---- Accessors ---------------------------------------------------------

    public boolean isEnabled() { return enabled; }
    public int getTriggerEveryNthElection() { return triggerEveryNthElection; }
    public long getConsequencesDurationDays() { return consequencesDurationDays; }
    public long getConsequencesDurationMs() { return consequencesDurationMs; }

    public String getArenaWorld() { return arenaWorld; }
    public int getArenaX() { return arenaX; }
    public int getArenaY() { return arenaY; }
    public int getArenaZ() { return arenaZ; }
    public double getBossHealth() { return bossHealth; }
    public int getArenaRadius() { return arenaRadius; }
    public boolean isEffectsEnabled() { return effectsEnabled; }
    public boolean isSoundEffectsEnabled() { return soundEffectsEnabled; }

    public double getBaseVoteCost() { return baseVoteCost; }
    public double getCostMultiplier() { return costMultiplier; }
    public int getMaxVotesPerPlayer() { return maxVotesPerPlayer; }
    public boolean isRefundOnLoss() { return refundOnLoss; }

    public int getProphecyCount() { return prophecyCount; }
    public long getProphecyExecutionTimeMinutes() { return prophecyExecutionTimeMinutes; }

    public boolean isBuildMonument() { return buildMonument; }
    public boolean isUnlockDimension() { return unlockDimension; }
    public boolean isCurseEffectsEnabled() { return curseEffectsEnabled; }
    public boolean isSpectatorAllowed() { return spectatorAllowed; }

    public int getGlobalElectionCount() { return globalElectionCount; }
    public int incrementAndGetGlobalElectionCount() {
        globalElectionCount++;
        saveGlobalElectionCount();
        return globalElectionCount;
    }

    /** Should the next election trigger the Election of Legends? */
    public boolean shouldTriggerLegends() {
        return enabled && (globalElectionCount + 1) % triggerEveryNthElection == 0;
    }
}
