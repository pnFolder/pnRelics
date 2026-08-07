package ru.privatenull.pnrelics;

import org.junit.jupiter.api.Test;
import ru.privatenull.pnrelics.model.ActivationSlot;
import ru.privatenull.pnrelics.model.AttributeBonus;
import ru.privatenull.pnrelics.model.RelicDefinition;
import ru.privatenull.pnrelics.util.ServerVersion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelTest {
    @Test
    void parsesSupportedServerVersions() {
        assertEquals(new ServerVersion(1, 16, 5), ServerVersion.parse("1.16.5-R0.1-SNAPSHOT"));
        assertEquals(new ServerVersion(1, 21, 11), ServerVersion.parse("git-Paper-1.21.11"));
        assertTrue(ServerVersion.parse("1.16.5").supported());
        assertTrue(ServerVersion.parse("1.21.11").supported());
        assertFalse(ServerVersion.parse("1.15.2").supported());
    }

    @Test
    void normalizesConfigurationNames() {
        assertEquals("fire-rune", RelicDefinition.normalizeName(" Fire Rune "));
        assertEquals(ActivationSlot.MAIN_HAND, ActivationSlot.parse("main-hand"));
        assertEquals(ActivationSlot.OFF_HAND, ActivationSlot.parse("offhand"));
        assertEquals(ActivationSlot.HELMET, ActivationSlot.parse("head"));
    }

    @Test
    void runesActivateOnlyInOffHand() {
        assertEquals(java.util.Set.of(ActivationSlot.OFF_HAND), ActivationSlot.runeSlots());
        assertFalse(ActivationSlot.runeSlots().contains(ActivationSlot.MAIN_HAND));
    }

    @Test
    void normalizesAttributeOperations() {
        assertEquals("ADD_NUMBER", new AttributeBonus("attack-damage", 3, "add").operation());
        assertEquals("ATTACK_DAMAGE", new AttributeBonus("attack-damage", 3, "add").attribute());
        assertEquals("ADD_SCALAR", new AttributeBonus("movement_speed", .1, "percent").operation());
    }
}
