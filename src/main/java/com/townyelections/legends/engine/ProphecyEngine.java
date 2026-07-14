package com.townyelections.legends.engine;

import com.townyelections.TownyElections;
import com.townyelections.legends.Ideology;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The ProphecyEngine generates mystical predictions before the Election of
 * Legends begins, then executes them in dramatic fashion after the winner is
 * determined. Prophecies are themed around the winning ideology's affinities.
 *
 * <p>Each prophecy has a name, a description, and a runnable effect that
 * manifests in the world. Effects include sky colour changes, gravity flips,
 * mob invasions, floating islands, etc.
 */
public class ProphecyEngine {

    private final TownyElections plugin;
    private final Random random = new Random();

    /** All available prophecy definitions. */
    private final List<Prophecy> prophecyLibrary = new ArrayList<>();

    /** Prophecies selected for the current election. */
    private final List<Prophecy> activeProphecies = new ArrayList<>();

    /** Task that executes prophecies one by one. */
    private BukkitTask executionTask;

    public ProphecyEngine(TownyElections plugin) {
        this.plugin = plugin;
        buildProphecyLibrary();
    }

    // ========================================================================
    //  Prophecy definitions
    // ========================================================================

    private void buildProphecyLibrary() {
        prophecyLibrary.add(new Prophecy("SKY_TURNS_RED", "The sky shall bleed crimson, and day shall know no dawn.",
                this::executeSkyTurnsRed));
        prophecyLibrary.add(new Prophecy("GRAVITY_INVERTS", "The ground shall reject all who walk upon it.",
                this::executeGravityInverts));
        prophecyLibrary.add(new Prophecy("TOOLS_GAIN_SENTIENCE", "That which was forged shall rise against its master.",
                this::executeToolsGainSentience));
        prophecyLibrary.add(new Prophecy("MOB_INVASION", "The beasts of the dark shall swarm the light.",
                this::executeMobInvasion));
        prophecyLibrary.add(new Prophecy("PILLAR_OF_FIRE", "A pillar of flame shall mark the seat of power.",
                this::executePillarOfFire));
        prophecyLibrary.add(new Prophecy("CAVE_COLLAPSE", "The deep places shall groan and give way.",
                this::executeCaveCollapse));
        prophecyLibrary.add(new Prophecy("FLOATING_ISLANDS", "Chunks of earth shall defy gravity forevermore.",
                this::executeFloatingIslands));
        prophecyLibrary.add(new Prophecy("ANVIL_RAIN", "Iron from the heavens shall batter the unworthy.",
                this::executeAnvilRain));
        prophecyLibrary.add(new Prophecy("MIDAS_TOUCH", "All that glitters is gold, and all shall glitter.",
                this::executeMidasTouch));
        prophecyLibrary.add(new Prophecy("GOLDEN_RAIN", "Wealth shall fall from the skies like gentle rain.",
                this::executeGoldenRain));
        prophecyLibrary.add(new Prophecy("GOLEM_UPRISING", "The protectors shall become the hunted.",
                this::executeGolemUprising));
        prophecyLibrary.add(new Prophecy("ENDER_RIFT", "A wound in reality shall tear open above the land.",
                this::executeEnderRift));
        prophecyLibrary.add(new Prophecy("ARCANE_STORM", "Raw magic shall fall as rain, warping all it touches.",
                this::executeArcaneStorm));
    }

    // ========================================================================
    //  Public API
    // ========================================================================

    /**
     * Select a set of prophecies for the upcoming election. When an ideology is
     * provided, prophecies matching its affinities are preferred.
     *
     * @param count    number of prophecies to select
     * @param ideology the winning ideology (may be null during pre-election)
     * @return the list of selected prophecies (also stored internally)
     */
    public List<Prophecy> selectProphecies(int count, Ideology ideology) {
        activeProphecies.clear();

        List<Prophecy> pool = new ArrayList<>(prophecyLibrary);
        Collections.shuffle(pool, random);

        // Prioritise ideology-affinity prophecies.
        if (ideology != null) {
            List<Prophecy> affinities = new ArrayList<>();
            List<Prophecy> others = new ArrayList<>();
            for (Prophecy p : pool) {
                if (ideology.getProphecyAffinities().contains(p.id)) {
                    affinities.add(p);
                } else {
                    others.add(p);
                }
            }
            pool.clear();
            pool.addAll(affinities);
            pool.addAll(others);
        }

        for (int i = 0; i < Math.min(count, pool.size()); i++) {
            activeProphecies.add(pool.get(i));
        }
        return List.copyOf(activeProphecies);
    }

