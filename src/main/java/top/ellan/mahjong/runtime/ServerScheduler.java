package top.ellan.mahjong.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

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
        Object scheduler = this.globalRegionScheduler();
        if (scheduler != null) {
            PluginTask task = this.invokeSchedulerTask(
                scheduler,
                "run",
                new Class<?>[] {Plugin.class, Consumer.class},
                this.plugin,
                taskConsumer(runnable)
            );
            if (task != null) {
                return task;
            }
        }
        return wrap(this.plugin.getServer().getScheduler().runTask(this.plugin, runnable));
    }

    public PluginTask runGlobalDelayed(Runnable runnable, long delayTicks) {
        if (runnable == null) {
            return NO_OP_TASK;
        }
        Object scheduler = this.globalRegionScheduler();
        if (scheduler != null) {
            PluginTask task = this.invokeSchedulerTask(
                scheduler,
                "runDelayed",
                new Class<?>[] {Plugin.class, Consumer.class, long.class},
                this.plugin,
                taskConsumer(runnable),
                delayTicks
            );
            if (task != null) {
                return task;
            }
        }
        return wrap(this.plugin.getServer().getScheduler().runTaskLater(this.plugin, runnable, delayTicks));
    }

    public PluginTask runGlobalTimer(Runnable runnable, long delayTicks, long periodTicks) {
        if (runnable == null) {
            return NO_OP_TASK;
        }
        Object scheduler = this.globalRegionScheduler();
        if (scheduler != null) {
            PluginTask task = this.invokeSchedulerTask(
                scheduler,
                "runAtFixedRate",
                new Class<?>[] {Plugin.class, Consumer.class, long.class, long.class},
                this.plugin,
                taskConsumer(runnable),
                delayTicks,
                periodTicks
            );
            if (task != null) {
                return task;
            }
        }
        return wrap(this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, runnable, delayTicks, periodTicks));
    }

    public PluginTask runRegion(Location location, Runnable runnable) {
        if (location == null || location.getWorld() == null || runnable == null) {
            return NO_OP_TASK;
        }
        Object scheduler = this.regionScheduler();
        if (scheduler != null) {
            PluginTask task = this.invokeSchedulerTask(
                scheduler,
                "run",
                new Class<?>[] {Plugin.class, Location.class, Consumer.class},
                this.plugin,
                location,
                taskConsumer(runnable)
            );
            if (task != null) {
                return task;
            }
        }
        return this.runGlobal(runnable);
    }

    public PluginTask runRegionDelayed(Location location, Runnable runnable, long delayTicks) {
        if (location == null || location.getWorld() == null || runnable == null) {
            return NO_OP_TASK;
        }
        Object scheduler = this.regionScheduler();
        if (scheduler != null) {
            PluginTask task = this.invokeSchedulerTask(
                scheduler,
                "runDelayed",
                new Class<?>[] {Plugin.class, Location.class, Consumer.class, long.class},
                this.plugin,
                location,
                taskConsumer(runnable),
                delayTicks
            );
            if (task != null) {
                return task;
            }
        }
        return this.runGlobalDelayed(runnable, delayTicks);
    }

    public PluginTask runRegionTimer(Location location, Runnable runnable, long delayTicks, long periodTicks) {
        if (location == null || location.getWorld() == null || runnable == null) {
            return NO_OP_TASK;
        }
        Object scheduler = this.regionScheduler();
        if (scheduler != null) {
            PluginTask task = this.invokeSchedulerTask(
                scheduler,
                "runAtFixedRate",
                new Class<?>[] {Plugin.class, Location.class, Consumer.class, long.class, long.class},
                this.plugin,
                location,
                taskConsumer(runnable),
                delayTicks,
                periodTicks
            );
            if (task != null) {
                return task;
            }
        }
        return this.runGlobalTimer(runnable, delayTicks, periodTicks);
    }

    public PluginTask runEntity(Entity entity, Runnable runnable) {
        if (entity == null || runnable == null) {
            return NO_OP_TASK;
        }
        Object scheduler = this.entityScheduler(entity);
        if (scheduler != null) {
            PluginTask task = this.invokeSchedulerTask(
                scheduler,
                "run",
                new Class<?>[] {Plugin.class, Consumer.class, Runnable.class},
                this.plugin,
                taskConsumer(runnable),
                NO_OP_RUNNABLE
            );
            if (task != null) {
                return task;
            }
        }
        return this.runGlobal(runnable);
    }

    public PluginTask runEntityDelayed(Entity entity, Runnable runnable, long delayTicks) {
        if (entity == null || runnable == null) {
            return NO_OP_TASK;
        }
        Object scheduler = this.entityScheduler(entity);
        if (scheduler != null) {
            PluginTask task = this.invokeSchedulerTask(
                scheduler,
                "runDelayed",
                new Class<?>[] {Plugin.class, Consumer.class, Runnable.class, long.class},
                this.plugin,
                taskConsumer(runnable),
                NO_OP_RUNNABLE,
                delayTicks
            );
            if (task != null) {
                return task;
            }
        }
        return this.runGlobalDelayed(runnable, delayTicks);
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> teleport(Entity entity, Location location) {
        if (entity == null || location == null) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        try {
            Method teleportAsync = entity.getClass().getMethod("teleportAsync", Location.class);
            Object result = teleportAsync.invoke(entity, location);
            if (result instanceof CompletableFuture<?> future) {
                return (CompletableFuture<Boolean>) future;
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            // Fall back to the Bukkit teleport API on older Paper builds.
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        this.runEntity(entity, () -> {
            try {
                future.complete(entity.teleport(location));
            } catch (RuntimeException exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
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

    private Object globalRegionScheduler() {
        return this.invokeNoArgs(this.plugin.getServer(), "getGlobalRegionScheduler");
    }

    private Object regionScheduler() {
        return this.invokeNoArgs(this.plugin.getServer(), "getRegionScheduler");
    }

    private Object entityScheduler(Entity entity) {
        return this.invokeNoArgs(entity, "getScheduler");
    }

    private Object invokeNoArgs(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            return null;
        }
    }

    private PluginTask invokeSchedulerTask(Object scheduler, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = scheduler.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return wrap(method.invoke(scheduler, args));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            return null;
        }
    }

    private static Consumer<Object> taskConsumer(Runnable runnable) {
        return task -> runnable.run();
    }

    private static PluginTask wrap(BukkitTask task) {
        return task == null ? NO_OP_TASK : new BukkitTaskHandle(task);
    }

    private static PluginTask wrap(Object task) {
        return task == null ? NO_OP_TASK : new ScheduledTaskHandle(task);
    }

    private record BukkitTaskHandle(BukkitTask task) implements PluginTask {
        @Override
        public void cancel() {
            this.task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return this.task.isCancelled();
        }
    }

    private record ScheduledTaskHandle(Object task) implements PluginTask {
        @Override
        public void cancel() {
            invokeTaskMethod(this.task, "cancel");
        }

        @Override
        public boolean isCancelled() {
            Object result = invokeTaskMethod(this.task, "isCancelled");
            return result instanceof Boolean cancelled && cancelled;
        }
    }

    private static Object invokeTaskMethod(Object task, String methodName) {
        if (task == null) {
            return null;
        }
        try {
            Method method = task.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(task);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            return null;
        }
    }

    private static final Runnable NO_OP_RUNNABLE = () -> {
    };
}

