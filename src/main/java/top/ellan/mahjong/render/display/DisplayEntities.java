package top.ellan.mahjong.render.display;

import top.ellan.mahjong.compat.PaperCompatibility;
import top.ellan.mahjong.model.MahjongTile;
import top.ellan.mahjong.model.MahjongVariant;
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

    private record BukkitDisplayEntityRuntime(Plugin bukkitPlugin) implements DisplayEntityRuntime {
    }

    public static List<Entity> spawnAll(Plugin plugin, List<EntitySpec> specs) {
        return spawnAll(new BukkitDisplayEntityRuntime(plugin), specs);
    }

    public static List<Entity> spawnAll(DisplayEntityRuntime runtime, List<EntitySpec> specs) {
        if (runtime == null || runtime.bukkitPlugin() == null || specs == null || specs.isEmpty()) {
            return List.of();
        }
        List<Entity> spawned = new java.util.ArrayList<>(specs.size());
        for (EntitySpec spec : specs) {
            Entity entity = spec.spawn(runtime);
            if (entity != null) {
                spawned.add(entity);
            }
        }
        return List.copyOf(spawned);
    }

    public static boolean reconcile(Plugin plugin, List<Entity> entities, List<EntitySpec> specs) {
        return reconcile(new BukkitDisplayEntityRuntime(plugin), entities, specs);
    }

    public static boolean reconcile(DisplayEntityRuntime runtime, List<Entity> entities, List<EntitySpec> specs) {
        if (runtime == null || runtime.bukkitPlugin() == null || entities == null || specs == null || entities.size() != specs.size()) {
            return false;
        }
        for (int i = 0; i < specs.size(); i++) {
            Entity entity = entities.get(i);
            EntitySpec spec = specs.get(i);
            if (entity == null || spec == null || !spec.canReuse(runtime, entity)) {
                return false;
            }
            if (!spec.managesOwnReuse() && !isManagedEntity(runtime.bukkitPlugin(), entity)) {
                return false;
            }
        }
        for (int i = 0; i < specs.size(); i++) {
            specs.get(i).apply(runtime, entities.get(i));
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
        Entity spawn(DisplayEntityRuntime runtime);

        boolean canReuse(DisplayEntityRuntime runtime, Entity entity);

        void apply(DisplayEntityRuntime runtime, Entity entity);

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
        public Entity spawn(DisplayEntityRuntime runtime) {
            return spawnTileDisplayInternal(
                runtime,
                this.location,
                this.yaw,
                this.variant,
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
        public boolean canReuse(DisplayEntityRuntime runtime, Entity entity) {
            return entity instanceof ItemDisplay;
        }

        @Override
        public void apply(DisplayEntityRuntime runtime, Entity entity) {
            applyTileDisplay(runtime, (ItemDisplay) entity, this);
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
        public Entity spawn(DisplayEntityRuntime runtime) {
            return spawnLabel(runtime, this.location, this.text, this.color, this.privateViewers, this.billboard, this.yaw, this.pitch, this.shadowed);
        }

        @Override
        public boolean canReuse(DisplayEntityRuntime runtime, Entity entity) {
            return entity instanceof TextDisplay;
        }

        @Override
        public void apply(DisplayEntityRuntime runtime, Entity entity) {
            applyLabel(runtime, (TextDisplay) entity, this);
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
        public Entity spawn(DisplayEntityRuntime runtime) {
            return spawnInteraction(runtime, this.location, this.width, this.height, this.clickAction, this.privateViewers);
        }

        @Override
        public boolean canReuse(DisplayEntityRuntime runtime, Entity entity) {
            return entity instanceof Interaction;
        }

        @Override
        public void apply(DisplayEntityRuntime runtime, Entity entity) {
            applyInteraction(runtime, (Interaction) entity, this);
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
        return spawnTileDisplayInternal(new BukkitDisplayEntityRuntime(plugin), location, yaw, variant, tile, pose, clickAction, visibleByDefault, privateViewers, hiddenViewers, scale, glowColor, billboard, smoothMovement);
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
        return spawnTileDisplayInternal(new BukkitDisplayEntityRuntime(plugin), location, yaw, null, tile, pose, clickAction, visibleByDefault, privateViewers, hiddenViewers, scale, glowColor, billboard, smoothMovement);
    }

    private static ItemDisplay spawnTileDisplayInternal(
        DisplayEntityRuntime runtime,
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

        // World.spawn(Location, Class, Consumer) was source-compatible across 1.20.x but the
        // Consumer parameter type changed from org.bukkit.util.Consumer (forRemoval) to
        // java.util.function.Consumer in 1.21+. To keep one jar working on both we use the
        // pre-1.20 World.spawn(Location, Class) entry point, which has been stable since
        // Bukkit's earliest releases, and configure the entity immediately after spawning.
        ItemDisplay display = world.spawn(location, ItemDisplay.class);
        boolean restrictedVisibility = privateViewers != null;
        // Set visibility before any other property so the entity tracker that
        // wakes up on the next broadcast tick already sees the final state and
        // never publishes a one-frame window of default visibility to clients.
        display.setVisibleByDefault(!restrictedVisibility && visibleByDefault);
        display.setPersistent(false);
        markManagedEntity(runtime.bukkitPlugin(), display);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
        display.setInterpolationDuration(smoothMovement ? 1 : 0);
        display.setInterpolationDelay(0);
        PaperCompatibility.setTeleportDuration(display, smoothMovement ? 1 : 0);
        display.setViewRange(32.0F);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        display.setDisplayWidth(0.4F * scale);
        display.setDisplayHeight(0.6F * scale);
        if (billboard != null) {
            display.setBillboard(billboard);
        }
        display.setRotation(yaw, 0.0F);
        if (glowColor != null) {
            display.setGlowing(true);
            display.setGlowColorOverride(glowColor);
            display.setBrightness(new Display.Brightness(15, 15));
        }
        display.setTransformation(new Transformation(
            new Vector3f(),
            new AxisAngle4f((float) Math.toRadians(pose.xRotationDegrees()), 1.0F, 0.0F, 0.0F),
            new Vector3f(scale, scale, scale),
            new AxisAngle4f()
        ));
        display.setItemStack(tileItem(runtime, variant, tile, pose.faceDown()));

        if (clickAction != null) {
            TableDisplayRegistry.register(display.getEntityId(), clickAction);
        }
        if (privateViewers != null) {
            if (privateViewers.isEmpty()) {
                DisplayVisibilityRegistry.registerHidden(display.getEntityId());
            } else {
                DisplayVisibilityRegistry.registerPrivate(display.getEntityId(), privateViewers);
            }
            syncPrivateVisibility(runtime, display, privateViewers);
        } else if (hiddenViewers != null && !hiddenViewers.isEmpty()) {
            DisplayVisibilityRegistry.registerExcluded(display.getEntityId(), hiddenViewers);
            syncExcludedVisibility(runtime, display, hiddenViewers, visibleByDefault);
        }
        registerForCraftEngineCulling(runtime, display);
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
        return spawnLabel(new BukkitDisplayEntityRuntime(plugin), location, text, color, privateViewers, billboard, yaw, pitch, shadowed);
    }

    private static TextDisplay spawnLabel(
        DisplayEntityRuntime runtime,
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

        TextDisplay display = world.spawn(location, TextDisplay.class);
        boolean privateOnly = privateViewers != null && !privateViewers.isEmpty();
        // See ItemDisplay spawn comment above: ensure the visibility flag is
        // resolved before any tracker tick observes the entity.
        display.setVisibleByDefault(!privateOnly);
        display.setPersistent(false);
        markManagedEntity(runtime.bukkitPlugin(), display);
        display.text(text);
        display.setSeeThrough(false);
        display.setShadowed(shadowed);
        display.setDefaultBackground(false);
        display.setBillboard(billboard);
        display.setRotation(yaw, pitch);
        display.setLineWidth(160);
        display.setViewRange(LABEL_VIEW_RANGE);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setBackgroundColor(color);
        if (privateViewers != null && !privateViewers.isEmpty()) {
            DisplayVisibilityRegistry.registerPrivate(display.getEntityId(), privateViewers);
            syncPrivateVisibility(runtime, display, privateViewers);
        }
        registerForCraftEngineCulling(runtime, display);
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
        return spawnInteraction(new BukkitDisplayEntityRuntime(plugin), location, width, height, clickAction, privateViewers);
    }

    private static Interaction spawnInteraction(
        DisplayEntityRuntime runtime,
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

        Interaction interaction = world.spawn(location, Interaction.class);
        boolean interactionPrivateOnly = privateViewers != null && !privateViewers.isEmpty();
        // See ItemDisplay spawn comment above.
        interaction.setVisibleByDefault(!interactionPrivateOnly);
        interaction.setPersistent(false);
        markManagedEntity(runtime.bukkitPlugin(), interaction);
        interaction.setResponsive(true);
        interaction.setInteractionWidth(width);
        interaction.setInteractionHeight(height);
        if (clickAction != null) {
            TableDisplayRegistry.register(interaction.getEntityId(), clickAction);
        }
        if (privateViewers != null && !privateViewers.isEmpty()) {
            DisplayVisibilityRegistry.registerPrivate(interaction.getEntityId(), privateViewers);
            syncPrivateVisibility(runtime, interaction, privateViewers);
        }
        registerForCraftEngineCulling(runtime, interaction);
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
        DisplayEntityRuntime runtime = new BukkitDisplayEntityRuntime(plugin);
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location world is null");
        }

        BlockDisplay display = world.spawn(location, BlockDisplay.class);
        boolean blockPrivateOnly = privateViewers != null && !privateViewers.isEmpty();
        // See ItemDisplay spawn comment above.
        display.setVisibleByDefault(!blockPrivateOnly && visibleByDefault);
        display.setPersistent(false);
        markManagedEntity(plugin, display);
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
        PaperCompatibility.setTeleportDuration(display, 1);
        display.setViewRange(48.0F);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        display.setRotation(0.0F, 0.0F);
        display.setBlock(material.createBlockData());
        // Keep display hit volume aligned with visual scale so custom ray hit-testing is stable.
        display.setDisplayWidth(scaleX);
        display.setDisplayHeight(scaleY);
        display.setTransformation(new Transformation(
            new Vector3f(),
            new AxisAngle4f(),
            new Vector3f(scaleX, scaleY, scaleZ),
            new AxisAngle4f()
        ));
        if (clickAction != null) {
            TableDisplayRegistry.register(display.getEntityId(), clickAction);
        }
        if (privateViewers != null && !privateViewers.isEmpty()) {
            DisplayVisibilityRegistry.registerPrivate(display.getEntityId(), privateViewers);
            syncPrivateVisibility(runtime, display, privateViewers);
        }
        registerForCraftEngineCulling(runtime, display);
        return display;
    }

    private static void applyTileDisplay(DisplayEntityRuntime runtime, ItemDisplay display, TileDisplaySpec spec) {
        applyEntityLocation(runtime, display, spec.location(), spec.yaw(), 0.0F);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
        display.setInterpolationDuration(spec.smoothMovement() ? 1 : 0);
        display.setInterpolationDelay(0);
        PaperCompatibility.setTeleportDuration(display, spec.smoothMovement() ? 1 : 0);
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
        display.setItemStack(tileItem(runtime, spec.variant(), spec.tile(), spec.pose().faceDown()));
        applyClickAction(display.getEntityId(), spec.clickAction());
        applyTileVisibility(runtime, display, spec.privateViewers(), spec.hiddenViewers(), spec.visibleByDefault());
    }

    private static void applyLabel(DisplayEntityRuntime runtime, TextDisplay display, LabelSpec spec) {
        applyEntityLocation(runtime, display, spec.location(), spec.yaw(), spec.pitch());
        display.text(spec.text());
        display.setSeeThrough(false);
        display.setShadowed(spec.shadowed());
        display.setDefaultBackground(false);
        display.setBillboard(spec.billboard());
        display.setLineWidth(160);
        display.setViewRange(LABEL_VIEW_RANGE);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setBackgroundColor(spec.color());
        applyPrivateVisibility(runtime, display, spec.privateViewers(), true);
    }

    private static void applyInteraction(DisplayEntityRuntime runtime, Interaction interaction, InteractionSpec spec) {
        applyEntityLocation(runtime, interaction, spec.location(), interaction.getYaw(), interaction.getPitch());
        interaction.setResponsive(true);
        interaction.setInteractionWidth(spec.width());
        interaction.setInteractionHeight(spec.height());
        applyClickAction(interaction.getEntityId(), spec.clickAction());
        applyPrivateVisibility(runtime, interaction, spec.privateViewers(), true);
    }

    private static void applyEntityLocation(DisplayEntityRuntime runtime, Entity entity, Location location, float yaw, float pitch) {
        Location target = location.clone();
        target.setYaw(yaw);
        target.setPitch(pitch);
        runtime.teleport(entity, target);
    }

    private static void applyClickAction(int entityId, DisplayClickAction clickAction) {
        if (clickAction == null) {
            TableDisplayRegistry.unregister(entityId);
            return;
        }
        TableDisplayRegistry.register(entityId, clickAction);
    }

    private static void applyTileVisibility(
        DisplayEntityRuntime runtime,
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
            if (!requiresVisibilityResync(runtime) && DisplayVisibilityRegistry.matchesPrivate(entity.getEntityId(), privateViewers)) {
                return;
            }
            if (privateViewers.isEmpty()) {
                DisplayVisibilityRegistry.registerHidden(entity.getEntityId());
                syncPrivateVisibility(runtime, entity, privateViewers);
                return;
            }
            DisplayVisibilityRegistry.registerPrivate(entity.getEntityId(), privateViewers);
            syncPrivateVisibility(runtime, entity, privateViewers);
            return;
        }
        if (hiddenSpecific) {
            if (!requiresVisibilityResync(runtime) && DisplayVisibilityRegistry.matchesExcluded(entity.getEntityId(), hiddenViewers)) {
                return;
            }
            DisplayVisibilityRegistry.registerExcluded(entity.getEntityId(), hiddenViewers);
            syncExcludedVisibility(runtime, entity, hiddenViewers, visibleByDefault);
            return;
        }
        DisplayVisibilityRegistry.unregister(entity.getEntityId());
        syncPublicVisibility(runtime, entity);
    }

    private static void applyPrivateVisibility(DisplayEntityRuntime runtime, Entity entity, Collection<UUID> privateViewers, boolean visibleByDefault) {
        boolean privateOnly = privateViewers != null && !privateViewers.isEmpty();
        entity.setVisibleByDefault(!privateOnly && visibleByDefault);
        if (!requiresVisibilityResync(runtime) && DisplayVisibilityRegistry.matchesPrivate(entity.getEntityId(), privateViewers)) {
            return;
        }
        if (privateViewers == null) {
            DisplayVisibilityRegistry.unregister(entity.getEntityId());
            syncPublicVisibility(runtime, entity);
            return;
        }
        if (privateViewers.isEmpty()) {
            DisplayVisibilityRegistry.registerHidden(entity.getEntityId());
            syncPrivateVisibility(runtime, entity, privateViewers);
            return;
        }
        DisplayVisibilityRegistry.registerPrivate(entity.getEntityId(), privateViewers);
        syncPrivateVisibility(runtime, entity, privateViewers);
    }

    private static void syncExcludedVisibility(DisplayEntityRuntime runtime, Entity entity, Collection<UUID> hiddenViewers, boolean visibleByDefault) {
        for (org.bukkit.entity.Player player : onlinePlayersSnapshot()) {
            runForViewer(runtime, entity, player, () -> {
                if (hiddenViewers.contains(player.getUniqueId())) {
                    player.hideEntity(runtime.bukkitPlugin(), entity);
                } else if (visibleByDefault) {
                    player.showEntity(runtime.bukkitPlugin(), entity);
                } else {
                    player.hideEntity(runtime.bukkitPlugin(), entity);
                }
            });
        }
    }

    private static void syncPrivateVisibility(DisplayEntityRuntime runtime, Entity entity, Collection<UUID> privateViewers) {
        for (org.bukkit.entity.Player player : onlinePlayersSnapshot()) {
            runForViewer(runtime, entity, player, () -> {
                if (!privateViewers.isEmpty() && privateViewers.contains(player.getUniqueId())) {
                    player.showEntity(runtime.bukkitPlugin(), entity);
                } else {
                    player.hideEntity(runtime.bukkitPlugin(), entity);
                }
            });
        }
    }

    private static void syncPublicVisibility(DisplayEntityRuntime runtime, Entity entity) {
        for (org.bukkit.entity.Player player : onlinePlayersSnapshot()) {
            runForViewer(runtime, entity, player, () -> player.showEntity(runtime.bukkitPlugin(), entity));
        }
    }

    private static List<org.bukkit.entity.Player> onlinePlayersSnapshot() {
        return List.copyOf(Bukkit.getOnlinePlayers());
    }

    private static void runForViewer(DisplayEntityRuntime runtime, Entity entity, org.bukkit.entity.Player player, Runnable runnable) {
        runtime.runForViewer(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            // Folia region safety: show/hide touches both viewer and target entity internals.
            if (!PaperCompatibility.isOwnedByCurrentRegion(player) || !PaperCompatibility.isOwnedByCurrentRegion(entity)) {
                return;
            }
            if (!entity.isValid() || entity.isDead()) {
                return;
            }
            runnable.run();
        });
    }

    private static void registerForCraftEngineCulling(DisplayEntityRuntime runtime, org.bukkit.entity.Entity entity) {
        runtime.registerCullableEntity(entity);
    }

    private static boolean requiresVisibilityResync(DisplayEntityRuntime runtime) {
        return runtime.requiresVisibilityResync();
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

    private static ItemStack tileItem(DisplayEntityRuntime runtime, MahjongVariant variant, MahjongTile tile, boolean faceDown) {
        ItemStack customItem = runtime.resolveTileItem(variant, tile, faceDown);
        if (customItem != null) {
            return customItem;
        }
        String path = faceDown ? "mahjong_tile/back" : tile.itemModelPath();
        return TILE_ITEM_CACHE.computeIfAbsent(path, key -> createTileItem(tile, key)).clone();
    }

    private static ItemStack createTileItem(MahjongTile tile, String path) {
        ItemStack itemStack = new ItemStack(Material.PAPER);
        ItemMeta meta = itemStack.getItemMeta();
        PaperCompatibility.applyItemModel(meta, new NamespacedKey(ITEM_MODEL_NAMESPACE, path));
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

