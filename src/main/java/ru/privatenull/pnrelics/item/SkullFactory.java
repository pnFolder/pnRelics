package ru.privatenull.pnrelics.item;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SkullFactory {
    private static final Pattern TEXTURE_URL = Pattern.compile(
            "https?://textures\\.minecraft\\.net/texture/[A-Za-z0-9]+"
    );
    private static final Set<String> REPORTED_TEXTURES = ConcurrentHashMap.newKeySet();

    private SkullFactory() {
    }

    public static ItemStack create(String texture) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null || texture == null || texture.isBlank()) return head;

        try {
            String payload = normalizePayload(texture);
            URL url = textureUrl(payload);
            if (url == null) throw new IllegalArgumentException("текстура не содержит корректный Minecraft URL");
            if (!applyModern(meta, url, payload)) applyLegacy(meta, payload);
            head.setItemMeta(meta);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            if (REPORTED_TEXTURES.add(texture)) {
                Bukkit.getLogger().warning("pnRelics: не удалось применить B64-текстуру: " + exception.getMessage());
            }
        }
        return head;
    }

    private static boolean applyModern(SkullMeta meta, URL url, String payload) {
        try {
            Class<?> profileType = Class.forName("org.bukkit.profile.PlayerProfile");
            Class<?> texturesType = Class.forName("org.bukkit.profile.PlayerTextures");
            UUID uuid = profileUuid(payload);
            Method create = Bukkit.class.getMethod("createPlayerProfile", UUID.class, String.class);
            Object profile = create.invoke(null, uuid, shortName(uuid));
            Object textures = profileType.getMethod("getTextures").invoke(profile);
            texturesType.getMethod("setSkin", URL.class).invoke(textures, url);
            profileType.getMethod("setTextures", texturesType).invoke(profile, textures);
            // Invoke the public Bukkit interface method. CraftMetaSkull itself is package-private
            // on modern Paper, so invoking a method obtained from its runtime class can fail with
            // IllegalAccessException and incorrectly fall through to the legacy GameProfile field.
            SkullMeta.class.getMethod("setOwnerProfile", profileType).invoke(meta, profile);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static void applyLegacy(SkullMeta meta, String payload) throws ReflectiveOperationException {
        UUID uuid = profileUuid(payload);
        GameProfile profile = new GameProfile(uuid, shortName(uuid));
        profile.getProperties().put("textures", new Property("textures", payload));
        Field field = findField(meta.getClass(), "profile");
        field.setAccessible(true);
        field.set(meta, profile);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static String normalizePayload(String input) {
        String value = input.trim();
        String lower = value.toLowerCase();
        if (lower.startsWith("base64:")) value = value.substring(7).trim();
        if (lower.startsWith("base64-")) value = value.substring(7).trim();
        Matcher matcher = TEXTURE_URL.matcher(value);
        if (matcher.find() || value.matches("[A-Fa-f0-9]{32,}")) {
            String url = value.matches("[A-Fa-f0-9]{32,}")
                    ? "https://textures.minecraft.net/texture/" + value
                    : matcher.group();
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }
        return value;
    }

    private static URL textureUrl(String payload) {
        try {
            String json = new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
            Matcher matcher = TEXTURE_URL.matcher(json);
            return matcher.find() ? new URL(matcher.group()) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static UUID profileUuid(String payload) {
        return UUID.nameUUIDFromBytes(("pnRelics:" + payload).getBytes(StandardCharsets.UTF_8));
    }

    private static String shortName(UUID uuid) {
        return "pn" + uuid.toString().replace("-", "").substring(0, 14);
    }
}
