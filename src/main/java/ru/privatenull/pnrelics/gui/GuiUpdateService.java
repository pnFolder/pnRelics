package ru.privatenull.pnrelics.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Updates individual slots and the title of an already open top inventory without reopening it.
 * Only the exact inventory the caller expects is touched, so a delayed refresh never changes another GUI.
 */
public final class GuiUpdateService {

    public void setTopSlot(Player player, Inventory expectedTop, int slot, ItemStack item) {
        if (player == null || expectedTop == null || slot < 0) return;
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top != expectedTop || slot >= top.getSize()) return;
        if (sameItem(top.getItem(slot), item)) return;
        top.setItem(slot, item == null ? null : item.clone());
    }

    public boolean setTitle(Player player, Inventory expectedTop, String title) {
        if (player == null || expectedTop == null || title == null) return false;
        InventoryView view = player.getOpenInventory();
        if (view.getTopInventory() != expectedTop) return false;
        if (title.equals(view.getTitle())) return true;
        try {
            Method method = InventoryView.class.getMethod("setTitle", String.class);
            method.invoke(view, title);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    /** Bukkit sends every changed slot on its own, so there is nothing extra to synchronise. */
    public void syncTop(Player player, Inventory expectedTop) {
    }

    private static boolean sameItem(ItemStack current, ItemStack replacement) {
        boolean currentEmpty = current == null || current.getType().isAir();
        boolean replacementEmpty = replacement == null || replacement.getType().isAir();
        if (currentEmpty || replacementEmpty) return currentEmpty == replacementEmpty;
        return current.equals(replacement);
    }
}
