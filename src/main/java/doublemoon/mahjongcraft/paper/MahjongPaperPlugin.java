package doublemoon.mahjongcraft.paper;

import doublemoon.mahjongcraft.paper.command.MahjongCommand;
import doublemoon.mahjongcraft.paper.compat.CraftEngineService;
import doublemoon.mahjongcraft.paper.debug.DebugService;
import doublemoon.mahjongcraft.paper.db.DatabaseService;
import doublemoon.mahjongcraft.paper.i18n.MessageService;
import doublemoon.mahjongcraft.paper.render.DisplayVisibilityRegistry;
import doublemoon.mahjongcraft.paper.render.TableDisplayRegistry;
import doublemoon.mahjongcraft.paper.table.MahjongTableManager;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public final class MahjongPaperPlugin extends JavaPlugin {
    private final MessageService messages = new MessageService();
    private PluginSettings settings;
    private DebugService debug;
    private DatabaseService database;
    private CraftEngineService craftEngine;
    private MahjongTableManager tableManager;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.settings = PluginSettings.from(this.getConfig());
        this.debug = new DebugService(this.getLogger(), this.settings.debugSection());
        this.debug.log("lifecycle", "Debug logging enabled.");
        this.craftEngine = new CraftEngineService(this, this.settings.craftEngineSection());

        if (DatabaseService.isEnabled(this.settings.databaseSection())) {
            try {
                this.database = new DatabaseService(this, this.settings.databaseSection());
                this.getLogger().info("Database enabled: " + this.database.databaseType());
                this.debug.log("database", "Database service initialized with type=" + this.database.databaseType());
            } catch (DatabaseService.InitializationException ex) {
                this.handleDatabaseStartupFailure(ex);
                if (this.settings.databaseFailOnError()) {
                    this.getLogger().severe("database.failOnError=true, stopping plugin startup.");
                    this.getServer().getPluginManager().disablePlugin(this);
                    return;
                }
            }
        }

        this.tableManager = new MahjongTableManager(this);
        this.craftEngine.enableFurnitureInteractionBridge(this.tableManager);
        this.craftEngine.cleanupMahjongFurniture();
        this.tableManager.loadPersistentTables();

        MahjongCommand mahjongCommand = new MahjongCommand(this, this.tableManager);
        this.registerCommand(
            "mahjong",
            "Manage MahjongPaper tables and rounds. Use /mahjong help for command explanations.",
            mahjongCommand
        );

        this.getServer().getPluginManager().registerEvents(this.tableManager, this);
        this.getServer().getScheduler().runTask(this, () -> {
            this.craftEngine.initializeAfterStartup();
            if (this.tableManager != null) {
                this.tableManager.tables().forEach(table -> table.render());
            }
        });
        this.getLogger().info("MahjongPaper enabled.");
        this.debug.log("lifecycle", "Plugin bootstrap complete.");
    }

    @Override
    public void onDisable() {
        if (this.tableManager != null) {
            this.tableManager.shutdown();
        }
        TableDisplayRegistry.clear();
        DisplayVisibilityRegistry.clear();
        if (this.craftEngine != null) {
            this.craftEngine.disableFurnitureInteractionBridge();
            this.craftEngine.clearTrackedCullables();
        }
        if (this.database != null) {
            this.database.close();
            this.database = null;
        }
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
        this.getLogger().severe("Hint: use database.type=h2 for local storage, or start MariaDB and verify the configured connection settings.");
        if (this.debug != null && this.debug.isCategoryEnabled("database")) {
            this.getLogger().log(Level.SEVERE, "Database startup stack trace:", ex);
        }
    }

    public MessageService messages() {
        return this.messages;
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

    public CraftEngineService craftEngine() {
        return this.craftEngine;
    }

    public MahjongTableManager tableManager() {
        return this.tableManager;
    }
}
