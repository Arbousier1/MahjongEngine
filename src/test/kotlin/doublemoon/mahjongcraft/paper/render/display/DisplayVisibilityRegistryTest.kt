package doublemoon.mahjongcraft.paper.render.display

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisplayVisibilityRegistryTest {
    @Test
    fun `excluded viewers are hidden while others retain access`() {
        val entityId = 42
        val ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val observerId = UUID.fromString("00000000-0000-0000-0000-000000000002")

        DisplayVisibilityRegistry.clear()
        DisplayVisibilityRegistry.registerExcluded(entityId, listOf(ownerId))

        assertTrue(DisplayVisibilityRegistry.matchesExcluded(entityId, listOf(ownerId)))
        assertFalse(DisplayVisibilityRegistry.canView(entityId, ownerId))
        assertTrue(DisplayVisibilityRegistry.canView(entityId, observerId))

        DisplayVisibilityRegistry.clear()
    }
}
