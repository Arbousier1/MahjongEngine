package top.ellan.mahjong.config;

@FunctionalInterface
public interface PluginSettingsListener {
    void onSettingsChanged(PluginSettings previous, PluginSettings current);
}
