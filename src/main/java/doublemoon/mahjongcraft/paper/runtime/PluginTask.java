package doublemoon.mahjongcraft.paper.runtime;

public interface PluginTask {
    void cancel();

    boolean isCancelled();
}
