package com.townyelections.commands;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.townyelections.TownyElections;
import com.townyelections.integration.Constituency;
import com.townyelections.integration.TownyHook;
import com.townyelections.manager.CommandConfig;
import com.townyelections.manager.ConfigManager;
import com.townyelections.manager.ElectionManager;
import com.townyelections.manager.MessageManager;
import com.townyelections.manager.OperationResult;
import com.townyelections.model.Candidate;
import com.townyelections.model.Election;
import com.townyelections.model.ElectionPhase;
import com.townyelections.model.ElectionResult;
import com.townyelections.model.ElectionScope;
import com.townyelections.model.VotingSystem;
import com.townyelections.util.DurationUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single dispatcher for {@code /election <sub-command>}. Sub-command literals
 * are configurable, so we translate the typed literal back to an internal
 * action key before dispatching.
 *
 * <p>Prefixing any sub-command with the {@code nation} literal (e.g.
 * {@code /election nation vote Alice}) targets the player's nation instead of
 * their town; every resident of every town in the nation may take part.
 */
public class ElectionCommand implements CommandExecutor, TabCompleter {

    private final TownyElections plugin;
    private final ElectionManager elections;
    private final MessageManager messages;
    private final CommandConfig commands;
    private final ConfigManager config;
    private final TownyHook towny;

