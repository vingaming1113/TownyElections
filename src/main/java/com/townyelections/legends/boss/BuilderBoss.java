package com.townyelections.legends.boss;

import com.townyelections.legends.Ideology;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * Builder ideology boss — an Iron Golem with diamond tools. Attacks: wall
 * construction, block crushing (anvil rain), structure summoning (obsidian cage).
 */
public class BuilderBoss extends IdeologyBoss {

    public BuilderBoss() {
        super(Ideology.BUILDER, "§b§lBUILDER §3§lARCHITECT §bTHAL'NOR", 500.0);
    }

    @Override
    protected void onSpawn() {
        if (entity != null) {
            entity.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
        }
    }

    @Override
    public void tick(Collection<Player> nearbyPlayers) {
        super.tick(nearbyPlayers);
        if (!isAlive()) return;
        cycleAttacks(nearbyPlayers);
        if (entity != null && entity.getWorld().getFullTime() % 5 == 0) {
            entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                    entity.getLocation().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0);
        }
    }

    @Override
    protected void customAttack1(Collection<Player> nearbyPlayers) {
        // Wall building — erect a temporary wall between boss and nearest player
        Player target = randomTarget(nearbyPlayers);
        if (target == null || entity == null) return;
        Location bossLoc = entity.getLocation();
        Location playerLoc = target.getLocation();
        World world = bossLoc.getWorld();
        double dx = playerLoc.getX() - bossLoc.getX();
        double dz = playerLoc.getZ() - bossLoc.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1) return;
        double perpX = -dz / len;
        double perpZ = dx / len;
        Location mid = bossLoc.clone().add(dx / 2, 0, dz / 2);
        for (int h = 0; h < 4; h++) {
            for (int w = -3; w <= 3; w++) {
                Location block = mid.clone().add(perpX * w, h, perpZ * w);
                if (block.getBlock().isEmpty()) {
                    block.getBlock().setType(Material.COBBLESTONE);
                }
            }
        }
        world.playSound(mid, Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);
        world.spawnParticle(Particle.BLOCK_CRACK, mid, 50, 3, 3, 3,
                Material.COBBLESTONE.createBlockData());
    }

    @Override
    protected void customAttack2(Collection<Player> nearbyPlayers) {
        // Anvil rain
        if (entity == null) return;
        World world = entity.getWorld();
        Location center = entity.getLocation();
        for (int i = 0; i < 5; i++) {
            Location drop = center.clone().add(
                    (random.nextDouble() - 0.5) * 15, 10 + random.nextDouble() * 5,
                    (random.nextDouble() - 0.5) * 15);
            FallingBlock anvil = world.spawnFallingBlock(drop, Material.ANVIL.createBlockData());
            anvil.setHurtEntities(true);
            anvil.setDropItem(false);
        }
        world.playSound(center, Sound.BLOCK_ANVIL_PLACE, 1.0f, 0.8f);
    }

    @Override
    protected void customAttack3(Collection<Player> nearbyPlayers) {
        // Obsidian cage — encase the target in obsidian
        Player target = randomTarget(nearbyPlayers);
        if (target == null || entity == null) return;
        Location loc = target.getLocation();
        World world = loc.getWorld();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    if (dx == 0 && dz == 0 && dy == 1) continue; // leave breathing room
                    world.getBlockAt(loc.clone().add(dx, dy, dz)).setType(Material.OBSIDIAN);
                }
            }
        }
        world.playSound(loc, Sound.BLOCK_STONE_PLACE, 1.2f, 0.5f);
        world.spawnParticle(Particle.BLOCK_CRACK, loc, 30, 1, 1, 1,
                Material.OBSIDIAN.createBlockData());
    }

    @Override
    protected void arenaMutation(double hpPercent, Location arenaCenter) {
        if (entity == null) return;
        World world = arenaCenter.getWorld();
        BlockData stone = Material.STONE.createBlockData();
        if (hpPercent < 0.3) {
            // Spawn pillars around the arena
            for (int i = 0; i < 8; i++) {
                double angle = (Math.PI * 2 * i) / 8;
                int radius = 20;
                Location base = arenaCenter.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                for (int y = 0; y < 5; y++) {
                    base.clone().add(0, y, 0).getBlock().setType(Material.OBSIDIAN);
                }
            }
            world.playSound(arenaCenter, Sound.BLOCK_STONE_PLACE, 1.5f, 0.5f);
        } else if (hpPercent < 0.6) {
            world.spawnParticle(Particle.BLOCK_CRACK, arenaCenter, 100, 20, 1, 20, stone);
            world.playSound(arenaCenter, Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
        }
    }

    @Override
    public Particle getParticleEffect() {
        return Particle.HAPPY_VILLAGER;
    }
}
