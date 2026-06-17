package top.ellan.mahjong.table.core;

import top.ellan.mahjong.runtime.PluginTask;

/**
 * Port for managing the armed bot task on a mahjong table session.
 */
public interface TableBotTaskPort {
    void setBotTask(PluginTask botTask);

    void clearBotTaskIfSame(PluginTask expected);

    boolean hasArmedBotTask();

    void cancelBotTask();
}
