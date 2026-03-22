package doublemoon.mahjongcraft.paper.table.render;

import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.render.scene.MeldView;
import doublemoon.mahjongcraft.paper.render.layout.TableRenderLayout;
import doublemoon.mahjongcraft.paper.table.core.MahjongTableSession;
import doublemoon.mahjongcraft.paper.table.core.TableRenderSnapshot;
import doublemoon.mahjongcraft.paper.table.core.TableSeatRenderSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class TableRegionFingerprintService {
    private static final String REGION_TABLE = "table";
    private static final String REGION_WALL = "wall";
    private static final String REGION_DORA = "dora";
    private static final String REGION_CENTER = "center";

    public Map<String, String> precomputeRegionFingerprints(MahjongTableSession session, TableRenderSnapshot snapshot) {
        Map<String, String> fingerprints = new HashMap<>();
        fingerprints.put(REGION_TABLE, this.tableFingerprint(session, snapshot));
        fingerprints.put(REGION_WALL, this.wallFingerprint(snapshot));
        fingerprints.put(REGION_DORA, this.doraFingerprint(snapshot));
        fingerprints.put(REGION_CENTER, this.centerFingerprint(snapshot));
        for (SeatWind wind : SeatWind.values()) {
            TableSeatRenderSnapshot seat = snapshot.seat(wind);
            fingerprints.put(this.seatRegionKey("visual", wind), this.seatVisualFingerprint(session, wind));
            fingerprints.put(this.seatRegionKey("labels", wind), this.seatLabelFingerprint(snapshot, seat));
            fingerprints.put(this.seatRegionKey("sticks", wind), this.stickFingerprint(snapshot, seat));
            fingerprints.put(this.seatRegionKey("hand-public", wind), this.handPublicFingerprint(snapshot, seat));
            fingerprints.put(this.seatRegionKey("melds", wind), this.meldFingerprint(seat));
        }
        return Map.copyOf(fingerprints);
    }

    public String handPrivateTileFingerprint(TableSeatRenderSnapshot seat, TableRenderLayout.SeatLayoutPlan plan, int tileIndex) {
        TableRenderLayout.Point point = plan.privateHandPoints().get(tileIndex);
        return fingerprintBuilder(160)
            .field("hand-private-tile")
            .field(seat.wind().name())
            .field(Objects.toString(seat.playerId(), "empty"))
            .field(tileIndex)
            .field(seat.online())
            .field(seat.hand().size())
            .field(tileIndex == seat.selectedHandTileIndex())
            .field(Double.doubleToLongBits(point.x()))
            .field(Double.doubleToLongBits(point.y()))
            .field(Double.doubleToLongBits(point.z()))
            .field(seat.hand().get(tileIndex).name())
            .toString();
    }

    public String handPublicTileFingerprint(
        TableRenderSnapshot snapshot,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan,
        int tileIndex
    ) {
        TableRenderLayout.Point point = plan.publicHandPoints().get(tileIndex);
        boolean concealHand = snapshot.started();
        return fingerprintBuilder(160)
            .field("hand-public-tile")
            .field(seat.wind().name())
            .field(Objects.toString(seat.playerId(), "empty"))
            .field(tileIndex)
            .field(snapshot.started())
            .field(seat.online())
            .field(seat.viewerMembershipSignature())
            .field(seat.stickLayoutCount())
            .field(Double.doubleToLongBits(point.x()))
            .field(Double.doubleToLongBits(point.y()))
            .field(Double.doubleToLongBits(point.z()))
            .field(concealHand ? "unknown" : seat.hand().get(tileIndex).name())
            .toString();
    }

    public String discardTileFingerprint(TableSeatRenderSnapshot seat, TableRenderLayout.SeatLayoutPlan plan, int discardIndex) {
        TableRenderLayout.TilePlacement placement = plan.discardPlacements().get(discardIndex);
        return fingerprintBuilder(160)
            .field("discard-tile")
            .field(seat.wind().name())
            .field(Objects.toString(seat.playerId(), "empty"))
            .field(discardIndex)
            .field(seat.riichiDiscardIndex())
            .field(Float.floatToIntBits(placement.yaw()))
            .field(Double.doubleToLongBits(placement.point().x()))
            .field(Double.doubleToLongBits(placement.point().y()))
            .field(Double.doubleToLongBits(placement.point().z()))
            .field(placement.tile().name())
            .field(placement.pose().name())
            .toString();
    }

    String wallTileFingerprint(TableRenderLayout.LayoutPlan plan, int wallIndex) {
        TableRenderLayout.TilePlacement placement = plan.wallTiles().get(wallIndex);
        if (placement == null) {
            return fingerprintBuilder(32)
                .field("wall-empty")
                .field(wallIndex)
                .toString();
        }
        return fingerprintBuilder(160)
            .field("wall-tile")
            .field(wallIndex)
            .field(Float.floatToIntBits(placement.yaw()))
            .field(Double.doubleToLongBits(placement.point().x()))
            .field(Double.doubleToLongBits(placement.point().y()))
            .field(Double.doubleToLongBits(placement.point().z()))
            .field(placement.tile().name())
            .field(placement.pose().name())
            .toString();
    }

    private String wallFingerprint(TableRenderSnapshot snapshot) {
        if (!snapshot.started()) {
            return "waiting";
        }
        return fingerprintBuilder(32)
            .field("wall")
            .field(snapshot.started())
            .field(snapshot.gameFinished())
            .field(snapshot.remainingWallCount())
            .toString();
    }

    private String tableFingerprint(MahjongTableSession session, TableRenderSnapshot snapshot) {
        return fingerprintBuilder(48)
            .field("table")
            .field(snapshot.worldName())
            .field((int) Math.floor(snapshot.centerX()))
            .field((int) Math.floor(snapshot.centerY()))
            .field((int) Math.floor(snapshot.centerZ()))
            .field(Objects.toString(session.plugin().settings().craftEngineTableFurnitureId(), ""))
            .toString();
    }

    private String doraFingerprint(TableRenderSnapshot snapshot) {
        if (!snapshot.started()) {
            return "dora:waiting";
        }
        FingerprintBuilder builder = fingerprintBuilder(64)
            .field("dora")
            .field(snapshot.started())
            .field(snapshot.doraIndicators().size());
        snapshot.doraIndicators().forEach(tile -> builder.raw(tile.name()).raw(','));
        return builder.toString();
    }

    private String centerFingerprint(TableRenderSnapshot snapshot) {
        return fingerprintBuilder(192)
            .field("center")
            .field(snapshot.started() ? "started" : "waiting")
            .field(snapshot.publicCenterText())
            .field(snapshot.lastPublicDiscardPlayerId())
            .field(snapshot.lastPublicDiscardTile())
            .toString();
    }

    private String seatLabelFingerprint(TableRenderSnapshot snapshot, TableSeatRenderSnapshot seat) {
        return fingerprintBuilder(128)
            .field("labels")
            .field(seat.wind().name())
            .field(snapshot.currentSeat().name())
            .field(Objects.toString(seat.playerId(), "empty"))
            .field(seat.displayName())
            .field(seat.publicSeatStatus())
            .field(seat.riichi())
            .field(seat.ready())
            .field(seat.queuedToLeave())
            .toString();
    }

    private String seatVisualFingerprint(MahjongTableSession session, SeatWind wind) {
        return fingerprintBuilder(96)
            .field("visual")
            .field(wind.name())
            .field(Objects.toString(session.plugin().settings().craftEngineSeatFurnitureId(), ""))
            .toString();
    }

    private String handPublicFingerprint(TableRenderSnapshot snapshot, TableSeatRenderSnapshot seat) {
        FingerprintBuilder builder = fingerprintBuilder(256)
            .field("hand-public")
            .field(seat.wind().name())
            .field(Objects.toString(seat.playerId(), "empty"));
        if (seat.playerId() == null) {
            return builder.toString();
        }
        builder.field(snapshot.started())
            .field(seat.online())
            .field(seat.viewerMembershipSignature())
            .field(seat.stickLayoutCount());
        seat.hand().forEach(tile -> builder.field(tile.name()));
        return builder.toString();
    }

    private String meldFingerprint(TableSeatRenderSnapshot seat) {
        FingerprintBuilder builder = fingerprintBuilder(256)
            .field("melds")
            .field(seat.wind().name())
            .field(Objects.toString(seat.playerId(), "empty"));
        if (seat.playerId() == null) {
            return builder.toString();
        }
        builder.field(seat.stickLayoutCount());
        seat.melds().forEach(meld -> this.appendMeldFingerprint(builder, meld));
        return builder.toString();
    }

    private String stickFingerprint(TableRenderSnapshot snapshot, TableSeatRenderSnapshot seat) {
        FingerprintBuilder builder = fingerprintBuilder(128)
            .field("sticks")
            .field(seat.wind().name())
            .field(Objects.toString(seat.playerId(), "empty"))
            .field(snapshot.honbaCount())
            .field(snapshot.dealerSeat().name());
        if (seat.playerId() == null) {
            return builder.toString();
        }
        builder.field(seat.riichi());
        seat.scoringSticks().forEach(stick -> builder.field(stick.name()));
        seat.cornerSticks().forEach(stick -> builder.field(stick.name()));
        return builder.toString();
    }

    private void appendMeldFingerprint(FingerprintBuilder builder, MeldView meld) {
        builder.raw('[')
            .raw(meld.claimTileIndex())
            .raw('/')
            .raw(meld.claimYawOffset())
            .raw('/')
            .raw(Objects.toString(meld.addedKanTile(), "none"))
            .raw(':');
        for (int i = 0; i < meld.tiles().size(); i++) {
            builder.raw(meld.tiles().get(i).name())
                .raw('/')
                .raw(meld.faceDownAt(i))
                .raw(',');
        }
        builder.raw(']');
    }

    private String seatRegionKey(String region, SeatWind wind) {
        return region + ":" + wind.name();
    }

    private static FingerprintBuilder fingerprintBuilder(int capacity) {
        return new FingerprintBuilder(capacity);
    }

    private static final class FingerprintBuilder {
        private final StringBuilder delegate;
        private boolean needsSeparator;

        private FingerprintBuilder(int capacity) {
            this.delegate = new StringBuilder(capacity);
        }

        private FingerprintBuilder field(Object value) {
            if (this.needsSeparator) {
                this.delegate.append(':');
            }
            this.delegate.append(Objects.toString(value, ""));
            this.needsSeparator = true;
            return this;
        }

        private FingerprintBuilder raw(Object value) {
            this.delegate.append(value);
            return this;
        }

        @Override
        public String toString() {
            return this.delegate.toString();
        }
    }
}


