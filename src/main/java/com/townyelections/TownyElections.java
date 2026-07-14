package com.townyelections;

import com.townyelections.commands.ElectionCommand;
import com.townyelections.gui.ElectionMenu;
import com.townyelections.integration.ElectionsPlaceholderExpansion;
import com.townyelections.integration.TownyHook;
import com.townyelections.legends.ElectionOfLegendsManager;
import com.townyelections.listeners.PlayerListener;
import com.townyelections.manager.CommandConfig;
import com.townyelections.manager.ConfigManager;
import com.townyelections.manager.ElectionManager;
import com.townyelections.manager.MessageManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * TownyElections - a formal, configurable election system for Towny towns.
 *
 * <p>Entry point: wires up managers, registers commands/listeners, and drives
 * the election state machine via a repeating scheduler task.
 */
public class TownyElections extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 32328; // placeholder id

    private ConfigManager configManager;
    private CommandConfig commandConfig;
    private MessageManager messageManager;
    private TownyHook townyHook;
    private ElectionManager electionManager;
    private ElectionMenu electionMenu;
    private ElectionOfLegendsManager legendsManager;

    private BukkitTask tickTask;

    @Override
    public void onEnable() {
        // Hard dependency check.
        if (Bukkit.getPluginManager().getPlugin("Towny") == null) {
            getLogger().severe("Towny is not installed! TownyElections requires Towny. Disabling.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Configuration & messages.
        configManager = new ConfigManager(this);
        configManager.load();

        commandConfig = new CommandConfig(this);
        commandConfig.load();

        messageManager = new MessageManager(this);
        messageManager.load(configManager.getLocale());

        // Towny bridge.
        townyHook = new TownyHook(this);

        // Core manager and persisted state.
        electionManager = new ElectionManager(this);
        electionManager.load();

        // Commands.
        electionMenu = new ElectionMenu(this);
        PluginCommand command = getCommand("election");
        if (command != null) {
            ElectionCommand executor = new ElectionCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("Command 'election' is not defined in plugin.yml!");
        }

        // Listeners.
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(electionMenu, this);

        // State-machine tick: every 20 ticks (~1 second) is plenty for durations
        // measured in minutes/days and keeps timing responsive for admins.
        tickTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                electionManager.tick();
            } catch (Throwable t) {
                getLogger().warning("Error during election tick: " + t.getMessage());
                if (configManager.isDebug()) {
                    t.printStackTrace();
                }
            }
        }, 100L, 20L);

        // Election of Legends (reality-warping event system).
        legendsManager = new ElectionOfLegendsManager(this);
        legendsManager.load();

        // Optional integrations.
        hookPlaceholderAPI();
        setupMetrics();

        getLogger().info("TownyElections enabled. Managing " + electionManager.getActiveElections().size()
                + " active election(s).");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (legendsManager != null) {
            legendsManager.shutdown();
        }
        if (electionManager != null) {
            electionManager.save();
        }
        getLogger().info("TownyElections disabled.");
    }

    /** Reload configuration, command literals, and messages at runtime. */
    public void reloadAll() {
        configManager.load();
        commandConfig.load();
        messageManager.load(configManager.getLocale());
    }

    private void hookPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new ElectionsPlaceholderExpansion(this).register();
                getLogger().info("Hooked into PlaceholderAPI.");
            } catch (Throwable t) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
            }
        }
    }

    private void setupMetrics() {
        if (!configManager.isMetrics()) {
            return;
        }
        try {
            new Metrics(this, BSTATS_PLUGIN_ID);
        } catch (Throwable t) {
            // Metrics are best-effort; never let them break startup.
            if (configManager.isDebug()) {
                getLogger().warning("Could not start metrics: " + t.getMessage());
            }
        }
    }

    // ---- Accessors ---------------------------------------------------------

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CommandConfig getCommandConfig() {
        return commandConfig;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public TownyHook getTownyHook() {
        return townyHook;
    }

    public ElectionManager getElectionManager() {
        return electionManager;
    }

    public ElectionMenu getElectionMenu() {
        return electionMenu;
    }

    public ElectionOfLegendsManager getLegendsManager() {
        return legendsManager;
    }
}