    /**
     * Execute all active prophecies in sequence, with a delay between each.
     *
     * @param delayBetweenSeconds seconds between prophecy executions
     */
    public void executeProphecies(long delayBetweenSeconds) {
        if (activeProphecies.isEmpty()) {
            return;
        }
        executeNext(0, delayBetweenSeconds);
    }

    private void executeNext(int index, long delaySeconds) {
        if (index >= activeProphecies.size()) {
            executionTask = null;
            return;
        }
        Prophecy prophecy = activeProphecies.get(index);
        Bukkit.broadcastMessage("§5§l[PROPHECY " + (index + 1) + "/" + activeProphecies.size() + "] §d\""
                + prophecy.description + "\"");
        try {
            prophecy.effect.run();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to execute prophecy '" + prophecy.id + "': " + e.getMessage());
        }
        executionTask = Bukkit.getScheduler().runTaskLater(plugin,
                () -> executeNext(index + 1, delaySeconds), delaySeconds * 20L);
    }

    /** Cancel any in-progress prophecy execution. */
    public void cancel() {
        if (executionTask != null) {
            executionTask.cancel();
            executionTask = null;
        }
        activeProphecies.clear();
    }

    /** @return the list of currently active prophecies */
    public List<Prophecy> getActiveProphecies() {
        return List.copyOf(activeProphecies);
    }

    // ========================================================================
    //  Prophecy effects (runnables)
    // ========================================================================

