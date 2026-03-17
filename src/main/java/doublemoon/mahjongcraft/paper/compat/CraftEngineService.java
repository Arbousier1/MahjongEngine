package doublemoon.mahjongcraft.paper.compat;

import doublemoon.mahjongcraft.paper.config.ConfigAccess;
import doublemoon.mahjongcraft.paper.bootstrap.MahjongPaperPlugin;
import doublemoon.mahjongcraft.paper.model.MahjongTile;
import doublemoon.mahjongcraft.paper.render.display.DisplayClickAction;
import doublemoon.mahjongcraft.paper.render.display.DisplayVisibilityRegistry;
import doublemoon.mahjongcraft.paper.render.display.TableDisplayRegistry;
import doublemoon.mahjongcraft.paper.table.core.MahjongTableManager;
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
    private static final String BUNDLE_ROOT = "craftengine/mahjongpaper";
    private static final String BUNDLE_INDEX = BUNDLE_ROOT + "/_bundle_index.txt";
    private static final String CRAFT_ENGINE_PLUGIN_NAME = "CraftEngine";
    private static final String TABLE_HITBOX_ITEM_ID = "mahjongpaper:table_hitbox";
    private static final String HAND_TILE_HITBOX_ITEM_ID = "mahjongpaper:hand_tile_hitbox";
    private static final String SEAT_HITBOX_ITEM_ID = "mahjongpaper:seat_hitbox";
    private static final String MAHJONGPAPER_FURNITURE_PREFIX = "mahjongpaper:";

    private final MahjongPaperPlugin plugin;
    private final boolean exportBundleOnEnable;
    private final boolean preferCustomItems;
    private final boolean preferFurnitureHitbox;
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
    private volatile boolean itemReflectionUnavailable;
    private volatile boolean furnitureReflectionUnavailable;
    private volatile boolean cullingReflectionUnavailable;
    private volatile Plugin craftEnginePlugin;
    private volatile ItemBridge itemBridge;
    private volatile FurnitureBridge furnitureBridge;
    private volatile ReflectionBridge reflectionBridge;
    private volatile NamespacedKey furnitureDataKey;
    private Listener furnitureInteractListener;

    public CraftEngineService(MahjongPaperPlugin plugin, ConfigurationSection section) {
        this.plugin = plugin;
        this.exportBundleOnEnable = ConfigAccess.bool(section, true, "exportBundleOnEnable", "bundle.exportOnEnable");
        this.preferCustomItems = ConfigAccess.bool(section, true, "preferCustomItems", "items.preferCustomItems");
        this.preferFurnitureHitbox = ConfigAccess.bool(section, true, "preferFurnitureHitbox", "furniture.preferHitboxInteraction");
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

        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                String furnitureId = entity.getPersistentDataContainer().get(furnitureKey, PersistentDataType.STRING);
                if (furnitureId == null || !furnitureId.startsWith(MAHJONGPAPER_FURNITURE_PREFIX)) {
                    continue;
                }
                boolean removedByCraftEngine = this.removeFurniture(entity);
                if (!removedByCraftEngine && entity.isValid()) {
                    entity.remove();
                }
                removed++;
            }
        }

        if (removed > 0) {
            this.plugin.getLogger().info("Removed " + removed + " leftover MahjongPaper CraftEngine furniture entities from previous sessions.");
            this.plugin.debug().log("lifecycle", "Startup cleanup removed " + removed + " lingering mahjongpaper furniture entities.");
        }
        return removed;
    }

    public ItemStack resolveTileItem(MahjongTile tile, boolean faceDown) {
        if (!this.preferCustomItems || this.itemReflectionUnavailable) {
            return null;
        }

        Plugin craftEngine = this.craftEnginePlugin();
        ItemBridge bridge = this.itemBridge(craftEngine);
        if (bridge == null) {
            return null;
        }

        String itemId = this.customItemId(tile, faceDown);
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
            Object built = this.resolveBuildItemStackMethod(customItem.getClass()).invoke(customItem);
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
            TableDisplayRegistry.register(entity.getEntityId(), action);
        }
        return entity;
    }

    public Entity placeFurniture(Location location, String furnitureItemId, DisplayClickAction action) {
        Entity entity = this.placeFurniture(location, furnitureItemId);
        if (entity != null) {
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
                return null;
            }
            Object bukkitEntity = this.resolveFurnitureEntityMethod(furniture.getClass()).invoke(furniture);
            return bukkitEntity instanceof Entity entity ? entity : null;
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
            Class<? extends Event> eventClass = Class.forName(
                "net.momirealms.craftengine.bukkit.api.event.FurnitureInteractEvent",
                true,
                classLoader
            ).asSubclass(Event.class);
            Method playerMethod = eventClass.getMethod("player");
            Method furnitureMethod = eventClass.getMethod("furniture");
            Method entityIdMethod = furnitureMethod.getReturnType().getMethod("entityId");
            Listener listener = new Listener() {
            };
            EventExecutor executor = (ignored, event) -> this.handleFurnitureInteractEvent(
                event,
                tableManager,
                playerMethod,
                furnitureMethod,
                entityIdMethod
            );
            this.plugin.getServer().getPluginManager().registerEvent(
                eventClass,
                listener,
                EventPriority.NORMAL,
                executor,
                this.plugin,
                true
            );
            this.furnitureInteractListener = listener;
        } catch (ReflectiveOperationException exception) {
            this.plugin.getLogger().warning(
                "CraftEngine was detected, but MahjongPaper could not register the furniture interaction bridge. Tile clicking will not use CraftEngine."
            );
            this.plugin.debug().log(
                "lifecycle",
                "CraftEngine interaction bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
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
                this.scheduleViewerVisibility(tracked.entity(), player, true);
            }
        }
    }

    public void clearTrackedCullables() {
        this.trackedCullableEntities.clear();
    }

    public String customItemId(MahjongTile tile, boolean faceDown) {
        return CraftEngineTileItemResolver.resolve(this.plugin.settings().craftEngineTileItemIdPrefix(), tile, faceDown);
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
        entity.teleport(target);
        entity.setRotation(target.getYaw(), target.getPitch());
        this.applyDisplayClickAction(entity, action);
        return true;
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
        if (action == null) {
            TableDisplayRegistry.unregister(entity.getEntityId());
            return;
        }
        TableDisplayRegistry.register(entity.getEntityId(), action);
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

    private Method resolveBuildItemStackMethod(Class<?> customItemClass) {
        return this.customItemBuildMethods.computeIfAbsent(customItemClass, this::lookupBuildItemStackMethod);
    }

    private Method lookupBuildItemStackMethod(Class<?> customItemClass) {
        try {
            return customItemClass.getMethod("buildItemStack");
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("CraftEngine custom item class does not expose buildItemStack(): " + customItemClass.getName(), exception);
        }
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

    private record TrackedCullableEntity(Entity entity, int entityId, Object cullableProxy) {
        private TrackedCullableEntity(Entity entity, Object cullableProxy) {
            this(entity, entity.getEntityId(), cullableProxy);
        }
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

