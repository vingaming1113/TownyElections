package com.townyelections.integration;

import com.palmergames.bukkit.towny.object.Town;
import com.townyelections.TownyElections;
import com.townyelections.model.Candidate;
import com.townyelections.model.Election;
import com.townyelections.model.ElectionResult;
import com.townyelections.util.DurationUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PlaceholderAPI expansion exposing election information for a player's town.
 *
 * <p>Available placeholders (identifier: {@code townyelections}):
 * <ul>
 *   <li>%townyelections_phase% - current phase, or "none"</li>
 *   <li>%townyelections_voting_system% - electoral system of the active election, or "none"</li>
 *   <li>%townyelections_time_left% - readable time until the phase ends</li>
 *   <li>%townyelections_candidates% - number of candidates</li>
 *   <li>%townyelections_votes% - number of votes cast</li>
 *   <li>%townyelections_has_voted% - true/false whether the player voted</li>
 *   <li>%townyelections_my_party% - party of the viewing player, if they are a candidate</li>
 *   <li>%townyelections_leading_party% - leading party in the current town election</li>
 *   <li>%townyelections_last_winner% - name of the last winner in their town</li>
 *   <li>%townyelections_last_winner_party% - party of the last winner in their town</li>
 * </ul>
 */
public class ElectionsPlaceholderExpansion extends PlaceholderExpansion {

    private final TownyElections plugin;

    public ElectionsPlaceholderExpansion(TownyElections plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "townyelections";
    }

    @Override
    public @NotNull String getAuthor() {
        return "TownyElections";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        com.palmergames.bukkit.towny.object.Resident resident =
                plugin.getTownyHook().getResident(player.getUniqueId());
        Town town = (resident != null && resident.hasTown()) ? resident.getTownOrNull() : null;

        Election election = town == null ? null : plugin.getElectionManager().getElection(town);

        switch (params.toLowerCase()) {
            case "phase":
                return election == null ? "none" : election.getPhase().name().toLowerCase();
            case "voting_system":
                return election == null ? "none" : election.getVotingSystem().name().toLowerCase();
            case "time_left":
                return election == null ? "0s" : DurationUtil.format(election.getMillisRemaining());
            case "candidates":
                return election == null ? "0" : String.valueOf(election.getCandidateCount());
            case "votes":
                return election == null ? "0" : String.valueOf(election.getTotalVotes());
            case "has_voted":
                return (election != null && election.hasVoted(player.getUniqueId())) ? "true" : "false";
            case "my_party": {
                if (election == null) {
                    return "";
                }
                Candidate candidate = election.getCandidate(player.getUniqueId());
                return candidate == null ? "" : candidate.getPartyName();
            }
            case "leading_party":
                return election == null ? "" : leadingParty(election);
            case "last_winner": {
                ElectionResult result = getLastResult(town);
                return result == null || !result.hasWinner() ? "" : result.getWinnerName();
            }
            case "last_winner_party": {
                ElectionResult result = getLastResult(town);
                if (result == null || !result.hasWinner()) {
                    return "";
                }
                return result.getStandings().stream()
                        .filter(standing -> standing.uuid.equals(result.getWinnerUuid()))
                        .map(standing -> standing.partyName)
                        .findFirst()
                        .orElse(plugin.getConfigManager().getDefaultPartyName());
            }
            default:
                return null;
        }
    }

    private String leadingParty(Election election) {
        Map<String, Integer> partyScores = new HashMap<>();
        Map<UUID, Integer> tally = election.tally();
        boolean rankByVotes = plugin.getConfigManager().isPublicLiveResults();

        for (Candidate candidate : election.getCandidateList()) {
            String party = candidate.getPartyName();
            if (party == null || party.isBlank()) {
                party = plugin.getConfigManager().getDefaultPartyName();
            }
            int score = rankByVotes ? tally.getOrDefault(candidate.getUuid(), 0) : 1;
            partyScores.merge(party, score, Integer::sum);
        }

        return partyScores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("");
    }

    private ElectionResult getLastResult(Town town) {
        return town == null ? null : plugin.getElectionManager().getLastResult(town.getUUID());
    }
}
