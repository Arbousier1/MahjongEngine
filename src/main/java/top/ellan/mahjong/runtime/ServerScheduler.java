package top.ellan.mahjong.runtime;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class ServerScheduler {
    private static final PluginTask NO_OP_TASK = new PluginTask() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    };

    private final Plugin plugin;

    public ServerScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public PluginTask runGlobal(Runnable runnable) {
        if (runnable == null) {
            return NO_OP_TASK;
        }
        return wrap(this.plugin.getServer().getGlobalRegionScheduler().run(this.plugin, task -> runnable.run()));
    }

    public PluginTask runGlobalDelayed(Runnable runnable, long delayTicks) {
        if (runnable == null) {
            return NO_OP_TASK;
        }
        return wrap(this.plugin.getServer().getGlobalRegionScheduler().runDelayed(this.plugin, task -> runnable.run(), delayTicks));
    }

    public PluginTask runGlobalTimer(Runnable runnable, long delayTicks, long periodTicks) {
        if (runnable == null) {
            return NO_OP_TASK;
        }
        return wrap(this.plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(this.plugin, task -> runnable.run(), delayTicks, periodTicks));
    }

    public PluginTask runRegion(Location location, Runnable runnable) {
        if (location == null || location.getWorld() == null || runnable == null) {
            return NO_OP_TASK;
        }
        return wrap(this.plugin.getServer().getRegionScheduler().run(this.plugin, location, task -> runnable.run()));
    }

    public PluginTask runRegionDelayed(Location location, Runnable runnable, long delayTicks) {
        if (location == null || location.getWorld() == null || runnable == null) {
            return NO_OP_TASK;
        }
        return wrap(this.plugin.getServer().getRegionScheduler().runDelayed(this.plugin, location, task -> runnable.run(), delayTicks));
    }

    public PluginTask runRegionTimer(Location location, Runnable runnable, long delayTicks, long periodTicks) {
        if (location == null || location.getWorld() == null || runnable == null) {
            return NO_OP_TASK;
        }
        return wrap(this.plugin.getServer().getRegionScheduler().runAtFixedRate(this.plugin, location, task -> runnable.run(), delayTicks, periodTicks));
    }

    public PluginTask runEntity(Entity entity, Runnable runnable) {
        if (entity == null || runnable == null) {
            return NO_OP_TASK;
        }
        return wrap(entity.getScheduler().run(this.plugin, task -> runnable.run(), () -> {
        }));
    }

    public PluginTask runEntityDelayed(Entity entity, Runnable runnable, long delayTicks) {
        if (entity == null || runnable == null) {
            return NO_OP_TASK;
        }
        return wrap(entity.getScheduler().runDelayed(this.plugin, task -> runnable.run(), () -> {
        }, delayTicks));
    }

    public CompletableFuture<Boolean> teleport(Entity entity, Location location) {
        if (entity == null || location == null) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        return entity.teleportAsync(location);
    }

    public PluginTask removeEntity(Entity entity) {
        return this.removeEntity(entity, 0L);
    }

    public PluginTask removeEntity(Entity entity, long delayTicks) {
        if (entity == null) {
            return NO_OP_TASK;
        }
        Runnable removeTask = () -> {
            if (!entity.isDead() && entity.isValid()) {
                entity.remove();
            }
        };
        if (delayTicks <= 0L) {
            return this.runEntity(entity, removeTask);
        }
        return this.runEntityDelayed(entity, removeTask, delayTicks);
    }

    private static PluginTask wrap(ScheduledTask task) {
        return task == null ? NO_OP_TASK : new ScheduledTaskHandle(task);
    }

    private record ScheduledTaskHandle(ScheduledTask task) implements PluginTask {
        @Override
        public void cancel() {
            this.task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return this.task.isCancelled();
        }
    }
}

