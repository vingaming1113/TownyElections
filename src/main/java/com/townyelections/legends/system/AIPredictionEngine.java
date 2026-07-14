package com.townyelections.legends.system;

import com.townyelections.TownyElections;
import com.townyelections.legends.Ideology;
import com.townyelections.legends.engine.BossBattleArena;
import com.townyelections.legends.engine.ProphecyEngine;
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
import java.util.stream.Collectors;

/**
 * An "AI-powered" prediction engine that forecasts Election of Legends outcomes
 * using multiple analytical models, historical data, and real-time sentiment.
 *
 * <p><b>Prediction Models:</b>
 * <ul>
 *   <li><b>Popular Vote Model (PVM)</b> — weighted by current vote pools (40%)</li>
 *   <li><b>Sentiment Momentum Model (SMM)</b> — weighted by public sentiment trends (25%)</li>
 *   <li><b>Boss Performance Model (BPM)</b> — weighted by remaining boss HP and damage rates (20%)</li>
 *   <li><b>Historical Oracle Model (HOM)</b> — weighted by ideology affinity with active prophecies (15%)</li>
 * </ul>
 *
 * <p>Each prediction includes a confidence score, margin of error, and a
 * narrative explanation. The engine also tracks prediction accuracy over time
 * and adjusts model weights based on past performance — a simple form of
 * online learning.
 */
public class AIPredictionEngine {

    private final Random random = new Random(1337); // fixed seed for reproducibility

    /** Model weights — adjusted by online learning. */
    private double modelWeightPVM = 0.40;
    private double modelWeightSMM = 0.25;
    private double modelWeightBPM = 0.20;
    private double modelWeightHOM = 0.15;

    /** Tracked predictions for accuracy scoring. */
    private final List<PredictionRecord> predictionHistory = new ArrayList<>();

    /** Number of predictions generated this election. */
    private int predictionCount;

    /** The last full prediction generated. */
    private ElectionPrediction lastPrediction;

    // ========================================================================
    //  Public API
    // ========================================================================

    /**
     * Generate a full election prediction with all models and narrative.
     *
     * @param voteWeights    current vote weight system (for PVM)
     * @param sentiment      current sentiment analyser (for SMM)
     * @param arena          current boss battle arena (for BPM)
     * @param prophecies     current prophecy engine (for HOM)
     * @return a complete prediction with rankings, confidence, and narrative
     */
    public ElectionPrediction predict(VoteWeightSystem voteWeights,
                                       SentimentAnalyzer sentiment,
                                       BossBattleArena arena,
                                       ProphecyEngine prophecies) {
        predictionCount++;

        Map<Ideology, Double> pvmScores = computePopularVoteModel(voteWeights);
        Map<Ideology, Double> smmScores = computeSentimentMomentumModel(sentiment);
        Map<Ideology, Double> bpmScores = computeBossPerformanceModel(arena);
        Map<Ideology, Double> homScores = computeHistoricalOracleModel(prophecies);

        // Ensemble: weighted blend
        Map<Ideology, Double> blendedScores = new EnumMap<>(Ideology.class);
        for (Ideology ideology : Ideology.values()) {
            double blended = pvmScores.getOrDefault(ideology, 0.0) * modelWeightPVM
                    + smmScores.getOrDefault(ideology, 0.0) * modelWeightSMM
                    + bpmScores.getOrDefault(ideology, 0.0) * modelWeightBPM
                    + homScores.getOrDefault(ideology, 0.0) * modelWeightHOM;
            blendedScores.put(ideology, blended);
        }

        // Normalise to probabilities
        double total = blendedScores.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total > 0) {
            for (Ideology ideology : Ideology.values()) {
                blendedScores.put(ideology, blendedScores.get(ideology) / total);
            }
        } else {
            // Equal split fallback
            double equal = 1.0 / Ideology.values().length;
            for (Ideology ideology : Ideology.values()) {
                blendedScores.put(ideology, equal);
            }
        }

        // Sort by probability descending
        List<Map.Entry<Ideology, Double>> sorted = blendedScores.entrySet().stream()
                .sorted(Map.Entry.<Ideology, Double>comparingByValue().reversed())
                .toList();

