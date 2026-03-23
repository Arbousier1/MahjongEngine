package top.ellan.mahjong.compat;

import top.ellan.mahjong.config.ConfigAccess;
import top.ellan.mahjong.bootstrap.MahjongPaperPlugin;
import top.ellan.mahjong.model.MahjongTile;
import top.ellan.mahjong.render.display.DisplayClickAction;
import top.ellan.mahjong.render.display.DisplayVisibilityRegistry;
import top.ellan.mahjong.render.display.TableDisplayRegistry;
import top.ellan.mahjong.table.core.MahjongTableManager;
import top.ellan.mahjong.table.core.MahjongVariant;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

public final class CraftEngineService {
    private static final String MANAGED_FURNITURE_KEY = "managed_craftengine_furniture";
    private static final String BUNDLE_ROOT = "craftengine/mahjongpaper";
    private static final String BUNDLE_INDEX = BUNDLE_ROOT + "/_bundle_index.txt";
    private static final String CRAFT_ENGINE_PLUGIN_NAME = "CraftEngine";
    private static final String TABLE_HITBOX_ITEM_ID = "mahjongpaper:table_hitbox";
    private static final String HAND_TILE_HITBOX_ITEM_ID = "mahjongpaper:hand_tile_hitbox";
    private static final String SEAT_HITBOX_ITEM_ID = "mahjongpaper:seat_hitbox";
    private static final String MAHJONGPAPER_FURNITURE_PREFIX = "mahjongpaper:";
    private static final String WRAPPED_BLOCK_STATE_HELPER_CLASS =
        "net.momirealms.craftengine.bukkit.compatibility.packetevents.WrappedBlockStateHelper";
    private static final String WRAPPED_BLOCK_STATE_REGISTER_METHOD = "register";
    private static final String GRIM_PACKETEVENTS_PACKAGE = "ac{}grim{}grimac{}shaded{}com{}github{}retrooper{}packetevents";
    private static final String[] VULCAN_PACKETEVENTS_PACKAGES = new String[] {
        "me{}frep{}vulcan{}shaded{}com{}github{}retrooper{}packetevents",
        "me{}frep{}vulcan{}libs{}com{}github{}retrooper{}packetevents"
    };
    private static final int STARTUP_FURNITURE_CLEANUP_REMOVALS_PER_TICK = 8;

    private final MahjongPaperPlugin plugin;
    private final boolean exportBundleOnEnable;
    private final boolean preferCustomItems;
    private final boolean preferFurnitureHitbox;
    private final boolean injectAntiCheatPacketEventsMappings;
    private final String bundleFolderName;
    private final Map<String, ItemStack> customItemCache = new ConcurrentHashMap<>();
    private final Map<String, Object> craftEngineKeyCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> customItemBuildMethods = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> furnitureEntityMethods = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> furnitureHitboxesMethods = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> furniturePositionMethods = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> hitboxSeatsMethods = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> seatOccupiedMethods = new ConcurrentHashMap<>();
    private final Map<Integer, TrackedCullableEntity> trackedCullableEntities = new ConcurrentHashMap<>();
    private final Set<String> warnedUnavailableFurnitureIds = ConcurrentHashMap.newKeySet();
    private volatile boolean itemReflectionUnavailable;
    private volatile boolean furnitureReflectionUnavailable;
    private volatile boolean cullingReflectionUnavailable;
    private volatile Plugin craftEnginePlugin;
    private volatile ItemBridge itemBridge;
    private volatile FurnitureBridge furnitureBridge;
    private volatile ReflectionBridge reflectionBridge;
    private volatile NamespacedKey furnitureDataKey;
    private volatile boolean antiCheatMappingsInjected;
    private Listener furnitureInteractListener;

    public CraftEngineService(MahjongPaperPlugin plugin, ConfigurationSection section) {
        this.plugin = plugin;
        this.exportBundleOnEnable = ConfigAccess.bool(section, true, "exportBundleOnEnable", "bundle.exportOnEnable");
        this.preferCustomItems = ConfigAccess.bool(section, true, "preferCustomItems", "items.preferCustomItems");
        this.preferFurnitureHitbox = ConfigAccess.bool(section, true, "preferFurnitureHitbox", "furniture.preferHitboxInteraction");
        this.injectAntiCheatPacketEventsMappings = ConfigAccess.bool(
            section,
            true,
            "injectAntiCheatPacketEventsMappings",
            "compatibility.injectAntiCheatPacketEventsMappings"
        );
        this.bundleFolderName = ConfigAccess.string(section, "mahjongpaper", "bundleFolder", "bundle.folder");
    }

    public void initializeAfterStartup() {
        Plugin craftEngine = this.craftEnginePlugin();
        if (craftEngine == null) {
            this.plugin.debug().log("lifecycle", "CraftEngine not detected. Using direct item_model items.");
            return;
        }

        if (this.exportBundleOnEnable) {
            this.plugin.async().execute("export-craftengine-bundle", () -> this.exportBundle(craftEngine));
        }
        this.injectAntiCheatMappingsIfNeeded(craftEngine);
    }

