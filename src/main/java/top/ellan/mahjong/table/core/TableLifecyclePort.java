package top.ellan.mahjong.table.core;

/**
 * Lifecycle port for a mahjong table session, exposing shutdown and reset
 * operations used by external coordinators that manage table teardown.
 */
public interface TableLifecyclePort {
    void shutdown();

    void forceEndMatch();

    void resetForServerStartup();
}
