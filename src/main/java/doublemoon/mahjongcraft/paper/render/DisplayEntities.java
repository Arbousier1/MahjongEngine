package doublemoon.mahjongcraft.paper.render;

import doublemoon.mahjongcraft.paper.model.MahjongTile;
import net.kyori.adventure.text.Component;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class DisplayEntities {
    private static final String ITEM_MODEL_NAMESPACE = "mahjongcraft";
    private static final String MANAGED_ENTITY_KEY = "managed_entity";
    private static final float TILE_SCALE = 1.0F;
    private static final float LABEL_VIEW_RANGE = 48.0F;
    private static final Map<String, ItemStack> TILE_ITEM_CACHE = new ConcurrentHashMap<>();

    private DisplayEntities() {
    }

    public static ItemDisplay spawnTileDisplay(
        Plugin plugin,
        Location location,
        float yaw,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault
    ) {
        return spawnTileDisplay(plugin, location, yaw, tile, pose, clickAction, visibleByDefault, null);
    }

    public static ItemDisplay spawnTileDisplay(
        Plugin plugin,
        Location location,
        float yaw,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers
    ) {
        return spawnTileDisplay(plugin, location, yaw, tile, pose, clickAction, visibleByDefault, privateViewers, TILE_SCALE, null);
    }

    public static ItemDisplay spawnTileDisplay(
        Plugin plugin,
        Location location,
        float yaw,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers,
        float scale,
        Color glowColor
    ) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location world is null");
        }

        ItemDisplay display = world.spawn(location, ItemDisplay.class, spawned -> {
            boolean restrictedVisibility = privateViewers != null;
            spawned.setPersistent(false);
            markManagedEntity(plugin, spawned);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
            spawned.setInterpolationDuration(1);
            spawned.setInterpolationDelay(0);
            spawned.setTeleportDuration(1);
            spawned.setViewRange(32.0F);
            spawned.setShadowRadius(0.0F);
            spawned.setShadowStrength(0.0F);
            spawned.setDisplayWidth(0.4F * scale);
            spawned.setDisplayHeight(0.6F * scale);
            spawned.setRotation(yaw, 0.0F);
            spawned.setVisibleByDefault(!restrictedVisibility && visibleByDefault);
            if (glowColor != null) {
                spawned.setGlowing(true);
                spawned.setGlowColorOverride(glowColor);
                spawned.setBrightness(new Display.Brightness(15, 15));
            }
            spawned.setTransformation(new Transformation(
                new Vector3f(),
                new AxisAngle4f((float) Math.toRadians(pose.xRotationDegrees()), 1.0F, 0.0F, 0.0F),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f()
            ));
            spawned.setItemStack(tileItem(plugin, tile, pose.faceDown()));
        });

        if (clickAction != null) {
            TableDisplayRegistry.register(display.getEntityId(), clickAction);
        }
        if (privateViewers != null) {
            if (privateViewers.isEmpty()) {
                DisplayVisibilityRegistry.registerHidden(display.getEntityId());
            } else {
                DisplayVisibilityRegistry.registerPrivate(display.getEntityId(), privateViewers);
            }
            syncPrivateVisibility(plugin, display, privateViewers);
        }
        registerForCraftEngineCulling(plugin, display);
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
            boolean privateOnly = privateViewers != null && !privateViewers.isEmpty();
            spawned.setPersistent(false);
            markManagedEntity(plugin, spawned);
            spawned.text(text);
            spawned.setSeeThrough(false);
            spawned.setShadowed(true);
            spawned.setDefaultBackground(false);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setLineWidth(160);
            spawned.setViewRange(LABEL_VIEW_RANGE);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setBackgroundColor(color);
            spawned.setVisibleByDefault(!privateOnly);
        });
        if (privateViewers != null && !privateViewers.isEmpty()) {
            DisplayVisibilityRegistry.registerPrivate(display.getEntityId(), privateViewers);
            syncPrivateVisibility(plugin, display, privateViewers);
        }
        registerForCraftEngineCulling(plugin, display);
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
            boolean privateOnly = privateViewers != null && !privateViewers.isEmpty();
            spawned.setPersistent(false);
            markManagedEntity(plugin, spawned);
            spawned.setResponsive(true);
            spawned.setInteractionWidth(width);
            spawned.setInteractionHeight(height);
            spawned.setVisibleByDefault(!privateOnly);
        });
        if (clickAction != null) {
            TableDisplayRegistry.register(interaction.getEntityId(), clickAction);
        }
        if (privateViewers != null && !privateViewers.isEmpty()) {
            DisplayVisibilityRegistry.registerPrivate(interaction.getEntityId(), privateViewers);
            syncPrivateVisibility(plugin, interaction, privateViewers);
        }
        registerForCraftEngineCulling(plugin, interaction);
        return interaction;
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
            boolean privateOnly = privateViewers != null && !privateViewers.isEmpty();
            spawned.setPersistent(false);
            markManagedEntity(plugin, spawned);
            spawned.setInterpolationDuration(1);
            spawned.setInterpolationDelay(0);
            spawned.setTeleportDuration(1);
            spawned.setViewRange(48.0F);
            spawned.setShadowRadius(0.0F);
            spawned.setShadowStrength(0.0F);
            spawned.setVisibleByDefault(!privateOnly && visibleByDefault);
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
            syncPrivateVisibility(plugin, display, privateViewers);
        }
        registerForCraftEngineCulling(plugin, display);
        return display;
    }

    private static void syncPrivateVisibility(Plugin plugin, Entity entity, Collection<UUID> privateViewers) {
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            if (!privateViewers.isEmpty() && privateViewers.contains(player.getUniqueId())) {
                player.showEntity(plugin, entity);
            } else {
                player.hideEntity(plugin, entity);
            }
        }
    }

    private static void registerForCraftEngineCulling(Plugin plugin, org.bukkit.entity.Entity entity) {
        if (plugin instanceof doublemoon.mahjongcraft.paper.MahjongPaperPlugin mahjongPlugin && mahjongPlugin.craftEngine() != null) {
            mahjongPlugin.craftEngine().registerCullableEntity(entity);
        }
    }

    public static boolean isManagedEntity(Plugin plugin, Entity entity) {
        if (plugin == null || entity == null) {
            return false;
        }
        return entity.getPersistentDataContainer().has(managedEntityKey(plugin), PersistentDataType.BYTE);
    }

    private static void markManagedEntity(Plugin plugin, Entity entity) {
        entity.getPersistentDataContainer().set(managedEntityKey(plugin), PersistentDataType.BYTE, (byte) 1);
    }

    private static NamespacedKey managedEntityKey(Plugin plugin) {
        return new NamespacedKey(plugin, MANAGED_ENTITY_KEY);
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

    public enum TileRenderPose {
        STANDING(false, 0.0F),
        STANDING_FACE_DOWN(true, 0.0F),
        FLAT_FACE_UP(false, -90.0F),
        FLAT_FACE_DOWN(true, 90.0F);

        private final boolean faceDown;
        private final float xRotationDegrees;

        TileRenderPose(boolean faceDown, float xRotationDegrees) {
            this.faceDown = faceDown;
            this.xRotationDegrees = xRotationDegrees;
        }

        public boolean faceDown() {
            return this.faceDown;
        }

        public float xRotationDegrees() {
            return this.xRotationDegrees;
        }
    }
}
