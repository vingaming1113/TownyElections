package com.townyelections.gui;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.townyelections.TownyElections;
import com.townyelections.integration.TownyHook;
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
import com.townyelections.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ElectionMenu implements Listener {

    private static final int MAIN_SIZE = 54;
    private static final int PAGE_SIZE = 54;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int CLEAR_BALLOT_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int STATUS_SLOT = 4;
    private static final List<Integer> CONTENT_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43);

    private final TownyElections plugin;
    private final ElectionManager elections;
    private final MessageManager messages;
    private final ConfigManager config;
    private final TownyHook towny;
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();

    public ElectionMenu(TownyElections plugin) {
        this.plugin = plugin;
        this.elections = plugin.getElectionManager();
        this.messages = plugin.getMessageManager();
        this.config = plugin.getConfigManager();
        this.towny = plugin.getTownyHook();
    }

    public void openMain(Player player) {
        PlayerContext ctx = resolveContext(player);
        if (ctx == null) {
            return;
        }

        Election election = elections.getElection(ctx.town());
        Map<String, String> menuPlaceholders = placeholders(ctx.town(), election, ctx.resident());
        ElectionMenuHolder holder = new ElectionMenuHolder(ElectionMenuView.MAIN, ctx.town().getUUID(), 0);
        Inventory inventory = Bukkit.createInventory(holder, MAIN_SIZE,
                messages.legacy("gui.main-title", menuPlaceholders));
        holder.setInventory(inventory);
        fillFrame(inventory);

        inventory.setItem(STATUS_SLOT, statusItem(ctx.town(), election, ctx.resident()));
        addAction(inventory, holder, 10, ElectionMenuAction.CANDIDATES, Material.PLAYER_HEAD,
                "gui.main-candidates-name", "gui.main-candidates-lore", menuPlaceholders);
        addAction(inventory, holder, 12, ElectionMenuAction.STANDINGS, Material.OAK_SIGN,
                "gui.main-standings-name", "gui.main-standings-lore", menuPlaceholders);
        addAction(inventory, holder, 14, ElectionMenuAction.RESULTS, Material.WRITABLE_BOOK,
                "gui.main-results-name", "gui.main-results-lore", menuPlaceholders);
        addAction(inventory, holder, 16, ElectionMenuAction.RUN, Material.NAME_TAG,
                "gui.main-run-name", "gui.main-run-lore", menuPlaceholders);
        addAction(inventory, holder, 20, ElectionMenuAction.WITHDRAW, Material.RED_BED,
                "gui.main-withdraw-name", "gui.main-withdraw-lore", menuPlaceholders);
        addAction(inventory, holder, 22, ElectionMenuAction.SET_CAMPAIGN, Material.PAPER,
                "gui.main-campaign-name", "gui.main-campaign-lore", menuPlaceholders);
        addAction(inventory, holder, 24, ElectionMenuAction.SET_PARTY, Material.BLUE_BANNER,
                "gui.main-party-name", "gui.main-party-lore", menuPlaceholders);
        addAction(inventory, holder, 30, ElectionMenuAction.LEAVE_PARTY, Material.WHITE_BANNER,
                "gui.main-leave-party-name", "gui.main-leave-party-lore", menuPlaceholders);

        if (player.hasPermission("townyelections.admin")) {
            addAction(inventory, holder, 37, ElectionMenuAction.ADMIN_START, Material.EMERALD_BLOCK,
                    "gui.admin-start-name", "gui.admin-start-lore", menuPlaceholders);
            addAction(inventory, holder, 39, ElectionMenuAction.ADMIN_STOP, Material.REDSTONE_BLOCK,
                    "gui.admin-stop-name", "gui.admin-stop-lore", menuPlaceholders);
            addAction(inventory, holder, 41, ElectionMenuAction.ADMIN_CANCEL, Material.BARRIER,
                    "gui.admin-cancel-name", "gui.admin-cancel-lore", menuPlaceholders);
            addAction(inventory, holder, 43, ElectionMenuAction.ADMIN_RELOAD, Material.COMPARATOR,
                    "gui.admin-reload-name", "gui.admin-reload-lore", menuPlaceholders);
        }

        player.openInventory(inventory);
    }

    private void openRoster(Player player, UUID townUuid) {
        openRoster(player, townUuid, 0);
    }

    private void openRoster(Player player, UUID townUuid, int requestedPage) {
        if (!player.hasPermission("townyelections.info") && !player.hasPermission("townyelections.vote")) {
            messages.send(player, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContextForTown(player, townUuid);
        if (ctx == null) {
            return;
        }

        Election election = elections.getElection(ctx.town());
        List<Candidate> candidates = sortedCandidates(election);
        int maxPage = Math.max(0, (candidates.size() - 1) / CONTENT_SLOTS.size());
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        Map<String, String> menuPlaceholders = placeholders(ctx.town(), election, ctx.resident());
        menuPlaceholders.put("page", String.valueOf(page + 1));
        menuPlaceholders.put("pages", String.valueOf(maxPage + 1));

        ElectionMenuHolder holder = new ElectionMenuHolder(ElectionMenuView.ROSTER, townUuid, page);
        Inventory inventory = Bukkit.createInventory(holder, PAGE_SIZE,
                messages.legacy("gui.roster-title", menuPlaceholders));
        holder.setInventory(inventory);
        fillFrame(inventory);
        addBackButton(inventory, holder, menuPlaceholders);

        if (candidates.isEmpty()) {
            inventory.setItem(22, icon(Material.BARRIER,
                    "gui.no-candidates-name", "gui.no-candidates-lore", menuPlaceholders));
        } else {
            Map<UUID, Integer> tally = election.tally();
            int start = page * CONTENT_SLOTS.size();
            int end = Math.min(candidates.size(), start + CONTENT_SLOTS.size());
            for (int index = start; index < end; index++) {
                Candidate candidate = candidates.get(index);
                int slot = CONTENT_SLOTS.get(index - start);
                holder.setCandidate(slot, candidate.getUuid());
                inventory.setItem(slot, candidateItem(candidate, election, tally, ctx.resident()));
            }
        }

        if (election != null && election.getVotingSystem() != VotingSystem.PLURALITY
                && !election.getBallot(ctx.resident().getUUID()).isEmpty()) {
            addAction(inventory, holder, CLEAR_BALLOT_SLOT, ElectionMenuAction.CLEAR_BALLOT,
                    Material.MILK_BUCKET, "gui.clear-ballot-name", "gui.clear-ballot-lore", menuPlaceholders);
        }

        addPageButtons(inventory, holder, menuPlaceholders, page, maxPage);
        player.openInventory(inventory);
    }

    private void openStandings(Player player, UUID townUuid) {
        openStandings(player, townUuid, 0);
    }

    private void openStandings(Player player, UUID townUuid, int requestedPage) {
        if (!player.hasPermission("townyelections.info")) {
            messages.send(player, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContextForTown(player, townUuid);
        if (ctx == null) {
            return;
        }
        Election election = elections.getElection(ctx.town());
        if (election == null) {
            messages.send(player, "election.none-active");
            openMain(player);
            return;
        }

        boolean showVotes = config.isPublicLiveResults() || election.getPhase() == ElectionPhase.CONCLUDED;
        List<PartyEntry> parties = rankedParties(election, showVotes);
        int maxPage = Math.max(0, (parties.size() - 1) / CONTENT_SLOTS.size());
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        Map<String, String> menuPlaceholders = placeholders(ctx.town(), election, ctx.resident());
        menuPlaceholders.put("page", String.valueOf(page + 1));
        menuPlaceholders.put("pages", String.valueOf(maxPage + 1));

        ElectionMenuHolder holder = new ElectionMenuHolder(ElectionMenuView.STANDINGS, townUuid, page);
        Inventory inventory = Bukkit.createInventory(holder, PAGE_SIZE,
                messages.legacy("gui.standings-title", menuPlaceholders));
        holder.setInventory(inventory);
        fillFrame(inventory);
        addBackButton(inventory, holder, menuPlaceholders);

        if (parties.isEmpty()) {
            inventory.setItem(22, icon(Material.BARRIER,
                    "gui.no-parties-name", "gui.no-parties-lore", menuPlaceholders));
        } else {
            int start = page * CONTENT_SLOTS.size();
            int end = Math.min(parties.size(), start + CONTENT_SLOTS.size());
            for (int index = start; index < end; index++) {
                PartyEntry party = parties.get(index);
                int slot = CONTENT_SLOTS.get(index - start);
                inventory.setItem(slot, partyItem(party, showVotes));
            }
        }

        addPageButtons(inventory, holder, menuPlaceholders, page, maxPage);
        player.openInventory(inventory);
    }

    private void openResults(Player player, UUID townUuid) {
        openResults(player, townUuid, 0);
    }

    private void openResults(Player player, UUID townUuid, int requestedPage) {
        if (!player.hasPermission("townyelections.info")) {
            messages.send(player, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContextForTown(player, townUuid);
        if (ctx == null) {
            return;
        }
        ElectionResult result = elections.getLastResult(ctx.town().getUUID());
        if (result == null) {
            messages.send(player, "results.none-recorded");
            openMain(player);
            return;
        }

        List<ElectionResult.Standing> standings = result.getStandings();
        int maxPage = Math.max(0, (standings.size() - 1) / CONTENT_SLOTS.size());
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        Map<String, String> menuPlaceholders = resultPlaceholders(result, null, 0);
        menuPlaceholders.put("page", String.valueOf(page + 1));
        menuPlaceholders.put("pages", String.valueOf(maxPage + 1));

        ElectionMenuHolder holder = new ElectionMenuHolder(ElectionMenuView.RESULTS, townUuid, page);
        Inventory inventory = Bukkit.createInventory(holder, PAGE_SIZE,
                messages.legacy("gui.results-title", menuPlaceholders));
        holder.setInventory(inventory);
        fillFrame(inventory);
        addBackButton(inventory, holder, menuPlaceholders);
        inventory.setItem(STATUS_SLOT, icon(Material.NETHER_STAR,
                "gui.results-summary-name", "gui.results-summary-lore", menuPlaceholders));

        int start = page * CONTENT_SLOTS.size();
        int end = Math.min(standings.size(), start + CONTENT_SLOTS.size());
        for (int index = start; index < end; index++) {
            ElectionResult.Standing standing = standings.get(index);
            int slot = CONTENT_SLOTS.get(index - start);
            inventory.setItem(slot, resultStandingItem(standing, result, index + 1));
        }

        addPageButtons(inventory, holder, menuPlaceholders, page, maxPage);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof ElectionMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        UUID candidateUuid = holder.getCandidate(event.getRawSlot());
        if (candidateUuid != null) {
            handleCandidateClick(player, holder, candidateUuid);
            return;
        }

        ElectionMenuAction action = holder.getAction(event.getRawSlot());
        if (action == null) {
            return;
        }

        switch (action) {
            case CANDIDATES -> openRoster(player, holder.getTownUuid());
            case STANDINGS -> openStandings(player, holder.getTownUuid());
            case RESULTS -> openResults(player, holder.getTownUuid());
            case RUN -> handleRun(player, holder.getTownUuid());
            case WITHDRAW -> handleWithdraw(player, holder.getTownUuid());
            case SET_CAMPAIGN -> beginTextInput(player, holder.getTownUuid(), PendingInputType.CAMPAIGN);
            case SET_PARTY -> beginTextInput(player, holder.getTownUuid(), PendingInputType.PARTY);
            case LEAVE_PARTY -> handleLeaveParty(player, holder.getTownUuid());
            case CLEAR_BALLOT -> handleClearBallot(player, holder);
            case ADMIN_START -> handleAdmin(player, holder.getTownUuid(), ElectionMenuAction.ADMIN_START);
            case ADMIN_STOP -> handleAdmin(player, holder.getTownUuid(), ElectionMenuAction.ADMIN_STOP);
            case ADMIN_CANCEL -> handleAdmin(player, holder.getTownUuid(), ElectionMenuAction.ADMIN_CANCEL);
            case ADMIN_RELOAD -> handleReload(player, holder.getTownUuid());
            case BACK -> openMain(player);
            case PREVIOUS_PAGE -> openPreviousPage(player, holder);
            case NEXT_PAGE -> openNextPage(player, holder);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ElectionMenuHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPendingChat(AsyncPlayerChatEvent event) {
        PendingInput pending = pendingInputs.remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        String input = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> handlePendingInput(event.getPlayer(), pending, input));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingInputs.remove(event.getPlayer().getUniqueId());
    }

    private void openPreviousPage(Player player, ElectionMenuHolder holder) {
        openPagedView(player, holder, holder.getPage() - 1);
    }

    private void openNextPage(Player player, ElectionMenuHolder holder) {
        openPagedView(player, holder, holder.getPage() + 1);
    }

    private void openPagedView(Player player, ElectionMenuHolder holder, int page) {
        switch (holder.getView()) {
            case MAIN -> openMain(player);
            case ROSTER -> openRoster(player, holder.getTownUuid(), page);
            case STANDINGS -> openStandings(player, holder.getTownUuid(), page);
            case RESULTS -> openResults(player, holder.getTownUuid(), page);
        }
    }

    private void handleRun(Player player, UUID townUuid) {
        if (!player.hasPermission("townyelections.candidate")) {
            messages.send(player, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContextForTown(player, townUuid);
        if (ctx == null) {
            return;
        }
        respond(player, elections.registerCandidate(ctx.resident(), ctx.town()), MessageManager.placeholders(
                "town", ctx.town().getName(),
                "max", String.valueOf(config.getMaxCandidates())));
        openMain(player);
    }

    private void handleWithdraw(Player player, UUID townUuid) {
        if (!player.hasPermission("townyelections.candidate")) {
            messages.send(player, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContextForTown(player, townUuid);
        if (ctx == null) {
            return;
        }
        respond(player, elections.withdrawCandidate(ctx.resident(), ctx.town()),
                MessageManager.placeholders("town", ctx.town().getName()));
        openMain(player);
    }

    private void handleLeaveParty(Player player, UUID townUuid) {
        if (!player.hasPermission("townyelections.candidate")) {
            messages.send(player, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContextForTown(player, townUuid);
        if (ctx == null) {
            return;
        }
        respond(player, elections.leaveParty(ctx.resident(), ctx.town()),
                MessageManager.placeholders("party", config.getDefaultPartyName()));
        openMain(player);
    }

    private void handleCandidateClick(Player player, ElectionMenuHolder holder, UUID candidateUuid) {
        if (!player.hasPermission("townyelections.vote")) {
            messages.send(player, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContextForTown(player, holder.getTownUuid());
        if (ctx == null) {
            return;
        }
        Election election = elections.getElection(ctx.town());
        VotingSystem system = election == null ? VotingSystem.PLURALITY : election.getVotingSystem();
        if (system != VotingSystem.PLURALITY) {
            handleBallotToggleClick(player, holder, ctx, election, candidateUuid);
            return;
        }
        OperationResult result = elections.castVote(ctx.resident(), ctx.town(), candidateUuid);
        Map<String, String> placeholders = MessageManager.placeholders(
                "town", ctx.town().getName(),
                "name", candidateName(ctx.town(), candidateUuid));
        if (result.isSuccess() && result.getPayload() instanceof String candidateName) {
            placeholders.put("candidate", candidateName);
            player.closeInventory();
        }
        respond(player, result, placeholders);
        if (!result.isSuccess()) {
            openRoster(player, holder.getTownUuid(), holder.getPage());
        }
    }

    /**
     * Ranked-choice and approval ballots build up click by click, so the
     * roster stays open and refreshes to show the updated ballot state.
     */
    private void handleBallotToggleClick(Player player, ElectionMenuHolder holder, PlayerContext ctx,
                                         Election election, UUID candidateUuid) {
        OperationResult result = elections.toggleBallotEntry(ctx.resident(), ctx.town(), candidateUuid);
        Map<String, String> placeholders = MessageManager.placeholders(
                "town", ctx.town().getName(),
                "name", candidateName(ctx.town(), candidateUuid));
        if (result.isSuccess() && result.getPayload() instanceof String candidateName) {
            placeholders.put("candidate", candidateName);
            int rank = election.getBallot(ctx.resident().getUUID()).indexOf(candidateUuid) + 1;
            placeholders.put("rank", String.valueOf(Math.max(1, rank)));
            String ballot = elections.describeBallot(election, ctx.resident().getUUID());
            placeholders.put("ballot", ballot.isEmpty() ? messages.raw("gui.vote-none") : ballot);
        }
        respond(player, result, placeholders);
        openRoster(player, holder.getTownUuid(), holder.getPage());
    }

    private void handleClearBallot(Player player, ElectionMenuHolder holder) {
        if (!player.hasPermission("townyelections.vote")) {
            messages.send(player, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContextForTown(player, holder.getTownUuid());
        if (ctx == null) {
            return;
        }
        respond(player, elections.clearBallot(ctx.resident(), ctx.town()),
                MessageManager.placeholders("town", ctx.town().getName()));
        openRoster(player, holder.getTownUuid(), holder.getPage());
    }

    private void handleAdmin(Player player, UUID townUuid, ElectionMenuAction action) {
        if (!player.hasPermission("townyelections.admin")) {
            messages.send(player, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContextForTown(player, townUuid);
        if (ctx == null) {
            return;
        }
        OperationResult result = switch (action) {
            case ADMIN_START -> elections.startElection(ctx.town());
            case ADMIN_STOP -> elections.stopElection(ctx.town());
            case ADMIN_CANCEL -> elections.cancelElection(ctx.town());
            default -> OperationResult.fail("general.unknown-command");
        };
        respond(player, result, MessageManager.placeholders(
                "town", ctx.town().getName(),
                "min", String.valueOf(config.getMinTownResidents())));
        openMain(player);
    }

    private void handleReload(Player player, UUID townUuid) {
        if (!player.hasPermission("townyelections.admin")) {
            messages.send(player, "general.no-permission");
            return;
        }
        plugin.reloadAll();
        messages.send(player, "general.reloaded");
        openMain(player);
    }

    private void beginTextInput(Player player, UUID townUuid, PendingInputType type) {
        if (!player.hasPermission("townyelections.candidate")) {
            messages.send(player, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContextForTown(player, townUuid);
        if (ctx == null) {
            return;
        }
        Election election = elections.getElection(ctx.town());
        if (election == null || election.getCandidate(ctx.resident().getUUID()) == null) {
            messages.send(player, "candidate.not-a-candidate");
            openMain(player);
            return;
        }
        pendingInputs.put(player.getUniqueId(), new PendingInput(type, townUuid));
        player.closeInventory();
        messages.send(player, type == PendingInputType.CAMPAIGN ? "gui.campaign-prompt" : "gui.party-prompt",
                MessageManager.placeholders(
                        "max_campaign", String.valueOf(config.getMaxMessageLength()),
                        "max_party", String.valueOf(config.getMaxPartyNameLength())));
    }

    private void handlePendingInput(Player player, PendingInput pending, String input) {
        if (input.equalsIgnoreCase("cancel")) {
            messages.send(player, "gui.input-cancelled");
            openMain(player);
            return;
        }
        PlayerContext ctx = resolveContextForTown(player, pending.townUuid());
        if (ctx == null) {
            return;
        }
        if (pending.type() == PendingInputType.CAMPAIGN) {
            respond(player, elections.setCampaignMessage(ctx.resident(), ctx.town(), input),
                    MessageManager.placeholders("max", String.valueOf(config.getMaxMessageLength())));
        } else {
            respond(player, elections.setPartyName(ctx.resident(), ctx.town(), input), MessageManager.placeholders(
                    "party", input.trim(),
                    "max", String.valueOf(config.getMaxPartyNameLength())));
        }
        openMain(player);
    }

    private PlayerContext resolveContext(Player player) {
        Resident resident = towny.getResident(player);
        if (resident == null) {
            messages.send(player, "general.not-a-resident");
            return null;
        }
        Town town = towny.getPlayerTown(player);
        if (town == null) {
            messages.send(player, "general.no-town");
            return null;
        }
        return new PlayerContext(resident, town);
    }

    private PlayerContext resolveContextForTown(Player player, UUID expectedTownUuid) {
        PlayerContext ctx = resolveContext(player);
        if (ctx == null) {
            player.closeInventory();
            return null;
        }
        if (!ctx.town().getUUID().equals(expectedTownUuid)) {
            player.closeInventory();
            messages.send(player, "general.no-town");
            return null;
        }
        return ctx;
    }

    private List<Candidate> sortedCandidates(Election election) {
        if (election == null) {
            return List.of();
        }
        List<Candidate> candidates = new ArrayList<>(election.getCandidateList());
        candidates.sort(Comparator.comparing(Candidate::getName, String.CASE_INSENSITIVE_ORDER));
        return candidates;
    }

    private List<PartyEntry> rankedParties(Election election, boolean rankByVotes) {
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

        List<PartyEntry> parties = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : partyCandidates.entrySet()) {
            parties.add(new PartyEntry(entry.getKey(), partyVotes.getOrDefault(entry.getKey(), 0), entry.getValue()));
        }
        Comparator<PartyEntry> comparator = rankByVotes
                ? Comparator.comparingInt(PartyEntry::votes).reversed()
                        .thenComparing(PartyEntry::name, String.CASE_INSENSITIVE_ORDER)
                : Comparator.comparing(PartyEntry::name, String.CASE_INSENSITIVE_ORDER);
        parties.sort(comparator);
        return parties;
    }

    private ItemStack candidateItem(Candidate candidate, Election election, Map<UUID, Integer> tally,
                                    Resident viewer) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta baseMeta = item.getItemMeta();
        if (baseMeta instanceof SkullMeta skullMeta) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(candidate.getUuid());
            skullMeta.setOwningPlayer(offlinePlayer);
            item.setItemMeta(skullMeta);
        }
        Map<String, String> placeholders = placeholders(election, candidate, tally, viewer);
        applyMeta(item, "gui.candidate-name", candidateLoreKey(election, candidate, viewer), placeholders);
        return item;
    }

    private ItemStack partyItem(PartyEntry party, boolean showVotes) {
        Map<String, String> placeholders = MessageManager.placeholders(
                "party", party.name(),
                "party_votes", String.valueOf(party.votes()),
                "count", String.valueOf(party.candidates().size()),
                "candidates", String.join(", ", party.candidates()));
        return icon(Material.BLUE_BANNER, "gui.party-entry-name",
                showVotes ? "gui.party-entry-lore-public" : "gui.party-entry-lore-hidden", placeholders);
    }

    private ItemStack resultStandingItem(ElectionResult.Standing standing, ElectionResult result, int rank) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta baseMeta = item.getItemMeta();
        if (baseMeta instanceof SkullMeta skullMeta) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(standing.uuid);
            skullMeta.setOwningPlayer(offlinePlayer);
            item.setItemMeta(skullMeta);
        }
        applyMeta(item, "gui.result-candidate-name", "gui.result-candidate-lore",
                resultPlaceholders(result, standing, rank));
        return item;
    }

    private String candidateLoreKey(Election election, Candidate candidate, Resident viewer) {
        if (election == null || (election.getPhase() != ElectionPhase.VOTING
                && election.getPhase() != ElectionPhase.RUNOFF)) {
            return "gui.candidate-lore-closed";
        }
        boolean onBallot = viewer != null
                && election.getBallot(viewer.getUUID()).contains(candidate.getUuid());
        return switch (election.getVotingSystem()) {
            case RANKED_CHOICE -> onBallot
                    ? "gui.candidate-lore-ranked" : "gui.candidate-lore-unranked";
            case APPROVAL -> onBallot
                    ? "gui.candidate-lore-approved" : "gui.candidate-lore-unapproved";
            default -> config.isPublicLiveResults()
                    ? "gui.candidate-lore-public" : "gui.candidate-lore-secret";
        };
    }

    private ItemStack statusItem(Town town, Election election, Resident viewer) {
        return icon(Material.NETHER_STAR, "gui.status-name", "gui.status-lore",
                placeholders(election, null, election == null ? Map.of() : election.tally(), viewer, town));
    }

    private ItemStack icon(Material material, String nameKey, String loreKey, Map<String, String> placeholders) {
        ItemStack item = new ItemStack(material);
        applyMeta(item, nameKey, loreKey, placeholders);
        return item;
    }

    private void addAction(Inventory inventory, ElectionMenuHolder holder, int slot, ElectionMenuAction action,
                           Material material, String nameKey, String loreKey, Map<String, String> placeholders) {
        holder.setAction(slot, action);
        inventory.setItem(slot, icon(material, nameKey, loreKey, placeholders));
    }

    private void addBackButton(Inventory inventory, ElectionMenuHolder holder, Map<String, String> placeholders) {
        addAction(inventory, holder, BACK_SLOT, ElectionMenuAction.BACK, Material.ARROW,
                "gui.back-name", "gui.back-lore", placeholders);
    }

    private void addMainPageButtons(Inventory inventory, ElectionMenuHolder holder, Map<String, String> placeholders,
                                    int page, int maxPage) {
        if (page > 0) {
            addAction(inventory, holder, PREVIOUS_SLOT, ElectionMenuAction.PREVIOUS_PAGE, Material.LIME_DYE,
                    "gui.previous-page-name", "gui.previous-page-lore", placeholders);
        }
        if (page < maxPage) {
            addAction(inventory, holder, NEXT_SLOT, ElectionMenuAction.NEXT_PAGE, Material.LIME_DYE,
                    "gui.next-page-name", "gui.next-page-lore", placeholders);
        }
    }

    private void addPageButtons(Inventory inventory, ElectionMenuHolder holder, Map<String, String> placeholders,
                                int page, int maxPage) {
        addMainPageButtons(inventory, holder, placeholders, page, maxPage);
    }

    private void applyMeta(ItemStack item, String nameKey, String loreKey, Map<String, String> placeholders) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.displayName(messages.component(nameKey, placeholders));
        meta.lore(lore(loreKey, placeholders));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
    }

    private List<Component> lore(String key, Map<String, String> placeholders) {
        List<String> lines = messages.rawList(key);
        if (lines.isEmpty()) {
            return List.of(messages.component(key, placeholders));
        }
        List<Component> out = new ArrayList<>();
        for (String line : lines) {
            out.add(TextUtil.colorize(apply(line, placeholders)));
        }
        return out;
    }

    private String apply(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private void fillFrame(Inventory inventory) {
        ItemStack gray = filler(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack blue = filler(Material.CYAN_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isFrameSlot(slot)) {
                inventory.setItem(slot, slot % 2 == 0 ? blue : gray);
            }
        }
    }

    private ItemStack filler(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isFrameSlot(int slot) {
        return slot < 9 || slot % 9 == 0 || slot % 9 == 8 || slot >= 45;
    }

    private Map<String, String> placeholders(Town town, Election election) {
        return placeholders(town, election, null);
    }

    private Map<String, String> placeholders(Town town, Election election, Resident viewer) {
        return placeholders(election, null, election == null ? Map.of() : election.tally(), viewer, town);
    }

    private Map<String, String> placeholders(Election election, Candidate candidate,
                                             Map<UUID, Integer> tally, Resident viewer) {
        return placeholders(election, candidate, tally, viewer, null);
    }

    private Map<String, String> placeholders(Election election, Candidate candidate,
                                             Map<UUID, Integer> tally, Resident viewer, Town town) {
        String townName = town == null ? (election == null ? "?" : election.getTownName()) : town.getName();
        String phase = election == null ? messages.raw("gui.phase-none") : switch (election.getPhase()) {
            case NOMINATION -> messages.raw("election.phase-nomination");
            case VOTING -> messages.raw("election.phase-voting");
            case RUNOFF -> messages.raw("election.phase-runoff");
            default -> election.getPhase().name();
        };
        String time = election == null ? "-" : DurationUtil.format(election.getMillisRemaining());
        String candidateName = candidate == null ? "-" : candidate.getName();
        String party = candidate == null ? "-" : candidate.getPartyName();
        int votes = candidate == null ? 0 : tally.getOrDefault(candidate.getUuid(), 0);
        Candidate viewerCandidate = election == null || viewer == null ? null : election.getCandidate(viewer.getUUID());
        String yourParty = viewerCandidate == null ? "-" : viewerCandidate.getPartyName();
        String yourCampaign = viewerCandidate == null ? "-" : viewerCandidate.getCampaignMessage();
        String voted = messages.raw("gui.vote-none");
        if (election != null && viewer != null && election.hasVoted(viewer.getUUID())) {
            voted = elections.describeBallot(election, viewer.getUUID());
        }
        String system = election == null ? "-" : messages.raw(election.getVotingSystem().messageKey());
        String yourRank = "-";
        if (election != null && viewer != null && candidate != null) {
            int rank = election.getBallot(viewer.getUUID()).indexOf(candidate.getUuid()) + 1;
            if (rank > 0) {
                yourRank = String.valueOf(rank);
            }
        }

        return MessageManager.placeholders(
                "town", townName,
                "phase", phase,
                "system", system,
                "time", time,
                "candidates", election == null ? "0" : String.valueOf(election.getCandidateCount()),
                "votes", election == null ? "0" : String.valueOf(election.getTotalVotes()),
                "candidate", candidateName,
                "party", party,
                "candidate_votes", String.valueOf(votes),
                "message", candidate == null ? "-" : candidate.getCampaignMessage(),
                "your_vote", voted,
                "your_rank", yourRank,
                "your_party", yourParty,
                "your_campaign", yourCampaign,
                "default_party", config.getDefaultPartyName());
    }

    private Map<String, String> resultPlaceholders(ElectionResult result, ElectionResult.Standing standing, int rank) {
        int total = Math.max(1, result.getTotalVotes());
        int percent = standing == null ? 0 : (int) Math.round((standing.votes * 100.0) / total);
        int residents = Math.max(1, result.getResidentCount());
        int turnout = (int) Math.round((result.getTotalVotes() * 100.0) / residents);
        String winnerParty = result.getStandings().stream()
                .filter(entry -> result.getWinnerUuid() != null && entry.uuid.equals(result.getWinnerUuid()))
                .map(entry -> entry.partyName)
                .findFirst()
                .orElse(config.getDefaultPartyName());
        return MessageManager.placeholders(
                "town", result.getTownName(),
                "system", messages.raw(result.getVotingSystem().messageKey()),
                "rounds", String.valueOf(result.getRounds().size()),
                "rank", String.valueOf(rank),
                "candidate", standing == null ? "-" : standing.name,
                "party", standing == null ? "-" : standing.partyName,
                "candidate_votes", standing == null ? "0" : String.valueOf(standing.votes),
                "percent", String.valueOf(percent),
                "winner", result.hasWinner() ? result.getWinnerName() : messages.raw("results.no-winner"),
                "winner_party", winnerParty,
                "winner_votes", String.valueOf(result.getWinnerVotes()),
                "total", String.valueOf(result.getTotalVotes()),
                "residents", String.valueOf(result.getResidentCount()),
                "turnout", String.valueOf(turnout));
    }

    private String candidateName(Town town, UUID candidateUuid) {
        Election election = elections.getElection(town);
        Candidate candidate = election == null ? null : election.getCandidate(candidateUuid);
        return candidate == null ? "?" : candidate.getName();
    }

    private void respond(Player player, OperationResult result, Map<String, String> placeholders) {
        messages.send(player, result.getMessageKey(), placeholders);
    }

    private enum PendingInputType {
        CAMPAIGN,
        PARTY
    }

    private record PendingInput(PendingInputType type, UUID townUuid) {
    }

    private record PlayerContext(Resident resident, Town town) {
    }

    private record PartyEntry(String name, int votes, List<String> candidates) {
    }
}
