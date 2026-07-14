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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;

/**
 * Mystic ideology boss — a Witch wielding amethyst. Attacks: teleportation,
 * potion storm, enderman portal (rift summoning).
 */
public class MysticBoss extends IdeologyBoss {

    public MysticBoss() {
        super(Ideology.MYSTIC, "§d§lMYSTIC §5§lORACLE §dZYL'VETH", 500.0);
    }

    @Override
    protected void onSpawn() {
        if (entity != null) {
            entity.getEquipment().setItemInMainHand(new ItemStack(Material.AMETHYST_CLUSTER));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                    Integer.MAX_VALUE, 0, true, false, true));
        }
    }

    @Override
    public void tick(Collection<Player> nearbyPlayers) {
        super.tick(nearbyPlayers);
        if (!isAlive()) return;
        cycleAttacks(nearbyPlayers);
        if (entity != null && entity.getWorld().getFullTime() % 3 == 0) {
            entity.getWorld().spawnParticle(Particle.SPELL_WITCH,
                    entity.getLocation().add(0, 1, 0), 5, 0.8, 0.8, 0.8, 0);
            entity.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE,
                    entity.getLocation().add(0, 1.5, 0), 2, 0.3, 0.3, 0.3, 0.05);
        }
    }

    @Override
    protected void customAttack1(Collection<Player> nearbyPlayers) {
        // Teleport — randomly teleport the boss and nearby players
        if (entity == null) return;
        World world = entity.getWorld();
        Location center = entity.getLocation();
        world.spawnParticle(Particle.PORTAL, center, 50, 1, 2, 1, 0.5);
        world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        // Teleport boss
        Location newLoc = center.clone().add(
                (random.nextDouble() - 0.5) * 20, 0, (random.nextDouble() - 0.5) * 20);
        entity.teleport(newLoc);
        world.spawnParticle(Particle.PORTAL, newLoc, 30, 0.5, 1, 0.5, 0.3);

        // Scatter 1-2 nearby players
        for (Player player : nearbyPlayers) {
            if (random.nextBoolean()) {
                Location playerNew = player.getLocation().clone().add(
                        (random.nextDouble() - 0.5) * 15, 0, (random.nextDouble() - 0.5) * 15);
                player.teleport(playerNew);
            }
        }
    }

    @Override
    protected void customAttack2(Collection<Player> nearbyPlayers) {
        // Potion storm — apply random harmful effects to nearby players
        if (entity == null) return;
        World world = entity.getWorld();
        Location center = entity.getLocation();
        PotionEffectType[] debuffs = {PotionEffectType.POISON, PotionEffectType.WEAKNESS,
                PotionEffectType.SLOWNESS, PotionEffectType.BLINDNESS, PotionEffectType.WITHER};

        for (Player player : nearbyPlayers) {
            PotionEffectType type = debuffs[random.nextInt(debuffs.length)];
            player.addPotionEffect(new PotionEffect(type, 100, random.nextInt(2), true, false, true));
        }

        world.spawnParticle(Particle.SPELL_WITCH, center, 80, 8, 3, 8, 0);
        world.playSound(center, Sound.ENTITY_WITCH_THROW, 1.0f, 0.8f);
    }

    @Override
    protected void customAttack3(Collection<Player> nearbyPlayers) {
        // Enderman rift — summon endermen and create portal effects
        if (entity == null) return;
        World world = entity.getWorld();
        Location center = entity.getLocation();

        for (int i = 0; i < 4; i++) {
            Location spawn = center.clone().add(
                    (random.nextDouble() - 0.5) * 10, 0, (random.nextDouble() - 0.5) * 10);
            world.spawnEntity(spawn, EntityType.ENDERMAN);
            world.spawnParticle(Particle.PORTAL, spawn, 20, 0.5, 1, 0.5, 0.1);
        }

        // Create a dramatic portal ring
        for (int i = 0; i < 36; i++) {
            double angle = (Math.PI * 2 * i) / 36;
            Location ring = center.clone().add(Math.cos(angle) * 5, 0, Math.sin(angle) * 5);
            world.spawnParticle(Particle.DRAGON_BREATH, ring, 1, 0, 0, 0, 0);
        }
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.5f);
    }

    @Override
    protected void arenaMutation(double hpPercent, Location arenaCenter) {
        if (entity == null) return;
        World world = arenaCenter.getWorld();
        if (hpPercent < 0.3) {
            // Massive enderman swarm + dragon breath
            for (int i = 0; i < 8; i++) {
                world.spawnEntity(arenaCenter.clone().add(
                        (random.nextDouble() - 0.5) * 30, 0, (random.nextDouble() - 0.5) * 30),
                        EntityType.ENDERMAN);
            }
            world.spawnParticle(Particle.DRAGON_BREATH, arenaCenter, 100, 15, 5, 15, 0.01);
            world.playSound(arenaCenter, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.3f);
        } else if (hpPercent < 0.6) {
            world.spawnParticle(Particle.SPELL_WITCH, arenaCenter, 100, 20, 3, 20, 0);
            world.spawnParticle(Particle.ENCHANTMENT_TABLE, arenaCenter, 30, 10, 10, 10, 0.05);
            world.playSound(arenaCenter, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        }
    }

    @Override
    public Particle getParticleEffect() {
        return Particle.SPELL_WITCH;
    }
}
