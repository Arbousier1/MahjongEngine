package doublemoon.mahjongcraft.paper.table.presentation;

import doublemoon.mahjongcraft.paper.riichi.ReactionOptions;
import doublemoon.mahjongcraft.paper.table.core.MahjongTableSession;
import doublemoon.mahjongcraft.paper.table.core.TableFinalStanding;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import kotlin.Pair;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class TablePlayerFeedbackCoordinator {
    private final MahjongTableSession session;
    private final Map<UUID, String> feedbackState = new HashMap<>();
    private String lastSettlementFingerprint = "";
    private String lastPersistedSettlementFingerprint = "";
    private String lastPersistedRankFingerprint = "";

    public TablePlayerFeedbackCoordinator(MahjongTableSession session) {
        this.session = session;
    }

    public void sync() {
        if (!this.session.hasRoundController()) {
            this.resetState();
            return;
        }

        String settlementFingerprint = Objects.toString(this.session.lastResolution(), "");
        this.persistSettlementIfNeeded(settlementFingerprint);
        this.persistRankIfNeeded(settlementFingerprint);
        boolean settlementChanged = SettlementFeedbackGate.isNewSettlement(settlementFingerprint, this.lastSettlementFingerprint);
        if (settlementChanged) {
            this.lastSettlementFingerprint = settlementFingerprint;
        }
        this.syncSettlementFeedback(settlementFingerprint, settlementChanged);
        this.syncSeatFeedbackStates();
    }

    public void clearPlayerState(UUID playerId) {
        this.feedbackState.remove(playerId);
    }

    public void resetForRoundStart() {
        this.lastSettlementFingerprint = "";
        this.lastPersistedSettlementFingerprint = "";
        this.lastPersistedRankFingerprint = "";
    }

    public void resetState() {
        this.feedbackState.clear();
        this.lastSettlementFingerprint = "";
        this.lastPersistedSettlementFingerprint = "";
        this.lastPersistedRankFingerprint = "";
        this.session.cancelNextRoundCountdown();
    }

    private void persistSettlementIfNeeded(String settlementFingerprint) {
        if (settlementFingerprint.isBlank()
            || settlementFingerprint.equals(this.lastPersistedSettlementFingerprint)
            || this.session.plugin().database() == null) {
            return;
        }
        this.session.plugin().database().persistRoundResultAsync(this.session, this.session.lastResolution());
        this.lastPersistedSettlementFingerprint = settlementFingerprint;
    }

    private void persistRankIfNeeded(String settlementFingerprint) {
        if (settlementFingerprint.isBlank()
            || settlementFingerprint.equals(this.lastPersistedRankFingerprint)
            || this.session.plugin().database() == null
            || !this.session.plugin().database().rankingEnabled()) {
            return;
        }
        List<TableFinalStanding> standings = this.session.finalStandings();
        if (standings.isEmpty()) {
            return;
        }
        this.session.plugin().database().persistMatchRanksAsync(
            this.session.id(),
            this.session.configuredRuleSnapshot().getLength(),
            standings
        );
        this.lastPersistedRankFingerprint = settlementFingerprint;
    }

    private void syncSettlementFeedback(String settlementFingerprint, boolean settlementChanged) {
        if (settlementFingerprint.isBlank() || !settlementChanged) {
            return;
        }
        this.session.resetReadyStateForNextRound();
        this.session.render();
        this.openSettlementForPlayers();
        this.session.cancelNextRoundCountdown();
        this.session.promptPlayersToReady();
    }

    private void openSettlementForPlayers() {
        for (UUID playerId : this.session.seatIds()) {
            if (playerId == null || this.session.isBot(playerId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            this.session.openSettlementUi(player);
            if (!this.session.isRoundFinished()) {
                this.session.plugin().messages().send(player, "table.round_finished_ready");
            } else {
                this.session.plugin().messages().send(player, "table.match_finished_ready");
            }
        }
    }

    private void syncSeatFeedbackStates() {
        for (UUID playerId : this.session.seatIds()) {
            if (playerId == null || this.session.isBot(playerId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            this.syncSeatFeedbackState(player, this.capturePlayerFeedbackSnapshot(player, playerId));
        }
    }

    private void syncSeatFeedbackState(Player player, PlayerFeedbackSnapshot snapshot) {
        String previous = this.feedbackState.put(snapshot.playerId(), snapshot.signature());
        if (Objects.equals(snapshot.signature(), previous)) {
            return;
        }
        if (snapshot.signature().isBlank()) {
            player.sendActionBar(Component.empty());
            return;
        }
        this.sendFeedback(player, snapshot);
    }

    private void sendFeedback(Player player, PlayerFeedbackSnapshot snapshot) {
        if (snapshot.actionBar() != null) {
            player.sendActionBar(snapshot.actionBar());
        }
        if (snapshot.reactionPrompt() != null) {
            player.sendMessage(snapshot.reactionPrompt());
        }
    }

    private PlayerFeedbackSnapshot capturePlayerFeedbackSnapshot(Player player, UUID playerId) {
        Locale locale = this.session.plugin().messages().resolveLocale(player);
        ReactionOptions options = this.session.availableReactions(playerId);
        if (options != null && this.session.hasPendingReaction()) {
            Component actionBar = this.session.plugin().messages().render(locale, "table.reaction_available");
            Component reactionPrompt = this.reactionPrompt(locale, options);
            String signature = fingerprintBuilder(192)
                .field("reaction")
                .field(playerId)
                .field(this.session.pendingReactionTileKey())
                .field(options)
                .toString();
            return new PlayerFeedbackSnapshot(playerId, signature, actionBar, reactionPrompt);
        }
        if (this.session.isStarted() && this.session.currentSeat() == this.session.seatOf(playerId)) {
            List<String> actions = new ArrayList<>(4);
            if (this.session.canDeclareRiichi(playerId)) {
                actions.add(this.session.plugin().messages().plain(locale, "table.action.riichi"));
            }
            if (this.session.canDeclareConcealedKan(playerId)) {
                actions.add(this.session.plugin().messages().plain(locale, "table.action.ankan"));
            }
            if (this.session.canDeclareAddedKan(playerId)) {
                actions.add(this.session.plugin().messages().plain(locale, "table.action.kakan"));
            }
            if (this.session.canDeclareKyuushu(playerId)) {
                actions.add(this.session.plugin().messages().plain(locale, "table.action.kyuushu"));
            }
            if (this.session.currentVariant() == doublemoon.mahjongcraft.paper.table.core.MahjongVariant.GB) {
                doublemoon.mahjongcraft.paper.gb.jni.GbTingResponse ting = this.session.gbTingOptions(playerId);
                if (this.session.gbCanWinByTsumo(playerId)) {
                    actions.add(this.session.plugin().messages().plain(locale, "table.action.tsumo"));
                }
                String suggestion = ting.getValid()
                    ? this.session.plugin().messages().plain(locale, "table.gb_ting_prompt", this.session.plugin().messages().number(locale, "count", ting.getWaits().size()))
                    : "";
                Component actionBar = this.session.plugin().messages().render(
                    locale,
                    "table.turn_prompt",
                    this.session.plugin().messages().tag("actions", String.join(" / ", actions)),
                    this.session.plugin().messages().tag("suggestion", suggestion.isBlank() ? "" : " | " + suggestion)
                );
                String signature = fingerprintBuilder(192)
                    .field("turn-gb")
                    .field(playerId)
                    .field(this.session.remainingWallCount())
                    .field(ting.getValid())
                    .field(ting.getWaits().size())
                    .field(this.session.gbCanWinByTsumo(playerId))
                    .field(this.session.canDeclareConcealedKan(playerId))
                    .field(this.session.canDeclareAddedKan(playerId))
                    .field(this.session.suggestedConcealedKanTiles(playerId))
                    .field(this.session.suggestedAddedKanTiles(playerId))
                    .field(this.session.canDeclareRiichi(playerId))
                    .field(this.session.suggestedRiichiIndices(playerId))
                    .toString();
                return new PlayerFeedbackSnapshot(playerId, signature, actionBar, this.turnPrompt(locale, playerId));
            }
            actions.add(0, this.session.plugin().messages().plain(locale, "table.action.tsumo"));
            String discardSuggestion = this.discardSuggestionSuffix(locale, playerId);
            Component actionBar = this.session.plugin().messages().render(
                locale,
                "table.turn_prompt",
                this.session.plugin().messages().tag("actions", String.join(" / ", actions)),
                this.session.plugin().messages().tag("suggestion", discardSuggestion.isBlank() ? "" : " | " + discardSuggestion)
            );
            String signature = fingerprintBuilder(192)
                .field("turn")
                .field(playerId)
                .field(this.session.remainingWallCount())
                .field(this.session.canDeclareRiichi(playerId))
                .field(this.session.canDeclareConcealedKan(playerId))
                .field(this.session.canDeclareAddedKan(playerId))
                .field(this.session.canDeclareKyuushu(playerId))
                .field(actions)
                .field(discardSuggestion)
                .field(this.session.suggestedConcealedKanTiles(playerId))
                .field(this.session.suggestedAddedKanTiles(playerId))
                .field(this.session.suggestedRiichiIndices(playerId))
                .toString();
            return new PlayerFeedbackSnapshot(playerId, signature, actionBar, this.turnPrompt(locale, playerId));
        }
        return new PlayerFeedbackSnapshot(playerId, "", null, null);
    }

    private Component reactionPrompt(Locale locale, ReactionOptions options) {
        Component message = this.session.plugin().messages().render(locale, "table.reactions_prefix");
        if (options.getCanRon()) {
            message = message.append(this.actionButton(this.session.plugin().messages().plain(locale, "table.action.ron"), "/mahjong ron", NamedTextColor.RED))
                .append(Component.space());
        }
        if (options.getCanPon()) {
            message = message.append(this.actionButton(this.session.plugin().messages().plain(locale, "table.action.pon"), "/mahjong pon", NamedTextColor.YELLOW))
                .append(Component.space());
        }
        if (options.getCanMinkan()) {
            message = message.append(this.actionButton(this.session.plugin().messages().plain(locale, "table.action.minkan"), "/mahjong minkan", NamedTextColor.DARK_AQUA))
                .append(Component.space());
        }
        for (Pair<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile, doublemoon.mahjongcraft.paper.riichi.model.MahjongTile> pair : options.getChiiPairs()) {
            String command = "/mahjong chii " + pair.getFirst().name().toLowerCase(Locale.ROOT) + " " + pair.getSecond().name().toLowerCase(Locale.ROOT);
            String label = this.session.plugin().messages().plain(locale, "table.action.chii") + " "
                + this.session.tileLabelForDisplay(locale, pair.getFirst().name()) + " "
                + this.session.tileLabelForDisplay(locale, pair.getSecond().name());
            message = message.append(this.actionButton(label, command, NamedTextColor.GREEN)).append(Component.space());
        }
        message = message.append(this.actionButton(this.session.plugin().messages().plain(locale, "table.action.skip"), "/mahjong skip", NamedTextColor.GRAY));
        Component suggestedAction = this.suggestedActionComponent(locale, options);
        return suggestedAction == null ? message : message.append(Component.space()).append(suggestedAction);
    }

    private Component turnPrompt(Locale locale, UUID playerId) {
        List<Component> buttons = new ArrayList<>();
        if (this.session.currentVariant() == doublemoon.mahjongcraft.paper.table.core.MahjongVariant.GB) {
            if (this.session.gbCanWinByTsumo(playerId)) {
                buttons.add(this.actionButton(
                    this.session.plugin().messages().plain(locale, "table.action.tsumo"),
                    "/mahjong tsumo",
                    NamedTextColor.GOLD
                ));
            }
        } else {
            buttons.add(this.actionButton(
                this.session.plugin().messages().plain(locale, "table.action.tsumo"),
                "/mahjong tsumo",
                NamedTextColor.GOLD
            ));
        }
        if (this.session.canDeclareConcealedKan(playerId)) {
            for (String tileName : this.session.suggestedConcealedKanTiles(playerId)) {
                String label = this.session.plugin().messages().plain(locale, "table.action.ankan") + " "
                    + this.session.tileLabelForDisplay(locale, tileName);
                buttons.add(this.actionButton(label, "/mahjong kan " + tileName, NamedTextColor.DARK_AQUA));
            }
        }
        if (this.session.canDeclareAddedKan(playerId)) {
            for (String tileName : this.session.suggestedAddedKanTiles(playerId)) {
                String label = this.session.plugin().messages().plain(locale, "table.action.kakan") + " "
                    + this.session.tileLabelForDisplay(locale, tileName);
                buttons.add(this.actionButton(label, "/mahjong kan " + tileName, NamedTextColor.BLUE));
            }
        }
        if (this.session.canDeclareRiichi(playerId)) {
            List<doublemoon.mahjongcraft.paper.model.MahjongTile> hand = this.session.hand(playerId);
            for (Integer index : this.session.suggestedRiichiIndices(playerId)) {
                if (index == null || index < 0 || index >= hand.size()) {
                    continue;
                }
                String tileLabel = this.session.tileLabelForDisplay(locale, hand.get(index).name());
                String label = this.session.plugin().messages().plain(locale, "table.action.riichi") + " " + tileLabel;
                buttons.add(this.actionButton(label, "/mahjong riichi " + index, NamedTextColor.LIGHT_PURPLE));
            }
        }
        if (this.session.canDeclareKyuushu(playerId)) {
            buttons.add(this.actionButton(
                this.session.plugin().messages().plain(locale, "table.action.kyuushu"),
                "/mahjong kyuushu",
                NamedTextColor.RED
            ));
        }
        if (buttons.isEmpty()) {
            return null;
        }
        Component message = this.session.plugin().messages().render(locale, "table.reactions_prefix");
        for (int i = 0; i < buttons.size(); i++) {
            message = message.append(buttons.get(i));
            if (i + 1 < buttons.size()) {
                message = message.append(Component.space());
            }
        }
        return message;
    }

    private String discardSuggestionSuffix(Locale locale, UUID playerId) {
        List<doublemoon.mahjongcraft.paper.riichi.RiichiDiscardSuggestion> suggestions = this.session.suggestedDiscardSuggestions(playerId);
        if (suggestions.isEmpty()) {
            return "";
        }
        doublemoon.mahjongcraft.paper.riichi.RiichiDiscardSuggestion best = suggestions.getFirst();
        String labels = this.suggestedDiscardLabels(locale, suggestions);
        if (labels.isBlank()) {
            return "";
        }
        return this.session.plugin().messages().plain(
            locale,
            "table.suggested_discards_detail",
            this.session.plugin().messages().tag("tiles", labels),
            this.session.plugin().messages().number(locale, "shanten", best.getShantenNum()),
            this.session.plugin().messages().number(locale, "advance", best.getAdvanceCount()),
            this.session.plugin().messages().number(locale, "improvement", best.getImprovementCount())
        );
    }

    private String suggestedDiscardLabels(
        Locale locale,
        List<doublemoon.mahjongcraft.paper.riichi.RiichiDiscardSuggestion> suggestions
    ) {
        doublemoon.mahjongcraft.paper.riichi.RiichiDiscardSuggestion best = suggestions.getFirst();
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (doublemoon.mahjongcraft.paper.riichi.RiichiDiscardSuggestion suggestion : suggestions) {
            if (!this.hasSameDiscardShape(best, suggestion) || labels.size() >= 3) {
                break;
            }
            labels.add(this.session.tileLabelForDisplay(locale, suggestion.getTile().name()));
        }
        return String.join("/", labels);
    }

    private boolean hasSameDiscardShape(
        doublemoon.mahjongcraft.paper.riichi.RiichiDiscardSuggestion left,
        doublemoon.mahjongcraft.paper.riichi.RiichiDiscardSuggestion right
    ) {
        return left.getShantenNum() == right.getShantenNum()
            && left.getAdvanceCount() == right.getAdvanceCount()
            && left.getGoodShapeAdvanceCount() == right.getGoodShapeAdvanceCount()
            && left.getImprovementCount() == right.getImprovementCount()
            && left.getGoodShapeImprovementCount() == right.getGoodShapeImprovementCount();
    }

    private Component suggestedActionComponent(Locale locale, ReactionOptions options) {
        if (options.getSuggestedResponse() == null) {
            return null;
        }
        String label = this.session.plugin().messages().plain(
            locale,
            "table.suggested_action",
            this.session.plugin().messages().tag("action", this.reactionLabel(locale, options.getSuggestedResponse()))
        );
        return Component.text("(" + label + ")", NamedTextColor.GRAY);
    }

    private String reactionLabel(Locale locale, doublemoon.mahjongcraft.paper.riichi.ReactionResponse response) {
        return switch (response.getType()) {
            case RON -> this.session.plugin().messages().plain(locale, "table.action.ron");
            case PON -> this.session.plugin().messages().plain(locale, "table.action.pon");
            case MINKAN -> this.session.plugin().messages().plain(locale, "table.action.minkan");
            case SKIP -> this.session.plugin().messages().plain(locale, "table.action.skip");
            case CHII -> {
                Pair<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile, doublemoon.mahjongcraft.paper.riichi.model.MahjongTile> pair = response.getChiiPair();
                if (pair == null) {
                    yield this.session.plugin().messages().plain(locale, "table.action.chii");
                }
                yield this.session.plugin().messages().plain(locale, "table.action.chii") + " "
                    + this.session.tileLabelForDisplay(locale, pair.getFirst().name()) + " "
                    + this.session.tileLabelForDisplay(locale, pair.getSecond().name());
            }
        };
    }

    private Component actionButton(String label, String command, NamedTextColor color) {
        return Component.text("[" + label + "]", color)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(command, NamedTextColor.GRAY)));
    }

    private static FingerprintBuilder fingerprintBuilder(int capacity) {
        return new FingerprintBuilder(capacity);
    }

    private record PlayerFeedbackSnapshot(
        UUID playerId,
        String signature,
        Component actionBar,
        Component reactionPrompt
    ) {
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

        @Override
        public String toString() {
            return this.delegate.toString();
        }
    }
}


