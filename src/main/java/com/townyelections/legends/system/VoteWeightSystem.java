package com.townyelections.legends.system;

import com.townyelections.legends.Ideology;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Economy-based vote weighting for the Election of Legends. Players purchase
 * additional votes with escalating costs. Each ideology tracks its own pool of
 * weighted votes. The total weighted votes drive the boss battle damage-to-vote
 * conversion and the final tally.
 *
 * <p>Cost formula: {@code baseCost * multiplier^(voteNumber - 1)}
 * Example: 100, 250, 625, 1562, 3906...
 */
public class VoteWeightSystem {

    /** player UUID -> number of votes purchased */
    private final Map<UUID, Integer> voteCounts = new HashMap<>();
    /** player UUID -> total gold spent */
    private final Map<UUID, Long> goldSpent = new HashMap<>();
    /** ideology -> total weighted vote pool */
    private final Map<Ideology, Double> ideologyVotePool = new HashMap<>();

    private final double baseVoteCost;
    private final double costMultiplier;
    private final int maxVotesPerPlayer;

    public VoteWeightSystem(double baseVoteCost, double costMultiplier, int maxVotesPerPlayer) {
        this.baseVoteCost = baseVoteCost;
        this.costMultiplier = costMultiplier;
        this.maxVotesPerPlayer = maxVotesPerPlayer;
    }

    // ========================================================================
    //  Vote purchasing
    // ========================================================================

    /**
     * Calculate the cost for a player's next vote purchase (1-indexed).
     * Vote #1 is free (base vote). Vote #2+ costs gold.
     *
     * @param player the purchasing player
     * @return the gold cost for their next vote, or -1 if at max
     */
    public long calculateNextVoteCost(Player player) {
        int nextVote = voteCounts.getOrDefault(player.getUniqueId(), 0) + 1;
        if (nextVote > maxVotesPerPlayer) {
            return -1;
        }
        if (nextVote <= 1) {
            return 0; // first vote is free
        }
        return calculateEscalatingCost(nextVote);
    }

    private long calculateEscalatingCost(int voteNumber) {
        return (long) (baseVoteCost * Math.pow(costMultiplier, voteNumber - 2));
    }

    /**
     * Record that a player has purchased a vote. The caller must handle
     * economy transactions; this only tracks the logical vote count.
     *
     * @param player the purchasing player
     * @return the new total vote count for this player
     */
    public int purchaseVote(Player player) {
        UUID uuid = player.getUniqueId();
        int current = voteCounts.getOrDefault(uuid, 0);
        int next = current + 1;
        if (next > maxVotesPerPlayer) {
            return current;
        }
        long cost = next <= 1 ? 0 : calculateEscalatingCost(next);
        voteCounts.put(uuid, next);
        goldSpent.merge(uuid, cost, Long::sum);
        return next;
    }

    // ========================================================================
    //  Weighted vote pool
    // ========================================================================

    /**
     * Add weighted votes to an ideology's pool. Called during boss battles when
     * damage is converted to votes.
     *
     * @param ideology the ideology
     * @param weight   the number of weighted votes contributed
     */
    public void addToVotePool(Ideology ideology, double weight) {
        ideologyVotePool.merge(ideology, weight, Double::sum);
    }

    /** Get the total weighted votes for an ideology. */
    public double getVotePool(Ideology ideology) {
        return ideologyVotePool.getOrDefault(ideology, 0.0);
    }

    /** Get the sorted standings: ideology with most weighted votes first. */
    public Map<Ideology, Double> getStandings() {
        Map<Ideology, Double> sorted = new HashMap<>(ideologyVotePool);
        // initialise all ideologies to 0
        for (Ideology ideology : Ideology.values()) {
            sorted.putIfAbsent(ideology, 0.0);
        }
        return sorted;
    }

    /** Get the leading ideology by weighted votes. */
    public Ideology getLeadingIdeology() {
        return ideologyVotePool.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    // ========================================================================
    //  Reset
    // ========================================================================

    /** Reset all vote pools and player purchases for a new election. */
    public void reset() {
        voteCounts.clear();
        goldSpent.clear();
        ideologyVotePool.clear();
    }

    // ========================================================================
    //  Accessors
    // ========================================================================

    public int getVoteCount(Player player) {
        return voteCounts.getOrDefault(player.getUniqueId(), 0);
    }

    public long getGoldSpent(Player player) {
        return goldSpent.getOrDefault(player.getUniqueId(), 0L);
    }

    public int getMaxVotesPerPlayer() {
        return maxVotesPerPlayer;
    }

    public double getBaseVoteCost() {
        return baseVoteCost;
    }

    public double getCostMultiplier() {
        return costMultiplier;
    }
}
