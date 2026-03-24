package top.ellan.mahjong.render.scene;

import top.ellan.mahjong.model.MahjongTile;
import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.render.display.DisplayClickAction;
import top.ellan.mahjong.render.display.DisplayEntities;
import top.ellan.mahjong.render.layout.DiscardLayout;
import top.ellan.mahjong.render.layout.TableRenderLayout;
import top.ellan.mahjong.render.layout.WallLayout;
import top.ellan.mahjong.table.core.MahjongTableSession;
import top.ellan.mahjong.table.core.MahjongVariant;
import top.ellan.mahjong.table.core.TableRenderSnapshot;
import top.ellan.mahjong.table.core.TableSeatRenderSnapshot;
import top.ellan.mahjong.table.core.TableSpectatorSeatOverlaySnapshot;
import top.ellan.mahjong.table.core.TableViewerOverlaySnapshot;
import top.ellan.mahjong.riichi.model.ScoringStick;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
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
    private static final double STICK_Y_OFFSET = 0.515625D;
    private static final int STICKS_PER_STACK = 5;
    private static final double TABLE_BOTTOM_SIZE = 14.0D * ONE_SIXTEENTH;
    private static final double TABLE_BOTTOM_HEIGHT = 2.0D * ONE_SIXTEENTH;
    private static final double TABLE_PILLAR_SIZE = 8.0D * ONE_SIXTEENTH;
    private static final double TABLE_PILLAR_HEIGHT = 12.0D * ONE_SIXTEENTH;
    private static final double TABLE_TOP_SIZE_EXPANSION = ONE_SIXTEENTH;
    private static final double TABLE_TOP_THICKNESS = 2.0D * ONE_SIXTEENTH;
    private static final double TABLE_BORDER_THICKNESS = ONE_SIXTEENTH;
    private static final double TABLE_BORDER_OUTWARD_OFFSET = 0.0D;
    private static final double TABLE_BORDER_HEIGHT = 3.0D * ONE_SIXTEENTH;
    private static final double DISPLAY_CENTER_Y_OFFSET = 0.52D;
    private static final double TABLE_VISUAL_Y_OFFSET = 0.5D;
    private static final double FLOATING_TEXT_Y_OFFSET = 1.0D;
    private static final double SEAT_DISTANCE_FROM_HAND_BASE = 0.9D;
    private static final double SEAT_BASE_Y_OFFSET = -0.62D;
    private static final double SEAT_RAISE_Y_OFFSET = 1.0D;
    private static final double SEAT_ANCHOR_Y_OFFSET = -0.18D;
    private static final double SEAT_BASE_WIDTH = 0.72D;
    private static final double SEAT_BASE_HEIGHT = 0.16D;
    private static final double SEAT_BACKREST_WIDTH = 0.72D;
    private static final double SEAT_BACKREST_HEIGHT = 0.72D;
    private static final double SEAT_BACKREST_DEPTH = 0.12D;
    private static final double SEAT_BACKREST_OFFSET = 0.26D;
    private static final double SEAT_CARPET_INSET = 0.08D;
    private static final double SEAT_CARPET_THICKNESS = 0.04D;
    private static final double SEAT_LABEL_DEPTH_OFFSET = 0.03D;
    private static final double CENTER_LABEL_Y_OFFSET = 0.55D + FLOATING_TEXT_Y_OFFSET - 0.5D;
    private static final double CENTER_LAST_DISCARD_TILE_Y_OFFSET = CENTER_LABEL_Y_OFFSET - 0.18D;
    private static final float CENTER_LAST_DISCARD_TILE_SCALE = 2.0F;
    private static final Color CENTER_LAST_DISCARD_TILE_GLOW = Color.fromRGB(255, 220, 96);
    private static final double WALL_DIRECTION_OFFSET = 1.0D;
    private static final double HAND_DIRECTION_OFFSET = WALL_DIRECTION_OFFSET + TILE_DEPTH + TILE_HEIGHT;
    private static final double HALF_TABLE_LENGTH_NO_BORDER = 0.5D + 15.0D / 16.0D;
    private static final double DEAD_WALL_GAP = TILE_PADDING * 20.0D;
    private static final double WALL_TILE_STEP = TILE_WIDTH + TILE_PADDING;
    private static final double UPRIGHT_TILE_Y = TILE_HEIGHT / 2.0D;
    private static final double FLAT_TILE_Y = TILE_DEPTH / 2.0D;
    // Keep kakan as a near-table overlay on the target tile (not visually stacked high above it).
    private static final double KAKAN_STACK_Y_OFFSET = 0.003D;
    private static final float HAND_INTERACTION_WIDTH = (float) TILE_WIDTH;
    private static final float HAND_INTERACTION_HEIGHT = (float) TILE_HEIGHT;
    private static final float SEAT_LABEL_INTERACTION_WIDTH = 1.2F;
    private static final float SEAT_LABEL_INTERACTION_HEIGHT = 0.85F;
    private static final int WALL_TILES_PER_SIDE = 34;
    private static final int TOTAL_WALL_TILES = 136;
    private static final int DEAD_WALL_SIZE = 14;
    private static final int LIVE_WALL_SIZE = TOTAL_WALL_TILES - DEAD_WALL_SIZE;
    private static final int DISCARDS_PER_ROW = 6;
    private static final double CUSTOM_FURNITURE_ORIGIN_Y_OFFSET = 1.375D;
    private static final String TABLE_VISUAL_FURNITURE_ID = "mahjongpaper:table_visual";
    private static final String SEAT_VISUAL_FURNITURE_ID = "mahjongpaper:seat_chair";
    private static final String STICK_FURNITURE_PREFIX = "mahjongpaper:stick_";

    public List<Entity> renderTableStructure(MahjongTableSession session) {
        Location center = displayCenter(session);
        TableBounds bounds = tableBoundsFromTiles(center);
        List<Entity> spawned = new ArrayList<>(16);
        Location tableCenter = center.clone().set(bounds.centerX(), center.getY(), bounds.centerZ());
        double topWidth = bounds.width() + TABLE_TOP_SIZE_EXPANSION;
        double topDepth = bounds.depth() + TABLE_TOP_SIZE_EXPANSION;
        double borderSpanX = topWidth + TABLE_BORDER_THICKNESS;
        double borderSpanZ = topDepth + TABLE_BORDER_THICKNESS;
        double borderCenterOffsetX = topWidth / 2.0D + TABLE_BORDER_THICKNESS / 2.0D + TABLE_BORDER_OUTWARD_OFFSET;
        double borderCenterOffsetZ = topDepth / 2.0D + TABLE_BORDER_THICKNESS / 2.0D + TABLE_BORDER_OUTWARD_OFFSET;
        Entity tableVisual = spawnTableVisual(session, tableCenter);
        if (tableVisual != null) {
            spawned.add(tableVisual);
            spawned.addAll(this.renderTableHitboxes(session, tableCenter, true));
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
        spawned.addAll(this.renderTableHitboxes(session, tableCenter, false));
        return spawned;
    }

    public List<Entity> renderTableStructure(MahjongTableSession session, TableRenderLayout.LayoutPlan plan) {
        List<Entity> spawned = new ArrayList<>(16);
        Location tableCenter = toLocation(session, plan.tableCenter());
        double topWidth = plan.borderSpanX() - TABLE_BORDER_THICKNESS;
        double topDepth = plan.borderSpanZ() - TABLE_BORDER_THICKNESS;
        double borderSpanX = plan.borderSpanX();
        double borderSpanZ = plan.borderSpanZ();
        double borderCenterOffsetX = topWidth / 2.0D + TABLE_BORDER_THICKNESS / 2.0D + TABLE_BORDER_OUTWARD_OFFSET;
        double borderCenterOffsetZ = topDepth / 2.0D + TABLE_BORDER_THICKNESS / 2.0D + TABLE_BORDER_OUTWARD_OFFSET;
        Entity tableVisual = spawnTableVisual(session, tableCenter);
        if (tableVisual != null) {
            spawned.add(tableVisual);
            spawned.addAll(this.renderTableHitboxes(session, tableCenter, true));
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
        spawned.addAll(this.renderTableHitboxes(session, tableCenter, false));
        return spawned;
    }

    public TableDiagnostics inspectTable(MahjongTableSession session) {
        Location center = displayCenter(session);
        TableBounds bounds = tableBoundsFromTiles(center);
        Location tableCenter = center.clone().set(bounds.centerX(), center.getY(), bounds.centerZ());
        double borderSpanX = bounds.width() + TABLE_TOP_SIZE_EXPANSION + TABLE_BORDER_THICKNESS + TABLE_BORDER_OUTWARD_OFFSET * 2.0D;
        double borderSpanZ = bounds.depth() + TABLE_TOP_SIZE_EXPANSION + TABLE_BORDER_THICKNESS + TABLE_BORDER_OUTWARD_OFFSET * 2.0D;
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
        List<Entity> spawned = new ArrayList<>(2);
        Location center = displayCenter(session);
        UUID playerId = session.playerAt(wind);
        Location handBase = handDirectionBase(center, wind);
        boolean active = session.currentSeat() == wind;
        Location statusLabelLocation = withSeatLabelDepthOffset(
            handBase.clone().add(0.0D, 0.45D + FLOATING_TEXT_Y_OFFSET, 0.0D),
            wind,
            -SEAT_LABEL_DEPTH_OFFSET * 0.5D
        );
        DisplayClickAction action = seatVisualAction(session, wind);
        Component text = playerId == null
            ? Component.text(session.publicSeatStatus(wind))
            : Component.text(session.publicSeatStatus(wind) + "\n" + session.displayName(playerId, session.publicLocale()));
        spawned.add(DisplayEntities.spawnLabel(
            session.plugin(),
            statusLabelLocation,
            text,
            seatLabelColor(wind, active),
            null,
            Display.Billboard.FIXED,
            seatYaw(wind),
            0.0F
        ));
        if (action != null) {
            Entity interaction = DisplayEntities.spawnInteraction(
                session.plugin(),
                seatLabelInteractionLocation(statusLabelLocation),
                SEAT_LABEL_INTERACTION_WIDTH,
                SEAT_LABEL_INTERACTION_HEIGHT,
                action,
                null
            );
            if (interaction != null) {
                spawned.add(interaction);
            }
        }
        return spawned;
    }

    public List<Entity> renderSeatVisual(MahjongTableSession session, SeatWind wind) {
        Location center = displayCenter(session);
        Location handBase = handDirectionBase(center, wind);
        return renderSeatVisual(session, wind, handBase, seatVisualAction(session, wind));
    }

    public List<Entity> renderSeatLabels(
        MahjongTableSession session,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan
    ) {
        List<Entity> spawned = new ArrayList<>(2);
        boolean active = seat.wind() == session.currentSeat();
        Location statusLabelLocation = withSeatLabelDepthOffset(
            toLocation(session, plan.statusLabelLocation()),
            seat.wind(),
            -SEAT_LABEL_DEPTH_OFFSET * 0.5D
        );
        DisplayClickAction action = seatVisualAction(session, seat.wind());
        Component text = seat.playerId() == null
            ? Component.text(seat.publicSeatStatus())
            : Component.text(seat.publicSeatStatus() + "\n" + seat.displayName());
        spawned.add(DisplayEntities.spawnLabel(
            session.plugin(),
            statusLabelLocation,
            text,
            seatLabelColor(seat.wind(), active),
            null,
            Display.Billboard.FIXED,
            seatYaw(seat.wind()),
            0.0F
        ));
        if (action != null) {
            Entity interaction = DisplayEntities.spawnInteraction(
                session.plugin(),
                seatLabelInteractionLocation(statusLabelLocation),
                SEAT_LABEL_INTERACTION_WIDTH,
                SEAT_LABEL_INTERACTION_HEIGHT,
                action,
                null
            );
            if (interaction != null) {
                spawned.add(interaction);
            }
        }
        return spawned;
    }

    public List<DisplayEntities.EntitySpec> renderSeatLabelSpecs(
        MahjongTableSession session,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan
    ) {
        List<DisplayEntities.EntitySpec> specs = new ArrayList<>(3);
        boolean active = seat.wind() == session.currentSeat();
        Location statusLabelLocation = withSeatLabelDepthOffset(
            toLocation(session, plan.statusLabelLocation()),
            seat.wind(),
            -SEAT_LABEL_DEPTH_OFFSET * 0.5D
        );
        DisplayClickAction action = seatVisualAction(session, seat.wind());
        Component text = seat.playerId() == null
            ? Component.text(seat.publicSeatStatus())
            : Component.text(seat.publicSeatStatus() + "\n" + seat.displayName());
        specs.add(DisplayEntities.labelSpec(
            statusLabelLocation,
            text,
            seatLabelColor(seat.wind(), active),
            null,
            Display.Billboard.FIXED,
            seatYaw(seat.wind()),
            0.0F,
            true
        ));
        if (action != null) {
            specs.add(DisplayEntities.interactionSpec(
                seatLabelInteractionLocation(statusLabelLocation),
                SEAT_LABEL_INTERACTION_WIDTH,
                SEAT_LABEL_INTERACTION_HEIGHT,
                action,
                null
            ));
        }
        return List.copyOf(specs);
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
        int liveWallCount = session.remainingWallCount();
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
            if (placement != null) {
                spawned.add(spawnPublicTile(session, placement));
            }
        }
        return spawned;
    }

    public List<Entity> renderWallTile(MahjongTableSession session, TableRenderLayout.LayoutPlan plan, int wallIndex) {
        if (!session.isStarted() || wallIndex < 0 || wallIndex >= plan.wallTiles().size()) {
            return List.of();
        }
        TableRenderLayout.TilePlacement placement = plan.wallTiles().get(wallIndex);
        return placement == null ? List.of() : List.of(spawnPublicTile(session, placement));
    }

    public List<DisplayEntities.EntitySpec> renderWallTileSpecs(MahjongTableSession session, TableRenderLayout.LayoutPlan plan, int wallIndex) {
        if (!session.isStarted() || wallIndex < 0 || wallIndex >= plan.wallTiles().size()) {
            return List.of();
        }
        TableRenderLayout.TilePlacement placement = plan.wallTiles().get(wallIndex);
        return placement == null ? List.of() : List.of(publicTileSpec(session, placement));
    }

    public List<Entity> renderSticks(
        MahjongTableSession session,
        TableSeatRenderSnapshot seat,
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

    public List<DisplayEntities.EntitySpec> renderDoraSpecs(MahjongTableSession session, TableRenderLayout.LayoutPlan plan) {
        if (!session.isStarted()) {
            return List.of();
        }
        List<DisplayEntities.EntitySpec> specs = new ArrayList<>(plan.doraTiles().size());
        for (TableRenderLayout.TilePlacement placement : plan.doraTiles()) {
            specs.add(publicTileSpec(session, placement));
        }
        return List.copyOf(specs);
    }

    public List<Entity> renderCenterLabel(MahjongTableSession session) {
        Location center = displayCenter(session);
        List<Entity> spawned = new ArrayList<>(2);
        spawned.add(DisplayEntities.spawnLabel(
            session.plugin(),
            center.clone().add(0.0D, CENTER_LABEL_Y_OFFSET, 0.0D),
            Component.text(session.publicCenterText()),
            Color.fromARGB(112, 20, 80, 20)
        ));
        if (session.lastPublicDiscardTile() != null) {
            spawned.add(spawnCenterLastDiscardTile(session, center, session.lastPublicDiscardTile()));
        }
        return List.copyOf(spawned);
    }

    public List<Entity> renderCenterLabel(
        MahjongTableSession session,
        TableRenderSnapshot snapshot,
        TableRenderLayout.LayoutPlan plan
    ) {
        Location center = toLocation(session, plan.displayCenter());
        List<Entity> spawned = new ArrayList<>(2);
        spawned.add(DisplayEntities.spawnLabel(
            session.plugin(),
            center.clone().add(0.0D, CENTER_LABEL_Y_OFFSET, 0.0D),
            Component.text(snapshot.publicCenterText()),
            Color.fromARGB(112, 20, 80, 20)
        ));
        if (snapshot.lastPublicDiscardTile() != null) {
            spawned.add(spawnCenterLastDiscardTile(session, center, snapshot.lastPublicDiscardTile()));
        }
        return List.copyOf(spawned);
    }

    public List<DisplayEntities.EntitySpec> renderCenterLabelSpecs(
        MahjongTableSession session,
        TableRenderSnapshot snapshot,
        TableRenderLayout.LayoutPlan plan
    ) {
        Location center = toLocation(session, plan.displayCenter());
        List<DisplayEntities.EntitySpec> specs = new ArrayList<>(2);
        specs.add(DisplayEntities.labelSpec(
            center.clone().add(0.0D, CENTER_LABEL_Y_OFFSET, 0.0D),
            Component.text(snapshot.publicCenterText()),
            Color.fromARGB(112, 20, 80, 20)
        ));
        if (snapshot.lastPublicDiscardTile() != null) {
            specs.add(DisplayEntities.tileDisplaySpec(
                center.clone().add(0.0D, CENTER_LAST_DISCARD_TILE_Y_OFFSET, 0.0D),
                0.0F,
                session.currentVariant(),
                snapshot.lastPublicDiscardTile(),
                DisplayEntities.TileRenderPose.STANDING,
                null,
                true,
                null,
                CENTER_LAST_DISCARD_TILE_SCALE,
                CENTER_LAST_DISCARD_TILE_GLOW,
                Display.Billboard.CENTER
            ));
        }
        return List.copyOf(specs);
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

    public List<Entity> renderViewerOverlay(MahjongTableSession session, TableViewerOverlaySnapshot snapshot) {
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
            for (TableSpectatorSeatOverlaySnapshot seatOverlay : snapshot.spectatorSeatOverlays()) {
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
        TableSeatRenderSnapshot seat,
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
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan,
        int discardIndex
    ) {
        if (seat.playerId() == null || discardIndex < 0 || discardIndex >= plan.discardPlacements().size()) {
            return List.of();
        }
        return List.of(spawnPublicTile(session, plan.discardPlacements().get(discardIndex)));
    }

    public List<DisplayEntities.EntitySpec> renderDiscardTileSpecs(
        MahjongTableSession session,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan,
        int discardIndex
    ) {
        if (seat.playerId() == null || discardIndex < 0 || discardIndex >= plan.discardPlacements().size()) {
            return List.of();
        }
        return List.of(publicTileSpec(session, plan.discardPlacements().get(discardIndex)));
    }

    public List<Entity> renderHandPrivate(
        MahjongTableSession session,
        TableSeatRenderSnapshot seat,
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
                session.plugin(),
                handInteractionLocation(tileLocation),
                HAND_INTERACTION_WIDTH,
                HAND_INTERACTION_HEIGHT,
                DisplayClickAction.handTile(session.id(), seat.playerId(), tileIndex),
                ownerOnly
            );
            if (clickHitbox != null) {
                spawned.add(clickHitbox);
            }
        }
        return spawned;
    }

    public List<Entity> renderHandPrivateTile(
        MahjongTableSession session,
        TableSeatRenderSnapshot seat,
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
            session.plugin(),
            handInteractionLocation(tileLocation),
            HAND_INTERACTION_WIDTH,
            HAND_INTERACTION_HEIGHT,
            DisplayClickAction.handTile(session.id(), playerId, tileIndex),
            List.of(playerId)
        );
        if (clickHitbox != null) {
            spawned.add(clickHitbox);
        }
        return spawned;
    }

    public List<Entity> renderHandPublic(
        MahjongTableSession session,
        TableRenderSnapshot snapshot,
        TableSeatRenderSnapshot seat,
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
                session.currentVariant(),
                concealHand ? MahjongTile.UNKNOWN : seat.hand().get(i),
                DisplayEntities.TileRenderPose.STANDING,
                null,
                true,
                null,
                List.of(seat.playerId())
            ));
        }
        return spawned;
    }

    public List<Entity> renderHandPublicTile(
        MahjongTableSession session,
        TableRenderSnapshot snapshot,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan,
        int tileIndex
    ) {
        if (seat.playerId() == null || tileIndex < 0 || tileIndex >= seat.hand().size()) {
            return List.of();
        }

        boolean concealHand = snapshot.started();
        return List.of(DisplayEntities.spawnTileDisplay(
            session.plugin(),
            toLocation(session, plan.publicHandPoints().get(tileIndex)),
            plan.yaw(),
            session.currentVariant(),
            concealHand ? MahjongTile.UNKNOWN : seat.hand().get(tileIndex),
            DisplayEntities.TileRenderPose.STANDING,
            null,
            true,
            null,
            List.of(seat.playerId())
        ));
    }

    public List<DisplayEntities.EntitySpec> renderHandPublicTileSpecs(
        MahjongTableSession session,
        TableRenderSnapshot snapshot,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan,
        int tileIndex
    ) {
        if (seat.playerId() == null || tileIndex < 0 || tileIndex >= seat.hand().size()) {
            return List.of();
        }

        boolean concealHand = snapshot.started();
        return List.of(DisplayEntities.tileDisplaySpec(
            toLocation(session, plan.publicHandPoints().get(tileIndex)),
            plan.yaw(),
            session.currentVariant(),
            concealHand ? MahjongTile.UNKNOWN : seat.hand().get(tileIndex),
            DisplayEntities.TileRenderPose.STANDING,
            null,
            true,
            null,
            List.of(seat.playerId())
        ));
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
        boolean lastTileWasHorizontal = false;
        int placedTileCount = 0;

        for (MeldView meld : melds) {
            Location kakanStackBase = null;
            float kakanStackYaw = yaw;
            Location firstTileBase = null;
            Offset kakanPlanarOffset = new Offset(0.0D, 0.0D);
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
                if (firstTileBase == null) {
                    firstTileBase = baseLocation.clone();
                }
                float tileYaw = isClaimTile ? yaw + meld.claimYawOffset() : yaw;
                spawned.add(spawnPublicTile(
                    session,
                    baseLocation.clone().add(0.0D, FLAT_TILE_Y, 0.0D),
                    tileYaw,
                    meld.tiles().get(i),
                    meld.faceDownAt(i) ? DisplayEntities.TileRenderPose.FLAT_FACE_DOWN : DisplayEntities.TileRenderPose.FLAT_FACE_UP
                ));
                // Added-kan tile should follow the target (claimed) tile position.
                if (isClaimTile) {
                    kakanStackBase = baseLocation.clone();
                    kakanStackYaw = tileYaw;
                    kakanPlanarOffset = kakanAdjacentOffset(wind, meld.claimYawOffset());
                }
                lastTileWasHorizontal = isClaimTile;
                placedTileCount++;
            }

            if (meld.hasAddedKanTile() && kakanStackBase == null) {
                kakanStackBase = firstTileBase;
                kakanStackYaw = yaw;
            }
            if (meld.hasAddedKanTile() && kakanStackBase != null) {
                spawned.add(spawnPublicTile(
                    session,
                    add(kakanStackBase, kakanPlanarOffset).add(0.0D, FLAT_TILE_Y + KAKAN_STACK_Y_OFFSET, 0.0D),
                    kakanStackYaw,
                    meld.addedKanTile(),
                    DisplayEntities.TileRenderPose.FLAT_FACE_UP
                ));
                lastTileWasHorizontal = false;
            }
        }
        return spawned;
    }

    public List<Entity> renderMelds(
        MahjongTableSession session,
        TableSeatRenderSnapshot seat,
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

    public List<DisplayEntities.EntitySpec> renderHandPrivateTileSpecs(
        MahjongTableSession session,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan,
        int tileIndex
    ) {
        if (seat.playerId() == null || tileIndex < 0 || tileIndex >= seat.hand().size()) {
            return List.of();
        }

        Location tileLocation = toLocation(session, plan.privateHandPoints().get(tileIndex));
        DisplayEntities.EntitySpec tileSpec = DisplayEntities.tileDisplaySpec(
            tileLocation,
            plan.yaw(),
            session.currentVariant(),
            seat.hand().get(tileIndex),
            DisplayEntities.TileRenderPose.STANDING,
            null,
            true,
            List.of(seat.playerId()),
            1.0F,
            null,
            null,
            true
        );
        return List.of(
            tileSpec,
            DisplayEntities.interactionSpec(
                handInteractionLocation(tileLocation),
                HAND_INTERACTION_WIDTH,
                HAND_INTERACTION_HEIGHT,
                DisplayClickAction.handTile(session.id(), seat.playerId(), tileIndex),
                List.of(seat.playerId())
            )
        );
    }

    public List<DisplayEntities.EntitySpec> renderMeldSpecs(
        MahjongTableSession session,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan
    ) {
        if (seat.playerId() == null) {
            return List.of();
        }
        List<DisplayEntities.EntitySpec> specs = new ArrayList<>(plan.meldPlacements().size());
        for (TableRenderLayout.TilePlacement placement : plan.meldPlacements()) {
            specs.add(publicTileSpec(session, placement));
        }
        return List.copyOf(specs);
    }

    private static Location displayCenter(MahjongTableSession session) {
        return session.center().add(0.0D, DISPLAY_CENTER_Y_OFFSET, 0.0D);
    }

    private static Location handInteractionLocation(Location tileLocation) {
        return tileLocation.clone().subtract(0.0D, UPRIGHT_TILE_Y, 0.0D);
    }

    public Location seatAnchorLocation(MahjongTableSession session, SeatWind wind) {
        return seatAnchorLocation(handDirectionBase(displayCenter(session), wind), wind);
    }

    public float seatFacingYaw(SeatWind wind) {
        return seatYaw(wind) + 180.0F;
    }

    private static Location seatAnchorLocation(Location handBase, SeatWind wind) {
        return seatBaseLocation(handBase, wind).add(0.0D, SEAT_ANCHOR_Y_OFFSET, 0.0D);
    }

    private static Location seatBaseLocation(Location handBase, SeatWind wind) {
        Offset forward = offsetTowardSeatFront(wind, SEAT_DISTANCE_FROM_HAND_BASE);
        return handBase.clone().add(forward.x(), SEAT_BASE_Y_OFFSET + SEAT_RAISE_Y_OFFSET, forward.z());
    }

    private static Location withSeatLabelDepthOffset(Location location, SeatWind wind, double amount) {
        Offset offset = offsetTowardSeatFront(wind, amount);
        return location.clone().add(offset.x(), 0.0D, offset.z());
    }

    private static Location seatLabelInteractionLocation(Location labelLocation) {
        return labelLocation.clone().subtract(0.0D, 0.35D, 0.0D);
    }

    private static List<Entity> renderSeatVisual(
        MahjongTableSession session,
        SeatWind wind,
        Location handBase,
        DisplayClickAction action
    ) {
        String seatFurnitureId = configuredSeatFurnitureId(session);
        if (seatFurnitureId != null) {
            Entity furniture = spawnSeatFurniture(session, seatBaseLocation(handBase, wind), wind, seatFurnitureId, action);
            if (furniture != null) {
                return List.of(furniture);
            }
        }

        List<Entity> spawned = new ArrayList<>(3);
        Location seatBase = seatBaseLocation(handBase, wind);
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(seatBase.clone(), SEAT_BASE_WIDTH, SEAT_BASE_HEIGHT, SEAT_BASE_WIDTH),
            Material.SMOOTH_STONE,
            (float) SEAT_BASE_WIDTH,
            (float) SEAT_BASE_HEIGHT,
            (float) SEAT_BASE_WIDTH,
            true,
            null,
            action
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(
                seatBase.clone().add(0.0D, SEAT_BASE_HEIGHT, 0.0D),
                SEAT_BASE_WIDTH - SEAT_CARPET_INSET * 2.0D,
                SEAT_CARPET_THICKNESS,
                SEAT_BASE_WIDTH - SEAT_CARPET_INSET * 2.0D
            ),
            Material.GREEN_WOOL,
            (float) (SEAT_BASE_WIDTH - SEAT_CARPET_INSET * 2.0D),
            (float) SEAT_CARPET_THICKNESS,
            (float) (SEAT_BASE_WIDTH - SEAT_CARPET_INSET * 2.0D),
            true,
            null,
            action
        ));

        Offset backrestOffset = offsetTowardSeatFront(wind, -SEAT_BACKREST_OFFSET);
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(seatBase.clone().add(backrestOffset.x(), SEAT_BACKREST_HEIGHT / 2.0D, backrestOffset.z()), SEAT_BACKREST_WIDTH, SEAT_BACKREST_HEIGHT, SEAT_BACKREST_DEPTH),
            Material.STRIPPED_OAK_WOOD,
            (float) SEAT_BACKREST_WIDTH,
            (float) SEAT_BACKREST_HEIGHT,
            (float) SEAT_BACKREST_DEPTH,
            true,
            null,
            action
        ));
        return spawned;
    }

    public List<DisplayEntities.EntitySpec> renderViewerOverlaySpecs(MahjongTableSession session, TableViewerOverlaySnapshot snapshot) {
        Location center = displayCenter(session);
        List<DisplayEntities.EntitySpec> specs = new ArrayList<>(snapshot.spectatorSeatOverlays().isEmpty() ? 1 : 1 + snapshot.spectatorSeatOverlays().size());
        if (session.isStarted() || snapshot.spectator()) {
            specs.add(DisplayEntities.labelSpec(
                center.clone().add(0.0D, 0.9D + FLOATING_TEXT_Y_OFFSET, 0.0D),
                snapshot.overlay(),
                Color.fromARGB(84, 12, 12, 12),
                List.of(snapshot.viewerId()),
                Display.Billboard.CENTER,
                0.0F,
                0.0F,
                true
            ));
        }
        if (snapshot.spectator()) {
            for (TableSpectatorSeatOverlaySnapshot seatOverlay : snapshot.spectatorSeatOverlays()) {
                specs.add(DisplayEntities.labelSpec(
                    add(handDirectionBase(center, seatOverlay.wind()), offsetAcrossSeat(seatOverlay.wind(), 0.42D)).add(0.0D, 0.62D + FLOATING_TEXT_Y_OFFSET, 0.0D),
                    seatOverlay.overlay(),
                    Color.fromARGB(92, 14, 14, 18),
                    List.of(snapshot.viewerId()),
                    Display.Billboard.CENTER,
                    0.0F,
                    0.0F,
                    true
                ));
            }
        }
        return List.copyOf(specs);
    }

    private static Entity spawnSeatFurniture(
        MahjongTableSession session,
        Location location,
        SeatWind wind,
        String furnitureId,
        DisplayClickAction action
    ) {
        if (session.plugin().craftEngine() == null || furnitureId == null || furnitureId.isBlank()) {
            return null;
        }
        return session.plugin().craftEngine().placeSeatFurniture(
            seatFurnitureAnchor(location, wind, furnitureId),
            furnitureId,
            action
        );
    }

    private static Location seatPlacementLocation(Location location, SeatWind wind) {
        Location placed = location.clone();
        placed.setYaw(seatYaw(wind) + 180.0F);
        placed.setPitch(0.0F);
        return placed;
    }

    private static String configuredTableFurnitureId(MahjongTableSession session) {
        return configuredFurnitureId(session, session.plugin().settings().craftEngineTableFurnitureId());
    }

    private static String configuredSeatFurnitureId(MahjongTableSession session) {
        return configuredFurnitureId(session, session.plugin().settings().craftEngineSeatFurnitureId());
    }

    private static String configuredFurnitureId(MahjongTableSession session, String configuredValue) {
        if (session.plugin().craftEngine() == null) {
            return null;
        }
        if (configuredValue == null) {
            return null;
        }
        String trimmed = configuredValue.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<Entity> renderTableHitboxes(MahjongTableSession session, Location tableCenter, boolean usingFurnitureVisual) {
        if (usingFurnitureVisual) {
            return List.of();
        }
        Entity furnitureHitbox = session.plugin().craftEngine().placeTableHitbox(tableCenter.clone());
        return furnitureHitbox == null ? List.of() : List.of(furnitureHitbox);
    }

    private static Entity spawnTableVisual(MahjongTableSession session, Location tableCenter) {
        String tableFurnitureId = configuredTableFurnitureId(session);
        if (tableFurnitureId == null) {
            return null;
        }
        return session.plugin().craftEngine().placeFurniture(tableFurnitureAnchor(tableCenter, tableFurnitureId), tableFurnitureId);
    }

    private static Location tableVisualAnchor(Location tableCenter) {
        return tableCenter.clone().add(0.0D, TABLE_VISUAL_Y_OFFSET, 0.0D);
    }

    static Location tableFurnitureAnchor(Location tableCenter, String furnitureId) {
        Location anchor = tableVisualAnchor(tableCenter);
        return usesBuiltinTableFurnitureAnchor(furnitureId) ? anchor : standardFurnitureAnchor(anchor);
    }

    static Location seatFurnitureAnchor(Location location, SeatWind wind, String furnitureId) {
        Location anchor = seatPlacementLocation(location, wind);
        return usesBuiltinSeatFurnitureAnchor(furnitureId) ? anchor : standardFurnitureAnchor(anchor);
    }

    private static boolean usesBuiltinTableFurnitureAnchor(String furnitureId) {
        return TABLE_VISUAL_FURNITURE_ID.equals(furnitureId);
    }

    private static boolean usesBuiltinSeatFurnitureAnchor(String furnitureId) {
        return SEAT_VISUAL_FURNITURE_ID.equals(furnitureId);
    }

    private static Location standardFurnitureAnchor(Location anchor) {
        return anchor.clone().subtract(0.0D, CUSTOM_FURNITURE_ORIGIN_Y_OFFSET, 0.0D);
    }

    private static DisplayClickAction seatVisualAction(MahjongTableSession session, SeatWind wind) {
        if (session == null || wind == null) {
            return null;
        }
        UUID seatedPlayer = session.playerAt(wind);
        if (seatedPlayer == null) {
            return DisplayClickAction.joinSeat(session.id(), wind);
        }
        if (!session.isStarted()) {
            return DisplayClickAction.toggleReady(session.id(), wind);
        }
        return null;
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
            session.currentVariant(),
            tile,
            pose,
            null,
            true
        );
    }

    private static Entity spawnPublicTile(MahjongTableSession session, TableRenderLayout.TilePlacement placement) {
        return spawnPublicTile(session, toLocation(session, placement.point()), placement.yaw(), placement.tile(), placement.pose());
    }

    private static DisplayEntities.TileDisplaySpec publicTileSpec(MahjongTableSession session, TableRenderLayout.TilePlacement placement) {
        return DisplayEntities.tileDisplaySpec(
            toLocation(session, placement.point()),
            placement.yaw(),
            session.currentVariant(),
            placement.tile(),
            placement.pose(),
            null,
            true
        );
    }

    private static Entity spawnCenterLastDiscardTile(
        MahjongTableSession session,
        Location center,
        MahjongTile tile
    ) {
        return DisplayEntities.spawnTileDisplay(
            session.plugin(),
            center.clone().add(0.0D, CENTER_LAST_DISCARD_TILE_Y_OFFSET, 0.0D),
            0.0F,
            session.currentVariant(),
            tile,
            DisplayEntities.TileRenderPose.STANDING,
            null,
            true,
            null,
            CENTER_LAST_DISCARD_TILE_SCALE,
            CENTER_LAST_DISCARD_TILE_GLOW,
            Display.Billboard.CENTER
        );
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

        SeatWind direction = placements.getLast().face();
        List<DeadWallPlacementMutable> reversed = placements.reversed();
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

            Location base = reversed.getFirst().location();
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
            case EAST -> center.clone().add(paddingFromCenter, STICK_Y_OFFSET, 0.0D);
            case SOUTH -> center.clone().add(0.0D, STICK_Y_OFFSET, paddingFromCenter);
            case WEST -> center.clone().add(-paddingFromCenter, STICK_Y_OFFSET, 0.0D);
            case NORTH -> center.clone().add(0.0D, STICK_Y_OFFSET, -paddingFromCenter);
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
        return add(start, multiply(cornerStickOffset(wind), stickIndex)).add(0.0D, STICK_Y_OFFSET + stackIndex * (STICK_HEIGHT + TILE_PADDING), 0.0D);
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

    private static Offset horizontalTileGravityOffset(SeatWind wind) {
        double amount = (TILE_HEIGHT - TILE_WIDTH) / 2.0D;
        return switch (wind) {
            case EAST -> new Offset(amount, 0.0D);
            case SOUTH -> new Offset(0.0D, amount);
            case WEST -> new Offset(-amount, 0.0D);
            case NORTH -> new Offset(0.0D, -amount);
        };
    }

    private static Offset kakanAdjacentOffset(SeatWind wind, int claimYawOffset) {
        int direction = claimYawOffset == 0 ? 1 : Integer.signum(claimYawOffset);
        double amount = TILE_WIDTH - TILE_PADDING;
        return offsetTowardSeatFront(wind, amount * direction);
    }

    private static Offset offsetAcrossSeat(SeatWind wind, double amount) {
        return switch (wind) {
            case EAST -> new Offset(0.0D, amount);
            case SOUTH -> new Offset(-amount, 0.0D);
            case WEST -> new Offset(0.0D, -amount);
            case NORTH -> new Offset(amount, 0.0D);
        };
    }

    private static Offset offsetTowardSeatFront(SeatWind wind, double amount) {
        return switch (wind) {
            case EAST -> new Offset(amount, 0.0D);
            case SOUTH -> new Offset(0.0D, amount);
            case WEST -> new Offset(-amount, 0.0D);
            case NORTH -> new Offset(0.0D, -amount);
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



