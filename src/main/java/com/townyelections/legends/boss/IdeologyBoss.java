package com.townyelections.legends.boss;

import com.townyelections.legends.Ideology;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Abstract base for all ideology bosses. Extends the concept of a boss fight
 * by defining three custom attack slots and an arena mutation hook. Subclasses
 * implement ideology-specific behaviour.
 *
 * <p>Bosses use a backing {@link Mob} entity (spawned via Bukkit). The boss
 * tracks total damage taken to drive vote tallies and applies arena mutations
 * at health thresholds.
 */
public abstract class IdeologyBoss {

    protected final Ideology ideology;
    protected final String displayName;
    protected final double baseHealth;
    protected LivingEntity entity;
    protected Location spawnLocation;
    protected final Random random = new Random();

    private double totalDamageTaken;
    private int attackCycle;

    protected IdeologyBoss(Ideology ideology, String displayName, double baseHealth) {
        this.ideology = ideology;
        this.displayName = displayName;
        this.baseHealth = baseHealth;
    }

    // ========================================================================
    //  Lifecycle
    // ========================================================================

    /**
     * Spawn the backing entity at the given location. Called once at fight start.
     *
     * @param location the spawn location
     * @param entityType the Bukkit entity type to spawn
     */
    public void spawn(Location location, EntityType entityType) {
        this.spawnLocation = location.clone();
        this.entity = (LivingEntity) location.getWorld().spawnEntity(location, entityType);
        this.entity.setCustomNameVisible(true);
        this.entity.setCustomName(displayName);
        this.entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(baseHealth);
        this.entity.setHealth(baseHealth);
        this.entity.setPersistent(true);
        this.entity.setRemoveWhenFarAway(false);
        this.totalDamageTaken = 0;
        this.attackCycle = 0;
        onSpawn();
    }

    /** Called after the entity is spawned. Override for setup. */
    protected void onSpawn() {
    }

    /** Remove the boss entity from the world. */
    public void remove() {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
        entity = null;
    }

    /** @return true if the boss is alive and valid */
    public boolean isAlive() {
        return entity != null && entity.isValid() && entity.getHealth() > 0;
    }

    // ========================================================================
    //  Combat
    // ========================================================================

    /**
     * Called every tick (or every N ticks) so the boss can perform actions.
     * Subclasses should call {@link #cycleAttacks()} to drive attack rotation.
     *
     * @param nearbyPlayers players within the arena
     */
    public void tick(Collection<Player> nearbyPlayers) {
        if (!isAlive()) {
            return;
        }
        // Apply boss aura to nearby players
        PotionEffectType aura = ideology.getBossPotionAura();
        for (Player player : nearbyPlayers) {
            if (player.getLocation().distance(entity.getLocation()) < 10) {
                player.addPotionEffect(new PotionEffect(aura, 60, 0, true, false, true));
            }
        }
    }

    /**
     * Cycle through the three custom attacks. Call from {@link #tick}.
     */
    protected void cycleAttacks(Collection<Player> nearbyPlayers) {
        attackCycle = (attackCycle + 1) % 3;
        if (nearbyPlayers.isEmpty()) {
            return;
        }
        switch (attackCycle) {
            case 0 -> customAttack1(nearbyPlayers);
            case 1 -> customAttack2(nearbyPlayers);
            case 2 -> customAttack3(nearbyPlayers);
        }
    }

    /** Record damage dealt by a player. */
    public void recordDamage(Player damager, double damage) {
        totalDamageTaken += damage;
    }

    /**
     * Check health thresholds and trigger arena mutations.
     *
     * @param arenaCenter the arena centre for mutation placement
     */
    public void checkMutations(Location arenaCenter) {
        if (!isAlive()) {
            return;
        }
        double hpPercent = entity.getHealth() / baseHealth;
        arenaMutation(hpPercent, arenaCenter);
    }

    // ========================================================================
    //  Abstract attack methods
    // ========================================================================

    /** First custom attack (e.g. melee sweep). */
    protected abstract void customAttack1(Collection<Player> nearbyPlayers);

    /** Second custom attack (e.g. ranged ability). */
    protected abstract void customAttack2(Collection<Player> nearbyPlayers);

    /** Third custom attack (e.g. ultimate ability). */
    protected abstract void customAttack3(Collection<Player> nearbyPlayers);

    /**
     * Arena mutation triggered at health thresholds.
     *
     * @param hpPercent current HP as a fraction (0.0 - 1.0)
     * @param arenaCenter the arena centre location
     */
    protected abstract void arenaMutation(double hpPercent, Location arenaCenter);

    /** The particle effect this boss uses for ambient visuals. */
    public abstract Particle getParticleEffect();

    // ========================================================================
    //  Utilities
    // ========================================================================

    /** Get a random nearby player from the collection. */
    protected Player randomTarget(Collection<Player> players) {
        List<Player> list = new ArrayList<>(players);
        return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }

    // ========================================================================
    //  Accessors
    // ========================================================================

    public Ideology getIdeology() { return ideology; }
    public String getDisplayName() { return displayName; }
    public LivingEntity getEntity() { return entity; }
    public Location getSpawnLocation() { return spawnLocation; }
    public double getBaseHealth() { return baseHealth; }
    public double getTotalDamageTaken() { return totalDamageTaken; }
    public double getHealthPercent() {
        return entity != null && entity.isValid() ? entity.getHealth() / baseHealth : 0;
    }
}
