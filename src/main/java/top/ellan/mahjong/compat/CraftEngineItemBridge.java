package top.ellan.mahjong.compat;

import top.ellan.mahjong.model.MahjongTile;
import top.ellan.mahjong.table.core.MahjongVariant;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

final class CraftEngineItemBridge {
    private final CraftEngineBridgeContext context;
    private final boolean preferCustomItems;
    private final Map<String, ItemStack> customItemCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> customItemBuildMethods = new ConcurrentHashMap<>();
    private volatile boolean reflectionUnavailable;
    private volatile ItemReflection reflection;

    CraftEngineItemBridge(CraftEngineBridgeContext context, boolean preferCustomItems) {
        this.context = context;
        this.preferCustomItems = preferCustomItems;
    }

    ItemStack resolveTileItem(MahjongVariant variant, MahjongTile tile, boolean faceDown) {
        if (!this.preferCustomItems || this.reflectionUnavailable) {
            return null;
        }

        Plugin craftEngine = this.context.craftEnginePlugin();
        ItemReflection bridge = this.reflection(craftEngine);
        if (bridge == null) {
            return null;
        }

        String itemId = this.customItemId(variant, tile, faceDown);
        ItemStack resolved = this.buildCustomItem(bridge, itemId);
        if (resolved != null) {
            return resolved;
        }
        if (!faceDown && tile != MahjongTile.UNKNOWN) {
            String fallbackItemId = this.customItemId(variant, MahjongTile.UNKNOWN, false);
            if (!Objects.equals(fallbackItemId, itemId)) {
                ItemStack fallback = this.buildCustomItem(bridge, fallbackItemId);
                if (fallback != null) {
                    return fallback;
                }
            }
        }
        return null;
    }

    String customItemId(MahjongVariant variant, MahjongTile tile, boolean faceDown) {
        return CraftEngineTileItemResolver.resolve(this.context.plugin().settings().craftEngineTileItemIdPrefix(variant), tile, faceDown);
    }

    Method resolveBuildItemStackMethod(Class<?> customItemClass) {
        return this.customItemBuildMethods.computeIfAbsent(customItemClass, CraftEngineItemBridge::lookupBuildItemStackMethod);
    }

    private ItemStack buildCustomItem(ItemReflection bridge, String itemId) {
        ItemStack cached = this.customItemCache.get(itemId);
        if (cached != null) {
            return cached.clone();
        }

        try {
            Object key = this.context.craftEngineKey(itemId, bridge.keyOfMethod());
            Object customItem = bridge.byIdMethod().invoke(null, key);
            if (customItem == null) {
                return null;
            }
            Method buildMethod = this.resolveBuildItemStackMethod(customItem.getClass());
            Object built = invokeCustomItemBuildMethod(customItem, buildMethod);
            if (!(built instanceof ItemStack itemStack)) {
                return null;
            }
            this.customItemCache.put(itemId, itemStack.clone());
            return itemStack;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.reflectionUnavailable = true;
            this.context.plugin().getLogger().warning(
                "CraftEngine was detected, but MahjongPaper could not build CraftEngine custom items. Falling back to direct item_model items."
            );
            this.context.plugin().debug().log(
                "lifecycle",
                "CraftEngine reflection bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return null;
        }
    }

    private ItemReflection reflection(Plugin craftEngine) {
        if (craftEngine == null || !craftEngine.isEnabled() || this.reflectionUnavailable) {
            return null;
        }
        ItemReflection cached = this.reflection;
        if (cached != null) {
            return cached;
        }
        try {
            ClassLoader classLoader = craftEngine.getClass().getClassLoader();
            Class<?> keyClass = Class.forName("net.momirealms.craftengine.core.util.Key", true, classLoader);
            Class<?> itemsClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems", true, classLoader);
            ItemReflection resolved = new ItemReflection(
                keyClass.getMethod("of", String.class),
                itemsClass.getMethod("byId", keyClass)
            );
            this.reflection = resolved;
            return resolved;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.reflectionUnavailable = true;
            this.context.plugin().getLogger().warning(
                "CraftEngine was detected, but MahjongPaper could not build CraftEngine custom items. Falling back to direct item_model items."
            );
            this.context.plugin().debug().log(
                "lifecycle",
                "CraftEngine reflection bridge failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return null;
        }
    }

    static Method lookupBuildItemStackMethod(Class<?> customItemClass) {
        try {
            return customItemClass.getMethod("buildBukkitItem");
        } catch (NoSuchMethodException buildBukkitItemMissing) {
            try {
                return customItemClass.getMethod("buildItemStack");
            } catch (NoSuchMethodException buildItemStackMissing) {
                // Fall through to CraftEngine builds that require ItemBuildContext.
            }
            for (Method method : customItemClass.getMethods()) {
                if (!method.getName().equals("buildBukkitItem")) {
                    continue;
                }
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length == 1 && isItemBuildContextType(parameters[0])) {
                    return method;
                }
                if (
                    parameters.length == 2
                        && isItemBuildContextType(parameters[0])
                        && (parameters[1] == int.class || parameters[1] == Integer.class)
                ) {
                    return method;
                }
            }
            throw new IllegalStateException(
                "CraftEngine custom item class does not expose a compatible build method "
                    + "(buildBukkitItem(), buildItemStack(), or buildBukkitItem(ItemBuildContext[, int])): "
                    + customItemClass.getName(),
                buildBukkitItemMissing
            );
        }
    }

    static Object invokeCustomItemBuildMethod(Object customItem, Method buildMethod) throws ReflectiveOperationException {
        Class<?>[] parameters = buildMethod.getParameterTypes();
        if (parameters.length == 0) {
            return buildMethod.invoke(customItem);
        }
        if (parameters.length == 1 && isItemBuildContextType(parameters[0])) {
            return buildMethod.invoke(customItem, emptyItemBuildContext(parameters[0]));
        }
        if (
            parameters.length == 2
                && isItemBuildContextType(parameters[0])
                && (parameters[1] == int.class || parameters[1] == Integer.class)
        ) {
            return buildMethod.invoke(customItem, emptyItemBuildContext(parameters[0]), 1);
        }
        throw new IllegalStateException("Unsupported CraftEngine custom item build method signature: " + buildMethod.toGenericString());
    }

    private static boolean isItemBuildContextType(Class<?> type) {
        return "net.momirealms.craftengine.core.item.ItemBuildContext".equals(type.getName());
    }

    private static Object emptyItemBuildContext(Class<?> itemBuildContextType) throws ReflectiveOperationException {
        Method emptyMethod = itemBuildContextType.getMethod("empty");
        return emptyMethod.invoke(null);
    }

    private record ItemReflection(Method keyOfMethod, Method byIdMethod) {
    }
}
