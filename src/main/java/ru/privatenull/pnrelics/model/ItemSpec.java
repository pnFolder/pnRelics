package ru.privatenull.pnrelics.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ItemSpec(
        String material,
        String base64,
        String displayName,
        List<String> lore,
        Map<String, Integer> enchantments,
        Set<String> flags,
        boolean unbreakable,
        boolean glow,
        Integer customModelData
) {
    public ItemSpec {
        lore = List.copyOf(lore);
        enchantments = Map.copyOf(enchantments);
        flags = Set.copyOf(flags);
    }
}

