package doublemoon.mahjongcraft.paper.render;

import doublemoon.mahjongcraft.paper.MahjongPaperPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public record CraftEngineFurnitureSpec(
    Location location,
    String furnitureItemId,
    DisplayClickAction clickAction
) implements DisplayEntities.EntitySpec {
    @Override
    public Entity spawn(Plugin plugin) {
        if (!(plugin instanceof MahjongPaperPlugin mahjongPlugin)) {
            return null;
        }
        return mahjongPlugin.craftEngine().placeFurniture(this.location, this.furnitureItemId, this.clickAction);
    }

    @Override
    public boolean canReuse(Plugin plugin, Entity entity) {
        if (!(plugin instanceof MahjongPaperPlugin mahjongPlugin)) {
            return false;
        }
        return mahjongPlugin.craftEngine().reconcileFurniture(entity, this.location, this.furnitureItemId, this.clickAction);
    }

    @Override
    public void apply(Plugin plugin, Entity entity) {
        // CraftEngine furniture is already updated during canReuse/reconcile.
    }

    @Override
    public boolean managesOwnReuse() {
        return true;
    }
}
