package com.townyelections.legends.system;

import com.townyelections.TownyElections;
import com.townyelections.legends.Ideology;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Grants permanent god-like powers to the winner of the Election of Legends.
 * Powers are ideology-specific and persist until the next legends election.
 * Each power maps to an ability ID that can be triggered via right-click or
 * automatic passive effects.
 */
public class AscensionSystem {

    private final TownyElections plugin;

    /** player UUID -> ideology they ascended with */
    private final Map<UUID, Ideology> ascendedPlayers = new HashMap<>();
    /** player UUID -> epoch millis when powers expire */
    private final Map<UUID, Long> ascensionExpiry = new HashMap<>();

    public AscensionSystem(TownyElections plugin) {
        this.plugin = plugin;
    }

    // ========================================================================
    //  Public API
    // ========================================================================

    /**
     * Ascend a player with the winning ideology's powers.
     *
     * @param player    the election winner
     * @param ideology  the winning ideology
     * @param durationMs how long powers last
     */
    public void ascend(Player player, Ideology ideology, long durationMs) {
        ascendedPlayers.put(player.getUniqueId(), ideology);
        ascensionExpiry.put(player.getUniqueId(), System.currentTimeMillis() + durationMs);

        // Dramatic ascension effect
        Location loc = player.getLocation();
        World world = player.getWorld();
        world.strikeLightningEffect(loc);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.add(0, 1, 0), 100, 1, 2, 1, 0.1);
        world.playSound(loc, Sound.ITEM_TOTEM_USE, 2.0f, 1.0f);
        world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);

        Bukkit.broadcastMessage(ideology.getChatColor() + "§l" + player.getName()
                + " has ASCENDED as the " + ideology.getDisplayName() + " champion!");
        Bukkit.broadcastMessage("§7§o" + ideology.getSlogan());
    }

    /**
     * Remove all ascension powers from a player. Called when powers expire or
     * when a new election begins.
     */
    public void revoke(Player player) {
        ascendedPlayers.remove(player.getUniqueId());
        ascensionExpiry.remove(player.getUniqueId());
        player.sendMessage("§c§lYour ascension powers have faded...");
    }

    /** Revoke all ascensions (called before a new legends election). */
    public void revokeAll() {
        for (UUID uuid : ascendedPlayers.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                revoke(player);
            }
        }
        ascendedPlayers.clear();
        ascensionExpiry.clear();
    }

    /** Check if a player is currently ascended. */
    public boolean isAscended(Player player) {
        return ascendedPlayers.containsKey(player.getUniqueId())
                && ascensionExpiry.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    /** Get a player's ascension ideology, or null. */
    public Ideology getIdeology(Player player) {
        return ascendedPlayers.get(player.getUniqueId());
    }

    /** Get all ascension power IDs for a player's ideology. */
    public List<String> getPowers(Player player) {
        Ideology ideology = getIdeology(player);
        return ideology == null ? List.of() : ideology.getAscensionPowers();
    }

    // ========================================================================
    //  Power execution
    // ========================================================================

    /**
     * Execute a specific ascension power for a player. Called from event
     * listeners when the player triggers an ability.
     *
     * @param player  the ascended player
     * @param powerId the power to execute
     */
    public void executePower(Player player, String powerId) {
        if (!isAscended(player)) return;
        Ideology ideology = getIdeology(player);
        if (ideology == null) return;

        switch (powerId) {
            // Warmonger
            case "LIGHTNING_STRIKE" -> executeLightningStrike(player);
            case "MOB_COMMAND" -> executeMobCommand(player);
            case "BLOOD_RAGE" -> executeBloodRage(player);
            case "FEAR_AURA" -> executeFearAura(player);
            // Builder
            case "INSTANT_STRUCTURE" -> executeInstantStructure(player);
            case "TERRAFORM" -> executeTerraform(player);
            case "FORCEFIELD" -> executeForcefield(player);
            case "BLOCK_REGENERATION" -> executeBlockRegeneration(player);
            // Merchant
            case "SHOP_TELEPORT" -> executeShopTeleport(player);
            case "INFINITE_CURRENCY" -> executeInfiniteCurrency(player);
            case "TRADE_WINDS" -> executeTradeWinds(player);
            case "GOLEM_BODYGUARD" -> executeGolemBodyguard(player);
            // Mystic
            case "FLIGHT" -> executeFlight(player);
            case "PORTAL_CREATION" -> executePortalCreation(player);
            case "TELEPORT_DASH" -> executeTeleportDash(player);
            case "MAGIC_SHIELD" -> executeMagicShield(player);
        }
    }

    // === Warmonger powers ===

    private void executeLightningStrike(Player player) {
        Location target = player.getTargetBlock(null, 50).getLocation();
        player.getWorld().strikeLightningEffect(target);
        player.getWorld().playSound(target, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        player.sendMessage("§c§l⚡ Lightning Strike!");
    }

    private void executeMobCommand(Player player) {
        // Tame nearby hostile mobs temporarily
        int count = 0;
        for (var entity : player.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof org.bukkit.entity.Monster monster) {
                monster.setTarget(null);
                count++;
            }
        }
        player.sendMessage("§c§l☠ Commanded " + count + " nearby mobs to stand down.");
    }

    private void executeBloodRage(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 2, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1, true, false, true));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0f, 1.0f);
        player.sendMessage("§c§l⚔ BLOOD RAGE ACTIVATED!");
    }

    private void executeFearAura(Player player) {
        for (var entity : player.getNearbyEntities(15, 15, 15)) {
            if (entity instanceof org.bukkit.entity.Mob mob) {
                mob.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 2));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
            }
        }
        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation(), 50, 5, 3, 5, 0.02);
        player.sendMessage("§c§l☠ Fear Aura unleashed!");
    }

    // === Builder powers ===

    private void executeInstantStructure(Player player) {
        Location base = player.getLocation().clone().add(0, -1, 0);
        // Quick 3x3 platform
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                base.clone().add(x, 0, z).getBlock().setType(Material.STONE_BRICKS);
            }
        }
        player.getWorld().playSound(base, Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);
        player.sendMessage("§b§l🏗 Structure placed!");
    }

    private void executeTerraform(Player player) {
        Location center = player.getLocation();
        // Flatten a radius around the player
        int radius = 5;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) {
                    Location block = center.clone().add(x, -1, z);
                    if (block.getBlock().getType() != Material.BEDROCK) {
                        block.getBlock().setType(Material.GRASS_BLOCK);
                    }
                }
            }
        }
        player.getWorld().playSound(center, Sound.BLOCK_GRASS_PLACE, 1.0f, 1.0f);
        player.sendMessage("§b§l🌍 Terraformed!");
    }

    private void executeForcefield(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 3, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0, true, false, true));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        player.sendMessage("§b§l🛡 Forcefield active!");
    }

    private void executeBlockRegeneration(Player player) {
        // Heal durability on held item
        var item = player.getInventory().getItemInMainHand();
        if (item.getType().getMaxDurability() > 0) {
            item.setDurability((short) 0);
            player.sendMessage("§b§l🔧 Item fully repaired!");
        } else {
            player.sendMessage("§7No item to repair.");
        }
    }

    // === Merchant powers ===

    private void executeShopTeleport(Player player) {
        player.sendMessage("§e§l💰 Finding nearest shop...");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 30, 0.5, 1, 0.5, 0.1);
    }

    private void executeInfiniteCurrency(Player player) {
        // Grant some emeralds as "infinite currency"
        player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.EMERALD, 16));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.sendMessage("§e§l💰 16 Emeralds materialized from the Golden Realm!");
    }

    private void executeTradeWinds(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 2, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 600, 1, true, false, true));
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 20, 1, 0.5, 1, 0.05);
        player.sendMessage("§e§l💨 Trade Winds carry you swiftly!");
    }

    private void executeGolemBodyguard(Player player) {
        player.getWorld().spawnEntity(player.getLocation(), EntityType.IRON_GOLEM);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 1.0f);
        player.sendMessage("§e§l🛡 A loyal golem joins your side!");
    }

    // === Mystic powers ===

    private void executeFlight(Player player) {
        player.setAllowFlight(true);
        player.setFlying(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 0, true, false, true));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.0f);
        player.sendMessage("§d§l✨ You take flight!");
    }

    private void executePortalCreation(Player player) {
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.PORTAL, loc, 100, 1, 2, 1, 0.3);
        player.getWorld().playSound(loc, Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 1.0f);
        player.sendMessage("§d§l🌀 Portal ripples open!");
    }

    private void executeTeleportDash(Player player) {
        Location forward = player.getLocation().add(player.getLocation().getDirection().multiply(10));
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 20, 0.3, 1, 0.3, 0.1);
        player.teleport(forward);
        player.getWorld().spawnParticle(Particle.PORTAL, forward, 20, 0.3, 1, 0.3, 0.1);
        player.getWorld().playSound(forward, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        player.sendMessage("§d§l💨 Dashed forward!");
    }

    private void executeMagicShield(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 4, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1, true, false, true));
        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, player.getLocation(), 30, 0.5, 1, 0.5, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        player.sendMessage("§d§l🛡 Magic Shield surrounds you!");
    }
}
