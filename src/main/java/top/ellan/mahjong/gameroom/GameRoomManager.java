package top.ellan.mahjong.gameroom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import top.ellan.mahjong.config.PluginSettings;
import top.ellan.mahjong.debug.DebugService;
import top.ellan.mahjong.i18n.MessageService;
import top.ellan.mahjong.runtime.ServerScheduler;
import top.ellan.mahjong.table.core.MahjongTableManager;
import top.ellan.mahjong.table.core.MahjongTableSession;

public final class GameRoomManager {
    private final MahjongTableManager tableManager;
    private final DebugService debug;
    private final ServerScheduler scheduler;
    private final MessageService messages;
    private final Supplier<PluginSettings> settingsSupplier;
    private final Path storageFile;
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerRoomMembership = new ConcurrentHashMap<>();
    private final Map<UUID, Long> exitCountdowns = new ConcurrentHashMap<>();
    private final Map<UUID, String> exitCountdownTableIds = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.Set<Integer>> warnedSeconds = new ConcurrentHashMap<>();

    public GameRoomManager(
        MahjongTableManager tableManager,
        DebugService debug,
        ServerScheduler scheduler,
        MessageService messages,
        Supplier<PluginSettings> settingsSupplier,
        Path storageFile
    ) {
        this.tableManager = Objects.requireNonNull(tableManager, "tableManager");
        this.debug = Objects.requireNonNull(debug, "debug");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.settingsSupplier = Objects.requireNonNull(settingsSupplier, "settingsSupplier");
        this.storageFile = Objects.requireNonNull(storageFile, "storageFile");
    }

    public Collection<GameRoom> rooms() {
        return List.copyOf(this.rooms.values());
    }

    public GameRoom room(String id) {
        if (id == null) {
            return null;
        }
        return this.rooms.get(GameRoom.normalizeId(id));
    }

    public Optional<GameRoom> roomAt(Location location) {
        if (location == null) {
            return Optional.empty();
        }
        for (GameRoom room : this.rooms.values()) {
            if (room.contains(location)) {
                return Optional.of(room);
            }
        }
        return Optional.empty();
    }

    public boolean isLocationInAnyRoom(Location location) {
        return this.roomAt(location).isPresent();
    }

    public GameRoom createRoom(GameRoom room) {
        Objects.requireNonNull(room, "room");
        GameRoom existing = this.rooms.putIfAbsent(room.id(), room);
        if (existing != null) {
            return existing;
        }
        this.save();
        return room;
    }

    public boolean deleteRoom(String id) {
        if (id == null) {
            return false;
        }
        GameRoom removed = this.rooms.remove(GameRoom.normalizeId(id));
        if (removed == null) {
            return false;
        }
        this.playerRoomMembership.entrySet().removeIf(entry -> removed.id().equals(entry.getValue()));
        this.exitCountdowns.keySet().removeIf(playerId -> removed.equals(this.roomFor(playerId)));
        this.exitCountdownTableIds.keySet().removeIf(playerId -> removed.equals(this.roomFor(playerId)));
        this.save();
        return true;
    }

    public void load() {
        this.rooms.clear();
        if (!Files.exists(this.storageFile)) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.storageFile.toFile());
        ConfigurationSection roomsSection = yaml.getConfigurationSection("rooms");
        if (roomsSection == null) {
            return;
        }
        for (String id : roomsSection.getKeys(false)) {
            ConfigurationSection section = roomsSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String world = section.getString("world");
            int minX = section.getInt("minX");
            int minY = section.getInt("minY");
            int minZ = section.getInt("minZ");
            int maxX = section.getInt("maxX");
            int maxY = section.getInt("maxY");
            int maxZ = section.getInt("maxZ");
            String name = section.getString("name", id);
            UUID owner = section.getString("owner") == null ? null : UUID.fromString(section.getString("owner"));
            if (world == null || world.isBlank()) {
                continue;
            }
            this.rooms.put(GameRoom.normalizeId(id), new GameRoom(id, name, world, minX, minY, minZ, maxX, maxY, maxZ, owner));
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (GameRoom room : this.rooms.values()) {
            String base = "rooms." + room.id();
            yaml.set(base + ".name", room.name());
            yaml.set(base + ".world", room.worldName());
            yaml.set(base + ".minX", room.minX());
            yaml.set(base + ".minY", room.minY());
            yaml.set(base + ".minZ", room.minZ());
            yaml.set(base + ".maxX", room.maxX());
            yaml.set(base + ".maxY", room.maxY());
            yaml.set(base + ".maxZ", room.maxZ());
            yaml.set(base + ".owner", room.ownerId() == null ? null : room.ownerId().toString());
        }
        try {
            Files.createDirectories(this.storageFile.getParent());
            yaml.save(this.storageFile.toFile());
        } catch (IOException ex) {
            this.debug.log("gameroom", "Failed to save game rooms: " + ex.getMessage());
        }
    }

    public Optional<GameRoom> roomForPlayer(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        String roomId = this.playerRoomMembership.get(playerId);
        if (roomId != null) {
            GameRoom room = this.rooms.get(roomId);
            if (room != null) {
                return Optional.of(room);
            }
        }
        return Optional.empty();
    }

