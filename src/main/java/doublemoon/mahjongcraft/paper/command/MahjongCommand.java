package doublemoon.mahjongcraft.paper.command;

import doublemoon.mahjongcraft.paper.bootstrap.MahjongPaperPlugin;
import doublemoon.mahjongcraft.paper.db.MahjongSoulRankProfile;
import doublemoon.mahjongcraft.paper.db.MahjongSoulRankRules;
import doublemoon.mahjongcraft.paper.i18n.MessageService;
import doublemoon.mahjongcraft.paper.riichi.ReactionOptions;
import doublemoon.mahjongcraft.paper.riichi.ReactionResponse;
import doublemoon.mahjongcraft.paper.riichi.ReactionType;
import doublemoon.mahjongcraft.paper.riichi.model.MahjongTile;
import doublemoon.mahjongcraft.paper.table.core.MahjongTableManager;
import doublemoon.mahjongcraft.paper.table.core.MahjongTableSession;
import doublemoon.mahjongcraft.paper.table.core.MahjongVariant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import kotlin.Pair;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public final class MahjongCommand implements BasicCommand {
    private static final Set<String> ADMIN_ROOT_COMMANDS = Set.of(
        "create", "botmatch", "list", "render", "inspect", "clear", "forceend", "deletetable", "reload"
    );
    private static final List<String> ROOT_COMMANDS = List.of(
        "help", "create", "botmatch", "mode", "join", "leave", "list", "spectate", "unspectate", "addbot",
        "removebot", "rule", "start", "state", "riichi", "tsumo", "ron", "pon", "minkan", "chii", "kan",
        "skip", "kyuushu", "settlement", "rank", "render", "inspect", "clear", "forceend", "deletetable", "reload"
    );
    private static final List<String> BOTMATCH_PRESETS = List.of("MAJSOUL_HANCHAN", "MAJSOUL_TONPUU", "hanchan", "tonpuu");
    private static final String[] HELP_KEYS = {
        "command.help.create",
        "command.help.botmatch",
        "command.help.mode",
        "command.help.join",
        "command.help.leave",
        "command.help.list",
        "command.help.spectate",
        "command.help.unspectate",
        "command.help.addbot",
        "command.help.removebot",
        "command.help.rule",
        "command.help.start",
        "command.help.state",
        "command.help.riichi",
        "command.help.tsumo",
        "command.help.ron",
        "command.help.pon",
        "command.help.minkan",
        "command.help.chii",
        "command.help.kan",
        "command.help.skip",
        "command.help.kyuushu",
        "command.help.settlement",
        "command.help.rank",
        "command.help.render",
        "command.help.inspect",
        "command.help.clear",
        "command.help.forceend",
        "command.help.deletetable",
        "command.help.reload"
    };
    private static final Set<String> ADMIN_HELP_KEYS = Set.of(
        "command.help.create",
        "command.help.botmatch",
        "command.help.list",
        "command.help.render",
        "command.help.inspect",
        "command.help.clear",
        "command.help.forceend",
        "command.help.deletetable",
        "command.help.reload"
    );
    private final MahjongPaperPlugin plugin;
    private final MessageService messages;
    private final MahjongTableManager tableManager;

    public MahjongCommand(MahjongPaperPlugin plugin, MahjongTableManager tableManager) {
        this.plugin = plugin;
        this.messages = plugin.messages();
        this.tableManager = tableManager;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        this.handleCommand(stack.getSender(), args);
    }

    private void handleCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                this.sendHelp(player);
            } else {
                this.messages.send(sender, "command.usage");
            }
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("reload".equals(sub)) {
            this.handleReload(sender);
            return;
        }
        if (!(sender instanceof Player player)) {
            this.messages.send(sender, "common.only_players");
            return;
        }
        if (this.isAdminRootCommand(sub) && !this.requireAdmin(player)) {
            return;
        }
        this.plugin.debug().log("command", player.getName() + " executed /mahjong " + String.join(" ", args));
        switch (sub) {
            case "help" -> this.sendHelp(player);
            case "create" -> {
                MahjongTableSession table = this.tableManager.createTable(player);
                table.render();
                this.messages.send(player, "command.created_table", this.messages.tag("table_id", table.id()));
            }
            case "botmatch" -> {
                String preset = args.length >= 2 ? args[1] : "MAJSOUL_HANCHAN";
                MahjongTableSession table = this.tableManager.createBotMatch(player, preset);
                if (table == null) {
                    this.messages.send(player, "command.botmatch_failed_in_table");
                    return;
                }
                this.messages.send(
                    player,
                    "command.botmatch_created",
                    this.messages.tag("table_id", table.id()),
                    this.messages.tag("mode", preset.toLowerCase(Locale.ROOT))
                );
            }
            case "forceend" -> {
                MahjongTableSession target = this.resolveAdminTable(player, args);
                if (target == null) {
                    return;
                }
                this.tableManager.forceEndTable(target.id());
                this.messages.send(player, "command.forceend_success", this.messages.tag("table_id", target.id()));
            }
            case "deletetable" -> {
                MahjongTableSession target = this.resolveAdminTable(player, args);
                if (target == null) {
                    return;
                }
                this.tableManager.deleteTable(target.id());
                this.messages.send(player, "command.deletetable_success", this.messages.tag("table_id", target.id()));
            }
            case "mode" -> {
                MahjongTableSession table = requireTable(player);
                if (table == null) {
                    return;
                }
                if (args.length < 2) {
                    this.messages.send(player, "command.mode_usage");
                    return;
                }
                boolean updated = table.applyRulePreset(args[1]);
                Locale locale = this.messages.resolveLocale(player);
                this.messages.send(
                    player,
                    updated ? "command.mode_updated" : "command.mode_failed",
                    this.messages.tag("summary", table.ruleSummary(locale))
                );
                if (updated) {
                    table.render();
                }
            }
            case "join" -> {
                if (args.length < 2) {
                    this.messages.send(player, "command.join_usage");
                    return;
                }
                MahjongTableSession table = this.tableManager.join(player, args[1]);
                if (table == null) {
                    this.messages.send(player, "command.join_failed");
                } else {
                    this.messages.send(player, "command.joined_table", this.messages.tag("table_id", table.id()));
                }
            }
            case "leave" -> {
                MahjongTableSession current = this.tableManager.sessionForViewer(player.getUniqueId());
                if (current == null) {
                    this.messages.send(player, "command.not_in_table");
                    return;
                }
                MahjongTableManager.LeaveResult result = this.tableManager.leave(player.getUniqueId());
                switch (result.status()) {
                    case LEFT -> this.messages.send(player, "command.left_table");
                    case UNSPECTATED -> this.messages.send(player, "command.unspectated");
                    case NOT_IN_TABLE -> this.messages.send(player, "command.not_in_table");
                    case BLOCKED -> this.messages.send(player, "command.leave_blocked_started");
                }
            }
            case "list" -> {
                if (this.tableManager.tables().isEmpty()) {
                    this.messages.send(player, "command.no_active_tables");
                    return;
                }
                Locale locale = this.messages.resolveLocale(player);
                this.tableManager.tables().forEach(table -> player.sendMessage(this.messages.render(
                    locale,
                    "command.table_list_entry",
                    this.messages.tag("table_id", table.id()),
                    this.messages.tag("summary", table.waitingSummary(locale)),
                    this.messages.number(locale, "x", table.center().getBlockX()),
                    this.messages.number(locale, "y", table.center().getBlockY()),
                    this.messages.number(locale, "z", table.center().getBlockZ())
                )));
            }
            case "spectate" -> {
                if (args.length < 2) {
                    this.messages.send(player, "command.spectate_usage");
                    return;
                }
                MahjongTableSession table = this.tableManager.spectate(player, args[1]);
                if (table == null) {
                    this.messages.send(player, "command.spectate_failed");
                } else {
                    this.messages.send(player, "command.spectate_joined", this.messages.tag("table_id", table.id()));
                }
            }
            case "unspectate" -> {
                MahjongTableSession table = this.tableManager.unspectate(player.getUniqueId());
                this.messages.send(player, table == null ? "command.not_in_table" : "command.unspectated");
            }
            case "addbot" -> {
                MahjongTableSession table = requireTable(player);
                if (table == null) {
                    return;
                }
                this.messages.send(player, table.addBot() ? "command.bot_added" : "command.bot_add_failed");
            }
            case "removebot" -> {
                MahjongTableSession table = requireTable(player);
                if (table == null) {
                    return;
                }
                this.messages.send(player, table.removeBot() ? "command.bot_removed" : "command.bot_remove_failed");
            }
            case "rule" -> {
                MahjongTableSession table = requireTable(player);
                if (table == null) {
                    return;
                }
                if (args.length == 1) {
                    Locale locale = this.messages.resolveLocale(player);
                    this.messages.send(player, "command.rule_summary", this.messages.tag("summary", table.ruleSummary(locale)));
                    return;
                }
                if (args.length < 3) {
                    this.messages.send(player, "command.rule_usage");
                    return;
                }
                Locale locale = this.messages.resolveLocale(player);
                this.messages.send(
                    player,
                    table.setRuleOption(args[1], args[2]) ? "command.rule_updated" : "command.rule_update_failed",
                    this.messages.tag("summary", table.ruleSummary(locale))
                );
            }
            case "start" -> {
                try {
                    this.tableManager.sendReadyResult(player, this.tableManager.start(player));
                } catch (IllegalStateException ex) {
                    this.messages.send(player, "command.start_failed", this.messages.tag("reason", ex.getMessage()));
                }
            }
            case "state" -> {
                MahjongTableSession table = requireViewedTable(player);
                if (table == null) {
                    return;
                }
                player.sendMessage(table.stateSummary(player));
            }
            case "riichi" -> {
                MahjongTableSession table = requireTable(player);
                if (table == null) {
                    return;
                }
                if (table.currentVariant() != MahjongVariant.RIICHI) {
                    this.messages.send(player, "command.variant_action_unavailable", this.messages.tag("action", "riichi"), this.messages.tag("variant", table.currentVariant().name()));
                    return;
                }
                if (args.length < 2) {
                    this.messages.send(player, "command.riichi_usage");
                    return;
                }
                java.util.OptionalInt index = parseIndex(args[1]);
                this.messages.send(player, index.isPresent() && table.declareRiichi(player.getUniqueId(), index.getAsInt())
                    ? "command.riichi_success"
                    : "command.riichi_failed");
            }
            case "tsumo" -> {
                MahjongTableSession table = requireTable(player);
                if (table == null) {
                    return;
                }
                this.messages.send(player, table.declareTsumo(player.getUniqueId()) ? "command.tsumo_success" : "command.tsumo_failed");
            }
            case "ron" -> sendReaction(player, ReactionType.RON, null, "command.action.ron");
            case "pon" -> sendReaction(player, ReactionType.PON, null, "command.action.pon");
            case "minkan" -> sendReaction(player, ReactionType.MINKAN, null, "command.action.minkan");
            case "skip" -> sendReaction(player, ReactionType.SKIP, null, "command.action.skip");
            case "chii" -> {
                if (args.length < 3) {
                    this.messages.send(player, "command.chii_usage");
                    return;
                }
                try {
                    MahjongTile first = MahjongTile.valueOf(args[1].toUpperCase(Locale.ROOT));
                    MahjongTile second = MahjongTile.valueOf(args[2].toUpperCase(Locale.ROOT));
                    sendReaction(player, ReactionType.CHII, new Pair<>(first, second), "command.action.chii");
                } catch (IllegalArgumentException ex) {
                    this.messages.send(player, "command.invalid_tile");
                }
            }
            case "kan" -> {
                MahjongTableSession table = requireTable(player);
                if (table == null || args.length < 2) {
                    this.messages.send(player, "command.kan_usage");
                    return;
                }
                this.messages.send(player, table.declareKan(player.getUniqueId(), args[1]) ? "command.kan_success" : "command.kan_failed");
            }
            case "kyuushu", "kyuushukyuuhai" -> {
                MahjongTableSession table = requireTable(player);
                if (table == null) {
                    return;
                }
                if (table.currentVariant() != MahjongVariant.RIICHI) {
                    this.messages.send(player, "command.variant_action_unavailable", this.messages.tag("action", "kyuushu"), this.messages.tag("variant", table.currentVariant().name()));
                    return;
                }
                this.messages.send(player, table.declareKyuushuKyuuhai(player.getUniqueId()) ? "command.kyuushu_success" : "command.kyuushu_failed");
            }
            case "settlement" -> {
                MahjongTableSession table = requireViewedTable(player);
                if (table == null) {
                    return;
                }
                this.messages.send(player, table.openSettlementUi(player) ? "command.settlement_opened" : "command.settlement_unavailable");
            }
            case "rank" -> this.showRank(player);
            case "render" -> {
                MahjongTableSession table = requireTable(player);
                if (table == null) {
                    return;
                }
                table.render();
                this.messages.send(player, "command.rendered");
            }
            case "inspect" -> {
                MahjongTableSession table = requireViewedTable(player);
                if (table == null) {
                    return;
                }
                table.inspectRender(player);
                this.messages.send(player, "command.inspect_sent");
            }
            case "clear" -> {
                MahjongTableSession table = requireTable(player);
                if (table == null) {
                    return;
                }
                table.clearDisplays();
                this.messages.send(player, "command.cleared");
            }
            default -> this.sendHelp(player);
        }
    }

    @Override
    public List<String> suggest(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (args.length == 0) {
            return this.visibleRootCommands(sender);
        }
        String rootArg = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return matchPrefix(args[0], this.visibleRootCommands(sender));
        }
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (this.isAdminRootCommand(rootArg) && !player.hasPermission("mahjongpaper.admin")) {
            return List.of();
        }

        if (args.length == 2 && ("forceend".equals(rootArg) || "deletetable".equals(rootArg))) {
            return matchPrefix(args[1], this.tableManager.tableIds());
        }
        if (args.length == 2 && "botmatch".equals(rootArg)) {
            return matchPrefix(args[1], BOTMATCH_PRESETS);
        }
        if (args.length == 2 && "mode".equals(rootArg)) {
            MahjongTableSession table = this.tableManager.tableFor(player.getUniqueId());
            return table == null ? List.of() : matchPrefix(args[1], table.ruleValues("mode"));
        }
        if (args.length == 2 && ("join".equals(rootArg) || "spectate".equals(rootArg))) {
            return matchPrefix(args[1], this.tableManager.tableIds());
        }
        if (args.length == 2 && "kan".equals(rootArg)) {
            return matchPrefix(args[1], suggestedKanTiles(player));
        }
        if (args.length == 2 && "rule".equals(rootArg)) {
            MahjongTableSession table = this.tableManager.tableFor(player.getUniqueId());
            return table == null ? List.of() : matchPrefix(args[1], table.ruleKeys());
        }
        if (args.length == 3 && "rule".equals(rootArg)) {
            MahjongTableSession table = this.tableManager.tableFor(player.getUniqueId());
            return table == null ? List.of() : matchPrefix(args[2], table.ruleValues(args[1]));
        }
        if (args.length == 2 && "chii".equals(rootArg)) {
            return matchPrefix(args[1], suggestedChiiFirstTiles(player));
        }
        if (args.length == 3 && "chii".equals(rootArg)) {
            return matchPrefix(args[2], suggestedChiiSecondTiles(player, args[1]));
        }
        if (args.length == 2 && "riichi".equals(rootArg)) {
            return matchPrefix(args[1], suggestedRiichiIndices(player));
        }
        return List.of();
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission(this.permission());
    }

    @Override
    public String permission() {
        return "mahjongpaper.command";
    }

    private MahjongTableSession requireTable(Player player) {
        MahjongTableSession table = this.tableManager.tableFor(player.getUniqueId());
        if (table == null) {
            this.messages.send(player, "command.not_in_table");
        }
        return table;
    }

    private void sendHelp(Player player) {
        Locale locale = this.messages.resolveLocale(player);
        this.messages.send(player, "command.usage");
        for (String key : HELP_KEYS) {
            if (ADMIN_HELP_KEYS.contains(key) && !player.hasPermission("mahjongpaper.admin")) {
                continue;
            }
            player.sendMessage(this.messages.render(locale, key));
        }
    }

    private MahjongTableSession requireViewedTable(Player player) {
        MahjongTableSession table = this.tableManager.sessionForViewer(player.getUniqueId());
        if (table == null) {
            this.messages.send(player, "command.not_in_table");
        }
        return table;
    }

    private void sendReaction(Player player, ReactionType type, Pair<MahjongTile, MahjongTile> pair, String actionKey) {
        MahjongTableSession table = requireTable(player);
        if (table == null) {
            return;
        }
        boolean ok = table.react(player.getUniqueId(), new ReactionResponse(type, pair));
        Locale locale = this.messages.resolveLocale(player);
        String action = this.messages.plain(locale, actionKey);
        this.messages.send(
            player,
            ok ? "command.reaction_submitted" : "command.reaction_failed",
            this.messages.tag("action", action)
        );
    }

    private boolean requireAdmin(Player player) {
        if (player.hasPermission("mahjongpaper.admin")) {
            return true;
        }
        this.messages.send(player, "command.admin_required");
        return false;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("mahjongpaper.admin")) {
            this.messages.send(sender, "command.admin_required");
            return;
        }
        String failure = this.plugin.reloadMahjongConfiguration();
        if (failure == null) {
            this.messages.send(sender, "command.reload_success");
            return;
        }
        this.messages.send(sender, "command.reload_failed", this.messages.tag("reason", failure));
    }

    private boolean isAdminRootCommand(String subcommand) {
        return ADMIN_ROOT_COMMANDS.contains(subcommand);
    }

    private List<String> visibleRootCommands(CommandSender sender) {
        List<String> commands = sender.hasPermission("mahjongpaper.admin")
            ? new ArrayList<>(ROOT_COMMANDS)
            : ROOT_COMMANDS.stream().filter(command -> !this.isAdminRootCommand(command)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (sender instanceof Player player) {
            MahjongTableSession table = this.tableManager.tableFor(player.getUniqueId());
            if (table != null && table.currentVariant() == MahjongVariant.GB) {
                commands.remove("riichi");
                commands.remove("kyuushu");
            }
        }
        return List.copyOf(commands);
    }

    private MahjongTableSession resolveAdminTable(Player player, String[] args) {
        if (args.length >= 2) {
            for (MahjongTableSession table : this.tableManager.tables()) {
                if (table.id().equalsIgnoreCase(args[1])) {
                    return table;
                }
            }
            this.messages.send(player, "command.table_not_found", this.messages.tag("table_id", args[1]));
            return null;
        }

        MahjongTableSession table = this.tableManager.sessionForViewer(player.getUniqueId());
        if (table == null) {
            table = this.tableManager.nearestTable(player.getLocation());
        }
        if (table == null) {
            this.messages.send(player, "command.admin_table_required");
        }
        return table;
    }

    private java.util.OptionalInt parseIndex(String raw) {
        try {
            return java.util.OptionalInt.of(Integer.parseInt(raw));
        } catch (NumberFormatException ex) {
            return java.util.OptionalInt.empty();
        }
    }

    private List<String> suggestedRiichiIndices(Player player) {
        MahjongTableSession table = this.tableManager.tableFor(player.getUniqueId());
        return table == null
            ? List.of()
            : table.suggestedRiichiIndices(player.getUniqueId()).stream().map(String::valueOf).toList();
    }

    private List<String> suggestedKanTiles(Player player) {
        MahjongTableSession table = this.tableManager.tableFor(player.getUniqueId());
        return table == null ? List.of() : table.suggestedKanTiles(player.getUniqueId());
    }

    private List<String> suggestedChiiFirstTiles(Player player) {
        ReactionOptions options = currentReactionOptions(player);
        if (options == null || options.getChiiPairs().isEmpty()) {
            return List.of();
        }
        Set<String> firstTiles = new LinkedHashSet<>();
        options.getChiiPairs().forEach(pair -> firstTiles.add(pair.getFirst().name().toLowerCase(Locale.ROOT)));
        return List.copyOf(firstTiles);
    }

    private List<String> suggestedChiiSecondTiles(Player player, String firstArg) {
        ReactionOptions options = currentReactionOptions(player);
        if (options == null || options.getChiiPairs().isEmpty()) {
            return List.of();
        }
        String normalizedFirst = firstArg.toUpperCase(Locale.ROOT);
        Set<String> secondTiles = new LinkedHashSet<>();
        options.getChiiPairs().forEach(pair -> {
            if (pair.getFirst().name().equals(normalizedFirst)) {
                secondTiles.add(pair.getSecond().name().toLowerCase(Locale.ROOT));
            }
        });
        return List.copyOf(secondTiles);
    }

    private ReactionOptions currentReactionOptions(Player player) {
        MahjongTableSession table = this.tableManager.tableFor(player.getUniqueId());
        return table == null ? null : table.availableReactions(player.getUniqueId());
    }

    private List<String> matchPrefix(String raw, Collection<String> values) {
        String prefix = raw.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(value);
            }
        }
        return matches;
    }

    private void showRank(Player player) {
        if (this.plugin.database() == null || !this.plugin.database().rankingEnabled()) {
            this.messages.send(player, "command.rank_unavailable");
            return;
        }
        this.messages.send(player, "command.rank_loading");
        this.plugin.async().execute("load-rank-" + player.getUniqueId(), () -> {
            try {
                MahjongSoulRankProfile profile = this.plugin.database().loadRankProfile(player.getUniqueId(), player.getName());
                this.plugin.scheduler().runEntity(player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    Locale locale = this.messages.resolveLocale(player);
                    this.messages.send(
                        player,
                        "command.rank_summary",
                        this.messages.tag("rank", rankLabel(locale, profile)),
                        this.messages.tag("points", MahjongSoulRankRules.formatPoints(profile)),
                        this.messages.number(locale, "matches", profile.totalMatches()),
                        this.messages.number(locale, "first", profile.firstPlaces()),
                        this.messages.number(locale, "second", profile.secondPlaces()),
                        this.messages.number(locale, "third", profile.thirdPlaces()),
                        this.messages.number(locale, "fourth", profile.fourthPlaces())
                    );
                });
            } catch (java.sql.SQLException ex) {
                this.plugin.scheduler().runEntity(player, () -> {
                    if (player.isOnline()) {
                        this.messages.send(player, "command.rank_failed");
                    }
                });
            }
        });
    }

    private String rankLabel(Locale locale, MahjongSoulRankProfile profile) {
        String tierKey = switch (profile.tier()) {
            case NOVICE -> "rank.tier.novice";
            case ADEPT -> "rank.tier.adept";
            case EXPERT -> "rank.tier.expert";
            case MASTER -> "rank.tier.master";
            case SAINT -> "rank.tier.saint";
            case CELESTIAL -> "rank.tier.celestial";
        };
        if (profile.isCelestial()) {
            return this.messages.plain(locale, tierKey) + " " + profile.level();
        }
        return this.messages.plain(locale, tierKey) + " " + profile.level();
    }
}

