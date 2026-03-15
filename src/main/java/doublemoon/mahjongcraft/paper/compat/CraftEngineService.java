package doublemoon.mahjongcraft.paper.compat;

import doublemoon.mahjongcraft.paper.MahjongPaperPlugin;
import doublemoon.mahjongcraft.paper.model.MahjongTile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class CraftEngineService {
    private static final String BUNDLE_ROOT = "craftengine/mahjongpaper";
    private static final String BUNDLE_INDEX = BUNDLE_ROOT + "/_bundle_index.txt";
    private static final String CRAFT_ENGINE_PLUGIN_NAME = "CraftEngine";

    private final MahjongPaperPlugin plugin;
    private final boolean exportBundleOnEnable;
    private final boolean preferCustomItems;
    private final String bundleFolderName;
    private final Map<String, ItemStack> customItemCache = new ConcurrentHashMap<>();
    private volatile boolean reflectionUnavailable;

    public CraftEngineService(MahjongPaperPlugin plugin, ConfigurationSection section) {
        this.plugin = plugin;
        this.exportBundleOnEnable = section == null || section.getBoolean("exportBundleOnEnable", true);
        this.preferCustomItems = section == null || section.getBoolean("preferCustomItems", true);
        this.bundleFolderName = section == null ? "mahjongpaper" : section.getString("bundleFolder", "mahjongpaper");
    }

    public void initializeAfterStartup() {
        Plugin craftEngine = this.findCraftEnginePlugin();
        if (craftEngine == null) {
            this.plugin.debug().log("lifecycle", "CraftEngine not detected. Using direct item_model items.");
            return;
        }

        if (this.exportBundleOnEnable) {
            this.exportBundle(craftEngine);
        }
    }

    public ItemStack resolveTileItem(MahjongTile tile, boolean faceDown) {
        if (!this.preferCustomItems || this.reflectionUnavailable) {
            return null;
        }

        Plugin craftEngine = this.findCraftEnginePlugin();
        if (craftEngine == null || !craftEngine.isEnabled()) {
            return null;
        }

        String itemId = this.customItemId(tile, faceDown);
        ItemStack cached = this.customItemCache.get(itemId);
        if (cached != null) {
            return cached.clone();
        }

        try {
            ClassLoader classLoader = craftEngine.getClass().getClassLoader();
            Class<?> keyClass = Class.forName("net.momirealms.craftengine.core.util.Key", true, classLoader);
            Class<?> itemsClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems", true, classLoader);
            Object key = keyClass.getMethod("of", String.class).invoke(null, itemId);
            Object customItem = itemsClass.getMethod("byId", keyClass).invoke(null, key);
            if (customItem == null) {
                return null;
            }
            Object built = customItem.getClass().getMethod("buildItemStack").invoke(customItem);
            if (!(built instanceof ItemStack itemStack)) {
                return null;
            }
            this.customItemCache.put(itemId, itemStack.clone());
            return itemStack;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.reflectionUnavailable = true;
            this.plugin.getLogger().warning(
                "CraftEngine was detected, but MahjongPaper could not build CraftEngine custom items. Falling back to direct item_model items."
            );
            this.plugin.debug().log("lifecycle", "CraftEngine reflection bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            return null;
        }
    }

    public String customItemId(MahjongTile tile, boolean faceDown) {
        String tileName = faceDown ? "back" : tile.name().toLowerCase();
        return "mahjongpaper:" + tileName;
    }

    private void exportBundle(Plugin craftEngine) {
        try (InputStream indexStream = this.plugin.getResource(BUNDLE_INDEX)) {
            if (indexStream == null) {
                this.plugin.getLogger().warning("Missing bundled CraftEngine index. Skipping CraftEngine export.");
                return;
            }

            Path targetRoot = craftEngine.getDataFolder().toPath().resolve("resources").resolve(this.bundleFolderName);
            Collection<String> entries = new String(indexStream.readAllBytes(), StandardCharsets.UTF_8).lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
            for (String entry : entries) {
                this.copyBundledFile(entry, targetRoot.resolve(entry));
            }

            this.plugin.getLogger().info("CraftEngine detected. Exported MahjongPaper bundle to " + targetRoot.toAbsolutePath());
        } catch (IOException exception) {
            this.plugin.getLogger().warning("Failed to export MahjongPaper CraftEngine bundle: " + exception.getMessage());
        }
    }

    private void copyBundledFile(String relativePath, Path targetPath) throws IOException {
        String resourcePath = BUNDLE_ROOT + "/" + relativePath;
        try (InputStream resourceStream = this.plugin.getResource(resourcePath)) {
            if (resourceStream == null) {
                throw new IOException("Missing bundled resource: " + resourcePath);
            }
            Files.createDirectories(Objects.requireNonNull(targetPath.getParent()));
            Files.copy(resourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Plugin findCraftEnginePlugin() {
        Plugin exact = this.plugin.getServer().getPluginManager().getPlugin(CRAFT_ENGINE_PLUGIN_NAME);
        if (exact != null) {
            return exact;
        }
        for (Plugin candidate : this.plugin.getServer().getPluginManager().getPlugins()) {
            if (candidate.getName().equalsIgnoreCase(CRAFT_ENGINE_PLUGIN_NAME)) {
                return candidate;
            }
        }
        return null;
    }
}
