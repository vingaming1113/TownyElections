package com.townyelections.legends.system;

import com.townyelections.TownyElections;
import com.townyelections.legends.Ideology;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applies permanent curses to losing candidates after the Election of Legends.
 * Curses are ideology-specific and persist until the next legends election or
 * until manually revoked. Each curse alters gameplay in significant ways.
 *
 * <p>Curse types:
 * <ul>
 *   <li>WEAKNESS — all damage reduced by 50%</li>
 *   <li>HUMILIATION — custom display name showing "ELECTION LOSER"</li>
 *   <li>BANKRUPTCY — drop 50% of inventory items on death</li>
 *   <li>ISOLATION — can't open chests, chat restrictions</li>
 * </ul>
 */
public class CursesSystem {

    private final TownyElections plugin;

    /** player UUID -> curse type */
    private final Map<UUID, String> activeCurses = new HashMap<>();
    /** player UUID -> epoch millis when curse expires */
    private final Map<UUID, Long> curseExpiry = new HashMap<>();

    public CursesSystem(TownyElections plugin) {
        this.plugin = plugin;
    }

    // ========================================================================
    //  Public API
    // ========================================================================

    /**
     * Apply a curse to a losing candidate.
     *
     * @param player     the losing candidate
     * @param ideology   the winning ideology (determines curse type)
     * @param durationMs how long the curse lasts
     */
    public void applyCurse(Player player, Ideology ideology, long durationMs) {
        String curseType = ideology.getCurseType();
        activeCurses.put(player.getUniqueId(), curseType);
        curseExpiry.put(player.getUniqueId(), System.currentTimeMillis() + durationMs);

        // Dramatic curse effect
        player.getWorld().strikeLightningEffect(player.getLocation());
        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0, 1, 0),
                50, 0.5, 1, 0.5, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);

        switch (curseType) {
            case "WEAKNESS" -> applyWeaknessCurse(player);
            case "HUMILIATION" -> applyHumiliationCurse(player);
            case "BANKRUPTCY" -> applyBankruptcyCurse(player);
            case "ISOLATION" -> applyIsolationCurse(player);
        }

        Bukkit.broadcastMessage("§8§l☠ " + player.getName() + " has been cursed with §c"
                + curseType + "§8§l by the " + ideology.getDisplayName() + " victory!");
    }

    private void applyWeaknessCurse(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                Integer.MAX_VALUE, 1, true, false, true));
        player.sendMessage("§c§l☠ CURSE OF WEAKNESS: Your damage is halved for 30 days.");
    }

    private void applyHumiliationCurse(Player player) {
        player.setDisplayName("§7§k||§c ELECTION LOSER §7§k||§r §7" + player.getName());
        player.setPlayerListName("§c§lL §7" + player.getName());
        player.sendMessage("§c§l☠ CURSE OF HUMILIATION: All shall know of your defeat.");
    }

    private void applyBankruptcyCurse(Player player) {
        player.sendMessage("§c§l☠ CURSE OF BANKRUPTCY: You will drop half your items on death.");
    }

    private void applyIsolationCurse(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                Integer.MAX_VALUE, 0, true, false, true));
        player.sendMessage("§c§l☠ CURSE OF ISOLATION: You are cut off from the world.");
    }

    /** Remove all curses from a player. */
    public void revokeCurse(Player player) {
        String curseType = activeCurses.remove(player.getUniqueId());
        curseExpiry.remove(player.getUniqueId());

        if (curseType != null) {
            // Revert curse effects
            switch (curseType) {
                case "WEAKNESS" -> player.removePotionEffect(PotionEffectType.WEAKNESS);
                case "HUMILIATION" -> {
                    player.setDisplayName(player.getName());
                    player.setPlayerListName(player.getName());
                }
                case "BANKRUPTCY" -> {} // no persistent effect to remove
                case "ISOLATION" -> player.removePotionEffect(PotionEffectType.BLINDNESS);
            }
            player.sendMessage("§a§l✨ Your curse has been lifted!");
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0),
                    30, 0.5, 1, 0.5, 0.05);
        }
    }

    /** Revoke all active curses (called before new legends election). */
    public void revokeAll() {
        for (UUID uuid : activeCurses.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                revokeCurse(player);
            }
        }
        activeCurses.clear();
        curseExpiry.clear();
    }

    /** Check if a player is currently cursed. */
    public boolean isCursed(Player player) {
        return activeCurses.containsKey(player.getUniqueId())
                && curseExpiry.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    /** Get the curse type for a player, or null. */
    public String getCurseType(Player player) {
        return activeCurses.get(player.getUniqueId());
    }
}
