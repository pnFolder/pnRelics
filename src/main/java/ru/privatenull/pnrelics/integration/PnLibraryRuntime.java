package ru.privatenull.pnrelics.integration;

import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticContainer;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.remote.PolicyErrorAction;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider;
import org.pnfolder.pnlibrary.api.updates.UpdateChannel;

import java.util.function.Supplier;
import java.util.logging.Level;

/** Managed pnLibrary integration loaded only after the dependency is enabled. */
public final class PnLibraryRuntime implements AutoCloseable {

    public static final String MINIMUM_VERSION = "0.0.1-dev.6";
    private static final String MODULE_ID = "pnrelics";
    private static final int BSTATS_PROJECT_ID = 33213;
    private static final String FASTSTATS_TOKEN = "d1b2d467ca1dd9fe97e8163b3dd8db90";
    private static final String POLICY_SOURCE =
            "https://raw.githubusercontent.com/pnFolder/pnlibrary-policy/main/.pnlibrary/policies/PnRelicsPolicy.java";
    private static final String UPDATE_OWNER = "pnFolder";
    private static final String UPDATE_REPOSITORY = "pnRelics";

    private final JavaPlugin owner;
    private final PnLibrary runtime;
    private PluginRegistration registration;
    private ModuleContext module;

    private PnLibraryRuntime(JavaPlugin owner, PnLibrary runtime) {
        this.owner = owner;
        this.runtime = runtime;
    }

    public static PnLibraryRuntime connect(JavaPlugin owner) {
        PnLibrary runtime = PnLibraryProvider.get();
        if (!runtime.isAtLeastVersion(MINIMUM_VERSION)) {
            throw new IllegalStateException("pnRelics требует pnLibrary " + MINIMUM_VERSION
                    + " или новее, установлена " + runtime.getVersion());
        }
        return new PnLibraryRuntime(owner, runtime);
    }

    public void register(Supplier<String> diagnostics) {
        if (registration != null) throw new IllegalStateException("pnRelics уже зарегистрирован в pnLibrary");
        registration = runtime.getPlugins().register(owner);
        try {
            module = registration.registerModule(MODULE_ID, builder -> builder
                    .diagnostics(owner.getDataFolder().toPath(), DiagnosticContainer.builder("pnrelics")
                            .snapshot(() -> java.util.Map.of("relics", diagnostics.get()))
                            .configuration(DiagnosticConfiguration.builder("messages.yml").build())
                            .build())
                    // A policy that cannot be checked (offline server, JRE without a compiler) never stops pnRelics;
                    // only an explicit denial from the policy does.
                    .remotePolicy(RemotePolicySettings.builder()
                            .source(POLICY_SOURCE)
                            .onError(PolicyErrorAction.KEEP_RUNNING)
                            .support(PnLibraryIdentity.SUPPORT_URL)
                            .build())
                    .metrics(metrics -> metrics.bStats(BSTATS_PROJECT_ID).fastStats(FASTSTATS_TOKEN))
                    .updates(UPDATE_OWNER, UPDATE_REPOSITORY, updater -> updater
                            .channel(UpdateChannel.STABLE)
                            .automaticDownload(false)
                            .artifact("(?i)^pnRelics-[0-9].*\\.jar$", 17)));
        } catch (RuntimeException failure) {
            closeRegistration();
            throw failure;
        }
    }

    /** Shows the console card: the plugin has started. */
    public void started(String serverVersion, int relics) {
        if (module == null) return;
        try {
            module.getLifecycle().enabled()
                    .ok("Minecraft", serverVersion)
                    .ok("Руны и предметы", String.valueOf(relics))
                    .ok("Команда", "/pnrelic")
                    .ok("Диагностика", "/pndebug " + MODULE_ID)
                    .show();
        } catch (RuntimeException failure) {
            owner.getLogger().log(Level.FINE, "Не удалось вывести сообщение о запуске pnRelics", failure);
        }
    }

    /** Shows the console card: the plugin is stopping. Must run before the registration is closed. */
    public void stopped() {
        ModuleContext closing = module;
        if (closing == null || closing.isClosed()) return;
        try {
            closing.getLifecycle().disabled()
                    .ok("Эффекты", "остановлены")
                    .ok("Меню", "закрыты")
                    .show();
        } catch (RuntimeException failure) {
            owner.getLogger().log(Level.FINE, "Не удалось вывести сообщение об остановке pnRelics", failure);
        }
    }

    public String supportUrl() {
        return PnLibraryIdentity.SUPPORT_URL;
    }

    @Override
    public void close() {
        stopped();
        module = null;
        closeRegistration();
    }

    private void closeRegistration() {
        PluginRegistration closing = registration;
        registration = null;
        if (closing != null && !closing.isClosed()) closing.close();
    }
}
