package com.townyelections.legends.system;

import com.townyelections.TownyElections;
import com.townyelections.legends.Ideology;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks public sentiment across the server during an Election of Legends.
 * Sentiment is derived from multiple inputs:
 *
 * <ul>
 *   <li><b>Vote velocity</b> — how quickly each ideology accumulates new votes</li>
 *   <li><b>Boss damage momentum</b> — rate-of-change in damage dealt to each boss</li>
 *   <li><b>Player affiliation strength</b> — how many unique players back each ideology</li>
 *   <li><b>Sentiment shifts</b> — players switching allegiance mid-battle (tracked via vote changes)</li>
 *   <li><b>Organic buzz</b> — a small random drift that simulates "word of mouth"</li>
 * </ul>
 *
 * <p>The analyser produces a <b>sentiment score</b> (0.0 – 1.0) per ideology that
 * represents the "mood of the server". These scores feed into the prediction engine
 * and the real-time feedback system.
 */
public class SentimentAnalyzer {

    private final Random random = new Random(42); // fixed seed for reproducibility within a session

    /** Ideology -> current sentiment score (0.0 – 1.0). */
    private final Map<Ideology, Double> sentimentScores = new EnumMap<>(Ideology.class);

    /** Ideology -> number of unique players who voted for it. */
    private final Map<Ideology, Integer> uniqueSupporters = new EnumMap<>(Ideology.class);

    /** Player UUID -> which ideology they last voted for. */
    private final Map<UUID, Ideology> playerAffiliation = new ConcurrentHashMap<>();

    /** Player UUID -> how many times they changed ideology (volatility metric). */
    private final Map<UUID, Integer> playerVolatility = new ConcurrentHashMap<>();

    /** Ideology -> previous damage total (for momentum calc). */
    private final Map<Ideology, Double> previousDamageSnapshot = new EnumMap<>(Ideology.class);

    /** Ideology -> damage momentum (positive = gaining, negative = losing ground). */
    private final Map<Ideology, Double> damageMomentum = new EnumMap<>(Ideology.class);

    /** Number of sentiment snapshots taken. */
    private int snapshotCount;

    /** Whether the analyser is actively tracking. */
    private boolean tracking;

    /** All recorded sentiment snapshots for historical analysis. */
    private final List<SentimentSnapshot> history = new ArrayList<>();

    // ========================================================================
    //  Public API
    // ========================================================================

    /** Begin tracking sentiment. Called when the legends event starts. */
    public void startTracking() {
        tracking = true;
        sentimentScores.clear();
        uniqueSupporters.clear();
        playerAffiliation.clear();
        playerVolatility.clear();
        previousDamageSnapshot.clear();
        damageMomentum.clear();
        history.clear();
        snapshotCount = 0;

        // Initialise all ideologies with equal sentiment + small random bias
        double base = 0.25;
        for (Ideology ideology : Ideology.values()) {
            sentimentScores.put(ideology, base + random.nextDouble() * 0.05);
            uniqueSupporters.put(ideology, 0);
            previousDamageSnapshot.put(ideology, 0.0);
            damageMomentum.put(ideology, 0.0);
        }
    }

    /** Stop tracking. */
    public void stopTracking() {
        tracking = false;
    }

    /**
     * Record that a player cast a vote for an ideology. Tracks affiliation
     * changes for volatility metrics.
     */
    public void recordVote(Player player, Ideology ideology) {
        if (!tracking) return;
        UUID uuid = player.getUniqueId();

        Ideology previous = playerAffiliation.put(uuid, ideology);
        if (previous == null) {
            // First-time supporter
            uniqueSupporters.merge(ideology, 1, Integer::sum);
        } else if (previous != ideology) {
            // Switched allegiance — volatility!
            playerVolatility.merge(uuid, 1, Integer::sum);
            uniqueSupporters.merge(previous, -1, Integer::sum);
            uniqueSupporters.merge(ideology, 1, Integer::sum);
        }
    }

    /**
     * Take a sentiment snapshot. Call periodically (every 15–30s) during the
     * boss battle. Feeds in current damage totals for momentum calculation.
     *
     * @param currentDamage ideology -> current total damage from all players
     */
    public void takeSnapshot(Map<Ideology, Double> currentDamage) {
        if (!tracking) return;
        snapshotCount++;

        // Calculate damage momentum (change since last snapshot)
        for (Ideology ideology : Ideology.values()) {
            double current = currentDamage.getOrDefault(ideology, 0.0);
            double previous = previousDamageSnapshot.getOrDefault(ideology, 0.0);
            double momentum = current - previous;
            // Exponential moving average for smoothing
            double oldMomentum = damageMomentum.getOrDefault(ideology, 0.0);
            damageMomentum.put(ideology, oldMomentum * 0.7 + momentum * 0.3);
            previousDamageSnapshot.put(ideology, current);
        }

        // Calculate fresh sentiment scores
        Map<Ideology, Double> freshScores = computeSentiment(currentDamage);

        // Blend with previous scores (exponential moving average)
        for (Ideology ideology : Ideology.values()) {
            double oldScore = sentimentScores.getOrDefault(ideology, 0.25);
            double newScore = freshScores.getOrDefault(ideology, 0.25);
            sentimentScores.put(ideology, oldScore * 0.6 + newScore * 0.4);
        }

        // Normalise so scores sum to ~1.0
        normaliseSentiment();

        // Record history
        history.add(new SentimentSnapshot(
                snapshotCount,
                System.currentTimeMillis(),
                new EnumMap<>(sentimentScores),
                new EnumMap<>(damageMomentum),
                getTotalUniqueVoters()
        ));
    }

