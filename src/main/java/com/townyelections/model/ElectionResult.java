package com.townyelections.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An immutable snapshot of a concluded election, kept for history and the
 * {@code /election results} command.
 */
public class ElectionResult {

    /** A single candidate's standing in the final tally. */
    public static final class Standing {
        public final UUID uuid;
        public final String name;
        public final String partyName;
        public final int votes;

        public Standing(UUID uuid, String name, String partyName, int votes) {
            this.uuid = uuid;
            this.name = name;
            this.partyName = partyName;
            this.votes = votes;
        }

        public Standing(UUID uuid, String name, int votes) {
            this(uuid, name, "Independent", votes);
        }
    }

    private final UUID townUuid;
    private final String townName;
    private final UUID winnerUuid;
    private final String winnerName;
    private final int winnerVotes;
    private final int totalVotes;
    private final int residentCount;
    private final long concludedAt;
    private final List<Standing> standings;

    public ElectionResult(UUID townUuid, String townName, UUID winnerUuid, String winnerName,
                          int winnerVotes, int totalVotes, int residentCount, long concludedAt,
                          List<Standing> standings) {
        this.townUuid = townUuid;
        this.townName = townName;
        this.winnerUuid = winnerUuid;
        this.winnerName = winnerName;
        this.winnerVotes = winnerVotes;
        this.totalVotes = totalVotes;
        this.residentCount = residentCount;
        this.concludedAt = concludedAt;
        this.standings = standings;
    }

    public UUID getTownUuid() {
        return townUuid;
    }

    public String getTownName() {
        return townName;
    }

    public UUID getWinnerUuid() {
        return winnerUuid;
    }

    public String getWinnerName() {
        return winnerName;
    }

    public boolean hasWinner() {
        return winnerUuid != null;
    }

    public int getWinnerVotes() {
        return winnerVotes;
    }

    public int getTotalVotes() {
        return totalVotes;
    }

    public int getResidentCount() {
        return residentCount;
    }

    public long getConcludedAt() {
        return concludedAt;
    }

    public List<Standing> getStandings() {
        return standings;
    }

    // ---- Serialization -----------------------------------------------------

    public void serialize(ConfigurationSection section) {
        section.set("town-uuid", townUuid.toString());
        section.set("town-name", townName);
        section.set("winner-uuid", winnerUuid == null ? null : winnerUuid.toString());
        section.set("winner-name", winnerName);
        section.set("winner-votes", winnerVotes);
        section.set("total-votes", totalVotes);
        section.set("resident-count", residentCount);
        section.set("concluded-at", concludedAt);

        ConfigurationSection standingsSection = section.createSection("standings");
        int i = 0;
        for (Standing standing : standings) {
            ConfigurationSection s = standingsSection.createSection("s" + (i++));
            s.set("uuid", standing.uuid.toString());
            s.set("name", standing.name);
            s.set("party-name", standing.partyName);
            s.set("votes", standing.votes);
        }
    }

    public static ElectionResult deserialize(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String rawTown = section.getString("town-uuid");
        if (rawTown == null) {
            return null;
        }
        UUID townUuid;
        try {
            townUuid = UUID.fromString(rawTown);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        String townName = section.getString("town-name", "Unknown");
        UUID winnerUuid = null;
        String rawWinner = section.getString("winner-uuid");
        if (rawWinner != null) {
            try {
                winnerUuid = UUID.fromString(rawWinner);
            } catch (IllegalArgumentException ignored) {
                winnerUuid = null;
            }
        }
        String winnerName = section.getString("winner-name");
        int winnerVotes = section.getInt("winner-votes", 0);
        int totalVotes = section.getInt("total-votes", 0);
        int residentCount = section.getInt("resident-count", 0);
        long concludedAt = section.getLong("concluded-at", System.currentTimeMillis());

        List<Standing> standings = new ArrayList<>();
        ConfigurationSection standingsSection = section.getConfigurationSection("standings");
        if (standingsSection != null) {
            // Preserve order using a linked map keyed by section name.
            Map<String, ConfigurationSection> ordered = new LinkedHashMap<>();
            for (String key : standingsSection.getKeys(false)) {
                ordered.put(key, standingsSection.getConfigurationSection(key));
            }
            for (ConfigurationSection s : ordered.values()) {
                if (s == null) {
                    continue;
                }
                try {
                    UUID uuid = UUID.fromString(s.getString("uuid", ""));
                    standings.add(new Standing(uuid, s.getString("name", "Unknown"),
                            s.getString("party-name", "Independent"), s.getInt("votes", 0)));
                } catch (IllegalArgumentException ignored) {
                    // skip
                }
            }
        }

        return new ElectionResult(townUuid, townName, winnerUuid, winnerName, winnerVotes,
                totalVotes, residentCount, concludedAt, standings);
    }
}
