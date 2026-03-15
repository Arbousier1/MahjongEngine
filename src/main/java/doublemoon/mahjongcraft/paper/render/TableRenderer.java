package doublemoon.mahjongcraft.paper.render;

import doublemoon.mahjongcraft.paper.model.MahjongTile;
import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.table.MahjongTableSession;
import java.util.ArrayList;
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
    private static final double TILE_WIDTH = 0.1125D;
    private static final double TILE_HEIGHT = 0.15D;
    private static final double TILE_DEPTH = 0.075D;
    private static final double TILE_PADDING = 0.01D;
    private static final double HAND_RADIUS = 1.76D;
    private static final double MELD_RADIUS = 1.38D;
    private static final double WALL_RADIUS = 1.68D;
    private static final double DISCARD_RADIUS = 0.72D;
    private static final double DORA_RADIUS = 0.28D;
    private static final double HAND_STEP = TILE_WIDTH + TILE_PADDING;
    private static final double WALL_STEP = TILE_WIDTH + TILE_PADDING;
    private static final double DISCARD_ROW_STEP = TILE_HEIGHT + TILE_PADDING * 3.0D;
    private static final double MELD_GAP = TILE_WIDTH * 0.65D;
    private static final double WALL_LAYER_OFFSET = TILE_DEPTH + TILE_PADDING;
    private static final double UPRIGHT_TILE_Y = TILE_HEIGHT / 2.0D + 0.01D;
    private static final double FLAT_TILE_Y = 0.02D;
    private static final double TABLE_TOP_SIZE = 4.45D;
    private static final double TABLE_FELT_SIZE = 4.08D;
    private static final double TABLE_TOP_THICKNESS = 0.12D;
    private static final double TABLE_FRAME_WIDTH = 0.19D;
    private static final double TABLE_FRAME_HEIGHT = 0.18D;
    private static final double TABLE_APRON_DROP = 0.42D;
    private static final double TABLE_APRON_THICKNESS = 0.14D;
    private static final double TABLE_BASE_SIZE = 3.18D;
    private static final double TABLE_BASE_HEIGHT = 0.32D;
    private static final double TABLE_TOP_Y_OFFSET = -0.08D;
    private static final double TABLE_BASE_Y_OFFSET = -0.56D;
    private static final int DISCARDS_PER_ROW = 6;
    private static final int EXPECTED_ENTITY_COUNT = 256;

    public List<Entity> render(MahjongTableSession session) {
        List<Entity> spawned = new ArrayList<>(EXPECTED_ENTITY_COUNT);
        spawned.addAll(this.renderTableStructure(session));
        for (SeatWind wind : SeatWind.values()) {
            spawned.addAll(this.renderSeatLabels(session, wind));
            spawned.addAll(this.renderHand(session, wind));
            spawned.addAll(this.renderDiscards(session, wind));
            spawned.addAll(this.renderMelds(session, wind));
        }

        spawned.addAll(this.renderWall(session));
        spawned.addAll(this.renderDora(session));
        spawned.addAll(this.renderCenterLabel(session));
        return spawned;
    }

    public List<Entity> renderTableStructure(MahjongTableSession session) {
        Location center = displayCenter(session);
        List<Entity> spawned = new ArrayList<>(8);
        Location topCenter = center.clone().add(0.0D, TABLE_TOP_Y_OFFSET, 0.0D);

        spawned.add(DisplayEntities.spawnBlockDisplay(
            centeredCuboid(topCenter, TABLE_TOP_SIZE, TABLE_TOP_THICKNESS, TABLE_TOP_SIZE),
            Material.OAK_PLANKS,
            (float) TABLE_TOP_SIZE,
            (float) TABLE_TOP_THICKNESS,
            (float) TABLE_TOP_SIZE
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            centeredCuboid(topCenter.clone().add(0.0D, 0.005D, 0.0D), TABLE_FELT_SIZE, 0.035D, TABLE_FELT_SIZE),
            Material.LIME_CONCRETE,
            (float) TABLE_FELT_SIZE,
            0.035F,
            (float) TABLE_FELT_SIZE
        ));

        double outerHalf = TABLE_TOP_SIZE / 2.0D;
        double innerHalf = TABLE_FELT_SIZE / 2.0D;
        double railLength = TABLE_FELT_SIZE;
        spawned.add(DisplayEntities.spawnBlockDisplay(
            centeredCuboid(topCenter.clone().add(0.0D, 0.01D, -(innerHalf + TABLE_FRAME_WIDTH / 2.0D)), railLength, TABLE_FRAME_HEIGHT, TABLE_FRAME_WIDTH),
            Material.STRIPPED_OAK_WOOD,
            (float) railLength,
            (float) TABLE_FRAME_HEIGHT,
            (float) TABLE_FRAME_WIDTH
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            centeredCuboid(topCenter.clone().add(0.0D, 0.01D, innerHalf + TABLE_FRAME_WIDTH / 2.0D), railLength, TABLE_FRAME_HEIGHT, TABLE_FRAME_WIDTH),
            Material.STRIPPED_OAK_WOOD,
            (float) railLength,
            (float) TABLE_FRAME_HEIGHT,
            (float) TABLE_FRAME_WIDTH
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            centeredCuboid(topCenter.clone().add(-(innerHalf + TABLE_FRAME_WIDTH / 2.0D), 0.01D, 0.0D), TABLE_FRAME_WIDTH, TABLE_FRAME_HEIGHT, railLength),
            Material.STRIPPED_OAK_WOOD,
            (float) TABLE_FRAME_WIDTH,
            (float) TABLE_FRAME_HEIGHT,
            (float) railLength
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            centeredCuboid(topCenter.clone().add(innerHalf + TABLE_FRAME_WIDTH / 2.0D, 0.01D, 0.0D), TABLE_FRAME_WIDTH, TABLE_FRAME_HEIGHT, railLength),
            Material.STRIPPED_OAK_WOOD,
            (float) TABLE_FRAME_WIDTH,
            (float) TABLE_FRAME_HEIGHT,
            (float) railLength
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            centeredCuboid(center.clone().add(0.0D, TABLE_BASE_Y_OFFSET, 0.0D), TABLE_BASE_SIZE, TABLE_BASE_HEIGHT, TABLE_BASE_SIZE),
            Material.SPRUCE_PLANKS,
            (float) TABLE_BASE_SIZE,
            (float) TABLE_BASE_HEIGHT,
            (float) TABLE_BASE_SIZE
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            centeredCuboid(center.clone().add(0.0D, TABLE_APRON_DROP, 0.0D), TABLE_TOP_SIZE - 0.22D, TABLE_APRON_THICKNESS, TABLE_TOP_SIZE - 0.22D),
            Material.SMOOTH_SANDSTONE,
            (float) (TABLE_TOP_SIZE - 0.22D),
            (float) TABLE_APRON_THICKNESS,
            (float) (TABLE_TOP_SIZE - 0.22D)
        ));
        return spawned;
    }

    public List<Entity> renderSeatLabels(MahjongTableSession session, SeatWind wind) {
        List<Entity> spawned = new ArrayList<>(2);
        Location center = displayCenter(session);
        UUID playerId = session.playerAt(wind);
        Location handBase = seatBase(center, wind, HAND_RADIUS);
        boolean active = session.currentSeat() == wind;

        spawned.add(DisplayEntities.spawnLabel(
            handBase.clone().add(0.0D, 0.45D, 0.0D),
            Component.text(session.publicSeatStatus(wind)),
            seatLabelColor(wind, active)
        ));
        if (playerId != null) {
            spawned.add(DisplayEntities.spawnLabel(
                handBase.clone().add(0.0D, 0.26D, 0.0D),
                Component.text(session.displayName(playerId, session.publicLocale())),
                Color.fromARGB(100, 18, 18, 18)
            ));
        }
        return spawned;
    }

    public List<Entity> renderWall(MahjongTableSession session) {
        Location center = displayCenter(session);
        int wallCount = session.remainingWall().size();
        List<Entity> spawned = new ArrayList<>(wallCount);
        for (int i = 0; i < wallCount; i++) {
            SeatWind wind = WallLayout.wallSeat(i);
            int sideIndex = WallLayout.wallColumn(i);
            int layer = WallLayout.wallLayer(i);
            Location wallBase = seatBase(center, wind, WALL_RADIUS);
            Location tileLocation = offsetAlongSeat(wallBase, wind, centeredOffset(17, sideIndex, WALL_STEP))
                .add(0.0D, UPRIGHT_TILE_Y + layer * WALL_LAYER_OFFSET, 0.0D);
            spawned.add(DisplayEntities.spawnTileDisplay(session.plugin(), tileLocation, seatYaw(wind), MahjongTile.M1, true, false, null, true));
        }
        return spawned;
    }

    public List<Entity> renderDora(MahjongTableSession session) {
        Location center = displayCenter(session);
        List<MahjongTile> dora = session.doraIndicators();
        List<Entity> spawned = new ArrayList<>(dora.size());
        for (int i = 0; i < dora.size(); i++) {
            Location tileLocation = center.clone().add(centeredOffset(Math.max(1, dora.size()), i, HAND_STEP), FLAT_TILE_Y, -DORA_RADIUS);
            spawned.add(DisplayEntities.spawnTileDisplay(session.plugin(), tileLocation, 0.0F, dora.get(i), false, true, null, true));
        }
        return spawned;
    }

    public List<Entity> renderCenterLabel(MahjongTableSession session) {
        Location center = displayCenter(session);
        return List.of(DisplayEntities.spawnLabel(
            center.clone().add(0.0D, 0.3D, 0.0D),
            Component.text(session.publicCenterText()),
            Color.fromARGB(112, 20, 80, 20)
        ));
    }

    public List<Entity> renderViewerOverlay(MahjongTableSession session, Player viewer) {
        Location center = displayCenter(session);
        UUID viewerId = viewer.getUniqueId();
        List<Entity> spawned = new ArrayList<>(session.isSpectator(viewerId) ? 5 : 1);
        if (session.isStarted() || session.isSpectator(viewerId)) {
            spawned.add(DisplayEntities.spawnLabel(
                center.clone().add(0.0D, 0.9D, 0.0D),
                session.viewerOverlay(viewer),
                Color.fromARGB(84, 12, 12, 12),
                List.of(viewerId)
            ));
        }
        if (session.isSpectator(viewerId)) {
            for (SeatWind wind : SeatWind.values()) {
                spawned.add(DisplayEntities.spawnLabel(
                    seatBase(center, wind, HAND_RADIUS + 0.42D).add(0.0D, 0.62D, 0.0D),
                    session.spectatorSeatOverlay(viewer, wind),
                    Color.fromARGB(92, 14, 14, 18),
                    List.of(viewerId)
                ));
            }
        }
        return spawned;
    }

    public List<Entity> renderHand(MahjongTableSession session, SeatWind wind) {
        Location center = displayCenter(session);
        UUID playerId = session.playerAt(wind);
        if (playerId == null) {
            return List.of();
        }
        Location handBase = seatBase(center, wind, HAND_RADIUS);
        float yaw = seatYaw(wind);
        List<MahjongTile> hand = session.hand(playerId);
        List<UUID> ownerOnly = List.of(playerId);
        List<UUID> othersOnly = session.viewerIdsExcluding(playerId);
        List<Entity> spawned = new ArrayList<>(hand.size() * 2);
        for (int i = 0; i < hand.size(); i++) {
            Location tileLocation = offsetAlongSeat(handBase, wind, centeredOffset(hand.size(), i, HAND_STEP)).add(0.0D, UPRIGHT_TILE_Y, 0.0D);
            DisplayClickAction clickAction = new DisplayClickAction(session.id(), playerId, i);
            ItemDisplay publicDisplay = DisplayEntities.spawnTileDisplay(
                session.plugin(),
                tileLocation,
                yaw,
                hand.get(i),
                true,
                false,
                null,
                true,
                othersOnly
            );
            spawned.add(publicDisplay);
            ItemDisplay privateDisplay = DisplayEntities.spawnTileDisplay(
                session.plugin(),
                tileLocation,
                yaw,
                hand.get(i),
                false,
                false,
                null,
                true,
                ownerOnly
            );
            spawned.add(privateDisplay);
            spawned.add(DisplayEntities.spawnInteraction(
                tileLocation.clone().add(0.0D, 0.02D, 0.0D),
                0.20F,
                0.24F,
                clickAction,
                ownerOnly
            ));
        }
        return spawned;
    }

    public List<Entity> renderDiscards(MahjongTableSession session, SeatWind wind) {
        Location center = displayCenter(session);
        UUID playerId = session.playerAt(wind);
        if (playerId == null) {
            return List.of();
        }
        List<MahjongTile> discards = session.discards(playerId);
        int riichiDiscardIndex = session.riichiDiscardIndex(playerId);
        List<Entity> spawned = new ArrayList<>(discards.size());
        for (int row = 0; row * DISCARDS_PER_ROW < discards.size(); row++) {
            int rowStart = row * DISCARDS_PER_ROW;
            int rowSize = Math.min(DISCARDS_PER_ROW, discards.size() - rowStart);
            double cursor = -DiscardLayout.rowFootprint(TILE_WIDTH, TILE_HEIGHT, TILE_PADDING, rowStart, rowSize, riichiDiscardIndex) / 2.0D;
            Location discardBase = seatBase(center, wind, DISCARD_RADIUS + row * DISCARD_ROW_STEP);
            for (int column = 0; column < rowSize; column++) {
                int discardIndex = rowStart + column;
                boolean riichiTile = discardIndex == riichiDiscardIndex;
                double footprint = DiscardLayout.discardFootprint(TILE_WIDTH, TILE_HEIGHT, riichiTile);
                cursor += footprint / 2.0D;
                Location tileLocation = offsetAlongSeat(discardBase, wind, cursor).add(0.0D, FLAT_TILE_Y, 0.0D);
                spawned.add(DisplayEntities.spawnTileDisplay(
                    session.plugin(),
                    tileLocation,
                    DiscardLayout.discardYaw(wind, riichiTile),
                    discards.get(discardIndex),
                    false,
                    true,
                    null,
                    true
                ));
                cursor += footprint / 2.0D + TILE_PADDING;
            }
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

        int tileCount = 0;
        for (MeldView meld : melds) {
            tileCount += meld.tiles().size();
        }
        List<Entity> spawned = new ArrayList<>(tileCount);
        double totalWidth = 0.0D;
        for (MeldView meld : melds) {
            totalWidth += meld.tiles().size() * HAND_STEP;
        }
        totalWidth += Math.max(0, melds.size() - 1) * MELD_GAP;

        double cursor = -totalWidth / 2.0D + HAND_STEP / 2.0D;
        Location meldBase = seatBase(center, wind, MELD_RADIUS);
        for (int meldIndex = 0; meldIndex < melds.size(); meldIndex++) {
            MeldView meld = melds.get(meldIndex);
            for (int i = 0; i < meld.tiles().size(); i++) {
                Location tileLocation = offsetAlongSeat(meldBase, wind, cursor).add(0.0D, UPRIGHT_TILE_Y, 0.0D);
                spawned.add(DisplayEntities.spawnTileDisplay(session.plugin(), tileLocation, yaw, meld.tiles().get(i), meld.faceDownAt(i), false, null, true));
                cursor += HAND_STEP;
            }
            if (meldIndex + 1 < melds.size()) {
                cursor += MELD_GAP;
            }
        }
        return spawned;
    }

    private static Location displayCenter(MahjongTableSession session) {
        return session.center().add(0.0D, 1.02D, 0.0D);
    }

    private static Location centeredCuboid(Location anchor, double width, double height, double depth) {
        return anchor.clone().add(-width / 2.0D, 0.0D, -depth / 2.0D);
    }

    private static Location seatBase(Location center, SeatWind wind, double radius) {
        return switch (wind) {
            case EAST -> center.clone().add(radius, 0.0D, 0.0D);
            case SOUTH -> center.clone().add(0.0D, 0.0D, radius);
            case WEST -> center.clone().add(-radius, 0.0D, 0.0D);
            case NORTH -> center.clone().add(0.0D, 0.0D, -radius);
        };
    }

    private static Location offsetAlongSeat(Location base, SeatWind wind, double offset) {
        return switch (wind) {
            case EAST, WEST -> base.clone().add(0.0D, 0.0D, offset);
            case SOUTH, NORTH -> base.clone().add(offset, 0.0D, 0.0D);
        };
    }

    private static double centeredOffset(int size, int index, double step) {
        return (index - (size - 1) / 2.0D) * step;
    }

    private static float seatYaw(SeatWind wind) {
        return DiscardLayout.seatYaw(wind);
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
}
