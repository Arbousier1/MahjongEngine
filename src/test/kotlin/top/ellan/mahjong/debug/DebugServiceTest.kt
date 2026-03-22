package top.ellan.mahjong.debug

import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugServiceTest {
    @Test
    fun `disabled debug service turns all categories off`() {
        val debug = DebugService(Logger.getAnonymousLogger(), false, listOf("*"))

        assertFalse(debug.isEnabled())
        assertFalse(debug.isCategoryEnabled("table"))
    }

    @Test
    fun `enabled debug service honors category allowlist`() {
        val debug = DebugService(Logger.getAnonymousLogger(), true, listOf("table", "database"))

        assertTrue(debug.isEnabled())
        assertTrue(debug.isCategoryEnabled("table"))
        assertTrue(debug.isCategoryEnabled("database"))
        assertFalse(debug.isCategoryEnabled("packet"))
    }
}

