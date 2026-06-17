package top.ellan.mahjong.compat

import java.nio.file.Files
import java.nio.file.Path
import top.ellan.mahjong.bootstrap.MahjongPaperPlugin
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SingleJarCompatibilityTest {
    @Test
    fun `plugin descriptors keep the lowest supported Paper API`() {
        listOf("plugin.yml", "paper-plugin.yml").forEach { descriptor ->
            val stream = javaClass.classLoader.getResourceAsStream(descriptor)
            assertNotNull(stream, "$descriptor should be bundled")

            val text = stream.bufferedReader().use { it.readText() }
            assertContains(text, "api-version: \"1.20\"")
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

    @Test
    fun `platform compatibility uses reflective bridges for newer Paper APIs`() {
        val allowedFiles = setOf(
            Path.of("src/main/java/top/ellan/mahjong/compat/PaperCompatibility.java"),
            Path.of("src/main/java/top/ellan/mahjong/runtime/ServerScheduler.java")
        ).map { projectRoot.resolve(it).normalize() }.toSet()
        val forbidden = listOf(
            "import io.papermc.paper.command.brigadier.",
            "import io.papermc.paper.threadedregions.",
            ".setItemModel(",
            ".teleportAsync(",
            "Bukkit.isOwnedByCurrentRegion("
        )

        val violations = Files.walk(projectRoot.resolve("src/main/java")).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".java") }
                .filter { it.normalize() !in allowedFiles }
                .flatMap { file ->
                    Files.readString(file).lineSequence()
                        .withIndex()
                        .filter { (_, line) -> forbidden.any(line::contains) }
                        .map { (index, line) -> "${projectRoot.relativize(file)}:${index + 1}: $line" }
                        .toList()
                        .stream()
                }
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "Newer Paper APIs must stay behind compatibility bridges:\n${violations.joinToString("\n")}"
        )
    }

    private companion object {
        val projectRoot: Path = Path.of("").toAbsolutePath()
    }
}
