package top.ellan.mahjong.gameroom;

import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import top.ellan.mahjong.config.PluginSettings;
import top.ellan.mahjong.i18n.MessageService;

public final class GameRoomListener implements Listener {
    private final Supplier<GameRoomManager> gameRoomManagerSupplier;
    private final MessageService messages;
    private final Supplier<PluginSettings> settingsSupplier;

    public GameRoomListener(Supplier<GameRoomManager> gameRoomManagerSupplier, MessageService messages, Supplier<PluginSettings> settingsSupplier) {
        this.gameRoomManagerSupplier = gameRoomManagerSupplier;
        this.messages = messages;
        this.settingsSupplier = settingsSupplier;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        // Only process if the player actually moved to a different block
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        this.handleMovement(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        this.handleMovement(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        GameRoomManager gameRoomManager = this.gameRoomManager();
        if (gameRoomManager == null || !gameRoomManager.isEnabled()) {
            return;
        }
        gameRoomManager.handlePlayerQuit(event.getPlayer().getUniqueId());
    }

    private void handleMovement(Player player, Location to) {
        GameRoomManager gameRoomManager = this.gameRoomManager();
        if (gameRoomManager == null || !gameRoomManager.isEnabled()) {
            return;
        }
        Optional<GameRoom> previousRoom = gameRoomManager.roomForPlayer(player.getUniqueId());
        gameRoomManager.updateMembership(player.getUniqueId(), to);
        Optional<GameRoom> currentRoom = gameRoomManager.roomForPlayer(player.getUniqueId());

        if (!this.shouldShowMessages()) {
            return;
        }

        // Entered a room
        if (previousRoom.isEmpty() && currentRoom.isPresent()) {
            this.messages.send(player, "gameroom.enter",
                this.messages.tag("room_name", currentRoom.get().name())
            );
        }

        // Left a room
        if (previousRoom.isPresent() && currentRoom.isEmpty()) {
            this.messages.send(player, "gameroom.exit",
                this.messages.tag("room_name", previousRoom.get().name())
            );
        }

        // Switched rooms
        if (previousRoom.isPresent() && currentRoom.isPresent() && !previousRoom.get().id().equals(currentRoom.get().id())) {
            this.messages.send(player, "gameroom.exit",
                this.messages.tag("room_name", previousRoom.get().name())
            );
            this.messages.send(player, "gameroom.enter",
                this.messages.tag("room_name", currentRoom.get().name())
            );
        }
    }

    private boolean shouldShowMessages() {
        PluginSettings settings = this.settingsSupplier.get();
        return settings.gameRooms().enabled() && settings.gameRooms().enterExitMessages();
    }

    private GameRoomManager gameRoomManager() {
        return this.gameRoomManagerSupplier == null ? null : this.gameRoomManagerSupplier.get();
    }
}
