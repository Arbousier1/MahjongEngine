package doublemoon.mahjongcraft.paper.table.core;

import doublemoon.mahjongcraft.paper.model.SeatWind;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;

final class SessionMessaging {
    private final MahjongTableSession session;

    SessionMessaging(MahjongTableSession session) {
        this.session = session;
    }

    Component spectatorSeatOverlay(Locale locale, SeatWind wind) {
        UUID playerId = this.session.playerAt(wind);
        if (playerId == null) {
            return this.session.plugin().messages().render(
                locale,
                "overlay.seat_empty",
                this.session.plugin().messages().tag("seat", this.session.seatDisplayName(wind, locale))
            );
        }

        return this.session.plugin().messages().render(
            locale,
            "overlay.seat",
            this.session.plugin().messages().tag("seat", this.session.seatDisplayName(wind, locale)),
            this.session.plugin().messages().tag("name", this.session.displayName(playerId, locale)),
            this.session.plugin().messages().number(locale, "points", this.session.points(playerId)),
            this.session.plugin().messages().number(locale, "hand", this.session.hand(playerId).size()),
            this.session.plugin().messages().number(locale, "river", this.session.discards(playerId).size()),
            this.session.plugin().messages().number(locale, "melds", this.session.fuuro(playerId).size()),
            this.session.plugin().messages().tag("status", this.spectatorSeatStatus(locale, wind, playerId))
        );
    }

    private String spectatorSeatStatus(Locale locale, SeatWind wind, UUID playerId) {
        String status = "";
        if (this.session.currentSeat() == wind && this.session.isStarted()) {
            status = this.session.plugin().messages().plain(locale, "overlay.status_turn");
        }
        if (this.session.currentVariant() != MahjongVariant.RIICHI || !this.session.isRiichi(playerId)) {
            return status;
        }
        String riichi = this.session.plugin().messages().plain(locale, "overlay.status_riichi");
        return status.isBlank() ? riichi : status + " / " + riichi;
    }
}
