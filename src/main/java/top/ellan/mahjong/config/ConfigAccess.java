package top.ellan.mahjong.config;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

public final class ConfigAccess {
    private ConfigAccess() {
    }

    public static ConfigurationSection firstSection(ConfigurationSection root, String... paths) {
        if (root == null || paths == null) {
            return null;
        }
        for (String path : paths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(path);
            if (section != null) {
                return section;
            }
        }
        return null;
    }

    public static boolean bool(ConfigurationSection root, boolean defaultValue, String... paths) {
        if (root == null || paths == null) {
            return defaultValue;
        }
        for (String path : paths) {
            if (path == null || path.isBlank() || !root.contains(path, true)) {
                continue;
            }
            return root.getBoolean(path, defaultValue);
        }
        return defaultValue;
    }

    public static int integer(ConfigurationSection root, int defaultValue, String... paths) {
        if (root == null || paths == null) {
            return defaultValue;
        }
        for (String path : paths) {
            if (path == null || path.isBlank() || !root.contains(path, true)) {
                continue;
            }
            return root.getInt(path, defaultValue);
        }
        return defaultValue;
    }

    public static long longValue(ConfigurationSection root, long defaultValue, String... paths) {
        if (root == null || paths == null) {
            return defaultValue;
        }
        for (String path : paths) {
            if (path == null || path.isBlank() || !root.contains(path, true)) {
                continue;
            }
            return root.getLong(path, defaultValue);
        }
        return defaultValue;
    }

    public static String string(ConfigurationSection root, String defaultValue, String... paths) {
        if (root == null || paths == null) {
            return defaultValue;
        }
        for (String path : paths) {
            if (path == null || path.isBlank() || !root.contains(path, true)) {
                continue;
            }
            return root.getString(path, defaultValue);
        }
        return defaultValue;
    }

    public static List<String> stringList(ConfigurationSection root, String... paths) {
        if (root == null || paths == null) {
            return List.of();
        }
        for (String path : paths) {
            if (path == null || path.isBlank() || !root.contains(path, true)) {
                continue;
            }
            return root.getStringList(path);
        }
        return List.of();
    }
}

