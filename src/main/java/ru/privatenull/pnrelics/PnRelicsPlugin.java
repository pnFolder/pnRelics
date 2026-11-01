package ru.privatenull.pnrelics;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnrelics.gui.GuiOpenAnimationService;
import ru.privatenull.pnrelics.gui.GuiUpdateService;
import org.pnfolder.pnlibrary.bukkit.bootstrap.PnLibraryBootstrap;
import ru.privatenull.pnrelics.bootstrap.PnLibraryLifecycleListener;
import ru.privatenull.pnrelics.integration.PnLibraryRuntime;
import ru.privatenull.pnrelics.command.RelicCommand;
import ru.privatenull.pnrelics.config.Messages;
import ru.privatenull.pnrelics.config.RelicRegistry;
import ru.privatenull.pnrelics.gui.RelicListMenu;
import ru.privatenull.pnrelics.item.RelicItemFactory;
import ru.privatenull.pnrelics.runtime.RelicEffectService;
import ru.privatenull.pnrelics.runtime.RelicListener;
import ru.privatenull.pnrelics.util.ServerVersion;

import java.util.logging.Level;

public final class PnRelicsPlugin extends JavaPlugin {
    private static final long CHECK_PERIOD_TICKS = 10L;

    private Messages messages;
    private RelicRegistry registry;
    private RelicItemFactory itemFactory;
    private GuiUpdateService guiUpdates;
    private GuiOpenAnimationService guiAnimations;
    private RelicListMenu listMenu;
    private RelicEffectService effects;
    private boolean pnLibraryReady;
    private PnLibraryRuntime pnRuntime;

    @Override
    public void onLoad() {
        try {
            pnLibraryReady = PnLibraryBootstrap.ensureInstalled(this, PnLibraryRuntime.MINIMUM_VERSION);
        } catch (Throwable failure) {
            getLogger().log(Level.SEVERE,
                    "Не удалось автоматически подготовить pnLibrary. pnRelics не будет запущен.", failure);
        }
    }

    @Override
    public void onEnable() {
        if (!pnLibraryReady) {
            getLogger().severe("pnLibrary не подготовлена: проверьте доступ к GitHub и ошибку загрузки выше.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            pnRuntime = PnLibraryRuntime.connect(this);
        } catch (Throwable failure) {
            getLogger().log(Level.SEVERE, "pnLibrary не удалось включить или подключить к pnRelics.", failure);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(new PnLibraryLifecycleListener(this), this);

        ServerVersion version = ServerVersion.current();
        if (!version.supported()) {
            getLogger().severe("pnRelics поддерживает Minecraft 1.16.x-1.21.x. Обнаружено: " + version);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        pnRuntime.register(() -> registry == null ? "-" : registry.all().size() + " relics");

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

        getServer().getPluginManager().registerEvents(listMenu, this);
        getServer().getPluginManager().registerEvents(new RelicListener(effects), this);

        getLogger().info("Режим совместимости: Minecraft " + version);
        pnRuntime.started(version.toString(), registry.all().size());
    }

    @Override
    public void onDisable() {
        if (listMenu != null) listMenu.closeAll();
        if (guiAnimations != null) guiAnimations.shutdown();
        if (effects != null) effects.shutdown();
        if (pnRuntime != null) pnRuntime.close();
    }

    public int reloadPluginData() {
        registry.load();
        messages.load();
        listMenu.closeAll();
        listMenu.load();
        effects.start(CHECK_PERIOD_TICKS);
        effects.refreshAll();
        return registry.all().size();
    }
}
