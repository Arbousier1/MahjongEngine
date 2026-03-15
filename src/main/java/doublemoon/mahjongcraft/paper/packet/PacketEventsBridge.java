package doublemoon.mahjongcraft.paper.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import doublemoon.mahjongcraft.paper.MahjongPaperPlugin;
import doublemoon.mahjongcraft.paper.render.DisplayClickAction;
import doublemoon.mahjongcraft.paper.render.DisplayVisibilityRegistry;
import doublemoon.mahjongcraft.paper.render.TableDisplayRegistry;
import doublemoon.mahjongcraft.paper.table.MahjongTableManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PacketEventsBridge {
    private static final long CLICK_DEDUP_WINDOW_MILLIS = 100L;
    private final MahjongPaperPlugin plugin;
    private final MahjongTableManager tableManager;
    private final Map<UUID, RecentInteraction> recentInteractions = new ConcurrentHashMap<>();
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
                if (PacketEventsBridge.this.isDuplicateInteraction(player.getUniqueId(), interaction.getEntityId())) {
                    PacketEventsBridge.this.plugin.debug().log(
                        "packet",
                        "Ignored duplicate interact entity packet from " + player.getName() + " for entity=" + interaction.getEntityId()
                    );
                    return;
                }
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
                    PacketEventsBridge.this.cancelHiddenEntityPacket(event, viewerId, packet.getEntityId(), player.getName(), "SPAWN_ENTITY");
                    return;
                }
                if (event.getPacketType() == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
                    WrapperPlayServerSpawnLivingEntity packet = new WrapperPlayServerSpawnLivingEntity(event);
                    PacketEventsBridge.this.cancelHiddenEntityPacket(event, viewerId, packet.getEntityId(), player.getName(), "SPAWN_LIVING_ENTITY");
                    return;
                }
                if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
                    WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
                    PacketEventsBridge.this.cancelHiddenEntityPacket(event, viewerId, packet.getEntityId(), player.getName(), "ENTITY_METADATA");
                    return;
                }
                if (event.getPacketType() == PacketType.Play.Server.ENTITY_TELEPORT) {
                    WrapperPlayServerEntityTeleport packet = new WrapperPlayServerEntityTeleport(event);
                    PacketEventsBridge.this.cancelHiddenEntityPacket(event, viewerId, packet.getEntityId(), player.getName(), "ENTITY_TELEPORT");
                    return;
                }
                if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
                    WrapperPlayServerEntityRelativeMove packet = new WrapperPlayServerEntityRelativeMove(event);
                    PacketEventsBridge.this.cancelHiddenEntityPacket(event, viewerId, packet.getEntityId(), player.getName(), "ENTITY_RELATIVE_MOVE");
                    return;
                }
                if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
                    WrapperPlayServerEntityRelativeMoveAndRotation packet = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
                    PacketEventsBridge.this.cancelHiddenEntityPacket(event, viewerId, packet.getEntityId(), player.getName(), "ENTITY_RELATIVE_MOVE_AND_ROTATION");
                }
            }
        };
    }

    private void cancelHiddenEntityPacket(PacketPlaySendEvent event, UUID viewerId, int entityId, String playerName, String packetType) {
        if (!DisplayVisibilityRegistry.canView(entityId, viewerId)) {
            this.plugin.debug().log("packet", "Cancelled " + packetType + ' ' + entityId + " for " + playerName);
            event.setCancelled(true);
        }
    }

    private boolean isDuplicateInteraction(UUID playerId, int entityId) {
        long now = System.currentTimeMillis();
        RecentInteraction previous = this.recentInteractions.put(playerId, new RecentInteraction(entityId, now));
        return previous != null && previous.entityId() == entityId && now - previous.timestampMillis() <= CLICK_DEDUP_WINDOW_MILLIS;
    }

    public void enable() {
        PacketEvents.getAPI().getEventManager().registerListener(this.listener);
    }

    public void disable() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this.listener);
        this.recentInteractions.clear();
    }

    private record RecentInteraction(int entityId, long timestampMillis) {
    }
}
