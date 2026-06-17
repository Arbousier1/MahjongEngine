package top.ellan.mahjong.render.scene;

import top.ellan.mahjong.model.MahjongTile;
import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.render.display.DisplayEntities;
import top.ellan.mahjong.render.layout.TableRenderLayout;
import top.ellan.mahjong.render.layout.WallLayout;
import top.ellan.mahjong.render.TableRenderSubject;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Renders the live wall, dead wall and dora indicators.
 *
 * <p>The legacy {@code renderWall(session)} / {@code renderDora(session)} methods each
 * compute the {@link TableGeometry.DeadWallRenderState} independently. When both are
 * rendered in the same pass, callers should prefer {@link #renderWallAndDora} (or pass
 * a pre-computed state to the overloads) so the dead-wall placements are computed once.
 */
public final class WallRenderer {
    private WallRenderer() {
    }

    public static List<Entity> renderWall(TableRenderSubject session) {
        if (!session.isStarted()) {
            return List.of();
        }
        return renderWall(session, TableGeometry.computeDeadWallRenderState(session));
    }

    public static List<Entity> renderWall(TableRenderSubject session, TableGeometry.DeadWallRenderState deadWallState) {
        if (!session.isStarted()) {
            return List.of();
        }
        Location center = TableGeometry.displayCenter(session);
        int liveWallCount = session.remainingWallCount();
        int kanCount = deadWallState.kanCount();
        int frontDrawCount = Math.max(0, TableRenderConstants.LIVE_WALL_SIZE - liveWallCount - kanCount);
        List<Entity> spawned = new ArrayList<>(liveWallCount + TableRenderConstants.DEAD_WALL_SIZE - session.doraIndicators().size());
        int breakTileIndex = TableGeometry.wallBreakTileIndex(session);
        for (int i = 0; i < liveWallCount; i++) {
            int wallSlot = Math.floorMod(breakTileIndex + frontDrawCount + i, TableRenderConstants.TOTAL_WALL_TILES);
            SeatWind wind = WallLayout.wallSeat(wallSlot);
            Location tileLocation = TableGeometry.wallSlotLocation(center, wallSlot);
            if (kanCount % 2 == 1 && i == liveWallCount - 1) {
                tileLocation.subtract(0.0D, TableRenderConstants.TILE_DEPTH, 0.0D);
            }
            spawned.add(TableGeometry.spawnPublicTile(session, tileLocation, TableGeometry.seatYaw(wind), MahjongTile.UNKNOWN, DisplayEntities.TileRenderPose.FLAT_FACE_DOWN));
        }

        for (int i = 0; i < TableRenderConstants.DEAD_WALL_SIZE; i++) {
            if (deadWallState.hiddenDoraSlots()[i]) {
                continue;
            }
            TableGeometry.DeadWallPlacement placement = deadWallState.placements().get(i);
            spawned.add(TableGeometry.spawnPublicTile(session, placement.location(), placement.yaw(), MahjongTile.UNKNOWN, DisplayEntities.TileRenderPose.FLAT_FACE_DOWN));
        }
        return spawned;
    }

    public static List<Entity> renderWall(TableRenderSubject session, TableRenderLayout.LayoutPlan plan) {
        if (!session.isStarted()) {
            return List.of();
        }
        List<Entity> spawned = new ArrayList<>(plan.wallTiles().size());
        for (TableRenderLayout.TilePlacement placement : plan.wallTiles()) {
            if (placement != null) {
                spawned.add(TableGeometry.spawnPublicTile(session, placement));
            }
        }
        return spawned;
    }

    public static List<Entity> renderWallTile(TableRenderSubject session, TableRenderLayout.LayoutPlan plan, int wallIndex) {
        if (!session.isStarted() || wallIndex < 0 || wallIndex >= plan.wallTiles().size()) {
            return List.of();
        }
        TableRenderLayout.TilePlacement placement = plan.wallTiles().get(wallIndex);
        return placement == null ? List.of() : List.of(TableGeometry.spawnPublicTile(session, placement));
    }

    public static List<DisplayEntities.EntitySpec> renderWallTileSpecs(TableRenderSubject session, TableRenderLayout.LayoutPlan plan, int wallIndex) {
        if (!session.isStarted() || wallIndex < 0 || wallIndex >= plan.wallTiles().size()) {
            return List.of();
        }
        TableRenderLayout.TilePlacement placement = plan.wallTiles().get(wallIndex);
        return placement == null ? List.of() : List.of(TableGeometry.publicTileSpec(session, placement));
    }

    public static List<Entity> renderDora(TableRenderSubject session) {
        if (!session.isStarted()) {
            return List.of();
        }
        return renderDora(session, TableGeometry.computeDeadWallRenderState(session));
    }

    public static List<Entity> renderDora(TableRenderSubject session, TableGeometry.DeadWallRenderState deadWallState) {
        if (!session.isStarted()) {
            return List.of();
        }
        List<MahjongTile> dora = session.doraIndicators();
        List<Entity> spawned = new ArrayList<>(dora.size());
        int kanCount = deadWallState.kanCount();
        for (int i = 0; i < dora.size(); i++) {
            TableGeometry.DeadWallPlacement placement = deadWallState.placements().get(TableGeometry.doraIndicatorDeadWallIndex(kanCount, i));
            spawned.add(TableGeometry.spawnPublicTile(session, placement.location(), placement.yaw(), dora.get(i), DisplayEntities.TileRenderPose.FLAT_FACE_UP));
        }
        return spawned;
    }

    public static List<Entity> renderDora(TableRenderSubject session, TableRenderLayout.LayoutPlan plan) {
        if (!session.isStarted()) {
            return List.of();
        }
        List<Entity> spawned = new ArrayList<>(plan.doraTiles().size());
        for (TableRenderLayout.TilePlacement placement : plan.doraTiles()) {
            spawned.add(TableGeometry.spawnPublicTile(session, placement));
        }
        return spawned;
    }

    public static List<DisplayEntities.EntitySpec> renderDoraSpecs(TableRenderSubject session, TableRenderLayout.LayoutPlan plan) {
        if (!session.isStarted()) {
            return List.of();
        }
        List<DisplayEntities.EntitySpec> specs = new ArrayList<>(plan.doraTiles().size());
        for (TableRenderLayout.TilePlacement placement : plan.doraTiles()) {
            specs.add(TableGeometry.publicTileSpec(session, placement));
        }
        return List.copyOf(specs);
    }

    /**
     * Renders both the live/dead wall and the dora indicators using a single shared
     * {@link TableGeometry.DeadWallRenderState} so the dead-wall placements are computed
     * only once per pass.
     */
    public static List<Entity> renderWallAndDora(TableRenderSubject session) {
        if (!session.isStarted()) {
            return List.of();
        }
        TableGeometry.DeadWallRenderState deadWallState = TableGeometry.computeDeadWallRenderState(session);
        List<Entity> spawned = new ArrayList<>();
        spawned.addAll(renderWall(session, deadWallState));
        spawned.addAll(renderDora(session, deadWallState));
        return spawned;
    }
}
