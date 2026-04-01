package top.ellan.mahjong.table.render;

import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.render.layout.TableRenderLayout;
import top.ellan.mahjong.table.core.MahjongTableSession;
import top.ellan.mahjong.table.core.TableRenderSnapshot;
import top.ellan.mahjong.table.core.TableSeatRenderSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class TableRegionFingerprintService {
    private static final String REGION_TABLE = "table";
    private static final String REGION_WALL = "wall";
    private static final String REGION_DORA = "dora";
    private static final String REGION_CENTER = "center";

    public Map<String, Long> precomputeRegionFingerprints(MahjongTableSession session, TableRenderSnapshot snapshot) {
        Map<String, Long> fingerprints = new HashMap<>();
        fingerprints.put(REGION_TABLE, this.tableFingerprint(session, snapshot));
        fingerprints.put(REGION_WALL, this.wallFingerprint(snapshot));
        fingerprints.put(REGION_DORA, this.doraFingerprint(snapshot));
        fingerprints.put(REGION_CENTER, this.centerFingerprint(snapshot));
        for (SeatWind wind : SeatWind.values()) {
            TableSeatRenderSnapshot seat = snapshot.seat(wind);
            fingerprints.put(this.seatRegionKey("visual", wind), this.seatVisualFingerprint(session, wind));
            fingerprints.put(this.seatRegionKey("labels", wind), this.seatLabelFingerprint(session, snapshot, seat));
            fingerprints.put(this.seatRegionKey("sticks", wind), this.stickFingerprint(snapshot, seat));
            fingerprints.put(this.seatRegionKey("hand-public", wind), this.handPublicFingerprint(snapshot, seat));
        }
        return Map.copyOf(fingerprints);
    }

    public long handPrivateTileFingerprint(TableSeatRenderSnapshot seat, TableRenderLayout.SeatLayoutPlan plan, int tileIndex) {
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
            .value();
    }

    public long handPublicTileFingerprint(
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
            .value();
    }

    public long discardTileFingerprint(TableSeatRenderSnapshot seat, TableRenderLayout.SeatLayoutPlan plan, int discardIndex) {
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
            .value();
    }

    long wallTileFingerprint(TableRenderLayout.LayoutPlan plan, int wallIndex) {
        TableRenderLayout.TilePlacement placement = plan.wallTiles().get(wallIndex);
        if (placement == null) {
            return fingerprintBuilder(32)
                .field("wall-empty")
                .field(wallIndex)
                .value();
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
            .value();
    }

    public long opaqueFingerprint(String value) {
        return fingerprintBuilder(64)
            .field("opaque")
            .field(value)
            .value();
    }

    private long wallFingerprint(TableRenderSnapshot snapshot) {
        if (!snapshot.started()) {
            return fingerprintBuilder(24)
                .field("wall")
                .field("waiting")
                .value();
        }
        return fingerprintBuilder(32)
            .field("wall")
            .field(snapshot.started())
            .field(snapshot.gameFinished())
            .field(snapshot.remainingWallCount())
            .value();
    }

    private long tableFingerprint(MahjongTableSession session, TableRenderSnapshot snapshot) {
        return fingerprintBuilder(48)
            .field("table")
            .field(snapshot.worldName())
            .field((int) Math.floor(snapshot.centerX()))
            .field((int) Math.floor(snapshot.centerY()))
            .field((int) Math.floor(snapshot.centerZ()))
            .field(Objects.toString(session.plugin().settings().craftEngineTableFurnitureId(), ""))
            .value();
    }

    private long doraFingerprint(TableRenderSnapshot snapshot) {
        if (!snapshot.started()) {
            return fingerprintBuilder(24)
                .field("dora")
                .field("waiting")
                .value();
        }
        FingerprintBuilder builder = fingerprintBuilder(64)
            .field("dora")
            .field(snapshot.started())
            .field(snapshot.doraIndicators().size());
        snapshot.doraIndicators().forEach(tile -> builder.field(tile.name()));
        return builder.value();
    }

    private long centerFingerprint(TableRenderSnapshot snapshot) {
        return fingerprintBuilder(192)
            .field("center")
            .field(snapshot.started() ? "started" : "waiting")
            .field(snapshot.publicCenterText())
            .field(snapshot.lastPublicDiscardPlayerId())
            .field(snapshot.lastPublicDiscardTile())
            .value();
    }

    private long seatLabelFingerprint(MahjongTableSession session, TableRenderSnapshot snapshot, TableSeatRenderSnapshot seat) {
        return fingerprintBuilder(128)
            .field("labels")
            .field("depth-v2")
            .field(seat.wind().name())
            .field(snapshot.currentSeat().name())
            .field(snapshot.started())
            .field(session.isRoundStartInProgress())
            .field(Objects.toString(seat.playerId(), "empty"))
            .field(seat.displayName())
            .field(seat.publicSeatStatus())
            .field(seat.riichi())
            .field(seat.ready())
            .field(seat.queuedToLeave())
            .value();
    }

    private long seatVisualFingerprint(MahjongTableSession session, SeatWind wind) {
        return fingerprintBuilder(96)
            .field("visual")
            .field("chair-stable-v2")
            .field(wind.name())
            .field(Objects.toString(session.plugin().settings().craftEngineSeatFurnitureId(), ""))
            .value();
    }

    private long handPublicFingerprint(TableRenderSnapshot snapshot, TableSeatRenderSnapshot seat) {
        FingerprintBuilder builder = fingerprintBuilder(256)
            .field("hand-public")
            .field(seat.wind().name())
            .field(Objects.toString(seat.playerId(), "empty"));
        if (seat.playerId() == null) {
            return builder.value();
        }
        builder.field(snapshot.started())
            .field(seat.online())
            .field(seat.viewerMembershipSignature())
            .field(seat.stickLayoutCount());
        seat.hand().forEach(tile -> builder.field(tile.name()));
        return builder.value();
    }

    public long meldTileFingerprint(TableSeatRenderSnapshot seat, TableRenderLayout.SeatLayoutPlan plan, int meldIndex) {
        TableRenderLayout.TilePlacement placement = plan.meldPlacements().get(meldIndex);
        return fingerprintBuilder(160)
            .field("meld-tile")
            .field(seat.wind().name())
            .field(Objects.toString(seat.playerId(), "empty"))
            .field(meldIndex)
            .field(Float.floatToIntBits(placement.yaw()))
            .field(Double.doubleToLongBits(placement.point().x()))
            .field(Double.doubleToLongBits(placement.point().y()))
            .field(Double.doubleToLongBits(placement.point().z()))
            .field(placement.tile().name())
            .field(placement.pose().name())
            .value();
    }

    private long stickFingerprint(TableRenderSnapshot snapshot, TableSeatRenderSnapshot seat) {
        FingerprintBuilder builder = fingerprintBuilder(128)
            .field("sticks")
            .field(seat.wind().name())
            .field(Objects.toString(seat.playerId(), "empty"))
            .field(snapshot.honbaCount())
            .field(snapshot.dealerSeat().name());
        if (seat.playerId() == null) {
            return builder.value();
        }
        builder.field(seat.riichi());
        seat.scoringSticks().forEach(stick -> builder.field(stick.name()));
        seat.cornerSticks().forEach(stick -> builder.field(stick.name()));
        return builder.value();
    }

    private String seatRegionKey(String region, SeatWind wind) {
        return region + ":" + wind.name();
    }

    private static FingerprintBuilder fingerprintBuilder(int capacity) {
        return new FingerprintBuilder();
    }

    private static final class FingerprintBuilder {
        private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
        private static final long FNV_PRIME = 0x100000001b3L;

        private long hash = FNV_OFFSET_BASIS;
        private boolean needsSeparator;

        private FingerprintBuilder field(Object value) {
            if (this.needsSeparator) {
                this.mix(':');
            }
            String text = Objects.toString(value, "");
            for (int index = 0; index < text.length(); index++) {
                this.mix(text.charAt(index));
            }
            this.needsSeparator = true;
            return this;
        }

        private long value() {
            return this.hash;
        }

        private void mix(char value) {
            this.hash ^= value;
            this.hash *= FNV_PRIME;
        }
    }
}
