package top.ellan.mahjong.gameroom;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.Location;

public record GameRoomSummary(
    String id,
    String name,
    String worldName,
    int minX,
    int minY,
    int minZ,
    int maxX,
    int maxY,
    int maxZ,
    String ownerName
) {
    public static GameRoomSummary from(GameRoom room) {
        return new GameRoomSummary(
            room.id(),
            room.name(),
            room.worldName(),
            room.minX(),
            room.minY(),
            room.minZ(),
            room.maxX(),
            room.maxY(),
            room.maxZ(),
            room.ownerId() == null ? null : room.ownerId().toString()
        );
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null || !this.worldName.equals(location.getWorld().getName())) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
    }
}
