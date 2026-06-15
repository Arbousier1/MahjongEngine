package top.ellan.mahjong.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureBoundaryTest {
    @Test
    fun `rules packages stay free of platform and presentation dependencies`() {
        val violations = sourceFiles(
            "src/main/java/top/ellan/mahjong/riichi",
            "src/main/kotlin/top/ellan/mahjong/riichi",
            "src/main/java/top/ellan/mahjong/gb",
            "src/main/kotlin/top/ellan/mahjong/gb"
        ).flatMap { file ->
            Files.readString(file).lineSequence()
                .withIndex()
                .filter { (_, line) -> forbiddenRuleImports.any(line::startsWith) }
                .map { (index, line) -> "${projectRoot.relativize(file)}:${index + 1}: $line" }
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "Rules packages should not import platform or presentation layers:\n${violations.joinToString("\n")}"
        )
    }

    @Test
    fun `render package stays free of table dependencies`() {
        val violations = sourceFiles(
            "src/main/java/top/ellan/mahjong/render",
            "src/main/kotlin/top/ellan/mahjong/render"
        ).flatMap { file ->
            Files.readString(file).lineSequence()
                .withIndex()
                .filter { (_, line) -> forbiddenRenderReferences.any(line::contains) }
                .map { (index, line) -> "${projectRoot.relativize(file)}:${index + 1}: $line" }
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "Render package should not depend on table internals:\n${violations.joinToString("\n")}"
        )
    }

    private companion object {
        val projectRoot: Path = Path.of("").toAbsolutePath()
        val forbiddenRuleImports = listOf(
            "import org.bukkit.",
            "import io.papermc.",
            "import net.kyori.",
            "import top.ellan.mahjong.bootstrap.",
            "import top.ellan.mahjong.command.",
            "import top.ellan.mahjong.compat.",
            "import top.ellan.mahjong.config.",
            "import top.ellan.mahjong.db.",
            "import top.ellan.mahjong.i18n.",
            "import top.ellan.mahjong.metrics.",
            "import top.ellan.mahjong.render.",
            "import top.ellan.mahjong.runtime.",
            "import top.ellan.mahjong.ui."
        )
        val forbiddenRenderReferences = listOf(
            "top.ellan.mahjong.table."
        )

        fun sourceFiles(vararg roots: String): List<Path> = roots.flatMap { root ->
            val rootPath = projectRoot.resolve(root)
            if (!Files.exists(rootPath)) {
                return@flatMap emptyList()
            }
            Files.walk(rootPath).use { stream ->
                stream.filter { file ->
                    Files.isRegularFile(file) && (file.toString().endsWith(".java") || file.toString().endsWith(".kt"))
                }.sorted().toList()
            }
        }
    }
}
