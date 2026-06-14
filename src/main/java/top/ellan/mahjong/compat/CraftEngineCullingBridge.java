package top.ellan.mahjong.compat;

import top.ellan.mahjong.render.display.DisplayVisibilityRegistry;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

final class CraftEngineCullingBridge {
    private final CraftEngineBridgeContext context;
    private final Map<Integer, TrackedCullableEntity> trackedCullableEntities = new ConcurrentHashMap<>();
    private volatile boolean reflectionUnavailable;
    private volatile CullingReflection reflection;

    CraftEngineCullingBridge(CraftEngineBridgeContext context) {
        this.context = context;
    }

    void registerCullableEntity(Entity entity) {
        if (entity == null || !isCullableEntity(entity) || !this.context.plugin().isEnabled()) {
            return;
        }

        CullingReflection bridge = this.reflection();
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
                this.context.plugin().scheduler().runEntity(player, () -> this.removeTrackedEntity(player, previous.entityId()));
            }
            this.trackedCullableEntities.put(entity.getEntityId(), tracked);
        }

        for (Player player : this.onlinePlayersSnapshot()) {
            this.context.plugin().scheduler().runEntity(player, () -> this.addTrackedEntity(player, tracked));
        }
    }

    void unregisterCullableEntity(Entity entity) {
        if (entity == null) {
            return;
        }

        TrackedCullableEntity tracked = this.trackedCullableEntities.remove(entity.getEntityId());
        if (tracked == null) {
            return;
        }
        if (!this.context.plugin().isEnabled()) {
            return;
        }
        for (Player player : this.onlinePlayersSnapshot()) {
            this.context.plugin().scheduler().runEntity(player, () -> this.removeTrackedEntity(player, tracked.entityId()));
        }
    }

    void syncTrackedEntitiesFor(Player player) {
        if (player == null || !player.isOnline() || !this.context.plugin().isEnabled()) {
            return;
        }
        for (TrackedCullableEntity tracked : this.trackedCullableEntities.values()) {
            this.addTrackedEntity(player, tracked);
            this.scheduleViewerVisibility(tracked.entityId(), tracked.entity(), player, true);
        }
    }

    void clearTrackedCullables() {
        this.trackedCullableEntities.clear();
    }

    private Object createCullableProxy(Entity entity, int entityId, CullingReflection bridge, Object cullingData) {
        InvocationHandler handler = (proxy, method, args) -> this.handleCullableInvocation(proxy, entity, entityId, bridge, cullingData, method, args);
        return Proxy.newProxyInstance(bridge.classLoader(), new Class<?>[] {bridge.cullableClass()}, handler);
    }

    private Object handleCullableInvocation(Object proxy, Entity entity, int entityId, CullingReflection bridge, Object cullingData, Method method, Object[] args) {
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

    private Object createCullingData(Entity entity, CullingReflection bridge) {
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
            this.reflectionUnavailable = true;
            this.context.plugin().debug().log(
                "lifecycle",
                "CraftEngine culling data bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return null;
        }
    }

    private void addTrackedEntity(Player player, TrackedCullableEntity tracked) {
        CullingReflection bridge = this.reflection();
        if (bridge == null) {
            return;
        }
        try {
            Object cePlayer = bridge.adaptMethod().invoke(null, player);
            if (cePlayer != null) {
                bridge.addTrackedEntityMethod().invoke(cePlayer, tracked.entityId(), tracked.cullableProxy());
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.reflectionUnavailable = true;
            this.context.plugin().debug().log(
                "lifecycle",
                "CraftEngine tracked entity add failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private void removeTrackedEntity(Player player, int entityId) {
        CullingReflection bridge = this.reflection();
        if (bridge == null) {
            return;
        }
        try {
            Object cePlayer = bridge.adaptMethod().invoke(null, player);
            if (cePlayer != null) {
                bridge.removeTrackedEntityMethod().invoke(cePlayer, entityId);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.reflectionUnavailable = true;
            this.context.plugin().debug().log(
                "lifecycle",
                "CraftEngine tracked entity remove failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private void scheduleViewerVisibility(int entityId, Entity entity, Player viewer, boolean visible) {
        if (entity == null || viewer == null || !this.context.plugin().isEnabled()) {
            return;
        }
        this.context.plugin().scheduler().runEntity(viewer, () -> {
            if (!viewer.isOnline()) {
                return;
            }
            // Folia/Paper region threading: showEntity/hideEntity touches both viewer and target entity internals.
            // Only run when the current thread owns both entities to avoid cross-region thread-check violations.
            if (!Bukkit.isOwnedByCurrentRegion(viewer) || !Bukkit.isOwnedByCurrentRegion(entity)) {
                return;
            }
            if (!entity.isValid() || entity.isDead()) {
                return;
            }
            if (!DisplayVisibilityRegistry.canView(entityId, viewer.getUniqueId())) {
                viewer.hideEntity(this.context.plugin(), entity);
                return;
            }
            if (visible) {
                viewer.showEntity(this.context.plugin(), entity);
            } else {
                viewer.hideEntity(this.context.plugin(), entity);
            }
        });
    }

    private List<Player> onlinePlayersSnapshot() {
        return List.copyOf(Bukkit.getOnlinePlayers());
    }

    private Player resolvePlatformPlayer(CullingReflection bridge, Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return null;
        }
        try {
            Object platformPlayer = bridge.platformPlayerMethod().invoke(args[0]);
            return platformPlayer instanceof Player player ? player : null;
        } catch (ReflectiveOperationException exception) {
            this.reflectionUnavailable = true;
            this.context.plugin().debug().log(
                "lifecycle",
                "CraftEngine platform player bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return null;
        }
    }

    private CullingReflection reflection() {
        if (this.reflectionUnavailable) {
            return null;
        }
        CullingReflection cached = this.reflection;
        if (cached != null) {
            return cached;
        }

        Plugin craftEngine = this.context.craftEnginePlugin();
        if (craftEngine == null || !craftEngine.isEnabled()) {
            return null;
        }

        try {
            ClassLoader classLoader = craftEngine.getClass().getClassLoader();
            Class<?> adaptorClass = Class.forName("net.momirealms.craftengine.bukkit.api.BukkitAdaptor", true, classLoader);
            Class<?> cullableClass = Class.forName("net.momirealms.craftengine.core.entity.culling.Cullable", true, classLoader);
            Class<?> cePlayerClass = Class.forName("net.momirealms.craftengine.core.entity.player.Player", true, classLoader);
            Class<?> aabbClass = Class.forName("net.momirealms.craftengine.core.world.collision.AABB", true, classLoader);
            Class<?> cullingDataClass = Class.forName("net.momirealms.craftengine.core.entity.culling.CullingData", true, classLoader);
            CullingReflection resolved = new CullingReflection(
                classLoader,
                cullableClass,
                adaptorClass.getMethod("adapt", Player.class),
                cePlayerClass.getMethod("addTrackedEntity", int.class, cullableClass),
                cePlayerClass.getMethod("removeTrackedEntity", int.class),
                cePlayerClass.getMethod("platformPlayer"),
                aabbClass.getConstructor(double.class, double.class, double.class, double.class, double.class, double.class),
                cullingDataClass.getConstructor(aabbClass, int.class, double.class, boolean.class)
            );
            this.reflection = resolved;
            return resolved;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.reflectionUnavailable = true;
            this.context.plugin().getLogger().warning(
                "CraftEngine was detected, but MahjongPaper could not bridge CraftEngine entity culling. Display entities will use normal server tracking."
            );
            this.context.plugin().debug().log(
                "lifecycle",
                "CraftEngine culling reflection bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return null;
        }
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

    private static boolean proxyEquals(Object proxy, Object[] args) {
        return args != null && args.length == 1 && proxy == args[0];
    }

    private record TrackedCullableEntity(Entity entity, int entityId, UUID entityUuid, Object cullableProxy) {
    }

    private record CullingReflection(
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
