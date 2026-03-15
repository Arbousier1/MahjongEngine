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
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;

public final class TableRenderer {
    private static final double HAND_RADIUS = 1.45D;
    private static final double MELD_RADIUS = 1.13D;
    private static final double WALL_RADIUS = 1.9D;
    private static final double DORA_RADIUS = 0.42D;
    private static final double HAND_STEP = 0.16D;
    private static final double WALL_STEP = 0.145D;
    private static final double MELD_GAP = 0.07D;
    private static final int EXPECTED_ENTITY_COUNT = 256;

    public List<Entity> render(MahjongTableSession session) {
        List<Entity> spawned = new ArrayList<>(EXPECTED_ENTITY_COUNT);
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
                .add(0.0D, layer * 0.08D, 0.0D);
            spawned.add(DisplayEntities.spawnTileDisplay(session.plugin(), tileLocation, seatYaw(wind), MahjongTile.M1, true, null, true));
        }
        return spawned;
    }

    public List<Entity> renderDora(MahjongTableSession session) {
        Location center = displayCenter(session);
        List<MahjongTile> dora = session.doraIndicators();
        List<Entity> spawned = new ArrayList<>(dora.size());
        for (int i = 0; i < dora.size(); i++) {
            Location tileLocation = center.clone().add(centeredOffset(Math.max(1, dora.size()), i, HAND_STEP), 0.08D, -DORA_RADIUS);
            spawned.add(DisplayEntities.spawnTileDisplay(session.plugin(), tileLocation, 0.0F, dora.get(i), false, null, true));
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
            Location tileLocation = offsetAlongSeat(handBase, wind, centeredOffset(hand.size(), i, HAND_STEP));
            DisplayClickAction clickAction = new DisplayClickAction(session.id(), playerId, i);
            ItemDisplay publicDisplay = DisplayEntities.spawnTileDisplay(
                session.plugin(),
                tileLocation,
                yaw,
                hand.get(i),
                true,
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
                null,
                true,
                ownerOnly
            );
            spawned.add(privateDisplay);
            spawned.add(DisplayEntities.spawnInteraction(
                tileLocation.clone().add(0.0D, 0.02D, 0.0D),
                0.32F,
                0.48F,
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
        float yaw = seatYaw(wind);
        List<MahjongTile> discards = session.discards(playerId);
        List<Entity> spawned = new ArrayList<>(discards.size());
        for (int i = 0; i < discards.size(); i++) {
            int row = i / 6;
            int column = i % 6;
            Location discardBase = seatBase(center, wind, 0.75D + row * 0.22D);
            Location tileLocation = offsetAlongSeat(discardBase, wind, centeredOffset(6, column, HAND_STEP));
            spawned.add(DisplayEntities.spawnTileDisplay(session.plugin(), tileLocation, yaw, discards.get(i), false, null, true));
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
                Location tileLocation = offsetAlongSeat(meldBase, wind, cursor);
                spawned.add(DisplayEntities.spawnTileDisplay(session.plugin(), tileLocation, yaw, meld.tiles().get(i), meld.faceDownAt(i), null, true));
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
        return switch (wind) {
            case EAST -> -90.0F;
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
        };
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
