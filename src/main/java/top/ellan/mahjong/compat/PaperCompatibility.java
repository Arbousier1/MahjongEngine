package top.ellan.mahjong.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;

public final class PaperCompatibility {
    private static final Method BUKKIT_IS_OWNED_BY_CURRENT_REGION = findBukkitIsOwnedByCurrentRegion();
    private static final Method ITEM_META_SET_ITEM_MODEL = findItemMetaSetItemModel();
    private static final Particle DUST_PARTICLE = findParticle("DUST", "REDSTONE");
    private static final int CUSTOM_MODEL_DATA_BASE = 7_100_000;
    private static final int CUSTOM_MODEL_DATA_RANGE = 900_000;

    private static final Method CLICK_GET_VIEW = findMethod(InventoryClickEvent.class, "getView");
    private static final Method DRAG_GET_VIEW = findMethod(InventoryDragEvent.class, "getView");
    private static final Method GET_TOP_INVENTORY = findTopInventoryMethod();

    private PaperCompatibility() {
    }

    /**
     * Returns the top inventory from an InventoryClickEvent in a way that is compatible
     * with both old Bukkit (where InventoryView is an interface) and Paper 1.21.4+
     * (where InventoryView is an abstract class). Direct calls to event.getView()
     * cause IncompatibleClassChangeError at runtime when the compiled bytecode
     * expects an interface but the runtime provides a class.
     */
    public static Inventory getTopInventory(InventoryClickEvent event) {
        return getTopInventoryReflective(CLICK_GET_VIEW, event);
    }

    /**
     * Returns the top inventory from an InventoryDragEvent, compatible across
     * Bukkit/Paper versions where InventoryView may be an interface or a class.
     */
    public static Inventory getTopInventory(InventoryDragEvent event) {
        return getTopInventoryReflective(DRAG_GET_VIEW, event);
    }

    private static Inventory getTopInventoryReflective(Method getViewMethod, Object event) {
        if (getViewMethod == null || GET_TOP_INVENTORY == null) {
            throw new IllegalStateException("InventoryView.getView / getTopInventory not available");
        }
        try {
            Object view = getViewMethod.invoke(event);
            return (Inventory) GET_TOP_INVENTORY.invoke(view);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Failed to call getView/getTopInventory reflectively", exception);
        }
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

    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static Method findTopInventoryMethod() {
        try {
            // InventoryView is an interface in old Bukkit, a class in Paper 1.21.4+;
            // look up the method by name to avoid a direct class reference in compiled bytecode.
            Class<?> inventoryViewClass = Class.forName("org.bukkit.inventory.InventoryView");
            return inventoryViewClass.getMethod("getTopInventory");
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            return null;
        }
    }
}