    public int cleanupMahjongFurniture() {
        if (this.furnitureReflectionUnavailable) {
            return 0;
        }

        Plugin craftEngine = this.craftEnginePlugin();
        if (craftEngine == null || !craftEngine.isEnabled()) {
            return 0;
        }

        NamespacedKey furnitureKey = this.resolveFurnitureDataKey(craftEngine);
        if (furnitureKey == null) {
            return 0;
        }

        AtomicInteger removed = new AtomicInteger();
        int scheduledRegions = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                int chunkX = chunk.getX();
                int chunkZ = chunk.getZ();
                Location chunkCenter = new Location(world, (chunkX << 4) + 8.0D, world.getMinHeight(), (chunkZ << 4) + 8.0D);
                scheduledRegions++;
                this.plugin.scheduler().runRegion(chunkCenter, () -> {
                    if (!world.isChunkLoaded(chunkX, chunkZ)) {
                        return;
                    }
                    int scheduledFallbackRemovals = 0;
                    for (Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
                        String furnitureId = entity.getPersistentDataContainer().get(furnitureKey, PersistentDataType.STRING);
                        if (furnitureId == null || !furnitureId.startsWith(MAHJONGPAPER_FURNITURE_PREFIX)) {
                            continue;
                        }
                        boolean removedByCraftEngine = this.removeFurniture(entity);
                        if (!removedByCraftEngine && entity.isValid()) {
                            if (entity instanceof Interaction interaction) {
                                interaction.setResponsive(false);
                            }
                            long delayTicks = 1L + (scheduledFallbackRemovals / STARTUP_FURNITURE_CLEANUP_REMOVALS_PER_TICK);
                            this.plugin.scheduler().removeEntity(entity, delayTicks);
                            scheduledFallbackRemovals++;
                        }
                        removed.incrementAndGet();
                    }
                });
            }
        }

        if (scheduledRegions == 0) {
            return 0;
        }
        this.plugin.scheduler().runGlobalDelayed(() -> {
            int removedCount = removed.get();
            if (removedCount > 0) {
                this.plugin.getLogger().info("Removed " + removedCount + " leftover MahjongPaper CraftEngine furniture entities from previous sessions.");
                this.plugin.debug().log("lifecycle", "Startup cleanup removed " + removedCount + " lingering mahjongpaper furniture entities.");
            }
        }, 40L);
        return 0;
    }

    public ItemStack resolveTileItem(MahjongVariant variant, MahjongTile tile, boolean faceDown) {
        if (!this.preferCustomItems || this.itemReflectionUnavailable) {
            return null;
        }

        Plugin craftEngine = this.craftEnginePlugin();
        ItemBridge bridge = this.itemBridge(craftEngine);
        if (bridge == null) {
            return null;
        }

        String itemId = this.customItemId(variant, tile, faceDown);
        ItemStack resolved = this.buildCustomItem(craftEngine, bridge, itemId);
        if (resolved != null) {
            return resolved;
        }
        if (!faceDown && tile != MahjongTile.UNKNOWN) {
            String fallbackItemId = this.customItemId(variant, MahjongTile.UNKNOWN, false);
            if (!Objects.equals(fallbackItemId, itemId)) {
                ItemStack fallback = this.buildCustomItem(craftEngine, bridge, fallbackItemId);
                if (fallback != null) {
                    return fallback;
                }
            }
        }
        return null;
    }

    public ItemStack resolveTileItem(MahjongTile tile, boolean faceDown) {
        return this.resolveTileItem(MahjongVariant.RIICHI, tile, faceDown);
    }

    private ItemStack buildCustomItem(Plugin craftEngine, ItemBridge bridge, String itemId) {
        ItemStack cached = this.customItemCache.get(itemId);
        if (cached != null) {
            return cached.clone();
        }

        try {
            Object key = this.craftEngineKey(itemId, bridge.keyOfMethod());
            Object customItem = bridge.byIdMethod().invoke(null, key);
            if (customItem == null) {
                return null;
            }
            Method buildMethod = this.resolveBuildItemStackMethod(customItem.getClass());
            Object built = this.invokeCustomItemBuildMethod(customItem, buildMethod);
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
        return this.placeFurniture(location, TABLE_HITBOX_ITEM_ID);
    }

    public Entity placeHandTileHitbox(Location location, DisplayClickAction action) {
        return this.placeFurniture(location, HAND_TILE_HITBOX_ITEM_ID, action);
    }

    public Entity placeSeatHitbox(Location location, DisplayClickAction action) {
        return this.placeFurniture(location, SEAT_HITBOX_ITEM_ID, action);
    }

    public Entity placeSeatFurniture(Location location, String furnitureItemId, DisplayClickAction action) {
        Entity entity = this.placeFurniture(location, furnitureItemId);
        if (entity != null && action != null) {
            this.markManagedFurnitureEntity(entity);
            TableDisplayRegistry.register(entity.getEntityId(), action);
        }
        return entity;
    }

    public Entity placeFurniture(Location location, String furnitureItemId, DisplayClickAction action) {
        Entity entity = this.placeFurniture(location, furnitureItemId);
        if (entity != null) {
            this.markManagedFurnitureEntity(entity);
            this.applyDisplayClickAction(entity, action);
        }
        return entity;
    }

    public Entity placeFurniture(Location location, String furnitureItemId) {
        if (!this.preferFurnitureHitbox || this.furnitureReflectionUnavailable) {
            return null;
        }

        Plugin craftEngine = this.craftEnginePlugin();
        FurnitureBridge bridge = this.furnitureBridge(craftEngine);
        if (bridge == null) {
            return null;
        }

        try {
            Object key = this.craftEngineKey(furnitureItemId, bridge.keyOfMethod());
            Object furniture = bridge.placeMethod().invoke(null, location, key);
            if (furniture == null) {
                this.warnUnavailableFurnitureId(furnitureItemId);
                return null;
            }
            Object bukkitEntity = this.resolveFurnitureEntityMethod(furniture.getClass()).invoke(furniture);
            if (bukkitEntity instanceof Entity entity) {
                this.markManagedFurnitureEntity(entity);
                return entity;
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.furnitureReflectionUnavailable = true;
            this.plugin.getLogger().warning(
                "CraftEngine was detected, but MahjongPaper could not place CraftEngine furniture. CraftEngine-based interaction may be unavailable."
            );
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine furniture bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return null;
        }
    }

    public void enableFurnitureInteractionBridge(MahjongTableManager tableManager) {
        if (this.furnitureInteractListener != null) {
            return;
        }
        Plugin craftEngine = this.craftEnginePlugin();
        if (craftEngine == null || !craftEngine.isEnabled()) {
            this.plugin.getLogger().warning("CraftEngine interaction bridge is unavailable because CraftEngine is not enabled.");
            return;
        }
        try {
            ClassLoader classLoader = craftEngine.getClass().getClassLoader();
            Class<? extends Event> interactEventClass = Class.forName(
                "net.momirealms.craftengine.bukkit.api.event.FurnitureInteractEvent",
                true,
                classLoader
            ).asSubclass(Event.class);
            Class<? extends Event> breakEventClass = Class.forName(
                "net.momirealms.craftengine.bukkit.api.event.FurnitureBreakEvent",
                true,
                classLoader
            ).asSubclass(Event.class);
            Class<? extends Event> hitEventClass = Class.forName(
                "net.momirealms.craftengine.bukkit.api.event.FurnitureHitEvent",
                true,
                classLoader
            ).asSubclass(Event.class);
            Method playerMethod = interactEventClass.getMethod("player");
            Method furnitureMethod = interactEventClass.getMethod("furniture");
            Method entityIdMethod = furnitureMethod.getReturnType().getMethod("entityId");
            Method bukkitEntityMethod = furnitureMethod.getReturnType().getMethod("bukkitEntity");
            Method breakFurnitureMethod = breakEventClass.getMethod("furniture");
            Method hitFurnitureMethod = hitEventClass.getMethod("furniture");
            Listener listener = new Listener() {
            };
            EventExecutor interactExecutor = (ignored, event) -> this.handleFurnitureInteractEvent(
                event,
                tableManager,
                playerMethod,
                furnitureMethod,
                entityIdMethod
            );
            EventExecutor breakProtectionExecutor = (ignored, event) -> this.handleProtectedFurnitureEvent(
                event,
                breakFurnitureMethod,
                bukkitEntityMethod
            );
            EventExecutor hitProtectionExecutor = (ignored, event) -> this.handleProtectedFurnitureEvent(
                event,
                hitFurnitureMethod,
                bukkitEntityMethod
            );
            this.plugin.getServer().getPluginManager().registerEvent(
                interactEventClass,
                listener,
                EventPriority.NORMAL,
                interactExecutor,
                this.plugin,
                true
            );
            this.plugin.getServer().getPluginManager().registerEvent(
                breakEventClass,
                listener,
                EventPriority.HIGHEST,
                breakProtectionExecutor,
                this.plugin,
                true
            );
            this.plugin.getServer().getPluginManager().registerEvent(
                hitEventClass,
                listener,
                EventPriority.HIGHEST,
                hitProtectionExecutor,
                this.plugin,
                true
            );
            this.furnitureInteractListener = listener;
        } catch (ReflectiveOperationException exception) {
            this.plugin.getLogger().warning(
                "CraftEngine was detected, but MahjongPaper could not register the furniture bridge. CraftEngine interactions or protection may be unavailable."
            );
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine furniture bridge registration failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    public void disableFurnitureInteractionBridge() {
        if (this.furnitureInteractListener != null) {
            HandlerList.unregisterAll(this.furnitureInteractListener);
            this.furnitureInteractListener = null;
        }
    }

    private void handleFurnitureInteractEvent(
        Event event,
        MahjongTableManager tableManager,
        Method playerMethod,
        Method furnitureMethod,
        Method entityIdMethod
    ) throws EventException {
        try {
            Player player = (Player) playerMethod.invoke(event);
            Object furniture = furnitureMethod.invoke(event);
            int entityId = (int) entityIdMethod.invoke(furniture);
            DisplayClickAction action = TableDisplayRegistry.get(entityId);
            if (action == null) {
                return;
            }
            if (event instanceof Cancellable cancellable) {
                cancellable.setCancelled(true);
            }
            boolean accepted = tableManager.handleDisplayAction(player, action);
            if (!accepted) {
                if (action.actionType() == DisplayClickAction.ActionType.HAND_TILE) {
                    this.plugin.messages().actionBar(player, "packet.cannot_click_tile");
                } else {
                    this.plugin.messages().actionBar(player, "command.join_failed");
                }
            }
        } catch (ReflectiveOperationException exception) {
            throw new EventException(exception);
        }
    }

    private void handleProtectedFurnitureEvent(
        Event event,
        Method furnitureMethod,
        Method bukkitEntityMethod
    ) throws EventException {
        try {
            Object furniture = furnitureMethod.invoke(event);
            if (furniture == null) {
                return;
            }
            Object bukkitEntity = bukkitEntityMethod.invoke(furniture);
            if (!(bukkitEntity instanceof Entity entity)) {
                return;
            }
            if (this.isManagedFurnitureEntity(entity) && event instanceof Cancellable cancellable) {
                cancellable.setCancelled(true);
            }
        } catch (ReflectiveOperationException exception) {
            throw new EventException(exception);
        }
    }

    public boolean removeFurniture(Entity entity) {
        if (entity == null || this.furnitureReflectionUnavailable) {
            return false;
        }

        Plugin craftEngine = this.craftEnginePlugin();
        FurnitureBridge bridge = this.furnitureBridge(craftEngine);
        if (bridge == null) {
            return false;
        }

        try {
            Object isFurniture = bridge.isFurnitureMethod().invoke(null, entity);
            if (!(isFurniture instanceof Boolean isFurnitureEntity) || !isFurnitureEntity) {
                return false;
            }
            if (bridge.removeWithFlagsMethod() != null) {
                bridge.removeWithFlagsMethod().invoke(null, entity, false, false);
            } else if (bridge.removeMethod() != null) {
                bridge.removeMethod().invoke(null, entity);
            } else {
                return false;
            }
            return true;
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
        if (entity == null || !isCullableEntity(entity) || !this.plugin.isEnabled()) {
            return;
        }

        ReflectionBridge bridge = this.reflectionBridge();
        if (bridge == null) {
            return;
        }

        Object cullingData = this.createCullingData(entity, bridge);
        int entityId = entity.getEntityId();
        UUID entityUuid = entity.getUniqueId();
        TrackedCullableEntity tracked = new TrackedCullableEntity(entity, entityId, entityUuid, this.createCullableProxy(entity, entityId, bridge, cullingData));
        TrackedCullableEntity previous = this.trackedCullableEntities.put(entity.getEntityId(), tracked);
        if (previous != null && previous.entityUuid().equals(entityUuid)) {
            return;
        }
        if (previous != null) {
            this.trackedCullableEntities.remove(previous.entityId());
            for (Player player : this.onlinePlayersSnapshot()) {
                this.plugin.scheduler().runEntity(player, () -> this.removeTrackedEntity(player, previous.entityId()));
            }
            this.trackedCullableEntities.put(entity.getEntityId(), tracked);
        }

        for (Player player : this.onlinePlayersSnapshot()) {
            this.plugin.scheduler().runEntity(player, () -> this.addTrackedEntity(player, tracked));
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
        if (!this.plugin.isEnabled()) {
            return;
        }
        for (Player player : this.onlinePlayersSnapshot()) {
            this.plugin.scheduler().runEntity(player, () -> this.removeTrackedEntity(player, tracked.entityId()));
        }
    }

    public void syncTrackedEntitiesFor(Player player) {
        if (player == null || !player.isOnline() || !this.plugin.isEnabled()) {
            return;
        }
        for (TrackedCullableEntity tracked : this.trackedCullableEntities.values()) {
            this.addTrackedEntity(player, tracked);
            this.scheduleViewerVisibility(tracked.entityId(), tracked.entity(), player, true);
        }
    }

    public void clearTrackedCullables() {
        this.trackedCullableEntities.clear();
    }

    public String customItemId(MahjongVariant variant, MahjongTile tile, boolean faceDown) {
        return CraftEngineTileItemResolver.resolve(this.plugin.settings().craftEngineTileItemIdPrefix(variant), tile, faceDown);
    }

    public String customItemId(MahjongTile tile, boolean faceDown) {
        return this.customItemId(MahjongVariant.RIICHI, tile, faceDown);
    }

    private Object createCullableProxy(Entity entity, int entityId, ReflectionBridge bridge, Object cullingData) {
        InvocationHandler handler = (proxy, method, args) -> this.handleCullableInvocation(proxy, entity, entityId, bridge, cullingData, method, args);
        return Proxy.newProxyInstance(bridge.classLoader(), new Class<?>[] {bridge.cullableClass()}, handler);
    }

    private Object handleCullableInvocation(Object proxy, Entity entity, int entityId, ReflectionBridge bridge, Object cullingData, Method method, Object[] args) {
        return switch (method.getName()) {
            case "show" -> {
                this.scheduleViewerVisibility(entityId, entity, this.resolvePlatformPlayer(bridge, args), true);
                yield null;
            }
            case "hide" -> {
                this.scheduleViewerVisibility(entityId, entity, this.resolvePlatformPlayer(bridge, args), false);
                yield null;
            }
            case "cullingData" -> cullingData;
            case "hashCode" -> entityId;
            case "equals" -> proxyEquals(proxy, args);
            case "toString" -> "MahjongPaperCullable[" + entityId + ']';
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

    private void scheduleViewerVisibility(int entityId, Entity entity, Player viewer, boolean visible) {
        if (entity == null || viewer == null || !this.plugin.isEnabled()) {
            return;
        }
        this.plugin.scheduler().runEntity(viewer, () -> {
            if (!viewer.isOnline()) {
                return;
            }
            if (!entity.isValid() || entity.isDead()) {
                return;
            }
            if (!DisplayVisibilityRegistry.canView(entityId, viewer.getUniqueId())) {
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

    private List<Player> onlinePlayersSnapshot() {
        return List.copyOf(Bukkit.getOnlinePlayers());
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

        Plugin craftEngine = this.craftEnginePlugin();
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

    public boolean isFurnitureEntity(Entity entity) {
        if (entity == null || this.furnitureReflectionUnavailable) {
            return false;
        }
        Plugin craftEngine = this.craftEnginePlugin();
        FurnitureBridge bridge = this.furnitureBridge(craftEngine);
        if (bridge == null) {
            return false;
        }
        try {
            Object isFurniture = bridge.isFurnitureMethod().invoke(null, entity);
            return isFurniture instanceof Boolean flag && flag;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.furnitureReflectionUnavailable = true;
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine furniture detection bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return false;
        }
    }

    public boolean reconcileFurniture(Entity entity, Location location, String furnitureItemId, DisplayClickAction action) {
        if (entity == null || location == null || furnitureItemId == null || furnitureItemId.isBlank()) {
            return false;
        }
        // Hand-tile hitboxes move every discard/draw; respawning them avoids stale
        // CraftEngine interaction offsets when the furniture entity is teleported.
        if (HAND_TILE_HITBOX_ITEM_ID.equals(furnitureItemId)) {
            return false;
        }
        String existingFurnitureId = this.furnitureItemId(entity);
        if (!Objects.equals(existingFurnitureId, furnitureItemId)) {
            return false;
        }
        Location target = location.clone();
        this.plugin.scheduler().teleport(entity, target);
        this.markManagedFurnitureEntity(entity);
        this.applyDisplayClickAction(entity, action);
        return true;
    }

    public boolean isManagedFurnitureEntity(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(managedFurnitureKey(), PersistentDataType.BYTE);
    }

    public String furnitureItemId(Entity entity) {
        if (entity == null || this.furnitureReflectionUnavailable) {
            return null;
        }
        Plugin craftEngine = this.craftEnginePlugin();
        if (craftEngine == null || !craftEngine.isEnabled()) {
            return null;
        }
        NamespacedKey furnitureKey = this.resolveFurnitureDataKey(craftEngine);
        if (furnitureKey == null) {
            return null;
        }
        return entity.getPersistentDataContainer().get(furnitureKey, PersistentDataType.STRING);
    }

    public boolean isSeatEntity(Entity entity) {
        if (entity == null || this.furnitureReflectionUnavailable) {
            return false;
        }
        Plugin craftEngine = this.craftEnginePlugin();
        FurnitureBridge bridge = this.furnitureBridge(craftEngine);
        if (bridge == null || bridge.isSeatMethod() == null) {
            return false;
        }
        try {
            Object isSeat = bridge.isSeatMethod().invoke(null, entity);
            return isSeat instanceof Boolean flag && flag;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.furnitureReflectionUnavailable = true;
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine seat detection bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return false;
        }
    }

    public Entity furnitureEntityForSeat(Entity seatEntity) {
        if (seatEntity == null || this.furnitureReflectionUnavailable) {
            return null;
        }
        Plugin craftEngine = this.craftEnginePlugin();
        FurnitureBridge bridge = this.furnitureBridge(craftEngine);
        if (bridge == null || bridge.loadedFurnitureBySeatMethod() == null) {
            return null;
        }
        try {
            Object furniture = bridge.loadedFurnitureBySeatMethod().invoke(null, seatEntity);
            if (furniture == null) {
                return null;
            }
            Object bukkitEntity = this.resolveFurnitureEntityMethod(furniture.getClass()).invoke(furniture);
            return bukkitEntity instanceof Entity entity ? entity : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.furnitureReflectionUnavailable = true;
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine seat owner bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return null;
        }
    }

    public boolean canPlaceFurniture() {
        if (!this.preferFurnitureHitbox || this.furnitureReflectionUnavailable) {
            return false;
        }
        Plugin craftEngine = this.craftEnginePlugin();
        return craftEngine != null && craftEngine.isEnabled() && this.furnitureBridge(craftEngine) != null;
    }

    public boolean seatPlayerOnFurniture(Entity furnitureEntity, Player player) {
        if (furnitureEntity == null || player == null || !player.isOnline() || this.furnitureReflectionUnavailable) {
            return false;
        }
        Plugin craftEngine = this.craftEnginePlugin();
        FurnitureBridge bridge = this.furnitureBridge(craftEngine);
        if (bridge == null || bridge.loadedFurnitureByMetaEntityMethod() == null || bridge.playerAdaptMethod() == null || bridge.seatSpawnMethod() == null) {
            return false;
        }
        try {
            Object furniture = bridge.loadedFurnitureByMetaEntityMethod().invoke(null, furnitureEntity);
            if (furniture == null) {
                return false;
            }
            Object adaptedPlayer = bridge.playerAdaptMethod().invoke(null, player);
            if (adaptedPlayer == null) {
                return false;
            }
            Object position = this.resolveFurniturePositionMethod(furniture.getClass()).invoke(furniture);
            Object[] hitboxes = this.asObjectArray(this.resolveFurnitureHitboxesMethod(furniture.getClass()).invoke(furniture));
            for (Object hitbox : hitboxes) {
                Object[] seats = this.asObjectArray(this.resolveHitboxSeatsMethod(hitbox.getClass()).invoke(hitbox));
                for (Object seat : seats) {
                    Object occupied = this.resolveSeatOccupiedMethod(seat.getClass()).invoke(seat);
                    if (occupied instanceof Boolean flag && flag) {
                        continue;
                    }
                    Object spawned = bridge.seatSpawnMethod().invoke(seat, adaptedPlayer, position);
                    if (spawned instanceof Boolean success && success) {
                        return true;
                    }
                }
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.furnitureReflectionUnavailable = true;
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine seat spawn bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return false;
        }
    }

    private NamespacedKey resolveFurnitureDataKey(Plugin craftEngine) {
        NamespacedKey cached = this.furnitureDataKey;
        if (cached != null) {
            return cached;
        }

        try {
            ClassLoader classLoader = craftEngine.getClass().getClassLoader();
            Class<?> managerClass = Class.forName(
                "net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurnitureManager",
                true,
                classLoader
            );
            Object key = managerClass.getField("FURNITURE_KEY").get(null);
            if (key instanceof NamespacedKey namespacedKey) {
                this.furnitureDataKey = namespacedKey;
                return namespacedKey;
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine furniture key lookup failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
        return null;
    }

    private static boolean proxyEquals(Object proxy, Object[] args) {
        return args != null && args.length == 1 && proxy == args[0];
    }

    private void applyDisplayClickAction(Entity entity, DisplayClickAction action) {
        if (entity == null) {
            return;
        }
        this.markManagedFurnitureEntity(entity);
        if (action == null) {
            TableDisplayRegistry.unregister(entity.getEntityId());
            return;
        }
        TableDisplayRegistry.register(entity.getEntityId(), action);
    }

    private void markManagedFurnitureEntity(Entity entity) {
        if (entity == null) {
            return;
        }
        entity.getPersistentDataContainer().set(managedFurnitureKey(), PersistentDataType.BYTE, (byte) 1);
    }

    private NamespacedKey managedFurnitureKey() {
        return new NamespacedKey(this.plugin, MANAGED_FURNITURE_KEY);
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

    private void warnUnavailableFurnitureId(String furnitureItemId) {
        if (furnitureItemId == null || furnitureItemId.isBlank()) {
            return;
        }
        if (!this.warnedUnavailableFurnitureIds.add(furnitureItemId)) {
            return;
        }
        this.plugin.getLogger().warning(
            "CraftEngine could not place furniture '" + furnitureItemId
                + "'. Ensure the id exists and is defined as furniture, not only as a block or item."
        );
        this.plugin.debug().log(
            "lifecycle",
            "CraftEngine returned no furniture instance for id=" + furnitureItemId + ". MahjongPaper will use its fallback render path when available."
        );
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

    private Plugin craftEnginePlugin() {
        Plugin cached = this.craftEnginePlugin;
        if (cached != null) {
            return cached;
        }
        Plugin exact = this.findCraftEnginePlugin();
        if (exact != null) {
            this.craftEnginePlugin = exact;
        }
        return exact;
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

    private void injectAntiCheatMappingsIfNeeded(Plugin craftEngine) {
        if (!this.injectAntiCheatPacketEventsMappings || this.antiCheatMappingsInjected) {
            return;
        }
        if (craftEngine == null || !craftEngine.isEnabled()) {
            return;
        }

        boolean attempted = false;
        boolean injected = false;
        if (this.isPluginEnabled("packetevents")) {
            attempted = true;
            injected = this.invokeWrappedBlockStateRegister(craftEngine, null, "PacketEvents") || injected;
        }
        if (this.isPluginEnabled("GrimAC")) {
            attempted = true;
            injected = this.invokeWrappedBlockStateRegister(craftEngine, GRIM_PACKETEVENTS_PACKAGE, "GrimAC") || injected;
        }
        if (this.isPluginEnabled("Vulcan")) {
            attempted = true;
            for (String candidate : VULCAN_PACKETEVENTS_PACKAGES) {
                injected = this.invokeWrappedBlockStateRegister(craftEngine, candidate, "Vulcan") || injected;
                if (injected) {
                    break;
                }
            }
        }
        if (attempted && !injected) {
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine anti-cheat PacketEvents mapping injection was attempted but no compatible package was found."
            );
        }
        this.antiCheatMappingsInjected = injected;
    }

    private boolean invokeWrappedBlockStateRegister(Plugin craftEngine, String packageName, String sourceName) {
        try {
            ClassLoader classLoader = craftEngine.getClass().getClassLoader();
            Class<?> helperClass = Class.forName(WRAPPED_BLOCK_STATE_HELPER_CLASS, true, classLoader);
            Method registerMethod = helperClass.getMethod(WRAPPED_BLOCK_STATE_REGISTER_METHOD, String.class);
            registerMethod.invoke(null, packageName);
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine anti-cheat PacketEvents mapping injection succeeded via " + sourceName + "."
            );
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine anti-cheat PacketEvents mapping injection skipped for "
                    + sourceName + ": " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return false;
        }
    }

    private boolean isPluginEnabled(String pluginName) {
        Plugin plugin = this.plugin.getServer().getPluginManager().getPlugin(pluginName);
        return plugin != null && plugin.isEnabled();
    }

    private Method resolveBuildItemStackMethod(Class<?> customItemClass) {
        return this.customItemBuildMethods.computeIfAbsent(customItemClass, this::lookupBuildItemStackMethod);
    }

    private Method lookupBuildItemStackMethod(Class<?> customItemClass) {
        try {
            return customItemClass.getMethod("buildItemStack");
        } catch (NoSuchMethodException buildItemStackMissing) {
            for (Method method : customItemClass.getMethods()) {
                if (!method.getName().equals("buildBukkitItem")) {
                    continue;
                }
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length == 1 && isItemBuildContextType(parameters[0])) {
                    return method;
                }
                if (
                    parameters.length == 2
                        && isItemBuildContextType(parameters[0])
                        && (parameters[1] == int.class || parameters[1] == Integer.class)
                ) {
                    return method;
                }
            }
            throw new IllegalStateException(
                "CraftEngine custom item class does not expose a compatible build method "
                    + "(buildItemStack() or buildBukkitItem(ItemBuildContext[, int])): "
                    + customItemClass.getName(),
                buildItemStackMissing
            );
        }
    }

    private Object invokeCustomItemBuildMethod(Object customItem, Method buildMethod) throws ReflectiveOperationException {
        Class<?>[] parameters = buildMethod.getParameterTypes();
        if (parameters.length == 0) {
            return buildMethod.invoke(customItem);
        }
        if (parameters.length == 1 && isItemBuildContextType(parameters[0])) {
            return buildMethod.invoke(customItem, emptyItemBuildContext(parameters[0]));
        }
        if (
            parameters.length == 2
                && isItemBuildContextType(parameters[0])
                && (parameters[1] == int.class || parameters[1] == Integer.class)
        ) {
            return buildMethod.invoke(customItem, emptyItemBuildContext(parameters[0]), 1);
        }
        throw new IllegalStateException("Unsupported CraftEngine custom item build method signature: " + buildMethod.toGenericString());
    }

    private static boolean isItemBuildContextType(Class<?> type) {
        return "net.momirealms.craftengine.core.item.ItemBuildContext".equals(type.getName());
    }

    private static Object emptyItemBuildContext(Class<?> itemBuildContextType) throws ReflectiveOperationException {
        Method emptyMethod = itemBuildContextType.getMethod("empty");
        return emptyMethod.invoke(null);
    }

    private Method resolveFurnitureEntityMethod(Class<?> furnitureInstanceClass) {
        return this.furnitureEntityMethods.computeIfAbsent(furnitureInstanceClass, this::lookupFurnitureEntityMethod);
    }

    private Method resolveFurnitureHitboxesMethod(Class<?> furnitureInstanceClass) {
        return this.furnitureHitboxesMethods.computeIfAbsent(furnitureInstanceClass, ignored -> this.lookupMethod(ignored, "hitboxes"));
    }

    private Method resolveFurniturePositionMethod(Class<?> furnitureInstanceClass) {
        return this.furniturePositionMethods.computeIfAbsent(furnitureInstanceClass, ignored -> this.lookupMethod(ignored, "position"));
    }

    private Method resolveHitboxSeatsMethod(Class<?> hitboxClass) {
        return this.hitboxSeatsMethods.computeIfAbsent(hitboxClass, ignored -> this.lookupMethod(ignored, "seats"));
    }

    private Method resolveSeatOccupiedMethod(Class<?> seatClass) {
        return this.seatOccupiedMethods.computeIfAbsent(seatClass, ignored -> this.lookupMethod(ignored, "isOccupied"));
    }

    private Method lookupFurnitureEntityMethod(Class<?> furnitureInstanceClass) {
        try {
            return furnitureInstanceClass.getMethod("bukkitEntity");
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("CraftEngine furniture instance does not expose bukkitEntity(): " + furnitureInstanceClass.getName(), exception);
        }
    }

    private Method lookupMethod(Class<?> type, String methodName) {
        try {
            return type.getMethod(methodName);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("CraftEngine class does not expose " + methodName + "(): " + type.getName(), exception);
        }
    }

    private Object[] asObjectArray(Object value) {
        if (value instanceof Object[] array) {
            return array;
        }
        return new Object[0];
    }

    private Object craftEngineKey(String key, Method keyOfMethod) throws ReflectiveOperationException {
        Object cached = this.craftEngineKeyCache.get(key);
        if (cached != null) {
            return cached;
        }
        Object resolved = keyOfMethod.invoke(null, key);
        Object previous = this.craftEngineKeyCache.putIfAbsent(key, resolved);
        return previous == null ? resolved : previous;
    }

    private ItemBridge itemBridge(Plugin craftEngine) {
        if (craftEngine == null || !craftEngine.isEnabled() || this.itemReflectionUnavailable) {
            return null;
        }
        ItemBridge cached = this.itemBridge;
        if (cached != null) {
            return cached;
        }
        try {
            ClassLoader classLoader = craftEngine.getClass().getClassLoader();
            Class<?> keyClass = Class.forName("net.momirealms.craftengine.core.util.Key", true, classLoader);
            Class<?> itemsClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems", true, classLoader);
            ItemBridge bridge = new ItemBridge(
                keyClass.getMethod("of", String.class),
                itemsClass.getMethod("byId", keyClass)
            );
            this.itemBridge = bridge;
            return bridge;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.itemReflectionUnavailable = true;
            this.plugin.getLogger().warning(
                "CraftEngine was detected, but MahjongPaper could not build CraftEngine custom items. Falling back to direct item_model items."
            );
            this.plugin.debug().log("lifecycle", "CraftEngine reflection bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            return null;
        }
    }

    private FurnitureBridge furnitureBridge(Plugin craftEngine) {
        if (craftEngine == null || !craftEngine.isEnabled() || this.furnitureReflectionUnavailable) {
            return null;
        }
        FurnitureBridge cached = this.furnitureBridge;
        if (cached != null) {
            return cached;
        }
        try {
            ClassLoader classLoader = craftEngine.getClass().getClassLoader();
            Class<?> keyClass = Class.forName("net.momirealms.craftengine.core.util.Key", true, classLoader);
            Class<?> furnitureClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineFurniture", true, classLoader);
            Class<?> adaptorsClass = Class.forName("net.momirealms.craftengine.bukkit.api.BukkitAdaptors", true, classLoader);
            Class<?> seatClass = Class.forName("net.momirealms.craftengine.core.entity.seat.Seat", true, classLoader);
            Class<?> cePlayerClass = Class.forName("net.momirealms.craftengine.core.entity.player.Player", true, classLoader);
            Class<?> worldPositionClass = Class.forName("net.momirealms.craftengine.core.world.WorldPosition", true, classLoader);
            Method removeWithFlagsMethod = null;
            Method removeMethod = null;
            try {
                removeWithFlagsMethod = furnitureClass.getMethod("remove", Entity.class, boolean.class, boolean.class);
            } catch (NoSuchMethodException ignored) {
                removeMethod = furnitureClass.getMethod("remove", Entity.class);
            }
            FurnitureBridge bridge = new FurnitureBridge(
                keyClass.getMethod("of", String.class),
                furnitureClass.getMethod("place", Location.class, keyClass),
                furnitureClass.getMethod("isFurniture", Entity.class),
                furnitureClass.getMethod("isSeat", Entity.class),
                furnitureClass.getMethod("getLoadedFurnitureBySeat", Entity.class),
                furnitureClass.getMethod("getLoadedFurnitureByMetaEntity", Entity.class),
                adaptorsClass.getMethod("adapt", Player.class),
                seatClass.getMethod("spawnSeat", cePlayerClass, worldPositionClass),
                removeMethod,
                removeWithFlagsMethod
            );
            this.furnitureBridge = bridge;
            return bridge;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.furnitureReflectionUnavailable = true;
            this.plugin.getLogger().warning(
                "CraftEngine was detected, but MahjongPaper could not place CraftEngine furniture. CraftEngine-based interaction may be unavailable."
            );
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine furniture bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return null;
        }
    }

    private record TrackedCullableEntity(Entity entity, int entityId, UUID entityUuid, Object cullableProxy) {
    }

    private record ItemBridge(Method keyOfMethod, Method byIdMethod) {
    }

    private record FurnitureBridge(
        Method keyOfMethod,
        Method placeMethod,
        Method isFurnitureMethod,
        Method isSeatMethod,
        Method loadedFurnitureBySeatMethod,
        Method loadedFurnitureByMetaEntityMethod,
        Method playerAdaptMethod,
        Method seatSpawnMethod,
        Method removeMethod,
        Method removeWithFlagsMethod
    ) {
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


