package doublemoon.mahjongcraft.paper.render;

import doublemoon.mahjongcraft.paper.model.MahjongTile;
import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.table.MahjongTableSession;
import doublemoon.mahjongcraft.paper.riichi.model.ScoringStick;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;

public final class TableRenderer {
    private static final double ONE_SIXTEENTH = 1.0D / 16.0D;
    private static final double TILE_WIDTH = 0.1125D;
    private static final double TILE_HEIGHT = 0.15D;
    private static final double TILE_DEPTH = 0.075D;
    private static final double TILE_PADDING = 0.0025D;
    private static final double STICK_WIDTH = 0.4D;
    private static final double STICK_HEIGHT = 0.0125D;
    private static final double STICK_DEPTH = 0.0625D;
    private static final int STICKS_PER_STACK = 5;
    private static final double TABLE_BOTTOM_SIZE = 14.0D * ONE_SIXTEENTH;
    private static final double TABLE_BOTTOM_HEIGHT = 2.0D * ONE_SIXTEENTH;
    private static final double TABLE_PILLAR_SIZE = 8.0D * ONE_SIXTEENTH;
    private static final double TABLE_PILLAR_HEIGHT = 12.0D * ONE_SIXTEENTH;
    private static final double TABLE_TOP_SIZE = 46.0D * ONE_SIXTEENTH;
    private static final double TABLE_TOP_THICKNESS = 2.0D * ONE_SIXTEENTH;
    private static final double TABLE_BORDER_THICKNESS = ONE_SIXTEENTH;
    private static final double TABLE_BORDER_HEIGHT = 3.0D * ONE_SIXTEENTH;
    private static final double DISPLAY_CENTER_Y_OFFSET = 0.52D;
    private static final double TABLE_VISUAL_Y_OFFSET = 0.5D;
    private static final double FLOATING_TEXT_Y_OFFSET = 1.0D;
    private static final double WALL_DIRECTION_OFFSET = 1.0D;
    private static final double HAND_DIRECTION_OFFSET = WALL_DIRECTION_OFFSET + TILE_DEPTH + TILE_HEIGHT;
    private static final double HALF_TABLE_LENGTH_NO_BORDER = 0.5D + 15.0D / 16.0D;
    private static final double DEAD_WALL_GAP = TILE_PADDING * 20.0D;
    private static final double WALL_TILE_STEP = TILE_WIDTH + TILE_PADDING;
    private static final double UPRIGHT_TILE_Y = TILE_HEIGHT / 2.0D;
    private static final double FLAT_TILE_Y = TILE_DEPTH / 2.0D;
    private static final float HAND_INTERACTION_WIDTH = 0.14F;
    private static final float HAND_INTERACTION_HEIGHT = 0.18F;
    private static final float SEAT_INTERACTION_WIDTH = 0.8F;
    private static final float SEAT_INTERACTION_HEIGHT = 0.8F;
    private static final int WALL_TILES_PER_SIDE = 34;
    private static final int TOTAL_WALL_TILES = 136;
    private static final int DEAD_WALL_SIZE = 14;
    private static final int LIVE_WALL_SIZE = TOTAL_WALL_TILES - DEAD_WALL_SIZE;
    private static final int DISCARDS_PER_ROW = 6;
    private static final String TABLE_VISUAL_FURNITURE_ID = "mahjongpaper:table_visual";
    private static final String STICK_FURNITURE_PREFIX = "mahjongpaper:stick_";

