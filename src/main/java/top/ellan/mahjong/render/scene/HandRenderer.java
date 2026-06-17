package top.ellan.mahjong.render.scene;

import top.ellan.mahjong.model.MahjongTile;
import top.ellan.mahjong.render.display.DisplayClickAction;
import top.ellan.mahjong.render.display.DisplayEntities;
import top.ellan.mahjong.render.layout.TableRenderLayout;
import top.ellan.mahjong.render.TableRenderSubject;
import top.ellan.mahjong.render.snapshot.TableRenderSnapshot;
import top.ellan.mahjong.render.snapshot.TableSeatRenderSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Renders the private (owner-only) and public hand tiles, including click hitboxes.
 */
public final class HandRenderer {
    private HandRenderer() {
    }

    public static List<Entity> renderHandPrivate(
        TableRenderSubject session,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan
    ) {
        if (seat.playerId() == null) {
            return List.of();
        }

        List<UUID> ownerOnly = List.of(seat.playerId());
        List<Entity> spawned = new ArrayList<>(seat.hand().size() * 2);
        for (int tileIndex = 0; tileIndex < seat.hand().size(); tileIndex++) {
            Location tileLocation = TableGeometry.toLocation(session, plan.privateHandPoints().get(tileIndex));
            spawned.add(DisplayEntities.spawnTileDisplay(
                session.bukkitPlugin(),
                tileLocation,
                plan.yaw(),
                session.currentVariant(),
                seat.hand().get(tileIndex),
                DisplayEntities.TileRenderPose.STANDING,
                null,
                true,
                ownerOnly,
                1.0F,
                null,
                null,
                true
            ));
            Entity clickHitbox = DisplayEntities.spawnInteraction(
                session.bukkitPlugin(),
                handInteractionLocation(tileLocation),
                TableRenderConstants.HAND_INTERACTION_WIDTH,
                TableRenderConstants.HAND_INTERACTION_HEIGHT,
                DisplayClickAction.handTile(session.id(), seat.playerId(), tileIndex),
                ownerOnly
            );
            if (clickHitbox != null) {
                spawned.add(clickHitbox);
            }
        }
        return spawned;
    }

    public static List<Entity> renderHandPrivateTile(
        TableRenderSubject session,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan,
        int tileIndex
    ) {
        if (seat.playerId() == null || tileIndex < 0 || tileIndex >= seat.hand().size()) {
            return List.of();
        }

        Location tileLocation = TableGeometry.toLocation(session, plan.privateHandPoints().get(tileIndex));
        UUID playerId = seat.playerId();
        List<Entity> spawned = new ArrayList<>(2);
        spawned.add(DisplayEntities.spawnTileDisplay(
            session.bukkitPlugin(),
            tileLocation,
            plan.yaw(),
            session.currentVariant(),
            seat.hand().get(tileIndex),
            DisplayEntities.TileRenderPose.STANDING,
            null,
            true,
            List.of(playerId),
            1.0F,
            null,
            null,
            true
        ));
        Entity clickHitbox = DisplayEntities.spawnInteraction(
            session.bukkitPlugin(),
            handInteractionLocation(tileLocation),
            TableRenderConstants.HAND_INTERACTION_WIDTH,
            TableRenderConstants.HAND_INTERACTION_HEIGHT,
            DisplayClickAction.handTile(session.id(), playerId, tileIndex),
            List.of(playerId)
        );
        if (clickHitbox != null) {
            spawned.add(clickHitbox);
        }
        return spawned;
    }

    public static List<DisplayEntities.EntitySpec> renderHandPrivateTileSpecs(
        TableRenderSubject session,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan,
        int tileIndex
    ) {
        if (seat.playerId() == null || tileIndex < 0 || tileIndex >= seat.hand().size()) {
            return List.of();
        }

        List<UUID> ownerOnly = List.of(seat.playerId());
        Location tileLocation = TableGeometry.toLocation(session, plan.privateHandPoints().get(tileIndex));
        DisplayEntities.EntitySpec tileSpec = DisplayEntities.tileDisplaySpec(
            tileLocation,
            plan.yaw(),
            session.currentVariant(),
            seat.hand().get(tileIndex),
            DisplayEntities.TileRenderPose.STANDING,
            null,
            true,
            ownerOnly,
            1.0F,
            null,
            null,
            true
        );
        return List.of(
            tileSpec,
            DisplayEntities.interactionSpec(
                handInteractionLocation(tileLocation),
                TableRenderConstants.HAND_INTERACTION_WIDTH,
                TableRenderConstants.HAND_INTERACTION_HEIGHT,
                DisplayClickAction.handTile(session.id(), seat.playerId(), tileIndex),
                ownerOnly
            )
        );
    }

    public static List<Entity> renderHandPublic(
        TableRenderSubject session,
        TableRenderSnapshot snapshot,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan
    ) {
        if (seat.playerId() == null) {
            return List.of();
        }

        List<Entity> spawned = new ArrayList<>(seat.hand().size());
        boolean concealHand = snapshot.started();
        List<UUID> ownerHidden = List.of(seat.playerId());
        for (int i = 0; i < seat.hand().size(); i++) {
            spawned.add(DisplayEntities.spawnTileDisplay(
                session.bukkitPlugin(),
                TableGeometry.toLocation(session, plan.publicHandPoints().get(i)),
                plan.yaw(),
                session.currentVariant(),
                concealHand ? MahjongTile.UNKNOWN : seat.hand().get(i),
                DisplayEntities.TileRenderPose.STANDING,
                null,
                true,
                null,
                ownerHidden
            ));
        }
        return spawned;
    }

    public static List<Entity> renderHandPublicTile(
        TableRenderSubject session,
        TableRenderSnapshot snapshot,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan,
        int tileIndex
    ) {
        if (seat.playerId() == null || tileIndex < 0 || tileIndex >= seat.hand().size()) {
            return List.of();
        }

        boolean concealHand = snapshot.started();
        List<UUID> ownerHidden = List.of(seat.playerId());
        return List.of(DisplayEntities.spawnTileDisplay(
            session.bukkitPlugin(),
            TableGeometry.toLocation(session, plan.publicHandPoints().get(tileIndex)),
            plan.yaw(),
            session.currentVariant(),
            concealHand ? MahjongTile.UNKNOWN : seat.hand().get(tileIndex),
            DisplayEntities.TileRenderPose.STANDING,
            null,
            true,
            null,
            ownerHidden
        ));
    }

    public static List<DisplayEntities.EntitySpec> renderHandPublicTileSpecs(
        TableRenderSubject session,
        TableRenderSnapshot snapshot,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan,
        int tileIndex
    ) {
        if (seat.playerId() == null || tileIndex < 0 || tileIndex >= seat.hand().size()) {
            return List.of();
        }

        boolean concealHand = snapshot.started();
        List<UUID> ownerHidden = List.of(seat.playerId());
        return List.of(DisplayEntities.tileDisplaySpec(
            TableGeometry.toLocation(session, plan.publicHandPoints().get(tileIndex)),
            plan.yaw(),
            session.currentVariant(),
            concealHand ? MahjongTile.UNKNOWN : seat.hand().get(tileIndex),
            DisplayEntities.TileRenderPose.STANDING,
            null,
            true,
            null,
            ownerHidden
        ));
    }

    private static Location handInteractionLocation(Location tileLocation) {
        return tileLocation.clone().subtract(0.0D, TableRenderConstants.UPRIGHT_TILE_Y, 0.0D);
    }
}
