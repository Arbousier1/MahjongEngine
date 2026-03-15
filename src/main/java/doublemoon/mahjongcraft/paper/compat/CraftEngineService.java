package doublemoon.mahjongcraft.paper.compat;

import doublemoon.mahjongcraft.paper.MahjongPaperPlugin;
import doublemoon.mahjongcraft.paper.model.MahjongTile;
import doublemoon.mahjongcraft.paper.render.DisplayVisibilityRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

public final class CraftEngineService {
    private static final String BUNDLE_ROOT = "craftengine/mahjongpaper";
    private static final String BUNDLE_INDEX = BUNDLE_ROOT + "/_bundle_index.txt";
    private static final String CRAFT_ENGINE_PLUGIN_NAME = "CraftEngine";
    private static final String TABLE_HITBOX_ITEM_ID = "mahjongpaper:table_hitbox";

    private final MahjongPaperPlugin plugin;
    private final boolean exportBundleOnEnable;
    private final boolean preferCustomItems;
    private final boolean preferFurnitureHitbox;
    private final String bundleFolderName;
    private final Map<String, ItemStack> customItemCache = new ConcurrentHashMap<>();
    private final Map<Integer, TrackedCullableEntity> trackedCullableEntities = new ConcurrentHashMap<>();
    private volatile boolean itemReflectionUnavailable;
    private volatile boolean furnitureReflectionUnavailable;
    private volatile boolean cullingReflectionUnavailable;
    private volatile ReflectionBridge reflectionBridge;

