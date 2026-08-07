package ru.privatenull.pnrelics.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnlibrary.gui.GuiOpenAnimationService;
import ru.privatenull.pnlibrary.gui.GuiUpdateService;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.pnrelics.config.Messages;
import ru.privatenull.pnrelics.config.RelicRegistry;
import ru.privatenull.pnrelics.item.ItemDelivery;
import ru.privatenull.pnrelics.item.RelicItemFactory;
import ru.privatenull.pnrelics.item.SkullFactory;
import ru.privatenull.pnrelics.model.RelicDefinition;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class RelicListMenu implements Listener {
    private static final List<Integer> DEFAULT_CONTENT_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );

    private final JavaPlugin plugin;
    private final RelicRegistry registry;
    private final RelicItemFactory items;
    private final Messages messages;
    private final GuiOpenAnimationService animations;
    private final GuiUpdateService updates;
    private YamlConfiguration config;
    private Layout layout;

    public RelicListMenu(JavaPlugin plugin, RelicRegistry registry, RelicItemFactory items,
                         Messages messages, GuiOpenAnimationService animations,
                         GuiUpdateService updates) {
        this.plugin = plugin;
        this.registry = registry;
        this.items = items;
        this.messages = messages;
        this.animations = animations;
        this.updates = updates;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "gui.yml");
        if (!file.exists()) plugin.saveResource("gui.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("list");
        if (root == null) throw new IllegalArgumentException("gui.yml -> отсутствует раздел list");

        int size = normalizeSize(root.getInt("size", 54));
        List<Integer> content = validSlots(root.getIntegerList("content-slots"), size);
        if (content.isEmpty()) content = validSlots(DEFAULT_CONTENT_SLOTS, size);
        layout = new Layout(
                size,
                content,
                boundedSlot(root.getInt("previous-page.slot", 45), size),
                boundedSlot(root.getInt("page-info.slot", 4), size),
                boundedSlot(root.getInt("next-page.slot", 53), size),
                boundedSlot(root.getInt("close.slot", 49), size)
        );
    }

    public void open(Player player) {
        open(player, 0, false);
    }

    private void open(Player player, int requestedPage, boolean pageChange) {
        List<RelicDefinition> definitions = registry.sorted();
        if (definitions.isEmpty()) {
            messages.send(player, "list-empty");
            return;
        }

        int pageSize = layout.contentSlots().size();
        int pages = Math.max(1, (int) Math.ceil(definitions.size() / (double) pageSize));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory desired = createInventory(definitions, page, pages);

        Inventory current = player.getOpenInventory().getTopInventory();
        if (pageChange && current.getHolder() instanceof RelicListHolder holder
                && current.getSize() == desired.getSize()) {
            animations.cancel(player);
            holder.page(page);
            for (int slot = 0; slot < current.getSize(); slot++) {
                ItemStack before = current.getItem(slot);
                ItemStack after = desired.getItem(slot);
                if (!Objects.equals(before, after)) updates.setTopSlot(player, current, slot, after);
            }
        } else {
            animations.open(player, desired);
            play(player, Sound.BLOCK_BARREL_OPEN, 0.18f, 1.25f);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof RelicListHolder holder)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;

        List<RelicDefinition> definitions = registry.sorted();
        int pages = Math.max(1, (int) Math.ceil(definitions.size() / (double) layout.contentSlots().size()));
        if (slot == layout.previousSlot() && holder.page() > 0) {
            open(player, holder.page() - 1, true);
            play(player, Sound.UI_BUTTON_CLICK, 0.14f, 1.2f);
            return;
        }
        if (slot == layout.nextSlot() && holder.page() + 1 < pages) {
            open(player, holder.page() + 1, true);
            play(player, Sound.UI_BUTTON_CLICK, 0.14f, 1.2f);
            return;
        }
        if (slot == layout.closeSlot()) {
            animations.cancel(player);
            player.closeInventory();
            play(player, Sound.BLOCK_BARREL_CLOSE, 0.18f, 1.25f);
            return;
        }
        if (!event.getClick().isLeftClick()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        int localIndex = layout.contentSlots().indexOf(slot);
        if (localIndex < 0) return;
        int index = holder.page() * layout.contentSlots().size() + localIndex;
        if (index < 0 || index >= definitions.size()) return;
        RelicDefinition definition = definitions.get(index);
        ItemDelivery.give(player, items.create(definition), 1);
        messages.send(player, "list-taken", "name", items.displayName(definition));
        play(player, Sound.ENTITY_ITEM_PICKUP, 0.18f, 1.2f);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RelicListHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < topSize)) {
            event.setCancelled(true);
        }
    }

    public void closeAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof RelicListHolder) {
                player.closeInventory();
            }
        }
    }

    private Inventory createInventory(List<RelicDefinition> definitions, int page, int pages) {
        RelicListHolder holder = new RelicListHolder(page);
        String title = color(config.getString("list.title", "&0Руны и предметы"));
        Inventory inventory = Bukkit.createInventory(holder, layout.size(), title);
        holder.inventory(inventory);

        ItemStack decor = configuredItem(config.getConfigurationSection("list.decor"), " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, decor.clone());

        int start = page * layout.contentSlots().size();
        List<String> entryLore = config.getStringList("list.entry-lore");
        for (int local = 0; local < layout.contentSlots().size(); local++) {
            int index = start + local;
            if (index >= definitions.size()) break;
            ItemStack entry = items.create(definitions.get(index));
            appendLore(entry, entryLore, page, pages, definitions.size());
            hideAttributes(entry);
            inventory.setItem(layout.contentSlots().get(local), entry);
        }

        ConfigurationSection previous = config.getConfigurationSection(page > 0
                ? "list.previous-page" : "list.previous-page-disabled");
        inventory.setItem(layout.previousSlot(), configuredItem(previous,
                page > 0 ? "&#FC7165Предыдущая страница" : "&8Предыдущая страница",
                List.of(), page, pages, definitions.size()));

        ConfigurationSection next = config.getConfigurationSection(page + 1 < pages
                ? "list.next-page" : "list.next-page-disabled");
        inventory.setItem(layout.nextSlot(), configuredItem(next,
                page + 1 < pages ? "&#FC7165Следующая страница" : "&8Следующая страница",
                List.of(), page, pages, definitions.size()));

        inventory.setItem(layout.infoSlot(), configuredItem(
                config.getConfigurationSection("list.page-info"), "&#FC7165Список предметов",
                List.of(), page, pages, definitions.size()));
        inventory.setItem(layout.closeSlot(), configuredItem(
                config.getConfigurationSection("list.close"), "&#FC7165Закрыть",
                List.of(), page, pages, definitions.size()));
        return inventory;
    }

    private ItemStack configuredItem(ConfigurationSection section, String fallbackName,
                                     List<String> fallbackLore, int... pageData) {
        String base64 = section == null ? null : section.getString("base64");
        Material material = section == null ? Material.GRAY_STAINED_GLASS_PANE
                : Material.matchMaterial(section.getString("material", "GRAY_STAINED_GLASS_PANE"));
        ItemStack item = base64 == null || base64.isBlank()
                ? new ItemStack(material == null ? Material.GRAY_STAINED_GLASS_PANE : material)
                : SkullFactory.create(base64);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            int page = pageData.length > 0 ? pageData[0] : 0;
            int pages = pageData.length > 1 ? pageData[1] : 1;
            int amount = pageData.length > 2 ? pageData[2] : 0;
            meta.setDisplayName(color(replace(section == null ? fallbackName
                    : section.getString("name", fallbackName), page, pages, amount)));
            List<String> source = section != null && section.isList("lore")
                    ? section.getStringList("lore") : fallbackLore;
            List<String> lore = new ArrayList<>();
            for (String line : source) lore.add(color(replace(line, page, pages, amount)));
            meta.setLore(lore.isEmpty() ? null : lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void appendLore(ItemStack item, List<String> extra, int page, int pages, int amount) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || extra.isEmpty()) return;
        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        for (String line : extra) lore.add(color(replace(line, page, pages, amount)));
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private static void hideAttributes(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
    }

    private static List<Integer> validSlots(List<Integer> source, int size) {
        Set<Integer> unique = new LinkedHashSet<>();
        for (Integer slot : source) if (slot != null && slot >= 0 && slot < size) unique.add(slot);
        return new ArrayList<>(unique);
    }

    private static int normalizeSize(int requested) {
        int bounded = Math.max(9, Math.min(54, requested));
        return (bounded / 9) * 9;
    }

    private static int boundedSlot(int requested, int size) {
        return Math.max(0, Math.min(size - 1, requested));
    }

    private static String replace(String value, int page, int pages, int amount) {
        return (value == null ? "" : value)
                .replace("{page}", String.valueOf(page + 1))
                .replace("{pages}", String.valueOf(pages))
                .replace("{previous}", String.valueOf(Math.max(1, page)))
                .replace("{next}", String.valueOf(Math.min(pages, page + 2)))
                .replace("{amount}", String.valueOf(amount));
    }

    private static String color(String value) {
        return ColorUtil.colorize(value);
    }

    private static void play(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private record Layout(int size, List<Integer> contentSlots, int previousSlot,
                          int infoSlot, int nextSlot, int closeSlot) {
    }
}
