package com.townyelections.legends.system;

import com.townyelections.legends.Ideology;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simulates the future consequences of an ideology winning the election.
 * Generates a formatted report predicting crop growth, mob spawns, world
 * events, and resource availability for the next N days under a given
 * ideology's rule.
 *
 * <p>This is purely informational / role-play: it does not actually change
 * the world. It costs gold to run and helps undecided voters make a choice.
 */
public class TemporalSimulator {

    private final Random random = new Random();

    /** The gold cost to run a simulation. */
    public static final long SIMULATION_COST = 1000;

    // ========================================================================
    //  Public API
    // ========================================================================

    /**
     * Run a simulation for the given ideology and return a report.
     *
     * @param ideology the ideology to simulate
     * @param days     number of days to project forward
     * @return a simulation result with predictions
     */
    public SimulationResult simulate(Ideology ideology, int days) {
        List<Prediction> predictions = new ArrayList<>();

        // Crop growth prediction
        String cropRate = switch (ideology) {
            case WARMONGER -> random.nextBoolean() ? "Slowed by 50%" : "Normal (war effort diverted farmers)";
            case BUILDER -> random.nextBoolean() ? "Accelerated by 50%" : "Doubled (enhanced soil)";
            case MERCHANT -> "Tripled (commercial fertilizers)";
            case MYSTIC -> random.nextBoolean() ? "Unstable (magical interference)" : "Normal (arcane wards)";
        };
        predictions.add(new Prediction("Crop Growth Rate", cropRate, ideology));

        // Mob spawn prediction
        String mobRate = switch (ideology) {
            case WARMONGER -> "Increased 300% — constant invasions";
            case BUILDER -> random.nextBoolean() ? "Decreased 50%" : "Normal";
            case MERCHANT -> "Decreased 75% — PvP disabled, mobs pacified";
            case MYSTIC -> "Magical mobs spawn 5x more frequently";
        };
        predictions.add(new Prediction("Mob Activity", mobRate, ideology));

        // Weather prediction
        String weather = switch (ideology) {
            case WARMONGER -> "Permanent thunderstorms, fire rain likely";
            case BUILDER -> random.nextBoolean() ? "Clear skies" : "Mild, predictable weather";
            case MERCHANT -> "Permanent clear weather — business-friendly";
            case MYSTIC -> "Unpredictable — magical storms, ender rifts";
        };
        predictions.add(new Prediction("Weather Patterns", weather, ideology));

        // Resource prediction
        String resources = switch (ideology) {
            case WARMONGER -> "Nether resources become surface-accessible";
            case BUILDER -> "All block drops doubled, insta-mine enabled";
            case MERCHANT -> "Mob drops doubled, emerald ore quadrupled";
            case MYSTIC -> "Enchanted items drop from all mobs";
        };
        predictions.add(new Prediction("Resource Availability", resources, ideology));

        // World event prediction
        String events = switch (ideology) {
            case WARMONGER -> "Pillager raids every 3 days, boss invasions weekly";
            case BUILDER -> "Random structures rise from the earth daily";
            case MERCHANT -> "Traveling merchant visits every 12 hours";
            case MYSTIC -> "Ender dragon respawns, nether portals appear randomly";
        };
        predictions.add(new Prediction("World Events", events, ideology));

        return new SimulationResult(ideology, days, predictions);
    }

    /**
     * Send a formatted simulation report to a player.
     */
    public void sendReport(Player player, SimulationResult result) {
        player.sendMessage("");
        player.sendMessage("§5§l◈ TEMPORAL SIMULATION ◈");
        player.sendMessage("§7Projecting " + result.days() + " days under §"
                + result.ideology().getChatColor().getChar() + result.ideology().getDisplayName() + "§7 rule...");
        player.sendMessage("");

        for (Prediction p : result.predictions()) {
            player.sendMessage(" §8• " + p.label() + ": §7" + p.value());
        }

        player.sendMessage("");
        player.sendMessage("§8§oThis is a simulation only. Actual results may vary.");
    }

    // ========================================================================
    //  Records
    // ========================================================================

    /** A single prediction within a simulation. */
    public record Prediction(String label, String value, Ideology ideology) {
    }

    /** Full simulation result. */
    public record SimulationResult(Ideology ideology, int days, List<Prediction> predictions) {
    }
}
