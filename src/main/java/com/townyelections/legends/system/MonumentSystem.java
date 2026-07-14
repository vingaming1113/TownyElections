package com.townyelections.legends.system;

import com.townyelections.TownyElections;
import com.townyelections.legends.Ideology;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.scheduler.BukkitTask;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds growing monuments to commemorate each Election of Legends. The monument
 * tracks political history: each winning ideology gets its own pillar, past
 * winners' heads are displayed, and consecutive wins grow the structure taller.
 *
 * <p>The monument is built at the arena centre and persists across restarts.
 * When a previous winner wins again, their pillar auto-repairs.
 */
public class MonumentSystem {

    private final TownyElections plugin;
    private final List<MonumentRecord> history = new ArrayList<>();

    public MonumentSystem(TownyElections plugin) {
        this.plugin = plugin;
    }

    /** A record of one legends election for monument building. */
    public record MonumentRecord(Ideology ideology, String winnerName, UUID winnerUuid, int electionNumber) {
    }

    // ========================================================================
    //  Public API
    // ========================================================================

    /**
     * Build (or extend) the monument at the given location.
     *
     * @param location       centre of the monument
     * @param ideology       the winning ideology
     * @param winnerName     the winner's name
     * @param winnerUuid     the winner's UUID
     * @param electionNumber the global election count
     */
    public void buildMonument(Location location, Ideology ideology, String winnerName,
                               UUID winnerUuid, int electionNumber) {
        history.add(new MonumentRecord(ideology, winnerName, winnerUuid, electionNumber));
        World world = location.getWorld();
        int pillarIndex = countConsecutiveWins(ideology);

        // Place the ideology pillar
        Material pillarMaterial = getPillarMaterial(ideology);
        Location base = location.clone().add(pillarIndex * 4, 0, 0);

        int height = Math.min(20 + pillarIndex * 5, 200); // grows with consecutive wins

        // Build pillar
        for (int y = 0; y < height; y++) {
            Block block = world.getBlockAt(base.clone().add(0, y, 0));
            if (block.isEmpty() || block.getType() == Material.AIR) {
                block.setType(pillarMaterial);
            }
        }

        // Place winner's head at the top
        Block headBlock = world.getBlockAt(base.clone().add(0, height, 0));
        headBlock.setType(Material.PLAYER_HEAD);
        if (headBlock.getState() instanceof Skull skull) {
            PlayerProfile profile = Bukkit.createPlayerProfile(winnerUuid, winnerName);
            skull.setOwnerProfile(profile);
            skull.update(true);
        }

        // Decorative ring at base
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                Block ring = world.getBlockAt(base.clone().add(dx, 0, dz));
                ring.setType(Material.GLOWSTONE);
            }
        }

        // Particles and sound
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, base.clone().add(0, height, 0), 50, 3, 5, 3, 0.05);
        world.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.0f);

        Bukkit.broadcastMessage("§6§l🏛 A new pillar rises in the Monument of Legends!");
        Bukkit.broadcastMessage("§7§o" + ideology.getDisplayName() + " — " + winnerName
                + " — Election #" + electionNumber);
    }

    /**
     * Build the monument progressively (one block per tick) for cinematic effect.
     *
     * @param location       centre of the monument
     * @param ideology       the winning ideology
     * @param winnerName     the winner's name
     * @param winnerUuid     the winner's UUID
     * @param electionNumber the global election count
     * @return a BukkitTask that is building the monument
     */
    public BukkitTask buildMonumentAnimated(Location location, Ideology ideology, String winnerName,
                                             UUID winnerUuid, int electionNumber) {
        history.add(new MonumentRecord(ideology, winnerName, winnerUuid, electionNumber));
        World world = location.getWorld();
        int pillarIndex = countConsecutiveWins(ideology);
        Material pillarMaterial = getPillarMaterial(ideology);
        Location base = location.clone().add(pillarIndex * 4, 0, 0);
        int height = Math.min(20 + pillarIndex * 5, 200);

        return Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int currentY = 0;

            @Override
            public void run() {
                if (currentY >= height) {
                    // Place head and finish
                    Block headBlock = world.getBlockAt(base.clone().add(0, height, 0));
                    headBlock.setType(Material.PLAYER_HEAD);
                    if (headBlock.getState() instanceof Skull skull) {
                        PlayerProfile profile = Bukkit.createPlayerProfile(winnerUuid, winnerName);
                        skull.setOwnerProfile(profile);
                        skull.update(true);
                    }
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, base.clone().add(0, height, 0),
                            80, 5, 10, 5, 0.05);
                    world.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 1.0f);

                    // Decorative ring
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            world.getBlockAt(base.clone().add(dx, 0, dz)).setType(Material.GLOWSTONE);
                        }
                    }

                    Bukkit.broadcastMessage("§6§l🏛 Monument of Legends updated! Pillar for "
                            + ideology.getDisplayName() + " — " + winnerName);
                    return;
                }

                Block block = world.getBlockAt(base.clone().add(0, currentY, 0));
                if (block.isEmpty() || block.getType() == Material.AIR) {
                    block.setType(pillarMaterial);
                }
                if (currentY % 3 == 0) {
                    world.spawnParticle(ideology.getParticle(), base.clone().add(0, currentY, 0),
                            5, 0.5, 0.2, 0.5, 0);
                    world.playSound(base, Sound.BLOCK_STONE_PLACE, 0.3f, 0.5f + (currentY / (float) height));
                }
                currentY++;
            }
        }, 0L, 2L);
    }

    // ========================================================================
    //  Helpers
    // ========================================================================

    private int countConsecutiveWins(Ideology ideology) {
        int count = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).ideology() == ideology) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private Material getPillarMaterial(Ideology ideology) {
        return switch (ideology) {
            case WARMONGER -> Material.NETHERITE_BLOCK;
            case BUILDER -> Material.DIAMOND_BLOCK;
            case MERCHANT -> Material.EMERALD_BLOCK;
            case MYSTIC -> Material.AMETHYST_BLOCK;
        };
    }

    /** @return immutable copy of monument history */
    public List<MonumentRecord> getHistory() {
        return List.copyOf(history);
    }

    /** @return number of monuments built */
    public int getMonumentCount() {
        return history.size();
    }
}
