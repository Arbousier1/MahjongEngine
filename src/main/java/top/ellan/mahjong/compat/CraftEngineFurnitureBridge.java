package top.ellan.mahjong.compat;

import top.ellan.mahjong.render.display.DisplayClickAction;
import top.ellan.mahjong.render.display.TableDisplayRegistry;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

final class CraftEngineFurnitureBridge {
    static final String TABLE_HITBOX_ITEM_ID = "mahjongpaper:table_hitbox";
    static final String HAND_TILE_HITBOX_ITEM_ID = "mahjongpaper:hand_tile_hitbox";
    static final String SEAT_HITBOX_ITEM_ID = "mahjongpaper:seat_hitbox";

    private static final String MANAGED_FURNITURE_KEY = "managed_craftengine_furniture";
    private static final String MAHJONGPAPER_FURNITURE_PREFIX = "mahjongpaper:";
    private static final int STARTUP_FURNITURE_CLEANUP_REMOVALS_PER_TICK = 8;

    private final CraftEngineBridgeContext context;
    private final boolean preferFurnitureHitbox;
    private final Map<Class<?>, Method> furnitureEntityMethods = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> furnitureHitboxesMethods = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> furniturePositionMethods = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> hitboxSeatsMethods = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> seatOccupiedMethods = new ConcurrentHashMap<>();
    private final Set<String> warnedUnavailableFurnitureIds = ConcurrentHashMap.newKeySet();
    private volatile boolean reflectionUnavailable;
    private volatile FurnitureReflection reflection;
    private volatile NamespacedKey furnitureDataKey;

    CraftEngineFurnitureBridge(CraftEngineBridgeContext context, boolean preferFurnitureHitbox) {
        this.context = context;
        this.preferFurnitureHitbox = preferFurnitureHitbox;
    }