    public ElectionCommand(TownyElections plugin) {
        this.plugin = plugin;
        this.elections = plugin.getElectionManager();
        this.messages = plugin.getMessageManager();
        this.commands = plugin.getCommandConfig();
        this.config = plugin.getConfigManager();
        this.towny = plugin.getTownyHook();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                plugin.getElectionMenu().openMain(player);
                return true;
            }
            sendHelp(sender, label);
            return true;
        }

        String action = commands.actionFor(args[0]);
        if (action == null) {
            messages.send(sender, "general.unknown-command", MessageManager.placeholders(
                    "label", label, "help", commands.literal(CommandConfig.HELP)));
            return true;
        }

        ElectionScope scope = ElectionScope.TOWN;
        if (action.equals(CommandConfig.NATION)) {
            if (!config.isNationElectionsEnabled()) {
                messages.send(sender, "nation.disabled");
                return true;
            }
            scope = ElectionScope.NATION;
            if (args.length < 2) {
                sendHelp(sender, label);
                return true;
            }
            args = Arrays.copyOfRange(args, 1, args.length);
            action = commands.actionFor(args[0]);
            if (action == null || action.equals(CommandConfig.NATION)) {
                messages.send(sender, "general.unknown-command", MessageManager.placeholders(
                        "label", label, "help", commands.literal(CommandConfig.HELP)));
                return true;
            }
        }

        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (action) {
            case CommandConfig.HELP -> sendHelp(sender, label);
            case CommandConfig.RUN -> handleRun(sender, scope);
            case CommandConfig.WITHDRAW -> handleWithdraw(sender, scope);
            case CommandConfig.CAMPAIGN -> handleCampaign(sender, rest, label, scope);
            case CommandConfig.PROFILE -> handleProfile(sender, rest, label, scope);
            case CommandConfig.PARTY -> handleParty(sender, rest, label, scope);
            case CommandConfig.PARTIES -> handleParties(sender, scope);
            case CommandConfig.VOTE -> handleVote(sender, rest, label, scope);
            case CommandConfig.STATUS -> handleStatus(sender, label, scope);
            case CommandConfig.CANDIDATES -> handleCandidates(sender, label, scope);
            case CommandConfig.RESULTS -> handleResults(sender, scope);
            case CommandConfig.START -> handleAdmin(sender, rest, CommandConfig.START, scope);
            case CommandConfig.STOP -> handleAdmin(sender, rest, CommandConfig.STOP, scope);
            case CommandConfig.CANCEL -> handleAdmin(sender, rest, CommandConfig.CANCEL, scope);
            case CommandConfig.RELOAD -> handleReload(sender);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    // ---- Player context helper --------------------------------------------

    /** Resolves the sender to (Player, Resident, Constituency). Sends errors and returns null on failure. */
    private PlayerContext resolveContext(CommandSender sender, ElectionScope scope) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only");
            return null;
        }
        Resident resident = towny.getResident(player);
        if (resident == null) {
            messages.send(sender, "general.not-a-resident");
            return null;
        }
        if (scope == ElectionScope.NATION) {
            Nation nation = towny.getPlayerNation(player);
            if (nation == null) {
                messages.send(sender, "general.no-nation");
                return null;
            }
            return new PlayerContext(player, resident, towny.of(nation));
        }
        Town town = towny.getPlayerTown(player);
        if (town == null) {
            messages.send(sender, "general.no-town");
            return null;
        }
        return new PlayerContext(player, resident, towny.of(town));
    }

    private record PlayerContext(Player player, Resident resident, Constituency constituency) {
    }

    private Election electionOf(PlayerContext ctx) {
        return elections.getElection(ctx.constituency().getUuid());
    }

    /** The command literal for an action, prefixed with the nation token when needed. */
    private String literal(String action, ElectionScope scope) {
        String lit = commands.literal(action);
        return scope == ElectionScope.NATION ? commands.literal(CommandConfig.NATION) + " " + lit : lit;
    }

    // ---- Candidacy ---------------------------------------------------------

    private void handleRun(CommandSender sender, ElectionScope scope) {
        if (!sender.hasPermission("townyelections.candidate")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender, scope);
        if (ctx == null) {
            return;
        }
        respond(sender, elections.registerCandidate(ctx.resident(), ctx.constituency()),
                MessageManager.placeholders(
                        "town", ctx.constituency().getName(),
                        "max", String.valueOf(config.getMaxCandidates())));
    }

    private void handleWithdraw(CommandSender sender, ElectionScope scope) {
        if (!sender.hasPermission("townyelections.candidate")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender, scope);
        if (ctx == null) {
            return;
        }
        respond(sender, elections.withdrawCandidate(ctx.resident(), ctx.constituency()),
                MessageManager.placeholders("town", ctx.constituency().getName()));
    }

    private void handleCampaign(CommandSender sender, String[] rest, String label, ElectionScope scope) {
        if (!sender.hasPermission("townyelections.candidate")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender, scope);
        if (ctx == null) {
            return;
        }
        if (rest.length == 0) {
            messages.send(sender, "campaign.empty", MessageManager.placeholders(
                    "label", label, "campaign", literal(CommandConfig.CAMPAIGN, scope)));
            return;
        }
        String message = String.join(" ", rest);
        respond(sender, elections.setCampaignMessage(ctx.resident(), ctx.constituency(), message),
                MessageManager.placeholders("max", String.valueOf(config.getMaxMessageLength())));
    }

    private void handleProfile(CommandSender sender, String[] rest, String label, ElectionScope scope) {
        if (!sender.hasPermission("townyelections.candidate")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender, scope);
        if (ctx == null) {
            return;
        }
        Election election = electionOf(ctx);
        Candidate candidate = election == null ? null : election.getCandidate(ctx.resident().getUUID());
        if (candidate == null) {
            messages.send(sender, "candidate.not-a-candidate");
            return;
        }
        if (rest.length == 0) {
            messages.sendNoPrefix(sender, "profile.header",
                    MessageManager.placeholders("candidate", candidate.getName()));
            messages.sendNoPrefix(sender, "profile.body", MessageManager.placeholders(
                    "profile", displayProfile(candidate)));
            messages.sendNoPrefix(sender, "profile.usage", MessageManager.placeholders(
                    "label", label,
                    "profile", literal(CommandConfig.PROFILE, scope)));
            return;
        }
        String profile = String.join(" ", rest);
        respond(sender, elections.setCandidateProfile(ctx.resident(), ctx.constituency(), profile),
                MessageManager.placeholders("max", String.valueOf(config.getMaxProfileLength())));
    }

    private void handleParty(CommandSender sender, String[] rest, String label, ElectionScope scope) {
        if (rest.length > 0 && rest[0].equalsIgnoreCase("rename")) {
            if (!sender.hasPermission("townyelections.admin")) {
                messages.send(sender, "general.no-permission");
                return;
            }
            PlayerContext ctx = resolveContext(sender, scope);
            if (ctx == null) {
                return;
            }
            handlePartyRename(sender, ctx, rest, label, scope);
            return;
        }
        if (!sender.hasPermission("townyelections.candidate")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender, scope);
        if (ctx == null) {
            return;
        }
        if (rest.length == 0) {
            Election election = electionOf(ctx);
            Candidate candidate = election == null ? null : election.getCandidate(ctx.resident().getUUID());
            if (candidate == null) {
                messages.send(sender, "candidate.not-a-candidate");
                return;
            }
            messages.send(sender, "party.current", MessageManager.placeholders(
                    "party", candidate.getPartyName(),
                    "label", label,
                    "party_command", literal(CommandConfig.PARTY, scope)));
            return;
        }
        if (rest.length == 1 && rest[0].equalsIgnoreCase("leave")) {
            respond(sender, elections.leaveParty(ctx.resident(), ctx.constituency()),
                    MessageManager.placeholders("party", config.getDefaultPartyName()));
            return;
        }
        String partyName = String.join(" ", rest);
        respond(sender, elections.setPartyName(ctx.resident(), ctx.constituency(), partyName),
                MessageManager.placeholders(
                        "party", partyName.trim(),
                        "max", String.valueOf(config.getMaxPartyNameLength())));
    }

    private void handlePartyRename(CommandSender sender, PlayerContext ctx, String[] rest, String label,
                                   ElectionScope scope) {
        if (!sender.hasPermission("townyelections.admin")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (rest.length < 3) {
            messages.send(sender, "party.rename-usage", MessageManager.placeholders(
                    "label", label, "party", literal(CommandConfig.PARTY, scope)));
            return;
        }
        String oldName = rest[1];
        String newName = String.join(" ", Arrays.copyOfRange(rest, 2, rest.length));
        if (oldName.isBlank() || newName.isBlank()) {
            messages.send(sender, "party.rename-usage", MessageManager.placeholders(
                    "label", label, "party", literal(CommandConfig.PARTY, scope)));
            return;
        }
        respond(sender, elections.renameParty(ctx.constituency(), oldName, newName), MessageManager.placeholders(
                "old", oldName,
                "new", newName.trim(),
                "max", String.valueOf(config.getMaxPartyNameLength())));
    }

    // ---- Voting ------------------------------------------------------------

    private void handleVote(CommandSender sender, String[] rest, String label, ElectionScope scope) {
        if (!sender.hasPermission("townyelections.vote")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender, scope);
        if (ctx == null) {
            return;
        }
        Election election = electionOf(ctx);
        VotingSystem system = election == null ? config.getVotingSystem() : election.getVotingSystem();
        if (rest.length == 0) {
            messages.send(sender, voteUsageKey(system), MessageManager.placeholders(
                    "label", label, "vote", literal(CommandConfig.VOTE, scope)));
            return;
        }

        OperationResult result;
        Map<String, String> ph = MessageManager.placeholders(
                "town", ctx.constituency().getName(),
                "name", String.join(" ", rest));
        if (system == VotingSystem.PLURALITY) {
            result = elections.castVote(ctx.resident(), ctx.constituency(), String.join(" ", rest));
        } else {
            result = elections.castBallot(ctx.resident(), ctx.constituency(), Arrays.asList(rest));
        }
        if (result.getPayload() instanceof String payload) {
            if (result.isSuccess()) {
                ph.put("candidate", payload);
                ph.put("ballot", payload);
            } else {
                ph.put("name", payload);
            }
        }
        if (!result.isSuccess() && "vote.usage".equals(result.getMessageKey())) {
            messages.send(sender, voteUsageKey(system), MessageManager.placeholders(
                    "label", label, "vote", literal(CommandConfig.VOTE, scope)));
            return;
        }
        respond(sender, result, ph);
    }

    private String voteUsageKey(VotingSystem system) {
        return switch (system) {
            case RANKED_CHOICE -> "vote.usage-ranked";
            case APPROVAL -> "vote.usage-approval";
            default -> "vote.usage";
        };
    }

    // ---- Info --------------------------------------------------------------

    private void handleStatus(CommandSender sender, String label, ElectionScope scope) {
        if (!sender.hasPermission("townyelections.info")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender, scope);
        if (ctx == null) {
            return;
        }
        Election election = electionOf(ctx);
        if (election == null) {
            messages.send(sender, "election.none-active");
            return;
        }

        messages.sendNoPrefix(sender, "status.header",
                MessageManager.placeholders("town", ctx.constituency().getName()));

        String phaseLabel = switch (election.getPhase()) {
            case NOMINATION -> messages.raw("election.phase-nomination");
            case VOTING -> messages.raw("election.phase-voting");
            case RUNOFF -> messages.raw("election.phase-runoff");
            default -> election.getPhase().name();
        };
        messages.sendNoPrefix(sender, "status.phase", MessageManager.placeholders("phase", phaseLabel));
        messages.sendNoPrefix(sender, "status.voting-system", MessageManager.placeholders(
                "system", messages.raw(election.getVotingSystem().messageKey())));

        String timeKey = election.getPhase() == ElectionPhase.NOMINATION
                ? "election.time-remaining-nomination" : "election.time-remaining-voting";
        messages.sendNoPrefix(sender, timeKey,
                MessageManager.placeholders("time", DurationUtil.format(election.getMillisRemaining())));

        messages.sendNoPrefix(sender, "status.candidates-count",
                MessageManager.placeholders("count", String.valueOf(election.getCandidateCount())));
        messages.sendNoPrefix(sender, "status.votes-count",
                MessageManager.placeholders("votes", String.valueOf(election.getTotalVotes())));

        // Show the viewer's own ballot.
        List<UUID> ballot = election.getBallot(ctx.resident().getUUID());
        if (ballot.isEmpty()) {
            messages.sendNoPrefix(sender, "status.your-vote-none", null);
        } else if (election.getVotingSystem() == VotingSystem.PLURALITY) {
            Candidate c = election.getCandidate(ballot.get(0));
            messages.sendNoPrefix(sender, "status.your-vote",
                    MessageManager.placeholders(
                            "choice", c == null ? "?" : c.getName(),
                            "party", c == null ? "?" : c.getPartyName()));
        } else {
            messages.sendNoPrefix(sender, "status.your-ballot", MessageManager.placeholders(
                    "ballot", elections.describeBallot(election, ctx.resident().getUUID())));
        }

        printCandidateList(sender, election, label, scope);
    }

    private void handleCandidates(CommandSender sender, String label, ElectionScope scope) {
        if (!sender.hasPermission("townyelections.info")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender, scope);
        if (ctx == null) {
            return;
        }
        Election election = electionOf(ctx);
        if (election == null) {
            messages.send(sender, "election.none-active");
            return;
        }
        printCandidateList(sender, election, label, scope);
    }

    private void printCandidateList(CommandSender sender, Election election, String label, ElectionScope scope) {
        messages.sendNoPrefix(sender, "status.candidate-list-header",
                MessageManager.placeholders("label", label, "vote", literal(CommandConfig.VOTE, scope)));

        boolean showVotes = config.isPublicLiveResults()
                || election.getPhase() == ElectionPhase.CONCLUDED;
        Map<UUID, Integer> tally = election.tally();
        for (Candidate c : election.getCandidateList()) {
            if (showVotes) {
                messages.sendNoPrefix(sender, "status.candidate-entry", MessageManager.placeholders(
                        "candidate", c.getName(),
                        "party", c.getPartyName(),
                        "votes", String.valueOf(tally.getOrDefault(c.getUuid(), 0)),
                        "message", c.getCampaignMessage(),
                        "profile", displayProfile(c)));
            } else {
                messages.sendNoPrefix(sender, "status.candidate-entry-hidden", MessageManager.placeholders(
                        "candidate", c.getName(),
                        "party", c.getPartyName(),
                        "message", c.getCampaignMessage(),
                        "profile", displayProfile(c)));
            }
        }
    }

    private void handleParties(CommandSender sender, ElectionScope scope) {
        if (!sender.hasPermission("townyelections.info")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender, scope);
        if (ctx == null) {
            return;
        }
        Election election = electionOf(ctx);
        if (election == null) {
            messages.send(sender, "election.none-active");
            return;
        }
        printPartyList(sender, election, ctx.constituency().getName());
    }

    private void printPartyList(CommandSender sender, Election election, String townName) {
        Map<String, List<String>> partyCandidates = new LinkedHashMap<>();
        Map<String, Integer> partyVotes = new LinkedHashMap<>();
        Map<UUID, Integer> tally = election.tally();

        for (Candidate candidate : election.getCandidateList()) {
            String party = candidate.getPartyName();
            if (party == null || party.isBlank()) {
                party = config.getDefaultPartyName();
            }
            if (config.isHideDefaultPartyFromStandings()
                    && party.equalsIgnoreCase(config.getDefaultPartyName())) {
                continue;
            }
            partyCandidates.computeIfAbsent(party, ignored -> new ArrayList<>()).add(candidate.getName());
            partyVotes.merge(party, tally.getOrDefault(candidate.getUuid(), 0), Integer::sum);
        }

        messages.sendNoPrefix(sender, "parties.header", MessageManager.placeholders("town", townName));
        if (partyCandidates.isEmpty()) {
            messages.sendNoPrefix(sender, "parties.none", null);
            return;
        }

        boolean showVotes = config.isPublicLiveResults()
                || election.getPhase() == ElectionPhase.CONCLUDED;
        for (String party : rankedParties(partyCandidates, partyVotes, showVotes)) {
            List<String> candidates = partyCandidates.get(party);
            Map<String, String> placeholders = MessageManager.placeholders(
                    "party", party,
                    "count", String.valueOf(candidates.size()),
                    "candidates", String.join(", ", candidates),
                    "votes", String.valueOf(partyVotes.getOrDefault(party, 0)));
            messages.sendNoPrefix(sender, showVotes ? "parties.entry" : "parties.entry-hidden", placeholders);
        }
    }

    private void printRunoffRounds(CommandSender sender, ElectionResult result) {
        if (result.getRounds().isEmpty()) {
            return;
        }
        for (ElectionResult.Round round : result.getRounds()) {
            messages.sendNoPrefix(sender, "results.round-header",
                    MessageManager.placeholders("round", String.valueOf(round.number())));
            for (ElectionResult.RoundEntry entry : round.entries()) {
                messages.sendNoPrefix(sender, "results.round-line", MessageManager.placeholders(
                        "candidate", entry.name(),
                        "votes", String.valueOf(entry.votes())));
            }
            if (!round.eliminated().isEmpty()) {
                messages.sendNoPrefix(sender, "results.round-eliminated", MessageManager.placeholders(
                        "candidates", String.join(", ", round.eliminated())));
            }
            if (round.exhausted() > 0) {
                messages.sendNoPrefix(sender, "results.round-exhausted", MessageManager.placeholders(
                        "count", String.valueOf(round.exhausted())));
            }
        }
    }

    private void printResultPartyList(CommandSender sender, ElectionResult result) {
        Map<String, List<String>> partyCandidates = new LinkedHashMap<>();
        Map<String, Integer> partyVotes = new LinkedHashMap<>();

        for (ElectionResult.Standing standing : result.getStandings()) {
            String party = standing.partyName == null || standing.partyName.isBlank()
                    ? config.getDefaultPartyName() : standing.partyName;
            if (config.isHideDefaultPartyFromStandings()
                    && party.equalsIgnoreCase(config.getDefaultPartyName())) {
                continue;
            }
            partyCandidates.computeIfAbsent(party, ignored -> new ArrayList<>()).add(standing.name);
            partyVotes.merge(party, standing.votes, Integer::sum);
        }

        messages.sendNoPrefix(sender, "parties.result-header",
                MessageManager.placeholders("town", result.getTownName()));
        if (partyCandidates.isEmpty()) {
            messages.sendNoPrefix(sender, "parties.none", null);
            return;
        }
        for (String party : rankedParties(partyCandidates, partyVotes, true)) {
            List<String> candidates = partyCandidates.get(party);
            messages.sendNoPrefix(sender, "parties.entry", MessageManager.placeholders(
                    "party", party,
                    "count", String.valueOf(candidates.size()),
                    "candidates", String.join(", ", candidates),
                    "votes", String.valueOf(partyVotes.getOrDefault(party, 0))));
        }
    }

    private List<String> rankedParties(Map<String, List<String>> partyCandidates,
                                       Map<String, Integer> partyVotes, boolean rankByVotes) {
        List<String> parties = new ArrayList<>(partyCandidates.keySet());
        if (rankByVotes) {
            parties.sort(Comparator.comparingInt((String party) -> partyVotes.getOrDefault(party, 0))
                    .reversed()
                    .thenComparing(String.CASE_INSENSITIVE_ORDER));
        } else {
            parties.sort(String.CASE_INSENSITIVE_ORDER);
        }
        return parties;
    }

    private List<String> currentPartyNames(Election election) {
        Map<String, List<String>> parties = new LinkedHashMap<>();
        for (Candidate candidate : election.getCandidateList()) {
            String party = candidate.getPartyName();
            if (party == null || party.isBlank()) {
                party = config.getDefaultPartyName();
            }
            parties.putIfAbsent(party, List.of());
        }
        List<String> names = new ArrayList<>(parties.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private void handleResults(CommandSender sender, ElectionScope scope) {
        if (!sender.hasPermission("townyelections.info")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender, scope);
        if (ctx == null) {
            return;
        }
        ElectionResult result = elections.getLastResult(ctx.constituency().getUuid());
        if (result == null) {
            messages.send(sender, "results.none-recorded");
            return;
        }

        messages.sendNoPrefix(sender, "results.header",
                MessageManager.placeholders("town", result.getTownName()));
        messages.sendNoPrefix(sender, "results.voting-system", MessageManager.placeholders(
                "system", messages.raw(result.getVotingSystem().messageKey())));

        int rank = 1;
        int total = Math.max(1, result.getTotalVotes());
        for (ElectionResult.Standing standing : result.getStandings()) {
            int percent = (int) Math.round((standing.votes * 100.0) / total);
            messages.sendNoPrefix(sender, "results.line", MessageManager.placeholders(
                    "rank", String.valueOf(rank++),
                    "candidate", standing.name,
                    "party", standing.partyName,
                    "votes", String.valueOf(standing.votes),
                    "percent", String.valueOf(percent)));
        }

        printRunoffRounds(sender, result);
        printResultPartyList(sender, result);

        if (result.hasWinner()) {
            String winnerParty = result.getStandings().stream()
                    .filter(standing -> standing.uuid.equals(result.getWinnerUuid()))
                    .map(standing -> standing.partyName)
                    .findFirst()
                    .orElse(config.getDefaultPartyName());
            messages.sendNoPrefix(sender, "results.winner", MessageManager.placeholders(
                    "winner", result.getWinnerName(),
                    "party", winnerParty,
                    "votes", String.valueOf(result.getWinnerVotes())));
        } else {
            messages.sendNoPrefix(sender, "results.no-winner", null);
        }
        messages.sendNoPrefix(sender, "results.total-votes",
                MessageManager.placeholders("total", String.valueOf(result.getTotalVotes())));
        int residents = Math.max(1, result.getResidentCount());
        int turnout = (int) Math.round((result.getTotalVotes() * 100.0) / residents);
        messages.sendNoPrefix(sender, "results.turnout", MessageManager.placeholders(
                "voters", String.valueOf(result.getTotalVotes()),
                "residents", String.valueOf(result.getResidentCount()),
                "percent", String.valueOf(turnout)));
    }

    // ---- Admin -------------------------------------------------------------

    private void handleAdmin(CommandSender sender, String[] rest, String action, ElectionScope scope) {
        if (!sender.hasPermission("townyelections.admin")) {
            messages.send(sender, "general.no-permission");
            return;
        }

        Constituency target = resolveAdminTarget(sender, rest, scope);
        if (target == null) {
            return;
        }

        OperationResult result = switch (action) {
            case CommandConfig.START -> elections.startElection(target);
            case CommandConfig.STOP -> elections.stopElection(target);
            case CommandConfig.CANCEL -> elections.cancelElection(target);
            default -> OperationResult.fail("general.unknown-command");
        };
        int min = scope == ElectionScope.NATION ? config.getMinNationResidents() : config.getMinTownResidents();
        respond(sender, result, MessageManager.placeholders(
                "town", target.getName(),
                "min", String.valueOf(min)));
    }

    /** Resolve the town/nation an admin command targets, or null (with an error sent). */
    private Constituency resolveAdminTarget(CommandSender sender, String[] rest, ElectionScope scope) {
        if (scope == ElectionScope.NATION) {
            if (rest.length > 0) {
                Nation nation = towny.getNationByName(rest[0]);
                if (nation == null) {
                    messages.send(sender, "admin.nation-not-found",
                            MessageManager.placeholders("nation", rest[0]));
                    return null;
                }
                return towny.of(nation);
            }
            if (!(sender instanceof Player player)) {
                messages.send(sender, "admin.nation-not-found", MessageManager.placeholders("nation", "?"));
                return null;
            }
            Nation nation = towny.getPlayerNation(player);
            if (nation == null) {
                messages.send(sender, "general.no-nation");
                return null;
            }
            return towny.of(nation);
        }

        if (rest.length > 0) {
            Town town = towny.getTownByName(rest[0]);
            if (town == null) {
                messages.send(sender, "admin.town-not-found",
                        MessageManager.placeholders("town", rest[0]));
                return null;
            }
            return towny.of(town);
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "admin.town-not-found", MessageManager.placeholders("town", "?"));
            return null;
        }
        Town town = towny.getPlayerTown(player);
        if (town == null) {
            messages.send(sender, "general.no-town");
            return null;
        }
        return towny.of(town);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("townyelections.admin")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        plugin.reloadAll();
        messages.send(sender, "general.reloaded");
    }

    // ---- Helpers -----------------------------------------------------------

    private void respond(CommandSender sender, OperationResult result, Map<String, String> placeholders) {
        messages.send(sender, result.getMessageKey(), placeholders);
    }

    private void sendHelp(CommandSender sender, String label) {
        Map<String, String> base = MessageManager.placeholders(
                "label", label,
                "run", commands.literal(CommandConfig.RUN),
                "withdraw", commands.literal(CommandConfig.WITHDRAW),
                "campaign", commands.literal(CommandConfig.CAMPAIGN),
                "profile", commands.literal(CommandConfig.PROFILE),
                "party", commands.literal(CommandConfig.PARTY),
                "parties", commands.literal(CommandConfig.PARTIES),
                "vote", commands.literal(CommandConfig.VOTE),
                "status", commands.literal(CommandConfig.STATUS),
                "candidates", commands.literal(CommandConfig.CANDIDATES),
                "results", commands.literal(CommandConfig.RESULTS),
                "start", commands.literal(CommandConfig.START),
                "stop", commands.literal(CommandConfig.STOP),
                "cancel", commands.literal(CommandConfig.CANCEL),
                "reload", commands.literal(CommandConfig.RELOAD),
                "nation", commands.literal(CommandConfig.NATION));

        messages.sendNoPrefix(sender, "help.header", null);
        messages.sendNoPrefix(sender, "help.run", base);
        messages.sendNoPrefix(sender, "help.withdraw", base);
        messages.sendNoPrefix(sender, "help.campaign", base);
        messages.sendNoPrefix(sender, "help.profile", base);
        messages.sendNoPrefix(sender, "help.party", base);
        messages.sendNoPrefix(sender, "help.parties", base);
        messages.sendNoPrefix(sender, "help.vote", base);
        messages.sendNoPrefix(sender, "help.status", base);
        messages.sendNoPrefix(sender, "help.candidates", base);
        messages.sendNoPrefix(sender, "help.results", base);
        if (config.isNationElectionsEnabled()) {
            messages.sendNoPrefix(sender, "help.nation", base);
        }
        if (sender.hasPermission("townyelections.admin")) {
            messages.sendNoPrefix(sender, "help.start", base);
            messages.sendNoPrefix(sender, "help.stop", base);
            messages.sendNoPrefix(sender, "help.cancel", base);
            messages.sendNoPrefix(sender, "help.reload", base);
        }
    }

    // ---- Tab completion ----------------------------------------------------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        // Strip a leading nation prefix so the remaining completion mirrors town scope.
        ElectionScope scope = ElectionScope.TOWN;
        if (args.length >= 1 && CommandConfig.NATION.equals(commands.actionFor(args[0]))
                && config.isNationElectionsEnabled()) {
            if (args.length == 1) {
                return topLevelCompletions(sender, args[0].toLowerCase());
            }
            scope = ElectionScope.NATION;
            args = Arrays.copyOfRange(args, 1, args.length);
        }

        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            return topLevelCompletions(sender, args[0].toLowerCase());
        }

        if (args.length >= 2) {
            String action = commands.actionFor(args[0]);
            if (action == null) {
                return out;
            }
            String partial = args[args.length - 1].toLowerCase();
            // Suggest candidate names for voting.
            if (CommandConfig.VOTE.equals(action) && sender instanceof Player player) {
                Election election = playerElection(player, scope);
                if (election != null
                        && (args.length == 2 || election.getVotingSystem() != VotingSystem.PLURALITY)) {
                    List<String> alreadyTyped = Arrays.asList(args).subList(1, args.length - 1);
                    for (Candidate c : election.getCandidateList()) {
                        boolean used = alreadyTyped.stream().anyMatch(c.getName()::equalsIgnoreCase);
                        if (!used && c.getName().toLowerCase().startsWith(partial)) {
                            out.add(c.getName());
                        }
                    }
                }
            }
            // Suggest existing party names when choosing an affiliation.
            if (CommandConfig.PARTY.equals(action) && sender instanceof Player player) {
                Election election = playerElection(player, scope);
                if (election != null) {
                    String partyInput = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase();
                    for (String party : currentPartyNames(election)) {
                        if (party.toLowerCase().startsWith(partyInput)) {
                            out.add(party);
                        }
                    }
                }
            }
            // Suggest town/nation names for admin commands.
            if (args.length == 2 && isAdminAction(action) && sender.hasPermission("townyelections.admin")) {
                if (scope == ElectionScope.NATION) {
                    for (Nation nation : towny.getNations()) {
                        if (nation.getName().toLowerCase().startsWith(partial)) {
                            out.add(nation.getName());
                        }
                    }
                } else {
                    for (Town town : com.palmergames.bukkit.towny.TownyUniverse.getInstance().getTowns()) {
                        if (town.getName().toLowerCase().startsWith(partial)) {
                            out.add(town.getName());
                        }
                    }
                }
            }
        }
        return out;
    }

    private List<String> topLevelCompletions(CommandSender sender, String partial) {
        List<String> out = new ArrayList<>();
        for (String actionKey : commands.getActions()) {
            if (CommandConfig.NATION.equals(actionKey) && !config.isNationElectionsEnabled()) {
                continue;
            }
            if (isAdminAction(actionKey) && !sender.hasPermission("townyelections.admin")) {
                continue;
            }
            String lit = commands.literal(actionKey);
            if (lit.toLowerCase().startsWith(partial)) {
                out.add(lit);
            }
        }
        return out;
    }

    private Election playerElection(Player player, ElectionScope scope) {
        if (scope == ElectionScope.NATION) {
            Nation nation = towny.getPlayerNation(player);
            return nation == null ? null : elections.getElection(nation.getUUID());
        }
        Town town = towny.getPlayerTown(player);
        return town == null ? null : elections.getElection(town);
    }

    private String displayProfile(Candidate candidate) {
        String profile = candidate.getProfile();
        return profile == null || profile.isBlank() ? messages.raw("profile.none") : profile;
    }

    private boolean isAdminAction(String action) {
        return CommandConfig.START.equals(action)
                || CommandConfig.STOP.equals(action)
                || CommandConfig.CANCEL.equals(action)
                || CommandConfig.RELOAD.equals(action);
    }
}
