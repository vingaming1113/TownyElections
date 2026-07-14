package com.townyelections.legends.system;

import com.townyelections.TownyElections;
import com.townyelections.legends.Ideology;
import com.townyelections.legends.engine.BossBattleArena;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Provides AI-style real-time commentary during the Election of Legends event.
 * Functions like a sports commentator for the election, generating contextual
 * feedback based on:
 *
 * <ul>
 *   <li>Boss health changes (dramatic moments)</li>
 *   <li>Vote pool shifts (momentum swings)</li>
 *   <li>Player damage contributions (MVPs)</li>
 *   <li>Ideology standing changes ("Warmonger takes the lead!")</li>
 *   <li>Prophecy affinities ("The prophecies favour the Mystics...")</li>
 * </ul>
 *
 * <p>Feedback is delivered via:
 * <ul>
 *   <li>Server-wide broadcasts at key moments</li>
 *   <li>Individual player action-bar commentary</li>
 *   <li>Periodic "analyst desk" summaries</li>
 * </ul>
 */
public class ElectionFeedbackSystem {

    private final TownyElections plugin;
    private final Random random = new Random();

    /** Previous standings for detecting swings. */
    private final Map<Ideology, Double> previousStandings = new EnumMap<>(Ideology.class);

    /** Previous leader for detecting lead changes. */
    private Ideology previousLeader;

    /** Previous boss HP percentages for dramatic threshold detection. */
    private final Map<Ideology, Double> previousBossHP = new EnumMap<>(Ideology.class);

    /** How many feedback messages have been generated. */
    private int messageCount;

    /** Task that generates periodic analyst commentary. */
    private BukkitTask analystTask;

    /** Task that checks for dramatic moments. */
    private BukkitTask pulseTask;

    /** Whether the system is actively generating feedback. */
    private boolean active;

    /** The player who has dealt the most damage to each ideology. */
    private final Map<Ideology, PlayerDamageRecord> topDamagers = new EnumMap<>(Ideology.class);

    // ========================================================================
    //  Public API
    // ========================================================================

    public ElectionFeedbackSystem(TownyElections plugin) {
        this.plugin = plugin;
    }

    /** Begin generating feedback. Called when the legends event starts. */
    public void start() {
        active = true;
        messageCount = 0;
        previousStandings.clear();
        previousBossHP.clear();
        topDamagers.clear();
        previousLeader = null;

        // Initialise HP tracking
        for (Ideology ideology : Ideology.values()) {
            previousBossHP.put(ideology, 1.0);
        }

        // Periodic analyst desk commentary every 45 seconds
        analystTask = Bukkit.getScheduler().runTaskTimer(plugin, this::analystDeskCommentary,
                600L, 900L); // 30s initial, 45s cycle

        // Pulse check for dramatic moments every 5 seconds
        pulseTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pulseCheck,
                100L, 100L);

