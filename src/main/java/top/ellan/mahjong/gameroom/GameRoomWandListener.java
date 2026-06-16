package top.ellan.mahjong.gameroom;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import top.ellan.mahjong.i18n.MessageService;

public final class GameRoomWandListener implements Listener {
    static final Material WAND_MATERIAL = Material.BLAZE_ROD;
    private static final String WAND_NBT_KEY = "mahjongpaper:wand";

    private final GameRoomSelectionService selectionService;
    private final MessageService messages;

    public GameRoomWandListener(GameRoomSelectionService selectionService, MessageService messages) {
        this.selectionService = selectionService;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!isWand(item)) {
            return;
        }
        if (!player.hasPermission("mahjongpaper.admin")) {
            return;
        }

        Action action = event.getAction();
        UUID playerId = player.getUniqueId();

        if (action == Action.LEFT_CLICK_BLOCK) {
            Location loc = event.getClickedBlock().getLocation();
            this.selectionService.setFirst(playerId, loc);
            this.messages.send(player, "gameroom.wand_first",
                this.messages.tag("x", String.valueOf(loc.getBlockX())),
                this.messages.tag("y", String.valueOf(loc.getBlockY())),
                this.messages.tag("z", String.valueOf(loc.getBlockZ()))
            );
            event.setCancelled(true);
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            Location loc = event.getClickedBlock().getLocation();
            this.selectionService.setSecond(playerId, loc);
            this.messages.send(player, "gameroom.wand_second",
                this.messages.tag("x", String.valueOf(loc.getBlockX())),
                this.messages.tag("y", String.valueOf(loc.getBlockY())),
                this.messages.tag("z", String.valueOf(loc.getBlockZ()))
            );
            event.setCancelled(true);
        }
    }

    public static ItemStack createWand() {
        ItemStack wand = new ItemStack(WAND_MATERIAL);
        var meta = wand.getItemMeta();
        if (meta != null) {
            var persistentDataContainer = meta.getPersistentDataContainer();
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey("mahjongpaper", "wand");
            persistentDataContainer.set(key, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            meta.setDisplayName("§6§lMahjong Room Wand");
            meta.setLore(java.util.List.of(
                "§7Left-click: Set first corner",
                "§7Right-click: Set second corner",
                "§7Then use /mahjong room create <id>"
            ));
            wand.setItemMeta(meta);
        }
        return wand;
    }

    public static boolean isWand(ItemStack item) {
        if (item == null || item.getType() != WAND_MATERIAL) {
            return false;
        }
        var meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        var container = meta.getPersistentDataContainer();
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey("mahjongpaper", "wand");
        return container.has(key, org.bukkit.persistence.PersistentDataType.BYTE);
    }
}