        // Build rankings
        List<IdeologyRanking> rankings = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<Ideology, Double> entry : sorted) {
            double winProb = entry.getValue();
            double confidence = computeConfidence(winProb, rank, sorted.size());
            double marginOfError = computeMarginOfError(sorted.size(), predictionCount);
            String narrative = buildRankingNarrative(entry.getKey(), rank, winProb, sentiment, arena);

            rankings.add(new IdeologyRanking(
                    entry.getKey(), rank++, winProb, confidence, marginOfError, narrative));
        }

        ElectionPrediction prediction = new ElectionPrediction(
                predictionCount,
                System.currentTimeMillis(),
                rankings,
                sentiment.getVolatilityScore(),
                computeOverallConfidence(sorted),
                generateSummaryNarrative(rankings, sentiment)
        );

        lastPrediction = prediction;
        return prediction;
    }

    /**
     * Record the actual outcome and update model weights (online learning).
     * Called after the election concludes.
     *
     * @param actualWinner the ideology that actually won
     */
    public void recordOutcome(Ideology actualWinner) {
        if (lastPrediction == null) return;

        // Find predicted winner
        Ideology predictedWinner = lastPrediction.rankings().get(0).ideology();
        boolean correct = predictedWinner == actualWinner;

        predictionHistory.add(new PredictionRecord(
                lastPrediction.predictionNumber(), predictedWinner, actualWinner,
                correct, lastPrediction.overallConfidence()));

        // Crude online learning: adjust model weights
        if (correct) {
            // Reinforce current weights slightly
            modelWeightPVM = clamp(modelWeightPVM + 0.01, 0.1, 0.6);
        } else {
            // Penalise PVM (most heavily weighted) and redistribute to others
            double penalty = 0.02;
            modelWeightPVM = clamp(modelWeightPVM - penalty, 0.1, 0.6);
            double redistribution = penalty / 3.0;
            modelWeightSMM = clamp(modelWeightSMM + redistribution, 0.05, 0.5);
            modelWeightBPM = clamp(modelWeightBPM + redistribution, 0.05, 0.5);
            modelWeightHOM = clamp(modelWeightHOM + redistribution, 0.05, 0.5);
        }

        lastPrediction = null;
    }

    /** Reset for a new election. */
    public void reset() {
        predictionCount = 0;
        lastPrediction = null;
    }

    /** Get prediction accuracy over all tracked elections. */
    public double getAccuracy() {
        if (predictionHistory.isEmpty()) return -1;
        long correct = predictionHistory.stream().filter(PredictionRecord::correct).count();
        return (double) correct / predictionHistory.size();
    }

    // ========================================================================
    //  Model computations
    // ========================================================================

    private Map<Ideology, Double> computePopularVoteModel(VoteWeightSystem weights) {
        Map<Ideology, Double> scores = new EnumMap<>(Ideology.class);
        Map<Ideology, Double> standings = weights.getStandings();
        double total = standings.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) total = 1;
        for (Ideology ideology : Ideology.values()) {
            scores.put(ideology, standings.getOrDefault(ideology, 0.0) / total);
        }
        return scores;
    }

    private Map<Ideology, Double> computeSentimentMomentumModel(SentimentAnalyzer sentiment) {
        Map<Ideology, Double> scores = new EnumMap<>(Ideology.class);
        for (Ideology ideology : Ideology.values()) {
            double sent = sentiment.getSentiment(ideology);
            double mom = sentiment.getDamageMomentum(ideology);
            // Normalise momentum to [0,1]
            double momNorm = Math.tanh(mom / 100.0) * 0.5 + 0.5;
            scores.put(ideology, sent * 0.7 + momNorm * 0.3);
        }
        return normalise(scores);
    }

    private Map<Ideology, Double> computeBossPerformanceModel(BossBattleArena arena) {
        Map<Ideology, Double> scores = new EnumMap<>(Ideology.class);
        if (!arena.isBattleActive()) {
            // No battle active — equal scores
            for (Ideology ideology : Ideology.values()) {
                scores.put(ideology, 1.0 / Ideology.values().length);
            }
            return scores;
        }

        // Score based on remaining HP (lower HP = losing = lower score)
        for (Ideology ideology : Ideology.values()) {
            var boss = arena.getBoss(ideology);
            if (boss == null || !boss.isAlive()) {
                scores.put(ideology, 0.01); // dead boss = minimal score
            } else {
                double hpPercent = boss.getHealthPercent();
                double damageDealt = boss.getTotalDamageTaken();
                // Blend HP remaining with total damage taken
                double hpScore = (1.0 - hpPercent) * 0.5; // how close to dead
                double dmgScore = Math.tanh(damageDealt / 200.0) * 0.5;
                scores.put(ideology, hpScore + dmgScore);
            }
        }
        return normalise(scores);
    }

    private Map<Ideology, Double> computeHistoricalOracleModel(ProphecyEngine prophecies) {
        Map<Ideology, Double> scores = new EnumMap<>(Ideology.class);
        List<ProphecyEngine.Prophecy> active = prophecies.getActiveProphecies();
        if (active.isEmpty()) {
            for (Ideology ideology : Ideology.values()) {
                scores.put(ideology, 1.0 / Ideology.values().length);
            }
            return scores;
        }

        // Score based on how many active prophecies align with each ideology's affinities
        for (Ideology ideology : Ideology.values()) {
            int affinityMatches = 0;
            for (ProphecyEngine.Prophecy prophecy : active) {
                if (ideology.getProphecyAffinities().contains(prophecy.id())) {
                    affinityMatches++;
                }
            }
            scores.put(ideology, 1.0 + affinityMatches * 0.5);
        }
        return normalise(scores);
    }

    // ========================================================================
    //  Confidence & narrative
    // ========================================================================

    private double computeConfidence(double winProb, int rank, int totalCandidates) {
        if (rank == 1) {
            // Higher confidence when the gap to #2 is large
            return Math.min(0.99, winProb * 0.7 + 0.2);
        }
        return Math.max(0.05, winProb);
    }

    private double computeMarginOfError(int candidateCount, int dataPoints) {
        // Simulates statistical margin of error — shrinks with more data
        double base = 0.15 / Math.sqrt(candidateCount);
        double experienceBonus = 1.0 / Math.sqrt(Math.max(1, dataPoints));
        return Math.min(0.30, base + experienceBonus * 0.10);
    }

    private double computeOverallConfidence(List<Map.Entry<Ideology, Double>> sorted) {
        if (sorted.isEmpty()) return 0.5;
        double first = sorted.get(0).getValue();
        double second = sorted.size() > 1 ? sorted.get(1).getValue() : 0;
        double gap = first - second;
        return Math.min(0.98, 0.5 + gap * 0.8);
    }

    private String buildRankingNarrative(Ideology ideology, int rank, double winProb,
                                          SentimentAnalyzer sentiment, BossBattleArena arena) {
        double momentum = sentiment.getDamageMomentum(ideology);
        int supporters = sentiment.getUniqueSupporters(ideology);

        return switch (rank) {
            case 1 -> {
                String base = "§6§lFAVORITE §7— commanding " + formatPercent(winProb)
                        + " win probability with " + supporters + " active supporters.";
                if (momentum > 30) yield base + " §a▲ Momentum is surging.";
                if (momentum < -30) yield base + " §c▼ Momentum is fading — rivals closing in.";
                yield base + " §e● Holding steady.";
            }
            case 2 -> {
                String base = "§e§lCONTENDER §7— " + formatPercent(winProb)
                        + " chance. " + supporters + " supporters.";
                if (momentum > 30) yield base + " §a▲ Gaining fast — could overtake the leader!";
                yield base + " §7Needs a push to close the gap.";
            }
            case 3 -> "§7§lUNDERDOG §7— " + formatPercent(winProb)
                    + " probability. §8A dark horse that could surprise.";
            default -> "§8§lLONGSHOT §7— " + formatPercent(winProb)
                    + " — §8would need a miracle.";
        };
    }

    private String generateSummaryNarrative(List<IdeologyRanking> rankings,
                                             SentimentAnalyzer sentiment) {
        if (rankings.isEmpty()) return "Insufficient data for prediction.";

        IdeologyRanking leader = rankings.get(0);
        IdeologyRanking runnerUp = rankings.size() > 1 ? rankings.get(1) : null;
        double volatility = sentiment.getVolatilityScore();

        StringBuilder sb = new StringBuilder();
        sb.append("§6§l◈ AI PREDICTION SUMMARY ◈\n\n");

        sb.append("§7Our ensemble of 4 predictive models forecasts that ");
        sb.append(leader.ideology().getChatColor()).append("§l")
                .append(leader.ideology().getDisplayName())
                .append(" §7will win with §e")
                .append(formatPercent(leader.winProbability()))
                .append(" §7probability");

        if (leader.confidence() > 0.7) {
            sb.append(" (§ahigh confidence§7)");
        } else if (leader.confidence() > 0.5) {
            sb.append(" (§6moderate confidence§7)");
        } else {
            sb.append(" (§clow confidence§7 — race is too close to call)");
        }

        sb.append(".\n\n");

        if (runnerUp != null) {
            double gap = leader.winProbability() - runnerUp.winProbability();
            if (gap < 0.10) {
                sb.append("§c⚠ RACE IS WITHIN MARGIN OF ERROR! §7")
                        .append(runnerUp.ideology().getChatColor())
                        .append(runnerUp.ideology().getDisplayName())
                        .append(" §7is only §e").append(formatPercent(gap))
                        .append(" §7behind.\n");
            }
        }

        if (volatility > 0.4) {
            sb.append("\n§6⚡ High voter volatility detected — ")
                    .append("predictions may shift dramatically.\n");
        }

        sb.append("\n§8§oMargin of error: ±").append(formatPercent(rankings.get(0).marginOfError()))
                .append(" | Model: PVM ").append(formatPercent(modelWeightPVM))
                .append(" / SMM ").append(formatPercent(modelWeightSMM))
                .append(" / BPM ").append(formatPercent(modelWeightBPM))
                .append(" / HOM ").append(formatPercent(modelWeightHOM));

        return sb.toString();
    }

    /** Send a full prediction report to a player. */
    public void sendPredictionReport(Player player, ElectionPrediction prediction) {
        player.sendMessage("");
        player.sendMessage("§5§l▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀");
        player.sendMessage("§5§l  AI ELECTION PREDICTION #" + prediction.predictionNumber());
        player.sendMessage("§5§l▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄");
        player.sendMessage("");

        // Rankings table
        player.sendMessage("§7┌─────┬────────────────┬──────────┬──────────┐");
        player.sendMessage("§7│ §f#  §7│ §fIdeology       §7│ §fWin Prob §7│ §fMomentum §7│");
        player.sendMessage("§7├─────┼────────────────┼──────────┼──────────┤");
        for (IdeologyRanking ranking : prediction.rankings()) {
            String rankIcon = switch (ranking.rank()) {
                case 1 -> "§6🥇";
                case 2 -> "§7🥈";
                case 3 -> "§8🥉";
                default -> "§8 " + ranking.rank();
            };
            double mom = prediction.snapshotMomentum() != null
                    ? prediction.snapshotMomentum().getOrDefault(ranking.ideology(), 0.0) : 0;
            String momIcon = mom > 20 ? "§a▲" : mom < -20 ? "§c▼" : "§7●";
            player.sendMessage(String.format("§7│ %s §7│ %s%-14s §7│ §e%6s §7│ %s %7.0f §7│",
                    rankIcon,
                    ranking.ideology().getChatColor(),
                    ranking.ideology().getDisplayName(),
                    formatPercent(ranking.winProbability()),
                    momIcon, mom));
        }
        player.sendMessage("§7└─────┴────────────────┴──────────┴──────────┘");
        player.sendMessage("");

        // Narrative
        for (String line : prediction.summaryNarrative().split("\n")) {
            player.sendMessage(line);
        }

        player.sendMessage("");
        player.sendMessage("§7Volatility: " + formatPercent(prediction.volatilityScore())
                + " | Confidence: " + formatPercent(prediction.overallConfidence()));

        // Historical accuracy if available
        double accuracy = getAccuracy();
        if (accuracy >= 0) {
            player.sendMessage("§7Historical prediction accuracy: §e"
                    + formatPercent(accuracy));
        }

        player.sendMessage("§8§oPredictions reflect current data. The future is fluid.");
    }

    // ========================================================================
    //  Utilities
    // ========================================================================

    private Map<Ideology, Double> normalise(Map<Ideology, Double> scores) {
        double total = scores.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) total = 1;
        Map<Ideology, Double> normalised = new EnumMap<>(Ideology.class);
        for (Map.Entry<Ideology, Double> entry : scores.entrySet()) {
            normalised.put(entry.getKey(), entry.getValue() / total);
        }
        return normalised;
    }

    private String formatPercent(double value) {
        return String.format("%.1f%%", value * 100);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Get the last prediction (may be null). */
    public ElectionPrediction getLastPrediction() {
        return lastPrediction;
    }

    /** Get number of predictions made this election. */
    public int getPredictionCount() {
        return predictionCount;
    }

    // ========================================================================
    //  Records
    // ========================================================================

    /** A single ideology ranking within a prediction. */
    public record IdeologyRanking(
            Ideology ideology,
            int rank,
            double winProbability,
            double confidence,
            double marginOfError,
            String narrative
    ) {}

    /** A complete election prediction. */
    public record ElectionPrediction(
            int predictionNumber,
            long timestamp,
            List<IdeologyRanking> rankings,
            double volatilityScore,
            double overallConfidence,
            String summaryNarrative
    ) {
        /** Get predicted winner (rank 1). */
        public Ideology predictedWinner() {
            return rankings.isEmpty() ? null : rankings.get(0).ideology();
        }

        /** Get the momentum map from rankings (derived). */
        public Map<Ideology, Double> snapshotMomentum() {
            // Momentum not directly stored; return empty
            return Map.of();
        }
    }

    /** A record of a past prediction vs actual outcome. */
    private record PredictionRecord(
            int predictionNumber,
            Ideology predicted,
            Ideology actual,
            boolean correct,
            double confidence
    ) {}
}
