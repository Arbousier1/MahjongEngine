package top.ellan.mahjong.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import top.ellan.mahjong.bootstrap.MahjongPaperPlugin;
import top.ellan.mahjong.command.subcommand.AddBotSubcommand;
import top.ellan.mahjong.command.subcommand.BotMatchSubcommand;
import top.ellan.mahjong.command.subcommand.ChiiSubcommand;
import top.ellan.mahjong.command.subcommand.ClearSubcommand;
import top.ellan.mahjong.command.subcommand.CreateSubcommand;
import top.ellan.mahjong.command.subcommand.DebugSubcommand;
import top.ellan.mahjong.command.subcommand.DeleteTableSubcommand;
import top.ellan.mahjong.command.subcommand.ForceEndSubcommand;
import top.ellan.mahjong.command.subcommand.HelpSubcommand;
import top.ellan.mahjong.command.subcommand.InspectSubcommand;
import top.ellan.mahjong.command.subcommand.JoinSubcommand;
import top.ellan.mahjong.command.subcommand.KanSubcommand;
import top.ellan.mahjong.command.subcommand.KyuushuSubcommand;
import top.ellan.mahjong.command.subcommand.LeaderboardSubcommand;
import top.ellan.mahjong.command.subcommand.LeaveSubcommand;
import top.ellan.mahjong.command.subcommand.ListSubcommand;
import top.ellan.mahjong.command.subcommand.MinkanSubcommand;
import top.ellan.mahjong.command.subcommand.ModeSubcommand;
import top.ellan.mahjong.command.subcommand.PonSubcommand;
import top.ellan.mahjong.command.subcommand.RankSubcommand;
import top.ellan.mahjong.command.subcommand.ReloadSubcommand;
import top.ellan.mahjong.command.subcommand.RemoveBotSubcommand;
import top.ellan.mahjong.command.subcommand.RenderSubcommand;
import top.ellan.mahjong.command.subcommand.RiichiSubcommand;
import top.ellan.mahjong.command.subcommand.RonSubcommand;
import top.ellan.mahjong.command.subcommand.RuleSubcommand;
import top.ellan.mahjong.command.subcommand.SettlementSubcommand;
import top.ellan.mahjong.command.subcommand.SkipSubcommand;
import top.ellan.mahjong.command.subcommand.SpectateSubcommand;
import top.ellan.mahjong.command.subcommand.StartSubcommand;
import top.ellan.mahjong.command.subcommand.StateSubcommand;
import top.ellan.mahjong.command.subcommand.TableSubcommand;
import top.ellan.mahjong.command.subcommand.TsumoSubcommand;
import top.ellan.mahjong.command.subcommand.UnspectateSubcommand;
import top.ellan.mahjong.table.core.MahjongTableManager;
import top.ellan.mahjong.table.core.MahjongTableSession;
import top.ellan.mahjong.table.core.MahjongVariant;

public final class MahjongCommand implements CommandExecutor, TabCompleter {
    private final MahjongCommandContext context;
    private final Map<String, MahjongSubcommand> subcommands;

