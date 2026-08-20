package com.townyelections.manager;

import com.townyelections.TownyElections;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves the configurable sub-command literals (from {@code config.yml}'s
 * {@code commands} section) and lets the dispatcher map a typed literal back to
 * its internal action key.
 */
public class CommandConfig {

    /** Internal action keys. */
    public static final String RUN = "run";
    public static final String WITHDRAW = "withdraw";
    public static final String CAMPAIGN = "campaign";
    public static final String PARTY = "party";
    public static final String PARTIES = "parties";
    public static final String VOTE = "vote";
    public static final String STATUS = "status";
    public static final String CANDIDATES = "candidates";
    public static final String RESULTS = "results";
    public static final String START = "start";
    public static final String STOP = "stop";
    public static final String CANCEL = "cancel";
    public static final String RELOAD = "reload";
    public static final String HELP = "help";

    private static final String[] ACTIONS = {
            RUN, WITHDRAW, CAMPAIGN, PARTY, PARTIES, VOTE, STATUS, CANDIDATES,
            RESULTS, START, STOP, CANCEL, RELOAD, HELP
    };

    private final TownyElections plugin;

    /** action -> configured literal */
    private final Map<String, String> literals = new LinkedHashMap<>();
    /** lower-cased literal -> action */
    private final Map<String, String> reverse = new LinkedHashMap<>();

    public CommandConfig(TownyElections plugin) {
        this.plugin = plugin;
    }

    public void load() {
        literals.clear();
        reverse.clear();
        FileConfiguration c = plugin.getConfig();
        for (String action : ACTIONS) {
            String literal = c.getString("commands." + action, action);
            literals.put(action, literal);
            reverse.put(literal.toLowerCase(Locale.ROOT), action);
        }
    }

    /** The literal players type for a given action. */
    public String literal(String action) {
        return literals.getOrDefault(action, action);
    }

    /** Map a typed literal back to its action key, or null if unrecognised. */
    public String actionFor(String typed) {
        if (typed == null) {
            return null;
        }
        return reverse.get(typed.toLowerCase(Locale.ROOT));
    }

    public Map<String, String> getLiterals() {
        return literals;
    }

    public String[] getActions() {
        return ACTIONS;
    }
}
