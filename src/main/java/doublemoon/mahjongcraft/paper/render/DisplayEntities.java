package doublemoon.mahjongcraft.paper.render;

import doublemoon.mahjongcraft.paper.model.MahjongTile;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DisplayEntities {
    private static final String ITEM_MODEL_NAMESPACE = "mahjongcraft";
    private static final float TILE_SCALE = 1.0F;
    private static final Map<String, ItemStack> TILE_ITEM_CACHE = new ConcurrentHashMap<>();

    private DisplayEntities() {
    }

    public static ItemDisplay spawnTileDisplay(
        Plugin plugin,
        Location location,
        float yaw,
        MahjongTile tile,
        boolean faceDown,
        boolean flatOnTable,
        DisplayClickAction clickAction,
        boolean visibleByDefault
    ) {
        return spawnTileDisplay(plugin, location, yaw, tile, faceDown, flatOnTable, clickAction, visibleByDefault, null);
    }

    public static ItemDisplay spawnTileDisplay(
        Plugin plugin,
        Location location,
        float yaw,
        MahjongTile tile,
        boolean faceDown,
        boolean flatOnTable,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers
    ) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location world is null");
        }

        ItemDisplay display = world.spawn(location, ItemDisplay.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
            spawned.setInterpolationDuration(1);
            spawned.setInterpolationDelay(0);
            spawned.setTeleportDuration(1);
            spawned.setViewRange(32.0F);
            spawned.setShadowRadius(0.0F);
            spawned.setShadowStrength(0.0F);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setDisplayWidth(0.4F);
            spawned.setDisplayHeight(0.6F);
            spawned.setRotation(yaw, 0.0F);
            spawned.setVisibleByDefault(visibleByDefault);
            spawned.setTransformation(new Transformation(
                new Vector3f(),
                new AxisAngle4f((float) Math.toRadians(flatOnTable ? 90.0F : 0.0F), 1.0F, 0.0F, 0.0F),
                new Vector3f(TILE_SCALE, TILE_SCALE, TILE_SCALE),
                new AxisAngle4f()
            ));
            spawned.setItemStack(tileItem(plugin, tile, faceDown));
        });

        if (clickAction != null) {
            TableDisplayRegistry.register(display.getEntityId(), clickAction);
        }
        if (privateViewers != null && !privateViewers.isEmpty()) {
            DisplayVisibilityRegistry.registerPrivate(display.getEntityId(), privateViewers);
        }
        registerForCulling(plugin, display);
        return display;
    }

    public static TextDisplay spawnLabel(Plugin plugin, Location location, Component text, Color color) {
        return spawnLabel(plugin, location, text, color, null);
    }

    public static TextDisplay spawnLabel(Plugin plugin, Location location, Component text, Color color, Collection<UUID> privateViewers) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location world is null");
        }

        TextDisplay display = world.spawn(location, TextDisplay.class, spawned -> {
            spawned.setPersistent(false);
            spawned.text(text);
            spawned.setSeeThrough(false);
            spawned.setShadowed(true);
            spawned.setDefaultBackground(false);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setLineWidth(160);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setBackgroundColor(color);
            spawned.setVisibleByDefault(true);
        });
        if (privateViewers != null && !privateViewers.isEmpty()) {
            DisplayVisibilityRegistry.registerPrivate(display.getEntityId(), privateViewers);
        }
        registerForCulling(plugin, display);
        return display;
    }

    public static Interaction spawnInteraction(
        Plugin plugin,
        Location location,
        float width,
        float height
    ) {
        return spawnInteraction(plugin, location, width, height, null, null);
    }

    public static Interaction spawnInteraction(
        Plugin plugin,
        Location location,
        float width,
        float height,
        DisplayClickAction clickAction,
        Collection<UUID> privateViewers
    ) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location world is null");
        }

        Interaction interaction = world.spawn(location, Interaction.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setResponsive(true);
            spawned.setInteractionWidth(width);
            spawned.setInteractionHeight(height);
        });
        if (clickAction != null) {
            TableDisplayRegistry.register(interaction.getEntityId(), clickAction);
        }
        if (privateViewers != null && !privateViewers.isEmpty()) {
            DisplayVisibilityRegistry.registerPrivate(interaction.getEntityId(), privateViewers);
        }
        registerForCulling(plugin, interaction);
        return interaction;
    }

    public static Shulker spawnShulkerHitbox(Plugin plugin, Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location world is null");
        }

        Shulker shulker = world.spawn(location, Shulker.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setRemoveWhenFarAway(false);
            spawned.setAI(false);
            spawned.setAware(false);
            spawned.setCollidable(true);
            spawned.setInvisible(true);
            spawned.setInvulnerable(true);
            spawned.setSilent(true);
            spawned.setGravity(false);
            spawned.setPeek(0.0F);
            spawned.setRotation(0.0F, 0.0F);
            spawned.addScoreboardTag("mahjongcraft:table_hitbox");
        });
        DisplayVisibilityRegistry.registerHidden(shulker.getEntityId());
        return shulker;
    }

    public static BlockDisplay spawnBlockDisplay(Plugin plugin, Location location, Material material, float scaleX, float scaleY, float scaleZ) {
        return spawnBlockDisplay(plugin, location, material, scaleX, scaleY, scaleZ, true, null);
    }

    public static BlockDisplay spawnBlockDisplay(
        Plugin plugin,
        Location location,
        Material material,
        float scaleX,
        float scaleY,
        float scaleZ,
        boolean visibleByDefault,
        Collection<UUID> privateViewers
    ) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location world is null");
        }

        BlockDisplay display = world.spawn(location, BlockDisplay.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setInterpolationDuration(1);
            spawned.setInterpolationDelay(0);
            spawned.setTeleportDuration(1);
            spawned.setViewRange(48.0F);
            spawned.setShadowRadius(0.0F);
            spawned.setShadowStrength(0.0F);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setVisibleByDefault(visibleByDefault);
            spawned.setRotation(0.0F, 0.0F);
            spawned.setBlock(material.createBlockData());
            spawned.setTransformation(new Transformation(
                new Vector3f(),
                new AxisAngle4f(),
                new Vector3f(scaleX, scaleY, scaleZ),
                new AxisAngle4f()
            ));
        });
        if (privateViewers != null && !privateViewers.isEmpty()) {
            DisplayVisibilityRegistry.registerPrivate(display.getEntityId(), privateViewers);
        }
        registerForCulling(plugin, display);
        return display;
    }

    private static void registerForCulling(Plugin plugin, org.bukkit.entity.Entity entity) {
        if (plugin instanceof doublemoon.mahjongcraft.paper.MahjongPaperPlugin mahjongPlugin && mahjongPlugin.entityCulling() != null) {
            mahjongPlugin.entityCulling().register(entity);
        }
    }

    private static ItemStack tileItem(Plugin plugin, MahjongTile tile, boolean faceDown) {
        if (plugin instanceof doublemoon.mahjongcraft.paper.MahjongPaperPlugin mahjongPlugin) {
            ItemStack customItem = mahjongPlugin.craftEngine().resolveTileItem(tile, faceDown);
            if (customItem != null) {
                return customItem;
            }
        }
        String path = faceDown ? "mahjong_tile/back" : tile.itemModelPath();
        return TILE_ITEM_CACHE.computeIfAbsent(path, key -> createTileItem(tile, key)).clone();
    }

    private static ItemStack createTileItem(MahjongTile tile, String path) {
        ItemStack itemStack = new ItemStack(Material.PAPER);
        ItemMeta meta = itemStack.getItemMeta();
        meta.setItemModel(new NamespacedKey(ITEM_MODEL_NAMESPACE, path));
        meta.displayName(Component.text(tile.name()));
        itemStack.setItemMeta(meta);
        return itemStack;
    }
}
