package ru.privatenull.pnrelics.runtime;

import ru.privatenull.pnrelics.integration.Log;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import ru.privatenull.pnrelics.integration.PluginTasks;
import ru.privatenull.pnrelics.config.RelicRegistry;
import ru.privatenull.pnrelics.item.RelicItemFactory;
import ru.privatenull.pnrelics.model.ActivationSlot;
import ru.privatenull.pnrelics.model.AttributeBonus;
import ru.privatenull.pnrelics.model.EffectBonus;
import ru.privatenull.pnrelics.model.RelicDefinition;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Method;

public final class RelicEffectService {
    private static final Set<ActivationSlot> ALL_SLOTS = EnumSet.allOf(ActivationSlot.class);

    private final JavaPlugin plugin;
    private final RelicRegistry registry;
    private final RelicItemFactory items;
    private final AttributeBridge attributes;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<String, PotionEffectType> effectCache = new HashMap<>();
    private final Set<String> unknownEffects = new HashSet<>();
    private long periodTicks;
    private TaskHandle task;

    public RelicEffectService(JavaPlugin plugin, RelicRegistry registry, RelicItemFactory items) {
        this.plugin = plugin;
        this.registry = registry;
        this.items = items;
        this.attributes = new AttributeBridge(plugin);
    }

    public void start(long requestedPeriod) {
        stopTask();
        periodTicks = Math.max(1L, requestedPeriod);
        task = PluginTasks.timer(1L, periodTicks, this::syncOnline);
    }

