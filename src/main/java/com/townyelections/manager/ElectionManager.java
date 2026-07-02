package com.townyelections.manager;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.townyelections.TownyElections;
import com.townyelections.integration.TownyHook;
import com.townyelections.model.Candidate;
import com.townyelections.model.Election;
import com.townyelections.model.ElectionPhase;
import com.townyelections.model.ElectionResult;
import com.townyelections.model.TieBreaker;
import com.townyelections.util.DurationUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns all active elections and recorded results, drives the phase state
 * machine on a repeating task, and applies winner rewards through Towny.
 *
 * <p>Elections are keyed by town UUID. All mutation happens on the main thread.
 */
public class ElectionManager {

    private final TownyElections plugin;
    private final ConfigManager config;
    private final MessageManager messages;
    private final CommandConfig commands;
    private final TownyHook towny;
    private final Random random = new Random();

    /** town uuid -> active election */
    private final Map<UUID, Election> active = new ConcurrentHashMap<>();
    /** town uuid -> last concluded result */
    private final Map<UUID, ElectionResult> results = new ConcurrentHashMap<>();
    /** town uuid -> epoch millis at which an auto-scheduled election may next start */
    private final Map<UUID, Long> nextAutoStart = new ConcurrentHashMap<>();

    private final File dataFile;

