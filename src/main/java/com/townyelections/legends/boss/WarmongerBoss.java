package com.townyelections.legends.boss;

import com.townyelections.legends.Ideology;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * Warmonger ideology boss — a Wither Skeleton wielding a netherite sword.
 * Attacks: lightning strikes, skeleton army summoning, sweeping fire charge.
 */
public class WarmongerBoss extends IdeologyBoss {

    public WarmongerBoss() {
        super(Ideology.WARMONGER, "§c§lWARMONGER §4§lGENERAL §cKHA'ZIX", 500.0);
    }

    @Override
    protected void onSpawn() {
        if (entity != null) {
            entity.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            entity.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            entity.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            entity.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            entity.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        }
    }

    @Override
    public void tick(Collection<Player> nearbyPlayers) {
        super.tick(nearbyPlayers);
        if (!isAlive()) return;
        cycleAttacks(nearbyPlayers);
        // Ambient flame particles
        if (entity != null && entity.getWorld().getFullTime() % 5 == 0) {
            entity.getWorld().spawnParticle(Particle.FLAME, entity.getLocation().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0.01);
        }
    }

    @Override
    protected void customAttack1(Collection<Player> nearbyPlayers) {
        // Lightning strike on a random nearby player
        Player target = randomTarget(nearbyPlayers);
        if (target == null || entity == null) return;
        Location strike = target.getLocation();
        entity.getWorld().strikeLightningEffect(strike);
        entity.getWorld().playSound(strike, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.8f);
    }

    @Override
    protected void customAttack2(Collection<Player> nearbyPlayers) {
        // Summon skeleton army
        if (entity == null) return;
        World world = entity.getWorld();
        Location center = entity.getLocation();
        for (int i = 0; i < 3; i++) {
            Location spawn = center.clone().add((random.nextDouble() - 0.5) * 8, 0, (random.nextDouble() - 0.5) * 8);
            world.spawnEntity(spawn, EntityType.WITHER_SKELETON);
        }
        world.playSound(center, Sound.ENTITY_WITHER_SKELETON_AMBIENT, 0.8f, 0.5f);
    }

    @Override
    protected void customAttack3(Collection<Player> nearbyPlayers) {
        // Sweeping fire charge — spawn fire in an arc
        if (entity == null) return;
        Location center = entity.getLocation();
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2 * i) / 12;
            Location fire = center.clone().add(Math.cos(angle) * 3, 0, Math.sin(angle) * 3);
            center.getWorld().spawnParticle(Particle.FLAME, fire, 10, 0.3, 0.3, 0.3, 0.05);
        }
        center.getWorld().playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);
    }

    @Override
    protected void arenaMutation(double hpPercent, Location arenaCenter) {
        if (entity == null) return;
        World world = arenaCenter.getWorld();
        if (hpPercent < 0.3) {
            // Low HP: mass summon, fire everywhere
            for (int i = 0; i < 5; i++) {
                world.spawnEntity(arenaCenter.clone().add(
                        (random.nextDouble() - 0.5) * 30, 0, (random.nextDouble() - 0.5) * 30),
                        EntityType.BLAZE);
            }
            world.playSound(arenaCenter, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.5f);
            world.spawnParticle(Particle.LAVA, arenaCenter, 30, 10, 1, 10, 0.1);
        } else if (hpPercent < 0.6) {
            world.spawnParticle(Particle.FLAME, arenaCenter.clone().add(0, 20, 0), 50, 15, 20, 15, 0.05);
            world.playSound(arenaCenter, Sound.ENTITY_BLAZE_AMBIENT, 1.0f, 1.0f);
        }
    }

    @Override
    public Particle getParticleEffect() {
        return Particle.FLAME;
    }
}
