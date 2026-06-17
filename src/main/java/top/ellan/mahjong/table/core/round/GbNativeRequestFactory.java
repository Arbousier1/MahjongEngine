package top.ellan.mahjong.table.core.round;

import top.ellan.mahjong.gb.jni.GbFanRequest;
import top.ellan.mahjong.gb.jni.GbMeldInput;
import top.ellan.mahjong.gb.jni.GbSeatPointsInput;
import top.ellan.mahjong.gb.jni.GbTingRequest;
import top.ellan.mahjong.gb.jni.GbWinRequest;
import top.ellan.mahjong.gb.runtime.GbTileEncoding;
import top.ellan.mahjong.model.MahjongTile;
import top.ellan.mahjong.model.SeatWind;
import java.util.ArrayList;
import java.util.List;

final class GbNativeRequestFactory {
    private static final String RULE_PROFILE = "GB_MAHJONG";
    static final String WIN_TYPE_SELF_DRAW = "SELF_DRAW";

    GbFanRequest buildFanRequest(
        List<MahjongTile> hand,
        boolean hasDrawnTile,
        List<? extends MeldStateView> melds,
        MahjongTile winningTile,
        String winType,
        SeatWind seatWind,
        SeatWind roundWind,
        List<MahjongTile> flowers,
        List<String> flags
    ) {
        return new GbFanRequest(
            RULE_PROFILE,
            encodedConcealedHand(hand, hasDrawnTile, winType),
            toNativeMelds(seatWind, melds),
            GbTileEncoding.encode(winningTile),
            winType,
            GbTileEncoding.encodeWind(seatWind),
            GbTileEncoding.encodeWind(roundWind),
            encodedTiles(flowers),
            flags
        );
    }

    GbTingRequest buildTingRequest(
        List<MahjongTile> concealedHand,
        List<? extends MeldStateView> melds,
        SeatWind seatWind,
        SeatWind roundWind,
        List<MahjongTile> flowers
    ) {
        return new GbTingRequest(
            RULE_PROFILE,
            encodedTiles(concealedHand),
            toNativeMelds(seatWind, melds),
            GbTileEncoding.encodeWind(seatWind),
            GbTileEncoding.encodeWind(roundWind),
            encodedTiles(flowers),
            List.of()
        );
    }

    GbWinRequest buildWinRequest(
        List<MahjongTile> hand,
        boolean hasDrawnTile,
        List<? extends MeldStateView> melds,
        MahjongTile winningTile,
        String winType,
        SeatWind winnerSeat,
        SeatWind discarderSeat,
        SeatWind roundWind,
        List<SeatPointsView> seatPoints,
        List<MahjongTile> flowers,
        List<String> flags
    ) {
        return new GbWinRequest(
            RULE_PROFILE,
            encodedConcealedHand(hand, hasDrawnTile, winType),
            toNativeMelds(winnerSeat, melds),
            GbTileEncoding.encode(winningTile),
            winType,
            GbTileEncoding.encodeWind(winnerSeat),
            discarderSeat == null ? null : GbTileEncoding.encodeWind(discarderSeat),
            GbTileEncoding.encodeWind(winnerSeat),
            GbTileEncoding.encodeWind(roundWind),
            encodedSeatPoints(seatPoints),
            encodedTiles(flowers),
            flags
        );
    }

    private static List<String> encodedConcealedHand(List<MahjongTile> hand, boolean hasDrawnTile, String winType) {
        List<MahjongTile> concealed = new ArrayList<>(hand == null ? List.of() : hand);
        if (WIN_TYPE_SELF_DRAW.equals(winType) && hasDrawnTile && !concealed.isEmpty()) {
            concealed.remove(concealed.size() - 1);
        }
        return encodedTiles(concealed);
    }

    private static List<String> encodedTiles(List<MahjongTile> tiles) {
        if (tiles == null || tiles.isEmpty()) {
            return List.of();
        }
        return tiles.stream().map(GbTileEncoding::encode).toList();
    }

    private static List<GbMeldInput> toNativeMelds(SeatWind selfSeat, List<? extends MeldStateView> melds) {
        if (melds == null || melds.isEmpty()) {
            return List.of();
        }
        List<GbMeldInput> inputs = new ArrayList<>(melds.size());
        for (MeldStateView meld : melds) {
            inputs.add(new GbMeldInput(
                meld.nativeType(),
                encodedTiles(meld.tiles()),
                meld.claimedTile() == null ? null : GbTileEncoding.encode(meld.claimedTile()),
                meld.fromSeat() == null ? null : GbRoundSupport.relationLabel(selfSeat, meld.fromSeat()),
                meld.open()
            ));
        }
        return List.copyOf(inputs);
    }

    private static List<GbSeatPointsInput> encodedSeatPoints(List<SeatPointsView> seatPoints) {
        if (seatPoints == null || seatPoints.isEmpty()) {
            return List.of();
        }
        List<GbSeatPointsInput> inputs = new ArrayList<>(seatPoints.size());
        for (SeatPointsView seatPoint : seatPoints) {
            inputs.add(new GbSeatPointsInput(GbTileEncoding.encodeWind(seatPoint.seat()), seatPoint.points()));
        }
        return List.copyOf(inputs);
    }

    interface MeldStateView {
        List<MahjongTile> tiles();

        MahjongTile claimedTile();

        SeatWind fromSeat();

        boolean open();

        String nativeType();
    }

    record SeatPointsView(SeatWind seat, int points) {
    }
}