    int cleanupMahjongFurniture() {
        if (this.reflectionUnavailable) {
            return 0;
        }

        Plugin craftEngine = this.context.craftEnginePlugin();
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
                this.context.plugin().scheduler().runRegion(chunkCenter, () -> {
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
                            this.context.plugin().scheduler().removeEntity(entity, delayTicks);
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
        this.context.plugin().scheduler().runGlobalDelayed(() -> {
            int removedCount = removed.get();
            if (removedCount > 0) {
                this.context.plugin().getLogger().info(
                    "Removed " + removedCount + " leftover MahjongPaper CraftEngine furniture entities from previous sessions."
                );
                this.context.plugin().debug().log(
                    "lifecycle",
                    "Startup cleanup removed " + removedCount + " lingering mahjongpaper furniture entities."
                );
            }
        }, 40L);
        return 0;
    }

    Entity placeTableHitbox(Location location) {
        return this.placeFurniture(location, TABLE_HITBOX_ITEM_ID);
    }

    Entity placeHandTileHitbox(Location location, DisplayClickAction action) {
        return this.placeFurniture(location, HAND_TILE_HITBOX_ITEM_ID, action);
    }

    Entity placeSeatHitbox(Location location, DisplayClickAction action) {
        return this.placeFurniture(location, SEAT_HITBOX_ITEM_ID, action);
    }

    Entity placeSeatFurniture(Location location, String furnitureItemId, DisplayClickAction action) {
        Entity entity = this.placeFurniture(location, furnitureItemId);
        if (entity != null && action != null) {
            this.markManagedFurnitureEntity(entity);
            TableDisplayRegistry.register(entity.getEntityId(), action);
        }
        return entity;
    }

    Entity placeFurniture(Location location, String furnitureItemId, DisplayClickAction action) {
        Entity entity = this.placeFurniture(location, furnitureItemId);
        if (entity != null) {
            this.markManagedFurnitureEntity(entity);
            this.applyDisplayClickAction(entity, action);
        }
        return entity;
    }

    Entity placeFurniture(Location location, String furnitureItemId) {
        if (!this.preferFurnitureHitbox || this.reflectionUnavailable) {
            return null;
        }

        Plugin craftEngine = this.context.craftEnginePlugin();
        FurnitureReflection bridge = this.reflection(craftEngine);
        if (bridge == null) {
            return null;
        }

        try {
            Object key = this.context.craftEngineKey(furnitureItemId, bridge.keyOfMethod());
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
            this.reflectionUnavailable = true;
            this.context.plugin().getLogger().warning(
                "CraftEngine was detected, but MahjongPaper could not place CraftEngine furniture. CraftEngine-based interaction may be unavailable."
            );
            this.context.plugin().debug().log(
                "lifecycle",
                "CraftEngine furniture bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return null;
        }
    }

    boolean removeFurniture(Entity entity) {
        if (entity == null || this.reflectionUnavailable) {
            return false;
        }

        Plugin craftEngine = this.context.craftEnginePlugin();
        FurnitureReflection bridge = this.reflection(craftEngine);
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
            this.reflectionUnavailable = true;
            this.context.plugin().debug().log(
                "lifecycle",
                "CraftEngine furniture remove bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return false;
        }
    }

    boolean isFurnitureEntity(Entity entity) {
        if (entity == null || this.reflectionUnavailable) {
            return false;
        }
        Plugin craftEngine = this.context.craftEnginePlugin();
        FurnitureReflection bridge = this.reflection(craftEngine);
        if (bridge == null) {
            return false;
        }
        try {
            Object isFurniture = bridge.isFurnitureMethod().invoke(null, entity);
            return isFurniture instanceof Boolean flag && flag;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.reflectionUnavailable = true;
            this.context.plugin().debug().log(
                "lifecycle",
                "CraftEngine furniture detection bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return false;
        }
    }

    boolean reconcileFurniture(Entity entity, Location location, String furnitureItemId, DisplayClickAction action) {
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
        this.context.plugin().scheduler().teleport(entity, target);
        this.markManagedFurnitureEntity(entity);
        this.applyDisplayClickAction(entity, action);
        return true;
    }

    boolean isManagedFurnitureEntity(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(this.managedFurnitureKey(), PersistentDataType.BYTE);
    }

    boolean isMahjongFurnitureEntity(Entity entity) {
        String itemId = this.furnitureItemId(entity);
        return itemId != null && itemId.startsWith(MAHJONGPAPER_FURNITURE_PREFIX);
    }

    String furnitureItemId(Entity entity) {
        if (entity == null || this.reflectionUnavailable) {
            return null;
        }
        Plugin craftEngine = this.context.craftEnginePlugin();
        if (craftEngine == null || !craftEngine.isEnabled()) {
            return null;
        }
        NamespacedKey furnitureKey = this.resolveFurnitureDataKey(craftEngine);
        if (furnitureKey == null) {
            return null;
        }
        return entity.getPersistentDataContainer().get(furnitureKey, PersistentDataType.STRING);
    }

    boolean isSeatEntity(Entity entity) {
        if (entity == null || this.reflectionUnavailable) {
            return false;
        }
        Plugin craftEngine = this.context.craftEnginePlugin();
        FurnitureReflection bridge = this.reflection(craftEngine);
        if (bridge == null || bridge.isSeatMethod() == null) {
            return false;
        }
        try {
            Object isSeat = bridge.isSeatMethod().invoke(null, entity);
            return isSeat instanceof Boolean flag && flag;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.reflectionUnavailable = true;
            this.context.plugin().debug().log(
                "lifecycle",
                "CraftEngine seat detection bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return false;
        }
    }

    Entity furnitureEntityForSeat(Entity seatEntity) {
        if (seatEntity == null || this.reflectionUnavailable) {
            return null;
        }
        Plugin craftEngine = this.context.craftEnginePlugin();
        FurnitureReflection bridge = this.reflection(craftEngine);
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
            this.reflectionUnavailable = true;
            this.context.plugin().debug().log(
                "lifecycle",
                "CraftEngine seat owner bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return null;
        }
    }

    boolean canPlaceFurniture() {
        if (!this.preferFurnitureHitbox || this.reflectionUnavailable) {
            return false;
        }
        Plugin craftEngine = this.context.craftEnginePlugin();
        return craftEngine != null && craftEngine.isEnabled() && this.reflection(craftEngine) != null;
    }

    boolean seatPlayerOnFurniture(Entity furnitureEntity, Player player) {
        if (furnitureEntity == null || player == null || !player.isOnline() || this.reflectionUnavailable) {
            return false;
        }
        Plugin craftEngine = this.context.craftEnginePlugin();
        FurnitureReflection bridge = this.reflection(craftEngine);
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
            Object[] hitboxes = asObjectArray(this.resolveFurnitureHitboxesMethod(furniture.getClass()).invoke(furniture));
            for (Object hitbox : hitboxes) {
                Object[] seats = asObjectArray(this.resolveHitboxSeatsMethod(hitbox.getClass()).invoke(hitbox));
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
            this.reflectionUnavailable = true;
            this.context.plugin().debug().log(
                "lifecycle",
                "CraftEngine seat spawn bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return false;
        }
    }

    void markManagedFurnitureEntity(Entity entity) {
        if (entity == null) {
            return;
        }
        entity.getPersistentDataContainer().set(this.managedFurnitureKey(), PersistentDataType.BYTE, (byte) 1);
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

    private NamespacedKey managedFurnitureKey() {
        return new NamespacedKey(this.context.bukkitPlugin(), MANAGED_FURNITURE_KEY);
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
            this.context.plugin().debug().log(
                "lifecycle",
                "CraftEngine furniture key lookup failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
        return null;
    }

    private void warnUnavailableFurnitureId(String furnitureItemId) {
        if (furnitureItemId == null || furnitureItemId.isBlank()) {
            return;
        }
        if (!this.warnedUnavailableFurnitureIds.add(furnitureItemId)) {
            return;
        }
        this.context.plugin().getLogger().warning(
            "CraftEngine could not place furniture '" + furnitureItemId
                + "'. Ensure the id exists and is defined as furniture, not only as a block or item."
        );
        this.context.plugin().debug().log(
            "lifecycle",
            "CraftEngine returned no furniture instance for id=" + furnitureItemId + ". MahjongPaper will use its fallback render path when available."
        );
    }

    private FurnitureReflection reflection(Plugin craftEngine) {
        if (craftEngine == null || !craftEngine.isEnabled() || this.reflectionUnavailable) {
            return null;
        }
        FurnitureReflection cached = this.reflection;
        if (cached != null) {
            return cached;
        }
        try {
            ClassLoader classLoader = craftEngine.getClass().getClassLoader();
            Class<?> keyClass = Class.forName("net.momirealms.craftengine.core.util.Key", true, classLoader);
            Class<?> furnitureClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineFurniture", true, classLoader);
            Class<?> adaptorClass = Class.forName("net.momirealms.craftengine.bukkit.api.BukkitAdaptor", true, classLoader);
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
            FurnitureReflection resolved = new FurnitureReflection(
                keyClass.getMethod("of", String.class),
                furnitureClass.getMethod("place", Location.class, keyClass),
                furnitureClass.getMethod("isFurniture", Entity.class),
                furnitureClass.getMethod("isSeat", Entity.class),
                furnitureClass.getMethod("getLoadedFurnitureBySeat", Entity.class),
                furnitureClass.getMethod("getLoadedFurnitureByMetaEntity", Entity.class),
                adaptorClass.getMethod("adapt", Player.class),
                seatClass.getMethod("spawnSeat", cePlayerClass, worldPositionClass),
                removeMethod,
                removeWithFlagsMethod
            );
            this.reflection = resolved;
            return resolved;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.reflectionUnavailable = true;
            this.context.plugin().getLogger().warning(
                "CraftEngine was detected, but MahjongPaper could not place CraftEngine furniture. CraftEngine-based interaction may be unavailable."
            );
            this.context.plugin().debug().log(
                "lifecycle",
                "CraftEngine furniture bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return null;
        }
    }

    private Method resolveFurnitureEntityMethod(Class<?> furnitureInstanceClass) {
        return this.furnitureEntityMethods.computeIfAbsent(furnitureInstanceClass, CraftEngineFurnitureBridge::lookupFurnitureEntityMethod);
    }

    private Method resolveFurnitureHitboxesMethod(Class<?> furnitureInstanceClass) {
        return this.furnitureHitboxesMethods.computeIfAbsent(furnitureInstanceClass, ignored -> lookupMethod(ignored, "hitboxes"));
    }

    private Method resolveFurniturePositionMethod(Class<?> furnitureInstanceClass) {
        return this.furniturePositionMethods.computeIfAbsent(furnitureInstanceClass, ignored -> lookupMethod(ignored, "position"));
    }

    private Method resolveHitboxSeatsMethod(Class<?> hitboxClass) {
        return this.hitboxSeatsMethods.computeIfAbsent(hitboxClass, ignored -> lookupMethod(ignored, "seats"));
    }

    private Method resolveSeatOccupiedMethod(Class<?> seatClass) {
        return this.seatOccupiedMethods.computeIfAbsent(seatClass, ignored -> lookupMethod(ignored, "isOccupied"));
    }

    private static Method lookupFurnitureEntityMethod(Class<?> furnitureInstanceClass) {
        try {
            return furnitureInstanceClass.getMethod("bukkitEntity");
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("CraftEngine furniture instance does not expose bukkitEntity(): " + furnitureInstanceClass.getName(), exception);
        }
    }

    private static Method lookupMethod(Class<?> type, String methodName) {
        try {
            return type.getMethod(methodName);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("CraftEngine class does not expose " + methodName + "(): " + type.getName(), exception);
        }
    }

    private static Object[] asObjectArray(Object value) {
        if (value == null) {
            return new Object[0];
        }
        if (value instanceof Object[] array) {
            return array;
        }
        if (value instanceof Collection<?> collection) {
            return collection.toArray();
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> collected = new ArrayList<>();
            for (Object element : iterable) {
                collected.add(element);
            }
            return collected.toArray();
        }
        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            int length = Array.getLength(value);
            Object[] converted = new Object[length];
            for (int index = 0; index < length; index++) {
                converted[index] = Array.get(value, index);
            }
            return converted;
        }
        return new Object[0];
    }

    private record FurnitureReflection(
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
}
