package com.townyelections.legends.listener;

import com.townyelections.TownyElections;
import com.townyelections.legends.Ideology;
import com.townyelections.legends.system.AscensionSystem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listens for events related to ascended players: ability triggers on
 * right-click, passive effects, and preventing flight toggle abuse.
 */
public class AscensionListener implements Listener {

    private final TownyElections plugin;
    private final AscensionSystem ascension;

    public AscensionListener(TownyElections plugin, AscensionSystem ascension) {
        this.plugin = plugin;
        this.ascension = ascension;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAscendedAbilityUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!ascension.isAscended(player)) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        Ideology ideology = ascension.getIdeology(player);
        if (ideology == null) return;

        // Trigger ideology-specific power based on held item
        String powerId = null;
        Material held = item.getType();

        switch (ideology) {
            case WARMONGER -> {
                if (held == Material.NETHERITE_SWORD) powerId = "LIGHTNING_STRIKE";
                else if (held == Material.BONE) powerId = "MOB_COMMAND";
                else if (held == Material.BLAZE_POWDER) powerId = "BLOOD_RAGE";
                else if (held == Material.WITHER_SKELETON_SKULL) powerId = "FEAR_AURA";
            }
            case BUILDER -> {
                if (held == Material.DIAMOND_PICKAXE) powerId = "INSTANT_STRUCTURE";
                else if (held == Material.DIRT) powerId = "TERRAFORM";
                else if (held == Material.SHIELD) powerId = "FORCEFIELD";
                else if (held == Material.ANVIL) powerId = "BLOCK_REGENERATION";
            }
            case MERCHANT -> {
                if (held == Material.EMERALD) powerId = "SHOP_TELEPORT";
                else if (held == Material.GOLD_BLOCK) powerId = "INFINITE_CURRENCY";
                else if (held == Material.FEATHER) powerId = "TRADE_WINDS";
                else if (held == Material.IRON_BLOCK) powerId = "GOLEM_BODYGUARD";
            }
            case MYSTIC -> {
                if (held == Material.AMETHYST_CLUSTER) powerId = "FLIGHT";
                else if (held == Material.ENDER_PEARL) powerId = "PORTAL_CREATION";
                else if (held == Material.CHORUS_FRUIT) powerId = "TELEPORT_DASH";
                else if (held == Material.ENCHANTED_BOOK) powerId = "MAGIC_SHIELD";
            }
        }

        if (powerId != null) {
            ascension.executePower(player, powerId);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAscendedDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (ascension.isAscended(player)) {
            // Ascended players keep their powers even in death — dramatic flair
            event.setDeathMessage(event.getDeathMessage() + " §5§l⚡ THE ASCENDED FALLS!");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (ascension.isAscended(player)) {
            Ideology ideology = ascension.getIdeology(player);
            if (ideology != null) {
                player.sendMessage(ideology.getChatColor() + "§lYour " + ideology.getDisplayName()
                        + " powers still course through you.");
            }
        }
    }
}
