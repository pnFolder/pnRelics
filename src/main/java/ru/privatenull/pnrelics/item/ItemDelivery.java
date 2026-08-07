package ru.privatenull.pnrelics.item;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class ItemDelivery {
    private ItemDelivery() {
    }

    public static boolean give(Player player, ItemStack template, int amount) {
        if (player == null || template == null || amount <= 0) return false;
        boolean dropped = false;
        int remaining = amount;
        int stackSize = Math.max(1, template.getMaxStackSize());
        while (remaining > 0) {
            ItemStack stack = template.clone();
            stack.setAmount(Math.min(stackSize, remaining));
            remaining -= stack.getAmount();
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                dropped = true;
            }
        }
        return dropped;
    }
}

