package top.ellan.mahjong.command.subcommand;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.table.core.MahjongTableSession;
import top.ellan.mahjong.model.MahjongVariant;

public final class KyuushuSubcommand extends AbstractMahjongSubcommand {
    public KyuushuSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("kyuushu", List.of("kyuushukyuuhai"), false, true); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        MahjongTableSession table = this.context.requireTable(player);
        if (table == null) { return; }
        if (table.currentVariant() != MahjongVariant.RIICHI) {
            this.context.messages().send(player, "command.variant_action_unavailable", this.context.messages().tag("action", "kyuushu"), this.context.messages().tag("variant", table.currentVariant().name()));
            return;
        }
        this.context.messages().send(player, table.declareKyuushuKyuuhai(player.getUniqueId()) ? "command.kyuushu_success" : "command.kyuushu_failed");
    }
}