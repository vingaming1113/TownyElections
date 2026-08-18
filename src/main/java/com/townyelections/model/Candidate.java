package com.townyelections.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.UUID;

/**
 * A resident standing for election, including their campaign message and the
 * moment they registered (used for deterministic tie-breaking).
 */
public class Candidate {

    private final UUID uuid;
    private final String name;
    private String campaignMessage;
    private String partyName;
    private final long registeredAt;

    public Candidate(UUID uuid, String name, String campaignMessage, String partyName, long registeredAt) {
        this.uuid = uuid;
        this.name = name;
        this.campaignMessage = campaignMessage;
        this.partyName = partyName;
        this.registeredAt = registeredAt;
    }

    public Candidate(UUID uuid, String name, String campaignMessage) {
        this(uuid, name, campaignMessage, "Independent", System.currentTimeMillis());
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getCampaignMessage() {
        return campaignMessage;
    }

    public void setCampaignMessage(String campaignMessage) {
        this.campaignMessage = campaignMessage;
    }

    public String getPartyName() {
        return partyName;
    }

    public void setPartyName(String partyName) {
        this.partyName = partyName;
    }

    public long getRegisteredAt() {
        return registeredAt;
    }

    // ---- Serialization -----------------------------------------------------

    public void serialize(ConfigurationSection section) {
        section.set("uuid", uuid.toString());
        section.set("name", name);
        section.set("campaign-message", campaignMessage);
        section.set("party-name", partyName);
        section.set("registered-at", registeredAt);
    }

    public static Candidate deserialize(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String rawUuid = section.getString("uuid");
        if (rawUuid == null) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(rawUuid);
            String name = section.getString("name", "Unknown");
            String message = section.getString("campaign-message", "");
            String partyName = section.getString("party-name", "Independent");
            long registeredAt = section.getLong("registered-at", System.currentTimeMillis());
            return new Candidate(uuid, name, message, partyName, registeredAt);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
