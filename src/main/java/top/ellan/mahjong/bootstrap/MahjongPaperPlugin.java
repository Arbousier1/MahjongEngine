package top.ellan.mahjong.bootstrap;

import top.ellan.mahjong.command.MahjongCommand;
import top.ellan.mahjong.compat.CraftEngineService;
import top.ellan.mahjong.config.LocalizedConfigResource;
import top.ellan.mahjong.config.PluginSettings;
import top.ellan.mahjong.config.PluginSettingsListener;
import top.ellan.mahjong.debug.DebugService;
import top.ellan.mahjong.db.DatabaseService;
import top.ellan.mahjong.gb.jni.GbNativeWarmupService;
import top.ellan.mahjong.i18n.MessageService;
import top.ellan.mahjong.metrics.InMemoryMetricsCollector;
import top.ellan.mahjong.metrics.MetricsCollector;
import top.ellan.mahjong.render.display.DisplayVisibilityRegistry;
import top.ellan.mahjong.render.display.TableDisplayRegistry;
import top.ellan.mahjong.runtime.AsyncService;
import top.ellan.mahjong.runtime.ServerScheduler;
import top.ellan.mahjong.table.core.MahjongTableManager;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public final class MahjongPaperPlugin extends JavaPlugin {
    private final MessageService messages = new MessageService();
    private final MetricsCollector metrics = new InMemoryMetricsCollector();
    private final Set<PluginSettingsListener> settingsListeners = ConcurrentHashMap.newKeySet();
    private volatile PluginSettings settings;
    private DebugService debug;
    private AsyncService async;
    private ServerScheduler scheduler;
    private DatabaseService database;
    private CraftEngineService craftEngine;
    private MahjongTableManager tableManager;

    @Override
    public void onEnable() {
        LocalizedConfigResource.saveIfMissing(this, Locale.getDefault());
        this.async = new AsyncService(this.getLogger());
        this.scheduler = new ServerScheduler(this);
        String reloadFailure = this.reloadMahjongConfiguration();
        if (reloadFailure != null) {
            this.getLogger().severe("MahjongPaper failed to load configuration during startup: " + reloadFailure);
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (this.database != null) {
            this.getLogger().info("Database enabled: " + this.database.databaseType());
            this.debug.log("database", "Database service initialized with type=" + this.database.databaseType());
        }
        this.async.execute("gb-native-warmup", () -> GbNativeWarmupService.warmupOnce(this.getLogger()));

        this.tableManager = new MahjongTableManager(this);
        this.craftEngine.enableFurnitureInteractionBridge(this.tableManager);
        this.scheduler.runGlobal(this.craftEngine::cleanupMahjongFurniture);
        this.tableManager.loadPersistentTables();

        MahjongCommand mahjongCommand = new MahjongCommand(this, this.tableManager);
        this.registerCommand(
            "mahjong",
            "Manage MahjongPaper tables and rounds. Use /mahjong help for command explanations.",
            mahjongCommand
        );

        this.getServer().getPluginManager().registerEvents(this.tableManager, this);
        this.scheduler.runGlobal(() -> {
            this.craftEngine.initializeAfterStartup();
            if (this.tableManager != null) {
                this.tableManager.refreshPersistentTablesAfterStartup();
            }
        });
        this.getLogger().info("MahjongPaper enabled.");
        this.debug.log("lifecycle", "Plugin bootstrap complete.");
    }

    @Override
    public void onDisable() {
        if (this.craftEngine != null) {
            this.craftEngine.disableFurnitureInteractionBridge();
            this.craftEngine.clearTrackedCullables();
        }
        if (this.tableManager != null) {
            this.tableManager.shutdown();
        }
        TableDisplayRegistry.clear();
        DisplayVisibilityRegistry.clear();
        if (this.database != null) {
            this.database.close();
            this.database = null;
        }
        if (this.async != null) {
            this.async.close();
            this.async = null;
        }
        this.scheduler = null;
        this.craftEngine = null;
        if (this.debug != null) {
            this.debug.log("lifecycle", "Plugin shutdown complete.");
        }
    }

    private void handleDatabaseStartupFailure(DatabaseService.InitializationException ex) {
        Throwable rootCause = Objects.requireNonNullElse(ex.rootCause(), ex);
        this.getLogger().severe("Database initialization failed. MahjongPaper will continue with persistence disabled.");
        this.getLogger().severe("Reason: " + ex.userFacingReason());
        this.getLogger().severe("Detail: " + rootCause.getClass().getSimpleName() + ": " + Objects.toString(rootCause.getMessage(), "(no additional detail)"));
        this.getLogger().severe("Hint: use database.connection.type=h2 for local storage, or start MariaDB/MySQL and verify the configured connection settings.");
        if (this.debug != null && this.debug.isCategoryEnabled("database")) {
            this.getLogger().log(Level.SEVERE, "Database startup stack trace:", ex);
        }
    }

    public String reloadMahjongConfiguration() {
        this.reloadConfig();

        PluginSettings previousSettings = this.settings;
        PluginSettings reloadedSettings = PluginSettings.from(this.getConfig());
        DebugService reloadedDebug = new DebugService(this.getLogger(), reloadedSettings.debug());
        CraftEngineService reloadedCraftEngine = new CraftEngineService(this, reloadedSettings.craftEngine());
        DatabaseService reloadedDatabase = null;
        if (DatabaseService.isEnabled(reloadedSettings.database())) {
            try {
                reloadedDatabase = new DatabaseService(this, reloadedSettings.database());
            } catch (DatabaseService.InitializationException ex) {
                this.handleDatabaseStartupFailure(ex);
                if (reloadedSettings.databaseFailOnError()) {
                    return ex.userFacingReason();
                }
            }
        }

        CraftEngineService previousCraftEngine = this.craftEngine;
        DatabaseService previousDatabase = this.database;
        if (previousCraftEngine != null) {
            previousCraftEngine.disableFurnitureInteractionBridge();
            previousCraftEngine.clearTrackedCullables();
        }
        if (previousDatabase != null) {
            previousDatabase.close();
        }

        this.settings = reloadedSettings;
        this.debug = reloadedDebug;
        this.database = reloadedDatabase;
        this.craftEngine = reloadedCraftEngine;
        this.debug.log("lifecycle", "Debug logging enabled.");
        this.notifySettingsListeners(previousSettings, reloadedSettings);

        if (this.tableManager != null) {
            this.craftEngine.enableFurnitureInteractionBridge(this.tableManager);
            this.craftEngine.initializeAfterStartup();
            this.getServer().getOnlinePlayers().forEach(player -> this.scheduler.runEntity(player, () -> this.craftEngine.syncTrackedEntitiesFor(player)));
            this.tableManager.tables().forEach(table -> this.scheduler.runRegion(table.center(), () -> {
                table.clearDisplays();
                table.render();
            }));
        }
        return null;
    }

    public void addSettingsListener(PluginSettingsListener listener) {
        if (listener != null) {
            this.settingsListeners.add(listener);
        }
    }

    public void removeSettingsListener(PluginSettingsListener listener) {
        if (listener != null) {
            this.settingsListeners.remove(listener);
        }
    }

    private void notifySettingsListeners(PluginSettings previousSettings, PluginSettings currentSettings) {
        if (previousSettings == null) {
            return;
        }
        for (PluginSettingsListener listener : this.settingsListeners) {
            try {
                listener.onSettingsChanged(previousSettings, currentSettings);
            } catch (RuntimeException exception) {
                this.getLogger().log(Level.WARNING, "A PluginSettingsListener callback failed.", exception);
            }
        }
    }

    public MessageService messages() {
        return this.messages;
    }

    public MetricsCollector metrics() {
        return this.metrics;
    }

    public PluginSettings settings() {
        return this.settings;
    }

    public DatabaseService database() {
        return this.database;
    }

    public DebugService debug() {
        return this.debug;
    }

    public AsyncService async() {
        return this.async;
    }

    public ServerScheduler scheduler() {
        return this.scheduler;
    }

    public CraftEngineService craftEngine() {
        return this.craftEngine;
    }

    public MahjongTableManager tableManager() {
        return this.tableManager;
    }
}


