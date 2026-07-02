package com.townyelections.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class ElectionMenuHolder implements InventoryHolder {

    private final ElectionMenuView view;
    private final UUID townUuid;
    private final int page;
    private final Map<Integer, ElectionMenuAction> actions = new HashMap<>();
    private final Map<Integer, UUID> candidates = new HashMap<>();
    private Inventory inventory;

    ElectionMenuHolder(ElectionMenuView view, UUID townUuid, int page) {
        this.view = view;
        this.townUuid = townUuid;
        this.page = page;
    }

    ElectionMenuView getView() {
        return view;
    }

    UUID getTownUuid() {
        return townUuid;
    }

    int getPage() {
        return page;
    }

    void setAction(int slot, ElectionMenuAction action) {
        actions.put(slot, action);
    }

    ElectionMenuAction getAction(int slot) {
        return actions.get(slot);
    }

    void setCandidate(int slot, UUID candidateUuid) {
        candidates.put(slot, candidateUuid);
    }

    UUID getCandidate(int slot) {
        return candidates.get(slot);
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
