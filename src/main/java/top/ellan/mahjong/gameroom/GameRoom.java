package top.ellan.mahjong.gameroom;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class GameRoom {
    private final String id;
    private final String name;
    private final String worldName;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final UUID ownerId;

    public GameRoom(String id, String name, String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, UUID ownerId) {
        this.id = normalizeId(id);
        this.name = name == null || name.isBlank() ? this.id : name;
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        this.ownerId = ownerId;
    }

    public String id() {
        return this.id;
    }

    public String name() {
        return this.name;
    }

    public String worldName() {
        return this.worldName;
    }

    public int minX() {
        return this.minX;
    }

    public int minY() {
        return this.minY;
    }

    public int minZ() {
        return this.minZ;
    }

    public int maxX() {
        return this.maxX;
    }

    public int maxY() {
        return this.maxY;
    }

    public int maxZ() {
        return this.maxZ;
    }

    public UUID ownerId() {
        return this.ownerId;
    }

    public World world() {
        return Bukkit.getWorld(this.worldName);
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!this.worldName.equals(location.getWorld().getName())) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
    }

    public boolean overlaps(GameRoom other) {
        if (other == null || !this.worldName.equals(other.worldName())) {
            return false;
        }
        return this.minX <= other.maxX() && this.maxX >= other.minX()
            && this.minY <= other.maxY() && this.maxY >= other.minY()
            && this.minZ <= other.maxZ() && this.maxZ >= other.minZ();
    }

    public String boundsSummary() {
        return this.worldName + " " + this.minX + "," + this.minY + "," + this.minZ + " -> " + this.maxX + "," + this.maxY + "," + this.maxZ;
    }

    public static GameRoom fromCorners(String id, String name, Location first, Location second, UUID ownerId) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            return null;
        }
        return new GameRoom(
            id,
            name,
            first.getWorld().getName(),
            first.getBlockX(),
            first.getBlockY(),
            first.getBlockZ(),
            second.getBlockX(),
            second.getBlockY(),
            second.getBlockZ(),
            ownerId
        );
    }

    public static GameRoom fromCenter(String id, String name, Location center, int radius, int height, UUID ownerId) {
        if (center == null || center.getWorld() == null) {
            return null;
        }
        int safeRadius = Math.max(1, radius);
        int safeHeight = Math.max(2, height);
        int minY = Math.max(center.getWorld().getMinHeight(), center.getBlockY() - 1);
        int maxY = Math.min(center.getWorld().getMaxHeight() - 1, minY + safeHeight - 1);
        return new GameRoom(
            id,
            name,
            center.getWorld().getName(),
            center.getBlockX() - safeRadius,
            minY,
            center.getBlockZ() - safeRadius,
            center.getBlockX() + safeRadius,
            maxY,
            center.getBlockZ() + safeRadius,
            ownerId
        );
    }

    public static String normalizeId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Game room id cannot be blank");
        }
        return value;
    }
}