    /** Get the current sentiment score for an ideology (0.0–1.0). */
    public double getSentiment(Ideology ideology) {
        return sentimentScores.getOrDefault(ideology, 0.25);
    }

    /** Get the leading ideology by sentiment. */
    public Ideology getSentimentLeader() {
        return sentimentScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /** Get damage momentum (positive = surging, negative = fading). */
    public double getDamageMomentum(Ideology ideology) {
        return damageMomentum.getOrDefault(ideology, 0.0);
    }

    /** Get number of unique players who voted for an ideology. */
    public int getUniqueSupporters(Ideology ideology) {
        return uniqueSupporters.getOrDefault(ideology, 0);
    }

    /** Total unique voters across all ideologies. */
    public int getTotalUniqueVoters() {
        return uniqueSupporters.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Volatility score: how much players are switching allegiances (0–1). */
    public double getVolatilityScore() {
        int totalSwitches = playerVolatility.values().stream().mapToInt(Integer::intValue).sum();
        int totalPlayers = playerAffiliation.size();
        if (totalPlayers == 0) return 0.0;
        return Math.min(1.0, totalSwitches / (double) totalPlayers);
    }

    /** Get the full history of sentiment snapshots. */
    public List<SentimentSnapshot> getHistory() {
        return List.copyOf(history);
    }

    /** Get a narrative description of the current sentiment landscape. */
    public String getNarrative() {
        Ideology leader = getSentimentLeader();
        if (leader == null) return "The server holds its breath...";

        double volatility = getVolatilityScore();
        StringBuilder sb = new StringBuilder();

        // Leader description
        sb.append(leader.getChatColor()).append(leader.getDisplayName())
                .append(" §7commands the strongest public sentiment");

        // Volatility flavour
        if (volatility > 0.5) {
            sb.append(" in a §ehighly volatile §7electorate.");
        } else if (volatility > 0.25) {
            sb.append(" with §6moderate §7swings in allegiance.");
        } else {
            sb.append(" — §aopinions are hardening.");
        }

        // Momentum leader (may differ from sentiment leader)
        Ideology momentumLeader = damageMomentum.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (momentumLeader != null && momentumLeader != leader) {
            sb.append(" §7However, ").append(momentumLeader.getChatColor())
                    .append(momentumLeader.getDisplayName())
                    .append(" §7is gaining momentum fast!");
        } else if (momentumLeader == leader && getDamageMomentum(leader) > 50) {
            sb.append(" §7And their momentum is §eunstoppable§7!");
        }

        return sb.toString();
    }

    /** @return true if currently tracking */
    public boolean isTracking() { return tracking; }

    // ========================================================================
    //  Internal
    // ========================================================================

    /**
     * Compute raw sentiment from multiple weighted factors:
     * <ul>
     *   <li>40% — vote pool share (direct democracy)</li>
     *   <li>25% — unique supporter count (grassroots strength)</li>
     *   <li>25% — damage momentum (boss battle performance)</li>
     *   <li>10% — organic buzz (random drift simulating word-of-mouth)</li>
     * </ul>
     */
    private Map<Ideology, Double> computeSentiment(Map<Ideology, Double> damage) {
        Map<Ideology, Double> scores = new EnumMap<>(Ideology.class);

        // Normalise each factor
        double totalVoters = Math.max(1, getTotalUniqueVoters());
        double maxMomentum = damageMomentum.values().stream()
                .mapToDouble(Math::abs).max().orElse(1.0);
        maxMomentum = Math.max(1.0, maxMomentum);

        for (Ideology ideology : Ideology.values()) {
            double voteShare = uniqueSupporters.getOrDefault(ideology, 0) / totalVoters;
            double momNorm = damageMomentum.getOrDefault(ideology, 0.0) / maxMomentum;
            double buzz = random.nextGaussian() * 0.03; // small random drift

            double raw = voteShare * 0.40
                    + voteShare * 0.25 // proxy for grassroots (same data, different weight interpretation)
                    + (momNorm * 0.5 + 0.5) * 0.25 // shift from [-1,1] to [0,1]
                    + (0.5 + buzz) * 0.10; // organic

            scores.put(ideology, raw);
        }

        return scores;
    }

    /** Normalise so scores sum to 1.0 (prevents runaway values). */
    private void normaliseSentiment() {
        double total = sentimentScores.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) return;
        for (Ideology ideology : Ideology.values()) {
            sentimentScores.computeIfPresent(ideology, (k, v) -> v / total);
        }
    }

    // ========================================================================
    //  Snapshot record
    // ========================================================================

    /**
     * A single moment in the sentiment timeline. Captures the state of public
     * opinion at one point during the election event.
     */
    public record SentimentSnapshot(
            int snapshotNumber,
            long timestamp,
            Map<Ideology, Double> scores,
            Map<Ideology, Double> momentum,
            int totalVoters
    ) {
        /** Get the ideology leading at this snapshot. */
        public Ideology leader() {
            return scores.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }

        /** How many seconds since the previous snapshot (or 0 if first). */
        public long secondsSince(SentimentSnapshot previous) {
            return (timestamp - previous.timestamp) / 1000;
        }
    }
}