    public CraftEngineService(MahjongPaperPlugin plugin, ConfigurationSection section) {
        this.plugin = plugin;
        this.exportBundleOnEnable = section == null || section.getBoolean("exportBundleOnEnable", true);
        this.preferCustomItems = section == null || section.getBoolean("preferCustomItems", true);
        this.preferFurnitureHitbox = section == null || section.getBoolean("preferFurnitureHitbox", true);
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
        if (!this.preferCustomItems || this.itemReflectionUnavailable) {
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
            this.itemReflectionUnavailable = true;
            this.plugin.getLogger().warning(
                "CraftEngine was detected, but MahjongPaper could not build CraftEngine custom items. Falling back to direct item_model items."
            );
            this.plugin.debug().log("lifecycle", "CraftEngine reflection bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            return null;
        }
    }

    public Entity placeTableHitbox(Location location) {
        if (!this.preferFurnitureHitbox || this.furnitureReflectionUnavailable) {
            return null;
        }

        Plugin craftEngine = this.findCraftEnginePlugin();
        if (craftEngine == null || !craftEngine.isEnabled()) {
            return null;
        }

        try {
            ClassLoader classLoader = craftEngine.getClass().getClassLoader();
            Class<?> keyClass = Class.forName("net.momirealms.craftengine.core.util.Key", true, classLoader);
            Class<?> furnitureClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineFurniture", true, classLoader);
            Object key = keyClass.getMethod("of", String.class).invoke(null, TABLE_HITBOX_ITEM_ID);
            Object furniture = furnitureClass.getMethod("place", Location.class, keyClass).invoke(null, location, key);
            if (furniture == null) {
                return null;
            }
            Object bukkitEntity = furniture.getClass().getMethod("bukkitEntity").invoke(furniture);
            return bukkitEntity instanceof Entity entity ? entity : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.furnitureReflectionUnavailable = true;
            this.plugin.getLogger().warning(
                "CraftEngine was detected, but MahjongPaper could not place CraftEngine furniture hitboxes. Table collision will be disabled."
            );
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine furniture bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return null;
        }
    }

    public boolean removeFurniture(Entity entity) {
        if (entity == null || this.furnitureReflectionUnavailable) {
            return false;
        }

        Plugin craftEngine = this.findCraftEnginePlugin();
        if (craftEngine == null || !craftEngine.isEnabled()) {
            return false;
        }

        try {
            ClassLoader classLoader = craftEngine.getClass().getClassLoader();
            Class<?> furnitureClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineFurniture", true, classLoader);
            Object isFurniture = furnitureClass.getMethod("isFurniture", Entity.class).invoke(null, entity);
            if (!(isFurniture instanceof Boolean isFurnitureEntity) || !isFurnitureEntity) {
                return false;
            }
            try {
                furnitureClass.getMethod("remove", Entity.class, boolean.class, boolean.class).invoke(null, entity, false, false);
                return true;
            } catch (NoSuchMethodException ignored) {
                furnitureClass.getMethod("remove", Entity.class).invoke(null, entity);
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.furnitureReflectionUnavailable = true;
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine furniture remove bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return false;
        }
    }

    public void registerCullableEntity(Entity entity) {
        if (entity == null || !isCullableEntity(entity)) {
            return;
        }

        ReflectionBridge bridge = this.reflectionBridge();
        if (bridge == null) {
            return;
        }

        Object cullingData = this.createCullingData(entity, bridge);
        TrackedCullableEntity tracked = new TrackedCullableEntity(entity, this.createCullableProxy(entity, bridge, cullingData));
        TrackedCullableEntity previous = this.trackedCullableEntities.put(entity.getEntityId(), tracked);
        if (previous != null && previous.entity().isValid() && previous.entity().getUniqueId().equals(entity.getUniqueId())) {
            return;
        }
        if (previous != null) {
            this.unregisterCullableEntity(previous.entity());
            this.trackedCullableEntities.put(entity.getEntityId(), tracked);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            this.addTrackedEntity(player, tracked);
        }
    }

    public void unregisterCullableEntity(Entity entity) {
        if (entity == null) {
            return;
        }

        TrackedCullableEntity tracked = this.trackedCullableEntities.remove(entity.getEntityId());
        if (tracked == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.removeTrackedEntity(player, tracked.entityId());
        }
    }

    public void syncTrackedEntitiesFor(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        for (TrackedCullableEntity tracked : this.trackedCullableEntities.values()) {
            if (tracked.entity().isValid()) {
                this.addTrackedEntity(player, tracked);
            }
        }
    }

    public void clearTrackedCullables() {
        this.trackedCullableEntities.clear();
    }

    public String customItemId(MahjongTile tile, boolean faceDown) {
        String tileName = faceDown ? "back" : tile.name().toLowerCase();
        return "mahjongpaper:" + tileName;
    }

    private Object createCullableProxy(Entity entity, ReflectionBridge bridge, Object cullingData) {
        InvocationHandler handler = (proxy, method, args) -> this.handleCullableInvocation(proxy, entity, bridge, cullingData, method, args);
        return Proxy.newProxyInstance(bridge.classLoader(), new Class<?>[] {bridge.cullableClass()}, handler);
    }

    private Object handleCullableInvocation(Object proxy, Entity entity, ReflectionBridge bridge, Object cullingData, Method method, Object[] args) {
        return switch (method.getName()) {
            case "show" -> {
                this.scheduleViewerVisibility(entity, this.resolvePlatformPlayer(bridge, args), true);
                yield null;
            }
            case "hide" -> {
                this.scheduleViewerVisibility(entity, this.resolvePlatformPlayer(bridge, args), false);
                yield null;
            }
            case "cullingData" -> cullingData;
            case "hashCode" -> System.identityHashCode(entity);
            case "equals" -> proxyEquals(proxy, args);
            case "toString" -> "MahjongPaperCullable[" + entity.getEntityId() + ']';
            default -> null;
        };
    }

    private Object createCullingData(Entity entity, ReflectionBridge bridge) {
        BoundingBox box = entity.getBoundingBox();
        try {
            Object aabb = bridge.aabbConstructor().newInstance(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ());
            return bridge.cullingDataConstructor().newInstance(
                aabb,
                maxDistance(entity),
                0.25D,
                true
            );
        } catch (ReflectiveOperationException exception) {
            this.cullingReflectionUnavailable = true;
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine culling data bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return null;
        }
    }

    private void addTrackedEntity(Player player, TrackedCullableEntity tracked) {
        ReflectionBridge bridge = this.reflectionBridge();
        if (bridge == null) {
            return;
        }
        try {
            Object cePlayer = bridge.adaptMethod().invoke(null, player);
            if (cePlayer != null) {
                bridge.addTrackedEntityMethod().invoke(cePlayer, tracked.entityId(), tracked.cullableProxy());
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.cullingReflectionUnavailable = true;
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine tracked entity add failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private void removeTrackedEntity(Player player, int entityId) {
        ReflectionBridge bridge = this.reflectionBridge();
        if (bridge == null) {
            return;
        }
        try {
            Object cePlayer = bridge.adaptMethod().invoke(null, player);
            if (cePlayer != null) {
                bridge.removeTrackedEntityMethod().invoke(cePlayer, entityId);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.cullingReflectionUnavailable = true;
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine tracked entity remove failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private void scheduleViewerVisibility(Entity entity, Player viewer, boolean visible) {
        if (viewer == null) {
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (!viewer.isOnline()) {
                return;
            }
            if (!entity.isValid()) {
                return;
            }
            if (!DisplayVisibilityRegistry.canView(entity.getEntityId(), viewer.getUniqueId())) {
                viewer.hideEntity(this.plugin, entity);
                return;
            }
            if (visible) {
                viewer.showEntity(this.plugin, entity);
            } else {
                viewer.hideEntity(this.plugin, entity);
            }
        });
    }

    private Player resolvePlatformPlayer(ReflectionBridge bridge, Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return null;
        }
        try {
            Object platformPlayer = bridge.platformPlayerMethod().invoke(args[0]);
            return platformPlayer instanceof Player player ? player : null;
        } catch (ReflectiveOperationException exception) {
            this.cullingReflectionUnavailable = true;
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine platform player bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return null;
        }
    }

    private ReflectionBridge reflectionBridge() {
        if (this.cullingReflectionUnavailable) {
            return null;
        }
        ReflectionBridge cached = this.reflectionBridge;
        if (cached != null) {
            return cached;
        }

        Plugin craftEngine = this.findCraftEnginePlugin();
        if (craftEngine == null || !craftEngine.isEnabled()) {
            return null;
        }

        try {
            ClassLoader classLoader = craftEngine.getClass().getClassLoader();
            Class<?> adaptorsClass = Class.forName("net.momirealms.craftengine.bukkit.api.BukkitAdaptors", true, classLoader);
            Class<?> cullableClass = Class.forName("net.momirealms.craftengine.core.entity.culling.Cullable", true, classLoader);
            Class<?> cePlayerClass = Class.forName("net.momirealms.craftengine.core.entity.player.Player", true, classLoader);
            Class<?> aabbClass = Class.forName("net.momirealms.craftengine.core.world.collision.AABB", true, classLoader);
            Class<?> cullingDataClass = Class.forName("net.momirealms.craftengine.core.entity.culling.CullingData", true, classLoader);
            ReflectionBridge bridge = new ReflectionBridge(
                classLoader,
                cullableClass,
                adaptorsClass.getMethod("adapt", Player.class),
                cePlayerClass.getMethod("addTrackedEntity", int.class, cullableClass),
                cePlayerClass.getMethod("removeTrackedEntity", int.class),
                cePlayerClass.getMethod("platformPlayer"),
                aabbClass.getConstructor(double.class, double.class, double.class, double.class, double.class, double.class),
                cullingDataClass.getConstructor(aabbClass, int.class, double.class, boolean.class)
            );
            this.reflectionBridge = bridge;
            return bridge;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.cullingReflectionUnavailable = true;
            this.plugin.getLogger().warning(
                "CraftEngine was detected, but MahjongPaper could not bridge CraftEngine entity culling. Display entities will use normal server tracking."
            );
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine culling reflection bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return null;
        }
    }

    private static boolean proxyEquals(Object proxy, Object[] args) {
        return args != null && args.length == 1 && proxy == args[0];
    }

    private static int maxDistance(Entity entity) {
        if (entity instanceof Display display) {
            return Math.max(1, (int) Math.ceil(display.getViewRange()));
        }
        if (entity instanceof Interaction interaction) {
            return Math.max(8, (int) Math.ceil(interaction.getInteractionWidth() * 16.0D));
        }
        return 32;
    }

    private static boolean isCullableEntity(Entity entity) {
        return entity instanceof Display || entity instanceof Interaction;
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

    private record TrackedCullableEntity(Entity entity, int entityId, Object cullableProxy) {
        private TrackedCullableEntity(Entity entity, Object cullableProxy) {
            this(entity, entity.getEntityId(), cullableProxy);
        }
    }

    private record ReflectionBridge(
        ClassLoader classLoader,
        Class<?> cullableClass,
        Method adaptMethod,
        Method addTrackedEntityMethod,
        Method removeTrackedEntityMethod,
        Method platformPlayerMethod,
        Constructor<?> aabbConstructor,
        Constructor<?> cullingDataConstructor
    ) {
    }
}
