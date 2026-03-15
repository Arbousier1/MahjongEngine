package doublemoon.mahjongcraft.paper.render

import org.bukkit.configuration.MemoryConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntityCullingConfigTest {
    @Test
    fun `defaults are safe when config section is missing`() {
        val config = EntityCullingConfig.from(null)

        assertFalse(config.enabled())
        assertEquals(2L, config.intervalTicks())
        assertEquals(48.0, config.viewDistance())
        assertTrue(config.rateLimitingEnabled())
        assertEquals(64, config.bucketSize())
        assertEquals(8, config.restorePerTick())
    }

    @Test
    fun `custom values are parsed and clamped`() {
        val root = MemoryConfiguration()
        root.set("clientOptimization.entityCulling.enabled", true)
        root.set("clientOptimization.entityCulling.intervalTicks", 0)
        root.set("clientOptimization.entityCulling.viewDistance", 24.0)
        root.set("clientOptimization.entityCulling.rateLimiting.enabled", false)
        root.set("clientOptimization.entityCulling.rateLimiting.bucketSize", -5)
        root.set("clientOptimization.entityCulling.rateLimiting.restorePerTick", 0)

        val config = EntityCullingConfig.from(root)

        assertTrue(config.enabled())
        assertEquals(1L, config.intervalTicks())
        assertEquals(24.0, config.viewDistance())
        assertFalse(config.rateLimitingEnabled())
        assertEquals(1, config.bucketSize())
        assertEquals(1, config.restorePerTick())
    }
}
