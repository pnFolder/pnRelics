package ru.privatenull.pnrelics.model;

import java.util.Locale;

public record AttributeBonus(String attribute, double amount, String operation) {
    public AttributeBonus {
        attribute = normalize(attribute);
        operation = normalizeOperation(operation);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
    }

    private static String normalizeOperation(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "ADD", "ADDITION" -> "ADD_NUMBER";
            case "MULTIPLY_BASE", "ADD_PERCENT", "PERCENT" -> "ADD_SCALAR";
            case "MULTIPLY", "MULTIPLY_TOTAL" -> "MULTIPLY_SCALAR_1";
            case "" -> "ADD_NUMBER";
            default -> normalized;
        };
    }
}