    public void sync(Player player) {
        if (player == null || !player.isOnline()) return;
        List<ActiveRelic> active = activeRelics(player);
        String signature = signature(active);
        Session session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new Session());
        if (!signature.equals(session.signature)) {
            removeAttributes(session);
            applyAttributes(player, active, session);
            session.signature = signature;
        }
        applyEffects(player, active, session);
    }

    public void syncNextTick(Player player) {
        if (player == null) return;
        PluginTasks.run(() -> sync(player));
    }

    public void refreshAll() {
        for (Session session : sessions.values()) removeAttributes(session);
        sessions.clear();
        syncOnline();
    }

    public void remove(Player player) {
        if (player == null) return;
        Session session = sessions.remove(player.getUniqueId());
        if (session != null) {
            removeAttributes(session);
            removeManagedEffects(player, session, Map.of());
        }
    }

    public void shutdown() {
        stopTask();
        for (Player player : plugin.getServer().getOnlinePlayers()) remove(player);
        sessions.clear();
    }

    private void syncOnline() {
        for (Player player : plugin.getServer().getOnlinePlayers()) sync(player);
    }

    private List<ActiveRelic> activeRelics(Player player) {
        List<ActiveRelic> result = new ArrayList<>();
        Set<String> nonStacking = new HashSet<>();
        for (ActivationSlot slot : ALL_SLOTS) {
            ItemStack item = slot.item(player);
            String name = items.definitionName(item);
            if (name == null) continue;
            RelicDefinition definition = registry.find(name);
            if (definition == null || !definition.activeSlots().contains(slot)) continue;
            if (!items.isExpectedKind(item, definition.kind())) continue;
            if (!definition.stacking() && !nonStacking.add(definition.name())) continue;
            result.add(new ActiveRelic(slot, definition));
        }
        return result;
    }

    private void applyAttributes(Player player, List<ActiveRelic> active, Session session) {
        int sequence = 0;
        for (ActiveRelic relic : active) {
            for (AttributeBonus bonus : relic.definition().attributes()) {
                String source = relic.definition().name() + "/" + relic.slot().name().toLowerCase(Locale.ROOT)
                        + "/" + sequence++;
                AttributeBridge.Applied applied = attributes.apply(player, bonus, source);
                if (applied != null) session.attributes.add(applied);
            }
        }
    }

    private void removeAttributes(Session session) {
        for (AttributeBridge.Applied applied : session.attributes) attributes.remove(applied);
        session.attributes.clear();
    }

    private void applyEffects(Player player, List<ActiveRelic> active, Session session) {
        Map<PotionEffectType, DesiredEffect> desired = new LinkedHashMap<>();
        for (ActiveRelic relic : active) {
            for (EffectBonus bonus : relic.definition().effects()) {
                PotionEffectType type = resolveEffect(bonus.effect());
                if (type == null) continue;
                DesiredEffect candidate = new DesiredEffect(bonus.amplifier());
                DesiredEffect current = desired.get(type);
                if (current == null || candidate.amplifier() > current.amplifier()) desired.put(type, candidate);
            }
        }

        removeManagedEffects(player, session, desired);
        int duration = (int) Math.min(Integer.MAX_VALUE, Math.max(30L, periodTicks * 3L + 5L));
        for (Map.Entry<PotionEffectType, DesiredEffect> entry : desired.entrySet()) {
            PotionEffectType type = entry.getKey();
            DesiredEffect effect = entry.getValue();
            PotionEffect current = player.getPotionEffect(type);
            if (current != null && (current.getAmplifier() > effect.amplifier()
                    || (current.getAmplifier() == effect.amplifier() && current.getDuration() > duration))) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(type, duration, effect.amplifier(), true,
                    false, true), true);
        }
        session.effects.clear();
        session.effects.putAll(desired);
    }

    private void removeManagedEffects(Player player, Session session,
                                      Map<PotionEffectType, DesiredEffect> desired) {
        int maximumManagedDuration = (int) Math.min(Integer.MAX_VALUE,
                Math.max(35L, periodTicks * 3L + 10L));
        for (Map.Entry<PotionEffectType, DesiredEffect> previous : session.effects.entrySet()) {
            if (desired.containsKey(previous.getKey())) continue;
            PotionEffect current = player.getPotionEffect(previous.getKey());
            if (current != null && current.getAmplifier() == previous.getValue().amplifier()
                    && current.getDuration() <= maximumManagedDuration) {
                player.removePotionEffect(previous.getKey());
            }
        }
    }

    @SuppressWarnings("deprecation")
    private PotionEffectType resolveEffect(String configured) {
        String cacheName = configured.toUpperCase(Locale.ROOT);
        PotionEffectType cached = effectCache.get(cacheName);
        if (cached != null) return cached;
        if (unknownEffects.contains(cacheName)) return null;
        String modern = modernEffectName(configured);
        PotionEffectType byKey = resolveEffectByKey(modern);
        if (byKey != null) {
            effectCache.put(cacheName, byKey);
            return byKey;
        }
        PotionEffectType legacy = PotionEffectType.getByName(legacyEffectName(modern));
        if (legacy == null) {
            unknownEffects.add(cacheName);
            Log.warning("Неизвестный эффект: " + configured);
        } else {
            effectCache.put(cacheName, legacy);
        }
        return legacy;
    }

    private static PotionEffectType resolveEffectByKey(String modernName) {
        try {
            Method method = PotionEffectType.class.getMethod("getByKey", NamespacedKey.class);
            Object result = method.invoke(null, NamespacedKey.minecraft(modernName.toLowerCase(Locale.ROOT)));
            return result instanceof PotionEffectType type ? type : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static String modernEffectName(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "INCREASE_DAMAGE" -> "STRENGTH";
            case "FAST_DIGGING" -> "HASTE";
            case "SLOW_DIGGING" -> "MINING_FATIGUE";
            case "HEAL" -> "INSTANT_HEALTH";
            case "HARM" -> "INSTANT_DAMAGE";
            case "JUMP" -> "JUMP_BOOST";
            case "CONFUSION" -> "NAUSEA";
            case "DAMAGE_RESISTANCE" -> "RESISTANCE";
            default -> normalized;
        };
    }

    private static String legacyEffectName(String value) {
        return switch (value) {
            case "STRENGTH" -> "INCREASE_DAMAGE";
            case "HASTE" -> "FAST_DIGGING";
            case "MINING_FATIGUE" -> "SLOW_DIGGING";
            case "INSTANT_HEALTH" -> "HEAL";
            case "INSTANT_DAMAGE" -> "HARM";
            case "JUMP_BOOST" -> "JUMP";
            case "NAUSEA" -> "CONFUSION";
            case "RESISTANCE" -> "DAMAGE_RESISTANCE";
            default -> value;
        };
    }

    private static String signature(List<ActiveRelic> active) {
        StringBuilder result = new StringBuilder();
        for (ActiveRelic relic : active) {
            if (result.length() > 0) result.append('|');
            result.append(relic.slot()).append(':').append(relic.definition().name());
        }
        return result.toString();
    }

    private void stopTask() {
        if (task != null) task.cancel();
        task = null;
    }

    private record ActiveRelic(ActivationSlot slot, RelicDefinition definition) {
    }

    private record DesiredEffect(int amplifier) {
    }

    private static final class Session {
        private String signature = "";
        private final List<AttributeBridge.Applied> attributes = new ArrayList<>();
        private final Map<PotionEffectType, DesiredEffect> effects = new HashMap<>();
    }
}
