package doublemoon.mahjongcraft.paper.render;

import doublemoon.mahjongcraft.paper.model.MahjongTile;
import java.util.List;

public record MeldView(
    List<MahjongTile> tiles,
    List<Boolean> faceDownFlags,
    int claimTileIndex,
    int claimYawOffset,
    MahjongTile addedKanTile
) {
    public boolean faceDownAt(int index) {
        return index >= 0 && index < this.faceDownFlags.size() && this.faceDownFlags.get(index);
    }

    public boolean hasClaimTile() {
        return this.claimTileIndex >= 0 && this.claimTileIndex < this.tiles.size();
    }

    public boolean hasAddedKanTile() {
        return this.addedKanTile != null;
    }
}
