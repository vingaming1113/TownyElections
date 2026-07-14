package com.townyelections.legends.engine;

import com.townyelections.TownyElections;
import com.townyelections.legends.Ideology;
import com.townyelections.legends.boss.BuilderBoss;
import com.townyelections.legends.boss.IdeologyBoss;
import com.townyelections.legends.boss.MerchantBoss;
import com.townyelections.legends.boss.MysticBoss;
import com.townyelections.legends.boss.WarmongerBoss;
import com.townyelections.legends.system.ElectionFeedbackSystem;
import com.townyelections.legends.system.SentimentAnalyzer;
import com.townyelections.legends.system.VoteWeightSystem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the grand boss-battle arena at the centre of the Election of Legends.
 * Spawns all four ideology bosses simultaneously and tracks real-time damage
 * dealt by players to each boss. Damage is converted to weighted votes for the
 * final tally.
 *
 * <p>The arena supports 50-500 concurrent players, maintains clear boundaries,
 * adds terrain mutations based on boss HP, and provides live scoreboard updates.
 */
public class BossBattleArena {

    private final TownyElections plugin;
    private final VoteWeightSystem voteWeights;

    /** Optional AI feedback system — set after construction. */
    private ElectionFeedbackSystem feedbackSystem;
    /** Optional sentiment analyser — set after construction. */
    private SentimentAnalyzer sentimentAnalyzer;

    private Location arenaCenter;
    private int arenaRadius;

    /** Bosses keyed by ideology. */
    private final Map<Ideology, IdeologyBoss> bosses = new EnumMap<>(Ideology.class);

    /** Damage tracking: player UUID -> ideology -> total damage. */
    private final Map<UUID, Map<Ideology, Double>> damageTracker = new java.util.HashMap<>();

    private BukkitTask tickTask;
    private BukkitTask scoreboardTask;
    private boolean battleActive;
    private Ideology winningIdeology;

    public BossBattleArena(TownyElections plugin, VoteWeightSystem voteWeights) {
        this.plugin = plugin;
        this.voteWeights = voteWeights;
    }

    /** Set the feedback system for AI-powered battle commentary. */
    public void setFeedbackSystem(ElectionFeedbackSystem feedbackSystem) {
        this.feedbackSystem = feedbackSystem;
    }

    /** Set the sentiment analyser for player sentiment tracking. */
    public void setSentimentAnalyzer(SentimentAnalyzer sentimentAnalyzer) {
        this.sentimentAnalyzer = sentimentAnalyzer;
    }

    // ========================================================================
    //  Public API
    // ========================================================================

    /**
     * Initialise the arena and prepare for battle.
     *
     * @param center the arena centre
     * @param radius the arena radius in blocks
     */
    public void prepareArena(Location center, int radius) {
        this.arenaCenter = center.clone();
        this.arenaRadius = radius;
    }

    /**
     * Begin the boss battle. Spawns all four bosses, enables damage tracking,
     * and starts the tick loop.
     */
    public void startBattle() {
        if (arenaCenter == null) {
            plugin.getLogger().severe("Cannot start boss battle: arena not prepared.");
            return;
        }

        damageTracker.clear();
        battleActive = true;
        winningIdeology = null;

        // Spawn bosses at cardinal points around the arena
        int spawnRadius = arenaRadius / 3;
        bosses.put(Ideology.WARMONGER, spawnBoss(new WarmongerBoss(),
                arenaCenter.clone().add(spawnRadius, 0, 0), EntityType.WITHER_SKELETON));
        bosses.put(Ideology.BUILDER, spawnBoss(new BuilderBoss(),
                arenaCenter.clone().add(-spawnRadius, 0, 0), EntityType.IRON_GOLEM));
        bosses.put(Ideology.MERCHANT, spawnBoss(new MerchantBoss(),
                arenaCenter.clone().add(0, 0, spawnRadius), EntityType.EVOKER));
        bosses.put(Ideology.MYSTIC, spawnBoss(new MysticBoss(),
                arenaCenter.clone().add(0, 0, -spawnRadius), EntityType.WITCH));

        // Main tick: boss AI, mutations
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickBosses, 5L, 5L);

