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
    fun `render package stays free of table and bootstrap dependencies`() {
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
            "Render package should not depend on table internals or bootstrap services:\n${violations.joinToString("\n")}"
        )
    }

    @Test
    fun `ui package only imports the public table surface`() {
        // The ui package legitimately reads tables to render UI, but should
        // not reach into the table package internals (Session*Coordinator,
        // TableSessionContext, TableSessionMutator, ...). Add such a class to
        // the allowlist only after deliberate API design.
        val violations = sourceFiles(
            "src/main/java/top/ellan/mahjong/ui",
            "src/main/kotlin/top/ellan/mahjong/ui"
        ).flatMap { file ->
            Files.readString(file).lineSequence()
                .withIndex()
                .filter { (_, line) ->
                    line.startsWith("import top.ellan.mahjong.table.")
                        && allowedUiTableImports.none { line.startsWith(it) }
                }
                .map { (index, line) -> "${projectRoot.relativize(file)}:${index + 1}: $line" }
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "ui -> table imports must use the public table surface only; see allowedUiTableImports.\n${violations.joinToString("\n")}"
        )
    }

    @Test
    fun `table package does not depend on bootstrap`() {
        val violations = sourceFiles(
            "src/main/java/top/ellan/mahjong/table",
            "src/main/kotlin/top/ellan/mahjong/table"
        ).flatMap { file ->
            Files.readString(file).lineSequence()
                .withIndex()
                .filter { (_, line) -> line.startsWith("import top.ellan.mahjong.bootstrap.") }
                .map { (index, line) -> "${projectRoot.relativize(file)}:${index + 1}: $line" }
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "table -> bootstrap imports must go through TableRuntimeServices.\n${violations.joinToString("\n")}"
        )
    }

    @Test
    fun `bootstrap package does not import command, render, or ui internals`() {
        // Bootstrap is allowed to wire up table.core, but should not reach
        // into command subcommand classes, render snapshot internals, or ui
        // inventory internals -- those are platform glue that table or
        // command surface owns.
        val violations = sourceFiles(
            "src/main/java/top/ellan/mahjong/bootstrap",
            "src/main/kotlin/top/ellan/mahjong/bootstrap"
        ).flatMap { file ->
            Files.readString(file).lineSequence()
                .withIndex()
                .filter { (_, line) ->
                    line.startsWith("import top.ellan.mahjong.command.subcommand.")
                        || line.startsWith("import top.ellan.mahjong.render.snapshot.")
                        || line.startsWith("import top.ellan.mahjong.render.layout.")
                        || line.startsWith("import top.ellan.mahjong.render.scene.")
                        || line.startsWith("import top.ellan.mahjong.ui.")
                }
                .map { (index, line) -> "${projectRoot.relativize(file)}:${index + 1}: $line" }
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "bootstrap should only import top-level command/render packages and table.core, not their internals.\n${violations.joinToString("\n")}"
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
            "top.ellan.mahjong.table.",
            "top.ellan.mahjong.bootstrap."
        )

        /**
         * Imports the ui/ package is allowed to take from the table/ package.
         * Anything outside this list is expected to break out into a render
         * snapshot, an i18n key, or a new ui-facing API on MahjongTableSession
         * rather than directly reaching for table internals.
         */
        val allowedUiTableImports = listOf(
            "import top.ellan.mahjong.table.core.MahjongTableManager",
            "import top.ellan.mahjong.table.core.MahjongTableSession",
            "import top.ellan.mahjong.table.core.TableFinalStanding"
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
