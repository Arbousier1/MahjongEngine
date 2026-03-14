package doublemoon.mahjongcraft.paper.db

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseServiceTest {
    @Test
    fun `resolve h2 file path makes plugin-relative path absolute`() {
        val pluginDir = Path.of("build", "tmp", "mahjongpaper-test")

        val resolved = DatabasePaths.resolveH2FilePath(pluginDir, "data/mahjongpaper")

        assertTrue(resolved.isAbsolute)
        assertEquals(
            pluginDir.toAbsolutePath().normalize().resolve("data/mahjongpaper").normalize(),
            resolved
        )
    }

    @Test
    fun `resolve h2 file path keeps absolute path absolute`() {
        val absolute = Path.of(System.getProperty("user.home")).resolve("mahjongpaper-db").toAbsolutePath().normalize()

        val resolved = DatabasePaths.resolveH2FilePath(Path.of("ignored"), absolute.toString())

        assertEquals(absolute, resolved)
    }
}
