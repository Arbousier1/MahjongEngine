package top.ellan.mahjong.table.core;

import top.ellan.mahjong.compat.CraftEngineService;
import top.ellan.mahjong.config.PluginSettings;
import top.ellan.mahjong.db.DatabaseService;
import top.ellan.mahjong.debug.DebugService;
import top.ellan.mahjong.i18n.MessageService;
import top.ellan.mahjong.metrics.MetricsCollector;
import top.ellan.mahjong.runtime.AsyncService;
import top.ellan.mahjong.runtime.ServerScheduler;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

public interface TableRuntimeServices {
    Plugin bukkitPlugin();

    default Logger getLogger() {
        return this.bukkitPlugin().getLogger();
    }

    MessageService messages();

    DebugService debug();

    ServerScheduler scheduler();

    AsyncService async();

    PluginSettings settings();

    CraftEngineService craftEngine();

    DatabaseService database();

    MetricsCollector metrics();

    MahjongTableManager tableManager();
}
