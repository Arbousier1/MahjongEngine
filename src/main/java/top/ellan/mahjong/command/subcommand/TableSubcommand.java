package top.ellan.mahjong.command.subcommand;

import java.util.List;
import java.util.ArrayList;
import org.bukkit.command.CommandSender;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.table.core.MahjongTableSession;
import top.ellan.mahjong.ui.TableControlUi;

public final class TableSubcommand extends AbstractMahjongSubcommand {
    public TableSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("table", List.of("panel", "control"), false, true); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        if (args.length >= 2 && "owner".equalsIgnoreCase(args[1])) {
            this.transferOwner(player, args);
            return;
        }
        MahjongTableSession table = this.context.resolvePlayerVisibleTable(player, args);
        if (table == null) { return; }
        TableControlUi.open(player, table);
    }
    @Override protected List<String> suggest(Player player, String[] args) {
        if (args.length == 2) {
            List<String> values = new ArrayList<>(this.context.tableManager().tableIds());
            values.add("owner");
            return this.context.matchPrefix(args[1], values);
        }
        if (args.length == 3 && "owner".equalsIgnoreCase(args[1])) {
            MahjongTableSession table = this.currentOrNearestTable(player);
            return table == null ? List.of() : this.context.matchPrefix(args[2], this.onlineSeatedPlayerNames(table));
        }
        if (args.length == 4 && "owner".equalsIgnoreCase(args[1])) {
            return this.context.matchPrefix(args[3], this.context.tableManager().tableIds());
        }
        return List.of();
    }

    private void transferOwner(Player player, String[] args) {
        if (args.length < 3) {
            this.context.messages().send(player, "command.table_owner_usage");
            return;
        }
        MahjongTableSession table = args.length >= 4
            ? this.context.tableManager().resolveTableById(args[3])
            : this.currentOrNearestTable(player);
        if (table == null) {
            if (args.length >= 4) {
                this.context.messages().send(player, "command.table_not_found", this.context.messages().tag("table_id", args[3]));
            } else {
                this.context.messages().send(player, "command.table_panel_no_table");
            }
            return;
        }
        if (!this.context.requireTableManager(player, table)) {
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            this.context.messages().send(player, "command.table_owner_target_not_found", this.context.messages().tag("player", args[2]));
            return;
        }
        if (!table.contains(target.getUniqueId()) || table.isBot(target.getUniqueId())) {
            this.context.messages().send(player, "command.table_owner_target_not_seated", this.context.messages().tag("player", target.getName()));
            return;
        }
        table.setOwner(target.getUniqueId());
        table.render();
        this.context.messages().send(
            player,
            "command.table_owner_transferred",
            this.context.messages().tag("player", target.getName()),
            this.context.messages().tag("table_id", table.id())
        );
        if (!target.getUniqueId().equals(player.getUniqueId())) {
            this.context.messages().send(
                target,
                "command.table_owner_received",
                this.context.messages().tag("table_id", table.id())
            );
        }
    }

    private MahjongTableSession currentOrNearestTable(Player player) {
        MahjongTableSession table = this.context.tableManager().sessionForViewer(player.getUniqueId());
        return table == null ? this.context.tableManager().nearestTable(player.getLocation()) : table;
    }

    private List<String> onlineSeatedPlayerNames(MahjongTableSession table) {
        List<String> names = new ArrayList<>();
        for (java.util.UUID playerId : table.players()) {
            if (table.isBot(playerId)) {
                continue;
            }
            Player seated = Bukkit.getPlayer(playerId);
            if (seated != null) {
                names.add(seated.getName());
            }
        }
        return names;
    }
}
