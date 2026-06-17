package top.ellan.mahjong.render.scene;

import top.ellan.mahjong.render.display.DisplayEntities;
import top.ellan.mahjong.render.layout.TableRenderLayout;
import top.ellan.mahjong.render.TableRenderSubject;
import top.ellan.mahjong.render.snapshot.TableSeatRenderSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Entity;

/**
 * Renders a seat's discard pile tiles.
 */
public final class DiscardRenderer {
    private DiscardRenderer() {
    }

    public static List<Entity> renderDiscards(
        TableRenderSubject session,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan
    ) {
        if (seat.playerId() == null) {
            return List.of();
        }
        List<Entity> spawned = new ArrayList<>(plan.discardPlacements().size());
        for (TableRenderLayout.TilePlacement placement : plan.discardPlacements()) {
            spawned.add(TableGeometry.spawnPublicTile(session, placement));
        }
        return spawned;
    }

    public static List<Entity> renderDiscardTile(
        TableRenderSubject session,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan,
        int discardIndex
    ) {
        if (seat.playerId() == null || discardIndex < 0 || discardIndex >= plan.discardPlacements().size()) {
            return List.of();
        }
        return List.of(TableGeometry.spawnPublicTile(session, plan.discardPlacements().get(discardIndex)));
    }

    public static List<DisplayEntities.EntitySpec> renderDiscardTileSpecs(
        TableRenderSubject session,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan,
        int discardIndex
    ) {
        if (seat.playerId() == null || discardIndex < 0 || discardIndex >= plan.discardPlacements().size()) {
            return List.of();
        }
        return List.of(TableGeometry.publicTileSpec(session, plan.discardPlacements().get(discardIndex)));
    }
}
