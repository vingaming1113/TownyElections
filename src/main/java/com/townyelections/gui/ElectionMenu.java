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
import com.townyelections.util.DurationUtil;
import com.townyelections.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ElectionMenu implements Listener {

    private static final int MAIN_SIZE = 27;
    private static final int ROSTER_SIZE = 54;
    private static final int MAIN_STATUS_SLOT = 4;
    private static final int MAIN_CANDIDATES_SLOT = 11;
    private static final int MAIN_RUN_SLOT = 13;
    private static final int MAIN_WITHDRAW_SLOT = 15;
    private static final int ROSTER_BACK_SLOT = 45;
    private static final int ROSTER_PREVIOUS_SLOT = 48;
    private static final int ROSTER_NEXT_SLOT = 50;
    private static final List<Integer> CANDIDATE_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43);

    private final ElectionManager elections;
    private final MessageManager messages;
    private final ConfigManager config;
    private final TownyHook towny;

    public ElectionMenu(TownyElections plugin) {
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
        ElectionMenuHolder holder = new ElectionMenuHolder(ctx.town().getUUID(), 0);
        Inventory inventory = Bukkit.createInventory(holder, MAIN_SIZE,
                messages.legacy("gui.main-title", placeholders(ctx.town(), election)));
        holder.setInventory(inventory);

        fillFrame(inventory);
        inventory.setItem(MAIN_STATUS_SLOT, statusItem(ctx.town(), election, ctx.resident()));
        holder.setAction(MAIN_CANDIDATES_SLOT, ElectionMenuAction.CANDIDATES);
        inventory.setItem(MAIN_CANDIDATES_SLOT, icon(Material.PLAYER_HEAD,
                "gui.main-candidates-name", "gui.main-candidates-lore", placeholders(ctx.town(), election)));
        holder.setAction(MAIN_RUN_SLOT, ElectionMenuAction.RUN);
        inventory.setItem(MAIN_RUN_SLOT, icon(Material.NAME_TAG,
                "gui.main-run-name", "gui.main-run-lore", placeholders(ctx.town(), election)));
        holder.setAction(MAIN_WITHDRAW_SLOT, ElectionMenuAction.WITHDRAW);
        inventory.setItem(MAIN_WITHDRAW_SLOT, icon(Material.RED_BED,
                "gui.main-withdraw-name", "gui.main-withdraw-lore", placeholders(ctx.town(), election)));

        player.openInventory(inventory);
    }

    private void openRoster(Player player, UUID townUuid) {
        openRoster(player, townUuid, 0);
    }

    private void openRoster(Player player, UUID townUuid, int requestedPage) {
        PlayerContext ctx = resolveContext(player);
        if (ctx == null || !ctx.town().getUUID().equals(townUuid)) {
            player.closeInventory();
            return;
        }

        Election election = elections.getElection(ctx.town());
        List<Candidate> candidates = sortedCandidates(election);
        int maxPage = Math.max(0, (candidates.size() - 1) / CANDIDATE_SLOTS.size());
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        Map<String, String> menuPlaceholders = placeholders(ctx.town(), election);
        menuPlaceholders.put("page", String.valueOf(page + 1));
        menuPlaceholders.put("pages", String.valueOf(maxPage + 1));

        ElectionMenuHolder holder = new ElectionMenuHolder(townUuid, page);
        Inventory inventory = Bukkit.createInventory(holder, ROSTER_SIZE,
                messages.legacy("gui.roster-title", menuPlaceholders));
        holder.setInventory(inventory);
        fillFrame(inventory);

        holder.setAction(ROSTER_BACK_SLOT, ElectionMenuAction.BACK);
        inventory.setItem(ROSTER_BACK_SLOT, icon(Material.ARROW,
                "gui.back-name", "gui.back-lore", menuPlaceholders));

        if (candidates.isEmpty()) {
            inventory.setItem(22, icon(Material.BARRIER,
                    "gui.no-candidates-name", "gui.no-candidates-lore", menuPlaceholders));
        } else {
            Map<UUID, Integer> tally = election.tally();
            int start = page * CANDIDATE_SLOTS.size();
            int end = Math.min(candidates.size(), start + CANDIDATE_SLOTS.size());
            for (int index = start; index < end; index++) {
                Candidate candidate = candidates.get(index);
                int slot = CANDIDATE_SLOTS.get(index - start);
                holder.setCandidate(slot, candidate.getUuid());
                inventory.setItem(slot, candidateItem(candidate, election, tally, ctx.resident()));
            }
        }

        if (page > 0) {
            holder.setAction(ROSTER_PREVIOUS_SLOT, ElectionMenuAction.PREVIOUS_PAGE);
            inventory.setItem(ROSTER_PREVIOUS_SLOT, icon(Material.LIME_DYE,
                    "gui.previous-page-name", "gui.previous-page-lore", menuPlaceholders));
        }
        if (page < maxPage) {
            holder.setAction(ROSTER_NEXT_SLOT, ElectionMenuAction.NEXT_PAGE);
            inventory.setItem(ROSTER_NEXT_SLOT, icon(Material.LIME_DYE,
                    "gui.next-page-name", "gui.next-page-lore", menuPlaceholders));
        }

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
            case RUN -> handleRun(player, holder.getTownUuid());
            case WITHDRAW -> handleWithdraw(player, holder.getTownUuid());
            case BACK -> openMain(player);
            case PREVIOUS_PAGE -> openRoster(player, holder.getTownUuid(), holder.getPage() - 1);
            case NEXT_PAGE -> openRoster(player, holder.getTownUuid(), holder.getPage() + 1);
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

    private void handleRun(Player player, UUID townUuid) {
        if (!player.hasPermission("townyelections.candidate")) {
            messages.send(player, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(player);
        if (ctx == null || !ctx.town().getUUID().equals(townUuid)) {
            player.closeInventory();
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
        PlayerContext ctx = resolveContext(player);
        if (ctx == null || !ctx.town().getUUID().equals(townUuid)) {
            player.closeInventory();
            return;
        }
        respond(player, elections.withdrawCandidate(ctx.resident(), ctx.town()),
                MessageManager.placeholders("town", ctx.town().getName()));
        openMain(player);
    }

    private void handleCandidateClick(Player player, ElectionMenuHolder holder, UUID candidateUuid) {
        if (!player.hasPermission("townyelections.vote")) {
            messages.send(player, "general.no-permission");
            return;
        }
        PlayerContext ctx = resolveContext(player);
        if (ctx == null || !ctx.town().getUUID().equals(holder.getTownUuid())) {
            player.closeInventory();
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

    private List<Candidate> sortedCandidates(Election election) {
        if (election == null) {
            return List.of();
        }
        List<Candidate> candidates = new ArrayList<>(election.getCandidateList());
        candidates.sort(Comparator.comparing(Candidate::getName, String.CASE_INSENSITIVE_ORDER));
        return candidates;
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
        applyMeta(item, "gui.candidate-name", candidateLoreKey(election), placeholders);
        return item;
    }

    private String candidateLoreKey(Election election) {
        if (election == null || (election.getPhase() != ElectionPhase.VOTING
                && election.getPhase() != ElectionPhase.RUNOFF)) {
            return "gui.candidate-lore-closed";
        }
        return config.isPublicLiveResults() ? "gui.candidate-lore-public" : "gui.candidate-lore-secret";
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
        return placeholders(election, null, election == null ? Map.of() : election.tally(), null, town);
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
        String voted = messages.raw("gui.vote-none");
        if (election != null && viewer != null) {
            UUID choice = election.getVoteChoice(viewer.getUUID());
            Candidate chosen = choice == null ? null : election.getCandidate(choice);
            if (chosen != null) {
                voted = chosen.getName();
            }
        }

        return MessageManager.placeholders(
                "town", townName,
                "phase", phase,
                "time", time,
                "candidates", election == null ? "0" : String.valueOf(election.getCandidateCount()),
                "votes", election == null ? "0" : String.valueOf(election.getTotalVotes()),
                "candidate", candidateName,
                "party", party,
                "candidate_votes", String.valueOf(votes),
                "message", candidate == null ? "-" : candidate.getCampaignMessage(),
                "your_vote", voted);
    }

    private String candidateName(Town town, UUID candidateUuid) {
        Election election = elections.getElection(town);
        Candidate candidate = election == null ? null : election.getCandidate(candidateUuid);
        return candidate == null ? "?" : candidate.getName();
    }

    private void respond(Player player, OperationResult result, Map<String, String> placeholders) {
        messages.send(player, result.getMessageKey(), placeholders);
    }

    private record PlayerContext(Resident resident, Town town) {
    }
}
