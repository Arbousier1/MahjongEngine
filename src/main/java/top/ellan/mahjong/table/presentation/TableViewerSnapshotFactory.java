package top.ellan.mahjong.table.presentation;

import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.riichi.ReactionOptions;
import top.ellan.mahjong.table.core.MahjongTableSession;
import top.ellan.mahjong.table.core.TableSpectatorSeatOverlaySnapshot;
import top.ellan.mahjong.table.core.TableViewerHudSnapshot;
import top.ellan.mahjong.table.core.TableViewerOverlaySnapshot;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public final class TableViewerSnapshotFactory {
    private final MahjongTableSession session;

    public TableViewerSnapshotFactory(MahjongTableSession session) {
        this.session = session;
    }

    public Component createStateSummary(Player player) {
        if (!this.session.hasRoundController()) {
            return this.session.plugin().messages().render(player, "command.table_not_started");
        }

        Locale locale = this.session.plugin().messages().resolveLocale(player);
        ViewerSummarySnapshot summary = this.captureViewerSummarySnapshot(locale, player.getUniqueId());
        return this.session.plugin().messages().render(
            player,
            "command.rule_summary",
            this.session.plugin().messages().tag("summary", summary.commandStateSummary())
        );
    }

    public Component createViewerOverlay(Player viewer) {
        Locale locale = this.session.plugin().messages().resolveLocale(viewer);
        return this.viewerOverlay(locale, this.captureViewerSummarySnapshot(locale, viewer.getUniqueId()));
    }

    public TableViewerOverlaySnapshot captureViewerOverlaySnapshot(Player viewer) {
        Locale locale = this.session.plugin().messages().resolveLocale(viewer);
        UUID viewerId = viewer.getUniqueId();
        String regionKey = "viewer-overlay:" + viewerId;
        boolean spectator = this.session.isSpectator(viewerId);
        ViewerSummarySnapshot summary = this.captureViewerSummarySnapshot(locale, viewerId);
        Component overlay = this.viewerOverlay(locale, summary);
        List<TableSpectatorSeatOverlaySnapshot> seatOverlays = List.of();
        String fingerprint = this.viewerOverlayFingerprint(locale, viewerId, spectator, summary, seatOverlays);
        return new TableViewerOverlaySnapshot(viewerId, regionKey, spectator, overlay, seatOverlays, fingerprint);
    }

    public TableViewerHudSnapshot captureViewerHudSnapshot(Locale locale, UUID viewerId) {
        float progress = this.hudProgress();
        BossBar.Color color = this.hudColor(viewerId);
        ViewerSummarySnapshot summary = this.captureViewerSummarySnapshot(locale, viewerId);
        boolean spectator = summary.spectator();
        long nextRoundSeconds = this.session.nextRoundSecondsRemainingValue();
        Object lastResolution = this.session.lastResolution();

        if (!this.session.hasRoundController()) {
            return this.buildWaitingHudSnapshot(locale, progress, color, spectator, nextRoundSeconds, lastResolution, summary);
        }

        if (this.session.isRoundFinished() && this.session.lastResolution() != null) {
            Component title = this.session.plugin().messages().render(
                locale,
                "hud.finished",
                this.session.plugin().messages().tag("round", summary.round()),
                this.session.plugin().messages().tag("title", summary.resolutionTitle())
            );
            String stateSignature = fingerprintBuilder(192)
                .field(locale.toLanguageTag())
                .field(progress)
                .field(color)
                .field(nextRoundSeconds)
                .field(spectator)
                .field(this.session.isStarted())
                .field(lastResolution)
                .field(summary.round())
                .field(summary.resolutionTitle())
                .toString();
            return new TableViewerHudSnapshot(title, progress, color, stateSignature);
        }

        if (!this.session.isStarted()) {
            return this.buildWaitingHudSnapshot(locale, progress, color, spectator, nextRoundSeconds, lastResolution, summary);
        }

        Component title = this.session.plugin().messages().render(
            locale,
            "hud.round",
            this.session.plugin().messages().tag("round", summary.round()),
            this.session.plugin().messages().tag("dealer", summary.dealer()),
            this.session.plugin().messages().tag("turn", summary.turn()),
            this.session.plugin().messages().number(locale, "wall", summary.wall()),
            this.session.plugin().messages().number(locale, "riichi_pool", summary.riichiPool()),
            this.session.plugin().messages().tag("dora", summary.doraSummary()),
            this.session.plugin().messages().tag("role", summary.roleLabel())
        );
        String stateSignature = fingerprintBuilder(192)
            .field(locale.toLanguageTag())
            .field(progress)
            .field(color)
            .field(nextRoundSeconds)
            .field(spectator)
            .field(true)
            .field(lastResolution)
            .field(summary.round())
            .field(summary.dealer())
            .field(summary.turn())
            .field(summary.wall())
            .field(summary.riichiPool())
            .field(summary.doraSummary())
            .field(summary.roleLabel())
            .field(this.session.lastPublicDiscardPlayerId())
            .field(this.session.lastPublicDiscardTile())
            .field(summary.reactionOptionsFingerprint())
            .toString();
        return new TableViewerHudSnapshot(title, progress, color, stateSignature);
    }

    private TableViewerHudSnapshot buildWaitingHudSnapshot(
        Locale locale,
        float progress,
        BossBar.Color color,
        boolean spectator,
        long nextRoundSeconds,
        Object lastResolution,
        ViewerSummarySnapshot summary
    ) {
        Component title = this.session.plugin().messages().render(
            locale,
            "hud.waiting",
            this.session.plugin().messages().tag("table_id", this.session.id()),
            this.session.plugin().messages().tag("summary", summary.waitingSummary())
        );
        String stateSignature = fingerprintBuilder(192)
            .field(locale.toLanguageTag())
            .field(progress)
            .field(color)
            .field(nextRoundSeconds)
            .field(spectator)
            .field(false)
            .field(lastResolution)
            .field(summary.waitingSummary())
            .field(summary.ruleSummary())
            .toString();
        return new TableViewerHudSnapshot(title, progress, color, stateSignature);
    }

    private Component viewerOverlay(Locale locale, ViewerSummarySnapshot summary) {
        if (!this.session.hasRoundController()) {
            return this.waitingOverlay(locale, summary);
        }
        if (!this.session.isStarted()) {
            if (this.session.isRoundFinished() && this.session.lastResolution() != null) {
                return this.session.plugin().messages().render(
                    locale,
                    "overlay.finished",
                    this.session.plugin().messages().tag("round", summary.round()),
                    this.session.plugin().messages().tag("title", summary.resolutionTitle())
                );
            }
            return this.waitingOverlay(locale, summary);
        }
        return this.session.plugin().messages().render(
            locale,
            "overlay.active",
            this.session.plugin().messages().tag("role", summary.roleLabel()),
            this.session.plugin().messages().tag("round", summary.round()),
            this.session.plugin().messages().tag("dealer", summary.dealer()),
            this.session.plugin().messages().tag("turn", summary.turn()),
            this.session.plugin().messages().number(locale, "wall", summary.wall()),
            this.session.plugin().messages().number(locale, "riichi_pool", summary.riichiPool()),
            this.session.plugin().messages().tag("dora", summary.doraSummary()),
            this.session.plugin().messages().tag("last_discard", summary.lastDiscardSummary()),
            this.session.plugin().messages().tag("prompt", summary.viewerPrompt())
        );
    }

    private Component waitingOverlay(Locale locale, ViewerSummarySnapshot summary) {
        return this.session.plugin().messages().render(
            locale,
            "overlay.waiting",
            this.session.plugin().messages().tag("table_id", this.session.id()),
            this.session.plugin().messages().tag("summary", summary.waitingSummary())
        );
    }

    private ViewerSummarySnapshot captureViewerSummarySnapshot(Locale locale, UUID viewerId) {
        boolean spectator = this.session.isSpectator(viewerId);
        String waitingSummary = this.session.waitingDisplaySummary(locale);
        String ruleSummary = this.session.ruleDisplaySummary(locale);
        if (!this.session.hasRoundController()) {
            return new ViewerSummarySnapshot(
                spectator,
                waitingSummary,
                ruleSummary,
                "",
                "",
                "",
                0,
                0,
                "",
                this.viewerRoleLabel(locale, viewerId),
                "",
                "",
                "",
                "",
                ""
            );
        }

        String round = this.session.roundDisplay(locale);
        String dealer = this.session.dealerName(locale);
        String turn = this.session.currentTurnDisplayName();
        int wall = this.session.remainingWallCount();
        int riichiPool = this.session.riichiPoolCount();
        String doraSummary = this.doraSummary(locale);
        String roleLabel = this.viewerRoleLabel(locale, viewerId);
        String lastDiscardSummary = this.lastDiscardSummary(locale);
        ReactionOptions options = this.session.availableReactions(viewerId);
        String reactionOptionsFingerprint = Objects.toString(options, "");
        String resolutionTitle = this.session.lastResolution() == null
            ? ""
            : this.resolutionLabel(locale, this.session.lastResolution().getTitle());
        String viewerPrompt = this.viewerPrompt(locale, viewerId, options, turn, spectator, riichiPool, doraSummary, resolutionTitle);
        String commandStateSummary = this.buildCommandStateSummary(locale, round, turn, wall, options, resolutionTitle);
        return new ViewerSummarySnapshot(
            spectator,
            waitingSummary,
            ruleSummary,
            round,
            dealer,
            turn,
            wall,
            riichiPool,
            doraSummary,
            roleLabel,
            lastDiscardSummary,
            viewerPrompt,
            resolutionTitle,
            reactionOptionsFingerprint,
            commandStateSummary
        );
    }

    private String buildCommandStateSummary(
        Locale locale,
        String round,
        String turn,
        int wall,
        ReactionOptions options,
        String resolutionTitle
    ) {
        StringBuilder builder = new StringBuilder(128);
        builder.append(this.session.plugin().messages().plain(locale, "state.label.round")).append(' ').append(round);
        builder.append(" | ").append(this.session.plugin().messages().plain(locale, "state.label.turn")).append(' ').append(turn);
        builder.append(" | ").append(this.session.plugin().messages().plain(locale, "state.label.wall")).append(' ').append(wall);
        builder.append(" | ").append(this.session.plugin().messages().plain(locale, "state.label.spectators")).append(' ').append(this.session.spectatorCount());
        if (this.session.hasPendingReaction() && options != null) {
            builder.append(" | ").append(this.session.plugin().messages().plain(locale, "state.label.reactions"));
            this.appendReactionActionLabels(builder, locale, options);
        }
        if (this.session.lastResolution() != null) {
            builder.append(" | ")
                .append(this.session.plugin().messages().plain(locale, "state.label.resolution"))
                .append(' ')
                .append(resolutionTitle);
        }
        if (!this.session.isStarted() && !this.session.isRoundFinished()) {
            builder.append(" | ")
                .append(this.session.plugin().messages().plain(locale, "state.label.next_round"))
                .append(' ')
                .append(this.session.plugin().messages().plain(
                    locale,
                    "state.ready_summary",
                    this.session.plugin().messages().number(locale, "ready", this.session.readyCount()),
                    this.session.plugin().messages().number(locale, "total", this.session.size())
                ));
        }
        if (this.session.isRoundFinished()) {
            builder.append(" | ").append(this.session.plugin().messages().plain(locale, "state.match_finished"));
        }
        return builder.toString();
    }

    private String viewerRoleLabel(Locale locale, UUID viewerId) {
        return this.session.plugin().messages().plain(locale, this.session.isSpectator(viewerId) ? "hud.role_spectator" : "hud.role_player");
    }

    private String doraSummary(Locale locale) {
        String labels = this.session.doraIndicators().stream()
            .map(tile -> this.doraIndicatorSummary(locale, tile))
            .collect(java.util.stream.Collectors.joining("/"));
        if (labels.isBlank()) {
            labels = "-";
        }
        return this.session.plugin().messages().plain(
            locale,
            "ui.dora",
            this.session.plugin().messages().tag("value", labels)
        );
    }

    private String doraIndicatorSummary(Locale locale, top.ellan.mahjong.model.MahjongTile indicator) {
        top.ellan.mahjong.model.MahjongTile dora = DoraIndicatorMapper.doraFromIndicator(indicator);
        return this.session.plugin().messages().plain(
            locale,
            "hud.dora_entry",
            this.session.plugin().messages().tag("indicator", this.tileLabel(locale, indicator.name())),
            this.session.plugin().messages().tag("dora", this.tileLabel(locale, dora.name()))
        );
    }

    private String viewerPrompt(
        Locale locale,
        UUID viewerId,
        ReactionOptions options,
        String currentTurnName,
        boolean spectator,
        int riichiPool,
        String doraSummary,
        String resolutionTitle
    ) {
        if (options != null) {
            return this.reactionSummary(locale, options);
        }
        if (this.session.currentSeat() == this.session.seatOf(viewerId) && this.session.isStarted()) {
            String suggestion = this.discardSuggestion(locale, viewerId);
            return suggestion.isBlank()
                ? this.session.plugin().messages().plain(locale, "overlay.your_turn")
                : this.session.plugin().messages().plain(locale, "overlay.your_turn") + " | " + suggestion;
        }
        if (spectator) {
            return this.session.plugin().messages().plain(locale, "overlay.spectating_turn") + " " + currentTurnName;
        }
        if (!resolutionTitle.isBlank()) {
            return this.session.plugin().messages().plain(locale, "state.label.resolution") + " " + resolutionTitle;
        }
        return "";
    }

    private String viewerOverlayFingerprint(
        Locale locale,
        UUID viewerId,
        boolean spectator,
        ViewerSummarySnapshot summary,
        List<TableSpectatorSeatOverlaySnapshot> seatOverlays
    ) {
        FingerprintBuilder builder = fingerprintBuilder(256)
            .field(locale.toLanguageTag())
            .field(viewerId)
            .field(spectator)
            .field(this.session.nextRoundSecondsRemainingValue())
            .field(this.session.lastResolution())
            .field(summary.waitingSummary())
            .field(summary.ruleSummary())
            .field(summary.round())
            .field(summary.dealer())
            .field(summary.turn())
            .field(summary.wall())
            .field(summary.riichiPool())
            .field(summary.roleLabel())
            .field(summary.lastDiscardSummary())
            .field(summary.viewerPrompt())
            .field(summary.resolutionTitle())
            .field(summary.commandStateSummary());
        if (!this.session.hasRoundController()) {
            return builder.field("no-engine").toString();
        }
        builder.field(this.session.roundDisplay())
            .field(this.session.remainingWallCount())
            .field(this.session.currentSeat())
            .field(this.session.lastPublicDiscardPlayerId())
            .field(this.session.lastPublicDiscardTile())
            .field(summary.reactionOptionsFingerprint());
        for (TableSpectatorSeatOverlaySnapshot seatOverlay : seatOverlays) {
            builder.field(seatOverlay.signature());
        }
        return builder.toString();
    }

    private float hudProgress() {
        if (!this.session.hasRoundController()) {
            return Math.min(1.0F, this.session.size() / 4.0F);
        }
        if (!this.session.isStarted()) {
            return this.session.size() == 0 ? 0.0F : Math.max(0.0F, Math.min(1.0F, this.session.readyCount() / (float) this.session.size()));
        }
        int remainingWallCount = this.session.remainingWallCount();
        if (remainingWallCount <= 0) {
            return 0.0F;
        }
        return Math.max(0.03F, Math.min(1.0F, remainingWallCount / 70.0F));
    }

    private BossBar.Color hudColor(UUID viewerId) {
        if (!this.session.hasRoundController()) {
            return BossBar.Color.WHITE;
        }
        if (this.session.isRoundFinished()) {
            return BossBar.Color.PURPLE;
        }
        if (!this.session.isStarted()) {
            return BossBar.Color.YELLOW;
        }
        if (this.session.availableReactions(viewerId) != null) {
            return BossBar.Color.RED;
        }
        if (this.session.currentSeat() == this.session.seatOf(viewerId)) {
            return BossBar.Color.GREEN;
        }
        return this.session.isSpectator(viewerId) ? BossBar.Color.BLUE : BossBar.Color.WHITE;
    }

    private String reactionSummary(Locale locale, ReactionOptions options) {
        List<String> actions = new java.util.ArrayList<>(4);
        if (options.getCanRon()) {
            actions.add(this.session.plugin().messages().plain(locale, "table.action.ron"));
        }
        if (options.getCanPon()) {
            actions.add(this.session.plugin().messages().plain(locale, "table.action.pon"));
        }
        if (options.getCanMinkan()) {
            actions.add(this.session.plugin().messages().plain(locale, "table.action.minkan"));
        }
        if (!options.getChiiPairs().isEmpty()) {
            actions.add(this.session.plugin().messages().plain(locale, "table.action.chii"));
        }
        String summary = this.session.plugin().messages().plain(locale, "overlay.reactions") + " " + String.join("/", actions);
        if (options.getSuggestedResponse() == null) {
            return summary;
        }
        return summary + " | " + this.session.plugin().messages().plain(
            locale,
            "table.suggested_action",
            this.session.plugin().messages().tag("action", this.reactionLabel(locale, options.getSuggestedResponse()))
        );
    }

    private void appendReactionActionLabels(StringBuilder builder, Locale locale, ReactionOptions options) {
        if (options.getCanRon()) {
            builder.append(' ').append(this.session.plugin().messages().plain(locale, "table.action.ron"));
        }
        if (options.getCanPon()) {
            builder.append(' ').append(this.session.plugin().messages().plain(locale, "table.action.pon"));
        }
        if (options.getCanMinkan()) {
            builder.append(' ').append(this.session.plugin().messages().plain(locale, "table.action.minkan"));
        }
        if (!options.getChiiPairs().isEmpty()) {
            builder.append(' ').append(this.session.plugin().messages().plain(locale, "table.action.chii"));
        }
    }

    private String discardSuggestion(Locale locale, UUID viewerId) {
        List<top.ellan.mahjong.riichi.RiichiDiscardSuggestion> suggestions = this.session.suggestedDiscardSuggestions(viewerId);
        if (suggestions.isEmpty()) {
            return "";
        }
        top.ellan.mahjong.riichi.RiichiDiscardSuggestion best = suggestions.getFirst();
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
        List<top.ellan.mahjong.riichi.RiichiDiscardSuggestion> suggestions
    ) {
        top.ellan.mahjong.riichi.RiichiDiscardSuggestion best = suggestions.getFirst();
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (top.ellan.mahjong.riichi.RiichiDiscardSuggestion suggestion : suggestions) {
            if (!this.hasSameDiscardShape(best, suggestion) || labels.size() >= 3) {
                break;
            }
            labels.add(this.session.tileLabelForDisplay(locale, suggestion.getTile().name()));
        }
        return String.join("/", labels);
    }

    private boolean hasSameDiscardShape(
        top.ellan.mahjong.riichi.RiichiDiscardSuggestion left,
        top.ellan.mahjong.riichi.RiichiDiscardSuggestion right
    ) {
        return left.getShantenNum() == right.getShantenNum()
            && left.getAdvanceCount() == right.getAdvanceCount()
            && left.getGoodShapeAdvanceCount() == right.getGoodShapeAdvanceCount()
            && left.getImprovementCount() == right.getImprovementCount()
            && left.getGoodShapeImprovementCount() == right.getGoodShapeImprovementCount();
    }

    private String reactionLabel(Locale locale, top.ellan.mahjong.riichi.ReactionResponse response) {
        return switch (response.getType()) {
            case RON -> this.session.plugin().messages().plain(locale, "table.action.ron");
            case PON -> this.session.plugin().messages().plain(locale, "table.action.pon");
            case MINKAN -> this.session.plugin().messages().plain(locale, "table.action.minkan");
            case SKIP -> this.session.plugin().messages().plain(locale, "table.action.skip");
            case CHII -> {
                if (response.getChiiPair() == null) {
                    yield this.session.plugin().messages().plain(locale, "table.action.chii");
                }
                yield this.session.plugin().messages().plain(locale, "table.action.chii") + " "
                    + this.session.tileLabelForDisplay(locale, response.getChiiPair().getFirst().name()) + " "
                    + this.session.tileLabelForDisplay(locale, response.getChiiPair().getSecond().name());
            }
        };
    }

    private String resolutionLabel(Locale locale, String title) {
        String key = "resolution." + title.toLowerCase(Locale.ROOT);
        return this.session.plugin().messages().contains(locale, key) ? this.session.plugin().messages().plain(locale, key) : title;
    }

    private String lastDiscardSummary(Locale locale) {
        if (this.session.lastPublicDiscardPlayerId() == null || this.session.lastPublicDiscardTile() == null) {
            return this.session.plugin().messages().plain(locale, "table.last_discard_none");
        }
        return this.session.plugin().messages().plain(
            locale,
            "table.last_discard",
            this.session.plugin().messages().tag("player", this.session.displayName(this.session.lastPublicDiscardPlayerId(), locale)),
            this.session.plugin().messages().tag("tile", this.tileLabel(locale, this.session.lastPublicDiscardTile().name()))
        );
    }

    private String tileLabel(Locale locale, String tileName) {
        String key = "tile." + tileName.toLowerCase(Locale.ROOT);
        return this.session.plugin().messages().contains(locale, key) ? this.session.plugin().messages().plain(locale, key) : tileName.toLowerCase(Locale.ROOT);
    }

    private static FingerprintBuilder fingerprintBuilder(int capacity) {
        return new FingerprintBuilder(capacity);
    }

    private record ViewerSummarySnapshot(
        boolean spectator,
        String waitingSummary,
        String ruleSummary,
        String round,
        String dealer,
        String turn,
        int wall,
        int riichiPool,
        String doraSummary,
        String roleLabel,
        String lastDiscardSummary,
        String viewerPrompt,
        String resolutionTitle,
        String reactionOptionsFingerprint,
        String commandStateSummary
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