    private void executeSkyTurnsRed() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§c§lTHE SKY BLEEDS RED", "§7A prophecy fulfilled...", 10, 80, 20);
        }
        // Spawn red particles across all worlds
        for (World world : Bukkit.getWorlds()) {
            for (Player p : world.getPlayers()) {
                Location loc = p.getLocation();
                for (int i = 0; i < 100; i++) {
                    world.spawnParticle(Particle.REDSTONE, loc.clone().add(
                                    (Math.random() - 0.5) * 50, Math.random() * 30, (Math.random() - 0.5) * 50),
                            1, new Particle.DustOptions(Color.RED, 3f));
                }
            }
        }
    }

    private void executeGravityInverts() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§5§lGRAVITY INVERTS", "§7Hold onto something...", 10, 60, 20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 100, 0, true, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, true, false, true));
        }
    }

    private void executeToolsGainSentience() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§6§lTOOLS AWAKEN", "§7They remember every swing...", 10, 60, 20);
            Location loc = player.getLocation();
            // Spawn a silverfish swarm (representing animated tools)
            for (int i = 0; i < 5; i++) {
                player.getWorld().spawnEntity(loc.clone().add(
                        (Math.random() - 0.5) * 5, 1, (Math.random() - 0.5) * 5), EntityType.SILVERFISH);
            }
        }
    }

    private void executeMobInvasion() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§4§lTHE HORDE COMES", "§7Monsters pour from the darkness...", 10, 80, 20);
            Location loc = player.getLocation();
            for (int i = 0; i < 8; i++) {
                EntityType type = switch (random.nextInt(4)) {
                    case 0 -> EntityType.ZOMBIE;
                    case 1 -> EntityType.SKELETON;
                    case 2 -> EntityType.SPIDER;
                    default -> EntityType.CREEPER;
                };
                player.getWorld().spawnEntity(loc.clone().add(
                        (Math.random() - 0.5) * 20, 0, (Math.random() - 0.5) * 20), type);
            }
        }
    }

    private void executePillarOfFire() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§c§lPILLAR OF FIRE", "§7The land itself ignites...", 10, 100, 20);
        }
        // Spawn a massive fire pillar at each player's location
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location base = player.getLocation().clone();
            for (int y = 0; y < 30; y++) {
                base.add(0, 1, 0);
                player.getWorld().spawnParticle(Particle.FLAME, base, 3, 0.3, 0, 0.3, 0.02);
                player.getWorld().spawnParticle(Particle.LAVA, base, 1, 0.1, 0, 0.1, 0);
            }
        }
    }

    private void executeCaveCollapse() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§8§lTHE EARTH GROANS", "§7Caves collapse across the land...", 10, 60, 20);
            player.getWorld().spawnParticle(Particle.BLOCK_CRACK, player.getLocation(),
                    200, 5, 5, 5, Material.STONE.createBlockData());
        }
    }

    private void executeFloatingIslands() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§b§lISLANDS RISE", "§7The earth defies gravity...", 10, 80, 20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 2, true, false, true));
        }
    }

    private void executeAnvilRain() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§7§lANVIL RAIN", "§7Take cover!", 10, 40, 10);
            Location loc = player.getLocation();
            for (int i = 0; i < 10; i++) {
                player.getWorld().spawnFallingBlock(loc.clone().add(
                        (Math.random() - 0.5) * 20, 15, (Math.random() - 0.5) * 20),
                        Material.ANVIL.createBlockData());
            }
        }
    }

    private void executeMidasTouch() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§e§lMIDAS TOUCH", "§7Everything turns to gold...", 10, 60, 20);
        }
        // Spawn gold particles everywhere
        for (World world : Bukkit.getWorlds()) {
            for (Player p : world.getPlayers()) {
                world.spawnParticle(Particle.REDSTONE, p.getLocation(), 150, 10, 10, 10,
                        new Particle.DustOptions(Color.YELLOW, 2f));
            }
        }
    }

    private void executeGoldenRain() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§e§lGOLDEN RAIN", "§7Wealth falls from above...", 10, 80, 20);
            Location loc = player.getLocation();
            for (int i = 0; i < 15; i++) {
                player.getWorld().dropItemNaturally(loc.clone().add(
                        (Math.random() - 0.5) * 15, 10, (Math.random() - 0.5) * 15),
                        new org.bukkit.inventory.ItemStack(Material.GOLD_NUGGET, random.nextInt(3) + 1));
            }
        }
    }

    private void executeGolemUprising() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§6§lGOLEM UPRISING", "§7The iron protectors march...", 10, 60, 20);
            Location loc = player.getLocation();
            for (int i = 0; i < 3; i++) {
                player.getWorld().spawnEntity(loc.clone().add(
                        (Math.random() - 0.5) * 15, 0, (Math.random() - 0.5) * 15), EntityType.IRON_GOLEM);
            }
        }
    }

    private void executeEnderRift() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§5§lENDER RIFT", "§7Reality tears open...", 10, 100, 20);
            Location loc = player.getLocation();
            for (int i = 0; i < 50; i++) {
                player.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(
                        (Math.random() - 0.5) * 30, Math.random() * 20, (Math.random() - 0.5) * 30), 5, 0.5, 0.5, 0.5, 0);
            }
            // Spawn endermen
            for (int i = 0; i < 5; i++) {
                player.getWorld().spawnEntity(loc.clone().add(
                        (Math.random() - 0.5) * 10, 0, (Math.random() - 0.5) * 10), EntityType.ENDERMAN);
            }
        }
    }

    private void executeArcaneStorm() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§d§lARCANE STORM", "§7Magic rains from the sky...", 10, 80, 20);
            // Spawn spell particles
            for (int i = 0; i < 100; i++) {
                player.getWorld().spawnParticle(Particle.SPELL_WITCH, player.getLocation().add(
                        (Math.random() - 0.5) * 40, Math.random() * 20, (Math.random() - 0.5) * 40), 3, 1, 1, 1, 0);
            }
            // Random potion effects
            PotionEffectType[] effects = {PotionEffectType.SPEED, PotionEffectType.JUMP_BOOST,
                    PotionEffectType.INVISIBILITY, PotionEffectType.GLOWING};
            player.addPotionEffect(new PotionEffect(
                    effects[random.nextInt(effects.length)], 200, random.nextInt(2), true, false, true));
        }
    }

    // ========================================================================
    //  Prophecy record
    // ========================================================================

    /**
     * A single prophecy: an internal ID, a flavour-text description, and a
     * runnable that executes the prophecy's effects in the world.
     */
    public record Prophecy(String id, String description, Runnable effect) {
    }
}
