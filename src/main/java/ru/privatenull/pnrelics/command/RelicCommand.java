package ru.privatenull.pnrelics.command;

import ru.privatenull.pnrelics.integration.Log;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import ru.privatenull.pnrelics.PnRelicsPlugin;
import ru.privatenull.pnrelics.config.Messages;
import ru.privatenull.pnrelics.config.RelicRegistry;
import ru.privatenull.pnrelics.gui.RelicListMenu;
import ru.privatenull.pnrelics.item.ItemDelivery;
import ru.privatenull.pnrelics.item.RelicItemFactory;
import ru.privatenull.pnrelics.model.RelicDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class RelicCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION = "pnrelic.admin";
    private static final List<String> SUBCOMMANDS = List.of("list", "give", "reload");
    private static final List<String> AMOUNTS = List.of("1", "2", "4", "8", "16", "32", "64");

    private final PnRelicsPlugin plugin;
    private final RelicRegistry registry;
    private final RelicItemFactory items;
    private final RelicListMenu menu;
    private final Messages messages;

    public RelicCommand(PnRelicsPlugin plugin, RelicRegistry registry, RelicItemFactory items,
                        RelicListMenu menu, Messages messages) {
        this.plugin = plugin;
        this.registry = registry;
        this.items = items;
        this.menu = menu;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> openList(sender);
            case "give" -> give(sender, args);
            case "reload" -> reload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        if (args.length == 1) return filter(SUBCOMMANDS, args[0]);
        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                List<String> players = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) players.add(player.getName());
                return filter(players, args[1]);
            }
            if (args.length == 3) return filter(registry.names(), args[2]);
            if (args.length == 4) return filter(AMOUNTS, args[3]);
        }
        return List.of();
    }

    private void openList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return;
        }
        menu.open(player);
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) {
            sendHelp(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found", "player", args[1]);
            return;
        }
        RelicDefinition definition = registry.find(args[2]);
        if (definition == null) {
            messages.send(sender, "relic-not-found", "name", args[2]);
            return;
        }
        int amount = args.length == 4 ? parseAmount(args[3]) : 1;
        if (amount < 1 || amount > 2304) {
            messages.send(sender, "invalid-amount");
            return;
        }

        boolean dropped = ItemDelivery.give(target, items.create(definition), amount);
        String displayName = items.displayName(definition);
        messages.send(sender, "give-success", "player", target.getName(),
                "amount", String.valueOf(amount), "name", displayName);
        if (!sender.equals(target)) {
            messages.send(target, "receive-success", "amount", String.valueOf(amount), "name", displayName);
        }
        if (dropped) messages.send(target, "inventory-full");
    }

    private void reload(CommandSender sender) {
        try {
            int count = plugin.reloadPluginData();
            messages.send(sender, "reload-success", "count", String.valueOf(count));
        } catch (RuntimeException exception) {
            messages.send(sender, "reload-failed", "error", safeMessage(exception));
            Log.error("Ошибка перезагрузки", exception);
        }
    }

    private void sendHelp(CommandSender sender) {
        for (String line : messages.list("help")) sender.sendMessage(line);
    }

    private static int parseAmount(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static List<String> filter(List<String> values, String prefix) {
        List<String> result = new ArrayList<>();
        StringUtil.copyPartialMatches(prefix == null ? "" : prefix, values, result);
        Collections.sort(result);
        return result;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}

