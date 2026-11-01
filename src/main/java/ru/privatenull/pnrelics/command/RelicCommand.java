package ru.privatenull.pnrelics.command;

import ru.privatenull.pnrelics.integration.Log;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.pnfolder.pnlibrary.api.commands.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.BadUsage;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.NoPermission;
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

public final class RelicCommand {
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

    /** Дерево команды /pnrelic для pnLibrary: имя, права, подсказки и тексты прежние. */
    public CommandDefinition definition() {
        return CommandDefinition.builder("pnrelic")
                .permission(PERMISSION)
                .consoleBypassesPermission(true)
                .onError(NoPermission.class, (context, error) -> messages.send(bukkit(context), "no-permission"))
                .onError(BadUsage.class, (context, error) -> sendHelp(bukkit(context)))
                .executes(context -> sendHelp(bukkit(context)))
                .literal("list", node -> node.executes(context -> openList(bukkit(context))))
                .literal("reload", node -> node.executes(context -> reload(bukkit(context))))
                .literal("give", node -> node.argument("player", ArgumentType.string(), player -> {
                    player.suggests(context -> filter(onlinePlayers(), partial(context)));
                    player.argument("relic", ArgumentType.string(), relic -> {
                        relic.suggests(context -> filter(registry.names(), partial(context)));
                        relic.executes(context -> give(bukkit(context), context, false));
                        relic.argument("amount", ArgumentType.string(), amount -> {
                            amount.suggests(context -> filter(AMOUNTS, partial(context)));
                            amount.executes(context -> give(bukkit(context), context, true));
                        });
                    });
                }))
                .build();
    }

    /** Отправитель Bukkit: игрок или консоль (так же вели себя прежние обработчики). */
    private static CommandSender bukkit(CommandContext context) {
        if (!context.getSender().isConsole()) {
            try {
                Player player = Bukkit.getPlayer(java.util.UUID.fromString(context.getSender().getId()));
                if (player != null) return player;
            } catch (IllegalArgumentException notPlayer) {
                // Не игрок (например, командный блок): обрабатывается как консоль.
            }
        }
        return Bukkit.getConsoleSender();
    }

    private static String partial(CommandContext context) {
        List<String> arguments = context.getArguments();
        return arguments.isEmpty() ? "" : arguments.get(arguments.size() - 1);
    }

    private static List<String> onlinePlayers() {
        List<String> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) players.add(player.getName());
        return players;
    }

    private void openList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return;
        }
        menu.open(player);
    }

    private void give(CommandSender sender, CommandContext context, boolean withAmount) {
        String[] args = {"give", context.<String>get("player"), context.<String>get("relic"),
                withAmount ? context.<String>get("amount") : null};
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
        int amount = withAmount ? parseAmount(args[3]) : 1;
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

