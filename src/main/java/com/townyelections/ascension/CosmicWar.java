package com.townyelections.ascension;

import org.bukkit.configuration.ConfigurationSection;

import java.util.UUID;

/**
 * Represents a conflict between two ascending players.
 * Winner gains 30% of loser's AP.
 */
public class CosmicWar {
    private final UUID player1Uuid;
    private final String player1Name;
    private final UUID player2Uuid;
    private final String player2Name;
    private final long startedAt;
    private final long endsAt;
    private UUID winnerUuid; // null if still active

    public CosmicWar(UUID player1Uuid, String player1Name, UUID player2Uuid, String player2Name, long durationMs) {
        this.player1Uuid = player1Uuid;
        this.player1Name = player1Name;
        this.player2Uuid = player2Uuid;
        this.player2Name = player2Name;
        this.startedAt = System.currentTimeMillis();
        this.endsAt = startedAt + durationMs;
        this.winnerUuid = null;
    }

    public UUID getPlayer1Uuid() {
        return player1Uuid;
    }

    public String getPlayer1Name() {
        return player1Name;
    }

    public UUID getPlayer2Uuid() {
        return player2Uuid;
    }

    public String getPlayer2Name() {
        return player2Name;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getEndsAt() {
        return endsAt;
    }

    public UUID getWinnerUuid() {
        return winnerUuid;
    }

    public void setWinnerUuid(UUID winner) {
        this.winnerUuid = winner;
    }

    public boolean isActive() {
        return winnerUuid == null && System.currentTimeMillis() < endsAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= endsAt;
    }

    public void serialize(ConfigurationSection section) {
        section.set("player1-uuid", player1Uuid.toString());
        section.set("player1-name", player1Name);
        section.set("player2-uuid", player2Uuid.toString());
        section.set("player2-name", player2Name);
        section.set("started-at", startedAt);
        section.set("ends-at", endsAt);
        section.set("winner-uuid", winnerUuid == null ? null : winnerUuid.toString());
    }

    public static CosmicWar deserialize(ConfigurationSection section) {
        if (section == null) return null;
        try {
            UUID p1 = UUID.fromString(section.getString("player1-uuid"));
            String p1Name = section.getString("player1-name", "Unknown");
            UUID p2 = UUID.fromString(section.getString("player2-uuid"));
            String p2Name = section.getString("player2-name", "Unknown");
            long startedAt = section.getLong("started-at", 0);
            long endsAt = section.getLong("ends-at", 0);
            long durationMs = Math.max(0, endAt - startedAt);
            CosmicWar war = new CosmicWar(p1, p1Name, p2, p2Name, durationMs);
            String rawWinner = section.getString("winner-uuid");
            if (rawWinner != null) {
                war.winnerUuid = UUID.fromString(rawWinner);
            }
            return war;
        } catch (Exception ex) {
            return null;
        }
    }
}
