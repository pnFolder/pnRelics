package ru.privatenull.pnrelics;

import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnlibrary.gui.GuiOpenAnimationService;
import ru.privatenull.pnlibrary.gui.GuiUpdateService;
import ru.privatenull.pnlibrary.lifecycle.PluginBanner;
import ru.privatenull.pnlibrary.update.UpdateChecker;
import ru.privatenull.pnlibrary.update.UpdateSettings;
import ru.privatenull.pnrelics.command.RelicCommand;
import ru.privatenull.pnrelics.config.Messages;
import ru.privatenull.pnrelics.config.RelicRegistry;
import ru.privatenull.pnrelics.gui.RelicListMenu;
import ru.privatenull.pnrelics.item.RelicItemFactory;
import ru.privatenull.pnrelics.runtime.RelicEffectService;
import ru.privatenull.pnrelics.runtime.RelicListener;
import ru.privatenull.pnrelics.util.ServerVersion;

public final class PnRelicsPlugin extends JavaPlugin {
    private static final int BSTATS_PLUGIN_NUMBER = 33213;
    private static final long CHECK_PERIOD_TICKS = 10L;
    private static final boolean UPDATES_ENABLED = true;
    private static final String UPDATE_REPOSITORY = "pnFolder/pnRelics";
    private static final long UPDATE_PERIOD_HOURS = 6L;
    private static final String SUPPORT_URL = "https://discord.gg/rRbzq6cnc6";

    private Messages messages;
    private RelicRegistry registry;
    private RelicItemFactory itemFactory;
    private GuiUpdateService guiUpdates;
    private GuiOpenAnimationService guiAnimations;
    private RelicListMenu listMenu;
    private RelicEffectService effects;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        ServerVersion version = ServerVersion.current();
        if (!version.supported()) {
            getLogger().severe("pnRelics поддерживает Minecraft 1.16.x-1.21.x. Обнаружено: " + version);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        messages = new Messages(this);
        registry = new RelicRegistry(this);
        try {
            registry.load();
        } catch (RuntimeException exception) {
            getLogger().severe("Не удалось загрузить руны и предметы: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        itemFactory = new RelicItemFactory(this);
        guiUpdates = new GuiUpdateService();
        guiAnimations = new GuiOpenAnimationService(this, guiUpdates);
        listMenu = new RelicListMenu(this, registry, itemFactory, messages, guiAnimations, guiUpdates);
        effects = new RelicEffectService(this, registry, itemFactory);
        effects.start(CHECK_PERIOD_TICKS);

        RelicCommand executor = new RelicCommand(this, registry, itemFactory, listMenu, messages);
        PluginCommand command = getCommand("pnrelic");
        if (command == null) throw new IllegalStateException("Команда pnrelic отсутствует в plugin.yml");
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        setupUpdater();
        getServer().getPluginManager().registerEvents(listMenu, this);
        getServer().getPluginManager().registerEvents(new RelicListener(effects, updateChecker), this);
        new Metrics(this, BSTATS_PLUGIN_NUMBER);

        getLogger().info("Режим совместимости: Minecraft " + version);
        PluginBanner.enabled(this, SUPPORT_URL);
    }

    @Override
    public void onDisable() {
        if (listMenu != null) listMenu.closeAll();
        if (guiAnimations != null) guiAnimations.shutdown();
        if (effects != null) effects.shutdown();
        if (updateChecker != null) updateChecker.cancel();
        PluginBanner.disabled(this, SUPPORT_URL);
    }

    public int reloadPluginData() {
        registry.load();
        messages.load();
        listMenu.closeAll();
        listMenu.load();
        effects.start(CHECK_PERIOD_TICKS);
        effects.refreshAll();
        updateChecker.restart(updateSettings());
        return registry.all().size();
    }

    private void setupUpdater() {
        updateChecker = new UpdateChecker(this, updateSettings());
        updateChecker.start();
    }

    private UpdateSettings updateSettings() {
        return new UpdateSettings(
                UPDATES_ENABLED,
                UPDATE_REPOSITORY,
                "pnrelic.admin",
                UPDATE_PERIOD_HOURS,
                SUPPORT_URL
        );
    }
}
