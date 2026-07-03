package com.townyelections.commands;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.townyelections.TownyElections;
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

        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (action) {
            case CommandConfig.HELP -> sendHelp(sender, label);
            case CommandConfig.RUN -> handleRun(sender);
            case CommandConfig.WITHDRAW -> handleWithdraw(sender);
            case CommandConfig.CAMPAIGN -> handleCampaign(sender, rest, label);
            case CommandConfig.PARTY -> handleParty(sender, rest, label);
            case CommandConfig.PARTIES -> handleParties(sender);
            case CommandConfig.VOTE -> handleVote(sender, rest, label);
            case CommandConfig.STATUS -> handleStatus(sender, label);
            case CommandConfig.CANDIDATES -> handleCandidates(sender, label);
            case CommandConfig.RESULTS -> handleResults(sender);
            case CommandConfig.START -> handleAdmin(sender, rest, CommandConfig.START);
            case CommandConfig.STOP -> handleAdmin(sender, rest, CommandConfig.STOP);
            case CommandConfig.CANCEL -> handleAdmin(sender, rest, CommandConfig.CANCEL);
            case CommandConfig.RELOAD -> handleReload(sender);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    // ---- Player context helper --------------------------------------------

    /** Resolves the sender to (Player, Resident, Town). Sends errors and returns null on failure. */
    private PlayerContext resolveContext(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only");
            return null;
        }
        Resident resident = towny.getResident(player);
        if (resident == null) {
            messages.send(sender, "general.not-a-resident");
            return null;
        }
        Town town = towny.getPlayerTown(player);
        if (town == null) {
            messages.send(sender, "general.no-town");
            return null;
        }
        return new PlayerContext(player, resident, town);
    }

    private record PlayerContext(Player player, Resident resident, Town town) {
    }

    // ---- Candidacy ---------------------------------------------------------

    private void handleRun(CommandSender sender) {
        if (!sender.hasPermission("townyelections.candidate")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender);
        if (ctx == null) {
            return;
        }
        respond(sender, elections.registerCandidate(ctx.resident(), ctx.town()),
                MessageManager.placeholders(
                        "town", ctx.town().getName(),
                        "max", String.valueOf(config.getMaxCandidates())));
    }

    private void handleWithdraw(CommandSender sender) {
        if (!sender.hasPermission("townyelections.candidate")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender);
        if (ctx == null) {
            return;
        }
        respond(sender, elections.withdrawCandidate(ctx.resident(), ctx.town()),
                MessageManager.placeholders("town", ctx.town().getName()));
    }

    private void handleCampaign(CommandSender sender, String[] rest, String label) {
        if (!sender.hasPermission("townyelections.candidate")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender);
        if (ctx == null) {
            return;
        }
        if (rest.length == 0) {
            messages.send(sender, "campaign.empty", MessageManager.placeholders(
                    "label", label, "campaign", commands.literal(CommandConfig.CAMPAIGN)));
            return;
        }
        String message = String.join(" ", rest);
        respond(sender, elections.setCampaignMessage(ctx.resident(), ctx.town(), message),
                MessageManager.placeholders("max", String.valueOf(config.getMaxMessageLength())));
    }

    private void handleParty(CommandSender sender, String[] rest, String label) {
        if (rest.length > 0 && rest[0].equalsIgnoreCase("rename")) {
            if (!sender.hasPermission("townyelections.admin")) {
                messages.send(sender, "general.no-permission");
                return;
            }
            PlayerContext ctx = resolveContext(sender);
            if (ctx == null) {
                return;
            }
            handlePartyRename(sender, ctx, rest, label);
            return;
        }
        if (!sender.hasPermission("townyelections.candidate")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender);
        if (ctx == null) {
            return;
        }
        if (rest.length == 0) {
            Election election = elections.getElection(ctx.town());
            Candidate candidate = election == null ? null : election.getCandidate(ctx.resident().getUUID());
            if (candidate == null) {
                messages.send(sender, "candidate.not-a-candidate");
                return;
            }
            messages.send(sender, "party.current", MessageManager.placeholders(
                    "party", candidate.getPartyName(),
                    "label", label,
                    "party_command", commands.literal(CommandConfig.PARTY)));
            return;
        }
        if (rest.length == 1 && rest[0].equalsIgnoreCase("leave")) {
            respond(sender, elections.leaveParty(ctx.resident(), ctx.town()),
                    MessageManager.placeholders("party", config.getDefaultPartyName()));
            return;
        }
        String partyName = String.join(" ", rest);
        respond(sender, elections.setPartyName(ctx.resident(), ctx.town(), partyName),
                MessageManager.placeholders(
                        "party", partyName.trim(),
                        "max", String.valueOf(config.getMaxPartyNameLength())));
    }

    private void handlePartyRename(CommandSender sender, PlayerContext ctx, String[] rest, String label) {
        if (!sender.hasPermission("townyelections.admin")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (rest.length < 3) {
            messages.send(sender, "party.rename-usage", MessageManager.placeholders(
                    "label", label, "party", commands.literal(CommandConfig.PARTY)));
            return;
        }
        String oldName = rest[1];
        String newName = String.join(" ", Arrays.copyOfRange(rest, 2, rest.length));
        if (oldName.isBlank() || newName.isBlank()) {
            messages.send(sender, "party.rename-usage", MessageManager.placeholders(
                    "label", label, "party", commands.literal(CommandConfig.PARTY)));
            return;
        }
        respond(sender, elections.renameParty(ctx.town(), oldName, newName), MessageManager.placeholders(
                "old", oldName,
                "new", newName.trim(),
                "max", String.valueOf(config.getMaxPartyNameLength())));
    }

    // ---- Voting ------------------------------------------------------------

    private void handleVote(CommandSender sender, String[] rest, String label) {
        if (!sender.hasPermission("townyelections.vote")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender);
        if (ctx == null) {
            return;
        }
        Election election = elections.getElection(ctx.town());
        VotingSystem system = election == null ? config.getVotingSystem() : election.getVotingSystem();
        if (rest.length == 0) {
            messages.send(sender, voteUsageKey(system), MessageManager.placeholders(
                    "label", label, "vote", commands.literal(CommandConfig.VOTE)));
            return;
        }

        OperationResult result;
        Map<String, String> ph = MessageManager.placeholders(
                "town", ctx.town().getName(),
                "name", String.join(" ", rest));
        if (system == VotingSystem.PLURALITY) {
            result = elections.castVote(ctx.resident(), ctx.town(), String.join(" ", rest));
        } else {
            result = elections.castBallot(ctx.resident(), ctx.town(), Arrays.asList(rest));
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
                    "label", label, "vote", commands.literal(CommandConfig.VOTE)));
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

    private void handleStatus(CommandSender sender, String label) {
        if (!sender.hasPermission("townyelections.info")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender);
        if (ctx == null) {
            return;
        }
        Election election = elections.getElection(ctx.town());
        if (election == null) {
            messages.send(sender, "election.none-active");
            return;
        }

        messages.sendNoPrefix(sender, "status.header",
                MessageManager.placeholders("town", ctx.town().getName()));

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

        printCandidateList(sender, election, label);
    }

    private void handleCandidates(CommandSender sender, String label) {
        if (!sender.hasPermission("townyelections.info")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender);
        if (ctx == null) {
            return;
        }
        Election election = elections.getElection(ctx.town());
        if (election == null) {
            messages.send(sender, "election.none-active");
            return;
        }
        printCandidateList(sender, election, label);
    }

    private void printCandidateList(CommandSender sender, Election election, String label) {
        messages.sendNoPrefix(sender, "status.candidate-list-header",
                MessageManager.placeholders("label", label, "vote", commands.literal(CommandConfig.VOTE)));

        boolean showVotes = config.isPublicLiveResults()
                || election.getPhase() == ElectionPhase.CONCLUDED;
        Map<UUID, Integer> tally = election.tally();
        for (Candidate c : election.getCandidateList()) {
            if (showVotes) {
                messages.sendNoPrefix(sender, "status.candidate-entry", MessageManager.placeholders(
                        "candidate", c.getName(),
                        "party", c.getPartyName(),
                        "votes", String.valueOf(tally.getOrDefault(c.getUuid(), 0)),
                        "message", c.getCampaignMessage()));
            } else {
                messages.sendNoPrefix(sender, "status.candidate-entry-hidden", MessageManager.placeholders(
                        "candidate", c.getName(),
                        "party", c.getPartyName(),
                        "message", c.getCampaignMessage()));
            }
        }
    }

    private void handleParties(CommandSender sender) {
        if (!sender.hasPermission("townyelections.info")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender);
        if (ctx == null) {
            return;
        }
        Election election = elections.getElection(ctx.town());
        if (election == null) {
            messages.send(sender, "election.none-active");
            return;
        }
        printPartyList(sender, election, ctx.town().getName());
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

    private void handleResults(CommandSender sender) {
        if (!sender.hasPermission("townyelections.info")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(sender);
        if (ctx == null) {
            return;
        }
        ElectionResult result = elections.getLastResult(ctx.town().getUUID());
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

    private void handleAdmin(CommandSender sender, String[] rest, String action) {
        if (!sender.hasPermission("townyelections.admin")) {
            messages.send(sender, "general.no-permission");
            return;
        }

        Town town;
        if (rest.length > 0) {
            town = towny.getTownByName(rest[0]);
            if (town == null) {
                messages.send(sender, "admin.town-not-found",
                        MessageManager.placeholders("town", rest[0]));
                return;
            }
        } else {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "admin.town-not-found", MessageManager.placeholders("town", "?"));
                return;
            }
            town = towny.getPlayerTown(player);
            if (town == null) {
                messages.send(sender, "general.no-town");
                return;
            }
        }

        OperationResult result = switch (action) {
            case CommandConfig.START -> elections.startElection(town);
            case CommandConfig.STOP -> elections.stopElection(town);
            case CommandConfig.CANCEL -> elections.cancelElection(town);
            default -> OperationResult.fail("general.unknown-command");
        };
        respond(sender, result, MessageManager.placeholders(
                "town", town.getName(),
                "min", String.valueOf(config.getMinTownResidents())));
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
                "party", commands.literal(CommandConfig.PARTY),
                "parties", commands.literal(CommandConfig.PARTIES),
                "vote", commands.literal(CommandConfig.VOTE),
                "status", commands.literal(CommandConfig.STATUS),
                "candidates", commands.literal(CommandConfig.CANDIDATES),
                "results", commands.literal(CommandConfig.RESULTS),
                "start", commands.literal(CommandConfig.START),
                "stop", commands.literal(CommandConfig.STOP),
                "cancel", commands.literal(CommandConfig.CANCEL),
                "reload", commands.literal(CommandConfig.RELOAD));

        messages.sendNoPrefix(sender, "help.header", null);
        messages.sendNoPrefix(sender, "help.run", base);
        messages.sendNoPrefix(sender, "help.withdraw", base);
        messages.sendNoPrefix(sender, "help.campaign", base);
        messages.sendNoPrefix(sender, "help.party", base);
        messages.sendNoPrefix(sender, "help.parties", base);
        messages.sendNoPrefix(sender, "help.vote", base);
        messages.sendNoPrefix(sender, "help.status", base);
        messages.sendNoPrefix(sender, "help.candidates", base);
        messages.sendNoPrefix(sender, "help.results", base);
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
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String actionKey : commands.getActions()) {
                // Hide admin sub-commands from users without permission.
                if (isAdminAction(actionKey) && !sender.hasPermission("townyelections.admin")) {
                    continue;
                }
                String literal = commands.literal(actionKey);
                if (literal.toLowerCase().startsWith(partial)) {
                    out.add(literal);
                }
            }
            return out;
        }

        if (args.length >= 2) {
            String action = commands.actionFor(args[0]);
            if (action == null) {
                return out;
            }
            String partial = args[args.length - 1].toLowerCase();
            // Suggest candidate names for voting. Ranked-choice and approval
            // ballots list several names, so complete every argument position
            // and skip names already on the command line.
            if (CommandConfig.VOTE.equals(action) && sender instanceof Player player) {
                Town town = towny.getPlayerTown(player);
                Election election = elections.getElection(town);
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
                Town town = towny.getPlayerTown(player);
                Election election = elections.getElection(town);
                if (election != null) {
                    String partyInput = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase();
                    for (String party : currentPartyNames(election)) {
                        if (party.toLowerCase().startsWith(partyInput)) {
                            out.add(party);
                        }
                    }
                }
            }
            // Suggest town names for admin commands.
            if (args.length == 2 && isAdminAction(action) && sender.hasPermission("townyelections.admin")) {
                for (Town town : com.palmergames.bukkit.towny.TownyUniverse.getInstance().getTowns()) {
                    if (town.getName().toLowerCase().startsWith(partial)) {
                        out.add(town.getName());
                    }
                }
            }
        }
        return out;
    }

    private boolean isAdminAction(String action) {
        return CommandConfig.START.equals(action)
                || CommandConfig.STOP.equals(action)
                || CommandConfig.CANCEL.equals(action)
                || CommandConfig.RELOAD.equals(action);
    }
}
