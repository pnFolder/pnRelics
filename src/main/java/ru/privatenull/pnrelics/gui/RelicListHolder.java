package ru.privatenull.pnrelics.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class RelicListHolder implements InventoryHolder {
    private int page;
    private Inventory inventory;

    public RelicListHolder(int page) {
        this.page = page;
    }

    public int page() {
        return page;
    }

    public void page(int page) {
        this.page = page;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

