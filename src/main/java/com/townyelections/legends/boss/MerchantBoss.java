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
 * Merchant ideology boss — an Evoker cloaked in emeralds. Attacks: gold
 * throwing (blinding), iron golem summoning, emerald shard barrage.
 */
public class MerchantBoss extends IdeologyBoss {

    public MerchantBoss() {
        super(Ideology.MERCHANT, "§e§lMERCHANT §6§lBARON §eGILD'HART", 500.0);
    }

    @Override
    protected void onSpawn() {
        if (entity != null) {
            entity.getEquipment().setItemInMainHand(new ItemStack(Material.EMERALD));
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
        // Gold throwing — drop gold items that deal damage on contact
        if (entity == null) return;
        World world = entity.getWorld();
        Location center = entity.getLocation();
        for (int i = 0; i < 10; i++) {
            world.dropItemNaturally(center.clone().add(0, 1.5, 0),
                    new ItemStack(Material.GOLD_NUGGET, 1));
        }
        world.playSound(center, Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.5f);
        world.spawnParticle(Particle.REDSTONE, center, 20, 2, 2, 2,
                new Particle.DustOptions(org.bukkit.Color.YELLOW, 2f));
    }

    @Override
    protected void customAttack2(Collection<Player> nearbyPlayers) {
        // Summon iron golems
        if (entity == null) return;
        World world = entity.getWorld();
        Location center = entity.getLocation();
        for (int i = 0; i < 2; i++) {
            world.spawnEntity(center.clone().add(
                    (random.nextDouble() - 0.5) * 6, 0, (random.nextDouble() - 0.5) * 6),
                    EntityType.IRON_GOLEM);
        }
        world.playSound(center, Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 0.8f);
    }

    @Override
    protected void customAttack3(Collection<Player> nearbyPlayers) {
        // Emerald shard barrage — shoot emerald items at all nearby players
        if (entity == null) return;
        World world = entity.getWorld();
        Location center = entity.getLocation();
        for (Player target : nearbyPlayers) {
            Location tLoc = target.getLocation();
            world.spawnParticle(Particle.REDSTONE, center, 10, 0.5, 0.5, 0.5,
                    new Particle.DustOptions(org.bukkit.Color.LIME, 1.5f));
            // Visual "shards" using particle trail
            for (int step = 0; step < 10; step++) {
                double t = step / 10.0;
                Location along = center.clone().add(
                        (tLoc.getX() - center.getX()) * t,
                        (tLoc.getY() - center.getY()) * t + 0.5,
                        (tLoc.getZ() - center.getZ()) * t);
                world.spawnParticle(Particle.HAPPY_VILLAGER, along, 1, 0, 0, 0, 0);
            }
        }
        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
    }

    @Override
    protected void arenaMutation(double hpPercent, Location arenaCenter) {
        if (entity == null) return;
        World world = arenaCenter.getWorld();
        if (hpPercent < 0.3) {
            // Drop emeralds everywhere
            for (int i = 0; i < 20; i++) {
                world.dropItemNaturally(arenaCenter.clone().add(
                        (random.nextDouble() - 0.5) * 30, 5 + random.nextDouble() * 5,
                        (random.nextDouble() - 0.5) * 30), new ItemStack(Material.EMERALD, 2));
            }
            world.playSound(arenaCenter, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            world.spawnParticle(Particle.HAPPY_VILLAGER, arenaCenter, 50, 10, 5, 10, 0);
        } else if (hpPercent < 0.6) {
            world.spawnParticle(Particle.REDSTONE, arenaCenter, 80, 15, 3, 15,
                    new Particle.DustOptions(org.bukkit.Color.YELLOW, 2f));
            world.playSound(arenaCenter, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
        }
    }

    @Override
    public Particle getParticleEffect() {
        return Particle.HAPPY_VILLAGER;
    }
}
