package ru.privatenull.pnrelics.model;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record RelicDefinition(
        String name,
        RelicKind kind,
        ItemSpec item,
        Set<ActivationSlot> activeSlots,
        List<AttributeBonus> attributes,
        List<EffectBonus> effects,
        boolean stacking
) {
    public RelicDefinition {
        name = normalizeName(name);
        activeSlots = Set.copyOf(activeSlots);
        attributes = List.copyOf(attributes);
        effects = List.copyOf(effects);
    }

    public static String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
    }
}

