package top.ellan.mahjong.gameroom;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;

public final class GameRoomSelectionService {
    private final Map<UUID, Selection> selections = new HashMap<>();

    public void setFirst(UUID playerId, Location location) {
        Selection selection = this.selections.getOrDefault(playerId, new Selection(null, null));
        this.selections.put(playerId, new Selection(location == null ? null : location.clone(), selection.second()));
    }

    public void setSecond(UUID playerId, Location location) {
        Selection selection = this.selections.getOrDefault(playerId, new Selection(null, null));
        this.selections.put(playerId, new Selection(selection.first(), location == null ? null : location.clone()));
    }

    public Selection selection(UUID playerId) {
        return this.selections.get(playerId);
    }

    public void clear(UUID playerId) {
        this.selections.remove(playerId);
    }

    public record Selection(Location first, Location second) {
        public boolean complete() {
            return this.first != null && this.second != null && this.first.getWorld() != null && this.first.getWorld().equals(this.second.getWorld());
        }
    }
}
