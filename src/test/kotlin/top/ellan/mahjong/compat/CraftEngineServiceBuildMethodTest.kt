package top.ellan.mahjong.compat

import net.momirealms.craftengine.core.item.ItemBuildContext
import org.bukkit.configuration.MemoryConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import top.ellan.mahjong.bootstrap.MahjongPaperPlugin
import java.lang.reflect.Method

class CraftEngineServiceBuildMethodTest {
    @Test
    fun `lookup prefers legacy buildItemStack when available`() {
        val service = CraftEngineService(mock(MahjongPaperPlugin::class.java), MemoryConfiguration())

        val method = lookupBuildMethod(service, LegacyCustomItem::class.java)

        assertEquals("buildItemStack", method.name)
        val built = invokeBuildMethod(service, LegacyCustomItem(), method)
        assertEquals("legacy", built)
    }

    @Test
    fun `lookup supports craftengine dev buildBukkitItem with context and count`() {
        val service = CraftEngineService(mock(MahjongPaperPlugin::class.java), MemoryConfiguration())

        val method = lookupBuildMethod(service, DevCustomItemWithCount::class.java)

        assertEquals("buildBukkitItem", method.name)
        val built = invokeBuildMethod(service, DevCustomItemWithCount(), method)
        assertEquals("dev-with-count", built)
    }

    @Test
    fun `lookup supports buildBukkitItem with only context`() {
        val service = CraftEngineService(mock(MahjongPaperPlugin::class.java), MemoryConfiguration())

        val method = lookupBuildMethod(service, DevCustomItemContextOnly::class.java)

        assertEquals("buildBukkitItem", method.name)
        val built = invokeBuildMethod(service, DevCustomItemContextOnly(), method)
        assertEquals("dev-context-only", built)
    }

    private fun lookupBuildMethod(service: CraftEngineService, type: Class<*>): Method {
        val lookup = CraftEngineService::class.java.getDeclaredMethod("lookupBuildItemStackMethod", Class::class.java)
        lookup.isAccessible = true
        return lookup.invoke(service, type) as Method
    }

    private fun invokeBuildMethod(service: CraftEngineService, target: Any, buildMethod: Method): Any? {
        val invoke = CraftEngineService::class.java.getDeclaredMethod("invokeCustomItemBuildMethod", Any::class.java, Method::class.java)
        invoke.isAccessible = true
        return invoke.invoke(service, target, buildMethod)
    }

    private class LegacyCustomItem {
        fun buildItemStack(): String = "legacy"
    }

    private class DevCustomItemWithCount {
        fun buildBukkitItem(context: ItemBuildContext, count: Int): String {
            require(context.javaClass.simpleName == "ItemBuildContext")
            require(count == 1)
            return "dev-with-count"
        }
    }

    private class DevCustomItemContextOnly {
        fun buildBukkitItem(context: ItemBuildContext): String {
            require(context.javaClass.simpleName == "ItemBuildContext")
            return "dev-context-only"
        }
    }
}