    public ElectionManager(TownyElections plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.messages = plugin.getMessageManager();
        this.commands = plugin.getCommandConfig();
        this.towny = plugin.getTownyHook();
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    // ========================================================================
    //  Accessors
    // ========================================================================

    public Election getElection(UUID townUuid) {
        return active.get(townUuid);
    }

    public Election getElection(Town town) {
        return town == null ? null : active.get(town.getUUID());
    }

    public ElectionResult getLastResult(UUID townUuid) {
        return results.get(townUuid);
    }

    public Map<UUID, Election> getActiveElections() {
        return active;
    }

    // ========================================================================
    //  Candidacy
    // ========================================================================

    /** Register the resident as a candidate in their town's election. */
    public OperationResult registerCandidate(Resident resident, Town town) {
        Election election = active.get(town.getUUID());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        if (election.getPhase() != ElectionPhase.NOMINATION) {
            return OperationResult.fail("candidate.registration-closed");
        }
        if (election.isCandidate(resident.getUUID())) {
            return OperationResult.fail("candidate.already-registered");
        }
        int max = config.getMaxCandidates();
        if (max > 0 && election.getCandidateCount() >= max) {
            return OperationResult.fail("candidate.max-reached");
        }

        Candidate candidate = new Candidate(resident.getUUID(), resident.getName(),
                config.getDefaultCampaignMessage(), config.getDefaultPartyName(), System.currentTimeMillis());
        election.addCandidate(candidate);
        save();

        broadcastTown(town, "candidate.self-registered-broadcast",
                MessageManager.placeholders("player", resident.getName(), "town", town.getName()));
        return OperationResult.ok("candidate.registered");
    }

    /** Withdraw the resident from their town's election. */
    public OperationResult withdrawCandidate(Resident resident, Town town) {
        Election election = active.get(town.getUUID());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        if (!election.isCandidate(resident.getUUID())) {
            return OperationResult.fail("candidate.not-a-candidate");
        }
        election.removeCandidate(resident.getUUID());
        save();
        return OperationResult.ok("candidate.withdrawn");
    }

    /** Set the resident's campaign message. Returns the outcome. */
    public OperationResult setCampaignMessage(Resident resident, Town town, String message) {
        Election election = active.get(town.getUUID());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        Candidate candidate = election.getCandidate(resident.getUUID());
        if (candidate == null) {
            return OperationResult.fail("candidate.not-a-candidate");
        }
        if (message == null || message.isBlank()) {
            return OperationResult.fail("campaign.empty");
        }
        if (message.length() > config.getMaxMessageLength()) {
            return OperationResult.fail("campaign.too-long");
        }
        String lower = message.toLowerCase(Locale.ROOT);
        for (String blocked : config.getBlockedWords()) {
            if (!blocked.isBlank() && lower.contains(blocked.toLowerCase(Locale.ROOT))) {
                return OperationResult.fail("campaign.blocked");
            }
        }
        candidate.setCampaignMessage(message);
        save();
        return OperationResult.ok("campaign.set");
    }

    public OperationResult leaveParty(Resident resident, Town town) {
        Election election = active.get(town.getUUID());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        Candidate candidate = election.getCandidate(resident.getUUID());
        if (candidate == null) {
            return OperationResult.fail("candidate.not-a-candidate");
        }
        candidate.setPartyName(config.getDefaultPartyName());
        save();
        return OperationResult.ok("party.left");
    }

    public OperationResult setPartyName(Resident resident, Town town, String partyName) {
        Election election = active.get(town.getUUID());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        Candidate candidate = election.getCandidate(resident.getUUID());
        if (candidate == null) {
            return OperationResult.fail("candidate.not-a-candidate");
        }
        if (partyName == null || partyName.isBlank()) {
            return OperationResult.fail("party.empty");
        }
        String trimmed = partyName.trim();
        if (trimmed.length() > config.getMaxPartyNameLength()) {
            return OperationResult.fail("party.too-long");
        }
        candidate.setPartyName(trimmed);
        save();
        return OperationResult.ok("party.set");
    }

    // ========================================================================
    //  Voting
    // ========================================================================

    public OperationResult castVote(Resident voter, Town town, String candidateName) {
        Election election = active.get(town.getUUID());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        if (election.getPhase() != ElectionPhase.VOTING && election.getPhase() != ElectionPhase.RUNOFF) {
            return OperationResult.fail("vote.not-open");
        }
        if (!towny.isResidentOfTown(voter.getUUID(), town)) {
            return OperationResult.fail("vote.not-eligible");
        }
        Candidate candidate = election.findCandidateByName(candidateName);
        if (candidate == null) {
            return OperationResult.fail("vote.no-such-candidate");
        }
        if (!config.isAllowSelfVote() && candidate.getUuid().equals(voter.getUUID())) {
            return OperationResult.fail("vote.cannot-self-vote");
        }

        boolean alreadyVoted = election.hasVoted(voter.getUUID());
        if (alreadyVoted && !config.isAllowVoteChanges()) {
            return OperationResult.fail("vote.already-voted");
        }

        election.castVote(voter.getUUID(), candidate.getUuid());
        save();
        return OperationResult.ok(alreadyVoted ? "vote.changed" : "vote.cast", candidate.getName());
    }

    // ========================================================================
    //  Lifecycle
    // ========================================================================

    /** Start a new election for a town. Returns the outcome. */
    public OperationResult startElection(Town town) {
        if (active.containsKey(town.getUUID())) {
            return OperationResult.fail("election.already-active");
        }
        if (towny.getResidentCount(town) < config.getMinTownResidents()) {
            return OperationResult.fail("election.town-too-small");
        }
        long endsAt = System.currentTimeMillis() + config.getNominationDurationMs();
        Election election = new Election(town.getUUID(), town.getName(), ElectionPhase.NOMINATION, endsAt);
        active.put(town.getUUID(), election);
        save();

        broadcastTown(town, "election.started-nomination", MessageManager.placeholders(
                "town", town.getName(),
                "duration", DurationUtil.format(config.getNominationDurationMs()),
                "label", "election",
                "run", commands.literal(CommandConfig.RUN)));
        if (config.isBroadcastServerWide()) {
            Bukkit.broadcast(messages.prefixed(
                    messages.raw("election.announce-server-start").replace("{town}", town.getName())));
        }
        return OperationResult.ok("admin.started");
    }

    /** Force voting to end immediately and tally results. */
    public OperationResult stopElection(Town town) {
        Election election = active.get(town.getUUID());
        if (election == null) {
            return OperationResult.fail("admin.no-active-to-stop");
        }
        if (election.getPhase() == ElectionPhase.NOMINATION) {
            // Skip straight into concluding based on (likely zero) candidates.
            transitionToVoting(town, election);
            election = active.get(town.getUUID());
            if (election == null) {
                return OperationResult.ok("admin.stopped");
            }
        }
        concludeElection(town, election);
        return OperationResult.ok("admin.stopped");
    }

    /** Cancel an election with no winner. */
    public OperationResult cancelElection(Town town) {
        Election election = active.remove(town.getUUID());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        save();
        broadcastTown(town, "election.cancelled",
                MessageManager.placeholders("town", town.getName()));
        return OperationResult.ok("admin.cancelled");
    }

    // ========================================================================
    //  Tick / state machine (called on a repeating scheduler task)
    // ========================================================================

    public void tick() {
        long now = System.currentTimeMillis();

        // Process phase transitions for active elections.
        for (Election election : new ArrayList<>(active.values())) {
            Town town = towny.getTown(election.getTownUuid());
            if (town == null) {
                // Town no longer exists; drop the election silently.
                active.remove(election.getTownUuid());
                save();
                continue;
            }
            election.setTownName(town.getName());

            switch (election.getPhase()) {
                case NOMINATION -> {
                    if (election.isPhaseExpired()) {
                        transitionToVoting(town, election);
                    }
                }
                case VOTING, RUNOFF -> {
                    maybeSendVotingReminder(town, election, now);
                    if (election.isPhaseExpired()) {
                        concludeElection(town, election);
                    }
                }
                default -> {
                    // CONCLUDED / CANCELLED should not linger in the active map.
                    active.remove(election.getTownUuid());
                }
            }
        }

        // Auto-scheduling of new elections.
        if (config.isAutoScheduleEnabled()) {
            maybeAutoSchedule(now);
        }
    }

    private void transitionToVoting(Town town, Election election) {
        int candidates = election.getCandidateCount();

        // Not enough candidates: try auto-win or cancel.
        if (candidates < config.getMinCandidates()) {
            if (candidates == 1 && config.isAutoWinSingleCandidate()) {
                election.setPhase(ElectionPhase.VOTING);
                concludeElection(town, election);
                return;
            }
            active.remove(town.getUUID());
            save();
            broadcastTown(town, "election.cancelled-not-enough-candidates",
                    MessageManager.placeholders(
                            "town", town.getName(),
                            "count", String.valueOf(candidates),
                            "min", String.valueOf(config.getMinCandidates())));
            scheduleNextAuto(town.getUUID());
            return;
        }

        election.setPhase(ElectionPhase.VOTING);
        election.setPhaseEndsAt(System.currentTimeMillis() + config.getVotingDurationMs());
        election.setReminderSent(false);
        save();
        broadcastTown(town, "election.nominations-closed-voting-open",
                MessageManager.placeholders(
                        "town", town.getName(),
                        "duration", DurationUtil.format(config.getVotingDurationMs()),
                        "label", "election",
                        "vote", commands.literal(CommandConfig.VOTE)));
    }

    private void maybeSendVotingReminder(Town town, Election election, long now) {
        long reminderWindow = config.getVotingReminderBeforeEndMs();
        if (reminderWindow <= 0 || election.isReminderSent()) {
            return;
        }
        if (election.getPhaseEndsAt() - now <= reminderWindow) {
            election.setReminderSent(true);
            save();
            String time = DurationUtil.format(election.getMillisRemaining());
            for (Resident resident : town.getResidents()) {
                if (election.hasVoted(resident.getUUID())) {
                    continue;
                }
                Player online = Bukkit.getPlayer(resident.getUUID());
                if (online != null) {
                    messages.send(online, "vote.reminder", MessageManager.placeholders(
                            "town", town.getName(),
                            "time", time,
                            "label", "election",
                            "vote", commands.literal(CommandConfig.VOTE)));
                }
            }
        }
    }

    /**
     * Tally the election, resolve ties, record the result, apply rewards, and
     * remove it from the active set.
     */
    public void concludeElection(Town town, Election election) {
        Map<UUID, Integer> tally = election.tally();
        List<UUID> top = election.topCandidates();

        UUID winner = null;
        if (!election.getCandidates().isEmpty()) {
            if (top.size() == 1) {
                winner = top.get(0);
            } else if (top.size() > 1) {
                winner = resolveTie(town, election, top);
                // A RUNOFF tie-breaker starts a new round instead of concluding now.
                if (winner == null && election.getPhase() == ElectionPhase.RUNOFF) {
                    return;
                }
            }
        }

        recordAndReward(town, election, winner, tally);
    }

    private UUID resolveTie(Town town, Election election, List<UUID> tied) {
        TieBreaker breaker = config.getTieBreaker();
        switch (breaker) {
            case RANDOM:
                return tied.get(random.nextInt(tied.size()));
            case EARLIEST: {
                UUID earliest = null;
                long best = Long.MAX_VALUE;
                for (UUID id : tied) {
                    Candidate c = election.getCandidate(id);
                    if (c != null && c.getRegisteredAt() < best) {
                        best = c.getRegisteredAt();
                        earliest = id;
                    }
                }
                return earliest != null ? earliest : tied.get(0);
            }
            case INCUMBENT: {
                Resident mayor = towny.getMayor(town);
                if (mayor != null && tied.contains(mayor.getUUID())) {
                    return mayor.getUUID();
                }
                return tied.get(random.nextInt(tied.size()));
            }
            case RUNOFF: {
                startRunoff(town, election, tied);
                return null;
            }
            case NONE:
            default:
                return null;
        }
    }

    private void startRunoff(Town town, Election election, List<UUID> tied) {
        List<String> names = new ArrayList<>();
        for (UUID id : tied) {
            Candidate c = election.getCandidate(id);
            if (c != null) {
                names.add(c.getName());
            }
        }
        election.retainCandidates(tied);
        election.setPhase(ElectionPhase.RUNOFF);
        election.setPhaseEndsAt(System.currentTimeMillis() + config.getRunoffDurationMs());
        save();
        broadcastTown(town, "results.tie-runoff",
                MessageManager.placeholders("candidates", String.join(", ", names)));
    }

    private void recordAndReward(Town town, Election election, UUID winnerUuid, Map<UUID, Integer> tally) {
        // Build the ranked standings list.
        List<UUID> ranked = election.rankedCandidates();
        List<ElectionResult.Standing> standings = new ArrayList<>();
        for (UUID id : ranked) {
            Candidate c = election.getCandidate(id);
            if (c != null) {
                standings.add(new ElectionResult.Standing(id, c.getName(), c.getPartyName(), tally.getOrDefault(id, 0)));
            }
        }

        Candidate winnerCandidate = winnerUuid == null ? null : election.getCandidate(winnerUuid);
        int winnerVotes = winnerUuid == null ? 0 : tally.getOrDefault(winnerUuid, 0);

        ElectionResult result = new ElectionResult(
                town.getUUID(), town.getName(),
                winnerUuid, winnerCandidate == null ? null : winnerCandidate.getName(),
                winnerVotes, election.getTotalVotes(), towny.getResidentCount(town),
                System.currentTimeMillis(), standings);

        results.put(town.getUUID(), result);
        active.remove(town.getUUID());
        scheduleNextAuto(town.getUUID());
        save();

        // Announce.
        broadcastTown(town, "election.concluded",
                MessageManager.placeholders("town", town.getName()));

        if (winnerCandidate != null) {
            applyWinnerRewards(town, winnerCandidate, winnerVotes, election.getTotalVotes());
            broadcastTown(town, "winner.announce-town",
                    MessageManager.placeholders("winner", winnerCandidate.getName(),
                            "party", winnerCandidate.getPartyName(), "town", town.getName()));
            if (config.isBroadcastServerWide()) {
                Bukkit.broadcast(messages.prefixed(messages.raw("winner.announce-server")
                        .replace("{winner}", winnerCandidate.getName())
                        .replace("{party}", winnerCandidate.getPartyName())
                        .replace("{town}", town.getName())));
            }
        } else {
            broadcastTown(town, "results.no-winner", null);
        }

        // Loss commands for the other candidates.
        for (Candidate c : election.getCandidateList()) {
            if (winnerUuid != null && c.getUuid().equals(winnerUuid)) {
                continue;
            }
            runConfiguredCommands(config.getCommandsOnLoss(), MessageManager.placeholders(
                    "loser", c.getName(),
                    "loser_uuid", c.getUuid().toString(),
                    "party", c.getPartyName(),
                    "loser_party", c.getPartyName(),
                    "town", town.getName(),
                    "votes", String.valueOf(tally.getOrDefault(c.getUuid(), 0))));
        }
    }

    /** Grant the winner their configured Towny ranks / mayorship and run commands. */
    private void applyWinnerRewards(Town town, Candidate winner, int winnerVotes, int totalVotes) {
        Resident winnerResident = towny.getResident(winner.getUuid());
        if (winnerResident == null) {
            plugin.getLogger().warning("Winner " + winner.getName()
                    + " has no Towny resident record; skipping rank rewards.");
        } else {
            // Optionally revoke ranks from the previous holder (the current mayor).
            if (config.isRevokePreviousWinnerRanks()) {
                Resident previous = towny.getMayor(town);
                if (previous != null && !previous.getUUID().equals(winnerResident.getUUID())) {
                    towny.revokeRanks(previous, config.getGrantTownRanks());
                }
            }

            List<String> applied = towny.grantRanks(winnerResident, config.getGrantTownRanks());
            if (!applied.isEmpty()) {
                broadcastTown(town, "winner.ranks-granted", MessageManager.placeholders(
                        "winner", winner.getName(), "ranks", String.join(", ", applied)));
            }

            if (config.isSetAsMayor()) {
                if (towny.setMayor(town, winnerResident)) {
                    broadcastTown(town, "winner.made-mayor", MessageManager.placeholders(
                            "winner", winner.getName(), "town", town.getName()));
                }
            }
        }

        // Configured console commands.
        runConfiguredCommands(config.getCommandsOnWin(), MessageManager.placeholders(
                "winner", winner.getName(),
                "winner_uuid", winner.getUuid().toString(),
                "party", winner.getPartyName(),
                "winner_party", winner.getPartyName(),
                "town", town.getName(),
                "votes", String.valueOf(winnerVotes),
                "total_votes", String.valueOf(totalVotes)));
    }

    private void runConfiguredCommands(List<String> commandsList, Map<String, String> placeholders) {
        if (commandsList == null || commandsList.isEmpty()) {
            return;
        }
        for (String raw : commandsList) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String cmd = raw;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                cmd = cmd.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            final String toRun = cmd;
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), toRun);
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to run configured command '" + toRun + "': " + t.getMessage());
            }
        }
    }

    // ========================================================================
    //  Auto-scheduling
    // ========================================================================

    private void maybeAutoSchedule(long now) {
        for (Town town : com.palmergames.bukkit.towny.TownyUniverse.getInstance().getTowns()) {
            UUID uuid = town.getUUID();
            if (active.containsKey(uuid)) {
                continue;
            }
            if (towny.getResidentCount(town) < config.getMinTownResidents()) {
                continue;
            }
            long next = nextAutoStart.getOrDefault(uuid, 0L);
            if (next == 0L) {
                // First time we've seen this town under auto-schedule: arm the timer.
                nextAutoStart.put(uuid, now + config.getAutoScheduleIntervalMs());
                continue;
            }
            if (now >= next) {
                startElection(town);
            }
        }
    }

    private void scheduleNextAuto(UUID townUuid) {
        if (config.isAutoScheduleEnabled()) {
            nextAutoStart.put(townUuid, System.currentTimeMillis() + config.getAutoScheduleIntervalMs());
        }
    }

    // ========================================================================
    //  Messaging helper
    // ========================================================================

    private void broadcastTown(Town town, String key, Map<String, String> placeholders) {
        String body = messages.raw(key);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                body = body.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        for (Resident resident : town.getResidents()) {
            Player online = Bukkit.getPlayer(resident.getUUID());
            if (online != null) {
                online.sendMessage(messages.prefixed(body));
            }
        }
    }

    // ========================================================================
    //  Persistence
    // ========================================================================

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();

        ConfigurationSection activeSection = yaml.createSection("active");
        int i = 0;
        for (Election election : active.values()) {
            election.serialize(activeSection.createSection("e" + (i++)));
        }

        ConfigurationSection resultsSection = yaml.createSection("results");
        i = 0;
        for (ElectionResult result : results.values()) {
            result.serialize(resultsSection.createSection("r" + (i++)));
        }

        ConfigurationSection scheduleSection = yaml.createSection("next-auto-start");
        for (Map.Entry<UUID, Long> entry : nextAutoStart.entrySet()) {
            scheduleSection.set(entry.getKey().toString(), entry.getValue());
        }

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save election data: " + ex.getMessage());
        }
    }

    public void load() {
        active.clear();
        results.clear();
        nextAutoStart.clear();
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection activeSection = yaml.getConfigurationSection("active");
        if (activeSection != null) {
            for (String key : activeSection.getKeys(false)) {
                Election election = Election.deserialize(activeSection.getConfigurationSection(key));
                if (election != null) {
                    active.put(election.getTownUuid(), election);
                }
            }
        }

        ConfigurationSection resultsSection = yaml.getConfigurationSection("results");
        if (resultsSection != null) {
            for (String key : resultsSection.getKeys(false)) {
                ElectionResult result = ElectionResult.deserialize(resultsSection.getConfigurationSection(key));
                if (result != null) {
                    results.put(result.getTownUuid(), result);
                }
            }
        }

        ConfigurationSection scheduleSection = yaml.getConfigurationSection("next-auto-start");
        if (scheduleSection != null) {
            for (String key : scheduleSection.getKeys(false)) {
                try {
                    nextAutoStart.put(UUID.fromString(key), scheduleSection.getLong(key));
                } catch (IllegalArgumentException ignored) {
                    // skip malformed
                }
            }
        }
    }
}