    public MahjongCommand(MahjongPaperPlugin plugin, MahjongTableManager tableManager) {
        this.context = new MahjongCommandContext(plugin, tableManager);
        this.subcommands = this.createSubcommands();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(this.permission())) {
            this.context.messages().send(sender, "command.admin_required");
            return true;
        }
        this.handleCommand(sender, args);
        return true;
    }

    private void handleCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                this.context.sendHelp(player);
            } else {
                this.context.messages().send(sender, "command.usage");
            }
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        MahjongSubcommand command = this.subcommands.get(sub);
        if (command == null) {
            if (sender instanceof Player player) {
                this.context.plugin().debug().log("command", player.getName() + " executed /mahjong " + String.join(" ", args));
                this.context.sendHelp(player);
            } else {
                this.context.messages().send(sender, "common.only_players");
            }
            return;
        }
        if (command.playerOnly() && !(sender instanceof Player)) {
            this.context.messages().send(sender, "common.only_players");
            return;
        }
        if (command.adminOnly() && !this.context.requireAdmin(sender)) {
            return;
        }
        Player player = sender instanceof Player playerSender ? playerSender : null;
        if (player != null && command.playerOnly()) {
            this.context.plugin().debug().log("command", player.getName() + " executed /mahjong " + String.join(" ", args));
        }
        command.executor().execute(sender, player, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command bukkitCommand, String alias, String[] args) {
        if (args.length == 0) {
            return this.visibleRootCommands(sender);
        }
        String rootArg = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return this.context.matchPrefix(args[0], this.visibleRootCommands(sender));
        }
        MahjongSubcommand subcommand = this.subcommands.get(rootArg);
        if (subcommand == null || (subcommand.adminOnly() && !sender.hasPermission(MahjongCommandContext.ADMIN_PERMISSION))) {
            return List.of();
        }
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return subcommand.suggestions().suggest(player, args);
    }

    public boolean canUse(CommandSender sender) {
        return sender.hasPermission(this.permission());
    }

    public String permission() {
        return "mahjongpaper.command";
    }

    private Map<String, MahjongSubcommand> createSubcommands() {
        Map<String, MahjongSubcommand> registry = new LinkedHashMap<>();
        this.register(registry, new HelpSubcommand(this.context).create());
        this.register(registry, new CreateSubcommand(this.context).create());
        this.register(registry, new BotMatchSubcommand(this.context).create());
        this.register(registry, new ModeSubcommand(this.context).create());
        this.register(registry, new JoinSubcommand(this.context).create());
        this.register(registry, new LeaveSubcommand(this.context).create());
        this.register(registry, new ListSubcommand(this.context).create());
        this.register(registry, new SpectateSubcommand(this.context).create());
        this.register(registry, new UnspectateSubcommand(this.context).create());
        this.register(registry, new TableSubcommand(this.context).create());
        this.register(registry, new AddBotSubcommand(this.context).create());
        this.register(registry, new RemoveBotSubcommand(this.context).create());
        this.register(registry, new RuleSubcommand(this.context).create());
        this.register(registry, new StartSubcommand(this.context).create());
        this.register(registry, new StateSubcommand(this.context).create());
        this.register(registry, new RiichiSubcommand(this.context).create());
        this.register(registry, new TsumoSubcommand(this.context).create());
        this.register(registry, new RonSubcommand(this.context).create());
        this.register(registry, new PonSubcommand(this.context).create());
        this.register(registry, new MinkanSubcommand(this.context).create());
        this.register(registry, new ChiiSubcommand(this.context).create());
        this.register(registry, new KanSubcommand(this.context).create());
        this.register(registry, new SkipSubcommand(this.context).create());
        this.register(registry, new KyuushuSubcommand(this.context).create());
        this.register(registry, new SettlementSubcommand(this.context).create());
        this.register(registry, new RankSubcommand(this.context).create());
        this.register(registry, new LeaderboardSubcommand(this.context).create());
        this.register(registry, new RenderSubcommand(this.context).create());
        this.register(registry, new InspectSubcommand(this.context).create());
        this.register(registry, new DebugSubcommand(this.context).create());
        this.register(registry, new ClearSubcommand(this.context).create());
        this.register(registry, new ForceEndSubcommand(this.context).create());
        this.register(registry, new DeleteTableSubcommand(this.context).create());
        this.register(registry, new ReloadSubcommand(this.context).create());
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(registry));
    }

    private void register(Map<String, MahjongSubcommand> registry, MahjongSubcommand command) {
        registry.put(command.name().toLowerCase(Locale.ROOT), command);
        for (String alias : command.aliases()) {
            registry.put(alias.toLowerCase(Locale.ROOT), command);
        }
    }

    private List<String> visibleRootCommands(CommandSender sender) {
        List<String> commands = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (MahjongSubcommand command : this.subcommands.values()) {
            if (!seen.add(command.name())) {
                continue;
            }
            if (command.adminOnly() && !sender.hasPermission(MahjongCommandContext.ADMIN_PERMISSION)) {
                continue;
            }
            commands.add(command.name());
        }
        if (sender instanceof Player player) {
            MahjongTableSession table = this.context.tableManager().tableFor(player.getUniqueId());
            if (table != null && table.currentVariant() != MahjongVariant.RIICHI) {
                commands.remove("riichi");
                commands.remove("kyuushu");
            }
        }
        return List.copyOf(commands);
    }
}
