package com.townyelections.legends;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Set;

/**
 * The four primordial ideologies that vie for control of the server's reality
 * during an Election of Legends. Each ideology carries a complete blueprint for
 * how the server transforms when it wins: physics overrides, boss mechanics,
 * dimension unlocks, ascension powers, curses, and prophecy affinities.
 *
 * <p>These are the soul of the system — every other class reads from here to
 * determine behaviour. Adding a new ideology means adding a new constant here
 * and implementing its boss subclass.
 */
public enum Ideology {

    WARMONGER(
            "Warmonger",
            Material.NETHERITE_SWORD,
            ChatColor.RED,
            Color.RED,
            Particle.FLAME,
            "§c§lWAR IS THE ANSWER",
            """
                    The Warmongers believe that strength alone determines worth.
                    Under their rule the world hardens: gravity crushes harder,
                    mobs grow fierce, and the Nether bleeds into the overworld.
                    Only the strong survive — and they thrive.""",
            // Prophecy affinities — prophecy outcomes that favour this ideology
            Set.of("SKY_TURNS_RED", "GRAVITY_INVERTS", "MOB_INVASION", "PILLAR_OF_FIRE"),
            // Ascension powers
            List.of("LIGHTNING_STRIKE", "MOB_COMMAND", "BLOOD_RAGE", "FEAR_AURA"),
            // Curse on losers
            "WEAKNESS",
            // Dimension name
            "crimson_wasteland",
            PotionEffectType.STRENGTH
    ),

    BUILDER(
            "Builder",
            Material.DIAMOND_PICKAXE,
            ChatColor.AQUA,
            Color.AQUA,
            Particle.HAPPY_VILLAGER,
            "§b§lBUILD A BETTER WORLD",
            """
                    The Builders see creation as the highest calling. When they
                    prevail, the world itself becomes their canvas: blocks break
                    and place faster, random structures sprout from the earth,
                    and gravity itself becomes optional. For them, every sunrise
                    is a new blueprint.""",
            Set.of("TOOLS_GAIN_SENTIENCE", "CAVE_COLLAPSE", "FLOATING_ISLANDS", "ANVIL_RAIN"),
            List.of("INSTANT_STRUCTURE", "TERRAFORM", "FORCEFIELD", "BLOCK_REGENERATION"),
            "HUMILIATION",
            "architect_vault",
            PotionEffectType.HASTE
    ),

    MERCHANT(
            "Merchant",
            Material.EMERALD,
            ChatColor.GOLD,
            Color.YELLOW,
            Particle.HAPPY_VILLAGER,
            "§e§lPROSPERITY THROUGH TRADE",
            """
                    The Merchants know that gold greases every wheel. Their
                    victory brings boundless prosperity: crops grow three times
                    faster, mobs drop twice the loot, and PvP fades as everyone
                    rushes to trade. In their world, wealth is the highest
                    virtue and poverty the only crime.""",
            Set.of("TOOLS_GAIN_SENTIENCE", "MIDAS_TOUCH", "GOLDEN_RAIN", "GOLEM_UPRISING"),
            List.of("SHOP_TELEPORT", "INFINITE_CURRENCY", "TRADE_WINDS", "GOLEM_BODYGUARD"),
            "BANKRUPTCY",
            "golden_realm",
            PotionEffectType.LUCK
    ),

    MYSTIC(
            "Mystic",
            Material.AMETHYST_CLUSTER,
            ChatColor.DARK_PURPLE,
            Color.PURPLE,
            Particle.SPELL_WITCH,
            "§5§lUNRAVEL THE MYSTERIES",
            """
                    The Mystics seek truth beyond the material plane. When they
                    ascend, reality itself grows thin: magical mobs swarm the
                    land, potions last twice as long, and random teleportation
                    becomes commonplace. Under their sway, the arcane is
                    everyday and the mundane becomes magical.""",
            Set.of("SKY_TURNS_RED", "GRAVITY_INVERTS", "ENDER_RIFT", "ARCANE_STORM"),
            List.of("FLIGHT", "PORTAL_CREATION", "TELEPORT_DASH", "MAGIC_SHIELD"),
            "ISOLATION",
            "arcane_nexus",
            PotionEffectType.REGENERATION
    );

    // ---- Fields ------------------------------------------------------------

    private final String displayName;
    private final Material icon;
    private final ChatColor chatColor;
    private final Color color;
    private final Particle particle;
    private final String slogan;
    private final String lore;
    private final Set<String> prophecyAffinities;
    private final List<String> ascensionPowers;
    private final String curseType;
    private final String dimensionName;
    private final PotionEffectType bossPotionAura;

    Ideology(String displayName, Material icon, ChatColor chatColor, Color color,
             Particle particle, String slogan, String lore,
             Set<String> prophecyAffinities, List<String> ascensionPowers,
             String curseType, String dimensionName, PotionEffectType bossPotionAura) {
        this.displayName = displayName;
        this.icon = icon;
        this.chatColor = chatColor;
        this.color = color;
        this.particle = particle;
        this.slogan = slogan;
        this.lore = lore;
        this.prophecyAffinities = prophecyAffinities;
        this.ascensionPowers = ascensionPowers;
        this.curseType = curseType;
        this.dimensionName = dimensionName;
        this.bossPotionAura = bossPotionAura;
    }

    // ---- Accessors ---------------------------------------------------------

    /** Human-readable name, e.g. "Warmonger". */
    public String getDisplayName() {
        return displayName;
    }

    /** Icon material for GUIs and monuments. */
    public Material getIcon() {
        return icon;
    }

    /** Chat colour for text formatting. */
    public ChatColor getChatColor() {
        return chatColor;
    }

    /** Bukkit colour for particles and sky tint. */
    public Color getColor() {
        return color;
    }

    /** Default particle effect for this ideology. */
    public Particle getParticle() {
        return particle;
    }

    /** Short battle-cry shown during the finale. */
    public String getSlogan() {
        return slogan;
    }

    /** Multi-line flavour text explaining the ideology. */
    public String getLore() {
        return lore;
    }

    /**
     * Prophecy outcome keys that this ideology is "attuned" to. When this
     * ideology wins, the prophecy engine prefers outcomes from this set.
     */
    public Set<String> getProphecyAffinities() {
        return prophecyAffinities;
    }

    /** Ascension power IDs granted to the winning mayor. */
    public List<String> getAscensionPowers() {
        return ascensionPowers;
    }

    /** Curse type ID applied to losing candidates. */
    public String getCurseType() {
        return curseType;
    }

    /** The world name for this ideology's custom dimension. */
    public String getDimensionName() {
        return dimensionName;
    }

    /** Boss aura potion effect applied to nearby entities. */
    public PotionEffectType getBossPotionAura() {
        return bossPotionAura;
    }

    // ---- Lookup ------------------------------------------------------------

    /**
     * Case-insensitive lookup by display name or enum constant name.
     *
     * @param name the user-supplied string
     * @return the matching ideology, or null
     */
    public static Ideology fromString(String name) {
        if (name == null) {
            return null;
        }
        for (Ideology i : values()) {
            if (i.displayName.equalsIgnoreCase(name) || i.name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return null;
    }
}
