package ru.privatenull.pnrelics.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnrelics.model.ActivationSlot;
import ru.privatenull.pnrelics.model.AttributeBonus;
import ru.privatenull.pnrelics.model.EffectBonus;
import ru.privatenull.pnrelics.model.ItemSpec;
import ru.privatenull.pnrelics.model.RelicDefinition;
import ru.privatenull.pnrelics.model.RelicKind;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RelicRegistry {
    private final JavaPlugin plugin;
    private Map<String, RelicDefinition> definitions = Map.of();

    public RelicRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void load() {
        ensureResource("runes.yml");
        ensureResource("items.yml");

        LinkedHashMap<String, RelicDefinition> loaded = new LinkedHashMap<>();
        loadFile(new File(plugin.getDataFolder(), "runes.yml"), "runes", RelicKind.RUNE, loaded);
        loadFile(new File(plugin.getDataFolder(), "items.yml"), "items", RelicKind.ITEM, loaded);
        definitions = Collections.unmodifiableMap(loaded);
        plugin.getLogger().info("Загружено рун и предметов: " + definitions.size());
    }

    public RelicDefinition find(String name) {
        return definitions.get(RelicDefinition.normalizeName(name));
    }

    public Collection<RelicDefinition> all() {
        return definitions.values();
    }

    public List<RelicDefinition> sorted() {
        List<RelicDefinition> result = new ArrayList<>(definitions.values());
        result.sort(Comparator.comparing(RelicDefinition::kind).thenComparing(RelicDefinition::name));
        return result;
    }

    public List<String> names() {
        return new ArrayList<>(definitions.keySet());
    }

    private void ensureResource(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) plugin.saveResource(name, false);
    }

    private void loadFile(File file, String rootName, RelicKind kind,
                          Map<String, RelicDefinition> target) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection(rootName);
        if (root == null) return;

        for (String rawName : root.getKeys(false)) {
            String name = RelicDefinition.normalizeName(rawName);
            if (name.isBlank()) throw problem(file, rawName, "пустое название");
            if (target.containsKey(name)) throw problem(file, rawName, "название уже используется");
            ConfigurationSection section = root.getConfigurationSection(rawName);
            if (section == null) throw problem(file, rawName, "настройки должны быть разделом");
            target.put(name, parseDefinition(file, name, kind, section));
        }
    }

    private RelicDefinition parseDefinition(File file, String name, RelicKind kind,
                                            ConfigurationSection section) {
        String base64 = trimToNull(section.getString("base64"));
        String material = trimToNull(section.getString("material"));
        if (kind == RelicKind.RUNE && base64 == null && material == null) {
            throw problem(file, name, "для руны не указан material или base64");
        }
        if (kind == RelicKind.ITEM && base64 == null && material == null) {
            throw problem(file, name, "для предмета не указан material или base64");
        }

        String displayName = section.getString("name", "&f" + name);
        ItemSpec item = new ItemSpec(
                material,
                base64,
                displayName,
                section.getStringList("lore"),
                parseEnchantments(section.getConfigurationSection("enchantments")),
                parseFlags(section.get("flags")),
                section.getBoolean("unbreakable", false),
                section.getBoolean("glow", false),
                section.contains("custom-model-data") ? section.getInt("custom-model-data") : null
        );

        Set<ActivationSlot> slots = kind == RelicKind.RUNE
                ? ActivationSlot.runeSlots()
                : parseSlots(file, name, section.getStringList("active-slots"));

        return new RelicDefinition(
                name,
                kind,
                item,
                slots,
                parseAttributes(section.getConfigurationSection("attributes")),
                parseEffects(section.getConfigurationSection("effects")),
                section.getBoolean("stacking", false)
        );
    }

    private Map<String, Integer> parseEnchantments(ConfigurationSection section) {
        if (section == null) return Map.of();
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (String name : section.getKeys(false)) {
            result.put(name.toLowerCase(Locale.ROOT), Math.max(1, section.getInt(name, 1)));
        }
        return result;
    }

    private List<AttributeBonus> parseAttributes(ConfigurationSection section) {
        if (section == null) return List.of();
        List<AttributeBonus> result = new ArrayList<>();
        for (String name : section.getKeys(false)) {
            if (section.isConfigurationSection(name)) {
                ConfigurationSection value = section.getConfigurationSection(name);
                result.add(new AttributeBonus(name, value.getDouble("amount"),
                        value.getString("operation", "ADD_NUMBER")));
            } else {
                result.add(new AttributeBonus(name, section.getDouble(name), "ADD_NUMBER"));
            }
        }
        return result;
    }

    private List<EffectBonus> parseEffects(ConfigurationSection section) {
        if (section == null) return List.of();
        List<EffectBonus> result = new ArrayList<>();
        for (String name : section.getKeys(false)) {
            int level;
            if (section.isConfigurationSection(name)) {
                ConfigurationSection value = section.getConfigurationSection(name);
                level = value.getInt("level", 1);
            } else {
                level = section.getInt(name, 1);
            }
            result.add(new EffectBonus(name, Math.max(0, level - 1)));
        }
        return result;
    }

    private Set<ActivationSlot> parseSlots(File file, String name, List<String> values) {
        EnumSet<ActivationSlot> result = EnumSet.noneOf(ActivationSlot.class);
        for (String value : values) {
            ActivationSlot slot = ActivationSlot.parse(value);
            if (slot == null) throw problem(file, name, "неизвестный слот: " + value);
            result.add(slot);
        }
        return result;
    }

    private static Set<String> parseFlags(Object value) {
        if (value instanceof Boolean enabled) return enabled ? Set.of("*") : Set.of();
        Set<String> result = new LinkedHashSet<>();
        if (value instanceof List<?> values) {
            for (Object entry : values) {
                if (entry != null) result.add(String.valueOf(entry).trim().toUpperCase(Locale.ROOT));
            }
        } else if (value instanceof String text && !text.isBlank()) {
            result.add(text.trim().toUpperCase(Locale.ROOT));
        }
        return result;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static IllegalArgumentException problem(File file, String name, String message) {
        return new IllegalArgumentException(file.getName() + " -> " + name + ": " + message);
    }
}
