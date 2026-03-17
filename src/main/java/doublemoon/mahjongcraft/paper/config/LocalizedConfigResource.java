package doublemoon.mahjongcraft.paper.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

public final class LocalizedConfigResource {
    static final String DEFAULT_RESOURCE = "config.yml";
    static final String SIMPLIFIED_CHINESE_RESOURCE = "config_zh_CN.yml";
    static final String TRADITIONAL_CHINESE_RESOURCE = "config_zh_TW.yml";

    private LocalizedConfigResource() {
    }

    public static void saveIfMissing(JavaPlugin plugin, Locale locale) {
        Objects.requireNonNull(plugin, "plugin");
        File dataFolder = plugin.getDataFolder();
        File configFile = new File(dataFolder, DEFAULT_RESOURCE);
        if (configFile.exists()) {
            return;
        }

        String resourceName = resolveResourceName(locale);
        try (InputStream inputStream = plugin.getResource(resourceName)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing config template resource: " + resourceName);
            }
            Files.createDirectories(dataFolder.toPath());
            Files.copy(inputStream, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write default config from template " + resourceName, exception);
        }
    }

    static String resolveResourceName(Locale locale) {
        Locale safeLocale = locale == null ? Locale.getDefault() : locale;
        if (!"zh".equalsIgnoreCase(safeLocale.getLanguage())) {
            return DEFAULT_RESOURCE;
        }

        String region = safeLocale.getCountry().toUpperCase(Locale.ROOT);
        String script = safeLocale.getScript();
        boolean traditional = "Hant".equalsIgnoreCase(script) || region.equals("TW") || region.equals("HK") || region.equals("MO");
        boolean simplified = "Hans".equalsIgnoreCase(script) || region.equals("CN") || region.equals("SG") || region.equals("MY");
        if (traditional) {
            return TRADITIONAL_CHINESE_RESOURCE;
        }
        if (simplified || region.isBlank()) {
            return SIMPLIFIED_CHINESE_RESOURCE;
        }
        return DEFAULT_RESOURCE;
    }
}
