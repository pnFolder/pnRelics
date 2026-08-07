package ru.privatenull.pnrelics.config;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class Messages {
    private final JavaPlugin plugin;
    private YamlConfiguration config;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public String text(String path, String... replacements) {
        String raw = config.getString(path, path);
        return color(replace(raw, replacements));
    }

    public List<String> list(String path, String... replacements) {
        List<String> result = new ArrayList<>();
        for (String line : config.getStringList(path)) result.add(color(replace(line, replacements)));
        return result;
    }

    public void send(CommandSender sender, String path, String... replacements) {
        sender.sendMessage(text(path, replacements));
    }

    private String replace(String value, String... replacements) {
        String result = value == null ? "" : value;
        result = result.replace("{prefix}", config.getString("prefix", ""));
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            result = result.replace("{" + replacements[index] + "}", replacements[index + 1]);
        }
        return result;
    }

    private static String color(String value) {
        return ColorUtil.colorize(value);
    }
}

