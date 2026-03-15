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
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class DisplayEntities {
    private static final String ITEM_MODEL_NAMESPACE = "mahjongcraft";
    private static final float TILE_SCALE = 1.0F;
    private static final float LABEL_VIEW_RANGE = 48.0F;
    private static final float TILE_HEIGHT = 0.15F;
    private static final float TILE_DEPTH = 0.075F;
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
        Location renderedLocation = tileRenderLocation(location, yaw, pose);
        World world = renderedLocation.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location world is null");
        }

        ItemDisplay display = world.spawn(renderedLocation, ItemDisplay.class, spawned -> {
            boolean restrictedVisibility = privateViewers != null;
            spawned.setPersistent(false);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
            spawned.setInterpolationDuration(1);
            spawned.setInterpolationDelay(0);
            spawned.setTeleportDuration(1);
            spawned.setViewRange(32.0F);
            spawned.setShadowRadius(0.0F);
            spawned.setShadowStrength(0.0F);
            spawned.setDisplayWidth(0.4F);
            spawned.setDisplayHeight(0.6F);
            spawned.setRotation(tileDisplayYaw(yaw), 0.0F);
            spawned.setVisibleByDefault(!restrictedVisibility && visibleByDefault);
            spawned.setTransformation(new Transformation(
                new Vector3f(),
                new AxisAngle4f((float) Math.toRadians(pose.xRotationDegrees()), 1.0F, 0.0F, 0.0F),
                new Vector3f(TILE_SCALE, TILE_SCALE, TILE_SCALE),
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

    static Location tileRenderLocation(Location location, float yaw, TileRenderPose pose) {
        Offset3 offset = rotateTileOffset(pose.localYOffset(), pose.localZOffset(), pose.xRotationDegrees(), tileDisplayYaw(yaw));
        return location.clone().add(offset.x(), offset.y(), offset.z());
    }

    static float tileDisplayYaw(float yaw) {
        float renderedYaw = -yaw + 180.0F;
        while (renderedYaw >= 180.0F) {
            renderedYaw -= 360.0F;
        }
        while (renderedYaw < -180.0F) {
            renderedYaw += 360.0F;
        }
        return renderedYaw;
    }

    private static Offset3 rotateTileOffset(float localYOffset, float localZOffset, float xRotationDegrees, float renderedYawDegrees) {
        double xRotationRadians = Math.toRadians(xRotationDegrees);
        double cosX = Math.cos(xRotationRadians);
        double sinX = Math.sin(xRotationRadians);
        double rotatedY = localYOffset * cosX - localZOffset * sinX;
        double rotatedZ = localYOffset * sinX + localZOffset * cosX;

        double yawRadians = Math.toRadians(renderedYawDegrees);
        double sinYaw = Math.sin(yawRadians);
        double cosYaw = Math.cos(yawRadians);
        double rotatedX = -sinYaw * rotatedZ;
        double finalZ = cosYaw * rotatedZ;
        return new Offset3(rotatedX, rotatedY, finalZ);
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
        STANDING(false, 0.0F, TILE_HEIGHT / 2.0F, 0.0F),
        STANDING_FACE_DOWN(true, 0.0F, TILE_HEIGHT / 2.0F, 0.0F),
        FLAT_FACE_UP(false, 90.0F, 0.0F, -TILE_DEPTH / 2.0F),
        FLAT_FACE_DOWN(true, -90.0F, 0.0F, TILE_DEPTH / 2.0F);

        private final boolean faceDown;
        private final float xRotationDegrees;
        private final float localYOffset;
        private final float localZOffset;

        TileRenderPose(boolean faceDown, float xRotationDegrees, float localYOffset, float localZOffset) {
            this.faceDown = faceDown;
            this.xRotationDegrees = xRotationDegrees;
            this.localYOffset = localYOffset;
            this.localZOffset = localZOffset;
        }

        public boolean faceDown() {
            return this.faceDown;
        }

        public float xRotationDegrees() {
            return this.xRotationDegrees;
        }

        public float localYOffset() {
            return this.localYOffset;
        }

        public float localZOffset() {
            return this.localZOffset;
        }
    }

    private record Offset3(double x, double y, double z) {
    }
}
