package top.ellan.mahjong.command.subcommand;

import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.gameroom.GameRoom;
import top.ellan.mahjong.gameroom.GameRoomManager;
import top.ellan.mahjong.gameroom.GameRoomSelectionService;
import top.ellan.mahjong.gameroom.GameRoomWandListener;

public final class RoomSubcommand extends AbstractMahjongSubcommand {
    public RoomSubcommand(MahjongCommandContext context) {
        super(context);
    }

    public MahjongSubcommand create() {
        return this.subcommand("room", List.of("gameroom"), true, true);
    }

    @Override
    protected void execute(CommandSender sender, Player player, String[] args) {
        if (args.length < 2) {
            this.context.messages().send(player, "command.room_usage");
            return;
        }
        String subAction = args[1].toLowerCase(Locale.ROOT);
        switch (subAction) {
            case "create" -> this.handleCreate(player, args);
            case "delete" -> this.handleDelete(player, args);
            case "list" -> this.handleList(player);
            case "info" -> this.handleInfo(player, args);
            case "wand" -> this.handleWand(player);
            default -> this.context.messages().send(player, "command.room_usage");
        }
    }

    @Override
    protected List<String> suggest(Player player, String[] args) {
        if (args.length == 2) {
            return this.context.matchPrefix(args[1], List.of("create", "delete", "list", "info", "wand"));
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("delete")) {
            GameRoomManager manager = this.context.gameRoomManager();
            if (manager == null || !manager.isEnabled()) {
                return List.of();
            }
            return this.context.matchPrefix(args[2], manager.rooms().stream().map(GameRoom::id).toList());
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("info")) {
            GameRoomManager manager = this.context.gameRoomManager();
            if (manager == null || !manager.isEnabled()) {
                return List.of();
            }
            return this.context.matchPrefix(args[2], manager.rooms().stream().map(GameRoom::id).toList());
        }
        return List.of();
    }

    private void handleCreate(Player player, String[] args) {
        GameRoomManager manager = this.context.gameRoomManager();
        if (manager == null || !manager.isEnabled()) {
            this.context.messages().send(player, "command.room_disabled");
            return;
        }
        if (args.length < 3) {
            this.context.messages().send(player, "command.room_create_usage");
            return;
        }
        String id = args[2];
        String name = args.length >= 4 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : id;

        // Try selection-based creation first
        GameRoomSelectionService selectionService = this.context.selectionService();
        if (selectionService != null) {
            GameRoomSelectionService.Selection selection = selectionService.selection(player.getUniqueId());
            if (selection != null && selection.complete()) {
                GameRoom room = GameRoom.fromCorners(id, name, selection.first(), selection.second(), player.getUniqueId());
                if (room == null) {
                    this.context.messages().send(player, "command.room_create_invalid_selection");
                    return;
                }
                GameRoom existing = manager.createRoom(room);
                if (existing != room) {
                    this.context.messages().send(player, "command.room_create_exists",
                        this.context.messages().tag("room_id", id)
                    );
                    return;
                }
                selectionService.clear(player.getUniqueId());
                this.context.messages().send(player, "command.room_create_success",
                    this.context.messages().tag("room_id", room.id()),
                    this.context.messages().tag("room_name", room.name()),
                    this.context.messages().tag("bounds", room.boundsSummary())
                );
                return;
            }
        }

        // Fallback: center-radius creation at player location
        GameRoom room = GameRoom.fromCenter(
            id,
            name,
            player.getLocation(),
            manager.defaultRadius(),
            manager.defaultHeight(),
            player.getUniqueId()
        );
        if (room == null) {
            this.context.messages().send(player, "command.room_create_failed");
            return;
        }
        GameRoom existing = manager.createRoom(room);
        if (existing != room) {
            this.context.messages().send(player, "command.room_create_exists",
                this.context.messages().tag("room_id", id)
            );
            return;
        }
        this.context.messages().send(player, "command.room_create_success_center",
            this.context.messages().tag("room_id", room.id()),
            this.context.messages().tag("room_name", room.name()),
            this.context.messages().tag("bounds", room.boundsSummary())
        );
    }

