package com.townyelections.legends.listener;

import com.townyelections.TownyElections;
import com.townyelections.legends.engine.ProphecyEngine;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

/**
 * Continuously enforces prophecy effects on the world. Some prophecies
 * (like sky colour changes) require ongoing packet-level enforcement.
 * This listener handles both login-time application and periodic refreshing.
 */
public class ProphecyExecutionListener implements Listener {

    private final TownyElections plugin;
    private final ProphecyEngine prophecyEngine;
    private BukkitTask continuousTask;
    private final Random random = new Random();

    // Which continuous effects are active
    private boolean skyRedActive;
    private boolean gravityInverted;

    public ProphecyExecutionListener(TownyElections plugin, ProphecyEngine prophecyEngine) {
        this.plugin = plugin;
        this.prophecyEngine = prophecyEngine;
    }

    /**
     * Start continuous prophecy enforcement. Called when prophecies are executed.
     */
    public void startContinuousEffects() {
        if (continuousTask != null) {
            continuousTask.cancel();
        }

        // Determine which effects are active based on executed prophecies
        for (ProphecyEngine.Prophecy prophecy : prophecyEngine.getActiveProphecies()) {
            switch (prophecy.id()) {
                case "SKY_TURNS_RED" -> skyRedActive = true;
                case "GRAVITY_INVERTS" -> gravityInverted = true;
            }
        }

        // Periodic enforcement
        continuousTask = Bukkit.getScheduler().runTaskTimer(plugin, this::enforceContinuousEffects, 20L, 100L);
    }

    private void enforceContinuousEffects() {
        if (!skyRedActive && !gravityInverted) return;

        for (World world : Bukkit.getWorlds()) {
            for (Player player : world.getPlayers()) {
                if (skyRedActive) {
                    // Spawn red particles overhead
                    world.spawnParticle(Particle.REDSTONE, player.getLocation().add(0, 30, 0),
                            5, 10, 5, 10, new Particle.DustOptions(org.bukkit.Color.RED, 2f));
                }
                if (gravityInverted && random.nextDouble() < 0.01) {
                    // Brief levitation pulses
                    player.addPotionEffect(
                            new org.bukkit.potion.PotionEffect(
                                    org.bukkit.potion.PotionEffectType.LEVITATION, 20, 0, true, false, true));
                }
            }
        }
    }

    /** Stop all continuous effects. */
    public void stopContinuousEffects() {
        if (continuousTask != null) {
            continuousTask.cancel();
            continuousTask = null;
        }
        skyRedActive = false;
        gravityInverted = false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Notify joining players of active prophecies
        if (!prophecyEngine.getActiveProphecies().isEmpty()) {
            event.getPlayer().sendMessage("§5§lThe prophecies are still unfolding across the land...");
            event.getPlayer().sendMessage("§7" + prophecyEngine.getActiveProphecies().size()
                    + " prophecies remain active.");
        }
    }
}
