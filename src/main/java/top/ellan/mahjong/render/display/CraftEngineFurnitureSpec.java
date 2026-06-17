package top.ellan.mahjong.render.display;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public record CraftEngineFurnitureSpec(
    Location location,
    String furnitureItemId,
    DisplayClickAction clickAction
) implements DisplayEntities.EntitySpec {
    @Override
    public Entity spawn(DisplayEntityRuntime runtime) {
        return runtime.placeFurniture(this.location, this.furnitureItemId, this.clickAction);
    }

    @Override
    public boolean canReuse(DisplayEntityRuntime runtime, Entity entity) {
        return runtime.reconcileFurniture(entity, this.location, this.furnitureItemId, this.clickAction);
    }

    @Override
    public void apply(DisplayEntityRuntime runtime, Entity entity) {
        // CraftEngine furniture is already updated during canReuse/reconcile.
    }

    @Override
    public boolean managesOwnReuse() {
        return true;
    }
}

