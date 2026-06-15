package top.ellan.mahjong.compat;

import top.ellan.mahjong.render.display.DisplayClickAction;
import top.ellan.mahjong.render.display.TableDisplayRegistry;
import top.ellan.mahjong.table.core.MahjongTableManager;
import java.lang.reflect.Method;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

final class CraftEngineInteractionBridge {
    private final CraftEngineBridgeContext context;
    private final CraftEngineFurnitureBridge furnitureBridge;
    private Listener furnitureInteractListener;

    CraftEngineInteractionBridge(CraftEngineBridgeContext context, CraftEngineFurnitureBridge furnitureBridge) {
        this.context = context;
        this.furnitureBridge = furnitureBridge;
    }

    void enableFurnitureInteractionBridge(MahjongTableManager tableManager) {
        if (this.furnitureInteractListener != null) {
            return;
        }
        Plugin craftEngine = this.context.craftEnginePlugin();
        if (craftEngine == null || !craftEngine.isEnabled()) {
            this.context.plugin().getLogger().warning("CraftEngine interaction bridge is unavailable because CraftEngine is not enabled.");
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
            this.context.plugin().getServer().getPluginManager().registerEvent(
                interactEventClass,
                listener,
                EventPriority.NORMAL,
                interactExecutor,
                this.context.bukkitPlugin(),
                true
            );
            this.context.plugin().getServer().getPluginManager().registerEvent(
                breakEventClass,
                listener,
                EventPriority.HIGHEST,
                breakProtectionExecutor,
                this.context.bukkitPlugin(),
                true
            );
            this.context.plugin().getServer().getPluginManager().registerEvent(
                hitEventClass,
                listener,
                EventPriority.HIGHEST,
                hitProtectionExecutor,
                this.context.bukkitPlugin(),
                true
            );
            this.furnitureInteractListener = listener;
        } catch (ReflectiveOperationException exception) {
            this.context.plugin().getLogger().warning(
                "CraftEngine was detected, but MahjongPaper could not register the furniture bridge. CraftEngine interactions or protection may be unavailable."
            );
            this.context.plugin().debug().log(
                "lifecycle",
                "CraftEngine furniture bridge registration failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    void disableFurnitureInteractionBridge() {
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
                    this.context.plugin().messages().actionBar(player, "packet.cannot_click_tile");
                } else {
                    this.context.plugin().messages().actionBar(player, "command.join_failed");
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
            if (this.furnitureBridge.isManagedFurnitureEntity(entity) && event instanceof Cancellable cancellable) {
                cancellable.setCancelled(true);
            }
        } catch (ReflectiveOperationException exception) {
            throw new EventException(exception);
        }
    }
}