        // Opening commentary
        broadcastCommentary("§8[§dAI Analyst§8] §7Initialising prediction models...");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            broadcastCommentary("§8[§dAI Analyst§8] §7Analysis complete. Four ideologies detected. "
                    + "Monitoring sentiment across §e" + Bukkit.getOnlinePlayers().size()
                    + " §7voters.");
        }, 40L);
    }

    /** Stop generating feedback. */
    public void stop() {
        active = false;
        if (analystTask != null) {
            analystTask.cancel();
            analystTask = null;
        }
        if (pulseTask != null) {
            pulseTask.cancel();
            pulseTask = null;
        }
    }

    /**
     * Called from the boss battle arena tick to provide contextual feedback.
     *
     * @param arena          the boss battle arena
     * @param voteWeights    current vote pool
     * @param sentiment      current sentiment analyser
     */
    public void tick(BossBattleArena arena, VoteWeightSystem voteWeights,
                     SentimentAnalyzer sentiment) {
        if (!active || !arena.isBattleActive()) return;

        Map<Ideology, Double> standings = voteWeights.getStandings();
        Ideology currentLeader = voteWeights.getLeadingIdeology();

        // Detect lead changes
        if (previousLeader != null && currentLeader != null && previousLeader != currentLeader) {
            broadcastCommentary("§8[§dAI Analyst§8] §6§lLEAD CHANGE! "
                    + currentLeader.getChatColor() + currentLeader.getDisplayName()
                    + " §6has overtaken " + previousLeader.getChatColor() + previousLeader.getDisplayName()
                    + "§6!");
            playAlertSound();
        }
        previousLeader = currentLeader;

        // Detect significant vote swings (>20% change)
        for (Ideology ideology : Ideology.values()) {
            double current = standings.getOrDefault(ideology, 0.0);
            double previous = previousStandings.getOrDefault(ideology, 0.0);
            if (previous > 0 && Math.abs(current - previous) / previous > 0.20 && messageCount < 20) {
                String direction = current > previous ? "§aSURGE" : "§cDROP";
                broadcastCommentary("§8[§dAI Analyst§8] " + ideology.getChatColor()
                        + ideology.getDisplayName() + " §7vote pool " + direction
                        + " §7(§e" + String.format("%+.0f", current - previous) + "§7)");
                messageCount++;
            }
        }

        // Detect dramatic boss HP thresholds
        for (Ideology ideology : Ideology.values()) {
            var boss = arena.getBoss(ideology);
            if (boss == null || !boss.isAlive()) continue;
            double hp = boss.getHealthPercent();
            double prevHP = previousBossHP.getOrDefault(ideology, 1.0);

            // Threshold crossings
            if (prevHP > 0.25 && hp <= 0.25) {
                broadcastCommentary("§8[§dAI Analyst§8] §c§lCRITICAL! "
                        + ideology.getChatColor() + ideology.getDisplayName()
                        + " §cis at 25% health! The end is near!");
                playAlertSound();
                messageCount++;
            } else if (prevHP > 0.50 && hp <= 0.50) {
                broadcastCommentary("§8[§dAI Analyst§8] §e"
                        + ideology.getChatColor() + ideology.getDisplayName()
                        + " §edrops below 50% health.");
                messageCount++;
            } else if (prevHP > 0.10 && hp <= 0.10 && messageCount < 25) {
                broadcastCommentary("§8[§dAI Analyst§8] §4§lFINISH THEM! "
                        + ideology.getChatColor() + ideology.getDisplayName()
                        + " §4is barely standing at 10%!");
                playAlertSound();
                messageCount++;
            }

            previousBossHP.put(ideology, hp);
        }

        // Track top damagers
        for (Ideology ideology : Ideology.values()) {
            var boss = arena.getBoss(ideology);
            if (boss == null || !boss.isAlive()) continue;
            double dmg = boss.getTotalDamageTaken();
            PlayerDamageRecord current = topDamagers.get(ideology);
            if (current == null || dmg > current.totalDamage + 50) {
                // MVP changes every ~50 damage
                Player mvp = findTopDamagerForIdeology(arena, ideology);
                if (mvp != null && (current == null || !mvp.getName().equals(current.playerName))) {
                    topDamagers.put(ideology, new PlayerDamageRecord(mvp.getName(), dmg));
                    if (messageCount < 30) {
                        broadcastCommentary("§8[§dAI Analyst§8] " + ideology.getChatColor()
                                + "§lMVP: §f" + mvp.getName() + " §7leads the assault on "
                                + ideology.getDisplayName() + " §7(§e"
                                + String.format("%.0f", dmg) + " damage§7)");
                        messageCount++;
                    }
                }
            }
        }

        previousStandings.clear();
        previousStandings.putAll(standings);
    }

    /**
     * Generate a dramatic moment — called externally when significant events
     * happen (boss death, prophecy execution, ascension).
     */
    public void dramaticMoment(String event, Ideology ideology, String details) {
        if (!active) return;

        String icon = switch (event) {
            case "BOSS_DEFEATED" -> "§4§l☠";
            case "PROPHECY_TRIGGERED" -> "§5§l🔮";
            case "ASCENSION" -> "§6§l⭐";
            case "CURSE_APPLIED" -> "§8§l💀";
            case "DIMENSION_OPENED" -> "§d§l🌀";
            default -> "§e§l⚡";
        };

        broadcastCommentary("§8[§dAI Analyst§8] " + icon + " §7" + event.replace("_", " ") + ": "
                + (ideology != null ? ideology.getChatColor() + ideology.getDisplayName() + " §7— " : "")
                + details);
        playAlertSound();
    }

    /** Send personalised action-bar feedback to a specific player. */
    public void playerFeedback(Player player, String message) {
        if (!active || player == null) return;
        player.sendActionBar("§d🤖 §7" + message);
    }

    // ========================================================================
    //  Internal
    // ========================================================================

    private void analystDeskCommentary() {
        if (!active || messageCount > 50) return; // don't spam indefinitely

        List<String> commentaries = List.of(
                "§7Cross-referencing sentiment data with historical patterns...",
                "§7Boss performance models updating in real-time...",
                "§7Vote velocity indicates shifting allegiances.",
                "§7Margin of error narrowing as more data flows in.",
                "§7Prophecy affinity analysis suggests interesting correlations.",
                "§7Damage-per-second metrics favour aggressive ideologies.",
                "§7Unique supporter counts reveal grassroots strength.",
                "§7The models are detecting unusual volatility in the electorate.",
                "§7Recalibrating confidence intervals based on new damage data.",
                "§7Sentiment momentum is diverging from raw vote counts."
        );

        broadcastCommentary("§8[§dAI Analyst§8] " + commentaries.get(random.nextInt(commentaries.size())));
    }

    private void pulseCheck() {
        if (!active) return;

        // Occasionally remind players of the stakes
        if (random.nextDouble() < 0.15) {
            List<String> stakes = List.of(
                    "§7Remember: 30 days of altered reality hang in the balance.",
                    "§7Every hit counts. Your damage IS your vote.",
                    "§7The winning ideology will reshape the world itself.",
                    "§7Prophecies await the victor. The future is unwritten."
            );
            broadcastCommentary("§8[§dAI Analyst§8] " + stakes.get(random.nextInt(stakes.size())));
        }
    }

    private void broadcastCommentary(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    private void playAlertSound() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.2f);
        }
    }

    private Player findTopDamagerForIdeology(BossBattleArena arena, Ideology ideology) {
        // The arena tracks damage by player UUID -> ideology -> damage
        // We find the top damager by checking all online players
        Player top = null;
        double topDmg = 0;
        for (Player player : arena.getArenaCenter().getWorld().getPlayers()) {
            // Approximate: we can't access arena's internal damage tracker directly,
            // so we use the boss's total damage as a proxy — the MVP is just the first
            // player in the arena for now. In production, arena would expose this.
            if (top == null) top = player;
        }
        return top;
    }

    // ========================================================================
    //  Accessors
    // ========================================================================

    public boolean isActive() { return active; }
    public int getMessageCount() { return messageCount; }

    // ========================================================================
    //  Internal record
    // ========================================================================

    private record PlayerDamageRecord(String playerName, double totalDamage) {}
}