    private void handleDelete(Player player, String[] args) {
        GameRoomManager manager = this.context.gameRoomManager();
        if (manager == null || !manager.isEnabled()) {
            this.context.messages().send(player, "command.room_disabled");
            return;
        }
        if (args.length < 3) {
            this.context.messages().send(player, "command.room_delete_usage");
            return;
        }
        String id = args[2];
        boolean deleted = manager.deleteRoom(id);
        if (deleted) {
            this.context.messages().send(player, "command.room_delete_success",
                this.context.messages().tag("room_id", id)
            );
        } else {
            this.context.messages().send(player, "command.room_not_found",
                this.context.messages().tag("room_id", id)
            );
        }
    }

    private void handleList(Player player) {
        GameRoomManager manager = this.context.gameRoomManager();
        if (manager == null || !manager.isEnabled()) {
            this.context.messages().send(player, "command.room_disabled");
            return;
        }
        var rooms = manager.rooms();
        if (rooms.isEmpty()) {
            this.context.messages().send(player, "command.room_list_empty");
            return;
        }
        this.context.messages().send(player, "command.room_list_header",
            this.context.messages().tag("count", String.valueOf(rooms.size()))
        );
        for (GameRoom room : rooms) {
            this.context.messages().send(player, "command.room_list_entry",
                this.context.messages().tag("room_id", room.id()),
                this.context.messages().tag("room_name", room.name()),
                this.context.messages().tag("bounds", room.boundsSummary())
            );
        }
    }

    private void handleInfo(Player player, String[] args) {
        GameRoomManager manager = this.context.gameRoomManager();
        if (manager == null || !manager.isEnabled()) {
            this.context.messages().send(player, "command.room_disabled");
            return;
        }
        if (args.length < 3) {
            // Show info for the room the player is currently in
            var currentRoom = manager.roomAt(player.getLocation());
            if (currentRoom.isEmpty()) {
                this.context.messages().send(player, "command.room_info_usage");
                return;
            }
            this.sendRoomInfo(player, currentRoom.get());
            return;
        }
        GameRoom room = manager.room(args[2]);
        if (room == null) {
            this.context.messages().send(player, "command.room_not_found",
                this.context.messages().tag("room_id", args[2])
            );
            return;
        }
        this.sendRoomInfo(player, room);
    }

    private void sendRoomInfo(Player player, GameRoom room) {
        this.context.messages().send(player, "command.room_info_header",
            this.context.messages().tag("room_id", room.id()),
            this.context.messages().tag("room_name", room.name())
        );
        this.context.messages().send(player, "command.room_info_bounds",
            this.context.messages().tag("world", room.worldName()),
            this.context.messages().tag("min_x", String.valueOf(room.minX())),
            this.context.messages().tag("min_y", String.valueOf(room.minY())),
            this.context.messages().tag("min_z", String.valueOf(room.minZ())),
            this.context.messages().tag("max_x", String.valueOf(room.maxX())),
            this.context.messages().tag("max_y", String.valueOf(room.maxY())),
            this.context.messages().tag("max_z", String.valueOf(room.maxZ()))
        );
        int sizeX = room.maxX() - room.minX() + 1;
        int sizeY = room.maxY() - room.minY() + 1;
        int sizeZ = room.maxZ() - room.minZ() + 1;
        this.context.messages().send(player, "command.room_info_size",
            this.context.messages().tag("x", String.valueOf(sizeX)),
            this.context.messages().tag("y", String.valueOf(sizeY)),
            this.context.messages().tag("z", String.valueOf(sizeZ))
        );
        if (room.ownerId() != null) {
            this.context.messages().send(player, "command.room_info_owner",
                this.context.messages().tag("owner", room.ownerId().toString())
            );
        }
    }

    private void handleWand(Player player) {
        GameRoomManager manager = this.context.gameRoomManager();
        if (manager == null || !manager.isEnabled()) {
            this.context.messages().send(player, "command.room_disabled");
            return;
        }
        player.getInventory().addItem(GameRoomWandListener.createWand());
        this.context.messages().send(player, "command.room_wand_given");
    }
}
