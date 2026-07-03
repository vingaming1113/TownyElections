package com.townyelections.manager;

import com.townyelections.TownyElections;
import com.townyelections.model.TieBreaker;
import com.townyelections.model.VotingSystem;
import com.townyelections.util.DurationUtil;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

/**
 * Typed, cached access to {@code config.yml}. All values are read once on
 * {@link #load()} so the hot paths (voting, ticking) avoid repeated YAML lookups.
 */
public class ConfigManager {

    private final TownyElections plugin;

    // general
    private String locale;
    private boolean debug;
    private boolean metrics;

    // election
    private long nominationDurationMs;
    private long votingDurationMs;
    private int minCandidates;
    private int maxCandidates;
    private boolean autoWinSingleCandidate;
    private int minTownResidents;
    private int votesPerResident;
    private boolean allowVoteChanges;
    private boolean publicLiveResults;
    private boolean allowSelfVote;
    private VotingSystem votingSystem;
    private TieBreaker tieBreaker;
    private long runoffDurationMs;
    private boolean autoScheduleEnabled;
    private long autoScheduleIntervalMs;
    private double candidacyCost;
    private double winnerReward;

    // campaign
    private int maxMessageLength;
    private String defaultCampaignMessage;
    private int maxPartyNameLength;
    private String defaultPartyName;
    private boolean hideDefaultPartyFromStandings;
    private int maxParties;
    private List<String> blockedWords;

    // winner
    private boolean setAsMayor;
    private List<String> grantTownRanks;
    private boolean revokePreviousWinnerRanks;
    private List<String> commandsOnWin;
    private List<String> commandsOnLoss;

    // notifications
    private boolean broadcastServerWide;
    private long votingReminderBeforeEndMs;
    private boolean notifyOnJoin;

    public ConfigManager(TownyElections plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        locale = c.getString("general.locale", "en").toLowerCase(Locale.ROOT);
        debug = c.getBoolean("general.debug", false);
        metrics = c.getBoolean("general.metrics", true);

        nominationDurationMs = DurationUtil.parseMillis(c.getString("election.nomination-duration"), days(2));
        votingDurationMs = DurationUtil.parseMillis(c.getString("election.voting-duration"), days(3));
        minCandidates = Math.max(1, c.getInt("election.min-candidates", 2));
        maxCandidates = Math.max(0, c.getInt("election.max-candidates", 0));
        autoWinSingleCandidate = c.getBoolean("election.auto-win-single-candidate", true);
        minTownResidents = Math.max(1, c.getInt("election.min-town-residents", 2));
        votesPerResident = Math.max(1, c.getInt("election.votes-per-resident", 1));
        allowVoteChanges = c.getBoolean("election.allow-vote-changes", true);
        publicLiveResults = c.getBoolean("election.public-live-results", false);
        allowSelfVote = c.getBoolean("election.allow-self-vote", true);
        votingSystem = VotingSystem.fromString(c.getString("election.voting-system"), VotingSystem.PLURALITY);
        tieBreaker = TieBreaker.fromString(c.getString("election.tie-breaker"), TieBreaker.INCUMBENT);
        runoffDurationMs = DurationUtil.parseMillis(c.getString("election.runoff-duration"), days(1));
        autoScheduleEnabled = c.getBoolean("election.auto-schedule.enabled", false);
        autoScheduleIntervalMs = DurationUtil.parseMillis(c.getString("election.auto-schedule.interval"), days(30));
        candidacyCost = c.getDouble("election.economy.candidacy-cost", 0.0);
        winnerReward = c.getDouble("election.economy.winner-reward", 0.0);

        maxMessageLength = Math.max(1, c.getInt("campaign.max-message-length", 128));
        defaultCampaignMessage = c.getString("campaign.default-message", "I would be honored to serve this town.");
        maxPartyNameLength = Math.max(1, c.getInt("campaign.max-party-name-length", 32));
        defaultPartyName = c.getString("campaign.default-party-name", "Independent");
        hideDefaultPartyFromStandings = c.getBoolean("campaign.hide-default-party-from-standings", false);
        maxParties = Math.max(0, c.getInt("campaign.max-parties", 0));
        blockedWords = c.getStringList("campaign.blocked-words");

        setAsMayor = c.getBoolean("winner.set-as-mayor", false);
        grantTownRanks = c.getStringList("winner.grant-town-ranks");
        revokePreviousWinnerRanks = c.getBoolean("winner.revoke-previous-winner-ranks", true);
        commandsOnWin = c.getStringList("winner.commands-on-win");
        commandsOnLoss = c.getStringList("winner.commands-on-loss");

        broadcastServerWide = c.getBoolean("notifications.broadcast-server-wide", false);
        votingReminderBeforeEndMs = DurationUtil.parseMillis(c.getString("notifications.voting-reminder-before-end"), hours(6));
        notifyOnJoin = c.getBoolean("notifications.notify-on-join", true);
    }

    private static long days(long d) {
        return d * 24L * 60L * 60L * 1000L;
    }

    private static long hours(long h) {
        return h * 60L * 60L * 1000L;
    }

    // ---- Getters -----------------------------------------------------------

    public String getLocale() { return locale; }
    public boolean isDebug() { return debug; }
    public boolean isMetrics() { return metrics; }

    public long getNominationDurationMs() { return nominationDurationMs; }
    public long getVotingDurationMs() { return votingDurationMs; }
    public int getMinCandidates() { return minCandidates; }
    public int getMaxCandidates() { return maxCandidates; }
    public boolean isAutoWinSingleCandidate() { return autoWinSingleCandidate; }
    public int getMinTownResidents() { return minTownResidents; }
    public int getVotesPerResident() { return votesPerResident; }
    public boolean isAllowVoteChanges() { return allowVoteChanges; }
    public boolean isPublicLiveResults() { return publicLiveResults; }
    public boolean isAllowSelfVote() { return allowSelfVote; }
    public VotingSystem getVotingSystem() { return votingSystem; }
    public TieBreaker getTieBreaker() { return tieBreaker; }
    public long getRunoffDurationMs() { return runoffDurationMs; }
    public boolean isAutoScheduleEnabled() { return autoScheduleEnabled; }
    public long getAutoScheduleIntervalMs() { return autoScheduleIntervalMs; }
    public double getCandidacyCost() { return candidacyCost; }
    public double getWinnerReward() { return winnerReward; }

    public int getMaxMessageLength() { return maxMessageLength; }
    public String getDefaultCampaignMessage() { return defaultCampaignMessage; }
    public int getMaxPartyNameLength() { return maxPartyNameLength; }
    public String getDefaultPartyName() { return defaultPartyName; }
    public boolean isHideDefaultPartyFromStandings() { return hideDefaultPartyFromStandings; }
    public int getMaxParties() { return maxParties; }
    public List<String> getBlockedWords() { return blockedWords; }

    public boolean isSetAsMayor() { return setAsMayor; }
    public List<String> getGrantTownRanks() { return grantTownRanks; }
    public boolean isRevokePreviousWinnerRanks() { return revokePreviousWinnerRanks; }
    public List<String> getCommandsOnWin() { return commandsOnWin; }
    public List<String> getCommandsOnLoss() { return commandsOnLoss; }

    public boolean isBroadcastServerWide() { return broadcastServerWide; }
    public long getVotingReminderBeforeEndMs() { return votingReminderBeforeEndMs; }
    public boolean isNotifyOnJoin() { return notifyOnJoin; }
}
