package top.ellan.mahjong.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.meta.ItemMeta;

public final class PaperCompatibility {
    private static final Method BUKKIT_IS_OWNED_BY_CURRENT_REGION = findBukkitIsOwnedByCurrentRegion();
    private static final Method ITEM_META_SET_ITEM_MODEL = findItemMetaSetItemModel();
    private static final Particle DUST_PARTICLE = findParticle("DUST", "REDSTONE");
    private static final int CUSTOM_MODEL_DATA_BASE = 7_100_000;
    private static final int CUSTOM_MODEL_DATA_RANGE = 900_000;

    private PaperCompatibility() {
    }

    public static boolean isOwnedByCurrentRegion(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (BUKKIT_IS_OWNED_BY_CURRENT_REGION == null) {
            return true;
        }
        try {
            Object result = BUKKIT_IS_OWNED_BY_CURRENT_REGION.invoke(null, entity);
            return result instanceof Boolean owned && owned;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            return true;
        }
    }

    public static void applyItemModel(ItemMeta meta, NamespacedKey modelKey) {
        if (meta == null || modelKey == null) {
            return;
        }
        if (ITEM_META_SET_ITEM_MODEL != null) {
            try {
                ITEM_META_SET_ITEM_MODEL.invoke(meta, modelKey);
                return;
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
                // Fall through to the 1.20.x-compatible CustomModelData marker.
            }
        }
        meta.setCustomModelData(customModelData(modelKey));
    }

    public static void setTeleportDuration(Entity entity, int ticks) {
        if (entity == null) {
            return;
        }
        try {
            Method method = entity.getClass().getMethod("setTeleportDuration", int.class);
            method.invoke(entity, ticks);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            // Paper 1.20.1 display entities do not expose teleport interpolation duration.
        }
    }

    public static Particle dustParticle() {
        return DUST_PARTICLE;
    }

    public static int customModelData(NamespacedKey modelKey) {
        CRC32 crc = new CRC32();
        byte[] bytes = (modelKey.getNamespace() + ":" + modelKey.getKey()).getBytes(StandardCharsets.UTF_8);
        crc.update(bytes, 0, bytes.length);
        return CUSTOM_MODEL_DATA_BASE + (int) (crc.getValue() % CUSTOM_MODEL_DATA_RANGE);
    }

    private static Method findBukkitIsOwnedByCurrentRegion() {
        try {
            return Bukkit.class.getMethod("isOwnedByCurrentRegion", Entity.class);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static Method findItemMetaSetItemModel() {
        try {
            return ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static Particle findParticle(String... names) {
        for (String name : names) {
            try {
                return Particle.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Particle.CRIT;
    }
}
