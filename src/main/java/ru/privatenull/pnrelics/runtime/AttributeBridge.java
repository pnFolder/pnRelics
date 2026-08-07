package ru.privatenull.pnrelics.runtime;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnrelics.model.AttributeBonus;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AttributeBridge {
    private final JavaPlugin plugin;

    public AttributeBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Applied apply(Player player, AttributeBonus bonus, String source) {
        Attribute attribute = resolveAttribute(bonus.attribute());
        if (attribute == null) {
            plugin.getLogger().warning("Неизвестный атрибут: " + bonus.attribute());
            return null;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return null;

        try {
            AttributeModifier.Operation operation = AttributeModifier.Operation.valueOf(bonus.operation());
            AttributeModifier modifier = createModifier(source, bonus.amount(), operation);
            instance.addModifier(modifier);
            return new Applied(instance, modifier);
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError exception) {
            plugin.getLogger().warning("Не удалось применить атрибут " + bonus.attribute() + ": "
                    + exception.getMessage());
            return null;
        }
    }

    public void remove(Applied applied) {
        if (applied == null) return;
        try {
            applied.instance().removeModifier(applied.modifier());
        } catch (RuntimeException | LinkageError ignored) {
            // The entity or attribute may already be invalid during shutdown.
        }
    }

    private AttributeModifier createModifier(String source, double amount,
                                             AttributeModifier.Operation operation)
            throws ReflectiveOperationException {
        UUID uuid = UUID.randomUUID();
        String legacyName = "pnRelics:" + source;
        NamespacedKey key = new NamespacedKey(plugin,
                "runtime/" + uuid.toString().replace("-", "").toLowerCase(Locale.ROOT));

        for (Constructor<?> constructor : AttributeModifier.class.getConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types.length == 3 && types[0] == NamespacedKey.class
                    && types[1] == double.class && types[2] == AttributeModifier.Operation.class) {
                return (AttributeModifier) constructor.newInstance(key, amount, operation);
            }
            if (types.length == 4 && types[0] == UUID.class && types[1] == String.class
                    && types[2] == double.class && types[3] == AttributeModifier.Operation.class) {
                return (AttributeModifier) constructor.newInstance(uuid, legacyName, amount, operation);
            }
            if (types.length == 4 && types[0] == NamespacedKey.class && types[1] == double.class
                    && types[2] == AttributeModifier.Operation.class) {
                Object group = anySlotGroup(types[3]);
                if (group != null) return (AttributeModifier) constructor.newInstance(key, amount, operation, group);
            }
        }
        throw new NoSuchMethodException("совместимый конструктор AttributeModifier не найден");
    }

    private static Object anySlotGroup(Class<?> type) {
        for (String fieldName : List.of("ANY", "ALL")) {
            try {
                return type.getField(fieldName).get(null);
            } catch (ReflectiveOperationException ignored) {
                // Try the next known field.
            }
        }
        return null;
    }

    private Attribute resolveAttribute(String configured) {
        String normalized = configured.toUpperCase(Locale.ROOT).replace('-', '_');
        List<String> fields = new ArrayList<>();
        fields.add(normalized);
        if (normalized.startsWith("GENERIC_")) fields.add(normalized.substring("GENERIC_".length()));
        else fields.add("GENERIC_" + normalized);

        for (String fieldName : fields) {
            try {
                Field field = Attribute.class.getField(fieldName);
                if (Modifier.isStatic(field.getModifiers())) {
                    Object value = field.get(null);
                    if (value instanceof Attribute attribute) return attribute;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Try registry lookup below.
            }
        }
        return resolveFromRegistry(attributeKey(normalized));
    }

    private Attribute resolveFromRegistry(String keyName) {
        try {
            Class<?> registryType = Class.forName("org.bukkit.Registry");
            Field attributeField = registryType.getField("ATTRIBUTE");
            Object registry = attributeField.get(null);
            Method get = registry.getClass().getMethod("get", NamespacedKey.class);
            Object result = get.invoke(registry, NamespacedKey.minecraft(keyName));
            return result instanceof Attribute attribute ? attribute : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static String attributeKey(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("generic_")) normalized = normalized.substring("generic_".length());
        return normalized;
    }

    public record Applied(AttributeInstance instance, AttributeModifier modifier) {
    }
}

