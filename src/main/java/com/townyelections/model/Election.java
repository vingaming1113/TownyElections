package com.townyelections.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Represents the state of a single election within one town. Instances are
 * mutable and mutated only on the main server thread by the election manager.
 */
public class Election {

    private final UUID townUuid;
    private String townName;

    private ElectionPhase phase;
    private long phaseEndsAt;
    private VotingSystem votingSystem = VotingSystem.PLURALITY;

    /** candidate uuid -> Candidate */
    private final Map<UUID, Candidate> candidates = new LinkedHashMap<>();

    /**
     * voter uuid -> that voter's ballot. A ballot is an ordered candidate list:
     * a single entry under PLURALITY, a preference ranking under RANKED_CHOICE,
     * and an unordered approval set under APPROVAL.
     */
    private final Map<UUID, List<UUID>> ballots = new ConcurrentHashMap<>();

    /** Whether a reminder for the voting phase has already been dispatched. */
    private boolean reminderSent = false;

    public Election(UUID townUuid, String townName, ElectionPhase phase, long phaseEndsAt) {
        this.townUuid = townUuid;
        this.townName = townName;
        this.phase = phase;
        this.phaseEndsAt = phaseEndsAt;
    }

    // ---- Basic accessors ---------------------------------------------------

    public UUID getTownUuid() {
        return townUuid;
    }

    public String getTownName() {
        return townName;
    }

    public void setTownName(String townName) {
        this.townName = townName;
    }

    public ElectionPhase getPhase() {
        return phase;
    }

    public void setPhase(ElectionPhase phase) {
        this.phase = phase;
    }

    public long getPhaseEndsAt() {
        return phaseEndsAt;
    }

    public void setPhaseEndsAt(long phaseEndsAt) {
        this.phaseEndsAt = phaseEndsAt;
    }

    public long getMillisRemaining() {
        return Math.max(0L, phaseEndsAt - System.currentTimeMillis());
    }

    public boolean isPhaseExpired() {
        return System.currentTimeMillis() >= phaseEndsAt;
    }

    public boolean isReminderSent() {
        return reminderSent;
    }

    public void setReminderSent(boolean reminderSent) {
        this.reminderSent = reminderSent;
    }

    public VotingSystem getVotingSystem() {
        return votingSystem;
    }

    public void setVotingSystem(VotingSystem votingSystem) {
        this.votingSystem = votingSystem == null ? VotingSystem.PLURALITY : votingSystem;
    }

    // ---- Candidates --------------------------------------------------------

    public Map<UUID, Candidate> getCandidates() {
        return candidates;
    }

    public List<Candidate> getCandidateList() {
        return new ArrayList<>(candidates.values());
    }

    public int getCandidateCount() {
        return candidates.size();
    }

    public boolean isCandidate(UUID uuid) {
        return candidates.containsKey(uuid);
    }

    public Candidate getCandidate(UUID uuid) {
        return candidates.get(uuid);
    }

    public void addCandidate(Candidate candidate) {
        candidates.put(candidate.getUuid(), candidate);
    }

    public void removeCandidate(UUID uuid) {
        candidates.remove(uuid);
        // Strip the withdrawn candidate from every ballot; drop emptied ballots.
        for (Map.Entry<UUID, List<UUID>> entry : ballots.entrySet()) {
            if (entry.getValue().contains(uuid)) {
                List<UUID> updated = new ArrayList<>(entry.getValue());
                updated.remove(uuid);
                if (updated.isEmpty()) {
                    ballots.remove(entry.getKey());
                } else {
                    ballots.put(entry.getKey(), List.copyOf(updated));
                }
            }
        }
    }

