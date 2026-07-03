package com.townyelections.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure instant-runoff counter for {@link VotingSystem#RANKED_CHOICE} elections.
 *
 * <p>Each round counts every ballot for its highest-ranked surviving candidate.
 * A candidate holding a strict majority of the continuing (non-exhausted)
 * ballots wins immediately; otherwise every candidate tied for the fewest votes
 * is eliminated and the next round begins. If all surviving candidates are tied
 * the count stops and the tied set is returned for the configured tie-breaker.
 */
public final class InstantRunoff {

    /** One counting round: tallies per surviving candidate, who got eliminated, and dead ballots. */
    public record Round(int number, Map<UUID, Integer> counts, List<UUID> eliminated, int exhausted) {
    }

    /**
     * Full outcome of a count.
     *
     * @param rounds      every counting round in order
     * @param finalists   the winner, or the tied set when no majority emerged
     * @param finalVotes  per candidate, the votes held in their last active round
     * @param finishOrder candidates from best to worst finish
     */
    public record Outcome(List<Round> rounds, List<UUID> finalists,
                          Map<UUID, Integer> finalVotes, List<UUID> finishOrder) {
    }

    private InstantRunoff() {
    }

    /**
     * Runs the count. Candidate iteration order is used as the stable order for
     * deterministic results, so pass candidates in registration order.
     */
    public static Outcome count(Collection<UUID> candidates, Collection<List<UUID>> ballots) {
        List<UUID> stableOrder = new ArrayList<>(candidates);
        Set<UUID> active = new LinkedHashSet<>(stableOrder);
        List<Round> rounds = new ArrayList<>();
        Map<UUID, Integer> finalVotes = new HashMap<>();
        List<List<UUID>> eliminationHistory = new ArrayList<>();
        List<UUID> finalists = new ArrayList<>(active);

        int roundNumber = 1;
        while (!active.isEmpty()) {
            Map<UUID, Integer> counts = new LinkedHashMap<>();
            for (UUID candidate : active) {
                counts.put(candidate, 0);
            }
            int exhausted = 0;
            for (List<UUID> ballot : ballots) {
                UUID preference = firstActivePreference(ballot, active);
                if (preference == null) {
                    exhausted++;
                } else {
                    counts.merge(preference, 1, Integer::sum);
                }
            }
            for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
                finalVotes.put(entry.getKey(), entry.getValue());
            }

            int continuing = ballots.size() - exhausted;
            int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int min = counts.values().stream().mapToInt(Integer::intValue).min().orElse(0);

            if (active.size() == 1) {
                rounds.add(new Round(roundNumber, counts, List.of(), exhausted));
                finalists = new ArrayList<>(active);
                break;
            }
            if (max * 2 > continuing && continuing > 0) {
                rounds.add(new Round(roundNumber, counts, List.of(), exhausted));
                finalists = new ArrayList<>();
                for (UUID candidate : active) {
                    if (counts.getOrDefault(candidate, 0) == max) {
                        finalists.add(candidate);
                        break;
                    }
                }
                break;
            }
            if (max == min) {
                // Every surviving candidate is tied; hand the set to the tie-breaker.
                rounds.add(new Round(roundNumber, counts, List.of(), exhausted));
                finalists = new ArrayList<>(active);
                break;
            }

            List<UUID> eliminated = new ArrayList<>();
            for (UUID candidate : active) {
                if (counts.getOrDefault(candidate, 0) == min) {
                    eliminated.add(candidate);
                }
            }
            rounds.add(new Round(roundNumber, counts, eliminated, exhausted));
            eliminated.forEach(active::remove);
            eliminationHistory.add(eliminated);
            finalists = new ArrayList<>(active);
            roundNumber++;
        }

        return new Outcome(rounds, finalists, finalVotes,
                buildFinishOrder(stableOrder, finalists, finalVotes, eliminationHistory));
    }

    private static UUID firstActivePreference(List<UUID> ballot, Set<UUID> active) {
        for (UUID preference : ballot) {
            if (active.contains(preference)) {
                return preference;
            }
        }
        return null;
    }

    private static List<UUID> buildFinishOrder(List<UUID> stableOrder, List<UUID> finalists,
                                               Map<UUID, Integer> finalVotes,
                                               List<List<UUID>> eliminationHistory) {
        List<UUID> order = new ArrayList<>(finalists);
        order.sort((a, b) -> Integer.compare(
                finalVotes.getOrDefault(b, 0), finalVotes.getOrDefault(a, 0)));
        // Candidates who survived every round without winning outrank the eliminated.
        Set<UUID> eliminated = new LinkedHashSet<>();
        eliminationHistory.forEach(eliminated::addAll);
        List<UUID> survivors = new ArrayList<>();
        for (UUID candidate : stableOrder) {
            if (!order.contains(candidate) && !eliminated.contains(candidate)) {
                survivors.add(candidate);
            }
        }
        survivors.sort((a, b) -> Integer.compare(
                finalVotes.getOrDefault(b, 0), finalVotes.getOrDefault(a, 0)));
        order.addAll(survivors);
        for (int i = eliminationHistory.size() - 1; i >= 0; i--) {
            List<UUID> batch = new ArrayList<>(eliminationHistory.get(i));
            batch.sort((a, b) -> Integer.compare(
                    finalVotes.getOrDefault(b, 0), finalVotes.getOrDefault(a, 0)));
            order.addAll(batch);
        }
        return order;
    }
}
