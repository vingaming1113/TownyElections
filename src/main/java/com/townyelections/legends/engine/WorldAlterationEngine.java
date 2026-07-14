package com.townyelections.legends.engine;

import com.townyelections.TownyElections;
import com.townyelections.legends.Ideology;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Applies and reverts sweeping world-alteration effects when an ideology wins
 * the Election of Legends. Each ideology carries a unique set of physics changes
 * that affect every world on the server for 30 days.
 *
 * <p>All alterations are tracked so they can be cleanly reverted before the
 * next election. This engine operates on a repeating task to continuously
 * enforce alterations (mob buffs, weather, random events).
 */
public class WorldAlterationEngine {

    private final TownyElections plugin;
    private Ideology activeIdeology;
    private BukkitTask enforcementTask;
    private long alterationExpiresAt;
    private boolean reverted;

    /** Saved pre-alteration game rule values, restored on revert. */
    private final Map<World, Map<GameRule<?>, Object>> savedGameRules = new HashMap<>();

    public WorldAlterationEngine(TownyElections plugin) {
        this.plugin = plugin;
    }

    // ========================================================================
    //  Public API
    // ========================================================================

    /**
     * Apply the full suite of world alterations for the winning ideology.
     * Effects persist until {@link #revert()} is called or the duration expires.
     *
     * @param ideology   the winning ideology
     * @param durationMs how long effects should last
     */
    public void applyAlterations(Ideology ideology, long durationMs) {
        revert(); // clean slate — safety net
        this.activeIdeology = ideology;
        this.alterationExpiresAt = System.currentTimeMillis() + durationMs;
        this.reverted = false;

        for (World world : Bukkit.getWorlds()) {
            saveGameRules(world);
            applyWorldAlterations(world, ideology);
        }

        // Enforce mob buffs, random events, and weather on a 5-second cycle.
        enforcementTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (reverted || System.currentTimeMillis() >= alterationExpiresAt) {
                revert();
                return;
            }
            enforceAlterations(activeIdeology);
        }, 100L, 100L);

        plugin.getLogger().info("World alterations applied for ideology: " + ideology.getDisplayName());
    }

    /**
     * Revert all alterations and restore original world state. Safe to call
     * multiple times — subsequent calls are no-ops.
     */
    public void revert() {
        if (reverted) {
            return;
        }
        reverted = true;

        if (enforcementTask != null) {
            enforcementTask.cancel();
            enforcementTask = null;
        }

        for (Map.Entry<World, Map<GameRule<?>, Object>> entry : savedGameRules.entrySet()) {
            World world = entry.getKey();
            for (Map.Entry<GameRule<?>, Object> rule : entry.getValue().entrySet()) {
                restoreGameRule(world, rule.getKey(), rule.getValue());
            }
        }
        savedGameRules.clear();
        activeIdeology = null;

        plugin.getLogger().info("World alterations reverted.");
    }

    /** @return true if alterations are currently active and not yet reverted. */
    public boolean isActive() {
        return !reverted && activeIdeology != null;
    }

    /** @return the currently active ideology, or null */
    public Ideology getActiveIdeology() {
        return activeIdeology;
    }

    /** @return epoch millis when alterations expire, or 0 */
    public long getExpiresAt() {
        return alterationExpiresAt;
    }

    // ========================================================================
    //  Per-ideology world changes
    // ========================================================================

    private void applyWorldAlterations(World world, Ideology ideology) {
        switch (ideology) {
            case WARMONGER -> applyWarmonger(world);
            case BUILDER -> applyBuilder(world);
            case MERCHANT -> applyMerchant(world);
            case MYSTIC -> applyMystic(world);
        }
    }

    private void applyWarmonger(World world) {
        setGameRule(world, GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(18000L); // permanent midnight
        // Nether mobs can spawn on surface is handled via enforce cycle
    }

    private void applyBuilder(World world) {
        setGameRule(world, GameRule.RANDOM_TICK_SPEED, 6); // faster block/growth ticks
    }

    private void applyMerchant(World world) {
        setGameRule(world, GameRule.RANDOM_TICK_SPEED, 15); // triple crop growth
        setGameRule(world, GameRule.DO_WEATHER_CYCLE, false);
        world.setStorm(false);
        world.setThundering(false);
        world.setClearWeatherDuration(Integer.MAX_VALUE);
    }

    private void applyMystic(World world) {
        setGameRule(world, GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(18000L);
        setGameRule(world, GameRule.RANDOM_TICK_SPEED, 8);
    }

    // ========================================================================
    //  Continuous enforcement (called every 5s)
    // ========================================================================

    private void enforceAlterations(Ideology ideology) {
        for (World world : Bukkit.getWorlds()) {
            switch (ideology) {
                case WARMONGER -> enforceWarmonger(world);
                case BUILDER -> enforceBuilder(world);
                case MERCHANT -> enforceMerchant(world);
                case MYSTIC -> enforceMystic(world);
            }
        }
    }

    private void enforceWarmonger(World world) {
        // Buff all hostile mobs: +50% health, +25% damage
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Monster monster && monster.isValid()) {
                double baseMax = monster.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
                monster.getAttribute(Attribute.MAX_HEALTH).setBaseValue(baseMax * 1.5);
                monster.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(
                        monster.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue() * 1.25);
                // Remove if mob goes beyond arena
            }
        }
        // Randomly spawn nether mobs on surface (5% chance per tick cycle)
        if (Math.random() < 0.05 && world.getPlayers().size() > 0) {
            Player target = world.getPlayers().get((int) (Math.random() * world.getPlayers().size()));
            EntityType type = Math.random() < 0.5 ? EntityType.BLAZE : EntityType.WITHER_SKELETON;
            world.spawn(target.getLocation().add(
                    (Math.random() - 0.5) * 30, 0, (Math.random() - 0.5) * 30), type.getEntityClass());
        }
    }

    private void enforceBuilder(World world) {
        // Apply Haste I to all players
        for (Player player : world.getPlayers()) {
            if (!player.hasPotionEffect(PotionEffectType.HASTE)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,
                        200, 0, true, false, true));
            }
        }
    }

    private void enforceMerchant(World world) {
        // Apply Luck and Hero of the Village to all players
        for (Player player : world.getPlayers()) {
            if (!player.hasPotionEffect(PotionEffectType.LUCK)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK,
                        300, 1, true, false, true));
            }
            if (!player.hasPotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE,
                        300, 0, true, false, true));
            }
        }
    }

    private void enforceMystic(World world) {
        // Apply random potion effects and occasional teleports
        for (Player player : world.getPlayers()) {
            if (Math.random() < 0.02) {
                // Random teleport within 50 blocks
                player.teleport(player.getLocation().add(
                        (Math.random() - 0.5) * 100, 0, (Math.random() - 0.5) * 100));
            }
            if (!player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                        400, 0, true, false, true));
            }
        }
        // Randomly spawn witch / evoker near players
        if (Math.random() < 0.03 && world.getPlayers().size() > 0) {
            Player target = world.getPlayers().get((int) (Math.random() * world.getPlayers().size()));
            EntityType type = Math.random() < 0.5 ? EntityType.WITCH : EntityType.EVOKER;
            world.spawn(target.getLocation().add(
                    (Math.random() - 0.5) * 25, 0, (Math.random() - 0.5) * 25), type.getEntityClass());
        }
    }

    // ========================================================================
    //  GameRule helpers
    // ========================================================================

    @SuppressWarnings("unchecked")
    private <T> void saveGameRules(World world) {
        Map<GameRule<?>, Object> rules = new HashMap<>();
        for (GameRule<?> rule : GameRule.values()) {
            try {
                Object value = world.getGameRuleValue(rule);
                if (value != null) {
                    rules.put(rule, value);
                }
            } catch (Exception ignored) {
                // Some game rules may not be applicable
            }
        }
        savedGameRules.put(world, rules);
    }

    @SuppressWarnings("unchecked")
    private <T> void setGameRule(World world, GameRule<T> rule, T value) {
        try {
            world.setGameRule(rule, value);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to set game rule " + rule.getName() + " in world " + world.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void restoreGameRule(World world, GameRule<T> rule, Object value) {
        try {
            world.setGameRule(rule, (T) value);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to restore game rule " + rule.getName() + " in world " + world.getName(), e);
        }
    }
}
