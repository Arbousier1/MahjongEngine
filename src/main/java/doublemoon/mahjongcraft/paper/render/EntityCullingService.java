package doublemoon.mahjongcraft.paper.render;

import doublemoon.mahjongcraft.paper.MahjongPaperPlugin;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class EntityCullingService {
    private final MahjongPaperPlugin plugin;
    private final EntityCullingConfig config;
    private final Map<Integer, CullableEntity> trackedEntities = new HashMap<>();
    private final Map<UUID, ViewerState> viewerStates = new HashMap<>();
    private BukkitTask task;

    public EntityCullingService(MahjongPaperPlugin plugin, EntityCullingConfig config) {
        this.plugin = plugin;
        this.config = Objects.requireNonNullElseGet(config, EntityCullingConfig::defaults);
    }

    public void enable() {
        if (!this.config.enabled()) {
            return;
        }
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, this.config.intervalTicks(), this.config.intervalTicks());
    }

    public void disable() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        this.showAllHiddenEntities();
        this.trackedEntities.clear();
        this.viewerStates.clear();
    }

    public void register(Entity entity) {
        if (!this.config.enabled() || !isCullableEntity(entity)) {
            return;
        }
        this.trackedEntities.put(entity.getEntityId(), new CullableEntity(entity, maxDistance(entity)));
    }

    public void unregister(Entity entity) {
        if (entity == null) {
            return;
        }
        CullableEntity removed = this.trackedEntities.remove(entity.getEntityId());
        if (removed == null) {
            return;
        }
        UUID entityUuid = removed.entityUuid();
        for (ViewerState viewerState : this.viewerStates.values()) {
            viewerState.hiddenEntities.remove(entityUuid);
        }
    }

    private void tick() {
        this.pruneOfflineViewers();
        this.pruneInvalidEntities();
        if (this.trackedEntities.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            ViewerState viewerState = this.viewerStates.computeIfAbsent(player.getUniqueId(), ignored -> new ViewerState(this.config.bucketSize()));
            viewerState.restoreTokens(this.config.bucketSize(), this.config.restorePerTick());
            this.tickViewer(player, viewerState);
        }
    }

    private void tickViewer(Player player, ViewerState viewerState) {
        double viewerX = player.getX();
        double viewerY = player.getY();
        double viewerZ = player.getZ();
        UUID viewerId = player.getUniqueId();
        for (CullableEntity target : this.trackedEntities.values()) {
            Entity entity = target.entity();
            boolean shouldShow = DisplayVisibilityRegistry.canView(target.entityId(), viewerId)
                && entity.isValid()
                && entity.getWorld().equals(player.getWorld())
                && withinRange(viewerX, viewerY, viewerZ, entity, Math.min(target.maxDistance(), this.config.viewDistance()));
            boolean hidden = viewerState.hiddenEntities.contains(target.entityUuid());
            if (shouldShow) {
                if (hidden) {
                    if (this.config.rateLimitingEnabled() && !viewerState.takeToken()) {
                        continue;
                    }
                    player.showEntity(this.plugin, entity);
                    viewerState.hiddenEntities.remove(target.entityUuid());
                    this.plugin.debug().log("culling", "Showed entity " + target.entityId() + " to " + player.getName());
                }
            } else if (!hidden) {
                player.hideEntity(this.plugin, entity);
                viewerState.hiddenEntities.add(target.entityUuid());
                this.plugin.debug().log("culling", "Hid entity " + target.entityId() + " from " + player.getName());
            }
        }
    }

    private void pruneOfflineViewers() {
        Iterator<Map.Entry<UUID, ViewerState>> iterator = this.viewerStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ViewerState> entry = iterator.next();
            if (Bukkit.getPlayer(entry.getKey()) == null) {
                iterator.remove();
            }
        }
    }

    private void pruneInvalidEntities() {
        Iterator<Map.Entry<Integer, CullableEntity>> iterator = this.trackedEntities.entrySet().iterator();
        while (iterator.hasNext()) {
            CullableEntity target = iterator.next().getValue();
            if (target.entity().isValid()) {
                continue;
            }
            iterator.remove();
            for (ViewerState viewerState : this.viewerStates.values()) {
                viewerState.hiddenEntities.remove(target.entityUuid());
            }
        }
    }

    private void showAllHiddenEntities() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ViewerState viewerState = this.viewerStates.get(player.getUniqueId());
            if (viewerState == null || viewerState.hiddenEntities.isEmpty()) {
                continue;
            }
            for (CullableEntity target : this.trackedEntities.values()) {
                if (target.entity().isValid() && viewerState.hiddenEntities.contains(target.entityUuid())) {
                    player.showEntity(this.plugin, target.entity());
                }
            }
        }
    }

    private static boolean withinRange(double viewerX, double viewerY, double viewerZ, Entity entity, double maxDistance) {
        double dx = viewerX - entity.getX();
        double dy = viewerY - entity.getY();
        double dz = viewerZ - entity.getZ();
        return dx * dx + dy * dy + dz * dz <= maxDistance * maxDistance;
    }

    private static double maxDistance(Entity entity) {
        if (entity instanceof org.bukkit.entity.Display display) {
            return Math.max(1.0D, display.getViewRange());
        }
        if (entity instanceof Interaction interaction) {
            return Math.max(8.0D, interaction.getInteractionWidth() * 16.0D);
        }
        return 32.0D;
    }

    private static boolean isCullableEntity(Entity entity) {
        return entity instanceof org.bukkit.entity.Display || entity instanceof Interaction;
    }

    private record CullableEntity(Entity entity, int entityId, UUID entityUuid, double maxDistance) {
        private CullableEntity(Entity entity, double maxDistance) {
            this(entity, entity.getEntityId(), entity.getUniqueId(), maxDistance);
        }
    }

    private static final class ViewerState {
        private final Set<UUID> hiddenEntities = new HashSet<>();
        private int tokens;

        private ViewerState(int bucketSize) {
            this.tokens = bucketSize;
        }

        private void restoreTokens(int bucketSize, int restorePerTick) {
            this.tokens = Math.min(bucketSize, this.tokens + restorePerTick);
        }

        private boolean takeToken() {
            if (this.tokens <= 0) {
                return false;
            }
            this.tokens--;
            return true;
        }
    }
}