    public List<Entity> renderTableStructure(MahjongTableSession session) {
        Location center = displayCenter(session);
        TableBounds bounds = tableBoundsFromTiles(center);
        List<Entity> spawned = new ArrayList<>(16);
        Location tableCenter = center.clone().set(bounds.centerX(), center.getY(), bounds.centerZ());
        double topWidth = bounds.width();
        double topDepth = bounds.depth();
        double borderSpanX = topWidth + TABLE_BORDER_THICKNESS;
        double borderSpanZ = topDepth + TABLE_BORDER_THICKNESS;
        double borderCenterOffsetX = topWidth / 2.0D + TABLE_BORDER_THICKNESS / 2.0D;
        double borderCenterOffsetZ = topDepth / 2.0D + TABLE_BORDER_THICKNESS / 2.0D;
        Entity tableVisual = spawnTableVisual(session, tableCenter);
        if (tableVisual != null) {
            spawned.add(tableVisual);
            spawned.addAll(this.renderTableHitboxes(session, tableCenter));
            return spawned;
        }

        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(tableCenter.clone().add(0.0D, -1.0D, 0.0D), TABLE_BOTTOM_SIZE, TABLE_BOTTOM_HEIGHT, TABLE_BOTTOM_SIZE),
            Material.DARK_OAK_WOOD,
            (float) TABLE_BOTTOM_SIZE,
            (float) TABLE_BOTTOM_HEIGHT,
            (float) TABLE_BOTTOM_SIZE
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(tableCenter.clone().add(0.0D, -(TABLE_TOP_THICKNESS + TABLE_PILLAR_HEIGHT), 0.0D), TABLE_PILLAR_SIZE, TABLE_PILLAR_HEIGHT, TABLE_PILLAR_SIZE),
            Material.DARK_OAK_WOOD,
            (float) TABLE_PILLAR_SIZE,
            (float) TABLE_PILLAR_HEIGHT,
            (float) TABLE_PILLAR_SIZE
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(tableCenter.clone().add(0.0D, -TABLE_TOP_THICKNESS, 0.0D), topWidth, TABLE_TOP_THICKNESS, topDepth),
            Material.SMOOTH_STONE,
            (float) topWidth,
            (float) TABLE_TOP_THICKNESS,
            (float) topDepth
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(tableCenter.clone().add(0.0D, -TABLE_TOP_THICKNESS, -borderCenterOffsetZ), borderSpanX, TABLE_BORDER_HEIGHT, TABLE_BORDER_THICKNESS),
            Material.STRIPPED_OAK_WOOD,
            (float) borderSpanX,
            (float) TABLE_BORDER_HEIGHT,
            (float) TABLE_BORDER_THICKNESS
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(tableCenter.clone().add(0.0D, -TABLE_TOP_THICKNESS, borderCenterOffsetZ), borderSpanX, TABLE_BORDER_HEIGHT, TABLE_BORDER_THICKNESS),
            Material.STRIPPED_OAK_WOOD,
            (float) borderSpanX,
            (float) TABLE_BORDER_HEIGHT,
            (float) TABLE_BORDER_THICKNESS
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(tableCenter.clone().add(-borderCenterOffsetX, -TABLE_TOP_THICKNESS, 0.0D), TABLE_BORDER_THICKNESS, TABLE_BORDER_HEIGHT, borderSpanZ),
            Material.STRIPPED_OAK_WOOD,
            (float) TABLE_BORDER_THICKNESS,
            (float) TABLE_BORDER_HEIGHT,
            (float) borderSpanZ
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(tableCenter.clone().add(borderCenterOffsetX, -TABLE_TOP_THICKNESS, 0.0D), TABLE_BORDER_THICKNESS, TABLE_BORDER_HEIGHT, borderSpanZ),
            Material.STRIPPED_OAK_WOOD,
            (float) TABLE_BORDER_THICKNESS,
            (float) TABLE_BORDER_HEIGHT,
            (float) borderSpanZ
        ));
        spawned.addAll(this.renderTableHitboxes(session, tableCenter));
        return spawned;
    }

    public List<Entity> renderTableStructure(MahjongTableSession session, TableRenderLayout.LayoutPlan plan) {
        List<Entity> spawned = new ArrayList<>(16);
        Location tableCenter = toLocation(session, plan.tableCenter());
        double topWidth = plan.borderSpanX() - TABLE_BORDER_THICKNESS;
        double topDepth = plan.borderSpanZ() - TABLE_BORDER_THICKNESS;
        double borderSpanX = plan.borderSpanX();
        double borderSpanZ = plan.borderSpanZ();
        double borderCenterOffsetX = topWidth / 2.0D + TABLE_BORDER_THICKNESS / 2.0D;
        double borderCenterOffsetZ = topDepth / 2.0D + TABLE_BORDER_THICKNESS / 2.0D;
        Entity tableVisual = spawnTableVisual(session, tableCenter);
        if (tableVisual != null) {
            spawned.add(tableVisual);
            spawned.addAll(this.renderTableHitboxes(session, tableCenter));
            return spawned;
        }

        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(tableCenter.clone().add(0.0D, -1.0D, 0.0D), TABLE_BOTTOM_SIZE, TABLE_BOTTOM_HEIGHT, TABLE_BOTTOM_SIZE),
            Material.DARK_OAK_WOOD,
            (float) TABLE_BOTTOM_SIZE,
            (float) TABLE_BOTTOM_HEIGHT,
            (float) TABLE_BOTTOM_SIZE
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(tableCenter.clone().add(0.0D, -(TABLE_TOP_THICKNESS + TABLE_PILLAR_HEIGHT), 0.0D), TABLE_PILLAR_SIZE, TABLE_PILLAR_HEIGHT, TABLE_PILLAR_SIZE),
            Material.DARK_OAK_WOOD,
            (float) TABLE_PILLAR_SIZE,
            (float) TABLE_PILLAR_HEIGHT,
            (float) TABLE_PILLAR_SIZE
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(tableCenter.clone().add(0.0D, -TABLE_TOP_THICKNESS, 0.0D), topWidth, TABLE_TOP_THICKNESS, topDepth),
            Material.SMOOTH_STONE,
            (float) topWidth,
            (float) TABLE_TOP_THICKNESS,
            (float) topDepth
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(tableCenter.clone().add(0.0D, -TABLE_TOP_THICKNESS, -borderCenterOffsetZ), borderSpanX, TABLE_BORDER_HEIGHT, TABLE_BORDER_THICKNESS),
            Material.STRIPPED_OAK_WOOD,
            (float) borderSpanX,
            (float) TABLE_BORDER_HEIGHT,
            (float) TABLE_BORDER_THICKNESS
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(tableCenter.clone().add(0.0D, -TABLE_TOP_THICKNESS, borderCenterOffsetZ), borderSpanX, TABLE_BORDER_HEIGHT, TABLE_BORDER_THICKNESS),
            Material.STRIPPED_OAK_WOOD,
            (float) borderSpanX,
            (float) TABLE_BORDER_HEIGHT,
            (float) TABLE_BORDER_THICKNESS
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(tableCenter.clone().add(-borderCenterOffsetX, -TABLE_TOP_THICKNESS, 0.0D), TABLE_BORDER_THICKNESS, TABLE_BORDER_HEIGHT, borderSpanZ),
            Material.STRIPPED_OAK_WOOD,
            (float) TABLE_BORDER_THICKNESS,
            (float) TABLE_BORDER_HEIGHT,
            (float) borderSpanZ
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(tableCenter.clone().add(borderCenterOffsetX, -TABLE_TOP_THICKNESS, 0.0D), TABLE_BORDER_THICKNESS, TABLE_BORDER_HEIGHT, borderSpanZ),
            Material.STRIPPED_OAK_WOOD,
            (float) TABLE_BORDER_THICKNESS,
            (float) TABLE_BORDER_HEIGHT,
            (float) borderSpanZ
        ));
        spawned.addAll(this.renderTableHitboxes(session, tableCenter));
        return spawned;
    }

    public TableDiagnostics inspectTable(MahjongTableSession session) {
        Location center = displayCenter(session);
        TableBounds bounds = tableBoundsFromTiles(center);
        Location tableCenter = center.clone().set(bounds.centerX(), center.getY(), bounds.centerZ());
        double borderSpanX = bounds.width() + TABLE_BORDER_THICKNESS;
        double borderSpanZ = bounds.depth() + TABLE_BORDER_THICKNESS;
        return new TableDiagnostics(
            center,
            tableCenter,
            tableVisualAnchor(tableCenter),
            borderSpanX,
            borderSpanZ
        );
    }

    public List<StickDiagnostics> inspectSticks(MahjongTableSession session) {
        Location center = displayCenter(session);
        List<StickDiagnostics> diagnostics = new ArrayList<>();
        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = session.playerAt(wind);
            if (playerId == null) {
                continue;
            }
            List<ScoringStick> cornerSticks = session.cornerSticks(wind);
            for (int i = 0; i < cornerSticks.size(); i++) {
                boolean longOnX = cornerStickLongOnX(wind);
                ScoringStick stick = cornerSticks.get(i);
                diagnostics.add(new StickDiagnostics(
                    wind,
                    i,
                    false,
                    longOnX,
                    stick,
                    cornerStickCenter(center, wind, i),
                    stickFurnitureId(longOnX, stick)
                ));
            }
            if (session.isRiichi(playerId)) {
                boolean longOnX = riichiStickLongOnX(wind);
                diagnostics.add(new StickDiagnostics(
                    wind,
                    -1,
                    true,
                    longOnX,
                    ScoringStick.P1000,
                    riichiStickCenter(center, wind),
                    stickFurnitureId(longOnX, ScoringStick.P1000)
                ));
            }
        }
        return List.copyOf(diagnostics);
    }

    public List<Entity> renderSeatLabels(MahjongTableSession session, SeatWind wind) {
        List<Entity> spawned = new ArrayList<>(3);
        Location center = displayCenter(session);
        UUID playerId = session.playerAt(wind);
        Location handBase = handDirectionBase(center, wind);
        boolean active = session.currentSeat() == wind;

        spawned.add(DisplayEntities.spawnLabel(
            session.plugin(),
            handBase.clone().add(0.0D, 0.45D + FLOATING_TEXT_Y_OFFSET, 0.0D),
            Component.text(session.publicSeatStatus(wind)),
            seatLabelColor(wind, active)
        ));
        if (playerId != null) {
            spawned.add(DisplayEntities.spawnLabel(
                session.plugin(),
                handBase.clone().add(0.0D, 0.26D + FLOATING_TEXT_Y_OFFSET, 0.0D),
                Component.text(session.displayName(playerId, session.publicLocale())),
                Color.fromARGB(100, 18, 18, 18)
            ));
            if (!session.isStarted()) {
                spawned.add(DisplayEntities.spawnInteraction(
                    session.plugin(),
                    seatInteractionLocation(handBase),
                    SEAT_INTERACTION_WIDTH,
                    SEAT_INTERACTION_HEIGHT,
                    DisplayClickAction.toggleReady(session.id(), wind),
                    null
                ));
            }
        } else if (!session.isStarted()) {
            spawned.add(DisplayEntities.spawnInteraction(
                session.plugin(),
                seatInteractionLocation(handBase),
                SEAT_INTERACTION_WIDTH,
                SEAT_INTERACTION_HEIGHT,
                DisplayClickAction.joinSeat(session.id(), wind),
                null
            ));
        }
        return spawned;
    }

    public List<Entity> renderSeatLabels(
        MahjongTableSession session,
        MahjongTableSession.SeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan
    ) {
        List<Entity> spawned = new ArrayList<>(3);
        boolean active = seat.wind() == session.currentSeat();

        spawned.add(DisplayEntities.spawnLabel(
            session.plugin(),
            toLocation(session, plan.statusLabelLocation()),
            Component.text(seat.publicSeatStatus()),
            seatLabelColor(seat.wind(), active)
        ));
        if (seat.playerId() != null) {
            spawned.add(DisplayEntities.spawnLabel(
                session.plugin(),
                toLocation(session, plan.playerNameLocation()),
                Component.text(seat.displayName()),
                Color.fromARGB(100, 18, 18, 18)
            ));
            if (!session.isStarted()) {
                Entity seatHitbox = session.plugin().craftEngine().placeSeatHitbox(
                    toLocation(session, plan.interactionLocation()),
                    DisplayClickAction.toggleReady(session.id(), seat.wind())
                );
                if (seatHitbox != null) {
                    spawned.add(seatHitbox);
                } else {
                    spawned.add(DisplayEntities.spawnInteraction(
                        session.plugin(),
                        toLocation(session, plan.interactionLocation()),
                        SEAT_INTERACTION_WIDTH,
                        SEAT_INTERACTION_HEIGHT,
                        DisplayClickAction.toggleReady(session.id(), seat.wind()),
                        null
                    ));
                }
            }
        } else if (!session.isStarted()) {
            Entity seatHitbox = session.plugin().craftEngine().placeSeatHitbox(
                toLocation(session, plan.interactionLocation()),
                DisplayClickAction.joinSeat(session.id(), seat.wind())
            );
            if (seatHitbox != null) {
                spawned.add(seatHitbox);
            } else {
                spawned.add(DisplayEntities.spawnInteraction(
                    session.plugin(),
                    toLocation(session, plan.interactionLocation()),
                    SEAT_INTERACTION_WIDTH,
                    SEAT_INTERACTION_HEIGHT,
                    DisplayClickAction.joinSeat(session.id(), seat.wind()),
                    null
                ));
            }
        }
        return spawned;
    }

    public List<Entity> renderSticks(MahjongTableSession session, SeatWind wind) {
        Location center = displayCenter(session);
        UUID playerId = session.playerAt(wind);
        if (playerId == null) {
            return List.of();
        }

        List<Entity> spawned = new ArrayList<>();
        List<ScoringStick> cornerSticks = session.cornerSticks(wind);
        for (int i = 0; i < cornerSticks.size(); i++) {
            spawned.add(spawnStick(session, cornerStickCenter(center, wind, i), cornerStickLongOnX(wind), cornerSticks.get(i)));
        }
        if (session.isRiichi(playerId)) {
            spawned.add(spawnStick(session, riichiStickCenter(center, wind), riichiStickLongOnX(wind), ScoringStick.P1000));
        }
        return spawned;
    }

    public List<Entity> renderWall(MahjongTableSession session) {
        if (!session.isStarted()) {
            return List.of();
        }
        Location center = displayCenter(session);
        int liveWallCount = session.remainingWall().size();
        int kanCount = session.kanCount();
        int frontDrawCount = Math.max(0, LIVE_WALL_SIZE - liveWallCount - kanCount);
        boolean[] doraSlots = new boolean[DEAD_WALL_SIZE];
        for (int i = 0; i < session.doraIndicators().size(); i++) {
            int deadWallIndex = doraIndicatorDeadWallIndex(kanCount, i);
            if (deadWallIndex >= 0 && deadWallIndex < doraSlots.length) {
                doraSlots[deadWallIndex] = true;
            }
        }

        List<DeadWallPlacement> deadWallPlacements = deadWallPlacements(center, session);
        List<Entity> spawned = new ArrayList<>(liveWallCount + DEAD_WALL_SIZE - session.doraIndicators().size());
        int breakTileIndex = wallBreakTileIndex(session);
        for (int i = 0; i < liveWallCount; i++) {
            int wallSlot = Math.floorMod(breakTileIndex + frontDrawCount + i, TOTAL_WALL_TILES);
            SeatWind wind = WallLayout.wallSeat(wallSlot);
            Location tileLocation = wallSlotLocation(center, wallSlot);
            if (kanCount % 2 == 1 && i == liveWallCount - 1) {
                tileLocation.subtract(0.0D, TILE_DEPTH, 0.0D);
            }
            spawned.add(spawnPublicTile(session, tileLocation, seatYaw(wind), MahjongTile.UNKNOWN, DisplayEntities.TileRenderPose.FLAT_FACE_DOWN));
        }

        for (int i = 0; i < DEAD_WALL_SIZE; i++) {
            if (doraSlots[i]) {
                continue;
            }
            DeadWallPlacement placement = deadWallPlacements.get(i);
            spawned.add(spawnPublicTile(session, placement.location(), placement.yaw(), MahjongTile.UNKNOWN, DisplayEntities.TileRenderPose.FLAT_FACE_DOWN));
        }
        return spawned;
    }

    public List<Entity> renderWall(MahjongTableSession session, TableRenderLayout.LayoutPlan plan) {
        if (!session.isStarted()) {
            return List.of();
        }
        List<Entity> spawned = new ArrayList<>(plan.wallTiles().size());
        for (TableRenderLayout.TilePlacement placement : plan.wallTiles()) {
            spawned.add(spawnPublicTile(session, placement));
        }
        return spawned;
    }

    public List<Entity> renderSticks(
        MahjongTableSession session,
        MahjongTableSession.SeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan
    ) {
        if (seat.playerId() == null) {
            return List.of();
        }
        List<Entity> spawned = new ArrayList<>(plan.stickPlacements().size());
        for (TableRenderLayout.StickPlacement stickPlacement : plan.stickPlacements()) {
            spawned.add(spawnStick(session, toLocation(session, stickPlacement.center()), stickPlacement.longOnX(), stickPlacement.stick()));
        }
        return spawned;
    }

    public List<Entity> renderDora(MahjongTableSession session) {
        if (!session.isStarted()) {
            return List.of();
        }
        Location center = displayCenter(session);
        List<MahjongTile> dora = session.doraIndicators();
        List<DeadWallPlacement> deadWallPlacements = deadWallPlacements(center, session);
        List<Entity> spawned = new ArrayList<>(dora.size());
        int kanCount = session.kanCount();
        for (int i = 0; i < dora.size(); i++) {
            DeadWallPlacement placement = deadWallPlacements.get(doraIndicatorDeadWallIndex(kanCount, i));
            spawned.add(spawnPublicTile(session, placement.location(), placement.yaw(), dora.get(i), DisplayEntities.TileRenderPose.FLAT_FACE_UP));
        }
        return spawned;
    }

    public List<Entity> renderDora(MahjongTableSession session, TableRenderLayout.LayoutPlan plan) {
        if (!session.isStarted()) {
            return List.of();
        }
        List<Entity> spawned = new ArrayList<>(plan.doraTiles().size());
        for (TableRenderLayout.TilePlacement placement : plan.doraTiles()) {
            spawned.add(spawnPublicTile(session, placement));
        }
        return spawned;
    }

    public List<Entity> renderCenterLabel(MahjongTableSession session) {
        Location center = displayCenter(session);
        return List.of(DisplayEntities.spawnLabel(
            session.plugin(),
            center.clone().add(0.0D, 0.3D + FLOATING_TEXT_Y_OFFSET, 0.0D),
            Component.text(session.publicCenterText()),
            Color.fromARGB(112, 20, 80, 20)
        ));
    }

    public List<Entity> renderCenterLabel(
        MahjongTableSession session,
        MahjongTableSession.RenderSnapshot snapshot,
        TableRenderLayout.LayoutPlan plan
    ) {
        return List.of(DisplayEntities.spawnLabel(
            session.plugin(),
            toLocation(session, plan.displayCenter()).add(0.0D, 0.3D + FLOATING_TEXT_Y_OFFSET, 0.0D),
            Component.text(snapshot.publicCenterText()),
            Color.fromARGB(112, 20, 80, 20)
        ));
    }

    public List<Entity> renderViewerOverlay(MahjongTableSession session, Player viewer) {
        Location center = displayCenter(session);
        UUID viewerId = viewer.getUniqueId();
        List<Entity> spawned = new ArrayList<>(session.isSpectator(viewerId) ? 5 : 1);
        if (session.isStarted() || session.isSpectator(viewerId)) {
            spawned.add(DisplayEntities.spawnLabel(
                session.plugin(),
                center.clone().add(0.0D, 0.9D + FLOATING_TEXT_Y_OFFSET, 0.0D),
                session.viewerOverlay(viewer),
                Color.fromARGB(84, 12, 12, 12),
                List.of(viewerId)
            ));
        }
        if (session.isSpectator(viewerId)) {
            for (SeatWind wind : SeatWind.values()) {
                spawned.add(DisplayEntities.spawnLabel(
                    session.plugin(),
                    add(handDirectionBase(center, wind), offsetAcrossSeat(wind, 0.42D)).add(0.0D, 0.62D + FLOATING_TEXT_Y_OFFSET, 0.0D),
                    session.spectatorSeatOverlay(viewer, wind),
                    Color.fromARGB(92, 14, 14, 18),
                    List.of(viewerId)
                ));
            }
        }
        return spawned;
    }

    public List<Entity> renderViewerOverlay(MahjongTableSession session, MahjongTableSession.ViewerOverlaySnapshot snapshot) {
        Location center = displayCenter(session);
        List<Entity> spawned = new ArrayList<>(snapshot.spectatorSeatOverlays().isEmpty() ? 1 : 1 + snapshot.spectatorSeatOverlays().size());
        if (session.isStarted() || snapshot.spectator()) {
            spawned.add(DisplayEntities.spawnLabel(
                session.plugin(),
                center.clone().add(0.0D, 0.9D + FLOATING_TEXT_Y_OFFSET, 0.0D),
                snapshot.overlay(),
                Color.fromARGB(84, 12, 12, 12),
                List.of(snapshot.viewerId())
            ));
        }
        if (snapshot.spectator()) {
            for (MahjongTableSession.SpectatorSeatOverlaySnapshot seatOverlay : snapshot.spectatorSeatOverlays()) {
                spawned.add(DisplayEntities.spawnLabel(
                    session.plugin(),
                    add(handDirectionBase(center, seatOverlay.wind()), offsetAcrossSeat(seatOverlay.wind(), 0.42D)).add(0.0D, 0.62D + FLOATING_TEXT_Y_OFFSET, 0.0D),
                    seatOverlay.overlay(),
                    Color.fromARGB(92, 14, 14, 18),
                    List.of(snapshot.viewerId())
                ));
            }
        }
        return spawned;
    }

    public List<Entity> renderDiscards(
        MahjongTableSession session,
        MahjongTableSession.SeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan
    ) {
        if (seat.playerId() == null) {
            return List.of();
        }
        List<Entity> spawned = new ArrayList<>(plan.discardPlacements().size());
        for (TableRenderLayout.TilePlacement placement : plan.discardPlacements()) {
            spawned.add(spawnPublicTile(session, placement));
        }
        return spawned;
    }

    public List<Entity> renderDiscardTile(
        MahjongTableSession session,
        MahjongTableSession.SeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan,
        int discardIndex
    ) {
        if (seat.playerId() == null || discardIndex < 0 || discardIndex >= plan.discardPlacements().size()) {
            return List.of();
        }
        return List.of(spawnPublicTile(session, plan.discardPlacements().get(discardIndex)));
    }

    public List<Entity> renderHandPrivate(
        MahjongTableSession session,
        MahjongTableSession.SeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan
    ) {
        if (seat.playerId() == null) {
            return List.of();
        }

        List<UUID> ownerOnly = List.of(seat.playerId());
        List<Entity> spawned = new ArrayList<>(seat.hand().size() * 2);
        for (int tileIndex = 0; tileIndex < seat.hand().size(); tileIndex++) {
            Location tileLocation = toLocation(session, plan.privateHandPoints().get(tileIndex));
            spawned.add(DisplayEntities.spawnTileDisplay(
                session.plugin(),
                tileLocation,
                plan.yaw(),
                seat.hand().get(tileIndex),
                DisplayEntities.TileRenderPose.STANDING,
                null,
                true,
                ownerOnly
            ));
            Entity clickHitbox = session.plugin().craftEngine().placeHandTileHitbox(
                handInteractionLocation(tileLocation),
                DisplayClickAction.handTile(session.id(), seat.playerId(), tileIndex)
            );
            if (clickHitbox != null) {
                spawned.add(clickHitbox);
            }
        }
        return spawned;
    }

    public List<Entity> renderHandPrivateTile(
        MahjongTableSession session,
        MahjongTableSession.SeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan,
        int tileIndex
    ) {
        if (seat.playerId() == null || tileIndex < 0 || tileIndex >= seat.hand().size()) {
            return List.of();
        }

        Location tileLocation = toLocation(session, plan.privateHandPoints().get(tileIndex));
        UUID playerId = seat.playerId();
        List<Entity> spawned = new ArrayList<>(2);
        spawned.add(DisplayEntities.spawnTileDisplay(
            session.plugin(),
            tileLocation,
            plan.yaw(),
            seat.hand().get(tileIndex),
            DisplayEntities.TileRenderPose.STANDING,
            null,
            true,
            List.of(playerId)
        ));
        Entity clickHitbox = session.plugin().craftEngine().placeHandTileHitbox(
            handInteractionLocation(tileLocation),
            DisplayClickAction.handTile(session.id(), playerId, tileIndex)
        );
        if (clickHitbox != null) {
            spawned.add(clickHitbox);
        }
        return spawned;
    }

    public List<Entity> renderHandPublic(
        MahjongTableSession session,
        MahjongTableSession.RenderSnapshot snapshot,
        MahjongTableSession.SeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan
    ) {
        if (seat.playerId() == null) {
            return List.of();
        }

        List<Entity> spawned = new ArrayList<>(seat.hand().size());
        boolean concealHand = snapshot.started();
        for (int i = 0; i < seat.hand().size(); i++) {
            spawned.add(DisplayEntities.spawnTileDisplay(
                session.plugin(),
                toLocation(session, plan.publicHandPoints().get(i)),
                plan.yaw(),
                concealHand ? MahjongTile.UNKNOWN : seat.hand().get(i),
                DisplayEntities.TileRenderPose.STANDING,
                null,
                true,
                seat.viewerIdsExcluding()
            ));
        }
        return spawned;
    }

    public List<Entity> renderMelds(MahjongTableSession session, SeatWind wind) {
        Location center = displayCenter(session);
        UUID playerId = session.playerAt(wind);
        if (playerId == null) {
            return List.of();
        }

        float yaw = seatYaw(wind);
        List<MeldView> melds = session.fuuro(playerId);
        if (melds.isEmpty()) {
            return List.of();
        }

        int tileCount = melds.stream().mapToInt(meld -> meld.tiles().size() + (meld.hasAddedKanTile() ? 1 : 0)).sum();
        List<Entity> spawned = new ArrayList<>(tileCount);
        Location cursor = meldStart(center, wind);
        int stickCount = session.stickLayoutCount(wind);
        if (stickCount > 0) {
            cursor = add(cursor, cornerStickMeldOffset(wind, Math.min(stickCount, STICKS_PER_STACK)));
        }
        Location lastClaimBase = null;
        boolean lastTileWasHorizontal = false;
        int placedTileCount = 0;

        for (MeldView meld : melds) {
            boolean concealedKan = meld.tiles().size() == 4 && meld.faceDownAt(0) && meld.faceDownAt(meld.tiles().size() - 1);
            if (concealedKan) {
                for (int i = 0; i < meld.tiles().size(); i++) {
                    if (placedTileCount == 0) {
                        cursor = add(cursor, halfVerticalTileOffset(wind));
                    } else if (lastTileWasHorizontal) {
                        cursor = add(cursor, add(halfHorizontalTileOffset(wind), halfVerticalTileOffset(wind)));
                    } else {
                        cursor = add(cursor, verticalTileOffset(wind));
                    }
                    spawned.add(spawnPublicTile(
                        session,
                        cursor.clone().add(0.0D, FLAT_TILE_Y, 0.0D),
                        yaw,
                        meld.tiles().get(i),
                        meld.faceDownAt(i) ? DisplayEntities.TileRenderPose.FLAT_FACE_DOWN : DisplayEntities.TileRenderPose.FLAT_FACE_UP
                    ));
                    placedTileCount++;
                }
                lastClaimBase = null;
                lastTileWasHorizontal = false;
                continue;
            }

            for (int i = 0; i < meld.tiles().size(); i++) {
                boolean isClaimTile = meld.hasClaimTile() && i == meld.claimTileIndex();
                if (placedTileCount == 0) {
                    cursor = add(cursor, isClaimTile ? halfHorizontalTileOffset(wind) : halfVerticalTileOffset(wind));
                } else if (isClaimTile || lastTileWasHorizontal) {
                    cursor = isClaimTile && lastTileWasHorizontal
                        ? add(cursor, horizontalTileOffset(wind))
                        : add(cursor, add(halfHorizontalTileOffset(wind), halfVerticalTileOffset(wind)));
                } else {
                    cursor = add(cursor, verticalTileOffset(wind));
                }

                Location baseLocation = isClaimTile ? add(cursor, horizontalTileGravityOffset(wind)) : cursor;
                spawned.add(spawnPublicTile(
                    session,
                    baseLocation.clone().add(0.0D, FLAT_TILE_Y, 0.0D),
                    isClaimTile ? yaw + meld.claimYawOffset() : yaw,
                    meld.tiles().get(i),
                    meld.faceDownAt(i) ? DisplayEntities.TileRenderPose.FLAT_FACE_DOWN : DisplayEntities.TileRenderPose.FLAT_FACE_UP
                ));
                lastClaimBase = isClaimTile ? baseLocation.clone() : null;
                lastTileWasHorizontal = isClaimTile;
                placedTileCount++;
            }

            if (meld.hasAddedKanTile() && lastClaimBase != null) {
                spawned.add(spawnPublicTile(
                    session,
                    add(lastClaimBase, kakanOffset(wind)).add(0.0D, FLAT_TILE_Y, 0.0D),
                    yaw + meld.claimYawOffset(),
                    meld.addedKanTile(),
                    DisplayEntities.TileRenderPose.FLAT_FACE_UP
                ));
                lastClaimBase = null;
                lastTileWasHorizontal = false;
            }
        }
        return spawned;
    }

    public List<Entity> renderMelds(
        MahjongTableSession session,
        MahjongTableSession.SeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan
    ) {
        if (seat.playerId() == null) {
            return List.of();
        }
        List<Entity> spawned = new ArrayList<>(plan.meldPlacements().size());
        for (TableRenderLayout.TilePlacement placement : plan.meldPlacements()) {
            spawned.add(spawnPublicTile(session, placement));
        }
        return spawned;
    }

    private static Location displayCenter(MahjongTableSession session) {
        return session.center().add(0.0D, DISPLAY_CENTER_Y_OFFSET, 0.0D);
    }

    private static Location handInteractionLocation(Location tileLocation) {
        return tileLocation.clone().subtract(0.0D, UPRIGHT_TILE_Y, 0.0D);
    }

    private static Location seatInteractionLocation(Location handBase) {
        return handBase.clone().add(0.0D, 0.18D + FLOATING_TEXT_Y_OFFSET, 0.0D);
    }

    private List<Entity> renderTableHitboxes(MahjongTableSession session, Location tableCenter) {
        Entity furnitureHitbox = session.plugin().craftEngine().placeTableHitbox(tableCenter.clone());
        return furnitureHitbox == null ? List.of() : List.of(furnitureHitbox);
    }

    private static Entity spawnTableVisual(MahjongTableSession session, Location tableCenter) {
        if (session.plugin().craftEngine() == null) {
            return null;
        }
        return session.plugin().craftEngine().placeFurniture(tableVisualAnchor(tableCenter), TABLE_VISUAL_FURNITURE_ID);
    }

    private static Location tableVisualAnchor(Location tableCenter) {
        return tableCenter.clone().add(0.0D, TABLE_VISUAL_Y_OFFSET, 0.0D);
    }

    private static Entity spawnPublicTile(
        MahjongTableSession session,
        Location location,
        float yaw,
        MahjongTile tile,
        DisplayEntities.TileRenderPose pose
    ) {
        // Public tiles still use CraftEngine custom items, but render through ItemDisplay so
        // they keep the exact sub-block positioning Mahjong tables need and don't introduce
        // furniture-sized interaction volumes over the table surface.
        return DisplayEntities.spawnTileDisplay(
            session.plugin(),
            location,
            yaw,
            tile,
            pose,
            null,
            true
        );
    }

    private static Entity spawnPublicTile(MahjongTableSession session, TableRenderLayout.TilePlacement placement) {
        return spawnPublicTile(session, toLocation(session, placement.point()), placement.yaw(), placement.tile(), placement.pose());
    }

    private static Location toLocation(MahjongTableSession session, TableRenderLayout.Point point) {
        Location origin = session.center();
        return new Location(origin.getWorld(), point.x(), point.y(), point.z());
    }

    private static Location centeredCuboid(Location center, double width, double height, double depth) {
        return center.clone().add(-width / 2.0D, 0.0D, -depth / 2.0D);
    }

    private static TableBounds tableBoundsFromTiles(Location center) {
        Location eastMeldStart = meldStart(center, SeatWind.EAST);
        Location southMeldStart = meldStart(center, SeatWind.SOUTH);
        Location westMeldStart = meldStart(center, SeatWind.WEST);
        Location northMeldStart = meldStart(center, SeatWind.NORTH);
        double halfTileHeight = TILE_HEIGHT / 2.0D;
        double minX = northMeldStart.getX();
        double maxX = southMeldStart.getX();
        double minZ = eastMeldStart.getZ();
        double maxZ = westMeldStart.getZ();
        double centerX = (westMeldStart.getX() - halfTileHeight + maxX) / 2.0D;
        double centerZ = (northMeldStart.getZ() - halfTileHeight + maxZ) / 2.0D;
        return new TableBounds(centerX, centerZ, minX, maxX, minZ, maxZ);
    }

    private static int wallBreakTileIndex(MahjongTableSession session) {
        int seatCount = SeatWind.values().length;
        int dicePoints = session.dicePoints();
        int directionIndex = 4 - (((dicePoints % seatCount) - 1 + session.roundIndex()) % seatCount);
        return Math.floorMod(directionIndex * WALL_TILES_PER_SIDE + dicePoints * 2, TOTAL_WALL_TILES);
    }

    private static int deadWallAnchorSlot(MahjongTableSession session) {
        return Math.floorMod(wallBreakTileIndex(session) - DEAD_WALL_SIZE, TOTAL_WALL_TILES);
    }

    private static int doraIndicatorDeadWallIndex(int kanCount, int indicatorIndex) {
        return (4 - indicatorIndex) * 2 + kanCount;
    }

    private static Location wallSlotLocation(Location center, int wallSlot) {
        SeatWind wind = WallLayout.wallSeat(wallSlot);
        int stackIndex = WallLayout.wallColumn(wallSlot);
        double stackWidth = stackIndex * WALL_TILE_STEP;
        double startingPos = (17.0D * TILE_WIDTH) / 2.0D - TILE_HEIGHT;
        double yOffset = FLAT_TILE_Y + wallLayerYOffset(WallLayout.wallLayer(wallSlot));
        return switch (wind) {
            case EAST -> center.clone().add(WALL_DIRECTION_OFFSET, yOffset, -startingPos + stackWidth);
            case SOUTH -> center.clone().add(startingPos - stackWidth, yOffset, WALL_DIRECTION_OFFSET);
            case WEST -> center.clone().add(-WALL_DIRECTION_OFFSET, yOffset, startingPos - stackWidth);
            case NORTH -> center.clone().add(-startingPos + stackWidth, yOffset, -WALL_DIRECTION_OFFSET);
        };
    }

    private static double wallLayerYOffset(int layer) {
        return layer * TILE_DEPTH + (layer == 1 ? TILE_PADDING : 0.0D);
    }

    private static List<DeadWallPlacement> deadWallPlacements(Location center, MahjongTableSession session) {
        int breakTileIndex = wallBreakTileIndex(session);
        List<DeadWallPlacementMutable> placements = new ArrayList<>(DEAD_WALL_SIZE);
        for (int i = 0; i < DEAD_WALL_SIZE; i++) {
            int wallSlot = Math.floorMod(breakTileIndex - DEAD_WALL_SIZE + i, TOTAL_WALL_TILES);
            SeatWind face = WallLayout.wallSeat(wallSlot);
            placements.add(new DeadWallPlacementMutable(
                wallSlot,
                face,
                seatYaw(face),
                add(wallSlotLocation(center, wallSlot), deadWallGapOffset(face))
            ));
        }

        SeatWind direction = placements.get(placements.size() - 1).face();
        List<DeadWallPlacementMutable> reversed = new ArrayList<>(placements);
        Collections.reverse(reversed);
        for (int index = 0; index < reversed.size(); index++) {
            DeadWallPlacementMutable placement = reversed.get(index);
            if (placement.face() == direction) {
                continue;
            }

            placement.setYaw(seatYaw(direction));
            if (index % 2 == 0) {
                Offset shift = deadWallCornerShift(direction);
                for (DeadWallPlacementMutable other : reversed) {
                    other.shift(shift.x(), shift.z());
                }
            }

            Location base = reversed.get(0).location();
            double positionY = reversed.get(index % 2 == 0 ? 0 : 1).location().getY();
            double offset = WALL_TILE_STEP * (index / 2);
            placement.setLocation(switch (direction) {
                case EAST -> new Location(base.getWorld(), base.getX(), positionY, base.getZ() - offset);
                case SOUTH -> new Location(base.getWorld(), base.getX() + offset, positionY, base.getZ());
                case WEST -> new Location(base.getWorld(), base.getX(), positionY, base.getZ() + offset);
                case NORTH -> new Location(base.getWorld(), base.getX() - offset, positionY, base.getZ());
            });
        }

        List<DeadWallPlacement> result = new ArrayList<>(placements.size());
        for (DeadWallPlacementMutable placement : placements) {
            result.add(new DeadWallPlacement(placement.location(), placement.yaw()));
        }
        return result;
    }

    private static float seatYaw(SeatWind wind) {
        return DiscardLayout.seatYaw(wind);
    }

    private static Location handDirectionBase(Location center, SeatWind wind) {
        return switch (wind) {
            case EAST -> center.clone().add(HAND_DIRECTION_OFFSET, 0.0D, 0.0D);
            case SOUTH -> center.clone().add(0.0D, 0.0D, HAND_DIRECTION_OFFSET);
            case WEST -> center.clone().add(-HAND_DIRECTION_OFFSET, 0.0D, 0.0D);
            case NORTH -> center.clone().add(0.0D, 0.0D, -HAND_DIRECTION_OFFSET);
        };
    }

    private static Location meldStart(Location center, SeatWind wind) {
        double halfHeight = TILE_HEIGHT / 2.0D;
        return switch (wind) {
            case EAST -> center.clone().add(HALF_TABLE_LENGTH_NO_BORDER - halfHeight, 0.0D, -HALF_TABLE_LENGTH_NO_BORDER);
            case SOUTH -> center.clone().add(HALF_TABLE_LENGTH_NO_BORDER, 0.0D, HALF_TABLE_LENGTH_NO_BORDER - halfHeight);
            case WEST -> center.clone().add(-HALF_TABLE_LENGTH_NO_BORDER + halfHeight, 0.0D, HALF_TABLE_LENGTH_NO_BORDER);
            case NORTH -> center.clone().add(-HALF_TABLE_LENGTH_NO_BORDER, 0.0D, -HALF_TABLE_LENGTH_NO_BORDER + halfHeight);
        };
    }

    private static Location add(Location location, Offset offset) {
        return location.clone().add(offset.x(), 0.0D, offset.z());
    }

    private static double centeredOffset(int size, int index, double step) {
        return (index - (size - 1) / 2.0D) * step;
    }

    private static Entity spawnStick(MahjongTableSession session, Location center, boolean longOnX, ScoringStick stick) {
        Entity furniture = session.plugin().craftEngine() == null
            ? null
            : session.plugin().craftEngine().placeFurniture(center, stickFurnitureId(longOnX, stick));
        if (furniture != null) {
            return furniture;
        }
        double width = longOnX ? STICK_WIDTH : STICK_DEPTH;
        double depth = longOnX ? STICK_DEPTH : STICK_WIDTH;
        return DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(center, width, STICK_HEIGHT, depth),
            stickMaterial(stick),
            (float) width,
            (float) STICK_HEIGHT,
            (float) depth
        );
    }

    private static String stickFurnitureId(boolean longOnX, ScoringStick stick) {
        return STICK_FURNITURE_PREFIX
            + (longOnX ? "x_" : "z_")
            + switch (stick) {
                case P100 -> "p100";
                case P5000 -> "p5000";
                case P10000 -> "p10000";
                default -> "p1000";
            };
    }

    private static Material stickMaterial(ScoringStick stick) {
        return switch (stick) {
            case P100 -> Material.RED_CONCRETE;
            case P5000 -> Material.GOLD_BLOCK;
            case P10000 -> Material.EMERALD_BLOCK;
            default -> Material.QUARTZ_BLOCK;
        };
    }

    private static Location riichiStickCenter(Location center, SeatWind wind) {
        double halfWidthOfSixTiles = TILE_WIDTH * DISCARDS_PER_ROW / 2.0D;
        double paddingFromCenter = halfWidthOfSixTiles - STICK_DEPTH / 2.0D;
        return switch (wind) {
            case EAST -> center.clone().add(paddingFromCenter, 0.0D, 0.0D);
            case SOUTH -> center.clone().add(0.0D, 0.0D, paddingFromCenter);
            case WEST -> center.clone().add(-paddingFromCenter, 0.0D, 0.0D);
            case NORTH -> center.clone().add(0.0D, 0.0D, -paddingFromCenter);
        };
    }

    private static boolean riichiStickLongOnX(SeatWind wind) {
        return wind == SeatWind.SOUTH || wind == SeatWind.NORTH;
    }

    private static Location cornerStickCenter(Location center, SeatWind wind, int index) {
        int stackIndex = index / STICKS_PER_STACK;
        int stickIndex = index % STICKS_PER_STACK;
        double halfWidthOfStick = STICK_WIDTH / 2.0D;
        double halfDepthOfStick = STICK_DEPTH / 2.0D;
        Location start = switch (wind) {
            case EAST -> center.clone().add(HALF_TABLE_LENGTH_NO_BORDER - halfWidthOfStick, 0.0D, -HALF_TABLE_LENGTH_NO_BORDER + halfDepthOfStick);
            case SOUTH -> center.clone().add(HALF_TABLE_LENGTH_NO_BORDER - halfDepthOfStick, 0.0D, HALF_TABLE_LENGTH_NO_BORDER - halfWidthOfStick);
            case WEST -> center.clone().add(-HALF_TABLE_LENGTH_NO_BORDER + halfWidthOfStick, 0.0D, HALF_TABLE_LENGTH_NO_BORDER - halfDepthOfStick);
            case NORTH -> center.clone().add(-HALF_TABLE_LENGTH_NO_BORDER + halfDepthOfStick, 0.0D, -HALF_TABLE_LENGTH_NO_BORDER + halfWidthOfStick);
        };
        return add(start, multiply(cornerStickOffset(wind), stickIndex)).add(0.0D, stackIndex * (STICK_HEIGHT + TILE_PADDING), 0.0D);
    }

    private static boolean cornerStickLongOnX(SeatWind wind) {
        return wind == SeatWind.EAST || wind == SeatWind.WEST;
    }

    private static Offset cornerStickOffset(SeatWind wind) {
        double amount = STICK_DEPTH + TILE_PADDING;
        return switch (wind) {
            case EAST -> new Offset(0.0D, amount);
            case SOUTH -> new Offset(-amount, 0.0D);
            case WEST -> new Offset(0.0D, -amount);
            case NORTH -> new Offset(amount, 0.0D);
        };
    }

    private static Offset cornerStickMeldOffset(SeatWind wind, int firstStackCount) {
        double amount = firstStackCount * STICK_DEPTH + Math.max(0, firstStackCount - 1) * TILE_PADDING;
        return switch (wind) {
            case EAST -> new Offset(0.0D, amount);
            case SOUTH -> new Offset(-amount, 0.0D);
            case WEST -> new Offset(0.0D, -amount);
            case NORTH -> new Offset(amount, 0.0D);
        };
    }

    private static Offset deadWallGapOffset(SeatWind wind) {
        return switch (wind) {
            case EAST -> new Offset(0.0D, DEAD_WALL_GAP);
            case SOUTH -> new Offset(-DEAD_WALL_GAP, 0.0D);
            case WEST -> new Offset(0.0D, -DEAD_WALL_GAP);
            case NORTH -> new Offset(DEAD_WALL_GAP, 0.0D);
        };
    }

    private static Offset deadWallLineOffset(SeatWind wind) {
        double amount = WALL_TILE_STEP;
        return switch (wind) {
            case EAST -> new Offset(0.0D, -amount);
            case SOUTH -> new Offset(amount, 0.0D);
            case WEST -> new Offset(0.0D, amount);
            case NORTH -> new Offset(-amount, 0.0D);
        };
    }

    private static Offset deadWallCornerShift(SeatWind wind) {
        return switch (wind) {
            case EAST -> new Offset(0.0D, TILE_WIDTH);
            case SOUTH -> new Offset(-TILE_WIDTH, 0.0D);
            case WEST -> new Offset(0.0D, -TILE_WIDTH);
            case NORTH -> new Offset(TILE_WIDTH, 0.0D);
        };
    }

    private static Offset verticalTileOffset(SeatWind wind) {
        double amount = TILE_WIDTH + TILE_PADDING;
        return switch (wind) {
            case EAST -> new Offset(0.0D, amount);
            case SOUTH -> new Offset(-amount, 0.0D);
            case WEST -> new Offset(0.0D, -amount);
            case NORTH -> new Offset(amount, 0.0D);
        };
    }

    private static Offset halfVerticalTileOffset(SeatWind wind) {
        return multiply(verticalTileOffset(wind), 0.5D);
    }

    private static Offset horizontalTileOffset(SeatWind wind) {
        double amount = TILE_HEIGHT + TILE_PADDING;
        return switch (wind) {
            case EAST -> new Offset(0.0D, amount);
            case SOUTH -> new Offset(-amount, 0.0D);
            case WEST -> new Offset(0.0D, -amount);
            case NORTH -> new Offset(amount, 0.0D);
        };
    }

    private static Offset halfHorizontalTileOffset(SeatWind wind) {
        return multiply(horizontalTileOffset(wind), 0.5D);
    }

    private static Offset kakanOffset(SeatWind wind) {
        double amount = TILE_WIDTH + TILE_PADDING;
        return switch (wind) {
            case EAST -> new Offset(-amount, 0.0D);
            case SOUTH -> new Offset(0.0D, -amount);
            case WEST -> new Offset(amount, 0.0D);
            case NORTH -> new Offset(0.0D, amount);
        };
    }

    private static Offset horizontalTileGravityOffset(SeatWind wind) {
        double amount = (TILE_HEIGHT - TILE_WIDTH) / 2.0D;
        return switch (wind) {
            case EAST -> new Offset(amount, 0.0D);
            case SOUTH -> new Offset(0.0D, amount);
            case WEST -> new Offset(-amount, 0.0D);
            case NORTH -> new Offset(0.0D, -amount);
        };
    }

    private static Offset offsetAcrossSeat(SeatWind wind, double amount) {
        return switch (wind) {
            case EAST -> new Offset(0.0D, amount);
            case SOUTH -> new Offset(-amount, 0.0D);
            case WEST -> new Offset(0.0D, -amount);
            case NORTH -> new Offset(amount, 0.0D);
        };
    }

    private static Offset add(Offset first, Offset second) {
        return new Offset(first.x() + second.x(), first.z() + second.z());
    }

    private static Offset multiply(Offset offset, double factor) {
        return new Offset(offset.x() * factor, offset.z() * factor);
    }

    private static Color seatLabelColor(SeatWind wind, boolean active) {
        if (active) {
            return Color.fromARGB(148, 255, 220, 70);
        }
        return switch (wind) {
            case EAST -> Color.fromARGB(132, 255, 183, 0);
            case SOUTH -> Color.fromARGB(132, 72, 217, 92);
            case WEST -> Color.fromARGB(132, 120, 120, 120);
            case NORTH -> Color.fromARGB(132, 86, 148, 255);
        };
    }

    private record Offset(double x, double z) {
    }

    public record TableDiagnostics(
        Location displayCenter,
        Location tableCenter,
        Location visualAnchor,
        double borderSpanX,
        double borderSpanZ
    ) {
    }

    public record StickDiagnostics(
        SeatWind wind,
        int index,
        boolean riichi,
        boolean longOnX,
        ScoringStick stick,
        Location center,
        String furnitureId
    ) {
    }

    private record TableBounds(double centerX, double centerZ, double minX, double maxX, double minZ, double maxZ) {
        double width() {
            return this.maxX - this.minX;
        }

        double depth() {
            return this.maxZ - this.minZ;
        }
    }

    private static final class DeadWallPlacementMutable {
        private final int wallSlot;
        private final SeatWind face;
        private float yaw;
        private Location location;

        private DeadWallPlacementMutable(int wallSlot, SeatWind face, float yaw, Location location) {
            this.wallSlot = wallSlot;
            this.face = face;
            this.yaw = yaw;
            this.location = location;
        }

        private int wallSlot() {
            return this.wallSlot;
        }

        private SeatWind face() {
            return this.face;
        }

        private float yaw() {
            return this.yaw;
        }

        private void setYaw(float yaw) {
            this.yaw = yaw;
        }

        private Location location() {
            return this.location;
        }

        private void setLocation(Location location) {
            this.location = location;
        }

        private void shift(double deltaX, double deltaZ) {
            this.location = this.location.clone().add(deltaX, 0.0D, deltaZ);
        }
    }

    private record DeadWallPlacement(Location location, float yaw) {
    }
}
