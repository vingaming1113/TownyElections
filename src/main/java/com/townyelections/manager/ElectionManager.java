package com.townyelections.manager;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.townyelections.TownyElections;
import com.townyelections.integration.Constituency;
import com.townyelections.integration.TownyHook;
import com.townyelections.model.Candidate;
import com.townyelections.model.Election;
import com.townyelections.model.ElectionPhase;
import com.townyelections.model.ElectionResult;
import com.townyelections.model.ElectionScope;
import com.townyelections.model.InstantRunoff;
import com.townyelections.model.TieBreaker;
import com.townyelections.model.VotingSystem;
import com.townyelections.util.DurationUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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

    /** Register the resident as a candidate in their constituency's election. */
    public OperationResult registerCandidate(Resident resident, Town town) {
        return registerCandidate(resident, towny.of(town));
    }

    public OperationResult registerCandidate(Resident resident, Constituency c) {
        Election election = active.get(c.getUuid());
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

        broadcast(c, "candidate.self-registered-broadcast",
                MessageManager.placeholders("player", resident.getName(), "town", c.getName()));
        return OperationResult.ok("candidate.registered");
    }

    /** Withdraw the resident from their constituency's election. */
    public OperationResult withdrawCandidate(Resident resident, Town town) {
        return withdrawCandidate(resident, towny.of(town));
    }

    public OperationResult withdrawCandidate(Resident resident, Constituency c) {
        Election election = active.get(c.getUuid());
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
        return setCampaignMessage(resident, towny.of(town), message);
    }

    public OperationResult setCampaignMessage(Resident resident, Constituency c, String message) {
        Election election = active.get(c.getUuid());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        Candidate candidate = election.getCandidate(resident.getUUID());
        if (candidate == null) {
            return OperationResult.fail("candidate.not-a-candidate");
        }
        if (editsLocked(election)) {
            return OperationResult.fail("candidate.edits-locked");
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

    /** Set the resident's candidate profile. Returns the outcome. */
    public OperationResult setCandidateProfile(Resident resident, Town town, String profile) {
        return setCandidateProfile(resident, towny.of(town), profile);
    }

    public OperationResult setCandidateProfile(Resident resident, Constituency c, String profile) {
        Election election = active.get(c.getUuid());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        Candidate candidate = election.getCandidate(resident.getUUID());
        if (candidate == null) {
            return OperationResult.fail("candidate.not-a-candidate");
        }
        if (editsLocked(election)) {
            return OperationResult.fail("candidate.edits-locked");
        }
        if (profile == null || profile.isBlank()) {
            return OperationResult.fail("profile.empty");
        }
        if (profile.length() > config.getMaxProfileLength()) {
            return OperationResult.fail("profile.too-long");
        }
        String lower = profile.toLowerCase(Locale.ROOT);
        for (String blocked : config.getBlockedWords()) {
            if (!blocked.isBlank() && lower.contains(blocked.toLowerCase(Locale.ROOT))) {
                return OperationResult.fail("profile.blocked");
            }
        }
        candidate.setProfile(profile);
        save();
        return OperationResult.ok("profile.set");
    }

    public OperationResult leaveParty(Resident resident, Town town) {
        return leaveParty(resident, towny.of(town));
    }

    public OperationResult leaveParty(Resident resident, Constituency c) {
        Election election = active.get(c.getUuid());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        Candidate candidate = election.getCandidate(resident.getUUID());
        if (candidate == null) {
            return OperationResult.fail("candidate.not-a-candidate");
        }
        if (editsLocked(election)) {
            return OperationResult.fail("candidate.edits-locked");
        }
        candidate.setPartyName(config.getDefaultPartyName());
        save();
        return OperationResult.ok("party.left");
    }

    public OperationResult renameParty(Town town, String oldName, String newName) {
        return renameParty(towny.of(town), oldName, newName);
    }

    public OperationResult renameParty(Constituency c, String oldName, String newName) {
        Election election = active.get(c.getUuid());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        if (editsLocked(election)) {
            return OperationResult.fail("candidate.edits-locked");
        }
        if (oldName == null || oldName.isBlank() || newName == null || newName.isBlank()) {
            return OperationResult.fail("party.rename-usage");
        }
        String trimmedNew = newName.trim();
        if (trimmedNew.length() > config.getMaxPartyNameLength()) {
            return OperationResult.fail("party.too-long");
        }
        int renamed = 0;
        for (Candidate candidate : election.getCandidateList()) {
            if (candidate.getPartyName().equalsIgnoreCase(oldName.trim())) {
                candidate.setPartyName(trimmedNew);
                renamed++;
            }
        }
        if (renamed == 0) {
            return OperationResult.fail("party.no-such-party");
        }
        election.renamePartyColor(oldName.trim(), trimmedNew);
        save();
        return OperationResult.ok("party.renamed");
    }

    private boolean wouldExceedPartyLimit(Election election, UUID changingCandidate, String targetParty) {
        int max = config.getMaxParties();
        if (max <= 0 || targetParty.equalsIgnoreCase(config.getDefaultPartyName())) {
            return false;
        }

        Set<String> parties = new HashSet<>();
        boolean targetAlreadyExists = false;
        for (Candidate candidate : election.getCandidateList()) {
            if (candidate.getUuid().equals(changingCandidate)) {
                continue;
            }
            String party = candidate.getPartyName();
            if (party == null || party.isBlank() || party.equalsIgnoreCase(config.getDefaultPartyName())) {
                continue;
            }
            String normalized = party.toLowerCase(Locale.ROOT);
            parties.add(normalized);
            if (party.equalsIgnoreCase(targetParty)) {
                targetAlreadyExists = true;
            }
        }
        return !targetAlreadyExists && parties.size() >= max;
    }

    public OperationResult setPartyName(Resident resident, Town town, String partyName) {
        return setPartyName(resident, towny.of(town), partyName);
    }

    public OperationResult setPartyName(Resident resident, Constituency c, String partyName) {
        Election election = active.get(c.getUuid());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        Candidate candidate = election.getCandidate(resident.getUUID());
        if (candidate == null) {
            return OperationResult.fail("candidate.not-a-candidate");
        }
        if (editsLocked(election)) {
            return OperationResult.fail("candidate.edits-locked");
        }
        if (partyName == null || partyName.isBlank()) {
            return OperationResult.fail("party.empty");
        }
        String trimmed = partyName.trim();
        if (trimmed.length() > config.getMaxPartyNameLength()) {
            return OperationResult.fail("party.too-long");
        }
        if (wouldExceedPartyLimit(election, resident.getUUID(), trimmed)) {
            return OperationResult.fail("party.max-parties");
        }
        String previousParty = candidate.getPartyName();
        candidate.setPartyName(trimmed);
        save();
        if (previousParty == null || !previousParty.equalsIgnoreCase(trimmed)) {
            broadcast(c, "party.joined-broadcast", MessageManager.placeholders(
                    "player", candidate.getName(),
                    "party", partyDisplay(election, trimmed),
                    "town", c.getName()));
        }
        return OperationResult.ok("party.set");
    }

    public OperationResult setPartyColor(Resident resident, Town town, String colorInput) {
        return setPartyColor(resident, towny.of(town), colorInput);
    }

    /** Set the colour of the candidate's (non-default) party for this election. */
    public OperationResult setPartyColor(Resident resident, Constituency c, String colorInput) {
        Election election = active.get(c.getUuid());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        Candidate candidate = election.getCandidate(resident.getUUID());
        if (candidate == null) {
            return OperationResult.fail("candidate.not-a-candidate");
        }
        if (editsLocked(election)) {
            return OperationResult.fail("candidate.edits-locked");
        }
        String party = candidate.getPartyName();
        if (party == null || party.isBlank() || party.equalsIgnoreCase(config.getDefaultPartyName())) {
            return OperationResult.fail("party.color-needs-party");
        }
        String code = com.townyelections.util.TextUtil.parseColor(colorInput);
        if (code == null) {
            return OperationResult.fail("party.color-invalid");
        }
        election.setPartyColor(party, code);
        save();
        return OperationResult.ok("party.color-set", partyDisplay(election, party));
    }

    /** True when candidacy details are frozen because voting has begun. */
    private boolean editsLocked(Election election) {
        return config.isLockEditsDuringVoting() && election.getPhase() != ElectionPhase.NOMINATION;
    }

    /** A party name prefixed with its configured colour (falls back to no colour). */
    public String partyDisplay(Election election, String party) {
        if (election == null || party == null) {
            return party;
        }
        String color = election.getPartyColor(party);
        return color.isEmpty() ? party : color + party;
    }

    // ========================================================================
    //  Voting
    // ========================================================================

    public OperationResult castVote(Resident voter, Town town, String candidateName) {
        return castVote(voter, towny.of(town), candidateName);
    }

    public OperationResult castVote(Resident voter, Constituency c, String candidateName) {
        Election election = active.get(c.getUuid());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        Candidate candidate = election.findCandidateByName(candidateName);
        return castVote(voter, c, election, candidate);
    }

    public OperationResult castVote(Resident voter, Town town, UUID candidateUuid) {
        return castVote(voter, towny.of(town), candidateUuid);
    }

    public OperationResult castVote(Resident voter, Constituency c, UUID candidateUuid) {
        Election election = active.get(c.getUuid());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        Candidate candidate = election.getCandidate(candidateUuid);
        return castVote(voter, c, election, candidate);
    }

    private OperationResult castVote(Resident voter, Constituency c, Election election, Candidate candidate) {
        OperationResult eligibility = checkVoterEligibility(voter, c, election);
        if (eligibility != null) {
            return eligibility;
        }
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

    /**
     * Replaces the voter's full ballot from typed candidate names. Used by the
     * vote command under RANKED_CHOICE (names in preference order) and APPROVAL
     * (names form the approved set).
     */
    public OperationResult castBallot(Resident voter, Town town, List<String> candidateNames) {
        return castBallot(voter, towny.of(town), candidateNames);
    }

    public OperationResult castBallot(Resident voter, Constituency c, List<String> candidateNames) {
        Election election = active.get(c.getUuid());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        OperationResult eligibility = checkVoterEligibility(voter, c, election);
        if (eligibility != null) {
            return eligibility;
        }

        List<UUID> ballot = new ArrayList<>();
        for (String name : candidateNames) {
            Candidate candidate = election.findCandidateByName(name);
            if (candidate == null) {
                return OperationResult.fail("vote.no-such-candidate", name);
            }
            if (!config.isAllowSelfVote() && candidate.getUuid().equals(voter.getUUID())) {
                return OperationResult.fail("vote.cannot-self-vote");
            }
            if (!ballot.contains(candidate.getUuid())) {
                ballot.add(candidate.getUuid());
            }
        }
        if (ballot.isEmpty()) {
            return OperationResult.fail("vote.usage");
        }

        boolean alreadyVoted = election.hasVoted(voter.getUUID());
        if (alreadyVoted && !config.isAllowVoteChanges()) {
            return OperationResult.fail("vote.already-voted");
        }

        election.castBallot(voter.getUUID(), ballot);
        save();
        return OperationResult.ok(alreadyVoted ? "vote.ballot-changed" : "vote.ballot-cast",
                describeBallot(election, voter.getUUID()));
    }

    /**
     * Adds or removes one candidate on the voter's ballot. Used by GUI clicks
     * under RANKED_CHOICE (appends the next preference) and APPROVAL (toggles
     * approval). Extending a ballot is always allowed; removing an entry
     * requires vote changes to be enabled.
     */
    public OperationResult toggleBallotEntry(Resident voter, Town town, UUID candidateUuid) {
        return toggleBallotEntry(voter, towny.of(town), candidateUuid);
    }

    public OperationResult toggleBallotEntry(Resident voter, Constituency c, UUID candidateUuid) {
        Election election = active.get(c.getUuid());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        OperationResult eligibility = checkVoterEligibility(voter, c, election);
        if (eligibility != null) {
            return eligibility;
        }
        Candidate candidate = election.getCandidate(candidateUuid);
        if (candidate == null) {
            return OperationResult.fail("vote.no-such-candidate");
        }

        boolean ranked = election.getVotingSystem() == VotingSystem.RANKED_CHOICE;
        List<UUID> ballot = new ArrayList<>(election.getBallot(voter.getUUID()));
        if (ballot.contains(candidateUuid)) {
            if (!config.isAllowVoteChanges()) {
                return OperationResult.fail("vote.already-voted");
            }
            ballot.remove(candidateUuid);
            election.castBallot(voter.getUUID(), ballot);
            save();
            return OperationResult.ok(ranked ? "vote.unranked" : "vote.unapproved", candidate.getName());
        }

        if (!config.isAllowSelfVote() && candidateUuid.equals(voter.getUUID())) {
            return OperationResult.fail("vote.cannot-self-vote");
        }
        ballot.add(candidateUuid);
        election.castBallot(voter.getUUID(), ballot);
        save();
        return OperationResult.ok(ranked ? "vote.ranked" : "vote.approved", candidate.getName());
    }

    /** Clears the voter's entire ballot (GUI button; requires vote changes). */
    public OperationResult clearBallot(Resident voter, Town town) {
        return clearBallot(voter, towny.of(town));
    }

    public OperationResult clearBallot(Resident voter, Constituency c) {
        Election election = active.get(c.getUuid());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        OperationResult eligibility = checkVoterEligibility(voter, c, election);
        if (eligibility != null) {
            return eligibility;
        }
        if (!election.hasVoted(voter.getUUID())) {
            return OperationResult.fail("vote.nothing-to-clear");
        }
        if (!config.isAllowVoteChanges()) {
            return OperationResult.fail("vote.already-voted");
        }
        election.removeBallot(voter.getUUID());
        save();
        return OperationResult.ok("vote.ballot-cleared");
    }

    /** Shared phase and residency checks; null when the voter may proceed. */
    private OperationResult checkVoterEligibility(Resident voter, Constituency c, Election election) {
        if (election.getPhase() != ElectionPhase.VOTING && election.getPhase() != ElectionPhase.RUNOFF) {
            return OperationResult.fail("vote.not-open");
        }
        if (!c.isResident(voter.getUUID())) {
            return OperationResult.fail("vote.not-eligible");
        }
        return null;
    }

    /** Human-readable summary of a voter's ballot, e.g. "1. Alice, 2. Bob". */
    public String describeBallot(Election election, UUID voter) {
        List<UUID> ballot = election.getBallot(voter);
        List<String> parts = new ArrayList<>();
        boolean ranked = election.getVotingSystem() == VotingSystem.RANKED_CHOICE;
        int rank = 1;
        for (UUID choice : ballot) {
            Candidate candidate = election.getCandidate(choice);
            String name = candidate == null ? "?" : candidate.getName();
            parts.add(ranked ? (rank++) + ". " + name : name);
        }
        return String.join(", ", parts);
    }

    // ========================================================================
    //  Lifecycle
    // ========================================================================

    /** Start a new election for a town. Returns the outcome. */
    public OperationResult startElection(Town town) {
        return startElection(towny.of(town));
    }

    /** Start a new election for any constituency (town or nation). */
    public OperationResult startElection(Constituency c) {
        if (active.containsKey(c.getUuid())) {
            return OperationResult.fail("election.already-active");
        }
        int minResidents = c.scope() == ElectionScope.NATION
                ? config.getMinNationResidents() : config.getMinTownResidents();
        if (c.getResidentCount() < minResidents) {
            return OperationResult.fail("election.town-too-small");
        }
        long endsAt = System.currentTimeMillis() + config.getNominationDurationMs();
        Election election = new Election(c.getUuid(), c.getName(), ElectionPhase.NOMINATION, endsAt);
        election.setScope(c.scope());
        election.setVotingSystem(config.getVotingSystem());
        active.put(c.getUuid(), election);
        save();

        broadcast(c, "election.started-nomination", MessageManager.placeholders(
                "town", c.getName(),
                "duration", DurationUtil.format(config.getNominationDurationMs()),
                "label", "election",
                "run", subLiteral(election, CommandConfig.RUN)));
        if (config.isBroadcastServerWide()) {
            Bukkit.broadcast(messages.prefixed(
                    messages.raw("election.announce-server-start").replace("{town}", c.getName())));
        }
        return OperationResult.ok("admin.started");
    }

    /** Force voting to end immediately and tally results. */
    public OperationResult stopElection(Town town) {
        return stopElection(towny.of(town));
    }

    public OperationResult stopElection(Constituency c) {
        Election election = active.get(c.getUuid());
        if (election == null) {
            return OperationResult.fail("admin.no-active-to-stop");
        }
        if (election.getPhase() == ElectionPhase.NOMINATION) {
            // Skip straight into concluding based on (likely zero) candidates.
            transitionToVoting(c, election);
            election = active.get(c.getUuid());
            if (election == null) {
                return OperationResult.ok("admin.stopped");
            }
        }
        concludeElection(c, election);
        return OperationResult.ok("admin.stopped");
    }

    /** Cancel an election with no winner. */
    public OperationResult cancelElection(Town town) {
        return cancelElection(towny.of(town));
    }

    public OperationResult cancelElection(Constituency c) {
        Election election = active.remove(c.getUuid());
        if (election == null) {
            return OperationResult.fail("election.none-active");
        }
        save();
        broadcast(c, "election.cancelled",
                MessageManager.placeholders("town", c.getName()));
        return OperationResult.ok("admin.cancelled");
    }

    // ========================================================================
    //  Tick / state machine (called on a repeating scheduler task)
    // ========================================================================

    public void tick() {
        long now = System.currentTimeMillis();

        // Process phase transitions for active elections.
        for (Election election : new ArrayList<>(active.values())) {
            Constituency c = towny.constituencyFor(election);
            if (c == null) {
                // Town/nation no longer exists; drop the election silently.
                active.remove(election.getTownUuid());
                save();
                continue;
            }
            election.setTownName(c.getName());

            switch (election.getPhase()) {
                case NOMINATION -> {
                    if (election.isPhaseExpired()) {
                        transitionToVoting(c, election);
                    }
                }
                case VOTING, RUNOFF -> {
                    maybeSendVotingReminder(c, election, now);
                    if (election.isPhaseExpired()) {
                        concludeElection(c, election);
                    }
                }
                default -> {
                    // CONCLUDED / CANCELLED should not linger in the active map.
                    active.remove(election.getTownUuid());
                }
            }
        }

        // Auto-scheduling of new elections.
        boolean nationAuto = config.isNationElectionsEnabled() && config.isNationAutoSchedule();
        if (config.isAutoScheduleEnabled() || nationAuto) {
            maybeAutoSchedule(now, nationAuto);
        }
    }

    private void transitionToVoting(Constituency c, Election election) {
        int candidates = election.getCandidateCount();

        // Not enough candidates: try auto-win or cancel.
        if (candidates < config.getMinCandidates()) {
            if (candidates == 1 && config.isAutoWinSingleCandidate()) {
                election.setPhase(ElectionPhase.VOTING);
                concludeElection(c, election);
                return;
            }
            active.remove(c.getUuid());
            save();
            broadcast(c, "election.cancelled-not-enough-candidates",
                    MessageManager.placeholders(
                            "town", c.getName(),
                            "count", String.valueOf(candidates),
                            "min", String.valueOf(config.getMinCandidates())));
            scheduleNextAuto(c.getUuid());
            return;
        }

        election.setPhase(ElectionPhase.VOTING);
        election.setPhaseEndsAt(System.currentTimeMillis() + config.getVotingDurationMs());
        election.setReminderSent(false);
        save();
        broadcast(c, "election.nominations-closed-voting-open",
                MessageManager.placeholders(
                        "town", c.getName(),
                        "duration", DurationUtil.format(config.getVotingDurationMs()),
                        "label", "election",
                        "vote", subLiteral(election, CommandConfig.VOTE),
                        "system", messages.raw(election.getVotingSystem().messageKey())));
    }

    private void maybeSendVotingReminder(Constituency c, Election election, long now) {
        long reminderWindow = config.getVotingReminderBeforeEndMs();
        if (reminderWindow <= 0 || election.isReminderSent()) {
            return;
        }
        if (election.getPhaseEndsAt() - now <= reminderWindow) {
            election.setReminderSent(true);
            save();
            String time = DurationUtil.format(election.getMillisRemaining());
            for (Resident resident : c.getResidents()) {
                if (election.hasVoted(resident.getUUID())) {
                    continue;
                }
                Player online = Bukkit.getPlayer(resident.getUUID());
                if (online != null) {
                    messages.send(online, "vote.reminder", MessageManager.placeholders(
                            "town", c.getName(),
                            "time", time,
                            "label", "election",
                            "vote", subLiteral(election, CommandConfig.VOTE)));
                }
            }
        }
    }

    /**
     * Tally the election, resolve ties, record the result, apply rewards, and
     * remove it from the active set.
     */
    public void concludeElection(Constituency c, Election election) {
        if (election.getVotingSystem() == VotingSystem.RANKED_CHOICE) {
            concludeRankedChoice(c, election);
            return;
        }

        Map<UUID, Integer> tally = election.tally();
        List<UUID> top = election.topCandidates();

        UUID winner = null;
        if (!election.getCandidates().isEmpty()) {
            if (top.size() == 1) {
                winner = top.get(0);
            } else if (top.size() > 1) {
                winner = resolveTie(c, election, top);
                // A RUNOFF tie-breaker starts a new round instead of concluding now.
                if (winner == null && election.getPhase() == ElectionPhase.RUNOFF) {
                    return;
                }
            }
        }

        recordAndReward(c, election, winner, tally, List.of(), null);
    }

    /** Runs the instant-runoff count and concludes from its outcome. */
    private void concludeRankedChoice(Constituency c, Election election) {
        InstantRunoff.Outcome outcome = election.instantRunoff();
        List<UUID> finalists = outcome.finalists();

        UUID winner = null;
        if (!election.getCandidates().isEmpty()) {
            if (finalists.size() == 1) {
                winner = finalists.get(0);
            } else if (finalists.size() > 1) {
                winner = resolveTie(c, election, finalists);
                if (winner == null && election.getPhase() == ElectionPhase.RUNOFF) {
                    return;
                }
            }
        }

        recordAndReward(c, election, winner, outcome.finalVotes(),
                toResultRounds(election, outcome), outcome.finishOrder());
    }

    private List<ElectionResult.Round> toResultRounds(Election election, InstantRunoff.Outcome outcome) {
        List<ElectionResult.Round> rounds = new ArrayList<>();
        for (InstantRunoff.Round round : outcome.rounds()) {
            List<ElectionResult.RoundEntry> entries = new ArrayList<>();
            round.counts().entrySet().stream()
                    .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                    .forEach(entry -> entries.add(new ElectionResult.RoundEntry(
                            candidateName(election, entry.getKey()), entry.getValue())));
            List<String> eliminated = round.eliminated().stream()
                    .map(id -> candidateName(election, id))
                    .toList();
            rounds.add(new ElectionResult.Round(round.number(), entries, eliminated, round.exhausted()));
        }
        return rounds;
    }

    private String candidateName(Election election, UUID candidateUuid) {
        Candidate candidate = election.getCandidate(candidateUuid);
        return candidate == null ? "?" : candidate.getName();
    }

    private UUID resolveTie(Constituency constituency, Election election, List<UUID> tied) {
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
                Resident leader = constituency.getLeader();
                if (leader != null && tied.contains(leader.getUUID())) {
                    return leader.getUUID();
                }
                return tied.get(random.nextInt(tied.size()));
            }
            case RUNOFF: {
                startRunoff(constituency, election, tied);
                return null;
            }
            case NONE:
            default:
                return null;
        }
    }

    private void startRunoff(Constituency constituency, Election election, List<UUID> tied) {
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
        broadcast(constituency, "results.tie-runoff",
                MessageManager.placeholders("candidates", String.join(", ", names)));
    }

    private void recordAndReward(Constituency constituency, Election election, UUID winnerUuid,
                                 Map<UUID, Integer> tally,
                                 List<ElectionResult.Round> rounds, List<UUID> finishOrder) {
        // Build the ranked standings list.
        List<UUID> ranked = finishOrder != null ? finishOrder : election.rankedCandidates();
        List<ElectionResult.Standing> standings = new ArrayList<>();
        for (UUID id : ranked) {
            Candidate c = election.getCandidate(id);
            if (c != null) {
                standings.add(new ElectionResult.Standing(id, c.getName(), c.getPartyName(),
                        election.getPartyColor(c.getPartyName()), tally.getOrDefault(id, 0)));
            }
        }

        Candidate winnerCandidate = winnerUuid == null ? null : election.getCandidate(winnerUuid);
        int winnerVotes = winnerUuid == null ? 0 : tally.getOrDefault(winnerUuid, 0);

        ElectionResult result = new ElectionResult(
                constituency.getUuid(), constituency.getName(),
                winnerUuid, winnerCandidate == null ? null : winnerCandidate.getName(),
                winnerVotes, election.getTotalVotes(), constituency.getResidentCount(),
                System.currentTimeMillis(), standings, election.getVotingSystem(), rounds);

        results.put(constituency.getUuid(), result);
        active.remove(constituency.getUuid());
        scheduleNextAuto(constituency.getUuid());
        save();

        // Announce.
        broadcast(constituency, "election.concluded",
                MessageManager.placeholders("town", constituency.getName()));

        PartyStanding leadingParty = leadingParty(election, tally);
        if (leadingParty != null) {
            broadcast(constituency, "results.leading-party", MessageManager.placeholders(
                    "party", partyDisplay(election, leadingParty.name()),
                    "votes", String.valueOf(leadingParty.votes())));
        }

        if (winnerCandidate != null) {
            applyWinnerRewards(constituency, winnerCandidate, winnerVotes, election.getTotalVotes());
            broadcast(constituency, "winner.announce-town",
                    MessageManager.placeholders("winner", winnerCandidate.getName(),
                            "party", partyDisplay(election, winnerCandidate.getPartyName()),
                            "town", constituency.getName()));
            if (config.isBroadcastServerWide()) {
                Bukkit.broadcast(messages.prefixed(messages.raw("winner.announce-server")
                        .replace("{winner}", winnerCandidate.getName())
                        .replace("{party}", partyDisplay(election, winnerCandidate.getPartyName()))
                        .replace("{town}", constituency.getName())));
            }
        } else {
            broadcast(constituency, "results.no-winner", null);
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
                    "town", constituency.getName(),
                    "votes", String.valueOf(tally.getOrDefault(c.getUuid(), 0))));
        }
    }

    private PartyStanding leadingParty(Election election, Map<UUID, Integer> tally) {
        Map<String, Integer> partyVotes = new HashMap<>();
        for (Candidate candidate : election.getCandidateList()) {
            String party = candidate.getPartyName();
            if (party == null || party.isBlank()) {
                party = config.getDefaultPartyName();
            }
            if (config.isHideDefaultPartyFromStandings()
                    && party.equalsIgnoreCase(config.getDefaultPartyName())) {
                continue;
            }
            partyVotes.merge(party, tally.getOrDefault(candidate.getUuid(), 0), Integer::sum);
        }
        return partyVotes.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                .map(entry -> new PartyStanding(entry.getKey(), entry.getValue()))
                .findFirst()
                .orElse(null);
    }

    private record PartyStanding(String name, int votes) {
    }

    /** Grant the winner their configured Towny ranks / leadership and run commands. */
    private void applyWinnerRewards(Constituency constituency, Candidate winner, int winnerVotes, int totalVotes) {
        boolean nation = constituency.scope() == ElectionScope.NATION;
        List<String> ranks = nation ? config.getGrantNationRanks() : config.getGrantTownRanks();
        boolean setLeader = nation ? config.isSetAsKing() : config.isSetAsMayor();

        Resident winnerResident = towny.getResident(winner.getUuid());
        if (winnerResident == null) {
            plugin.getLogger().warning("Winner " + winner.getName()
                    + " has no Towny resident record; skipping rank rewards.");
        } else {
            // Optionally revoke ranks from the previous holder (the current leader).
            if (config.isRevokePreviousWinnerRanks()) {
                Resident previous = constituency.getLeader();
                if (previous != null && !previous.getUUID().equals(winnerResident.getUUID())) {
                    constituency.revokeRanks(previous, ranks);
                }
            }

            List<String> applied = constituency.grantRanks(winnerResident, ranks);
            if (!applied.isEmpty()) {
                broadcast(constituency, "winner.ranks-granted", MessageManager.placeholders(
                        "winner", winner.getName(), "ranks", String.join(", ", applied)));
            }

            if (setLeader) {
                if (constituency.setLeader(winnerResident)) {
                    broadcast(constituency, nation ? "winner.made-king" : "winner.made-mayor",
                            MessageManager.placeholders(
                                    "winner", winner.getName(), "town", constituency.getName()));
                }
            }
        }

        // Configured console commands.
        runConfiguredCommands(config.getCommandsOnWin(), MessageManager.placeholders(
                "winner", winner.getName(),
                "winner_uuid", winner.getUuid().toString(),
                "party", winner.getPartyName(),
                "winner_party", winner.getPartyName(),
                "town", constituency.getName(),
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

    private void maybeAutoSchedule(long now, boolean nationAuto) {
        if (config.isAutoScheduleEnabled()) {
            for (Town town : com.palmergames.bukkit.towny.TownyUniverse.getInstance().getTowns()) {
                maybeAutoScheduleConstituency(towny.of(town), config.getMinTownResidents(), now);
            }
        }
        if (nationAuto) {
            for (Nation nation : towny.getNations()) {
                maybeAutoScheduleConstituency(towny.of(nation), config.getMinNationResidents(), now);
            }
        }
    }

    private void maybeAutoScheduleConstituency(Constituency c, int minResidents, long now) {
        if (c == null) {
            return;
        }
        UUID uuid = c.getUuid();
        if (active.containsKey(uuid)) {
            return;
        }
        if (c.getResidentCount() < minResidents) {
            return;
        }
        long next = nextAutoStart.getOrDefault(uuid, 0L);
        if (next == 0L) {
            // First time we've seen this constituency under auto-schedule: arm the timer.
            nextAutoStart.put(uuid, now + config.getAutoScheduleIntervalMs());
            return;
        }
        if (now >= next) {
            startElection(c);
        }
    }

    private void scheduleNextAuto(UUID uuid) {
        if (config.isAutoScheduleEnabled()
                || (config.isNationElectionsEnabled() && config.isNationAutoSchedule())) {
            nextAutoStart.put(uuid, System.currentTimeMillis() + config.getAutoScheduleIntervalMs());
        }
    }

    // ========================================================================
    //  Messaging helper
    // ========================================================================

    private void broadcast(Constituency c, String key, Map<String, String> placeholders) {
        String body = messages.raw(key);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                body = body.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        for (Resident resident : c.getResidents()) {
            Player online = Bukkit.getPlayer(resident.getUUID());
            if (online != null) {
                online.sendMessage(messages.prefixed(body));
            }
        }
    }

    /**
     * The command literal for an action, prefixed with the nation sub-command
     * when the election is a nation election (e.g. {@code nation vote}).
     */
    private String subLiteral(Election election, String action) {
        String literal = commands.literal(action);
        if (election.getScope() == ElectionScope.NATION) {
            return commands.literal(CommandConfig.NATION) + " " + literal;
        }
        return literal;
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
