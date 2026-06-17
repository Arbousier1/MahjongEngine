package top.ellan.mahjong.render.scene;

import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.render.display.DisplayEntities;
import top.ellan.mahjong.render.layout.TableRenderLayout;
import top.ellan.mahjong.render.TableRenderSubject;
import top.ellan.mahjong.render.snapshot.TableSeatRenderSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Renders called melds (fuuro) for a seat, including the legacy cursor-based
 * placement and the plan-based placement variants.
 */
public final class MeldRenderer {
    private MeldRenderer() {
    }

    public static List<Entity> renderMelds(TableRenderSubject session, SeatWind wind) {
        Location center = TableGeometry.displayCenter(session);
        UUID playerId = session.playerAt(wind);
        if (playerId == null) {
            return List.of();
        }

        float yaw = TableGeometry.seatYaw(wind);
        List<MeldView> melds = session.fuuro(playerId);
        if (melds.isEmpty()) {
            return List.of();
        }

        int tileCount = melds.stream().mapToInt(meld -> meld.tiles().size() + (meld.hasAddedKanTile() ? 1 : 0)).sum();
        List<Entity> spawned = new ArrayList<>(tileCount);
        Location cursor = TableGeometry.meldStart(center, wind);
        int stickCount = session.stickLayoutCount(wind);
        if (stickCount > 0) {
            cursor = TableGeometry.add(cursor, TableGeometry.cornerStickMeldOffset(wind, Math.min(stickCount, TableRenderConstants.STICKS_PER_STACK)));
        }
        boolean lastTileWasHorizontal = false;
        int placedTileCount = 0;

        for (MeldView meld : melds) {
            Location kakanStackBase = null;
            float kakanStackYaw = yaw;
            Location firstTileBase = null;
            boolean concealedKan = meld.tiles().size() == 4 && meld.faceDownAt(0) && meld.faceDownAt(meld.tiles().size() - 1);
            if (concealedKan) {
                for (int i = 0; i < meld.tiles().size(); i++) {
                    if (placedTileCount == 0) {
                        cursor = TableGeometry.add(cursor, TableGeometry.halfVerticalTileOffset(wind));
                    } else if (lastTileWasHorizontal) {
                        cursor = TableGeometry.add(cursor, TableGeometry.add(TableGeometry.halfHorizontalTileOffset(wind), TableGeometry.halfVerticalTileOffset(wind)));
                    } else {
                        cursor = TableGeometry.add(cursor, TableGeometry.verticalTileOffset(wind));
                    }
                    spawned.add(TableGeometry.spawnPublicTile(
                        session,
                        cursor.clone().add(0.0D, TableRenderConstants.FLAT_TILE_Y, 0.0D),
                        yaw,
                        meld.tiles().get(i),
                        meld.faceDownAt(i) ? DisplayEntities.TileRenderPose.FLAT_FACE_DOWN : DisplayEntities.TileRenderPose.FLAT_FACE_UP
                    ));
                    placedTileCount++;
                }
                lastTileWasHorizontal = false;
                continue;
            }

            for (int i = 0; i < meld.tiles().size(); i++) {
                boolean isClaimTile = meld.hasClaimTile() && i == meld.claimTileIndex();
                if (placedTileCount == 0) {
                    cursor = TableGeometry.add(cursor, isClaimTile ? TableGeometry.halfHorizontalTileOffset(wind) : TableGeometry.halfVerticalTileOffset(wind));
                } else if (isClaimTile || lastTileWasHorizontal) {
                    cursor = isClaimTile && lastTileWasHorizontal
                        ? TableGeometry.add(cursor, TableGeometry.horizontalTileOffset(wind))
                        : TableGeometry.add(cursor, TableGeometry.add(TableGeometry.halfHorizontalTileOffset(wind), TableGeometry.halfVerticalTileOffset(wind)));
                } else {
                    cursor = TableGeometry.add(cursor, TableGeometry.verticalTileOffset(wind));
                }

                Location baseLocation = isClaimTile ? TableGeometry.add(cursor, TableGeometry.horizontalTileGravityOffset(wind)) : cursor;
                if (firstTileBase == null) {
                    firstTileBase = baseLocation.clone();
                }
                float tileYaw = isClaimTile ? yaw + meld.claimYawOffset() : yaw;
                spawned.add(TableGeometry.spawnPublicTile(
                    session,
                    baseLocation.clone().add(0.0D, TableRenderConstants.FLAT_TILE_Y, 0.0D),
                    tileYaw,
                    meld.tiles().get(i),
                    meld.faceDownAt(i) ? DisplayEntities.TileRenderPose.FLAT_FACE_DOWN : DisplayEntities.TileRenderPose.FLAT_FACE_UP
                ));
                // Added-kan tile should follow the target (claimed) tile position.
                if (isClaimTile) {
                    kakanStackBase = baseLocation.clone();
                    kakanStackYaw = tileYaw;
                }
                lastTileWasHorizontal = isClaimTile;
                placedTileCount++;
            }

            if (meld.hasAddedKanTile() && kakanStackBase == null) {
                kakanStackBase = firstTileBase;
                kakanStackYaw = yaw;
            }
            if (meld.hasAddedKanTile() && kakanStackBase != null) {
                spawned.add(TableGeometry.spawnPublicTile(
                    session,
                    TableGeometry.add(kakanStackBase, TableGeometry.offsetTowardTableCenter(wind, TableRenderConstants.TILE_WIDTH + TableRenderConstants.TILE_PADDING)).add(0.0D, TableRenderConstants.FLAT_TILE_Y, 0.0D),
                    kakanStackYaw,
                    meld.addedKanTile(),
                    DisplayEntities.TileRenderPose.FLAT_FACE_UP
                ));
                lastTileWasHorizontal = false;
            }
        }
        return spawned;
    }

    public static List<Entity> renderMelds(
        TableRenderSubject session,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan
    ) {
        if (seat.playerId() == null) {
            return List.of();
        }
        List<Entity> spawned = new ArrayList<>(plan.meldPlacements().size());
        for (TableRenderLayout.TilePlacement placement : plan.meldPlacements()) {
            spawned.add(TableGeometry.spawnPublicTile(session, placement));
        }
        return spawned;
    }

    public static List<DisplayEntities.EntitySpec> renderMeldSpecs(
        TableRenderSubject session,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan
    ) {
        if (seat.playerId() == null) {
            return List.of();
        }
        List<DisplayEntities.EntitySpec> specs = new ArrayList<>(plan.meldPlacements().size());
        for (TableRenderLayout.TilePlacement placement : plan.meldPlacements()) {
            specs.add(TableGeometry.publicTileSpec(session, placement));
        }
        return List.copyOf(specs);
    }

    public static List<DisplayEntities.EntitySpec> renderMeldTileSpecs(
        TableRenderSubject session,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan,
        int meldIndex
    ) {
        if (seat.playerId() == null || meldIndex < 0 || meldIndex >= plan.meldPlacements().size()) {
            return List.of();
        }
        return List.of(TableGeometry.publicTileSpec(session, plan.meldPlacements().get(meldIndex)));
    }
}
