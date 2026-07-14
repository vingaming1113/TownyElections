package com.townyelections.legends.listener;

import com.townyelections.TownyElections;
import com.townyelections.legends.boss.IdeologyBoss;
import com.townyelections.legends.engine.BossBattleArena;
import com.townyelections.legends.Ideology;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Listens for combat events during the Election of Legends boss battle.
 * Tracks player damage to ideology bosses, prevents arena exploits, and
 * handles boss deaths.
 */
public class BossBattleListener implements Listener {

    private final TownyElections plugin;
    private final BossBattleArena arena;

    public BossBattleListener(TownyElections plugin, BossBattleArena arena) {
        this.plugin = plugin;
        this.arena = arena;
    }

    @EventHandler
    public void onPlayerDamageBoss(EntityDamageByEntityEvent event) {
        if (!arena.isBattleActive()) return;
        if (!(event.getDamager() instanceof Player player)) return;

        Entity entity = event.getEntity();

        // Check if the entity belongs to any boss
        for (Ideology ideology : Ideology.values()) {
            IdeologyBoss boss = arena.getBoss(ideology);
            if (boss != null && boss.isAlive() && boss.getEntity().equals(entity)) {
                arena.recordDamage(player, ideology, event.getFinalDamage());
                return;
            }
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        if (!arena.isBattleActive()) return;
        LivingEntity entity = event.getEntity();

        for (Ideology ideology : Ideology.values()) {
            IdeologyBoss boss = arena.getBoss(ideology);
            if (boss != null && boss.getEntity() != null && boss.getEntity().equals(entity)) {
                // Boss death announcement
                org.bukkit.Bukkit.broadcastMessage(ideology.getChatColor() + "§l"
                        + boss.getDisplayName() + " §7has been defeated!");
                entity.getWorld().strikeLightningEffect(entity.getLocation());
                entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_HUGE,
                        entity.getLocation(), 3, 2, 2, 2, 0);
                break;
            }
        }
    }

    @EventHandler
    public void onArenaEntityDamage(EntityDamageEvent event) {
        if (!arena.isBattleActive()) return;

        // Prevent non-boss, non-player entities from being damaged in the arena
        if (event.getEntity() instanceof Player) return;

        for (Ideology ideology : Ideology.values()) {
            IdeologyBoss boss = arena.getBoss(ideology);
            if (boss != null && boss.getEntity() != null && boss.getEntity().equals(event.getEntity())) {
                return; // Boss damage is allowed
            }
        }

        // Non-boss entities in the arena: cancel damage
        if (arena.getArenaCenter() != null
                && event.getEntity().getLocation().distance(arena.getArenaCenter()) <= arena.getArenaRadius()) {
            event.setCancelled(true);
        }
    }
}
