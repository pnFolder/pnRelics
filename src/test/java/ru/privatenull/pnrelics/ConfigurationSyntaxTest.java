package ru.privatenull.pnrelics;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationSyntaxTest {
    @Test
    void bundledYamlFilesAreValid() {
        for (String name : new String[]{"messages.yml", "gui.yml", "runes.yml", "items.yml"}) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
                assertNotNull(input, name);
                Object parsed = new Yaml().load(input);
                assertTrue(parsed instanceof Map<?, ?>, name);
            } catch (Exception exception) {
                throw new AssertionError(name, exception);
            }
        }
    }

    @Test
    void bundledRuneExamplesCoverBothVisualTypesAndCompactEffects() {
        Map<?, ?> root = resource("runes.yml");
        Map<?, ?> runes = (Map<?, ?>) root.get("runes");
        assertTrue(runes.values().stream().map(Map.class::cast).anyMatch(value -> value.containsKey("base64")));
        assertTrue(runes.values().stream().map(Map.class::cast).anyMatch(value -> value.containsKey("material")));
        Map<?, ?> flame = (Map<?, ?>) runes.get("flame");
        Map<?, ?> effects = (Map<?, ?>) flame.get("effects");
        assertEquals(2, effects.get("strength"));
        for (Object raw : runes.values()) {
            Map<?, ?> rune = (Map<?, ?>) raw;
            if (!rune.containsKey("base64")) continue;
            String decoded = new String(Base64.getDecoder().decode(String.valueOf(rune.get("base64"))),
                    StandardCharsets.UTF_8);
            assertTrue(decoded.contains("textures.minecraft.net/texture/"));
        }
    }

    @Test
    void bundledGuiUsesCurrentB64Design() {
        Map<?, ?> root = resource("gui.yml");
        Map<?, ?> list = (Map<?, ?>) root.get("list");
        for (String button : new String[]{"page-info", "previous-page", "next-page",
                "previous-page-disabled", "next-page-disabled", "close"}) {
            Map<?, ?> section = (Map<?, ?>) list.get(button);
            assertNotNull(section, button);
            assertTrue(String.valueOf(section.get("base64")).length() > 100, button);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> resource(String name) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, name);
            return new Yaml().load(input);
        } catch (Exception exception) {
            throw new AssertionError(name, exception);
        }
    }
}