        // Scoreboard: update action bars every second
        scoreboardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateScoreboard, 20L, 20L);

        Bukkit.broadcastMessage("§6§l⚔ THE BOSS BATTLE BEGINS! ⚔");
        Bukkit.broadcastMessage("§7All four ideology champions have spawned. Fight for your faction!");
        arenaCenter.getWorld().playSound(arenaCenter, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
    }

    /**
     * End the battle and determine the winner based on damage dealt.
     *
     * @return the winning ideology
     */
    public Ideology endBattle() {
        battleActive = false;
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (scoreboardTask != null) {
            scoreboardTask.cancel();
            scoreboardTask = null;
        }

        // Remove all boss entities
        for (IdeologyBoss boss : bosses.values()) {
            boss.remove();
        }
        bosses.clear();

        // Determine winner from vote pools
        winningIdeology = voteWeights.getLeadingIdeology();
        if (winningIdeology == null) {
            // Fallback: ideology with most total damage
            Map<Ideology, Double> totalDamage = new EnumMap<>(Ideology.class);
            for (Ideology ideology : Ideology.values()) {
                totalDamage.put(ideology, 0.0);
            }
            for (Map<Ideology, Double> playerDamage : damageTracker.values()) {
                for (Map.Entry<Ideology, Double> entry : playerDamage.entrySet()) {
                    totalDamage.merge(entry.getKey(), entry.getValue(), Double::sum);
                }
            }
            winningIdeology = totalDamage.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(Ideology.WARMONGER);
        }

        Bukkit.broadcastMessage("§6§l⚔ THE BATTLE IS OVER! ⚔");
        Bukkit.broadcastMessage("§6§l" + winningIdeology.getDisplayName() + " reigns supreme!");

        // Massive victory effects
        arenaCenter.getWorld().strikeLightningEffect(arenaCenter);
        arenaCenter.getWorld().playSound(arenaCenter, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
        arenaCenter.getWorld().spawnParticle(winningIdeology.getParticle(), arenaCenter, 500, 20, 20, 20, 0.1);

        return winningIdeology;
    }

    /** Record damage dealt by a player to an ideology boss. */
    public void recordDamage(Player player, Ideology ideology, double damage) {
        if (!battleActive) return;

        damageTracker.computeIfAbsent(player.getUniqueId(), k -> new EnumMap<>(Ideology.class))
                .merge(ideology, damage, Double::sum);

        // Convert damage to weighted votes
        voteWeights.addToVotePool(ideology, damage);
        // Player's own votes multiply their damage contribution
        int playerVotes = voteWeights.getVoteCount(player);
        if (playerVotes > 1) {
            double extraWeight = damage * (playerVotes - 1) * 0.1;
            voteWeights.addToVotePool(ideology, extraWeight);
        }
    }

    // ========================================================================
    //  Internal
    // ========================================================================

    private IdeologyBoss spawnBoss(IdeologyBoss boss, Location location, EntityType type) {
        boss.spawn(location, type);
        return boss;
    }

    private void tickBosses() {
        if (!battleActive) return;

        Collection<Player> nearbyPlayers = getNearbyPlayers();

        for (IdeologyBoss boss : bosses.values()) {
            if (boss.isAlive()) {
                boss.tick(nearbyPlayers);
                boss.checkMutations(arenaCenter);

                // Ambient particles at arena level
                if (arenaCenter.getWorld().getFullTime() % 10 == 0) {
                    Location bossLoc = boss.getEntity().getLocation();
                    arenaCenter.getWorld().spawnParticle(boss.getParticleEffect(), bossLoc, 3, 0.3, 0.3, 0.3, 0);
                }
            }
        }

        // ---- AI-powered: sentiment snapshots & feedback every 5s ----
        if (sentimentAnalyzer != null && arenaCenter.getWorld().getFullTime() % 100 == 0) {
            Map<Ideology, Double> currentDamage = new EnumMap<>(Ideology.class);
            for (Ideology ideology : Ideology.values()) {
                IdeologyBoss boss = bosses.get(ideology);
                currentDamage.put(ideology, boss != null ? boss.getTotalDamageTaken() : 0.0);
            }
            sentimentAnalyzer.takeSnapshot(currentDamage);
        }

        if (feedbackSystem != null && arenaCenter.getWorld().getFullTime() % 60 == 0) {
            feedbackSystem.tick(this, voteWeights, sentimentAnalyzer);
        }

        // Check if all bosses are dead
        if (bosses.values().stream().noneMatch(IdeologyBoss::isAlive)) {
            endBattle();
        }
    }

    private Collection<Player> getNearbyPlayers() {
        List<Player> nearby = new ArrayList<>();
        for (Player player : arenaCenter.getWorld().getPlayers()) {
            if (player.getLocation().distance(arenaCenter) <= arenaRadius) {
                nearby.add(player);
            }
        }
        return nearby;
    }

    private void updateScoreboard() {
        if (!battleActive) return;

        Map<Ideology, Double> standings = voteWeights.getStandings();
        StringBuilder bar = new StringBuilder();
        for (Ideology ideology : Ideology.values()) {
            double votes = standings.getOrDefault(ideology, 0.0);
            String icon = switch (ideology) {
                case WARMONGER -> "§c⚔";
                case BUILDER -> "§b⛏";
                case MERCHANT -> "§e💰";
                case MYSTIC -> "§d🔮";
            };
            bar.append(icon).append(String.format(" %.0f  ", votes));
        }

        String actionBar = "§6§lELECTION OF LEGENDS §8| " + bar.toString().trim();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(actionBar);
        }
    }

    // ========================================================================
    //  Accessors
    // ========================================================================

    public boolean isBattleActive() { return battleActive; }
    public Ideology getWinningIdeology() { return winningIdeology; }
    public Location getArenaCenter() { return arenaCenter; }
    public int getArenaRadius() { return arenaRadius; }
    public IdeologyBoss getBoss(Ideology ideology) { return bosses.get(ideology); }
    public boolean isInArena(Player player) {
        return arenaCenter != null && player.getLocation().distance(arenaCenter) <= arenaRadius;
    }
}