    /** Case-insensitive lookup of a candidate by resident name. */
    public Candidate findCandidateByName(String name) {
        for (Candidate candidate : candidates.values()) {
            if (candidate.getName().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return null;
    }

    /** Restrict the candidate set to the provided uuids (used to begin a runoff). */
    public void retainCandidates(List<UUID> keep) {
        candidates.keySet().retainAll(keep);
        ballots.clear();
        reminderSent = false;
    }

    // ---- Ballots -----------------------------------------------------------

    public Map<UUID, List<UUID>> getBallots() {
        return ballots;
    }

    public boolean hasVoted(UUID voter) {
        return ballots.containsKey(voter);
    }

    /** The voter's first (or only) choice, or null if they have not voted. */
    public UUID getVoteChoice(UUID voter) {
        List<UUID> ballot = ballots.get(voter);
        return ballot == null || ballot.isEmpty() ? null : ballot.get(0);
    }

    /** The voter's full ballot in cast order, or an empty list. */
    public List<UUID> getBallot(UUID voter) {
        return ballots.getOrDefault(voter, List.of());
    }

    public void castVote(UUID voter, UUID candidate) {
        castBallot(voter, List.of(candidate));
    }

    /** Replaces the voter's ballot. Entries must be existing candidates, deduped, in order. */
    public void castBallot(UUID voter, List<UUID> ballot) {
        if (ballot == null || ballot.isEmpty()) {
            ballots.remove(voter);
        } else {
            ballots.put(voter, List.copyOf(ballot));
        }
    }

    public void removeBallot(UUID voter) {
        ballots.remove(voter);
    }

    /** Number of ballots cast (one per voter regardless of system). */
    public int getTotalVotes() {
        return ballots.size();
    }

    public int getUniqueVoterCount() {
        return ballots.size();
    }

    /**
     * candidate uuid -> tally, including candidates with zero votes. Under
     * APPROVAL every approved candidate on a ballot counts; under PLURALITY and
     * RANKED_CHOICE only the ballot's first choice counts (for ranked ballots
     * this is the live first-preference tally, not the runoff outcome).
     */
    public Map<UUID, Integer> tally() {
        Map<UUID, Integer> tally = new HashMap<>();
        for (UUID candidate : candidates.keySet()) {
            tally.put(candidate, 0);
        }
        for (List<UUID> ballot : ballots.values()) {
            if (votingSystem == VotingSystem.APPROVAL) {
                for (UUID choice : ballot) {
                    tally.merge(choice, 1, Integer::sum);
                }
            } else if (!ballot.isEmpty()) {
                tally.merge(ballot.get(0), 1, Integer::sum);
            }
        }
        return tally;
    }

    /** Runs the instant-runoff count over the current ballots. */
    public InstantRunoff.Outcome instantRunoff() {
        return InstantRunoff.count(candidates.keySet(), ballots.values());
    }

    /**
     * Returns candidate uuids ordered by descending vote count. Ties preserve
     * candidate insertion (registration) order via a stable sort.
     */
    public List<UUID> rankedCandidates() {
        Map<UUID, Integer> tally = tally();
        List<UUID> ordered = new ArrayList<>(candidates.keySet());
        ordered.sort(Comparator.comparingInt((UUID id) -> tally.getOrDefault(id, 0)).reversed());
        return ordered;
    }

    /**
     * Returns the uuids sharing the highest vote total (the potential winners).
     * A list larger than one indicates a tie.
     */
    public List<UUID> topCandidates() {
        Map<UUID, Integer> tally = tally();
        int max = tally.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return tally.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // ---- Serialization -----------------------------------------------------

    public void serialize(ConfigurationSection section) {
        section.set("town-uuid", townUuid.toString());
        section.set("town-name", townName);
        section.set("phase", phase.name());
        section.set("phase-ends-at", phaseEndsAt);
        section.set("voting-system", votingSystem.name());
        section.set("reminder-sent", reminderSent);

        ConfigurationSection candidatesSection = section.createSection("candidates");
        int i = 0;
        for (Candidate candidate : candidates.values()) {
            candidate.serialize(candidatesSection.createSection("c" + (i++)));
        }

        ConfigurationSection votesSection = section.createSection("votes");
        for (Map.Entry<UUID, List<UUID>> entry : ballots.entrySet()) {
            List<String> choices = entry.getValue().stream().map(UUID::toString).toList();
            votesSection.set(entry.getKey().toString(), choices);
        }
    }

    public static Election deserialize(ConfigurationSection section) {
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
        ElectionPhase phase;
        try {
            phase = ElectionPhase.valueOf(section.getString("phase", "NOMINATION"));
        } catch (IllegalArgumentException ex) {
            phase = ElectionPhase.NOMINATION;
        }
        long phaseEndsAt = section.getLong("phase-ends-at", System.currentTimeMillis());

        Election election = new Election(townUuid, townName, phase, phaseEndsAt);
        election.votingSystem = VotingSystem.fromString(
                section.getString("voting-system"), VotingSystem.PLURALITY);
        election.reminderSent = section.getBoolean("reminder-sent", false);

        ConfigurationSection candidatesSection = section.getConfigurationSection("candidates");
        if (candidatesSection != null) {
            for (String key : candidatesSection.getKeys(false)) {
                Candidate candidate = Candidate.deserialize(candidatesSection.getConfigurationSection(key));
                if (candidate != null) {
                    election.candidates.put(candidate.getUuid(), candidate);
                }
            }
        }

        ConfigurationSection votesSection = section.getConfigurationSection("votes");
        if (votesSection != null) {
            for (String key : votesSection.getKeys(false)) {
                UUID voter;
                try {
                    voter = UUID.fromString(key);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                // Ballots are stored as string lists; pre-ballot data files
                // stored a single candidate uuid string per voter.
                List<String> rawChoices = votesSection.isList(key)
                        ? votesSection.getStringList(key)
                        : List.of(votesSection.getString(key, ""));
                List<UUID> ballot = new ArrayList<>();
                for (String rawChoice : rawChoices) {
                    try {
                        UUID choice = UUID.fromString(rawChoice);
                        // Only keep votes for candidates that still exist.
                        if (election.candidates.containsKey(choice) && !ballot.contains(choice)) {
                            ballot.add(choice);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Skip malformed vote entries.
                    }
                }
                if (!ballot.isEmpty()) {
                    election.ballots.put(voter, List.copyOf(ballot));
                }
            }
        }
        return election;
    }
}
