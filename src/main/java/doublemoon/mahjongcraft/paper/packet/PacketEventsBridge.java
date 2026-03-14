package doublemoon.mahjongcraft.paper.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import doublemoon.mahjongcraft.paper.MahjongPaperPlugin;
import doublemoon.mahjongcraft.paper.render.DisplayClickAction;
import doublemoon.mahjongcraft.paper.render.TableDisplayRegistry;
import doublemoon.mahjongcraft.paper.render.DisplayVisibilityRegistry;
import doublemoon.mahjongcraft.paper.table.MahjongTableManager;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PacketEventsBridge {
    private final MahjongPaperPlugin plugin;
    private final MahjongTableManager tableManager;
    private final SimplePacketListenerAbstract listener;

    public PacketEventsBridge(MahjongPaperPlugin plugin, MahjongTableManager tableManager) {
        this.plugin = plugin;
        this.tableManager = tableManager;
        this.listener = new SimplePacketListenerAbstract(PacketListenerPriority.NORMAL) {
            @Override
            public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
                if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
                    return;
                }
                WrapperPlayClientInteractEntity interaction = new WrapperPlayClientInteractEntity(event);
                if (interaction.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                    return;
                }

                DisplayClickAction action = TableDisplayRegistry.get(interaction.getEntityId());
                if (action == null) {
                    return;
                }

                Player player = event.getPlayer();
                PacketEventsBridge.this.plugin.debug().log(
                    "packet",
                    "Interact entity packet from " + player.getName() + " for entity=" + interaction.getEntityId()
                );
                Bukkit.getScheduler().runTask(PacketEventsBridge.this.plugin, () -> {
                    boolean accepted = PacketEventsBridge.this.tableManager.clickTile(
                        player,
                        action.tableId(),
                        action.ownerId(),
                        action.tileIndex()
                    );
                    if (!accepted) {
                        PacketEventsBridge.this.plugin.messages().actionBar(player, "packet.cannot_click_tile");
                    }
                });
            }

            @Override
            public void onPacketPlaySend(PacketPlaySendEvent event) {
                Player player = event.getPlayer();
                if (player == null) {
                    return;
                }
                UUID viewerId = player.getUniqueId();
                if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
                    WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
                    if (!DisplayVisibilityRegistry.canView(packet.getEntityId(), viewerId)) {
                        PacketEventsBridge.this.plugin.debug().log("packet", "Cancelled SPAWN_ENTITY " + packet.getEntityId() + " for " + player.getName());
                        event.setCancelled(true);
                    }
                    return;
                }
                if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
                    WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
                    if (!DisplayVisibilityRegistry.canView(packet.getEntityId(), viewerId)) {
                        PacketEventsBridge.this.plugin.debug().log("packet", "Cancelled ENTITY_METADATA " + packet.getEntityId() + " for " + player.getName());
                        event.setCancelled(true);
                    }
                }
            }
        };
    }

    public void enable() {
        PacketEvents.getAPI().getEventManager().registerListener(this.listener);
    }

    public void disable() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this.listener);
    }
}
