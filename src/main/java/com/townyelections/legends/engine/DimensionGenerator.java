package com.townyelections.legends.engine;

import com.townyelections.TownyElections;
import com.townyelections.legends.Ideology;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.generator.WorldGenerator;

import java.util.Random;

/**
 * Unlocks and generates custom dimensions themed to each ideology after the
 * Election of Legends. Each dimension is a separate Bukkit world with unique
 * settings. Access is restricted via portal mechanics (handled elsewhere).
 *
 * <p>Dimension names:
 * <ul>
 *   <li>crimson_wasteland — Warmonger (nether-like surface world)</li>
 *   <li>architect_vault — Builder (flat, resource-rich)</li>
 *   <li>golden_realm — Merchant (peaceful, abundant)</li>
 *   <li>arcane_nexus — Mystic (magical, end-like)</li>
 * </ul>
 */
public class DimensionGenerator {

    private final TownyElections plugin;
    private final Random random = new Random();

    public DimensionGenerator(TownyElections plugin) {
        this.plugin = plugin;
    }

    // ========================================================================
    //  Public API
    // ========================================================================

    /**
     * Generate (or load) the custom dimension for the given ideology.
     * Safe to call if the world already exists — it just loads it.
     *
     * @param ideology the ideology whose dimension to unlock
     * @return the world, or null on failure
     */
    public World generateDimension(Ideology ideology) {
        String worldName = ideology.getDimensionName();

        // Check if already loaded
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            configureWorld(existing, ideology);
            return existing;
        }

        // Create the world
        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(getEnvironment(ideology));
        creator.type(getWorldType(ideology));
        creator.generateStructures(true);

        if (ideology == Ideology.BUILDER) {
            creator.generator(new EmptyFlatGenerator());
        }

        World world;
        try {
            world = creator.createWorld();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create dimension '" + worldName + "': " + e.getMessage());
            return null;
        }

        if (world != null) {
            configureWorld(world, ideology);
            plugin.getLogger().info("Dimension '" + worldName + "' (" + ideology.getDisplayName() + ") unlocked!");
        }
        return world;
    }

    /**
     * Close (unload) a dimension. Called when the legends consequences expire.
     *
     * @param ideology the ideology whose dimension to close
     */
    public void closeDimension(Ideology ideology) {
        String worldName = ideology.getDimensionName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        // Eject all players back to the default world
        World mainWorld = Bukkit.getWorlds().get(0);
        world.getPlayers().forEach(p -> p.teleport(mainWorld.getSpawnLocation()));

        Bukkit.unloadWorld(world, true);
        plugin.getLogger().info("Dimension '" + worldName + "' closed.");
    }

    /** Close all ideology dimensions. */
    public void closeAllDimensions() {
        for (Ideology ideology : Ideology.values()) {
            closeDimension(ideology);
        }
    }

    /** Check if a dimension is currently accessible. */
    public boolean isDimensionUnlocked(Ideology ideology) {
        return Bukkit.getWorld(ideology.getDimensionName()) != null;
    }

    // ========================================================================
    //  Configuration
    // ========================================================================

    private void configureWorld(World world, Ideology ideology) {
        switch (ideology) {
            case WARMONGER -> {
                world.setDifficulty(Difficulty.HARD);
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setTime(18000L);
                world.setGameRule(GameRule.KEEP_INVENTORY, false);
                world.setGameRule(GameRule.DO_MOB_SPAWNING, true);
                world.setGameRule(GameRule.MOB_GRIEFING, true);
            }
            case BUILDER -> {
                world.setDifficulty(Difficulty.PEACEFUL);
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
                world.setGameRule(GameRule.KEEP_INVENTORY, true);
                world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                world.setGameRule(GameRule.MOB_GRIEFING, false);
            }
            case MERCHANT -> {
                world.setDifficulty(Difficulty.EASY);
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
                world.setGameRule(GameRule.KEEP_INVENTORY, true);
                world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                world.setGameRule(GameRule.RANDOM_TICK_SPEED, 15);
            }
            case MYSTIC -> {
                world.setDifficulty(Difficulty.HARD);
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setTime(18000L);
                world.setGameRule(GameRule.KEEP_INVENTORY, false);
                world.setGameRule(GameRule.DO_MOB_SPAWNING, true);
            }
        }
    }

    private World.Environment getEnvironment(Ideology ideology) {
        return switch (ideology) {
            case WARMONGER -> World.Environment.NETHER;
            case BUILDER, MERCHANT -> World.Environment.NORMAL;
            case MYSTIC -> World.Environment.THE_END;
        };
    }

    private WorldType getWorldType(Ideology ideology) {
        return switch (ideology) {
            case BUILDER -> WorldType.FLAT;
            default -> WorldType.NORMAL;
        };
    }

    /**
     * A minimal flat-world generator for the Builder dimension. Produces a
     * single layer of grass over stone, ideal for creative building.
     */
    private static class EmptyFlatGenerator extends WorldGenerator {
        @Override
        public org.bukkit.generator.ChunkGenerator.ChunkData generateChunkData(World world, Random random,
                                                                                 int x, int z,
                                                                                 org.bukkit.generator.ChunkGenerator.BiomeGrid biome) {
            org.bukkit.generator.ChunkGenerator.ChunkData data =
                    createChunkData(world);
            int minY = world.getMinHeight();
            data.setRegion(0, minY, 0, 16, minY + 1, 16,
                    org.bukkit.Material.BEDROCK);
            data.setRegion(0, minY + 1, 0, 16, minY + 65, 16,
                    org.bukkit.Material.STONE);
            data.setRegion(0, minY + 65, 0, 16, minY + 66, 16,
                    org.bukkit.Material.GRASS_BLOCK);
            return data;
        }
    }
}