    private Optional<GameRoom> roomFor(UUID playerId) {
        String roomId = this.playerRoomMembership.get(playerId);
        if (roomId != null) {
            GameRoom room = this.rooms.get(roomId);
            if (room != null) {
                return Optional.of(room);
            }
        }
        return Optional.empty();
    }

    public void updateMembership(UUID playerId, Location location) {
        if (playerId == null || location == null) {
            return;
        }
        Optional<GameRoom> currentRoom = this.roomAt(location);
        Optional<GameRoom> previousRoom = this.roomFor(playerId);

        boolean sameRoom = previousRoom.isPresent() && currentRoom.isPresent()
            && previousRoom.get().id().equals(currentRoom.get().id());
        if (sameRoom) {
            return;
        }

        // Player left a room
        if (previousRoom.isPresent() && currentRoom.isEmpty()) {
            this.playerRoomMembership.remove(playerId);
            // If player is in an active match, start countdown
            this.startExitCountdownIfNeeded(playerId);
        }

        // Player entered a room
        if (currentRoom.isPresent()) {
            this.playerRoomMembership.put(playerId, currentRoom.get().id());
            // Cancel countdown if player returns
            this.cancelExitCountdown(playerId);
        }
    }

    private void startExitCountdownIfNeeded(UUID playerId) {
        MahjongTableSession session = this.tableManager.tableFor(playerId);
        if (session == null || !session.isStarted()) {
            return;
        }
        int countdownSeconds = this.settingsSupplier.get().gameRooms().leaveCountdownSeconds();
        long deadline = System.currentTimeMillis() + countdownSeconds * 1000L;
        this.exitCountdowns.put(playerId, deadline);
        this.exitCountdownTableIds.put(playerId, session.id());

        // Send warning message
        org.bukkit.entity.Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            this.messages.send(player, "gameroom.leave_warning",
                this.messages.tag("seconds", String.valueOf(countdownSeconds)),
                this.messages.tag("room_name", this.roomFor(playerId).map(GameRoom::name).orElse(""))
            );
        }
        this.debug.log("gameroom", "Player " + playerId + " left room during match, countdown started (" + countdownSeconds + "s)");
    }

    private void cancelExitCountdown(UUID playerId) {
        Long removed = this.exitCountdowns.remove(playerId);
        this.exitCountdownTableIds.remove(playerId);
        this.warnedSeconds.remove(playerId);
        if (removed != null) {
            org.bukkit.entity.Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                this.messages.send(player, "gameroom.countdown_cancelled");
            }
            this.debug.log("gameroom", "Player " + playerId + " returned, countdown cancelled");
        }
    }

    public boolean hasActiveCountdown(UUID playerId) {
        return this.exitCountdowns.containsKey(playerId);
    }

    public Optional<GameRoom> roomForTable(MahjongTableSession table) {
        if (table == null) {
            return Optional.empty();
        }
        return this.roomAt(table.center());
    }

    public Optional<GameRoom> roomForTableLocation(Location tableCenter) {
        return this.roomAt(tableCenter);
    }

    public boolean isTableInAnyRoom(Location tableCenter) {
        return this.roomAt(tableCenter).isPresent();
    }

    public boolean isRestrictNewTables() {
        PluginSettings settings = this.settingsSupplier.get();
        return settings.gameRooms().enabled() && settings.gameRooms().restrictNewTables();
    }

    public boolean isEnterExitMessages() {
        return this.settingsSupplier.get().gameRooms().enterExitMessages();
    }

    public void tick() {
        if (this.exitCountdowns.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        var iterator = this.exitCountdowns.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            UUID playerId = entry.getKey();
            long deadline = entry.getValue();

            if (now >= deadline) {
                // Countdown expired, force end the match
                String tableId = this.exitCountdownTableIds.get(playerId);
                if (tableId != null) {
                    this.debug.log("gameroom", "Player " + playerId + " countdown expired, force-ending table " + tableId);
                    this.tableManager.forceEndTable(tableId);
                }
                iterator.remove();
                this.exitCountdownTableIds.remove(playerId);
                this.warnedSeconds.remove(playerId);
            } else {
                long remainingSeconds = (deadline - now) / 1000;
                int remaining = (int) remainingSeconds;
                if (isCountdownWarningSecond(remaining)) {
                    java.util.Set<Integer> warned = this.warnedSeconds.computeIfAbsent(playerId, k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
                    if (warned.add(remaining)) {
                        org.bukkit.entity.Player player = Bukkit.getPlayer(playerId);
                        if (player != null) {
                            this.messages.send(player, "gameroom.countdown",
                                this.messages.tag("seconds", String.valueOf(remaining))
                            );
                        }
                    }
                }
            }
        }
    }

    private static boolean isCountdownWarningSecond(int remainingSeconds) {
        if (remainingSeconds <= 0) {
            return false;
        }
        // Last 10 seconds: warn at 10, 8, 6, 5, 4, 3, 2, 1
        if (remainingSeconds <= 10) {
            return remainingSeconds == 10 || remainingSeconds == 8
                || remainingSeconds == 6 || remainingSeconds <= 5;
        }
        // Before that: every 15 seconds
        return remainingSeconds % 15 == 0;
    }

    public Map<String, GameRoom> roomMap() {
        return Map.copyOf(this.rooms);
    }
}
