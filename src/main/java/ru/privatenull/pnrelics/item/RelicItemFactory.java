package ru.privatenull.pnrelics.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.pnrelics.model.ItemSpec;
import ru.privatenull.pnrelics.model.RelicDefinition;
import ru.privatenull.pnrelics.model.RelicKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RelicItemFactory {
    private final NamespacedKey definitionKey;
    private final NamespacedKey kindKey;

    public RelicItemFactory(JavaPlugin plugin) {
        definitionKey = new NamespacedKey(plugin, "definition");
        kindKey = new NamespacedKey(plugin, "kind");
    }

    public ItemStack create(RelicDefinition definition) {
        ItemSpec spec = definition.item();
        ItemStack item = spec.base64() == null
                ? new ItemStack(resolveMaterial(spec.material()))
                : SkullFactory.create(spec.base64());

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(ColorUtil.colorize(spec.displayName()));
        if (!spec.lore().isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (String line : spec.lore()) lore.add(ColorUtil.colorize(line));
            meta.setLore(lore);
        }
        meta.setUnbreakable(spec.unbreakable());
        if (spec.customModelData() != null) meta.setCustomModelData(spec.customModelData());
        Iterable<String> configuredFlags = spec.flags().contains("*") ? allFlags() : spec.flags();
        for (String rawFlag : configuredFlags) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(rawFlag));
            } catch (IllegalArgumentException ignored) {
                // Unknown flags are ignored to keep one config working across supported versions.
            }
        }
        meta.getPersistentDataContainer().set(definitionKey, PersistentDataType.STRING, definition.name());
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, definition.kind().name());
        item.setItemMeta(meta);

        for (Map.Entry<String, Integer> enchantment : spec.enchantments().entrySet()) {
            Enchantment resolved = resolveEnchantment(enchantment.getKey());
            if (resolved != null) item.addUnsafeEnchantment(resolved, enchantment.getValue());
        }
        if (spec.glow() && item.getEnchantments().isEmpty()) {
            Enchantment glow = resolveEnchantment("durability");
            if (glow != null) item.addUnsafeEnchantment(glow, 1);
            ItemMeta glowingMeta = item.getItemMeta();
            if (glowingMeta != null) {
                glowingMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(glowingMeta);
            }
        }
        return item;
    }

    public String definitionName(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(definitionKey, PersistentDataType.STRING);
    }

    public boolean isExpectedKind(ItemStack item, RelicKind kind) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return kind.name().equals(meta.getPersistentDataContainer().get(kindKey, PersistentDataType.STRING));
    }

    public String displayName(RelicDefinition definition) {
        return ColorUtil.colorize(definition.item().displayName());
    }

    private static Material resolveMaterial(String value) {
        Material material = value == null ? null : Material.matchMaterial(value.trim());
        return material == null ? Material.STONE : material;
    }

    private static List<String> allFlags() {
        List<String> result = new ArrayList<>();
        for (ItemFlag flag : ItemFlag.values()) result.add(flag.name());
        return result;
    }

    @SuppressWarnings("deprecation")
    private static Enchantment resolveEnchantment(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
        Enchantment byKey = Enchantment.getByKey(NamespacedKey.minecraft(modernEnchantmentName(normalized)));
        if (byKey != null) return byKey;
        return Enchantment.getByName(normalized.toUpperCase(Locale.ROOT));
    }

    private static String modernEnchantmentName(String value) {
        return switch (value) {
            case "protection_environmental" -> "protection";
            case "protection_fire" -> "fire_protection";
            case "protection_fall" -> "feather_falling";
            case "protection_explosions" -> "blast_protection";
            case "protection_projectile" -> "projectile_protection";
            case "damage_all" -> "sharpness";
            case "damage_undead" -> "smite";
            case "damage_arthropods" -> "bane_of_arthropods";
            case "durability" -> "unbreaking";
            case "loot_bonus_mobs" -> "looting";
            case "loot_bonus_blocks" -> "fortune";
            case "dig_speed" -> "efficiency";
            case "arrow_damage" -> "power";
            case "arrow_knockback" -> "punch";
            case "arrow_fire" -> "flame";
            case "arrow_infinite" -> "infinity";
            default -> value;
        };
    }
}
