package doublemoon.mahjongcraft.paper.gb.jni

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

class GbMahjongNativeBridgeSmokeTest {
    @Test
    fun `native bridge responds when library is available`() {
        val bridge = GbMahjongNativeBridge()
        assumeTrue(bridge.isAvailable(), bridge.availabilityDetail())

        assertTrue(bridge.libraryVersion().isNotBlank())
        assertTrue(bridge.ping().contains("ready"))
    }
}
