package top.ellan.mahjong.runtime;

public interface PluginTask {
    void cancel();

    boolean isCancelled();
}

