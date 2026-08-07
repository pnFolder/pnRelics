package ru.privatenull.pnrelics.model;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Set;

public enum ActivationSlot {
    MAIN_HAND,
    OFF_HAND,
    HELMET,
    CHESTPLATE,
    LEGGINGS,
    BOOTS;

    public ItemStack item(Player player) {
        return switch (this) {
            case MAIN_HAND -> player.getInventory().getItemInMainHand();
            case OFF_HAND -> player.getInventory().getItemInOffHand();
            case HELMET -> player.getInventory().getHelmet();
            case CHESTPLATE -> player.getInventory().getChestplate();
            case LEGGINGS -> player.getInventory().getLeggings();
            case BOOTS -> player.getInventory().getBoots();
        };
    }

    public static ActivationSlot parse(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "HAND", "MAINHAND" -> MAIN_HAND;
            case "OFFHAND" -> OFF_HAND;
            case "HEAD" -> HELMET;
            case "CHEST" -> CHESTPLATE;
            case "LEGS" -> LEGGINGS;
            case "FEET" -> BOOTS;
            default -> {
                try {
                    yield valueOf(normalized);
                } catch (IllegalArgumentException ignored) {
                    yield null;
                }
            }
        };
    }

    /** Runes are talismans and only activate in the shield/totem slot. */
    public static Set<ActivationSlot> runeSlots() {
        return Set.of(OFF_HAND);
    }
}
