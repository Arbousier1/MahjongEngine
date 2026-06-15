package top.ellan.mahjong.compat

import top.ellan.mahjong.bootstrap.MahjongPaperPlugin
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SingleJarCompatibilityTest {
    @Test
    fun `plugin descriptors keep the lowest supported Paper API`() {
        listOf("plugin.yml", "paper-plugin.yml").forEach { descriptor ->
            val stream = javaClass.classLoader.getResourceAsStream(descriptor)
            assertNotNull(stream, "$descriptor should be bundled")

            val text = stream.bufferedReader().use { it.readText() }
            assertContains(text, "api-version: \"1.21.11\"")
        }
    }

    @Test
    fun `plugin bytecode follows the configured Java target`() {
        val classResource = MahjongPaperPlugin::class.java.name.replace('.', '/') + ".class"
        val stream = MahjongPaperPlugin::class.java.classLoader.getResourceAsStream(classResource)
        assertNotNull(stream, "$classResource should be on the test classpath")

        val header = ByteArray(8)
        val bytesRead = stream.use { it.read(header) }
        assertEquals(8, bytesRead)

        val majorVersion = ((header[6].toInt() and 0xFF) shl 8) or (header[7].toInt() and 0xFF)
        val expectedMajorVersion = System.getProperty("mahjong.test.expectedClassfileMajor", "65").toInt()
        assertEquals(expectedMajorVersion, majorVersion)
    }
}
