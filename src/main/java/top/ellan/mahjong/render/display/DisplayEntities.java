package top.ellan.mahjong.render.display;

import top.ellan.mahjong.model.MahjongTile;
import top.ellan.mahjong.table.core.MahjongVariant;
import net.kyori.adventure.text.Component;
import java.util.Collection;
import java.util.List;
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
    private static final Map<Plugin, NamespacedKey> MANAGED_ENTITY_KEYS = new ConcurrentHashMap<>();

    private DisplayEntities() {
    }

    public static List<Entity> spawnAll(Plugin plugin, List<EntitySpec> specs) {
        if (specs == null || specs.isEmpty()) {
            return List.of();
        }
        List<Entity> spawned = new java.util.ArrayList<>(specs.size());
        for (EntitySpec spec : specs) {
            Entity entity = spec.spawn(plugin);
            if (entity != null) {
                spawned.add(entity);
            }
        }
        return List.copyOf(spawned);
    }

    public static boolean reconcile(Plugin plugin, List<Entity> entities, List<EntitySpec> specs) {
        if (plugin == null || entities == null || specs == null || entities.size() != specs.size()) {
            return false;
        }
        for (int i = 0; i < specs.size(); i++) {
            Entity entity = entities.get(i);
            EntitySpec spec = specs.get(i);
            if (entity == null || spec == null || !spec.canReuse(plugin, entity)) {
                return false;
            }
            if (!spec.managesOwnReuse() && !isManagedEntity(plugin, entity)) {
                return false;
            }
        }
        for (int i = 0; i < specs.size(); i++) {
            specs.get(i).apply(plugin, entities.get(i));
        }
        return true;
    }

    public static TileDisplaySpec tileDisplaySpec(
        Location location,
        float yaw,
        MahjongVariant variant,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault
    ) {
        return new TileDisplaySpec(location, yaw, variant, tile, pose, clickAction, visibleByDefault, null, null, TILE_SCALE, null, null, true);
    }

    public static TileDisplaySpec tileDisplaySpec(
        Location location,
        float yaw,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault
    ) {
        return new TileDisplaySpec(location, yaw, null, tile, pose, clickAction, visibleByDefault, null, null, TILE_SCALE, null, null, true);
    }

    public static TileDisplaySpec tileDisplaySpec(
        Location location,
        float yaw,
        MahjongVariant variant,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers
    ) {
        return new TileDisplaySpec(location, yaw, variant, tile, pose, clickAction, visibleByDefault, privateViewers, null, TILE_SCALE, null, null, true);
    }

    public static TileDisplaySpec tileDisplaySpec(
        Location location,
        float yaw,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers
    ) {
        return new TileDisplaySpec(location, yaw, null, tile, pose, clickAction, visibleByDefault, privateViewers, null, TILE_SCALE, null, null, true);
    }

    public static TileDisplaySpec tileDisplaySpec(
        Location location,
        float yaw,
        MahjongVariant variant,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers,
        Collection<UUID> hiddenViewers
    ) {
        return new TileDisplaySpec(location, yaw, variant, tile, pose, clickAction, visibleByDefault, privateViewers, hiddenViewers, TILE_SCALE, null, null, true);
    }

    public static TileDisplaySpec tileDisplaySpec(
        Location location,
        float yaw,
        MahjongVariant variant,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers,
        float scale,
        Color glowColor,
        Display.Billboard billboard
    ) {
        return new TileDisplaySpec(location, yaw, variant, tile, pose, clickAction, visibleByDefault, privateViewers, null, scale, glowColor, billboard, true);
    }

    public static TileDisplaySpec tileDisplaySpec(
        Location location,
        float yaw,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers,
        float scale,
        Color glowColor,
        Display.Billboard billboard
    ) {
        return new TileDisplaySpec(location, yaw, null, tile, pose, clickAction, visibleByDefault, privateViewers, null, scale, glowColor, billboard, true);
    }

    public static TileDisplaySpec tileDisplaySpec(
        Location location,
        float yaw,
        MahjongVariant variant,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers,
        float scale,
        Color glowColor,
        Display.Billboard billboard,
        boolean smoothMovement
    ) {
        return new TileDisplaySpec(location, yaw, variant, tile, pose, clickAction, visibleByDefault, privateViewers, null, scale, glowColor, billboard, smoothMovement);
    }

    public static TileDisplaySpec tileDisplaySpec(
        Location location,
        float yaw,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers,
        float scale,
        Color glowColor,
        Display.Billboard billboard,
        boolean smoothMovement
    ) {
        return new TileDisplaySpec(location, yaw, null, tile, pose, clickAction, visibleByDefault, privateViewers, null, scale, glowColor, billboard, smoothMovement);
    }

    public static LabelSpec labelSpec(Plugin plugin, Location location, Component text, Color color) {
        return new LabelSpec(location, text, color, null, Display.Billboard.CENTER, 0.0F, 0.0F, true);
    }

    public static LabelSpec labelSpec(Location location, Component text, Color color) {
        return new LabelSpec(location, text, color, null, Display.Billboard.CENTER, 0.0F, 0.0F, true);
    }

    public static LabelSpec labelSpec(
        Location location,
        Component text,
        Color color,
        Collection<UUID> privateViewers,
        Display.Billboard billboard,
        float yaw,
        float pitch,
        boolean shadowed
    ) {
        return new LabelSpec(location, text, color, privateViewers, billboard, yaw, pitch, shadowed);
    }

    public static InteractionSpec interactionSpec(
        Location location,
        float width,
        float height,
        DisplayClickAction clickAction,
        Collection<UUID> privateViewers
    ) {
        return new InteractionSpec(location, width, height, clickAction, privateViewers);
    }

    public interface EntitySpec {
        Entity spawn(Plugin plugin);

        boolean canReuse(Plugin plugin, Entity entity);

        void apply(Plugin plugin, Entity entity);

        default boolean managesOwnReuse() {
            return false;
        }
    }

    public record TileDisplaySpec(
        Location location,
        float yaw,
        MahjongVariant variant,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers,
        Collection<UUID> hiddenViewers,
        float scale,
        Color glowColor,
        Display.Billboard billboard,
        boolean smoothMovement
    ) implements EntitySpec {
        public TileDisplaySpec {
            privateViewers = privateViewers == null ? null : List.copyOf(privateViewers);
            hiddenViewers = hiddenViewers == null ? null : List.copyOf(hiddenViewers);
        }

        @Override
        public Entity spawn(Plugin plugin) {
            return spawnTileDisplay(
                plugin,
                this.location,
                this.yaw,
                this.tile,
                this.pose,
                this.clickAction,
                this.visibleByDefault,
                this.privateViewers,
                this.hiddenViewers,
                this.scale,
                this.glowColor,
                this.billboard,
                this.smoothMovement
            );
        }

        @Override
        public boolean canReuse(Plugin plugin, Entity entity) {
            return entity instanceof ItemDisplay;
        }

        @Override
        public void apply(Plugin plugin, Entity entity) {
            applyTileDisplay(plugin, (ItemDisplay) entity, this);
        }
    }

    public record LabelSpec(
        Location location,
        Component text,
        Color color,
        Collection<UUID> privateViewers,
        Display.Billboard billboard,
        float yaw,
        float pitch,
        boolean shadowed
    ) implements EntitySpec {
        public LabelSpec {
            privateViewers = privateViewers == null ? null : List.copyOf(privateViewers);
        }

        @Override
        public Entity spawn(Plugin plugin) {
            return spawnLabel(plugin, this.location, this.text, this.color, this.privateViewers, this.billboard, this.yaw, this.pitch, this.shadowed);
        }

        @Override
        public boolean canReuse(Plugin plugin, Entity entity) {
            return entity instanceof TextDisplay;
        }

        @Override
        public void apply(Plugin plugin, Entity entity) {
            applyLabel(plugin, (TextDisplay) entity, this);
        }
    }

    public record InteractionSpec(
        Location location,
        float width,
        float height,
        DisplayClickAction clickAction,
        Collection<UUID> privateViewers
    ) implements EntitySpec {
        public InteractionSpec {
            privateViewers = privateViewers == null ? null : List.copyOf(privateViewers);
        }

        @Override
        public Entity spawn(Plugin plugin) {
            return spawnInteraction(plugin, this.location, this.width, this.height, this.clickAction, this.privateViewers);
        }

        @Override
        public boolean canReuse(Plugin plugin, Entity entity) {
            return entity instanceof Interaction;
        }

        @Override
        public void apply(Plugin plugin, Entity entity) {
            applyInteraction(plugin, (Interaction) entity, this);
        }
    }

    public static ItemDisplay spawnTileDisplay(
        Plugin plugin,
        Location location,
        float yaw,
        MahjongVariant variant,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault
    ) {
        return spawnTileDisplay(plugin, location, yaw, variant, tile, pose, clickAction, visibleByDefault, null);
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
        return spawnTileDisplay(plugin, location, yaw, null, tile, pose, clickAction, visibleByDefault, null);
    }

    public static ItemDisplay spawnTileDisplay(
        Plugin plugin,
        Location location,
        float yaw,
        MahjongVariant variant,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers
    ) {
        return spawnTileDisplay(plugin, location, yaw, variant, tile, pose, clickAction, visibleByDefault, privateViewers, null, TILE_SCALE, null, null);
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
        return spawnTileDisplay(plugin, location, yaw, null, tile, pose, clickAction, visibleByDefault, privateViewers, null, TILE_SCALE, null, null);
    }

    public static ItemDisplay spawnTileDisplay(
        Plugin plugin,
        Location location,
        float yaw,
        MahjongVariant variant,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers,
        Collection<UUID> hiddenViewers
    ) {
        return spawnTileDisplay(plugin, location, yaw, variant, tile, pose, clickAction, visibleByDefault, privateViewers, hiddenViewers, TILE_SCALE, null, null);
    }

    public static ItemDisplay spawnTileDisplay(
        Plugin plugin,
        Location location,
        float yaw,
        MahjongVariant variant,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers,
        float scale,
        Color glowColor
    ) {
        return spawnTileDisplay(plugin, location, yaw, variant, tile, pose, clickAction, visibleByDefault, privateViewers, null, scale, glowColor);
    }

    public static ItemDisplay spawnTileDisplay(
        Plugin plugin,
        Location location,
        float yaw,
        MahjongVariant variant,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers,
        Collection<UUID> hiddenViewers,
        float scale,
        Color glowColor
    ) {
        return spawnTileDisplay(plugin, location, yaw, variant, tile, pose, clickAction, visibleByDefault, privateViewers, hiddenViewers, scale, glowColor, null);
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
        return spawnTileDisplay(plugin, location, yaw, null, tile, pose, clickAction, visibleByDefault, privateViewers, null, scale, glowColor);
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
        Collection<UUID> hiddenViewers,
        float scale,
        Color glowColor
    ) {
        return spawnTileDisplay(plugin, location, yaw, null, tile, pose, clickAction, visibleByDefault, privateViewers, hiddenViewers, scale, glowColor, null);
    }

    public static ItemDisplay spawnTileDisplay(
        Plugin plugin,
        Location location,
        float yaw,
        MahjongVariant variant,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers,
        float scale,
        Color glowColor,
        Display.Billboard billboard
    ) {
        return spawnTileDisplay(plugin, location, yaw, variant, tile, pose, clickAction, visibleByDefault, privateViewers, null, scale, glowColor, billboard);
    }

    public static ItemDisplay spawnTileDisplay(
        Plugin plugin,
        Location location,
        float yaw,
        MahjongVariant variant,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers,
        Collection<UUID> hiddenViewers,
        float scale,
        Color glowColor,
        Display.Billboard billboard
    ) {
        return spawnTileDisplay(plugin, location, yaw, variant, tile, pose, clickAction, visibleByDefault, privateViewers, hiddenViewers, scale, glowColor, billboard, true);
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
        Color glowColor,
        Display.Billboard billboard
    ) {
        return spawnTileDisplay(plugin, location, yaw, null, tile, pose, clickAction, visibleByDefault, privateViewers, null, scale, glowColor, billboard);
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
        Collection<UUID> hiddenViewers,
        float scale,
        Color glowColor,
        Display.Billboard billboard
    ) {
        return spawnTileDisplay(plugin, location, yaw, null, tile, pose, clickAction, visibleByDefault, privateViewers, hiddenViewers, scale, glowColor, billboard, true);
    }

    public static ItemDisplay spawnTileDisplay(
        Plugin plugin,
        Location location,
        float yaw,
        MahjongVariant variant,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers,
        float scale,
        Color glowColor,
        Display.Billboard billboard,
        boolean smoothMovement
    ) {
        return spawnTileDisplay(plugin, location, yaw, variant, tile, pose, clickAction, visibleByDefault, privateViewers, null, scale, glowColor, billboard, smoothMovement);
    }

    public static ItemDisplay spawnTileDisplay(
        Plugin plugin,
        Location location,
        float yaw,
        MahjongVariant variant,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers,
        Collection<UUID> hiddenViewers,
        float scale,
        Color glowColor,
        Display.Billboard billboard,
        boolean smoothMovement
    ) {
        return spawnTileDisplayInternal(plugin, location, yaw, variant, tile, pose, clickAction, visibleByDefault, privateViewers, hiddenViewers, scale, glowColor, billboard, smoothMovement);
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
        Color glowColor,
        Display.Billboard billboard,
        boolean smoothMovement
    ) {
        return spawnTileDisplay(plugin, location, yaw, null, tile, pose, clickAction, visibleByDefault, privateViewers, null, scale, glowColor, billboard, smoothMovement);
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
        Collection<UUID> hiddenViewers,
        float scale,
        Color glowColor,
        Display.Billboard billboard,
        boolean smoothMovement
    ) {
        return spawnTileDisplayInternal(plugin, location, yaw, null, tile, pose, clickAction, visibleByDefault, privateViewers, hiddenViewers, scale, glowColor, billboard, smoothMovement);
    }

    private static ItemDisplay spawnTileDisplayInternal(
        Plugin plugin,
        Location location,
        float yaw,
        MahjongVariant variant,
        MahjongTile tile,
        TileRenderPose pose,
        DisplayClickAction clickAction,
        boolean visibleByDefault,
        Collection<UUID> privateViewers,
        Collection<UUID> hiddenViewers,
        float scale,
        Color glowColor,
        Display.Billboard billboard,
        boolean smoothMovement
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
            spawned.setInterpolationDuration(smoothMovement ? 1 : 0);
            spawned.setInterpolationDelay(0);
            spawned.setTeleportDuration(smoothMovement ? 1 : 0);
            spawned.setViewRange(32.0F);
            spawned.setShadowRadius(0.0F);
            spawned.setShadowStrength(0.0F);
            spawned.setDisplayWidth(0.4F * scale);
            spawned.setDisplayHeight(0.6F * scale);
            if (billboard != null) {
                spawned.setBillboard(billboard);
            }
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
            spawned.setItemStack(tileItem(plugin, variant, tile, pose.faceDown()));
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
        } else if (hiddenViewers != null && !hiddenViewers.isEmpty()) {
            DisplayVisibilityRegistry.registerExcluded(display.getEntityId(), hiddenViewers);
            syncExcludedVisibility(plugin, display, hiddenViewers, visibleByDefault);
        }
        registerForCraftEngineCulling(plugin, display);
        return display;
    }

    public static TextDisplay spawnLabel(Plugin plugin, Location location, Component text, Color color) {
        return spawnLabel(plugin, location, text, color, null);
    }

    public static TextDisplay spawnLabel(Plugin plugin, Location location, Component text, Color color, Collection<UUID> privateViewers) {
        return spawnLabel(plugin, location, text, color, privateViewers, Display.Billboard.CENTER, 0.0F, 0.0F);
    }

    public static TextDisplay spawnLabel(
        Plugin plugin,
        Location location,
        Component text,
        Color color,
        Collection<UUID> privateViewers,
        Display.Billboard billboard,
        float yaw,
        float pitch
    ) {
        return spawnLabel(plugin, location, text, color, privateViewers, billboard, yaw, pitch, true);
    }

    public static TextDisplay spawnLabel(
        Plugin plugin,
        Location location,
        Component text,
        Color color,
        Collection<UUID> privateViewers,
        Display.Billboard billboard,
        float yaw,
        float pitch,
        boolean shadowed
    ) {
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
            spawned.setShadowed(shadowed);
            spawned.setDefaultBackground(false);
            spawned.setBillboard(billboard);
            spawned.setRotation(yaw, pitch);
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
        return spawnBlockDisplay(plugin, location, material, scaleX, scaleY, scaleZ, true, null, null);
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
        return spawnBlockDisplay(plugin, location, material, scaleX, scaleY, scaleZ, visibleByDefault, privateViewers, null);
    }

    public static BlockDisplay spawnBlockDisplay(
        Plugin plugin,
        Location location,
        Material material,
        float scaleX,
        float scaleY,
        float scaleZ,
        boolean visibleByDefault,
        Collection<UUID> privateViewers,
        DisplayClickAction clickAction
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
            // Keep display hit volume aligned with visual scale so custom ray hit-testing is stable.
            spawned.setDisplayWidth(scaleX);
            spawned.setDisplayHeight(scaleY);
            spawned.setTransformation(new Transformation(
                new Vector3f(),
                new AxisAngle4f(),
                new Vector3f(scaleX, scaleY, scaleZ),
                new AxisAngle4f()
            ));
        });
        if (clickAction != null) {
            TableDisplayRegistry.register(display.getEntityId(), clickAction);
        }
        if (privateViewers != null && !privateViewers.isEmpty()) {
            DisplayVisibilityRegistry.registerPrivate(display.getEntityId(), privateViewers);
            syncPrivateVisibility(plugin, display, privateViewers);
        }
        registerForCraftEngineCulling(plugin, display);
        return display;
    }

    private static void applyTileDisplay(Plugin plugin, ItemDisplay display, TileDisplaySpec spec) {
        applyEntityLocation(plugin, display, spec.location(), spec.yaw(), 0.0F);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
        display.setInterpolationDuration(spec.smoothMovement() ? 1 : 0);
        display.setInterpolationDelay(0);
        display.setTeleportDuration(spec.smoothMovement() ? 1 : 0);
        display.setViewRange(32.0F);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        display.setDisplayWidth(0.4F * spec.scale());
        display.setDisplayHeight(0.6F * spec.scale());
        display.setBillboard(spec.billboard() == null ? Display.Billboard.FIXED : spec.billboard());
        if (spec.glowColor() != null) {
            display.setGlowing(true);
            display.setGlowColorOverride(spec.glowColor());
            display.setBrightness(new Display.Brightness(15, 15));
        } else {
            display.setGlowing(false);
            display.setGlowColorOverride(null);
            display.setBrightness(null);
        }
        display.setTransformation(new Transformation(
            new Vector3f(),
            new AxisAngle4f((float) Math.toRadians(spec.pose().xRotationDegrees()), 1.0F, 0.0F, 0.0F),
            new Vector3f(spec.scale(), spec.scale(), spec.scale()),
            new AxisAngle4f()
        ));
        display.setItemStack(tileItem(plugin, spec.variant(), spec.tile(), spec.pose().faceDown()));
        applyClickAction(display.getEntityId(), spec.clickAction());
        applyTileVisibility(plugin, display, spec.privateViewers(), spec.hiddenViewers(), spec.visibleByDefault());
    }

    private static void applyLabel(Plugin plugin, TextDisplay display, LabelSpec spec) {
        applyEntityLocation(plugin, display, spec.location(), spec.yaw(), spec.pitch());
        display.text(spec.text());
        display.setSeeThrough(false);
        display.setShadowed(spec.shadowed());
        display.setDefaultBackground(false);
        display.setBillboard(spec.billboard());
        display.setLineWidth(160);
        display.setViewRange(LABEL_VIEW_RANGE);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setBackgroundColor(spec.color());
        applyPrivateVisibility(plugin, display, spec.privateViewers(), true);
    }

    private static void applyInteraction(Plugin plugin, Interaction interaction, InteractionSpec spec) {
        applyEntityLocation(plugin, interaction, spec.location(), interaction.getYaw(), interaction.getPitch());
        interaction.setResponsive(true);
        interaction.setInteractionWidth(spec.width());
        interaction.setInteractionHeight(spec.height());
        applyClickAction(interaction.getEntityId(), spec.clickAction());
        applyPrivateVisibility(plugin, interaction, spec.privateViewers(), true);
    }

    private static void applyEntityLocation(Plugin plugin, Entity entity, Location location, float yaw, float pitch) {
        Location target = location.clone();
        target.setYaw(yaw);
        target.setPitch(pitch);
        if (plugin instanceof top.ellan.mahjong.bootstrap.MahjongPaperPlugin mahjongPlugin) {
            mahjongPlugin.scheduler().teleport(entity, target);
            return;
        }
        entity.teleportAsync(target);
    }

    private static void applyClickAction(int entityId, DisplayClickAction clickAction) {
        if (clickAction == null) {
            TableDisplayRegistry.unregister(entityId);
            return;
        }
        TableDisplayRegistry.register(entityId, clickAction);
    }

    private static void applyTileVisibility(
        Plugin plugin,
        Entity entity,
        Collection<UUID> privateViewers,
        Collection<UUID> hiddenViewers,
        boolean visibleByDefault
    ) {
        if (privateViewers != null && hiddenViewers != null && !hiddenViewers.isEmpty()) {
            throw new IllegalArgumentException("Tile visibility cannot define both private viewers and hidden viewers");
        }
        boolean privateOnly = privateViewers != null && !privateViewers.isEmpty();
        boolean hiddenSpecific = hiddenViewers != null && !hiddenViewers.isEmpty();
        entity.setVisibleByDefault(!privateOnly && visibleByDefault);
        if (privateViewers != null) {
            if (!requiresVisibilityResync(plugin) && DisplayVisibilityRegistry.matchesPrivate(entity.getEntityId(), privateViewers)) {
                return;
            }
            if (privateViewers.isEmpty()) {
                DisplayVisibilityRegistry.registerHidden(entity.getEntityId());
                syncPrivateVisibility(plugin, entity, privateViewers);
                return;
            }
            DisplayVisibilityRegistry.registerPrivate(entity.getEntityId(), privateViewers);
            syncPrivateVisibility(plugin, entity, privateViewers);
            return;
        }
        if (hiddenSpecific) {
            if (!requiresVisibilityResync(plugin) && DisplayVisibilityRegistry.matchesExcluded(entity.getEntityId(), hiddenViewers)) {
                return;
            }
            DisplayVisibilityRegistry.registerExcluded(entity.getEntityId(), hiddenViewers);
            syncExcludedVisibility(plugin, entity, hiddenViewers, visibleByDefault);
            return;
        }
        DisplayVisibilityRegistry.unregister(entity.getEntityId());
        syncPublicVisibility(plugin, entity);
    }

    private static void applyPrivateVisibility(Plugin plugin, Entity entity, Collection<UUID> privateViewers, boolean visibleByDefault) {
        boolean privateOnly = privateViewers != null && !privateViewers.isEmpty();
        entity.setVisibleByDefault(!privateOnly && visibleByDefault);
        if (!requiresVisibilityResync(plugin) && DisplayVisibilityRegistry.matchesPrivate(entity.getEntityId(), privateViewers)) {
            return;
        }
        if (privateViewers == null) {
            DisplayVisibilityRegistry.unregister(entity.getEntityId());
            syncPublicVisibility(plugin, entity);
            return;
        }
        if (privateViewers.isEmpty()) {
            DisplayVisibilityRegistry.registerHidden(entity.getEntityId());
            syncPrivateVisibility(plugin, entity, privateViewers);
            return;
        }
        DisplayVisibilityRegistry.registerPrivate(entity.getEntityId(), privateViewers);
        syncPrivateVisibility(plugin, entity, privateViewers);
    }

    private static void syncExcludedVisibility(Plugin plugin, Entity entity, Collection<UUID> hiddenViewers, boolean visibleByDefault) {
        for (org.bukkit.entity.Player player : onlinePlayersSnapshot()) {
            runForViewer(plugin, entity, player, () -> {
                if (hiddenViewers.contains(player.getUniqueId())) {
                    player.hideEntity(plugin, entity);
                } else if (visibleByDefault) {
                    player.showEntity(plugin, entity);
                } else {
                    player.hideEntity(plugin, entity);
                }
            });
        }
    }

    private static void syncPrivateVisibility(Plugin plugin, Entity entity, Collection<UUID> privateViewers) {
        for (org.bukkit.entity.Player player : onlinePlayersSnapshot()) {
            runForViewer(plugin, entity, player, () -> {
                if (!privateViewers.isEmpty() && privateViewers.contains(player.getUniqueId())) {
                    player.showEntity(plugin, entity);
                } else {
                    player.hideEntity(plugin, entity);
                }
            });
        }
    }

    private static void syncPublicVisibility(Plugin plugin, Entity entity) {
        for (org.bukkit.entity.Player player : onlinePlayersSnapshot()) {
            runForViewer(plugin, entity, player, () -> player.showEntity(plugin, entity));
        }
    }

    private static List<org.bukkit.entity.Player> onlinePlayersSnapshot() {
        return List.copyOf(Bukkit.getOnlinePlayers());
    }

    private static void runForViewer(Plugin plugin, Entity entity, org.bukkit.entity.Player player, Runnable runnable) {
        if (plugin instanceof top.ellan.mahjong.bootstrap.MahjongPaperPlugin mahjongPlugin) {
            mahjongPlugin.scheduler().runEntity(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                runnable.run();
            });
            return;
        }
        runnable.run();
    }

    private static void registerForCraftEngineCulling(Plugin plugin, org.bukkit.entity.Entity entity) {
        if (plugin instanceof top.ellan.mahjong.bootstrap.MahjongPaperPlugin mahjongPlugin && mahjongPlugin.craftEngine() != null) {
            mahjongPlugin.craftEngine().registerCullableEntity(entity);
        }
    }

    private static boolean requiresVisibilityResync(Plugin plugin) {
        return plugin instanceof top.ellan.mahjong.bootstrap.MahjongPaperPlugin mahjongPlugin
            && mahjongPlugin.craftEngine() != null;
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
        return MANAGED_ENTITY_KEYS.computeIfAbsent(plugin, key -> new NamespacedKey(key, MANAGED_ENTITY_KEY));
    }

    private static ItemStack tileItem(Plugin plugin, MahjongVariant variant, MahjongTile tile, boolean faceDown) {
        if (plugin instanceof top.ellan.mahjong.bootstrap.MahjongPaperPlugin mahjongPlugin) {
            ItemStack customItem = mahjongPlugin.craftEngine().resolveTileItem(variant == null ? MahjongVariant.RIICHI : variant, tile, faceDown);
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

