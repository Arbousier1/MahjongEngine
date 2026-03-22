package top.ellan.mahjong.gb.jni

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

class GbMahjongNativeBridgeSmokeTest {
    @Test
    fun `native bridge responds when library is available`() {
        val bridge = GbMahjongNativeBridge()
        assumeTrue(bridge.isAvailable(), bridge.availabilityDetail())

        val version = runCatching { bridge.libraryVersion() }
            .getOrElse { error ->
                assumeTrue(false, "Native library loaded but JNI symbols unavailable: ${error.message}")
                return
            }
        val ping = runCatching { bridge.ping() }
            .getOrElse { error ->
                assumeTrue(false, "Native library loaded but ping JNI call failed: ${error.message}")
                return
            }

        assertTrue(version.isNotBlank())
        assertTrue(ping.contains("ready"))
    }
}

