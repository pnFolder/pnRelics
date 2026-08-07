package ru.privatenull.pnrelics.model;

import java.util.Locale;

public record EffectBonus(String effect, int amplifier) {
    public EffectBonus {
        effect = effect == null ? "" : effect.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        amplifier = Math.max(0, amplifier);
    }
}
